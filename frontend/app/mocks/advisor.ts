
import type { ApplicationResult, BankOption, ChatMessage, InboxEmail, InboxThread, InboxThreadDetail, Profile, ProfileField, Property, PropertyField, StreamEvent } from '~/types/api'
import { mergeProfileFields } from '~/utils/profile'

// Explicit development-only mock. Never sends or stores passwords. It intentionally
// does NOT simulate secure authentication: use the real API for that.
interface MockUser { id: string; email: string }
interface MockData { messages: ChatMessage[]; profile: Profile; nextId: number }
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
    messages: [{ id: 1, role: 'advisor', text: 'Hi, how can I help you?', createdAt: new Date().toISOString() }],
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

export function mockSend(text: string): Response {
  const user = current()
  const state = data(user)
  state.messages.push({ id: state.nextId++, role: 'user', text, createdAt: new Date().toISOString() })
  const fields = detectIncome(text)
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
  } else if (/^(?:yes|yes,\s*that['’]s correct|confirm property)[.!]?$/i.test(text.trim())
      && state.profile.property?.fields.some(field => field.status === 'UNCERTAIN')) {
    const existing = state.profile.property
    propertyUpdate = {
      ...existing,
      fields: existing.fields.map(field => ({ ...field, status: 'CONFIRMED' as const })),
    }
    propertyReply = 'Thanks — I have confirmed the property details.'
  }
  if (propertyUpdate) state.profile.property = propertyUpdate
  save(user, state)

  const wantsBanks = /\b(banks?|lenders?)\b/i.test(text)
    && /\b(apply|application|send|pick|choose|show|ready|go)\b/i.test(text)
  const ready = state.profile.fields.some(field => field.key === 'netIncome')
  const showPicker = wantsBanks && ready
  const answer = wantsBanks
    ? showPicker
      ? 'You can choose one or more banks below and send your application.'
      : 'Before we contact banks, please tell me your monthly net income.'
    : propertyUpdate
      ? propertyReply
      : fields.length
        ? 'Thanks — I have added your monthly net income to your profile. What else should I know?'
        : 'Thanks for sharing. Tell me a little more about your finances or the home you are looking for.'
  const parts = answer.match(/.{1,12}/gs) || [answer]
  const encoder = new TextEncoder()
  let cancelled = false
  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const emit = (event: StreamEvent) => {
        controller.enqueue(encoder.encode(`event:${event.type}\ndata:${JSON.stringify(event.data)}\n\n`))
      }
      try {
        // First send a fact BEFORE the first text token, like the real advisor.
        if (fields.length) emit({ type: 'profile_updated', data: { fields } })
        if (propertyUpdate) emit({ type: 'property_updated', data: { property: propertyUpdate } })
        if (showPicker) emit({ type: 'bank_picker', data: { banks: MOCK_BANKS } })
        for (const delta of parts) {
          await new Promise(resolve => setTimeout(resolve, 65))
          // Like the real server, a cancelled request stops generating and saves no reply.
          if (cancelled) return
          emit({ type: 'text', data: { delta } })
        }
        const id = state.nextId++
        state.messages.push({ id, role: 'advisor', text: answer, createdAt: new Date().toISOString() })
        save(user, state)
        emit({ type: 'done', data: { messageId: id } })
        controller.close()
      } catch (error) { controller.error(error) }
    },
    cancel() { cancelled = true },
  })
  return new Response(stream, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
}
