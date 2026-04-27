package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

private data class Car(var x: Float, var y: Float)

@Composable
fun CarGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    var carX by remember { mutableStateOf(0f) }
    val obstacles = remember { mutableStateListOf<Car>() }
    var score by remember { mutableStateOf(0) }
    var nearMisses by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var nitro by remember { mutableStateOf(false) }
    var spawnTimer by remember { mutableStateOf(0) }
    val baseSpeed = when (difficulty) { "Easy" -> 6f; "Hard" -> 10f; "Insane" -> 13f; else -> 8f }
    val timeLimit = if (mode.equals("Time", true)) 60_000L else 0L
    val startT = remember { System.currentTimeMillis() }

    LaunchedEffect(w, h) { if (w > 0 && carX == 0f) carX = w / 2 }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            val sp = baseSpeed * (if (nitro) 1.7f else 1f)
            for (o in obstacles) o.y += sp
            spawnTimer += 1
            val rate = if (nitro) 30 else 50
            if (spawnTimer >= rate) {
                spawnTimer = 0
                obstacles.add(Car((Math.random() * (w - 60) + 30).toFloat(), -100f))
            }
            obstacles.removeAll { it.y > h + 50 }
            score += if (nitro) 2 else 1
            for (o in obstacles) {
                val dx = kotlin.math.abs(o.x - carX)
                val dy = kotlin.math.abs(o.y - (h - 100))
                if (dx < 50 && dy < 60) { alive = false; onGameOver(); return@LaunchedEffect }
                if (dx < 80 && dy < 30 && o.y > h - 130) {
                    nearMisses += 1; score += 5
                }
            }
            onScore(score)
            if (timeLimit > 0 && System.currentTimeMillis() - startT >= timeLimit) {
                alive = false; onGameOver(); break
            }
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { nitro = true },
                onDragEnd = { nitro = false },
                onDragCancel = { nitro = false },
            ) { change, _ ->
                carX = change.position.x.coerceIn(40f, w - 40f)
            }
        }
    ) {
        w = size.width; h = size.height
        drawRect(Color(0xFF1A0F2E), size = size)
        drawRect(Color.White.copy(0.2f), Offset(w / 2 - 4, 0f), Size(8f, h))
        for (o in obstacles) {
            drawRect(Color(0xFFEF4444), Offset(o.x - 30, o.y), Size(60f, 90f))
        }
        drawRect(accent, Offset(carX - 30, h - 140), Size(60f, 90f))
        if (nitro) {
            drawCircle(Color(0xFFFFD166), radius = 22f, center = Offset(carX, h - 30))
        }
    }
}
