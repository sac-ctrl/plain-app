import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { initMutation, createContactGQL, updateContactGQL } from '@/lib/api/mutation'
import { types } from '@/lib/contact/contact'
import { popModal, pushModal } from '@/components/modal'
import PromptModal from '@/components/PromptModal.vue'
import type { IContact, IContactContentItem, IContactPhoneNumber } from '@/lib/interfaces'

export function useEditContact(data: IContact | undefined, sources: any[], done: () => void) {
  const { t } = useI18n()

  const editItem = reactive({
    firstName: '', middleName: '', lastName: '', prefix: '', suffix: '',
    nickname: '',
    organization: { company: '', title: '' } as { company: string; title: string },
    notes: '', source: '', starred: false,
    phoneNumbers: [] as IContactPhoneNumber[],
    emails: [] as IContactContentItem[],
    addresses: [] as IContactContentItem[],
    websites: [] as IContactContentItem[],
    events: [] as IContactContentItem[],
    ims: [] as IContactContentItem[],
    groupIds: [] as string[],
  })

  const complexName = ref(false)
  const addFieldMenuVisible = ref(false)

  const { mutate: create, loading: createLoading, onDone: createDone } = initMutation({
    document: createContactGQL,
  })
  createDone(() => { done(); popModal() })

  const { mutate: edit, loading: editLoading, onDone: editDone } = initMutation({
    document: updateContactGQL,
  })
  editDone(() => { done(); popModal() })

  const copyContentItems = (items: any[], newItems: any[]) => {
    items.splice(0, items.length)
    for (const item of newItems) items.push({ label: item.label, value: item.value, type: item.type })
  }

  if (data) {
    Object.assign(editItem, { firstName: data.firstName, middleName: data.middleName, lastName: data.lastName, prefix: data.prefix, suffix: data.suffix, notes: data.notes })
    copyContentItems(editItem.phoneNumbers, data.phoneNumbers)
    copyContentItems(editItem.emails, data.emails)
    copyContentItems(editItem.addresses, data.addresses)
    copyContentItems(editItem.websites, data.websites)
    copyContentItems(editItem.events, data.events)
    copyContentItems(editItem.ims, data.ims)
  } else {
    Object.assign(editItem, { firstName: '', middleName: '', lastName: '', prefix: '', suffix: '', notes: '', phoneNumbers: [{ type: 2, value: '', label: '' }], emails: [], addresses: [], websites: [], events: [], ims: [] })
  }

  const onTypeChanged = (item: any) => {
    if (item.type === -1) {
      pushModal(PromptModal, { value: item.label, title: t('custom'), do: (value: string) => { item.label = value } })
    }
  }

  const getTypeLabel = (item: any, type: number, key: string) => {
    return type === -1 ? (item.label || t('custom')) : t(`contact.${key}.${type}`)
  }

  const createTypeOptions = (typeArray: number[], key: string, item: any) => {
    return typeArray.map((type) => ({ value: type, label: getTypeLabel(item, type, key) }))
  }

  const addField = (items: any[]) => { items.push({ type: 1, value: '', label: '' }); addFieldMenuVisible.value = false }
  const deleteField = (items: any[], index: number) => { items.splice(index, 1) }

  function buildPayload() {
    const fix = (item: any) => ({
      type: typeof item.type === 'number' ? item.type : parseInt(String(item.type ?? 1), 10) || 1,
      value: String(item.value ?? '').trim(),
      label: String(item.label ?? '').trim(),
    })
    const org = editItem.organization as any
    return {
      firstName: String(editItem.firstName ?? '').trim(),
      middleName: String(editItem.middleName ?? '').trim(),
      lastName: String(editItem.lastName ?? '').trim(),
      prefix: String(editItem.prefix ?? '').trim(),
      suffix: String(editItem.suffix ?? '').trim(),
      nickname: String(editItem.nickname ?? '').trim(),
      notes: String(editItem.notes ?? '').trim(),
      source: String(editItem.source ?? '').trim(),
      starred: !!editItem.starred,
      phoneNumbers: (editItem.phoneNumbers ?? []).filter((p: any) => p && (p.value ?? '').toString().trim()).map(fix),
      emails: (editItem.emails ?? []).filter((p: any) => p && (p.value ?? '').toString().trim()).map(fix),
      addresses: (editItem.addresses ?? []).filter((p: any) => p && (p.value ?? '').toString().trim()).map(fix),
      websites: (editItem.websites ?? []).filter((p: any) => p && (p.value ?? '').toString().trim()).map(fix),
      events: (editItem.events ?? []).filter((p: any) => p && (p.value ?? '').toString().trim()).map(fix),
      ims: (editItem.ims ?? []).filter((p: any) => p && (p.value ?? '').toString().trim()).map(fix),
      groupIds: editItem.groupIds ?? [],
      organization: org && (String(org.company ?? '').trim() || String(org.title ?? '').trim())
        ? { company: String(org.company ?? '').trim(), title: String(org.title ?? '').trim() }
        : null,
    }
  }

  function doAction() {
    try {
      const input = buildPayload()
      console.log('[contact] sending', JSON.stringify(input))
      if (data) { edit({ id: data.id, input }) }
      else {
        if (!input.source) input.source = sources?.[0]?.name ?? ''
        create({ input })
      }
    } catch (e: any) {
      console.error('[contact] build error', e)
    }
  }

  return {
    editItem, complexName, addFieldMenuVisible, createLoading, editLoading, types,
    onTypeChanged, createTypeOptions, addField, deleteField, doAction,
  }
}
