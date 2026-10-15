import { defineStore } from 'pinia'
import type { BankOption, ChatMessage, Profile, ProfileField } from '~/types/api'
import { useAuthStore } from '~/stores/auth'
import { consumeStream } from '~/utils/stream'
import { copy } from '~/utils/copy'
import { errorStatus } from '~/utils/errors'
import { mergeProfileFields } from '~/utils/profile'

// Not reactive state: lets logout abort a send that is still streaming or refreshing.
// Aborting a send that already finished is harmless, so it is never cleared.
let activeSend: AbortController | null = null

export const useAdvisorStore = defineStore('advisor', {
  state: () => ({
    messages: [] as ChatMessage[],
    profile: { fields: [], property: null } as Profile,
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
      } catch (error) {
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        this.error = copy.loadError
      } finally { this.loading = false }
    },
    mergeProfile(fields: ProfileField[]) {
      mergeProfileFields(this.profile.fields, fields)
    },
    /** Abort an in-flight send so it cannot write into a reset store. */
    cancel() {
      activeSend?.abort()
    },
    async send(text: string) {
      const trimmed = text.trim()
      if (!trimmed || this.sending || !this.loaded) return
      const controller = new AbortController()
      activeSend = controller
      const { signal } = controller
      this.sending = true
      this.error = ''
      const api = useApi()
      const createdAt = new Date().toISOString()
      const localId = -Date.now()
      const replyId = localId - 1
      this.messages.push({ id: localId, role: 'user', text: trimmed, createdAt })
      this.messages.push({ id: replyId, role: 'advisor', text: '', createdAt })
      let completed = false
      let streamedError = false
      try {
        const response = await api.sendMessage(trimmed, signal)
        await consumeStream(response, (event) => {
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
        }, signal)
        if (!completed && !streamedError) throw new Error('Stream ended before done')
      } catch (error) {
        if (signal.aborted) return
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        if (!this.error) this.error = error instanceof Error && error.message === 'Stream ended before done'
          ? copy.streamIncomplete : copy.sendError
      } finally {
        if (!signal.aborted) {
          this.messages = this.messages.filter(message => message.id !== replyId || !!message.text)
          this.sending = false
        }
      }

      // The reply was delivered; a failed refresh only means the optimistic IDs stay local.
      if (!completed || signal.aborted) return
      try {
        // Server-provided IDs are authoritative, including the optimistic user message.
        const messages = await api.getChat()
        if (!signal.aborted) this.messages = messages
      } catch (error) {
        if (errorStatus(error) === 401 && !signal.aborted) useAuthStore().expire()
      }
    },
  },
})
