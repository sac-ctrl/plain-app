package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun Game2048(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val n = 4
    val board = remember { mutableStateListOf<Int>().apply { repeat(n * n) { add(0) } } }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }

    fun spawn() {
        val empty = (0 until n * n).filter { board[it] == 0 }
        if (empty.isEmpty()) return
        board[empty.random()] = if (Math.random() < 0.9) 2 else 4
    }

    LaunchedEffect(Unit) { if (board.all { it == 0 }) { spawn(); spawn() } }

    fun get(r: Int, c: Int): Int = board[r * n + c]
    fun set(r: Int, c: Int, v: Int) { board[r * n + c] = v }

    fun mergeLine(line: IntArray): Pair<IntArray, Int> {
        val nz = line.filter { it != 0 }.toMutableList()
        var gained = 0
        var i = 0
        while (i < nz.size - 1) {
            if (nz[i] == nz[i + 1]) {
                nz[i] = nz[i] * 2; gained += nz[i]; nz.removeAt(i + 1)
            }
            i += 1
        }
        while (nz.size < n) nz.add(0)
        return nz.toIntArray() to gained
    }

    fun move(dir: Int): Boolean {
        var moved = false; var gained = 0
        when (dir) {
            0 -> for (r in 0 until n) {
                val orig = IntArray(n) { get(r, it) }
                val (m, g) = mergeLine(orig)
                if (!m.contentEquals(orig)) { for (c in 0 until n) set(r, c, m[c]); moved = true }
                gained += g
            }
            1 -> for (r in 0 until n) {
                val orig = IntArray(n) { get(r, it) }
                val rev = orig.reversedArray()
                val (m, g) = mergeLine(rev)
                val res = m.reversedArray()
                if (!res.contentEquals(orig)) { for (c in 0 until n) set(r, c, res[c]); moved = true }
                gained += g
            }
            2 -> for (c in 0 until n) {
                val orig = IntArray(n) { get(it, c) }
                val (m, g) = mergeLine(orig)
                if (!m.contentEquals(orig)) { for (r in 0 until n) set(r, c, m[r]); moved = true }
                gained += g
            }
            3 -> for (c in 0 until n) {
                val orig = IntArray(n) { get(it, c) }
                val rev = orig.reversedArray()
                val (m, g) = mergeLine(rev)
                val res = m.reversedArray()
                if (!res.contentEquals(orig)) { for (r in 0 until n) set(r, c, res[r]); moved = true }
                gained += g
            }
        }
        if (moved) {
            score += gained; onScore(score); spawn()
            if ((0 until n * n).all { board[it] != 0 }) {
                var canMove = false
                for (r in 0 until n) for (c in 0 until n) {
                    val v = get(r, c)
                    if (c + 1 < n && get(r, c + 1) == v) canMove = true
                    if (r + 1 < n && get(r + 1, c) == v) canMove = true
                }
                if (!canMove) { alive = false; onGameOver() }
            }
        }
        return moved
    }

    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            var dx = 0f; var dy = 0f
            detectDragGestures(
                onDragStart = { dx = 0f; dy = 0f },
                onDragEnd = {
                    if (!alive || paused) { dx = 0f; dy = 0f; return@detectDragGestures }
                    val dir = if (abs(dx) > abs(dy)) {
                        if (dx > 30) 1 else if (dx < -30) 0 else -1
                    } else {
                        if (dy > 30) 3 else if (dy < -30) 2 else -1
                    }
                    if (dir >= 0) move(dir)
                    dx = 0f; dy = 0f
                },
            ) { _, drag -> dx += drag.x; dy += drag.y }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column {
            for (r in 0 until n) {
                Row {
                    for (c in 0 until n) {
                        val v = get(r, c)
                        Box(
                            modifier = Modifier
                                .padding(4.dp).size(70.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (v == 0) Color.White.copy(0.05f) else colorFor(v, accent)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v != 0) Text("$v",
                                color = if (v <= 4) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (v < 100) 22.sp else if (v < 1000) 18.sp else 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun colorFor(v: Int, accent: Color): Color = when (v) {
    2 -> Color(0xFFEEE4DA); 4 -> Color(0xFFEDE0C8); 8 -> Color(0xFFF2B179)
    16 -> Color(0xFFF59563); 32 -> Color(0xFFF67C5F); 64 -> Color(0xFFF65E3B)
    128 -> Color(0xFFEDCF72); 256 -> Color(0xFFEDCC61); 512 -> Color(0xFFEDC850)
    1024 -> Color(0xFFEDC53F); 2048 -> Color(0xFFEDC22E); else -> accent
}
