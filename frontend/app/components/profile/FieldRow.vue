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

const value = computed(() => displayValue(props.field))
// Two fields share a row; a long value (an address, say) gets the whole row.
const wide = computed(() => value.value.length > 14)
</script>

<template>
  <div :class="['min-w-0 rounded-lg border border-[var(--ui-border)] px-2.5 py-1.5', wide ? 'col-span-2' : '']">
    <p class="truncate text-xs text-[var(--ui-text-muted)]" :title="props.label">{{ props.label }}</p>
    <p class="flex items-center gap-1 text-sm font-semibold tabular-nums break-words">
      <span class="min-w-0">{{ value }}</span>
      <!-- Most values come from the chat; only say where the others came from. -->
      <UIcon
        v-if="props.field.source !== 'CHAT'"
        :name="props.field.source === 'DOCUMENT' ? 'i-lucide-file-text' : 'i-lucide-link'"
        class="shrink-0 text-[var(--ui-text-muted)]"
        :title="sourceLabels[props.field.source]"
        :aria-label="sourceLabels[props.field.source]"
      />
    </p>
    <span v-if="props.field.status === 'UNCERTAIN'" class="mt-1 inline-block rounded-md bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900">{{ copy.uncertain }}</span>
  </div>
</template>
