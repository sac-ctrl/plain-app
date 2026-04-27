package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.MemorySettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Serializable
data class MemorySettings(
    val rows: Int = 4,
    val cols: Int = 4,
    val theme: String = "animals", // animals | space | food | geometry | fantasy | nostalgia
    val cardBack: String = "classic",
    val timed: Boolean = false,
    val timedSec: Int = 60,
    val haptics: Boolean = true,
    val audio: Boolean = true,
    val adaptive: Boolean = true,
    val flip3d: Boolean = true,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val colorblindShapes: Boolean = false,
    val oneHanded: Boolean = false,
    val batterySaver: Boolean = false,
    val assistAutoMatch: Boolean = false,
    val assistLongerReveal: Boolean = false,
    val assistLargeTouch: Boolean = false,
    val gameMode: String = "classic", // classic | timed | memorylane | mismatch | zen
    val flipSpeed: Float = 1f,
    val mismatchMs: Int = 800,
    val unlocks: List<String> = listOf("back-classic"),
    val powerupReveal: Int = 2,
    val powerupShuffle: Int = 1,
    val powerupFreeze: Int = 1,
    val lastPowerupRefresh: String = "",
)

private val memThemes = mapOf(
    "animals" to listOf("🐱","🐶","🦊","🐼","🐯","🐸","🐵","🐰","🦁","🐨","🐮","🐷","🐔","🐧","🦄","🐻"),
    "space"   to listOf("🚀","🌟","🌍","🪐","🌙","☄","👽","🛸","🌞","☀","🌠","⭐","🌑","🌌","🛰","🌒"),
    "food"    to listOf("🍕","🍔","🍟","🌭","🥗","🍣","🍜","🍩","🍦","🍓","🍇","🍎","🥭","🍑","🌶","🥑"),
    "geometry"to listOf("◇","◯","△","▽","◊","▣","✦","✪","✱","✜","❖","⬢","⬣","⬡","◐","◑"),
    "fantasy" to listOf("🐉","💎","🗡","🛡","🏰","🧙","🧝","🧚","🐲","⚔","📜","🔮","🪄","💍","👑","🪙"),
    "nostalgia" to listOf("♠","♣","♥","♦","J","Q","K","A","♢","♤","♧","♡","★","☆","✶","✷"),
)
private val shapeMap = listOf("●","■","▲","◆","★","♥","♣","♠","✦","✚","◐","◔","◓","◑","◒","✸")

private data class MCard(val v: String, val pid: Int, val uid: Int, var flipped: Boolean = false, var found: Boolean = false, var revisits: Int = 0)

private fun todayStr(): String {
    val d = java.util.Calendar.getInstance()
    return "${d.get(java.util.Calendar.YEAR)}-${d.get(java.util.Calendar.MONTH)+1}-${d.get(java.util.Calendar.DAY_OF_MONTH)}"
}

@Composable
fun MemoryGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }
    var settings by remember { mutableStateOf(MemorySettings()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val raw = MemorySettingsJsonPreference.getAsync(context)
            if (raw.isNotEmpty() && raw != "{}") settings = json.decodeFromString(MemorySettings.serializer(), raw)
        } catch (_: Exception) { /* keep default */ }
        // adapt to difficulty if first time per session
        val sizes = mapOf("Easy" to (3 to 4), "Medium" to (4 to 4), "Hard" to (4 to 5), "Insane" to (5 to 6))
        sizes[difficulty]?.let { (r, c) -> if (settings.rows == 4 && settings.cols == 4) settings = settings.copy(rows = r, cols = c) }
        // adapt mode
        when (mode.lowercase()) {
            "time" -> settings = settings.copy(gameMode = "timed", timed = true)
            "classic" -> if (settings.gameMode != "memorylane" && settings.gameMode != "mismatch" && settings.gameMode != "zen")
                settings = settings.copy(gameMode = "classic")
        }
        // refresh daily power-ups
        val t = todayStr()
        if (settings.lastPowerupRefresh != t) {
            settings = settings.copy(
                powerupReveal = min(5, settings.powerupReveal + 2),
                powerupShuffle = min(3, settings.powerupShuffle + 1),
                powerupFreeze = min(3, settings.powerupFreeze + 1),
                lastPowerupRefresh = t,
            )
        }
        loaded = true
    }
    fun persist() {
        scope.launch {
            try { MemorySettingsJsonPreference.putAsync(context, json.encodeToString(settings)) } catch (_: Exception) {}
        }
    }

    val cards = remember { mutableStateListOf<MCard>() }
    var matched by remember { mutableStateOf(0) }
    var moves by remember { mutableStateOf(0) }
    var mismatches by remember { mutableStateOf(0) }
    var lock by remember { mutableStateOf(false) }
    var streak by remember { mutableStateOf(0) }
    var bestStreak by remember { mutableStateOf(0) }
    var consecutiveMisses by remember { mutableStateOf(0) }
    var hintIdx by remember { mutableStateOf<Int?>(null) }
    val sel = remember { mutableStateListOf<Int>() }
    var time by remember { mutableStateOf(0) }
    var timeRemain by remember { mutableStateOf(60) }
    var frozen by remember { mutableStateOf(false) }
    var frozenTimer by remember { mutableStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    var winShown by remember { mutableStateOf(false) }
    var newUnlock by remember { mutableStateOf<String?>(null) }

    fun setupBoard() {
        cards.clear()
        var r = settings.rows; var c = settings.cols
        if ((r * c) % 2 != 0) c += 1
        val total = r * c
        val pairs = total / 2
        val sym = (memThemes[settings.theme] ?: memThemes["animals"]!!).take(pairs)
        var uid = 1
        val list = mutableListOf<MCard>()
        sym.forEachIndexed { i, s ->
            list.add(MCard(s, i, uid++))
            list.add(MCard(s, i, uid++))
        }
        list.shuffle(Random(System.nanoTime()))
        cards.addAll(list)
        matched = 0; moves = 0; mismatches = 0; lock = false
        streak = 0; bestStreak = 0; consecutiveMisses = 0; hintIdx = null
        sel.clear()
        time = 0; timeRemain = settings.timedSec
        frozen = false; frozenTimer = 0
        winShown = false; newUnlock = null
    }

    LaunchedEffect(loaded, settings.rows, settings.cols, settings.theme, settings.gameMode) {
        if (loaded) setupBoard()
    }
    LaunchedEffect(loaded, paused, winShown) {
        if (!loaded || paused || winShown) return@LaunchedEffect
        while (!winShown) {
            delay(1000)
            if (paused || winShown) continue
            if (frozen) { frozenTimer -= 1; if (frozenTimer <= 0) frozen = false; continue }
            time += 1
            if (settings.timed || settings.gameMode == "timed") {
                timeRemain -= 1
                if (timeRemain <= 0) { winShown = true; onScore(max(0, matched * 60 - mismatches * 10)); onGameOver(); return@LaunchedEffect }
            }
            val score = max(0, matched * 60 + (if (settings.timed) timeRemain * 4 else max(0, 300 - time * 2)) - mismatches * 10 + streak * 8)
            onScore(score)
        }
    }

    fun showHint() {
        val groups = mutableMapOf<Int, MutableList<Int>>()
        cards.forEachIndexed { i, c -> if (!c.found) groups.getOrPut(c.pid) { mutableListOf() }.add(i) }
        val candidates = groups.values.filter { it.size == 2 }
        if (candidates.isEmpty()) return
        val pick = candidates.random()
        hintIdx = pick[0]
        scope.launch { delay(1200); hintIdx = null }
    }
    fun autoMatchHint() {
        val groups = mutableMapOf<Int, MutableList<Int>>()
        cards.forEachIndexed { i, c -> if (!c.found) groups.getOrPut(c.pid) { mutableListOf() }.add(i) }
        val candidates = groups.values.filter { it.size == 2 }
        if (candidates.isEmpty()) return
        val pair = candidates[0]
        scope.launch {
            delay(700)
            cards[pair[0]] = cards[pair[0]].copy(flipped = true)
            cards[pair[1]] = cards[pair[1]].copy(flipped = true)
            delay(700)
            cards[pair[0]] = cards[pair[0]].copy(found = true)
            cards[pair[1]] = cards[pair[1]].copy(found = true)
            matched += 2
        }
    }
    fun addPair() {
        val theme = memThemes[settings.theme] ?: return
        val usedPids = cards.map { it.pid }.toSet()
        val nextPid = theme.indices.firstOrNull { it !in usedPids } ?: return
        val sym = theme[nextPid]
        val uid = (cards.maxOfOrNull { it.uid } ?: 0) + 1
        cards.add(MCard(sym, nextPid, uid))
        cards.add(MCard(sym, nextPid, uid + 1))
    }
    fun usePower(kind: String) {
        when (kind) {
            "reveal" -> {
                if (settings.powerupReveal <= 0) return
                showHint()
                val groups = mutableMapOf<Int, MutableList<Int>>()
                cards.forEachIndexed { i, c -> if (!c.found) groups.getOrPut(c.pid) { mutableListOf() }.add(i) }
                val candidates = groups.values.filter { it.size == 2 }
                if (candidates.isNotEmpty()) {
                    val pair = candidates.random()
                    cards[pair[0]] = cards[pair[0]].copy(flipped = true)
                    cards[pair[1]] = cards[pair[1]].copy(flipped = true)
                    scope.launch {
                        delay(900)
                        if (!cards[pair[0]].found) cards[pair[0]] = cards[pair[0]].copy(flipped = false)
                        if (!cards[pair[1]].found) cards[pair[1]] = cards[pair[1]].copy(flipped = false)
                    }
                }
                settings = settings.copy(powerupReveal = settings.powerupReveal - 1); persist()
            }
            "shuffle" -> {
                if (settings.powerupShuffle <= 0) return
                val open = cards.indices.filter { !cards[it].found }
                val shuffled = open.shuffled(Random(System.nanoTime()))
                val originals = open.map { cards[it] }
                shuffled.forEachIndexed { idx, dst ->
                    cards[dst] = originals[idx]
                }
                settings = settings.copy(powerupShuffle = settings.powerupShuffle - 1); persist()
            }
            "freeze" -> {
                if (settings.powerupFreeze <= 0) return
                frozen = true; frozenTimer = 5
                settings = settings.copy(powerupFreeze = settings.powerupFreeze - 1); persist()
            }
        }
    }
    fun flip(i: Int) {
        if (paused || lock || frozen) return
        val card = cards.getOrNull(i) ?: return
        if (card.found || card.flipped) return
        cards[i] = card.copy(flipped = true, revisits = card.revisits + 1)
        sel.add(i)
        if (sel.size == 2) {
            moves += 1
            lock = true
            val a = sel[0]; val b = sel[1]
            scope.launch {
                val A = cards[a]; val B = cards[b]
                if (A.pid == B.pid) {
                    delay(350)
                    cards[a] = A.copy(found = true); cards[b] = B.copy(found = true)
                    matched += 2
                    streak += 1; bestStreak = max(bestStreak, streak)
                    consecutiveMisses = 0
                    if (settings.timed || settings.gameMode == "timed") timeRemain += 5
                    sel.clear(); lock = false
                    if (matched == cards.size) {
                        // unlocks
                        if (settings.rows * settings.cols >= 16 && "back-cyber" !in settings.unlocks) {
                            val nu = settings.unlocks + "back-cyber"; settings = settings.copy(unlocks = nu); newUnlock = "Cyber back"; persist()
                        }
                        if (mismatches == 0 && "back-forest" !in settings.unlocks) {
                            val nu = settings.unlocks + "back-forest"; settings = settings.copy(unlocks = nu); newUnlock = "Forest back"; persist()
                        }
                        if (settings.gameMode == "memorylane") {
                            settings = settings.copy(rows = min(6, settings.rows + 1)); persist()
                        }
                        winShown = true
                        val final = max(0, matched * 60 + (if (settings.timed) timeRemain * 4 else max(0, 300 - time * 2)) - mismatches * 10 + streak * 8)
                        onScore(final)
                        delay(800); onGameOver()
                    }
                } else {
                    mismatches += 1; consecutiveMisses += 1; streak = 0
                    if (settings.adaptive && consecutiveMisses >= 3) { showHint(); consecutiveMisses = 0 }
                    if (settings.gameMode == "mismatch") addPair()
                    if (settings.assistAutoMatch && consecutiveMisses >= 2) autoMatchHint()
                    val delayMs = if (settings.assistLongerReveal) 1600L else settings.mismatchMs.toLong()
                    delay(delayMs)
                    if (!cards[a].found) cards[a] = cards[a].copy(flipped = false)
                    if (!cards[b].found) cards[b] = cards[b].copy(flipped = false)
                    sel.clear(); lock = false
                }
            }
        }
    }

    val cardSize = if (settings.assistLargeTouch) 80.dp else 68.dp
    val r = settings.rows; var c = settings.cols
    if ((r * c) % 2 != 0) c += 1

    Column(
        modifier = Modifier.fillMaxSize().padding(if (settings.oneHanded) PaddingValues(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 70.dp) else PaddingValues(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pairs ${matched / 2}/${cards.size / 2}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Moves $moves", color = Color.White.copy(0.85f), fontSize = 12.sp)
                if (settings.timed || settings.gameMode == "timed") Text("${timeRemain}s", color = Color(0xFFFDE68A), fontSize = 12.sp)
                else Text("${time}s", color = Color.White.copy(0.6f), fontSize = 12.sp)
                if (streak > 0) Text("×$streak", color = Color(0xFFFACC15), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                AssistChip(onClick = { usePower("reveal") }, enabled = settings.powerupReveal > 0, label = { Text("👁 ${settings.powerupReveal}", fontSize = 10.sp) })
                AssistChip(onClick = { usePower("shuffle") }, enabled = settings.powerupShuffle > 0, label = { Text("🔀 ${settings.powerupShuffle}", fontSize = 10.sp) })
                AssistChip(onClick = { usePower("freeze") }, enabled = settings.powerupFreeze > 0 && (settings.timed || settings.gameMode == "timed"), label = { Text("❄ ${settings.powerupFreeze}", fontSize = 10.sp) })
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).clickable { settingsOpen = true }, contentAlignment = Alignment.Center) {
                    Text("⚙", color = Color.White, fontSize = 14.sp)
                }
            }
        }
        if (settings.timed || settings.gameMode == "timed") {
            LinearProgressIndicator(
                progress = { timeRemain.toFloat() / settings.timedSec },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                color = if (timeRemain > 20) accent else Color(0xFFEF4444),
            )
        }
        Spacer(Modifier.height(4.dp))
        // board
        for (rIdx in 0 until r) {
            Row(horizontalArrangement = Arrangement.Center) {
                for (cIdx in 0 until c) {
                    val idx = rIdx * c + cIdx
                    if (idx >= cards.size) {
                        Spacer(Modifier.padding(3.dp).size(cardSize))
                    } else {
                        val card = cards[idx]
                        val isOpen = card.flipped || card.found
                        val bg = when {
                            card.found -> accent.copy(alpha = 0.45f)
                            isOpen -> Color.White.copy(alpha = 0.22f)
                            hintIdx == idx -> Color(0xFFFACC15).copy(alpha = 0.4f)
                            else -> Color.White.copy(alpha = 0.10f)
                        }
                        Box(
                            modifier = Modifier
                                .padding(3.dp).size(cardSize)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .clickable(enabled = !isOpen && !lock && !frozen) { flip(idx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isOpen) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (settings.colorblindShapes) {
                                        Text(shapeMap[card.pid % shapeMap.size], color = Color.White.copy(0.65f), fontSize = 10.sp)
                                    }
                                    Text(card.v, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (settings.highContrast) {
                                Text("·", color = Color.White, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (settings.gameMode) {
                "zen" -> "Zen mode"
                "memorylane" -> "Memory Lane — grid grows"
                "mismatch" -> "Mismatch Penalty — misses add cards"
                "timed" -> "Timed — beat the clock"
                else -> "Match every pair"
            },
            color = Color.White.copy(0.55f), fontSize = 11.sp,
        )
        newUnlock?.let { Text("🎉 Unlocked: $it", color = Color(0xFFFACC15), fontSize = 11.sp) }
    }

    if (settingsOpen) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { settingsOpen = false }, contentAlignment = Alignment.Center) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0E1A)),
                modifier = Modifier.padding(16.dp).widthIn(max = 420.dp).heightIn(max = 600.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text("Recall · Twin Echo · Settings", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Mode", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        for (m in listOf("classic","timed","memorylane","mismatch","zen")) {
                            AssistChip(onClick = { settings = settings.copy(gameMode = m, timed = m == "timed"); persist(); setupBoard() },
                                label = { Text(m, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = if (settings.gameMode == m) accent.copy(0.4f) else Color(0xFF1C2030)))
                        }
                    }
                    Text("Theme", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        for (t in listOf("animals","space","food","geometry","fantasy","nostalgia")) {
                            AssistChip(onClick = { settings = settings.copy(theme = t); persist(); setupBoard() },
                                label = { Text(t, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = if (settings.theme == t) accent.copy(0.4f) else Color(0xFF1C2030)))
                        }
                    }
                    Text("Grid", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        for ((rr, cc) in listOf(2 to 2, 2 to 3, 4 to 3, 4 to 4, 4 to 5, 4 to 6, 6 to 6)) {
                            AssistChip(onClick = { settings = settings.copy(rows = rr, cols = cc); persist(); setupBoard() },
                                label = { Text("${rr}×${cc}", fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = if (settings.rows == rr && settings.cols == cc) accent.copy(0.4f) else Color(0xFF1C2030)))
                        }
                    }
                    Row { Checkbox(checked = settings.haptics, onCheckedChange = { settings = settings.copy(haptics = it); persist() }); Text("Haptics", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.audio, onCheckedChange = { settings = settings.copy(audio = it); persist() }); Text("Audio cues", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.adaptive, onCheckedChange = { settings = settings.copy(adaptive = it); persist() }); Text("Adaptive hints", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.flip3d, onCheckedChange = { settings = settings.copy(flip3d = it); persist() }); Text("3D flip animation", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.reducedMotion, onCheckedChange = { settings = settings.copy(reducedMotion = it); persist() }); Text("Reduced motion", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.highContrast, onCheckedChange = { settings = settings.copy(highContrast = it); persist() }); Text("High contrast borders", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.colorblindShapes, onCheckedChange = { settings = settings.copy(colorblindShapes = it); persist() }); Text("Colourblind shape tags", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.oneHanded, onCheckedChange = { settings = settings.copy(oneHanded = it); persist() }); Text("One-handed", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.batterySaver, onCheckedChange = { settings = settings.copy(batterySaver = it); persist() }); Text("Battery saver", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.assistAutoMatch, onCheckedChange = { settings = settings.copy(assistAutoMatch = it); persist() }); Text("Assist: auto-match after 2 misses", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.assistLongerReveal, onCheckedChange = { settings = settings.copy(assistLongerReveal = it); persist() }); Text("Assist: longer reveal (1.6s)", color = Color.White, fontSize = 12.sp) }
                    Row { Checkbox(checked = settings.assistLargeTouch, onCheckedChange = { settings = settings.copy(assistLargeTouch = it); persist() }); Text("Assist: large touch zones", color = Color.White, fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { settingsOpen = false; setupBoard() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("Apply & restart") }
                }
            }
        }
    }
}
