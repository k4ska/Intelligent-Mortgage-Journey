import type { AgentStatus } from '~/types/api'

/**
 * Thin typed wrapper over the Spring Boot API. One place that knows the URLs.
 */
export function useApi() {
  const base = useRuntimeConfig().public.apiBase

  const req = <T>(path: string, opts: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(`${base}${path}`, opts)

  return {
    getAgentStatus: () => req<AgentStatus>('/agent/status'),
  }
}
