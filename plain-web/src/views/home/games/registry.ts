import { defineAsyncComponent, type Component } from 'vue'
import type { GameMode } from './gamesStore'

export interface GameTutorial {
  tagline: string
  howTo: string[]
  controls: { key: string; action: string }[]
  features: string[]
  tips: string[]
}

export interface GameDef {
  id: string
  name: string
  icon: string
  desc: string
  badge?: string
  rating: number
  gradient: string
  modes?: GameMode[]
  tutorial: GameTutorial
  loader: () => Component
}

export const gameList: GameDef[] = [
  {
    id: 'flappy',
    name: 'Flappy Bird',
    icon: '🐦',
    desc: 'Tap to flap. Keep flying.',
    badge: 'Trending',
    rating: 4.8,
    gradient: 'linear-gradient(135deg, #f59e0b, #ef4444)',
    modes: ['classic', 'survival'],
    tutorial: {
      tagline: 'Tap to flap. Slip through every gap. Beat your best.',
      howTo: [
        'The bird hovers in place until you tap — take a breath, then go.',
        'Each tap gives the bird a quick upward boost.',
        'Stop tapping and gravity pulls you back down.',
        'Slip through the gap between every pipe to score a point.',
      ],
      controls: [
        { key: 'Tap / Click', action: 'Flap up' },
        { key: 'Space / W / ↑', action: 'Flap up (keyboard)' },
      ],
      features: [
        'Fixed 60 Hz physics — same feel on 60 / 120 / 144 Hz screens',
        'Hover-to-start so you never spawn already falling',
        'Animated bird with tilt, wing, and beak',
        'Best score saved per difficulty',
      ],
      tips: [
        'Tap small bursts — long taps will rocket you into the ceiling.',
        'Aim for the middle of each gap, not the edge.',
        'Easy mode has bigger gaps and gentler gravity if you’re learning.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/FlappyBird.vue')),
  },
  {
    id: 'snake',
    name: 'Viper · Evolved',
    icon: '🐍',
    desc: 'Snake reimagined: themes, skins, combos, daily seeds, ghost replay.',
    badge: 'New',
    rating: 4.9,
    gradient: 'linear-gradient(135deg, #10b981, #14b8a6)',
    modes: ['classic', 'time', 'challenge'],
    tutorial: {
      tagline: 'Glide. Combo. Evolve. The classic, fully reimagined.',
      howTo: [
        'Steer with arrow keys, WASD, swipes, the on-screen D-pad, or device tilt.',
        'Eat coloured food (normal, golden, poison, timed, magnetic) to grow and chain combo multipliers.',
        'Avoid your own tail and the walls — or turn on wrap-around in settings.',
        'Open the ⚙ menu in-game for movement style, themes, skins, trails and accessibility options.',
      ],
      controls: [
        { key: '↑ ↓ ← →', action: 'Turn' },
        { key: 'W A S D', action: 'Turn (alt)' },
        { key: 'Swipe', action: 'Turn (touch / smooth steering)' },
        { key: 'Tilt', action: 'Lateral steering (gyro)' },
        { key: 'Z', action: 'Undo last move' },
        { key: 'Esc', action: 'Open settings' },
      ],
      features: [
        'Two movement modes: tick-based grid or fluid vector steering.',
        'Five food types and five power-ups (phase, magnet, shrink, slow, x2).',
        'Adaptive combo multiplier and hidden MMR-based difficulty.',
        '4 themes (Cyber Grid, Zen Garden, Toxic Waste, Void) and 7 unlockable skins.',
        '5 trail effects: sparkle, fire, smoke, rainbow, none.',
        'Daily challenge seed and shareable “beat my seed” links.',
        'Post-run heatmap, reaction graph and personal coaching tip.',
        'Ghost-replay overlay of your previous best run.',
        'Live swipe-sensitivity calibration in 5 swipes.',
        'Accessibility: colourblind palettes, reduced motion, high contrast, left-hand UI, battery saver, voice hints.',
        'Adaptive layered music — bass and beat fade in with your score.',
      ],
      tips: [
        'You can’t reverse 180° — plan your turns early.',
        'Chain food in straight lines to stack combo (up to x5).',
        'Golden food gives x3 growth; poison shrinks you and inverts controls for 4s.',
        'In Challenge mode every day uses the same seed — compare with friends.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/SnakeGame.vue')),
  },
  {
    id: '2048',
    name: '2048: Merger Ascension',
    icon: '🔢',
    desc: '5 modes · 4–6 grids · skins, themes, daily seed, post-game heatmap.',
    rating: 4.8,
    gradient: 'linear-gradient(135deg, #6366f1, #3b82f6)',
    modes: ['classic', 'time', 'survival', 'challenge', 'daily'],
    tutorial: {
      tagline: 'Climb to 2048 — then keep climbing. Six grids, five modes, full mastery.',
      howTo: [
        'Swipe, arrow-key or tilt to slide every tile in that direction.',
        'When two tiles with the same number touch, they merge into one of double value.',
        'Each mode plays differently: Classic to 2048, Timed dash, Endless expanding grid, Challenge puzzles, Daily seed.',
        'Earn power-ups: undo, shuffle, lowest-tile clear. Refilled daily.',
      ],
      controls: [
        { key: '↑ ↓ ← →', action: 'Slide tiles' },
        { key: 'Swipe / Tilt', action: 'Slide tiles (touch / gyro)' },
        { key: 'Z', action: 'Undo last move' },
      ],
      features: [
        '5 modes: Classic · Timed (60 s) · Endless (grid expands at 2048) · Challenge puzzles · Daily seed',
        '4×4, 5×5, 6×6 grids — unlock with milestones',
        '4 themes (neon · paper · glass · dark) and 6 tile skins (classic · digital · neon · marble · wood · crystal)',
        'Merge animation, bouncy slides, post-game heat-map, merge-frequency chart, AI tip',
        'On-screen arrows, tilt control, swipe-distance slider, calibration wizard',
        'Accessibility: reduced motion, high contrast, one-handed, battery-saver, colourblind palettes, font scale',
        'Assists: merge hints, auto-undo on losing swipe, slower animations, next-tile preview',
        'Adaptive difficulty, daily seed, full settings persistence',
      ],
      tips: [
        'Pick a corner and keep your largest tile locked there.',
        'Chain three merges in one swipe for a combo bonus.',
        'In Endless, save your shuffle for after the grid expands to 5×5.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/Game2048.vue')),
  },
  {
    id: 'cardodge',
    name: 'Car Dodge',
    icon: '🚗',
    desc: 'Dodge traffic. Hold for nitro.',
    rating: 4.5,
    gradient: 'linear-gradient(135deg, #ef4444, #f97316)',
    modes: ['classic', 'survival'],
    tutorial: {
      tagline: 'Three lanes, faster traffic, one life. Pick your line.',
      howTo: [
        'Slide left/right to change lane.',
        'Survive as long as you can — speed ramps up over time.',
        'Each car you pass adds to your score.',
      ],
      controls: [
        { key: '← →', action: 'Change lane' },
        { key: 'A / D', action: 'Change lane (alt)' },
        { key: 'Tap left/right', action: 'Change lane (touch)' },
      ],
      features: [
        'Fixed 60 Hz physics so 120 Hz screens feel fair',
        'Best distance saved',
        'Smooth lane snap',
      ],
      tips: [
        'Don’t hover in the middle lane — commit to one side.',
        'Watch two rows ahead, not the car in front of you.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/CarDodge.vue')),
  },
  {
    id: 'reaction',
    name: 'Reaction',
    icon: '🎯',
    desc: 'Tap as fast as you can.',
    rating: 4.4,
    gradient: 'linear-gradient(135deg, #06b6d4, #3b82f6)',
    modes: ['classic', 'time'],
    tutorial: {
      tagline: 'How fast is your finger? Down to the millisecond.',
      howTo: [
        'Wait for the screen to flash green.',
        'Tap as fast as you can the moment it changes.',
        'Your reaction time in milliseconds is recorded.',
        'Tapping early counts as a fault — patience wins.',
      ],
      controls: [
        { key: 'Tap / Click / Space', action: 'React' },
      ],
      features: [
        'Best (lowest) reaction time saved',
        'False-start detection',
        '5-round average score',
      ],
      tips: [
        'Don’t blink — focus on the centre of the screen.',
        'A typical human reaction is 200–300 ms; under 200 is excellent.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/Reaction.vue')),
  },
  {
    id: 'memory',
    name: 'Recall · Twin Echo',
    icon: '🧠',
    desc: '6 themes, 5 modes, power-ups, daily seed',
    badge: 'Deep',
    rating: 4.7,
    gradient: 'linear-gradient(135deg, #ec4899, #a855f7)',
    modes: ['classic', 'time'],
    tutorial: {
      tagline: 'Flip two cards. Find the twin. Beat the clock.',
      howTo: [
        'Tap a card to flip it face-up.',
        'Flip a second card — if they match, they stay open.',
        'If they don’t match, both flip back. Remember where they were!',
        'Clear the whole board in as few moves as possible.',
      ],
      controls: [
        { key: 'Tap / Click', action: 'Flip card' },
        { key: 'Long press', action: 'Confirm flip (motor accessibility)' },
      ],
      features: [
        'Adjustable grid 2×2 → 6×6',
        '6 themes (Animals, Space, Food, Geometry, Fantasy, Nostalgia)',
        '5 modes (Classic, Timed, Memory Lane, Mismatch Penalty, Zen)',
        '6 unlockable card backs',
        '3 power-ups: Reveal, Shuffle, Freeze (refilled daily)',
        'Adaptive hints after 3 misses in a row',
        'Daily / shareable seed for fixed boards',
        'Memory-span calibration test suggests grid size',
        'Post-game heatmap, pace chart, efficiency %, star rating',
        'Flip replay viewer',
        'Colourblind shape tags, high contrast, reduced motion',
        'Assists: auto-match, longer reveal, larger touch zones',
        'One-handed layout, font scale slider, battery saver',
      ],
      tips: [
        'Spread your first flips so you build a mental map fast.',
        'Hold a Reveal for the last 4 cards — that’s where mistakes pile up.',
        'In Mismatch Penalty mode, slow down — every miss adds two cards.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/MemoryGame.vue')),
  },
  {
    id: 'shooter',
    name: 'Space Hunter · Nebula Strike',
    icon: '🚀',
    desc: 'Boss waves, perks, ships, daily seed',
    badge: 'Deep',
    rating: 4.9,
    gradient: 'linear-gradient(135deg, #1e293b, #6366f1)',
    modes: ['classic', 'survival'],
    tutorial: {
      tagline: 'Drag to fly. Auto-fire. Beat the boss.',
      howTo: [
        'Drag (or use arrow keys) to fly your ship.',
        'Your ship auto-fires — pick a perk between waves and hunt boss ships.',
        'Grab power-ups: weapon, shield, life, damage boost, slow, energy, bomb.',
        'Build energy → 2-finger tap (or Q / right-click) for an Overdrive missile salvo.',
      ],
      controls: [
        { key: 'Drag', action: 'Move ship' },
        { key: '2-finger tap / Q / right-click', action: 'Overdrive (homing salvo)' },
        { key: 'Long press', action: 'Charged shot (×3 damage)' },
        { key: '← →', action: 'Move ship (keyboard)' },
      ],
      features: [
        '6 enemy types: swarmer, tank, sniper, mothership, elite, spawnling',
        'Multi-phase boss every 3 waves with weak-point and HP bar',
        '7 power-up types with magnetic pickup',
        'Combo multiplier (×2 → ×10) with i-frames after every hit',
        '4 ship hulls (Interceptor, Brawler, Stealth, Healer) with unlock paths',
        '5 persistent ship upgrades bought with coins',
        '7 per-run perks rolled at the start of each life',
        'Adaptive difficulty (hidden MMR) and elite enemies above rank 70',
        'Daily seed + shared "Beat my seed" link',
        'Drag-test calibration auto-tunes sensitivity',
        'Settings: gyro, voice, layered music, haptics, reduced motion, high contrast, battery saver, one-handed, three assists',
        'Post-run mission report: accuracy, kills, best combo, death heatmap, damage chart, replay button',
      ],
      tips: [
        'Stay in motion — every parked second is a hit.',
        'Save Overdrive for the boss\'s ring patterns.',
        'Pick the Magnet perk on Healer for a vacuum-pickup build.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/SpaceShooter.vue')),
  },
  {
    id: 'sliding',
    name: 'Sliding Puzzle',
    icon: '🧩',
    desc: 'Sort the tiles in order.',
    rating: 4.3,
    gradient: 'linear-gradient(135deg, #0ea5e9, #14b8a6)',
    modes: ['classic'],
    tutorial: {
      tagline: 'Slide the numbered tiles into order. Mind the gap.',
      howTo: [
        'Tap a tile next to the empty space to slide it into the gap.',
        'Arrange numbers 1 → 15 from top-left to bottom-right.',
        'Fewer moves = better score.',
      ],
      controls: [
        { key: 'Tap / Click', action: 'Slide tile' },
      ],
      features: [
        'Move counter',
        'Best (lowest) move count saved',
        'Solvability guaranteed every shuffle',
      ],
      tips: [
        'Solve the top row first, then the left column, then a 2×2 at the end.',
        'Don’t panic — almost any layout is solvable.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/SlidingPuzzle.vue')),
  },
  {
    id: 'dice',
    name: 'Dice Battle',
    icon: '🎲',
    desc: 'Beat the AI to 50 points.',
    rating: 4.4,
    gradient: 'linear-gradient(135deg, #a21caf, #db2777)',
    modes: ['classic'],
    tutorial: {
      tagline: 'Push your luck. Bank early or roll for the win.',
      howTo: [
        'Roll the dice to add the result to your turn total.',
        'Bank to add your turn total to your score and pass the turn.',
        'But — if you roll a 1, you lose the whole turn total.',
        'First to 50 points wins.',
      ],
      controls: [
        { key: 'Tap Roll / Bank', action: 'Choose action' },
      ],
      features: [
        'Smart AI opponent',
        'Best win streak saved',
        'Animated dice roll',
      ],
      tips: [
        'Bank around 20 — anything more and the 1 starts hunting you.',
        'When you’re behind, push further; when ahead, bank earlier.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/DiceBattle.vue')),
  },
  {
    id: 'brick',
    name: 'Crusher · Neon Fracture',
    icon: '🧱',
    desc: 'Precision paddle. Elemental bricks. Ghost replay.',
    rating: 4.9,
    gradient: 'linear-gradient(135deg, #7c3aed, #f472b6)',
    modes: ['campaign', 'endless', 'boss', 'daily'],
    tutorial: {
      tagline: 'Bounce. Smash. Don’t let it drop.',
      howTo: [
        'Drag your finger or move the mouse — the paddle binds to your X position.',
        'Use ← → or A/D on a keyboard. Hold Shift for precision (half speed).',
        'Bounce the ball into bricks. Catch power-up orbs that drop.',
        'Clear all breakable bricks to advance.',
      ],
      controls: [
        { key: 'Mouse / Drag', action: 'Move paddle (absolute)' },
        { key: '← → · A/D', action: 'Move paddle with acceleration' },
        { key: 'Shift / RT', action: 'Precision mode (half speed)' },
        { key: 'Two-finger hold', action: 'Precision mode (touch)' },
        { key: 'Settings ⚙', action: 'Sensitivity, ghost line, assists, accessibility, paddle/trail/arena' },
      ],
      features: [
        '240 Hz fixed-timestep simulation, framerate-independent',
        'Angle-based paddle deflection with paddle-velocity spin',
        'Trajectory ghost line, slow-mo on last brick',
        '8 worlds (Factory · Crystal · Temple · Sanctum · Cosmos · Abyss · Inferno · Zero) with unique brick mechanics',
        'Elemental bricks (fire heat-wave, ice slow, electric chain), unbreakable, boss with weak points',
        'Tier 1/2/3 power-ups (longer/multi/slow/sticky/laser, wrap/gravity/shield, nuke/shrink) + 3 debuffs',
        'Adaptive difficulty (3 deaths → easier, 5 clears → +bricks/moving)',
        'Skill rank E–S, crystal shards, paddle/trail/arena/music unlocks',
        'Daily seed challenge with shareable code, ghost replay of best run',
        'Live calibration paddle test, post-loss miss-heatmap + efficiency + tip',
        'Adaptive music tempo, voice announcer, audio visualiser rings',
        'Colourblind / high-contrast / reduced-motion / UI scaling / assists',
      ],
      tips: [
        'Hit the ball with the paddle edge to angle it sideways.',
        'Sweep the paddle as the ball lands — it adds spin to your shot.',
        'Save shields and screen-nukes for boss waves.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/BrickBreaker.vue')),
  },
  {
    id: 'aim',
    name: 'Aim Trainer',
    icon: '🎯',
    desc: 'Hit targets. Track accuracy.',
    rating: 4.5,
    gradient: 'linear-gradient(135deg, #ef4444, #b91c1c)',
    modes: ['classic', 'time'],
    tutorial: {
      tagline: 'Tap targets the second they appear. Sharpen your finger.',
      howTo: [
        'A target appears at a random spot.',
        'Tap inside the target as fast as you can.',
        'Misses cost you points; precision pays.',
      ],
      controls: [
        { key: 'Tap / Click', action: 'Hit target' },
      ],
      features: [
        'Accuracy and reaction-time tracking',
        'Best score saved',
        'Targets shrink as you improve',
      ],
      tips: [
        'Move your finger before you fully aim — the brain catches up.',
        'Don’t chase a missed target; reset and breathe.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/AimTrainer.vue')),
  },
  {
    id: 'runner',
    name: 'Lane Rush · Velocity Shift',
    icon: '🏎️',
    desc: 'Lane-based highway dodging at impossible speed.',
    rating: 4.9,
    gradient: 'linear-gradient(135deg, #f59e0b, #ef4444 60%, #6366f1)',
    modes: ['Endless', 'Stage', 'Time', 'Mission'],
    tutorial: {
      tagline: 'One thumb. Three to five lanes. Zero margin for error.',
      howTo: [
        'Your car drives forward automatically — speed ramps with distance.',
        'Swipe (or tap) left / right to flick between lanes instantly.',
        'Optional: enable tilt steering in settings to lean into corners.',
        'Grab pickups — shield, slow-mo, magnet, double score.',
        'Survive every 2000m to face a boss truck blockade with one moving gap.',
      ],
      controls: [
        { key: 'Swipe ← / →', action: 'Change lane (one buffer queued)' },
        { key: 'Tap left / right half', action: 'Lane change (accessibility)' },
        { key: 'Tilt phone', action: 'Optional gyro steering' },
      ],
      features: [
        '3, 4, or 5 adjustable lanes',
        '20+ obstacle types: cars, trucks, police, motos, drones, barrels, elite, boss',
        '4 modes: Endless, Stage Rush (500/1000/2000m), Time Trial 60s, Daily Mission',
        'Adaptive difficulty — adjusts ramp + warning time to your reaction speed',
        'Near-miss combo system + screen-edge glow + tiny time-dilation',
        'Daily seed (everyone runs the same road) + share via QR',
        'Layered synthwave that speeds up with you',
        'Post-run lane heatmap, reaction graph, and crash dissection',
        'Ghost replay of your best run (transparent car)',
        'Calibration sheet — 5 swipes to tune your sensitivity',
        '20+ vehicle skins, road themes (asphalt/rain/night/desert/neon city)',
        'Accessibility: 3 colour-blind modes, reduced motion, high contrast, one-handed UI, battery saver',
      ],
      tips: [
        'Pre-buffer one lane change while a flick animation is mid-air — the second is queued.',
        'Police cars have a fatter hitbox; treat them like a 1.4-lane wide truck.',
        'On boss trucks, watch the gap two seconds out and start drifting early.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/EndlessRunner.vue')),
  },
  {
    id: 'colorswitch',
    name: 'Color Switch',
    icon: '🎨',
    desc: 'Match the color. Pass the ring.',
    rating: 4.4,
    gradient: 'linear-gradient(135deg, #ec4899, #f59e0b)',
    modes: ['classic', 'survival'],
    tutorial: {
      tagline: 'Tap to bounce. Pass through bars matching your colour. Only.',
      howTo: [
        'The ball bounces — tap to give it an upward burst.',
        'You can only pass through obstacles whose colour matches your ball.',
        'Touching the wrong colour ends the run.',
        'Hit a colour-swap star to change your ball’s colour.',
      ],
      controls: [
        { key: 'Tap / Click / Space', action: 'Bounce up' },
      ],
      features: [
        'Fixed-timestep collisions',
        'Best score saved',
        'Smooth colour transitions',
      ],
      tips: [
        'Don’t over-tap — short bursts give finer control.',
        'Watch the rotation timing of each obstacle, not your ball.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/ColorSwitch.vue')),
  },
  {
    id: 'math',
    name: 'Quick Math',
    icon: '🧠',
    desc: 'Solve before time runs out.',
    rating: 4.5,
    gradient: 'linear-gradient(135deg, #14b8a6, #6366f1)',
    modes: ['classic', 'time'],
    tutorial: {
      tagline: 'Brain warm-up — answer before the timer empties.',
      howTo: [
        'A math problem appears on screen.',
        'Tap the correct answer from the choices.',
        'Each correct answer adds time; each wrong one costs you.',
      ],
      controls: [
        { key: 'Tap / Click', action: 'Pick an answer' },
      ],
      features: [
        'Mixed +, −, ×, ÷ problems',
        'Difficulty ramps as you score',
        'Best score saved',
      ],
      tips: [
        'Trust your gut on small numbers — don’t triple-check.',
        'On multiplication, estimate first, then refine.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/QuickMath.vue')),
  },
  {
    id: 'tap',
    name: 'Pattern Pulse',
    icon: '🧩',
    desc: '4 modes · sequence, rhythm, shape, cascade · sound packs, themes, post-game stats.',
    rating: 4.7,
    gradient: 'linear-gradient(135deg, #6366f1, #ec4899)',
    modes: ['sequence', 'rhythm', 'shape', 'cascade'],
    tutorial: {
      tagline: 'Four reflex games in one — sequence memory, rhythm taps, shape tracing, cascade rush.',
      howTo: [
        'Sequence: watch pads light up, repeat in order, sequence grows each round.',
        'Rhythm: tap each falling note as it crosses the line — judged Perfect / Good / Miss.',
        'Shape: trace numbered pads in the right order to complete the shape.',
        'Cascade: tap falling tiles in numerical order before they reach the bottom.',
      ],
      controls: [
        { key: 'Tap / Click', action: 'Tap a pad / note / tile' },
        { key: '1–9', action: 'Tap pad by number (sequence/shape)' },
      ],
      features: [
        '4 modes: Sequence · Rhythm · Shape · Cascade — pick from settings',
        '4, 6, or 9 pads (4×4 grid in rhythm mode)',
        '4 themes (synth · particles · dark · nature) and 4 sound packs (piano · drums · synth · 8-bit)',
        '4 tap effects (ripple · sparks · stars · splash) — unlock with combo milestones',
        'Power-ups: hint, slow-motion, extra life — refilled daily',
        'Adaptive timing window, multi-touch, calibration metronome, daily seed',
        'Post-game pad heat-map, timing histogram, average-reaction stats',
        'Accessibility: reduced motion, high contrast, one-handed, battery-saver, colourblind glyphs',
        'Assists: glow next pad, larger tap radius, wider timing window',
      ],
      tips: [
        'Hum the rhythm in your head — humans remember rhythm easier than colour.',
        'In Rhythm mode, watch the spawn line, not the hit line, to anticipate the tap.',
        'Stack combo bonuses for multiplied scores — keep your accuracy above 90 %.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/TapPattern.vue')),
  },
  {
    id: 'hollow',
    name: 'Hollow Minds',
    icon: '🧠',
    desc: 'Crack the secret colour code · 4 modes · power-ups · daily seed.',
    badge: 'NEW',
    rating: 4.8,
    gradient: 'linear-gradient(135deg, #0B1024, #38FFB1)',
    modes: ['classic', 'daily', 'blitz', 'endless'],
    tutorial: {
      tagline: 'A modern Mastermind — deduce the hidden cipher peg by peg before attempts run out.',
      howTo: [
        'A secret colour code is hidden — guess it within the attempts shown.',
        'Tap colours from the palette to fill your guess slots, then Submit.',
        'Each row gives feedback: black peg = right colour & slot. White peg = right colour, wrong slot.',
        'Use deduction across rows to narrow down the cipher before attempts run out.',
      ],
      controls: [
        { key: 'Tap colour', action: 'Fill the next empty slot' },
        { key: 'Tap slot', action: 'Clear that slot' },
        { key: 'Submit', action: 'Lock in the guess and read feedback' },
        { key: '⚙ Settings', action: 'Switch mode, theme, code length, palette size' },
      ],
      features: [
        '4 modes: Classic · Daily seed · Blitz (90s) · Endless chain',
        'Adjustable code length 3–6 and palette 4–8 colours',
        'Toggle duplicates allowed for harder ciphers',
        'Power-ups: Reveal a peg, Eliminate a wrong colour, Undo last guess (refilled daily)',
        '4 themes (neon · candy · mono · nature) + colourblind glyphs',
        'Daily seed — same code for everyone worldwide each day',
        'Tracks best score, fewest-guesses record, total wins & losses',
        'Difficulty multiplier: Easy ×1, Hard ×2, Insane ×3, plus time bonus in Blitz',
      ],
      tips: [
        'Spend the first guess sampling four different colours to map presence fast.',
        'Treat blacks and whites separately — a black peg fixes a position, a white only hints presence.',
        'Save Reveal for late game when one slot blocks all your deductions.',
      ],
    },
    loader: () => defineAsyncComponent(() => import('./impl/HollowMinds.vue')),
  },
]

export function getGame(id: string): GameDef | undefined {
  return gameList.find((g) => g.id === id)
}
