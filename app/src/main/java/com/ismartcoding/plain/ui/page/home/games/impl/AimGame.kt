package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

private data class Target(var x: Float, var y: Float, var life: Int)

@Composable
fun AimGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val targets = remember { mutableStateListOf<Target>() }
    var score by remember { mutableStateOf(0) }
    var misses by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    val maxMisses = when (difficulty) { "Easy" -> 12; "Hard" -> 5; "Insane" -> 3; else -> 8 }
    val targetLife = when (difficulty) { "Easy" -> 110; "Hard" -> 55; "Insane" -> 35; else -> 75 }
    val timeLimit = if (mode.equals("Time", true)) 30_000L else 0L
    val startT = remember { System.currentTimeMillis() }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            for (t in targets) t.life -= 1
            val expired = targets.filter { it.life <= 0 }
            if (expired.isNotEmpty()) {
                misses += expired.size
                targets.removeAll(expired)
                if (misses >= maxMisses) { alive = false; onGameOver(); break }
            }
            if (targets.size < 3 && Math.random() < 0.05) {
                targets.add(Target(40f + Math.random().toFloat() * (w - 80),
                    100f + Math.random().toFloat() * (h - 200), targetLife))
            }
            if (timeLimit > 0 && System.currentTimeMillis() - startT >= timeLimit) {
                alive = false; onGameOver(); break
            }
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures { p ->
                if (paused || !alive) return@detectTapGestures
                val hit = targets.firstOrNull {
                    val dx = it.x - p.x; val dy = it.y - p.y
                    dx * dx + dy * dy < 50f * 50f
                }
                if (hit != null) {
                    targets.remove(hit); score += 100; onScore(score)
                } else {
                    misses += 1
                    if (misses >= maxMisses) { alive = false; onGameOver() }
                }
            }
        }
    ) {
        w = size.width; h = size.height
        for (t in targets) {
            val r = 50f * (t.life.toFloat() / targetLife).coerceAtLeast(0.3f)
            drawCircle(accent.copy(0.85f), radius = r, center = Offset(t.x, t.y))
            drawCircle(Color.White, radius = r * 0.5f, center = Offset(t.x, t.y))
            drawCircle(accent, radius = r * 0.25f, center = Offset(t.x, t.y))
        }
    }
}
