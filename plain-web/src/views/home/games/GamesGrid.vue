<template>
  <div class="grid-wrap">
    <div class="hero">
      <div>
        <div class="hero-eyebrow">Welcome back</div>
        <div class="hero-title">Play 15 mini-games</div>
        <div class="hero-sub">Quick fun. Beat your best score. Earn coins.</div>
      </div>
      <div class="coins-box">
        <i-lucide:coins />
        <span>{{ store.coins }}</span>
      </div>
    </div>

    <div class="cards">
      <div
        v-for="g in games"
        :key="g.id"
        class="game-card"
        :style="{ background: g.gradient }"
        @click="emit('play', g.id)"
      >
        <div class="gc-top">
          <div class="gc-icon">{{ g.icon }}</div>
          <div v-if="g.badge" class="gc-badge">{{ g.badge }}</div>
        </div>
        <div class="gc-body">
          <div class="gc-name">{{ g.name }}</div>
          <div class="gc-desc">{{ g.desc }}</div>
        </div>
        <div class="gc-foot">
          <div class="gc-stat">
            <i-lucide:star /> {{ g.rating }}
          </div>
          <div class="gc-stat">
            <i-lucide:trophy /> {{ store.bestOf(g.id) }}
          </div>
          <div class="play-btn"><i-lucide:play /></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useGamesStore } from './gamesStore'
import { gameList } from './registry'

const emit = defineEmits<{ play: [string] }>()
const store = useGamesStore()
const games = gameList
</script>

<style lang="scss" scoped>
.grid-wrap {
  padding: 16px;
  overflow-y: auto;
  height: 100%;
}
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.18), rgba(168, 85, 247, 0.18));
  border: 1px solid rgba(99, 102, 241, 0.18);
  border-radius: 20px;
  padding: 20px 22px;
  margin-bottom: 18px;
}
.hero-eyebrow {
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--md-sys-color-on-surface-variant);
  margin-bottom: 4px;
}
.hero-title {
  font-size: 1.45rem;
  font-weight: 800;
  color: var(--md-sys-color-on-surface);
}
.hero-sub {
  margin-top: 4px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.92rem;
}
.coins-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(250, 204, 21, 0.18);
  color: #b45309;
  font-weight: 700;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 1rem;
}
.cards {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  padding-bottom: 32px;
}
.game-card {
  position: relative;
  border-radius: 18px;
  padding: 16px;
  color: #fff;
  cursor: pointer;
  min-height: 170px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.22);
}
.game-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(120% 60% at 100% 0%, rgba(255, 255, 255, 0.18), transparent 60%);
  pointer-events: none;
}
.game-card:hover {
  transform: translateY(-3px) scale(1.01);
  box-shadow: 0 14px 28px rgba(0, 0, 0, 0.3);
}
.gc-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.gc-icon {
  font-size: 2rem;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.25));
}
.gc-badge {
  background: rgba(0, 0, 0, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.gc-name {
  font-weight: 800;
  font-size: 1.05rem;
}
.gc-desc {
  font-size: 0.78rem;
  opacity: 0.92;
  margin-top: 2px;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.gc-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 0.78rem;
}
.gc-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.18);
  border-radius: 999px;
  padding: 4px 8px;
}
.play-btn {
  margin-left: auto;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
</style>
