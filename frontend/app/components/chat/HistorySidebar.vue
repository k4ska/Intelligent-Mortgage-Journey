<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import { useInboxStore } from '~/stores/inbox'
import { copy } from '~/utils/copy'

const emit = defineEmits<{ navigate: [] }>()
const advisor = useAdvisorStore()
const inbox = useInboxStore()
const changing = computed(() => !advisor.loaded || advisor.loading || advisor.switchingConversation ||
  advisor.sending || advisor.applying || !!advisor.uploading || !!advisor.showingChart || !!inbox.assistingThreadId ||
  !!inbox.replyingThreadId || inbox.accepting)

async function create() {
  if (await advisor.newConversation()) emit('navigate')
}
async function select(id: string) {
  if (await advisor.selectConversation(id)) emit('navigate')
}
</script>

<template>
  <aside class="app-panel flex min-h-0 flex-col" :aria-label="copy.chatHistory">
    <div class="app-panel-header">
      <UIcon name="i-lucide-history" class="text-lg" />
      <h2>{{ copy.chatHistory }}</h2>
    </div>
    <div class="shrink-0 p-3">
      <UButton icon="i-lucide-plus" block color="primary" :disabled="changing" @click="create">
        {{ copy.newConversation }}
      </UButton>
    </div>
    <nav class="min-h-0 flex-1 space-y-1 overflow-y-auto px-2 pb-3" :aria-label="copy.chatHistory">
      <button
        v-for="conversation in advisor.conversations"
        :key="conversation.id"
        type="button"
        :disabled="changing"
        :aria-current="conversation.active ? 'page' : undefined"
        :title="conversation.title"
        class="w-full rounded-lg px-3 py-2 text-left text-sm transition-colors hover:bg-[var(--ui-bg-muted)] disabled:cursor-not-allowed disabled:opacity-60"
        :class="conversation.active ? 'bg-[var(--ui-bg-muted)] font-semibold text-[var(--ui-text)]' : 'text-[var(--ui-text-muted)]'"
        @click="select(conversation.id)"
      >
        <span class="block truncate">{{ conversation.title }}</span>
        <span class="block text-xs font-normal text-[var(--ui-text-dimmed)]">{{ new Date(conversation.updatedAt).toLocaleDateString() }}</span>
      </button>
    </nav>
    <p class="shrink-0 border-t border-[var(--ui-border)] p-3 text-xs text-[var(--ui-text-dimmed)]">
      {{ copy.sharedJourneyNotice }}
    </p>
  </aside>
</template>