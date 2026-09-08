<script setup lang="ts">
const store = useApplicationStore()
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
</script>

<template>
  <UContainer class="max-w-2xl">
    <div class="text-center">
      <h1 class="text-3xl sm:text-4xl font-bold tracking-tight">
        A mortgage application that explains itself
      </h1>
      <p class="mt-4 text-[var(--ui-text-muted)]">
        Answer a handful of questions. The assistant explains each term in plain language,
        checks your numbers as you go, and reads the whole thing back before you finish.
        You leave with a standardized loan profile you could take to any lender.
      </p>
    </div>

    <UCard class="mt-8">
      <div class="flex flex-col gap-4">
        <div v-if="store.flow" class="flex items-center gap-3 text-sm text-[var(--ui-text-muted)]">
          <UIcon name="i-lucide-list-checks" />
          <span>{{ store.flow.steps.length }} steps, roughly 5 minutes</span>
        </div>
        <UButton
          size="lg"
          block
          :loading="starting"
          trailing-icon="i-lucide-arrow-right"
          @click="start"
        >
          Start the journey
        </UButton>
      </div>
    </UCard>

    <p class="mt-6 text-center text-xs text-[var(--ui-text-dimmed)]">
      Nothing here is a real application. All figures are illustrative.
    </p>
  </UContainer>
</template>
