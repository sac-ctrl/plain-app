package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay

private data class Pipe(var x: Float, val gapY: Float, val gapH: Float, var passed: Boolean = false)

@Composable
fun FlappyGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val gravity = when (difficulty) { "Easy" -> 0.45f; "Hard" -> 0.7f; "Insane" -> 0.9f; else -> 0.55f }
    val jump = -8.5f
    var birdY by remember { mutableStateOf(0f) }
    var vel by remember { mutableStateOf(0f) }
    val pipes = remember { mutableStateListOf<Pipe>() }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    val pipeSpeed = when (difficulty) { "Easy" -> 3.0f; "Hard" -> 4.5f; "Insane" -> 5.5f; else -> 3.6f }
    val gapH = when (difficulty) { "Easy" -> 220f; "Hard" -> 150f; "Insane" -> 130f; else -> 180f }

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && pipes.isEmpty()) {
            birdY = h / 2
            for (i in 0..2) {
                pipes.add(Pipe(w + i * (w / 2.2f), 80f + (Math.random() * (h - gapH - 200)).toFloat(), gapH))
            }
        }
    }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            vel += gravity
            birdY += vel
            if (birdY < 0 || birdY > h) { alive = false; onGameOver(); break }
            for (p in pipes) {
                p.x -= pipeSpeed
                val birdX = w / 4
                if (birdX > p.x && birdX < p.x + 60) {
                    if (birdY < p.gapY || birdY > p.gapY + p.gapH) { alive = false; onGameOver(); return@LaunchedEffect }
                }
                if (!p.passed && p.x + 60 < w / 4) {
                    p.passed = true; score += 1; onScore(score)
                }
            }
            pipes.removeAll { it.x < -80 }
            if (pipes.isEmpty() || pipes.last().x < w - w / 2.2f) {
                pipes.add(Pipe(w + 40, 80f + (Math.random() * (h - gapH - 200)).toFloat(), gapH))
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (alive && !paused) vel = jump
            },
    ) {
        w = size.width; h = size.height
        drawRect(Color(0xFF0B1340), size = size)
        for (p in pipes) {
            drawRect(accent.copy(0.85f), Offset(p.x, 0f), Size(60f, p.gapY))
            drawRect(accent.copy(0.85f), Offset(p.x, p.gapY + p.gapH),
                Size(60f, h - (p.gapY + p.gapH)))
        }
        drawCircle(Color(0xFFFFD166), radius = 22f, center = Offset(w / 4, birdY))
        drawCircle(Color.Black, radius = 4f, center = Offset(w / 4 + 6, birdY - 4))
    }
}
