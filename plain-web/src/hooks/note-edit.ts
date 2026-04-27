import { useRoute } from 'vue-router'
import { onMounted, onUnmounted, ref, computed, watch } from 'vue'
import toast from '@/components/toaster'
import { useI18n } from 'vue-i18n'
import { initLazyQuery, initQuery, noteGQL, tagsGQL } from '@/lib/api/query'
import type { IItemTagsUpdatedEvent, IItemsTagsUpdatedEvent, INote, ITag } from '@/lib/interfaces'
import { formatDateTime } from '@/lib/format'
import { useMarkdown } from '@/hooks/markdown'
import { initMutation, saveNoteGQL } from '@/lib/api/mutation'
import { debounce } from '@/lib/array'
import router, { replacePath, replacePathNoReload } from '@/plugins/router'
import { useMainStore } from '@/stores/main'
import { useTempStore } from '@/stores/temp'
import { useVaultStore } from '@/stores/vault'
import { storeToRefs } from 'pinia'
import { openModal } from '@/components/modal'
import UpdateTagRelationsModal from '@/components/UpdateTagRelationsModal.vue'
import PrivateVaultDialog from '@/components/PrivateVaultDialog.vue'
import emitter from '@/plugins/eventbus'
import { upload as uploadFile } from '@/lib/upload/upload'
import { shortUUID } from '@/lib/strutil'
import type { IUploadItem } from '@/stores/temp'

const dataType = 'NOTE'

export type ViewMode = 'editor' | 'preview' | 'split'
export type SaveStatus = 'idle' | 'unsaved' | 'saving' | 'saved' | 'error' | 'locked'

export function useNoteEdit() {
  const mainStore = useMainStore()
  const { t } = useI18n()
  const route = useRoute()
  const id = ref('')
  const note = ref<INote>()
  const title = ref('')
  const content = ref('')
  const markdown = ref('')
  const notSaved = ref(false)
  const viewMode = ref<ViewMode>('editor')
  const uploadingImage = ref(false)
  const isPrivate = ref(false)
  const isLocked = ref(false)
  const fullscreen = ref(false)
  const showOutline = ref(false)
  const showFindReplace = ref(false)
  const saveStatus = ref<SaveStatus>('idle')
  const { app, urlTokenKey } = storeToRefs(useTempStore())
  const vault = useVaultStore()
  const { unlocked: vaultUnlocked, hasVault } = storeToRefs(vault)

  let suspendWatch = false

  function backToList() {
    const q = router.currentRoute.value.query.q
    replacePath(mainStore, q ? `/notes?q=${q}` : '/notes')
  }

  const { render } = useMarkdown(app, urlTokenKey)

  const { mutate: save, onDone: saveDone, onError: saveError } = initMutation({ document: saveNoteGQL })
  saveDone((r: any) => {
    note.value = r.data.saveNote
    saveStatus.value = 'saved'
    if (!id.value && note.value?.id) {
      id.value = note.value.id
      replacePathNoReload(mainStore, `/notes/${id.value}`)
    }
  })
  saveError(() => { saveStatus.value = 'error' })

  async function buildNoteInput() {
    const rawContent = (content.value ?? '').toString()
    const rawTitle = (title.value ?? '').toString()
    if (isPrivate.value) {
      if (!vaultUnlocked.value) throw new Error('vault_locked')
      const blob = await vault.encrypt({ title: rawTitle, content: rawContent })
      return { title: '', content: '', isPrivate: true, encryptedBlob: blob }
    }
    const safeContent = rawContent.length > 0 ? rawContent : '(empty)'
    const safeTitle = rawTitle.trim() || rawContent.substring(0, 250).trim() || 'Untitled'
    return { content: safeContent, title: safeTitle, isPrivate: false, encryptedBlob: null }
  }

  async function doSave() {
    if (isLocked.value) return
    notSaved.value = false
    saveStatus.value = 'saving'
    try {
      const input = await buildNoteInput()
      save({ id: id.value, input })
    } catch (e: any) {
      saveStatus.value = 'error'
      console.error('[note] build error', e)
    }
  }

  const saveContent = debounce(doSave, 500)
  const saveTitle = debounce(doSave, 500)
  const saveNow = () => doSave()

  let watchersStarted = false
  const watchContent = () => {
    if (watchersStarted) return
    watchersStarted = true
    watch(content, async (value: string) => {
      if (suspendWatch) return
      notSaved.value = true
      saveStatus.value = 'unsaved'
      markdown.value = await render(value)
      saveContent()
    })
    watch(title, () => {
      if (suspendWatch) return
      notSaved.value = true
      saveStatus.value = 'unsaved'
      saveTitle()
    })
  }

  const tags = ref<ITag[]>()
  initQuery({
    handle: (data: { tags: ITag[] }, error: string) => {
      if (error) toast(t(error), 'error')
      else if (data) tags.value = data.tags
    },
    document: tagsGQL,
    variables: { type: dataType },
  })

  async function applyDecryptedBlob(blob: string) {
    const dec = await vault.decrypt(blob)
    if (!dec) {
      isLocked.value = true
      saveStatus.value = 'locked'
      return
    }
    suspendWatch = true
    title.value = dec.title || ''
    content.value = dec.content || ''
    markdown.value = await render(content.value)
    isLocked.value = false
    saveStatus.value = 'saved'
    setTimeout(() => { suspendWatch = false }, 50)
  }

  function promptUnlock(blob: string) {
    openModal(PrivateVaultDialog, {
      mode: hasVault.value ? 'unlock' : 'setup',
      blob,
      onUnlocked: () => { applyDecryptedBlob(blob) },
      onCancel: () => { backToList() },
    })
  }

  const { fetch } = initLazyQuery({
    handle: async (data: { note: INote }, error: string) => {
      if (error) { toast(t(error), 'error'); return }
      if (!data?.note) return
      note.value = data.note
      if (data.note.isPrivate && data.note.encryptedBlob) {
        isPrivate.value = true
        isLocked.value = true
        saveStatus.value = 'locked'
        if (vaultUnlocked.value) {
          await applyDecryptedBlob(data.note.encryptedBlob)
          watchContent()
        } else {
          promptUnlock(data.note.encryptedBlob)
        }
      } else {
        isPrivate.value = false
        suspendWatch = true
        title.value = data.note.title
        content.value = data.note.content
        markdown.value = await render(content.value)
        saveStatus.value = 'saved'
        setTimeout(() => { suspendWatch = false }, 50)
        watchContent()
      }
    },
    document: noteGQL,
    variables: () => ({ id: id.value }),
    options: { fetchPolicy: 'no-cache' },
  })

  function getTime() {
    const time = note?.value?.updatedAt
    return time ? `(${t('updated_at')}: ${formatDateTime(time)})` : ''
  }

  function addToTags() {
    openModal(UpdateTagRelationsModal, {
      type: dataType, tags: tags.value,
      item: { key: note.value?.id, title: '', size: 0 },
      selected: tags.value?.filter((it: ITag) => note.value?.tags.some((tg) => tg.id === it.id)),
    })
  }

  const print = () => window.print()

  async function handlePasteImages(files: File[]): Promise<string[]> {
    if (isPrivate.value) {
      toast(t('vault_image_blocked'), 'warning')
      return []
    }
    uploadingImage.value = true
    const insertedPaths: string[] = []
    try {
      const appDir = app.value.appDir
      const noteImgDir = `${appDir}/note-images`
      for (const file of files) {
        const ext = file.name.split('.').pop() || 'png'
        const fileName = `${shortUUID()}.${ext}`
        const item: IUploadItem = {
          id: shortUUID(),
          dir: noteImgDir,
          fileName,
          file,
          status: 'pending',
          uploadedSize: 0,
          error: '',
          isAppFile: false,
        }
        const result = (await uploadFile(item, false)) as { fileName?: string; error?: string } | undefined
        if (result && result.fileName) {
          insertedPaths.push(`![image](app://note-images/${result.fileName})`)
        }
      }
    } finally {
      uploadingImage.value = false
    }
    return insertedPaths
  }

  function setViewMode(mode: ViewMode) { viewMode.value = mode }
  function toggleFullscreen() { fullscreen.value = !fullscreen.value }
  function toggleOutline() { showOutline.value = !showOutline.value }
  function toggleFindReplace() { showFindReplace.value = !showFindReplace.value }

  async function togglePrivate(v: boolean) {
    if (v) {
      if (!vaultUnlocked.value) {
        openModal(PrivateVaultDialog, {
          mode: hasVault.value ? 'unlock' : 'setup',
          blob: '',
          onUnlocked: () => { isPrivate.value = true; saveNow() },
        })
        return
      }
      isPrivate.value = true
      saveNow()
    } else {
      isPrivate.value = false
      saveNow()
    }
  }

  function exportMarkdown() {
    const safeTitle = (title.value || 'note').replace(/[/\\?%*:|"<>]/g, '_').slice(0, 80)
    const blob = new Blob([content.value], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${safeTitle}.md`
    a.click()
    URL.revokeObjectURL(url)
  }

  function exportHtml() {
    const safeTitle = (title.value || 'note').replace(/[/\\?%*:|"<>]/g, '_').slice(0, 80)
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>${safeTitle}</title>` +
      `<style>body{font-family:system-ui,-apple-system,sans-serif;max-width:780px;margin:32px auto;padding:0 16px;line-height:1.65;color:#222}pre{background:#f5f5f5;padding:12px;border-radius:6px;overflow:auto}code{font-family:Menlo,monospace;background:#f0f0f0;padding:2px 4px;border-radius:3px}img{max-width:100%}blockquote{border-left:4px solid #ddd;margin:0;padding-left:12px;color:#555}table{border-collapse:collapse}th,td{border:1px solid #ccc;padding:6px 10px}</style>` +
      `</head><body>${markdown.value}</body></html>`
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${safeTitle}.html`
    a.click()
    URL.revokeObjectURL(url)
  }

  const stats = computed(() => {
    const text = content.value ?? ''
    const chars = text.length
    const words = text.trim() ? text.trim().split(/\s+/).length : 0
    const lines = text ? text.split('\n').length : 0
    const minutes = Math.max(1, Math.round(words / 220))
    return { chars, words, lines, minutes }
  })

  function lockVault() { vault.lock() }

  onMounted(() => {
    id.value = route.params.id as string
    if (id.value === 'create') id.value = ''
    if (id.value) fetch()
    else watchContent()
    emitter.on('item_tags_updated', (event: IItemTagsUpdatedEvent) => { if (event.type === dataType) fetch() })
    emitter.on('items_tags_updated', (event: IItemsTagsUpdatedEvent) => { if (event.type === dataType) fetch() })
  })

  onUnmounted(() => {
    emitter.off('item_tags_updated')
    emitter.off('items_tags_updated')
  })

  return {
    id, note, title, content, markdown, notSaved, dataType, viewMode,
    uploadingImage, t, backToList, getTime, addToTags, print,
    handlePasteImages, setViewMode, saveNow,
    isPrivate, isLocked, togglePrivate, lockVault,
    fullscreen, toggleFullscreen,
    showOutline, toggleOutline,
    showFindReplace, toggleFindReplace,
    saveStatus, stats,
    exportMarkdown, exportHtml,
  }
}
