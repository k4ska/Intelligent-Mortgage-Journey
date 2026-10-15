import { defineStore } from 'pinia'
import type { BankOption, ChatMessage, Profile, ProfileField, StreamEvent } from '~/types/api'
import { consumeStream } from '~/utils/stream'
import { copy } from '~/utils/copy'

export const useAdvisorStore = defineStore('advisor', {
  state: () => ({
    messages: [] as ChatMessage[],
    profile: { fields: [], property: null } as Profile,
    events: [] as StreamEvent[],
    bankOptions: [] as BankOption[], // #53 will render these as an interactive picker.
    loaded: false,
    loading: false,
    sending: false,
    error: '',
  }),
  actions: {
    async load() {
      if (this.loading || this.loaded) return
      this.loading = true
      this.error = ''
      try {
        const api = useApi()
        const [messages, profile] = await Promise.all([api.getChat(), api.getProfile()])
        this.messages = messages
        this.profile = profile
        this.loaded = true
      } catch {
        this.error = copy.loadError
      } finally { this.loading = false }
    },
    mergeProfile(fields: ProfileField[]) {
      for (const field of fields) {
        const index = this.profile.fields.findIndex(existing => existing.key === field.key)
        if (index >= 0) this.profile.fields[index] = field
        else this.profile.fields.push(field)
      }
    },
    async send(text: string) {
      const trimmed = text.trim()
      if (!trimmed || this.sending || !this.loaded) return
      this.sending = true
      this.error = ''
      this.events = []
      const api = useApi()
      const createdAt = new Date().toISOString()
      const localId = -Date.now()
      const replyId = localId - 1
      this.messages.push({ id: localId, role: 'user', text: trimmed, createdAt })
      this.messages.push({ id: replyId, role: 'advisor', text: '', createdAt })
      let completed = false
      let streamedError = false
      try {
        const response = await api.sendMessage(trimmed)
        await consumeStream(response, (event) => {
          this.events.push(event)
          switch (event.type) {
            case 'text': {
              // Do not trim delta! A whitespace-only delta is a valid stream chunk.
              const reply = this.messages.find(message => message.id === replyId)
              if (reply) reply.text += event.data.delta
              break
            }
            case 'profile_updated':
              this.mergeProfile(event.data.fields)
              break
            case 'bank_picker':
              this.bankOptions = event.data.banks
              break
            case 'done': {
              const reply = this.messages.find(message => message.id === replyId)
              if (reply) reply.id = event.data.messageId
              completed = true
              break
            }
            case 'error':
              streamedError = true
              this.error = event.data.message
              break
          }
        })
        if (!completed && !streamedError) throw new Error('Stream ended before done')
        // Server-provided IDs are authoritative, including the optimistic user message.
        if (completed) this.messages = await api.getChat()
      } catch (error) {
        if (!this.error) this.error = error instanceof Error && error.message === 'Stream ended before done'
          ? copy.streamIncomplete : copy.sendError
      } finally {
        this.messages = this.messages.filter(message => message.id !== replyId || !!message.text)
        this.sending = false
      }
    },
  },
})