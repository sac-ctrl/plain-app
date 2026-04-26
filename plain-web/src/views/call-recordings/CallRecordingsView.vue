<template>
  <div class="recordings-page">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <div class="title">{{ $t('call_recordings') }}</div>
    </Teleport>

    <Teleport v-if="isActive" to="#header-end-slot" defer>
      <div class="header-actions">
        <v-icon-button v-tooltip="$t('refresh')" @click="reload">
          <i-lucide:refresh-cw />
        </v-icon-button>
        <v-icon-button v-if="recordings.length > 0" v-tooltip="$t('call_recorder_delete_all')" @click="onDeleteAll">
          <i-lucide:trash-2 />
        </v-icon-button>
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

    <div class="meta-row">
      <span>{{ $t('call_recorder_count', { count: state.totalCount }, state.totalCount) }}</span>
      <span>·</span>
      <span>{{ $t('call_recorder_total_size', { size: formatFileSize(state.totalSize) }) }}</span>
    </div>

    <div v-if="loading && recordings.length === 0" class="empty">{{ $t('loading') }}</div>
    <div v-else-if="recordings.length === 0" class="empty">{{ $t('call_recorder_no_recordings') }}</div>
    <div v-else class="recordings-list">
      <article v-for="r in recordings" :key="r.filename" class="recording-card">
        <header class="recording-head">
          <div class="recording-title">
            <span class="badge" :class="r.source">{{ r.source }}</span>
            <span class="caller">{{ r.displayName || r.filename }}</span>
            <span class="dir-badge" :class="r.direction">{{ r.direction === 'incoming' ? $t('incoming') : $t('outgoing') }}</span>
          </div>
          <div class="recording-actions">
            <a :href="getFileUrl(r.fileId) + '&dl=1'" :download="r.filename" class="action-btn" :title="$t('download')">
              <i-lucide:download />
            </a>
            <button class="action-btn danger" :title="$t('delete')" @click="onDelete(r.filename)">
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
        </div>

        <audio :src="getFileUrl(r.fileId)" controls preload="metadata" class="player" />
      </article>
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
} from '@/lib/api/mutation'
import { getFileUrl } from '@/lib/api/file'
import { formatDateTime, formatFileSize, formatSeconds } from '@/lib/format'

interface ICallRecorderState {
  enabled: boolean; recording: boolean; currentDisplayName: string; currentSource: string
  currentStartedAt: number; totalCount: number; totalSize: number; lastError: string
}
interface ICallRecording {
  id: string; filename: string; displayName: string; source: string; direction: string
  appId: string; appName: string; startedAt: number; endedAt: number; durationMs: number
  sizeBytes: number; fileId: string
}

const { t } = useI18n()
const route = useRoute()
const isActive = computed(() => route.path === '/call-recordings')

const state = ref<ICallRecorderState>({
  enabled: true, recording: false, currentDisplayName: '', currentSource: '',
  currentStartedAt: 0, totalCount: 0, totalSize: 0, lastError: '',
})
const recordings = ref<ICallRecording[]>([])
const loading = ref(true)

const tickNow = ref(Date.now())
let tickHandle: any
const elapsed = computed(() => {
  void tickNow.value
  if (!state.value.recording || !state.value.currentStartedAt) return ''
  const sec = Math.floor((Date.now() - state.value.currentStartedAt) / 1000)
  const m = Math.floor(sec / 60); const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
})

const { mutate: mToggle } = initMutation({ document: setCallRecorderEnabledGQL })
const { mutate: mDel } = initMutation({ document: deleteCallRecordingGQL })
const { mutate: mDelAll } = initMutation({ document: deleteAllCallRecordingsGQL })

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
.header-actions { display: flex; gap: 4px; }

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
}

.recording-head {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
}
.recording-title { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; flex-wrap: wrap; }
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
</style>
