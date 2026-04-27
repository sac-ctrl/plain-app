package com.ismartcoding.plain.ui.page.home.games

import androidx.compose.ui.graphics.Color

data class GameControl(val key: String, val action: String)

data class GameTutorial(
    val howTo: List<String>,
    val controls: List<GameControl>,
    val features: List<String>,
    val tips: List<String>,
)

data class GameMeta(
    val id: String,
    val title: String,
    val tagline: String,
    val color: Color,
    val accent: Color,
    val modes: List<String>,
    val tutorial: GameTutorial,
)

object GamesRegistry {
    val all: List<GameMeta> = listOf(
        GameMeta("snake", "Neon Snake", "Eat. Grow. Survive.",
            Color(0xFF0F3D2E), Color(0xFF38FFB1), listOf("Classic", "Time", "Challenge"),
            GameTutorial(
                howTo = listOf(
                    "Steer the snake by swiping in the direction you want to go.",
                    "Eat the glowing food to grow longer and gain points.",
                    "Don’t crash into the walls or your own tail.",
                ),
                controls = listOf(
                    GameControl("Swipe", "Turn the snake"),
                ),
                features = listOf(
                    "Smooth tick-based movement",
                    "Best length saved",
                    "Trail glow that brightens as you grow",
                ),
                tips = listOf(
                    "Plan turns early — you can’t reverse 180°.",
                    "Hug the walls early; uncoil to score fast.",
                ),
            )),
        GameMeta("flappy", "Flappy Pulse", "Tap to glide through neon pipes",
            Color(0xFF1B1340), Color(0xFFFFD166), listOf("Classic", "Survival", "Ghost"),
            GameTutorial(
                howTo = listOf(
                    "The bird hovers in place until you tap — take a breath, then go.",
                    "Each tap gives the bird a quick upward boost.",
                    "Stop tapping and gravity pulls you back down.",
                    "Slip through the gap between every pipe to score.",
                ),
                controls = listOf(
                    GameControl("Tap", "Flap up"),
                ),
                features = listOf(
                    "Fixed 60 Hz physics — same feel on every screen",
                    "Hover-to-start so you never spawn already falling",
                    "Animated bird with tilt, wing, and beak",
                    "Best score saved",
                ),
                tips = listOf(
                    "Tap small bursts — long taps rocket you into the ceiling.",
                    "Aim for the middle of the gap, not the edge.",
                ),
            )),
        GameMeta("brick", "Brick Crusher", "Smash, drop power-ups, advance",
            Color(0xFF2B0F4F), Color(0xFFFF6BD6), listOf("Classic", "Challenge"),
            GameTutorial(
                howTo = listOf(
                    "Drag to move the paddle left and right.",
                    "Bounce the ball into the bricks above to break them.",
                    "Don’t let the ball fall past your paddle.",
                    "Clear all bricks to advance to the next level.",
                ),
                controls = listOf(
                    GameControl("Drag", "Move paddle"),
                ),
                features = listOf(
                    "Angle-based paddle deflection",
                    "Fixed-timestep physics",
                    "Best score saved",
                ),
                tips = listOf(
                    "Hit the ball with the edge of the paddle to angle it.",
                    "Aim for tunnels to trap the ball above the bricks.",
                ),
            )),
        GameMeta("space", "Space Hunter", "Dodge, shoot, beat the boss",
            Color(0xFF02123A), Color(0xFF7CE7FF), listOf("Classic", "Survival", "Boss"),
            GameTutorial(
                howTo = listOf(
                    "Drag to fly your ship across the bottom of the screen.",
                    "You auto-fire — focus on dodging incoming bullets.",
                    "Destroy enemies to score; survive escalating waves.",
                ),
                controls = listOf(
                    GameControl("Drag", "Move ship"),
                ),
                features = listOf(
                    "Auto-fire so you can focus on movement",
                    "Escalating enemy waves",
                    "Best score saved",
                ),
                tips = listOf(
                    "Stay in motion — never park.",
                    "Pick off enemies on the edges first.",
                ),
            )),
        GameMeta("car", "Lane Rush", "Hold for nitro. Near miss = bonus",
            Color(0xFF1A0F2E), Color(0xFFFFB347), listOf("Classic", "Time", "Challenge"),
            GameTutorial(
                howTo = listOf(
                    "Tap left or right to switch lanes.",
                    "Survive as long as you can — speed ramps up over time.",
                    "Each car you pass adds to your score.",
                ),
                controls = listOf(
                    GameControl("Tap left/right", "Change lane"),
                ),
                features = listOf(
                    "Fixed 60 Hz physics so 120 Hz feels fair",
                    "Best distance saved",
                    "Smooth lane snap",
                ),
                tips = listOf(
                    "Don’t hover in the middle — commit to one side.",
                    "Watch two rows ahead, not the car in front.",
                ),
            )),
        GameMeta("color", "Color Switch", "Pass only matching colors",
            Color(0xFF101828), Color(0xFFFF5C8A), listOf("Classic", "Survival"),
            GameTutorial(
                howTo = listOf(
                    "The ball bounces — tap to give it an upward burst.",
                    "Pass only through obstacles whose colour matches your ball.",
                    "Hit a colour-swap star to change your ball’s colour.",
                    "Touching the wrong colour ends the run.",
                ),
                controls = listOf(
                    GameControl("Tap", "Bounce up"),
                ),
                features = listOf(
                    "Fixed-timestep collisions",
                    "Best score saved",
                    "Smooth colour transitions",
                ),
                tips = listOf(
                    "Short bursts give finer control.",
                    "Watch obstacle rotation, not your ball.",
                ),
            )),
        GameMeta("react", "Reaction", "Tap as fast as humanly possible",
            Color(0xFF051F2A), Color(0xFF36F1CD), listOf("Classic"),
            GameTutorial(
                howTo = listOf(
                    "Wait for the screen to flash green.",
                    "Tap as fast as you can the moment it changes.",
                    "Tapping early counts as a fault — patience wins.",
                ),
                controls = listOf(
                    GameControl("Tap", "React"),
                ),
                features = listOf(
                    "Best (lowest) reaction time saved",
                    "False-start detection",
                ),
                tips = listOf(
                    "Don’t blink — focus on the centre.",
                    "200–300 ms is typical; under 200 is excellent.",
                ),
            )),
        GameMeta("g2048", "2048", "Slide tiles, double up",
            Color(0xFF22150B), Color(0xFFFFA94D), listOf("Classic", "Time"),
            GameTutorial(
                howTo = listOf(
                    "Swipe to slide every tile in that direction.",
                    "When two tiles with the same number touch, they merge.",
                    "A new 2 or 4 appears each move.",
                    "Game over when no moves are possible.",
                ),
                controls = listOf(
                    GameControl("Swipe", "Slide tiles"),
                ),
                features = listOf(
                    "4×4 grid with smooth merge animation",
                    "Best score saved",
                    "Combo merges chain in one move",
                ),
                tips = listOf(
                    "Pick a corner and lock your largest tile there.",
                    "Plan two moves ahead.",
                ),
            )),
        GameMeta("memory", "Memory Match", "Find all the pairs",
            Color(0xFF0F1A2E), Color(0xFF60A5FA), listOf("Classic", "Time"),
            GameTutorial(
                howTo = listOf(
                    "Tap a card to flip it face-up.",
                    "Flip a second card — if they match, they stay open.",
                    "If they don’t match, both flip back. Remember!",
                    "Clear the whole board in as few moves as possible.",
                ),
                controls = listOf(
                    GameControl("Tap", "Flip card"),
                ),
                features = listOf(
                    "Move counter and timer",
                    "Best move count saved",
                    "Smooth 3D card flip",
                ),
                tips = listOf(
                    "Spread your first flips to build a mental map.",
                    "Stay calm — looking longer helps you remember.",
                ),
            )),
        GameMeta("slide", "Sliding Puzzle", "Order tiles 1 to 15",
            Color(0xFF1F1A2E), Color(0xFFB28DFF), listOf("Classic", "Time"),
            GameTutorial(
                howTo = listOf(
                    "Tap a tile next to the empty space to slide it in.",
                    "Arrange numbers 1 → 15 from top-left to bottom-right.",
                    "Fewer moves = better score.",
                ),
                controls = listOf(
                    GameControl("Tap", "Slide tile"),
                ),
                features = listOf(
                    "Move counter",
                    "Best (lowest) move count saved",
                    "Solvability guaranteed",
                ),
                tips = listOf(
                    "Solve top row, then left column, then a 2×2.",
                    "Almost any layout is solvable — don’t panic.",
                ),
            )),
        GameMeta("dice", "Dice Battle", "Beat the AI in 5 rounds",
            Color(0xFF1A1A1A), Color(0xFFEE5253), listOf("Classic"),
            GameTutorial(
                howTo = listOf(
                    "Roll the dice to add the result to your turn total.",
                    "Bank to add your turn total to your score and pass turn.",
                    "If you roll a 1, you lose the whole turn total.",
                    "First to 50 wins.",
                ),
                controls = listOf(
                    GameControl("Roll / Bank", "Choose action"),
                ),
                features = listOf(
                    "Smart AI opponent",
                    "Best win streak saved",
                    "Animated dice",
                ),
                tips = listOf(
                    "Bank around 20 — anything more and the 1 hunts you.",
                    "Behind: push further. Ahead: bank earlier.",
                ),
            )),
        GameMeta("aim", "Aim Trainer", "Pop targets before they vanish",
            Color(0xFF101828), Color(0xFFEF4444), listOf("Classic", "Time"),
            GameTutorial(
                howTo = listOf(
                    "A target appears at a random spot.",
                    "Tap inside the target as fast as you can.",
                    "Misses cost you points; precision pays.",
                ),
                controls = listOf(
                    GameControl("Tap", "Hit target"),
                ),
                features = listOf(
                    "Accuracy and reaction tracking",
                    "Best score saved",
                    "Targets shrink as you improve",
                ),
                tips = listOf(
                    "Move your finger before you fully aim.",
                    "Don’t chase a missed target — reset.",
                ),
            )),
        GameMeta("runner", "Dino Dash · Extinction Run", "Tap to jump, swipe to crouch, run forever",
            Color(0xFF1F1B16), Color(0xFFFFD166), listOf("Classic", "TimeTrial", "BossRun", "Mission"),
            GameTutorial(
                howTo = listOf(
                    "Your runner moves forward automatically.",
                    "Tap to jump over obstacles.",
                    "Speed increases the longer you survive.",
                ),
                controls = listOf(
                    GameControl("Tap", "Jump"),
                ),
                features = listOf(
                    "Fixed-timestep physics",
                    "Procedural obstacles",
                    "Best distance saved",
                ),
                tips = listOf(
                    "Tap a hair earlier than feels right.",
                    "When speed cranks up, jump short obstacles.",
                ),
            )),
        GameMeta("math", "Quick Math", "Solve. Quickly.",
            Color(0xFF0E1F1B), Color(0xFF34D399), listOf("Classic", "Time"),
            GameTutorial(
                howTo = listOf(
                    "A math problem appears on screen.",
                    "Tap the correct answer from the choices.",
                    "Correct answers add time; wrong ones cost you.",
                ),
                controls = listOf(
                    GameControl("Tap", "Pick an answer"),
                ),
                features = listOf(
                    "Mixed +, −, ×, ÷ problems",
                    "Difficulty ramps with score",
                    "Best score saved",
                ),
                tips = listOf(
                    "Trust your gut on small numbers.",
                    "On multiplication, estimate first.",
                ),
            )),
        GameMeta("tap", "Tap Pattern", "Repeat the lit sequence",
            Color(0xFF1B1040), Color(0xFFFFD166), listOf("Classic", "Survival"),
            GameTutorial(
                howTo = listOf(
                    "The pads light up in a sequence — watch carefully.",
                    "Tap the same pads in the same order.",
                    "Each round adds one more step.",
                    "One mistake ends the run.",
                ),
                controls = listOf(
                    GameControl("Tap", "Tap a pad"),
                ),
                features = listOf(
                    "Sequence grows infinitely",
                    "Best (longest) sequence saved",
                    "Audio + colour cue",
                ),
                tips = listOf(
                    "Hum the rhythm in your head.",
                    "Don’t look ahead until you’ve tapped the current pad.",
                ),
            )),
    )

    fun byId(id: String): GameMeta? = all.firstOrNull { it.id == id }
}
