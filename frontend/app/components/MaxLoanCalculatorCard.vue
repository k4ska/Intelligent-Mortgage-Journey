<script setup lang="ts">
import type { MaxLoanResponse } from '~/types/api'

// "How much can I borrow?" - the largest loan the journey's own affordability checks
// would accept, so this never promises something the journey then flags.
const api = useApi()

const income = ref<number | null>(2500)
const coIncome = ref<number | null>(null)
const debts = ref<number | null>(null)
const downPayment = ref<number | null>(60000)
// Household details are tucked away: left empty, the minimum living costs still count.
const showHousehold = ref(false)
const dependants = ref<number | null>(null)
const householdCosts = ref<number | null>(null)
const creditLimits = ref<number | null>(null)
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
      dependants: dependants.value,
      monthlyHouseholdCosts: householdCosts.value,
      totalCreditLimits: creditLimits.value,
    })
  } finally {
    loading.value = false
  }
}

watch([income, coIncome, debts, downPayment, dependants, householdCosts, creditLimits], () => {
  clearTimeout(debounce)
  debounce = setTimeout(refresh, 300)
}, { immediate: true })

const limitText = computed(() => {
  const r = result.value
  if (!r) return ''
  if (r.maxLoanAmount <= 0) {
    return r.limitedBy === 'STRESS'
      ? 'After debts and living costs there is too little left each month for a mortgage payment.'
      : `Your debts already take ${r.maxDtiPct}% or more of your net income - no room for a mortgage payment.`
  }
  switch (r.limitedBy) {
    case 'INCOME':
      return `Limited by your income: at a stress-test rate of ${r.stressRatePct}%, more would put your loan payments above ${r.maxDtiPct}% of it.`
    case 'STRESS':
      return `Limited by the stress test: at ${r.stressRatePct}%, a bigger loan would leave too little each month after living costs.`
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
      Monthly net income and debts, plus any down payment.
    </p>

    <div class="mt-3 grid grid-cols-2 gap-2">
      <NumberInput
        v-model="income"
        size="md"
        placeholder="Income"
        leading-icon="i-lucide-euro"
        :aria-label="'Your monthly net income (after tax)'"
      />
      <NumberInput
        v-model="coIncome"
        size="md"
        placeholder="Partner"
        leading-icon="i-lucide-euro"
        :aria-label="'Co-applicant monthly net income (optional)'"
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

    <button
      type="button"
      class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-[var(--ui-primary)]"
      :aria-expanded="showHousehold"
      @click="showHousehold = !showHousehold"
    >
      <UIcon :name="showHousehold ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'" />
      Household and credit cards
    </button>
    <div v-if="showHousehold" class="mt-2 grid grid-cols-2 gap-2">
      <NumberInput
        v-model="dependants"
        size="md"
        placeholder="Dependants"
        leading-icon="i-lucide-users"
        :aria-label="'Dependants in the household (e.g. children)'"
      />
      <NumberInput
        v-model="householdCosts"
        size="md"
        placeholder="Costs"
        leading-icon="i-lucide-euro"
        :aria-label="'Monthly household living costs, excluding rent and loans (optional)'"
      />
      <NumberInput
        v-model="creditLimits"
        size="md"
        class="col-span-2"
        placeholder="Credit card & overdraft limits"
        leading-icon="i-lucide-euro"
        :aria-label="'Total credit card and overdraft limits, even if unused'"
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
      <p class="text-xs text-[var(--ui-text-dimmed)]">
        Counts {{ formatMoney(result.monthlyLivingCosts) }}/mo living costs<template v-if="result.creditLimitMonthlyCost > 0">
          and {{ formatMoney(result.creditLimitMonthlyCost) }}/mo for credit limits</template>
      </p>
      <p class="mt-1.5 text-xs text-[var(--ui-text-muted)]">
        {{ limitText }} <span class="text-[var(--ui-text-dimmed)]">An estimate, not an offer.</span>
      </p>
    </template>
  </div>
</template>
