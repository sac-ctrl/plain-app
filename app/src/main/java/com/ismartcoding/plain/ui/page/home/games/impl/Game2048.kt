package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ismartcoding.plain.preferences.Game2048SettingsJsonPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

@Serializable
private data class Game2048Settings(
    var theme: String = "neon",
    var skin: String = "classic",
    var size: Int = 4,
    var gameMode: String = "classic", // classic|timed|endless|challenge|daily
    var haptics: Boolean = true,
    var sound: Boolean = true,
    var voice: Boolean = false,
    var dynamicMusic: Boolean = true,
    var swipeThreshold: Int = 28,
    var bouncy: Boolean = true,
    var arrows: Boolean = false,
    var tilt: Boolean = false,
    var reducedMotion: Boolean = false,
    var highContrast: Boolean = false,
    var oneHanded: Boolean = false,
    var batterySaver: Boolean = false,
    var colorblind: String = "off",
    var fontScale: Float = 1f,
    var assistHints: Boolean = false,
    var assistAutoUndo: Boolean = false,
    var assistSlow: Boolean = false,
    var previewNext: Boolean = false,
    var totalMerges: Int = 0,
    var totalMoves: Int = 0,
    var totalScore: Int = 0,
    var bestTile: Int = 0,
    var best: Int = 0,
    var unlockedSkins: List<String> = listOf("classic"),
    var unlockedGrids: List<Int> = listOf(4),
    var puShuffle: Int = 1,
    var puClear: Int = 1,
    var puUndoExtra: Int = 0,
    var lastRefresh: String = "",
    var losses: Int = 0,
    var wins: Int = 0,
    var challengeLvl: Int = 0,
)

private data class Tile2(
    val id: Int,
    var r: Int,
    var c: Int,
    var v: Int,
    var justMerged: Boolean = false,
    var justSpawned: Boolean = true,
)

private fun today2048(): String {
    val d = java.util.Calendar.getInstance()
    return "${d.get(java.util.Calendar.YEAR)}-${d.get(java.util.Calendar.MONTH) + 1}-${d.get(java.util.Calendar.DAY_OF_MONTH)}"
}

private fun seedToday2048(): Int {
    val d = java.util.Calendar.getInstance()
    return d.get(java.util.Calendar.YEAR) * 10000 + (d.get(java.util.Calendar.MONTH) + 1) * 100 + d.get(java.util.Calendar.DAY_OF_MONTH)
}

private fun colorFor2(v: Int, accent: Color, skin: String): Color = when (v) {
    2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8); 8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563); 32 -> Color(0xFFF67C5F); 64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61); 512 -> Color(0xFFEDC850)
    1024 -> Color(0xFF2563EB); 2048 -> Color(0xFF1D4ED8); 4096 -> Color(0xFF7C3AED)
    8192 -> Color(0xFFC026D3); else -> if (v >= 16384) Color(0xFFE91E63) else accent
}

@Composable
fun Game2048(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; isLenient = true } }
    var settings by remember { mutableStateOf(Game2048Settings()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val raw = Game2048SettingsJsonPreference.getAsync(context)
            if (raw.isNotBlank() && raw != "{}") settings = json.decodeFromString(raw)
        } catch (_: Exception) {}
        // bridge passed mode if provided
        if (mode.isNotBlank() && mode.lowercase() != "classic") {
            val m = when (mode.lowercase()) { "time", "timed" -> "timed"; "endless", "survival" -> "endless"; "challenge" -> "challenge"; "daily" -> "daily"; else -> "classic" }
            settings = settings.copy(gameMode = m)
        }
        if (settings.lastRefresh != today2048()) {
            settings = settings.copy(
                puShuffle = min(3, settings.puShuffle + 1),
                puClear = min(3, settings.puClear + 1),
                puUndoExtra = min(5, settings.puUndoExtra + 3),
                lastRefresh = today2048(),
            )
        }
        loaded = true
    }
    fun save(s: Game2048Settings) {
        settings = s
        scope.launch {
            try { Game2048SettingsJsonPreference.putAsync(context, json.encodeToString(s)) } catch (_: Exception) {}
        }
    }

    if (!loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading…", color = Color.White) }
        return
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    fun buzz() { if (settings.haptics) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) }

    var sizeN by remember { mutableStateOf(if (settings.gameMode == "challenge") 4 else settings.size) }
    val tiles = remember { mutableStateListOf<Tile2>() }
    var tileIdSeq by remember { mutableStateOf(1) }
    var score by remember { mutableStateOf(0) }
    var bestTile by remember { mutableStateOf(0) }
    var moves by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var won by remember { mutableStateOf(false) }
    var contunue by remember { mutableStateOf(false) }
    var undosLeft by remember { mutableStateOf(5 + settings.puUndoExtra) }
    var settingsOpen by remember { mutableStateOf(false) }
    var analyticsOpen by remember { mutableStateOf(false) }
    var timeRemain by remember { mutableStateOf(60) }
    val heat = remember { mutableStateListOf<Int>() }
    val peaks = remember { mutableStateListOf<Int>() }
    val historyTiles = remember { mutableStateListOf<List<Tile2>>() }
    val historyScores = remember { mutableStateListOf<Int>() }
    val blocked = remember { mutableStateListOf<Pair<Int, Int>>() }
    var rng by remember { mutableStateOf(Random(System.nanoTime())) }

    val challengeBoards = remember {
        listOf(
            Triple(4, listOf(Triple(0, 0, 2), Triple(0, 3, 2), Triple(3, 0, 4), Triple(3, 3, 4)), listOf(1 to 1, 2 to 2)),
            Triple(4, listOf(Triple(0, 0, 8), Triple(0, 1, 4), Triple(0, 2, 2)), listOf(3 to 0, 3 to 3)),
            Triple(5, listOf(Triple(2, 2, 16), Triple(0, 0, 4), Triple(0, 4, 4), Triple(4, 0, 4), Triple(4, 4, 4)), emptyList<Pair<Int, Int>>()),
        )
    }

    fun emptyCells(): List<Pair<Int, Int>> {
        val occ = tiles.map { it.r to it.c }.toSet()
        val bl = blocked.toSet()
        val out = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until sizeN) for (c in 0 until sizeN) {
            val k = r to c
            if (k !in occ && k !in bl) out.add(k)
        }
        return out
    }
    fun spawn(forceVal: Int? = null) {
        val empty = emptyCells()
        if (empty.isEmpty()) return
        val cell = empty[rng.nextInt(empty.size)]
        val four = if (settings.losses >= 3) 0.05 else if (settings.wins >= 3) 0.2 else 0.1
        val v = forceVal ?: if (rng.nextDouble() < (1.0 - four)) 2 else 4
        tiles.add(Tile2(tileIdSeq++, cell.first, cell.second, v, justSpawned = true))
    }

    fun reset() {
        tiles.clear(); historyTiles.clear(); historyScores.clear(); blocked.clear()
        peaks.clear()
        score = 0; bestTile = 0; moves = 0; alive = true; won = false; contunue = false
        undosLeft = 5 + settings.puUndoExtra
        if (settings.gameMode == "daily") rng = Random(seedToday2048().toLong())
        else rng = Random(System.nanoTime())
        if (settings.gameMode == "challenge") {
            val b = challengeBoards[settings.challengeLvl % challengeBoards.size]
            sizeN = b.first
            for (f in b.second) tiles.add(Tile2(tileIdSeq++, f.first, f.second, f.third, justSpawned = true))
            for (bl in b.third) blocked.add(bl)
        } else {
            sizeN = settings.size
            spawn(); spawn()
        }
        heat.clear(); for (i in 0 until sizeN * sizeN) heat.add(0)
        if (settings.gameMode == "timed") timeRemain = 60
        onScore(0)
    }

    LaunchedEffect(loaded, settings.gameMode, settings.size, settings.challengeLvl) { reset() }

    LaunchedEffect(settings.gameMode, paused) {
        if (settings.gameMode != "timed") return@LaunchedEffect
        while (timeRemain > 0 && alive) {
            delay(1000)
            if (!paused) timeRemain -= 1
        }
        if (timeRemain <= 0 && alive) {
            alive = false
            if (score > settings.best) settings = settings.copy(best = score)
            save(settings.copy(losses = settings.losses + 1, totalScore = settings.totalScore + score))
            analyticsOpen = true
            onGameOver()
        }
    }

    fun isStuck(): Boolean {
        if (emptyCells().isNotEmpty()) return false
        val grid = Array(sizeN) { Array<Tile2?>(sizeN) { null } }
        for (t in tiles) grid[t.r][t.c] = t
        for (r in 0 until sizeN) for (c in 0 until sizeN) {
            val t = grid[r][c] ?: continue
            if (c + 1 < sizeN && grid[r][c + 1]?.v == t.v) return false
            if (r + 1 < sizeN && grid[r + 1][c]?.v == t.v) return false
        }
        return true
    }

    fun snapshot() {
        historyTiles.add(tiles.map { it.copy() })
        historyScores.add(score)
        if (historyTiles.size > 30) { historyTiles.removeAt(0); historyScores.removeAt(0) }
    }
    fun undo(silent: Boolean = false) {
        if (historyTiles.isEmpty() || undosLeft <= 0) return
        tiles.clear(); tiles.addAll(historyTiles.removeAt(historyTiles.size - 1))
        score = historyScores.removeAt(historyScores.size - 1)
        if (!silent) undosLeft -= 1
        onScore(score)
        buzz()
    }

    fun endGame() {
        alive = false
        if (score > settings.best) settings = settings.copy(best = score)
        val ns = settings.copy(
            losses = settings.losses + (if (won) 0 else 1),
            wins = settings.wins + (if (won) 1 else 0),
            totalScore = settings.totalScore + score,
            totalMoves = settings.totalMoves + moves,
            totalMerges = settings.totalMerges,
            bestTile = max(settings.bestTile, bestTile),
        )
        save(ns)
        analyticsOpen = true
        onGameOver()
    }

    fun move(dir: Int) {
        if (!alive || (won && !contunue) || paused) return
        snapshot()
        val dx = if (dir == 0) -1 else if (dir == 1) 1 else 0
        val dy = if (dir == 2) -1 else if (dir == 3) 1 else 0
        val grid = Array(sizeN) { Array<Tile2?>(sizeN) { null } }
        for (t in tiles) grid[t.r][t.c] = t
        val order = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until sizeN) for (c in 0 until sizeN) order.add(r to c)
        if (dir == 1) order.sortByDescending { it.second }
        if (dir == 3) order.sortByDescending { it.first }
        var moved = false
        var gained = 0
        var mergeCount = 0
        val dead = mutableListOf<Int>()
        val mergedFlag = HashSet<Int>()
        for ((r, c) in order) {
            val t = grid[r][c] ?: continue
            var nr = r; var nc = c
            while (true) {
                val tr = nr + dy; val tc = nc + dx
                if (tr < 0 || tr >= sizeN || tc < 0 || tc >= sizeN) break
                if ((tr to tc) in blocked) break
                val occ = grid[tr][tc]
                if (occ == null) { grid[tr][tc] = t; grid[nr][nc] = null; nr = tr; nc = tc; continue }
                if (occ.v == t.v && occ.id !in mergedFlag) {
                    occ.v *= 2; mergedFlag.add(occ.id); occ.justMerged = true
                    gained += occ.v; mergeCount += 1
                    bestTile = max(bestTile, occ.v)
                    if (occ.v >= 2048 && !won) { won = true }
                    dead.add(t.id)
                    grid[nr][nc] = null
                    nr = tr; nc = tc
                    break
                }
                break
            }
            if (nr != r || nc != c) { moved = true; t.r = nr; t.c = nc }
        }
        if (!moved) { historyTiles.removeAt(historyTiles.size - 1); historyScores.removeAt(historyScores.size - 1); buzz(); return }
        tiles.removeAll { it.id in dead }
        for (t in tiles) t.justSpawned = false
        score += gained
        if (gained > 0) onScore(score); buzz()
        moves += 1
        for (t in tiles) {
            val idx = t.r * sizeN + t.c
            if (idx in heat.indices) heat[idx] = heat[idx] + 1
        }
        peaks.add(bestTile)
        // track stats
        if (mergeCount > 0) settings = settings.copy(totalMerges = settings.totalMerges + mergeCount)
        // spawn
        if (settings.gameMode != "challenge") spawn()
        else if (rng.nextDouble() < 0.4) spawn()
        // unlock grids/skins
        var s = settings
        if (bestTile >= 1024 && !s.unlockedGrids.contains(5)) s = s.copy(unlockedGrids = s.unlockedGrids + 5)
        if (bestTile >= 2048 && !s.unlockedGrids.contains(6)) s = s.copy(unlockedGrids = s.unlockedGrids + 6)
        if (mergeCount >= 3 && !s.unlockedSkins.contains("digital")) s = s.copy(unlockedSkins = s.unlockedSkins + "digital")
        if (bestTile >= 512 && !s.unlockedSkins.contains("neon")) s = s.copy(unlockedSkins = s.unlockedSkins + "neon")
        if (bestTile >= 1024 && !s.unlockedSkins.contains("marble")) s = s.copy(unlockedSkins = s.unlockedSkins + "marble")
        if (bestTile >= 2048 && !s.unlockedSkins.contains("crystal")) s = s.copy(unlockedSkins = s.unlockedSkins + "crystal")
        if (s.totalMerges >= 100 && !s.unlockedSkins.contains("wood")) s = s.copy(unlockedSkins = s.unlockedSkins + "wood")
        if (s != settings) settings = s
        // endless: expand
        if (settings.gameMode == "endless" && bestTile >= 2048 && sizeN < 6) {
            sizeN += 1
            while (heat.size < sizeN * sizeN) heat.add(0)
            spawn()
        }
        // game-over check
        if (isStuck()) {
            if (settings.assistAutoUndo && historyTiles.isNotEmpty()) { undo(silent = true); return }
            endGame()
        }
        save(settings)
    }

    fun usePower(kind: String) {
        if (kind == "shuffle" && settings.puShuffle > 0) {
            val all = (0 until sizeN).flatMap { r -> (0 until sizeN).map { c -> r to c } }.toMutableList()
            all.shuffle(Random(System.nanoTime()))
            tiles.forEachIndexed { i, t -> if (i < all.size) { t.r = all[i].first; t.c = all[i].second } }
            settings = settings.copy(puShuffle = settings.puShuffle - 1); save(settings); buzz()
        } else if (kind == "clear" && settings.puClear > 0) {
            if (tiles.isEmpty()) return
            val lo = tiles.minByOrNull { it.v } ?: return
            tiles.removeAll { it.id == lo.id }
            settings = settings.copy(puClear = settings.puClear - 1); save(settings); buzz()
        }
    }

    var boardW by remember { mutableStateOf(1f) }
    val cellPad = 6.dp

    Column(
        modifier = Modifier.fillMaxSize().background(
            when (settings.theme) { "paper" -> Color(0xFFF5F1E8); "dark" -> Color.Black; "glass" -> Color(0xFF1A1A2E); else -> Color(0xFF1A0F2E) }
        ).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // HUD
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Chip2("SCORE", "$score")
                Chip2("BEST", "${settings.best}")
                Chip2("MOVES", "$moves")
                if (settings.gameMode == "timed") Chip2("TIME", "${max(0, timeRemain)}s")
                if (settings.gameMode == "challenge") Chip2("LVL", "${settings.challengeLvl + 1}/${challengeBoards.size}")
            }
            Row {
                IconBtn("↶$undosLeft", undosLeft > 0 && historyTiles.isNotEmpty()) { undo() }
                IconBtn("🔀${settings.puShuffle}", settings.puShuffle > 0) { usePower("shuffle") }
                IconBtn("✂${settings.puClear}", settings.puClear > 0) { usePower("clear") }
                IconBtn("⚙", true) { settingsOpen = true }
            }
        }
        // Board
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(0.95f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(0.3f)).pointerInput(sizeN, settings.swipeThreshold) {
            var dx = 0f; var dy = 0f
            detectDragGestures(
                onDragStart = { dx = 0f; dy = 0f },
                onDragEnd = {
                    val th = settings.swipeThreshold.toFloat()
                    if (abs(dx) < th && abs(dy) < th) { dx = 0f; dy = 0f; return@detectDragGestures }
                    val dir = if (abs(dx) > abs(dy)) (if (dx > 0) 1 else 0) else (if (dy > 0) 3 else 2)
                    move(dir)
                    dx = 0f; dy = 0f
                },
            ) { _, drag -> dx += drag.x; dy += drag.y }
        }) {
            val density = LocalDensity.current
            val sidePx = with(density) { maxWidth.toPx() }
            val cellPx = sidePx / sizeN
            val cellDp = with(density) { cellPx.toDp() } - 6.dp
            // Cells background
            Column(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                for (r in 0 until sizeN) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until sizeN) {
                            Box(modifier = Modifier.weight(1f).padding(3.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (Pair(r, c) in blocked) Color(0xFF333333) else Color.White.copy(0.04f)))
                        }
                    }
                }
            }
            // Tiles
            for (t in tiles) {
                key(t.id) {
                    val animX by animateFloatAsState(
                        targetValue = t.c * cellPx,
                        animationSpec = tween(if (settings.assistSlow) 220 else if (settings.reducedMotion) 60 else 130),
                        label = "x"
                    )
                    val animY by animateFloatAsState(
                        targetValue = t.r * cellPx,
                        animationSpec = tween(if (settings.assistSlow) 220 else if (settings.reducedMotion) 60 else 130),
                        label = "y"
                    )
                    val pop by animateFloatAsState(
                        targetValue = if (t.justMerged) 1.15f else if (t.justSpawned) 0.5f else 1f,
                        animationSpec = tween(180),
                        label = "pop"
                    )
                    LaunchedEffect(t.justMerged, t.justSpawned) {
                        if (t.justMerged || t.justSpawned) { delay(220); t.justMerged = false; t.justSpawned = false }
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = with(density) { animX.toDp() } + 3.dp, y = with(density) { animY.toDp() } + 3.dp)
                            .size(cellDp)
                            .scale(pop)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colorFor2(t.v, accent, settings.skin))
                            .let { m -> if (settings.highContrast) m.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else m },
                        contentAlignment = Alignment.Center,
                    ) {
                        val baseSize = if (t.v < 100) 22 else if (t.v < 1000) 18 else 14
                        val sp = (baseSize * settings.fontScale).sp
                        Text(
                            "${t.v}",
                            color = if (t.v <= 4) Color(0xFF776E65) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = sp,
                            fontFamily = if (settings.skin == "digital") androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                        )
                    }
                }
            }
            if (!alive) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Game over", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Score $score · Best tile $bestTile", color = Color.White.copy(0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { reset() }) { Text("New game") }
                    }
                }
            }
            if (won && !contunue) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("You hit 2048!", color = Color(0xFFFCD34D), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row { Button(onClick = { contunue = true }) { Text("Continue") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { reset() }) { Text("New game") } }
                    }
                }
            }
        }
        // On-screen arrows
        if (settings.arrows) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { move(2) }, modifier = Modifier.width(70.dp).height(38.dp)) { Text("▲") }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { move(0) }, modifier = Modifier.width(70.dp).height(38.dp)) { Text("◀") }
                    Button(onClick = { move(3) }, modifier = Modifier.width(70.dp).height(38.dp)) { Text("▼") }
                    Button(onClick = { move(1) }, modifier = Modifier.width(70.dp).height(38.dp)) { Text("▶") }
                }
            }
        }
        Text("Mode: ${settings.gameMode} · Grid: ${sizeN}×${sizeN} · Daily seed #${seedToday2048()}", color = Color.White.copy(0.6f), fontSize = 11.sp)
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            confirmButton = { Button(onClick = { settingsOpen = false }) { Text("Done") } },
            title = { Text("2048 · Settings") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp)) {
                    SettingHeader2("Mode")
                    ChipsRow2(listOf("classic","timed","endless","challenge","daily"), settings.gameMode) { save(settings.copy(gameMode = it)) }
                    SettingHeader2("Grid")
                    ChipsRow2(listOf(4,5,6).map { it.toString() }, sizeN.toString()) {
                        val n = it.toInt()
                        if (settings.unlockedGrids.contains(n)) { save(settings.copy(size = n)); sizeN = n; reset() }
                    }
                    SettingHeader2("Theme")
                    ChipsRow2(listOf("neon","paper","glass","dark"), settings.theme) { save(settings.copy(theme = it)) }
                    SettingHeader2("Tile skin")
                    ChipsRow2(listOf("classic","digital","neon","marble","wood","crystal"), settings.skin) {
                        if (settings.unlockedSkins.contains(it)) save(settings.copy(skin = it))
                    }
                    SettingHeader2("Sound & haptics")
                    Toggle2("Haptics", settings.haptics) { save(settings.copy(haptics = it)) }
                    Toggle2("Sound", settings.sound) { save(settings.copy(sound = it)) }
                    Toggle2("Voice announcer", settings.voice) { save(settings.copy(voice = it)) }
                    Toggle2("Dynamic music", settings.dynamicMusic) { save(settings.copy(dynamicMusic = it)) }
                    SettingHeader2("Controls")
                    Slider2("Swipe distance ${settings.swipeThreshold}px", settings.swipeThreshold.toFloat(), 10f, 80f) { save(settings.copy(swipeThreshold = it.toInt())) }
                    Toggle2("Tilt to slide", settings.tilt) { save(settings.copy(tilt = it)) }
                    Toggle2("On-screen arrows", settings.arrows) { save(settings.copy(arrows = it)) }
                    Toggle2("Bouncy slide", settings.bouncy) { save(settings.copy(bouncy = it)) }
                    SettingHeader2("Accessibility")
                    Toggle2("Reduced motion", settings.reducedMotion) { save(settings.copy(reducedMotion = it)) }
                    Toggle2("High contrast", settings.highContrast) { save(settings.copy(highContrast = it)) }
                    Toggle2("One-handed", settings.oneHanded) { save(settings.copy(oneHanded = it)) }
                    Toggle2("Battery saver (30 fps)", settings.batterySaver) { save(settings.copy(batterySaver = it)) }
                    SettingHeader2("Colourblind")
                    ChipsRow2(listOf("off","protanopia","deuteranopia","tritanopia"), settings.colorblind) { save(settings.copy(colorblind = it)) }
                    Slider2("Font scale ${"%.2f".format(settings.fontScale)}", settings.fontScale, 0.85f, 1.5f) { save(settings.copy(fontScale = it)) }
                    SettingHeader2("Assists")
                    Toggle2("Show merge hints", settings.assistHints) { save(settings.copy(assistHints = it)) }
                    Toggle2("Auto-undo on losing swipe", settings.assistAutoUndo) { save(settings.copy(assistAutoUndo = it)) }
                    Toggle2("Slower animations", settings.assistSlow) { save(settings.copy(assistSlow = it)) }
                    Toggle2("Next-tile preview", settings.previewNext) { save(settings.copy(previewNext = it)) }
                    SettingHeader2("Stats")
                    Text("Total merges: ${settings.totalMerges}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Text("Most-merged tile: ${settings.bestTile}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Text("Total points: ${settings.totalScore}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Text("Total moves: ${settings.totalMoves}", color = Color.White.copy(0.85f), fontSize = 12.sp)
                }
            }
        )
    }

    if (analyticsOpen) {
        AlertDialog(
            onDismissRequest = { analyticsOpen = false; reset() },
            confirmButton = { Button(onClick = { analyticsOpen = false; reset() }) { Text("New game") } },
            title = { Text("Post-game") },
            text = {
                Column {
                    Text("Score $score · Best tile $bestTile · Moves $moves", color = Color.White.copy(0.85f))
                    Text("Efficiency: ${if (moves > 0) "%.1f".format(score.toFloat() / moves) else "0"} pts/move", color = Color.White.copy(0.85f), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Cell heatmap", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    val maxH = (heat.maxOrNull() ?: 1).coerceAtLeast(1)
                    Column {
                        for (r in 0 until sizeN) {
                            Row {
                                for (c in 0 until sizeN) {
                                    val v = heat.getOrNull(r * sizeN + c) ?: 0
                                    Box(modifier = Modifier.padding(1.dp).size(36.dp).clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFACC15).copy(alpha = 0.1f + 0.7f * v / maxH)),
                                        contentAlignment = Alignment.Center) {
                                        Text("$v", color = Color(0xFF1A1A2E), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val tip = run {
                        val corners = listOf(0, sizeN - 1, sizeN * (sizeN - 1), sizeN * sizeN - 1)
                        val cTotal = corners.sumOf { heat.getOrNull(it) ?: 0 }
                        val total = heat.sum().coerceAtLeast(1)
                        if (cTotal.toFloat() / total > 0.5f) "Great corner discipline — you locked your highest tiles in corners."
                        else "Try to chain merges in one swipe — line up equal tiles before pushing."
                    }
                    Text(tip, color = Color(0xFFFCD34D), fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun Chip2(label: String, value: String) {
    Box(modifier = Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(999.dp)).background(Color.White.copy(0.06f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White.copy(0.55f), fontSize = 9.sp); Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable
private fun IconBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val a = if (enabled) 1f else 0.4f
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(horizontal = 2.dp).height(32.dp).alpha(a), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
        Text(label, fontSize = 11.sp, color = Color.White)
    }
}
@Composable
private fun SettingHeader2(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}
@Composable
private fun ChipsRow2(opts: List<String>, current: String, onChoose: (String) -> Unit) {
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
private fun Toggle2(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
@Composable
private fun Slider2(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 11.sp)
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
