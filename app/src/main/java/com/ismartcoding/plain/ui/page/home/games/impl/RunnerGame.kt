package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

private data class Obstacle(var x: Float, val high: Boolean)

@Composable
fun RunnerGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    var heroY by remember { mutableStateOf(0f) }
    var vy by remember { mutableStateOf(0f) }
    var sliding by remember { mutableStateOf(false) }
    var alive by remember { mutableStateOf(true) }
    var score by remember { mutableStateOf(0) }
    val obstacles = remember { mutableStateListOf<Obstacle>() }
    var spawn by remember { mutableStateOf(0) }
    val speed = when (difficulty) { "Easy" -> 5f; "Hard" -> 9f; "Insane" -> 12f; else -> 7f }
    val gravity = 1.2f
    var groundY by remember { mutableStateOf(0f) }

    LaunchedEffect(w, h) {
        if (w > 0 && groundY == 0f) { groundY = h - 80; heroY = groundY }
    }

    LaunchedEffect(paused, alive, w) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            vy += gravity
            heroY += vy
            if (heroY > groundY) { heroY = groundY; vy = 0f }
            for (o in obstacles) o.x -= speed
            obstacles.removeAll { it.x < -40 }
            spawn += 1
            if (spawn > 50 && Math.random() < 0.05) {
                spawn = 0
                obstacles.add(Obstacle(w + 40, Math.random() < 0.4))
            }
            score += 1
            onScore(score)
            for (o in obstacles) {
                val heroX = w / 4
                if (kotlin.math.abs(o.x - heroX) < 28) {
                    if (o.high) {
                        if (!sliding && heroY > groundY - 30) { alive = false; onGameOver(); return@LaunchedEffect }
                    } else {
                        if (heroY > groundY - 60) { alive = false; onGameOver(); return@LaunchedEffect }
                    }
                }
            }
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { },
                onDragEnd = { sliding = false },
                onDragCancel = { sliding = false },
            ) { _, dy ->
                if (!alive || paused) return@detectVerticalDragGestures
                if (dy < -10 && heroY >= groundY - 1) vy = -22f
                if (dy > 10) sliding = true
            }
        }
    ) {
        w = size.width; h = size.height
        drawRect(Color(0xFF1F1B16), size = size)
        drawRect(Color.White.copy(0.4f), Offset(0f, groundY + 40), Size(w, 4f))
        for (o in obstacles) {
            if (o.high) drawRect(Color(0xFFEF4444), Offset(o.x - 20, groundY - 60), Size(40f, 40f))
            else drawRect(Color(0xFFF59E0B), Offset(o.x - 20, groundY), Size(40f, 40f))
        }
        val heroH = if (sliding) 30f else 60f
        drawRect(accent, Offset(w / 4 - 20, heroY - heroH), Size(40f, heroH))
    }
}
