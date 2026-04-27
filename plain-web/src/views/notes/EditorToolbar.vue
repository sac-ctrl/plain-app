<template>
  <div class="editor-toolbar">
    <div class="toolbar-group">
      <button v-tooltip="$t('editor_undo')" class="tb-btn" @click="emit('cmd', 'undo')">
        <i-material-symbols:undo-rounded />
      </button>
      <button v-tooltip="$t('editor_redo')" class="tb-btn" @click="emit('cmd', 'redo')">
        <i-material-symbols:redo-rounded />
      </button>
    </div>
    <span class="tb-sep" />
    <div class="toolbar-group">
      <button v-tooltip="$t('editor_h1')" class="tb-btn" @click="emit('cmd', 'h1')"><strong>H1</strong></button>
      <button v-tooltip="$t('editor_h2')" class="tb-btn" @click="emit('cmd', 'h2')"><strong>H2</strong></button>
      <button v-tooltip="$t('editor_h3')" class="tb-btn" @click="emit('cmd', 'h3')"><strong>H3</strong></button>
    </div>
    <span class="tb-sep" />
    <div class="toolbar-group">
      <button v-tooltip="$t('editor_bold')" class="tb-btn" @click="emit('cmd', 'bold')">
        <i-material-symbols:format-bold-rounded />
      </button>
      <button v-tooltip="$t('editor_italic')" class="tb-btn" @click="emit('cmd', 'italic')">
        <i-material-symbols:format-italic-rounded />
      </button>
      <button v-tooltip="$t('editor_strike')" class="tb-btn" @click="emit('cmd', 'strike')">
        <i-material-symbols:format-strikethrough-rounded />
      </button>
      <button v-tooltip="$t('editor_inline_code')" class="tb-btn" @click="emit('cmd', 'code')">
        <i-material-symbols:code-rounded />
      </button>
    </div>
    <span class="tb-sep" />
    <div class="toolbar-group">
      <button v-tooltip="$t('editor_ul')" class="tb-btn" @click="emit('cmd', 'ul')">
        <i-material-symbols:format-list-bulleted-rounded />
      </button>
      <button v-tooltip="$t('editor_ol')" class="tb-btn" @click="emit('cmd', 'ol')">
        <i-material-symbols:format-list-numbered-rounded />
      </button>
      <button v-tooltip="$t('editor_task')" class="tb-btn" @click="emit('cmd', 'task')">
        <i-material-symbols:checklist-rounded />
      </button>
      <button v-tooltip="$t('editor_quote')" class="tb-btn" @click="emit('cmd', 'quote')">
        <i-material-symbols:format-quote-rounded />
      </button>
    </div>
    <span class="tb-sep" />
    <div class="toolbar-group">
      <button v-tooltip="$t('editor_link')" class="tb-btn" @click="emit('cmd', 'link')">
        <i-material-symbols:link-rounded />
      </button>
      <button v-tooltip="$t('editor_image')" class="tb-btn" @click="emit('cmd', 'image')">
        <i-material-symbols:image-outline-rounded />
      </button>
      <button v-tooltip="$t('editor_table')" class="tb-btn" @click="emit('cmd', 'table')">
        <i-material-symbols:table />
      </button>
      <button v-tooltip="$t('editor_codeblock')" class="tb-btn" @click="emit('cmd', 'codeblock')">
        <i-material-symbols:code-blocks-rounded />
      </button>
      <button v-tooltip="$t('editor_hr')" class="tb-btn" @click="emit('cmd', 'hr')">
        <i-material-symbols:horizontal-rule-rounded />
      </button>
    </div>
    <span class="tb-sep" />
    <div class="toolbar-group">
      <button v-tooltip="$t('editor_find_replace')" class="tb-btn" @click="emit('cmd', 'find')">
        <i-material-symbols:search-rounded />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
const emit = defineEmits<{ (e: 'cmd', name: string): void }>()
</script>

<style scoped lang="scss">
.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  background: var(--md-sys-color-surface-container-low);
  border-bottom: 1px solid var(--md-sys-color-outline-variant);
  overflow-x: auto;
  flex-wrap: nowrap;
}
.toolbar-group {
  display: inline-flex;
  gap: 2px;
}
.tb-btn {
  border: none;
  background: transparent;
  color: var(--md-sys-color-on-surface);
  width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.78rem;
  transition: background 0.12s ease;
}
.tb-btn:hover {
  background: var(--md-sys-color-surface-container-highest);
}
.tb-btn:active {
  background: var(--md-sys-color-secondary-container);
}
.tb-sep {
  width: 1px;
  height: 20px;
  background: var(--md-sys-color-outline-variant);
  margin: 0 4px;
  flex-shrink: 0;
}
</style>
