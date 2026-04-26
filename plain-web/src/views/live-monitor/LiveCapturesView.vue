<template>
  <div class="captures-page">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <div class="title">{{ $t('live_captures_title') }}</div>
    </Teleport>

    <Teleport v-if="isActive" to="#header-end-slot" defer>
      <div class="header-actions">
        <v-segmented-button v-model="view" :options="viewOptions" />
        <v-icon-button v-tooltip="$t('refresh')" @click="reload">
          <i-lucide:refresh-cw />
        </v-icon-button>
        <v-icon-button v-if="captures.length > 0" v-tooltip="$t('live_captures_delete_all')" @click="onDeleteAll">
          <i-lucide:trash-2 />
        </v-icon-button>
      </div>
    </Teleport>

    <div class="filter-row">
      <v-segmented-button v-model="source" :options="sourceOptions" @update:model-value="onSourceChange" />
      <span class="count">{{ total }}</span>
    </div>

    <div v-if="loading && captures.length === 0" class="empty">{{ $t('loading') }}</div>
    <div v-else-if="captures.length === 0" class="empty">{{ $t('live_captures_empty') }}</div>

    <template v-else>
      <div v-if="view === 'grid'" class="captures-grid">
        <article v-for="c in captures" :key="c.id" class="grid-card">
          <div class="grid-media">
            <img v-if="c.kind === 'photo'" :src="getFileUrl(c.fileId)" :alt="c.filename" />
            <video v-else-if="c.kind === 'video'" :src="getFileUrl(c.fileId)" controls preload="metadata" />
            <div v-else class="audio-thumb">
              <i-lucide:music />
              <audio :src="getFileUrl(c.fileId)" controls preload="metadata" />
            </div>
          </div>
          <div class="grid-foot">
            <div class="grid-name" :title="c.filename">{{ c.filename }}</div>
            <div class="grid-meta">
              <span class="badge" :class="c.source">{{ c.source === 'camera' ? $t('live_captures_camera') : $t('live_captures_mic') }}</span>
              <span class="badge kind">{{ kindLabel(c.kind) }}</span>
              <span v-if="c.durationMs" class="grid-meta-text">· {{ formatSeconds(Math.round(c.durationMs / 1000)) }}</span>
              <span class="grid-meta-text">· {{ formatFileSize(c.sizeBytes) }}</span>
            </div>
            <div class="grid-meta-text">{{ formatDateTime(new Date(c.createdAt).toISOString()) }}</div>
          </div>
          <div class="grid-actions">
            <a :href="getFileUrl(c.fileId) + '&dl=1'" :download="c.filename" class="action-btn" :title="$t('download')">
              <i-lucide:download />
            </a>
            <button class="action-btn danger" :title="$t('delete')" @click="onDelete(c.filename)">
              <i-lucide:trash-2 />
            </button>
          </div>
        </article>
      </div>

      <div v-else class="captures-list">
        <article v-for="c in captures" :key="c.id" class="list-card">
          <header class="list-head">
            <div class="list-title">
              <span class="badge" :class="c.source">{{ c.source === 'camera' ? $t('live_captures_camera') : $t('live_captures_mic') }}</span>
              <span class="badge kind">{{ kindLabel(c.kind) }}</span>
              <span class="filename">{{ c.filename }}</span>
            </div>
            <div class="list-actions">
              <a :href="getFileUrl(c.fileId) + '&dl=1'" :download="c.filename" class="action-btn" :title="$t('download')">
                <i-lucide:download />
              </a>
              <button class="action-btn danger" :title="$t('delete')" @click="onDelete(c.filename)">
                <i-lucide:trash-2 />
              </button>
            </div>
          </header>
          <div class="list-meta">
            <div class="meta-cell">
              <span class="cell-label">{{ $t('call_recorder_started_at') }}</span>
              <span class="cell-value">{{ formatDateTime(new Date(c.createdAt).toISOString()) }}</span>
            </div>
            <div v-if="c.durationMs" class="meta-cell">
              <span class="cell-label">{{ $t('call_recorder_duration') }}</span>
              <span class="cell-value">{{ formatSeconds(Math.round(c.durationMs / 1000)) }}</span>
            </div>
            <div class="meta-cell">
              <span class="cell-label">{{ $t('call_recorder_size') }}</span>
              <span class="cell-value">{{ formatFileSize(c.sizeBytes) }}</span>
            </div>
          </div>
          <img v-if="c.kind === 'photo'" :src="getFileUrl(c.fileId)" class="list-photo" />
          <video v-else-if="c.kind === 'video'" :src="getFileUrl(c.fileId)" controls preload="metadata" class="list-video" />
          <audio v-else :src="getFileUrl(c.fileId)" controls preload="metadata" class="list-audio" />
        </article>
      </div>

      <v-pagination
        v-if="total > limit"
        :page="page"
        :go="gotoPage"
        :total="total"
        :limit="limit"
        :page-size="limit"
        :on-change-page-size="onChangePageSize"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import { gqlFetch } from '@/lib/api/gql-client'
import { initMutation } from '@/lib/api/mutation'
import { liveCapturesGQL } from '@/lib/api/query'
import {
  deleteLiveCaptureGQL,
  deleteAllLiveCapturesGQL,
} from '@/lib/api/mutation'
import { getFileUrl } from '@/lib/api/file'
import { formatDateTime, formatFileSize, formatSeconds } from '@/lib/format'
import type { ServerLiveCapture } from '@/lib/api/live-captures'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const isActive = computed(() => route.path === '/live-captures')

type SourceFilter = '' | 'camera' | 'mic'

const captures = ref<ServerLiveCapture[]>([])
const total = ref(0)
const loading = ref(true)
const view = ref<'grid' | 'list'>('grid')
const source = ref<SourceFilter>('')
const page = ref(1)
const limit = ref(50)

const viewOptions = computed(() => [
  { value: 'grid', label: t('live_captures_view_grid') },
  { value: 'list', label: t('live_captures_view_list') },
])
const sourceOptions = computed(() => [
  { value: '', label: t('live_captures_all') },
  { value: 'camera', label: t('live_captures_camera') },
  { value: 'mic', label: t('live_captures_mic') },
])

function kindLabel(k: string) {
  if (k === 'photo') return t('photo')
  if (k === 'video') return t('video')
  return t('audio')
}

const { mutate: mDel } = initMutation({ document: deleteLiveCaptureGQL })
const { mutate: mDelAll } = initMutation({ document: deleteAllLiveCapturesGQL })

async function reload() {
  loading.value = true
  try {
    const r = await gqlFetch<{ liveCaptures: ServerLiveCapture[]; liveCapturesCount: number }>(
      liveCapturesGQL,
      {
        offset: (page.value - 1) * limit.value,
        limit: limit.value,
        source: source.value || null,
      }
    )
    if (!r.errors) {
      captures.value = r.data.liveCaptures
      total.value = r.data.liveCapturesCount
    }
  } finally {
    loading.value = false
  }
}

async function onDelete(filename: string) {
  await mDel({ filename })
  captures.value = captures.value.filter((c) => c.filename !== filename)
  total.value = Math.max(0, total.value - 1)
  toast(t('deleted'))
}

async function onDeleteAll() {
  if (!confirm(t('live_captures_delete_all_confirm'))) return
  await mDelAll({ source: source.value || null })
  await reload()
  toast(t('deleted'))
}

function onSourceChange() {
  page.value = 1
  syncQuery()
  reload()
}

function gotoPage(p: number) {
  page.value = p
  reload()
}

function onChangePageSize(s: number) {
  limit.value = s
  page.value = 1
  reload()
}

function syncQuery() {
  router.replace({ path: route.path, query: { ...route.query, source: source.value || undefined } })
}

const onListChanged = () => { reload() }

onMounted(() => {
  const q = String(route.query.source || '')
  if (q === 'camera' || q === 'mic') source.value = q
  reload()
  emitter.on('live_captures_changed', onListChanged)
})

onUnmounted(() => {
  emitter.off('live_captures_changed', onListChanged)
})

watch(() => route.query.source, (v) => {
  const next = (v === 'camera' || v === 'mic') ? v : ''
  if (next !== source.value) {
    source.value = next as SourceFilter
    page.value = 1
    reload()
  }
})
</script>

<style scoped lang="scss">
.captures-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; height: 100%; }
.title { flex: 1; font-weight: 500; }
.header-actions { display: flex; gap: 8px; align-items: center; }

.filter-row { display: flex; align-items: center; gap: 12px; }
.count {
  padding: 2px 10px; border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.8rem;
}

.empty {
  text-align: center; padding: 32px;
  color: var(--md-sys-color-on-surface-variant);
  background: var(--md-sys-color-surface-container);
  border-radius: 12px;
}

.captures-grid {
  display: grid; gap: 12px;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  padding-bottom: 24px;
}
.grid-card {
  position: relative;
  display: flex; flex-direction: column; gap: 8px;
  padding: 8px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
}
.grid-media {
  width: 100%; aspect-ratio: 16 / 10; background: #000;
  border-radius: 8px; overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  img, video { width: 100%; height: 100%; object-fit: contain; }
}
.audio-thumb {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; width: 100%; height: 100%; color: #fff;
  background: linear-gradient(135deg, var(--md-sys-color-primary-container), var(--md-sys-color-tertiary-container));
  svg { font-size: 36px; color: var(--md-sys-color-on-primary-container); }
  audio { width: 90%; }
}
.grid-foot { display: flex; flex-direction: column; gap: 4px; }
.grid-name {
  font-size: 0.85rem; font-weight: 500; word-break: break-all;
  color: var(--md-sys-color-on-surface);
}
.grid-meta { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; font-size: 0.75rem; }
.grid-meta-text { font-size: 0.75rem; color: var(--md-sys-color-on-surface-variant); }
.grid-actions {
  position: absolute; top: 12px; right: 12px;
  display: flex; gap: 4px;
  opacity: 0; transition: opacity 0.15s ease;
}
.grid-card:hover .grid-actions { opacity: 1; }

.captures-list { display: flex; flex-direction: column; gap: 12px; padding-bottom: 24px; }
.list-card {
  background: var(--md-sys-color-surface-container);
  border-radius: 16px; padding: 16px;
  display: flex; flex-direction: column; gap: 12px;
}
.list-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.list-title { display: flex; align-items: center; gap: 8px; min-width: 0; flex: 1; flex-wrap: wrap; }
.filename {
  font-size: 0.95rem; color: var(--md-sys-color-on-surface);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 100%;
}
.badge {
  text-transform: capitalize; padding: 2px 8px;
  border-radius: 6px; font-size: 0.75rem;
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  &.camera { background: var(--md-sys-color-primary-container); color: var(--md-sys-color-on-primary-container); }
  &.mic { background: var(--md-sys-color-tertiary-container); color: var(--md-sys-color-on-tertiary-container); }
  &.kind { background: var(--md-sys-color-surface-variant); color: var(--md-sys-color-on-surface-variant); }
}
.list-actions { display: flex; gap: 4px; }
.action-btn {
  display: inline-flex; align-items: center; justify-content: center;
  width: 36px; height: 36px; border-radius: 50%;
  background: rgba(0,0,0,0.45); border: none; cursor: pointer;
  color: #fff; text-decoration: none;
  transition: background 0.15s ease;
  &:hover { background: rgba(0,0,0,0.65); }
  &.danger:hover { background: var(--md-sys-color-error); }
}
.list-card .action-btn {
  background: transparent; color: var(--md-sys-color-on-surface-variant);
  &:hover { background: var(--md-sys-color-surface-variant); }
  &.danger:hover { background: var(--md-sys-color-error-container); color: var(--md-sys-color-on-error-container); }
}
.list-meta {
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
.list-photo { max-width: 100%; max-height: 480px; object-fit: contain; border-radius: 8px; background: #000; }
.list-video { width: 100%; max-height: 480px; border-radius: 8px; background: #000; }
.list-audio { width: 100%; }
</style>
