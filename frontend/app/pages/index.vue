<script setup lang="ts">

const store = useApplicationStore()
const api = useApi()
const router = useRouter()
const starting = ref(false)

await useAsyncData('bootstrap', async () => {
  await store.bootstrap()
  return true
})

async function start() {
  starting.value = true
  try {
    await store.startNewApplication()
    router.push(`/journey/${store.application!.id}`)
  } finally {
    starting.value = false
  }
}

// "What is a loan?" / "Top 10 mistakes" are fixed, general content, so they come from the
// built-in definitions (loaded once in bootstrap) and open instantly with no LLM call.
// The explain-agent is only a fallback when those definitions failed to load.
interface Topic { key: string, title: string, teaser: string, icon: string }

const topics: Topic[] = [
  {
    key: 'what-is-a-loan',
    title: 'What is a loan?',
    teaser: 'The short version of how a mortgage actually works.',
    icon: 'i-lucide-landmark',
  },
  {
    key: 'top-mortgage-mistakes',
    title: 'Top 10 mistakes when applying',
    teaser: 'The pitfalls that trip up first-time buyers most often.',
    icon: 'i-lucide-shield-alert',
  },
]

const modalOpen = ref(false)
const activeTopic = ref<Topic | null>(null)
const loadingTopic = ref(false)
const explanation = ref<{ text: string, related: string[], fromAgent: boolean } | null>(null)

async function openTopic(topic: Topic) {
  activeTopic.value = topic
  modalOpen.value = true

  const definition = store.definitions[topic.key]
  if (definition) {
    explanation.value = { ...definition, fromAgent: false }
    return
  }

  explanation.value = null
  loadingTopic.value = true
  try {
    const answer = await api.explain(null, topic.key)
    explanation.value = { text: answer.explanation, related: answer.relatedTopics, fromAgent: true }
  } finally {
    loadingTopic.value = false
  }
}

async function openRelated(key: string) {
  const known = topics.find(t => t.key === key)
  await openTopic(known ?? { key, title: key.replace(/-/g, ' '), teaser: '', icon: 'i-lucide-sparkles' })
}
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden">
    <!-- Hero: sits flush against the header, no gap -->
    <section class="hero-band relative shrink-0 overflow-hidden">
      <div class="hero-blob hero-blob-a" />
      <div class="hero-blob hero-blob-b" />

      <UContainer class="relative py-7 sm:py-9">
        <div class="mx-auto max-w-2xl text-center">
          <span class="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white/90 ring-1 ring-inset ring-white/20 backdrop-blur">
            <UIcon name="i-lucide-sparkles" class="text-[var(--accent-gold)]" />
            Prototype - agent-guided mortgage journey
          </span>

          <h1 class="mt-3 text-2xl font-bold tracking-tight text-white sm:text-3xl">
            A mortgage application that
            <span class="text-gradient">explains itself</span>
          </h1>

          <p class="mt-2 text-sm text-white/75 sm:text-base">
            Answer a handful of questions. The assistant explains each term, checks your
            numbers as you go, and reads the whole thing back before you finish.
          </p>

          <div class="mt-4 flex flex-wrap items-center justify-center gap-2">
            <span v-if="store.flow" class="hero-chip">
              <UIcon name="i-lucide-list-checks" class="text-[var(--accent-gold)]" />
              {{ store.flow.steps.length }} steps · ~5 minutes
            </span>
            <span class="hero-chip">
              <UIcon
                :name="store.agentLive ? 'i-lucide-bot' : 'i-lucide-test-tube-2'"
                :class="store.agentLive ? 'text-emerald-400' : 'text-white/60'"
              />
              Assistant: {{ store.assistantName }}
            </span>
          </div>
        </div>
      </UContainer>
    </section>

    <!-- Hub: fills the rest of the viewport; scrolls internally only if it truly can't fit -->
    <div class="min-h-0 flex-1 overflow-y-auto">
      <UContainer class="flex h-full max-w-6xl flex-col justify-center gap-4 py-4">
        <!-- Primary: start the journey -->
        <button
          type="button"
          class="start-card group"
          :disabled="starting"
          @click="start"
        >
          <div class="flex flex-col items-start gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex items-center gap-3.5">
              <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-white/15 ring-1 ring-inset ring-white/25">
                <UIcon name="i-lucide-rocket" class="text-xl text-white" />
              </span>
              <div class="text-left">
                <p class="font-semibold text-white">Start the journey</p>
                <p class="text-sm text-white/75">
                  Begin your standardized loan profile - nothing here is a real application.
                </p>
              </div>
            </div>
            <span class="start-cta">
              <UIcon v-if="starting" name="i-lucide-loader-2" class="animate-spin" />
              <template v-else>
                Let's go
                <UIcon name="i-lucide-arrow-right" class="transition-transform group-hover:translate-x-0.5" />
              </template>
            </span>
          </div>
        </button>

        <!-- Secondary: quick-guide topics, then the two standalone calculators -->
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <button
            v-for="topic in topics"
            :key="topic.key"
            type="button"
            class="topic-card group"
            @click="openTopic(topic)"
          >
            <span class="topic-icon">
              <UIcon :name="topic.icon" class="text-xl" />
            </span>
            <p class="mt-4 font-semibold text-[var(--ui-text-highlighted)]">{{ topic.title }}</p>
            <p class="mt-1.5 text-sm text-[var(--ui-text-muted)]">{{ topic.teaser }}</p>
            <span class="mt-4 inline-flex items-center gap-1 text-sm font-medium text-[var(--ui-primary)]">
              Read the guide
              <UIcon name="i-lucide-arrow-right" class="transition-transform group-hover:translate-x-0.5" />
            </span>
          </button>

          <LoanCalculatorCard />
          <MaxLoanCalculatorCard />
        </div>

        <p class="text-center text-xs text-[var(--ui-text-dimmed)]">
          Nothing here is a real application. All figures are illustrative.
        </p>
      </UContainer>
    </div>

    <!-- Topic explanation modal -->
    <UModal v-model:open="modalOpen">
      <template #content>
        <div class="p-5 sm:p-6">
          <div class="flex items-start gap-3">
            <span class="topic-icon shrink-0">
              <UIcon :name="activeTopic?.icon ?? 'i-lucide-sparkles'" class="text-lg" />
            </span>
            <div>
              <h2 class="font-semibold text-[var(--ui-text-highlighted)]">
                {{ activeTopic?.title }}
              </h2>
              <p v-if="explanation?.fromAgent" class="text-xs text-[var(--ui-text-dimmed)]">
                Answered by the {{ store.assistantName }} assistant
              </p>
            </div>
          </div>

          <div class="mt-4">
            <div v-if="loadingTopic" class="space-y-2">
              <div class="h-3 w-full animate-pulse rounded bg-[var(--ui-bg-elevated)]" />
              <div class="h-3 w-5/6 animate-pulse rounded bg-[var(--ui-bg-elevated)]" />
              <div class="h-3 w-2/3 animate-pulse rounded bg-[var(--ui-bg-elevated)]" />
            </div>
            <p v-else class="whitespace-pre-line text-sm leading-relaxed text-[var(--ui-text-muted)]">
              {{ explanation?.text }}
            </p>

            <div v-if="explanation?.related.length" class="mt-4 flex flex-wrap gap-1.5">
              <UButton
                v-for="t in explanation.related"
                :key="t"
                size="xs"
                variant="soft"
                color="neutral"
                @click="openRelated(t)"
              >
                {{ t.replace(/-/g, ' ') }}
              </UButton>
            </div>
          </div>
        </div>
      </template>
    </UModal>
  </div>
</template>

<style scoped>
.hero-band {
  background: linear-gradient(155deg, var(--color-current-950) 0%, var(--color-current-900) 45%, var(--color-current-700) 100%);
}

.hero-blob {
  position: absolute;
  border-radius: 9999px;
  filter: blur(70px);
  opacity: 0.55;
  pointer-events: none;
}

.hero-blob-a {
  top: -6rem;
  right: -4rem;
  width: 18rem;
  height: 18rem;
  background: var(--accent-violet);
}

.hero-blob-b {
  bottom: -8rem;
  left: -6rem;
  width: 16rem;
  height: 16rem;
  background: var(--accent-gold);
  opacity: 0.35;
}

.text-gradient {
  background: linear-gradient(90deg, var(--accent-gold), var(--color-current-300));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.hero-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.1);
  padding: 0.35rem 0.8rem;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.85);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.15);
}

.start-card {
  border-radius: var(--ui-radius, 0.75rem);
  padding: 1.1rem 1.35rem;
  text-align: left;
  background: linear-gradient(120deg, var(--color-current-600), var(--color-current-800));
  box-shadow: 0 12px 30px -12px rgba(26, 58, 143, 0.55);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.start-card:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 16px 36px -12px rgba(26, 58, 143, 0.65);
}

.start-card:disabled {
  opacity: 0.85;
  cursor: default;
}

.start-cta {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border-radius: 9999px;
  background: var(--accent-gold);
  padding: 0.55rem 1.1rem;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-current-950);
  white-space: nowrap;
}
</style>
