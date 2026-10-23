import type { AgentStatus, ApplicationResult, ChatMessage, InboxThread, InboxThreadDetail, Profile } from '~/types/api'
import { mockAssist, mockAcceptOffer, mockChat, mockGetInbox, mockGetThread, mockLogin, mockLogout, mockMe, mockProfile, mockRegister, mockReplyToThread, mockReset, mockSend, mockSubmitApplication } from '~/mocks/advisor'

export interface AuthUser { id: string; email: string }
export type ChatInput = string
  | { trigger: 'OFFER_ACCEPTED'; threadId: string }
  | { trigger: 'APPLICATION_SENT'; applicationId: string }

/** All backend URLs and the optional explicit mock switch live in one place. */
export function useApi() {
  const { public: config } = useRuntimeConfig()
  const base = config.apiBase
  const mock = String(config.mockApi) === 'true'

  const request = <T>(path: string, options: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(`${base}${path}`, { credentials: 'same-origin', ...options })
  const later = <T>(fn: () => T): Promise<T> => Promise.resolve().then(fn)

  /** Shared POST-SSE transport for both the main chat and inbox advisor. */
  const postStream = async (path: string, body: unknown, signal?: AbortSignal): Promise<Response> => {
    const response = await fetch(`${base}${path}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      signal,
    })
    if (!response.ok) throw Object.assign(new Error(`HTTP ${response.status}`), { status: response.status })
    if (!response.body) throw new Error('Missing SSE response body')
    return response
  }

  return {
    isMock: mock,
    getMe: (): Promise<AuthUser> => mock ? later(mockMe) : request<AuthUser>('/auth/me'),
    register: (email: string, password: string): Promise<AuthUser> => mock
      ? later(() => mockRegister(email))
      : request<AuthUser>('/auth/register', { method: 'POST', body: { email, password } }),
    login: (email: string, password: string): Promise<AuthUser> => mock
      ? later(() => mockLogin(email))
      : request<AuthUser>('/auth/login', { method: 'POST', body: { email, password } }),
    logout: (): Promise<void> => mock ? later(mockLogout) : request<void>('/auth/logout', { method: 'POST' }),
    getChat: (): Promise<ChatMessage[]> => mock ? later(mockChat) : request<ChatMessage[]>('/chat'),
    resetChat: (): Promise<ChatMessage[]> => mock
      ? later(mockReset) : request<ChatMessage[]>('/chat/reset', { method: 'POST' }),
    getProfile: (): Promise<Profile> => mock ? later(mockProfile) : request<Profile>('/profile'),
    getAgentStatus: () => request<AgentStatus>('/agent/status'),
    sendApplication: (bankIds: string[]): Promise<ApplicationResult> => mock
      ? later(() => mockSubmitApplication(bankIds))
      : request<ApplicationResult>('/applications', { method: 'POST', body: { bankIds } }),
    getInbox: (): Promise<InboxThread[]> => mock ? later(mockGetInbox) : request<InboxThread[]>('/inbox'),
    getInboxThread: (threadId: string): Promise<InboxThreadDetail> => mock
      ? later(() => mockGetThread(threadId))
      : request<InboxThreadDetail>(`/inbox/${encodeURIComponent(threadId)}`),
    replyToThread: (threadId: string, body: string): Promise<unknown> => mock
      ? mockReplyToThread(threadId, body)
      : request<unknown>(`/inbox/${encodeURIComponent(threadId)}/reply`, { method: 'POST', body: { body } }),
    acceptOffer: (threadId: string, emailId: string | number): Promise<unknown> => mock
      ? later(() => mockAcceptOffer(threadId, emailId))
      : request<unknown>(`/inbox/${encodeURIComponent(threadId)}/accept`, { method: 'POST', body: { emailId } }),
    sendMessage: (input: ChatInput, signal?: AbortSignal): Promise<Response> => mock
      ? later(() => mockSend(input))
      : postStream('/chat/messages', typeof input === 'string' ? { text: input } : input, signal),
    assistThread: (threadId: string, text: string, signal?: AbortSignal): Promise<Response> => mock
      ? later(() => mockAssist(threadId, text))
      : postStream(`/inbox/${encodeURIComponent(threadId)}/assist`, { text }, signal),
  }
}