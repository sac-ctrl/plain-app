package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.HollowSettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.random.Random

@Serializable
data class HollowSettings(
    val mode: String = "classic", // classic | daily | blitz | endless
    val codeLen: Int = 4,
    val paletteSize: Int = 6,
    val maxAttempts: Int = 10,
    val allowDup: Boolean = true,
    val theme: String = "neon", // neon | candy | mono | nature
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val showColorblind: Boolean = false,
    val bestScore: Int = 0,
    val bestAttempts: Int = 0, // fewest attempts on a win
    val wins: Int = 0,
    val losses: Int = 0,
    val powerReveal: Int = 2,
    val powerEliminate: Int = 2,
    val powerUndo: Int = 1,
    val lastDailyDate: String = "",
    val lastPowerRefresh: String = "",
)

private val pegPalettes = mapOf(
    "neon" to listOf(
        Color(0xFFFF3B6E), Color(0xFFFFD166), Color(0xFF38FFB1), Color(0xFF38BDF8),
        Color(0xFFA855F7), Color(0xFFF97316), Color(0xFFFF66E0), Color(0xFFB8FF66),
    ),
    "candy" to listOf(
        Color(0xFFFF7AA8), Color(0xFFFFC371), Color(0xFFFFE76A), Color(0xFF7BE495),
        Color(0xFF6FC3FF), Color(0xFFB28DFF), Color(0xFFFF9CD7), Color(0xFFCFF09E),
    ),
    "mono" to listOf(
        Color(0xFFE8E8E8), Color(0xFFC0C0C0), Color(0xFF909090), Color(0xFF606060),
        Color(0xFF404040), Color(0xFFB0B0FF), Color(0xFFFFB0B0), Color(0xFFB0FFB0),
    ),
    "nature" to listOf(
        Color(0xFF8FBC8F), Color(0xFF4682B4), Color(0xFFCD853F), Color(0xFFDC143C),
        Color(0xFFFFD700), Color(0xFF9370DB), Color(0xFF20B2AA), Color(0xFFFF7F50),
    ),
)

private val cbGlyphs = listOf("●", "■", "▲", "◆", "★", "♥", "♣", "♠")

private data class HGuess(val pegs: List<Int>, val black: Int, val white: Int)

private fun todayStr(): String {
    val d = java.util.Calendar.getInstance()
    return "${d.get(java.util.Calendar.YEAR)}-${d.get(java.util.Calendar.MONTH) + 1}-${d.get(java.util.Calendar.DAY_OF_MONTH)}"
}

private fun seedFromDate(): Long {
    val s = todayStr()
    var h = 1469598103934665603L
    for (c in s) {
        h = h xor c.code.toLong()
        h *= 1099511628211L
    }
    return h
}

private fun makeCode(len: Int, palette: Int, allowDup: Boolean, rnd: Random): List<Int> {
    val out = mutableListOf<Int>()
    if (allowDup) {
        repeat(len) { out.add(rnd.nextInt(palette)) }
    } else {
        val pool = (0 until palette).toMutableList()
        repeat(len) {
            val i = rnd.nextInt(pool.size)
            out.add(pool.removeAt(i))
        }
    }
    return out
}

private fun scoreGuess(secret: List<Int>, guess: List<Int>): Pair<Int, Int> {
    var black = 0
    val secretLeft = mutableListOf<Int>()
    val guessLeft = mutableListOf<Int>()
    for (i in secret.indices) {
        if (guess[i] == secret[i]) black++
        else { secretLeft.add(secret[i]); guessLeft.add(guess[i]) }
    }
    var white = 0
    val pool = secretLeft.toMutableList()
    for (g in guessLeft) {
        val idx = pool.indexOf(g)
        if (idx >= 0) { white++; pool.removeAt(idx) }
    }
    return black to white
}

@Composable
fun HollowGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    var settings by remember { mutableStateOf(HollowSettings()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val raw = HollowSettingsJsonPreference.getAsync(context)
            if (raw.isNotBlank() && raw != "{}") settings = json.decodeFromString(raw)
        }
        // Daily power refresh
        val today = todayStr()
        if (settings.lastPowerRefresh != today) {
            settings = settings.copy(
                powerReveal = max(2, settings.powerReveal),
                powerEliminate = max(2, settings.powerEliminate),
                powerUndo = max(1, settings.powerUndo),
                lastPowerRefresh = today,
            )
        }
        loaded = true
    }
    fun persist(next: HollowSettings) {
        settings = next
        scope.launch { runCatching { HollowSettingsJsonPreference.putAsync(context, json.encodeToString(next)) } }
    }

    // Difficulty defaults
    val effLen = when (difficulty) { "Easy" -> 4; "Hard" -> 5; "Insane" -> 6; else -> settings.codeLen }
    val effPal = when (difficulty) { "Easy" -> 6; "Hard" -> 7; "Insane" -> 8; else -> settings.paletteSize }
    val effAttempts = when (difficulty) { "Easy" -> 12; "Hard" -> 10; "Insane" -> 8; else -> settings.maxAttempts }
    val palette = pegPalettes[settings.theme] ?: pegPalettes["neon"]!!

    val rndRef = remember(loaded, mode, difficulty) {
        if (mode.equals("daily", true)) Random(seedFromDate()) else Random(System.currentTimeMillis())
    }
    var secret by remember(loaded, mode, difficulty) {
        mutableStateOf(makeCode(effLen, effPal, settings.allowDup, rndRef))
    }
    var current by remember(loaded, mode, difficulty) { mutableStateOf<List<Int?>>(List(effLen) { null }) }
    var guesses by remember(loaded, mode, difficulty) { mutableStateOf(listOf<HGuess>()) }
    var alive by remember(loaded, mode, difficulty) { mutableStateOf(true) }
    var score by remember(loaded, mode, difficulty) { mutableStateOf(0) }
    var won by remember(loaded, mode, difficulty) { mutableStateOf(false) }
    var time by remember(loaded, mode, difficulty) { mutableStateOf(if (mode.equals("blitz", true)) 90 else 0) }
    var hintMask by remember(loaded, mode, difficulty) { mutableStateOf(setOf<Int>()) } // colors eliminated
    var revealed by remember(loaded, mode, difficulty) { mutableStateOf(setOf<Int>()) } // pre-revealed positions

    LaunchedEffect(loaded, mode, alive, paused) {
        if (!loaded || mode != "blitz" || !alive) return@LaunchedEffect
        while (alive && !paused && time > 0) { delay(1000); time -= 1 }
        if (alive && time <= 0) { alive = false; onGameOver() }
    }

    fun submit() {
        val g = current.mapNotNull { it }
        if (g.size != effLen) return
        val (b, w) = scoreGuess(secret, g)
        guesses = guesses + HGuess(g, b, w)
        current = List(effLen) { null }
        if (b == effLen) {
            won = true; alive = false
            val left = effAttempts - guesses.size
            val multi = when (difficulty) { "Easy" -> 1; "Hard" -> 2; "Insane" -> 3; else -> 1 }
            val timeBonus = if (mode.equals("blitz", true)) time * 5 else 0
            val s = (200 + left * 60 + timeBonus) * multi
            score = s; onScore(s)
            val newBest = max(settings.bestScore, s)
            val bestAtt = if (settings.bestAttempts == 0) guesses.size else minOf(settings.bestAttempts, guesses.size)
            persist(settings.copy(bestScore = newBest, bestAttempts = bestAtt, wins = settings.wins + 1,
                lastDailyDate = if (mode.equals("daily", true)) todayStr() else settings.lastDailyDate))
            onGameOver()
        } else if (guesses.size >= effAttempts) {
            alive = false
            persist(settings.copy(losses = settings.losses + 1))
            onGameOver()
        }
    }

    fun reveal() {
        if (settings.powerReveal <= 0 || !alive) return
        val candidates = (0 until effLen).filter { it !in revealed }
        if (candidates.isEmpty()) return
        val pos = candidates.random()
        revealed = revealed + pos
        val cur = current.toMutableList(); cur[pos] = secret[pos]; current = cur
        persist(settings.copy(powerReveal = settings.powerReveal - 1))
    }
    fun eliminate() {
        if (settings.powerEliminate <= 0 || !alive) return
        val notInSecret = (0 until effPal).filter { it !in secret && it !in hintMask }
        if (notInSecret.isEmpty()) return
        hintMask = hintMask + notInSecret.random()
        persist(settings.copy(powerEliminate = settings.powerEliminate - 1))
    }
    fun undo() {
        if (settings.powerUndo <= 0 || guesses.isEmpty() || !alive) return
        guesses = guesses.dropLast(1)
        persist(settings.copy(powerUndo = settings.powerUndo - 1))
    }

    val attemptsLeft = effAttempts - guesses.size

    Column(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFF0B1024), Color(0xFF160E2C)))
    ).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("HOLLOW MINDS", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Crack the cipher · ${effLen} pegs · ${effPal} colors",
                    color = Color.White.copy(0.65f), fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Attempts $attemptsLeft", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (mode.equals("blitz", true)) Text("⏱ ${time}s",
                    color = if (time < 15) Color(0xFFEF4444) else accent, fontSize = 12.sp)
                if (mode.equals("daily", true)) Text("Daily · ${todayStr()}", color = accent, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Power-ups
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            PowerChip("👁 Reveal · ${settings.powerReveal}", accent, settings.powerReveal > 0 && alive) { reveal() }
            PowerChip("✖ Eliminate · ${settings.powerEliminate}", accent, settings.powerEliminate > 0 && alive) { eliminate() }
            PowerChip("↶ Undo · ${settings.powerUndo}", accent, settings.powerUndo > 0 && guesses.isNotEmpty() && alive) { undo() }
        }
        Spacer(Modifier.height(10.dp))

        // History
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Empty placeholder rows showing remaining attempts
            for ((idx, g) in guesses.withIndex()) {
                GuessRow(idx + 1, g.pegs.map { palette[it] }, g.pegs, g.black, g.white, settings.showColorblind)
            }
            for (i in guesses.size until effAttempts) {
                GuessRowPlaceholder(i + 1, effLen)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Current guess slots
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Guess →", color = Color.White.copy(0.6f), fontSize = 11.sp)
            for ((i, c) in current.withIndex()) {
                val color = c?.let { palette[it] } ?: Color.White.copy(0.08f)
                Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(color)
                    .border(2.dp, accent.copy(0.5f), CircleShape)
                    .clickable(enabled = alive) {
                        val cur = current.toMutableList(); cur[i] = null; current = cur
                    }, contentAlignment = Alignment.Center) {
                    if (settings.showColorblind && c != null) Text(cbGlyphs[c % cbGlyphs.size],
                        color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp))
                .background(if (current.all { it != null } && alive) accent else Color.White.copy(0.1f))
                .clickable(enabled = current.all { it != null } && alive) { submit() }
                .padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("Submit", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(8.dp))

        // Color palette
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            for (ci in 0 until effPal) {
                val isOut = ci in hintMask
                Box(modifier = Modifier.padding(3.dp).size(40.dp).clip(CircleShape)
                    .background(palette[ci])
                    .alpha(if (isOut) 0.25f else 1f)
                    .border(2.dp, Color.White.copy(0.2f), CircleShape)
                    .clickable(enabled = !isOut && alive) {
                        val firstEmpty = current.indexOfFirst { it == null }
                        if (firstEmpty >= 0) {
                            val cur = current.toMutableList(); cur[firstEmpty] = ci; current = cur
                        }
                    }, contentAlignment = Alignment.Center) {
                    if (settings.showColorblind) Text(cbGlyphs[ci % cbGlyphs.size],
                        color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!alive) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(if (won) "✓ Cracked in ${guesses.size} · +$score" else "✗ Code was:",
                    color = if (won) Color(0xFF38FFB1) else Color(0xFFFF7AA8),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (!won) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center) {
                    for (s in secret) Box(modifier = Modifier.padding(3.dp).size(28.dp)
                        .clip(CircleShape).background(palette[s]))
                }
            }
        }
    }
}

@Composable
private fun PowerChip(label: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))
        .background(if (enabled) accent.copy(0.2f) else Color.White.copy(0.05f))
        .border(1.dp, if (enabled) accent.copy(0.4f) else Color.White.copy(0.1f), RoundedCornerShape(8.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 5.dp)) {
        Text(label, color = if (enabled) Color.White else Color.White.copy(0.4f), fontSize = 10.sp)
    }
}

@Composable
private fun GuessRow(num: Int, colors: List<Color>, idxs: List<Int>, black: Int, white: Int, cb: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$num", color = Color.White.copy(0.4f), fontSize = 10.sp,
            modifier = Modifier.width(20.dp))
        for ((i, c) in colors.withIndex()) {
            Box(modifier = Modifier.padding(2.dp).size(28.dp).clip(CircleShape).background(c),
                contentAlignment = Alignment.Center) {
                if (cb) Text(cbGlyphs[idxs[i] % cbGlyphs.size], color = Color.Black,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        // Feedback pegs
        Row {
            repeat(black) {
                Box(modifier = Modifier.padding(1.dp).size(10.dp).clip(CircleShape).background(Color.Black)
                    .border(1.dp, Color.White, CircleShape))
            }
            repeat(white) {
                Box(modifier = Modifier.padding(1.dp).size(10.dp).clip(CircleShape).background(Color.White)
                    .border(1.dp, Color.Black, CircleShape))
            }
        }
    }
}

@Composable
private fun GuessRowPlaceholder(num: Int, len: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().alpha(0.35f)) {
        Text("$num", color = Color.White.copy(0.3f), fontSize = 10.sp, modifier = Modifier.width(20.dp))
        repeat(len) {
            Box(modifier = Modifier.padding(2.dp).size(28.dp).clip(CircleShape)
                .background(Color.White.copy(0.05f))
                .border(1.dp, Color.White.copy(0.1f), CircleShape))
        }
    }
}
