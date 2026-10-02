<script setup lang="ts">
import type { BankOffer, LoanProfile, ReviewResponse } from '~/types/api'

const props = defineProps<{ applicationId: string }>()

const api = useApi()

const followUp = ref('')
const followUpAnswer = ref('')
const followUpBusy = ref(false)

const { data: profile } = await useAsyncData<LoanProfile>(
  `profile-${props.applicationId}`,
  () => api.getProfile(props.applicationId),
)
const { data: offers } = await useAsyncData<BankOffer[]>(
  `offers-${props.applicationId}`,
  () => api.getOffers(props.applicationId),
)
// Not awaited: the read-back is the slowest agent call, so the rest of the summary
// shows straight away and the read-back card fills in when it arrives.
const { data: review, pending: reviewPending } = useLazyAsyncData<ReviewResponse>(
  `review-${props.applicationId}`,
  () => api.review(props.applicationId),
)

const money = (n: number) =>
  new Intl.NumberFormat(undefined, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(n)

const stressColor = computed(() => {
  const r = profile.value?.financials.stressTestResult
  return r === 'FAIL' ? 'error' : r === 'TIGHT' ? 'warning' : 'success'
})

async function askAboutReview() {
  const question = followUp.value.trim()
  if (!question || followUpBusy.value) return

  followUp.value = ''
  followUpAnswer.value = ''
  followUpBusy.value = true
  try {
    await api.chat(props.applicationId, `We are reviewing my mortgage application. ${question}`, (chunk) => {
      followUpAnswer.value += chunk
    })
  } finally {
    followUpBusy.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-6">
    <UCard v-if="review || reviewPending">
      <template #header>
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-scroll-text" class="text-primary" />
          <h2 class="font-semibold">The assistant's read-back</h2>
        </div>
      </template>
      <div v-if="!review" class="space-y-2">
        <p class="mb-3 flex items-center gap-2 text-sm text-[var(--ui-text-muted)]">
          <UIcon name="i-lucide-loader-2" class="animate-spin text-[var(--ui-primary)]" />
          The assistant is reading your application back...
        </p>
        <div class="h-3 w-full animate-pulse rounded bg-[var(--ui-bg-elevated)]" />
        <div class="h-3 w-5/6 animate-pulse rounded bg-[var(--ui-bg-elevated)]" />
        <div class="h-3 w-2/3 animate-pulse rounded bg-[var(--ui-bg-elevated)]" />
      </div>
      <template v-else>
        <p class="text-sm leading-relaxed">{{ review.summary }}</p>
        <ul v-if="review.concerns.length" class="mt-3 space-y-1 text-sm">
          <li v-for="(c, i) in review.concerns" :key="i" class="flex gap-2">
            <UIcon name="i-lucide-alert-triangle" class="mt-0.5 shrink-0 text-warning" />
            <span>{{ c }}</span>
          </li>
        </ul>
      </template>
    </UCard>

    <UCard>
      <template #header>
        <div class="flex items-center gap-2">
          <UIcon name="i-lucide-messages-square" class="text-primary" />
          <h2 class="font-semibold">Questions about your review?</h2>
        </div>
        <p class="mt-1 text-sm text-muted">
          The assistant is still here. Ask for clarification before you decide what to do next.
        </p>
      </template>

      <UAlert
        v-if="review?.followUpQuestion"
        color="primary"
        variant="subtle"
        icon="i-lucide-sparkles"
        :description="review.followUpQuestion"
        title="The assistant would like to know"
      />

      <form class="mt-4 flex flex-col gap-2 sm:flex-row" @submit.prevent="askAboutReview">
        <UInput
          v-model="followUp"
          class="flex-1"
          placeholder="Ask about your summary, payment, or next steps..."
          :disabled="followUpBusy"
        />
        <UButton
          type="submit"
          icon="i-lucide-send"
          :loading="followUpBusy"
          :disabled="!followUp.trim()"
        >
          Ask assistant
        </UButton>
      </form>

      <p v-if="followUpAnswer" class="mt-4 whitespace-pre-wrap rounded-lg bg-elevated p-3 text-sm leading-relaxed">
        {{ followUpAnswer }}
      </p>
    </UCard>

    <UCard v-if="profile">
      <template #header>
        <h2 class="font-semibold">Your loan profile</h2>
      </template>
      <dl class="grid grid-cols-2 gap-4 text-sm sm:grid-cols-3">
        <div>
          <dt class="text-dimmed">Loan amount</dt>
          <dd class="font-medium">{{ money(profile.loanRequest.amount) }}</dd>
        </div>
        <div>
          <dt class="text-dimmed">Term</dt>
          <dd class="font-medium">{{ profile.loanRequest.termYears }} years</dd>
        </div>
        <div>
          <dt class="text-dimmed">Rate type</dt>
          <dd class="font-medium capitalize">{{ profile.loanRequest.rateType.toLowerCase() }}</dd>
        </div>
        <div>
          <dt class="text-dimmed">Loan-to-value</dt>
          <dd class="font-medium">{{ profile.financials.ltv }}%</dd>
        </div>
        <div>
          <dt class="text-dimmed">Debt-to-income</dt>
          <dd class="font-medium">{{ profile.financials.dti }}%</dd>
        </div>
        <div>
          <dt class="text-dimmed">Est. monthly payment</dt>
          <dd class="font-medium">{{ money(profile.financials.estimatedMonthlyPayment) }}</dd>
        </div>
      </dl>

      <div class="mt-4 flex items-center gap-2">
        <UBadge :color="stressColor" variant="subtle">
          Stress test: {{ profile.financials.stressTestResult }}
        </UBadge>
        <span class="text-xs text-muted">
          {{ money(profile.financials.stressedMonthlyPayment) }}/mo if rates rose 3 points
          ({{ money(profile.financials.monthlyBufferAfterStress) }} left over)
        </span>
      </div>

      <div v-if="profile.riskIndicators.length" class="mt-4">
        <p class="text-xs font-medium uppercase tracking-wide text-dimmed">
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
        <p class="mt-1 text-xs text-muted">
          The same standardized profile, sent to four sample lenders. Mock data.
        </p>
      </template>
      <div class="grid gap-3 sm:grid-cols-2">
        <div
          v-for="offer in offers"
          :key="offer.bankId"
          class="rounded-lg border border-default p-3"
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
          <ul class="mt-2 space-y-0.5 text-xs text-muted">
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
          <UIcon name="i-lucide-file-check" class="mt-0.5 shrink-0 text-dimmed" />
          <span>{{ d }}</span>
        </li>
      </ul>
    </UCard>
  </div>
</template>
