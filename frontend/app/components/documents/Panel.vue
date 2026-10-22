<script setup lang="ts">
import type { DocumentView } from '~/types/api'
import { useAdvisorStore } from '~/stores/advisor'
import { copy } from '~/utils/copy'

const advisor = useAdvisorStore()

function upload(document: DocumentView, event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  // Allow the same file to be selected again if an upload failed.
  input.value = ''
  if (!file || document.status !== 'NEEDED') return
  void advisor.uploadDocument(document.type, file)
}
</script>

<template>
  <aside class="app-panel flex min-h-0 flex-col" :aria-label="copy.documents">
    <div class="app-panel-header">
      <UIcon name="i-lucide-files" class="text-lg" />
      <h2>{{ copy.documents }}</h2>
    </div>
    <div class="min-h-0 flex-1 space-y-3 overflow-y-auto p-3">
      <p v-if="advisor.loading" class="py-4 text-center text-sm text-[var(--ui-text-muted)]">{{ copy.loading }}</p>
      <p v-else-if="!advisor.loaded" class="text-sm text-[var(--ui-text-muted)]">{{ copy.documentsLoadError }}</p>
      <p v-else-if="!advisor.documents.length" class="text-sm text-[var(--ui-text-muted)]">{{ copy.documentsEmpty }}</p>
      <p v-if="advisor.documentError" class="text-sm text-red-600" role="alert">{{ advisor.documentError }}</p>

      <section
        v-for="document in advisor.documents"
        :key="document.type"
        :class="[
          'rounded-xl border p-3 text-sm',
          document.status === 'NOT_NEEDED_YET' ? 'border-[var(--ui-border)] opacity-60' : '',
          document.status === 'NEEDED' ? 'border-2 border-[var(--ui-primary)]' : '',
          document.status === 'UPLOADED' ? 'border-amber-500 bg-amber-50/70 dark:bg-amber-950/20' : '',
          document.status === 'CONFIRMED' ? 'border-green-600 bg-green-50/70 dark:bg-green-950/20' : '',
        ]"
        :aria-label="document.label"
      >
        <div class="flex items-start gap-2">
          <UIcon
            :name="document.status === 'CONFIRMED' ? 'i-lucide-circle-check' : 'i-lucide-file-text'"
            :class="['mt-0.5 shrink-0 text-lg', document.status === 'CONFIRMED' ? 'text-green-700 dark:text-green-400' : '']"
            aria-hidden="true"
          />
          <h3 class="min-w-0 font-semibold leading-5 break-words">{{ document.label }}</h3>
        </div>
        <p
          :class="[
            'mt-2 text-xs font-semibold',
            document.status === 'UPLOADED' ? 'text-amber-800 dark:text-amber-300' : '',
            document.status === 'CONFIRMED' ? 'text-green-800 dark:text-green-300' : '',
          ]"
          role="status"
        >
          {{ document.status === 'NOT_NEEDED_YET' ? copy.documentNotNeeded
            : document.status === 'NEEDED' ? copy.documentNeeded
              : document.status === 'UPLOADED' ? copy.documentUploaded : copy.documentConfirmed }}
        </p>
        <p v-if="document.status === 'NOT_NEEDED_YET'" class="mt-1 text-xs leading-5">{{ copy.documentPreview }}</p>
        <p v-if="document.fileName" class="mt-1 break-all text-xs text-[var(--ui-text-muted)]">{{ document.fileName }}</p>
        <p class="mt-2 leading-5">{{ document.why }}</p>
        <p class="mt-2 text-xs leading-5 text-[var(--ui-text-muted)]">
          <strong>{{ copy.documentWhereToGet }}:</strong> {{ document.whereToGet }}
        </p>
        <details class="mt-2 text-xs">
          <summary class="cursor-pointer rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--ui-primary)]">{{ copy.documentTips }}</summary>
          <p class="mt-1 leading-5 text-[var(--ui-text-muted)]">{{ document.tips }}</p>
        </details>
        <label
          v-if="document.status === 'NEEDED'"
          :class="[
            'mt-3 inline-flex cursor-pointer items-center gap-1.5 rounded-lg bg-[var(--ui-primary)] px-3 py-2 text-xs font-semibold text-white focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-[var(--ui-primary)]',
            advisor.sending || advisor.uploading ? 'cursor-not-allowed opacity-50' : '',
          ]"
        >
          <UIcon name="i-lucide-upload" aria-hidden="true" />
          {{ advisor.uploading === document.type ? copy.documentUploading : copy.documentUpload }}
          <input
            type="file"
            class="sr-only"
            :aria-label="`${copy.documentUpload}: ${document.label}`"
            :disabled="!!advisor.uploading || advisor.sending"
            @change="upload(document, $event)"
          />
        </label>
      </section>
    </div>
  </aside>
</template>