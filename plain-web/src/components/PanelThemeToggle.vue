<template>
  <button
    type="button"
    class="theme-toggle-fab"
    :title="theme.isMatrix ? 'Switch to classic UI' : 'Switch to matrix UI'"
    @click="theme.toggle()"
  >
    <span class="dot" />
    <span>{{ theme.isMatrix ? 'MATRIX' : 'CLASSIC' }}</span>
  </button>
</template>

<script setup lang="ts">
import { usePanelThemeStore } from '@/stores/theme'

const theme = usePanelThemeStore()
</script>
