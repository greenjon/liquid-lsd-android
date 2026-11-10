package lsd.liquid.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioCaptureTest {
    @Test
    fun peakIsTheLoudestSampleAndLeavesTheBufferAlone() {
        val b = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder())
        listOf(100, -16384, 8192, 0).forEach { b.putShort(it.toShort()) }
        b.flip()
        assertEquals(0.5f, AudioCapture.peak(b), 1e-6f)
        assertEquals(0, b.position())
    }
}
