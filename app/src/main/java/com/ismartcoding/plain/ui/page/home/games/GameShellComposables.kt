package com.ismartcoding.plain.ui.page.home.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameTopBar(
    meta: GameMeta,
    score: Int,
    best: Int,
    paused: Boolean,
    canPause: Boolean,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(meta.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Best $best", color = Color.White.copy(0.7f), fontSize = 12.sp)
        }
        ScoreChip(score, meta.accent)
        Spacer(Modifier.width(8.dp))
        if (canPause) {
            IconButtonGlass(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause) { onTogglePause() }
            Spacer(Modifier.width(8.dp))
        }
        IconButtonGlass(Icons.Filled.Replay) { onRestart() }
    }
}

@Composable
fun ScoreChip(score: Int, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.06f))
            .border(1.dp, accent.copy(0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(score.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun IconButtonGlass(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.08f))
            .border(1.dp, Color.White.copy(0.18f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PauseOverlay(onResume: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.55f)).clickable { onResume() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Paused", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Tap anywhere to resume", color = Color.White.copy(0.7f), fontSize = 14.sp)
        }
    }
}

@Composable
fun StartCard(
    meta: GameMeta,
    selectedMode: String,
    onModeChange: (String) -> Unit,
    selectedDifficulty: String,
    onDifficultyChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    val r = GamesStore.record(meta.id)
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.07f), Color.White.copy(0.02f))))
                .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(meta.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(meta.tagline, color = Color.White.copy(0.7f), fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatTile("Best", r.best.toString(), meta.accent)
                StatTile("Plays", r.plays.toString(), meta.accent)
                val avg = if (r.plays == 0) 0 else (r.totalScore / r.plays).toInt()
                StatTile("Avg", avg.toString(), meta.accent)
            }
            Spacer(Modifier.height(20.dp))
            Text("Mode", color = Color.White.copy(0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipRow(meta.modes, selectedMode, onModeChange, meta.accent)
            Spacer(Modifier.height(14.dp))
            Text("Difficulty", color = Color.White.copy(0.7f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            ChipRow(listOf("Easy", "Medium", "Hard", "Insane"), selectedDifficulty, onDifficultyChange, meta.accent)
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(meta.accent, meta.accent.copy(0.7f))))
                    .clickable { onStart() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("PLAY", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Text(label, color = Color.White.copy(0.6f), fontSize = 11.sp)
    }
}

@Composable
fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val isSel = opt.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) accent.copy(0.25f) else Color.White.copy(0.05f))
                    .border(1.dp, if (isSel) accent else Color.White.copy(0.12f), RoundedCornerShape(12.dp))
                    .clickable { onSelect(opt) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(opt, color = Color.White, fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun ResultCard(
    meta: GameMeta,
    score: Int,
    isNewBest: Boolean,
    coinsAwarded: Int,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val board = remember(score) { GamesStore.fakeLeaderboard(score) }
    val rank = board.indexOfFirst { it.first == "You" } + 1
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.08f), Color.White.copy(0.02f))))
                .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (isNewBest) "NEW BEST!" else "Run complete", color = meta.accent,
                fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(score.toString(), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text("+$coinsAwarded coins   |   Rank #$rank", color = Color.White.copy(0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text("Live ranking", color = Color.White.copy(0.6f), fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                board.forEachIndexed { idx, (name, sc) ->
                    val isYou = name == "You"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isYou) meta.accent.copy(0.18f) else Color.Transparent)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#${idx + 1}", color = Color.White.copy(0.7f), fontSize = 12.sp,
                            modifier = Modifier.width(28.dp))
                        Text(name, color = Color.White,
                            fontWeight = if (isYou) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text(sc.toString(), color = if (isYou) meta.accent else Color.White, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(14.dp))
                        .clickable { onExit() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Exit", color = Color.White, fontWeight = FontWeight.Medium) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(meta.accent, meta.accent.copy(0.7f))))
                        .clickable { onRetry() }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Play again", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
