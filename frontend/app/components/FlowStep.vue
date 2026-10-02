<script setup lang="ts">
import type { Step, ValidateResponse } from '~/types/api'

defineProps<{
  step: Step
  draft: Record<string, unknown>
  validation: ValidateResponse | null
  busy: boolean
  /** The agent is checking this step's answers. */
  validating: boolean
  isLast: boolean
}>()

const emit = defineEmits<{
  setField: [key: string, value: unknown]
  explain: [helpKey: string]
  validate: []
  next: []
}>()

const severityColor = (s: string) =>
  s === 'BLOCKER' ? 'error' : s === 'WARNING' ? 'warning' : 'success'
</script>

<template>
  <UCard>
    <template #header>
      <h2 class="text-lg font-semibold">{{ step.title }}</h2>
      <p v-if="step.subtitle" class="mt-1 text-sm text-[var(--ui-text-muted)]">
        {{ step.subtitle }}
      </p>
    </template>

    <div class="flex flex-col gap-5">
      <FieldInput
        v-for="field in step.fields"
        :key="field.key"
        :field="field"
        :model-value="draft[field.key]"
        @update:model-value="emit('setField', field.key, $event)"
        @explain="emit('explain', $event)"
      />

      <p v-if="!step.fields.length" class="text-sm text-[var(--ui-text-muted)]">
        Nothing to fill in here - continue when you're ready.
      </p>

      <div
        v-if="validating"
        class="flex items-center gap-2 rounded-lg bg-[var(--ui-bg-elevated)] p-3 text-sm text-[var(--ui-text-muted)]"
      >
        <UIcon name="i-lucide-loader-2" class="animate-spin text-[var(--ui-primary)]" />
        The assistant is checking your answers...
      </div>
      <div v-else-if="validation && validation.issues.length" class="flex flex-col gap-2">
        <UAlert
          v-for="(issue, i) in validation.issues"
          :key="i"
          :color="severityColor(issue.severity)"
          variant="subtle"
          :title="issue.severity === 'BLOCKER' ? 'Needs attention' : 'Worth a look'"
          :description="issue.message"
        />
      </div>
      <UAlert
        v-else-if="validation"
        color="success"
        variant="subtle"
        title="Looks fine"
        description="The assistant found nothing to flag on this step."
      />
    </div>

    <template #footer>
      <div class="flex items-center justify-between gap-3">
        <UButton
          v-if="step.fields.length"
          color="neutral"
          variant="outline"
          icon="i-lucide-sparkles"
          :loading="validating"
          :disabled="busy"
          @click="emit('validate')"
        >
          Check my answers
        </UButton>
        <span v-else />

        <UButton
          :trailing-icon="isLast ? 'i-lucide-check' : 'i-lucide-arrow-right'"
          :loading="busy && !validating"
          :disabled="validating"
          @click="emit('next')"
        >
          {{ isLast ? 'Finish' : 'Continue' }}
        </UButton>
      </div>
    </template>
  </UCard>
</template>
