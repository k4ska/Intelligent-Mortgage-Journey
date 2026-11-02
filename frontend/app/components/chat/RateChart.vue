<script setup lang="ts">
import type { RateChart } from '~/types/api'
import { copy } from '~/utils/copy'
import { formatMoney, formatNumber } from '~/utils/numbers'

// The payment at each Euribor level as bars, today's highlighted, with the payment the
// stress test checked as a dashed line (#130). All figures come from the backend.
const props = defineProps<{ title: string; chart: RateChart }>()

const width = 420
const height = 220
const plot = { left: 8, right: 8, top: 28, bottom: 40 }

const top = computed(() => {
  const highest = Math.max(props.chart.stressedPayment, ...props.chart.points.map(p => p.monthlyPayment))
  return highest * 1.12
})
const slot = computed(() => (width - plot.left - plot.right) / props.chart.points.length)
const barWidth = computed(() => slot.value * 0.56)

function y(value: number) {
  return plot.top + (height - plot.top - plot.bottom) * (1 - value / top.value)
}

const bars = computed(() => props.chart.points.map((point, i) => {
  const x = plot.left + slot.value * i + (slot.value - barWidth.value) / 2
  return { ...point, x, centre: x + barWidth.value / 2, y: y(point.monthlyPayment) }
}))

const stressY = computed(() => y(props.chart.stressedPayment))
const description = computed(() => props.chart.points
  .map(p => `${copy.chartEuribor} ${formatNumber(p.euriborPct, 2)}%: ${formatMoney(p.monthlyPayment)}`)
  .join(', '))
</script>

<template>
  <figure class="mt-3 rounded-xl border border-[var(--ui-border)] bg-[var(--ui-bg)] p-3 whitespace-normal">
    <figcaption class="mb-1 text-sm font-semibold text-[var(--ui-text-highlighted)]">{{ title }}</figcaption>
    <p class="mb-2 text-xs text-[var(--ui-text-muted)]">
      {{ formatMoney(chart.loanAmount) }} · {{ chart.termYears }} {{ copy.chartYears }} · {{ copy.chartMargin }} {{ formatNumber(chart.marginPct, 2) }}%
    </p>
    <svg :viewBox="`0 0 ${width} ${height}`" class="h-auto w-full" role="img" :aria-label="`${title}. ${description}`">
      <line :x1="plot.left" :x2="width - plot.right" :y1="height - plot.bottom" :y2="height - plot.bottom"
        stroke="var(--ui-border-accented)" />
      <g v-for="bar in bars" :key="bar.euriborPct">
        <rect :x="bar.x" :y="bar.y" :width="barWidth" :height="height - plot.bottom - bar.y" rx="4"
          :fill="bar.today ? 'var(--ui-primary)' : 'var(--ui-border-accented)'" />
        <text :x="bar.centre" :y="bar.y - 6" text-anchor="middle" font-size="12" font-weight="600"
          fill="var(--ui-text-highlighted)">{{ formatMoney(bar.monthlyPayment) }}</text>
        <text :x="bar.centre" :y="height - plot.bottom + 16" text-anchor="middle" font-size="12"
          fill="var(--ui-text)">{{ formatNumber(bar.euriborPct, 2) }}%</text>
        <text v-if="bar.today" :x="bar.centre" :y="height - plot.bottom + 31" text-anchor="middle" font-size="11"
          font-weight="600" fill="var(--ui-primary)">{{ copy.chartToday }}</text>
      </g>
      <line :x1="plot.left" :x2="width - plot.right" :y1="stressY" :y2="stressY" stroke="var(--accent-gold)"
        stroke-width="1.5" stroke-dasharray="5 4" />
      <!-- On the left, above the lowest bar, so it never runs into a bar's label. -->
      <text :x="plot.left" :y="stressY - 5" text-anchor="start" font-size="11" font-weight="600"
        fill="var(--ui-text-muted)">{{ copy.chartStress }} {{ formatMoney(chart.stressedPayment) }}</text>
    </svg>
    <p class="mt-1 text-xs text-[var(--ui-text-muted)]">{{ copy.chartAxis }}</p>
  </figure>
</template>
