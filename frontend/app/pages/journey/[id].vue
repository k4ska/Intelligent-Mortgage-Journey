<script setup lang="ts">
const route = useRoute()
const store = useApplicationStore()
const id = computed(() => route.params.id as string)

const assist = ref<{ explain: (k: string) => void } | null>(null)
const assistantWidth = ref(360)
const resizingAssistant = ref(false)

await useAsyncData(`journey-${id.value}`, async () => {
  await store.loadApplication(id.value)
  return true
})

const progress = computed(() =>
  store.totalSteps ? Math.round(((store.stepIndex + 1) / store.totalSteps) * 100) : 0,
)

const journeySteps = computed(() => store.flow?.steps ?? [])

function onExplain(helpKey: string) {
  assist.value?.explain(helpKey)
}

async function onNext() {
  await store.submitDraft({ advance: true })
}

function startAssistantResize(event: PointerEvent) {
  if (event.button !== 0) return
  resizingAssistant.value = true
  document.addEventListener('pointermove', resizeAssistant)
  document.addEventListener('pointerup', stopAssistantResize, { once: true })
}

function resizeAssistant(event: PointerEvent) {
  if (!resizingAssistant.value) return
  const shell = document.querySelector('.journey-shell')
  const rightEdge = shell?.getBoundingClientRect().right ?? window.innerWidth
  assistantWidth.value = Math.min(560, Math.max(280, Math.round(rightEdge - event.clientX)))
}

function stopAssistantResize() {
  resizingAssistant.value = false
  document.removeEventListener('pointermove', resizeAssistant)
}

onBeforeUnmount(stopAssistantResize)
</script>

<template>
  <UContainer class="flex h-full max-w-7xl flex-col overflow-hidden px-0 sm:px-4 lg:px-6">
    <div v-if="store.error" class="mx-auto max-w-md">
      <UAlert color="error" :title="store.error" />
    </div>

    <template v-else>
      <div class="journey-shell min-h-0 flex-1 overflow-hidden md:grid md:grid-cols-[250px_minmax(0,1fr)] md:grid-rows-[minmax(0,1fr)]">
        <aside class="journey-sidebar shrink-0 border-b border-default px-5 py-5 md:border-b-0 md:border-r md:px-6 md:py-8">
          <div class="flex items-start justify-between gap-4 md:block">
            <div>
              <p class="eyebrow">Your application</p>
              <h1 class="mt-1 text-xl font-semibold text-highlighted">Home loan plan</h1>
            </div>
            <div class="progress-ring md:mt-7" :style="{ '--progress': `${progress * 3.6}deg` }">
              <span>{{ progress }}%</span>
            </div>
          </div>

          <div class="mt-5 h-1.5 overflow-hidden rounded-full bg-accented md:hidden">
            <div class="h-full rounded-full bg-primary transition-all" :style="{ width: `${progress}%` }" />
          </div>

          <nav class="mt-7 hidden md:block" aria-label="Application progress">
            <ol class="space-y-1">
              <li v-for="(step, index) in journeySteps" :key="step.id">
                <div
                  class="step-nav-item"
                  :class="{
                    'step-nav-item-active': index === store.stepIndex && !store.isComplete,
                    'step-nav-item-complete': index < store.stepIndex || store.isComplete,
                  }"
                >
                  <span class="step-marker">
                    <UIcon v-if="index < store.stepIndex || store.isComplete" name="i-lucide-check" class="text-sm" />
                    <span v-else>{{ index + 1 }}</span>
                  </span>
                  <span class="min-w-0">
                    <span class="block truncate text-sm font-medium">{{ step.title }}</span>
                    <span class="mt-0.5 block text-xs text-dimmed">
                      {{ index < store.stepIndex || store.isComplete ? 'Complete' : index === store.stepIndex ? 'In progress' : 'Up next' }}
                    </span>
                  </span>
                </div>
              </li>
            </ol>
          </nav>

          <div class="mt-8 hidden rounded-xl bg-elevated p-3.5 text-sm text-muted md:block">
            <div class="flex items-center gap-2 font-medium text-highlighted">
              <UIcon name="i-lucide-sparkles" class="text-(--accent-gold)" />
              You’re in good hands
            </div>
            <p class="mt-1.5 leading-relaxed">Take your time. You can ask the assistant about anything along the way.</p>
          </div>
        </aside>

        <div class="min-h-0 overflow-y-scroll px-5 pb-24 pt-5 sm:px-8 md:px-10 md:pb-24 md:pt-8">
          <div
            class="assistant-layout relative mx-auto grid max-w-6xl gap-8"
            :class="{ 'assistant-layout-closed': !store.assistantOpen }"
            :style="{ '--assistant-width': `${assistantWidth}px` }"
          >
            <div class="min-w-0">
              <div class="mb-6 flex items-center justify-between gap-4">
                <div>
                  <p class="eyebrow text-primary">Step {{ Math.max(store.stepIndex + 1, 1) }} of {{ store.totalSteps }}</p>
                  <p class="mt-1 text-sm text-muted">A few details now, a clearer decision later.</p>
                </div>
                <UIcon name="i-lucide-shield-check" class="hidden text-2xl text-emerald-600 sm:block" />
              </div>

              <ProfileSummary v-if="store.isComplete" :application-id="id" />

              <FlowStep
                v-else-if="store.currentStep"
                :step="store.currentStep"
                :draft="store.draft"
                :validation="store.validation"
                :busy="store.loading"
                :is-last="store.stepIndex === store.totalSteps - 2"
                @set-field="store.setField"
                @explain="onExplain"
                @validate="store.runValidation"
                @next="onNext"
              />
            </div>

            <div v-if="store.assistantOpen" class="relative min-h-105 lg:sticky lg:top-0 lg:h-[calc(100vh-8rem)] lg:max-h-180">
              <button
                type="button"
                class="assistant-resize-handle"
                aria-label="Resize assistant panel"
                title="Drag to resize assistant panel"
                @pointerdown="startAssistantResize"
              >
                <span />
              </button>
              <div class="absolute right-3 top-3 z-10 flex gap-1">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="soft"
                  icon="i-lucide-maximize-2"
                  title="Widen assistant panel"
                  aria-label="Widen assistant panel"
                  @click="assistantWidth = assistantWidth >= 460 ? 360 : 460"
                />
                <UButton
                  class="assistant-close-button"
                  size="xs"
                  color="neutral"
                  variant="soft"
                  icon="i-lucide-x"
                  title="Close assistant panel"
                  aria-label="Close assistant panel"
                  @click="store.assistantOpen = false"
                />
              </div>
              <AssistPanel ref="assist" :application-id="id" />
            </div>

          </div>
        </div>
      </div>
    </template>
  </UContainer>
</template>

<style scoped>
.journey-shell {
  background: var(--ui-bg);
}

.assistant-layout {
  --assistant-width: 360px;
}

@media (min-width: 1024px) {
  .assistant-layout {
    grid-template-columns: minmax(0, 1fr) var(--assistant-width);
  }

  .assistant-layout.assistant-layout-closed {
    grid-template-columns: minmax(0, 1fr);
  }
}

.journey-sidebar {
  background: linear-gradient(145deg, var(--ui-bg) 0%, var(--ui-bg-muted) 100%);
}

.eyebrow {
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ui-text-dimmed);
}

.progress-ring {
  display: grid;
  height: 3.25rem;
  width: 3.25rem;
  flex-shrink: 0;
  place-items: center;
  border-radius: 9999px;
  background: conic-gradient(var(--ui-primary) var(--progress), var(--ui-bg-accented) 0);
  position: relative;
}

.progress-ring::before {
  content: '';
  position: absolute;
  inset: 0.25rem;
  border-radius: inherit;
  background: var(--ui-bg);
}

.progress-ring span {
  position: relative;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--ui-text-highlighted);
}

.step-nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border-radius: 0.75rem;
  padding: 0.7rem 0.65rem;
  color: var(--ui-text-muted);
}

.step-nav-item-active {
  background: var(--color-current-100);
  color: var(--color-current-800);
}

.step-nav-item-complete {
  color: var(--ui-text-highlighted);
}

.step-marker {
  display: grid;
  height: 1.75rem;
  width: 1.75rem;
  flex-shrink: 0;
  place-items: center;
  border: 1px solid var(--ui-border);
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 700;
  background: var(--ui-bg);
}

.step-nav-item-active .step-marker {
  border-color: var(--ui-primary);
  background: var(--ui-primary);
  color: white;
}

.step-nav-item-complete .step-marker {
  border-color: #86efac;
  background: #dcfce7;
  color: #15803d;
}

.assistant-resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  left: -1rem;
  z-index: 20;
  display: grid;
  width: 2rem;
  cursor: col-resize;
  place-items: center;
  touch-action: none;
}

.assistant-resize-handle span {
  height: 2.5rem;
  width: 0.2rem;
  border-radius: 9999px;
  background: var(--ui-border-accented);
  transition: background 0.15s ease, height 0.15s ease;
}

.assistant-resize-handle:hover span,
.assistant-resize-handle:active span {
  height: 4rem;
  background: var(--ui-primary);
}

.assistant-reopen {
  position: fixed;
  right: 1rem;
  top: 5rem;
  z-index: 30;
}
</style>
