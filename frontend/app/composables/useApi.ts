
import type {
  AgentStatus,
  ApplicationView,
  BankOffer,
  CalculatorEstimateRequest,
  CalculatorEstimateResponse,
  Definition,
  ExplainResponse,
  FlowDefinition,
  LoanProfile,
  ReviewResponse,
  ValidateResponse,
} from '~/types/api'

/**
 * Thin typed wrapper over the Spring Boot API. One place that knows the URLs.
 */
export function useApi() {
  const base = useRuntimeConfig().public.apiBase

  const req = <T>(path: string, opts: Parameters<typeof $fetch>[1] = {}) =>
    $fetch<T>(`${base}${path}`, opts)

  return {
    getFlow: () => req<FlowDefinition>('/flow'),

    getAgentStatus: () => req<AgentStatus>('/agent/status'),

    createApplication: () =>
      req<ApplicationView>('/applications', { method: 'POST' }),

    getApplication: (id: string) =>
      req<ApplicationView>(`/applications/${id}`),

    /** advance=false saves the answers but stays on the step. */
    submitStep: (id: string, stepId: string, answers: Record<string, unknown>, advance = true) =>
      req<ApplicationView>(`/applications/${id}/steps`, {
        method: 'POST',
        body: { stepId, answers, advance },
      }),

    goToStep: (id: string, stepId: string) =>
      req<ApplicationView>(`/applications/${id}/current-step`, {
        method: 'PUT',
        body: { stepId },
      }),

    getProfile: (id: string) =>
      req<LoanProfile>(`/applications/${id}/profile`),

    getOffers: (id: string) =>
      req<BankOffer[]>(`/applications/${id}/offers`),

    estimate: (request: CalculatorEstimateRequest) =>
      req<CalculatorEstimateResponse>('/calculator/estimate', {
        method: 'POST',
        body: request,
      }),

    /** Built-in definitions by help key. No LLM involved. */
    getDefinitions: () => req<Record<string, Definition>>('/explanations'),

    explain: (applicationId: string | null, fieldKey: string) =>
      req<ExplainResponse>('/agent/explain', {
        method: 'POST',
        body: { applicationId, fieldKey },
      }),

    validate: (applicationId: string, stepId: string) =>
      req<ValidateResponse>('/agent/validate', {
        method: 'POST',
        body: { applicationId, stepId },
      }),

    review: (applicationId: string) =>
      req<ReviewResponse>('/agent/review', {
        method: 'POST',
        body: { applicationId },
      }),

    /**
     * Streams the chat reply token-by-token from the SSE endpoint.
     * Calls onToken for each chunk; resolves when the stream ends.
     */
    chat: async (
      applicationId: string,
      message: string,
      onToken: (chunk: string) => void,
    ): Promise<void> => {
      const res = await fetch(`${base}/agent/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
        body: JSON.stringify({ applicationId, message }),
      })
      if (!res.body) return
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let dataLines: string[] = []

      const emitEvent = () => {
        if (dataLines.length === 0) return
        onToken(dataLines.join('\n'))
        dataLines = []
      }

      const processLine = (rawLine: string) => {
        const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
        if (line === '') {
          // An empty line ends the current SSE event.
          emitEvent()
        } else if (line.startsWith('data:')) {
          // Spring sends "data:" directly followed by the token. Do not trim:
          // a space immediately after the colon can be the entire token.
          dataLines.push(line.slice(5))
        }
        // Ignore SSE comments and other fields (event, id, retry).
      }

      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() ?? ''
        for (const line of lines) processLine(line)
      }

      buffer += decoder.decode()
      if (buffer) processLine(buffer)
      emitEvent()
    },
  }
}
