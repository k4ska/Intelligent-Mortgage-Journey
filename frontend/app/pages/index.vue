<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import { copy } from '~/utils/copy'

const advisor = useAdvisorStore()
type MobilePanel = 'documents' | 'chat' | 'profile'
const mobilePanel = ref<MobilePanel>('chat')
onMounted(() => advisor.load())
</script>

<template>
  <div class="mx-auto h-full min-h-0 w-full max-w-[1800px] p-2 sm:p-3 lg:p-4">
    <div class="grid h-full min-h-0 grid-cols-1 grid-rows-[48px_minmax(0,1fr)] gap-2 lg:hidden" aria-label="Mortgage journey mobile panels">
      <nav class="app-panel grid h-12 shrink-0 grid-cols-3 overflow-hidden p-1" aria-label="Mortgage journey panels">
        <button type="button" class="rounded-lg text-sm font-semibold" :class="mobilePanel === 'documents' ? 'bg-[var(--ui-primary)] text-white' : 'text-[var(--ui-text-muted)]'" :aria-pressed="mobilePanel === 'documents'" @click="mobilePanel = 'documents'">
          {{ copy.documents }}
        </button>
        <button type="button" class="rounded-lg text-sm font-semibold" :class="mobilePanel === 'chat' ? 'bg-[var(--ui-primary)] text-white' : 'text-[var(--ui-text-muted)]'" :aria-pressed="mobilePanel === 'chat'" @click="mobilePanel = 'chat'">
          {{ copy.chatTab }}
        </button>
        <button type="button" class="rounded-lg text-sm font-semibold" :class="mobilePanel === 'profile' ? 'bg-[var(--ui-primary)] text-white' : 'text-[var(--ui-text-muted)]'" :aria-pressed="mobilePanel === 'profile'" @click="mobilePanel = 'profile'">
          {{ copy.profile }}
        </button>
      </nav>
      <DocumentsPanel v-if="mobilePanel === 'documents'" class="min-h-0" />
      <ChatPanel v-if="mobilePanel === 'chat'" class="min-h-0" />
      <ProfilePanel v-if="mobilePanel === 'profile'" class="min-h-0" />
    </div>

    <div class="hidden h-full min-h-0 grid-cols-[minmax(210px,1fr)_minmax(360px,2.15fr)_minmax(240px,1.15fr)] gap-3 lg:grid xl:grid-cols-[minmax(185px,0.8fr)_minmax(210px,1fr)_minmax(400px,2.15fr)_minmax(240px,1.15fr)]">
      <ChatHistorySidebar class="hidden xl:flex" />
      <DocumentsPanel />
      <ChatPanel />
      <ProfilePanel />
    </div>
  </div>
</template>
