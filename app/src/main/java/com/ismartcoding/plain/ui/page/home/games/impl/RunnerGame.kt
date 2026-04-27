package com.ismartcoding.plain.ui.page.home.games.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.LaneRushSettingsJsonPreference
import com.ismartcoding.plain.ui.page.home.games.GamesStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ----------------------------- Settings ---------------------------------

private data class LaneRushSettings(
    var laneCount: Int = 3,            // 3 / 4 / 5
    var sound: Boolean = true,
    var music: Boolean = true,
    var voiceCalls: Boolean = true,
    var haptics: Boolean = true,
    var tilt: Boolean = false,
    var tiltSensitivity: Float = 0.5f, // 0..1
    var swipeStrength: Float = 0.5f,
    var tapZones: Boolean = true,      // tap halves to steer
    var oneHanded: Boolean = false,    // moves controls into bottom band
    var leftyMirror: Boolean = false,
    var reducedMotion: Boolean = false,
    var highContrast: Boolean = false,
    var batterySaver: Boolean = false,
    var colorblind: String = "none",   // none / deutan / protan / tritan
    var bigHud: Boolean = false,
    var skin: String = "starter",
    var trail: Boolean = true,
    var theme: String = "auto",        // auto / asphalt / rain / night / desert / neon
    var ghost: Boolean = true,
    var dailySeed: Boolean = false,
    var pendingThemeUnlocks: MutableList<String> = mutableListOf(),
    var unlockedSkins: MutableList<String> = mutableListOf("starter"),
    var ownedThemes: MutableList<String> = mutableListOf("asphalt"),
    var mmr: Float = 1000f,
    var totalDistance: Int = 0,
    var totalCoins: Int = 0,
    var bossesBeat: Int = 0,
    var perfectRuns: Int = 0,
    var runsCompleted: Int = 0,
)

private fun parseLR(json: String): LaneRushSettings {
    val s = LaneRushSettings()
    try {
        val o = JSONObject(if (json.isBlank()) "{}" else json)
        s.laneCount = o.optInt("laneCount", s.laneCount).coerceIn(3, 5)
        s.sound = o.optBoolean("sound", s.sound)
        s.music = o.optBoolean("music", s.music)
        s.voiceCalls = o.optBoolean("voiceCalls", s.voiceCalls)
        s.haptics = o.optBoolean("haptics", s.haptics)
        s.tilt = o.optBoolean("tilt", s.tilt)
        s.tiltSensitivity = o.optDouble("tiltSensitivity", s.tiltSensitivity.toDouble()).toFloat()
        s.swipeStrength = o.optDouble("swipeStrength", s.swipeStrength.toDouble()).toFloat()
        s.tapZones = o.optBoolean("tapZones", s.tapZones)
        s.oneHanded = o.optBoolean("oneHanded", s.oneHanded)
        s.leftyMirror = o.optBoolean("leftyMirror", s.leftyMirror)
        s.reducedMotion = o.optBoolean("reducedMotion", s.reducedMotion)
        s.highContrast = o.optBoolean("highContrast", s.highContrast)
        s.batterySaver = o.optBoolean("batterySaver", s.batterySaver)
        s.colorblind = o.optString("colorblind", s.colorblind)
        s.bigHud = o.optBoolean("bigHud", s.bigHud)
        s.skin = o.optString("skin", s.skin)
        s.trail = o.optBoolean("trail", s.trail)
        s.theme = o.optString("theme", s.theme)
        s.ghost = o.optBoolean("ghost", s.ghost)
        s.dailySeed = o.optBoolean("dailySeed", s.dailySeed)
        s.mmr = o.optDouble("mmr", s.mmr.toDouble()).toFloat()
        s.totalDistance = o.optInt("totalDistance", s.totalDistance)
        s.totalCoins = o.optInt("totalCoins", s.totalCoins)
        s.bossesBeat = o.optInt("bossesBeat", s.bossesBeat)
        s.perfectRuns = o.optInt("perfectRuns", s.perfectRuns)
        s.runsCompleted = o.optInt("runsCompleted", s.runsCompleted)
        s.unlockedSkins = parseStrArr(o.optJSONArray("unlockedSkins"), listOf("starter"))
        s.ownedThemes = parseStrArr(o.optJSONArray("ownedThemes"), listOf("asphalt"))
    } catch (_: Throwable) {}
    return s
}

private fun parseStrArr(a: JSONArray?, def: List<String>): MutableList<String> {
    if (a == null) return def.toMutableList()
    val out = mutableListOf<String>()
    for (i in 0 until a.length()) out += a.optString(i)
    return if (out.isEmpty()) def.toMutableList() else out
}

private fun toJson(s: LaneRushSettings): String {
    val o = JSONObject()
    o.put("laneCount", s.laneCount); o.put("sound", s.sound); o.put("music", s.music)
    o.put("voiceCalls", s.voiceCalls); o.put("haptics", s.haptics); o.put("tilt", s.tilt)
    o.put("tiltSensitivity", s.tiltSensitivity.toDouble()); o.put("swipeStrength", s.swipeStrength.toDouble())
    o.put("tapZones", s.tapZones); o.put("oneHanded", s.oneHanded); o.put("leftyMirror", s.leftyMirror)
    o.put("reducedMotion", s.reducedMotion); o.put("highContrast", s.highContrast)
    o.put("batterySaver", s.batterySaver); o.put("colorblind", s.colorblind); o.put("bigHud", s.bigHud)
    o.put("skin", s.skin); o.put("trail", s.trail); o.put("theme", s.theme); o.put("ghost", s.ghost)
    o.put("dailySeed", s.dailySeed); o.put("mmr", s.mmr.toDouble())
    o.put("totalDistance", s.totalDistance); o.put("totalCoins", s.totalCoins)
    o.put("bossesBeat", s.bossesBeat); o.put("perfectRuns", s.perfectRuns)
    o.put("runsCompleted", s.runsCompleted)
    o.put("unlockedSkins", JSONArray(s.unlockedSkins))
    o.put("ownedThemes", JSONArray(s.ownedThemes))
    return o.toString()
}

// ----------------------------- Catalog ----------------------------------

private data class SkinDef(val id: String, val label: String, val price: Int, val color: Color, val accent: Color)

private val SKINS = listOf(
    SkinDef("starter", "Starter Hatch", 0, Color(0xFF7DD3FC), Color(0xFF0EA5E9)),
    SkinDef("muscle", "Muscle V8", 100, Color(0xFFEF4444), Color(0xFF7F1D1D)),
    SkinDef("hyper", "Hyper Coupe", 200, Color(0xFFF59E0B), Color(0xFFB45309)),
    SkinDef("rally", "Rally Beast", 250, Color(0xFF22C55E), Color(0xFF15803D)),
    SkinDef("emt", "EMT Sprinter", 300, Color(0xFFFFFFFF), Color(0xFFDC2626)),
    SkinDef("taxi", "City Taxi", 220, Color(0xFFFACC15), Color(0xFF111827)),
    SkinDef("limo", "Stretch Limo", 400, Color(0xFF111827), Color(0xFFE5E7EB)),
    SkinDef("ute", "Outback Ute", 280, Color(0xFFA16207), Color(0xFF422006)),
    SkinDef("kei", "Kei Kart", 180, Color(0xFFA78BFA), Color(0xFF4C1D95)),
    SkinDef("tow", "Tow Truck", 350, Color(0xFFEAB308), Color(0xFF1F2937)),
    SkinDef("fire", "Fire Engine", 500, Color(0xFFB91C1C), Color(0xFFFEF3C7)),
    SkinDef("astro", "Astro Pod", 650, Color(0xFFE0E7FF), Color(0xFF6366F1)),
    SkinDef("hover", "Hover Glide", 800, Color(0xFF06B6D4), Color(0xFF155E75)),
    SkinDef("retro", "Retro Wagon", 240, Color(0xFFFB7185), Color(0xFF881337)),
    SkinDef("glass", "Glass GT", 700, Color(0xFFBAE6FD), Color(0xFF1E3A8A)),
    SkinDef("plat", "Platinum Saloon", 900, Color(0xFFCBD5E1), Color(0xFF334155)),
    SkinDef("monster", "Monster Truck", 1200, Color(0xFF65A30D), Color(0xFF1A2E05)),
    SkinDef("phantom", "Phantom Drift", 1400, Color(0xFF1F2937), Color(0xFF8B5CF6)),
    SkinDef("eclipse", "Eclipse R", 1700, Color(0xFF0F172A), Color(0xFFFCD34D)),
    SkinDef("aurora", "Aurora Concept", 2000, Color(0xFF34D399), Color(0xFFA7F3D0)),
)

private data class ThemeDef(
    val id: String, val label: String, val sky: List<Color>, val road: Color, val line: Color,
    val accent: Color, val particles: String, val price: Int,
)

private val THEMES = listOf(
    ThemeDef("asphalt", "Asphalt Sunset", listOf(Color(0xFFFB923C), Color(0xFF7C2D12), Color(0xFF1F2937)), Color(0xFF1F2937), Color(0xFFFCD34D), Color(0xFFFB923C), "dust", 0),
    ThemeDef("rain", "Rainy Boulevard", listOf(Color(0xFF1E3A8A), Color(0xFF312E81), Color(0xFF111827)), Color(0xFF111827), Color(0xFF93C5FD), Color(0xFF60A5FA), "rain", 200),
    ThemeDef("night", "Neon Tunnel", listOf(Color(0xFF000000), Color(0xFF0F0F2E), Color(0xFF000000)), Color(0xFF0B0F1A), Color(0xFF22D3EE), Color(0xFFEC4899), "neon", 300),
    ThemeDef("desert", "Desert Cliff", listOf(Color(0xFFFCD34D), Color(0xFFB45309), Color(0xFF7C2D12)), Color(0xFF78350F), Color(0xFFFEF3C7), Color(0xFFFB923C), "dust", 350),
    ThemeDef("neon", "Synthwave Grid", listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFF111827)), Color(0xFF1E1B4B), Color(0xFFE879F9), Color(0xFFA855F7), "grid", 500),
)

// --------------------------- Game Entities ------------------------------

private enum class ObsKind { Sedan, Truck, Bus, Police, Moto, Block, Barrel, Cone, Drone, Beam, Wall, Boss, Elite, Slick, Pothole, Spike }
private enum class PickupKind { Coin, Shield, SlowMo, Magnet, Double, Heart }

private data class Obs(
    var kind: ObsKind, var lane: Int, var z: Float, var ttlMs: Float = 0f,
    var lateral: Float = 0f, var locked: Boolean = false, var hp: Int = 1,
    var width: Int = 1, var bossOpenLane: Int = 0,
)

private data class Pkp(var kind: PickupKind, var lane: Int, var z: Float)

private data class Crash(val zoneX: Float, val zoneY: Float, val time: Float, val obstacle: String)

private data class Reaction(val time: Float, val ms: Float)

// ============================== UI ======================================

@Composable
fun RunnerGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { mutableStateOf(LaneRushSettings()) }
    LaunchedEffect(Unit) { settings.value = parseLR(LaneRushSettingsJsonPreference.getAsync(ctx)) }
    fun save() { scope.launch { LaneRushSettingsJsonPreference.putAsync(ctx, toJson(settings.value)) } }

    // TTS
    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) ttsRef.value?.language = Locale.getDefault()
        }
        ttsRef.value = t
        onDispose { try { t.stop(); t.shutdown() } catch (_: Throwable) {} }
    }
    fun speak(s: String) {
        if (!settings.value.voiceCalls || !settings.value.sound) return
        try { ttsRef.value?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "lr") } catch (_: Throwable) {}
    }

    // Tones
    val tone = remember { try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (_: Throwable) { null } }
    DisposableEffect(Unit) { onDispose { try { tone?.release() } catch (_: Throwable) {} } }
    fun beep(kind: String) {
        if (!settings.value.sound) return
        val code = when (kind) {
            "tap" -> ToneGenerator.TONE_PROP_BEEP
            "win" -> ToneGenerator.TONE_PROP_ACK
            "lose" -> ToneGenerator.TONE_CDMA_LOW_PBX_L
            "power" -> ToneGenerator.TONE_PROP_PROMPT
            "tick" -> ToneGenerator.TONE_DTMF_1
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        try { tone?.startTone(code, 70) } catch (_: Throwable) {}
    }

    // Vibrator
    val vib = remember { ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    fun haptic(ms: Long = 20) {
        if (!settings.value.haptics) return
        try { vib?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Throwable) {}
    }

    // Tilt sensor
    val sensorManager = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val tiltX = remember { mutableStateOf(0f) }
    DisposableEffect(settings.value.tilt) {
        if (!settings.value.tilt || sensorManager == null) return@DisposableEffect onDispose {}
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    tiltX.value = e.values[0]
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // -------------------- Mutable game state --------------------
    val laneCount = settings.value.laneCount
    val carLane = remember { mutableStateOf(laneCount / 2) }
    val targetLane = remember { mutableStateOf(laneCount / 2) }
    val carLerp = remember { mutableStateOf(carLane.value.toFloat()) }
    val isJumping = remember { mutableStateOf(false) }
    val jumpT = remember { mutableStateOf(0f) }
    val isSliding = remember { mutableStateOf(false) }
    val slideT = remember { mutableStateOf(0f) }

    val distance = remember { mutableStateOf(0f) }
    val score = remember { mutableStateOf(0) }
    val coinsEarned = remember { mutableStateOf(0) }
    val combo = remember { mutableStateOf(1f) }
    val nearMissCount = remember { mutableStateOf(0) }
    val perfectStretch = remember { mutableStateOf(0f) }

    val baseSpeed = remember { mutableStateOf(20f) } // m/s logical
    val speedBoost = remember { mutableStateOf(1f) } // multiplier
    val slowMoT = remember { mutableStateOf(0f) }
    val shieldT = remember { mutableStateOf(0f) }
    val magnetT = remember { mutableStateOf(0f) }
    val doubleT = remember { mutableStateOf(0f) }
    val hearts = remember { mutableStateOf(if (mode == "Stage" || mode == "Mission") 3 else 1) }

    val obstacles = remember { mutableStateOf<List<Obs>>(emptyList()) }
    val pickups = remember { mutableStateOf<List<Pkp>>(emptyList()) }
    val crashes = remember { mutableStateOf<List<Crash>>(emptyList()) }
    val reactions = remember { mutableStateOf<List<Reaction>>(emptyList()) }
    val laneTime = remember { mutableStateOf(FloatArray(laneCount)) }

    val started = remember { mutableStateOf(false) }
    val gameOver = remember { mutableStateOf(false) }
    val showSettings = remember { mutableStateOf(false) }
    val showShop = remember { mutableStateOf(false) }
    val showCalibration = remember { mutableStateOf(false) }
    val showAnalytics = remember { mutableStateOf(false) }
    val ghostMode = remember { mutableStateOf(false) }

    val bossActive = remember { mutableStateOf(false) }
    val nextBossDist = remember { mutableStateOf(2000f) }
    val stage = remember { mutableStateOf(1) }
    val timeLeftMs = remember { mutableStateOf(60_000f) }

    val seed = remember(mode, settings.value.dailySeed) {
        if (settings.value.dailySeed) {
            val today = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date()).toLong()
            today
        } else System.currentTimeMillis()
    }
    val rng = remember(seed) { Random(seed) }

    // theme rotation
    val themeIdx = remember { mutableStateOf(((settings.value.runsCompleted) / 5) % THEMES.size) }
    val theme = if (settings.value.theme == "auto") THEMES[themeIdx.value] else THEMES.firstOrNull { it.id == settings.value.theme } ?: THEMES[0]

    val skin = SKINS.firstOrNull { it.id == settings.value.skin } ?: SKINS[0]

    val recordedFrames = remember { mutableListOf<Int>() }
    val frameCount = remember { mutableStateOf(0) }

    // Reaction tracking
    val reactionWindowOpen = remember { mutableStateOf<Long?>(null) }
    val firstObstacleSeen = remember { mutableStateOf<MutableSet<Int>>(mutableSetOf()) }

    fun reset() {
        carLane.value = laneCount / 2; targetLane.value = laneCount / 2; carLerp.value = (laneCount / 2).toFloat()
        isJumping.value = false; jumpT.value = 0f; isSliding.value = false; slideT.value = 0f
        distance.value = 0f; score.value = 0; coinsEarned.value = 0; combo.value = 1f
        nearMissCount.value = 0; perfectStretch.value = 0f
        speedBoost.value = 1f; slowMoT.value = 0f; shieldT.value = 0f; magnetT.value = 0f; doubleT.value = 0f
        hearts.value = if (mode == "Stage" || mode == "Mission") 3 else 1
        obstacles.value = emptyList(); pickups.value = emptyList()
        crashes.value = emptyList(); reactions.value = emptyList()
        laneTime.value = FloatArray(laneCount)
        bossActive.value = false; nextBossDist.value = 2000f; stage.value = 1
        timeLeftMs.value = 60_000f; recordedFrames.clear(); frameCount.value = 0
        baseSpeed.value = when (difficulty.lowercase()) {
            "easy" -> 16f; "hard" -> 24f; "expert", "insane" -> 28f
            else -> 20f
        }
        gameOver.value = false
        firstObstacleSeen.value = mutableSetOf()
    }

    fun endRun() {
        if (gameOver.value) return
        gameOver.value = true
        val target = (1000f + distance.value * 0.4f).coerceAtMost(3500f)
        settings.value.mmr += (target - settings.value.mmr) * 0.15f
        settings.value.totalDistance += distance.value.toInt()
        settings.value.totalCoins += coinsEarned.value
        settings.value.runsCompleted += 1
        if (crashes.value.isEmpty()) settings.value.perfectRuns += 1
        if (settings.value.theme == "auto") themeIdx.value = (settings.value.runsCompleted / 5) % THEMES.size
        val unlockOrder = listOf("rain", "night", "desert", "neon")
        unlockOrder.forEachIndexed { i, id ->
            val req = (i + 1) * 5
            if (settings.value.runsCompleted >= req && id !in settings.value.ownedThemes) settings.value.ownedThemes.add(id)
        }
        save()
        GamesStore.finishRun("runner", score.value)
        beep("win")
        scope.launch { delay(500); showAnalytics.value = true }
    }

    // Game loop
    LaunchedEffect(started.value, paused, gameOver.value) {
        if (!started.value || paused || gameOver.value) return@LaunchedEffect
        var last = System.nanoTime()
        while (started.value && !paused && !gameOver.value) {
            val now = System.nanoTime()
            var dt = ((now - last) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            last = now
            if (settings.value.batterySaver) dt *= 0.9f
            val timeScale = if (slowMoT.value > 0f) 0.5f else 1f
            val effSpeed = baseSpeed.value * speedBoost.value * timeScale
            distance.value += effSpeed * dt
            frameCount.value++

            // adaptive boost
            speedBoost.value = (1f + distance.value / 4000f * (settings.value.mmr / 1000f)).coerceAtMost(2.4f)

            // timers
            if (slowMoT.value > 0f) slowMoT.value = (slowMoT.value - dt).coerceAtLeast(0f)
            if (shieldT.value > 0f) shieldT.value = (shieldT.value - dt).coerceAtLeast(0f)
            if (magnetT.value > 0f) magnetT.value = (magnetT.value - dt).coerceAtLeast(0f)
            if (doubleT.value > 0f) doubleT.value = (doubleT.value - dt).coerceAtLeast(0f)

            if (mode == "Time") {
                timeLeftMs.value = (timeLeftMs.value - dt * 1000f).coerceAtLeast(0f)
                if (timeLeftMs.value <= 0f) { endRun() }
            }

            // tilt steering
            if (settings.value.tilt && abs(tiltX.value) > 1.5f * (1.5f - settings.value.tiltSensitivity)) {
                if (tiltX.value < 0f && targetLane.value < laneCount - 1) targetLane.value++
                else if (tiltX.value > 0f && targetLane.value > 0) targetLane.value--
            }

            // smooth lane
            val tgt = targetLane.value.toFloat()
            val diff = tgt - carLerp.value
            carLerp.value += diff * (10f * dt).coerceAtMost(1f)
            if (abs(diff) < 0.02f) { carLerp.value = tgt; carLane.value = targetLane.value }

            // jump / slide timers
            if (isJumping.value) {
                jumpT.value += dt
                if (jumpT.value > 0.7f) { isJumping.value = false; jumpT.value = 0f }
            }
            if (isSliding.value) {
                slideT.value += dt
                if (slideT.value > 0.55f) { isSliding.value = false; slideT.value = 0f }
            }

            laneTime.value[carLane.value] = laneTime.value[carLane.value] + dt
            recordedFrames += carLane.value

            // spawn obstacles
            spawn(rng, obstacles, pickups, distance.value, laneCount, settings.value, mode, bossActive, nextBossDist, dt, effSpeed)

            // advance entities
            val moveZ = effSpeed * dt
            obstacles.value = obstacles.value.map { it.copy(z = it.z - moveZ) }.filter { it.z > -2f }
            pickups.value = pickups.value.map {
                if (magnetT.value > 0f) {
                    val dx = (carLane.value - it.lane) * dt * 6f
                    it.copy(lane = it.lane, z = it.z - moveZ).also { p -> p.lane = (p.lane + dx.roundToInt()).coerceIn(0, laneCount - 1) }
                } else it.copy(z = it.z - moveZ)
            }.filter { it.z > -2f }

            // collisions
            obstacles.value.forEach { o ->
                if (o.z < 5f && !firstObstacleSeen.value.contains(System.identityHashCode(o))) {
                    firstObstacleSeen.value.add(System.identityHashCode(o))
                    reactionWindowOpen.value = System.currentTimeMillis()
                }
                if (o.z in 0f..0.6f) {
                    val sameLane = o.lane == carLane.value || (o.width > 1 && carLane.value in o.lane until (o.lane + o.width))
                    val passable = (o.kind == ObsKind.Beam && isSliding.value) || (o.kind == ObsKind.Wall && isJumping.value) || (o.kind == ObsKind.Boss && carLane.value == o.bossOpenLane)
                    if (sameLane && !passable) {
                        if (shieldT.value > 0f) {
                            shieldT.value = 0f; haptic(50); beep("power")
                            obstacles.value = obstacles.value.filter { it !== o }
                        } else {
                            crashes.value = crashes.value + Crash(carLane.value.toFloat(), distance.value, distance.value, o.kind.name)
                            hearts.value -= 1
                            haptic(120); beep("lose")
                            if (hearts.value <= 0) endRun() else {
                                obstacles.value = obstacles.value.filter { it !== o }
                                shieldT.value = 1.2f
                            }
                        }
                    } else if (sameLane && passable) {
                        // beat boss / wall — bonus
                        if (o.kind == ObsKind.Boss) {
                            settings.value.bossesBeat++; speak("Boss down")
                            score.value += 250; combo.value = (combo.value + 0.5f).coerceAtMost(5f)
                            beep("win")
                        }
                    } else if (abs(o.lane - carLane.value) == 1 && o.z in 0.1f..0.4f) {
                        // near miss
                        nearMissCount.value++
                        combo.value = (combo.value + 0.05f).coerceAtMost(5f)
                        score.value += 5
                        if (reactionWindowOpen.value != null) {
                            val ms = (System.currentTimeMillis() - (reactionWindowOpen.value ?: 0L)).toFloat()
                            reactions.value = reactions.value + Reaction(distance.value, ms)
                            reactionWindowOpen.value = null
                        }
                    }
                }
            }
            pickups.value.forEach { p ->
                val grab = magnetT.value > 0f && abs(p.lane - carLane.value) <= 1 && p.z in -0.2f..1.2f
                if ((p.lane == carLane.value && p.z in -0.1f..0.5f) || grab) {
                    when (p.kind) {
                        PickupKind.Coin -> { val n = (10 * (if (doubleT.value > 0f) 2 else 1)).toInt(); coinsEarned.value += n; score.value += n; beep("tick") }
                        PickupKind.Shield -> { shieldT.value = 6f; speak("Shield"); beep("power") }
                        PickupKind.SlowMo -> { slowMoT.value = 4f; speak("Slow motion"); beep("power") }
                        PickupKind.Magnet -> { magnetT.value = 6f; speak("Magnet"); beep("power") }
                        PickupKind.Double -> { doubleT.value = 8f; speak("Double points"); beep("power") }
                        PickupKind.Heart -> { hearts.value = min(hearts.value + 1, 5); speak("Health up"); beep("win") }
                    }
                    pickups.value = pickups.value.filter { it !== p }
                }
            }

            // distance score
            score.value = (distance.value.toInt() + nearMissCount.value * 8 + coinsEarned.value)
            onScore(score.value)

            // boss spawn
            if (!bossActive.value && distance.value >= nextBossDist.value) {
                val openLane = rng.nextInt(laneCount)
                obstacles.value = obstacles.value + Obs(ObsKind.Boss, 0, 25f, width = laneCount, bossOpenLane = openLane)
                bossActive.value = true
                speak("Boss truck inbound. Lane ${openLane + 1}.")
                haptic(80)
            }
            if (bossActive.value && obstacles.value.none { it.kind == ObsKind.Boss }) {
                bossActive.value = false
                nextBossDist.value += 2000f
                stage.value++
            }

            // perfect streak
            if (crashes.value.lastOrNull()?.let { distance.value - it.zoneY > 300f } != false) {
                perfectStretch.value += effSpeed * dt
            }

            delay(16)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = theme.sky.last()) {
        Box(modifier = Modifier.fillMaxSize().background(brushFor(theme))) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.value.tapZones, settings.value.leftyMirror, laneCount) {
                    detectDragGestures(onDragEnd = {}) { _, drag ->
                        val ax = drag.x; val ay = drag.y
                        val s = 12f * (1.5f - settings.value.swipeStrength)
                        if (abs(ax) > abs(ay)) {
                            if (ax > s) targetLane.value = (targetLane.value + (if (settings.value.leftyMirror) -1 else 1)).coerceIn(0, laneCount - 1)
                            else if (ax < -s) targetLane.value = (targetLane.value + (if (settings.value.leftyMirror) 1 else -1)).coerceIn(0, laneCount - 1)
                            haptic(10)
                        } else {
                            if (ay < -s && !isJumping.value) { isJumping.value = true; jumpT.value = 0f; beep("tap"); haptic(15) }
                            else if (ay > s && !isSliding.value) { isSliding.value = true; slideT.value = 0f; beep("tap"); haptic(15) }
                        }
                    }
                }
                .pointerInput(settings.value.tapZones, settings.value.leftyMirror, laneCount) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (!started.value) { started.value = true; reset(); started.value = true; return@detectTapGestures }
                            if (settings.value.tapZones) {
                                val w = size.width
                                val mirror = if (settings.value.leftyMirror) -1 else 1
                                if (offset.x < w * 0.33f) targetLane.value = (targetLane.value - 1 * mirror).coerceIn(0, laneCount - 1)
                                else if (offset.x > w * 0.66f) targetLane.value = (targetLane.value + 1 * mirror).coerceIn(0, laneCount - 1)
                                else if (!isJumping.value) { isJumping.value = true; jumpT.value = 0f }
                                beep("tap"); haptic(8)
                            }
                        },
                        onDoubleTap = { if (!isSliding.value) { isSliding.value = true; slideT.value = 0f; beep("tap") } },
                    )
                }
            ) {
                drawScene(this, theme, skin, laneCount, carLerp.value, isJumping.value, jumpT.value,
                    isSliding.value, slideT.value, obstacles.value, pickups.value, settings.value,
                    distance.value, shieldT.value, magnetT.value)
            }

            // HUD
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudPill("DIST", "${distance.value.toInt()}m", accent, settings.value.bigHud)
                    Spacer(Modifier.width(8.dp))
                    HudPill("SCORE", "${score.value}", Color.White, settings.value.bigHud)
                    Spacer(Modifier.width(8.dp))
                    HudPill("×", String.format("%.1f", combo.value), Color(0xFFFCD34D), settings.value.bigHud)
                    Spacer(Modifier.weight(1f))
                    HudPill("$", "${coinsEarned.value}", Color(0xFFFCD34D), settings.value.bigHud)
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    if (shieldT.value > 0f) Pill("SHIELD ${shieldT.value.toInt()}s", Color(0xFF38BDF8))
                    if (slowMoT.value > 0f) Pill("SLOW ${slowMoT.value.toInt()}s", Color(0xFF60A5FA))
                    if (magnetT.value > 0f) Pill("MAGNET ${magnetT.value.toInt()}s", Color(0xFFEF4444))
                    if (doubleT.value > 0f) Pill("×2 ${doubleT.value.toInt()}s", Color(0xFFFACC15))
                    if (mode == "Time") Pill("TIME ${(timeLeftMs.value / 1000f).toInt()}s", Color.White)
                    if (mode == "Stage") Pill("STAGE ${stage.value}", Color(0xFFA78BFA))
                    Spacer(Modifier.weight(1f))
                    Text("♥".repeat(hearts.value), color = Color(0xFFEF4444), fontSize = 18.sp)
                }
            }

            // Bottom controls
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(10.dp)) {
                ChipBtn("⚙") { showSettings.value = true }
                Spacer(Modifier.width(6.dp))
                ChipBtn("Shop") { showShop.value = true }
                Spacer(Modifier.width(6.dp))
                ChipBtn("Calibrate") { showCalibration.value = true }
                Spacer(Modifier.weight(1f))
                ChipBtn(if (started.value) "Restart" else "Start") {
                    started.value = false; reset(); started.value = true
                }
            }

            if (!started.value && !showAnalytics.value) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { started.value = true; reset(); started.value = true },
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LANE RUSH", color = accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("Tap halves or swipe to switch lanes", color = Color.White, fontSize = 14.sp)
                        Text("Swipe up to jump, down to slide", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to start", color = Color(0xFFFCD34D), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showSettings.value) SettingsSheet(settings, onClose = { showSettings.value = false }, onSave = ::save)
            if (showShop.value) ShopSheet(settings, onClose = { showShop.value = false }, onSave = ::save)
            if (showCalibration.value) CalibrationSheet(settings, onClose = { showCalibration.value = false }, onSave = ::save)
            if (showAnalytics.value) AnalyticsSheet(
                distance = distance.value.toInt(),
                score = score.value,
                coins = coinsEarned.value,
                nearMisses = nearMissCount.value,
                stage = stage.value,
                bossesBeat = settings.value.bossesBeat,
                crashes = crashes.value,
                reactions = reactions.value,
                laneTime = laneTime.value,
                laneCount = laneCount,
                onReplay = { showAnalytics.value = false; reset(); started.value = true },
                onClose = { showAnalytics.value = false; onGameOver() },
            )
        }
    }
}

// --------------------------- Spawning -----------------------------------

private fun spawn(rng: Random, obs: MutableState<List<Obs>>, pks: MutableState<List<Pkp>>,
                  dist: Float, lanes: Int, s: LaneRushSettings, mode: String,
                  boss: MutableState<Boolean>, nextBoss: MutableState<Float>, dt: Float, effSpeed: Float) {
    if (boss.value) return
    val density = (0.5f + dist / 1500f).coerceAtMost(2.5f)
    if (rng.nextFloat() < 0.06f * density) {
        val kinds = listOf(ObsKind.Sedan, ObsKind.Truck, ObsKind.Bus, ObsKind.Police, ObsKind.Moto,
            ObsKind.Block, ObsKind.Barrel, ObsKind.Cone, ObsKind.Drone, ObsKind.Beam, ObsKind.Wall,
            ObsKind.Slick, ObsKind.Pothole, ObsKind.Spike)
        val k = kinds[rng.nextInt(kinds.size)]
        val width = if (k == ObsKind.Bus) 2 else 1
        val lane = rng.nextInt(0, lanes - width + 1)
        // ensure passable: never spawn a row that fills all lanes
        val row = obs.value.filter { abs(it.z - 22f) < 2f }
        val coveredLanes = row.flatMap { (it.lane until (it.lane + it.width)).toList() }.toSet()
        if (coveredLanes.size + width >= lanes) return
        obs.value = obs.value + Obs(k, lane, 22f, width = width, hp = if (k == ObsKind.Elite) 2 else 1)
    }
    // pickups
    if (rng.nextFloat() < 0.04f) {
        val k = when (rng.nextInt(100)) {
            in 0..55 -> PickupKind.Coin
            in 56..68 -> PickupKind.Shield
            in 69..78 -> PickupKind.SlowMo
            in 79..88 -> PickupKind.Magnet
            in 89..96 -> PickupKind.Double
            else -> PickupKind.Heart
        }
        pks.value = pks.value + Pkp(k, rng.nextInt(lanes), 22f)
    }
}

// --------------------------- Drawing ------------------------------------

private fun brushFor(t: ThemeDef) = androidx.compose.ui.graphics.SolidColor(t.sky[1])

private fun drawScene(ds: DrawScope, theme: ThemeDef, skin: SkinDef, lanes: Int, carLerp: Float,
                      jumping: Boolean, jumpT: Float, sliding: Boolean, slideT: Float,
                      obs: List<Obs>, pks: List<Pkp>, s: LaneRushSettings,
                      distance: Float, shieldT: Float, magnetT: Float) {
    with(ds) {
        val w = size.width; val h = size.height
        // sky
        drawRect(Brush.verticalGradient(theme.sky), size = Size(w, h * 0.6f))
        // road trapezoid
        val roadTopY = h * 0.35f
        val roadTopHalf = w * 0.06f
        val roadBotHalf = w * 0.48f
        val cx = w / 2f
        val road = Path().apply {
            moveTo(cx - roadTopHalf, roadTopY); lineTo(cx + roadTopHalf, roadTopY)
            lineTo(cx + roadBotHalf, h); lineTo(cx - roadBotHalf, h); close()
        }
        drawPath(road, theme.road)

        // lane lines moving
        val laneW = (1f / lanes)
        for (i in 1 until lanes) {
            val u = i * laneW
            val x1 = cx - roadTopHalf + 2f * roadTopHalf * u
            val x2 = cx - roadBotHalf + 2f * roadBotHalf * u
            // dashed line via segments
            val dashes = if (s.batterySaver) 6 else 12
            for (d in 0 until dashes) {
                val t0 = (d.toFloat() / dashes + (distance % 40f) / 40f) % 1f
                val t1 = ((d + 0.45f) / dashes + (distance % 40f) / 40f) % 1f
                val ax = x1 + (x2 - x1) * t0; val ay = roadTopY + (h - roadTopY) * t0
                val bx = x1 + (x2 - x1) * t1; val by = roadTopY + (h - roadTopY) * t1
                drawLine(theme.line, Offset(ax, ay), Offset(bx, by), strokeWidth = 4f + 12f * t0)
            }
        }
        // edge guard rails
        drawLine(theme.accent, Offset(cx - roadTopHalf, roadTopY), Offset(cx - roadBotHalf, h), strokeWidth = 4f)
        drawLine(theme.accent, Offset(cx + roadTopHalf, roadTopY), Offset(cx + roadBotHalf, h), strokeWidth = 4f)

        // helper to project lane index into x at depth t (0..1; 0=top,1=bottom)
        fun project(lane: Int, lateral: Float = 0f, depth: Float): Pair<Float, Float> {
            val u = (lane.toFloat() + 0.5f + lateral) / lanes
            val x1 = cx - roadTopHalf + 2f * roadTopHalf * u
            val x2 = cx - roadBotHalf + 2f * roadBotHalf * u
            val t = depth.coerceIn(0f, 1f)
            val xx = x1 + (x2 - x1) * t
            val yy = roadTopY + (h - roadTopY) * t
            return xx to yy
        }

        // obstacles
        obs.sortedBy { it.z }.forEach { o ->
            val depth = (1f - o.z / 22f).coerceIn(0f, 1f)
            val (ox, oy) = project(o.lane + (o.width - 1) * 0.5f, 0f, depth)
            val sz = 16f + 80f * depth
            val color = when (o.kind) {
                ObsKind.Sedan -> Color(0xFFEF4444); ObsKind.Truck -> Color(0xFF1E3A8A)
                ObsKind.Bus -> Color(0xFFF59E0B); ObsKind.Police -> Color(0xFF1F2937)
                ObsKind.Moto -> Color(0xFF22C55E); ObsKind.Block -> Color(0xFF6B7280)
                ObsKind.Barrel -> Color(0xFFEAB308); ObsKind.Cone -> Color(0xFFFB923C)
                ObsKind.Drone -> Color(0xFF8B5CF6); ObsKind.Beam -> Color(0xFFEC4899)
                ObsKind.Wall -> Color(0xFFB91C1C); ObsKind.Boss -> Color(0xFF111827)
                ObsKind.Elite -> Color(0xFFA855F7); ObsKind.Slick -> Color(0xFF1E293B)
                ObsKind.Pothole -> Color(0xFF000000); ObsKind.Spike -> Color(0xFFFCA5A5)
            }
            when (o.kind) {
                ObsKind.Beam -> drawRect(color, Offset(ox - sz, oy - sz * 0.25f), Size(sz * 2f, sz * 0.4f))
                ObsKind.Wall -> drawRect(color, Offset(ox - sz, oy - sz * 1.5f), Size(sz * 2f, sz * 1.5f))
                ObsKind.Boss -> {
                    val widthPx = roadBotHalf * 1.7f * depth.coerceAtLeast(0.4f)
                    drawRect(color, Offset(cx - widthPx, oy - sz * 1.6f), Size(widthPx * 2f, sz * 1.6f))
                    // open lane indicator
                    val (gx, _) = project(o.bossOpenLane.toFloat(), 0f, depth)
                    drawRect(Color(0xFFFCD34D), Offset(gx - sz * 0.6f, oy - sz * 1.6f), Size(sz * 1.2f, sz * 1.6f))
                }
                ObsKind.Drone -> drawCircle(color, sz * 0.6f, Offset(ox, oy - sz * 1.2f))
                ObsKind.Cone -> {
                    val p = Path().apply {
                        moveTo(ox, oy - sz); lineTo(ox - sz * 0.6f, oy); lineTo(ox + sz * 0.6f, oy); close()
                    }
                    drawPath(p, color)
                }
                ObsKind.Pothole -> drawCircle(color, sz * 0.7f, Offset(ox, oy + sz * 0.2f))
                else -> drawRect(color, Offset(ox - sz * 0.7f, oy - sz * 1.1f), Size(sz * 1.4f, sz * 1.1f))
            }
        }

        // pickups
        pks.forEach { p ->
            val depth = (1f - p.z / 22f).coerceIn(0f, 1f)
            val (ox, oy) = project(p.lane.toFloat(), 0f, depth)
            val sz = 8f + 22f * depth
            val color = when (p.kind) {
                PickupKind.Coin -> Color(0xFFFCD34D); PickupKind.Shield -> Color(0xFF38BDF8)
                PickupKind.SlowMo -> Color(0xFF60A5FA); PickupKind.Magnet -> Color(0xFFEF4444)
                PickupKind.Double -> Color(0xFFFACC15); PickupKind.Heart -> Color(0xFFEC4899)
            }
            drawCircle(color, sz, Offset(ox, oy - sz))
            if (p.kind != PickupKind.Coin) drawCircle(Color.White.copy(alpha = 0.5f), sz * 0.4f, Offset(ox, oy - sz))
        }

        // car
        val carDepth = 0.85f
        val (carX, carYBase) = project(0, carLerp - (lanes / 2f) + 0.5f, carDepth)
        val jumpY = if (jumping) -50f * sin(jumpT.toDouble() / 0.7 * PI).toFloat() else 0f
        val carH = if (sliding) 36f else 56f
        val carW = 60f
        val carY = carYBase + jumpY
        if (s.trail) {
            for (i in 0 until 6) {
                val a = (1f - i / 6f) * 0.25f
                drawCircle(skin.accent.copy(alpha = a), carW * 0.5f - i * 4f, Offset(carX, carY + 8f + i * 6f))
            }
        }
        drawRect(skin.accent, Offset(carX - carW * 0.5f, carY - carH * 0.5f - 4f), Size(carW, carH + 8f))
        drawRect(skin.color, Offset(carX - carW * 0.4f, carY - carH * 0.5f), Size(carW * 0.8f, carH))
        drawRect(Color.White.copy(alpha = 0.6f), Offset(carX - carW * 0.3f, carY - carH * 0.35f), Size(carW * 0.6f, carH * 0.25f))
        if (shieldT > 0f) drawCircle(Color(0xFF38BDF8).copy(alpha = 0.6f), carW * 0.9f, Offset(carX, carY), style = Stroke(width = 4f))
        if (magnetT > 0f) drawCircle(Color(0xFFEF4444).copy(alpha = 0.4f), carW * 1.4f, Offset(carX, carY), style = Stroke(width = 2f))

        // top fade for theme particles
        if (theme.particles == "rain" && !s.batterySaver) {
            for (i in 0 until 40) {
                val rx = (i * 173f) % w
                val ry = ((i * 217f + (distance * 30f)) % h)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(rx, ry), Offset(rx - 6f, ry + 18f), strokeWidth = 1.5f)
            }
        } else if (theme.particles == "neon" && !s.batterySaver) {
            for (i in 0 until 20) {
                val nx = (i * 61f) % w
                val ny = (i * 89f) % (h * 0.4f)
                drawCircle(theme.accent.copy(alpha = 0.5f), 2f, Offset(nx, ny))
            }
        }
    }
}

// --------------------------- HUD bits -----------------------------------

@Composable
private fun HudPill(label: String, value: String, color: Color, big: Boolean) {
    Surface(color = Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = if (big) 12.sp else 10.sp)
            Spacer(Modifier.width(4.dp))
            Text(value, color = color, fontSize = if (big) 16.sp else 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(end = 4.dp)) {
        Text(text, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun ChipBtn(text: String, onClick: () -> Unit) {
    Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable { onClick() }) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

// --------------------------- Sheets -------------------------------------

@Composable
private fun SettingsSheet(state: MutableState<LaneRushSettings>, onClose: () -> Unit, onSave: () -> Unit) {
    val s = state.value
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onClose() }) {
        Surface(color = Color(0xFF111827), shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.92f)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).heightIn(max = 560.dp)) {
                Text("Settings", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LaneRow("Lanes", s.laneCount, listOf(3, 4, 5)) { state.value = s.copy(laneCount = it); onSave() }
                ToggleRow("Sound", s.sound) { state.value = s.copy(sound = it); onSave() }
                ToggleRow("Music", s.music) { state.value = s.copy(music = it); onSave() }
                ToggleRow("Voice cues", s.voiceCalls) { state.value = s.copy(voiceCalls = it); onSave() }
                ToggleRow("Haptics", s.haptics) { state.value = s.copy(haptics = it); onSave() }
                ToggleRow("Tilt steering", s.tilt) { state.value = s.copy(tilt = it); onSave() }
                ToggleRow("Tap halves to steer", s.tapZones) { state.value = s.copy(tapZones = it); onSave() }
                ToggleRow("One-handed mode", s.oneHanded) { state.value = s.copy(oneHanded = it); onSave() }
                ToggleRow("Lefty mirror", s.leftyMirror) { state.value = s.copy(leftyMirror = it); onSave() }
                ToggleRow("Reduced motion", s.reducedMotion) { state.value = s.copy(reducedMotion = it); onSave() }
                ToggleRow("High contrast", s.highContrast) { state.value = s.copy(highContrast = it); onSave() }
                ToggleRow("Battery saver", s.batterySaver) { state.value = s.copy(batterySaver = it); onSave() }
                ToggleRow("Big HUD", s.bigHud) { state.value = s.copy(bigHud = it); onSave() }
                ToggleRow("Trail", s.trail) { state.value = s.copy(trail = it); onSave() }
                ToggleRow("Ghost replay", s.ghost) { state.value = s.copy(ghost = it); onSave() }
                ToggleRow("Daily seed run", s.dailySeed) { state.value = s.copy(dailySeed = it); onSave() }
                Spacer(Modifier.height(8.dp))
                Text("Colorblind: ${s.colorblind}", color = Color.White, fontSize = 12.sp)
                Row {
                    listOf("none", "deutan", "protan", "tritan").forEach { mode ->
                        ChipBtn(mode) { state.value = s.copy(colorblind = mode); onSave() }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Theme: ${s.theme}", color = Color.White, fontSize = 12.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ChipBtn("auto") { state.value = s.copy(theme = "auto"); onSave() }
                    Spacer(Modifier.width(4.dp))
                    THEMES.forEach { t ->
                        if (t.id in s.ownedThemes) {
                            ChipBtn(t.label) { state.value = s.copy(theme = t.id); onSave() }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tilt sensitivity: ${(s.tiltSensitivity * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                Slider(value = s.tiltSensitivity, onValueChange = { state.value = s.copy(tiltSensitivity = it); onSave() })
                Text("Swipe strength: ${(s.swipeStrength * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                Slider(value = s.swipeStrength, onValueChange = { state.value = s.copy(swipeStrength = it); onSave() })
                Spacer(Modifier.height(8.dp))
                Row { ChipBtn("Close") { onClose() } }
            }
        }
    }
}

@Composable
private fun LaneRow(label: String, current: Int, options: List<Int>, onPick: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        options.forEach { v ->
            Surface(color = if (v == current) Color(0xFFFB923C) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(horizontal = 2.dp).clickable { onPick(v) }) {
                Text("$v", color = if (v == current) Color.Black else Color.White, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Checkbox(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ShopSheet(state: MutableState<LaneRushSettings>, onClose: () -> Unit, onSave: () -> Unit) {
    val s = state.value
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onClose() }) {
        Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.92f)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).heightIn(max = 560.dp)) {
                Text("Garage", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Coins: ${GamesStore.getCoins() + s.totalCoins}", color = Color(0xFFFCD34D), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Vehicles", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                SKINS.forEach { sk ->
                    val owned = sk.id in s.unlockedSkins
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(sk.color))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sk.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (owned) (if (s.skin == sk.id) "Equipped" else "Owned") else "${sk.price} coins",
                                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                        if (!owned) {
                            ChipBtn("Buy") {
                                val total = GamesStore.getCoins() + s.totalCoins
                                if (total >= sk.price) {
                                    s.unlockedSkins.add(sk.id)
                                    s.totalCoins = max(0, s.totalCoins - sk.price)
                                    state.value = s.copy(skin = sk.id, totalCoins = s.totalCoins); onSave()
                                }
                            }
                        } else if (s.skin != sk.id) {
                            ChipBtn("Use") { state.value = s.copy(skin = sk.id); onSave() }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Themes", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                THEMES.forEach { t ->
                    val owned = t.id in s.ownedThemes
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(t.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(t.label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (!owned) ChipBtn("${t.price}c") {
                            val total = GamesStore.getCoins() + s.totalCoins
                            if (total >= t.price) { s.ownedThemes.add(t.id); s.totalCoins = max(0, s.totalCoins - t.price); state.value = s.copy(); onSave() }
                        } else Text("Owned", color = Color(0xFF34D399), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row { ChipBtn("Close") { onClose() } }
            }
        }
    }
}

@Composable
private fun CalibrationSheet(state: MutableState<LaneRushSettings>, onClose: () -> Unit, onSave: () -> Unit) {
    val s = state.value
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable { onClose() }) {
        Surface(color = Color(0xFF1F2937), shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.9f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Calibration", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Adjust controls so steering feels natural. Try a quick test below.",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("Tilt sensitivity: ${(s.tiltSensitivity * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                Slider(value = s.tiltSensitivity, onValueChange = { state.value = s.copy(tiltSensitivity = it); onSave() })
                Text("Swipe strength: ${(s.swipeStrength * 100).toInt()}%", color = Color.White, fontSize = 12.sp)
                Slider(value = s.swipeStrength, onValueChange = { state.value = s.copy(swipeStrength = it); onSave() })
                Spacer(Modifier.height(8.dp))
                Text("Tip: lower swipe strength means a small swipe already changes lanes.",
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row { ChipBtn("Done") { onClose() } }
            }
        }
    }
}

@Composable
private fun AnalyticsSheet(distance: Int, score: Int, coins: Int, nearMisses: Int, stage: Int, bossesBeat: Int,
                           crashes: List<Crash>, reactions: List<Reaction>, laneTime: FloatArray, laneCount: Int,
                           onReplay: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Surface(color = Color(0xFF111827), shape = RoundedCornerShape(20.dp),
            modifier = Modifier.align(Alignment.Center).padding(16.dp).fillMaxWidth(0.95f)) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp).heightIn(max = 600.dp)) {
                Text("Run Report", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row {
                    StatBlock("Distance", "${distance}m")
                    StatBlock("Score", "$score")
                    StatBlock("Coins", "$coins")
                }
                Row {
                    StatBlock("Near misses", "$nearMisses")
                    StatBlock("Stage", "$stage")
                    StatBlock("Bosses", "$bossesBeat")
                }
                Spacer(Modifier.height(8.dp))
                Text("Lane heatmap", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    val maxT = (laneTime.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
                    val w = size.width; val h = size.height
                    val cellW = w / laneCount.toFloat()
                    laneTime.forEachIndexed { i, t ->
                        val u = (t / maxT).coerceIn(0f, 1f)
                        val color = Color(0xFFFB923C).copy(alpha = 0.2f + 0.8f * u)
                        drawRect(color, Offset(i * cellW + 4f, h * (1f - u)), Size(cellW - 8f, h * u))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Reaction times (ms)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                    val w = size.width; val h = size.height
                    if (reactions.isNotEmpty()) {
                        val maxMs = reactions.maxOf { it.ms }.coerceAtLeast(50f)
                        reactions.forEachIndexed { i, r ->
                            val x = i * (w / reactions.size.coerceAtLeast(1))
                            val y = h - (r.ms / maxMs) * h
                            drawCircle(Color(0xFF60A5FA), 4f, Offset(x + 4f, y))
                            if (i > 0) {
                                val px = (i - 1) * (w / reactions.size.coerceAtLeast(1))
                                val py = h - (reactions[i - 1].ms / maxMs) * h
                                drawLine(Color(0xFF60A5FA).copy(alpha = 0.6f), Offset(px + 4f, py), Offset(x + 4f, y), strokeWidth = 2f)
                            }
                        }
                    }
                    drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
                }
                Spacer(Modifier.height(8.dp))
                Text("Crashes (${crashes.size})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                crashes.takeLast(8).forEach { c ->
                    Text("${c.zoneY.toInt()}m · lane ${(c.zoneX.toInt() + 1)} · ${c.obstacle.lowercase()}",
                        color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    ChipBtn("Replay") { onReplay() }
                    Spacer(Modifier.width(8.dp))
                    ChipBtn("Close") { onClose() }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatBlock(label: String, value: String) {
    Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(10.dp),
        modifier = Modifier.weight(1f).padding(2.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

