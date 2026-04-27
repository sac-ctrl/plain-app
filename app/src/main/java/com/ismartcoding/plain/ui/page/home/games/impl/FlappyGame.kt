package com.ismartcoding.plain.ui.page.home.games.impl

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.FlappySettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private data class Pipe(
    var x: Float,
    var gapY: Float,
    val gapH: Float,
    var passed: Boolean = false,
    val moving: Boolean = false,
    val baseY: Float = gapY,
    val phase: Float = 0f,
    val intangible: Boolean = false,
)

private data class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Int, val maxLife: Int,
    val color: Color, val size: Float,
)

private data class Fragment(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Int, var ang: Float, val va: Float,
)

private data class FlappySettings(
    var sensitivity: Float = 1.0f,
    var holdMode: Boolean = false,
    var haptics: Boolean = true,
    var reducedMotion: Boolean = false,
    var screenPulse: Boolean = false,
    var colorblind: String = "off", // off / protanopia / deuteranopia / tritanopia
    var flavour: String = "vanilla", // vanilla / rocket / ghost / magnetic
    var assistGhost: Boolean = false,
    var autoFlap: Boolean = false,
    var unlockedRocket: Boolean = false,
    var unlockedGhost: Boolean = false,
    var unlockedMagnetic: Boolean = false,
    var mmr: Float = 50f,
)

private fun loadSettingsJson(json: String): FlappySettings {
    val s = FlappySettings()
    if (json.isBlank() || json == "{}") return s
    try {
        val o = JSONObject(json)
        s.sensitivity = o.optDouble("sensitivity", 1.0).toFloat()
        s.holdMode = o.optBoolean("holdMode", false)
        s.haptics = o.optBoolean("haptics", true)
        s.reducedMotion = o.optBoolean("reducedMotion", false)
        s.screenPulse = o.optBoolean("screenPulse", false)
        s.colorblind = o.optString("colorblind", "off")
        s.flavour = o.optString("flavour", "vanilla")
        s.assistGhost = o.optBoolean("assistGhost", false)
        s.autoFlap = o.optBoolean("autoFlap", false)
        s.unlockedRocket = o.optBoolean("unlockedRocket", false)
        s.unlockedGhost = o.optBoolean("unlockedGhost", false)
        s.unlockedMagnetic = o.optBoolean("unlockedMagnetic", false)
        s.mmr = o.optDouble("mmr", 50.0).toFloat()
    } catch (_: Exception) { }
    return s
}

private fun toJson(s: FlappySettings): String {
    val o = JSONObject()
    o.put("sensitivity", s.sensitivity.toDouble())
    o.put("holdMode", s.holdMode)
    o.put("haptics", s.haptics)
    o.put("reducedMotion", s.reducedMotion)
    o.put("screenPulse", s.screenPulse)
    o.put("colorblind", s.colorblind)
    o.put("flavour", s.flavour)
    o.put("assistGhost", s.assistGhost)
    o.put("autoFlap", s.autoFlap)
    o.put("unlockedRocket", s.unlockedRocket)
    o.put("unlockedGhost", s.unlockedGhost)
    o.put("unlockedMagnetic", s.unlockedMagnetic)
    o.put("mmr", s.mmr.toDouble())
    return o.toString()
}

private fun applyColorblind(c: Color, mode: String): Color {
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

private fun vibrate(ctx: Context, ms: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    } catch (_: Throwable) { }
}

@Composable
fun FlappyGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load persisted per-game settings once
    val settings = remember { mutableStateOf(FlappySettings()) }
    LaunchedEffect(Unit) {
        val json = FlappySettingsJsonPreference.getAsync(context)
        settings.value = loadSettingsJson(json)
    }
    fun saveSettings() {
        scope.launch {
            FlappySettingsJsonPreference.putAsync(context, toJson(settings.value))
        }
    }

    val baseGravity = when (difficulty) { "Easy" -> 0.42f; "Hard" -> 0.58f; "Insane" -> 0.68f; else -> 0.50f }
    val baseJump = when (difficulty) { "Easy" -> -7.0f; "Hard" -> -7.8f; "Insane" -> -8.2f; else -> -7.4f }
    val basePipeSpeed = when (difficulty) { "Easy" -> 1.9f; "Hard" -> 2.7f; "Insane" -> 3.2f; else -> 2.3f }
    val baseGap = when (difficulty) { "Easy" -> 220f; "Hard" -> 150f; "Insane" -> 130f; else -> 180f }
    val baseSpawn = when (difficulty) { "Easy" -> 1900f; "Hard" -> 1500f; "Insane" -> 1300f; else -> 1700f }

    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }

    // game state
    val pipes = remember { mutableStateListOf<Pipe>() }
    val particles = remember { mutableStateListOf<Particle>() }
    val fragments = remember { mutableStateListOf<Fragment>() }
    var birdY by remember { mutableStateOf(0f) }
    var vel by remember { mutableStateOf(0f) }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var frameIdx by remember { mutableStateOf(0) }
    var spawnTimer by remember { mutableStateOf(-800f) }
    var bgOffset by remember { mutableStateOf(0f) }
    var holdSince by remember { mutableStateOf(0L) }
    var holdFatigue by remember { mutableStateOf(0f) }
    var shakeT by remember { mutableStateOf(0) }
    var slowMo by remember { mutableStateOf(0) }
    var pulse by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    val tapsHeights = remember { mutableStateListOf<Float>() }
    val heightSamples = remember { mutableStateListOf<Float>() }
    var crashInfo by remember { mutableStateOf("") }
    var inFocus by remember { mutableStateOf(false) }
    var focusUntil by remember { mutableStateOf(0L) }
    var runStartMs by remember { mutableStateOf(0L) }
    val unlockedNow = remember { mutableStateListOf<String>() }
    val rng = remember { Random(System.currentTimeMillis()) }

    // initialise positions when canvas size known
    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && pipes.isEmpty()) {
            birdY = h / 2
        }
    }

    fun resolvedCfg(): FloatArray {
        val mmrSkew = (settings.value.mmr - 50f) / 100f // -0.5 .. +0.5
        val gravity = max(0.3f, baseGravity * (1 + mmrSkew * 0.25f)) / settings.value.sensitivity
        val jump = baseJump * settings.value.sensitivity
        val gap = max(95f, baseGap * (1 - mmrSkew * 0.18f))
        val speed = max(1.5f, basePipeSpeed * (1 + mmrSkew * 0.18f))
        val spawn = baseSpawn
        return floatArrayOf(gravity, jump, gap, speed, spawn)
    }

    fun resetRun() {
        pipes.clear(); particles.clear(); fragments.clear()
        tapsHeights.clear(); heightSamples.clear()
        unlockedNow.clear()
        birdY = h / 2; vel = 0f; score = 0; alive = true; started = false
        frameIdx = 0; spawnTimer = -800f; bgOffset = 0f
        holdSince = 0L; holdFatigue = 0f; shakeT = 0; slowMo = 0
        crashInfo = ""; inFocus = false; focusUntil = 0L
        runStartMs = System.currentTimeMillis()
        showAnalytics = false
        onScore(0)
    }

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && score == 0 && pipes.isEmpty() && alive && !started) {
            resetRun()
        }
    }

    fun flap(strength: Float = 1f) {
        if (!alive) return
        if (!started) started = true
        val cfg = resolvedCfg()
        val jump = cfg[1]
        var force = jump * strength
        if (settings.value.flavour == "rocket") force *= 1.18f
        vel = force
        if (settings.value.haptics) vibrate(context, 8L)
        if (settings.value.screenPulse) {
            pulse = true
            scope.launch { delay(80); pulse = false }
        }
        tapsHeights.add(birdY)
    }

    fun die() {
        if (!alive) return
        alive = false
        if (settings.value.haptics) vibrate(context, 80L)
        if (!settings.value.reducedMotion) {
            shakeT = 18; slowMo = 24
            for (i in 0 until 28) {
                val a = rng.nextFloat() * (PI * 2).toFloat()
                val v = 1f + rng.nextFloat() * 4f
                fragments.add(Fragment(w / 4f, birdY, cos(a) * v, sin(a) * v - 1f, 60, rng.nextFloat() * (PI * 2).toFloat(), (rng.nextFloat() - 0.5f) * 0.3f))
            }
        }
        // MMR
        val s = settings.value
        val newMmr = when {
            score >= 50 -> min(100f, s.mmr + 6f)
            score < 5 -> max(0f, s.mmr - 5f)
            score > 25 -> min(100f, s.mmr + 1f)
            else -> s.mmr
        }
        // Unlocks
        val elapsedSec = (System.currentTimeMillis() - runStartMs) / 1000
        val unlockRocket = !s.unlockedRocket && score >= 20
        val unlockGhost = !s.unlockedGhost && elapsedSec >= 60
        val unlockMag = !s.unlockedMagnetic && score >= 30
        if (unlockRocket) unlockedNow.add("Rocket Bird")
        if (unlockGhost) unlockedNow.add("Ghost Bird")
        if (unlockMag) unlockedNow.add("Magnetic")
        settings.value = s.copy(
            mmr = newMmr,
            unlockedRocket = s.unlockedRocket || unlockRocket,
            unlockedGhost = s.unlockedGhost || unlockGhost,
            unlockedMagnetic = s.unlockedMagnetic || unlockMag,
        )
        saveSettings()
        // Schedule analytics overlay
        scope.launch {
            delay(if (settings.value.reducedMotion) 200L else 700L)
            showAnalytics = true
        }
    }

    LaunchedEffect(paused, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (true) {
            delay(16)
            if (paused || settingsOpen || showAnalytics || !alive) {
                // animate fragments / particles even when dying
                if (!alive) {
                    val it = fragments.iterator()
                    while (it.hasNext()) {
                        val f = it.next()
                        f.x += f.vx; f.y += f.vy; f.vy += 0.18f
                        f.ang += f.va; f.life -= 1
                        if (f.life <= 0) it.remove()
                    }
                    if (shakeT > 0) shakeT -= 1
                }
                continue
            }
            val cfg = resolvedCfg()
            val gravity = cfg[0]; val gap = cfg[2]; val speed = cfg[3]; val spawn = cfg[4]

            // slow-mo factor
            val factor = if (slowMo > 0 && !settings.value.reducedMotion) { slowMo -= 1; 0.2f } else 1f

            if (!started) {
                birdY = (h / 2) + sin(frameIdx / 12f) * 6f
                frameIdx += 1
                continue
            }
            // recharge fatigue when not holding
            if (holdFatigue > 0 && holdSince == 0L) holdFatigue = max(0f, holdFatigue - 0.015f)
            // assist auto-flap
            if (settings.value.autoFlap && vel > 1f && birdY > h * 0.55f) flap(0.7f)

            // magnetic flavour pull
            if (settings.value.flavour == "magnetic") {
                val next = pipes.firstOrNull { it.x + 60f > w / 4f - 30f }
                if (next != null) {
                    val center = next.gapY + gap / 2f
                    val dy = center - birdY
                    vel += kotlin.math.sign(dy) * min(0.18f, abs(dy) * 0.005f)
                }
            }
            vel += gravity * factor
            if (vel > 9f) vel = 9f
            birdY += vel * factor
            heightSamples.add(birdY)
            frameIdx += 1
            spawnTimer += 16f * factor

            if (spawnTimer >= spawn) {
                spawnTimer = 0f
                val minY = 70f
                val maxY = h - 40f - gap - 70f
                val gapY = minY + rng.nextFloat() * max(40f, maxY - minY)
                val moving = score >= 25 && rng.nextFloat() < 0.35f
                val intangible = settings.value.flavour == "ghost" && (score + 1) % 10 == 0
                pipes.add(Pipe(w + 10f, gapY, gap, false, moving, gapY, rng.nextFloat() * (PI * 2).toFloat(), intangible))
            }
            // move pipes
            for (p in pipes) {
                p.x -= speed * factor
                if (p.moving) p.gapY = p.baseY + sin(frameIdx / 22f + p.phase) * 30f
            }
            pipes.removeAll { it.x < -80f }
            bgOffset = (bgOffset + speed * 0.4f * factor) % 40f

            // ground / ceiling
            if (!settings.value.assistGhost) {
                if (birdY + 14f > h - 40f) { crashInfo = "ground @ frame $frameIdx"; die(); continue }
                if (birdY - 14f < 0f) { crashInfo = "ceiling @ frame $frameIdx"; die(); continue }
            } else {
                if (birdY + 14f > h - 40f) birdY = h - 40f - 14f
                if (birdY - 14f < 0f) birdY = 14f
            }
            // pipe collisions & passing
            val grace = 2f
            for (p in pipes) {
                val birdX = w / 4f
                val inX = birdX + 14f - grace > p.x && birdX - 14f + grace < p.x + 60f
                if (inX && !p.intangible && !settings.value.assistGhost) {
                    if (birdY - 14f + grace < p.gapY || birdY + 14f - grace > p.gapY + p.gapH) {
                        crashInfo = "pipe @ x=${p.x.toInt()},y=${birdY.toInt()}"; die(); break
                    }
                }
                if (!p.passed && p.x + 60f < birdX - 14f) {
                    p.passed = true
                    score += 1
                    onScore(score)
                    // particles
                    val n = if (settings.value.reducedMotion) 3 else 8
                    repeat(n) {
                        particles.add(Particle(
                            p.x + 30f + (rng.nextFloat() - 0.5f) * 10f,
                            p.gapY + gap / 2f + (rng.nextFloat() - 0.5f) * gap,
                            (rng.nextFloat() - 0.5f) * 1.2f,
                            (rng.nextFloat() - 0.5f) * 1.2f,
                            18, 18, Color.White.copy(0.4f), 1.5f,
                        ))
                    }
                    if (score > 0 && score % 10 == 0) {
                        inFocus = true; focusUntil = System.currentTimeMillis() + 2500L
                    }
                }
            }
            if (inFocus && System.currentTimeMillis() > focusUntil) inFocus = false
            // particles tick
            run {
                val it = particles.iterator()
                while (it.hasNext()) {
                    val pt = it.next()
                    pt.x += pt.vx; pt.y += pt.vy; pt.vy += 0.02f; pt.life -= 1
                    if (pt.life <= 0) it.remove()
                }
            }
            // trail particle
            if (!settings.value.reducedMotion && frameIdx % 2 == 0) {
                val sp = abs(vel)
                val color = when {
                    inFocus -> Color(0xFFFACC15).copy(0.9f)
                    sp > 5f -> Color(0xFFF87171).copy(0.7f)
                    sp > 2f -> Color(0xFF60A5FA).copy(0.7f)
                    else -> Color(0xFFA78BFA).copy(0.5f)
                }
                particles.add(Particle(w / 4f - 14f, birdY + (rng.nextFloat() - 0.5f) * 6f, -1.2f, 0f, 22, 22, color, 2.2f))
            }
            if (shakeT > 0) shakeT -= 1
        }
    }

    val shakeOffsetX = if (shakeT > 0) ((rng.nextFloat() - 0.5f) * 6f) else 0f
    val shakeOffsetY = if (shakeT > 0) ((rng.nextFloat() - 0.5f) * 6f) else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.value.holdMode, alive, paused, showAnalytics, settingsOpen) {
                    if (!alive || paused || showAnalytics || settingsOpen) return@pointerInput
                    if (settings.value.holdMode) {
                        detectTapGestures(
                            onPress = {
                                holdSince = System.currentTimeMillis()
                                tryAwaitRelease()
                                if (alive) {
                                    val dt = min(400L, System.currentTimeMillis() - holdSince).toFloat()
                                    val s = 0.6f + (dt / 400f) * 0.8f * (1f - holdFatigue)
                                    flap(max(0.4f, s))
                                    holdFatigue = min(1f, holdFatigue + 0.5f)
                                }
                                holdSince = 0L
                            },
                        )
                    } else {
                        detectTapGestures(onTap = { flap() })
                    }
                },
        ) {
            w = size.width; h = size.height
            val gap = resolvedCfg()[2]
            // shake transform
            translate(shakeOffsetX, shakeOffsetY) {
                drawSky(score, bgOffset, w, h, settings.value.colorblind)
                drawHills(bgOffset, w, h, settings.value.colorblind)
                drawPipes(pipes, gap, w, h, settings.value.colorblind)
                drawGround(bgOffset, w, h, settings.value.colorblind)
                // particles
                for (pt in particles) {
                    val a = pt.life.toFloat() / pt.maxLife
                    drawRect(applyColorblind(pt.color, settings.value.colorblind).copy(alpha = a),
                        Offset(pt.x, pt.y), Size(pt.size, pt.size))
                }
                // bird or fragments
                if (alive) {
                    drawBird(w / 4f, birdY, vel, settings.value.flavour, inFocus, settings.value.colorblind)
                }
                for (f in fragments) {
                    val a = max(0f, f.life / 60f)
                    drawCircle(applyColorblind(Color(0xFFFACC15), settings.value.colorblind).copy(alpha = a),
                        radius = 3f, center = Offset(f.x, f.y))
                }
                // focus vignette
                if (inFocus && !settings.value.reducedMotion) {
                    drawRect(Color(0x66000000), Offset(0f, 0f), Size(w, h))
                }
            }
        }

        // Score overlay
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            Text("$score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
        }
        // Settings button
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Surface(color = Color(0x99000000), shape = RoundedCornerShape(50), modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settingsOpen = true }) {
                Text("⚙", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        // Hover-to-start hint
        if (!started && alive && !showAnalytics && !settingsOpen) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Surface(color = Color(0x99000000), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (settings.value.holdMode) "Hold to ascend" else "Tap to start", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Sensitivity ${"%.2f".format(settings.value.sensitivity)} · ${settings.value.flavour}",
                            color = Color.White.copy(0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
        // Pulse
        AnimatedVisibility(visible = pulse, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0x33FACC15)))
        }

        // Settings sheet
        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            FlappySettingsSheet(
                settings = settings.value,
                onChange = { settings.value = it; saveSettings() },
                onClose = { settingsOpen = false },
            )
        }
        // Analytics overlay
        AnimatedVisibility(visible = showAnalytics, enter = fadeIn(), exit = fadeOut()) {
            FlappyAnalyticsSheet(
                score = score,
                crash = crashInfo,
                heights = heightSamples,
                taps = tapsHeights,
                unlocks = unlockedNow.toList(),
                onReplay = {
                    showAnalytics = false
                    resetRun()
                },
                onContinue = {
                    showAnalytics = false
                    onGameOver()
                },
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSky(score: Int, bg: Float, w: Float, h: Float, cb: String) {
    val (c0, c1, c2) = when {
        score < 10 -> Triple(Color(0xFFFDE68A), Color(0xFFFB923C), Color(0xFF0C4A6E))
        score < 30 -> Triple(Color(0xFF7DD3FC), Color(0xFF38BDF8), Color(0xFF1E3A8A))
        score < 60 -> Triple(Color(0xFFFDBA74), Color(0xFFF472B6), Color(0xFF7C3AED))
        else -> Triple(Color(0xFF0B1D3A), Color(0xFF1E1B4B), Color(0xFF000000))
    }
    drawRect(
        Brush.verticalGradient(listOf(applyColorblind(c0, cb), applyColorblind(c1, cb), applyColorblind(c2, cb))),
        size = Size(w, h),
    )
    if (score >= 60) {
        for (i in 0 until 35) {
            val sx = ((i * 53 + bg * 0.2f) % w)
            val sy = ((i * 37) % (h - 40f - 80f)) + 10f
            drawRect(Color.White.copy(0.3f), Offset(sx, sy), Size(1.5f, 1.5f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHills(bg: Float, w: Float, h: Float, cb: String) {
    val path = androidx.compose.ui.graphics.Path()
    path.moveTo(0f, h - 40f - 30f)
    var x = 0f
    while (x <= w) {
        path.lineTo(x, h - 40f - 30f - sin((x + bg) / 30f) * 8f)
        x += 30f
    }
    path.lineTo(w, h - 40f); path.lineTo(0f, h - 40f); path.close()
    drawPath(path, applyColorblind(Color(0xFF0F1E3C), cb).copy(0.55f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPipes(pipes: List<Pipe>, gap: Float, w: Float, h: Float, cb: String) {
    for (p in pipes) {
        val color = if (p.intangible) applyColorblind(Color(0xFF7E22CE), cb).copy(0.55f) else applyColorblind(Color(0xFF22C55E), cb)
        val cap = if (p.intangible) applyColorblind(Color(0xFF7E22CE), cb) else applyColorblind(Color(0xFF15803D), cb)
        drawRect(color, Offset(p.x, 0f), Size(60f, p.gapY))
        drawRect(color, Offset(p.x, p.gapY + gap), Size(60f, h - 40f - p.gapY - gap))
        drawRect(cap, Offset(p.x - 4f, p.gapY - 14f), Size(68f, 14f))
        drawRect(cap, Offset(p.x - 4f, p.gapY + gap), Size(68f, 14f))
        // moss stripes
        var yy = 12f
        while (yy < p.gapY) {
            drawRect(Color.Black.copy(0.18f), Offset(p.x + 6f, yy), Size(48f, 2f))
            yy += 24f
        }
        yy = p.gapY + gap + 12f
        while (yy < h - 40f) {
            drawRect(Color.Black.copy(0.18f), Offset(p.x + 6f, yy), Size(48f, 2f))
            yy += 24f
        }
        // glow
        drawRect(Color.White.copy(0.18f), Offset(p.x + 6f, 0f), Size(4f, p.gapY))
        drawRect(Color.White.copy(0.18f), Offset(p.x + 6f, p.gapY + gap), Size(4f, h - 40f - p.gapY - gap))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGround(bg: Float, w: Float, h: Float, cb: String) {
    drawRect(
        Brush.verticalGradient(listOf(applyColorblind(Color(0xFF854D0E), cb), applyColorblind(Color(0xFF422006), cb))),
        Offset(0f, h - 40f), Size(w, 40f),
    )
    drawRect(applyColorblind(Color(0xFF65A30D), cb), Offset(0f, h - 40f), Size(w, 6f))
    var x = -bg
    while (x < w) {
        drawRect(Color.Black.copy(0.18f), Offset(x, h - 40f + 6f), Size(12f, 4f))
        x += 22f
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBird(x: Float, y: Float, vy: Float, flavour: String, focus: Boolean, cb: String) {
    val (a, b, wing) = when (flavour) {
        "rocket"   -> Triple(Color(0xFFFECACA), Color(0xFFEF4444), Color(0xFF7F1D1D))
        "ghost"    -> Triple(Color(0xFFE9D5FF), Color(0xFFA78BFA), Color(0xFF5B21B6))
        "magnetic" -> Triple(Color(0xFFBAE6FD), Color(0xFF0EA5E9), Color(0xFF075985))
        else       -> Triple(Color(0xFFFDE68A), Color(0xFFF59E0B), Color(0xFFEA580C))
    }
    drawCircle(
        Brush.radialGradient(listOf(applyColorblind(a, cb), applyColorblind(b, cb)), center = Offset(x, y), radius = 18f),
        radius = 14f, center = Offset(x, y),
    )
    drawCircle(applyColorblind(if (focus) Color(0xCCFACC15) else Color(0x80FACC15), cb), radius = 18f, center = Offset(x, y), style = Stroke(width = 2f))
    // wing
    drawCircle(applyColorblind(wing, cb), radius = 5f, center = Offset(x - 3f, y + 2f))
    // eye
    drawCircle(Color.White, radius = 4f, center = Offset(x + 5f, y - 3f))
    drawCircle(Color.Black, radius = 2f, center = Offset(x + 6f, y - 3f))
    // beak
    drawCircle(applyColorblind(Color(0xFFDC2626), cb), radius = 3f, center = Offset(x + 14f, y))
    if (flavour == "rocket") {
        drawCircle(applyColorblind(Color(0xCCFB923C), cb), radius = 5f, center = Offset(x - 18f, y))
    }
}

@Composable
private fun FlappySettingsSheet(
    settings: FlappySettings,
    onChange: (FlappySettings) -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null
    ) { onClose() }) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(20.dp)
                .fillMaxWidth(0.92f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Flappy Eclipse · Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 18.sp,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { onClose() })
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Sensitivity ${"%.2f".format(settings.sensitivity)}", color = Color.White, fontSize = 13.sp)
                Slider(
                    value = settings.sensitivity, onValueChange = { onChange(settings.copy(sensitivity = it)) },
                    valueRange = 0.6f..1.6f, steps = 19,
                )
                ToggleRow("Hold-to-ascend mode", settings.holdMode) { onChange(settings.copy(holdMode = it)) }
                ToggleRow("Haptic flap feedback", settings.haptics) { onChange(settings.copy(haptics = it)) }
                ToggleRow("Reduced motion", settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
                ToggleRow("Screen-pulse cue (instead of haptics)", settings.screenPulse) { onChange(settings.copy(screenPulse = it)) }
                ToggleRow("Assist: ghost collision", settings.assistGhost) { onChange(settings.copy(assistGhost = it)) }
                ToggleRow("Assist: auto-flap", settings.autoFlap) { onChange(settings.copy(autoFlap = it)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Colourblind mode", color = Color.White.copy(0.85f), fontSize = 12.sp)
                Row {
                    listOf("off", "protanopia", "deuteranopia", "tritanopia").forEach { m ->
                        Surface(
                            color = if (settings.colorblind == m) Color(0xFF6366F1) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .padding(end = 6.dp, top = 6.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null
                                ) { onChange(settings.copy(colorblind = m)) },
                        ) {
                            Text(m, color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Bird flavour (skin)", color = Color.White.copy(0.85f), fontSize = 12.sp)
                val flavours = listOf(
                    Triple("vanilla", "Vanilla", true),
                    Triple("rocket", "Rocket", settings.unlockedRocket),
                    Triple("ghost", "Ghost", settings.unlockedGhost),
                    Triple("magnetic", "Magnetic", settings.unlockedMagnetic),
                )
                Row {
                    flavours.forEach { (id, label, unlocked) ->
                        Surface(
                            color = if (settings.flavour == id) Color(0xFF6366F1) else Color(0x33FFFFFF),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .padding(end = 6.dp, top = 6.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                                    enabled = unlocked,
                                ) { if (unlocked) onChange(settings.copy(flavour = id)) },
                        ) {
                            Text(if (unlocked) label else "🔒 $label",
                                color = if (unlocked) Color.White else Color.White.copy(0.55f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Adaptive difficulty (hidden) — ${settings.mmr.toInt()}/100",
                    color = Color.White.copy(0.65f), fontSize = 11.sp)
                Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF))) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(settings.mmr / 100f)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF34D399), Color(0xFFF59E0B), Color(0xFFEF4444)))))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Unlock skins by passing 20 pipes (Rocket), surviving 60s (Ghost), and 30 pipes (Magnetic).",
                    color = Color.White.copy(0.55f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Checkbox(checked = value, onCheckedChange = onChange)
        Text(label, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun FlappyAnalyticsSheet(
    score: Int,
    crash: String,
    heights: List<Float>,
    taps: List<Float>,
    unlocks: List<String>,
    onReplay: () -> Unit,
    onContinue: () -> Unit,
) {
    val suggestion = remember(score, crash, heights.size, taps.size) {
        when {
            crash.startsWith("ceiling") -> "You over-flapped into the ceiling — try a lighter tap or lower sensitivity."
            crash.startsWith("ground")  -> "You panicked into the floor — start tapping ~0.1s earlier on descents."
            taps.isEmpty() && heights.size > 30 -> "You stopped flapping — one well-timed tap mid-gap helps."
            taps.size >= 6 && heights.size in 1..200 -> "Lots of frantic taps — slow down, relax."
            else -> "Aim for the middle of each gap, not the edges."
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Surface(
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Run analysis · Score $score", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = Color(0x3360A5FA), shape = RoundedCornerShape(10.dp)) {
                    Text(suggestion, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Height over time", color = Color.White.copy(0.7f), fontSize = 11.sp)
                Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color(0x14FFFFFF))) {
                    val w = size.width; val h = size.height
                    if (heights.isEmpty()) return@Canvas
                    val maxH = max(540f, heights.max())
                    var prevX = 0f; var prevY = (heights[0] / maxH) * h
                    for (i in 1 until heights.size) {
                        val x = (i.toFloat() / (heights.size - 1)) * w
                        val y = (heights[i] / maxH) * h
                        drawLine(Color(0xFF60A5FA), Offset(prevX, prevY), Offset(x, y), strokeWidth = 1.6f)
                        prevX = x; prevY = y
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Tap heatmap (where you tapped)", color = Color.White.copy(0.7f), fontSize = 11.sp)
                Canvas(modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0x14FFFFFF))) {
                    val w = size.width; val h = size.height
                    val bins = 12
                    val counts = IntArray(bins)
                    for (t in taps) {
                        val idx = min(bins - 1, ((t / 540f) * bins).toInt().coerceAtLeast(0))
                        counts[idx]++
                    }
                    val mx = max(1, counts.max())
                    val bw = w / bins
                    for (i in 0 until bins) {
                        val v = counts[i].toFloat() / mx
                        drawRect(Color(0xFFF87171).copy(alpha = 0.25f + v * 0.65f),
                            Offset(i * bw, h - v * h), Size(bw - 1f, v * h))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row {
                    StatTile("Pipes", "$score", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    StatTile("Taps", "${taps.size}", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    StatTile("Crash", if (crash.isBlank()) "—" else crash.substringBefore(" "), Modifier.weight(1f))
                }
                if (unlocks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(color = Color(0x55FACC15), shape = RoundedCornerShape(10.dp)) {
                        Text("🎉 Unlocked: ${unlocks.joinToString()}", color = Color(0xFFFDE68A),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Surface(color = Color(0xFF6366F1), shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { onReplay() }) {
                        Text("↺ Instant replay", color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = Color(0x33FFFFFF), shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null
                        ) { onContinue() }) {
                        Text("Continue", color = Color.White, fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Color(0x14FFFFFF), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun List<Float>.max(): Float = if (isEmpty()) 0f else reduce { a, b -> if (a > b) a else b }
private fun IntArray.max(): Int = if (isEmpty()) 0 else reduce { a, b -> if (a > b) a else b }
