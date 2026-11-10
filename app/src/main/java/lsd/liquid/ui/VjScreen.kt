package lsd.liquid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import lsd.liquid.ExternalOutput
import lsd.liquid.VjViewModel
import lsd.liquid.audio.AudioService
import lsd.liquid.audio.AudioSource
import lsd.liquid.visuals.LsdVisuals
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import lsd.liquid.visuals.LsdState

val Lime = Color(0xFFB8FF3C)
private val scheme = darkColorScheme(primary = Lime, onPrimary = Color.Black, secondary = Color(0xFFFF4FD8), surface = Color(0xE6101010))

private const val AUTO_HIDE_MS = 8_000L

/**
 * The picture full screen. The controls start hidden; a tap anywhere shows or hides them, and they hide again
 * after [AUTO_HIDE_MS] without a touch. A live broadcast gets a banner with "Ignore" whether or not they show.
 */
@Composable
fun VjScreen(vm: VjViewModel, output: ExternalOutput, onSource: (AudioSource) -> Unit) {
    val settings by vm.settings.collectAsState()
    val state by vm.state.collectAsState()
    val catalog by vm.catalog.collectAsState()
    val failed by vm.failed.collectAsState()
    val audio by AudioService.status.collectAsState()
    val level by AudioService.level.collectAsState()
    var controls by rememberSaveable { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(0L) }
    val hint by vm.hint.collectAsState()
    var liveBannerHidden by remember { mutableStateOf(false) }
    LaunchedEffect(state.live) { if (!state.live) liveBannerHidden = false }
    LaunchedEffect(controls, lastTouch) {
        if (!controls) return@LaunchedEffect
        delay(AUTO_HIDE_MS)
        controls = false
    }
    LaunchedEffect(Unit) {
        // Once the picture is up and any first-run notice has gone, and only if nobody has touched the screen yet.
        delay(1500)
        vm.hint.first { it == null }
        delay(500)
        if (!controls && lastTouch == 0L) vm.showHint("Tap anywhere for controls", 4000)
    }
    val presenting by output.presenting.collectAsState()
    val preview by output.preview.collectAsState()
    val display by output.display.collectAsState()

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
                // Every touch, wherever it lands, keeps the controls up (seen first, consumed by no one here).
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent(PointerEventPass.Initial); lastTouch = System.currentTimeMillis() }
                }
            },
        ) {
            if (failed) {
                Text(
                    "The visuals can't run on this device: they need a WebView with WebGL 2.",
                    Modifier.align(Alignment.Center).padding(32.dp), color = Color.White, textAlign = TextAlign.Center,
                )
            } else if (presenting) {
                // The picture is on the second display; here, a preview of it.
                preview?.let {
                    Image(it.asImageBitmap(), "Preview of the external display", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
                Text(
                    "ON ${display?.name ?: "EXTERNAL DISPLAY"}".uppercase() + (display?.let { "  ${it.width}x${it.height}" } ?: ""),
                    Modifier.align(Alignment.Center).background(Color(0x99000000)).padding(8.dp), color = Lime,
                )
            } else {
                LsdVisuals(
                    settings = settings, active = true, onFailed = vm::onFailed, modifier = Modifier.fillMaxSize(),
                    control = vm.control, onState = vm::onState, onReady = vm::onPageReady,
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
                    if (state.live && !liveBannerHidden) LiveBanner(state, onIgnore = vm::ignoreLive, onHide = { liveBannerHidden = true })
                    Spacer(Modifier.weight(1f))
                    ControlPanel(vm, state, catalog, settings, audio, level, onSource, display, Modifier.fillMaxWidth())
                }
            }
            if (!controls) {
                Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().safeDrawingPadding()) {
                    if (state.live && !liveBannerHidden) LiveBanner(state, onIgnore = vm::ignoreLive, onHide = { liveBannerHidden = true })
                }
            }
            hint?.let {
                Text(
                    it, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp).background(Color(0xB3000000)).padding(12.dp),
                )
            }
        }
    }
}

/** A desktop live broadcast has the picture: say so, and offer to ignore it for this session. */
@Composable
private fun LiveBanner(state: LsdState, onIgnore: () -> Unit, onHide: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xE6300028)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "LIVE: " + (state.broadcaster ?: "a Liquid LSD broadcast") + " is playing the picture",
            Modifier.weight(1f), color = Color.White, fontSize = 13.sp,
        )
        TextButton(onClick = onIgnore) { Text("IGNORE") }
        TextButton(onClick = onHide) { Text("OK") }
    }
}
