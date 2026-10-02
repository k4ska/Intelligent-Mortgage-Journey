<script setup lang="ts">
import type { ExplainResponse } from '~/types/api'

const props = defineProps<{
  applicationId: string | null
}>()

const api = useApi()

type Entry =
  | { kind: 'explanation', topic: string, data: ExplainResponse }
  | { kind: 'chat', question: string, answer: string }

const entries = ref<Entry[]>([])
const question = ref('')
const busy = ref(false)
/** Topic of the explanation the agent is working on, shown as a "thinking" card. */
const pendingTopic = ref<string | null>(null)
const panel = ref<HTMLElement | null>(null)

function scrollToBottom() {
  nextTick(() => panel.value?.scrollTo({ top: panel.value.scrollHeight, behavior: 'smooth' }))
}

async function explain(helpKey: string) {
  busy.value = true
  pendingTopic.value = helpKey
  scrollToBottom()
  try {
    const data = await api.explain(props.applicationId, helpKey)
    entries.value.push({ kind: 'explanation', topic: helpKey, data })
    scrollToBottom()
  } finally {
    busy.value = false
    pendingTopic.value = null
  }
}

async function ask() {
  const q = question.value.trim()
  if (!q || !props.applicationId) return
  question.value = ''
  const entry = reactive<Entry>({ kind: 'chat', question: q, answer: '' }) as Entry & { answer: string }
  entries.value.push(entry)
  busy.value = true
  scrollToBottom()
  try {
    await api.chat(props.applicationId, q, (chunk) => {
      entry.answer += chunk
      scrollToBottom()
    })
  } finally {
    busy.value = false
  }
}

// Let the page trigger explanations from field help buttons.
defineExpose({ explain })
</script>

<template>
  <UCard class="flex h-full flex-col" :ui="{ body: 'flex-1 overflow-hidden flex flex-col' }">
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon name="i-lucide-messages-square" class="text-[var(--ui-primary)]" />
        <h2 class="font-semibold">Assistant</h2>
      </div>
      <p class="mt-1 text-xs text-[var(--ui-text-muted)]">
        Ask anything, or tap "What's this?" on a field.
      </p>
    </template>

    <div ref="panel" class="flex-1 space-y-3 overflow-y-auto pr-1">
      <p v-if="!entries.length && !pendingTopic" class="text-sm text-[var(--ui-text-dimmed)]">
        No questions yet.
      </p>

      <div
        v-for="(entry, i) in entries"
        :key="i"
        class="rounded-lg bg-[var(--ui-bg-elevated)] p-3 text-sm"
      >
        <template v-if="entry.kind === 'explanation'">
          <p class="mb-1 text-xs font-medium uppercase tracking-wide text-[var(--ui-text-dimmed)]">
            {{ entry.topic.replace(/-/g, ' ') }}
          </p>
          <p>{{ entry.data.explanation }}</p>
          <div v-if="entry.data.relatedTopics.length" class="mt-2 flex flex-wrap gap-1">
            <UButton
              v-for="t in entry.data.relatedTopics"
              :key="t"
              size="xs"
              variant="soft"
              color="neutral"
              @click="explain(t)"
            >
              {{ t.replace(/-/g, ' ') }}
            </UButton>
          </div>
        </template>

        <template v-else>
          <p class="font-medium">{{ entry.question }}</p>
          <p v-if="entry.answer" class="mt-1 whitespace-pre-wrap text-[var(--ui-text-muted)]">
            {{ entry.answer }}
          </p>
          <p v-else class="mt-1 flex items-center gap-2 text-[var(--ui-text-dimmed)]">
            <UIcon name="i-lucide-loader-2" class="animate-spin" />
            Thinking...
          </p>
        </template>
      </div>

      <div v-if="pendingTopic" class="rounded-lg bg-[var(--ui-bg-elevated)] p-3 text-sm">
        <p class="mb-2 flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-[var(--ui-text-dimmed)]">
          <UIcon name="i-lucide-loader-2" class="animate-spin" />
          {{ pendingTopic.replace(/-/g, ' ') }}
        </p>
        <div class="space-y-2">
          <div class="h-3 w-full animate-pulse rounded bg-[var(--ui-bg-accented)]" />
          <div class="h-3 w-5/6 animate-pulse rounded bg-[var(--ui-bg-accented)]" />
          <div class="h-3 w-2/3 animate-pulse rounded bg-[var(--ui-bg-accented)]" />
        </div>
      </div>
    </div>

    <template #footer>
      <form class="flex gap-2" @submit.prevent="ask">
        <UInput
          v-model="question"
          class="flex-1"
          placeholder="Ask the assistant..."
          :disabled="busy || !applicationId"
        />
        <UButton type="submit" icon="i-lucide-send" :loading="busy" :disabled="!applicationId" />
      </form>
    </template>
  </UCard>
</template>
