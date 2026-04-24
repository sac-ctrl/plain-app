<template>
  <div class="live-call-view">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <div class="title">{{ $t('live_call') }}</div>
    </Teleport>

    <div v-if="state.state === 'idle' || state.state === 'ended'" class="empty-panel">
      <i-lucide:phone-off class="empty-icon" />
      <p class="empty-title">{{ $t('no_active_call') }}</p>
      <p class="empty-hint">{{ $t('no_active_call_hint') }}</p>
      <v-filled-button @click="goHome">{{ $t('back_to_home') }}</v-filled-button>
    </div>

    <div v-else class="call-stage">
      <div class="caller-panel" :class="{ ringing: state.state === 'ringing', active: state.state === 'active' }">
        <div class="avatar-big" :class="state.source">
          <i-lucide:phone-incoming v-if="state.state === 'ringing'" />
          <i-lucide:phone-call v-else />
        </div>
        <div class="caller-meta">
          <div class="row">
            <span class="badge" :class="state.source">{{ sourceLabel }}</span>
            <span v-if="state.state === 'ringing'" class="state-text">{{ $t('incoming_call') }}</span>
            <span v-else class="state-text">{{ $t('on_call') }} · {{ duration }}</span>
          </div>
          <h2 class="display">{{ state.display || state.appName || $t('unknown') }}</h2>
          <div class="meta-row">
            <span v-if="state.appName" class="meta-chip"><i-lucide:smartphone /> {{ state.appName }}</span>
            <span class="meta-chip"><i-lucide:arrow-right v-if="state.direction === 'outgoing'" /><i-lucide:arrow-left v-else /> {{ state.direction === 'outgoing' ? $t('outgoing') : $t('incoming') }}</span>
            <span class="meta-chip" :class="connClass"><i-lucide:radio /> {{ connText }}</span>
          </div>
        </div>
      </div>

      <div v-if="state.state === 'active'" class="audio-panel">
        <div v-if="state.silenced" class="silenced-warning">
          <i-lucide:alert-triangle />
          <span>{{ $t('mic_silenced_warning') }}</span>
        </div>
        <audio ref="audioEl" autoplay playsinline />

        <div class="visualizer" :class="{ playing: !paused && !muted, paused }">
          <span v-for="i in 24" :key="i" class="bar" :style="{ animationDelay: `${i * 0.04}s` }" />
        </div>

        <div class="primary-controls">
          <button class="ctl ctl-large" @click="togglePause" v-tooltip="paused ? $t('resume') : $t('pause')">
            <i-lucide:play v-if="paused" />
            <i-lucide:pause v-else />
          </button>
          <button class="ctl ctl-large" :class="{ on: muted }" @click="toggleLocalMute" v-tooltip="muted ? $t('unmute_local') : $t('mute_local')">
            <i-lucide:volume-x v-if="muted" />
            <i-lucide:volume-2 v-else />
          </button>
          <button class="ctl ctl-large danger" @click="end" v-tooltip="$t('hang_up')">
            <i-lucide:phone-off />
          </button>
        </div>

        <div class="slider-row">
          <i-lucide:volume-1 />
          <input
            type="range"
            min="0"
            max="100"
            v-model.number="volumePct"
            @input="onVolumeChange"
            :aria-label="$t('volume')"
          />
          <i-lucide:volume-2 />
          <span class="vol-num">{{ volumePct }}%</span>
        </div>

        <div class="secondary-controls">
          <button class="chip" :class="{ on: state.muted }" @click="toggleRemoteMute">
            <i-lucide:mic-off v-if="state.muted" /><i-lucide:mic v-else />
            <span>{{ state.muted ? $t('phone_mic_muted') : $t('phone_mic_open') }}</span>
          </button>
        </div>

        <p class="hint">{{ $t('live_call_hint') }}</p>
      </div>

      <div v-else-if="state.state === 'ringing'" class="ringing-panel">
        <p class="hint">{{ $t('answer_to_listen_hint') }}</p>
        <div class="primary-controls">
          <button class="ctl ctl-large danger" @click="end" v-tooltip="$t('decline')">
            <i-lucide:phone-off />
          </button>
          <button class="ctl ctl-large success pulse" @click="accept" v-tooltip="$t('answer_speakerphone')">
            <i-lucide:phone />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import emitter from '@/plugins/eventbus'
import { gqlFetch } from '@/lib/api/gql-client'
import { liveCallStateGQL } from '@/lib/api/query'
import { initMutation, acceptLiveCallGQL, endLiveCallGQL, setLiveCallMutedGQL, ensureLiveCallListeningGQL } from '@/lib/api/mutation'
import type { ILiveCallState } from '@/lib/interfaces'
import { WebRTCClient, type SignalingMessage } from '@/lib/webrtc-client'
import { makeSendWebRTCSignalingFor } from '@/lib/webrtc-signaling'
import { getPhoneIp } from '@/lib/api/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const isActive = computed(() => route.path === '/live-call')

const state = ref<ILiveCallState>({
  state: 'idle', direction: 'incoming', source: 'phone',
  appId: '', appName: '', display: '',
  startedAt: 0, acceptedAt: 0, muted: false, silenced: false,
})

const audioEl = ref<HTMLAudioElement | null>(null)
const paused = ref(false)
const muted = ref(false)            // local playback mute (browser side)
const volumePct = ref(100)
const connState = ref<RTCPeerConnectionState | 'idle'>('idle')

const connClass = computed(() => {
  switch (connState.value) {
    case 'connected': return 'ok'
    case 'connecting': return 'warn'
    case 'failed':
    case 'disconnected':
    case 'closed': return 'bad'
    default: return ''
  }
})
const connText = computed(() => {
  switch (connState.value) {
    case 'connected': return t('audio_connected')
    case 'connecting': return t('connecting')
    case 'failed': return t('audio_failed')
    case 'disconnected': return t('audio_disconnected')
    case 'closed': return t('audio_closed')
    default: return t('audio_idle')
  }
})

const sourceLabel = computed(() => ({
  phone: 'Phone', whatsapp: 'WhatsApp', telegram: 'Telegram', signal: 'Signal', messenger: 'Messenger',
} as any)[state.value.source] || state.value.source)

const tickNow = ref(Date.now())
let tickHandle: any
const duration = computed(() => {
  void tickNow.value
  if (!state.value.acceptedAt) return ''
  const sec = Math.floor((Date.now() - state.value.acceptedAt) / 1000)
  const m = Math.floor(sec / 60); const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
})

const { mutate: mAccept } = initMutation({ document: acceptLiveCallGQL })
const { mutate: mEnd } = initMutation({ document: endLiveCallGQL })
const { mutate: mMute } = initMutation({ document: setLiveCallMutedGQL })
const { mutate: mEnsureListening } = initMutation({ document: ensureLiveCallListeningGQL })

let client: WebRTCClient | null = null
const signalQueue: SignalingMessage[] = []

function attach(stream: MediaStream) {
  if (!audioEl.value) return
  audioEl.value.srcObject = stream
  audioEl.value.muted = muted.value
  audioEl.value.volume = volumePct.value / 100
  if (!paused.value) audioEl.value.play().catch(() => {})
}

function startStreaming() {
  stopStreaming()
  connState.value = 'connecting'
  client = new WebRTCClient({
    sendSignaling: makeSendWebRTCSignalingFor('mic'),
    onStream: attach,
    onConnectionStateChange: (s: RTCPeerConnectionState) => { connState.value = s },
    onError: () => { connState.value = 'failed' },
  })
  client.startSession(true, false, getPhoneIp(), { video: false, audio: true })
  while (signalQueue.length) client.handleSignalingMessage(signalQueue.shift()!)
}

function stopStreaming() {
  if (audioEl.value) { audioEl.value.pause(); audioEl.value.srcObject = null }
  client?.cleanup(); client = null
  connState.value = 'idle'
}

const onSignaling = (msg: any) => {
  if (!msg || msg.stream !== 'mic') return
  if (client) client.handleSignalingMessage(msg)
  else signalQueue.push(msg)
}

function onState(d: any) {
  if (!d) return
  state.value = d
}

function togglePause() {
  paused.value = !paused.value
  if (!audioEl.value) return
  if (paused.value) audioEl.value.pause()
  else audioEl.value.play().catch(() => {})
}
function toggleLocalMute() {
  muted.value = !muted.value
  if (audioEl.value) audioEl.value.muted = muted.value
}
function onVolumeChange() {
  if (audioEl.value) audioEl.value.volume = volumePct.value / 100
}
async function toggleRemoteMute() {
  await mMute({ muted: !state.value.muted })
}
async function accept() { await mAccept() }
async function end() { await mEnd() }
function goHome() { router.push('/') }

watch(() => state.value.state, async (s, prev) => {
  if (s === 'active' && prev !== 'active') {
    await new Promise(r => setTimeout(r, 50))
    startStreaming()
  } else if (s !== 'active') {
    stopStreaming()
  }
})

async function loadOnce() {
  try {
    const r = await gqlFetch<{ liveCallState: ILiveCallState }>(liveCallStateGQL)
    if (!r.errors) {
      state.value = r.data.liveCallState
      if (state.value.state === 'active') {
        // Make sure the phone is actually streaming mic audio (it may not
        // have been started yet if the call was answered on the device).
        try { await mEnsureListening() } catch {}
        await new Promise(r2 => setTimeout(r2, 80))
        startStreaming()
      }
    }
  } catch {}
}

onMounted(() => {
  emitter.on('live_call_state', onState)
  emitter.on('webrtc_signaling', onSignaling)
  loadOnce()
  tickHandle = setInterval(() => { tickNow.value = Date.now() }, 1000)
})
onBeforeUnmount(() => {
  emitter.off('live_call_state', onState)
  emitter.off('webrtc_signaling', onSignaling)
  stopStreaming()
  if (tickHandle) clearInterval(tickHandle)
})
</script>

<style scoped lang="scss">
.live-call-view {
  display: flex; flex-direction: column; flex: 1; min-height: 0;
  padding: 16px; gap: 16px;
}
.title { flex: 1; font-weight: 500; }

.empty-panel {
  margin: auto; max-width: 420px; text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 32px; border-radius: 16px;
  background: var(--md-sys-color-surface-container);
}
.empty-icon { font-size: 56px; color: var(--md-sys-color-on-surface-variant); }
.empty-title { font-size: 1.25rem; font-weight: 600; margin: 0; }
.empty-hint { color: var(--md-sys-color-on-surface-variant); margin: 0; }

.call-stage { display: flex; flex-direction: column; gap: 16px; align-items: stretch; }

.caller-panel {
  display: flex; gap: 18px; align-items: center;
  padding: 22px; border-radius: 18px;
  background: linear-gradient(135deg, var(--md-sys-color-primary), var(--md-sys-color-tertiary));
  color: white;
  box-shadow: 0 8px 24px rgba(0,0,0,0.18);
  &.ringing { animation: ring 1.4s infinite; }
}
@keyframes ring {
  0%, 100% { box-shadow: 0 8px 24px rgba(0,0,0,0.18); }
  50% { box-shadow: 0 0 0 6px rgba(255,255,255,0.18), 0 8px 24px rgba(0,0,0,0.28); }
}
.avatar-big {
  width: 72px; height: 72px; flex-shrink: 0;
  border-radius: 50%; background: rgba(255,255,255,0.22);
  display: flex; align-items: center; justify-content: center;
  svg { width: 36px; height: 36px; }
}
.caller-meta { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; font-size: 0.85rem; }
.state-text { font-weight: 500; opacity: 0.95; }
.badge {
  background: rgba(255,255,255,0.22); padding: 2px 10px; border-radius: 999px;
  text-transform: uppercase; font-weight: 700; font-size: 0.7rem; letter-spacing: 0.5px;
}
.display {
  font-size: 1.6rem; font-weight: 700; margin: 0;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meta-row { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; font-size: 0.8rem; }
.meta-chip {
  display: inline-flex; align-items: center; gap: 4px;
  background: rgba(255,255,255,0.18); padding: 4px 10px; border-radius: 999px;
  svg { width: 14px; height: 14px; }
  &.ok { background: rgba(46,125,50,0.45); }
  &.warn { background: rgba(245,124,0,0.45); }
  &.bad { background: rgba(198,40,40,0.55); }
}

.audio-panel, .ringing-panel {
  display: flex; flex-direction: column; align-items: center; gap: 18px;
  padding: 24px;
  border-radius: 18px;
  background: var(--md-sys-color-surface-container);
}
.silenced-warning {
  display: flex; align-items: center; gap: 8px;
  width: 100%;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--md-sys-color-error-container, #fde7e9);
  color: var(--md-sys-color-on-error-container, #842029);
  font-size: 13px;
  line-height: 1.35;
  svg { flex: 0 0 18px; width: 18px; height: 18px; }
}
.visualizer {
  display: flex; align-items: flex-end; gap: 4px; height: 72px; width: 100%;
  max-width: 480px;
  .bar {
    flex: 1; min-width: 4px; height: 30%; border-radius: 4px;
    background: var(--md-sys-color-primary);
    opacity: 0.35;
  }
  &.playing .bar { animation: vis 1s ease-in-out infinite; opacity: 1; }
  &.paused .bar { opacity: 0.2; }
}
@keyframes vis {
  0%, 100% { transform: scaleY(0.35); }
  50% { transform: scaleY(1); }
}

.primary-controls { display: flex; gap: 18px; }
.ctl {
  border: none; cursor: pointer; padding: 0; border-radius: 999px;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface);
  width: 56px; height: 56px;
  display: inline-flex; align-items: center; justify-content: center;
  transition: transform 0.1s ease, background 0.2s ease;
  svg { width: 26px; height: 26px; }
  &:hover { transform: translateY(-2px); }
  &.on { background: var(--md-sys-color-primary); color: var(--md-sys-color-on-primary); }
  &.danger { background: #c62828; color: white; }
  &.success { background: #2e7d32; color: white; }
}
.ctl-large { width: 64px; height: 64px; svg { width: 30px; height: 30px; } }
.pulse { animation: btn-pulse 1s infinite; }
@keyframes btn-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.06); box-shadow: 0 0 0 6px rgba(46, 125, 50, 0.35); }
}

.slider-row {
  display: flex; align-items: center; gap: 10px;
  width: 100%; max-width: 480px;
  svg { width: 20px; height: 20px; color: var(--md-sys-color-on-surface-variant); }
  input[type='range'] {
    flex: 1; accent-color: var(--md-sys-color-primary);
  }
  .vol-num { min-width: 40px; text-align: right; font-variant-numeric: tabular-nums; color: var(--md-sys-color-on-surface-variant); }
}

.secondary-controls { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.chip {
  display: inline-flex; align-items: center; gap: 6px;
  border: 1px solid var(--md-sys-color-outline-variant);
  background: transparent;
  color: var(--md-sys-color-on-surface);
  padding: 8px 14px; border-radius: 999px;
  cursor: pointer; font-size: 0.85rem;
  svg { width: 16px; height: 16px; }
  &.on { background: var(--md-sys-color-error-container); color: var(--md-sys-color-on-error-container); border-color: transparent; }
}

.hint {
  margin: 0; text-align: center; max-width: 480px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.85rem;
}

@media (max-width: 600px) {
  .caller-panel { flex-direction: column; align-items: flex-start; }
  .display { font-size: 1.3rem; white-space: normal; }
}
</style>
