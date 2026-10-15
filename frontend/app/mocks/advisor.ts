import type { ChatMessage, Profile, ProfileField, StreamEvent } from '~/types/api'
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

export function mockSend(text: string): Response {
  const user = current()
  const state = data(user)
  state.messages.push({ id: state.nextId++, role: 'user', text, createdAt: new Date().toISOString() })
  const fields = detectIncome(text)
  mergeProfileFields(state.profile.fields, fields)
  save(user, state)

  const answer = fields.length
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