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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.SnakeSettingsJsonPreference
import com.ismartcoding.plain.ui.page.home.games.GamesStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---- model ----

private const val COLS = 18
private const val ROWS = 27

private enum class Movement { Grid, Smooth }
private enum class Theme { Cyber, Zen, Toxic, Void }
private enum class Skin { Neon, Crystal, Lava, Ice, Ghost, Pixel, Golden }
private enum class Trail { None, Sparkle, Fire, Smoke, Rainbow }
private enum class FoodKind { Normal, Golden, Poison, Timed, Magnetic }
private enum class PowerKind { Phase, Magnet, Shrink, Slow, Double }

private data class SnakeSettings(
    var movement: Movement = Movement.Grid,
    var baseSpeed: String = "normal", // slow / normal / fast
    var swipeSens: Float = 1.0f,
    var deadZone: Boolean = false,
    var wrap: Boolean = false,
    var tilt: Boolean = false,
    var showDpad: Boolean = true,
    var leftHanded: Boolean = false,
    var haptics: Boolean = true,
    var layeredMusic: Boolean = true,
    var swipeTick: Boolean = false,
    var voiceHints: Boolean = false,
    var reducedMotion: Boolean = false,
    var highContrast: Boolean = false,
    var batterySaver: Boolean = false,
    var assistPredict: Boolean = false,
    var colorblind: String = "off",
    var theme: Theme = Theme.Cyber,
    var skin: Skin = Skin.Neon,
    var trail: Trail = Trail.Sparkle,
    var unlockSkins: MutableSet<Skin> = mutableSetOf(Skin.Neon),
    var unlockThemes: MutableSet<Theme> = mutableSetOf(Theme.Cyber),
    var mmr: Float = 50f,
    var goldenTotal: Int = 0,
    var dailyDate: Int = 0,
    var dailyBest: Int = 0,
    var ghostMoves: List<Pair<Int, Int>> = emptyList(),
    var ghostScore: Int = 0,
    var ghostDifficulty: String = "",
)

private fun loadSnakeJson(json: String): SnakeSettings {
    val s = SnakeSettings()
    if (json.isBlank() || json == "{}") return s
    try {
        val o = JSONObject(json)
        s.movement = if (o.optString("movement", "grid") == "smooth") Movement.Smooth else Movement.Grid
        s.baseSpeed = o.optString("baseSpeed", "normal")
        s.swipeSens = o.optDouble("swipeSens", 1.0).toFloat()
        s.deadZone = o.optBoolean("deadZone", false)
        s.wrap = o.optBoolean("wrap", false)
        s.tilt = o.optBoolean("tilt", false)
        s.showDpad = o.optBoolean("showDpad", true)
        s.leftHanded = o.optBoolean("leftHanded", false)
        s.haptics = o.optBoolean("haptics", true)
        s.layeredMusic = o.optBoolean("layeredMusic", true)
        s.swipeTick = o.optBoolean("swipeTick", false)
        s.voiceHints = o.optBoolean("voiceHints", false)
        s.reducedMotion = o.optBoolean("reducedMotion", false)
        s.highContrast = o.optBoolean("highContrast", false)
        s.batterySaver = o.optBoolean("batterySaver", false)
        s.assistPredict = o.optBoolean("assistPredict", false)
        s.colorblind = o.optString("colorblind", "off")
        s.theme = runCatching { Theme.valueOf(o.optString("theme", "Cyber")) }.getOrDefault(Theme.Cyber)
        s.skin = runCatching { Skin.valueOf(o.optString("skin", "Neon")) }.getOrDefault(Skin.Neon)
        s.trail = runCatching { Trail.valueOf(o.optString("trail", "Sparkle")) }.getOrDefault(Trail.Sparkle)
        s.mmr = o.optDouble("mmr", 50.0).toFloat()
        s.goldenTotal = o.optInt("goldenTotal", 0)
        s.dailyDate = o.optInt("dailyDate", 0)
        s.dailyBest = o.optInt("dailyBest", 0)
        s.ghostScore = o.optInt("ghostScore", 0)
        s.ghostDifficulty = o.optString("ghostDifficulty", "")
        val skinsArr = o.optJSONArray("unlockSkins")
        if (skinsArr != null) {
            s.unlockSkins.clear()
            for (i in 0 until skinsArr.length()) runCatching { s.unlockSkins.add(Skin.valueOf(skinsArr.getString(i))) }
            if (s.unlockSkins.isEmpty()) s.unlockSkins.add(Skin.Neon)
        }
        val themesArr = o.optJSONArray("unlockThemes")
        if (themesArr != null) {
            s.unlockThemes.clear()
            for (i in 0 until themesArr.length()) runCatching { s.unlockThemes.add(Theme.valueOf(themesArr.getString(i))) }
            if (s.unlockThemes.isEmpty()) s.unlockThemes.add(Theme.Cyber)
        }
        val gh = o.optJSONArray("ghostMoves")
        if (gh != null) {
            val list = mutableListOf<Pair<Int, Int>>()
            for (i in 0 until gh.length()) {
                val mv = gh.getJSONArray(i)
                list.add(mv.getInt(0) to mv.getInt(1))
            }
            s.ghostMoves = list
        }
    } catch (_: Exception) {}
    return s
}

private fun saveSnakeJson(s: SnakeSettings): String {
    val o = JSONObject()
    o.put("movement", if (s.movement == Movement.Smooth) "smooth" else "grid")
    o.put("baseSpeed", s.baseSpeed)
    o.put("swipeSens", s.swipeSens.toDouble())
    o.put("deadZone", s.deadZone); o.put("wrap", s.wrap); o.put("tilt", s.tilt)
    o.put("showDpad", s.showDpad); o.put("leftHanded", s.leftHanded)
    o.put("haptics", s.haptics); o.put("layeredMusic", s.layeredMusic)
    o.put("swipeTick", s.swipeTick); o.put("voiceHints", s.voiceHints)
    o.put("reducedMotion", s.reducedMotion); o.put("highContrast", s.highContrast)
    o.put("batterySaver", s.batterySaver); o.put("assistPredict", s.assistPredict)
    o.put("colorblind", s.colorblind); o.put("theme", s.theme.name)
    o.put("skin", s.skin.name); o.put("trail", s.trail.name)
    o.put("mmr", s.mmr.toDouble()); o.put("goldenTotal", s.goldenTotal)
    o.put("dailyDate", s.dailyDate); o.put("dailyBest", s.dailyBest)
    o.put("ghostScore", s.ghostScore); o.put("ghostDifficulty", s.ghostDifficulty)
    val sa = JSONArray(); s.unlockSkins.forEach { sa.put(it.name) }; o.put("unlockSkins", sa)
    val ta = JSONArray(); s.unlockThemes.forEach { ta.put(it.name) }; o.put("unlockThemes", ta)
    val ga = JSONArray()
    s.ghostMoves.forEach { p -> val a = JSONArray(); a.put(p.first); a.put(p.second); ga.put(a) }
    o.put("ghostMoves", ga)
    return o.toString()
}

private fun applySnakeColorblind(c: Color, mode: String): Color {
    if (mode == "off") return c
    val r = c.red; val g = c.green; val b = c.blue
    val (nr, ng, nb) = when (mode) {
        "protanopia" -> Triple(0.567f * r + 0.433f * g, 0.558f * r + 0.442f * g, 0.242f * g + 0.758f * b)
        "deuteranopia" -> Triple(0.625f * r + 0.375f * g, 0.7f * r + 0.3f * g, 0.3f * g + 0.7f * b)
        "tritanopia" -> Triple(0.95f * r + 0.05f * g, 0.433f * g + 0.567f * b, 0.475f * g + 0.525f * b)
        else -> Triple(r, g, b)
    }
    return Color(nr.coerceIn(0f, 1f), ng.coerceIn(0f, 1f), nb.coerceIn(0f, 1f), c.alpha)
}

private fun snakeVibrate(ctx: Context, ms: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    } catch (_: Throwable) {}
}

private fun snakeBeep(tone: Int = ToneGenerator.TONE_PROP_BEEP, dur: Int = 80) {
    try { ToneGenerator(AudioManager.STREAM_MUSIC, 35).startTone(tone, dur) } catch (_: Throwable) {}
}

private data class SnakeCell(var x: Int, var y: Int)
private data class SnakeFood(var x: Int, var y: Int, var kind: FoodKind, var spawnFrame: Int)
private data class SnakePower(var x: Int, var y: Int, var kind: PowerKind)
private data class SnakeMovingObs(var x: Float, var y: Float, var dx: Float)
private data class SnakeParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Int, val maxLife: Int, val color: Color, val size: Float,
)
private data class SnakePopup(var x: Float, var y: Float, val text: String, var life: Int, val color: Color)
private data class ActivePower(val id: Int, val kind: PowerKind, var timeLeft: Int)
private data class SmoothSeg(val x: Float, val y: Float, val t: Int)

private fun todayDateNumber(): Int {
    val cal = java.util.Calendar.getInstance()
    return cal.get(java.util.Calendar.YEAR) * 10000 +
        (cal.get(java.util.Calendar.MONTH) + 1) * 100 +
        cal.get(java.util.Calendar.DAY_OF_MONTH)
}

@Composable
fun SnakeGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings = remember { mutableStateOf(SnakeSettings()) }
    LaunchedEffect(Unit) {
        settings.value = loadSnakeJson(SnakeSettingsJsonPreference.getAsync(context))
    }
    fun saveSettings() { scope.launch { SnakeSettingsJsonPreference.putAsync(context, saveSnakeJson(settings.value)) } }

    // canvas size in pixels
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val cellPx by remember { derivedStateOf { if (w == 0f || h == 0f) 1f else min(w / COLS, h / ROWS) } }
    val offX by remember { derivedStateOf { (w - cellPx * COLS) / 2f } }
    val offY by remember { derivedStateOf { (h - cellPx * ROWS) / 2f } }

    // game state
    val snakeGrid = remember { mutableStateListOf<SnakeCell>() }
    val smoothBody = remember { mutableStateListOf<SmoothSeg>() }
    var smoothPos by remember { mutableStateOf(Offset(0f, 0f)) }
    var smoothVel by remember { mutableStateOf(Offset(1f, 0f)) }
    var dir by remember { mutableStateOf(Pair(1, 0)) }
    var pendingDir by remember { mutableStateOf(Pair(1, 0)) }
    var food by remember { mutableStateOf(SnakeFood(5, 5, FoodKind.Normal, 0)) }
    var powerUp by remember { mutableStateOf<SnakePower?>(null) }
    val walls = remember { mutableStateListOf<SnakeCell>() }
    val movingObs = remember { mutableStateListOf<SnakeMovingObs>() }
    val particles = remember { mutableStateListOf<SnakeParticle>() }
    val popups = remember { mutableStateListOf<SnakePopup>() }
    val activePowers = remember { mutableStateListOf<ActivePower>() }
    var nextPowerId by remember { mutableStateOf(1) }

    var score by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(1) }
    var snakeLen by remember { mutableStateOf(3) }
    var alive by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var frameIdx by remember { mutableStateOf(0) }
    var runStartMs by remember { mutableStateOf(0L) }
    var timeLeftMs by remember { mutableStateOf(if (mode.equals("Time", true)) 60_000L else 0L) }
    var stepIntervalMs by remember { mutableStateOf(110L) }
    var poisonInvertMs by remember { mutableStateOf(0) }
    var comboRecharge by remember { mutableStateOf(0) }
    var goldenEatsInRun by remember { mutableStateOf(0) }
    var poisonEatsInRun by remember { mutableStateOf(0) }
    var phaseUsedInRun by remember { mutableStateOf(0) }
    var arenaShrink by remember { mutableStateOf(0f) }
    var bgT by remember { mutableStateOf(0f) }
    var shakeT by remember { mutableStateOf(0) }
    var seedRng by remember { mutableStateOf(Random(System.nanoTime())) }
    var dailyActive by remember { mutableStateOf(false) }
    var crashInfo by remember { mutableStateOf("") }
    val pathHeat = remember { IntArray(COLS * ROWS) }
    val turnLog = remember { mutableStateListOf<Int>() }
    val recordedMoves = remember { mutableStateListOf<Pair<Int, Int>>() }
    val newUnlocks = remember { mutableStateListOf<String>() }
    var settingsOpen by remember { mutableStateOf(false) }
    var calOpen by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var deathSuggestion by remember { mutableStateOf("") }
    var hasGhost by remember { mutableStateOf(false) }
    var undoSnapshot by remember { mutableStateOf<Triple<List<SnakeCell>, Pair<Int, Int>, SnakeFood>?>(null) }

    // Recompute step speed
    fun recomputeSpeed() {
        val map = when (settings.value.baseSpeed) {
            "slow" -> mapOf("Easy" to 180L, "Medium" to 150L, "Hard" to 130L, "Insane" to 110L)
            "fast" -> mapOf("Easy" to 105L, "Medium" to 85L, "Hard" to 68L, "Insane" to 54L)
            else -> mapOf("Easy" to 140L, "Medium" to 110L, "Hard" to 90L, "Insane" to 72L)
        }
        var s = (map[difficulty] ?: 110L).toFloat()
        val skew = (settings.value.mmr - 50f) / 100f
        s *= (1f - skew * 0.18f)
        if (activePowers.any { it.kind == PowerKind.Slow }) s *= 1.6f
        stepIntervalMs = s.toLong().coerceIn(40L, 260L)
    }

    fun isFreeCell(x: Int, y: Int): Boolean {
        if (x < 0 || x >= COLS || y < 0 || y >= ROWS) return false
        if (snakeGrid.any { it.x == x && it.y == y }) return false
        if (walls.any { it.x == x && it.y == y }) return false
        return true
    }

    fun placeFood() {
        val r = seedRng.nextFloat()
        val kind: FoodKind = when {
            settings.value.mmr > 70f && r < 0.18f -> FoodKind.Poison
            r < 0.10f -> FoodKind.Golden
            r < 0.16f -> FoodKind.Timed
            r < 0.22f -> FoodKind.Magnetic
            else -> FoodKind.Normal
        }
        for (i in 0 until 200) {
            val fx = seedRng.nextInt(COLS); val fy = seedRng.nextInt(ROWS)
            if (isFreeCell(fx, fy)) { food = SnakeFood(fx, fy, kind, frameIdx); return }
        }
    }

    fun buildMaze() {
        walls.clear(); movingObs.clear()
        if (!mode.equals("Challenge", true)) return
        val n = 6 + seedRng.nextInt(6)
        for (i in 0 until n) {
            val w = SnakeCell(1 + seedRng.nextInt(COLS - 2), 1 + seedRng.nextInt(ROWS - 2))
            val head = snakeGrid.firstOrNull() ?: continue
            if (abs(w.x - head.x) < 2 && abs(w.y - head.y) < 2) continue
            walls.add(w)
        }
        if (settings.value.mmr > 60f) {
            val m = 1 + seedRng.nextInt(3)
            for (i in 0 until m) {
                movingObs.add(SnakeMovingObs(2f + seedRng.nextInt(COLS - 4), 2f + seedRng.nextInt(ROWS - 4), if (seedRng.nextBoolean()) 1f else -1f))
            }
        }
    }

    fun reset() {
        // Determine seed
        val today = todayDateNumber()
        if (mode.equals("Challenge", true) || seedRng.nextFloat() < 0.33f) {
            seedRng = Random(today.toLong()); dailyActive = true
        } else { seedRng = Random(System.nanoTime()); dailyActive = false }
        snakeGrid.clear(); smoothBody.clear()
        snakeGrid.add(SnakeCell(COLS / 2, ROWS / 2))
        snakeGrid.add(SnakeCell(COLS / 2 - 1, ROWS / 2))
        snakeGrid.add(SnakeCell(COLS / 2 - 2, ROWS / 2))
        smoothPos = Offset(snakeGrid[0].x * cellPx + cellPx / 2 + offX, snakeGrid[0].y * cellPx + cellPx / 2 + offY)
        smoothVel = Offset(1f, 0f)
        for (i in 0 until 3) smoothBody.add(SmoothSeg(smoothPos.x - i * cellPx, smoothPos.y, -i))
        dir = 1 to 0; pendingDir = 1 to 0
        particles.clear(); popups.clear(); activePowers.clear()
        score = 0; combo = 1; snakeLen = 3
        alive = true; started = false; frameIdx = 0
        timeLeftMs = if (mode.equals("Time", true)) 60_000L else 0L
        runStartMs = System.currentTimeMillis()
        poisonInvertMs = 0; comboRecharge = 0
        goldenEatsInRun = 0; poisonEatsInRun = 0; phaseUsedInRun = 0
        arenaShrink = 0f; bgT = 0f; shakeT = 0
        for (i in pathHeat.indices) pathHeat[i] = 0
        turnLog.clear(); recordedMoves.clear()
        newUnlocks.clear(); undoSnapshot = null; powerUp = null
        crashInfo = ""
        showAnalytics = false
        buildMaze()
        placeFood()
        recomputeSpeed()
        hasGhost = settings.value.ghostMoves.isNotEmpty() && settings.value.ghostDifficulty == difficulty
        onScore(0)
    }

    LaunchedEffect(cellPx) {
        if (snakeGrid.isEmpty() && cellPx > 1f) reset()
    }

    fun setDir(x: Int, y: Int) {
        if (!alive) return
        if (snakeGrid.size > 1 && dir.first == -x && dir.second == -y) return
        var nx = x; var ny = y
        if (poisonInvertMs > 0) { nx = -nx; ny = -ny }
        pendingDir = nx to ny
        if (!started) started = true
        if (settings.value.swipeTick) snakeBeep(ToneGenerator.TONE_DTMF_1, 30)
    }

    fun applyPower(kind: PowerKind) {
        snakeBeep(ToneGenerator.TONE_PROP_PROMPT, 90)
        if (settings.value.haptics) snakeVibrate(context, 35)
        var t = 5000
        when (kind) {
            PowerKind.Slow -> t = 6000
            PowerKind.Double -> t = 8000
            PowerKind.Magnet -> t = 6000
            PowerKind.Phase -> { t = 5000; phaseUsedInRun++ }
            PowerKind.Shrink -> {
                val target = max(3, snakeGrid.size / 2)
                while (snakeGrid.size > target) snakeGrid.removeAt(snakeGrid.size - 1)
                while (smoothBody.size > target * 4) smoothBody.removeAt(smoothBody.size - 1)
                snakeLen = snakeGrid.size; t = 1500
                popups.add(SnakePopup(snakeGrid[0].x * cellPx + offX, snakeGrid[0].y * cellPx + offY, "✂ shrink", 50, Color(0xFF22C55E)))
            }
        }
        activePowers.add(ActivePower(nextPowerId++, kind, t))
        recomputeSpeed()
    }

    fun foodColor(k: FoodKind): Color = when (k) {
        FoodKind.Golden -> Color(0xFFFACC15)
        FoodKind.Poison -> Color(0xFFA855F7)
        FoodKind.Timed -> Color(0xFF38BDF8)
        FoodKind.Magnetic -> Color(0xFFF472B6)
        FoodKind.Normal -> Color(0xFFEC4899)
    }

    fun spawnEatBurst(f: SnakeFood) {
        if (settings.value.reducedMotion) return
        val cx = f.x * cellPx + cellPx / 2 + offX
        val cy = f.y * cellPx + cellPx / 2 + offY
        for (i in 0 until 14) {
            val a = (i / 14f) * (PI * 2).toFloat()
            particles.add(SnakeParticle(cx, cy, cos(a) * 1.6f, sin(a) * 1.6f, 24, 24, foodColor(f.kind), 2.4f))
        }
    }

    fun eatFood(f: SnakeFood) {
        var pts = 10
        when (f.kind) {
            FoodKind.Golden -> {
                pts = 30; goldenEatsInRun++; comboRecharge = 0
                popups.add(SnakePopup(f.x * cellPx + offX, f.y * cellPx + offY, "+30 GOLD", 50, Color(0xFFFACC15)))
            }
            FoodKind.Poison -> {
                pts = -5; poisonEatsInRun++; poisonInvertMs = 4000
                popups.add(SnakePopup(f.x * cellPx + offX, f.y * cellPx + offY, "☠ INVERT", 50, Color(0xFFA855F7)))
                snakeBeep(ToneGenerator.TONE_CDMA_PIP, 200)
            }
            FoodKind.Timed -> {
                pts = 15; if (mode.equals("Time", true)) timeLeftMs += 5000
                popups.add(SnakePopup(f.x * cellPx + offX, f.y * cellPx + offY, "+15 TIMED", 50, Color(0xFF38BDF8)))
            }
            FoodKind.Magnetic -> {
                pts = 20; applyPower(PowerKind.Magnet)
                popups.add(SnakePopup(f.x * cellPx + offX, f.y * cellPx + offY, "🧲 MAGNET", 50, Color(0xFFF472B6)))
            }
            FoodKind.Normal -> {
                popups.add(SnakePopup(f.x * cellPx + offX, f.y * cellPx + offY, "+10", 50, Color(0xFF10B981)))
            }
        }
        if (f.kind != FoodKind.Poison) {
            if (comboRecharge < 3000) combo = min(5, combo + 1) else combo = 1
            comboRecharge = 0
        } else combo = 1
        pts *= combo
        score = max(0, score + pts)
        onScore(score)
        snakeBeep(if (pts > 0) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_CDMA_LOW_L, 60)
        if (settings.value.haptics) snakeVibrate(context, 12)
        spawnEatBurst(f)
        if (f.kind == FoodKind.Poison) {
            if (snakeGrid.size > 4) snakeGrid.removeAt(snakeGrid.size - 1)
            if (smoothBody.size > 16) smoothBody.removeAt(smoothBody.size - 1)
        } else {
            val grow = if (f.kind == FoodKind.Golden) 3 else 1
            val tail = snakeGrid.last()
            for (g in 0 until grow) snakeGrid.add(SnakeCell(tail.x, tail.y))
            val sb = smoothBody.lastOrNull()
            if (sb != null) for (g in 0 until grow) smoothBody.add(SmoothSeg(sb.x, sb.y, sb.t - 1))
        }
        snakeLen = snakeGrid.size
        placeFood()
    }

    fun snapshotForUndo() {
        undoSnapshot = Triple(snakeGrid.map { SnakeCell(it.x, it.y) }, dir.copy(), food.copy())
    }
    fun undoMove() {
        val u = undoSnapshot ?: return
        snakeGrid.clear(); u.first.forEach { snakeGrid.add(SnakeCell(it.x, it.y)) }
        dir = u.second; pendingDir = u.second; food = u.third
        snakeLen = snakeGrid.size; undoSnapshot = null
        if (settings.value.haptics) snakeVibrate(context, 20)
    }

    fun die() {
        if (!alive) return
        alive = false
        snakeBeep(ToneGenerator.TONE_CDMA_ABBR_ALERT, 200)
        if (settings.value.haptics) snakeVibrate(context, 80)
        if (!settings.value.reducedMotion) {
            val head = if (settings.value.movement == Movement.Grid)
                Offset(snakeGrid[0].x * cellPx + cellPx / 2 + offX, snakeGrid[0].y * cellPx + cellPx / 2 + offY) else smoothPos
            for (i in 0 until 36) {
                val a = seedRng.nextFloat() * (PI * 2).toFloat()
                val v = 1f + seedRng.nextFloat() * 4f
                particles.add(SnakeParticle(head.x, head.y, cos(a) * v, sin(a) * v, 60, 60, accent, 2.6f))
            }
            shakeT = 18
        }
        // MMR adjust
        val s = settings.value
        if (score < 50) s.mmr = max(0f, s.mmr - 2f)
        else if (score > 200) s.mmr = min(100f, s.mmr + 4f)
        else s.mmr = (s.mmr + (if (score > 100) 1f else -1f)).coerceIn(0f, 100f)
        // Unlocks
        val elapsedSec = (System.currentTimeMillis() - runStartMs) / 1000
        val ann = mutableListOf<String>()
        if (Skin.Crystal !in s.unlockSkins && snakeLen >= 20) { s.unlockSkins.add(Skin.Crystal); ann.add("Crystal skin") }
        if (Skin.Lava !in s.unlockSkins && score >= 200) { s.unlockSkins.add(Skin.Lava); ann.add("Lava skin") }
        if (Skin.Ice !in s.unlockSkins && elapsedSec >= 90) { s.unlockSkins.add(Skin.Ice); ann.add("Ice skin") }
        if (Skin.Ghost !in s.unlockSkins && phaseUsedInRun >= 3) { s.unlockSkins.add(Skin.Ghost); ann.add("Ghost skin") }
        s.goldenTotal += goldenEatsInRun
        if (Skin.Golden !in s.unlockSkins && s.goldenTotal >= 20) { s.unlockSkins.add(Skin.Golden); ann.add("Golden skin") }
        val plays = GamesStore.record("snake").plays + 1
        if (Skin.Pixel !in s.unlockSkins && plays >= 10) { s.unlockSkins.add(Skin.Pixel); ann.add("Pixel skin") }
        if (Theme.Zen !in s.unlockThemes && plays >= 5) { s.unlockThemes.add(Theme.Zen); ann.add("Zen Garden theme") }
        if (Theme.Toxic !in s.unlockThemes && poisonEatsInRun >= 5) { s.unlockThemes.add(Theme.Toxic); ann.add("Toxic Waste theme") }
        if (Theme.Void !in s.unlockThemes && snakeLen >= 30) { s.unlockThemes.add(Theme.Void); ann.add("Void theme") }
        // ghost
        val prevBest = GamesStore.record("snake").best
        if (score >= prevBest && score > 0) {
            s.ghostMoves = recordedMoves.take(3000).toList()
            s.ghostScore = score
            s.ghostDifficulty = difficulty
        }
        // daily best
        if (dailyActive) {
            val today = todayDateNumber()
            if (s.dailyDate != today) { s.dailyDate = today; s.dailyBest = 0 }
            if (score > s.dailyBest) s.dailyBest = score
        }
        if (ann.isNotEmpty()) { newUnlocks.clear(); newUnlocks.addAll(ann) }
        settings.value = s
        saveSettings()
        // suggestion
        val wallEnd = crashInfo.startsWith("wall")
        val tailEnd = crashInfo.startsWith("tail")
        val obs = crashInfo.startsWith("obstacle") || crashInfo.startsWith("moving")
        val lateTurns = turnLog.count { it > frameIdx - 30 }
        deathSuggestion = when {
            tailEnd && snakeLen > 12 -> "You curled into your own tail. When long, hug the walls and uncoil into the centre slowly."
            wallEnd -> "You hit a wall. Try wrap-around in settings, or swipe a hair earlier."
            obs -> "You hit an obstacle. Pre-plan your route through the maze and pause when you spot a corridor."
            lateTurns >= 4 -> "A flurry of late turns at the end. Slow down a notch (Settings → Base speed)."
            else -> "Solid run. Keep food collection in straight lines to extend your combo multiplier."
        }
    }

    fun stepGrid() {
        if (!alive || !started) return
        snapshotForUndo()
        if (dir != pendingDir) turnLog.add(frameIdx)
        dir = pendingDir
        recordedMoves.add(dir.first to dir.second)
        // magnet
        if (activePowers.any { it.kind == PowerKind.Magnet }) {
            val head = snakeGrid[0]
            val dx = head.x - food.x; val dy = head.y - food.y
            if (abs(dx) <= 4 && abs(dy) <= 4 && (dx != 0 || dy != 0)) {
                food = food.copy(x = food.x + dx.compareTo(0), y = food.y + dy.compareTo(0))
            }
        }
        if (food.kind == FoodKind.Timed && frameIdx - food.spawnFrame > 60 * 5) placeFood()

        var nx = snakeGrid[0].x + dir.first
        var ny = snakeGrid[0].y + dir.second
        if (settings.value.wrap) {
            if (nx < 0) nx = COLS - 1; if (nx >= COLS) nx = 0
            if (ny < 0) ny = ROWS - 1; if (ny >= ROWS) ny = 0
        } else if (nx < 0 || nx >= COLS || ny < 0 || ny >= ROWS) {
            crashInfo = "wall @ frame $frameIdx"; die(); return
        }
        if (activePowers.none { it.kind == PowerKind.Phase }) {
            val tailIdx = snakeGrid.indexOfFirst { it.x == nx && it.y == ny }
            if (tailIdx >= 0) { crashInfo = "tail seg $tailIdx @ frame $frameIdx"; die(); return }
        }
        if (walls.any { it.x == nx && it.y == ny }) { crashInfo = "obstacle @ frame $frameIdx"; die(); return }
        if (movingObs.any { it.x.roundToInt() == nx && it.y.roundToInt() == ny }) { crashInfo = "moving obs @ frame $frameIdx"; die(); return }

        snakeGrid.add(0, SnakeCell(nx, ny))
        pathHeat[ny * COLS + nx] = pathHeat[ny * COLS + nx] + 1
        if (nx == food.x && ny == food.y) eatFood(food) else snakeGrid.removeAt(snakeGrid.size - 1)
        // power-up spawn
        if (powerUp == null && score > 30 && seedRng.nextFloat() < 0.005f) {
            val kinds = listOf(PowerKind.Phase, PowerKind.Slow, PowerKind.Double)
            val k = kinds.random(seedRng)
            for (i in 0 until 80) {
                val px = seedRng.nextInt(COLS); val py = seedRng.nextInt(ROWS)
                if (isFreeCell(px, py)) { powerUp = SnakePower(px, py, k); break }
            }
        }
        powerUp?.let { p -> if (p.x == nx && p.y == ny) { applyPower(p.kind); powerUp = null } }
    }

    fun stepSmooth(dtMs: Long) {
        if (!alive || !started) return
        if (pendingDir.first.toFloat() != smoothVel.x || pendingDir.second.toFloat() != smoothVel.y) {
            smoothVel = Offset(pendingDir.first.toFloat(), pendingDir.second.toFloat())
            turnLog.add(frameIdx)
        }
        val speed = (cellPx / stepIntervalMs.toFloat()) * dtMs.toFloat()
        var nx = smoothPos.x + smoothVel.x * speed
        var ny = smoothPos.y + smoothVel.y * speed
        val left = offX; val top = offY; val right = offX + cellPx * COLS; val bot = offY + cellPx * ROWS
        if (settings.value.wrap) {
            if (nx < left) nx = right; if (nx > right) nx = left
            if (ny < top) ny = bot; if (ny > bot) ny = top
        } else if (nx < left || nx > right || ny < top || ny > bot) {
            crashInfo = "wall @ smooth $frameIdx"; die(); return
        }
        smoothPos = Offset(nx, ny)
        smoothBody.add(0, SmoothSeg(nx, ny, frameIdx))
        while (smoothBody.size > snakeLen * 4) smoothBody.removeAt(smoothBody.size - 1)
        if (activePowers.none { it.kind == PowerKind.Phase }) {
            for (i in 12 until smoothBody.size) {
                val s = smoothBody[i]
                if (hypot(s.x - nx, s.y - ny) < cellPx * 0.55f) {
                    crashInfo = "tail seg $i (smooth)"; die(); return
                }
            }
        }
        for (w0 in walls) {
            val cx = w0.x * cellPx + cellPx / 2 + offX
            val cy = w0.y * cellPx + cellPx / 2 + offY
            if (hypot(cx - nx, cy - ny) < cellPx * 0.85f) { crashInfo = "obstacle (smooth)"; die(); return }
        }
        val fx = food.x * cellPx + cellPx / 2 + offX
        val fy = food.y * cellPx + cellPx / 2 + offY
        if (hypot(fx - nx, fy - ny) < cellPx * 0.7f) eatFood(food)
        val gx = ((nx - offX) / cellPx).toInt().coerceIn(0, COLS - 1)
        val gy = ((ny - offY) / cellPx).toInt().coerceIn(0, ROWS - 1)
        pathHeat[gy * COLS + gx] = pathHeat[gy * COLS + gx] + 1
    }

    // ==== game loop ====
    LaunchedEffect(paused, alive, settingsOpen, calOpen, showAnalytics) {
        var last = System.currentTimeMillis()
        while (alive && !paused && !settingsOpen && !calOpen && !showAnalytics) {
            val targetFps = if (settings.value.batterySaver) 30 else 60
            delay((1000 / targetFps).toLong())
            val now = System.currentTimeMillis()
            val dt = (now - last).coerceAtMost(64L); last = now

            // power timers
            val toRemove = mutableListOf<ActivePower>()
            activePowers.forEach { ap ->
                ap.timeLeft -= dt.toInt()
                if (ap.timeLeft <= 0) toRemove.add(ap)
            }
            if (toRemove.isNotEmpty()) {
                activePowers.removeAll(toRemove); recomputeSpeed()
            }
            if (poisonInvertMs > 0) poisonInvertMs = max(0, poisonInvertMs - dt.toInt())
            comboRecharge += dt.toInt()
            if (comboRecharge > 4500 && combo > 1) combo = 1
            // time mode
            if (mode.equals("Time", true) && started) {
                timeLeftMs -= dt
                if (timeLeftMs <= 0) { crashInfo = "time up @ $frameIdx"; die(); break }
            }
            // moving obstacles
            movingObs.forEach { o ->
                o.x += o.dx * 0.04f
                if (o.x < 1f || o.x > COLS - 2f) o.dx = -o.dx
            }
            // shrinking arena
            if (settings.value.mmr > 80f && (System.currentTimeMillis() - runStartMs) > 60_000L) {
                arenaShrink = min(40f, arenaShrink + dt * 0.005f)
            }
            // step
            if (settings.value.movement == Movement.Grid) {
                var stepAcc = dt
                while (stepAcc >= stepIntervalMs && alive) {
                    stepAcc -= stepIntervalMs
                    stepGrid()
                }
            } else {
                stepSmooth(dt)
            }
            // particles
            val dead = mutableListOf<SnakeParticle>()
            particles.forEach { p ->
                p.x += p.vx; p.y += p.vy; p.life--
                p.vy += 0.03f
                if (p.life <= 0) dead.add(p)
            }
            particles.removeAll(dead)
            val popDead = mutableListOf<SnakePopup>()
            popups.forEach { it.life--; it.y -= 0.6f; if (it.life <= 0) popDead.add(it) }
            popups.removeAll(popDead)
            // trail particles
            if (settings.value.trail != Trail.None && !settings.value.batterySaver && frameIdx % 2 == 0) {
                val head = if (settings.value.movement == Movement.Grid && snakeGrid.isNotEmpty())
                    Offset(snakeGrid[0].x * cellPx + cellPx / 2 + offX, snakeGrid[0].y * cellPx + cellPx / 2 + offY) else smoothPos
                val (color, size) = when (settings.value.trail) {
                    Trail.Sparkle -> Color.hsv(((frameIdx * 9) % 360).toFloat(), 0.8f, 0.85f, 0.9f) to 1.6f
                    Trail.Fire -> Color(0xFFEF4444).copy(alpha = 0.85f) to 2.4f
                    Trail.Smoke -> Color(0xFFB0B6C8).copy(alpha = 0.25f) to 3.0f
                    Trail.Rainbow -> Color.hsv(((frameIdx * 4) % 360).toFloat(), 0.9f, 0.7f, 0.85f) to 2.0f
                    else -> Color(0xFF38BDF8).copy(alpha = 0.7f) to 2f
                }
                particles.add(SnakeParticle(head.x, head.y, -dir.first * 0.4f + (Random.nextFloat() - 0.5f) * 0.6f, -dir.second * 0.4f + (Random.nextFloat() - 0.5f) * 0.6f, 26, 26, color, size))
            }
            bgT += dt * 0.03f
            frameIdx++
            if (shakeT > 0) shakeT--
            if (!alive && !showAnalytics) {
                delay(if (settings.value.reducedMotion) 200L else 750L)
                showAnalytics = true
                break
            }
        }
    }

    // ==== tilt sensor ====
    DisposableEffect(settings.value.tilt) {
        var sm: SensorManager? = null
        var listener: SensorEventListener? = null
        if (settings.value.tilt) {
            sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) {
                listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        val gx = e.values[0]
                        if (abs(gx) > 4f) setDir(if (gx < 0) 1 else -1, 0)
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        onDispose { listener?.let { sm?.unregisterListener(it) } }
    }

    // ==== UI ====
    val accentCb = applySnakeColorblind(accent, settings.value.colorblind)
    val themeBg = when (settings.value.theme) {
        Theme.Cyber -> Brush.verticalGradient(listOf(Color(0xFF0B0E1F), Color(0xFF1E1B4B)))
        Theme.Zen -> Brush.verticalGradient(listOf(Color(0xFFFDE68A), Color(0xFFFCD34D)))
        Theme.Toxic -> Brush.verticalGradient(listOf(Color(0xFF0A2A14), Color(0xFF166534)))
        Theme.Void -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
    }

    val shakeOffsetX by animateFloatAsState(if (shakeT > 0 && !settings.value.reducedMotion) (Random.nextFloat() - 0.5f) * 6f else 0f)
    val shakeOffsetY by animateFloatAsState(if (shakeT > 0 && !settings.value.reducedMotion) (Random.nextFloat() - 0.5f) * 6f else 0f)

    Box(modifier = Modifier.fillMaxSize().background(themeBg)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.value.movement, settings.value.swipeSens, settings.value.deadZone) {
                    var startX = 0f; var startY = 0f; var startT = 0L
                    var totalDx = 0f; var totalDy = 0f
                    detectDragGestures(
                        onDragStart = { o ->
                            startX = o.x; startY = o.y; startT = System.currentTimeMillis()
                            totalDx = 0f; totalDy = 0f
                        },
                        onDragEnd = {
                            val m = hypot(totalDx, totalDy)
                            val threshold = 36f / settings.value.swipeSens
                            val dz = if (settings.value.deadZone) 16f else 0f
                            if (m >= max(dz, threshold)) {
                                if (abs(totalDx) > abs(totalDy)) setDir(if (totalDx > 0) 1 else -1, 0)
                                else setDir(0, if (totalDy > 0) 1 else -1)
                            }
                            totalDx = 0f; totalDy = 0f
                        },
                    ) { _, drag ->
                        totalDx += drag.x; totalDy += drag.y
                        if (settings.value.movement == Movement.Smooth) {
                            val m = hypot(totalDx, totalDy)
                            val dz = if (settings.value.deadZone) 18f else 6f
                            if (m >= dz) {
                                if (abs(totalDx) > abs(totalDy)) setDir(if (totalDx > 0) 1 else -1, 0)
                                else setDir(0, if (totalDy > 0) 1 else -1)
                            }
                        } else {
                            val sens = 30f / settings.value.swipeSens
                            if (abs(totalDx) > sens || abs(totalDy) > sens) {
                                if (abs(totalDx) > abs(totalDy)) setDir(if (totalDx > 0) 1 else -1, 0)
                                else setDir(0, if (totalDy > 0) 1 else -1)
                                totalDx = 0f; totalDy = 0f
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { if (!started) started = true })
                }
            ) {
                w = size.width; h = size.height
                // background pattern per theme
                drawSnakeTheme(settings.value.theme, bgT, size, cellPx, COLS, ROWS, offX, offY)

                // arena border
                drawRect(accentCb.copy(alpha = 0.25f), Offset(offX - 2f, offY - 2f),
                    Size(cellPx * COLS + 4f, cellPx * ROWS + 4f), style = Stroke(2f))
                if (arenaShrink > 0) {
                    drawRect(Color(0xFFEF4444).copy(alpha = 0.6f),
                        Offset(offX + arenaShrink, offY + arenaShrink),
                        Size(cellPx * COLS - arenaShrink * 2, cellPx * ROWS - arenaShrink * 2),
                        style = Stroke(2f))
                }
                // walls
                walls.forEach { w0 ->
                    drawRect(Color(0xFF475569),
                        Offset(offX + w0.x * cellPx + 2f, offY + w0.y * cellPx + 2f),
                        Size(cellPx - 4f, cellPx - 4f))
                }
                movingObs.forEach { o ->
                    drawRect(Color(0xFF94A3B8),
                        Offset(offX + o.x.roundToInt() * cellPx + 4f, offY + o.y.roundToInt() * cellPx + 4f),
                        Size(cellPx - 8f, cellPx - 8f))
                }
                // power-up
                powerUp?.let { p ->
                    val pcol = when (p.kind) { PowerKind.Phase -> Color(0xFFA78BFA); PowerKind.Slow -> Color(0xFF38BDF8); PowerKind.Double -> Color(0xFFFACC15); else -> Color(0xFF22C55E) }
                    drawCircle(pcol, cellPx * 0.4f, Offset(offX + p.x * cellPx + cellPx / 2, offY + p.y * cellPx + cellPx / 2))
                }
                // food (with bobbing)
                val wob = sin(bgT / 8f) * 1.5f
                val fx = offX + food.x * cellPx + cellPx / 2
                val fy = offY + food.y * cellPx + cellPx / 2 + wob
                val fc = applySnakeColorblind(foodColor(food.kind), settings.value.colorblind)
                drawCircle(fc, cellPx * 0.42f, Offset(fx, fy))
                if (food.kind == FoodKind.Golden) drawCircle(Color(0xFFFDE68A), cellPx * 0.55f, Offset(fx, fy), style = Stroke(1.5f))
                if (food.kind == FoodKind.Timed) {
                    val remain = max(0f, 5f - (frameIdx - food.spawnFrame) / 60f)
                    drawArc(Color.White, -90f, (remain / 5f) * 360f, false,
                        topLeft = Offset(fx - cellPx * 0.5f, fy - cellPx * 0.5f),
                        size = Size(cellPx, cellPx), style = Stroke(2f))
                }
                if (settings.value.assistPredict) {
                    drawCircle(Color(0xFFFACC15).copy(alpha = 0.5f + 0.3f * sin(bgT / 6f)),
                        cellPx * 0.7f, Offset(fx, fy - wob), style = Stroke(1.5f))
                }
                // trail particles
                particles.forEach { p ->
                    val a = (p.life.toFloat() / p.maxLife)
                    drawRect(p.color.copy(alpha = a * p.color.alpha), Offset(p.x, p.y), Size(p.size, p.size))
                }
                // snake
                if (settings.value.movement == Movement.Grid) {
                    drawSnakeGridBody(snakeGrid, dir, settings.value.skin, accentCb, cellPx, offX, offY, settings.value.batterySaver, settings.value.colorblind)
                } else {
                    drawSnakeSmoothBody(smoothBody, smoothPos, smoothVel, settings.value.skin, accentCb, cellPx, settings.value.batterySaver, settings.value.colorblind)
                }
                // popups
                popups.forEach { p ->
                    val a = (p.life.toFloat() / 50f).coerceIn(0f, 1f)
                    drawContext.canvas.nativeCanvas.also { c ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb((a * 255).toInt(), (p.color.red * 255).toInt(), (p.color.green * 255).toInt(), (p.color.blue * 255).toInt())
                            textSize = 28f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        c.drawText(p.text, p.x + cellPx / 2, p.y, paint)
                    }
                }
                // poison invert tint
                if (poisonInvertMs > 0) {
                    drawRect(Color(0xFFA855F7).copy(alpha = 0.10f + 0.06f * sin(bgT / 4f).coerceIn(-1f, 1f)),
                        Offset(offX, offY), Size(cellPx * COLS, cellPx * ROWS))
                }
                // start hint
                if (!started && alive) {
                    val txt = if (settings.value.movement == Movement.Smooth) "Drag to begin steering" else "Swipe / D-pad to begin"
                    drawContext.canvas.nativeCanvas.also { c ->
                        val paint = android.graphics.Paint().apply { color = android.graphics.Color.argb(160, 0, 0, 0) }
                        c.drawRoundRect(size.width / 2f - 220f, size.height / 2f - 50f, size.width / 2f + 220f, size.height / 2f + 30f, 18f, 18f, paint)
                        val tp = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 36f; isFakeBoldText = true; textAlign = android.graphics.Paint.Align.CENTER }
                        c.drawText(txt, size.width / 2f, size.height / 2f - 8f, tp)
                        val sp = android.graphics.Paint().apply { color = android.graphics.Color.argb(200, 255, 255, 255); textSize = 24f; textAlign = android.graphics.Paint.Align.CENTER }
                        c.drawText(if (dailyActive) "Daily seed (#${todayDateNumber()})" else "Random seed", size.width / 2f, size.height / 2f + 18f, sp)
                    }
                }
            }

            // HUD
            Column(modifier = Modifier.align(if (settings.value.leftHanded) Alignment.TopEnd else Alignment.TopStart).padding(10.dp)) {
                HudPill("SCORE", "$score")
                if (combo > 1) HudPill("COMBO", "x$combo")
                if (mode.equals("Time", true)) HudPill("TIME", "${max(0, (timeLeftMs / 1000).toInt() + 1)}s")
                if (snakeLen > 3) HudPill("LEN", "$snakeLen")
                if (hasGhost) HudPill("👻", "ghost")
                if (dailyActive) HudPill("🌅", "daily")
            }

            // Active powers
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                activePowers.forEach { ap ->
                    PowerPill(ap.kind, ap.timeLeft)
                }
            }

            // Settings button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { settingsOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", color = Color.White, fontSize = 16.sp)
            }

            // D-pad
            if (settings.value.showDpad) {
                Box(
                    modifier = Modifier
                        .align(if (settings.value.leftHanded) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(12.dp),
                ) { OnScreenDpad(onDir = { dx, dy -> setDir(dx, dy) }, onUndo = { undoMove() }, canUndo = undoSnapshot != null && alive) }
            }
        }

        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SnakeSettingsSheet(settings,
                onClose = { settingsOpen = false; saveSettings(); recomputeSpeed() },
                onChange = { saveSettings(); recomputeSpeed() },
                onCalibrate = { settingsOpen = false; calOpen = true },
            )
        }
        AnimatedVisibility(visible = calOpen, enter = fadeIn(), exit = fadeOut()) {
            SnakeCalibrationSheet(onDone = { newSens ->
                if (newSens != null) {
                    settings.value = settings.value.copy(swipeSens = newSens.coerceIn(0.4f, 2.0f))
                    saveSettings()
                }
                calOpen = false
            })
        }
        AnimatedVisibility(visible = showAnalytics, enter = fadeIn(), exit = fadeOut()) {
            SnakeAnalyticsSheet(
                score = score, snakeLen = snakeLen, crashInfo = crashInfo, turns = turnLog.size,
                avgDistLabel = if (recordedMoves.isEmpty() || score == 0) "—" else "${"%.1f".format(recordedMoves.size / max(1f, score / 10f))} steps",
                heat = pathHeat, turnLog = turnLog, frameIdx = frameIdx,
                suggestion = deathSuggestion, unlocks = newUnlocks,
                onReplay = { showAnalytics = false; reset() },
                onContinue = { showAnalytics = false; onGameOver() },
            )
        }
    }
}

// ===================
// Drawing helpers
// ===================

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnakeTheme(
    theme: Theme, bgT: Float, size: Size, cellPx: Float, cols: Int, rows: Int, offX: Float, offY: Float
) {
    when (theme) {
        Theme.Cyber -> {
            val gridColor = Color(0xFFA855F7).copy(alpha = 0.08f + 0.04f * sin(bgT / 30f).coerceIn(-1f, 1f))
            for (i in 0..cols) drawLine(gridColor, Offset(offX + i * cellPx, offY), Offset(offX + i * cellPx, offY + cellPx * rows), 1f)
            for (j in 0..rows) drawLine(gridColor, Offset(offX, offY + j * cellPx), Offset(offX + cellPx * cols, offY + j * cellPx), 1f)
        }
        Theme.Zen -> {
            val sandLine = Color(0xFF92400E).copy(alpha = 0.18f)
            var r = 30f
            while (r < size.width) {
                val path = Path()
                var first = true
                var x = 0f
                while (x < size.width) {
                    val y = r + sin((x + bgT) / 22f) * 4f
                    if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                    x += 6f
                }
                drawPath(path, sandLine, style = Stroke(1.2f))
                r += 18f
            }
        }
        Theme.Toxic -> {
            for (i in 0 until 18) {
                val x = (i * 73f + bgT * 0.6f) % size.width
                val y = ((i * 41) % size.height.toInt()).toFloat()
                drawCircle(Color(0xFF22C55E).copy(alpha = 0.12f + 0.08f * sin((bgT + i * 30f) / 25f).coerceIn(-1f, 1f)),
                    radius = 24f + (i % 3) * 8f, center = Offset(x, y))
            }
        }
        Theme.Void -> { /* black bg already from gradient */ }
    }
}

private fun skinColorAt(skin: Skin, accent: Color, idx: Int, total: Int, cb: String): Color {
    val t = idx / max(1f, total.toFloat())
    val c = when (skin) {
        Skin.Crystal -> Color(0xFFBAE6FD).copy(alpha = 1f - t * 0.6f)
        Skin.Lava -> Color(((255 - t * 60).toInt()).coerceIn(0, 255), ((110 - t * 60).toInt()).coerceIn(0, 255), ((30 - t * 20).toInt()).coerceIn(0, 255))
        Skin.Ice -> Color(((190 + t * 20).toInt()).coerceIn(0, 255), ((220 + t * 20).toInt()).coerceIn(0, 255), 255)
        Skin.Ghost -> Color(0xFFA78BFA).copy(alpha = 0.4f + 0.3f * (1f - t))
        Skin.Pixel -> if (idx % 2 == 0) Color(0xFF22C55E) else Color(0xFF16A34A)
        Skin.Golden -> Color(((250 - t * 30).toInt()).coerceIn(0, 255), ((204 - t * 60).toInt()).coerceIn(0, 255), 21)
        Skin.Neon -> accent.copy(alpha = 1f - t * 0.55f)
    }
    return applySnakeColorblind(c, cb)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnakeGridBody(
    snake: List<SnakeCell>, dir: Pair<Int, Int>, skin: Skin, accent: Color,
    cellPx: Float, offX: Float, offY: Float, batterySaver: Boolean, cb: String,
) {
    if (snake.isEmpty()) return
    for (i in snake.size - 1 downTo 1) {
        val s = snake[i]
        val col = skinColorAt(skin, accent, i, snake.size, cb)
        drawRect(col, Offset(offX + s.x * cellPx + 1f, offY + s.y * cellPx + 1f), Size(cellPx - 2f, cellPx - 2f))
    }
    val h = snake[0]
    val cx = offX + h.x * cellPx + cellPx / 2
    val cy = offY + h.y * cellPx + cellPx / 2
    val ang = atan2(dir.second.toFloat(), dir.first.toFloat())
    drawRotate(degrees = ang * 180f / PI.toFloat(), pivot = Offset(cx, cy)) {
        val headColor = skinColorAt(skin, accent, 0, snake.size, cb)
        val path = Path().apply {
            moveTo(cx + cellPx * 0.55f, cy)
            lineTo(cx - cellPx * 0.45f, cy - cellPx * 0.45f)
            lineTo(cx - cellPx * 0.45f, cy + cellPx * 0.45f)
            close()
        }
        drawPath(path, headColor)
        drawCircle(Color(0xFFFDE68A), 2.2f, Offset(cx + cellPx * 0.15f, cy - cellPx * 0.18f))
        drawCircle(Color(0xFFFDE68A), 2.2f, Offset(cx + cellPx * 0.15f, cy + cellPx * 0.18f))
        drawCircle(Color.Black, 1.0f, Offset(cx + cellPx * 0.18f, cy - cellPx * 0.18f))
        drawCircle(Color.Black, 1.0f, Offset(cx + cellPx * 0.18f, cy + cellPx * 0.18f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnakeSmoothBody(
    body: List<SmoothSeg>, head: Offset, vel: Offset, skin: Skin, accent: Color,
    cellPx: Float, batterySaver: Boolean, cb: String,
) {
    if (body.isEmpty()) return
    var i = body.size - 1
    while (i >= 1) {
        val s = body[i]
        val col = skinColorAt(skin, accent, i / 4, max(1, body.size / 4), cb)
        drawCircle(col, cellPx * 0.42f, Offset(s.x, s.y))
        i -= 2
    }
    val ang = atan2(vel.y, vel.x)
    val cx = head.x; val cy = head.y
    drawRotate(degrees = ang * 180f / PI.toFloat(), pivot = Offset(cx, cy)) {
        val headColor = skinColorAt(skin, accent, 0, max(1, body.size / 4), cb)
        val path = Path().apply {
            moveTo(cx + cellPx * 0.55f, cy)
            lineTo(cx - cellPx * 0.45f, cy - cellPx * 0.45f)
            lineTo(cx - cellPx * 0.45f, cy + cellPx * 0.45f)
            close()
        }
        drawPath(path, headColor)
        drawCircle(Color(0xFFFDE68A), 2.2f, Offset(cx + cellPx * 0.15f, cy - cellPx * 0.18f))
        drawCircle(Color(0xFFFDE68A), 2.2f, Offset(cx + cellPx * 0.15f, cy + cellPx * 0.18f))
    }
}

// ===================
// UI subcomponents
// ===================

@Composable
private fun HudPill(label: String, value: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PowerPill(kind: PowerKind, timeLeftMs: Int) {
    val (label, color) = when (kind) {
        PowerKind.Phase -> "👻 Phase" to Color(0xFFA78BFA)
        PowerKind.Magnet -> "🧲 Magnet" to Color(0xFFF472B6)
        PowerKind.Shrink -> "✂ Shrunk" to Color(0xFF22C55E)
        PowerKind.Slow -> "⏳ Slow" to Color(0xFF38BDF8)
        PowerKind.Double -> "✨ x2" to Color(0xFFFACC15)
    }
    Surface(color = color.copy(alpha = 0.45f), shape = RoundedCornerShape(20.dp)) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Color.White, fontSize = 11.sp)
            Text("${(timeLeftMs / 1000) + 1}s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnScreenDpad(onDir: (Int, Int) -> Unit, onUndo: () -> Unit, canUndo: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DpadBtn("▲") { onDir(0, -1) }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            DpadBtn("◀") { onDir(-1, 0) }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (canUndo) Color(0xFFFACC15).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                    .clickable(enabled = canUndo) { onUndo() },
                contentAlignment = Alignment.Center,
            ) { Text("↺", color = Color.White, fontSize = 18.sp) }
            DpadBtn("▶") { onDir(1, 0) }
        }
        DpadBtn("▼") { onDir(0, 1) }
    }
}

@Composable
private fun DpadBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 20.sp) }
}

@Composable
private fun SnakeSettingsSheet(
    settings: MutableState<SnakeSettings>,
    onClose: () -> Unit,
    onChange: () -> Unit,
    onCalibrate: () -> Unit,
) {
    val s = settings.value
    fun update(block: SnakeSettings.() -> Unit) {
        val ns = s.copy(unlockSkins = s.unlockSkins.toMutableSet(), unlockThemes = s.unlockThemes.toMutableSet())
        ns.apply(block)
        settings.value = ns
        onChange()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F172A),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* swallow */ },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Viper · Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.size(30.dp).clip(CircleShape).clickable { onClose() }, contentAlignment = Alignment.Center) {
                        Text("✕", color = Color.White)
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SetRow("Movement (${if (s.movement == Movement.Grid) "Grid" else "Smooth"})") {
                        Chip("Grid", s.movement == Movement.Grid) { update { movement = Movement.Grid } }
                        Chip("Smooth", s.movement == Movement.Smooth) { update { movement = Movement.Smooth } }
                    }
                    SetRow("Base speed (${s.baseSpeed})") {
                        Chip("Slow", s.baseSpeed == "slow") { update { baseSpeed = "slow" } }
                        Chip("Normal", s.baseSpeed == "normal") { update { baseSpeed = "normal" } }
                        Chip("Fast", s.baseSpeed == "fast") { update { baseSpeed = "fast" } }
                    }
                    Column {
                        Text("Swipe sensitivity (${"%.2f".format(s.swipeSens)})", color = Color.White.copy(0.85f), fontSize = 13.sp)
                        Slider(value = s.swipeSens, onValueChange = { v -> update { swipeSens = v } }, valueRange = 0.4f..2.0f, steps = 31)
                        Text("Lower = longer swipe needed.", color = Color.White.copy(0.55f), fontSize = 11.sp)
                    }
                    CheckOpt("Centre dead-zone (rest thumb)", s.deadZone) { v -> update { deadZone = v } }
                    CheckOpt("Wrap-around walls", s.wrap) { v -> update { wrap = v } }
                    CheckOpt("Tilt steering (gyro)", s.tilt) { v -> update { tilt = v } }
                    CheckOpt("Show on-screen D-pad", s.showDpad) { v -> update { showDpad = v } }
                    CheckOpt("Left-handed UI", s.leftHanded) { v -> update { leftHanded = v } }
                    CheckOpt("Haptic feedback", s.haptics) { v -> update { haptics = v } }
                    CheckOpt("Adaptive layered music", s.layeredMusic) { v -> update { layeredMusic = v } }
                    CheckOpt("Swipe tick sound", s.swipeTick) { v -> update { swipeTick = v } }
                    CheckOpt("Voice hints (toast)", s.voiceHints) { v -> update { voiceHints = v } }
                    CheckOpt("Reduced motion", s.reducedMotion) { v -> update { reducedMotion = v } }
                    CheckOpt("High contrast", s.highContrast) { v -> update { highContrast = v } }
                    CheckOpt("Battery saver (30 fps)", s.batterySaver) { v -> update { batterySaver = v } }
                    CheckOpt("Assist: predict food ring", s.assistPredict) { v -> update { assistPredict = v } }
                    SetRow("Colourblind") {
                        listOf("off", "protanopia", "deuteranopia", "tritanopia").forEach { m ->
                            Chip(m, s.colorblind == m) { update { colorblind = m } }
                        }
                    }
                    SetRow("Theme") {
                        Theme.values().forEach { t ->
                            val locked = t !in s.unlockThemes
                            Chip((if (locked) "🔒 " else "") + t.name, s.theme == t && !locked, locked = locked) { if (!locked) update { theme = t } }
                        }
                    }
                    SetRow("Skin") {
                        Skin.values().forEach { sk ->
                            val locked = sk !in s.unlockSkins
                            Chip((if (locked) "🔒 " else "") + sk.name, s.skin == sk && !locked, locked = locked) { if (!locked) update { skin = sk } }
                        }
                    }
                    SetRow("Trail") {
                        Trail.values().forEach { tr ->
                            Chip(tr.name, s.trail == tr) { update { trail = tr } }
                        }
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.06f), modifier = Modifier.fillMaxWidth().clickable { onCalibrate() }) {
                        Text("📐 Calibrate swipe sensitivity", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                    Column {
                        Text("Adaptive difficulty (hidden MMR)", color = Color.White.copy(0.85f), fontSize = 13.sp)
                        Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(0.08f))) {
                            Box(modifier = Modifier.fillMaxWidth(s.mmr / 100f).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF34D399), Color(0xFFF59E0B), Color(0xFFEF4444)))))
                        }
                        Text(when {
                            s.mmr < 25f -> "Adapting easier — slower start, larger food hitbox"
                            s.mmr < 60f -> "Tuned to your skill level"
                            s.mmr < 85f -> "Slightly tougher — moving obstacles, faster speed"
                            else -> "Pro mode — shrinking arena, frequent poison"
                        }, color = Color.White.copy(0.6f), fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SetRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 13.sp)
        SnakeFlow { content() }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SnakeFlow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Chip(label: String, on: Boolean, locked: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        on -> Color(0xFF6366F1)
        locked -> Color.White.copy(alpha = 0.04f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(enabled = !locked || on) { onClick() },
    ) {
        Text(label, color = Color.White.copy(alpha = if (locked && !on) 0.5f else 1f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun CheckOpt(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!value) }) {
        Checkbox(checked = value, onCheckedChange = { onChange(it) })
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun SnakeCalibrationSheet(onDone: (Float?) -> Unit) {
    var round by remember { mutableStateOf(1) }
    val lengths = remember { mutableStateListOf<Float>() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone(null) },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF0F172A), modifier = Modifier.fillMaxWidth(0.85f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Swipe calibration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .pointerInput(round) {
                            var sx = 0f; var sy = 0f
                            var totalDx = 0f; var totalDy = 0f
                            detectDragGestures(
                                onDragStart = { o -> sx = o.x; sy = o.y; totalDx = 0f; totalDy = 0f },
                                onDragEnd = {
                                    val m = hypot(totalDx, totalDy)
                                    if (m > 50f && round <= 5) { lengths.add(m); round++ }
                                },
                            ) { _, drag -> totalDx += drag.x; totalDy += drag.y }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (round <= 5) "Swipe across in any direction at your natural speed.\n($round of 5)"
                        else "Avg: ${lengths.average().toInt()}px — tap Done",
                        color = Color.White, fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth().clickable {
                    val avg = if (lengths.isEmpty()) null else (1.6f - (lengths.average().toFloat() - 40f) / 200f).coerceIn(0.4f, 2.0f)
                    onDone(avg)
                }) { Text("Done", color = Color.White, modifier = Modifier.padding(12.dp).fillMaxWidth(), textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun SnakeAnalyticsSheet(
    score: Int, snakeLen: Int, crashInfo: String, turns: Int, avgDistLabel: String,
    heat: IntArray, turnLog: List<Int>, frameIdx: Int,
    suggestion: String, unlocks: List<String>,
    onReplay: () -> Unit, onContinue: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF0F172A), modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Run analysis · Score $score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF38BDF8).copy(alpha = 0.18f)) {
                    Text(suggestion, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(10.dp))
                }
                // Heatmap
                Text("Path heatmap (where you spent time)", color = Color.White.copy(0.6f), fontSize = 11.sp)
                Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.04f))) {
                    val cw = size.width / COLS; val ch = size.height / ROWS
                    var maxV = 1
                    for (v in heat) if (v > maxV) maxV = v
                    for (y in 0 until ROWS) for (x in 0 until COLS) {
                        val v = (heat[y * COLS + x].toFloat() / maxV)
                        if (v > 0.02f) drawRect(Color(0xFF38BDF8).copy(alpha = 0.15f + v * 0.7f), Offset(x * cw, y * ch), Size(cw - 0.5f, ch - 0.5f))
                    }
                }
                // Reaction chart
                Text("Reactions per second (turns)", color = Color.White.copy(0.6f), fontSize = 11.sp)
                Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(0.04f))) {
                    val bins = 16
                    val counts = IntArray(bins)
                    val maxFr = max(60, frameIdx)
                    turnLog.forEach { t ->
                        val idx = min(bins - 1, (t.toFloat() / maxFr * bins).toInt())
                        counts[idx]++
                    }
                    val maxC = max(1, counts.maxOrNull() ?: 1)
                    val bw = size.width / bins
                    for (i in 0 until bins) {
                        val v = counts[i].toFloat() / maxC
                        drawRect(Color(0xFFF472B6).copy(alpha = 0.25f + v * 0.65f), Offset(i * bw, size.height - v * size.height), Size(bw - 1f, v * size.height))
                    }
                }
                // stats grid
                FlowRow {
                    StatBox("Length", "$snakeLen")
                    StatBox("Crashed", crashInfo.ifBlank { "—" })
                    StatBox("Turns", "$turns")
                    StatBox("Steps/food", avgDistLabel)
                }
                if (unlocks.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF14B8A6).copy(alpha = 0.25f)) {
                        Text("🎉 Unlocked: ${unlocks.joinToString(", ")}", color = Color(0xFFA7F3D0), modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF6366F1), modifier = Modifier.weight(1f).clickable { onReplay() }) {
                        Text("↺ Instant replay", color = Color.White, modifier = Modifier.padding(10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Surface(shape = RoundedCornerShape(10.dp), color = Color.White.copy(0.1f), modifier = Modifier.weight(1f).clickable { onContinue() }) {
                        Text("Continue", color = Color.White, modifier = Modifier.padding(10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBox(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(0.06f), modifier = Modifier.padding(2.dp).widthIn(min = 80.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
