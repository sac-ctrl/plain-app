<template>
  <div class="top-app-bar">
    <v-icon-button v-tooltip="$t('back')" @click="goBack">
      <i-material-symbols:arrow-back-rounded />
    </v-icon-button>
    <div class="title">
      <span v-if="contact">{{ displayName || $t('contacts') }}</span>
      <span v-else-if="loadingContact">{{ $t('loading') }}</span>
      <span v-else>{{ $t('contact_not_found') }}</span>
    </div>
    <div class="actions">
      <v-icon-button v-tooltip="$t('refresh')" :loading="loadingContact || loadingCalls" @click="refresh">
        <i-material-symbols:refresh-rounded />
      </v-icon-button>
      <v-icon-button v-if="contact" v-tooltip="$t('edit')" @click="onEdit">
        <i-material-symbols:edit />
      </v-icon-button>
      <v-icon-button v-if="contact" v-tooltip="$t('add_to_tags')" @click="onAddToTags">
        <i-material-symbols:label-outline-rounded />
      </v-icon-button>
      <v-icon-button v-if="contact" v-tooltip="$t('delete')" @click="onDelete">
        <i-material-symbols:delete-forever-outline-rounded />
      </v-icon-button>
    </div>
  </div>

  <div class="scroll-content">
    <div v-if="loadingContact && !contact" class="loading-block">
      {{ $t('loading') }}
    </div>
    <div v-else-if="!contact" class="loading-block">
      {{ $t('contact_not_found') }}
    </div>
    <div v-else class="detail-wrap">
      <!-- Header card -->
      <section class="detail-card hero-card">
        <div class="hero-left">
          <img v-if="contact.thumbnailId" class="avatar" :src="getFileUrl(contact.thumbnailId)" />
          <div v-else class="avatar avatar-fallback">
            <i-material-symbols:person-rounded />
          </div>
        </div>
        <div class="hero-main">
          <div class="hero-name">
            <span>{{ displayName || $t('no_name') }}</span>
            <i-material-symbols:star-rounded v-if="contact.starred" class="starred-icon" />
          </div>
          <div v-if="contact.notes" class="hero-notes">{{ contact.notes }}</div>
          <div class="hero-meta">
            <span v-if="contact.source" class="meta-pill">{{ contact.source }}</span>
            <span class="meta-pill" v-tooltip="formatDateTime(contact.updatedAt)">
              {{ $t('updated_at') }}: {{ formatTimeAgo(contact.updatedAt) }}
            </span>
            <item-tags :tags="contact.tags" :type="dataType" :only-links="true" />
          </div>
          <div class="hero-actions">
            <v-filled-button v-if="primaryNumber" @click="callNumber(primaryNumber)">
              {{ $t('call') }}
            </v-filled-button>
            <v-outlined-button v-if="primaryNumber" @click="smsNumber(primaryNumber)">
              {{ $t('send_sms') }}
            </v-outlined-button>
            <v-outlined-button v-if="primaryEmail" @click="mailto(primaryEmail)">
              {{ $t('email') }}
            </v-outlined-button>
          </div>
        </div>
      </section>

      <!-- Stats grid -->
      <section class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon"><i-material-symbols:phone-in-talk-outline-rounded /></div>
          <div class="stat-value">{{ stats.total.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('contact_detail.total_calls') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon"><i-material-symbols:schedule-outline-rounded /></div>
          <div class="stat-value">{{ formatSeconds(stats.totalDuration) }}</div>
          <div class="stat-label">{{ $t('contact_detail.total_talk_time') }}</div>
        </div>
        <div class="stat-card stat-incoming">
          <div class="stat-icon"><i-material-symbols:call-received-rounded /></div>
          <div class="stat-value">{{ stats.incoming.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('call_type.1') }}</div>
        </div>
        <div class="stat-card stat-outgoing">
          <div class="stat-icon"><i-material-symbols:call-made-rounded /></div>
          <div class="stat-value">{{ stats.outgoing.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('call_type.2') }}</div>
        </div>
        <div class="stat-card stat-missed">
          <div class="stat-icon"><i-material-symbols:call-missed-rounded /></div>
          <div class="stat-value">{{ stats.missed.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('call_type.3') }}</div>
        </div>
        <div class="stat-card stat-rejected">
          <div class="stat-icon"><i-material-symbols:phone-disabled-rounded /></div>
          <div class="stat-value">{{ stats.rejected.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('call_type.5') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon"><i-material-symbols:hourglass-empty-rounded /></div>
          <div class="stat-value">{{ formatSeconds(stats.avgDuration) }}</div>
          <div class="stat-label">{{ $t('contact_detail.avg_duration') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon"><i-material-symbols:event-available-outline-rounded /></div>
          <div class="stat-value">
            <span v-if="stats.lastCallAt" v-tooltip="formatDateTime(stats.lastCallAt)">{{ formatTimeAgo(stats.lastCallAt) }}</span>
            <span v-else>—</span>
          </div>
          <div class="stat-label">{{ $t('contact_detail.last_call') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon"><i-material-symbols:trending-up-rounded /></div>
          <div class="stat-value">{{ stats.last7d.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('contact_detail.calls_last_7d') }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-icon"><i-material-symbols:calendar-month-outline-rounded /></div>
          <div class="stat-value">{{ stats.last30d.toLocaleString() }}</div>
          <div class="stat-label">{{ $t('contact_detail.calls_last_30d') }}</div>
        </div>
      </section>

      <!-- Contact info -->
      <section v-if="hasAnyInfo" class="detail-card info-card">
        <div class="card-header">{{ $t('contact_detail.contact_info') }}</div>
        <div v-if="contact.phoneNumbers.length" class="info-section">
          <div class="info-section-title">{{ $t('phone_numbers') }}</div>
          <ul class="info-list">
            <li v-for="(it, i) in contact.phoneNumbers" :key="'p' + i">
              <span class="info-label">{{ it.type > 0 ? $t(`contact.phone_number_type.${it.type}`) : it.label || $t('phone_number') }}</span>
              <span class="info-value">{{ it.normalizedNumber || it.value }}</span>
              <span class="info-actions">
                <v-icon-button v-tooltip="$t('make_a_phone_call')" :loading="callLoading && callTarget === (it.normalizedNumber || it.value)" @click="callNumber(it.normalizedNumber || it.value)">
                  <i-material-symbols:call-outline-rounded />
                </v-icon-button>
                <v-icon-button v-tooltip="$t('send_sms')" @click="smsNumber(it.normalizedNumber || it.value)">
                  <i-material-symbols:sms-outline-rounded />
                </v-icon-button>
                <v-icon-button v-tooltip="$t('copy')" @click="copy(it.normalizedNumber || it.value)">
                  <i-material-symbols:content-copy-outline-rounded />
                </v-icon-button>
              </span>
            </li>
          </ul>
        </div>
        <div v-if="contact.emails.length" class="info-section">
          <div class="info-section-title">{{ $t('email') }}</div>
          <ul class="info-list">
            <li v-for="(it, i) in contact.emails" :key="'e' + i">
              <span class="info-label">{{ it.type > 0 ? $t(`contact.email_type.${it.type}`) : it.label || $t('email') }}</span>
              <a class="info-value link" :href="`mailto:${it.value}`">{{ it.value }}</a>
              <span class="info-actions">
                <v-icon-button v-tooltip="$t('copy')" @click="copy(it.value)">
                  <i-material-symbols:content-copy-outline-rounded />
                </v-icon-button>
              </span>
            </li>
          </ul>
        </div>
        <div v-if="contact.addresses.length" class="info-section">
          <div class="info-section-title">{{ $t('contact_detail.addresses') }}</div>
          <ul class="info-list">
            <li v-for="(it, i) in contact.addresses" :key="'a' + i">
              <span class="info-label">{{ it.type > 0 ? $t(`contact.address_type.${it.type}`) : it.label || $t('contact_detail.address') }}</span>
              <span class="info-value">{{ it.value }}</span>
              <span class="info-actions">
                <v-icon-button v-tooltip="$t('copy')" @click="copy(it.value)">
                  <i-material-symbols:content-copy-outline-rounded />
                </v-icon-button>
              </span>
            </li>
          </ul>
        </div>
        <div v-if="contact.websites.length" class="info-section">
          <div class="info-section-title">{{ $t('website') }}</div>
          <ul class="info-list">
            <li v-for="(it, i) in contact.websites" :key="'w' + i">
              <span class="info-label">{{ it.type > 0 ? $t(`contact.website_type.${it.type}`) : it.label || $t('website') }}</span>
              <a class="info-value link" :href="ensureUrl(it.value)" target="_blank" rel="noopener">{{ it.value }}</a>
            </li>
          </ul>
        </div>
        <div v-if="contact.ims.length" class="info-section">
          <div class="info-section-title">{{ $t('im') }}</div>
          <ul class="info-list">
            <li v-for="(it, i) in contact.ims" :key="'i' + i">
              <span class="info-label">{{ it.type > 0 ? $t(`contact.im_type.${it.type}`) : it.label || $t('im') }}</span>
              <span class="info-value">{{ it.value }}</span>
            </li>
          </ul>
        </div>
        <div v-if="contact.events.length" class="info-section">
          <div class="info-section-title">{{ $t('contact_detail.events') }}</div>
          <ul class="info-list">
            <li v-for="(it, i) in contact.events" :key="'ev' + i">
              <span class="info-label">{{ it.type > 0 ? $t(`contact.event_type.${it.type}`) : it.label }}</span>
              <span class="info-value">{{ it.value }}</span>
            </li>
          </ul>
        </div>
      </section>

      <!-- Calls timeline -->
      <section class="detail-card calls-card">
        <div class="card-header">
          <span>{{ $t('contact_detail.call_history') }}</span>
          <span class="card-header-meta" v-if="!loadingCalls">{{ stats.total.toLocaleString() }}</span>
          <span v-else class="card-header-meta">{{ $t('loading') }}</span>
        </div>
        <div v-if="!loadingCalls && groupedCalls.length === 0" class="no-data-placeholder">
          {{ $t('contact_detail.no_calls') }}
        </div>
        <div v-for="group in groupedCalls" :key="group.label" class="call-group">
          <div class="call-group-header">{{ group.label }}</div>
          <ul class="call-timeline">
            <li v-for="c in group.items" :key="c.id" class="call-row" :class="callRowClass(c.type)">
              <div class="call-icon">
                <component :is="callIcon(c.type)" />
              </div>
              <div class="call-meta">
                <div class="call-row-title">
                  <span>{{ $t('call_type.' + c.type) }}</span>
                  <span class="dot">·</span>
                  <span>{{ formatSeconds(c.duration) }}</span>
                  <span v-if="c.accountId" class="dot">·</span>
                  <span v-if="c.accountId" class="sim-pill">SIM {{ c.accountId }}</span>
                </div>
                <div class="call-row-sub">
                  <span v-tooltip="formatDateTime(c.startedAt)">{{ formatDateTimeShort(c.startedAt) }}</span>
                  <span v-if="c.geo && (c.geo.city || c.geo.province || c.geo.isp)" class="geo-text">
                    · {{ [c.geo.city, c.geo.province, c.geo.isp].filter(Boolean).join(' · ') }}
                  </span>
                </div>
              </div>
              <div class="call-actions">
                <v-icon-button v-tooltip="$t('make_a_phone_call')" @click="callNumber(c.number)">
                  <i-material-symbols:call-outline-rounded />
                </v-icon-button>
                <v-icon-button v-tooltip="$t('send_sms')" @click="smsNumber(c.number)">
                  <i-material-symbols:sms-outline-rounded />
                </v-icon-button>
              </div>
            </li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useContactDetail } from './hooks/useContactDetail'
import { getContactFullName } from '@/lib/contact/format'
import { getFileUrl } from '@/lib/api/file'
import { formatDateTime, formatTimeAgo, formatSeconds, formatDate, formatTime } from '@/lib/format'
import { DataType } from '@/lib/data'
import { openModal } from '@/components/modal'
import EditContactModal from '@/components/EditContactModal.vue'
import DeleteConfirm from '@/components/DeleteConfirm.vue'
import UpdateTagRelationsModal from '@/components/UpdateTagRelationsModal.vue'
import SendSmsModal from '@/views/messages/SendSmsModal.vue'
import { callGQL, deleteContactsGQL, initMutation } from '@/lib/api/mutation'
import { useTags } from '@/hooks/tags'
import emitter from '@/plugins/eventbus'
import toast from '@/components/toaster'
import IconCallReceived from '~icons/material-symbols/call-received-rounded'
import IconCallMade from '~icons/material-symbols/call-made-rounded'
import IconCallMissed from '~icons/material-symbols/call-missed-rounded'
import IconCallEnd from '~icons/material-symbols/phone-disabled-rounded'
import IconVoicemail from '~icons/material-symbols/voicemail'
import IconBlock from '~icons/material-symbols/block'

const { t } = useI18n()
const router = useRouter()

const dataType = DataType.CONTACT
const { contact, calls, loadingContact, loadingCalls, refresh } = useContactDetail()
const { tags } = useTags(dataType)

const { mutate: mutateCall, loading: callLoading } = initMutation({ document: callGQL })
const callTarget = ref<string>('')

function mailto(email: string) {
  if (!email) return
  window.location.href = `mailto:${email}`
}

const displayName = computed(() => (contact.value ? getContactFullName(contact.value) : ''))
const primaryNumber = computed(() => {
  const list = contact.value?.phoneNumbers || []
  if (!list.length) return ''
  return list[0].normalizedNumber || list[0].value
})
const primaryEmail = computed(() => contact.value?.emails?.[0]?.value || '')

const hasAnyInfo = computed(() => {
  const c = contact.value
  if (!c) return false
  return (
    c.phoneNumbers.length || c.emails.length || c.addresses.length ||
    c.websites.length || c.ims.length || c.events.length
  )
})

const stats = computed(() => {
  const list = calls.value
  const total = list.length
  let incoming = 0, outgoing = 0, missed = 0, rejected = 0, totalDuration = 0
  let last7d = 0, last30d = 0
  let lastCallAt: string | null = null
  const now = Date.now()
  const week = 7 * 24 * 3600 * 1000
  const month = 30 * 24 * 3600 * 1000
  for (const c of list) {
    totalDuration += c.duration || 0
    if (c.type === 1) incoming++
    else if (c.type === 2) outgoing++
    else if (c.type === 3) missed++
    else if (c.type === 5) rejected++
    const ts = new Date(c.startedAt).getTime()
    if (!lastCallAt || ts > new Date(lastCallAt).getTime()) lastCallAt = c.startedAt
    const diff = now - ts
    if (diff <= week) last7d++
    if (diff <= month) last30d++
  }
  const answered = incoming + outgoing
  const avgDuration = answered > 0 ? Math.round(totalDuration / answered) : 0
  return { total, incoming, outgoing, missed, rejected, totalDuration, avgDuration, last7d, last30d, lastCallAt }
})

function dayKey(s: string): string {
  const d = new Date(s)
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

function dayLabel(s: string): string {
  const d = new Date(s)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  const isToday = d.toDateString() === today.toDateString()
  const isYesterday = d.toDateString() === yesterday.toDateString()
  if (isToday) return t('today')
  if (isYesterday) return t('yesterday')
  return formatDate(s)
}

const groupedCalls = computed(() => {
  const groups: { key: string; label: string; items: typeof calls.value }[] = []
  const map = new Map<string, { label: string; items: typeof calls.value }>()
  for (const c of calls.value) {
    const k = dayKey(c.startedAt)
    if (!map.has(k)) {
      const g = { label: dayLabel(c.startedAt), items: [] as typeof calls.value }
      map.set(k, g)
      groups.push({ key: k, label: g.label, items: g.items })
    }
    map.get(k)!.items.push(c)
  }
  return groups
})

function formatDateTimeShort(s: string): string {
  return formatTime(s)
}

function callIcon(type: number) {
  switch (type) {
    case 1: return markRaw(IconCallReceived)
    case 2: return markRaw(IconCallMade)
    case 3: return markRaw(IconCallMissed)
    case 4: return markRaw(IconVoicemail)
    case 5: return markRaw(IconCallEnd)
    case 6: return markRaw(IconBlock)
    default: return markRaw(IconCallReceived)
  }
}

function callRowClass(type: number): string {
  if (type === 1) return 'is-incoming'
  if (type === 2) return 'is-outgoing'
  if (type === 3) return 'is-missed'
  if (type === 5) return 'is-rejected'
  if (type === 6) return 'is-blocked'
  return ''
}

function callNumber(num: string) {
  if (!num) return
  callTarget.value = num
  mutateCall({ number: num })
}

function smsNumber(num: string) {
  if (!num) return
  openModal(SendSmsModal, { number: num })
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    toast(t('copied'), 'success')
  } catch (_) {
    toast(text, 'info')
  }
}

function ensureUrl(u: string): string {
  if (!u) return '#'
  if (/^https?:\/\//i.test(u)) return u
  return `https://${u}`
}

function onEdit() {
  if (!contact.value) return
  openModal(EditContactModal, {
    data: contact.value,
    sources: ref([]),
    done: refresh,
  })
}

function onAddToTags() {
  if (!contact.value) return
  openModal(UpdateTagRelationsModal, {
    type: dataType,
    tags: tags.value,
    item: { key: contact.value.id, title: displayName.value, size: 0 },
    selected: tags.value.filter((t) => contact.value!.tags.some((it) => it.id === t.id)),
  })
}

function onDelete() {
  if (!contact.value) return
  openModal(DeleteConfirm, {
    id: contact.value.id,
    name: displayName.value,
    gql: deleteContactsGQL,
    variables: () => ({ query: `ids:${contact.value!.id}` }),
    typeName: 'Contact',
    done: () => {
      emitter.emit('refetch_tags', dataType)
      router.push('/contacts')
    },
  })
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/contacts')
}
</script>

<style lang="scss" scoped>
.scroll-content {
  padding: 0;
}
.loading-block {
  text-align: center;
  padding: 64px 16px;
  color: var(--md-sys-color-on-surface-variant);
}
.detail-wrap {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1100px;
  margin: 0 auto;
  width: 100%;
}
.detail-card {
  background: var(--md-sys-color-surface-container-low);
  border-radius: 16px;
  padding: 20px;
}
.card-header {
  font-size: 14px;
  font-weight: 600;
  color: var(--md-sys-color-on-surface-variant);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header-meta {
  font-size: 12px;
  font-weight: 500;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface);
  padding: 2px 10px;
  border-radius: 999px;
}
.hero-card {
  display: flex;
  gap: 20px;
  align-items: stretch;
}
.hero-left .avatar {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--md-sys-color-surface-container-high);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  color: var(--md-sys-color-on-surface-variant);
}
.avatar-fallback svg {
  width: 56px;
  height: 56px;
}
.hero-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hero-name {
  font-size: 26px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
}
.starred-icon {
  color: #f5b400;
}
.hero-notes {
  color: var(--md-sys-color-on-surface-variant);
  font-size: 14px;
}
.hero-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.meta-pill {
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
}
.hero-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
}
.stat-card {
  background: var(--md-sys-color-surface-container-low);
  border-radius: 16px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  position: relative;
}
.stat-icon {
  font-size: 20px;
  color: var(--md-sys-color-on-surface-variant);
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--md-sys-color-on-surface);
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: var(--md-sys-color-on-surface-variant);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.stat-incoming .stat-icon { color: #2e7d32; }
.stat-outgoing .stat-icon { color: #1565c0; }
.stat-missed .stat-icon { color: #c62828; }
.stat-rejected .stat-icon { color: #ef6c00; }

.info-section { padding: 12px 0; border-top: 1px solid var(--md-sys-color-outline-variant); }
.info-section:first-of-type { border-top: 0; padding-top: 0; }
.info-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--md-sys-color-on-surface-variant);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}
.info-list { list-style: none; margin: 0; padding: 0; }
.info-list li {
  display: grid;
  grid-template-columns: 140px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 6px 0;
}
.info-label {
  color: var(--md-sys-color-on-surface-variant);
  font-size: 13px;
}
.info-value {
  color: var(--md-sys-color-on-surface);
  font-size: 14px;
  word-break: break-all;
}
.info-value.link {
  color: var(--md-sys-color-primary);
  text-decoration: none;
}
.info-value.link:hover { text-decoration: underline; }
.info-actions { display: flex; gap: 4px; }

.calls-card .no-data-placeholder {
  padding: 32px 0;
  text-align: center;
  color: var(--md-sys-color-on-surface-variant);
}
.call-group { padding-top: 8px; }
.call-group-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--md-sys-color-on-surface-variant);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 8px 4px;
}
.call-timeline { list-style: none; margin: 0; padding: 0; }
.call-row {
  display: grid;
  grid-template-columns: 36px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 10px 8px;
  border-radius: 12px;
  transition: background-color 0.15s ease;
}
.call-row:hover { background: var(--md-sys-color-surface-container); }
.call-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 18px;
}
.call-row.is-incoming .call-icon { background: rgba(46,125,50,0.16); color: #2e7d32; }
.call-row.is-outgoing .call-icon { background: rgba(21,101,192,0.16); color: #1565c0; }
.call-row.is-missed .call-icon { background: rgba(198,40,40,0.16); color: #c62828; }
.call-row.is-rejected .call-icon { background: rgba(239,108,0,0.16); color: #ef6c00; }
.call-row.is-blocked .call-icon { background: rgba(97,97,97,0.16); color: #616161; }
.call-row-title {
  font-size: 14px;
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--md-sys-color-on-surface);
}
.call-row-sub {
  font-size: 12px;
  color: var(--md-sys-color-on-surface-variant);
  margin-top: 2px;
}
.dot { color: var(--md-sys-color-on-surface-variant); opacity: 0.7; }
.sim-pill {
  font-size: 11px;
  background: var(--md-sys-color-surface-container-high);
  color: var(--md-sys-color-on-surface);
  padding: 1px 8px;
  border-radius: 999px;
}
.geo-text { color: var(--md-sys-color-on-surface-variant); }
.call-actions { display: flex; gap: 4px; }
@media (max-width: 720px) {
  .hero-card { flex-direction: column; align-items: center; text-align: center; }
  .hero-name { justify-content: center; }
  .hero-meta { justify-content: center; }
  .hero-actions { justify-content: center; }
  .info-list li { grid-template-columns: 1fr; }
  .info-actions { justify-content: flex-end; }
}
</style>
