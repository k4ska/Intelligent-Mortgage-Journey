// TypeScript mirror of the backend API. The full set of contracts for the chat-based
// advisor (auth, chat, stream events, profile, documents, banks, inbox) is in
// docs/architecture.md §5; add types here as each endpoint is built, and keep them in
// sync with the backend DTOs.

export type AgentMode = 'stub' | 'openai'

export interface AgentStatus {
  mode: AgentMode
}

// ---- Chat (GET /api/chat, POST /api/chat/messages) ----

export interface ChatMessage {
  /** Increasing number; also the order of the conversation. */
  id: number
  /** 'system' notes something that happened outside the chat, e.g. "Uploaded payslip.pdf as: ..." (#72). */
  role: 'user' | 'advisor' | 'system'
  text: string
  createdAt: string
  /** True when the verifier replaced the original streamed advisor reply. */
  corrected: boolean
  /** Present on messages written by the advisor next to a bank thread (#80). */
  threadId?: string | null
}

// ---- Profile and property (GET /api/profile) ----

export type ProfileFieldSource = 'CHAT' | 'LISTING' | 'DOCUMENT'
export type ProfileFieldStatus = 'UNCERTAIN' | 'CONFIRMED'

/**
 * Keys the advisor can fill in so far (backend: ProfileKey). Amounts are plain numbers in
 * euros as strings ("2350"), yes/no answers are "yes" or "no".
 */
export type ProfileFieldKey =
  | 'netIncome'
  | 'employment'
  | 'savings'
  | 'monthlyDebtPayments'
  | 'creditCardLimits'
  | 'hasCoApplicant'
  | 'coApplicantNetIncome'
  | 'dependants'
  | 'monthlyLivingCosts'

export interface ProfileField {
  key: ProfileFieldKey
  value: string
  source: ProfileFieldSource
  status: ProfileFieldStatus
  /** The document it was read from, when source is DOCUMENT. */
  sourceDocument?: DocumentType | null
}

/** Matches backend PropertyKey and PropertyView. Only known fields are included. */
export type PropertyFieldKey = 'type' | 'price' | 'location' | 'size' | 'rooms' | 'yearBuilt'

export interface PropertyField {
  key: PropertyFieldKey
  value: string
  source: 'LISTING' | 'CHAT'
  status: ProfileFieldStatus
}

export interface Property {
  listingUrl: string | null
  fields: PropertyField[]
}

export interface Profile {
  fields: ProfileField[]
  /** Null until the user has a property in mind. */
  property: Property | null
}

// ---- Documents (GET /api/documents, POST /api/documents/{type}) (#72) ----

export type DocumentType =
  | 'ID'
  | 'PAYSLIP'
  | 'BANK_STATEMENT'
  | 'DOWN_PAYMENT_PROOF'
  | 'VALUATION'
  | 'PURCHASE_AGREEMENT'
  | 'CO_APPLICANT_ID'
  | 'CO_APPLICANT_PAYSLIP'

/** NOT_NEEDED_YET: in the checklist, but nobody has asked for it or uploaded it yet. */
export type DocumentStatus = 'NOT_NEEDED_YET' | 'NEEDED' | 'UPLOADED' | 'CONFIRMED'

export interface DocumentView {
  /** Null until the document is asked for or uploaded. */
  id: string | null
  type: DocumentType
  label: string
  status: DocumentStatus
  whereToGet: string
  /** Why the bank needs it, in one sentence. */
  why: string
  /** Honest tips for making it count in the months before applying. */
  tips: string
  fileName: string | null
  /** What the document reader read from it, by profile key. */
  values: Partial<Record<ProfileFieldKey, string>>
}

// ---- Stream events of one advisor turn (SSE event name = type) ----

export interface BankOption {
  id: string
  name: string
}

export type StreamEvent =
  | { type: 'text', data: { delta: string } }
  | { type: 'profile_updated', data: { fields: ProfileField[] } }
  | { type: 'property_updated', data: { property: Property } }
  | { type: 'bank_picker', data: { banks: BankOption[] } }
  | { type: 'draft_reply', data: { threadId: string, body: string } }
  | { type: 'document_status', data: { type: DocumentType, label: string, status: DocumentStatus, whereToGet: string, why: string, tips: string, fileName: string | null } }
  // The verifier replaced the reply that just streamed with a safe version (#67).
  | { type: 'corrected', data: { messageId: number, text: string } }
  | { type: 'done', data: { messageId: number } }
  | { type: 'error', data: { message: string } }

// ---- Applications and inbox (#51, #79) ----

export interface ApplicationResult {
  applicationId: string
  threads: { threadId: string; bankId: string; status: string }[]
}

export type InboxThreadStatus = 'OPEN' | 'OFFER' | 'NEGOTIATING' | 'ACCEPTED' | 'DECLINED' | 'CLOSED'

export interface MortgageOffer {
  amount: number
  termYears: number
  euriborPct: number
  marginPct: number
  ratePct: number
  monthlyPayment: number
  conditions: string[]
  validUntil: string
}

export interface InboxThread {
  threadId: string
  bank: BankOption
  status: InboxThreadStatus
  unread: boolean
  lastEmail: {
    subject: string
    preview: string
    createdAt: string
  }
}

export interface InboxEmail {
  id: string | number
  from: 'BANK' | 'USER'
  type: string
  subject: string
  body: string
  offer: MortgageOffer | null
  createdAt: string
}

export interface InboxThreadDetail {
  threadId: string
  bank: BankOption
  status: InboxThreadStatus
  emails: InboxEmail[]
}