<script setup lang="ts">
const store = useApplicationStore()
const route = useRoute()
</script>

<template>
  <div class="flex h-screen flex-col overflow-hidden bg-[var(--ui-bg-muted)]">
    <header class="shrink-0 border-b border-[var(--ui-border)] bg-[var(--ui-bg)]">
      <UContainer class="flex items-center justify-between py-2.5">
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
            :color="store.agentLive ? 'primary' : 'neutral'"
            variant="subtle"
          >
            Assistant: {{ store.assistantName }}
          </UBadge>
          <UButton
            v-if="route.path.startsWith('/journey') && !store.assistantOpen"
            class="assistant-header-button"
            color="primary"
            variant="soft"
            size="sm"
            icon="i-lucide-panel-right-open"
            title="Open assistant panel"
            aria-label="Open assistant panel"
            @click="store.assistantOpen = true"
          >
            Assistant
          </UButton>
          <UColorModeButton />
        </div>
      </UContainer>
    </header>

    <main class="min-h-0 flex-1">
      <slot />
    </main>

    <footer class="shrink-0 py-1.5 text-center text-[11px] text-[var(--ui-text-dimmed)]">
      Prototype - mock data, not financial advice.
    </footer>
  </div>
</template>

<style scoped>
.assistant-header-button {
  border: 1px solid color-mix(in srgb, var(--ui-primary) 28%, transparent);
  box-shadow: 0 4px 12px -8px color-mix(in srgb, var(--ui-primary) 70%, transparent);
}
</style>
