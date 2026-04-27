package com.ismartcoding.plain.ui.page.home.games

import androidx.compose.ui.graphics.Color

data class GameMeta(
    val id: String,
    val title: String,
    val tagline: String,
    val color: Color,
    val accent: Color,
    val modes: List<String>,
)

object GamesRegistry {
    val all: List<GameMeta> = listOf(
        GameMeta("snake", "Neon Snake", "Eat. Grow. Survive.",
            Color(0xFF0F3D2E), Color(0xFF38FFB1), listOf("Classic", "Time", "Challenge")),
        GameMeta("flappy", "Flappy Pulse", "Tap to glide through neon pipes",
            Color(0xFF1B1340), Color(0xFFFFD166), listOf("Classic", "Survival", "Ghost")),
        GameMeta("brick", "Brick Crusher", "Smash, drop power-ups, advance",
            Color(0xFF2B0F4F), Color(0xFFFF6BD6), listOf("Classic", "Challenge")),
        GameMeta("space", "Space Hunter", "Dodge, shoot, beat the boss",
            Color(0xFF02123A), Color(0xFF7CE7FF), listOf("Classic", "Survival", "Boss")),
        GameMeta("car", "Lane Rush", "Hold for nitro. Near miss = bonus",
            Color(0xFF1A0F2E), Color(0xFFFFB347), listOf("Classic", "Time", "Challenge")),
        GameMeta("color", "Color Switch", "Pass only matching colors",
            Color(0xFF101828), Color(0xFFFF5C8A), listOf("Classic", "Survival")),
        GameMeta("react", "Reaction", "Tap as fast as humanly possible",
            Color(0xFF051F2A), Color(0xFF36F1CD), listOf("Classic")),
        GameMeta("g2048", "2048", "Slide tiles, double up",
            Color(0xFF22150B), Color(0xFFFFA94D), listOf("Classic", "Time")),
        GameMeta("memory", "Memory Match", "Find all the pairs",
            Color(0xFF0F1A2E), Color(0xFF60A5FA), listOf("Classic", "Time")),
        GameMeta("slide", "Sliding Puzzle", "Order tiles 1 to 15",
            Color(0xFF1F1A2E), Color(0xFFB28DFF), listOf("Classic", "Time")),
        GameMeta("dice", "Dice Battle", "Beat the AI in 5 rounds",
            Color(0xFF1A1A1A), Color(0xFFEE5253), listOf("Classic")),
        GameMeta("aim", "Aim Trainer", "Pop targets before they vanish",
            Color(0xFF101828), Color(0xFFEF4444), listOf("Classic", "Time")),
        GameMeta("runner", "Endless Runner", "Jump and slide forever",
            Color(0xFF1F1B16), Color(0xFFFFD166), listOf("Classic", "Survival")),
        GameMeta("math", "Quick Math", "Solve. Quickly.",
            Color(0xFF0E1F1B), Color(0xFF34D399), listOf("Classic", "Time")),
        GameMeta("tap", "Tap Pattern", "Repeat the lit sequence",
            Color(0xFF1B1040), Color(0xFFFFD166), listOf("Classic", "Survival")),
    )

    fun byId(id: String): GameMeta? = all.firstOrNull { it.id == id }
}
