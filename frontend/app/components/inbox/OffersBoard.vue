<script lang="ts">
/** Each thread's monthly payment when the board last showed it; outlives the board while a thread is open. */
const seen = new Map<string, number>()
</script>

<script setup lang="ts">
import { useInboxStore } from '~/stores/inbox'
import { copy } from '~/utils/copy'
import { formatCents, formatMoney, formatNumber } from '~/utils/numbers'
import { offerBoard, statusClass, totalRepaid } from '~/utils/offers'

// Every bank's offer side by side (#160, point 1): the cheapest live offer is highlighted,
// a counter-offer shows what it changed, and a row opens that bank's thread.
const inbox = useInboxStore()
const board = computed(() => offerBoard(inbox.threads))
const highestPayment = computed(() => Math.max(0, ...board.value.rows.map(row => row.offer?.monthlyPayment ?? 0)))

// A row whose offer changed since the board last showed it glows for a moment, so a
// counter-offer is easy to spot when the user comes back from the thread they negotiated in.
const flashing = ref(new Set<string>())
watch(() => inbox.threads.map(thread => [thread.threadId, thread.offer?.monthlyPayment] as const), (threads) => {
  for (const [threadId, payment] of threads) {
    const before = seen.get(threadId)
    if (payment != null && before != null && before !== payment) {
      flashing.value = new Set([...flashing.value, threadId])
      setTimeout(() => {
        const next = new Set(flashing.value)
        next.delete(threadId)
        flashing.value = next
      }, 4000)
    }
    if (payment != null) seen.set(threadId, payment)
  }
}, { immediate: true })

function signed(value: number, format: (n: number) => string) {
  return `${value < 0 ? '−' : '+'}${format(Math.abs(value))}`
}

function barWidth(payment: number) {
  return highestPayment.value ? `${Math.max(8, (payment / highestPayment.value) * 100)}%` : '0%'
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col">
    <div class="app-panel-header">
      <UIcon name="i-lucide-scale" class="text-lg" />
      <h2 class="min-w-0 flex-1 truncate">{{ copy.offersTitle }}</h2>
      <span v-if="board.liveCount" class="shrink-0 text-sm text-[var(--ui-text-muted)]">{{ board.liveCount }} {{ board.liveCount === 1 ? copy.offersCountOne : copy.offersCountMany }}</span>
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto p-4 lg:p-5">
      <p v-if="inbox.loading && !inbox.loaded" class="text-sm text-[var(--ui-text-muted)]">{{ copy.inboxLoading }}</p>
      <div v-else-if="!inbox.threads.length" class="flex flex-col items-center gap-3 py-12 text-center text-sm text-[var(--ui-text-muted)]">
        <UIcon name="i-lucide-scale" class="text-3xl" />
        <p class="max-w-sm">{{ copy.offersEmpty }}</p>
        <UButton to="/" color="neutral" variant="soft" icon="i-lucide-messages-square">{{ copy.offersToChat }}</UButton>
      </div>

      <template v-else>
        <p v-if="board.spread && board.spread.monthly > 0" class="offers-spread mb-4 rounded-xl p-4 text-sm">
          <UIcon name="i-lucide-piggy-bank" class="mr-1 align-[-3px] text-base" aria-hidden="true" />
          {{ copy.offersSpreadLead }} <strong>{{ formatCents(board.spread.monthly) }} {{ copy.offersPerMonth }}</strong>,
          {{ copy.offersSpreadTotal }} <strong>{{ formatMoney(board.spread.total) }}</strong> {{ copy.offersSpreadEnd }}
        </p>

        <div class="offers-grid hidden px-4 pb-2 text-xs font-medium uppercase tracking-wide text-[var(--ui-text-dimmed)] md:grid">
          <span>{{ copy.offersBank }}</span>
          <span>{{ copy.inboxOfferRate }}</span>
          <span>{{ copy.inboxOfferMonthly }}</span>
          <span>{{ copy.offersTotal }}</span>
          <span class="text-right">{{ copy.offersStatus }}</span>
        </div>

        <ul class="space-y-2" aria-live="polite">
          <li v-for="row in board.rows" :key="row.thread.threadId">
            <button
              type="button"
              class="offers-row offers-grid w-full rounded-xl border p-4 text-left transition focus-visible:outline-2 focus-visible:outline-[var(--ui-primary)]"
              :class="[
                row.thread.status === 'ACCEPTED' ? 'offers-row-accepted' : row.best ? 'offers-row-best' : 'border-[var(--ui-border)]',
                ['CLOSED', 'DECLINED'].includes(row.thread.status) ? 'opacity-60' : '',
                flashing.has(row.thread.threadId) ? 'offers-row-flash' : '',
              ]"
              :aria-label="`${row.thread.bank.name}: ${copy.offersOpenThread}`"
              @click="inbox.open(row.thread.threadId)"
            >
              <span class="flex min-w-0 items-center gap-2">
                <span v-if="row.thread.unread" class="size-2 shrink-0 rounded-full bg-[var(--ui-primary)]" :aria-label="copy.unread" />
                <span class="truncate font-semibold">{{ row.thread.bank.name }}</span>
                <span v-if="row.best" class="shrink-0 rounded-md bg-[var(--ui-primary)] px-2 py-0.5 text-xs font-semibold text-white">{{ copy.offersBest }}</span>
              </span>

              <template v-if="row.offer">
                <span class="text-sm">
                  <span class="md:hidden text-[var(--ui-text-muted)]">{{ copy.inboxOfferRate }}: </span>
                  <span class="font-semibold">{{ formatNumber(row.offer.ratePct, 2) }}%</span>
                  <span class="block text-xs text-[var(--ui-text-muted)]">{{ formatNumber(row.offer.euriborPct, 2) }}% {{ copy.chartEuribor }} + {{ formatNumber(row.offer.marginPct, 2) }}% {{ copy.chartMargin }}</span>
                  <span v-if="row.change && row.change.marginPct !== 0" class="offers-change block text-xs font-semibold">{{ signed(row.change.marginPct, n => `${formatNumber(n, 2)}%`) }} {{ copy.chartMargin }}</span>
                </span>
                <span class="text-sm">
                  <span class="md:hidden text-[var(--ui-text-muted)]">{{ copy.inboxOfferMonthly }}: </span>
                  <span class="font-semibold">{{ formatCents(row.offer.monthlyPayment) }}</span>
                  <span class="mt-1 block h-1.5 rounded-full bg-[var(--ui-bg-accented)]" aria-hidden="true">
                    <span class="block h-full rounded-full" :class="row.best || row.thread.status === 'ACCEPTED' ? 'bg-[var(--ui-primary)]' : 'bg-[var(--ui-border-accented)]'" :style="{ width: barWidth(row.offer.monthlyPayment) }" />
                  </span>
                  <span v-if="row.change && row.change.monthlyPayment !== 0" class="offers-change mt-1 block text-xs font-semibold">{{ signed(row.change.monthlyPayment, formatCents) }}{{ copy.offersPerMonthShort }}</span>
                </span>
                <span class="text-sm">
                  <span class="md:hidden text-[var(--ui-text-muted)]">{{ copy.offersTotal }}: </span>
                  <span class="font-semibold">{{ formatMoney(totalRepaid(row.offer)) }}</span>
                  <span class="block text-xs text-[var(--ui-text-muted)]">{{ formatMoney(row.offer.amount) }} · {{ row.offer.termYears }} {{ copy.inboxOfferYears }}</span>
                </span>
              </template>
              <span v-else class="text-sm text-[var(--ui-text-muted)] md:col-span-3">{{ row.thread.status === 'DECLINED' ? copy.offersDeclined : copy.offersWaiting }}</span>

              <span class="md:text-right">
                <span :class="['inline-block rounded-md px-2 py-0.5 text-xs font-medium', statusClass(row.thread.status)]">{{ copy.inboxStatus[row.thread.status] }}</span>
              </span>
            </button>
          </li>
        </ul>
        <p class="mt-4 text-xs text-[var(--ui-text-dimmed)]">{{ copy.offersFootnote }}</p>
      </template>
    </div>
  </div>
</template>

<style scoped>
.offers-grid {
  display: grid;
  gap: 0.5rem;
  align-items: start;
}
@media (min-width: 768px) {
  .offers-grid {
    grid-template-columns: minmax(110px, 1.1fr) minmax(130px, 1.3fr) minmax(120px, 1.2fr) minmax(120px, 1.2fr) minmax(90px, 0.8fr);
    gap: 1rem;
  }
}
.offers-row:hover { background: var(--ui-bg-muted); }
.offers-row-best {
  border-color: var(--ui-primary);
  background: color-mix(in srgb, var(--ui-primary) 6%, var(--ui-bg));
  box-shadow: 0 0 0 1px var(--ui-primary);
}
.offers-row-accepted {
  border-color: rgb(34 197 94);
  background: color-mix(in srgb, rgb(34 197 94) 8%, var(--ui-bg));
}
.offers-change { color: rgb(21 128 61); }
:global(.dark) .offers-change { color: rgb(134 239 172); }
.offers-spread {
  background: linear-gradient(120deg, color-mix(in srgb, var(--ui-primary) 12%, var(--ui-bg)), color-mix(in srgb, var(--accent-violet) 12%, var(--ui-bg)));
  border: 1px solid color-mix(in srgb, var(--ui-primary) 25%, transparent);
}
.offers-row-flash { animation: offers-flash 1.2s ease-in-out 3; }
@keyframes offers-flash {
  50% { box-shadow: 0 0 0 3px var(--accent-gold); }
}
@media (prefers-reduced-motion: reduce) {
  .offers-row-flash { animation: none; box-shadow: 0 0 0 3px var(--accent-gold); }
}
</style>
