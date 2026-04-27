<template>
  <v-modal @close="cancel">
    <template #headline>
      <span class="hl"><i-material-symbols:tune-rounded /> Feedback security settings</span>
    </template>
    <template #content>
      <p class="intro">
        Change the question and answer that opens this dashboard. Keep the answer somewhere safe — without it
        you cannot get back in.
      </p>

      <div class="form-row">
        <v-text-field
          v-model="currentAns"
          type="password"
          label="Current answer (to confirm it's you)"
          :error="!!err.cur"
          :error-text="err.cur"
        />
      </div>

      <div class="form-row">
        <v-text-field
          v-model="newQ"
          label="New question"
          :error="!!err.q"
          :error-text="err.q"
        />
      </div>

      <div class="form-row">
        <v-text-field
          v-model="newA"
          type="password"
          label="New answer"
          :error="!!err.a"
          :error-text="err.a"
        />
      </div>

      <div class="form-row">
        <v-text-field
          v-model="newA2"
          type="password"
          label="Confirm new answer"
          :error="!!err.a2"
          :error-text="err.a2"
        />
      </div>
    </template>
    <template #actions>
      <v-outlined-button @click="cancel">Cancel</v-outlined-button>
      <v-filled-button :loading="loading" @click="save">Save</v-filled-button>
    </template>
  </v-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { popModal } from '@/components/modal'
import { useDisguiseStore } from '@/stores/disguise'
import toast from '@/components/toaster'

const disguise = useDisguiseStore()
const currentAns = ref('')
const newQ = ref(disguise.currentQuestion)
const newA = ref('')
const newA2 = ref('')
const err = ref<{ cur?: string; q?: string; a?: string; a2?: string }>({})
const loading = ref(false)

function cancel() {
  popModal()
}

async function save() {
  err.value = {}
  if (!currentAns.value.trim()) err.value.cur = 'Required'
  if (!newQ.value.trim()) err.value.q = 'Required'
  if (!newA.value.trim()) err.value.a = 'Required'
  if (newA.value !== newA2.value) err.value.a2 = 'Answers do not match'
  if (Object.keys(err.value).length) return
  loading.value = true
  try {
    const ok = await disguise.updateSecurity(currentAns.value, newQ.value, newA.value)
    if (!ok) {
      err.value.cur = 'Current answer is wrong'
      return
    }
    toast('Saved.', 'success')
    popModal()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.hl {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.intro {
  margin: 0 0 14px;
  color: var(--md-sys-color-on-surface-variant);
  font-size: 0.9rem;
  line-height: 1.4;
}
.form-row {
  margin-bottom: 12px;
}
</style>
