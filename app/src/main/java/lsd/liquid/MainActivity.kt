package lsd.liquid

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import lsd.liquid.audio.AudioService
import lsd.liquid.audio.AudioSource
import lsd.liquid.ui.VjScreen

class MainActivity : ComponentActivity() {
    private val vm: VjViewModel by viewModels()
    private var pendingSource: AudioSource? = null

    private val askAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val source = pendingSource
        pendingSource = null
        if (!granted) { toast("Liquid LSD needs the microphone permission to hear anything"); return@registerForActivityResult }
        source?.let { startSource(it) }
    }

    private val askCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) AudioService.startSystem(this, r.resultCode, data)
        else toast("Capture not allowed: the picture keeps its current input")
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A VJ screen: edge to edge, no system bars (a swipe brings them back), never dims.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { VjScreen(vm, onSource = ::chooseSource) }
    }

    /** Switches the audio input, asking for whatever permission it needs first. */
    private fun chooseSource(source: AudioSource) {
        if (source == AudioSource.OFF) { AudioService.stop(this); return }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) // the input's notification; it runs without it
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingSource = source
            askAudio.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startSource(source)
    }

    private fun startSource(source: AudioSource) {
        when (source) {
            AudioSource.MICROPHONE -> AudioService.startMicrophone(this)
            AudioSource.SYSTEM -> askCapture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
            AudioSource.OFF -> AudioService.stop(this)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
