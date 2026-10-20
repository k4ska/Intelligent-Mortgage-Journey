
<script setup lang="ts">
import type { ProfileField, PropertyField } from '~/types/api'
import { copy } from '~/utils/copy'
import { formatMoney, formatNumber } from '~/utils/numbers'

const props = defineProps<{
  field: ProfileField | PropertyField
  label: string
}>()

const moneyKeys = ['netIncome', 'savings', 'monthlyDebtPayments', 'coApplicantNetIncome', 'creditCardLimits', 'monthlyLivingCosts', 'price']
const sourceLabels = { CHAT: copy.sourceChat, LISTING: copy.sourceListing, DOCUMENT: copy.sourceDocument }

function displayValue(field: ProfileField | PropertyField): string {
  if (moneyKeys.includes(field.key) && /^\d+(\.\d+)?$/.test(field.value)) {
    return formatMoney(Number(field.value))
  }
  if (field.key === 'type') {
    return copy.propertyTypes[field.value as keyof typeof copy.propertyTypes] ?? field.value
  }
  if (field.key === 'size' && /^\d+(\.\d+)?$/.test(field.value)) {
    return `${formatNumber(Number(field.value), 2)} m²`
  }
  if (field.value === 'yes') return copy.yes
  if (field.value === 'no') return copy.no
  return field.value || copy.unknown
}
</script>

<template>
  <div class="rounded-xl border border-[var(--ui-border)] p-3">
    <p class="text-xs text-[var(--ui-text-muted)]">{{ props.label }}</p>
    <p class="mt-1 font-semibold tabular-nums break-words">{{ displayValue(props.field) }}</p>
    <div class="mt-2 flex flex-wrap gap-1.5">
      <span class="rounded-md bg-[var(--ui-bg-muted)] px-2 py-0.5 text-xs text-[var(--ui-text-muted)]">{{ sourceLabels[props.field.source] }}</span>
      <span v-if="props.field.status === 'UNCERTAIN'" class="rounded-md bg-amber-100 px-2 py-0.5 text-xs text-amber-900">{{ copy.uncertain }}</span>
    </div>
  </div>
</template>
