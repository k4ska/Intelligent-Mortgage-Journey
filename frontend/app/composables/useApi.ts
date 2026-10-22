import type { AgentStatus, ApplicationResult, ChatMessage, InboxThread, InboxThreadDetail, Profile } from '~/types/api'
import { mockChat, mockReset, mockGetInbox, mockGetThread, mockLogin, mockLogout, mockMe, mockProfile, mockRegister, mockSend, mockSubmitApplication, mockReplyToThread, mockAcceptOffer } from '~/mocks/advisor'

export interface AuthUser { id: string; email: string }
export type ChatInput = string | { trigger: 'OFFER_ACCEPTED'; threadId: string }

/** All backend URLs and the optional explicit mock switch live in one place. */
export function useApi() {
  const { public: config } = useRuntimeConfig()
  const base = config.apiBase

  // Runtime environment variables can arrive as strings from Docker.
  const mock = String(config.mockApi) === 'true'

  const request = <T>(path: string, options: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(`${base}${path}`, { credentials: 'same-origin', ...options })

  // Run mocks asynchronously so their errors reject like real requests instead of throwing.
  const later = <T>(fn: () => T): Promise<T> => Promise.resolve().then(fn)

  return {
    isMock: mock,
    getMe: (): Promise<AuthUser> => mock
      ? later(mockMe)
      : request<AuthUser>('/auth/me'),
    register: (email: string, password: string): Promise<AuthUser> => mock
      ? later(() => mockRegister(email))
      : request<AuthUser>('/auth/register', { method: 'POST', body: { email, password } }),
    login: (email: string, password: string): Promise<AuthUser> => mock
      ? later(() => mockLogin(email))
      : request<AuthUser>('/auth/login', { method: 'POST', body: { email, password } }),
    logout: (): Promise<void> => mock
      ? later(mockLogout)
      : request<void>('/auth/logout', { method: 'POST' }),
    getChat: (): Promise<ChatMessage[]> => mock
      ? later(mockChat)
      : request<ChatMessage[]>('/chat'),
    // Wipes the user's conversation, profile, property and bank emails; returns the greeting.
    resetChat: (): Promise<ChatMessage[]> => mock
      ? later(mockReset)
      : request<ChatMessage[]>('/chat/reset', { method: 'POST' }),
    getProfile: (): Promise<Profile> => mock
      ? later(mockProfile)
      : request<Profile>('/profile'),
    getAgentStatus: () => request<AgentStatus>('/agent/status'),
    sendApplication: (bankIds: string[]): Promise<ApplicationResult> => mock
      ? later(() => mockSubmitApplication(bankIds))
      : request<ApplicationResult>('/applications', { method: 'POST', body: { bankIds } }),
    getInbox: (): Promise<InboxThread[]> => mock
      ? later(mockGetInbox)
      : request<InboxThread[]>('/inbox'),
    getInboxThread: (threadId: string): Promise<InboxThreadDetail> => mock
      ? later(() => mockGetThread(threadId))
      : request<InboxThreadDetail>(`/inbox/${encodeURIComponent(threadId)}`),

    replyToThread: (threadId: string, body: string): Promise<unknown> => mock
      ? mockReplyToThread(threadId, body)
      : request<unknown>(`/inbox/${encodeURIComponent(threadId)}/reply`, { method: 'POST', body: { body } }),
    acceptOffer: (threadId: string, emailId: string | number): Promise<unknown> => mock
      ? later(() => mockAcceptOffer(threadId, emailId))
      : request<unknown>(`/inbox/${encodeURIComponent(threadId)}/accept`, { method: 'POST', body: { emailId } }),

    // EventSource cannot POST JSON; fetch provides the ReadableStream we need.
    sendMessage: async (input: ChatInput, signal?: AbortSignal): Promise<Response> => {
      if (mock) return mockSend(input)

      const response = await fetch(`${base}/chat/messages`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(typeof input === 'string' ? { text: input } : input),
        signal,
      })

      if (!response.ok) {
        throw Object.assign(new Error(`HTTP ${response.status}`), {
          status: response.status,
        })
      }

      if (!response.body) throw new Error('Missing SSE response body')

      return response
    },
  }
}