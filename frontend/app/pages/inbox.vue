<script setup lang="ts">
import { copy } from '~/utils/copy'
</script>

<template>
  <div class="flex h-full items-center justify-center p-6">
    <div class="w-full max-w-lg rounded-2xl border border-[var(--ui-border)] bg-[var(--ui-bg)] p-8 text-center">
      <UIcon name="i-lucide-mail-open" class="mx-auto mb-4 text-4xl text-[var(--ui-text-muted)]" />
      <h1 class="text-2xl">{{ copy.inboxTitle }}</h1>
      <p class="mt-3 text-[var(--ui-text-muted)]">{{ copy.inboxPlaceholder }}</p>
    </div>
  </div>
</template>