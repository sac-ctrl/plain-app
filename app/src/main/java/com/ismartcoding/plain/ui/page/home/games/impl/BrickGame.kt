package com.ismartcoding.plain.ui.page.home.games.impl

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.BrickSettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private enum class BKind { NORMAL, FIRE, ICE, ELECTRIC, METAL, BOSS, REGEN }
private enum class PKind { LONGER, MULTI, SLOW, STICKY, LASER, WRAP, GRAVITY, SHIELD, NUKE, SHRINK, SHORT, REVERSE, INVISIBLE }

private data class CBrick(
    var x: Float, var y: Float, val w: Float, val h: Float,
    var alive: Boolean, val kind: BKind, var hp: Int, val maxHp: Int,
    var hue: Float, var vx: Float = 0f, var rotPhase: Float = 0f,
    var regenAt: Long = 0L,
)
private data class CBall(var x: Float, var y: Float, var vx: Float, var vy: Float, var r: Float, var sticky: Boolean = false, var trailHue: Float = 200f)
private data class CDrop(var x: Float, var y: Float, val kind: PKind, val hue: Float)
private data class CLaser(var x: Float, var y: Float)
private data class CPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val max: Int, val col: Color, val size: Float)
private data class CFloat(var x: Float, var y: Float, var t: Int, val max: Int, val text: String, val col: Color)
private data class CRing(var x: Float, var y: Float, var r: Float, val max: Float, val col: Color)

private data class BrickSettings(
    var world: Int = 0,
    var music: String = "synthwave",
    var paddleShape: String = "classic",
    var paddleMat: String = "neon",
    var ballTrail: String = "comet",
    var colourblind: String = "off",
    var mouseSmoothing: Int = 20,
    var kbRamp: Int = 220,
    var uiScale: Int = 100,
    var ghostLine: Boolean = true,
    var spin: Boolean = true,
    var haptics: Boolean = true,
    var reducedMotion: Boolean = false,
    var highContrast: Boolean = false,
    var announcer: Boolean = true,
    var audioViz: Boolean = true,
    var assistLock: Boolean = false,
    var assistAim: Boolean = false,
    var assistInfinite: Boolean = false,
    var shards: Int = 0,
    var deaths: Int = 0,
    var clears: Int = 0,
    var unlockedPaddles: List<String> = listOf("classic"),
    var unlockedTrails: List<String> = listOf("comet"),
    var unlockedArenas: List<Int> = listOf(0),
    var unlockedMusic: List<String> = listOf("synthwave"),
    var bestRank: String = "E",
    var ghostBest: List<Float> = emptyList(),
)

private fun parseBrick(j: String): BrickSettings {
    val s = BrickSettings()
    if (j.isBlank() || j == "{}") return s
    try {
        val o = JSONObject(j)
        s.world = o.optInt("world", 0)
        s.music = o.optString("music", "synthwave")
        s.paddleShape = o.optString("paddleShape", "classic")
        s.paddleMat = o.optString("paddleMat", "neon")
        s.ballTrail = o.optString("ballTrail", "comet")
        s.colourblind = o.optString("colourblind", "off")
        s.mouseSmoothing = o.optInt("mouseSmoothing", 20)
        s.kbRamp = o.optInt("kbRamp", 220)
        s.uiScale = o.optInt("uiScale", 100)
        s.ghostLine = o.optBoolean("ghostLine", true)
        s.spin = o.optBoolean("spin", true)
        s.haptics = o.optBoolean("haptics", true)
        s.reducedMotion = o.optBoolean("reducedMotion", false)
        s.highContrast = o.optBoolean("highContrast", false)
        s.announcer = o.optBoolean("announcer", true)
        s.audioViz = o.optBoolean("audioViz", true)
        s.assistLock = o.optBoolean("assistLock", false)
        s.assistAim = o.optBoolean("assistAim", false)
        s.assistInfinite = o.optBoolean("assistInfinite", false)
        s.shards = o.optInt("shards", 0)
        s.deaths = o.optInt("deaths", 0)
        s.clears = o.optInt("clears", 0)
        s.bestRank = o.optString("bestRank", "E")
        s.unlockedPaddles = (o.optJSONArray("unlockedPaddles")?.let { a -> (0 until a.length()).map { a.getString(it) } }) ?: listOf("classic")
        s.unlockedTrails = (o.optJSONArray("unlockedTrails")?.let { a -> (0 until a.length()).map { a.getString(it) } }) ?: listOf("comet")
        s.unlockedArenas = (o.optJSONArray("unlockedArenas")?.let { a -> (0 until a.length()).map { a.getInt(it) } }) ?: listOf(0)
        s.unlockedMusic = (o.optJSONArray("unlockedMusic")?.let { a -> (0 until a.length()).map { a.getString(it) } }) ?: listOf("synthwave")
        s.ghostBest = (o.optJSONArray("ghostBest")?.let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } }) ?: emptyList()
    } catch (_: Exception) { }
    return s
}

private fun toJson(s: BrickSettings): String {
    val o = JSONObject()
    o.put("world", s.world); o.put("music", s.music)
    o.put("paddleShape", s.paddleShape); o.put("paddleMat", s.paddleMat); o.put("ballTrail", s.ballTrail)
    o.put("colourblind", s.colourblind)
    o.put("mouseSmoothing", s.mouseSmoothing); o.put("kbRamp", s.kbRamp); o.put("uiScale", s.uiScale)
    o.put("ghostLine", s.ghostLine); o.put("spin", s.spin); o.put("haptics", s.haptics)
    o.put("reducedMotion", s.reducedMotion); o.put("highContrast", s.highContrast)
    o.put("announcer", s.announcer); o.put("audioViz", s.audioViz)
    o.put("assistLock", s.assistLock); o.put("assistAim", s.assistAim); o.put("assistInfinite", s.assistInfinite)
    o.put("shards", s.shards); o.put("deaths", s.deaths); o.put("clears", s.clears)
    o.put("bestRank", s.bestRank)
    o.put("unlockedPaddles", JSONArray(s.unlockedPaddles))
    o.put("unlockedTrails", JSONArray(s.unlockedTrails))
    o.put("unlockedArenas", JSONArray(s.unlockedArenas))
    o.put("unlockedMusic", JSONArray(s.unlockedMusic))
    o.put("ghostBest", JSONArray(s.ghostBest))
    return o.toString()
}

private fun applyCB(c: Color, mode: String): Color {
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

private fun hsl(h: Float, s: Float, l: Float, a: Float = 1f): Color {
    val c = (1f - abs(2 * l - 1f)) * s
    val hp = (h % 360f + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2 - 1f))
    val (r1, g1, b1) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f), a)
}

private fun vibrate(ctx: Context, ms: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    } catch (_: Throwable) { }
}

private fun todaySeed(): Long {
    val cal = Calendar.getInstance()
    return (cal.get(Calendar.YEAR) * 372L + (cal.get(Calendar.MONTH) + 1) * 31L + cal.get(Calendar.DAY_OF_MONTH)).toLong()
}

private val WORLD_LABELS = listOf("Factory", "Crystal", "Temple", "Sanctum", "Cosmos", "Abyss", "Inferno", "Zero")

@Composable
fun BrickGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { mutableStateOf(BrickSettings()) }
    LaunchedEffect(Unit) { settings.value = parseBrick(BrickSettingsJsonPreference.getAsync(ctx)) }
    fun save() { scope.launch { BrickSettingsJsonPreference.putAsync(ctx, toJson(settings.value)) } }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(ctx) { st -> if (st == TextToSpeech.SUCCESS) tts.value?.language = Locale.getDefault() }
        tts.value = t
        onDispose { try { t.stop(); t.shutdown() } catch (_: Throwable) { } }
    }
    fun say(text: String) {
        if (!settings.value.announcer) return
        try { tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "brk") } catch (_: Throwable) { }
    }

    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    var paddleX by remember { mutableStateOf(0f) }
    var paddleW by remember { mutableStateOf(120f) }
    var paddlePrevX by remember { mutableStateOf(0f) }
    var paddleVx by remember { mutableStateOf(0f) }
    val paddleH = 12f
    val paddleY by remember { derivedStateOf { h - 50f } }

    val balls = remember { mutableStateListOf<CBall>() }
    val bricks = remember { mutableStateListOf<CBrick>() }
    val drops = remember { mutableStateListOf<CDrop>() }
    val lasers = remember { mutableStateListOf<CLaser>() }
    val particles = remember { mutableStateListOf<CPart>() }
    val texts = remember { mutableStateListOf<CFloat>() }
    val rings = remember { mutableStateListOf<CRing>() }

    var alive by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(3) }
    var level by remember { mutableStateOf(1) }
    var levelInWorld by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }

    var settingsOpen by remember { mutableStateOf(false) }
    var analyticsOpen by remember { mutableStateOf(false) }

    var stickyT by remember { mutableStateOf(0) }
    var laserT by remember { mutableStateOf(0) }
    var wideT by remember { mutableStateOf(0) }
    var slowT by remember { mutableStateOf(0) }
    var wrapT by remember { mutableStateOf(0) }
    var gravityT by remember { mutableStateOf(0) }
    var shrinkT by remember { mutableStateOf(0) }
    var shieldCharges by remember { mutableStateOf(0) }
    var debuffLabel by remember { mutableStateOf("") }
    var shortT by remember { mutableStateOf(0) }
    var reverseT by remember { mutableStateOf(0) }
    var invisibleT by remember { mutableStateOf(0) }
    var powerLabel by remember { mutableStateOf("") }
    var powerLeft by remember { mutableStateOf(0) }

    var shakeT by remember { mutableStateOf(0) }
    var shakeMag by remember { mutableStateOf(0f) }
    var cinematicSlowT by remember { mutableStateOf(0) }
    var lastLaserShot by remember { mutableStateOf(0L) }

    var baseSpeed by remember { mutableStateOf(4.4f) }
    var baseRows by remember { mutableStateOf(4) }
    var baseLives by remember { mutableStateOf(3) }
    val seedHolder = remember { mutableStateOf(Random(System.currentTimeMillis())) }

    // run analytics
    var brokenCnt by remember { mutableStateOf(0) }
    var paddleHits by remember { mutableStateOf(0) }
    var powersCaught by remember { mutableStateOf(0) }
    var maxCombo by remember { mutableStateOf(0) }
    val missZones = remember { mutableStateMapOf("left" to 0, "centre" to 0, "right" to 0) }
    val deathHist = remember { mutableStateListOf<Int>() }
    var runRank by remember { mutableStateOf("E") }
    var runTip by remember { mutableStateOf("") }
    val runUnlocks = remember { mutableStateListOf<String>() }

    // calibration
    var calActive by remember { mutableStateOf(false) }
    var calStart by remember { mutableStateOf(0L) }
    val calReact = remember { mutableStateListOf<Long>() }

    // recording / replay
    val recordX = remember { mutableStateListOf<Float>() }
    var replayActive by remember { mutableStateOf(false) }
    var replayIdx by remember { mutableStateOf(0) }
    val replayBuf = remember { mutableStateListOf<Float>() }

    // trajectory cache
    val traj = remember { mutableStateListOf<Offset>() }
    var trajCacheT by remember { mutableStateOf(0) }

    fun rand(): Float = seedHolder.value.nextFloat()
    val modeNorm = remember(mode) {
        when (mode.lowercase()) {
            "classic", "campaign" -> "campaign"
            "survival", "endless" -> "endless"
            "boss" -> "boss"
            "daily" -> "daily"
            else -> "campaign"
        }
    }

    fun configureForRun() {
        val (sp, rw, lv) = when (difficulty) {
            "Easy" -> Triple(3.4f, 3, 5)
            "Hard" -> Triple(5.4f, 5, 3)
            "Insane" -> Triple(6.6f, 6, 2)
            else -> Triple(4.4f, 4, 4)
        }
        baseSpeed = sp; baseRows = rw
        baseLives = if (settings.value.assistInfinite) 999 else lv
        if (modeNorm == "endless") baseLives = max(2, baseLives - 1)
        seedHolder.value = if (modeNorm == "daily") Random(todaySeed()) else Random(System.currentTimeMillis())
        if (settings.value.deaths >= 3) { paddleW = 150f; baseSpeed *= 0.92f }
        if (settings.value.clears >= 5) baseRows = min(8, baseRows + 1)
    }

    fun spawnBall(x: Float, y: Float) {
        val ang = (rand() * 0.5f - 0.25f) - (PI / 2).toFloat()
        balls.add(CBall(x, y,
            cos(ang) * baseSpeed * (if (rand() < 0.5f) -1f else 1f),
            sin(ang) * baseSpeed,
            if (shrinkT > 0) 4f else 7f, false, 200f))
    }

    fun buildBoss() {
        bricks.clear()
        val cw = 200f; val ch = 80f
        val cx = w / 2f - cw / 2f; val cy = 100f
        bricks.add(CBrick(cx, cy, cw, ch, true, BKind.BOSS, 16 + level * 2, 16 + level * 2, 320f, 0f, 0f))
        for (i in 0 until 6) {
            bricks.add(CBrick(30f + i * 50f, 200f, 38f, 14f, true,
                if (i % 2 == 0) BKind.FIRE else BKind.ICE, 1, 1,
                if (i % 2 == 0) 18f else 200f, 0.3f * if (i % 2 == 0) 1 else -1))
        }
    }

    fun buildLevel(lvl: Int) {
        val world = settings.value.world
        if (modeNorm == "boss" || (world == 7 && lvl % 3 == 0)) { buildBoss(); return }
        bricks.clear()
        val cols = 8
        val bw = (w - 40f) / cols
        val bh = 18f
        val rows = min(8, baseRows + (lvl - 1) / 3)
        for (r in 0 until rows) {
            for (i in 0 until cols) {
                if (lvl > 1 && rand() < 0.06f + min(0.18f, lvl * 0.012f)) continue
                val x = 20f + i * bw
                val y = 60f + r * (bh + 5f)
                var kind = BKind.NORMAL
                var hp = 1; var hue = 200f + r * 28f
                val roll = rand()
                when {
                    world == 0 && r == 0 -> { hp = 2; hue = 30f }
                    world == 1 && roll < 0.25f -> { kind = BKind.ELECTRIC; hue = 50f }
                    world == 2 && roll < 0.35f -> { kind = BKind.REGEN; hue = 280f }
                    world == 3 && r >= 2 && roll < 0.4f -> { kind = BKind.METAL; hp = 99 }
                    world == 4 && roll < 0.2f -> { kind = BKind.FIRE; hue = 18f }
                    world == 5 && roll < 0.4f -> { hue = 220f }
                    world == 6 && roll < 0.45f -> { kind = BKind.FIRE; hue = 8f }
                    world == 7 -> {
                        if (roll < 0.18f) kind = BKind.FIRE
                        else if (roll < 0.36f) kind = BKind.ICE
                        else if (roll < 0.54f) kind = BKind.ELECTRIC
                        else if (roll < 0.62f) { kind = BKind.METAL; hp = 99 }
                    }
                }
                if (kind != BKind.METAL && r < 2 && lvl > 3 && rand() < 0.15f) hp = 2
                val vx = when {
                    world == 0 && r % 2 == 0 -> 0.4f
                    world == 7 && rand() < 0.2f -> 0.5f * if (rand() < 0.5f) 1 else -1
                    else -> 0f
                }
                bricks.add(CBrick(x, y, bw - 4f, bh, true, kind, hp, hp, hue, vx, rand() * (PI * 2).toFloat()))
            }
        }
    }

    fun reset() {
        configureForRun()
        score = 0; lives = baseLives; level = 1; levelInWorld = 0; combo = 0
        powerLabel = ""; powerLeft = 0
        stickyT = 0; laserT = 0; wideT = 0; slowT = 0; wrapT = 0; gravityT = 0; shrinkT = 0
        shieldCharges = 0; debuffLabel = ""; shortT = 0; reverseT = 0; invisibleT = 0
        paddleW = 120f; paddleX = w / 2f - paddleW / 2f; paddlePrevX = paddleX
        balls.clear(); drops.clear(); lasers.clear(); particles.clear(); texts.clear(); rings.clear()
        traj.clear()
        brokenCnt = 0; paddleHits = 0; powersCaught = 0; maxCombo = 0
        missZones["left"] = 0; missZones["centre"] = 0; missZones["right"] = 0
        deathHist.clear()
        runUnlocks.clear(); runTip = ""; runRank = "E"
        recordX.clear(); replayActive = false; replayIdx = 0
        if (w > 0f && h > 0f) {
            buildLevel(1)
            spawnBall(paddleX + paddleW / 2f, paddleY - 30f)
            if (settings.value.assistLock) balls.firstOrNull()?.sticky = true
        }
        started = false; alive = true
        analyticsOpen = false
        onScore(0)
    }

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && bricks.isEmpty()) reset()
    }

    fun aliveBricksCount() = bricks.count { it.alive && it.kind != BKind.METAL }

    fun pushFloat(x: Float, y: Float, txt: String, c: Color) {
        texts.add(CFloat(x, y, 0, 36, txt, c))
    }
    fun pushRing(x: Float, y: Float, c: Color, max: Float = 40f) {
        if (!settings.value.audioViz) return
        rings.add(CRing(x, y, 4f, max, c))
    }
    fun spawnFragments(x: Float, y: Float, hue: Float, n: Int = 12) {
        val nn = if (settings.value.reducedMotion) 4 else n
        for (i in 0 until nn) {
            val a = rand() * (PI * 2).toFloat(); val v = 1f + rand() * 3.2f
            particles.add(CPart(x, y, cos(a) * v, sin(a) * v - 0.6f, 28, 28,
                hsl(hue, 0.9f, 0.65f), 2f + rand() * 2f))
        }
    }
    fun spawnDrop(x: Float, y: Float) {
        val r = rand()
        val (kind, hue) = when {
            r < 0.08f -> {
                val dr = rand()
                Pair(if (dr < 0.34f) PKind.SHORT else if (dr < 0.67f) PKind.REVERSE else PKind.INVISIBLE, 0f)
            }
            r < 0.68f -> {
                val tr = rand()
                Pair(when {
                    tr < 0.2f -> PKind.LONGER
                    tr < 0.4f -> PKind.MULTI
                    tr < 0.6f -> PKind.SLOW
                    tr < 0.8f -> PKind.STICKY
                    else -> PKind.LASER
                }, 200f)
            }
            r < 0.93f -> {
                val tr = rand()
                Pair(when {
                    tr < 0.34f -> PKind.WRAP
                    tr < 0.67f -> PKind.GRAVITY
                    else -> PKind.SHIELD
                }, 280f)
            }
            else -> Pair(if (rand() < 0.5f) PKind.NUKE else PKind.SHRINK, 320f)
        }
        drops.add(CDrop(x, y, kind, hue))
    }
    fun screenNuke() {
        for (b in bricks) {
            if (b.alive && b.kind != BKind.METAL && b.kind != BKind.BOSS) {
                b.alive = false
                spawnFragments(b.x + b.w / 2f, b.y + b.h / 2f, b.hue)
                score += 10; brokenCnt += 1
            }
        }
        shakeT = 18; shakeMag = 8f
        pushRing(w / 2f, h / 2f, Color(0xFFFFB347).copy(alpha = 0.7f), 280f)
        onScore(score)
    }
    fun applyPower(kind: PKind) {
        powersCaught += 1
        var label = ""
        when (kind) {
            PKind.LONGER -> { paddleW = min(220f, paddleW + 30f); wideT = 60 * 10; label = "Longer" }
            PKind.MULTI -> {
                val b0 = balls.firstOrNull() ?: return
                for (i in 0 until 2) {
                    val ang = (rand() - 0.5f) * 0.6f - (PI / 2).toFloat()
                    balls.add(CBall(b0.x, b0.y, cos(ang) * baseSpeed, sin(ang) * baseSpeed, b0.r, false, b0.trailHue))
                }
                label = "Multi-ball"
            }
            PKind.SLOW -> { slowT = 60 * 10; label = "Slow ball" }
            PKind.STICKY -> { stickyT = 60 * 12; label = "Sticky" }
            PKind.LASER -> { laserT = 60 * 10; label = "Laser" }
            PKind.WRAP -> { wrapT = 60 * 10; label = "Wrap walls" }
            PKind.GRAVITY -> { gravityT = 60 * 8; label = "Gravity" }
            PKind.SHIELD -> { shieldCharges += 1; label = "Shield +1" }
            PKind.NUKE -> { screenNuke(); label = "NUKE"; say("Crusher") }
            PKind.SHRINK -> { shrinkT = 60 * 8; balls.forEach { it.r = 4f }; label = "Tiny ball" }
            PKind.SHORT -> { paddleW = max(60f, paddleW - 30f); shortT = 60 * 8; debuffLabel = "Short"; return }
            PKind.REVERSE -> { reverseT = 60 * 8; debuffLabel = "Reverse"; return }
            PKind.INVISIBLE -> { invisibleT = 60 * 8; debuffLabel = "Invisible"; return }
        }
        powerLabel = label
        powerLeft = max(wideT, max(slowT, max(stickyT, max(laserT, max(wrapT, max(gravityT, max(shrinkT, 60 * 6)))))))
        if (settings.value.haptics) vibrate(ctx, 20L)
        pushFloat(paddleX + paddleW / 2f, paddleY - 8f, label, Color(0xFFA78BFA))
        pushRing(paddleX + paddleW / 2f, paddleY, Color(0xFFA78BFA).copy(alpha = 0.6f))
    }
    fun heatWave(x: Float, y: Float) {
        for (o in bricks) {
            if (!o.alive || o.kind == BKind.METAL) continue
            val dx = o.x + o.w / 2f - x; val dy = o.y + o.h / 2f - y
            if (hypot(dx, dy) < 60f) o.hp = max(0, o.hp - 1)
            if (o.hp <= 0 && o.alive) {
                o.alive = false; spawnFragments(o.x + o.w / 2f, o.y + o.h / 2f, o.hue, 8)
                score += 10; brokenCnt += 1
            }
        }
        pushRing(x, y, Color(0xFFF87171).copy(alpha = 0.7f), 90f)
        onScore(score)
    }
    fun chainElectric(start: CBrick) {
        var cur = start; val visited = mutableSetOf(cur)
        for (step in 0 until 3) {
            var nearest: CBrick? = null; var nd = 999f
            for (o in bricks) {
                if (!o.alive || o.kind == BKind.METAL || o in visited) continue
                val d = hypot(cur.x - o.x, cur.y - o.y)
                if (d < nd && d < 90f) { nd = d; nearest = o }
            }
            val n = nearest ?: break
            visited.add(n)
            pushRing((cur.x + n.x) / 2f, (cur.y + n.y) / 2f, Color(0xFFFACC15).copy(alpha = 0.8f), 30f)
            n.hp = max(0, n.hp - 1)
            if (n.hp <= 0) { n.alive = false; spawnFragments(n.x + n.w / 2f, n.y + n.h / 2f, n.hue, 8); score += 10; brokenCnt += 1 }
            cur = n
        }
        onScore(score)
    }

    fun damageBrick(b: CBrick, x: Float, y: Float, ballRef: CBall? = null) {
        b.hp -= 1
        if (b.kind == BKind.METAL && b.hp > 1) b.hp = 99
        if (b.hp <= 0) {
            b.alive = false
            spawnFragments(x, y, b.hue, if (b.kind == BKind.BOSS) 36 else 14)
            pushRing(x, y, hsl(b.hue, 0.8f, 0.6f, 0.7f))
            score += if (b.kind == BKind.BOSS) 200 else 10 + (if (b.kind == BKind.FIRE) 4 else 0)
            brokenCnt += 1
            combo += 1
            maxCombo = max(maxCombo, combo)
            when (combo) {
                10 -> say("Good")
                25 -> say("Excellent")
                50 -> say("Crusher")
            }
            if (rand() < 0.18f) spawnDrop(x, y)
            when (b.kind) {
                BKind.FIRE -> heatWave(x, y)
                BKind.ICE -> { for (ba in balls) { ba.vx *= 0.6f; ba.vy *= 0.6f } ; pushRing(x, y, Color(0xFF7DD3FC).copy(alpha = 0.7f), 60f) }
                BKind.ELECTRIC -> chainElectric(b)
                BKind.REGEN -> b.regenAt = System.currentTimeMillis() + 3000
                BKind.BOSS -> { say("Boss down"); shakeT = 30; shakeMag = 10f }
                else -> { }
            }
            pushFloat(x, y, "+${if (b.kind == BKind.BOSS) 200 else 10}", hsl(b.hue, 0.8f, 0.7f))
            ballRef?.let { it.trailHue = b.hue }
            if (settings.value.haptics) vibrate(ctx, 10L)
            onScore(score)
        } else {
            score += 2; onScore(score)
        }
    }

    fun computeTrajectory() {
        if (!settings.value.ghostLine || balls.isEmpty()) { traj.clear(); return }
        val b = balls[0]
        var x = b.x; var y = b.y; var vx = b.vx; var vy = b.vy
        traj.clear()
        for (i in 0 until 60) {
            x += vx; y += vy
            if (x < b.r || x > w - b.r) vx = -vx
            if (y < b.r) vy = -vy
            if (y > paddleY - b.r) break
            if (i % 2 == 0) traj.add(Offset(x, y))
        }
    }

    fun rankFromStats(): String {
        val eff = brokenCnt.toFloat() / max(1, deathHist.size + 1)
        return when {
            eff > 35 -> "S"; eff > 25 -> "A"; eff > 18 -> "B"
            eff > 12 -> "C"; eff > 6 -> "D"; else -> "E"
        }
    }
    fun rankWeight(r: String) = listOf("E","D","C","B","A","S").indexOf(r)
    fun rankColor(r: String): Color = when (r) {
        "S" -> Color(0xFFFDE68A); "A" -> Color(0xFFA78BFA); "B" -> Color(0xFF60A5FA)
        "C" -> Color(0xFF22D3EE); "D" -> Color(0xFF94A3B8); else -> Color(0xFFCBD5E1)
    }

    fun endRun() {
        alive = false
        runRank = rankFromStats()
        if (rankWeight(runRank) > rankWeight(settings.value.bestRank)) settings.value = settings.value.copy(bestRank = runRank)
        // unlocks
        val s = settings.value
        val newPaddles = s.unlockedPaddles.toMutableList()
        val newTrails = s.unlockedTrails.toMutableList()
        val newArenas = s.unlockedArenas.toMutableList()
        val newMusic = s.unlockedMusic.toMutableList()
        if (brokenCnt >= 30 && "curved" !in newPaddles) { newPaddles.add("curved"); runUnlocks.add("Curved paddle") }
        if (maxCombo >= 25 && "rainbow" !in newTrails) { newTrails.add("rainbow"); runUnlocks.add("Rainbow trail") }
        if (level >= 5 && (s.world + 1) !in newArenas && s.world + 1 < 8) { newArenas.add(s.world + 1); runUnlocks.add("World ${s.world + 2}") }
        if (powersCaught >= 8 && "chiptune" !in newMusic) { newMusic.add("chiptune"); runUnlocks.add("Chiptune") }
        if (brokenCnt >= 60 && "twin" !in newPaddles) { newPaddles.add("twin"); runUnlocks.add("Twin paddle") }
        val shards = s.shards + (brokenCnt / 4 + maxCombo / 5 + powersCaught)
        // tip
        val worst = missZones.maxByOrNull { it.value }?.key ?: "centre"
        runTip = if (worst == "centre") "Most misses dead-centre — let the paddle catch up."
                 else "Most misses on the $worst — track the ball there sooner."
        // save replay & shards
        settings.value = s.copy(
            unlockedPaddles = newPaddles, unlockedTrails = newTrails,
            unlockedArenas = newArenas, unlockedMusic = newMusic,
            shards = shards,
            ghostBest = recordX.takeLast(1800),
            deaths = min(6, s.deaths + 1),
        )
        save()
        scope.launch { delay(if (settings.value.reducedMotion) 200L else 500L); analyticsOpen = true }
    }

    fun nextLevel() {
        settings.value = settings.value.copy(clears = min(8, settings.value.clears + 1))
        score += 100
        level += 1
        levelInWorld += 1
        if (levelInWorld >= 4) {
            levelInWorld = 0
            settings.value = settings.value.copy(world = (settings.value.world + 1) % 8)
            pushFloat(w / 2f, h / 2f, "World ${settings.value.world + 1} →", Color(0xFFFDE68A))
            say("New world")
        }
        buildLevel(level)
        balls.clear()
        spawnBall(paddleX + paddleW / 2f, paddleY - 30f)
        if (settings.value.assistLock) balls.firstOrNull()?.sticky = true
        started = false
        save()
    }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        var lastTs = System.currentTimeMillis()
        var acc = 0f
        val FIXED_DT = 1000f / 240f
        while (true) {
            delay(8)
            val now = System.currentTimeMillis()
            val dt = (now - lastTs).coerceAtMost(64L).toFloat()
            lastTs = now
            if (paused || settingsOpen || analyticsOpen) continue
            if (!alive) continue
            acc += dt
            var safety = 0
            while (acc >= FIXED_DT && alive && safety < 24) {
                acc -= FIXED_DT
                safety += 1
                if (replayActive) {
                    if (replayIdx >= replayBuf.size) { replayActive = false; analyticsOpen = false; reset(); break }
                    paddleX = replayBuf[replayIdx]; replayIdx += 1
                } else {
                    if (settings.value.assistAim && balls.isNotEmpty()) {
                        val b = balls[0]; val tx = b.x - paddleW / 2f
                        paddleX += (tx - paddleX) * 0.04f
                        paddleX = paddleX.coerceIn(0f, w - paddleW)
                    }
                    paddleVx = paddleX - paddlePrevX; paddlePrevX = paddleX
                    recordX.add(paddleX); if (recordX.size > 1800) recordX.removeAt(0)
                }

                // brick movement & regen
                for (b in bricks) {
                    if (!b.alive) {
                        if (b.kind == BKind.REGEN && b.regenAt > 0L && System.currentTimeMillis() > b.regenAt) {
                            b.alive = true; b.hp = b.maxHp; b.regenAt = 0L
                        }
                        continue
                    }
                    if (b.vx != 0f) { b.x += b.vx; if (b.x < 10f || b.x + b.w > w - 10f) b.vx = -b.vx }
                    b.rotPhase += 0.04f
                }
                if (cinematicSlowT > 0) cinematicSlowT -= 1
                val slowMul = (if (slowT > 0) 0.65f else 1f) * (if (cinematicSlowT > 0) 0.6f else 1f)

                if (started || replayActive) {
                    val itB = balls.iterator()
                    val toRemove = mutableListOf<CBall>()
                    while (itB.hasNext()) {
                        val ball = itB.next()
                        if (ball.sticky) {
                            ball.x = paddleX + paddleW / 2f
                            ball.y = paddleY - ball.r - 1f
                            continue
                        }
                        if (gravityT > 0) ball.vy += 0.012f
                        var nx = ball.x + ball.vx * slowMul
                        var ny = ball.y + ball.vy * slowMul
                        if (wrapT > 0) {
                            if (nx < 0f) nx += w; if (nx > w) nx -= w
                        } else {
                            if (nx < ball.r) { nx = ball.r; ball.vx = -ball.vx }
                            if (nx > w - ball.r) { nx = w - ball.r; ball.vx = -ball.vx }
                        }
                        if (ny < ball.r) { ny = ball.r; ball.vy = -ball.vy }
                        if (ny > paddleY - ball.r && ny < paddleY + paddleH && nx > paddleX && nx < paddleX + paddleW && ball.vy > 0) {
                            ny = paddleY - ball.r
                            ball.vy = -abs(ball.vy)
                            val off = (nx - (paddleX + paddleW / 2f)) / (paddleW / 2f)
                            val sp = hypot(ball.vx, ball.vy)
                            ball.vx = off * max(sp, baseSpeed) + (if (settings.value.spin) paddleVx * 0.4f else 0f)
                            val sp2 = hypot(ball.vx, ball.vy)
                            if (sp2 > 0f) { val k = sp / sp2; ball.vx *= k; ball.vy *= k }
                            val minVy = (PI / 90f).toFloat() * sp
                            if (abs(ball.vy) < minVy) ball.vy = (if (ball.vy < 0) -1f else 1f) * minVy
                            paddleHits += 1
                            if (settings.value.haptics) vibrate(ctx, 6L)
                            pushRing(nx, paddleY, Color(0xFF60A5FA).copy(alpha = 0.6f), 28f)
                            if (calActive) {
                                calReact.add(System.currentTimeMillis() - calStart)
                                calStart = System.currentTimeMillis()
                                if (calReact.size >= 5) {
                                    val avg = calReact.average().toFloat()
                                    settings.value = settings.value.copy(mouseSmoothing = max(0, min(100, (100 - avg / 8f).toInt())))
                                    save(); calActive = false
                                    pushFloat(paddleX + paddleW / 2f, paddleY - 12f, "Sens ${settings.value.mouseSmoothing}", Color(0xFF22D3EE))
                                }
                            }
                        } else if (ny > h + 20f) {
                            val zone = if (ball.x < w / 3f) "left" else if (ball.x < 2 * w / 3f) "centre" else "right"
                            missZones[zone] = (missZones[zone] ?: 0) + 1
                            toRemove.add(ball); continue
                        }
                        // brick collisions
                        for (b in bricks) {
                            if (!b.alive) continue
                            if (nx + ball.r > b.x && nx - ball.r < b.x + b.w && ny + ball.r > b.y && ny - ball.r < b.y + b.h) {
                                val ovL = nx + ball.r - b.x
                                val ovR = b.x + b.w - (nx - ball.r)
                                val ovT = ny + ball.r - b.y
                                val ovB = b.y + b.h - (ny - ball.r)
                                val mn = minOf(ovL, ovR, ovT, ovB)
                                if (mn == ovL || mn == ovR) ball.vx = -ball.vx else ball.vy = -ball.vy
                                if (settings.value.world == 1 && rand() < 0.5f) {
                                    val a = (rand() - 0.5f) * 0.6f
                                    val sp = hypot(ball.vx, ball.vy)
                                    val cur = atan2(ball.vy, ball.vx) + a
                                    ball.vx = cos(cur) * sp; ball.vy = sin(cur) * sp
                                }
                                if (b.kind != BKind.METAL) damageBrick(b, b.x + b.w / 2f, b.y + b.h / 2f, ball)
                                if (aliveBricksCount() == 1) cinematicSlowT = 60
                                break
                            }
                        }
                        ball.x = nx; ball.y = ny
                    }
                    balls.removeAll(toRemove)
                    if (balls.isEmpty() && started) {
                        lives -= 1
                        if (settings.value.haptics) vibrate(ctx, 40L)
                        deathHist.add(score)
                        if (lives <= 0) { say("Uh oh"); endRun(); break }
                        spawnBall(paddleX + paddleW / 2f, paddleY - 30f)
                        if (settings.value.assistLock) balls.firstOrNull()?.sticky = true
                        started = false; combo = 0
                    }
                }
                // drops
                val itD = drops.iterator()
                while (itD.hasNext()) {
                    val d = itD.next()
                    d.y += 2f
                    val dx = (paddleX + paddleW / 2f) - d.x
                    if (abs(d.y - paddleY) < 80f) d.x += if (dx > 0) min(0.6f, abs(dx) * 0.04f) else -min(0.6f, abs(dx) * 0.04f)
                    if (d.y > paddleY && d.y < paddleY + paddleH && d.x > paddleX && d.x < paddleX + paddleW) {
                        applyPower(d.kind); itD.remove()
                    } else if (d.y > h + 20f) itD.remove()
                }
                // lasers
                if (laserT > 0 && System.currentTimeMillis() - lastLaserShot > 240L) {
                    lastLaserShot = System.currentTimeMillis()
                    lasers.add(CLaser(paddleX + 8f, paddleY))
                    lasers.add(CLaser(paddleX + paddleW - 8f, paddleY))
                }
                val itL = lasers.iterator()
                while (itL.hasNext()) {
                    val l = itL.next()
                    l.y -= 8f
                    var hit = false
                    for (b in bricks) {
                        if (!b.alive) continue
                        if (l.x > b.x && l.x < b.x + b.w && l.y < b.y + b.h && l.y > b.y - 6f) {
                            if (b.kind != BKind.METAL) damageBrick(b, l.x, l.y)
                            hit = true; break
                        }
                    }
                    if (hit || l.y < -10f) itL.remove()
                }
                // particles, texts, rings
                run {
                    val it = particles.iterator()
                    while (it.hasNext()) { val p = it.next(); p.x += p.vx; p.y += p.vy; p.vy += 0.06f; p.life -= 1; if (p.life <= 0) it.remove() }
                }
                run {
                    val it = texts.iterator()
                    while (it.hasNext()) { val t = it.next(); t.t += 1; t.y -= 0.4f; if (t.t >= t.max) it.remove() }
                }
                run {
                    val it = rings.iterator()
                    while (it.hasNext()) { val r = it.next(); r.r += (r.max - r.r) * 0.12f; if (r.r > r.max - 1f) it.remove() }
                }
                // timers
                if (wideT > 0) wideT -= 1 else if (paddleW > 120f && shortT == 0) paddleW = 120f
                if (shortT > 0) shortT -= 1 else if (paddleW < 120f && wideT == 0) paddleW = 120f
                if (slowT > 0) slowT -= 1
                if (stickyT > 0) stickyT -= 1
                if (laserT > 0) laserT -= 1
                if (wrapT > 0) wrapT -= 1
                if (gravityT > 0) gravityT -= 1
                if (shrinkT > 0) shrinkT -= 1 else if (balls.isNotEmpty() && balls.first().r == 4f) balls.forEach { it.r = 7f }
                if (reverseT > 0) reverseT -= 1
                if (invisibleT > 0) invisibleT -= 1
                if (powerLeft > 0) powerLeft -= 1 else powerLabel = ""
                if (shakeT > 0) shakeT -= 1
                trajCacheT += 1
                if (trajCacheT >= 4) { trajCacheT = 0; computeTrajectory() }
                if (aliveBricksCount() == 0 && bricks.isNotEmpty()) nextLevel()
            }
        }
    }

    val s = settings.value
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(alive, paused, settingsOpen, analyticsOpen, replayActive) {
                if (replayActive) return@pointerInput
                detectDragGestures(
                    onDragStart = { if (!started && alive && !settingsOpen && !analyticsOpen) started = true; balls.forEach { it.sticky = false } },
                ) { change, _ ->
                    if (settingsOpen || analyticsOpen) return@detectDragGestures
                    val x = change.position.x
                    val target = (x - paddleW / 2f).coerceIn(0f, w - paddleW)
                    val k = if (s.mouseSmoothing == 0) 1f else (1f - s.mouseSmoothing / 110f)
                    paddleX = paddleX + (target - paddleX) * k
                }
            }
            .pointerInput(alive, paused, settingsOpen, analyticsOpen) {
                detectTapGestures(onTap = {
                    if (!started && alive && !settingsOpen && !analyticsOpen) { started = true; balls.forEach { it.sticky = false } }
                })
            }
        ) {
            w = size.width; h = size.height
            // background by world
            val bg = when (s.world) {
                0 -> Pair(Color(0xFF0B0E1F), Color(0xFF1F2937))
                1 -> Pair(Color(0xFF020617), Color(0xFF0F172A))
                2 -> Pair(Color(0xFF311B92), Color(0xFF1A0B3D))
                3 -> Pair(Color(0xFF0F172A), Color(0xFF1E3A8A))
                4 -> Pair(Color(0xFF0B132B), Color(0xFF3B0764))
                5 -> Pair(Color(0xFF020617), Color(0xFF020617))
                6 -> Pair(Color(0xFF3A0A0A), Color(0xFF7F1D1D))
                else -> Pair(Color.Black, Color(0xFF1E293B))
            }
            drawRect(Brush.verticalGradient(listOf(applyCB(bg.first, s.colourblind), applyCB(bg.second, s.colourblind))), size = size)
            // grid
            val gc = if (s.highContrast) Color.White.copy(0.35f) else Color.White.copy(0.06f)
            val off = (System.currentTimeMillis() / 60L % 30L).toFloat()
            var gx = -off
            while (gx < w) { drawLine(gc, Offset(gx, 0f), Offset(gx, h), 1f); gx += 30f }
            var gy = -off
            while (gy < h) { drawLine(gc, Offset(0f, gy), Offset(w, gy), 1f); gy += 30f }
            // shake
            val shx = if (shakeT > 0 && !s.reducedMotion) (rand() - 0.5f) * shakeMag else 0f
            val shy = if (shakeT > 0 && !s.reducedMotion) (rand() - 0.5f) * shakeMag else 0f
            androidx.compose.ui.graphics.drawscope.translate(shx, shy) {
            // bricks
            for (b in bricks) {
                if (!b.alive) {
                    if (b.kind == BKind.REGEN && b.regenAt > 0L) {
                        val left = ((b.regenAt - System.currentTimeMillis()) / 3000f).coerceIn(0f, 1f)
                        drawRect(hsl(280f, 0.8f, 0.6f, 0.3f + (1f - left) * 0.5f),
                            Offset(b.x, b.y), Size(b.w, b.h), style = Stroke(1f))
                    }
                    continue
                }
                val hpRatio = b.hp.toFloat() / max(1, b.maxHp)
                when (b.kind) {
                    BKind.METAL -> {
                        drawRect(applyCB(Color(0xFF94A3B8), s.colourblind), Offset(b.x, b.y), Size(b.w, b.h))
                        drawRect(Color.White.copy(0.18f), Offset(b.x + 2f, b.y + 2f), Size(b.w - 4f, 3f))
                    }
                    BKind.BOSS -> {
                        val hue = 320f + sin(b.rotPhase * 2f) * 20f
                        drawRect(applyCB(hsl(hue, 0.75f, 0.55f), s.colourblind), Offset(b.x, b.y), Size(b.w, b.h))
                        drawRect(Color(0xFFFDE68A), Offset(b.x, b.y), Size(b.w, b.h), style = Stroke(2f))
                        drawCircle(Color(0xFFFDE68A).copy(alpha = 0.4f + sin(b.rotPhase * 3f) * 0.4f),
                            radius = 12f, center = Offset(b.x + b.w / 2f, b.y + b.h / 2f))
                        drawRect(Color.Black.copy(0.5f), Offset(b.x, b.y - 6f), Size(b.w, 4f))
                        drawRect(Color(0xFFF43F5E), Offset(b.x, b.y - 6f), Size(b.w * hpRatio, 4f))
                    }
                    else -> {
                        var hue = b.hue
                        if (b.kind == BKind.FIRE) hue = 14f + sin(b.rotPhase * 4f) * 8f
                        else if (b.kind == BKind.ICE) hue = 200f
                        else if (b.kind == BKind.ELECTRIC) hue = 50f + sin(b.rotPhase * 6f) * 8f
                        val light = if (s.highContrast) 0.7f else (0.5f + (1f - hpRatio) * 0.1f)
                        drawRect(applyCB(hsl(hue, 0.8f, light), s.colourblind), Offset(b.x, b.y), Size(b.w, b.h))
                        if (b.maxHp > 1 && b.hp < b.maxHp) {
                            drawLine(Color.Black.copy(0.5f), Offset(b.x + 2f, b.y + b.h - 4f), Offset(b.x + b.w - 4f, b.y + 4f), 1f)
                        }
                        if (!s.reducedMotion && (b.kind == BKind.FIRE || b.kind == BKind.ELECTRIC || b.kind == BKind.ICE)) {
                            drawRect(hsl(hue, 0.9f, 0.7f, 0.18f), Offset(b.x - 2f, b.y - 2f), Size(b.w + 4f, b.h + 4f))
                        }
                        if (b.kind == BKind.ELECTRIC) {
                            val p = androidx.compose.ui.graphics.Path().apply {
                                moveTo(b.x + 4f, b.y + b.h / 2f); lineTo(b.x + b.w / 2f, b.y + 2f); lineTo(b.x + b.w - 4f, b.y + b.h / 2f)
                            }
                            drawPath(p, Color(0xFFFACC15), style = Stroke(1f))
                        }
                    }
                }
            }
            // trajectory ghost
            if (s.ghostLine && traj.isNotEmpty() && balls.isNotEmpty()) {
                val b0 = balls[0]
                val pe = PathEffect.dashPathEffect(floatArrayOf(2f, 4f), 0f)
                var prev = Offset(b0.x, b0.y)
                for (p in traj) { drawLine(Color(0xFF60A5FA).copy(0.6f), prev, p, 1f, pathEffect = pe); prev = p }
            }
            // paddle
            run {
                val mat = when (s.paddleMat) { "carbon" -> Color(0xFF1F2937); "glass" -> Color.White.copy(0.6f); else -> Color.White }
                val fill = if (laserT > 0) Color(0xFFEF4444) else mat
                if (s.paddleShape == "twin") {
                    val half = (paddleW - 18f) / 2f
                    drawRect(fill, Offset(paddleX, paddleY), Size(half, paddleH))
                    drawRect(fill, Offset(paddleX + half + 18f, paddleY), Size(half, paddleH))
                } else {
                    drawRect(fill, Offset(paddleX, paddleY), Size(paddleW, paddleH))
                    if (s.paddleShape == "curved") {
                        drawCircle(fill, radius = paddleH / 2f, center = Offset(paddleX, paddleY + paddleH / 2f))
                        drawCircle(fill, radius = paddleH / 2f, center = Offset(paddleX + paddleW, paddleY + paddleH / 2f))
                    }
                }
                if (s.paddleMat == "neon") {
                    drawRect(Color(0xFFA78BFA).copy(0.3f), Offset(paddleX - 2f, paddleY - 2f), Size(paddleW + 4f, paddleH + 4f), style = Stroke(2f))
                }
                drawRect(Color.White.copy(0.18f), Offset(paddleX, paddleY + paddleH + 4f), Size(paddleW, 6f))
                if (shieldCharges > 0) drawCircle(Color(0xFF60A5FA), radius = paddleW / 2f + 8f,
                    center = Offset(paddleX + paddleW / 2f, paddleY + paddleH / 2f), style = Stroke(2f))
            }
            // balls
            for (b in balls) {
                if (!s.reducedMotion) {
                    for (i in 1..6) {
                        val tx = b.x - b.vx * i * 0.6f; val ty = b.y - b.vy * i * 0.6f
                        val hue = when (s.ballTrail) {
                            "rainbow" -> (b.trailHue + i * 25f) % 360f
                            "golden" -> 50f
                            "electric" -> 220f
                            else -> b.trailHue
                        }
                        drawCircle(hsl(hue, 0.9f, 0.65f, 0.3f - i * 0.04f), radius = b.r * (1f - i * 0.12f), center = Offset(tx, ty))
                    }
                }
                drawCircle(Color.White, radius = b.r, center = Offset(b.x, b.y))
                drawCircle(hsl(b.trailHue, 0.9f, 0.65f, 0.5f), radius = b.r + 2f, center = Offset(b.x, b.y), style = Stroke(1f))
            }
            // drops
            for (d in drops) {
                drawCircle(applyCB(hsl(d.hue, 0.8f, 0.6f), s.colourblind), radius = 8f, center = Offset(d.x, d.y))
                drawCircle(Color.White, radius = 8f, center = Offset(d.x, d.y), style = Stroke(1f))
            }
            // lasers
            for (l in lasers) drawRect(Color(0xFFEF4444), Offset(l.x - 1f, l.y - 8f), Size(2f, 8f))
            // particles
            for (p in particles) {
                val a = (p.life.toFloat() / p.max).coerceIn(0f, 1f)
                drawRect(p.col.copy(alpha = a), Offset(p.x, p.y), Size(p.size, p.size))
            }
            // floats - tiny lines (no native text in Canvas easily, render as thin arrow)
            for (t in texts) {
                val a = 1f - t.t.toFloat() / t.max
                drawCircle(t.col.copy(alpha = a * 0.9f), radius = 2f, center = Offset(t.x, t.y))
            }
            // rings
            for (r in rings) {
                val a = (1f - r.r / r.max).coerceIn(0f, 1f)
                drawCircle(r.col.copy(alpha = a), radius = r.r, center = Offset(r.x, r.y), style = Stroke(2f))
            }
            // bottom death-zone
            drawRect(Color(0xFFEF4444).copy(0.06f), Offset(0f, paddleY + 18f), Size(w, h - paddleY - 18f))
            if (cinematicSlowT > 0) drawRect(Color.Black.copy(0.18f), Offset(0f, 0f), Size(w, h))
            if (invisibleT > 0) drawRect(Color.Black.copy(0.85f), Offset(0f, 0f), Size(w, h))
            } // end translate block
        }

        // top HUD
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChipText("W${s.world + 1}·L${level} · ${WORLD_LABELS[s.world]}", Color(0xCC000000), Color.White)
            Spacer(modifier = Modifier.width(4.dp))
            ChipText("${aliveBricksCount()} bricks", Color(0xCC000000), Color.White)
            if (combo > 1) { Spacer(modifier = Modifier.width(4.dp)); ChipText("x$combo", Color(0xCC22D3EE), Color.Black) }
            Spacer(modifier = Modifier.width(4.dp))
            ChipText("♥ $lives", Color(0xCC000000), Color.White)
            if (powerLabel.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                ChipText("$powerLabel ${powerLeft / 60}s", Color(0xCC6366F1), Color.White)
            }
            if (debuffLabel.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                ChipText("⚠ $debuffLabel", Color(0xCCEF4444), Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            ChipText("◇ ${s.shards}", Color(0xCC000000), Color(0xFFFACC15))
            Spacer(modifier = Modifier.width(4.dp))
            Surface(color = Color(0x99000000), shape = RoundedCornerShape(50),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settingsOpen = true }) {
                Text("⚙", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        if (!started && alive && !settingsOpen && !analyticsOpen) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Surface(color = Color(0x99000000), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tap to launch", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Drag = paddle · ⚙ for everything", color = Color.White.copy(0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            BrickSettingsSheet(
                settings = settings.value,
                onChange = { settings.value = it; save() },
                onCalibrate = { calActive = true; calStart = System.currentTimeMillis(); calReact.clear(); settingsOpen = false; reset(); started = true },
                onReplay = {
                    if (settings.value.ghostBest.isNotEmpty()) {
                        replayBuf.clear(); replayBuf.addAll(settings.value.ghostBest)
                        replayActive = true; replayIdx = 0; settingsOpen = false; reset(); started = true
                    }
                },
                onClose = { settingsOpen = false },
            )
        }
        AnimatedVisibility(visible = analyticsOpen, enter = fadeIn(), exit = fadeOut()) {
            BrickAnalyticsSheet(
                score = score, rank = runRank, rankCol = rankColor(runRank),
                broken = brokenCnt, paddleHits = paddleHits, powers = powersCaught, maxCombo = maxCombo,
                deaths = deathHist.toList(), misses = missZones.toMap(),
                tip = runTip, unlocks = runUnlocks.toList(),
                onReplay = { analyticsOpen = false; reset() },
                onContinue = { analyticsOpen = false; onGameOver() },
            )
        }
    }
}

@Composable
private fun ChipText(label: String, bg: Color, fg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun BrickSettingsSheet(
    settings: BrickSettings, onChange: (BrickSettings) -> Unit,
    onCalibrate: () -> Unit, onReplay: () -> Unit, onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }) {
        Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.94f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Crusher · Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 18.sp,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("World", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(WORLD_LABELS, WORLD_LABELS[settings.world]) { i ->
                    onChange(settings.copy(world = WORLD_LABELS.indexOf(i).coerceAtLeast(0)))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Paddle shape", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("classic", "curved", "twin"), settings.paddleShape) { onChange(settings.copy(paddleShape = it)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Paddle material", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("neon", "carbon", "glass"), settings.paddleMat) { onChange(settings.copy(paddleMat = it)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ball trail", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("comet", "rainbow", "golden", "electric"), settings.ballTrail) { onChange(settings.copy(ballTrail = it)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Music genre", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("synthwave", "chiptune", "lofi", "industrial"), settings.music) { onChange(settings.copy(music = it)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Colourblind", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("off", "protanopia", "deuteranopia", "tritanopia"), settings.colourblind) { onChange(settings.copy(colourblind = it)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Mouse smoothing: ${settings.mouseSmoothing}", color = Color.White, fontSize = 12.sp)
                Slider(value = settings.mouseSmoothing.toFloat(), onValueChange = { onChange(settings.copy(mouseSmoothing = it.toInt())) },
                    valueRange = 0f..100f, steps = 19)
                Text("Keyboard ramp: ${settings.kbRamp} ms", color = Color.White, fontSize = 12.sp)
                Slider(value = settings.kbRamp.toFloat(), onValueChange = { onChange(settings.copy(kbRamp = it.toInt())) },
                    valueRange = 0f..600f, steps = 29)
                Text("UI scale: ${settings.uiScale}%", color = Color.White, fontSize = 12.sp)
                Slider(value = settings.uiScale.toFloat(), onValueChange = { onChange(settings.copy(uiScale = it.toInt())) },
                    valueRange = 80f..150f, steps = 13)
                Spacer(modifier = Modifier.height(4.dp))
                ToggleRow("Trajectory ghost line", settings.ghostLine) { onChange(settings.copy(ghostLine = it)) }
                ToggleRow("Realistic ball spin (paddle velocity)", settings.spin) { onChange(settings.copy(spin = it)) }
                ToggleRow("Haptics", settings.haptics) { onChange(settings.copy(haptics = it)) }
                ToggleRow("Reduced motion", settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
                ToggleRow("High contrast", settings.highContrast) { onChange(settings.copy(highContrast = it)) }
                ToggleRow("Voice announcer", settings.announcer) { onChange(settings.copy(announcer = it)) }
                ToggleRow("Audio visualiser rings", settings.audioViz) { onChange(settings.copy(audioViz = it)) }
                ToggleRow("Assist · ball locks to paddle on respawn", settings.assistLock) { onChange(settings.copy(assistLock = it)) }
                ToggleRow("Assist · paddle auto-aim", settings.assistAim) { onChange(settings.copy(assistAim = it)) }
                ToggleRow("Assist · infinite lives (no rank)", settings.assistInfinite) { onChange(settings.copy(assistInfinite = it)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Best rank: ${settings.bestRank} · Shards: ${settings.shards} · Adaptive deaths: ${settings.deaths}, clears: ${settings.clears}",
                    color = Color.White.copy(0.7f), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Surface(color = Color(0xFF6366F1), shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCalibrate() }) {
                        Text("Calibrate", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = if (settings.ghostBest.isNotEmpty()) Color(0x55FFFFFF) else Color(0x22FFFFFF),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            enabled = settings.ghostBest.isNotEmpty()
                        ) { onReplay() }) {
                        Text("▶ Watch replay", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipsRow(items: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column {
            val rows = items.chunked(4)
            for (row in rows) {
                Row {
                    for (id in row) {
                        Surface(color = if (selected == id) Color(0xFF6366F1) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.padding(end = 6.dp, top = 6.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null
                            ) { onPick(id) }) {
                            Text(id, color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = value, onCheckedChange = onChange)
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun BrickAnalyticsSheet(
    score: Int, rank: String, rankCol: Color,
    broken: Int, paddleHits: Int, powers: Int, maxCombo: Int,
    deaths: List<Int>, misses: Map<String, Int>,
    tip: String, unlocks: List<String>,
    onReplay: () -> Unit, onContinue: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.94f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Run analysis · $score pts", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Tile("Rank", rank, Modifier.weight(1f), valueCol = rankCol)
                    Spacer(modifier = Modifier.width(6.dp))
                    Tile("Bricks", "$broken", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Tile("Bounces", "$paddleHits", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    Tile("Power-ups", "$powers", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Tile("Max combo", "$maxCombo", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Tile("Effic.", String.format("%.2f/b", broken.toFloat() / max(1, paddleHits)), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Miss heatmap", color = Color.White.copy(0.7f), fontSize = 11.sp)
                val total = max(1, misses.values.sum())
                misses.forEach { (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(k, color = Color.White, fontSize = 11.sp, modifier = Modifier.width(60.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
                            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(v.toFloat() / total)
                                .background(Brush.horizontalGradient(listOf(Color(0xFFFB7185), Color(0xFFF59E0B)))))
                        }
                        Text("  $v", color = Color.White, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Death-score history", color = Color.White.copy(0.7f), fontSize = 11.sp)
                Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).background(Color(0x14FFFFFF))) {
                    if (deaths.size < 2) return@Canvas
                    val mx = max(1f, (deaths.maxOrNull() ?: 1).toFloat())
                    var prev = Offset(0f, size.height - (deaths[0] / mx) * size.height)
                    for (i in 1 until deaths.size) {
                        val xo = (i.toFloat() / (deaths.size - 1)) * size.width
                        val yo = size.height - (deaths[i] / mx) * size.height
                        drawLine(Color(0xFF60A5FA), prev, Offset(xo, yo), 1.6f)
                        prev = Offset(xo, yo)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = Color(0x3360A5FA), shape = RoundedCornerShape(10.dp)) {
                    Text(tip, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
                if (unlocks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color(0x55FACC15), shape = RoundedCornerShape(10.dp)) {
                        Text("🎉 Unlocked: ${unlocks.joinToString()}", color = Color(0xFFFDE68A),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Surface(color = Color(0xFFF59E0B), shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onReplay() }) {
                        Text("↺ Instant replay", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onContinue() }) {
                        Text("Continue", color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, valueCol: Color = Color.White) {
    Surface(color = Color(0x14FFFFFF), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp)
            Text(value, color = valueCol, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

