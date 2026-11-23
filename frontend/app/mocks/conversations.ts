import type { ChatMessage, ConversationSummary } from '~/types/api'
import { mockChat, mockMe, mockReset } from '~/mocks/advisor'

// Keep mock chat history in the same browser-only storage as the existing mock API.
// Profile, documents and the inbox remain account-scoped, just as in the real backend.
interface SavedChat { id: string; messages: ChatMessage[]; nextId: number; createdAt: string; updatedAt: string }
interface MockHistory { activeId: string; chats: SavedChat[] }

function storageKey() { return `mortgage-mock-conversations:${mockMe().id}` }
function dataKey() { return `mortgage-mock-data:${mockMe().id}` }
function activeData(): { messages: ChatMessage[]; nextId: number; [key: string]: unknown } {
  return JSON.parse(localStorage.getItem(dataKey()) || 'null') ?? { messages: mockChat(), nextId: 2 }
}
function read(): MockHistory {
  const saved = localStorage.getItem(storageKey())
  if (saved) return JSON.parse(saved) as MockHistory
  const state = activeData()
  const createdAt = state.messages[0]?.createdAt ?? new Date().toISOString()
  return { activeId: 'original', chats: [{ id: 'original', messages: state.messages, nextId: state.nextId, createdAt, updatedAt: createdAt }] }
}
function write(history: MockHistory) { localStorage.setItem(storageKey(), JSON.stringify(history)) }
function snapshot(): MockHistory {
  const history = read()
  const active = history.chats.find(chat => chat.id === history.activeId)
  if (active) {
    const current = activeData()
    active.messages = current.messages
    active.nextId = current.nextId
    active.updatedAt = current.messages.at(-1)?.createdAt ?? active.createdAt
  }
  write(history)
  return history
}
function apply(chat: SavedChat) {
  const data = activeData()
  localStorage.setItem(dataKey(), JSON.stringify({ ...data, messages: chat.messages, nextId: chat.nextId }))
}
function summary(chat: SavedChat, activeId: string): ConversationSummary {
  const title = chat.messages.find(message => message.role === 'user' && !message.threadId)?.text.replace(/\s+/g, ' ').trim() || 'New conversation'
  return {
    id: chat.id, title: title.length > 60 ? `${title.slice(0, 60)}…` : title,
    createdAt: chat.createdAt, updatedAt: chat.updatedAt, active: chat.id === activeId,
  }
}
export function mockListConversations(): ConversationSummary[] {
  const history = snapshot()
  return history.chats.map(chat => summary(chat, history.activeId))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
}
export function mockNewConversation(): ConversationSummary {
  const history = snapshot()
  // Like the backend: a chat that is still just the greeting is reused, not duplicated.
  const current = history.chats.find(chat => chat.id === history.activeId)
  if (current && current.messages.length <= 1) return summary(current, history.activeId)
  const now = new Date().toISOString()
  const chat: SavedChat = {
    id: crypto.randomUUID(), createdAt: now, updatedAt: now, nextId: 2,
    messages: [{ id: 1, role: 'advisor', text: 'Hi, how can I help you?', createdAt: now, corrected: false }],
  }
  history.chats.push(chat)
  history.activeId = chat.id
  write(history)
  apply(chat)
  return summary(chat, history.activeId)
}
export function mockSelectConversation(id: string): void {
  const history = snapshot()
  const selected = history.chats.find(chat => chat.id === id)
  if (!selected) throw Object.assign(new Error('Conversation not found'), { status: 404 })
  history.activeId = selected.id
  write(history)
  apply(selected)
}
export function mockResetConversations(): ChatMessage[] {
  localStorage.removeItem(storageKey())
  return mockReset()
}
