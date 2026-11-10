package lsd.liquid

import android.app.Activity
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lsd.liquid.visuals.LsdPresentation
import lsd.liquid.visuals.VisualsSettings

private const val TAG = "ExternalOutput"
private const val PREVIEW_WIDTH = 480
private const val PREVIEW_EVERY_MS = 500L

/** A second display the picture can go to. */
data class OutputDisplay(val id: Int, val name: String, val width: Int, val height: Int, val refreshHz: Float)

/**
 * Sends the picture to a second display (USB-C/HDMI, Miracast, a simulated overlay) when one is connected and
 * output is on: the picture then runs only there (one renderer, one audio feed) and the phone shows the controls
 * over a small preview. Unplugging brings the picture back to the phone. Owned by the activity.
 */
class ExternalOutput(private val activity: Activity, private val vm: VjViewModel) {
    private val dm = activity.getSystemService(DisplayManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var presentation: LsdPresentation? = null

    private val _display = MutableStateFlow<OutputDisplay?>(null)
    /** The second display connected, if any (whether or not the picture is on it). */
    val display: StateFlow<OutputDisplay?> = _display.asStateFlow()

    private val _presenting = MutableStateFlow(false)
    /** True while the picture is on the second display. */
    val presenting: StateFlow<Boolean> = _presenting.asStateFlow()

    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview.asStateFlow()

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = update()
        override fun onDisplayRemoved(displayId: Int) = update()
        override fun onDisplayChanged(displayId: Int) = update()
    }

    fun start() {
        dm.registerDisplayListener(listener, main)
        update()
    }

    fun stop() {
        dm.unregisterDisplayListener(listener)
        hide()
    }

    /** Re-reads the displays and the setting, and shows or hides the presentation to match. */
    fun update() {
        val d = LsdPresentation.displays(activity).firstOrNull()
        _display.value = d?.let { info(it) }
        val want = d != null && vm.output.value
        val current = presentation
        when {
            !want -> hide()
            current == null -> show(d!!)
            current.display.displayId != d!!.displayId -> { hide(); show(d) }
        }
    }

    fun applySettings(s: VisualsSettings) { presentation?.player?.settings = s }

    private fun show(d: Display) {
        Log.d(TAG, "picture to display ${d.displayId} ${d.name}")
        val p = LsdPresentation(
            activity, d, vm.control,
            onReady = vm::onPageReady,
            onFailed = { Log.w(TAG, "picture failed on the display"); hide(); vm.setOutput(false) },
            onState = vm::onState,
        )
        try {
            p.show()
        } catch (e: Exception) { // the display went away, or the window can't be added there
            Log.w(TAG, "could not show on display ${d.displayId}", e)
            return
        }
        p.player.settings = vm.settings.value
        p.player.active = true
        presentation = p
        _presenting.value = true
        main.post(previewTick)
    }

    private fun hide() {
        main.removeCallbacks(previewTick)
        presentation?.let { p ->
            Log.d(TAG, "picture back to the phone")
            try { p.dismiss() } catch (_: Exception) {}
        }
        presentation = null
        _presenting.value = false
        _preview.value = null
    }

    private val previewTick = object : Runnable {
        override fun run() {
            val p = presentation ?: return
            p.snapshot(PREVIEW_WIDTH) { bmp -> if (presentation === p && bmp != null) _preview.value = bmp }
            main.postDelayed(this, PREVIEW_EVERY_MS)
        }
    }

    private fun info(d: Display): OutputDisplay {
        val mode = d.mode
        return OutputDisplay(d.displayId, d.name ?: "Display ${d.displayId}", mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
    }
}
