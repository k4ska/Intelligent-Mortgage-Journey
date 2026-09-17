<script setup lang="ts">
const store = useApplicationStore()
</script>

<template>
  <div class="min-h-screen bg-[var(--ui-bg-muted)]">
    <header class="border-b border-[var(--ui-border)] bg-[var(--ui-bg)]">
      <UContainer class="flex items-center justify-between py-3.5">
        <NuxtLink to="/" class="flex items-center gap-2.5">
          <span class="flex h-8 w-8 items-center justify-center rounded-md bg-[var(--ui-primary)]">
            <UIcon name="i-lucide-key-round" class="text-white text-base" />
          </span>
          <span class="font-serif text-[15px] font-semibold tracking-tight" style="font-family: 'Source Serif 4', serif;">
            Intelligent Mortgage Journey
          </span>
        </NuxtLink>
        <div class="flex items-center gap-3">
          <UBadge
            v-if="store.flow"
            :color="store.agentMode === 'claude' ? 'primary' : 'neutral'"
            variant="subtle"
          >
            Assistant: {{ store.agentMode === 'claude' ? 'Claude' : 'demo stub' }}
          </UBadge>
          <UColorModeButton />
        </div>
      </UContainer>
    </header>

    <main class="py-8 sm:py-14">
      <slot />
    </main>

    <footer class="py-8 text-center text-xs text-[var(--ui-text-dimmed)]">
      Prototype - mock data, not financial advice.
    </footer>
  </div>
</template>
