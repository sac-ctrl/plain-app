package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

private data class Brick(val x: Float, val y: Float, val w: Float, val h: Float, var hp: Int)

@Composable
fun BrickGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    val cols = 7
    val rows = 5
    var paddleX by remember { mutableStateOf(0f) }
    var ballX by remember { mutableStateOf(0f) }
    var ballY by remember { mutableStateOf(0f) }
    var vx by remember { mutableStateOf(5f) }
    var vy by remember { mutableStateOf(-5f) }
    val bricks = remember { mutableStateListOf<Brick>() }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var lives by remember { mutableStateOf(3) }
    val paddleW = 130f
    val ballR = 12f
    val speedScale = when (difficulty) { "Easy" -> 0.85f; "Hard" -> 1.2f; "Insane" -> 1.5f; else -> 1f }

    fun resetBall() {
        ballX = paddleX + paddleW / 2
        ballY = h - 80
        vx = 4.5f * speedScale * (if (Math.random() > 0.5) 1 else -1)
        vy = -5f * speedScale
    }

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && bricks.isEmpty()) {
            paddleX = w / 2 - paddleW / 2
            val brickW = (w - 40) / cols
            val brickH = 28f
            for (r in 0 until rows) for (c in 0 until cols) {
                bricks.add(Brick(20 + c * brickW, 80 + r * (brickH + 6),
                    brickW - 4, brickH, if (r == 0) 2 else 1))
            }
            resetBall()
        }
    }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            ballX += vx; ballY += vy
            if (ballX - ballR < 0) { ballX = ballR; vx = -vx }
            if (ballX + ballR > w) { ballX = w - ballR; vx = -vx }
            if (ballY - ballR < 0) { ballY = ballR; vy = -vy }
            if (ballY + ballR > h) {
                lives -= 1
                if (lives <= 0) { alive = false; onGameOver(); break } else resetBall()
            }
            val pY = h - 60
            if (ballY + ballR > pY && ballY < pY + 14 && ballX > paddleX && ballX < paddleX + paddleW) {
                vy = -kotlin.math.abs(vy)
                val rel = (ballX - (paddleX + paddleW / 2)) / (paddleW / 2)
                vx = rel * 6f * speedScale
            }
            val it = bricks.iterator()
            while (it.hasNext()) {
                val b = it.next()
                if (ballX > b.x && ballX < b.x + b.w && ballY - ballR < b.y + b.h && ballY + ballR > b.y) {
                    vy = -vy
                    b.hp -= 1
                    score += 10
                    onScore(score)
                    if (b.hp <= 0) it.remove()
                    break
                }
            }
            if (bricks.isEmpty()) { alive = false; onGameOver(); break }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    paddleX = (change.position.x - paddleW / 2).coerceIn(0f, w - paddleW)
                }
            },
    ) {
        w = size.width; h = size.height
        for (b in bricks) {
            val c = if (b.hp >= 2) accent else accent.copy(0.65f)
            drawRect(c, Offset(b.x, b.y), Size(b.w, b.h))
        }
        drawRect(Color.White, Offset(paddleX, h - 60), Size(paddleW, 14f))
        drawCircle(Color(0xFFFFD166), radius = ballR, center = Offset(ballX, ballY))
        drawRect(Color.Red.copy(0.8f), Offset(20f, 20f), Size(40f * lives, 6f))
    }
}
