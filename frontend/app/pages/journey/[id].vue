<script setup lang="ts">
const route = useRoute()
const store = useApplicationStore()
const id = computed(() => route.params.id as string)

const assist = ref<{ explain: (k: string) => void } | null>(null)

await useAsyncData(`journey-${id.value}`, async () => {
  await store.loadApplication(id.value)
  return true
})

const progress = computed(() =>
  store.totalSteps ? Math.round(((store.stepIndex + 1) / store.totalSteps) * 100) : 0,
)

function onExplain(helpKey: string) {
  assist.value?.explain(helpKey)
}

async function onNext() {
  await store.submitDraft({ advance: true })
}
</script>

<template>
  <UContainer class="flex h-full max-w-6xl flex-col overflow-hidden py-4">
    <div v-if="store.error" class="mx-auto max-w-md">
      <UAlert color="error" :title="store.error" />
    </div>

    <template v-else>
      <div class="mb-4 shrink-0">
        <div class="flex items-center justify-between text-xs text-[var(--ui-text-muted)]">
          <span>Step {{ store.stepIndex + 1 }} of {{ store.totalSteps }}</span>
          <span>{{ progress }}%</span>
        </div>
        <div class="mt-1 h-1.5 overflow-hidden rounded-full bg-[var(--ui-bg-accented)]">
          <div
            class="h-full rounded-full bg-[var(--ui-primary)] transition-all"
            :style="{ width: `${progress}%` }"
          />
        </div>
      </div>

      <div class="grid min-h-0 flex-1 gap-6 lg:grid-cols-[1fr_360px]">
        <div class="min-h-0 overflow-y-auto pr-1">
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

        <div class="min-h-0">
          <AssistPanel ref="assist" :application-id="id" />
        </div>
      </div>
    </template>
  </UContainer>
</template>
