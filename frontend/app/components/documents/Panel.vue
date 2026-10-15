<script setup lang="ts">
import { copy } from '~/utils/copy'
</script>

<template>
  <aside class="app-panel flex min-h-0 flex-col" :aria-label="copy.documents">
    <div class="app-panel-header"><UIcon name="i-lucide-files" class="text-lg" /><h2>{{ copy.documents }}</h2></div>
    <div class="flex flex-1 flex-col items-center justify-center px-5 text-center">
      <UIcon name="i-lucide-file-up" class="mb-3 text-3xl text-[var(--ui-text-dimmed)]" />
      <p class="text-sm leading-6 text-[var(--ui-text-muted)]">{{ copy.documentsPlaceholder }}</p>
    </div>
  </aside>
</template>