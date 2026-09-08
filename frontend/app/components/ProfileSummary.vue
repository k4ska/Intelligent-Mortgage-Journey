<script setup lang="ts">
import type { BankOffer, LoanProfile, ReviewResponse } from '~/types/api'

const props = defineProps<{ applicationId: string }>()

const api = useApi()

const { data: profile } = await useAsyncData<LoanProfile>(
  `profile-${props.applicationId}`,
  () => api.getProfile(props.applicationId),
)
const { data: offers } = await useAsyncData<BankOffer[]>(
  `offers-${props.applicationId}`,
  () => api.getOffers(props.applicationId),
)
const { data: review } = await useAsyncData<ReviewResponse>(
  `review-${props.applicationId}`,
  () => api.review(props.applicationId),
)

const money = (n: number) =>
  new Intl.NumberFormat(undefined, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(n)

const stressColor = computed(() => {
  const r = profile.value?.financials.stressTestResult
  return r === 'FAIL' ? 'error' : r === 'TIGHT' ? 'warning' : 'success'
})
</script>

<template>
  <div class="flex flex-col gap-6">
    <UCard v-if="review">
      <template #header>
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-scroll-text" class="text-[var(--ui-primary)]" />
          <h2 class="font-semibold">The assistant's read-back</h2>
        </div>
      </template>
      <p class="text-sm leading-relaxed">{{ review.summary }}</p>
      <ul v-if="review.concerns.length" class="mt-3 space-y-1 text-sm">
        <li v-for="(c, i) in review.concerns" :key="i" class="flex gap-2">
          <UIcon name="i-lucide-alert-triangle" class="mt-0.5 shrink-0 text-[var(--ui-warning)]" />
          <span>{{ c }}</span>
        </li>
      </ul>
    </UCard>

    <UCard v-if="profile">
      <template #header>
        <h2 class="font-semibold">Your loan profile</h2>
      </template>
      <dl class="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
        <div>
          <dt class="text-[var(--ui-text-dimmed)]">Loan amount</dt>
          <dd class="font-medium">{{ money(profile.loanRequest.amount) }}</dd>
        </div>
        <div>
          <dt class="text-[var(--ui-text-dimmed)]">Term</dt>
          <dd class="font-medium">{{ profile.loanRequest.termYears }} years</dd>
        </div>
        <div>
          <dt class="text-[var(--ui-text-dimmed)]">Rate type</dt>
          <dd class="font-medium capitalize">{{ profile.loanRequest.rateType.toLowerCase() }}</dd>
        </div>
        <div>
          <dt class="text-[var(--ui-text-dimmed)]">Loan-to-value</dt>
          <dd class="font-medium">{{ profile.financials.ltv }}%</dd>
        </div>
        <div>
          <dt class="text-[var(--ui-text-dimmed)]">Debt-to-income</dt>
          <dd class="font-medium">{{ profile.financials.dti }}%</dd>
        </div>
        <div>
          <dt class="text-[var(--ui-text-dimmed)]">Est. monthly payment</dt>
          <dd class="font-medium">{{ money(profile.financials.estimatedMonthlyPayment) }}</dd>
        </div>
      </dl>

      <div class="mt-4 flex items-center gap-2">
        <UBadge :color="stressColor" variant="subtle">
          Stress test: {{ profile.financials.stressTestResult }}
        </UBadge>
        <span class="text-xs text-[var(--ui-text-muted)]">
          {{ money(profile.financials.stressedMonthlyPayment) }}/mo if rates rose 3 points
          ({{ money(profile.financials.monthlyBufferAfterStress) }} left over)
        </span>
      </div>

      <div v-if="profile.riskIndicators.length" class="mt-4">
        <p class="text-xs font-medium uppercase tracking-wide text-[var(--ui-text-dimmed)]">
          Risk indicators
        </p>
        <ul class="mt-1 space-y-1 text-sm">
          <li v-for="(r, i) in profile.riskIndicators" :key="i">- {{ r }}</li>
        </ul>
      </div>
    </UCard>

    <UCard v-if="offers">
      <template #header>
        <h2 class="font-semibold">Indicative offers</h2>
        <p class="mt-1 text-xs text-[var(--ui-text-muted)]">
          The same standardized profile, sent to four sample lenders. Mock data.
        </p>
      </template>
      <div class="grid gap-3 sm:grid-cols-2">
        <div
          v-for="offer in offers"
          :key="offer.bankId"
          class="rounded-lg border border-[var(--ui-border)] p-3"
        >
          <div class="flex items-center justify-between">
            <span class="font-medium">{{ offer.bankName }}</span>
            <UBadge :color="offer.preApproved ? 'success' : 'neutral'" variant="subtle">
              {{ offer.preApproved ? 'Pre-approved' : 'Not yet' }}
            </UBadge>
          </div>
          <p class="mt-1 text-sm">
            {{ offer.offeredRatePct }}% - {{ money(offer.estimatedMonthlyPayment) }}/mo
          </p>
          <ul class="mt-2 space-y-0.5 text-xs text-[var(--ui-text-muted)]">
            <li v-for="(c, i) in offer.conditions" :key="i">- {{ c }}</li>
          </ul>
        </div>
      </div>
    </UCard>

    <UCard v-if="profile">
      <template #header>
        <h2 class="font-semibold">Documents you'll need</h2>
      </template>
      <ul class="space-y-1 text-sm">
        <li v-for="(d, i) in profile.documents" :key="i" class="flex gap-2">
          <UIcon name="i-lucide-file-check" class="mt-0.5 shrink-0 text-[var(--ui-text-dimmed)]" />
          <span>{{ d }}</span>
        </li>
      </ul>
    </UCard>
  </div>
</template>
