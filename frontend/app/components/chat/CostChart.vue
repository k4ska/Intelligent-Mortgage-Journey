<script setup lang="ts">
import type { CostBreakdown } from '~/types/api'
import { copy } from '~/utils/copy'
import { formatMoney, formatNumber } from '~/utils/numbers'

// What the home costs in all (#160): one bar split into the down payment, the loan and the
// interest over the term. All figures come from the backend.
const props = defineProps<{ title: string; chart: CostBreakdown }>()

const total = computed(() => props.chart.downPayment + props.chart.loanAmount + props.chart.totalInterest)
const parts = computed(() => [
  { key: 'down', label: copy.costDownPayment, value: props.chart.downPayment, color: 'var(--accent-gold)' },
  { key: 'loan', label: copy.costLoan, value: props.chart.loanAmount, color: 'var(--ui-primary)' },
  { key: 'interest', label: copy.costInterest, value: props.chart.totalInterest, color: 'var(--accent-violet)' },
].filter(part => part.value > 0))

function share(value: number) {
  return total.value ? (value / total.value) * 100 : 0
}
const description = computed(() => parts.value.map(part => `${part.label}: ${formatMoney(part.value)}`).join(', '))
</script>

<template>
  <figure class="mt-3 rounded-xl border border-[var(--ui-border)] bg-[var(--ui-bg)] p-3 whitespace-normal">
    <figcaption class="mb-1 text-sm font-semibold text-[var(--ui-text-highlighted)]">{{ title }}</figcaption>
    <p class="mb-3 text-xs text-[var(--ui-text-muted)]">
      {{ formatMoney(chart.loanAmount) }} · {{ chart.termYears }} {{ copy.chartYears }} · {{ formatNumber(chart.ratePct, 2) }}% · {{ formatMoney(chart.monthlyPayment) }} {{ copy.costMonthly }}
    </p>
    <div class="flex h-7 w-full overflow-hidden rounded-lg" role="img" :aria-label="`${title}. ${description}. ${copy.costTotal}: ${formatMoney(total)}`">
      <div v-for="part in parts" :key="part.key" class="h-full first:rounded-l-lg last:rounded-r-lg" :style="{ width: `${share(part.value)}%`, background: part.color }" />
    </div>
    <dl class="mt-3 grid grid-cols-1 gap-2 text-sm sm:grid-cols-3">
      <div v-for="part in parts" :key="part.key" class="flex items-start gap-2">
        <span class="mt-1.5 size-2.5 shrink-0 rounded-sm" :style="{ background: part.color }" aria-hidden="true" />
        <div>
          <dt class="text-xs text-[var(--ui-text-muted)]">{{ part.label }}</dt>
          <dd class="font-semibold text-[var(--ui-text-highlighted)]">{{ formatMoney(part.value) }} <span class="text-xs font-normal text-[var(--ui-text-muted)]">{{ formatNumber(share(part.value), 0) }}%</span></dd>
        </div>
      </div>
    </dl>
    <p class="mt-3 border-t border-[var(--ui-border)] pt-2 text-sm">
      {{ copy.costTotal }}: <strong>{{ formatMoney(total) }}</strong>
    </p>
    <p class="mt-1 text-xs text-[var(--ui-text-muted)]">{{ copy.costNote }}</p>
  </figure>
</template>
