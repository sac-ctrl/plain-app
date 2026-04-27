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
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun SnakeGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val cols = 18
    val rows = 26
    val baseDelay = when (difficulty) { "Easy" -> 160L; "Hard" -> 80L; "Insane" -> 55L; else -> 110L }
    var canvasSize by remember { mutableStateOf(IntSize(0, 0)) }
    val snake = remember { mutableStateListOf(8 to 13, 8 to 14, 8 to 15) }
    var dir by remember { mutableStateOf(0 to -1) }
    var pendingDir by remember { mutableStateOf(dir) }
    var food by remember { mutableStateOf(5 to 5) }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var startTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val walls = mode.equals("Challenge", true)
    val timeLimitMs = if (mode.equals("Time", true)) 60_000L else 0L

    fun placeFood() {
        var ok = false
        while (!ok) {
            val p = (0 until cols).random() to (0 until rows).random()
            if (p !in snake) { food = p; ok = true }
        }
    }

    LaunchedEffect(Unit) { placeFood() }

    LaunchedEffect(paused, alive) {
        while (alive && !paused) {
            delay(baseDelay)
            dir = pendingDir
            val head = snake.last()
            var nx = head.first + dir.first
            var ny = head.second + dir.second
            if (walls) {
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) { alive = false; onGameOver(); break }
            } else {
                nx = (nx + cols) % cols
                ny = (ny + rows) % rows
            }
            val newHead = nx to ny
            if (newHead in snake) { alive = false; onGameOver(); break }
            snake.add(newHead)
            if (newHead == food) {
                score += 10
                onScore(score)
                placeFood()
            } else snake.removeAt(0)
            if (timeLimitMs > 0 && System.currentTimeMillis() - startTime >= timeLimitMs) {
                alive = false; onGameOver(); break
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDx = 0f; var totalDy = 0f
                detectDragGestures(
                    onDragStart = { totalDx = 0f; totalDy = 0f },
                    onDragEnd = { totalDx = 0f; totalDy = 0f },
                ) { _, drag ->
                    totalDx += drag.x; totalDy += drag.y
                    if (abs(totalDx) > 30 || abs(totalDy) > 30) {
                        val nd = if (abs(totalDx) > abs(totalDy)) {
                            (if (totalDx > 0) 1 else -1) to 0
                        } else {
                            0 to (if (totalDy > 0) 1 else -1)
                        }
                        if (nd.first != -dir.first || nd.second != -dir.second) pendingDir = nd
                        totalDx = 0f; totalDy = 0f
                    }
                }
            },
    ) {
        canvasSize = IntSize(size.width.toInt(), size.height.toInt())
        val cellW = size.width / cols
        val cellH = size.height / rows
        val cell = minOf(cellW, cellH)
        val offX = (size.width - cell * cols) / 2f
        val offY = (size.height - cell * rows) / 2f
        if (walls) {
            drawRect(accent.copy(0.3f), Offset(offX - 2f, offY - 2f),
                Size(cell * cols + 4f, cell * rows + 4f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        snake.forEachIndexed { idx, p ->
            val isHead = idx == snake.size - 1
            val color = if (isHead) accent else accent.copy(0.7f)
            drawRect(color,
                Offset(offX + p.first * cell + 1, offY + p.second * cell + 1),
                Size(cell - 2, cell - 2))
        }
        drawCircle(Color(0xFFFF6B6B),
            radius = cell / 2.2f,
            center = Offset(offX + food.first * cell + cell / 2, offY + food.second * cell + cell / 2))
    }
}
