<script setup lang="ts">
import type { CalculatorEstimateResponse } from '~/types/api'

const api = useApi()

const price = ref<number | null>(300000)
const downPayment = ref<number | null>(60000)
const result = ref<CalculatorEstimateResponse | null>(null)
const loading = ref(false)

let debounce: ReturnType<typeof setTimeout> | undefined

async function refresh() {
  loading.value = true
  try {
    result.value = await api.estimate({
      propertyPrice: price.value,
      downPayment: downPayment.value,
    })
  } finally {
    loading.value = false
  }
}

watch([price, downPayment], () => {
  clearTimeout(debounce)
  debounce = setTimeout(refresh, 300)
}, { immediate: true })
</script>

<template>
  <div class="topic-card cursor-default">
    <span class="topic-icon">
      <UIcon name="i-lucide-calculator" class="text-xl" />
    </span>
    <p class="mt-4 font-semibold text-[var(--ui-text-highlighted)]">Quick loan calculator</p>
    <p class="mt-1.5 text-sm text-[var(--ui-text-muted)]">
      A rough monthly payment before you start.
    </p>

    <div class="mt-3 grid grid-cols-2 gap-2">
      <NumberInput
        v-model="price"
        size="sm"
        placeholder="Home price"
        leading-icon="i-lucide-euro"
        :aria-label="'Home price'"
      />
      <NumberInput
        v-model="downPayment"
        size="sm"
        placeholder="Down payment"
        leading-icon="i-lucide-euro"
        :aria-label="'Down payment'"
      />
    </div>

    <div class="mt-3 flex items-baseline gap-1.5">
      <UIcon v-if="loading" name="i-lucide-loader-2" class="animate-spin text-[var(--ui-primary)]" />
      <template v-else-if="result">
        <span class="text-lg font-semibold text-[var(--ui-primary)]">{{ formatMoney(result.monthlyPayment) }}</span>
        <span class="text-sm text-[var(--ui-text-muted)]">/mo</span>
      </template>
    </div>
    <p v-if="result && !loading" class="text-xs text-[var(--ui-text-dimmed)]">
      at {{ result.annualRatePct }}% · {{ result.termYears }} yrs · LTV {{ result.ltv }}%
    </p>
  </div>
</template>
