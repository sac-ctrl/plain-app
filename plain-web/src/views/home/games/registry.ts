import { defineAsyncComponent, type Component } from 'vue'
import type { GameMode } from './gamesStore'

export interface GameDef {
  id: string
  name: string
  icon: string
  desc: string
  badge?: string
  rating: number
  gradient: string
  modes?: GameMode[]
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
    loader: () => defineAsyncComponent(() => import('./impl/FlappyBird.vue')),
  },
  {
    id: 'snake',
    name: 'Snake Neon',
    icon: '🐍',
    desc: 'Eat. Grow. Don\u2019t hit yourself.',
    badge: 'Hot',
    rating: 4.7,
    gradient: 'linear-gradient(135deg, #10b981, #14b8a6)',
    modes: ['classic', 'time', 'challenge'],
    loader: () => defineAsyncComponent(() => import('./impl/SnakeGame.vue')),
  },
  {
    id: '2048',
    name: '2048 Pro',
    icon: '🔢',
    desc: 'Slide tiles. Reach 2048.',
    rating: 4.6,
    gradient: 'linear-gradient(135deg, #6366f1, #3b82f6)',
    modes: ['classic', 'time'],
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
    loader: () => defineAsyncComponent(() => import('./impl/Reaction.vue')),
  },
  {
    id: 'memory',
    name: 'Memory Flip',
    icon: '🧠',
    desc: 'Match all pairs. Combo bonuses.',
    rating: 4.5,
    gradient: 'linear-gradient(135deg, #ec4899, #a855f7)',
    modes: ['classic', 'time'],
    loader: () => defineAsyncComponent(() => import('./impl/MemoryGame.vue')),
  },
  {
    id: 'shooter',
    name: 'Space Shooter',
    icon: '🔫',
    desc: 'Blast aliens. Beat the boss.',
    badge: 'New',
    rating: 4.7,
    gradient: 'linear-gradient(135deg, #1e293b, #6366f1)',
    modes: ['classic', 'survival'],
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
    loader: () => defineAsyncComponent(() => import('./impl/DiceBattle.vue')),
  },
  {
    id: 'brick',
    name: 'Brick Breaker',
    icon: '🧱',
    desc: 'Smash bricks. Catch power-ups.',
    rating: 4.6,
    gradient: 'linear-gradient(135deg, #f97316, #facc15)',
    modes: ['classic', 'survival'],
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
    loader: () => defineAsyncComponent(() => import('./impl/AimTrainer.vue')),
  },
  {
    id: 'runner',
    name: 'Endless Runner',
    icon: '🏃',
    desc: 'Jump and slide forever.',
    rating: 4.6,
    gradient: 'linear-gradient(135deg, #0ea5e9, #6366f1)',
    modes: ['classic', 'survival'],
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
    loader: () => defineAsyncComponent(() => import('./impl/QuickMath.vue')),
  },
  {
    id: 'tap',
    name: 'Tap Pattern',
    icon: '🧩',
    desc: 'Repeat the sequence.',
    rating: 4.4,
    gradient: 'linear-gradient(135deg, #6366f1, #ec4899)',
    modes: ['classic', 'challenge'],
    loader: () => defineAsyncComponent(() => import('./impl/TapPattern.vue')),
  },
]

export function getGame(id: string): GameDef | undefined {
  return gameList.find((g) => g.id === id)
}
