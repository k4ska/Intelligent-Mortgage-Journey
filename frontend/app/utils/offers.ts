import type { InboxThread, InboxThreadStatus, MortgageOffer } from '~/types/api'

/** Threads whose offer can still be accepted or improved. */
const LIVE: InboxThreadStatus[] = ['OFFER', 'NEGOTIATING']

/** Everything the user pays back over the term: the monthly payment times the number of months. */
export function totalRepaid(offer: MortgageOffer): number {
  return offer.monthlyPayment * offer.termYears * 12
}

export interface OfferRow {
  thread: InboxThread
  offer: MortgageOffer | null
  /** What the last counter-offer changed; null when there was no earlier offer. */
  change: { marginPct: number; monthlyPayment: number } | null
  best: boolean
}

export interface OfferBoard {
  rows: OfferRow[]
  /** Offers that can still be accepted. */
  liveCount: number
  /** Monthly and total difference between the cheapest and the dearest live offer, when there are two or more. */
  spread: { monthly: number; total: number } | null
}

/**
 * One row per bank, cheapest offer first, then the banks without one. The best offer is the
 * cheapest one that can still be accepted; once one is accepted, that one is the only
 * highlight, so no row is marked best.
 */
export function offerBoard(threads: InboxThread[]): OfferBoard {
  const accepted = threads.some(thread => thread.status === 'ACCEPTED')
  const live = threads.filter(thread => thread.offer && LIVE.includes(thread.status))
  const payments = live.map(thread => thread.offer!.monthlyPayment)
  const cheapest = payments.length ? Math.min(...payments) : null

  const rows = threads.map((thread): OfferRow => {
    const offer = thread.offer
    const previous = thread.previousOffer
    return {
      thread,
      offer,
      change: offer && previous
        ? { marginPct: round2(offer.marginPct - previous.marginPct), monthlyPayment: round2(offer.monthlyPayment - previous.monthlyPayment) }
        : null,
      best: !accepted && !!offer && LIVE.includes(thread.status) && offer.monthlyPayment === cheapest,
    }
  }).sort((a, b) => rank(a) - rank(b) || (a.offer?.monthlyPayment ?? 0) - (b.offer?.monthlyPayment ?? 0))

  let spread: OfferBoard['spread'] = null
  if (live.length >= 2) {
    const offers = live.map(thread => thread.offer!)
    const low = offers.reduce((a, b) => (b.monthlyPayment < a.monthlyPayment ? b : a))
    const high = offers.reduce((a, b) => (b.monthlyPayment > a.monthlyPayment ? b : a))
    spread = { monthly: round2(high.monthlyPayment - low.monthlyPayment), total: round2(totalRepaid(high) - totalRepaid(low)) }
  }
  return { rows, liveCount: live.length, spread }
}

/** Accepted first, then live offers, then everything else. */
function rank(row: OfferRow): number {
  if (row.thread.status === 'ACCEPTED') return 0
  if (row.offer && LIVE.includes(row.thread.status)) return 1
  if (row.offer) return 2
  return 3
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

export function statusClass(status: InboxThreadStatus): string {
  switch (status) {
    case 'ACCEPTED': return 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300'
    case 'OFFER': return 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
    case 'NEGOTIATING': return 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-300'
    case 'DECLINED': return 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
    default: return 'bg-[var(--ui-bg-muted)] text-[var(--ui-text-muted)]'
  }
}
