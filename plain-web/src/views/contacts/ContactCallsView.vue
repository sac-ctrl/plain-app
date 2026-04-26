<template>
  <div class="contact-calls">
    <Teleport v-if="isActive" to="#header-start-slot" defer>
      <v-icon-button v-tooltip="$t('back')" @click="goBack">
        <i-lucide:arrow-left />
      </v-icon-button>
      <div class="title">{{ contact ? fullName(contact) : $t('contacts') }}</div>
    </Teleport>

    <Teleport v-if="isActive && contact" to="#header-end-slot" defer>
      <div class="header-actions">
        <v-icon-button v-tooltip="$t('refresh')" @click="reload">
          <i-lucide:refresh-cw />
        </v-icon-button>
        <v-icon-button v-tooltip="$t('edit')" @click="onEdit">
          <i-lucide:pencil />
        </v-icon-button>
        <v-icon-button v-tooltip="$t('add_to_tags')" @click="onAddToTags">
          <i-lucide:tag />
        </v-icon-button>
        <v-icon-button v-tooltip="$t('delete')" class="danger" @click="onDeleteContact">
          <i-lucide:trash-2 />
        </v-icon-button>
      </div>
    </Teleport>

    <div v-if="loadingContact && !contact" class="empty">{{ $t('loading') }}</div>
    <div v-else-if="!contact" class="empty">{{ $t('no_data') }}</div>
    <template v-else>
      <header class="contact-card">
        <img v-if="contact.thumbnailId" class="avatar" :src="getFileUrl(contact.thumbnailId)" />
        <div v-else class="avatar avatar-fallback">
          <i-material-symbols:contact-page-outline-rounded />
        </div>
        <div class="contact-info">
          <h2 class="contact-name">{{ fullName(contact) }}</h2>
          <p v-if="contact.notes" class="contact-notes">{{ contact.notes }}</p>
          <ul class="phone-list">
            <li v-for="(p, i) in contact.phoneNumbers" :key="i" class="phone-row">
              <span class="phone-type">
                {{ p.type > 0 ? $t(`contact.phone_number_type.${p.type}`) : (p.label || '') }}
              </span>
              <span class="phone-value">{{ p.normalizedNumber || p.value }}</span>
              <v-icon-button
                v-tooltip="$t('send_sms')"
                @click="onSendSms(p.normalizedNumber || p.value)"
              >
                <i-material-symbols:sms-outline-rounded />
              </v-icon-button>
              <v-icon-button
                v-tooltip="$t('make_a_phone_call')"
                :loading="callingNumber === (p.normalizedNumber || p.value)"
                @click="onCallNumber(p.normalizedNumber || p.value)"
              >
                <i-material-symbols:call-outline-rounded />
              </v-icon-button>
            </li>
          </ul>
          <p v-if="!contact.phoneNumbers.length" class="no-numbers">
            {{ $t('no_data') }}
          </p>
        </div>
      </header>

      <section class="calls-section">
        <header class="calls-header">
          <h3 class="calls-heading">
            {{ $t('calls') }}
            <span class="count">{{ calls.length }}</span>
          </h3>
        </header>

        <div v-if="loadingCalls && calls.length === 0" class="empty">{{ $t('loading') }}</div>
        <div v-else-if="calls.length === 0" class="empty">{{ $t('no_data') }}</div>
        <ul v-else class="calls-list">
          <li v-for="c in calls" :key="c.id" class="call-row">
            <div class="call-icon" :class="callTypeClass(c.type)">
              <i-material-symbols:call-received v-if="c.type === 1" />
              <i-material-symbols:call-made v-else-if="c.type === 2" />
              <i-material-symbols:call-missed v-else-if="c.type === 3" />
              <i-material-symbols:voicemail v-else-if="c.type === 4" />
              <i-material-symbols:block v-else-if="c.type === 5 || c.type === 6" />
              <i-material-symbols:phone-iphone v-else />
            </div>
            <div class="call-main">
              <div class="call-title">
                <span class="call-type">{{ $t('call_type.' + c.type) }}</span>
                <span class="call-number">{{ c.number }}</span>
              </div>
              <div class="call-meta">
                <span v-tooltip="formatDateTime(c.startedAt)">{{ formatTimeAgo(c.startedAt) }}</span>
                <span>· {{ formatSeconds(c.duration) }}</span>
                <span v-if="getGeoText(c.geo)">· {{ getGeoText(c.geo) }}</span>
              </div>
            </div>
            <div class="call-actions">
              <v-icon-button v-tooltip="$t('send_sms')" @click="onSendSms(c.number)">
                <i-material-symbols:sms-outline-rounded />
              </v-icon-button>
              <v-icon-button
                v-tooltip="$t('make_a_phone_call')"
                :loading="callingNumber === c.number"
                @click="onCallNumber(c.number)"
              >
                <i-material-symbols:call-outline-rounded />
              </v-icon-button>
              <v-icon-button v-tooltip="$t('delete')" @click="onDeleteCall(c)">
                <i-material-symbols:delete-forever-outline-rounded />
              </v-icon-button>
            </div>
          </li>
        </ul>
      </section>
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
import { contactsGQL, callsGQL } from '@/lib/api/query'
import {
  initMutation,
  callGQL,
  deleteCallGQL,
  deleteContactsGQL,
} from '@/lib/api/mutation'
import { getFileUrl } from '@/lib/api/file'
import { getContactFullName } from '@/lib/contact/format'
import { formatDateTime, formatSeconds, formatTimeAgo } from '@/lib/format'
import type { ICall, ICallGeo, IContact } from '@/lib/interfaces'
import { openModal } from '@/components/modal'
import EditContactModal from '@/components/EditContactModal.vue'
import UpdateTagRelationsModal from '@/components/UpdateTagRelationsModal.vue'
import SendSmsModal from '@/views/messages/SendSmsModal.vue'
import DeleteConfirm from '@/components/DeleteConfirm.vue'
import { DataType } from '@/lib/data'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isActive = computed(() => route.path.startsWith('/contacts/') && route.path.endsWith('/calls'))
const contactId = computed(() => String(route.params.id || ''))

const contact = ref<IContact | null>(null)
const loadingContact = ref(true)
const calls = ref<ICall[]>([])
const loadingCalls = ref(false)
const callingNumber = ref<string | null>(null)

const fullName = getContactFullName

const { mutate: mutateCall, loading: callMutating, onDone: onCallDone, onError: onCallError } =
  initMutation({ document: callGQL })
const { mutate: mutateDeleteCall } = initMutation({ document: deleteCallGQL })

onCallDone(() => { callingNumber.value = null })
onCallError(() => { callingNumber.value = null })
void callMutating

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/contacts')
}

function callTypeClass(type: number) {
  if (type === 1) return 'incoming'
  if (type === 2) return 'outgoing'
  if (type === 3) return 'missed'
  if (type === 5 || type === 6) return 'rejected'
  return ''
}

function getGeoText(geo: ICallGeo | null | undefined) {
  if (!geo) return ''
  const texts: string[] = []
  if (geo.isp) texts.push(t('phone_isp_type.' + geo.isp))
  if (geo.city === geo.province) texts.push(geo.city)
  else texts.push(`${geo.province || ''}${geo.city || ''}`)
  return texts.filter(Boolean).join(', ')
}

/**
 * Strip everything but digits, then keep the trailing 7 (or fewer) so we can
 * match locally formatted variants of the same number ("+1 (415) 555-2671" vs
 * "5552671"). 7 digits is short enough to ignore country/area-code differences
 * but long enough that collisions are rare in a single user's call log.
 */
function tailDigits(num: string): string {
  const digits = (num || '').replace(/\D+/g, '')
  if (!digits) return ''
  return digits.length <= 7 ? digits : digits.slice(-7)
}

async function fetchContact() {
  loadingContact.value = true
  try {
    const id = contactId.value
    if (!id) { contact.value = null; return }
    const r = await gqlFetch<{ contacts: IContact[] }>(contactsGQL, {
      offset: 0, limit: 1, query: `ids:${id}`,
    })
    contact.value = (!r.errors && r.data.contacts.length) ? r.data.contacts[0] : null
  } catch (_) {
    contact.value = null
  } finally {
    loadingContact.value = false
  }
}

async function fetchCalls() {
  if (!contact.value) { calls.value = []; return }
  const numbers = contact.value.phoneNumbers
    .map((p) => tailDigits(p.normalizedNumber || p.value))
    .filter((n) => n.length > 0)
  const uniqueTails = Array.from(new Set(numbers))
  if (uniqueTails.length === 0) { calls.value = []; return }
  loadingCalls.value = true
  const merged = new Map<string, ICall>()
  try {
    await Promise.all(uniqueTails.map(async (tail) => {
      const r = await gqlFetch<{ calls: ICall[] }>(callsGQL, {
        offset: 0, limit: 500, query: `text:${tail}`,
      })
      if (!r.errors) {
        for (const c of r.data.calls) {
          if (!merged.has(c.id)) merged.set(c.id, c)
        }
      }
    }))
    calls.value = Array.from(merged.values()).sort((a, b) => {
      const ta = new Date(a.startedAt).getTime()
      const tb = new Date(b.startedAt).getTime()
      return tb - ta
    })
  } finally {
    loadingCalls.value = false
  }
}

async function reload() {
  await fetchContact()
  await fetchCalls()
}

function onCallNumber(number: string) {
  if (!number) return
  callingNumber.value = number
  mutateCall({ number })
}

function onSendSms(number: string) {
  if (!number) return
  openModal(SendSmsModal, { number })
}

function onDeleteCall(c: ICall) {
  openModal(DeleteConfirm, {
    id: c.id,
    name: c.number,
    gql: deleteCallGQL,
    variables: () => ({ query: `ids:${c.id}` }),
    typeName: 'Call',
    done: () => {
      calls.value = calls.value.filter((x) => x.id !== c.id)
      toast(t('deleted'))
    },
  })
  void mutateDeleteCall
}

function onEdit() {
  if (!contact.value) return
  openModal(EditContactModal, { data: contact.value, sources: ref([]), done: reload })
}

function onAddToTags() {
  if (!contact.value) return
  openModal(UpdateTagRelationsModal, {
    type: DataType.CONTACT,
    tags: [],
    item: { key: contact.value.id, title: '', size: 0 },
    selected: contact.value.tags,
  })
}

function onDeleteContact() {
  if (!contact.value) return
  const c = contact.value
  openModal(DeleteConfirm, {
    id: c.id,
    name: fullName(c),
    gql: deleteContactsGQL,
    variables: () => ({ query: `ids:${c.id}` }),
    typeName: 'Contact',
    done: () => {
      toast(t('deleted'))
      router.push('/contacts')
    },
  })
}

const onCallsChanged = () => { fetchCalls() }
const onContactsChanged = () => { reload() }

onMounted(async () => {
  await reload()
  emitter.on('calls_deleted', onCallsChanged)
  emitter.on('contacts_changed', onContactsChanged)
})

onUnmounted(() => {
  emitter.off('calls_deleted', onCallsChanged)
  emitter.off('contacts_changed', onContactsChanged)
})

watch(contactId, () => {
  reload()
})
</script>

<style scoped lang="scss">
.contact-calls { padding: 16px; display: flex; flex-direction: column; gap: 16px; overflow-y: auto; height: 100%; }
.title { flex: 1; font-weight: 500; padding-left: 8px; }
.header-actions { display: flex; gap: 4px; align-items: center; }
.header-actions .danger { color: var(--md-sys-color-error); }

.empty {
  text-align: center; padding: 32px;
  color: var(--md-sys-color-on-surface-variant);
  background: var(--md-sys-color-surface-container);
  border-radius: 12px;
}

.contact-card {
  display: flex; gap: 16px; align-items: flex-start;
  padding: 16px; border-radius: 16px;
  background: var(--md-sys-color-surface-container);
}
.avatar {
  width: 72px; height: 72px; border-radius: 50%;
  object-fit: cover; flex-shrink: 0;
  background: var(--md-sys-color-surface-variant);
}
.avatar-fallback {
  display: inline-flex; align-items: center; justify-content: center;
  color: var(--md-sys-color-on-surface-variant);
  svg { font-size: 40px; }
}
.contact-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.contact-name { margin: 0; font-size: 1.3rem; font-weight: 500; color: var(--md-sys-color-on-surface); }
.contact-notes { margin: 0; color: var(--md-sys-color-on-surface-variant); font-size: 0.9rem; }
.phone-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.phone-row {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 4px 0;
}
.phone-type {
  font-size: 0.75rem; padding: 2px 8px; border-radius: 6px;
  background: var(--md-sys-color-secondary-container);
  color: var(--md-sys-color-on-secondary-container);
  text-transform: capitalize;
}
.phone-value {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.95rem;
  color: var(--md-sys-color-on-surface);
}
.no-numbers { color: var(--md-sys-color-on-surface-variant); margin: 0; font-size: 0.85rem; }

.calls-section { display: flex; flex-direction: column; gap: 8px; padding-bottom: 24px; }
.calls-header { display: flex; align-items: center; justify-content: space-between; padding: 0 4px; }
.calls-heading {
  margin: 0; font-size: 1rem; font-weight: 500;
  display: inline-flex; align-items: center; gap: 8px;
}
.count {
  padding: 2px 10px; border-radius: 999px;
  background: var(--md-sys-color-surface-container);
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.75rem;
}

.calls-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 6px; }
.call-row {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 14px; border-radius: 12px;
  background: var(--md-sys-color-surface-container);
}
.call-icon {
  width: 36px; height: 36px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--md-sys-color-surface-variant);
  color: var(--md-sys-color-on-surface-variant);
  flex-shrink: 0;
  &.incoming { background: var(--md-sys-color-secondary-container); color: var(--md-sys-color-on-secondary-container); }
  &.outgoing { background: var(--md-sys-color-tertiary-container); color: var(--md-sys-color-on-tertiary-container); }
  &.missed, &.rejected { background: var(--md-sys-color-error-container, #ffdad6); color: var(--md-sys-color-on-error-container, #410002); }
  svg { font-size: 20px; }
}
.call-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.call-title {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
  font-size: 0.95rem; color: var(--md-sys-color-on-surface);
}
.call-type {
  font-size: 0.7rem; text-transform: uppercase; letter-spacing: 0.5px;
  color: var(--md-sys-color-on-surface-variant);
}
.call-number {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 500;
}
.call-meta {
  font-size: 0.8rem; color: var(--md-sys-color-on-surface-variant);
  display: flex; gap: 4px; flex-wrap: wrap;
}
.call-actions { display: flex; gap: 2px; align-items: center; flex-shrink: 0; }
</style>
