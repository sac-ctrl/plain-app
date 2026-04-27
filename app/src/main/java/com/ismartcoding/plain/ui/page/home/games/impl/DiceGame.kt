package com.ismartcoding.plain.ui.page.home.games.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
fun DiceGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val rounds = 5
    var round by remember { mutableStateOf(1) }
    var youDie by remember { mutableStateOf(0) }
    var aiDie by remember { mutableStateOf(0) }
    var youScore by remember { mutableStateOf(0) }
    var aiScore by remember { mutableStateOf(0) }
    var rolling by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val aiBoost = when (difficulty) { "Easy" -> -1; "Hard" -> 1; "Insane" -> 2; else -> 0 }

    LaunchedEffect(rolling) {
        if (rolling) {
            repeat(10) {
                youDie = (1..6).random(); aiDie = (1..6).random()
                delay(60)
            }
            youDie = (1..6).random()
            aiDie = (1..6).random().let { (it + aiBoost).coerceIn(1, 6) }
            if (youDie > aiDie) youScore += 1
            else if (aiDie > youDie) aiScore += 1
            onScore(youScore * 100 - aiScore * 50)
            round += 1
            rolling = false
            if (round > rounds) {
                done = true
                onScore(youScore * 100 - aiScore * 50 + (if (youScore > aiScore) 200 else 0))
                onGameOver()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                DiceFace(youDie, accent, "You")
                Text("vs", color = Color.White.copy(0.7f), fontSize = 18.sp)
                DiceFace(aiDie, Color(0xFFEF4444), "AI")
            }
            Spacer(Modifier.height(20.dp))
            Text("$youScore  -  $aiScore", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Round ${round.coerceAtMost(rounds)}/$rounds", color = Color.White.copy(0.7f))
            Spacer(Modifier.height(28.dp))
            if (!done) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent)
                        .clickable(enabled = !rolling && !paused) { rolling = true }
                        .padding(horizontal = 32.dp, vertical = 14.dp),
                ) { Text(if (rolling) "Rolling..." else "Roll", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun DiceFace(v: Int, accent: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(2.dp, accent, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (v == 0) "?" else "$v", color = Color.Black, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(0.8f), fontSize = 13.sp)
    }
}
