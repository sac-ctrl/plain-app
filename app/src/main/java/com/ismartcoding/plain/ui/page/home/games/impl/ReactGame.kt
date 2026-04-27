package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ReactGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    var phase by remember { mutableStateOf("ready") }
    var startAt by remember { mutableStateOf(0L) }
    var ms by remember { mutableStateOf(0L) }
    var round by remember { mutableStateOf(0) }
    var totalMs by remember { mutableStateOf(0L) }
    val rounds = when (difficulty) { "Easy" -> 3; "Hard" -> 7; "Insane" -> 10; else -> 5 }

    LaunchedEffect(round, paused) {
        if (paused) return@LaunchedEffect
        if (phase == "ready" && round < rounds) {
            phase = "wait"
            delay((1000 + Math.random() * 2500).toLong())
            startAt = System.currentTimeMillis()
            phase = "tap"
        }
    }

    val bg = when (phase) {
        "wait" -> Color(0xFFEF4444)
        "tap" -> Color(0xFF22C55E)
        "result" -> Color(0xFF1F2937)
        else -> Color(0xFF1F2937)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .clickable {
                when (phase) {
                    "wait" -> {
                        ms = 999; totalMs += ms; round += 1; phase = if (round >= rounds) "done" else "ready"
                    }
                    "tap" -> {
                        ms = System.currentTimeMillis() - startAt
                        totalMs += ms
                        round += 1
                        phase = if (round >= rounds) "done" else "ready"
                    }
                    else -> {}
                }
                if (phase == "done") {
                    val avg = (totalMs / rounds).toInt()
                    val score = (1000 - avg).coerceAtLeast(0)
                    onScore(score); onGameOver()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                "ready" -> Text("Get ready...", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                "wait" -> Text("Wait for green", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                "tap" -> Text("TAP NOW", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                "result", "done" -> {
                    Text("$ms ms", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (phase != "tap") {
                Spacer(Modifier.height(12.dp))
                Text("Round ${round.coerceAtMost(rounds)}/$rounds", color = Color.White.copy(0.8f), fontSize = 16.sp)
            }
        }
    }
}
