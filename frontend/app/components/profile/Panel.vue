<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import type { ProfileField, ProfileFieldKey } from '~/types/api'
import { formatMoney } from '~/utils/numbers'
import { copy } from '~/utils/copy'

const advisor = useAdvisorStore()
const moneyKeys: ProfileFieldKey[] = ['netIncome', 'savings', 'monthlyDebtPayments', 'coApplicantNetIncome']
const sourceLabels = { CHAT: copy.sourceChat, LISTING: copy.sourceListing, DOCUMENT: copy.sourceDocument }

function displayValue(field: ProfileField): string {
  if (moneyKeys.includes(field.key) && /^\d+(\.\d+)?$/.test(field.value)) {
    return formatMoney(Number(field.value))
  }
  if (field.value === 'yes') return copy.yes
  if (field.value === 'no') return copy.no
  return field.value || copy.unknown
}
</script>

<template>
  <aside class="app-panel min-h-0 overflow-y-auto" :aria-label="copy.profile">
    <div class="app-panel-header"><UIcon name="i-lucide-user-round" class="text-lg" /><h2>{{ copy.profile }}</h2></div>
    <div class="space-y-3 p-4">
      <p v-if="!advisor.profile.fields.length" class="py-8 text-center text-sm leading-6 text-[var(--ui-text-muted)]">{{ copy.profileEmpty }}</p>
      <div v-for="field in advisor.profile.fields" :key="field.key" class="rounded-xl border border-[var(--ui-border)] p-3">
        <p class="text-xs text-[var(--ui-text-muted)]">{{ copy.profileFields[field.key] }}</p>
        <p class="mt-1 font-semibold tabular-nums break-words">{{ displayValue(field) }}</p>
        <div class="mt-2 flex flex-wrap gap-1.5">
          <span class="rounded-md bg-[var(--ui-bg-muted)] px-2 py-0.5 text-xs text-[var(--ui-text-muted)]">{{ sourceLabels[field.source] }}</span>
          <span v-if="field.status === 'UNCERTAIN'" class="rounded-md bg-amber-100 px-2 py-0.5 text-xs text-amber-900">{{ copy.uncertain }}</span>
        </div>
      </div>
    </div>
    <div class="app-panel-header border-t border-[var(--ui-border)]"><UIcon name="i-lucide-house" class="text-lg" /><h2>{{ copy.property }}</h2></div>
    <p class="p-5 text-sm leading-6 text-[var(--ui-text-muted)]">{{ copy.propertyEmpty }}</p>
  </aside>
</template>