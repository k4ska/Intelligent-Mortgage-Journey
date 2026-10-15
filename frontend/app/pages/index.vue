<script setup lang="ts">
// Placeholder until the walking skeleton lands (#52 replaces this with login and the
// three-panel chat). Shows whether the backend is up and which agent mode it runs in.
const api = useApi()
const { data: status, error } = await useAsyncData('agent-status', () => api.getAgentStatus())
</script>

<template>
  <UContainer class="flex h-full items-center justify-center py-8">
    <UCard class="w-full max-w-lg">
      <div class="flex flex-col gap-3">
        <h1 class="text-xl font-semibold">
          A chat-based mortgage advisor is on its way
        </h1>
        <p class="text-[var(--ui-text-muted)]">
          The form-based journey has been removed. Next comes a conversation with an advisor that
          helps you from "could I even get a loan?" to an accepted bank offer.
        </p>
        <p class="text-sm text-[var(--ui-text-dimmed)]">
          <template v-if="error">
            The backend isn't reachable.
          </template>
          <template v-else-if="status">
            Backend is up. Agent mode: {{ status.mode }}.
          </template>
        </p>
      </div>
    </UCard>
  </UContainer>
</template>
