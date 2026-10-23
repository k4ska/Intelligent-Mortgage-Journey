<script setup lang="ts">
import type { ChatMessage } from '~/types/api'
import { copy } from '~/utils/copy'

defineProps<{ message: ChatMessage; streaming?: boolean }>()
</script>

<template>
  <div :class="['flex', message.role === 'user' ? 'justify-end' : 'justify-start']">
    <div :class="['max-w-[92%] rounded-2xl px-4 py-3 text-sm leading-6 whitespace-pre-wrap break-words', message.role === 'user' ? 'rounded-br-sm bg-[var(--ui-primary)] text-white' : 'rounded-bl-sm bg-[var(--ui-bg-muted)] text-[var(--ui-text)]']">
      <div>{{ message.text || (streaming && message.role === 'advisor' ? '…' : '') }}</div>
      <span v-if="message.role === 'advisor' && message.corrected" class="mt-2 inline-flex items-center gap-1 rounded-md border border-[var(--ui-border)] px-2 py-0.5 text-xs text-[var(--ui-text-muted)]" :title="copy.correctedTooltip" :aria-label="`${copy.correctedLabel}. ${copy.correctedTooltip}`" role="note" tabindex="0">
        <UIcon name="i-lucide-info" aria-hidden="true" />
        {{ copy.correctedLabel }}
      </span>
    </div>
  </div>
</template>