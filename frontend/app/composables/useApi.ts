
import type { AgentStatus, ChatMessage, Profile } from '~/types/api'
import { mockChat, mockLogin, mockLogout, mockMe, mockProfile, mockRegister, mockSend } from '~/mocks/advisor'

export interface AuthUser { id: string; email: string }

/** All backend URLs and the optional explicit mock switch live in one place. */
export function useApi() {
  const { public: config } = useRuntimeConfig()
  const base = config.apiBase

  // Runtime environment variables can arrive as strings from Docker.
  const mock = String(config.mockApi) === 'true'

  const request = <T>(path: string, options: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(`${base}${path}`, { credentials: 'same-origin', ...options })

  return {
    isMock: mock,
    getMe: (): Promise<AuthUser> => mock
      ? Promise.resolve(mockMe())
      : request<AuthUser>('/auth/me'),
    register: (email: string, password: string): Promise<AuthUser> => mock
      ? Promise.resolve(mockRegister(email))
      : request<AuthUser>('/auth/register', { method: 'POST', body: { email, password } }),
    login: (email: string, password: string): Promise<AuthUser> => mock
      ? Promise.resolve(mockLogin(email))
      : request<AuthUser>('/auth/login', { method: 'POST', body: { email, password } }),
    logout: (): Promise<void> => mock
      ? Promise.resolve(mockLogout())
      : request<void>('/auth/logout', { method: 'POST' }),
    getChat: (): Promise<ChatMessage[]> => mock
      ? Promise.resolve(mockChat())
      : request<ChatMessage[]>('/chat'),
    getProfile: (): Promise<Profile> => mock
      ? Promise.resolve(mockProfile())
      : request<Profile>('/profile'),
    getAgentStatus: () => request<AgentStatus>('/agent/status'),

    // EventSource cannot POST JSON; fetch provides the ReadableStream we need.
    sendMessage: async (text: string): Promise<Response> => {
      if (mock) return mockSend(text)

      const response = await fetch(`${base}/chat/messages`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ text }),
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
