<script setup lang="ts">
import { useInboxStore } from '~/stores/inbox'
import { copy } from '~/utils/copy'

const inbox = useInboxStore()
onMounted(() => void inbox.load())

function formatTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
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
          class="block w-full border-b border-[var(--ui-border)] p-4 text-left hover:bg-[var(--ui-bg-muted)]"
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
          <span class="mt-2 inline-block rounded-md bg-[var(--ui-bg-elevated)] px-2 py-0.5 text-xs text-[var(--ui-text-muted)]">{{ thread.status }}</span>
        </button>
      </div>
    </section>

    <section class="app-panel flex min-h-0 min-w-0 flex-1 flex-col" :aria-label="copy.inboxSelectThread">
      <template v-if="inbox.activeThread">
        <div class="app-panel-header">
          <UIcon name="i-lucide-mail-open" class="text-lg" />
          <h2 class="min-w-0 flex-1 truncate">{{ inbox.activeThread.bank.name }}</h2>
          <span class="text-xs text-[var(--ui-text-muted)]">{{ inbox.activeThread.status }}</span>
        </div>
        <div class="min-h-0 flex-1 space-y-4 overflow-y-auto p-4 lg:p-6">
          <article v-for="email in inbox.activeThread.emails" :key="email.id" class="rounded-xl border border-[var(--ui-border)] p-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <span class="text-sm font-semibold">{{ email.from === 'BANK' ? inbox.activeThread.bank.name : copy.fromYou }}</span>
              <time class="text-xs text-[var(--ui-text-muted)]" :datetime="email.createdAt">{{ formatTime(email.createdAt) }}</time>
            </div>
            <h3 class="mt-3 font-semibold">{{ email.subject }}</h3>
            <p class="mt-3 whitespace-pre-wrap break-words text-sm leading-6">{{ email.body }}</p>
          </article>
        </div>
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
  </div>
</template>
