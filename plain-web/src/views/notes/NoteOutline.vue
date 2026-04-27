<template>
  <aside class="outline">
    <div class="outline-head">{{ $t('editor_outline') }}</div>
    <div v-if="headings.length === 0" class="outline-empty">{{ $t('editor_outline_empty') }}</div>
    <ul v-else class="outline-list">
      <li
        v-for="(h, i) in headings"
        :key="i"
        :class="['outline-item', `lvl-${h.level}`]"
        :title="h.text"
        @click="emit('jump', h.line)"
      >
        {{ h.text || '\u00a0' }}
      </li>
    </ul>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ source: string }>()
const emit = defineEmits<{ (e: 'jump', line: number): void }>()

interface Heading { level: number; text: string; line: number }

const headings = computed<Heading[]>(() => {
  const lines = (props.source ?? '').split('\n')
  const out: Heading[] = []
  let inFence = false
  for (let i = 0; i < lines.length; i++) {
    const ln = lines[i]
    if (ln.startsWith('```')) { inFence = !inFence; continue }
    if (inFence) continue
    const m = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(ln)
    if (m) out.push({ level: m[1].length, text: m[2].trim(), line: i + 1 })
  }
  return out
})
</script>

<style scoped lang="scss">
.outline {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  overflow-y: auto;
  background: var(--md-sys-color-surface-container-low);
  border-left: 1px solid var(--md-sys-color-outline-variant);
}
.outline-head {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--md-sys-color-on-surface-variant);
  margin-bottom: 8px;
}
.outline-empty {
  font-size: 0.82rem;
  color: var(--md-sys-color-on-surface-variant);
  opacity: 0.7;
}
.outline-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.outline-item {
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 0.85rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--md-sys-color-on-surface);
}
.outline-item:hover {
  background: var(--md-sys-color-surface-container-highest);
}
.lvl-1 { font-weight: 600; }
.lvl-2 { padding-left: 16px; }
.lvl-3 { padding-left: 24px; font-size: 0.8rem; }
.lvl-4 { padding-left: 32px; font-size: 0.78rem; opacity: 0.85; }
.lvl-5 { padding-left: 40px; font-size: 0.75rem; opacity: 0.75; }
.lvl-6 { padding-left: 48px; font-size: 0.72rem; opacity: 0.7; }
</style>
