// TypeScript mirror of the backend API. The full set of contracts for the chat-based
// advisor (auth, chat, stream events, profile, documents, banks, inbox) is in
// docs/architecture.md §5; add types here as each endpoint is built, and keep them in
// sync with the backend DTOs.

export type AgentMode = 'stub' | 'openai'

export interface AgentStatus {
  mode: AgentMode
}
