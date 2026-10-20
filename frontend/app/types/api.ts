
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
  role: 'user' | 'advisor'
  text: string
  createdAt: string
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
  // The verifier replaced the reply that just streamed with a safe version (#67).
  | { type: 'corrected', data: { messageId: number, text: string } }
  | { type: 'done', data: { messageId: number } }
  | { type: 'error', data: { message: string } }

// ---- Applications and inbox (#51) ----

export interface ApplicationResult {
  applicationId: string
  threads: { threadId: string; bankId: string; status: string }[]
}

export interface InboxThread {
  threadId: string
  bank: BankOption
  status: string
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
  offer: null
  createdAt: string
}

export interface InboxThreadDetail {
  threadId: string
  bank: BankOption
  status: string
  emails: InboxEmail[]
}
