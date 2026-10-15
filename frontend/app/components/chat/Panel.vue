<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import { copy } from '~/utils/copy'

const advisor = useAdvisorStore()
const draft = ref('')
const list = ref<HTMLElement | null>(null)
const lastText = computed(() => advisor.messages.map(message => `${message.id}:${message.text}`).join('|'))

watch(lastText, async () => {
  await nextTick()
  if (list.value) list.value.scrollTop = list.value.scrollHeight
}, { immediate: true })

async function send() {
  const text = draft.value.trim()
  if (!text || advisor.sending || !advisor.loaded) return
  draft.value = ''
  await advisor.send(text)
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
      <div v-for="message in advisor.messages" :key="message.id" :class="['flex', message.role === 'user' ? 'justify-end' : 'justify-start']">
        <div :class="['max-w-[87%] rounded-2xl px-4 py-3 text-sm leading-6 whitespace-pre-wrap break-words', message.role === 'user' ? 'rounded-br-sm bg-[var(--ui-primary)] text-white' : 'rounded-bl-sm bg-[var(--ui-bg-muted)] text-[var(--ui-text)]']">
          {{ message.text || (advisor.sending && message.role === 'advisor' ? '…' : '') }}
        </div>
      </div>
    </div>

    <div class="shrink-0 border-t border-[var(--ui-border)] p-4">
      <div v-if="advisor.error" role="alert" class="mb-3 flex items-center justify-between gap-2 text-sm text-red-600">
        <span>{{ advisor.error }}</span>
        <UButton v-if="!advisor.loaded" variant="soft" color="neutral" size="xs" @click="advisor.load()">{{ copy.retry }}</UButton>
      </div>
      <p v-if="advisor.sending" class="mb-2 text-xs text-[var(--ui-text-muted)]">{{ copy.sending }}</p>
      <form class="flex items-end gap-2" @submit.prevent="send">
        <textarea v-model="draft" :placeholder="copy.chatPlaceholder" :aria-label="copy.chatPlaceholder" class="app-input min-h-12 max-h-36 flex-1 resize-y" rows="2" :disabled="!advisor.loaded || advisor.sending" @keydown="onKeydown" />
        <UButton type="submit" size="lg" icon="i-lucide-arrow-up" :aria-label="copy.send" :disabled="!draft.trim() || !advisor.loaded || advisor.sending" :loading="advisor.sending" />
      </form>
      <p class="mt-2 text-xs text-[var(--ui-text-dimmed)]">{{ copy.chatHint }}</p>
    </div>
  </section>
</template>