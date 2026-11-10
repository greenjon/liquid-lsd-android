package lsd.liquid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lsd.liquid.OutputDisplay
import lsd.liquid.VjViewModel
import lsd.liquid.audio.AudioSource
import lsd.liquid.audio.AudioStatus
import lsd.liquid.visuals.LsdCatalog
import lsd.liquid.visuals.LsdState
import lsd.liquid.visuals.ReducedMotion
import lsd.liquid.visuals.VisualQuality
import lsd.liquid.visuals.VisualsSettings
import kotlin.math.roundToInt

private val Panel = Color(0xD9101010)
private val Dim = Color(0xFF8A8A8A)

/** "web-beat-spiral" -> "Beat Spiral": playlist names read as titles. */
fun displayName(name: String?): String {
    if (name.isNullOrBlank()) return "-"
    return name.removePrefix("web-").removePrefix("stock-").replace('-', ' ').replace('_', ' ')
        .split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}

/** What is on air, the tempo, the input and whether a live broadcast has the picture. */
@Composable
fun StatusBar(state: LsdState, audio: AudioStatus, level: Float, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Panel).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "CH %03d  %s".format(state.channel, displayName(state.scene)),
                color = Lime, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(state.chain ?: if (state.effects) null else "effects off", state.background?.let { "BG " + displayName(it) })
                    .joinToString("  ·  "),
                color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.live) {
            Text("LIVE" + (state.broadcaster?.let { " · $it" } ?: ""), color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (state.bpm > 0) "%.0f BPM".format(state.bpm) else "--- BPM",
                color = if (state.beatLocked) Lime else Dim, fontFamily = FontFamily.Monospace,
            )
            Text(
                when (audio.source) { AudioSource.OFF -> "no input"; AudioSource.MICROPHONE -> "mic"; AudioSource.SYSTEM -> "phone audio" },
                color = Dim, fontSize = 12.sp,
            )
            if (audio.source != AudioSource.OFF) LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, Modifier.width(64.dp).height(3.dp))
        }
    }
}

@Composable
fun ControlPanel(
    vm: VjViewModel, state: LsdState, catalog: LsdCatalog?, settings: VisualsSettings, audio: AudioStatus, level: Float,
    onSource: (AudioSource) -> Unit, display: OutputDisplay?, modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val height = (LocalConfiguration.current.screenHeightDp * 0.48f).dp
    Surface(modifier.height(height), color = Panel, contentColor = Color.White) {
        Column {
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
                listOf("SCENES", "EFFECTS", "BG", "AUDIO").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i; if (i < 3) vm.refreshCatalog() }, text = { Text(t, fontSize = 12.sp) })
                }
            }
            if (state.live && tab < 3) {
                Text("A live broadcast has the picture: these controls come back when it ends.",
                    color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(12.dp, 6.dp))
            }
            Box(Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
                when (tab) {
                    0 -> ScenesTab(vm, state, catalog)
                    1 -> EffectsTab(vm, state, catalog)
                    2 -> BackgroundTab(vm, state, catalog)
                    else -> AudioTab(vm, settings, audio, level, onSource, display)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenesTab(vm: VjViewModel, state: LsdState, catalog: LsdCatalog?) {
    val c = vm.control
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = c::prev) { Icon(Icons.Filled.SkipPrevious, "Previous scene") }
            IconButton(onClick = c::next) { Icon(Icons.Filled.SkipNext, "Next scene") }
            IconButton(onClick = c::random) { Icon(Icons.Filled.Casino, "Random scene") }
            IconButton(onClick = c::roll) { Icon(Icons.Filled.Refresh, "Roll: new random settings for this scene") }
            Spacer(Modifier.weight(1f))
            FilterChip(selected = state.hold, onClick = { c.hold(!state.hold) }, label = { Text("HOLD") })
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = vm::skip) { Text(if (state.skipped) "UNSKIP" else "SKIP") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
            Label("Change every")
            listOf(4, 8, 16, 32, 64).forEach { b -> FilterChip(state.holdBars == b, { c.time(b) }, { Text("$b bars") }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Label("Auto roll")
            listOf(0, 4, 8, 16).forEach { b -> FilterChip(state.autoRollBars == b, { c.autoRoll(b) }, { Text(if (b == 0) "off" else "$b bars") }) }
            FilterChip(state.follow, { c.follow(!state.follow) }, { Text("Follow music") })
        }
        val scenes = catalog?.scenes.orEmpty()
        LazyColumn(Modifier.fillMaxWidth()) {
            items(scenes, key = { it.channel }) { s ->
                val onAir = s.channel == state.channel
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = s.runnable) { c.channel(s.channel) }
                        .background(if (onAir) Lime.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%03d".format(s.channel), color = Dim, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayName(s.name), color = if (!s.runnable || s.skipped) Dim else Color.White, maxLines = 1)
                        if (s.tags.isNotEmpty()) Text(s.tags.joinToString(" "), color = Dim, fontSize = 11.sp, maxLines = 1)
                    }
                    when {
                        !s.runnable -> Text("too heavy", color = Dim, fontSize = 11.sp)
                        s.skipped -> TextButton(onClick = { vm.restore(s.name) }) { Text("restore", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectsTab(vm: VjViewModel, state: LsdState, catalog: LsdCatalog?) {
    val c = vm.control
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Effects", Modifier.weight(1f))
            Switch(state.effects, { c.effects(it) })
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = c::nextChain, enabled = state.effects) { Text("Next chain") }
        }
        Text("Tap a chain to put it on now; the lock keeps it on every scene.", color = Dim, fontSize = 11.sp)
        LazyColumn(Modifier.fillMaxWidth()) {
            item { Header("FX chains") }
            items(catalog?.fxChains.orEmpty(), key = { "fx:" + it.id }) { ch ->
                ChoiceRow(ch.name, on = state.chain == ch.name, locked = state.lockedChain == ch.id, enabled = state.effects,
                    onClick = { c.chain(ch.id) }, onLock = { c.lockChain(if (state.lockedChain == ch.id) null else ch.id) })
            }
            item { Header("Transitions") }
            items(catalog?.transitions.orEmpty(), key = { "tr:" + it.id }) { t ->
                ChoiceRow(displayName(t.name), on = false, locked = state.lockedTransition == t.id, enabled = true,
                    onClick = { c.lockTransition(if (state.lockedTransition == t.id) null else t.id) },
                    onLock = { c.lockTransition(if (state.lockedTransition == t.id) null else t.id) })
            }
        }
    }
}

@Composable
private fun BackgroundTab(vm: VjViewModel, state: LsdState, catalog: LsdCatalog?) {
    val c = vm.control
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("scenes" to "Changes", "hold" to "Holds", "off" to "Off").forEach { (m, label) ->
                FilterChip(state.bgMode == m, { c.backgroundMode(m) }, { Text(label) }, Modifier.padding(end = 6.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = c::prevBackground, enabled = state.bgMode != "off") { Icon(Icons.Filled.SkipPrevious, "Previous background") }
            IconButton(onClick = c::nextBackground, enabled = state.bgMode != "off") { Icon(Icons.Filled.SkipNext, "Next background") }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(catalog?.backgrounds.orEmpty(), key = { it.index }) { b ->
                ChoiceRow(displayName(b.name), on = state.background == b.name, locked = null, enabled = state.bgMode != "off",
                    onClick = { c.background(b.index) }, onLock = {})
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioTab(vm: VjViewModel, s: VisualsSettings, audio: AudioStatus, level: Float, onSource: (AudioSource) -> Unit, display: OutputDisplay?) {
    val output by vm.output.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Label("Listen to")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(audio.source == AudioSource.MICROPHONE, { onSource(AudioSource.MICROPHONE) }, { Text("Microphone") })
            FilterChip(audio.source == AudioSource.SYSTEM, { onSource(AudioSource.SYSTEM) }, { Text("Phone audio") })
            FilterChip(audio.source == AudioSource.OFF, { onSource(AudioSource.OFF) }, { Text("Nothing") })
        }
        LinearProgressIndicator(progress = { level.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
        audio.error?.let { Text("Input stopped: $it", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp) }
        Text(
            "Phone audio hears what other apps play. Some apps (many streaming apps) don't allow it and come through silent: use the microphone for those. Sound is analysed on this phone only, never recorded or sent.",
            color = Dim, fontSize = 11.sp,
        )
        Label("React: ${(s.reactivity * 100).roundToInt()}%")
        Slider(s.reactivity, { vm.update(s.copy(reactivity = it)) }, valueRange = VisualsSettings.MIN_REACTIVITY..VisualsSettings.MAX_REACTIVITY)
        Label("Picture delay: ${s.syncMs} ms (for speakers that lag behind)")
        Slider(s.syncMs.toFloat(), { vm.update(s.copy(syncMs = (it / 10).roundToInt() * 10)) }, valueRange = 0f..400f)
        Label("Quality")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VisualQuality.entries.forEach { q -> FilterChip(s.quality == q, { vm.update(s.copy(quality = q)) }, { Text(q.name.lowercase()) }) }
        }
        Label("Calm motion (no flashes)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReducedMotion.entries.forEach { r -> FilterChip(s.reducedMotion == r, { vm.update(s.copy(reducedMotion = r)) }, { Text(r.name.lowercase()) }) }
        }
        SwitchRow(
            "Picture on the external display" + (display?.let { ": ${it.name}, ${it.width}x${it.height} at ${it.refreshHz.toInt()} Hz" } ?: " (none connected)"),
            output,
        ) { vm.setOutput(it) }
        SwitchRow("Follow live broadcasts from Liquid LSD desktop", s.live) { vm.update(s.copy(live = it)) }
        SwitchRow("Download new scenes in the background", s.contentUpdates) { vm.update(s.copy(contentUpdates = it)) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ChoiceRow(name: String, on: Boolean, locked: Boolean?, enabled: Boolean, onClick: () -> Unit, onLock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)
            .background(if (on) Lime.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f).padding(vertical = 10.dp), color = if (enabled) Color.White else Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (locked != null) {
            IconButton(onClick = onLock, enabled = enabled, modifier = Modifier.size(36.dp)) {
                Icon(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, if (locked) "Unlock" else "Lock",
                    tint = if (locked) Lime else Dim)
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked, onChange)
    }
}

@Composable
private fun Header(text: String) = Text(text.uppercase(), color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Label(text: String) = Text(text, color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, end = 6.dp))
