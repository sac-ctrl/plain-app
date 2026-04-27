package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.TapPatternSettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

@Serializable
private data class TapSettings(
    var gameMode: String = "sequence", // sequence|rhythm|shape|cascade
    var theme: String = "synth",       // synth|particles|dark|nature
    var soundPack: String = "piano",
    var tapEffect: String = "ripple",
    var pads: Int = 4,                 // 4|6|9
    var timingMs: Int = 100,
    var tapPad: Int = 8,
    var adaptive: Boolean = true,
    var multitouch: Boolean = false,
    var haptics: Boolean = true,
    var audio: Boolean = true,
    var voice: Boolean = false,
    var dynamicMusic: Boolean = true,
    var reducedMotion: Boolean = false,
    var highContrast: Boolean = false,
    var oneHanded: Boolean = false,
    var batterySaver: Boolean = false,
    var colorblind: String = "off",
    var assistHint: Boolean = false,
    var assistRadius: Boolean = false,
    var assistTiming: Boolean = false,
    var unlockedSoundPacks: List<String> = listOf("piano"),
    var unlockedEffects: List<String> = listOf("ripple"),
    var bestStreak: Int = 0,
    var bestAccuracy: Int = 0,
    var totalSeq: Int = 0,
    var totalTaps: Int = 0,
    var best: Int = 0,
    var puHint: Int = 2,
    var puSlow: Int = 2,
    var puLife: Int = 1,
    var lastRefresh: String = "",
    var recentMisses: Int = 0,
    var recentCorrect: Int = 0,
)

private fun todayTap(): String {
    val d = java.util.Calendar.getInstance()
    return "${d.get(java.util.Calendar.YEAR)}-${d.get(java.util.Calendar.MONTH) + 1}-${d.get(java.util.Calendar.DAY_OF_MONTH)}"
}
private fun seedTodayTap(): Int {
    val d = java.util.Calendar.getInstance()
    return d.get(java.util.Calendar.YEAR) * 10000 + (d.get(java.util.Calendar.MONTH) + 1) * 100 + d.get(java.util.Calendar.DAY_OF_MONTH)
}

private val PAD_PALETTE = listOf(
    Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFEAB308),
    Color(0xFFA855F7), Color(0xFF06B6D4), Color(0xFFF97316), Color(0xFFEC4899), Color(0xFF14B8A6)
)
private val SHAPE_GLYPHS = listOf("●","■","▲","◆","★","♥","♣","♠","✦")

private data class RhTarget(val id: Int, val lane: Int, var spawnT: Long, var hit: Boolean = false)
private data class CTile(val id: Int, val xPct: Float, var yPct: Float, val color: Int, val order: Int, var tapped: Boolean = false)

@Composable
fun TapGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true } }
    var settings by remember { mutableStateOf(TapSettings()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val raw = TapPatternSettingsJsonPreference.getAsync(context)
            if (raw.isNotBlank() && raw != "{}") settings = json.decodeFromString(raw)
        } catch (_: Exception) {}
        if (mode.isNotBlank() && mode.lowercase() != "classic") {
            val m = when (mode.lowercase()) { "time", "timed", "rhythm" -> "rhythm"; "survival" -> "cascade"; "challenge" -> "shape"; else -> settings.gameMode }
            settings = settings.copy(gameMode = m)
        }
        if (settings.lastRefresh != todayTap()) {
            settings = settings.copy(
                puHint = min(5, settings.puHint + 2),
                puSlow = min(5, settings.puSlow + 1),
                puLife = min(3, settings.puLife + 1),
                lastRefresh = todayTap(),
            )
        }
        loaded = true
    }
    fun save(s: TapSettings) {
        settings = s
        scope.launch { try { TapPatternSettingsJsonPreference.putAsync(context, json.encodeToString(s)) } catch (_: Exception) {} }
    }
    if (!loaded) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = Color.White) }; return }

    val haptic = LocalHapticFeedback.current
    fun buzzOk() { if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun buzzMiss() { if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    val numPads = if (settings.gameMode == "rhythm") 4 else settings.pads
    val rng = remember(settings.gameMode) { Random(seedTodayTap().toLong()) }

    // shared state
    var score by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }
    var bestCombo by remember { mutableStateOf(0) }
    var lives by remember { mutableStateOf(1) }
    var maxLives by remember { mutableStateOf(1) }
    var alive by remember { mutableStateOf(true) }
    var hintIdx by remember { mutableStateOf(-1) }
    var lit by remember { mutableStateOf(-1) }
    var settingsOpen by remember { mutableStateOf(false) }
    var analyticsOpen by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var lastJudge by remember { mutableStateOf("") }

    // sequence
    val seq = remember { mutableStateListOf<Int>() }
    var seqIdx by remember { mutableStateOf(0) }
    var showing by remember { mutableStateOf(true) }

    // shape
    val shapePath = remember { mutableStateListOf<Int>() }
    var shapeStep by remember { mutableStateOf(0) }

    // rhythm
    val rhTargets = remember { mutableStateListOf<RhTarget>() }
    var rId by remember { mutableStateOf(1) }
    var rhStartTime by remember { mutableStateOf(0L) }
    var bpm by remember { mutableStateOf(100) }
    var nowMs by remember { mutableStateOf(0L) }

    // cascade
    val cTiles = remember { mutableStateListOf<CTile>() }
    var cId by remember { mutableStateOf(1) }
    var cNext by remember { mutableStateOf(0) }
    var cRound by remember { mutableStateOf(0) }

    // analytics
    val padTaps = remember { mutableStateListOf<Int>() }
    val padTimingMs = remember { mutableStateListOf<Long>() }
    val histo = remember { mutableStateListOf<Int>() }

    fun resetCommon() {
        score = 0; combo = 0; bestCombo = 0; alive = true
        seq.clear(); seqIdx = 0; showing = true
        shapePath.clear(); shapeStep = 0
        rhTargets.clear(); rhStartTime = System.currentTimeMillis()
        cTiles.clear(); cNext = 0; cRound = 0
        bpm = 90 + rng.nextInt(60) + (if (difficulty == "Hard") 20 else 0) + (if (difficulty == "Insane") 40 else 0)
        lives = 1 + settings.puLife; maxLives = lives
        padTaps.clear(); for (i in 0 until numPads) padTaps.add(0)
        padTimingMs.clear(); for (i in 0 until numPads) padTimingMs.add(0L)
        histo.clear(); for (i in 0 until 20) histo.add(0)
        statusText = "Get ready…"; lastJudge = ""
        onScore(0)
    }

    fun startSequence() {
        val v = rng.nextInt(numPads)
        seq.add(v); seqIdx = 0; showing = true; statusText = "Watch · sequence ${seq.size}"
    }
    fun startShape() {
        shapePath.clear()
        val variants = listOf(
            listOf(0, if (numPads >= 4) 2 else 1, numPads - 1),
            listOf(0, 1, numPads - 2, numPads - 1),
            (0 until numPads).toList(),
        )
        shapePath.addAll(variants[rng.nextInt(variants.size)])
        shapeStep = 0
        statusText = "Trace the shape"
        showing = false
    }
    fun spawnRhythm(beatStart: Int) {
        for (i in 0 until 24) {
            val lane = rng.nextInt(4)
            val t = rhStartTime + ((beatStart + i + 2) * (60000L / bpm))
            rhTargets.add(RhTarget(rId++, lane, t))
        }
    }
    fun spawnCascadeRound() {
        cNext = 0
        val n = 4 + (cRound * 0.7f).toInt()
        for (i in 0 until n) {
            cTiles.add(CTile(cId++, xPct = 5f + rng.nextInt(9) * 10f, yPct = -i * 14f,
                color = rng.nextInt(4), order = i))
        }
    }
    fun startMode() {
        resetCommon()
        when (settings.gameMode) {
            "sequence" -> { showing = true; startSequence() }
            "shape" -> { startShape() }
            "rhythm" -> { statusText = "Tap on the beat"; spawnRhythm(0) }
            "cascade" -> { statusText = "Tap tiles in order"; spawnCascadeRound() }
        }
    }

    LaunchedEffect(loaded, settings.gameMode, settings.pads) { startMode() }

    // sequence "show" coroutine
    LaunchedEffect(seq.size, settings.gameMode, paused) {
        if (settings.gameMode != "sequence" || paused) return@LaunchedEffect
        if (seq.isEmpty()) return@LaunchedEffect
        showing = true
        var sp = if (difficulty == "Easy") 700L else if (difficulty == "Hard") 360L else if (difficulty == "Insane") 240L else 500L
        if (settings.adaptive && settings.recentMisses >= 2) sp += 120
        if (settings.adaptive && settings.recentCorrect >= 5) sp = max(180L, sp - 60L)
        delay(350)
        for (s in seq) {
            lit = s
            delay(sp)
            lit = -1
            delay(150)
        }
        showing = false; seqIdx = 0
        statusText = "Tap the pattern · 1/${seq.size}"
    }

    // rhythm + cascade game loop
    LaunchedEffect(settings.gameMode, alive, paused) {
        if (paused || !alive) return@LaunchedEffect
        if (settings.gameMode != "rhythm" && settings.gameMode != "cascade") return@LaunchedEffect
        val targetFps = if (settings.batterySaver) 30 else 60
        val frameMs = (1000 / targetFps).toLong()
        while (alive && !paused) {
            nowMs = System.currentTimeMillis()
            if (settings.gameMode == "rhythm") {
                val cutoff = nowMs + 200
                val miss = rhTargets.filter { !it.hit && it.spawnT < cutoff - 250 }
                for (m in miss) { m.hit = true; combo = 0; lives -= 1 }
                if (rhTargets.size < 8) {
                    val beat = ((nowMs - rhStartTime) / (60000L / bpm)).toInt()
                    spawnRhythm(beat)
                }
                rhTargets.removeAll { it.hit && it.spawnT < nowMs - 1000 }
            } else if (settings.gameMode == "cascade") {
                for (t in cTiles) if (!t.tapped) t.yPct += 0.5f + cRound * 0.04f
                val miss = cTiles.filter { !it.tapped && it.yPct > 100 }
                for (m in miss) { m.tapped = true; combo = 0; lives -= 1 }
                if (cTiles.all { it.tapped }) { cRound += 1; spawnCascadeRound() }
                cTiles.removeAll { it.tapped && it.yPct > 110 }
            }
            if (lives <= 0) {
                alive = false
                if (score > settings.best) settings = settings.copy(best = score)
                save(settings.copy(
                    bestStreak = max(settings.bestStreak, bestCombo),
                    totalSeq = settings.totalSeq + (seq.size - 1),
                    totalTaps = settings.totalTaps,
                ))
                analyticsOpen = true
                onGameOver()
                break
            }
            delay(frameMs)
        }
    }

    fun finishGame() {
        alive = false
        if (score > settings.best) settings = settings.copy(best = score)
        save(settings.copy(
            bestStreak = max(settings.bestStreak, bestCombo),
            totalSeq = settings.totalSeq + 1,
            totalTaps = settings.totalTaps,
        ))
        analyticsOpen = true
        onGameOver()
    }

    fun pressPad(p: Int) {
        if (!alive || paused) return
        when (settings.gameMode) {
            "sequence" -> {
                if (showing) return
                lit = p; padTaps[p] = padTaps[p] + 1
                if (p != seq[seqIdx]) {
                    lives -= 1; combo = 0; settings = settings.copy(recentMisses = settings.recentMisses + 1, totalTaps = settings.totalTaps + 1)
                    buzzMiss()
                    if (lives <= 0) { finishGame(); return }
                    seqIdx = 0; showing = true; statusText = "Replay sequence ${seq.size}"
                    return
                }
                seqIdx += 1; combo += 1; bestCombo = max(bestCombo, combo); buzzOk()
                statusText = "Tap the pattern · ${seqIdx + 1}/${seq.size}"
                if (seqIdx >= seq.size) {
                    val bonus = seq.size * 10 * max(1, combo / 5)
                    score += bonus; onScore(score); statusText = "Sequence cleared!"
                    settings = settings.copy(totalTaps = settings.totalTaps + 1, recentCorrect = settings.recentCorrect + 1)
                    var s = settings
                    if (combo >= 10 && !s.unlockedEffects.contains("sparks")) s = s.copy(unlockedEffects = s.unlockedEffects + "sparks")
                    if (combo >= 25 && !s.unlockedSoundPacks.contains("synth")) s = s.copy(unlockedSoundPacks = s.unlockedSoundPacks + "synth")
                    if (combo >= 50 && !s.unlockedEffects.contains("stars")) s = s.copy(unlockedEffects = s.unlockedEffects + "stars")
                    if (s != settings) settings = s
                    scope.launch { delay(450); startSequence() }
                }
            }
            "shape" -> {
                lit = p; padTaps[p] = padTaps[p] + 1
                if (p != shapePath[shapeStep]) {
                    lives -= 1; combo = 0; buzzMiss(); if (lives <= 0) { finishGame() }; return
                }
                shapeStep += 1; combo += 1; bestCombo = max(bestCombo, combo); buzzOk()
                if (shapeStep >= shapePath.size) {
                    score += shapePath.size * 12; onScore(score)
                    scope.launch { delay(450); startShape() }
                }
            }
            else -> { /* rhythm / cascade press handled separately */ }
        }
    }

    fun pressRhythmLane(lane: Int) {
        if (!alive || paused) return
        val now = System.currentTimeMillis()
        val cand = rhTargets.filter { !it.hit && it.lane == lane }.minByOrNull { abs(it.spawnT - now) } ?: return
        val dtL = abs(cand.spawnT - now)
        val dt = dtL.toFloat()
        val w = settings.timingMs * (if (settings.assistTiming) 1.5f else 1f)
        if (dt > w * 1.5f) { combo = 0; lives -= 1; buzzMiss(); return }
        cand.hit = true
        val j = if (dt < w * 0.5f) "PERFECT" else if (dt < w) "GOOD" else "MISS"
        lastJudge = j
        if (j != "MISS") combo += 1 else combo = 0
        bestCombo = max(bestCombo, combo)
        val pts = if (j == "PERFECT") 50 else if (j == "GOOD") 25 else 0
        score += pts * max(1, combo / 5)
        onScore(score)
        padTaps[lane] = padTaps[lane] + 1
        padTimingMs[lane] = padTimingMs[lane] + dtL
        val bin = max(0, min(histo.size - 1, (dt / (2f * w / histo.size)).toInt()))
        histo[bin] = min(50, histo[bin] + 4)
        if (j == "PERFECT") buzzOk() else if (j == "GOOD") {} else buzzMiss()
        settings = settings.copy(totalTaps = settings.totalTaps + 1)
    }
    fun pressCascade(t: CTile) {
        if (!alive || paused) return
        if (t.order != cNext) { combo = 0; lives -= 1; buzzMiss(); return }
        t.tapped = true; cNext += 1; combo += 1; bestCombo = max(bestCombo, combo)
        score += 20 + combo; onScore(score); buzzOk()
        padTaps[t.color] = padTaps[t.color] + 1
        settings = settings.copy(totalTaps = settings.totalTaps + 1)
    }

    fun usePower(kind: String) {
        when (kind) {
            "hint" -> if (settings.puHint > 0) {
                if (settings.gameMode == "sequence" && seq.isNotEmpty()) hintIdx = seq[seqIdx]
                if (settings.gameMode == "shape" && shapePath.isNotEmpty()) hintIdx = shapePath[shapeStep]
                save(settings.copy(puHint = settings.puHint - 1))
                scope.launch { delay(1200); hintIdx = -1 }
            }
            "slow" -> if (settings.puSlow > 0) {
                if (settings.gameMode == "rhythm") for (t in rhTargets) t.spawnT += 1500
                if (settings.gameMode == "cascade") for (t in cTiles) t.yPct -= 25f
                save(settings.copy(puSlow = settings.puSlow - 1))
            }
            "life" -> if (settings.puLife > 0) {
                lives += 1; maxLives = max(maxLives, lives)
                save(settings.copy(puLife = settings.puLife - 1))
            }
        }
    }

    val bg: Brush = when (settings.theme) {
        "particles" -> Brush.radialGradient(listOf(Color(0xFF1E1B4B), Color.Black))
        "dark" -> Brush.linearGradient(listOf(Color.Black, Color.Black))
        "nature" -> Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF022C22)))
        else -> Brush.linearGradient(listOf(Color(0xFF4C1D95), Color(0xFF1E1B4B)))
    }

    Column(modifier = Modifier.fillMaxSize().background(bg).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // HUD
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                ChipT("SCORE", "$score"); ChipT("BEST", "${settings.best}"); ChipT("COMBO", "×$combo"); if (lives < maxLives) ChipT("❤", "$lives/$maxLives")
            }
            Row {
                IconBtnT("💡${settings.puHint}", settings.puHint > 0) { usePower("hint") }
                IconBtnT("⏱${settings.puSlow}", settings.puSlow > 0) { usePower("slow") }
                IconBtnT("❤${settings.puLife}", settings.puLife > 0) { usePower("life") }
                IconBtnT("⚙", true) { settingsOpen = true }
            }
        }
        Text(statusText + (if (lastJudge.isNotEmpty()) " · $lastJudge" else ""),
            color = if (lastJudge == "PERFECT") Color(0xFFFCD34D) else if (lastJudge == "GOOD") Color(0xFF22C55E) else if (lastJudge == "MISS") Color(0xFFEF4444) else Color.White.copy(0.85f),
            fontSize = 13.sp, fontWeight = FontWeight.Bold)

        // Mode-specific area
        when (settings.gameMode) {
            "sequence", "shape" -> {
                val cols = ceil(sqrt(numPads.toDouble())).toInt()
                Column(modifier = Modifier.fillMaxWidth(0.92f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var i = 0
                    while (i < numPads) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0 until cols) {
                                if (i >= numPads) { Box(Modifier.weight(1f)) } else {
                                    val padIdx = i
                                    val baseColor = PAD_PALETTE[padIdx % PAD_PALETTE.size]
                                    val isLit = lit == padIdx
                                    val isHint = (hintIdx == padIdx) || (settings.assistHint && settings.gameMode == "sequence" && !showing && seqIdx < seq.size && seq[seqIdx] == padIdx)
                                    val sc by animateFloatAsState(if (isLit) 0.92f else 1f, animationSpec = tween(120), label = "padScale")
                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1f).scale(sc).clip(RoundedCornerShape(16.dp))
                                            .background(if (isLit) baseColor else baseColor.copy(0.45f))
                                            .let { m -> if (isHint) m.border(3.dp, Color(0xFFFCD34D), RoundedCornerShape(16.dp)) else if (settings.highContrast) m.border(2.dp, Color.White, RoundedCornerShape(16.dp)) else m }
                                            .clickable(enabled = alive && !paused && (settings.gameMode == "shape" || !showing)) { lit = padIdx; pressPad(padIdx); scope.launch { delay(120); lit = -1 } },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (settings.colorblind != "off") Text(SHAPE_GLYPHS[padIdx % SHAPE_GLYPHS.size], color = Color.White, fontSize = 20.sp)
                                        else if (settings.gameMode == "shape" && padIdx in shapePath) Text("${shapePath.indexOf(padIdx) + 1}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    i += 1
                                }
                            }
                        }
                    }
                }
            }
            "rhythm" -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.7f).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.4f))) {
                    val w = maxWidth; val h = maxHeight
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (lane in 0 until 4) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()
                                .background(PAD_PALETTE[lane].copy(0.10f))
                                .clickable { pressRhythmLane(lane) })
                        }
                    }
                    // Falling targets
                    val fallMs = 1500f
                    for (t in rhTargets) {
                        if (t.hit) continue
                        val dt = (t.spawnT - nowMs).toFloat()
                        val yPct = 100f - (dt / fallMs) * 100f
                        if (yPct < -10f || yPct > 110f) continue
                        val xDp = w * (t.lane * 0.25f + 0.125f)
                        val yDp = h * (yPct / 100f)
                        Box(modifier = Modifier
                            .offset(x = xDp - 24.dp, y = yDp - 24.dp)
                            .size(48.dp).clip(RoundedCornerShape(50))
                            .background(PAD_PALETTE[t.lane]))
                    }
                    // hit line
                    Box(modifier = Modifier
                        .offset(y = h * 0.92f).fillMaxWidth().height(2.dp)
                        .background(Color.White.copy(0.6f)))
                }
            }
            "cascade" -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.7f).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.4f))) {
                    val w = maxWidth; val h = maxHeight
                    for (t in cTiles) {
                        if (t.tapped) continue
                        val xDp = w * (t.xPct / 100f)
                        val yDp = h * (t.yPct / 100f)
                        val isNext = t.order == cNext
                        Box(modifier = Modifier
                            .offset(x = xDp, y = yDp).size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PAD_PALETTE[t.color])
                            .let { m -> if (isNext) m.border(3.dp, Color(0xFFFCD34D), RoundedCornerShape(8.dp)) else m }
                            .clickable { pressCascade(t) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${t.order + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
        Text("Mode: ${settings.gameMode} · Pads: $numPads · Pack: ${settings.soundPack} · Seed #${seedTodayTap()}", color = Color.White.copy(0.6f), fontSize = 11.sp)
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            confirmButton = { Button(onClick = { settingsOpen = false }) { Text("Done") } },
            title = { Text("Pattern Pulse · Settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp)) {
                    HeaderT("Mode")
                    ChipsT(listOf("sequence","rhythm","shape","cascade"), settings.gameMode) { save(settings.copy(gameMode = it)); startMode() }
                    HeaderT("Pads")
                    ChipsT(listOf("4","6","9"), settings.pads.toString()) { save(settings.copy(pads = it.toInt())); startMode() }
                    HeaderT("Theme")
                    ChipsT(listOf("synth","particles","dark","nature"), settings.theme) { save(settings.copy(theme = it)) }
                    HeaderT("Sound pack")
                    ChipsT(listOf("piano","drums","synth","8bit"), settings.soundPack) { if (settings.unlockedSoundPacks.contains(it)) save(settings.copy(soundPack = it)) }
                    HeaderT("Tap effect")
                    ChipsT(listOf("ripple","sparks","stars","splash"), settings.tapEffect) { if (settings.unlockedEffects.contains(it)) save(settings.copy(tapEffect = it)) }
                    HeaderT("Timing")
                    SliderT("Window ±${settings.timingMs}ms", settings.timingMs.toFloat(), 40f, 220f) { save(settings.copy(timingMs = it.toInt())) }
                    SliderT("Tap radius +${settings.tapPad}px", settings.tapPad.toFloat(), 0f, 40f) { save(settings.copy(tapPad = it.toInt())) }
                    ToggleT("Adaptive difficulty", settings.adaptive) { save(settings.copy(adaptive = it)) }
                    ToggleT("Multi-touch pads", settings.multitouch) { save(settings.copy(multitouch = it)) }
                    HeaderT("Sound & haptics")
                    ToggleT("Haptics", settings.haptics) { save(settings.copy(haptics = it)) }
                    ToggleT("Sound", settings.audio) { save(settings.copy(audio = it)) }
                    ToggleT("Voice announcer", settings.voice) { save(settings.copy(voice = it)) }
                    ToggleT("Dynamic music layers", settings.dynamicMusic) { save(settings.copy(dynamicMusic = it)) }
                    HeaderT("Accessibility")
                    ToggleT("Reduced motion", settings.reducedMotion) { save(settings.copy(reducedMotion = it)) }
                    ToggleT("High contrast", settings.highContrast) { save(settings.copy(highContrast = it)) }
                    ToggleT("One-handed", settings.oneHanded) { save(settings.copy(oneHanded = it)) }
                    ToggleT("Battery saver", settings.batterySaver) { save(settings.copy(batterySaver = it)) }
                    HeaderT("Colourblind")
                    ChipsT(listOf("off","protanopia","deuteranopia","tritanopia"), settings.colorblind) { save(settings.copy(colorblind = it)) }
                    HeaderT("Assists")
                    ToggleT("Glow next pad", settings.assistHint) { save(settings.copy(assistHint = it)) }
                    ToggleT("Larger tap radius", settings.assistRadius) { save(settings.copy(assistRadius = it)) }
                    ToggleT("Wider timing window", settings.assistTiming) { save(settings.copy(assistTiming = it)) }
                    HeaderT("Stats")
                    Text("Best streak: ${settings.bestStreak}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Text("Best accuracy: ${settings.bestAccuracy}%", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Text("Sequences cleared: ${settings.totalSeq}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Text("Total taps: ${settings.totalTaps}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                }
            }
        )
    }

    if (analyticsOpen) {
        AlertDialog(
            onDismissRequest = { analyticsOpen = false; startMode() },
            confirmButton = { Button(onClick = { analyticsOpen = false; startMode() }) { Text("Play again") } },
            title = { Text("Post-game") },
            text = {
                Column {
                    Text("Score $score · Best combo $bestCombo · Best ${settings.best}", color = Color.White.copy(0.85f))
                    Spacer(Modifier.height(8.dp))
                    Text("Pad heatmap (taps)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    val maxT = (padTaps.maxOrNull() ?: 1).coerceAtLeast(1)
                    val cols = ceil(sqrt(numPads.toDouble())).toInt()
                    Column {
                        var i = 0
                        while (i < numPads) {
                            Row {
                                for (c in 0 until cols) {
                                    if (i >= numPads) { Box(Modifier.weight(1f)) } else {
                                        val v = padTaps[i]
                                        val avgMs = if (v > 0) padTimingMs[i] / v else 0L
                                        Box(modifier = Modifier.padding(2.dp).weight(1f).aspectRatio(1.5f).clip(RoundedCornerShape(6.dp))
                                            .background(PAD_PALETTE[i].copy(alpha = 0.15f + 0.7f * v / maxT)),
                                            contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("$v", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                if (avgMs > 0) Text("${avgMs}ms", color = Color.White.copy(0.7f), fontSize = 9.sp)
                                            }
                                        }
                                        i += 1
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Timing histogram (early ↔ late)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        for ((i, b) in histo.withIndex()) {
                            val h = (b.toFloat() / 50f).coerceIn(0f, 1f)
                            Box(modifier = Modifier.weight(1f).fillMaxHeight(h).padding(horizontal = 0.5.dp).background(Color(0xFF22D3EE)))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Tip: Aim for the centre of the timing window — adjust in Settings.", color = Color(0xFFFCD34D), fontSize = 11.sp)
                }
            }
        )
    }
}

@Composable
private fun ChipT(label: String, value: String) {
    Box(modifier = Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(0.08f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(0.55f), fontSize = 9.sp); Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
private fun IconBtnT(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(horizontal = 2.dp).height(32.dp).alpha(if (enabled) 1f else 0.4f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White)
    }
}
@Composable
private fun HeaderT(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}
@Composable
private fun ChipsT(opts: List<String>, current: String, onChoose: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) {
        for (o in opts) {
            val sel = o == current
            OutlinedButton(onClick = { onChoose(o) }, modifier = Modifier.padding(2.dp).heightIn(min = 28.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) Color(0xFFA855F7) else Color.Transparent)) {
                Text(o, fontSize = 11.sp, color = Color.White)
            }
        }
    }
}
@Composable
private fun ToggleT(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
@Composable
private fun SliderT(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 11.sp)
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
