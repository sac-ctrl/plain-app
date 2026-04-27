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
fun MathGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val totalTime = when (difficulty) { "Easy" -> 60; "Hard" -> 25; "Insane" -> 15; else -> 40 }
    var timeLeft by remember { mutableStateOf(totalTime) }
    var score by remember { mutableStateOf(0) }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf(0) }
    var options by remember { mutableStateOf(listOf<Int>()) }
    var alive by remember { mutableStateOf(true) }

    fun gen() {
        val a = (2..(if (difficulty == "Easy") 10 else 20)).random()
        val b = (2..(if (difficulty == "Easy") 10 else 20)).random()
        val ops = listOf("+", "-", "*")
        val op = ops.random()
        val v = when (op) { "+" -> a + b; "-" -> a - b; "*" -> a * b; else -> 0 }
        question = "$a $op $b"
        answer = v
        val list = mutableListOf(v)
        while (list.size < 4) {
            val w = v + (-5..5).random() * (1..3).random()
            if (w !in list) list.add(w)
        }
        list.shuffle(); options = list
    }
    LaunchedEffect(Unit) { gen() }

    LaunchedEffect(paused, alive) {
        while (alive && !paused && timeLeft > 0) {
            delay(1000); timeLeft -= 1
        }
        if (timeLeft <= 0 && alive) { alive = false; onGameOver() }
    }

    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$timeLeft s", color = if (timeLeft < 10) Color(0xFFEF4444) else Color.White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            Text(question, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(28.dp))
            Column {
                for (row in 0..1) {
                    Row {
                        for (col in 0..1) {
                            val opt = options.getOrNull(row * 2 + col) ?: continue
                            Box(
                                modifier = Modifier
                                    .padding(6.dp).size(width = 110.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(accent.copy(0.2f))
                                    .clickable(enabled = !paused) {
                                        if (opt == answer) { score += 100; onScore(score); gen() }
                                        else { score = (score - 25).coerceAtLeast(0); onScore(score) }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { Text("$opt", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}
