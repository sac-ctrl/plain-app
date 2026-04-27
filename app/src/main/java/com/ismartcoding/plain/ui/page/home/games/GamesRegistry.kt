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
        GameMeta("brick", "Crusher · Neon Fracture", "Precision paddle, elemental bricks, ghost replay",
            Color(0xFF2B0F4F), Color(0xFFFF6BD6), listOf("Campaign", "Endless", "Boss", "Daily"),
            GameTutorial(
                howTo = listOf(
                    "Drag to move the paddle — it follows your finger.",
                    "Bounce the ball into bricks above to break them.",
                    "Catch power-up orbs that drop from broken bricks.",
                    "Clear all breakable bricks to advance.",
                ),
                controls = listOf(
                    GameControl("Drag", "Move paddle"),
                    GameControl("Two-finger hold", "Precision (slower paddle)"),
                ),
                features = listOf(
                    "240 Hz fixed-timestep physics",
                    "Angle-based paddle deflection with paddle-velocity spin",
                    "8 worlds (Factory · Crystal · Temple · Sanctum · Cosmos · Abyss · Inferno · Zero)",
                    "Elemental bricks (fire / ice / electric), unbreakable, boss",
                    "3 power-up tiers + 3 debuffs",
                    "Adaptive difficulty + skill rank E–S",
                    "Trajectory ghost line, slow-mo on last brick",
                    "Live calibration, post-loss heatmap, ghost replay, daily seed",
                ),
                tips = listOf(
                    "Hit with the paddle edge to angle the ball sideways.",
                    "Sweep the paddle as the ball lands — it adds spin.",
                    "Save shields and screen-nukes for boss waves.",
                ),
            )),
        GameMeta("space", "Space Hunter · Nebula Strike", "Drag to fly. Auto-fire. Beat 5 acts of bosses.",
            Color(0xFF02123A), Color(0xFF7CE7FF), listOf("Campaign", "Endless", "BossRush", "Daily"),
            GameTutorial(
                howTo = listOf(
                    "Drag anywhere to fly your ship 1:1 with your finger.",
                    "You auto-fire 5 shots/sec; double-tap to drop an overdrive bomb.",
                    "Long press to charge a railgun shot that pierces enemies.",
                    "Grab power-ups: shield, slow-time, magnet, +1 life, double damage.",
                    "Each act is 10 waves + a multi-phase boss. Beat 5 to clear the campaign.",
                ),
                controls = listOf(
                    GameControl("Drag", "Move ship 1:1"),
                    GameControl("Tap halves", "Step left/right (assist)"),
                    GameControl("Double tap", "Overdrive bomb"),
                    GameControl("Long press", "Charged railgun"),
                    GameControl("Tilt", "Optional gyro steering"),
                ),
                features = listOf(
                    "15+ enemy types: swarmer, tank, sniper, mothership, drone, mine, bomber, weaver, splitter, shielder, kamikaze, turret, elite, miniboss, boss",
                    "Multi-phase bosses with weak points and pattern attacks",
                    "5 acts × 10 waves + boss · Endless · Boss Rush · Daily seed",
                    "Adaptive difficulty (death streak softens, win streak hardens)",
                    "Roguelite meta: ship upgrades, perks, permanent unlocks",
                    "Power-ups: shield, slow-time, magnet, +1 life, double damage, nuke",
                    "4 hulls: Interceptor / Brawler / Stealth / Healer",
                    "Drag-test live calibration + tilt calibration",
                    "Ghost run overlay against your best",
                    "Replay last run with slow-mo",
                    "Post-run heatmap, accuracy, danger enemy, tip",
                    "Voice announcer + boss-spawn triple haptic",
                    "Accessibility: colorblind, reduced motion, high contrast, assist, auto-fire, larger hitbox, one-handed, big HUD, battery saver",
                    "Daily seeded run with shareable code",
                    "Login streak rewards",
                ),
                tips = listOf(
                    "Stay in motion — parking gets you sniped.",
                    "Bait swarmers, then sweep them in one pass.",
                    "Save overdrive for elite waves and boss phase 2.",
                    "Magnet vacuums power-ups — pop it before tank waves.",
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
        GameMeta("runner", "Lane Rush · Velocity Shift", "Weave between lanes, dodge traffic, beat the boss truck",
            Color(0xFF0B0F1A), Color(0xFFFF7A1A), listOf("Endless", "Stage", "Time", "Mission"),
            GameTutorial(
                howTo = listOf(
                    "Your vehicle accelerates forward on its own.",
                    "Swipe left or right to change lanes; swipe up to jump a low wall; swipe down to slide under a beam.",
                    "Grab pickups: shield, slow-mo, magnet, double points.",
                    "Every 2000m a boss truck appears — find the lane gap to beat it.",
                ),
                controls = listOf(
                    GameControl("Swipe ←/→", "Change lane"),
                    GameControl("Swipe ↑", "Jump"),
                    GameControl("Swipe ↓", "Slide"),
                    GameControl("Tap halves", "Steer alt"),
                    GameControl("Tilt", "Optional steering"),
                ),
                features = listOf(
                    "3 / 4 / 5 lane layouts",
                    "20+ obstacle types incl. boss trucks",
                    "Pickups: shield, slow-mo, magnet, ×2",
                    "Endless / Stage / Time / Mission modes",
                    "Adaptive difficulty (MMR)",
                    "20 unlockable vehicle skins",
                    "5 road themes that rotate",
                    "Calibration sheet for tilt + tap zones",
                    "Ghost replay of your best run",
                    "Post-run heatmap + reaction graph",
                    "Daily seeded run with shareable code",
                    "Reduced motion / colorblind / one-handed",
                ),
                tips = listOf(
                    "Pre-commit: pick your lane before you can see clearly.",
                    "Save shield for boss waves and elite drones.",
                    "Slow-mo stretches reaction windows — pop it in dense traffic.",
                    "Magnet vacuums coins two lanes over.",
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
