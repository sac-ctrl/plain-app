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
fun TapGame(
    difficulty: String, mode: String, paused: Boolean,
    onScore: (Int) -> Unit, onGameOver: () -> Unit, accent: Color,
) {
    val palette = listOf(Color(0xFFEF4444), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFFEAB308))
    val seq = remember { mutableStateListOf<Int>() }
    var userIdx by remember { mutableStateOf(0) }
    var showing by remember { mutableStateOf(true) }
    var litIdx by remember { mutableStateOf(-1) }
    var score by remember { mutableStateOf(0) }
    var alive by remember { mutableStateOf(true) }
    val flashMs = when (difficulty) { "Easy" -> 700L; "Hard" -> 350L; "Insane" -> 220L; else -> 500L }

    LaunchedEffect(Unit) { seq.add((0..3).random()) }

    LaunchedEffect(seq.size, paused) {
        if (paused) return@LaunchedEffect
        showing = true
        for (s in seq) {
            litIdx = s
            delay(flashMs)
            litIdx = -1
            delay(180)
        }
        showing = false; userIdx = 0
    }

    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (showing) "WATCH" else "REPEAT", color = Color.White, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            for (r in 0..1) {
                Row {
                    for (c in 0..1) {
                        val i = r * 2 + c
                        val isLit = litIdx == i
                        Box(
                            modifier = Modifier
                                .padding(8.dp).size(110.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isLit) palette[i] else palette[i].copy(0.35f))
                                .clickable(enabled = !showing && alive && !paused) {
                                    litIdx = i
                                    if (i == seq[userIdx]) {
                                        userIdx += 1
                                        if (userIdx >= seq.size) {
                                            score += seq.size * 10
                                            onScore(score)
                                            seq.add((0..3).random())
                                        }
                                    } else {
                                        alive = false; onGameOver()
                                    }
                                },
                        ) {}
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Length ${seq.size}", color = Color.White.copy(0.7f), fontSize = 13.sp)
        }
    }
    LaunchedEffect(litIdx) {
        if (litIdx >= 0 && !showing) { delay(180); litIdx = -1 }
    }
}
