package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

@Composable
fun ColorGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val palette = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFEAB308))
    var ballY by remember { mutableStateOf(0f) }
    var vy by remember { mutableStateOf(0f) }
    var ballColorIdx by remember { mutableStateOf(0) }
    var ringY by remember { mutableStateOf(0f) }
    var ringPhase by remember { mutableStateOf(0f) }
    var score by remember { mutableStateOf(0) }
    var combo by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    val gravity = when (difficulty) { "Easy" -> 0.35f; "Hard" -> 0.6f; "Insane" -> 0.8f; else -> 0.5f }

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && ballY == 0f) {
            ballY = h - 200; ringY = h / 2
        }
    }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            vy += gravity
            ballY += vy
            ringPhase += 0.05f
            if (ballY > h) { alive = false; onGameOver(); break }
            if (ballY < 100) { ballY = 100f; vy = 0f }
            val cx = w / 2
            if (ballY in (ringY - 6f)..(ringY + 6f)) {
                val seg = ((ringPhase * 4) % 4).toInt()
                if (seg == ballColorIdx) {
                    score += 10 + combo * 2
                    combo += 1
                    onScore(score)
                    ballColorIdx = (palette.indices).random()
                    ringY -= 200
                } else {
                    alive = false; onGameOver(); break
                }
            }
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (alive && !paused) vy = -10f
        }
    ) {
        w = size.width; h = size.height
        drawRect(Color(0xFF101828), size = size)
        val cx = w / 2
        val ringR = 80f
        for (i in 0..3) {
            val sweepStart = (i * 90f + ringPhase * 180f / Math.PI.toFloat()) % 360
            drawArc(
                color = palette[i],
                startAngle = sweepStart, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - ringR, ringY - ringR),
                size = androidx.compose.ui.geometry.Size(ringR * 2, ringR * 2),
                style = Stroke(width = 18f),
            )
        }
        drawCircle(palette[ballColorIdx], radius = 18f, center = Offset(cx, ballY))
    }
}
