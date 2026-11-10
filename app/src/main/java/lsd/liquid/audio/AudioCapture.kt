package lsd.liquid.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.Log
import lsd.liquid.visuals.PcmTap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

private const val TAG = "AudioCapture"
private const val RATE = 48_000
private const val CHUNK_MS = 20

/**
 * Reads 16-bit mono PCM from an [AudioRecord] on its own thread and hands every buffer to [PcmTap] (which feeds
 * the visuals while they run) and its peak level to [onLevel]. [onEnded] fires if the recording stops by itself
 * (another app took the microphone, the capture was revoked).
 */
class AudioCapture private constructor(
    private val record: AudioRecord,
    private val onLevel: (Float) -> Unit,
    private val onEnded: (String) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        record.startRecording()
        thread = Thread({ loop() }, "lsd-audio").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }

    private fun loop() {
        PcmTap.configure(record.sampleRate, 1)
        val buf = ByteBuffer.allocateDirect(record.sampleRate * CHUNK_MS / 1000 * 2).order(ByteOrder.nativeOrder())
        var reason = "stopped"
        try {
            while (running) {
                buf.clear()
                val n = record.read(buf, buf.capacity(), AudioRecord.READ_BLOCKING)
                if (n < 0) { reason = "read error $n"; break }
                if (n == 0) continue
                buf.limit(n)
                PcmTap.onBuffer(buf)
                onLevel(peak(buf))
            }
        } catch (e: Exception) {
            reason = e.toString()
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
            onLevel(0f)
            if (running) { running = false; Log.w(TAG, "capture ended: $reason"); onEnded(reason) }
        }
    }

    companion object {
        /** Peak of a buffer of 16-bit samples, 0..1. Reads without moving the buffer's position. */
        fun peak(buf: ByteBuffer): Float {
            val s = buf.asReadOnlyBuffer().order(ByteOrder.nativeOrder()).asShortBuffer()
            var p = 0
            while (s.hasRemaining()) p = maxOf(p, abs(s.get().toInt()))
            return p / 32768f
        }

        private fun format() = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()

        private fun bufferBytes() =
            maxOf(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), RATE / 10 * 2)

        /**
         * The microphone, raw where the device offers it (speech processing flattens the beat). Needs RECORD_AUDIO.
         */
        @SuppressLint("MissingPermission")
        fun microphone(audio: AudioManager, onLevel: (Float) -> Unit, onEnded: (String) -> Unit): AudioCapture {
            val raw = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val source = if (raw) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val r = AudioRecord.Builder().setAudioSource(source).setAudioFormat(format()).setBufferSizeInBytes(bufferBytes()).build()
            check(r.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }
            return AudioCapture(r, onLevel, onEnded)
        }

        /**
         * What other apps are playing (media, games, unknown), through a MediaProjection the user granted. Apps that
         * opt out of capture (many streaming apps do) come through as silence. Needs RECORD_AUDIO.
         */
        @SuppressLint("MissingPermission")
        fun playback(projection: MediaProjection, onLevel: (Float) -> Unit, onEnded: (String) -> Unit): AudioCapture {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val r = AudioRecord.Builder().setAudioFormat(format()).setBufferSizeInBytes(bufferBytes())
                .setAudioPlaybackCaptureConfig(config).build()
            check(r.state == AudioRecord.STATE_INITIALIZED) { "playback capture unavailable" }
            return AudioCapture(r, onLevel, onEnded)
        }
    }
}
