package lsd.liquid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lsd.liquid.VjViewModel
import lsd.liquid.audio.AudioService
import lsd.liquid.audio.AudioSource
import lsd.liquid.visuals.LsdVisuals

val Lime = Color(0xFFB8FF3C)
private val scheme = darkColorScheme(primary = Lime, onPrimary = Color.Black, secondary = Color(0xFFFF4FD8), surface = Color(0xE6101010))

/** The picture full screen; a tap anywhere shows or hides the controls over it. */
@Composable
fun VjScreen(vm: VjViewModel, onSource: (AudioSource) -> Unit) {
    val settings by vm.settings.collectAsState()
    val state by vm.state.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val failed by vm.failed.collectAsState()
    val audio by AudioService.status.collectAsState()
    val level by AudioService.level.collectAsState()
    var controls by rememberSaveable { mutableStateOf(true) }

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (failed) {
                Text(
                    "The visuals can't run on this device: they need a WebView with WebGL 2.",
                    Modifier.align(Alignment.Center).padding(32.dp), color = Color.White, textAlign = TextAlign.Center,
                )
            } else {
                LsdVisuals(
                    settings = settings, active = true, onFailed = vm::onFailed, modifier = Modifier.fillMaxSize(),
                    control = vm.control, onState = vm::onState,
                )
            }
            Box(
                Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    controls = !controls
                },
            )
            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut()) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    StatusBar(state, audio, level, Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    ControlPanel(vm, state, catalog, settings, audio, level, onSource, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
