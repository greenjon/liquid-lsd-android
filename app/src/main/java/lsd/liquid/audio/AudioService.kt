package lsd.liquid.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lsd.liquid.MainActivity
import lsd.liquid.R

private const val TAG = "AudioService"
private const val CHANNEL = "audio-input"
private const val NOTIFICATION_ID = 1

/** Where the visuals' sound comes from. */
enum class AudioSource { OFF, MICROPHONE, SYSTEM }

/** What the audio input is doing, for the UI. */
data class AudioStatus(val source: AudioSource = AudioSource.OFF, val error: String? = null)

/**
 * Keeps the audio input running while the app is in the background or the screen is off (a foreground service:
 * Android stops a background microphone otherwise), and holds the system-audio MediaProjection. One input at a time.
 */
class AudioService : Service() {
    private var capture: AudioCapture? = null
    private var projection: MediaProjection? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MIC -> startInput(AudioSource.MICROPHONE, null)
            ACTION_SYSTEM -> startInput(AudioSource.SYSTEM, intent)
            else -> { stopInput(); stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun startInput(source: AudioSource, data: Intent?) {
        stopInput()
        val type = if (source == AudioSource.SYSTEM) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        else if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST // Android 10 has no microphone type (nor needs one)
        try {
            startForeground(NOTIFICATION_ID, notification(source), type)
            val c = when (source) {
                AudioSource.MICROPHONE -> AudioCapture.microphone(getSystemService(AudioManager::class.java), ::level, ::ended)
                AudioSource.SYSTEM -> {
                    val result = data?.getParcelableExtra<Intent>(EXTRA_DATA) ?: error("no capture permission")
                    val code = data.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                    val p = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, result)
                        ?: error("capture permission refused")
                    p.registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() { main.post { if (projection === p) ended("capture stopped") } }
                    }, main)
                    projection = p
                    AudioCapture.playback(p, ::level, ::ended)
                }
                AudioSource.OFF -> null
            }
            capture = c?.also { it.start() }
            _status.value = AudioStatus(source)
        } catch (e: Exception) {
            Log.w(TAG, "could not start $source", e)
            stopInput()
            _status.value = AudioStatus(AudioSource.OFF, e.message ?: e.toString())
            stopSelf()
        }
    }

    private fun level(peak: Float) {
        // Fall back smoothly, rise at once, so a meter shows hits without flicker.
        val l = _level.value
        _level.value = if (peak > l) peak else l * 0.85f + peak * 0.15f
    }

    private fun ended(reason: String) {
        main.post {
            stopInput()
            _status.value = AudioStatus(AudioSource.OFF, reason)
            stopSelf()
        }
    }

    private fun stopInput() {
        capture?.stop()
        capture = null
        projection?.stop()
        projection = null
        _level.value = 0f
        if (Build.VERSION.SDK_INT >= 33) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    override fun onDestroy() {
        stopInput()
        if (_status.value.source != AudioSource.OFF) _status.value = AudioStatus()
        super.onDestroy()
    }

    private fun notification(source: AudioSource): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Audio input", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while the visuals listen to the microphone or to other apps"
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, AudioService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val text = if (source == AudioSource.SYSTEM) "Listening to what's playing" else "Listening to the microphone"
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_audio)
            .setContentTitle("Liquid LSD")
            .setContentText("$text. The sound is analysed on this phone only.")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_MIC = "lsd.liquid.audio.MIC"
        private const val ACTION_SYSTEM = "lsd.liquid.audio.SYSTEM"
        private const val ACTION_STOP = "lsd.liquid.audio.STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        private val _status = MutableStateFlow(AudioStatus())
        val status: StateFlow<AudioStatus> = _status.asStateFlow()
        private val _level = MutableStateFlow(0f)
        /** Input peak level, 0..1, smoothed for a meter. */
        val level: StateFlow<Float> = _level.asStateFlow()

        /** The microphone; the caller has RECORD_AUDIO. */
        fun startMicrophone(ctx: Context) = ctx.startForegroundService(Intent(ctx, AudioService::class.java).setAction(ACTION_MIC))

        /** Other apps' audio, with the result of MediaProjectionManager.createScreenCaptureIntent(). */
        fun startSystem(ctx: Context, resultCode: Int, data: Intent) = ctx.startForegroundService(
            Intent(ctx, AudioService::class.java).setAction(ACTION_SYSTEM).putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data)
        )

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AudioService::class.java))
            _status.value = AudioStatus()
        }
    }
}
