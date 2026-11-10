package lsd.liquid

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import lsd.liquid.audio.AudioService
import lsd.liquid.audio.AudioSource
import lsd.liquid.ui.VjScreen

class MainActivity : ComponentActivity() {
    private val vm: VjViewModel by viewModels()
    private var pendingSource: AudioSource? = null
    private lateinit var output: ExternalOutput

    private val askAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val source = pendingSource
        pendingSource = null
        if (!granted) {
            vm.input = AudioSource.OFF
            vm.showHint("No microphone: the picture keeps its own beat. AUDIO tab to listen.", 8000)
            return@registerForActivityResult
        }
        source?.let { startSource(it) }
    }

    private val askCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) AudioService.startSystem(this, r.resultCode, data)
        else vm.showHint("Phone audio not allowed: the picture keeps its current input")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A VJ screen: edge to edge, no system bars (a swipe brings them back), never dims.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        output = ExternalOutput(this, vm)
        output.start()
        lifecycleScope.launch { vm.settings.collect { output.applySettings(it) } }
        lifecycleScope.launch { vm.output.collect { output.update() } }
        setContent { VjScreen(vm, output, onSource = { chooseSource(it) }) }
    }

    private var inputStarted = false

    override fun onStart() {
        super.onStart()
        // Once per launch, after the activity is on screen (a microphone service may only start from the foreground).
        if (!inputStarted) { inputStarted = true; startRememberedInput() }
    }

    /** First launch: ask for the microphone and listen. Later: the last input, the microphone coming back by itself. */
    private fun startRememberedInput() {
        if (AudioService.status.value.source != AudioSource.OFF) return // still running from before
        val micAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        when (vm.input) {
            null -> {
                vm.input = AudioSource.MICROPHONE
                if (!micAllowed) vm.showHint("Allow the microphone so the picture can hear the music. Sound never leaves the phone.", 10000)
                chooseSource(AudioSource.MICROPHONE, remember = false)
            }
            AudioSource.MICROPHONE -> if (micAllowed) AudioService.startMicrophone(this)
            AudioSource.SYSTEM -> if (micAllowed) {
                AudioService.startMicrophone(this)
                vm.showHint("Phone audio needs your OK each time: listening to the microphone (AUDIO tab to switch)", 8000)
            }
            AudioSource.OFF -> {}
        }
    }

    override fun onDestroy() {
        output.stop()
        super.onDestroy()
    }

    /** Switches the audio input, asking for whatever permission it needs first. */
    private fun chooseSource(source: AudioSource) = chooseSource(source, remember = true)

    private fun chooseSource(source: AudioSource, remember: Boolean) {
        if (remember) vm.input = source
        if (source == AudioSource.OFF) { AudioService.stop(this); return }
        // No notification permission prompt: the input's notification is a nicety (Android shows its own microphone
        // indicator), and one prompt on first launch is the most "no setup" can afford. Settings can enable it.
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
}
