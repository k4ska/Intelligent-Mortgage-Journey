<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import { useInboxStore } from '~/stores/inbox'
import { copy } from '~/utils/copy'

// Chat history (#160): a header button next to the theme toggle that opens the saved
// chats and a "+" for a new one, instead of a panel that is always on screen.
const advisor = useAdvisorStore()
const inbox = useInboxStore()
const route = useRoute()
const open = ref(false)

const changing = computed(() => !advisor.loaded || advisor.loading || advisor.switchingConversation ||
  advisor.sending || advisor.applying || !!advisor.uploading || !!advisor.showingChart || !!inbox.assistingThreadId ||
  !!inbox.replyingThreadId || inbox.accepting)

watch(open, (isOpen) => {
  if (isOpen) void advisor.refreshConversations()
})

// Picking a chat means wanting to read it, so go to the chat page from elsewhere (the inbox).
async function done(changed: boolean) {
  if (!changed) return
  open.value = false
  if (route.path !== '/') await navigateTo('/')
}
async function create() {
  await done(await advisor.newConversation())
}
async function select(id: string) {
  await done(await advisor.selectConversation(id))
}
</script>

<template>
  <UPopover v-model:open="open" :content="{ align: 'end', sideOffset: 8 }">
    <UButton color="neutral" variant="ghost" icon="i-lucide-history" :aria-label="copy.chatHistory" :title="copy.chatHistory" />

    <template #content>
      <div class="flex max-h-[min(70vh,560px)] w-[min(20rem,calc(100vw-2rem))] flex-col" :aria-label="copy.chatHistory" role="dialog">
        <div class="flex shrink-0 items-center gap-2 border-b border-[var(--ui-border)] py-2 pl-3 pr-2">
          <UIcon name="i-lucide-history" class="text-base" />
          <h2 class="flex-1 text-sm font-semibold">{{ copy.chatHistory }}</h2>
          <UButton
            icon="i-lucide-plus"
            size="sm"
            color="primary"
            :disabled="changing"
            :aria-label="copy.newConversation"
            :title="copy.newConversation"
            @click="create"
          />
        </div>
        <nav class="min-h-0 flex-1 space-y-0.5 overflow-y-auto p-1.5" :aria-label="copy.chatHistory">
          <button
            v-for="conversation in advisor.conversations"
            :key="conversation.id"
            type="button"
            :disabled="changing"
            :aria-current="conversation.active ? 'page' : undefined"
            :title="conversation.title"
            class="w-full rounded-lg px-2.5 py-2 text-left text-sm transition-colors hover:bg-[var(--ui-bg-muted)] disabled:cursor-not-allowed disabled:opacity-60"
            :class="conversation.active ? 'bg-[var(--ui-bg-muted)] font-semibold text-[var(--ui-text)]' : 'text-[var(--ui-text-muted)]'"
            @click="select(conversation.id)"
          >
            <span class="block truncate">{{ conversation.title }}</span>
            <span class="block text-xs font-normal text-[var(--ui-text-dimmed)]">{{ new Date(conversation.updatedAt).toLocaleDateString() }}</span>
          </button>
        </nav>
        <p class="shrink-0 border-t border-[var(--ui-border)] px-3 py-2 text-xs text-[var(--ui-text-dimmed)]">
          {{ copy.sharedJourneyNotice }}
        </p>
      </div>
    </template>
  </UPopover>
</template>
