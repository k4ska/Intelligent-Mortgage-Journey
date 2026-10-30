<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import { useInboxStore } from '~/stores/inbox'
import { useAuthStore } from '~/stores/auth'
import { errorStatus } from '~/utils/errors'
import { copy } from '~/utils/copy'

const advisor = useAdvisorStore()
const inbox = useInboxStore()
const auth = useAuthStore()
const selectedBankIds = ref<string[]>([])
const submittingApplication = ref(false)
const applicationError = ref('')
const applicationSent = ref(false)
const hasStartedChat = computed(() => advisor.messages.some(message => message.role === 'user' && !message.threadId))

watch(() => advisor.bankOptions, () => {
  selectedBankIds.value = []
  applicationError.value = ''
  applicationSent.value = false
})

async function submitApplication() {
  const bankIds = advisor.bankOptions
    .filter(bank => selectedBankIds.value.includes(bank.id))
    .map(bank => bank.id)
  if (!bankIds.length || submittingApplication.value) return
  submittingApplication.value = true
  applicationError.value = ''
  applicationSent.value = false
  try {
    const result = await useApi().sendApplication(bankIds)
    advisor.bankOptions = []
    selectedBankIds.value = []
    // #51 creates bank emails immediately; refresh the shared unread badge and list.
    await inbox.load(true)
    // Application creation is committed; the optional advisor notification does not affect it.
    await advisor.send({ trigger: 'APPLICATION_SENT', applicationId: result.applicationId })
    advisor.error = ''
    applicationSent.value = true
  } catch (error) {
    if (errorStatus(error) === 401) return void auth.expire()
    applicationError.value = copy.applicationError
  } finally {
    submittingApplication.value = false
  }
}

const draft = ref('')
const list = ref<HTMLElement | null>(null)
const input = ref<HTMLTextAreaElement | null>(null)
// Only the newest message changes while streaming, so the count and its text are enough.
const scrollKey = computed(() => {
  const last = advisor.messages.at(-1)
  return `${advisor.messages.length}:${last?.id}:${last?.text.length}`
})

watch(scrollKey, async () => {
  await nextTick()
  if (list.value) list.value.scrollTop = list.value.scrollHeight
}, { immediate: true })

async function send() {
  const text = draft.value.trim()
  if (!text || advisor.sending || advisor.uploading || !advisor.loaded) return
  draft.value = ''
  await advisor.send(text)
  // The box is disabled while the advisor answers, which drops the focus; give it back.
  await nextTick()
  input.value?.focus()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void send()
  }
}
</script>

<template>
  <section class="app-panel flex min-h-0 flex-col" :aria-label="copy.chatTitle">
    <div class="app-panel-header">
      <UIcon name="i-lucide-messages-square" class="text-lg" />
      <h2>{{ copy.chatTitle }}</h2>
    </div>

    <div ref="list" class="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-5" role="log" aria-live="polite" aria-relevant="additions text">
      <p v-if="advisor.loading" class="text-center text-sm text-[var(--ui-text-muted)]">{{ copy.loading }}</p>
      <ChatMessageBubble v-for="message in advisor.messages" :key="message.id" :message="message" :streaming="advisor.sending" />
    </div>

    <div v-if="advisor.bankOptions.length" class="shrink-0 border-t border-[var(--ui-border)] p-4">
      <div class="rounded-xl border border-[var(--ui-border)] bg-[var(--ui-bg-muted)] p-4">
        <h3 class="text-base font-semibold">{{ copy.bankPickerTitle }}</h3>
        <p class="mt-1 text-sm text-[var(--ui-text-muted)]">{{ copy.bankPickerHint }}</p>
        <form class="mt-3 flex flex-wrap items-center gap-x-5 gap-y-3" @submit.prevent="submitApplication">
          <label v-for="bank in advisor.bankOptions" :key="bank.id" class="flex cursor-pointer items-center gap-2 text-sm">
            <input v-model="selectedBankIds" type="checkbox" :value="bank.id" :disabled="submittingApplication" class="size-4 accent-[var(--ui-primary)]" />
            <span>{{ bank.name }}</span>
          </label>
          <p v-if="applicationError" class="w-full text-sm text-red-600" role="alert">{{ applicationError }}</p>
          <UButton class="ml-auto" type="submit" icon="i-lucide-send" :loading="submittingApplication" :disabled="!selectedBankIds.length || submittingApplication">
            {{ submittingApplication ? copy.sendingApplication : copy.sendApplication }}
          </UButton>
        </form>
      </div>
    </div>

    <div class="shrink-0 border-t border-[var(--ui-border)] p-4">
      <p v-if="applicationSent" class="mb-2 flex items-center gap-2 text-sm font-medium text-green-700 dark:text-green-300" role="status">
        <UIcon name="i-lucide-circle-check" aria-hidden="true" />
        {{ copy.applicationSentNotice }}
      </p>
      <div v-if="advisor.error" role="alert" class="mb-3 flex items-center justify-between gap-2 text-sm text-red-600">
        <span>{{ advisor.error }}</span>
        <UButton v-if="!advisor.loaded" variant="soft" color="neutral" size="xs" @click="advisor.load()">{{ copy.retry }}</UButton>
      </div>
      <p v-if="advisor.sending" class="mb-2 text-xs text-[var(--ui-text-muted)]">{{ copy.sending }}</p>
      <form class="flex items-end gap-2" @submit.prevent="send">
        <textarea ref="input" v-model="draft" :placeholder="copy.chatPlaceholder" :aria-label="copy.chatPlaceholder" class="app-input min-h-12 max-h-36 flex-1 resize-y" rows="2" :disabled="!advisor.loaded || advisor.sending || !!advisor.uploading" @keydown="onKeydown" />
        <UButton type="submit" size="lg" icon="i-lucide-arrow-up" :aria-label="copy.send" :disabled="!draft.trim() || !advisor.loaded || advisor.sending || !!advisor.uploading" :loading="advisor.sending" />
      </form>
      <p v-if="copy.chatHint && !hasStartedChat" class="mt-2 text-xs text-[var(--ui-text-dimmed)]">{{ copy.chatHint }}</p>
    </div>
  </section>
</template>
