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

private data class Bullet(var x: Float, var y: Float, val enemy: Boolean = false)
private data class Enemy(var x: Float, var y: Float, var hp: Int = 1)

@Composable
fun SpaceGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var w by remember { mutableStateOf(0f) }
    var h by remember { mutableStateOf(0f) }
    var shipX by remember { mutableStateOf(0f) }
    val bullets = remember { mutableStateListOf<Bullet>() }
    val enemies = remember { mutableStateListOf<Enemy>() }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    var lives by remember { mutableStateOf(3) }
    var wave by remember { mutableStateOf(1) }
    var fireCooldown by remember { mutableStateOf(0) }
    var spawnTimer by remember { mutableStateOf(0) }
    val spawnRate = when (difficulty) { "Easy" -> 60; "Hard" -> 30; "Insane" -> 18; else -> 45 }
    val isBoss = mode.equals("Boss", true)

    LaunchedEffect(w, h) {
        if (w > 0 && h > 0 && shipX == 0f) shipX = w / 2
    }

    LaunchedEffect(paused, alive, w, h) {
        if (w == 0f) return@LaunchedEffect
        while (alive && !paused) {
            delay(16)
            fireCooldown -= 1
            if (fireCooldown <= 0) {
                bullets.add(Bullet(shipX, h - 100))
                fireCooldown = 12
            }
            spawnTimer += 1
            if (spawnTimer >= spawnRate) {
                spawnTimer = 0
                if (isBoss && wave >= 3 && enemies.none { it.hp > 5 }) {
                    enemies.add(Enemy(w / 2, -50f, hp = 30 + wave * 5))
                } else {
                    enemies.add(Enemy((Math.random() * (w - 40) + 20).toFloat(), -30f, hp = 1 + wave / 3))
                }
            }
            for (b in bullets) b.y += if (b.enemy) 6f else -10f
            bullets.removeAll { it.y < -20 || it.y > h + 20 }
            for (e in enemies) e.y += 2f + wave * 0.3f
            val ei = enemies.iterator()
            while (ei.hasNext()) {
                val e = ei.next()
                if (e.y > h - 80 && kotlin.math.abs(e.x - shipX) < 40) {
                    lives -= 1; ei.remove()
                    if (lives <= 0) { alive = false; onGameOver(); break }
                    continue
                }
                if (e.y > h + 30) ei.remove()
            }
            val bi = bullets.iterator()
            while (bi.hasNext()) {
                val b = bi.next()
                if (b.enemy) {
                    if (kotlin.math.abs(b.x - shipX) < 25 && kotlin.math.abs(b.y - (h - 80)) < 30) {
                        lives -= 1; bi.remove()
                        if (lives <= 0) { alive = false; onGameOver(); break }
                    }
                    continue
                }
                var hit = false
                for (e in enemies) {
                    if (kotlin.math.abs(e.x - b.x) < 24 && kotlin.math.abs(e.y - b.y) < 24) {
                        e.hp -= 1
                        if (e.hp <= 0) { enemies.remove(e); score += 50; onScore(score) }
                        hit = true; break
                    }
                }
                if (hit) bi.remove()
            }
            if (score > 0 && score % 500 == 0 && enemies.isEmpty()) {
                wave += 1
            }
        }
    }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectDragGestures { change, _ ->
                shipX = change.position.x.coerceIn(30f, w - 30f)
            }
        }
    ) {
        w = size.width; h = size.height
        drawRect(Color(0xFF02091F), size = size)
        for (b in bullets) {
            drawCircle(if (b.enemy) Color.Red else accent, radius = 4f, center = Offset(b.x, b.y))
        }
        for (e in enemies) {
            val c = if (e.hp > 5) Color(0xFFEC4899) else Color(0xFF7CE7FF)
            drawCircle(c, radius = if (e.hp > 5) 28f else 16f, center = Offset(e.x, e.y))
        }
        drawCircle(accent, radius = 18f, center = Offset(shipX, h - 80))
        for (i in 0 until lives) {
            drawRect(Color(0xFFFFD166), Offset(20f + i * 16f, 20f), Size(10f, 10f))
        }
    }
}
