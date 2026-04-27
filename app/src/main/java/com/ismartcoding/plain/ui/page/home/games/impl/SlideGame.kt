package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SlideGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val n = if (difficulty == "Easy") 3 else if (difficulty == "Insane") 5 else 4
    val tiles = remember {
        val list = (1 until n * n).toMutableList().apply { add(0) }
        repeat(120) {
            val zero = list.indexOf(0)
            val moves = mutableListOf<Int>()
            if (zero % n > 0) moves.add(zero - 1)
            if (zero % n < n - 1) moves.add(zero + 1)
            if (zero / n > 0) moves.add(zero - n)
            if (zero / n < n - 1) moves.add(zero + n)
            val pick = moves.random()
            list[zero] = list[pick]; list[pick] = 0
        }
        mutableStateListOf<Int>().apply { addAll(list) }
    }
    var moves by remember { mutableStateOf(0) }

    fun tryMove(i: Int) {
        if (paused) return
        val zero = tiles.indexOf(0)
        val ok = (i == zero - 1 && i % n != n - 1) ||
                 (i == zero + 1 && i % n != 0) ||
                 (i == zero - n) || (i == zero + n)
        if (!ok) return
        tiles[zero] = tiles[i]; tiles[i] = 0
        moves += 1
        val score = (1000 - moves * 5).coerceAtLeast(0)
        onScore(score)
        if (tiles.dropLast(1) == (1 until n * n).toList() && tiles.last() == 0) onGameOver()
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column {
            for (r in 0 until n) {
                Row {
                    for (c in 0 until n) {
                        val idx = r * n + c
                        val v = tiles[idx]
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size((if (n == 5) 60 else if (n == 3) 90 else 72).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (v == 0) Color.Transparent else accent.copy(0.5f))
                                .clickable { if (v != 0) tryMove(idx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v != 0) Text("$v", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Moves $moves", color = Color.White.copy(0.7f), fontSize = 13.sp)
        }
    }
}
