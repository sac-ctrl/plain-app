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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.DinoSettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private enum class ObsKind { CACTUS, ROCK, PTERO, LOG }

private data class DObs(
    var x: Float, var y: Float, val w: Float, val h: Float,
    val kind: ObsKind, var passed: Boolean = false, val spawnAtMs: Long,
)

private data class DCoin(var x: Float, var y: Float, var got: Boolean = false, var phase: Float = 0f)
private data class DPart(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, val max: Int, val col: Color, val size: Float)

private data class DinoSettings(
    var jumpBuffer: Int = 100,
    var swipeMin: Int = 22,
    var haptics: Boolean = true,
    var reducedMotion: Boolean = false,
    var highContrast: Boolean = false,
    var oneHanded: Boolean = false,
    var batterySaver: Boolean = false,
    var announcer: Boolean = true,
    var assistAutoJump: Boolean = false,
    var assistBigBox: Boolean = false,
    var assistInvincible: Boolean = false,
    var colorblind: String = "off",
    var skin: String = "classic",
    var theme: String = "desert",
    var upgradeDoubleJump: Boolean = false,
    var upgradeShield: Int = 0,
    var upgradeMagnet: Int = 0,
    var unlockedCyber: Boolean = false,
    var unlockedBone: Boolean = false,
    var unlockedLava: Boolean = false,
    var unlockedIce: Boolean = false,
    var unlockedGold: Boolean = false,
    var coins: Int = 0,
    var longestM: Int = 0,
    var totalJumps: Int = 0,
    var recentDeaths: List<Int> = emptyList(),
)

private fun parseDino(j: String): DinoSettings {
    val s = DinoSettings()
    if (j.isBlank() || j == "{}") return s
    try {
        val o = JSONObject(j)
        s.jumpBuffer = o.optInt("jumpBuffer", 100)
        s.swipeMin = o.optInt("swipeMin", 22)
        s.haptics = o.optBoolean("haptics", true)
        s.reducedMotion = o.optBoolean("reducedMotion", false)
        s.highContrast = o.optBoolean("highContrast", false)
        s.oneHanded = o.optBoolean("oneHanded", false)
        s.batterySaver = o.optBoolean("batterySaver", false)
        s.announcer = o.optBoolean("announcer", true)
        s.assistAutoJump = o.optBoolean("assistAutoJump", false)
        s.assistBigBox = o.optBoolean("assistBigBox", false)
        s.assistInvincible = o.optBoolean("assistInvincible", false)
        s.colorblind = o.optString("colorblind", "off")
        s.skin = o.optString("skin", "classic")
        s.theme = o.optString("theme", "desert")
        s.upgradeDoubleJump = o.optBoolean("upgradeDoubleJump", false)
        s.upgradeShield = o.optInt("upgradeShield", 0)
        s.upgradeMagnet = o.optInt("upgradeMagnet", 0)
        s.unlockedCyber = o.optBoolean("unlockedCyber", false)
        s.unlockedBone = o.optBoolean("unlockedBone", false)
        s.unlockedLava = o.optBoolean("unlockedLava", false)
        s.unlockedIce = o.optBoolean("unlockedIce", false)
        s.unlockedGold = o.optBoolean("unlockedGold", false)
        s.coins = o.optInt("coins", 0)
        s.longestM = o.optInt("longestM", 0)
        s.totalJumps = o.optInt("totalJumps", 0)
        val arr = o.optJSONArray("recentDeaths")
        if (arr != null) s.recentDeaths = (0 until arr.length()).map { arr.getInt(it) }
    } catch (_: Exception) { }
    return s
}

private fun toJson(s: DinoSettings): String {
    val o = JSONObject()
    o.put("jumpBuffer", s.jumpBuffer); o.put("swipeMin", s.swipeMin)
    o.put("haptics", s.haptics); o.put("reducedMotion", s.reducedMotion); o.put("highContrast", s.highContrast)
    o.put("oneHanded", s.oneHanded); o.put("batterySaver", s.batterySaver); o.put("announcer", s.announcer)
    o.put("assistAutoJump", s.assistAutoJump); o.put("assistBigBox", s.assistBigBox); o.put("assistInvincible", s.assistInvincible)
    o.put("colorblind", s.colorblind); o.put("skin", s.skin); o.put("theme", s.theme)
    o.put("upgradeDoubleJump", s.upgradeDoubleJump); o.put("upgradeShield", s.upgradeShield); o.put("upgradeMagnet", s.upgradeMagnet)
    o.put("unlockedCyber", s.unlockedCyber); o.put("unlockedBone", s.unlockedBone); o.put("unlockedLava", s.unlockedLava)
    o.put("unlockedIce", s.unlockedIce); o.put("unlockedGold", s.unlockedGold)
    o.put("coins", s.coins); o.put("longestM", s.longestM); o.put("totalJumps", s.totalJumps)
    val arr = org.json.JSONArray(); s.recentDeaths.forEach { arr.put(it) }; o.put("recentDeaths", arr)
    return o.toString()
}

private fun applyCb(c: Color, mode: String): Color {
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
        if (android.os.Build.VERSION.SDK_INT >= 26)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(ms)
    } catch (_: Throwable) { }
}

private fun todaySeed(): Int {
    val cal = Calendar.getInstance()
    val s = "${cal.get(Calendar.YEAR)}${cal.get(Calendar.MONTH) + 1}${cal.get(Calendar.DAY_OF_MONTH)}"
    var h = 7; for (c in s) h = h * 31 + c.code
    return h
}

@Composable
fun RunnerGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { mutableStateOf(DinoSettings()) }
    LaunchedEffect(Unit) { settings.value = parseDino(DinoSettingsJsonPreference.getAsync(ctx)) }
    fun save() { scope.launch { DinoSettingsJsonPreference.putAsync(ctx, toJson(settings.value)) } }

    // TTS
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value?.language = Locale.getDefault()
        }
        tts.value = t
        onDispose { try { t.stop(); t.shutdown() } catch (_: Throwable) { } }
    }
    fun say(text: String) {
        if (!settings.value.announcer) return
        try { tts.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dino") } catch (_: Throwable) { }
    }

    // World
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val groundOffset = 60f
    var groundY by remember { mutableStateOf(0f) }
    val obstacles = remember { mutableStateListOf<DObs>() }
    val coins = remember { mutableStateListOf<DCoin>() }
    val particles = remember { mutableStateListOf<DPart>() }
    var playerY by remember { mutableStateOf(0f) }
    var vy by remember { mutableStateOf(0f) }
    var sliding by remember { mutableStateOf(false) }
    var jumpsUsed by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf(0f) }
    var runCoins by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }
    var maxCombo by remember { mutableStateOf(0) }
    var jumpCount by remember { mutableStateOf(0) }
    var spd by remember { mutableStateOf(4f) }
    var maxSpd by remember { mutableStateOf(11f) }
    var spawnAcc by remember { mutableStateOf(0f) }
    var bossSpawned by remember { mutableStateOf(false) }
    var dayTime by remember { mutableStateOf(0f) }
    var shieldCharges by remember { mutableStateOf(0) }
    var magnetT by remember { mutableStateOf(0) }
    var slowMoT by remember { mutableStateOf(0) }
    var doubleCoinT by remember { mutableStateOf(0) }
    var powerName by remember { mutableStateOf("") }
    var powerLeft by remember { mutableStateOf(0) }
    var bufferUntilMs by remember { mutableStateOf(0L) }
    var settingsOpen by remember { mutableStateOf(false) }
    var showAnalytics by remember { mutableStateOf(false) }
    var calActive by remember { mutableStateOf(false) }
    var calCount by remember { mutableStateOf(0) }
    val calReact = remember { mutableStateListOf<Float>() }
    val reactions = remember { mutableStateListOf<Float>() }
    val deathBuckets = remember { mutableStateMapOf("cactus" to 0, "ptero" to 0, "rock" to 0, "log" to 0) }
    var deathKind by remember { mutableStateOf("") }
    val unlockedNow = remember { mutableStateListOf<String>() }
    var runTip by remember { mutableStateOf("") }
    var ttEnd by remember { mutableStateOf(0L) }
    var lastMileK by remember { mutableStateOf(-1) }
    var flashGlow by remember { mutableStateOf(0) }
    val seededRng = remember { mutableStateOf(Random(System.currentTimeMillis())) }

    fun rand(): Float {
        val srng = seededRng.value
        return srng.nextFloat()
    }

    fun configure() {
        val diffMul = when (difficulty) { "Easy" -> 0.85f; "Hard" -> 1.18f; "Insane" -> 1.35f; else -> 1f }
        spd = 4.4f * diffMul
        maxSpd = 11.5f * diffMul
        val recents = settings.value.recentDeaths
        val shorts = recents.count { it < 500 }
        val longs = recents.count { it > 2000 }
        if (shorts >= 3) spd *= 0.9f
        if (longs >= 3) maxSpd *= 1.08f
        seededRng.value = if (mode == "Mission") Random(todaySeed().toLong()) else Random(System.currentTimeMillis())
    }

    fun resetRun() {
        configure()
        obstacles.clear(); coins.clear(); particles.clear()
        playerY = groundY; vy = 0f; sliding = false; jumpsUsed = 0
        distance = 0f; runCoins = 0; combo = 0; maxCombo = 0; jumpCount = 0
        spawnAcc = 0f; bossSpawned = false; dayTime = 0f
        shieldCharges = settings.value.upgradeShield
        magnetT = 0; slowMoT = 0; doubleCoinT = 0
        powerName = ""; powerLeft = 0
        alive = true; started = false
        deathBuckets["cactus"] = 0; deathBuckets["ptero"] = 0; deathBuckets["rock"] = 0; deathBuckets["log"] = 0
        deathKind = ""
        reactions.clear()
        unlockedNow.clear()
        runTip = ""; lastMileK = -1; flashGlow = 0
        ttEnd = if (mode == "TimeTrial") System.currentTimeMillis() + 60000L else 0L
        showAnalytics = false
        onScore(0)
    }

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0) {
            groundY = h - groundOffset
            if (playerY == 0f) playerY = groundY
            if (obstacles.isEmpty() && !started && alive) resetRun()
        }
    }

    fun jump() {
        if (!alive) return
        if (!started) started = true
        val onGround = playerY >= groundY - 0.5f
        val canDouble = settings.value.upgradeDoubleJump && !onGround && jumpsUsed < 2
        if (onGround) {
            vy = -11.6f; jumpsUsed = 1
            if (settings.value.haptics) vibrate(ctx, 8L)
            jumpCount += 1
            // record reaction time vs next obstacle
            val next = obstacles.firstOrNull { !it.passed }
            if (next != null) reactions.add((System.currentTimeMillis() - next.spawnAtMs).toFloat())
        } else if (canDouble) {
            vy = -10.5f; jumpsUsed = 2
            if (settings.value.haptics) vibrate(ctx, 14L)
            jumpCount += 1
        } else {
            bufferUntilMs = System.currentTimeMillis() + settings.value.jumpBuffer
        }
    }
    fun crouch(start: Boolean) {
        if (!alive) return
        if (start && playerY >= groundY - 0.5f) {
            sliding = true
            if (settings.value.haptics) vibrate(ctx, 4L)
        } else if (!start) sliding = false
    }

    LaunchedEffect(paused, w, h, alive) {
        if (w == 0f) return@LaunchedEffect
        var lastDraw = 0L
        val frame = if (settings.value.batterySaver) 33L else 16L
        while (true) {
            delay(frame)
            if (paused || settingsOpen || showAnalytics) continue
            // pre-start bob
            if (!started && alive) {
                playerY = groundY - kotlin.math.abs(sin(System.currentTimeMillis() / 250.0).toFloat()) * 4f
                continue
            }
            if (!alive) {
                // continue particle physics
                val it = particles.iterator()
                while (it.hasNext()) { val p = it.next(); p.x += p.vx; p.y += p.vy; p.vy += 0.1f; p.life -= 1; if (p.life <= 0) it.remove() }
                continue
            }
            val slow = if (slowMoT > 0) { slowMoT -= 1; 0.5f } else 1f
            if (magnetT > 0) magnetT -= 1
            if (doubleCoinT > 0) doubleCoinT -= 1
            if (powerLeft > 0) powerLeft -= 1
            if (powerLeft <= 0) powerName = ""

            // physics
            vy += 0.62f * slow
            playerY += vy * slow
            if (playerY >= groundY) {
                playerY = groundY; vy = 0f; jumpsUsed = 0
                if (bufferUntilMs > System.currentTimeMillis()) { jump(); bufferUntilMs = 0 }
            }
            // assist auto-jump
            if (settings.value.assistAutoJump) {
                val next = obstacles.firstOrNull { it.x in (w / 4f)..(w / 4f + 90f) && !it.passed && it.kind != ObsKind.PTERO }
                if (next != null && playerY >= groundY - 0.5f) jump()
            }
            // speed curve
            spd += (maxSpd - spd) * 0.0008f
            distance += (spd * slow) / 7f
            dayTime += spd * slow

            // spawn
            spawnAcc += spd * slow
            val gap = max(110f, 180f - spd * 4f)
            if (spawnAcc > gap + rand() * 90f) {
                spawnAcc = 0f
                val now = System.currentTimeMillis()
                val mileK = (distance / 2000f).toInt()
                if (mode == "BossRun" && mileK > 0 && !bossSpawned && (distance.toInt() % 2000) < 50) {
                    bossSpawned = true; say("Boss incoming")
                    for (i in 0 until 5) obstacles.add(DObs(w + i * 90f, groundY - 50f - (i % 2) * 30f, 32f, 24f, ObsKind.PTERO, false, now))
                } else {
                    if (mileK > (lastMileK + 0).coerceAtLeast(0) && mode == "BossRun") bossSpawned = false
                    val r = rand()
                    when {
                        r < 0.35f -> obstacles.add(DObs(w + 10f, groundY - 26f, 18f, 26f, ObsKind.CACTUS, false, now))
                        r < 0.6f  -> obstacles.add(DObs(w + 10f, groundY - 14f, 22f, 14f, ObsKind.ROCK, false, now))
                        r < 0.85f -> {
                            val high = rand() < 0.5f
                            obstacles.add(DObs(w + 10f, if (high) groundY - 70f else groundY - 40f, 28f, 18f, ObsKind.PTERO, false, now))
                        }
                        else -> {
                            if (settings.value.theme == "jungle") obstacles.add(DObs(w + 10f, groundY - 40f, 22f, 40f, ObsKind.LOG, false, now))
                            else obstacles.add(DObs(w + 10f, groundY - 22f, 18f, 22f, ObsKind.CACTUS, false, now))
                        }
                    }
                    if (rand() < 0.5f) coins.add(DCoin(w + 60f + rand() * 40f, groundY - 60f - rand() * 30f, false, rand() * 6f))
                }
                // power-up roll
                if (rand() < 0.012f) {
                    val pool = mutableListOf("slowmo", "double")
                    if (settings.value.upgradeShield > 0) pool.add("shield")
                    if (settings.value.upgradeMagnet > 0) pool.add("magnet")
                    when (pool.random(seededRng.value)) {
                        "shield"  -> { shieldCharges = min(shieldCharges + 1, 1 + settings.value.upgradeShield); powerName = "Shield"; powerLeft = 60 * 6; say("Shielded") }
                        "magnet"  -> { magnetT = 60 * 8; powerName = "Magnet"; powerLeft = magnetT; say("Magnet on") }
                        "slowmo"  -> { slowMoT = 60 * 2; powerName = "Slow-mo"; powerLeft = slowMoT; say("Slow motion") }
                        "double"  -> { doubleCoinT = 60 * 8; powerName = "2x Coins"; powerLeft = doubleCoinT; say("Double coins") }
                    }
                }
            }
            // move
            for (o in obstacles) o.x -= spd * slow
            for (c in coins) {
                c.x -= spd * slow; c.phase += 0.15f
                if (magnetT > 0) {
                    val dx = (w / 4f) - c.x; val dy = (playerY - 16f) - c.y
                    val d = hypot(dx, dy); val range = 60f + settings.value.upgradeMagnet * 25f
                    if (d < range && d > 0.0001f) { c.x += (dx / d) * 3f; c.y += (dy / d) * 3f }
                }
            }
            run {
                val it = particles.iterator()
                while (it.hasNext()) { val p = it.next(); p.x += p.vx; p.y += p.vy; p.vy += 0.06f; p.life -= 1; if (p.life <= 0) it.remove() }
            }
            // collision
            val px = w / 4f; val pwid = 26f
            val forgive = if (settings.value.assistBigBox) -4f else 0f
            var pTop = playerY - 30f - forgive; val pBot = playerY + forgive
            if (sliding) pTop = playerY - 14f
            val itO = obstacles.iterator()
            var died = false
            while (itO.hasNext()) {
                val o = itO.next()
                val oTop = o.y; val oBot = o.y + o.h
                val inX = o.x < px + pwid + forgive && o.x + o.w > px - forgive
                if (inX && oBot > pTop && oTop < pBot) {
                    if (settings.value.assistInvincible) { o.x = -200f; continue }
                    if (shieldCharges > 0) {
                        shieldCharges -= 1; o.x = -200f
                        if (settings.value.haptics) vibrate(ctx, 20L)
                        if (shieldCharges <= 0 && powerName == "Shield") powerName = ""
                        continue
                    }
                    deathKind = when (o.kind) { ObsKind.CACTUS -> "cactus"; ObsKind.PTERO -> "ptero"; ObsKind.ROCK -> "rock"; ObsKind.LOG -> "log" }
                    died = true; break
                }
                if (!o.passed && o.x + o.w < px) {
                    o.passed = true; combo += 1
                    maxCombo = max(maxCombo, combo)
                    onScore(distance.toInt() + runCoins * 5)
                    val passClear = abs(playerY - o.y - o.h / 2f)
                    if (passClear < 12f) say("Perfect")
                    else if (passClear < 22f) flashGlow = 18
                    if (calActive) {
                        reactions.lastOrNull()?.let { calReact.add(it) }
                        calCount += 1
                        if (calCount >= 5) {
                            if (calReact.isNotEmpty()) {
                                val avg = calReact.average().toFloat()
                                settings.value = settings.value.copy(jumpBuffer = max(40, min(180, (avg * 0.6f).toInt())))
                                save()
                            }
                            calActive = false
                        }
                    }
                }
            }
            if (died) { /* die */
                alive = false
                deathBuckets[deathKind] = (deathBuckets[deathKind] ?: 0) + 1
                if (settings.value.haptics) vibrate(ctx, 80L)
                if (!settings.value.reducedMotion) {
                    for (i in 0 until 16) {
                        val a = rand() * (PI * 2).toFloat(); val v = 1f + rand() * 3f
                        particles.add(DPart(w / 4f, playerY - 14f, cos(a) * v, sin(a) * v - 1f, 30, 30, Color(0xFFEF4444), 2f))
                    }
                }
                // adaptive
                val newRecent = (settings.value.recentDeaths + distance.toInt()).takeLast(5)
                val newLongest = max(settings.value.longestM, distance.toInt())
                val newCoins = settings.value.coins + runCoins
                val newJumps = settings.value.totalJumps + jumpCount
                // unlocks
                val s = settings.value
                val nuCyber = !s.unlockedCyber && distance >= 500
                val nuBone = !s.unlockedBone && distance >= 1000
                val nuLava = !s.unlockedLava && distance >= 1500
                val nuIce = !s.unlockedIce && maxCombo >= 25
                val nuGold = !s.unlockedGold && newJumps >= 500
                if (nuCyber) unlockedNow.add("Cyber skin")
                if (nuBone) unlockedNow.add("Bone skin")
                if (nuLava) unlockedNow.add("Lava skin")
                if (nuIce) unlockedNow.add("Ice skin")
                if (nuGold) unlockedNow.add("Gold skin")
                if (distance.toInt() > s.longestM) say("New record")
                runTip = when (deathKind) {
                    "ptero" -> "You hit a high pterodactyl — try crouching instead of jumping over."
                    "cactus" -> "You jumped too late — try jumping ~0.1s earlier."
                    "log" -> "Logs need a tall jump — full press, not a tap."
                    else -> "Slow down and look ahead — most deaths come from $deathKind."
                }
                settings.value = s.copy(
                    coins = newCoins, longestM = newLongest, totalJumps = newJumps,
                    recentDeaths = newRecent,
                    unlockedCyber = s.unlockedCyber || nuCyber,
                    unlockedBone = s.unlockedBone || nuBone,
                    unlockedLava = s.unlockedLava || nuLava,
                    unlockedIce = s.unlockedIce || nuIce,
                    unlockedGold = s.unlockedGold || nuGold,
                )
                save()
                scope.launch { delay(if (settings.value.reducedMotion) 200L else 600L); showAnalytics = true }
                continue
            }
            // coin collect
            for (c in coins) {
                if (c.got) continue
                if (abs(c.x - w / 4f) < 14f && abs(c.y - (playerY - 16f)) < 22f) {
                    c.got = true
                    val inc = 1 + (if (doubleCoinT > 0) 1 else 0)
                    runCoins += inc
                    if (settings.value.haptics) vibrate(ctx, 6L)
                }
            }
            // milestone
            val km = (distance.toInt() / 1000)
            if (distance.toInt() > 0 && distance.toInt() % 1000 == 0 && km != lastMileK) {
                lastMileK = km
                say("Good")
                if (settings.value.haptics) vibrate(ctx, 12L)
            }
            // cleanup
            obstacles.removeAll { it.x < -60f }
            coins.removeAll { it.x < -60f || it.got }
            // ground check (shouldn't happen for ground player but for safety)
            if (playerY > groundY) playerY = groundY
            if (flashGlow > 0) flashGlow -= 1
            // time-trial end
            if (mode == "TimeTrial" && ttEnd > 0 && System.currentTimeMillis() > ttEnd) {
                deathKind = "rock"; alive = false
                save()
                scope.launch { delay(200L); showAnalytics = true }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(settings.value.swipeMin, settings.value.upgradeDoubleJump, alive, paused, showAnalytics, settingsOpen, settings.value.oneHanded) {
                if (!alive || paused || showAnalytics || settingsOpen) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { },
                    onDragEnd = { sliding = false },
                    onDragCancel = { sliding = false },
                ) { change, dy ->
                    if (settings.value.oneHanded && change.position.y < size.height / 2) return@detectVerticalDragGestures
                    if (dy < -settings.value.swipeMin) jump()
                    if (dy > settings.value.swipeMin) crouch(true)
                }
            }
            .pointerInput(alive, paused, showAnalytics, settingsOpen, settings.value.oneHanded, settings.value.upgradeDoubleJump) {
                if (!alive || paused || showAnalytics || settingsOpen) return@pointerInput
                var lastTap = 0L
                detectTapGestures(
                    onTap = {
                        if (settings.value.oneHanded && it.y < size.height / 2) return@detectTapGestures
                        val now = System.currentTimeMillis()
                        if (now - lastTap < 220 && settings.value.upgradeDoubleJump) { jump(); jump(); lastTap = 0 }
                        else { jump(); lastTap = now }
                    },
                )
            }
        ) {
            w = size.width; h = size.height
            if (groundY == 0f) groundY = h - groundOffset
            drawBg(distance, dayTime, settings.value.theme, settings.value.highContrast, settings.value.colorblind, w, h, groundY)
            // ground details handled inside drawBg
            for (c in coins) {
                if (c.got) continue
                drawCircle(applyCb(Color(0xFFFBBF24), settings.value.colorblind), radius = 6f + sin(c.phase) * 1.2f, center = Offset(c.x, c.y))
                drawCircle(Color.Black.copy(0.4f), radius = 6f + sin(c.phase) * 1.2f, center = Offset(c.x, c.y), style = Stroke(width = 1f))
            }
            for (o in obstacles) drawObstacle(o, settings.value.theme, settings.value.highContrast, settings.value.colorblind, distance, groundY)
            for (p in particles) {
                val a = max(0f, p.life.toFloat() / p.max)
                drawRect(applyCb(p.col, settings.value.colorblind).copy(alpha = a), Offset(p.x, p.y), Size(p.size, p.size))
            }
            if (alive) {
                drawDino(w / 4f, playerY, sliding, settings.value.skin, shieldCharges, settings.value.colorblind)
            }
            // speed lines
            if (!settings.value.reducedMotion && spd / maxSpd > 0.8f) {
                for (i in 0 until 6) {
                    val ly = 60f + i * 30f
                    val off = (distance * 6f + i * 17f) % 80f
                    drawLine(Color.White.copy(0.3f), Offset(w - off, ly), Offset(w - off - 30f, ly), strokeWidth = 1f)
                }
            }
            if (flashGlow > 0) {
                drawRect(Color(0x6660A5FA).copy(alpha = flashGlow / 18f * 0.4f), Offset(0f, 0f), Size(w, h), style = Stroke(width = 6f))
            }
        }

        // top HUD
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0x99000000), shape = RoundedCornerShape(50)) {
                Text("${distance.toInt()}m · ${settings.value.theme}", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (combo > 1) {
                Surface(color = Color(0xCC22D3EE), shape = RoundedCornerShape(50)) {
                    Text("x$combo", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            Surface(color = Color(0x99000000), shape = RoundedCornerShape(50)) {
                Text("◎ $runCoins", color = Color(0xFFFACC15), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (powerName.isNotBlank()) {
                Surface(color = Color(0xCC6366F1), shape = RoundedCornerShape(50)) {
                    Text("$powerName ${powerLeft / 60}s", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Surface(color = Color(0x99000000), shape = RoundedCornerShape(50),
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { settingsOpen = true }) {
                Text("⚙", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        if (mode == "TimeTrial" && ttEnd > 0 && alive) {
            val secs = max(0, ((ttEnd - System.currentTimeMillis()) / 1000).toInt())
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp)) {
                Surface(color = Color(0x99000000), shape = RoundedCornerShape(50)) {
                    Text("$secs s", color = Color(0xFFFB7185), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        if (!started && alive && !showAnalytics && !settingsOpen) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                Surface(color = Color(0x99000000), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tap to start", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Swipe down = crouch · double-tap = double jump", color = Color.White.copy(0.7f), fontSize = 11.sp)
                    }
                }
            }
        }
        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            DinoSettingsSheet(
                settings = settings.value,
                onChange = { settings.value = it; save() },
                onCalibrate = { calActive = true; calCount = 0; calReact.clear(); settingsOpen = false; resetRun() },
                onClose = { settingsOpen = false },
            )
        }
        AnimatedVisibility(visible = showAnalytics, enter = fadeIn(), exit = fadeOut()) {
            DinoAnalyticsSheet(
                distance = distance.toInt(), runCoins = runCoins, jumps = jumpCount, maxCombo = maxCombo,
                tip = runTip, deaths = deathBuckets.toMap(), reactions = reactions,
                unlocks = unlockedNow.toList(),
                onReplay = { showAnalytics = false; resetRun() },
                onContinue = { showAnalytics = false; onGameOver() },
            )
        }
    }
}

private data class ThemePalette(
    val dayTop: Triple<Int, Int, Int>,
    val dayBot: Triple<Int, Int, Int>,
    val nightTop: Triple<Int, Int, Int>,
    val nightBot: Triple<Int, Int, Int>,
    val ground: Color,
)

private fun themePalette(theme: String): ThemePalette = when (theme) {
    "jungle" -> ThemePalette(Triple(134, 239, 172), Triple(16, 94, 56), Triple(9, 30, 32), Triple(4, 18, 24), Color(0xFF14532D))
    "volcano" -> ThemePalette(Triple(251, 146, 60), Triple(127, 29, 29), Triple(38, 5, 5), Triple(12, 0, 0), Color(0xFF7F1D1D))
    "ice" -> ThemePalette(Triple(186, 230, 253), Triple(100, 116, 139), Triple(12, 35, 60), Triple(4, 12, 30), Color(0xFFCBD5E1))
    "moon" -> ThemePalette(Triple(148, 163, 184), Triple(30, 41, 59), Triple(10, 12, 24), Triple(0, 0, 8), Color(0xFF475569))
    else -> ThemePalette(Triple(253, 230, 138), Triple(251, 146, 60), Triple(12, 18, 50), Triple(3, 5, 18), Color(0xFF92400E))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBg(
    dist: Float, dayTime: Float, theme: String, hc: Boolean, cb: String, w: Float, h: Float, groundY: Float,
) {
    val t = (sin(dayTime / 500f) + 1f) / 2f
    fun lerp(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Color = Color(
        ((a.first + (b.first - a.first) * t) / 255f).coerceIn(0f, 1f),
        ((a.second + (b.second - a.second) * t) / 255f).coerceIn(0f, 1f),
        ((a.third + (b.third - a.third) * t) / 255f).coerceIn(0f, 1f),
    )
    val pal = themePalette(theme)
    val top = lerp(pal.dayTop, pal.nightTop)
    val bot = lerp(pal.dayBot, pal.nightBot)
    drawRect(Brush.verticalGradient(listOf(applyCb(top, cb), applyCb(bot, cb))), size = Size(w, h))
    // far parallax clouds
    val cloudCol = if (hc) Color.White else Color.White.copy(0.3f)
    for (i in 0 until 5) {
        var cx = ((i * 90f - dist * 0.4f) % (w + 60f) + w + 60f) % (w + 60f) - 30f
        drawCircle(applyCb(cloudCol, cb), radius = 14f, center = Offset(cx, 50f + (i % 2) * 12f))
    }
    // ground
    drawRect(applyCb(pal.ground, cb), Offset(0f, groundY), Size(w, h - groundY))
    // ground details
    val det = Color.Black.copy(0.18f)
    var x = -((dist * 4f) % 22f)
    while (x < w) { drawRect(det, Offset(x, groundY + 8f), Size(12f, 3f)); x += 22f }
    if (t > 0.6f) {
        val starCol = Color.White.copy(0.6f)
        for (i in 0 until 22) {
            val sx = ((i * 53f + dist * 0.2f) % w)
            val sy = (i * 17f) % (groundY - 20f)
            drawRect(starCol, Offset(sx, sy), Size(1.2f, 1.2f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawObstacle(
    o: DObs, theme: String, hc: Boolean, cb: String, dist: Float, groundY: Float,
) {
    fun fill(def: Color) = if (hc) Color.White else applyCb(def, cb)
    when (o.kind) {
        ObsKind.CACTUS -> {
            val col = fill(when (theme) { "volcano" -> Color(0xFFDC2626); "ice" -> Color(0xFF7DD3FC); else -> Color(0xFF16A34A) })
            drawRect(col, Offset(o.x, o.y), Size(o.w, o.h))
            drawRect(col, Offset(o.x - 4f, o.y + 4f), Size(4f, 10f))
            drawRect(col, Offset(o.x + o.w, o.y + 8f), Size(4f, 10f))
        }
        ObsKind.ROCK -> drawCircle(fill(Color(0xFF9CA3AF)), radius = o.h / 2f, center = Offset(o.x + o.w / 2f, o.y + o.h / 2f))
        ObsKind.PTERO -> {
            val col = fill(Color(0xFFA855F7))
            val wing = (sin(dist / 5f + o.x) + 1f) * 6f
            drawRect(col, Offset(o.x, o.y), Size(o.w, o.h))
            drawRect(col, Offset(o.x - 10f, o.y + o.h / 2f - wing), Size(10f, 2f))
            drawRect(col, Offset(o.x + o.w, o.y + o.h / 2f - wing), Size(10f, 2f))
            drawRect(Color.Black.copy(0.25f), Offset(o.x - 2f, groundY - 2f), Size(o.w + 4f, 2f))
        }
        ObsKind.LOG -> {
            drawRect(fill(Color(0xFF92400E)), Offset(o.x, o.y), Size(o.w, o.h))
            drawRect(Color.Black.copy(0.3f), Offset(o.x + 4f, o.y + 4f), Size(4f, o.h - 8f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDino(x: Float, y: Float, sliding: Boolean, skin: String, shield: Int, cb: String) {
    val (body, accent, eye) = when (skin) {
        "cyber" -> Triple(Color(0xFF22D3EE), Color(0xFFA78BFA), Color.White)
        "bone" -> Triple(Color(0xFFE5E7EB), Color(0xFFF3F4F6), Color.Black)
        "lava" -> Triple(Color(0xFFF97316), Color(0xFFFACC15), Color.White)
        "ice" -> Triple(Color(0xFFBAE6FD), Color(0xFF7DD3FC), Color.Black)
        "gold" -> Triple(Color(0xFFFACC15), Color(0xFFFDE68A), Color.Black)
        else -> Triple(Color(0xFFFACC15), Color(0xFFF59E0B), Color.Black)
    }
    val b = applyCb(body, cb); val a = applyCb(accent, cb)
    if (sliding) {
        drawRect(b, Offset(x - 14f, y - 14f), Size(28f, 14f))
        drawRect(a, Offset(x + 4f, y - 14f), Size(10f, 4f))
        drawRect(eye, Offset(x + 8f, y - 11f), Size(3f, 3f))
    } else {
        drawRect(b, Offset(x - 12f, y - 30f), Size(24f, 30f)) // body
        drawRect(b, Offset(x - 18f, y - 24f), Size(6f, 8f))   // tail
        drawRect(a, Offset(x + 6f, y - 36f), Size(12f, 12f))  // head
        drawRect(eye, Offset(x + 13f, y - 33f), Size(3f, 3f))
        val leg = (System.currentTimeMillis() / 90L) % 2L == 0L
        drawRect(b, Offset(x - 8f, y - 6f), Size(5f, 6f + (if (leg) 0f else 2f)))
        drawRect(b, Offset(x + 3f, y - 6f), Size(5f, 6f + (if (leg) 2f else 0f)))
    }
    if (shield > 0) {
        drawCircle(Color(0xB360A5FA), radius = 22f, center = Offset(x, y - 16f), style = Stroke(width = 2f))
    }
}

@Composable
private fun DinoSettingsSheet(
    settings: DinoSettings, onChange: (DinoSettings) -> Unit,
    onCalibrate: () -> Unit, onClose: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null
    ) { onClose() }) {
        Surface(
            color = Color(0xFF0F172A), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.94f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dino Dash · Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        modifier = Modifier.weight(1f))
                    Text("✕", color = Color.White, fontSize = 18.sp,
                        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Theme", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("desert", "jungle", "volcano", "ice", "moon"), settings.theme) {
                    onChange(settings.copy(theme = it))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Skin", color = Color.White.copy(0.85f), fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth()) {
                    val skins = listOf(
                        Triple("classic", "Classic", true),
                        Triple("cyber", "Cyber", settings.unlockedCyber),
                        Triple("bone", "Bone", settings.unlockedBone),
                        Triple("lava", "Lava", settings.unlockedLava),
                        Triple("ice", "Ice", settings.unlockedIce),
                        Triple("gold", "Gold", settings.unlockedGold),
                    )
                    Column {
                        Row {
                            skins.take(3).forEach { (id, label, ok) -> SkinChip(id, label, ok, settings.skin) { if (ok) onChange(settings.copy(skin = id)) } }
                        }
                        Row {
                            skins.drop(3).forEach { (id, label, ok) -> SkinChip(id, label, ok, settings.skin) { if (ok) onChange(settings.copy(skin = id)) } }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Jump-buffer (ms): ${settings.jumpBuffer}", color = Color.White, fontSize = 12.sp)
                Slider(value = settings.jumpBuffer.toFloat(), onValueChange = { onChange(settings.copy(jumpBuffer = it.toInt())) },
                    valueRange = 0f..200f, steps = 19)
                Text("Crouch swipe length (px): ${settings.swipeMin}", color = Color.White, fontSize = 12.sp)
                Slider(value = settings.swipeMin.toFloat(), onValueChange = { onChange(settings.copy(swipeMin = it.toInt())) },
                    valueRange = 10f..80f, steps = 13)
                Spacer(modifier = Modifier.height(4.dp))
                ToggleRow("Haptics", settings.haptics) { onChange(settings.copy(haptics = it)) }
                ToggleRow("Reduced motion", settings.reducedMotion) { onChange(settings.copy(reducedMotion = it)) }
                ToggleRow("High contrast", settings.highContrast) { onChange(settings.copy(highContrast = it)) }
                ToggleRow("One-handed (tap zone bottom half)", settings.oneHanded) { onChange(settings.copy(oneHanded = it)) }
                ToggleRow("Battery saver (~30 fps)", settings.batterySaver) { onChange(settings.copy(batterySaver = it)) }
                ToggleRow("Voice announcer", settings.announcer) { onChange(settings.copy(announcer = it)) }
                ToggleRow("Assist · auto-jump", settings.assistAutoJump) { onChange(settings.copy(assistAutoJump = it)) }
                ToggleRow("Assist · forgiveness hitbox", settings.assistBigBox) { onChange(settings.copy(assistBigBox = it)) }
                ToggleRow("Practice (invincible)", settings.assistInvincible) { onChange(settings.copy(assistInvincible = it)) }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Colourblind mode", color = Color.White.copy(0.85f), fontSize = 12.sp)
                ChipsRow(listOf("off", "protanopia", "deuteranopia", "tritanopia"), settings.colorblind) { onChange(settings.copy(colorblind = it)) }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Coins: ${settings.coins} · Longest: ${settings.longestM}m · Total jumps: ${settings.totalJumps}",
                    color = Color.White.copy(0.65f), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Permanent upgrades", color = Color.White.copy(0.85f), fontSize = 12.sp)
                Row {
                    UpgradeChip("Double jump · 50", settings.upgradeDoubleJump, settings.upgradeDoubleJump || settings.coins < 50) {
                        if (!settings.upgradeDoubleJump && settings.coins >= 50) onChange(settings.copy(upgradeDoubleJump = true, coins = settings.coins - 50))
                    }
                    UpgradeChip("Shield Lv ${settings.upgradeShield}/3 · 30", settings.upgradeShield > 0, settings.upgradeShield >= 3 || settings.coins < 30) {
                        if (settings.upgradeShield < 3 && settings.coins >= 30) onChange(settings.copy(upgradeShield = settings.upgradeShield + 1, coins = settings.coins - 30))
                    }
                    UpgradeChip("Magnet Lv ${settings.upgradeMagnet}/3 · 30", settings.upgradeMagnet > 0, settings.upgradeMagnet >= 3 || settings.coins < 30) {
                        if (settings.upgradeMagnet < 3 && settings.coins >= 30) onChange(settings.copy(upgradeMagnet = settings.upgradeMagnet + 1, coins = settings.coins - 30))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(color = Color(0xFF6366F1), shape = RoundedCornerShape(50),
                    modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCalibrate() }) {
                    Text("Calibration · jump test (5 obstacles)", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ChipsRow(items: List<String>, selected: String, onPick: (String) -> Unit) {
    Row {
        items.forEach { id ->
            Surface(
                color = if (selected == id) Color(0xFF6366F1) else Color(0x33FFFFFF),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(end = 6.dp, top = 6.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { onPick(id) },
            ) { Text(id, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
        }
    }
}

@Composable
private fun SkinChip(id: String, label: String, unlocked: Boolean, selected: String, onClick: () -> Unit) {
    Surface(
        color = if (selected == id) Color(0xFF6366F1) else Color(0x33FFFFFF),
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(end = 6.dp, top = 6.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = unlocked,
        ) { onClick() },
    ) {
        Text(if (unlocked) label else "🔒 $label",
            color = if (unlocked) Color.White else Color.White.copy(0.55f),
            fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun UpgradeChip(label: String, on: Boolean, disabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (on) Color(0xFF22C55E) else if (disabled) Color(0x22FFFFFF) else Color(0x55FFFFFF),
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(end = 6.dp, top = 6.dp).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = !disabled,
        ) { onClick() },
    ) { Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = value, onCheckedChange = onChange)
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun DinoAnalyticsSheet(
    distance: Int, runCoins: Int, jumps: Int, maxCombo: Int,
    tip: String, deaths: Map<String, Int>, reactions: List<Float>,
    unlocks: List<String>,
    onReplay: () -> Unit, onContinue: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xCC000000))) {
        Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(18.dp),
            modifier = Modifier.align(Alignment.Center).padding(20.dp).fillMaxWidth(0.94f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Run analysis · ${distance}m", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(color = Color(0x3360A5FA), shape = RoundedCornerShape(10.dp)) {
                    Text(tip, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    StatTile("Distance", "${distance}m", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    StatTile("Coins", "+$runCoins", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    StatTile("Jumps", "$jumps", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(6.dp))
                    StatTile("Combo", "$maxCombo", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Death heatmap", color = Color.White.copy(0.7f), fontSize = 11.sp)
                val total = max(1, deaths.values.sum())
                deaths.forEach { (k, v) ->
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
                Text("Reaction time (obstacle spawn → jump)", color = Color.White.copy(0.7f), fontSize = 11.sp)
                Canvas(modifier = Modifier.fillMaxWidth().height(60.dp).background(Color(0x14FFFFFF))) {
                    val arr = reactions.takeLast(32)
                    if (arr.size < 2) return@Canvas
                    val mx = max(1f, arr.max())
                    var prevX = 0f; var prevY = size.height - (arr[0] / mx) * size.height
                    for (i in 1 until arr.size) {
                        val x = (i.toFloat() / (arr.size - 1)) * size.width
                        val y = size.height - (arr[i] / mx) * size.height
                        drawLine(Color(0xFF60A5FA), Offset(prevX, prevY), Offset(x, y), strokeWidth = 1.6f)
                        prevX = x; prevY = y
                    }
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
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Color(0x14FFFFFF), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun List<Float>.max(): Float = if (isEmpty()) 0f else reduce { a, b -> if (a > b) a else b }
