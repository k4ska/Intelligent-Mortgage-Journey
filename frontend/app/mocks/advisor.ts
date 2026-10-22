import type { ChatInput } from '~/composables/useApi'
import type { ApplicationResult, BankOption, ChatMessage, DocumentType, DocumentView, InboxEmail, InboxThread, InboxThreadDetail, Profile, ProfileField, Property, PropertyField, StreamEvent } from '~/types/api'
import { mergeProfileFields } from '~/utils/profile'

// Explicit development-only mock. Never sends or stores passwords. It intentionally
// does NOT simulate secure authentication: use the real API for that.
interface MockUser { id: string; email: string }
interface MockData { messages: ChatMessage[]; profile: Profile; documents?: DocumentView[]; pendingDocumentType?: DocumentType; nextId: number }
const USERS = 'mortgage-mock-users'
const CURRENT = 'mortgage-mock-current'

function read<T>(key: string, fallback: T): T {
  try { return JSON.parse(localStorage.getItem(key) || '') as T }
  catch { return fallback }
}
function write(key: string, value: unknown) { localStorage.setItem(key, JSON.stringify(value)) }
function users() { return read<MockUser[]>(USERS, []) }
function current(): MockUser {
  const user = read<MockUser | null>(CURRENT, null)
  if (!user) throw Object.assign(new Error('Not authenticated'), { status: 401 })
  return user
}
function key(user: MockUser) { return `mortgage-mock-data:${user.id}` }
function data(user: MockUser): MockData {
  return read<MockData>(key(user), {
    messages: [{ id: 1, role: 'advisor', text: 'Hi, how can I help you?', createdAt: new Date().toISOString(), corrected: false }],
    profile: { fields: [], property: null },
    nextId: 2,
  })
}
function save(user: MockUser, value: MockData) { write(key(user), value) }
export function mockMe(): MockUser { return current() }
export function mockRegister(email: string): MockUser {
  const normalized = email.trim().toLowerCase()
  if (users().some(user => user.email === normalized)) {
    throw Object.assign(new Error('Email already registered'), { status: 409 })
  }
  const user = { id: `mock-${normalized}`, email: normalized }
  write(USERS, [...users(), user])
  return user
}
export function mockLogin(email: string): MockUser {
  const user = users().find(user => user.email === email.trim().toLowerCase())
  if (!user) throw Object.assign(new Error('Invalid credentials'), { status: 401 })
  write(CURRENT, user)
  return user
}
export function mockLogout() { localStorage.removeItem(CURRENT) }
export function mockChat(): ChatMessage[] { return data(current()).messages }
/** Start over: forget this user's chat, profile and inbox; the account stays. */
export function mockReset(): ChatMessage[] {
  const user = current()
  localStorage.removeItem(key(user))
  localStorage.removeItem(inboxKey(user))
  return data(user).messages
}
export function mockProfile(): Profile { return data(current()).profile }

// The mocked checklist mirrors DocumentType on the backend. Files are never stored.
const DOCUMENT_TEMPLATES: DocumentView[] = [
  { id: null, type: 'ID', label: 'ID card or passport', status: 'NOT_NEEDED_YET', whereToGet: 'Your own ID card or passport.', why: 'The bank has to know who it lends to.', tips: "Check that it is valid for the next few months; an expired document can't be used.", fileName: null, values: {} },
  { id: null, type: 'PAYSLIP', label: 'Proof of income (payslips or an income statement)', status: 'NOT_NEEDED_YET', whereToGet: 'Your employer, or the Tax and Customs Board (EMTA) e-services.', why: 'It shows that your income is real and regular, which decides how much you can borrow.', tips: 'Banks look at about the last 6 months. A permanent contract past its trial period counts best; if you changed jobs recently, tell the bank up front.', fileName: null, values: {} },
  { id: null, type: 'BANK_STATEMENT', label: 'Bank statement, last 6 months', status: 'NOT_NEEDED_YET', whereToGet: 'Your internet bank.', why: 'It shows your salary coming in, your regular spending and any loan payments the bank should count.', tips: "In the months before applying, avoid new consumer loans or hire purchase, stay out of overdraft, close credit cards you don't use (banks count the limit), and have an explanation ready for any large or unusual transfers.", fileName: null, values: {} },
  { id: null, type: 'DOWN_PAYMENT_PROOF', label: 'Proof of down payment funds', status: 'NOT_NEEDED_YET', whereToGet: 'Your internet bank (savings account statement).', why: 'Banks lend only part of the price; it shows you have the rest yourself.', tips: 'Keep the money in your own account for a while before applying. If part of it is a gift from family, say so and be ready to show where it came from.', fileName: null, values: {} },
  { id: null, type: 'VALUATION', label: 'Valuation report', status: 'NOT_NEEDED_YET', whereToGet: 'A bank-approved licensed valuator, ordered after you have picked the property.', why: 'The bank lends against what the home is worth, not just its asking price.', tips: 'Order it only once you have agreed on the home, from a valuator your bank accepts.', fileName: null, values: {} },
  { id: null, type: 'PURCHASE_AGREEMENT', label: 'Purchase agreement or reservation', status: 'NOT_NEEDED_YET', whereToGet: 'The seller or the real estate agent.', why: 'It shows which home you are buying and on what terms.', tips: 'A reservation or preliminary agreement is usually enough to apply; check that its price matches what you told the bank.', fileName: null, values: {} },
  { id: null, type: 'CO_APPLICANT_ID', label: "Co-applicant's ID card or passport", status: 'NOT_NEEDED_YET', whereToGet: 'The co-applicant.', why: 'The bank has to know everyone who borrows.', tips: 'Check that it is valid for the next few months.', fileName: null, values: {} },
  { id: null, type: 'CO_APPLICANT_PAYSLIP', label: "Co-applicant's proof of income", status: 'NOT_NEEDED_YET', whereToGet: 'The co-applicant: their employer or EMTA e-services.', why: 'The bank counts both incomes, so it needs proof of theirs too.', tips: 'The same as yours: banks look at about the last 6 months of regular income.', fileName: null, values: {} },
]

function checklist(state: MockData): DocumentView[] {
  const hasCoApplicant = state.profile.fields.some(field => field.key === 'hasCoApplicant' && field.value === 'yes')
  return DOCUMENT_TEMPLATES
    .filter(template => !template.type.startsWith('CO_APPLICANT_') || hasCoApplicant || state.documents?.some(doc => doc.type === template.type))
    .map(template => {
      const stored = state.documents?.find(doc => doc.type === template.type)
      return { ...(stored ?? template), values: { ...(stored?.values ?? {}) } }
    })
}

function documentStatus(document: DocumentView): StreamEvent {
  const { type, label, status, whereToGet, why, tips, fileName } = document
  return { type: 'document_status', data: { type, label, status, whereToGet, why, tips, fileName } }
}

export function mockDocuments(): DocumentView[] { return checklist(data(current())) }

export function mockUploadDocument(type: DocumentType, file: File): DocumentView {
  const user = current()
  const state = data(user)
  const document = checklist(state).find(item => item.type === type)
  if (!document) throw Object.assign(new Error('Document does not apply'), { status: 400 })
  if (document.status === 'NOT_NEEDED_YET') {
    throw Object.assign(new Error("Your advisor hasn't asked for this document yet"), { status: 409 })
  }
  const updated: DocumentView = {
    ...document, id: document.id ?? `mock-${type.toLowerCase()}`,
    status: 'UPLOADED', fileName: file.name || 'document', values: {},
  }
  state.documents = checklist(state).map(item => item.type === type ? updated : item)
  save(user, state)
  return updated
}

/** The trigger's reply has no user bubble, just like the backend. */
function mockDocumentTrigger(input: Exclude<ChatInput, string>): Response {
  const user = current()
  const state = data(user)
  const document = checklist(state).find(doc => doc.id === input.documentId)
  if (!document) throw Object.assign(new Error('No such document'), { status: 404 })
  if (document.status !== 'UPLOADED' || Object.keys(document.values).length) {
    throw Object.assign(new Error('Document was already read'), { status: 409 })
  }

  const reads: Partial<Record<DocumentType, Partial<Record<ProfileField['key'], string>>>> = {
    PAYSLIP: { netIncome: '2350' },
    BANK_STATEMENT: { monthlyDebtPayments: '150', monthlyLivingCosts: '900' },
    DOWN_PAYMENT_PROOF: { savings: '30000' },
    CO_APPLICANT_PAYSLIP: { coApplicantNetIncome: '2000' },
  }
  const values = { ...reads[document.type] }
  const fields: ProfileField[] = []
  for (const key of Object.keys(values) as ProfileField['key'][]) {
    // Do not contradict a value the user already told the advisor.
    const value = state.profile.fields.find(field => field.key === key)?.value ?? values[key]!
    values[key] = value
    fields.push({ key, value, source: 'DOCUMENT', status: 'UNCERTAIN', sourceDocument: document.type })
  }
  mergeProfileFields(state.profile.fields, fields)
  const updated: DocumentView = { ...document, values, status: fields.length ? 'UPLOADED' : 'CONFIRMED' }
  state.documents = checklist(state).map(doc => doc.type === document.type ? updated : doc)
  state.pendingDocumentType = fields.length ? document.type : undefined
  state.messages.push({ id: state.nextId++, role: 'system', text: `Uploaded ${document.fileName} as: ${document.label}`, createdAt: new Date().toISOString(), corrected: false })
  save(user, state)

  const answer = fields.length
    ? `I read ${fields.map(field => `${field.key}: ${field.value}`).join(', ')} from your ${document.label.toLowerCase()}. Is that right?`
    : `Thanks, your ${document.label.toLowerCase()} has been confirmed.`
  return mockReply(user, state, answer, [
    ...(fields.length ? [{ type: 'profile_updated', data: { fields } } as StreamEvent] : []),
    documentStatus(updated),
  ])
}

function mockReply(user: MockUser, state: MockData, answer: string, events: StreamEvent[], corrected = false): Response {
  const parts = answer.match(/.{1,12}/gs) || [answer]
  const encoder = new TextEncoder()
  let cancelled = false
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const emit = (event: StreamEvent) => {
        controller.enqueue(encoder.encode(`event:${event.type}\ndata:${JSON.stringify(event.data)}\n\n`))
      }
      try {
        for (const event of events) emit(event)
        for (const delta of parts) {
          await new Promise(resolve => setTimeout(resolve, 65))
          if (cancelled) return
          emit({ type: 'text', data: { delta } })
        }
        const id = state.nextId++
        const finalAnswer = corrected
          ? 'I cannot guarantee approval. A bank needs to assess your finances and application first.'
          : answer
        state.messages.push({ id, role: 'advisor', text: finalAnswer, createdAt: new Date().toISOString(), corrected })
        save(user, state)
        if (corrected) emit({ type: 'corrected', data: { messageId: id, text: finalAnswer } })
        emit({ type: 'done', data: { messageId: id } })
        controller.close()
      } catch (error) { controller.error(error) }
    },
    cancel() { cancelled = true },
  })
  return new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
}

function detectIncome(message: string): ProfileField[] {
  // A deliberately small mock rule for testing profile_updated before text, not an AI.
  const match = message.match(/(?:earn|income|salary|make|take.home)[^\d€]{0,45}€?\s*([\d\s,.]+)/i)
  if (!match) return []
  const raw = match[1]!.trim().replace(/[\s,]/g, '')
  const amount = Number(raw)
  return Number.isFinite(amount) && amount > 0
    ? [{ key: 'netIncome', value: String(amount), source: 'CHAT', status: 'CONFIRMED' }]
    : []
}

// A made-up property for the mock. Listing data is confirmed; values inferred from
// pasted page text are uncertain until the user confirms them in chat.
function sampleProperty(listingUrl: string | null, source: PropertyField['source'], status: PropertyField['status']): Property {
  return {
    listingUrl,
    fields: [
      { key: 'type', value: 'APARTMENT', source, status },
      { key: 'price', value: '189000', source, status },
      { key: 'location', value: 'Tallinn, Kesklinn', source, status },
      { key: 'size', value: '61.4', source, status },
      { key: 'rooms', value: '3', source, status },
      { key: 'yearBuilt', value: '1937', source, status },
    ],
  }
}

// Development fixtures only. The real picker always uses the banks from the SSE event;
// these match the bank ids the backend uses (#51).
const MOCK_BANKS: BankOption[] = [
  { id: 'seb', name: 'SEB' },
  { id: 'swedbank', name: 'Swedbank' },
  { id: 'lhv', name: 'LHV' },
  { id: 'luminor', name: 'Luminor' },
  { id: 'coop', name: 'Coop Pank' },
]

interface MockInboxData {
  nextId: number
  threads: InboxThread[]
  emails: Record<string, InboxEmail[]>
}

function inboxKey(user: MockUser) { return `mortgage-mock-inbox:${user.id}` }
function inboxData(user: MockUser): MockInboxData {
  return read<MockInboxData>(inboxKey(user), { nextId: 1, threads: [], emails: {} })
}

export function mockSubmitApplication(bankIds: string[]): ApplicationResult {
  const user = current()
  const selected = [...new Set(bankIds)]
  if (!selected.length || selected.some(id => !MOCK_BANKS.some(bank => bank.id === id))) {
    throw Object.assign(new Error('Invalid banks'), { status: 400 })
  }

  const state = inboxData(user)
  const applicationId = `mock-application-${state.nextId++}`
  const threads: ApplicationResult['threads'] = []
  for (const id of selected) {
    const bank = MOCK_BANKS.find(option => option.id === id)!
    const threadId = `mock-thread-${state.nextId++}`
    const createdAt = new Date().toISOString()
    const subject = 'Your mortgage application'
    const body = `Thank you for applying to ${bank.name}. Could you confirm the property you would like to buy and the loan amount you need? We look forward to hearing from you.`
    const email: InboxEmail = {
      id: `mock-email-${state.nextId++}`,
      from: 'BANK',
      type: 'question',
      subject,
      body,
      offer: null,
      createdAt,
    }
    state.emails[threadId] = [email]
    state.threads.unshift({
      threadId,
      bank,
      status: 'OPEN',
      unread: true,
      lastEmail: { subject, preview: body.slice(0, 120), createdAt },
    })
    threads.push({ threadId, bankId: id, status: 'OPEN' })
  }
  write(inboxKey(user), state)
  return { applicationId, threads }
}

export function mockGetInbox(): InboxThread[] {
  return inboxData(current()).threads
}

export function mockGetThread(threadId: string): InboxThreadDetail {
  const user = current()
  const state = inboxData(user)
  const thread = state.threads.find(item => item.threadId === threadId)
  if (!thread) throw Object.assign(new Error('Thread not found'), { status: 404 })
  thread.unread = false
  write(inboxKey(user), state)
  return { threadId, bank: thread.bank, status: thread.status, emails: state.emails[threadId] || [] }
}

export function mockSend(input: ChatInput): Response {
  if (typeof input !== 'string') return mockDocumentTrigger(input)
  const text = input
  const user = current()
  const state = data(user)
  state.messages.push({ id: state.nextId++, role: 'user', text, createdAt: new Date().toISOString(), corrected: false })
  const fields = detectIncome(text)
  if (/\b(?:have|with|using) (?:a )?co.applicant\b/i.test(text)) {
    fields.push({ key: 'hasCoApplicant', value: 'yes', source: 'CHAT', status: 'CONFIRMED' })
  }
  mergeProfileFields(state.profile.fields, fields)

  let propertyUpdate: Property | null = null
  let propertyReply = ''
  const link = text.match(/\b(?:https?:\/\/)?(?:www\.)?kv\.ee\/[^\s<>"']+/i)?.[0]
    .replace(/[.,;!?)]*$/, '')
  if (link) {
    const listingUrl = /^https?:\/\//i.test(link) ? link : `https://${link}`
    propertyUpdate = sampleProperty(listingUrl, 'LISTING', 'CONFIRMED')
    propertyReply = 'I have added a sample property from that kv.ee link.'
  } else if (/^\s*pasted (?:page|listing) text:/i.test(text)) {
    propertyUpdate = sampleProperty(null, 'CHAT', 'UNCERTAIN')
    propertyReply = 'I have extracted sample details from the pasted page text. Are these correct?'
  }

  const documentEvents: StreamEvent[] = []
  let documentReply = ''
  const waiting = checklist(state).filter(document => document.status === 'UPLOADED' && Object.keys(document.values).length > 0)
  const pending = waiting.find(document => document.type === state.pendingDocumentType) ?? waiting[0]
  const confirmed = /^(?:yes|yes,? (?:that['’]s |it is )?correct|confirm)[.!]?$/i.test(text.trim())
  const correction = /^(?:no|not quite)[,;:]? .*?([\d][\d\s,.]*)/i.exec(text.trim())
  if (pending && (confirmed || correction)) {
    const readKeys = Object.keys(pending.values) as ProfileField['key'][]
    const confirmedFields = state.profile.fields.filter(field => readKeys.includes(field.key))
    for (const field of confirmedFields) {
      if (correction && confirmedFields.length === 1) {
        field.value = correction[1]!.replace(/[\s,]/g, '')
        field.source = 'CHAT'
        delete field.sourceDocument
      }
      field.status = 'CONFIRMED'
    }
    const updated: DocumentView = { ...pending, status: 'CONFIRMED' }
    state.pendingDocumentType = undefined
    state.documents = checklist(state).map(document => document.type === pending.type ? updated : document)
    if (confirmedFields.length) documentEvents.push({ type: 'profile_updated', data: { fields: confirmedFields } })
    documentEvents.push(documentStatus(updated))
    documentReply = 'Thanks — I have confirmed the values from your document.'
  } else if (confirmed && state.profile.property?.fields.some(field => field.status === 'UNCERTAIN')) {
    const existing = state.profile.property
    propertyUpdate = { ...existing, fields: existing.fields.map(field => ({ ...field, status: 'CONFIRMED' as const })) }
    propertyReply = 'Thanks — I have confirmed the property details.'
  }

  const askingForDocuments = /\b(documents?|paperwork|payslip)\b/i.test(text)
    && /\b(need|ask|request|upload|show)\b/i.test(text)
  if (askingForDocuments) {
    const requested: DocumentType[] = ['ID', 'PAYSLIP', 'BANK_STATEMENT', 'DOWN_PAYMENT_PROOF']
    if (state.profile.fields.some(field => field.key === 'hasCoApplicant' && field.value === 'yes')) {
      requested.push('CO_APPLICANT_ID', 'CO_APPLICANT_PAYSLIP')
    }
    const currentDocuments = checklist(state)
    state.documents = currentDocuments.map(document => requested.includes(document.type) && document.status === 'NOT_NEEDED_YET'
      ? { ...document, id: `mock-${document.type.toLowerCase()}`, status: 'NEEDED' as const }
      : document)
    for (const document of state.documents) {
      if (requested.includes(document.type)) documentEvents.push(documentStatus(document))
    }
    documentReply = 'I have requested the documents needed for your application. You can upload them in the left panel.'
  }
  if (propertyUpdate) state.profile.property = propertyUpdate
  save(user, state)

  const wantsBanks = /\b(banks?|lenders?)\b/i.test(text)
    && /\b(apply|application|send|pick|choose|show|ready|go)\b/i.test(text)
  const ready = state.profile.fields.some(field => field.key === 'netIncome')
  const showPicker = wantsBanks && ready
  const shouldCorrect = /\bcorrect me\b/i.test(text)
  const answer = shouldCorrect
    ? 'Your loan is guaranteed to be approved.'
    : documentReply || (wantsBanks
      ? showPicker
        ? 'You can choose one or more banks below and send your application.'
        : 'Before we contact banks, please tell me your monthly net income.'
      : propertyUpdate
        ? propertyReply
        : fields.length
          ? 'Thanks — I have added those details to your profile. What else should I know?'
          : 'Thanks for sharing. Tell me a little more about your finances or the home you are looking for.')
  return mockReply(user, state, answer, [
    ...(fields.length ? [{ type: 'profile_updated', data: { fields } } as StreamEvent] : []),
    ...(propertyUpdate ? [{ type: 'property_updated', data: { property: propertyUpdate } } as StreamEvent] : []),
    ...documentEvents,
    ...(showPicker ? [{ type: 'bank_picker', data: { banks: MOCK_BANKS } } as StreamEvent] : []),
  ], shouldCorrect)
}