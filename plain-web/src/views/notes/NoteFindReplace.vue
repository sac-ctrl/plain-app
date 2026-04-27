<template>
  <div class="find-bar">
    <div class="row">
      <input v-model="findText" :placeholder="$t('editor_find')" class="fr-input" @keyup.enter="doReplace(false)" />
      <input v-model="replaceText" :placeholder="$t('editor_replace_with')" class="fr-input" @keyup.enter="doReplace(true)" />
      <button class="fr-btn" @click="doReplace(false)">{{ $t('editor_replace_one') }}</button>
      <button class="fr-btn primary" @click="doReplace(true)">{{ $t('editor_replace_all') }}</button>
      <button class="fr-btn icon" :title="$t('close')" @click="emit('close')">
        <i-material-symbols:close-rounded />
      </button>
    </div>
    <div v-if="lastCount !== null" class="status">
      {{ $t('editor_replaced_count', { count: lastCount }) }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{
  (e: 'replace', payload: { find: string; replace: string; all: boolean }): number
  (e: 'close'): void
}>()

const findText = ref('')
const replaceText = ref('')
const lastCount = ref<number | null>(null)

function doReplace(all: boolean) {
  if (!findText.value) return
  const n = (emit('replace', { find: findText.value, replace: replaceText.value, all }) as unknown as number) ?? 0
  lastCount.value = n
}
</script>

<style scoped lang="scss">
.find-bar {
  background: var(--md-sys-color-surface-container);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.row {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
.fr-input {
  flex: 1 1 160px;
  min-width: 120px;
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
  font-size: 0.85rem;
  outline: none;
  &:focus { border-color: var(--md-sys-color-primary); }
}
.fr-btn {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: transparent;
  color: var(--md-sys-color-on-surface);
  cursor: pointer;
  font-size: 0.8rem;
  &.primary {
    background: var(--md-sys-color-primary);
    color: var(--md-sys-color-on-primary);
    border-color: transparent;
  }
  &.icon {
    width: 32px; height: 32px; padding: 0;
    display: inline-flex; align-items: center; justify-content: center;
  }
  &:hover { background: var(--md-sys-color-surface-container-highest); }
}
.status {
  font-size: 0.78rem;
  color: var(--md-sys-color-on-surface-variant);
}
</style>
