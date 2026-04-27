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
import kotlinx.coroutines.delay

@Composable
fun MemoryGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val pairs = when (difficulty) { "Easy" -> 6; "Hard" -> 10; "Insane" -> 12; else -> 8 }
    val cols = if (pairs <= 6) 3 else if (pairs <= 8) 4 else 4
    val rows = (pairs * 2 + cols - 1) / cols
    val symbols = listOf("A","B","C","D","E","F","G","H","I","J","K","L")
    val cards = remember {
        val list = symbols.take(pairs).flatMap { listOf(it, it) }.toMutableList()
        list.shuffle()
        mutableStateListOf<String>().apply { addAll(list) }
    }
    val flipped = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<Int>() }
    var moves by remember { mutableStateOf(0) }
    var lastFlip by remember { mutableStateOf<Pair<Int,Int>?>(null) }
    var score by remember { mutableStateOf(0) }
    val startT = remember { System.currentTimeMillis() }

    LaunchedEffect(lastFlip) {
        val (a,b) = lastFlip ?: return@LaunchedEffect
        delay(700)
        if (cards[a] == cards[b]) {
            matched.add(a); matched.add(b)
            score += 100
            onScore(score)
            if (matched.size == cards.size) {
                val timeBonus = (60_000 - (System.currentTimeMillis() - startT)).coerceAtLeast(0).toInt() / 100
                score += timeBonus; onScore(score); onGameOver()
            }
        } else {
            score = (score - 5).coerceAtLeast(0); onScore(score)
        }
        flipped.clear()
        lastFlip = null
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column {
            for (r in 0 until rows) {
                Row {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        if (idx >= cards.size) {
                            Spacer(Modifier.size(70.dp).padding(4.dp))
                        } else {
                            val isOpen = idx in flipped || idx in matched
                            Box(
                                modifier = Modifier
                                    .padding(4.dp).size(70.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (idx in matched) accent.copy(0.4f) else if (isOpen) Color.White.copy(0.2f) else Color.White.copy(0.08f))
                                    .clickable(enabled = !isOpen && flipped.size < 2 && lastFlip == null && !paused) {
                                        flipped.add(idx)
                                        if (flipped.size == 2) { moves += 1; lastFlip = flipped[0] to flipped[1] }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isOpen) Text(cards[idx], color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Moves $moves", color = Color.White.copy(0.7f), fontSize = 13.sp)
        }
    }
}
