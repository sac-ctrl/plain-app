<template>
  <div class="recordings-page">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <div class="title">
        {{ selectionMode ? $t('call_recorder_selected', { n: selected.size }) : $t('call_recordings') }}
      </div>
    </Teleport>

    <Teleport v-if="isActive" to="#header-end-slot" defer>
      <div class="header-actions">
        <template v-if="!selectionMode">
          <v-icon-button v-tooltip="$t('refresh')" @click="reload">
            <i-lucide:refresh-cw />
          </v-icon-button>
          <v-icon-button
            v-if="recordings.length > 0"
            v-tooltip="$t('call_recorder_select')"
            @click="enterSelection"
          >
            <i-lucide:check-square />
          </v-icon-button>
          <v-icon-button
            v-if="recordings.length > 0"
            v-tooltip="$t('call_recorder_delete_oldest')"
            @click="onDeleteOldest"
          >
            <i-lucide:clock />
          </v-icon-button>
          <v-icon-button
            v-if="recordings.length > 0"
            v-tooltip="$t('call_recorder_delete_by_dates')"
            @click="dateModal = true"
          >
            <i-lucide:calendar />
          </v-icon-button>
          <v-icon-button
            v-if="recordings.length > 0"
            v-tooltip="$t('call_recorder_delete_all')"
            @click="onDeleteAll"
          >
            <i-lucide:trash-2 />
          </v-icon-button>
        </template>
        <template v-else>
          <v-icon-button v-tooltip="$t('select_all')" @click="selectAllVisible">
            <i-lucide:list-checks />
          </v-icon-button>
          <v-icon-button v-tooltip="$t('clear')" @click="clearSelection">
            <i-lucide:x />
          </v-icon-button>
          <v-filled-button
            class="btn-sm"
            :disabled="selected.size === 0"
            @click="onDeleteSelected"
          >
            {{ $t('delete') }} ({{ selected.size }})
          </v-filled-button>
          <v-icon-button v-tooltip="$t('cancel')" @click="exitSelection">
            <i-lucide:arrow-left />
          </v-icon-button>
        </template>
      </div>
    </Teleport>

    <div class="status-card" :class="{ 'is-recording': state.recording }">
      <div class="status-left">
        <i-lucide:phone-call class="status-icon" />
        <div>
          <div class="status-title">{{ $t('call_recorder') }}</div>
          <div class="status-sub">
            <span v-if="state.recording" class="rec-pill">
              <span class="rec-dot" /> {{ $t('recording_now_label') }}
              <span class="elapsed">{{ elapsed }}</span>
              <span v-if="state.currentDisplayName">· {{ state.currentDisplayName }}</span>
            </span>
            <span v-else-if="state.enabled">{{ $t('call_recorder_idle') }}</span>
            <span v-else>{{ $t('call_recorder_disabled') }}</span>
          </div>
        </div>
      </div>
      <label class="switch">
        <input type="checkbox" :checked="state.enabled" @change="onToggle" />
        <span class="track" />
      </label>
    </div>

    <p class="page-hint">{{ $t('call_recorder_hint') }}</p>

    <div class="engine-card">
      <div class="engine-title">
        <i-lucide:settings-2 />
        <span>{{ $t('call_recorder_status_title') }}</span>
      </div>
      <p class="engine-text">{{ $t('call_recorder_status_hint') }}</p>
      <div v-if="state.recording" class="engine-live">
        <span class="badge live">{{ state.activeAudioSource || 'MIC' }}</span>
        <span class="badge" :class="state.speakerphoneForced ? 'good' : 'warn'">
          {{ state.speakerphoneForced ? $t('call_recorder_capture_both_sides') : $t('call_recorder_capture_one_side') }}
        </span>
      </div>
    </div>

    <div class="meta-row">
      <span>{{ $t('call_recorder_count', { count: state.totalCount }, state.totalCount) }}</span>
      <span>·</span>
      <span>{{ $t('call_recorder_total_size', { size: formatFileSize(state.totalSize) }) }}</span>
    </div>

    <div v-if="loading && recordings.length === 0" class="empty">{{ $t('loading') }}</div>
    <div v-else-if="recordings.length === 0" class="empty">{{ $t('call_recorder_no_recordings') }}</div>
    <div v-else class="recordings-list">
      <article
        v-for="r in recordings"
        :key="r.filename"
        class="recording-card"
        :class="{ selected: selected.has(r.filename), 'select-mode': selectionMode }"
        @click="onCardClick(r.filename, $event)"
      >
        <header class="recording-head">
          <div class="recording-title">
            <label v-if="selectionMode" class="checkbox" @click.stop>
              <input
                type="checkbox"
                :checked="selected.has(r.filename)"
                @change="toggleSelected(r.filename)"
              />
            </label>
            <span class="badge" :class="r.source">{{ r.source }}</span>
            <span class="caller">{{ r.displayName || r.filename }}</span>
            <span class="dir-badge" :class="r.direction">
              {{ r.direction === 'incoming' ? $t('incoming') : $t('outgoing') }}
            </span>
          </div>
          <div v-if="!selectionMode" class="recording-actions">
            <a
              :href="getFileUrl(r.fileId) + '&dl=1'"
              :download="r.filename"
              class="action-btn"
              :title="$t('download')"
              @click.stop
            >
              <i-lucide:download />
            </a>
            <button
              class="action-btn danger"
              :title="$t('delete')"
              @click.stop="onDelete(r.filename)"
            >
              <i-lucide:trash-2 />
            </button>
          </div>
        </header>

        <div class="recording-meta">
          <div class="meta-cell">
            <span class="cell-label">{{ $t('call_recorder_started_at') }}</span>
            <span class="cell-value">{{ formatDateTime(new Date(r.startedAt).toISOString()) }}</span>
          </div>
          <div class="meta-cell">
            <span class="cell-label">{{ $t('call_recorder_duration') }}</span>
            <span class="cell-value">{{ formatSeconds(Math.round(r.durationMs / 1000)) }}</span>
          </div>
          <div class="meta-cell">
            <span class="cell-label">{{ $t('call_recorder_size') }}</span>
            <span class="cell-value">{{ formatFileSize(r.sizeBytes) }}</span>
          </div>
          <div v-if="r.appName" class="meta-cell">
            <span class="cell-label">{{ $t('call_recorder_source') }}</span>
            <span class="cell-value">{{ r.appName }}</span>
          </div>
          <div class="meta-cell">
            <span class="cell-label">{{ $t('call_recorder_audio_source') }}</span>
            <span class="cell-value">{{ r.audioSource || 'MIC' }}</span>
          </div>
          <div class="meta-cell">
            <span class="cell-label">{{ $t('call_recorder_capture_quality') }}</span>
            <span class="cell-value capture" :class="r.speakerphoneForced ? 'good' : 'warn'">
              {{ r.speakerphoneForced ? $t('call_recorder_capture_both_sides') : $t('call_recorder_capture_one_side') }}
            </span>
          </div>
        </div>

        <audio v-if="!selectionMode" :src="getFileUrl(r.fileId)" controls preload="metadata" class="player" @click.stop />
      </article>
    </div>

    <div v-if="dateModal" class="modal-backdrop" @click.self="dateModal = false">
      <div class="modal">
        <h3 class="modal-title">{{ $t('call_recorder_delete_by_dates') }}</h3>
        <p class="modal-hint">{{ $t('call_recorder_delete_by_dates_hint') }}</p>
        <div class="date-grid">
          <button
            v-for="d in availableDates"
            :key="d.key"
            type="button"
            class="date-chip"
            :class="{ on: pickedDates.has(d.key) }"
            @click="togglePickedDate(d.key)"
          >
            <i-lucide:calendar-days />
            <span class="date-label">{{ d.label }}</span>
            <span class="date-count">{{ $t('call_recorder_count', { count: d.count }, d.count) }}</span>
          </button>
        </div>
        <p v-if="availableDates.length === 0" class="modal-empty">{{ $t('call_recorder_no_recordings') }}</p>
        <div class="modal-actions">
          <v-text-button @click="dateModal = false">{{ $t('cancel') }}</v-text-button>
          <v-filled-button :disabled="pickedDates.size === 0" @click="onDeleteByDates">
            {{ $t('delete') }}
          </v-filled-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { useI18n } from 'vue-i18n'
import { gqlFetch } from '@/lib/api/gql-client'
import { initMutation } from '@/lib/api/mutation'
import { callRecorderStateGQL, callRecordingsGQL } from '@/lib/api/query'
import {
  setCallRecorderEnabledGQL,
  deleteCallRecordingGQL,
  deleteAllCallRecordingsGQL,
  deleteCallRecordingsGQL,
  deleteOldestCallRecordingsGQL,
  deleteCallRecordingsByDatesGQL,
} from '@/lib/api/mutation'
import { getFileUrl } from '@/lib/api/file'
import { formatDateTime, formatFileSize, formatSeconds } from '@/lib/format'

interface ICallRecorderState {
  enabled: boolean; recording: boolean; currentDisplayName: string; currentSource: string
  currentStartedAt: number; totalCount: number; totalSize: number; lastError: string
  activeAudioSource: string; speakerphoneForced: boolean
}
interface ICallRecording {
  id: string; filename: string; displayName: string; source: string; direction: string
  appId: string; appName: string; startedAt: number; endedAt: number; durationMs: number
  sizeBytes: number; fileId: string; audioSource: string; speakerphoneForced: boolean
}

const { t } = useI18n()
const route = useRoute()
const isActive = computed(() => route.path === '/call-recordings')

const state = ref<ICallRecorderState>({
  enabled: true, recording: false, currentDisplayName: '', currentSource: '',
  currentStartedAt: 0, totalCount: 0, totalSize: 0, lastError: '',
  activeAudioSource: '', speakerphoneForced: false,
})
const recordings = ref<ICallRecording[]>([])
const loading = ref(true)

const selectionMode = ref(false)
const selected = ref<Set<string>>(new Set())
const dateModal = ref(false)
const pickedDates = ref<Set<string>>(new Set())

const tickNow = ref(Date.now())
let tickHandle: any
const elapsed = computed(() => {
  void tickNow.value
  if (!state.value.recording || !state.value.currentStartedAt) return ''
  const sec = Math.floor((Date.now() - state.value.currentStartedAt) / 1000)
  const m = Math.floor(sec / 60); const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
})

function dateKey(ts: number): string {
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const availableDates = computed(() => {
  const map = new Map<string, { label: string; count: number; ts: number }>()
  for (const r of recordings.value) {
    const k = dateKey(r.startedAt)
    const cur = map.get(k)
    if (cur) cur.count++
    else map.set(k, { label: formatDateTime(new Date(r.startedAt).toISOString()).split(' ')[0] || k, count: 1, ts: r.startedAt })
  }
  return Array.from(map.entries())
    .map(([key, v]) => ({ key, label: v.label, count: v.count, ts: v.ts }))
    .sort((a, b) => b.ts - a.ts)
})

const { mutate: mToggle } = initMutation({ document: setCallRecorderEnabledGQL })
const { mutate: mDel } = initMutation({ document: deleteCallRecordingGQL })
const { mutate: mDelAll } = initMutation({ document: deleteAllCallRecordingsGQL })
const { mutate: mDelMany } = initMutation({ document: deleteCallRecordingsGQL })
const { mutate: mDelOldest } = initMutation({ document: deleteOldestCallRecordingsGQL })
const { mutate: mDelByDates } = initMutation({ document: deleteCallRecordingsByDatesGQL })

async function reload() {
  loading.value = true
  try {
    const s = await gqlFetch<{ callRecorderState: ICallRecorderState }>(callRecorderStateGQL)
    if (!s.errors) state.value = s.data.callRecorderState
    const r = await gqlFetch<{ callRecordings: ICallRecording[] }>(callRecordingsGQL, { offset: 0, limit: 500 })
    if (!r.errors) recordings.value = r.data.callRecordings
  } finally {
    loading.value = false
  }
}

async function onToggle(e: Event) {
  const enabled = (e.target as HTMLInputElement).checked
  state.value.enabled = enabled
  await mToggle({ enabled })
  toast(enabled ? t('call_recorder_enabled') : t('call_recorder_disabled'))
}

async function onDelete(filename: string) {
  if (!confirm(t('confirm_delete'))) return
  await mDel({ filename })
  recordings.value = recordings.value.filter((r) => r.filename !== filename)
  toast(t('deleted'))
}

async function onDeleteAll() {
  if (!confirm(t('call_recorder_delete_all_confirm'))) return
  await mDelAll()
  recordings.value = []
  toast(t('deleted'))
}

function enterSelection() {
  selectionMode.value = true
  selected.value = new Set()
}
function exitSelection() {
  selectionMode.value = false
  selected.value = new Set()
}
function clearSelection() { selected.value = new Set() }
function selectAllVisible() {
  selected.value = new Set(recordings.value.map((r) => r.filename))
}
function toggleSelected(filename: string) {
  const next = new Set(selected.value)
  if (next.has(filename)) next.delete(filename)
  else next.add(filename)
  selected.value = next
}
function onCardClick(filename: string, e: MouseEvent) {
  if (!selectionMode.value) return
  if ((e.target as HTMLElement).closest('input,button,a,audio')) return
  toggleSelected(filename)
}

async function onDeleteSelected() {
  if (selected.value.size === 0) return
  if (!confirm(t('call_recorder_delete_selected_confirm', { n: selected.value.size }))) return
  const filenames = Array.from(selected.value)
  await mDelMany({ filenames })
  recordings.value = recordings.value.filter((r) => !selected.value.has(r.filename))
  toast(t('deleted'))
  exitSelection()
}

async function onDeleteOldest() {
  const raw = prompt(t('call_recorder_delete_oldest_prompt'), '5')
  if (raw === null) return
  const n = parseInt(raw, 10)
  if (!Number.isFinite(n) || n <= 0) {
    toast(t('invalid_input'), 'error')
    return
  }
  if (!confirm(t('call_recorder_delete_oldest_confirm', { n }))) return
  await mDelOldest({ count: n })
  await reload()
  toast(t('deleted'))
}

function togglePickedDate(key: string) {
  const next = new Set(pickedDates.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  pickedDates.value = next
}

async function onDeleteByDates() {
  if (pickedDates.value.size === 0) return
  if (!confirm(t('call_recorder_delete_by_dates_confirm', { n: pickedDates.value.size }))) return
  const dates = Array.from(pickedDates.value)
  await mDelByDates({ dates })
  pickedDates.value = new Set()
  dateModal.value = false
  await reload()
  toast(t('deleted'))
}

const onStateEvt = (d: any) => { if (d) state.value = d }
const onListChanged = () => { reload() }

onMounted(() => {
  reload()
  emitter.on('call_recorder_state', onStateEvt)
  emitter.on('call_recordings_changed', onListChanged)
  tickHandle = setInterval(() => { tickNow.value = Date.now() }, 1000)
})

onUnmounted(() => {
  emitter.off('call_recorder_state', onStateEvt)
  emitter.off('call_recordings_changed', onListChanged)
  if (tickHandle) clearInterval(tickHandle)
})
</script>

<style scoped lang="scss">
.recordings-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; height: 100%; }
.title { flex: 1; font-weight: 500; }
.header-actions { display: flex; gap: 4px; align-items: center; }

.status-card {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 16px; border-radius: 16px;
  background: var(--md-sys-color-surface-container);
  transition: box-shadow 0.2s ease;
  &.is-recording { box-shadow: 0 0 0 2px var(--md-sys-color-error); }
}
.status-left { display: flex; align-items: center; gap: 12px; min-width: 0; flex: 1; }
.status-icon { font-size: 32px; color: var(--md-sys-color-primary); }
.status-title { font-size: 1.05rem; font-weight: 500; color: var(--md-sys-color-on-surface); }
.status-sub { font-size: 0.85rem; color: var(--md-sys-color-on-surface-variant); margin-top: 2px; }

.rec-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 2px 10px; border-radius: 999px;
  background: var(--md-sys-color-error-container, #ffdad6);
  color: var(--md-sys-color-on-error-container, #410002);
  font-size: 0.8rem; font-weight: 500;
}
.rec-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--md-sys-color-error, #d32f2f);
  animation: blink 1s infinite;
}
.elapsed { font-variant-numeric: tabular-nums; }
@keyframes blink { 0%, 49% { opacity: 1; } 50%, 100% { opacity: 0.2; } }

.switch {
  position: relative; display: inline-block; width: 52px; height: 28px;
  flex-shrink: 0;
  input { opacity: 0; width: 0; height: 0; }
  .track {
    position: absolute; cursor: pointer; inset: 0;
    background: var(--md-sys-color-surface-variant);
    border-radius: 28px; transition: 0.2s;
    &::before {
      position: absolute; content: ''; height: 22px; width: 22px;
      left: 3px; top: 3px;
      background: var(--md-sys-color-on-surface-variant);
      border-radius: 50%; transition: 0.2s;
    }
  }
  input:checked + .track {
    background: var(--md-sys-color-primary);
    &::before { transform: translateX(24px); background: var(--md-sys-color-on-primary); }
  }
}

.page-hint { color: var(--md-sys-color-on-surface-variant); margin: 0; font-size: 0.875rem; }
.meta-row { display: flex; gap: 8px; color: var(--md-sys-color-on-surface-variant); font-size: 0.85rem; }

.engine-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 12px; padding: 12px 14px;
  display: flex; flex-direction: column; gap: 8px;
}
.engine-title { display: flex; align-items: center; gap: 6px; font-weight: 500; font-size: 0.9rem; }
.engine-text { font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant); margin: 0; line-height: 1.4; }
.engine-live { display: flex; gap: 8px; flex-wrap: wrap; }
.engine-live .badge {
  padding: 2px 10px; border-radius: 999px; font-size: 0.75rem; font-weight: 500;
  background: var(--md-sys-color-surface-variant); color: var(--md-sys-color-on-surface-variant);
  &.live { background: var(--md-sys-color-tertiary-container); color: var(--md-sys-color-on-tertiary-container); }
  &.good { background: var(--md-sys-color-secondary-container); color: var(--md-sys-color-on-secondary-container); }
  &.warn { background: var(--md-sys-color-error-container, #ffdad6); color: var(--md-sys-color-on-error-container, #410002); }
}

.cell-value.capture {
  white-space: normal;
  &.good { color: var(--md-sys-color-secondary, #006b5c); }
  &.warn { color: var(--md-sys-color-error, #b3261e); }
}

.empty {
  text-align: center; padding: 32px;
  color: var(--md-sys-color-on-surface-variant);
  background: var(--md-sys-color-surface-container);
  border-radius: 12px;
}

.recordings-list { display: flex; flex-direction: column; gap: 12px; padding-bottom: 24px; }
.recording-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 16px;
  padding: 16px;
  display: flex; flex-direction: column; gap: 12px;
  transition: outline-color 120ms ease, background 120ms ease;
  outline: 2px solid transparent;
  &.select-mode { cursor: pointer; }
  &.selected {
    outline-color: var(--md-sys-color-primary);
    background: var(--md-sys-color-secondary-container);
  }
}
.recording-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.recording-title { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; flex-wrap: wrap; }
.checkbox { display: inline-flex; align-items: center; justify-content: center; }
.checkbox input { width: 18px; height: 18px; cursor: pointer; accent-color: var(--md-sys-color-primary); }
.caller {
  font-size: 1rem; font-weight: 500;
  color: var(--md-sys-color-on-surface);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  max-width: 100%;
}
.badge {
  text-transform: capitalize; padding: 2px 8px;
  border-radius: 6px; font-size: 0.75rem;
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
}
.dir-badge {
  text-transform: capitalize; padding: 2px 8px;
  border-radius: 6px; font-size: 0.75rem;
  background: var(--md-sys-color-tertiary-container);
  color: var(--md-sys-color-on-tertiary-container);
}

.recording-actions { display: flex; gap: 4px; }
.action-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%;
  background: transparent; border: none; cursor: pointer;
  color: var(--md-sys-color-on-surface-variant);
  text-decoration: none;
  transition: background 0.15s ease;
  &:hover { background: var(--md-sys-color-surface-variant); }
  &.danger:hover { background: var(--md-sys-color-error-container); color: var(--md-sys-color-on-error-container); }
}

.recording-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  padding: 12px;
  background: var(--md-sys-color-surface);
  border-radius: 8px;
}
.meta-cell { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.cell-label { font-size: 0.7rem; color: var(--md-sys-color-on-surface-variant); text-transform: uppercase; letter-spacing: 0.5px; }
.cell-value { font-size: 0.9rem; color: var(--md-sys-color-on-surface); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.player { width: 100%; }

.modal-backdrop {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center;
  padding: 16px;
}
.modal {
  background: var(--md-sys-color-surface-container-high);
  border-radius: 20px;
  padding: 24px;
  max-width: 640px; width: 100%;
  max-height: 80vh; overflow-y: auto;
  display: flex; flex-direction: column; gap: 16px;
  box-shadow: 0 16px 32px rgba(0, 0, 0, 0.25);
}
.modal-title { margin: 0; font-size: 1.1rem; font-weight: 600; }
.modal-hint { margin: 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.875rem; }
.modal-empty { margin: 0; text-align: center; color: var(--md-sys-color-on-surface-variant); padding: 16px; }
.date-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}
.date-chip {
  display: flex; flex-direction: column; align-items: flex-start; gap: 4px;
  padding: 10px 12px;
  border: 1px solid var(--md-sys-color-outline-variant);
  border-radius: 12px;
  background: var(--md-sys-color-surface);
  color: var(--md-sys-color-on-surface);
  cursor: pointer;
  font: inherit;
  transition: background 120ms ease, border-color 120ms ease;
  &:hover { background: var(--md-sys-color-surface-container); }
  &.on {
    background: var(--md-sys-color-primary-container);
    color: var(--md-sys-color-on-primary-container);
    border-color: var(--md-sys-color-primary);
  }
}
.date-label { font-weight: 500; font-size: 0.9rem; }
.date-count { font-size: 0.75rem; opacity: 0.85; }
.modal-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
