<template>
  <Teleport to="body">
    <div v-if="game" class="gtm-backdrop" @click.self="emit('close')">
      <div class="gtm-card" :style="{ background: game.gradient }">
        <div class="gtm-header">
          <div class="gtm-icon">{{ game.icon }}</div>
          <div class="gtm-title-wrap">
            <div class="gtm-name">{{ game.name }}</div>
            <div class="gtm-tag">{{ game.tutorial.tagline }}</div>
          </div>
          <button class="gtm-close" @click="emit('close')" aria-label="Close">
            <i-lucide:x />
          </button>
        </div>

        <div class="gtm-body">
          <section class="gtm-section">
            <div class="gtm-h"><i-lucide:book-open /> How to play</div>
            <ol class="gtm-list">
              <li v-for="(step, i) in game.tutorial.howTo" :key="i">{{ step }}</li>
            </ol>
          </section>

          <section class="gtm-section">
            <div class="gtm-h"><i-lucide:gamepad-2 /> Controls</div>
            <div class="gtm-controls">
              <div v-for="(c, i) in game.tutorial.controls" :key="i" class="gtm-ctrl">
                <span class="gtm-key">{{ c.key }}</span>
                <span class="gtm-act">{{ c.action }}</span>
              </div>
            </div>
          </section>

          <section class="gtm-section">
            <div class="gtm-h"><i-lucide:sparkles /> Features</div>
            <ul class="gtm-features">
              <li v-for="(f, i) in game.tutorial.features" :key="i">
                <i-lucide:check-circle-2 /> {{ f }}
              </li>
            </ul>
          </section>

          <section class="gtm-section">
            <div class="gtm-h"><i-lucide:lightbulb /> Pro tips</div>
            <ul class="gtm-list">
              <li v-for="(t, i) in game.tutorial.tips" :key="i">{{ t }}</li>
            </ul>
          </section>
        </div>

        <div class="gtm-footer">
          <button class="gtm-secondary" @click="emit('close')">Maybe later</button>
          <button class="gtm-primary" @click="emit('play', game.id)">
            <i-lucide:play /> Play now
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import type { GameDef } from './registry'
defineProps<{ game: GameDef | null }>()
const emit = defineEmits<{ close: []; play: [string] }>()
</script>

<style lang="scss" scoped>
.gtm-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(6px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  animation: fadeIn 0.18s ease;
}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.gtm-card {
  width: min(560px, 100%);
  max-height: 90vh;
  border-radius: 20px;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.55);
  animation: pop 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
}
@keyframes pop {
  from { transform: scale(0.94); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
.gtm-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 18px 18px 12px;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.18), rgba(0, 0, 0, 0));
}
.gtm-icon {
  font-size: 2.6rem;
  filter: drop-shadow(0 3px 6px rgba(0, 0, 0, 0.35));
}
.gtm-title-wrap { flex: 1; min-width: 0; }
.gtm-name { font-size: 1.3rem; font-weight: 800; letter-spacing: -0.01em; }
.gtm-tag { font-size: 0.85rem; opacity: 0.92; margin-top: 2px; line-height: 1.3; }
.gtm-close {
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.22);
  color: #fff;
  border-radius: 50%;
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
  &:hover { background: rgba(255, 255, 255, 0.28); }
}
.gtm-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 18px 14px;
  background: rgba(0, 0, 0, 0.32);
}
.gtm-section { margin-top: 14px; }
.gtm-h {
  font-size: 0.82rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  opacity: 0.92;
  margin-bottom: 8px;
}
.gtm-list {
  margin: 0;
  padding-left: 22px;
  font-size: 0.92rem;
  line-height: 1.5;
  li { margin-bottom: 4px; }
}
.gtm-controls {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
}
.gtm-ctrl {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 12px;
  padding: 8px 10px;
  font-size: 0.85rem;
}
.gtm-key {
  font-family: ui-monospace, "SF Mono", Menlo, monospace;
  font-weight: 700;
  background: rgba(0, 0, 0, 0.35);
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 0.8rem;
}
.gtm-act { opacity: 0.95; }
.gtm-features {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 6px;
  font-size: 0.9rem;
  li {
    display: flex;
    gap: 8px;
    align-items: flex-start;
    line-height: 1.4;
    svg { flex-shrink: 0; margin-top: 2px; opacity: 0.9; }
  }
}
.gtm-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  padding: 14px 18px 18px;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.32), rgba(0, 0, 0, 0));
}
.gtm-secondary,
.gtm-primary {
  border: none;
  border-radius: 12px;
  padding: 10px 16px;
  font-weight: 700;
  font-size: 0.92rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.gtm-secondary {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.22);
  &:hover { background: rgba(255, 255, 255, 0.28); }
}
.gtm-primary {
  background: rgba(255, 255, 255, 0.95);
  color: #111;
  &:hover { background: #fff; }
}
</style>
