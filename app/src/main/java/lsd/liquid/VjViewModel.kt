package lsd.liquid

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lsd.liquid.audio.AudioSource
import lsd.liquid.visuals.ContentUpdates
import lsd.liquid.visuals.LsdCatalog
import lsd.liquid.visuals.LsdControl
import lsd.liquid.visuals.LsdState
import lsd.liquid.visuals.ReducedMotion
import lsd.liquid.visuals.VisualQuality
import lsd.liquid.visuals.VisualsSettings

/**
 * The app's state: the picture's settings (saved), what the picture reports ([state], [catalog]) and the
 * controls ([control]). The picture's own control settings (TIME, effects, background mode, locks, skips) live
 * in the page, which forgets them when it reloads, so they are saved here and handed back on the first report.
 */
class VjViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("liquid-lsd", Context.MODE_PRIVATE)

    val control = LsdControl()

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<VisualsSettings> = _settings.asStateFlow()

    private val _state = MutableStateFlow(LsdState())
    val state: StateFlow<LsdState> = _state.asStateFlow()

    private val _catalog = MutableStateFlow<LsdCatalog?>(null)
    val catalog: StateFlow<LsdCatalog?> = _catalog.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    private val _output = MutableStateFlow(prefs.getBoolean("output", true))
    /** Send the picture to a second display when one is connected. */
    val output: StateFlow<Boolean> = _output.asStateFlow()

    fun setOutput(on: Boolean) { _output.value = on; prefs.edit().putBoolean("output", on).apply() }

    private var restored = false

    /** The input the user last chose; null before the first launch has asked. The mic comes back by itself. */
    var input: AudioSource?
        get() = prefs.getString("input", null)?.let { n -> AudioSource.entries.firstOrNull { it.name == n } }
        set(v) { prefs.edit().putString("input", v?.name).apply() }

    private val _hint = MutableStateFlow<String?>(null)
    /** A one-line notice over the picture (first-run help, an input that fell back), shown for a few seconds. */
    val hint: StateFlow<String?> = _hint.asStateFlow()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val clearHint = Runnable { _hint.value = null }

    fun showHint(text: String, ms: Long = 6000) {
        _hint.value = text
        main.removeCallbacks(clearHint)
        main.postDelayed(clearHint, ms)
    }

    /**
     * Ignores the live broadcast on now: following live goes off for this session only (the saved setting
     * stays on, so the next launch follows again). Turning the switch back on ends it.
     */
    private var liveIgnored = false
    fun ignoreLive() {
        liveIgnored = true
        _settings.value = _settings.value.copy(live = false)
        showHint("Ignoring live broadcasts until the app restarts (AUDIO tab to follow again)")
    }

    /** A page came up (start, renderer restart, or the picture moved display): its controls start at the defaults. */
    fun onPageReady() { restored = false }

    init {
        ContentUpdates.schedule(app, _settings.value.contentUpdates)
    }

    fun onState(s: LsdState) {
        _state.value = s
        if (!restored) {
            restored = true
            restoreControls()
            refreshCatalog()
            return // the page's defaults: not saved over the settings just restored
        }
        saveControls(s)
    }

    fun onFailed() { _failed.value = true }

    /** The scenes' skipped flags change with skip/restore, so the list is read again when shown. */
    fun refreshCatalog() {
        control.catalog { c ->
            if (c == null) return@catalog
            _catalog.value = c
            prefs.edit().putStringSet("skipped", c.scenes.filter { it.skipped }.map { it.name }.toSet()).apply()
        }
    }

    fun skip() { control.skip(); refreshCatalogSoon() }
    fun restore(name: String) { control.restore(name); refreshCatalogSoon() }
    private fun refreshCatalogSoon() = android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refreshCatalog() }, 300)

    fun update(s: VisualsSettings) {
        val old = _settings.value
        if (liveIgnored && s.live) liveIgnored = false
        _settings.value = s
        if (s.contentUpdates != old.contentUpdates) ContentUpdates.schedule(getApplication(), s.contentUpdates)
        prefs.edit()
            .putString("quality", s.quality.name)
            .putFloat("reactivity", s.reactivity)
            .putString("reduced_motion", s.reducedMotion.name)
            .putBoolean("live", if (liveIgnored) prefs.getBoolean("live", true) else s.live)
            .putInt("sync_ms", s.syncMs)
            .putBoolean("content_updates", s.contentUpdates)
            .apply()
    }

    private fun loadSettings() = VisualsSettings(
        quality = enumPref("quality", VisualQuality.AUTO),
        reactivity = prefs.getFloat("reactivity", VisualsSettings.DEFAULT_REACTIVITY),
        reducedMotion = enumPref("reduced_motion", ReducedMotion.AUTO),
        live = prefs.getBoolean("live", true),
        // The input is live sound (no player buffering ahead), so the picture needs no delay by default.
        syncMs = prefs.getInt("sync_ms", 0),
        contentUpdates = prefs.getBoolean("content_updates", true),
    )

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        prefs.getString(key, null)?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private fun saveControls(s: LsdState) {
        prefs.edit()
            .putBoolean("hold", s.hold)
            .putInt("channel", s.channel)
            .putInt("hold_bars", s.holdBars)
            .putInt("auto_roll_bars", s.autoRollBars)
            .putBoolean("follow", s.follow)
            .putBoolean("effects", s.effects)
            .putString("bg_mode", s.bgMode)
            .putString("locked_chain", s.lockedChain)
            .putString("locked_transition", s.lockedTransition)
            .apply()
    }

    private fun restoreControls() {
        if (!prefs.contains("hold_bars")) {
            // First run: the page's defaults, except that backgrounds change with the scenes (the web TV holds its
            // photo gallery, which this app doesn't carry).
            control.backgroundMode("scenes")
            return
        }
        control.time(prefs.getInt("hold_bars", 16))
        control.autoRoll(prefs.getInt("auto_roll_bars", 0))
        control.follow(prefs.getBoolean("follow", true))
        control.effects(prefs.getBoolean("effects", true))
        control.backgroundMode(prefs.getString("bg_mode", "hold") ?: "hold")
        prefs.getString("locked_chain", null)?.let { control.lockChain(it) }
        prefs.getString("locked_transition", null)?.let { control.lockTransition(it) }
        control.setSkipped(prefs.getStringSet("skipped", emptySet()).orEmpty().toList())
        control.hold(prefs.getBoolean("hold", false))
        // Back on the scene that was on air, so a held look survives a restart.
        prefs.getInt("channel", 0).takeIf { it > 0 }?.let { control.channel(it) }
    }
}
