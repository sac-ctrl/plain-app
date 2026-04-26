import { computed, onActivated, onDeactivated, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { contactsGQL, callsGQL } from '@/lib/api/query'
import { gqlFetch } from '@/lib/api/gql-client'
import type { ICall, IContact } from '@/lib/interfaces'
import emitter from '@/plugins/eventbus'

interface ContactsResp { contacts: IContact[] }
interface CallsResp { calls: ICall[]; callCount: number }

function lastDigits(num: string, n = 7): string {
  const digits = (num || '').replace(/\D+/g, '')
  if (digits.length <= n) return digits
  return digits.slice(-n)
}

function normalizeForCompare(num: string): string {
  return (num || '').replace(/\D+/g, '')
}

export function useContactDetail() {
  const route = useRoute()
  const contactId = computed(() => String(route.params.id || ''))

  const contact = ref<IContact | null>(null)
  const calls = ref<ICall[]>([])
  const loadingContact = ref(false)
  const loadingCalls = ref(false)
  const errorMsg = ref('')

  let active = true

  async function loadContact() {
    const id = contactId.value
    if (!id) {
      contact.value = null
      return
    }
    loadingContact.value = true
    errorMsg.value = ''
    try {
      const r = await gqlFetch<ContactsResp>(contactsGQL, {
        offset: 0,
        limit: 1,
        query: `id:${id}`,
      })
      if (r.errors?.length) {
        errorMsg.value = r.errors[0].message
        contact.value = null
      } else {
        contact.value = r.data?.contacts?.[0] ?? null
      }
    } catch (e: any) {
      errorMsg.value = e?.message || String(e)
      contact.value = null
    } finally {
      loadingContact.value = false
    }
  }

  async function loadCalls() {
    const c = contact.value
    if (!c) {
      calls.value = []
      return
    }
    const numbers = (c.phoneNumbers || []).map((p) => p.normalizedNumber || p.value).filter(Boolean)
    if (numbers.length === 0) {
      calls.value = []
      return
    }
    loadingCalls.value = true
    try {
      const seen = new Map<string, ICall>()
      const tokens = Array.from(new Set(numbers.map((n) => lastDigits(n)).filter((s) => s.length > 0)))
      const requests = tokens.map((tok) =>
        gqlFetch<CallsResp>(callsGQL, { offset: 0, limit: 1000, query: `text:${tok}` })
      )
      const results = await Promise.all(requests)
      const normalizedSet = new Set(numbers.map(normalizeForCompare).filter((x) => x.length > 0))
      for (const r of results) {
        const list = r.data?.calls || []
        for (const call of list) {
          const callDigits = normalizeForCompare(call.number)
          let match = false
          if (callDigits) {
            for (const target of normalizedSet) {
              if (target.length >= 7 && callDigits.length >= 7) {
                if (callDigits.endsWith(target.slice(-7)) || target.endsWith(callDigits.slice(-7))) {
                  match = true
                  break
                }
              } else if (callDigits === target) {
                match = true
                break
              }
            }
          }
          if (match && !seen.has(call.id)) seen.set(call.id, call)
        }
      }
      calls.value = Array.from(seen.values()).sort(
        (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime()
      )
    } finally {
      loadingCalls.value = false
    }
  }

  async function refresh() {
    await loadContact()
    if (active) await loadCalls()
  }

  watch(contactId, () => {
    if (active) refresh()
  }, { immediate: true })

  onActivated(() => {
    active = true
    refresh()
  })
  onDeactivated(() => {
    active = false
  })

  const onCallsDeleted = () => { if (active) loadCalls() }
  emitter.on('calls_deleted', onCallsDeleted)

  return {
    contactId,
    contact,
    calls,
    loadingContact,
    loadingCalls,
    errorMsg,
    refresh,
    reloadCalls: loadCalls,
  }
}
