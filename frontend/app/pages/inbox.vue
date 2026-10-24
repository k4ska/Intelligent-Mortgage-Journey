<script setup lang="ts">
import type { InboxEmail, InboxThreadStatus } from '~/types/api'
import { useInboxStore } from '~/stores/inbox'
import { copy } from '~/utils/copy'
import { formatMoney, formatNumber } from '~/utils/numbers'

const inbox = useInboxStore()
const draft = ref('')
const confirmationEmail = ref<InboxEmail | null>(null)
const confirming = ref(false)

onMounted(() => void inbox.load())

watch(() => inbox.selectedThreadId, () => {
  draft.value = ''
  confirmationEmail.value = null
  confirming.value = false
})

const canReply = computed(() => !!inbox.activeThread && ['OPEN', 'OFFER', 'NEGOTIATING', 'CLOSED'].includes(inbox.activeThread.status))

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}

function statusClass(status: InboxThreadStatus) {
  switch (status) {
    case 'ACCEPTED': return 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300'
    case 'OFFER': return 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
    case 'NEGOTIATING': return 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-300'
    case 'DECLINED': return 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
    default: return 'bg-[var(--ui-bg-muted)] text-[var(--ui-text-muted)]'
  }
}

async function sendReply() {
  const body = draft.value.trim()
  if (!body) return
  const threadId = inbox.selectedThreadId
  if (await inbox.reply(body) && inbox.selectedThreadId === threadId) draft.value = ''
}

function askToAccept(email: InboxEmail) {
  if (inbox.accepting || inbox.replyingThreadId || !email.offer) return
  confirmationEmail.value = email
  confirming.value = true
}

async function confirmAcceptance() {
  const threadId = inbox.activeThread?.threadId
  const emailId = confirmationEmail.value?.id
  if (!threadId || emailId == null) return
  confirming.value = false
  confirmationEmail.value = null
  await inbox.accept(threadId, emailId)
}
</script>

<template>
  <div class="mx-auto flex h-full min-h-0 w-full max-w-[1600px] flex-col gap-3 p-3 md:flex-row lg:gap-4 lg:p-4">
    <section class="app-panel flex min-h-0 flex-col md:w-[370px] md:shrink-0" :aria-label="copy.inboxTitle">
      <div class="app-panel-header">
        <UIcon name="i-lucide-inbox" class="text-lg" />
        <h1 class="font-semibold">{{ copy.inboxTitle }}</h1>
        <span v-if="inbox.unreadCount" class="ml-auto text-sm text-[var(--ui-text-muted)]">{{ inbox.unreadCount }} {{ copy.unread.toLowerCase() }}</span>
      </div>
      <div class="min-h-0 overflow-y-auto">
        <p v-if="inbox.loading && !inbox.loaded" class="p-4 text-sm text-[var(--ui-text-muted)]">{{ copy.inboxLoading }}</p>
        <div v-if="inbox.error" class="space-y-2 p-4 text-sm" role="alert">
          <p class="text-red-600">{{ inbox.error }}</p>
          <UButton color="neutral" variant="soft" size="sm" @click="inbox.load(true)">{{ copy.retry }}</UButton>
        </div>
        <p v-else-if="inbox.loaded && !inbox.threads.length" class="p-5 text-sm text-[var(--ui-text-muted)]">{{ copy.inboxEmpty }}</p>
        <button
          v-for="thread in inbox.threads"
          :key="thread.threadId"
          type="button"
          class="block w-full border-b border-[var(--ui-border)] p-4 text-left hover:bg-[var(--ui-bg-muted)] focus-visible:outline-2 focus-visible:outline-[var(--ui-primary)]"
          :class="inbox.selectedThreadId === thread.threadId ? 'bg-[var(--ui-bg-muted)]' : ''"
          :aria-current="inbox.selectedThreadId === thread.threadId ? 'true' : undefined"
          @click="inbox.open(thread.threadId)"
        >
          <span class="flex items-center gap-2">
            <span v-if="thread.unread" class="size-2 shrink-0 rounded-full bg-[var(--ui-primary)]" :aria-label="copy.unread" />
            <span class="min-w-0 flex-1 truncate text-sm font-semibold">{{ thread.bank.name }}</span>
            <span class="shrink-0 text-xs text-[var(--ui-text-dimmed)]">{{ formatTime(thread.lastEmail.createdAt) }}</span>
          </span>
          <span class="mt-1 block truncate text-sm font-medium">{{ thread.lastEmail.subject }}</span>
          <span class="mt-1 block truncate text-sm text-[var(--ui-text-muted)]">{{ thread.lastEmail.preview }}</span>
          <span :class="['mt-2 inline-block rounded-md px-2 py-0.5 text-xs font-medium', statusClass(thread.status)]">{{ copy.inboxStatus[thread.status] }}</span>
        </button>
      </div>
    </section>

    <section class="app-panel flex min-h-0 min-w-0 flex-1 flex-col" :aria-label="copy.inboxSelectThread">
      <template v-if="inbox.activeThread">
        <div class="app-panel-header">
          <UIcon name="i-lucide-mail-open" class="text-lg" />
          <h2 class="min-w-0 flex-1 truncate">{{ inbox.activeThread.bank.name }}</h2>
          <span :class="['shrink-0 rounded-md px-2 py-0.5 text-xs font-medium', statusClass(inbox.activeThread.status)]">{{ copy.inboxStatus[inbox.activeThread.status] }}</span>
        </div>
        <div class="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 lg:p-6" aria-live="polite">
          <article v-for="email in inbox.activeThread.emails" :key="email.id" class="rounded-xl border border-[var(--ui-border)] p-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <span class="text-sm font-semibold">{{ email.from === 'BANK' ? inbox.activeThread.bank.name : copy.fromYou }}</span>
              <time class="text-xs text-[var(--ui-text-muted)]" :datetime="email.createdAt">{{ formatTime(email.createdAt) }}</time>
            </div>
            <h3 class="mt-3 font-semibold">{{ email.subject }}</h3>
            <p class="mt-3 whitespace-pre-wrap break-words text-sm leading-6">{{ email.body }}</p>
            <div v-if="email.offer" class="mt-4 space-y-3 rounded-xl border border-[var(--ui-border)] bg-[var(--ui-bg-muted)] p-4">
              <h4 class="font-semibold">{{ copy.inboxOfferTitle }}</h4>
              <dl class="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
                <div><dt class="text-[var(--ui-text-muted)]">{{ copy.inboxOfferAmount }}</dt><dd class="font-semibold">{{ formatMoney(email.offer.amount) }}</dd></div>
                <div><dt class="text-[var(--ui-text-muted)]">{{ copy.inboxOfferRate }}</dt><dd class="font-semibold">{{ formatNumber(email.offer.euriborPct, 2) }}% + {{ formatNumber(email.offer.marginPct, 2) }}% = {{ formatNumber(email.offer.ratePct, 2) }}%</dd></div>
                <div><dt class="text-[var(--ui-text-muted)]">{{ copy.inboxOfferTerm }}</dt><dd class="font-semibold">{{ email.offer.termYears }} {{ copy.inboxOfferYears }}</dd></div>
                <div><dt class="text-[var(--ui-text-muted)]">{{ copy.inboxOfferMonthly }}</dt><dd class="font-semibold">{{ formatMoney(email.offer.monthlyPayment) }}</dd></div>
                <div><dt class="text-[var(--ui-text-muted)]">{{ copy.inboxOfferValidUntil }}</dt><dd class="font-semibold">{{ email.offer.validUntil }}</dd></div>
              </dl>
              <div v-if="email.offer.conditions.length" class="text-sm">
                <p class="font-semibold">{{ copy.inboxOfferConditions }}</p>
                <ul class="mt-1 list-disc space-y-1 pl-5"><li v-for="condition in email.offer.conditions" :key="condition">{{ condition }}</li></ul>
              </div>
              <UButton
                v-if="email.from === 'BANK' && email.type === 'offer' && inbox.activeThread.status !== 'ACCEPTED' && inbox.activeThread.status !== 'CLOSED' && inbox.activeThread.status !== 'DECLINED'"
                icon="i-lucide-check-circle"
                :disabled="inbox.accepting || !!inbox.replyingThreadId"
                :loading="inbox.accepting"
                @click="askToAccept(email)"
              >{{ copy.inboxAccept }}</UButton>
            </div>
          </article>
          <p v-if="inbox.replyingThreadId === inbox.activeThread.threadId && inbox.activeThread.status !== 'CLOSED'" class="flex items-center gap-2 text-sm text-[var(--ui-text-muted)]" role="status">
            <UIcon name="i-lucide-loader-circle" class="animate-spin" aria-hidden="true" />
            {{ inbox.activeThread.bank.name }} {{ copy.inboxTyping }}
          </p>
        </div>
        <div v-if="canReply" class="shrink-0 space-y-2 border-t border-[var(--ui-border)] p-4">
          <p v-if="inbox.replyError" class="text-sm text-red-600" role="alert">{{ inbox.replyError }}</p>
          <form class="space-y-2" @submit.prevent="sendReply">
            <label for="bank-reply" class="block text-sm font-medium">{{ copy.inboxReplyPlaceholder }}</label>
            <textarea id="bank-reply" v-model="draft" rows="3" class="app-input w-full resize-y" :placeholder="copy.inboxReplyPlaceholder" :disabled="!!inbox.replyingThreadId || inbox.accepting" />
            <div class="flex justify-end">
              <UButton type="submit" icon="i-lucide-send" :disabled="!draft.trim() || !!inbox.replyingThreadId || inbox.accepting" :loading="!!inbox.replyingThreadId">{{ copy.inboxReplySend }}</UButton>
            </div>
          </form>
        </div>
        <p v-if="inbox.acceptError || inbox.advisorError" class="border-t border-[var(--ui-border)] p-4 text-sm text-red-600" role="alert">{{ inbox.acceptError || inbox.advisorError }}</p>
      </template>
      <div v-else class="flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center text-sm text-[var(--ui-text-muted)]">
        <UIcon name="i-lucide-mail-open" class="text-3xl" />
        <p v-if="inbox.opening">{{ copy.threadLoading }}</p>
        <template v-else-if="inbox.threadError">
          <p class="text-red-600" role="alert">{{ inbox.threadError }}</p>
          <UButton v-if="inbox.selectedThreadId" color="neutral" variant="soft" @click="inbox.open(inbox.selectedThreadId)">{{ copy.retry }}</UButton>
        </template>
        <p v-else>{{ copy.inboxSelectThread }}</p>
      </div>
    </section>

    <UModal v-model:open="confirming" :title="copy.inboxAcceptTitle" :description="copy.inboxAcceptBody" :close="false">
      <template #footer>
        <div class="flex w-full justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="copy.inboxAcceptCancel" @click="confirming = false" />
          <UButton icon="i-lucide-check-circle" :label="copy.inboxAcceptConfirm" @click="confirmAcceptance" />
        </div>
      </template>
    </UModal>
  </div>
</template>
