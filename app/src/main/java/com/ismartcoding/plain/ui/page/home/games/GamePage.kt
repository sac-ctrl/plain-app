package com.ismartcoding.plain.ui.page.home.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.ismartcoding.plain.preferences.GamesStateJsonPreference
import com.ismartcoding.plain.ui.page.home.games.impl.*
import kotlinx.coroutines.launch

enum class GamePhase { Start, Play, Result }

@Composable
fun GamePage(navController: NavHostController, gameId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meta = GamesRegistry.byId(gameId) ?: run {
        navController.navigateUp(); return
    }

    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!loaded) {
            GamesStore.loadFromJson(GamesStateJsonPreference.getAsync(context))
            loaded = true
        }
    }

    var phase by remember { mutableStateOf(GamePhase.Start) }
    var paused by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var lastScore by remember { mutableStateOf(0) }
    var lastWasBest by remember { mutableStateOf(false) }
    var lastCoins by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(meta.modes.first()) }
    var difficulty by remember { mutableStateOf("Medium") }
    var runId by remember { mutableStateOf(0) }

    BackHandler(enabled = phase == GamePhase.Play) {
        if (!paused) paused = true else { phase = GamePhase.Start; paused = false }
    }

    fun onScore(s: Int) { score = s }
    fun onGameOver() {
        val r = GamesStore.record(meta.id)
        val prev = r.best
        val coinsBefore = GamesStore.getCoins()
        GamesStore.finishRun(meta.id, score)
        val coinsAfter = GamesStore.getCoins()
        lastScore = score
        lastWasBest = score > prev
        lastCoins = (coinsAfter - coinsBefore).coerceAtLeast(0)
        scope.launch { GamesStore.persist(context) }
        phase = GamePhase.Result
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(meta.color, Color.Black))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameTopBar(
                meta = meta, score = score,
                best = GamesStore.record(meta.id).best,
                paused = paused,
                canPause = phase == GamePhase.Play,
                onTogglePause = { if (phase == GamePhase.Play) paused = !paused },
                onRestart = {
                    score = 0; paused = false; runId += 1
                    phase = GamePhase.Play
                },
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (phase) {
                    GamePhase.Start -> StartCard(meta, mode, { mode = it }, difficulty, { difficulty = it }) {
                        score = 0; paused = false; runId += 1; phase = GamePhase.Play
                    }
                    GamePhase.Play -> {
                        key(runId) {
                            GameRouter(meta.id, difficulty, mode, paused, ::onScore, ::onGameOver, meta.accent)
                        }
                        if (paused) PauseOverlay { paused = false }
                    }
                    GamePhase.Result -> ResultCard(meta, lastScore, lastWasBest, lastCoins,
                        onRetry = { score = 0; paused = false; runId += 1; phase = GamePhase.Play },
                        onExit = { navController.navigateUp() }
                    )
                }
            }
        }
    }
}

@Composable
fun GameRouter(
    id: String, difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    when (id) {
        "snake" -> SnakeGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "flappy" -> FlappyGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "brick" -> BrickGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "space" -> SpaceGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "car" -> CarGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "color" -> ColorGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "react" -> ReactGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "g2048" -> Game2048(difficulty, mode, paused, onScore, onGameOver, accent)
        "memory" -> MemoryGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "slide" -> SlideGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "dice" -> DiceGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "aim" -> AimGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "runner" -> RunnerGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "math" -> MathGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "tap" -> TapGame(difficulty, mode, paused, onScore, onGameOver, accent)
        "hollow" -> HollowGame(difficulty, mode, paused, onScore, onGameOver, accent)
    }
}
