package com.ismartcoding.plain.ui.page.home.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ismartcoding.plain.preferences.GamesStateJsonPreference
import com.ismartcoding.plain.ui.nav.Routing

@Composable
fun GamesTabContent(navController: NavHostController) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    val tick by GamesStore.state.collectAsState()

    LaunchedEffect(Unit) {
        if (!loaded) {
            GamesStore.loadFromJson(GamesStateJsonPreference.getAsync(context))
            loaded = true
        }
    }

    if (!loaded) return

    val lastId = remember(tick) { GamesStore.lastPlayedId() }
    val coins = remember(tick) { GamesStore.getCoins() }
    val streak = remember(tick) { GamesStore.getStreak() }
    val dailies = remember(tick) { GamesStore.getDailies() }

    var tutorialFor by remember { mutableStateOf<GameMeta?>(null) }
    tutorialFor?.let { meta ->
        GameTutorialDialog(
            meta = meta,
            onClose = { tutorialFor = null },
            onPlay = {
                tutorialFor = null
                navController.navigate(Routing.GameDetail(meta.id))
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {

        HeroCard(coins, streak)
        Spacer(Modifier.height(14.dp))

        if (lastId != null) {
            ContinuePlayingTile(lastId, navController)
            Spacer(Modifier.height(14.dp))
        }

        DailyChallengesCard(dailies)
        Spacer(Modifier.height(18.dp))

        Text("All games", color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))

        GamesRegistry.all.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { meta ->
                    Box(modifier = Modifier.weight(1f)) {
                        GameTile(
                            meta = meta,
                            onClick = { navController.navigate(Routing.GameDetail(meta.id)) },
                            onInfo = { tutorialFor = meta },
                        )
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f)) {}
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroCard(coins: Int, streak: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF6C5CE7), Color(0xFFEC4899))))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Game Lounge", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$coins coins   |   $streak run streak",
                    color = Color.White.copy(0.85f), fontSize = 13.sp)
            }
            Icon(Icons.Filled.Star, null, tint = Color(0xFFFFD166), modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun ContinuePlayingTile(id: String, navController: NavHostController) {
    val meta = GamesRegistry.byId(id) ?: return
    val r = GamesStore.record(id)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(meta.color)
            .border(1.dp, meta.accent.copy(0.4f), RoundedCornerShape(16.dp))
            .clickable { navController.navigate(Routing.GameDetail(id)) }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("CONTINUE", color = meta.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(meta.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Best ${r.best} | Plays ${r.plays}",
                    color = Color.White.copy(0.7f), fontSize = 12.sp)
            }
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(meta.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun DailyChallengesCard(list: List<DailyChallenge>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            .padding(14.dp),
    ) {
        Column {
            Text("Daily challenges", color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            list.forEach { d ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                        .background(if (d.done) Color(0xFF34D399) else Color(0xFF60A5FA)))
                    Spacer(Modifier.width(8.dp))
                    Text(d.description, color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(if (d.done) "DONE" else "+${d.rewardCoins}",
                        color = if (d.done) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GameTile(meta: GameMeta, onClick: () -> Unit, onInfo: () -> Unit) {
    val r = GamesStore.record(meta.id)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(meta.color, Color.Black)))
            .border(1.dp, meta.accent.copy(0.4f), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(meta.title, color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(meta.tagline, color = Color.White.copy(0.7f),
                        fontSize = 11.sp, maxLines = 2)
                }
                IconButton(
                    onClick = onInfo,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.35f))
                        .border(1.dp, Color.White.copy(0.22f), CircleShape),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "How to play",
                        tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(meta.accent.copy(0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) { Text("Best ${r.best}", color = meta.accent, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(meta.accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}
