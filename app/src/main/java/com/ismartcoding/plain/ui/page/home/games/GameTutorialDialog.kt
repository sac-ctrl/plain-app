package com.ismartcoding.plain.ui.page.home.games

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun GameTutorialDialog(
    meta: GameMeta,
    onClose: () -> Unit,
    onPlay: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(meta.color, Color.Black))),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .padding(20.dp),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(meta.title, color = Color.White,
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(meta.tagline, color = Color.White.copy(0.78f),
                            fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(meta.accent.copy(0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("?", color = meta.accent,
                            fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SectionTitle("HOW TO PLAY", meta.accent)
                    meta.tutorial.howTo.forEachIndexed { idx, step ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("${idx + 1}. ", color = meta.accent,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(step, color = Color.White.copy(0.92f),
                                fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SectionTitle("CONTROLS", meta.accent)
                    meta.tutorial.controls.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(0.4f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(c.key, color = meta.accent, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(c.action, color = Color.White.copy(0.9f), fontSize = 13.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SectionTitle("FEATURES", meta.accent)
                    meta.tutorial.features.forEach { f ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Filled.Check, null, tint = meta.accent,
                                modifier = Modifier.size(14.dp).padding(top = 3.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(f, color = Color.White.copy(0.9f),
                                fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SectionTitle("PRO TIPS", meta.accent)
                    meta.tutorial.tips.forEachIndexed { idx, tip ->
                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                            Text("${idx + 1}. ", color = meta.accent,
                                fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(tip, color = Color.White.copy(0.85f),
                                fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClose) {
                        Text("Maybe later", color = Color.White.copy(0.7f))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = meta.accent,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, accent: Color) {
    Text(
        text,
        color = accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
