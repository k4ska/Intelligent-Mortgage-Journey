<script setup lang="ts">
import type { MaxLoanResponse } from '~/types/api'

// "How much can I borrow?" - the largest loan the journey's own affordability checks
// would accept, so this never promises something the journey then flags.
const api = useApi()

const income = ref<number | null>(4000)
const coIncome = ref<number | null>(null)
const debts = ref<number | null>(null)
const downPayment = ref<number | null>(60000)
const result = ref<MaxLoanResponse | null>(null)
const loading = ref(false)

let debounce: ReturnType<typeof setTimeout> | undefined

async function refresh() {
  if (!income.value && !coIncome.value) {
    result.value = null
    return
  }
  loading.value = true
  try {
    result.value = await api.maxLoan({
      monthlyIncome: income.value,
      coApplicantMonthlyIncome: coIncome.value,
      monthlyDebtPayments: debts.value,
      downPayment: downPayment.value,
    })
  } finally {
    loading.value = false
  }
}

watch([income, coIncome, debts, downPayment], () => {
  clearTimeout(debounce)
  debounce = setTimeout(refresh, 300)
}, { immediate: true })

const limitText = computed(() => {
  const r = result.value
  if (!r) return ''
  if (r.maxLoanAmount <= 0) {
    return `Your debts already take ${r.maxDtiPct}% or more of your income - no room for a mortgage payment.`
  }
  switch (r.limitedBy) {
    case 'INCOME':
      return `Limited by your income: more would put your debts above ${r.maxDtiPct}% of it.`
    case 'STRESS':
      return 'Limited by the stress test: a 3-point rate rise would leave too little each month.'
    case 'DOWN_PAYMENT':
      return `Limited by your down payment: lenders typically lend up to ${r.maxLtvPct}% of the price.`
  }
  return ''
})
</script>

<template>
  <div class="topic-card cursor-default">
    <span class="topic-icon">
      <UIcon name="i-lucide-piggy-bank" class="text-xl" />
    </span>
    <p class="mt-4 font-semibold text-[var(--ui-text-highlighted)]">How much can I borrow?</p>
    <p class="mt-1.5 text-sm text-[var(--ui-text-muted)]">
      Monthly gross income and debts, plus any down payment.
    </p>

    <div class="mt-3 grid grid-cols-2 gap-2">
      <NumberInput
        v-model="income"
        size="md"
        placeholder="Income"
        leading-icon="i-lucide-euro"
        :aria-label="'Your monthly gross income'"
      />
      <NumberInput
        v-model="coIncome"
        size="md"
        placeholder="Partner"
        leading-icon="i-lucide-euro"
        :aria-label="'Co-applicant monthly gross income (optional)'"
      />
      <NumberInput
        v-model="debts"
        size="md"
        placeholder="Debts"
        leading-icon="i-lucide-euro"
        :aria-label="'Existing monthly debt payments'"
      />
      <NumberInput
        v-model="downPayment"
        size="md"
        placeholder="Down payment"
        leading-icon="i-lucide-euro"
        :aria-label="'Down payment (optional)'"
      />
    </div>

    <div class="mt-3 flex items-baseline gap-1.5">
      <UIcon v-if="loading" name="i-lucide-loader-2" class="animate-spin text-[var(--ui-primary)]" />
      <template v-else-if="result && result.maxLoanAmount > 0">
        <span class="text-lg font-semibold text-[var(--ui-primary)]">{{ formatMoney(result.maxLoanAmount) }}</span>
        <span class="text-sm text-[var(--ui-text-muted)]">max loan</span>
      </template>
      <span v-else-if="!result" class="text-sm text-[var(--ui-text-dimmed)]">Enter your monthly income</span>
    </div>
    <template v-if="result && !loading">
      <p v-if="result.maxLoanAmount > 0" class="text-xs text-[var(--ui-text-dimmed)]">
        <template v-if="result.maxPropertyPrice !== null">
          Homes up to {{ formatMoney(result.maxPropertyPrice) }} ·
        </template>
        {{ formatMoney(result.monthlyPayment) }}/mo at {{ result.annualRatePct }}%
      </p>
      <p class="mt-1.5 text-xs text-[var(--ui-text-muted)]">
        {{ limitText }} <span class="text-[var(--ui-text-dimmed)]">An estimate, not an offer.</span>
      </p>
    </template>
  </div>
</template>
