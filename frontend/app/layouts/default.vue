<script setup lang="ts">
const store = useApplicationStore()
</script>

<template>
  <div class="min-h-screen bg-[var(--ui-bg-muted)]">
    <header class="border-b border-[var(--ui-border)] bg-[var(--ui-bg)]">
      <UContainer class="flex items-center justify-between py-3">
        <NuxtLink to="/" class="flex items-center gap-2 font-semibold">
          <UIcon name="i-lucide-home" class="text-[var(--ui-primary)] text-xl" />
          <span>Intelligent Mortgage Journey</span>
        </NuxtLink>
        <UBadge
          v-if="store.flow"
          :color="store.agentMode === 'claude' ? 'primary' : 'neutral'"
          variant="subtle"
        >
          Assistant: {{ store.agentMode === 'claude' ? 'Claude' : 'demo stub' }}
        </UBadge>
      </UContainer>
    </header>

    <main class="py-6 sm:py-10">
      <slot />
    </main>

    <footer class="py-8 text-center text-xs text-[var(--ui-text-dimmed)]">
      Prototype - mock data, not financial advice.
    </footer>
  </div>
</template>
