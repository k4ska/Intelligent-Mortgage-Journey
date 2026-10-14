import type { Answers, BankOffer, LoanProfile, ReviewResponse } from '~/types/api'
import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces'
import { formatMoney, formatNumber } from '~/utils/numbers'

/**
 * Client-only export: all data has already been loaded on ProfileSummary.
 * Nothing is posted to the backend, no agent is called and no PDF is retained.
 */
export interface LoanProfilePdfData {
  profile: LoanProfile
  offers: BankOffer[]
  review: ReviewResponse
  answers: Answers
}

const ink = '#172554'
const muted = '#64748B'
const line = '#E2E8F0'

function readable(value: string | null | undefined): string {
  return value?.trim() ? value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()) : 'Not collected'
}

function enteredMoney(value: unknown): string {
  return typeof value === 'number' && Number.isFinite(value) ? formatMoney(value) : 'Not collected'
}

function localDate(date: Date): string {
  const part = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}`
}

function details(title: string, rows: Array<[string, string]>): Content[] {
  return [
    { text: title, style: 'sectionTitle', headlineLevel: 1, margin: [0, 16, 0, 7] },
    {
      table: {
        widths: ['43%', '*'],
        body: rows.map(([label, value]) => [
          { text: label, color: muted, margin: [0, 5, 7, 5] },
          { text: value, color: ink, bold: true, margin: [0, 5, 0, 5] },
        ]),
      },
      layout: {
        hLineWidth: (i: number) => (i === 0 || i === rows.length ? 0 : 0.6),
        vLineWidth: () => 0,
        hLineColor: () => line,
        paddingLeft: () => 0,
        paddingRight: () => 5,
      },
    },
  ]
}

function bullets(items: string[], emptyText: string): Content {
  return items.length
    ? { ul: items.map(text => ({ text, margin: [0, 2, 0, 2] })), margin: [5, 0, 0, 0] }
    : { text: emptyText, color: muted, italics: true }
}

/** Pure layout builder - useful for testing without invoking a browser download. */
export function createLoanProfileDocument(data: LoanProfilePdfData, generatedAt = new Date()): TDocumentDefinitions {
  const { profile, offers, review, answers } = data
  const f = profile.financials
  const income = profile.applicants.reduce((total, a) => total + a.monthlyNetIncome, 0)
  const debts = profile.applicants.reduce((total, a) => total + a.monthlyDebtPayments, 0)
  const content: Content[] = [
    { text: 'LOAN PROFILE', style: 'title' },
    { text: `Generated ${localDate(generatedAt)}`, style: 'meta', margin: [0, 2, 0, 13] },
    {
      table: { widths: ['*'], body: [[{
        text: 'PROTOTYPE - indicative figures, not a loan offer or a real application. No bank has reviewed or approved this profile.',
        color: ink, bold: true, fontSize: 9, margin: [11, 9, 11, 9],
        fillColor: '#EFF6FF',
      }]] },
      layout: 'noBorders',
    },
  ]

  profile.applicants.forEach((applicant, index) => {
    const isPrimary = index === 0
    const rawGross = isPrimary ? answers.monthlyGrossIncome : answers.coApplicantMonthlyGrossIncome
    const person = isPrimary ? 'Primary applicant' : 'Co-applicant'
    const name = applicant.name?.trim()
    const rows: Array<[string, string]> = [
      ['Employment', readable(applicant.employment)],
      ['Monthly gross income', enteredMoney(rawGross)],
      ['Monthly net income', formatMoney(applicant.monthlyNetIncome)],
      ['Existing debt payments / month', formatMoney(applicant.monthlyDebtPayments)],
    ]
    if (isPrimary && answers.employmentType === 'SELF_EMPLOYED') {
      rows.push(['Last year net business profit', enteredMoney(answers.lastYearNetProfit)])
    }
    content.push(...details(name ? `${person} - ${name}` : person, rows))
  })

  content.push(...details('Household totals', [
    ['Net income / month', formatMoney(income)],
    ['Existing debt payments / month', formatMoney(debts)],
  ]))

  content.push(...details('The property', [
    ['Type', readable(profile.property.type)],
    ['Address', typeof answers.propertyAddress === 'string' && answers.propertyAddress.trim()
      ? answers.propertyAddress.trim() : profile.property.location],
    ['Purchase price / stated value', formatMoney(profile.property.purchasePrice)],
    ['Estimated property value', formatMoney(profile.property.estimatedValue)],
  ]))

  content.push(...details('The loan', [
    ['Purpose', readable(profile.loanRequest.purpose)],
    ['Loan amount', formatMoney(profile.loanRequest.amount)],
    ['Down payment', formatMoney(f.downPayment)],
    ['Term', `${formatNumber(profile.loanRequest.termYears)} years`],
    ['Rate type', readable(profile.loanRequest.rateType)],
  ]))

  content.push(...details('Key affordability figures', [
    ['Loan-to-value (LTV)', `${formatNumber(f.ltv, 1)}%`],
    ['Debt-to-income (DTI)', `${formatNumber(f.dti, 1)}%`],
    ['Estimated payment / month', formatMoney(f.estimatedMonthlyPayment)],
    ['Stressed payment / month', formatMoney(f.stressedMonthlyPayment)],
    ['Stressed DTI', `${formatNumber(f.stressedDti, 1)}%`],
    ['Monthly buffer after stress', formatMoney(f.monthlyBufferAfterStress)],
    ['Stress test result', f.stressTestResult],
  ]))
  content.push({
    text: 'Stress-test payment uses the application\'s current rule: contract rate +2 percentage points, with a 6% minimum. The DTI limit is 50% of net household income.',
    style: 'smallNote', margin: [0, 5, 0, 0],
  })

  content.push({ text: "The assistant's read-back", style: 'sectionTitle', headlineLevel: 1, margin: [0, 17, 0, 7] })
  content.push({ text: review.summary, lineHeight: 1.3 })
  content.push({ text: 'Concerns flagged', style: 'subTitle', margin: [0, 11, 0, 4] })
  content.push(bullets(review.concerns, 'No concerns flagged in the read-back.'))

  content.push({ text: 'Other risk indicators', style: 'subTitle', margin: [0, 12, 0, 4] })
  content.push(bullets(profile.riskIndicators, 'None listed.'))

  content.push({ text: 'Indicative offers (mock banks)', style: 'sectionTitle', headlineLevel: 1, margin: [0, 17, 0, 7] })
  content.push({ text: 'These are simulated estimates, not commitments or actual pre-approvals.', style: 'smallNote', margin: [0, 0, 0, 8] })
  if (!offers.length) {
    content.push({ text: 'No illustrative offers available.', color: muted })
  } else {
    offers.forEach((offer) => {
      content.push({
        unbreakable: true,
        stack: [
          { text: offer.bankName, style: 'subTitle', margin: [0, 0, 0, 3] },
          { text: `${formatNumber(offer.offeredRatePct, 2)}% | ${formatMoney(offer.estimatedMonthlyPayment)}/month | Mock status: ${offer.preApproved ? 'Pre-approved' : 'Not yet'}` },
          { text: `Illustrative maximum loan: ${formatMoney(offer.maxLoanAmount)}`, color: muted, margin: [0, 2, 0, 3] },
          ...offer.conditions.map(text => ({ text: `- ${text}`, color: muted, margin: [0, 1, 0, 1] })),
        ],
        margin: [0, 0, 0, 12],
      })
    })
  }

  content.push({ text: "Documents you'll need", style: 'sectionTitle', headlineLevel: 1, margin: [0, 17, 0, 7] })
  if (!profile.documents.length) {
    content.push({ text: 'No checklist items available.', color: muted })
  } else {
    profile.documents.forEach(text => content.push({ text: `[  ]  ${text}`, margin: [0, 3, 0, 3] }))
  }

  content.push({
    text: 'Use this profile to prepare for a conversation with a lender. Check all figures and requirements with the bank before making any decisions.',
    style: 'smallNote', margin: [0, 14, 0, 0],
  })

  return {
    pageSize: 'A4',
    pageMargins: [44, 48, 44, 48],
    // Prevent a section heading from being stranded at the bottom of an A4 page.
    pageBreakBefore: (currentNode: { headlineLevel?: number }, followingNodesOnPage: unknown[]) =>
      currentNode.headlineLevel === 1 && followingNodesOnPage.length === 0,
    info: { title: 'Indicative loan profile', author: 'Intelligent Mortgage Journey', subject: 'Prototype loan profile' },
    defaultStyle: { font: 'Roboto', fontSize: 9.5, color: '#334155' },
    styles: {
      title: { fontSize: 23, bold: true, color: ink, characterSpacing: 0.7 },
      meta: { fontSize: 9, color: muted },
      sectionTitle: { fontSize: 13, bold: true, color: ink },
      subTitle: { fontSize: 10, bold: true, color: ink },
      smallNote: { fontSize: 8, color: muted, lineHeight: 1.2 },
    },
    footer: (currentPage: number, pageCount: number) => ({
      columns: [
        { text: 'PROTOTYPE - NOT A LOAN OFFER', color: muted, alignment: 'left' },
        { text: `${currentPage} / ${pageCount}`, alignment: 'right', color: muted },
      ],
      fontSize: 7.5,
      margin: [44, 12, 44, 0],
    }),
    content,
  }
}

export async function downloadLoanProfilePdf(data: LoanProfilePdfData): Promise<void> {
  if (!import.meta.client) return

  // Lazy-loaded only when the user clicks Download PDF, not on every summary visit.
  const [pdfMakeModule, fontsModule] = await Promise.all([
    import('pdfmake/build/pdfmake'),
    import('pdfmake/build/vfs_fonts'),
  ])
  const pdfMake = pdfMakeModule.default
  pdfMake.addVirtualFileSystem(fontsModule.default)

  const generatedAt = new Date()
  pdfMake.createPdf(createLoanProfileDocument(data, generatedAt))
    .download(`loan-profile-${localDate(generatedAt)}.pdf`)
}