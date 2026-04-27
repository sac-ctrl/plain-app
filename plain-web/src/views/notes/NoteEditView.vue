<!-- eslint-disable vue/no-v-html -->
<template>
  <div class="note-edit-page" :class="{ fullscreen, 'is-private': isPrivate }">
    <div class="top-app-bar note-bar">
      <v-icon-button v-tooltip="$t('back')" @click.prevent="backToList">
        <i-material-symbols:arrow-back-rounded />
      </v-icon-button>

      <div class="bar-title">
        <input
          v-model="title"
          class="title-input"
          :placeholder="$t('untitled_note')"
          :disabled="isLocked"
          maxlength="250"
        />
        <div class="bar-meta">
          <span class="status-pill" :class="saveStatus">
            <i-material-symbols:cloud-done-outline-rounded v-if="saveStatus === 'saved'" />
            <i-material-symbols:cloud-sync-outline-rounded v-else-if="saveStatus === 'saving'" />
            <i-material-symbols:cloud-off-outline-rounded v-else-if="saveStatus === 'error'" />
            <i-material-symbols:lock-outline v-else-if="saveStatus === 'locked'" />
            <i-material-symbols:edit-outline-rounded v-else-if="saveStatus === 'unsaved'" />
            <i-material-symbols:cloud-outline v-else />
            <span>{{ statusLabel }}</span>
          </span>
          <span v-if="isPrivate" class="privacy-pill">
            <i-material-symbols:lock /> {{ $t('vault_private') }}
          </span>
          <span v-if="note?.updatedAt" class="time-pill">
            {{ $t('updated_at') }}: {{ formatTimeAgo(note.updatedAt) }}
          </span>
        </div>
      </div>

      <div class="bar-actions">
        <v-icon-button
          v-tooltip="isPrivate ? $t('vault_make_public') : $t('vault_make_private')"
          :class="{ 'is-private-btn': isPrivate }"
          @click.prevent="togglePrivate(!isPrivate)"
        >
          <i-material-symbols:lock v-if="isPrivate" />
          <i-material-symbols:lock-open-outline-rounded v-else />
        </v-icon-button>

        <v-icon-button v-if="isPrivate && vaultUnlocked" v-tooltip="$t('vault_lock_now')" @click.prevent="lockNow">
          <i-material-symbols:lock-clock-outline-rounded />
        </v-icon-button>

        <div class="mode-group" role="tablist" :aria-label="$t('editor_view')">
          <button class="mode-btn" :class="{ active: viewMode === 'editor' }" @click="setViewMode('editor')">
            <i-material-symbols:edit-outline-rounded />
          </button>
          <button class="mode-btn" :class="{ active: viewMode === 'split' }" @click="setViewMode('split')">
            <i-material-symbols:vertical-split-rounded />
          </button>
          <button class="mode-btn" :class="{ active: viewMode === 'preview' }" @click="setViewMode('preview')">
            <i-material-symbols:visibility-outline-rounded />
          </button>
        </div>

        <v-icon-button v-tooltip="$t('editor_find_replace')" @click.prevent="toggleFindReplace">
          <i-material-symbols:find-replace-rounded />
        </v-icon-button>
        <v-icon-button v-tooltip="$t('editor_outline')" :class="{ active: showOutline }" @click.prevent="toggleOutline">
          <i-material-symbols:list-rounded />
        </v-icon-button>
        <v-icon-button v-tooltip="$t('editor_fullscreen')" @click.prevent="toggleFullscreen">
          <i-material-symbols:fullscreen-rounded v-if="!fullscreen" />
          <i-material-symbols:fullscreen-exit-rounded v-else />
        </v-icon-button>

        <div class="more-menu" @mouseleave="moreOpen = false">
          <v-icon-button v-tooltip="$t('more')" @click.prevent="moreOpen = !moreOpen">
            <i-material-symbols:more-vert />
          </v-icon-button>
          <ul v-show="moreOpen" class="more-pop" @click="moreOpen = false">
            <li v-if="id" @click="addToTags">
              <i-material-symbols:label-outline-rounded /> {{ $t('add_to_tags') }}
            </li>
            <li @click="exportMarkdown">
              <i-material-symbols:download-rounded /> {{ $t('editor_export_md') }}
            </li>
            <li @click="exportHtml">
              <i-material-symbols:html-rounded /> {{ $t('editor_export_html') }}
            </li>
            <li v-if="id" @click="print">
              <i-material-symbols:print-outline-rounded /> {{ $t('print') }}
            </li>
            <li @click="copyAll">
              <i-material-symbols:content-copy-outline-rounded /> {{ $t('editor_copy_all') }}
            </li>
            <li class="danger" @click="clearAll">
              <i-material-symbols:delete-sweep-outline-rounded /> {{ $t('editor_clear_all') }}
            </li>
          </ul>
        </div>
      </div>
    </div>

    <item-tags v-if="note?.tags?.length" class="note-tags-rail" :tags="note.tags" :type="dataType" :only-links="true" />

    <editor-toolbar v-show="viewMode !== 'preview' && !isLocked" @cmd="onToolbarCmd" />
    <note-find-replace v-if="showFindReplace && !isLocked" @replace="doFindReplace" @close="toggleFindReplace" />

    <div v-if="isLocked" class="locked-banner">
      <i-material-symbols:lock-outline />
      <div>
        <div class="lb-title">{{ $t('vault_note_locked') }}</div>
        <div class="lb-sub">{{ $t('vault_note_locked_sub') }}</div>
      </div>
      <v-filled-button @click="promptUnlock">{{ $t('vault_unlock') }}</v-filled-button>
    </div>

    <div v-else class="note-edit-container" :class="viewMode">
      <div v-show="viewMode !== 'preview'" class="editor-pane">
        <markdown-editor
          ref="editorRef"
          v-model="content"
          :placeholder="$t('write_markdown')"
          @paste-images="onPasteImages"
        />
        <div v-if="uploadingImage" class="upload-indicator">
          <v-circular-progress indeterminate class="sm" /> {{ $t('uploading') }}
        </div>
      </div>
      <div v-show="viewMode !== 'editor'" class="preview-pane md-container" v-html="markdown" />
      <note-outline
        v-if="showOutline"
        class="outline-pane"
        :source="content"
        @jump="onJumpToLine"
      />
    </div>

    <div class="status-bar">
      <span>{{ $t('editor_words', { n: stats.words }) }}</span>
      <span>{{ $t('editor_chars', { n: stats.chars }) }}</span>
      <span>{{ $t('editor_lines', { n: stats.lines }) }}</span>
      <span>{{ $t('editor_reading_time', { n: stats.minutes }) }}</span>
      <span class="grow" />
      <span v-if="isPrivate" class="status-private">
        <i-material-symbols:shield-lock-outline-rounded /> {{ $t('vault_e2e') }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useNoteEdit } from '@/hooks/note-edit'
import { useVaultStore } from '@/stores/vault'
import { storeToRefs } from 'pinia'
import { openModal } from '@/components/modal'
import PrivateVaultDialog from '@/components/PrivateVaultDialog.vue'
import EditorToolbar from '@/views/notes/EditorToolbar.vue'
import NoteOutline from '@/views/notes/NoteOutline.vue'
import NoteFindReplace from '@/views/notes/NoteFindReplace.vue'
import type MarkdownEditorVue from '@/components/MarkdownEditor.vue'
import { formatTimeAgo } from '@/lib/format'
import toast from '@/components/toaster'

const { t } = useI18n()
const editorRef = ref<InstanceType<typeof MarkdownEditorVue>>()
const moreOpen = ref(false)

const {
  id, note, title, content, markdown, dataType, viewMode, uploadingImage,
  backToList, addToTags, print, handlePasteImages, setViewMode,
  isPrivate, isLocked, togglePrivate, lockVault,
  fullscreen, toggleFullscreen,
  showOutline, toggleOutline,
  showFindReplace, toggleFindReplace,
  saveStatus, stats, exportMarkdown, exportHtml, saveNow,
} = useNoteEdit()

const vault = useVaultStore()
const { unlocked: vaultUnlocked, hasVault } = storeToRefs(vault)

const statusLabel = computed(() => {
  switch (saveStatus.value) {
    case 'saving': return t('editor_status_saving')
    case 'saved': return t('editor_status_saved')
    case 'error': return t('editor_status_error')
    case 'locked': return t('editor_status_locked')
    case 'unsaved': return t('editor_status_unsaved')
    default: return t('editor_status_idle')
  }
})

async function onPasteImages(files: File[]) {
  const paths = await handlePasteImages(files)
  for (const md of paths) editorRef.value?.insertText('\n' + md + '\n')
}

function lockNow() { lockVault(); saveNow() }

function promptUnlock() {
  openModal(PrivateVaultDialog, {
    mode: hasVault.value ? 'unlock' : 'setup',
    blob: note.value?.encryptedBlob || '',
    onUnlocked: () => { /* handled by hook re-fetch */ window.location.reload() },
  })
}

function onToolbarCmd(name: string) {
  const ed = editorRef.value
  if (!ed) return
  switch (name) {
    case 'undo': document.execCommand('undo'); ed.focusEditor(); break
    case 'redo': document.execCommand('redo'); ed.focusEditor(); break
    case 'h1': ed.setHeading(1); break
    case 'h2': ed.setHeading(2); break
    case 'h3': ed.setHeading(3); break
    case 'bold': ed.surroundSelection('**', '**', t('editor_bold')); break
    case 'italic': ed.surroundSelection('*', '*', t('editor_italic')); break
    case 'strike': ed.surroundSelection('~~', '~~', t('editor_strike')); break
    case 'code': ed.surroundSelection('`', '`', 'code'); break
    case 'ul': ed.toggleLinePrefix('- '); break
    case 'ol': ed.toggleLinePrefix('1. '); break
    case 'task': ed.toggleLinePrefix('- [ ] '); break
    case 'quote': ed.toggleLinePrefix('> '); break
    case 'link': {
      const sel = ed.getSelectionText() || t('editor_link')
      ed.replaceSelection(`[${sel}](https://)`)
      break
    }
    case 'image': ed.insertText('\n![alt](https://)\n'); break
    case 'table': ed.insertText('\n| Col 1 | Col 2 | Col 3 |\n| --- | --- | --- |\n|  |  |  |\n'); break
    case 'codeblock': ed.insertText('\n```\n\n```\n'); break
    case 'hr': ed.insertText('\n\n---\n\n'); break
    case 'find': toggleFindReplace(); break
  }
}

function doFindReplace(p: { find: string; replace: string; all: boolean }): number {
  return editorRef.value?.findAndReplace(p.find, p.replace, p.all) ?? 0
}

function onJumpToLine(line: number) {
  setViewMode('editor')
  setTimeout(() => editorRef.value?.focusEditor(), 0)
  void line
}

async function copyAll() {
  try {
    await navigator.clipboard.writeText(content.value || '')
    toast(t('copied'), 'success')
  } catch { toast(t('copy_failed') ?? 'Copy failed', 'error') }
}

function clearAll() {
  if (!confirm(t('editor_clear_confirm') as string)) return
  content.value = ''
}
</script>

<style lang="scss" scoped>
.note-edit-page {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: calc(100dvh - 64px);
  padding: 10px 14px 12px;
  box-sizing: border-box;
  overflow: hidden;
  background: var(--md-sys-color-background);
  &.fullscreen {
    position: fixed; inset: 0; z-index: 100;
    height: 100dvh; padding: 8px 12px 10px;
  }
  &.is-private {
    background: linear-gradient(180deg, var(--md-sys-color-background) 0%, var(--md-sys-color-surface-container-low) 100%);
  }
}
.note-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bar-title {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.title-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 1.15rem;
  font-weight: 600;
  background: transparent;
  color: var(--md-sys-color-on-surface);
  padding: 4px 6px;
  border-radius: 6px;
  width: 100%;
  &:focus {
    background: var(--md-sys-color-surface-container);
  }
  &::placeholder {
    color: var(--md-sys-color-on-surface-variant);
    opacity: 0.5;
    font-weight: 500;
  }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.bar-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 0 6px;
}
.status-pill, .privacy-pill, .time-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.72rem;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface-variant);
}
.status-pill.saved { color: var(--md-sys-color-primary); }
.status-pill.saving { color: var(--md-sys-color-tertiary); }
.status-pill.unsaved { color: var(--md-sys-color-secondary); }
.status-pill.error { color: var(--md-sys-color-error); }
.status-pill.locked { color: var(--md-sys-color-error); }
.privacy-pill {
  background: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
  font-weight: 600;
}
.bar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.is-private-btn {
  color: var(--md-sys-color-tertiary);
}
.mode-group {
  display: inline-flex;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 999px;
  overflow: hidden;
  margin: 0 4px;
  background: var(--md-sys-color-surface-container-low);
}
.mode-btn {
  border: none;
  background: transparent;
  color: var(--md-sys-color-on-surface-variant);
  width: 32px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.mode-btn.active {
  background: var(--md-sys-color-primary-container);
  color: var(--md-sys-color-on-primary-container);
}
.note-tags-rail { padding: 0 4px; }

.note-edit-container {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 14px;
  overflow: hidden;
  background: var(--md-sys-color-surface);
  display: grid;
  grid-template-columns: 1fr;
  &.split {
    grid-template-columns: 1fr 1fr;
  }
  &.preview { border: none; }
}
.note-edit-container.split .editor-pane,
.note-edit-container.split .preview-pane {
  display: block;
  border-right: 1px solid var(--md-sys-color-outline-variant);
}
.note-edit-container.split .preview-pane { border-right: none; }
.note-edit-container .outline-pane {
  grid-column: -1;
}
.note-edit-container:has(.outline-pane) {
  grid-template-columns: 1fr 220px;
}
.note-edit-container.split:has(.outline-pane) {
  grid-template-columns: 1fr 1fr 220px;
}
.editor-pane {
  height: 100%;
  position: relative;
  overflow: hidden;
}
.preview-pane {
  height: 100%;
  padding: 20px 24px;
  box-sizing: border-box;
  overflow-y: auto;
  position: relative;
}
.upload-indicator {
  position: absolute;
  bottom: 8px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  color: var(--md-sys-color-on-surface-variant);
  background: var(--md-sys-color-surface-variant);
  padding: 4px 10px;
  border-radius: 6px;
}

.locked-banner {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 14px;
  padding: 32px;
  border: 2px dashed var(--md-sys-color-outline-variant);
  border-radius: 14px;
  background: var(--md-sys-color-surface);
  text-align: center;
  > svg, > .iconify { font-size: 56px; color: var(--md-sys-color-tertiary); }
  .lb-title { font-size: 1.1rem; font-weight: 600; }
  .lb-sub { color: var(--md-sys-color-on-surface-variant); margin-top: 4px; max-width: 420px; }
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 8px;
  font-size: 0.74rem;
  color: var(--md-sys-color-on-surface-variant);
  border-top: 1px solid var(--md-sys-color-outline-variant);
  .grow { flex: 1; }
  .status-private {
    display: inline-flex; align-items: center; gap: 4px;
    color: var(--md-sys-color-tertiary);
    font-weight: 600;
  }
}

.more-menu { position: relative; }
.more-pop {
  position: absolute;
  right: 0; top: calc(100% + 4px);
  z-index: 50;
  list-style: none;
  margin: 0; padding: 6px;
  border-radius: 12px;
  min-width: 200px;
  background: var(--md-sys-color-surface-container-high);
  box-shadow: 0 8px 24px rgba(0,0,0,0.18);
  display: flex; flex-direction: column; gap: 2px;
  li {
    display: flex; align-items: center; gap: 10px;
    padding: 8px 12px;
    border-radius: 8px;
    font-size: 0.85rem;
    cursor: pointer;
    color: var(--md-sys-color-on-surface);
    &:hover { background: var(--md-sys-color-surface-container-highest); }
    &.danger { color: var(--md-sys-color-error); }
  }
}

@media (max-width: 960px) {
  .note-edit-page { padding: 8px 10px 10px; }
  .preview-pane { padding: 14px; }
  .bar-actions .mode-group { display: none; }
  .note-edit-container.split { grid-template-columns: 1fr; }
}
</style>
