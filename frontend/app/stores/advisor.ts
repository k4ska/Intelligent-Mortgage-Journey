import { defineStore } from 'pinia'
import type { ChatInput } from '~/composables/useApi'
import type { BankOption, ChatMessage, DocumentType, DocumentView, Profile, ProfileField, StreamEvent } from '~/types/api'
import { useAuthStore } from '~/stores/auth'
import { useInboxStore } from '~/stores/inbox'
import { consumeStream } from '~/utils/stream'
import { copy } from '~/utils/copy'
import { errorStatus } from '~/utils/errors'
import { mergeProfileFields } from '~/utils/profile'

// Not reactive state: lets logout abort a send that is still streaming or refreshing.
// Aborting a send that already finished is harmless, so it is never cleared.
let activeSend: AbortController | null = null

type DocumentStatusEvent = Extract<StreamEvent, { type: 'document_status' }>['data']

export const useAdvisorStore = defineStore('advisor', {
  state: () => ({
    messages: [] as ChatMessage[],
    profile: { fields: [], property: null } as Profile,
    documents: [] as DocumentView[],
    bankOptions: [] as BankOption[], // #53 will render these as an interactive picker.
    loaded: false,
    loading: false,
    sending: false,
    uploading: null as DocumentType | null,
    documentError: '',
    error: '',
  }),
  actions: {
    async load() {
      if (this.loading || this.loaded) return
      this.loading = true
      this.error = ''
      try {
        const api = useApi()
        const [messages, profile, documents] = await Promise.all([api.getChat(), api.getProfile(), api.getDocuments()])
        this.messages = messages
        this.profile = profile
        this.documents = documents
        this.loaded = true
      } catch (error) {
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        this.error = copy.loadError
      } finally { this.loading = false }
    },
    mergeProfile(fields: ProfileField[]) {
      mergeProfileFields(this.profile.fields, fields)
    },
    /** Apply a complete upload response without waiting for the follow-up chat stream. */
    updateDocument(document: DocumentView) {
      const index = this.documents.findIndex(item => item.type === document.type)
      if (index >= 0) this.documents[index] = document
      else this.documents.push(document)
    },
    /** A stream event is a patch, without the document ID or read values. */
    updateDocumentStatus(status: DocumentStatusEvent) {
      const item = this.documents.find(document => document.type === status.type)
      if (item) Object.assign(item, status)
    },
    async refreshDocuments() {
      try {
        this.documents = await useApi().getDocuments()
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        // Keep live status events visible if a refresh fails.
      }
    },
    /** Upload first, then run the advisor trigger. No user chat bubble is created. */
    async uploadDocument(type: DocumentType, file: File): Promise<void> {
      if (!this.loaded || this.uploading || this.sending) return
      this.uploading = type
      this.documentError = ''
      try {
        const uploaded = await useApi().uploadDocument(type, file)
        this.updateDocument(uploaded)
        if (!uploaded.id) throw new Error('Upload response is missing the document ID')
        if (!await this.send({ trigger: 'DOCUMENT_UPLOADED', documentId: uploaded.id })) {
          this.documentError = copy.documentTriggerError
        }
      } catch (error) {
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        this.documentError = errorStatus(error) === 409 ? copy.documentNotRequested : copy.documentUploadError
        await this.refreshDocuments()
      } finally {
        this.uploading = null
      }
    },
    /** Start over (#70): the server wipes everything; the chat is back to the greeting. */
    async startOver(): Promise<boolean> {
      // A reply still streaming must not land in the new conversation.
      this.cancel()
      try {
        const api = useApi()
        const messages = await api.resetChat()
        const documents = await api.getDocuments()
        this.$reset()
        this.messages = messages
        this.documents = documents
        this.loaded = true
      } catch (error) {
        if (errorStatus(error) === 401) {
          void useAuthStore().expire()
          return false
        }
        // Unknown how far it got, so show whatever the server has now.
        this.$reset()
        void this.load()
        return false
      }
      const inbox = useInboxStore()
      inbox.$reset()
      void inbox.load(true)
      return true
    },
    /** Abort an in-flight send so it cannot write into a reset store. */
    cancel() {
      activeSend?.abort()
    },
    async send(input: ChatInput): Promise<boolean> {
      const text = typeof input === 'string' ? input.trim() : null
      if ((text !== null && !text) || this.sending || !this.loaded || (text !== null && this.uploading)) return false
      const controller = new AbortController()
      activeSend = controller
      const { signal } = controller
      this.sending = true
      this.error = ''
      const api = useApi()
      const createdAt = new Date().toISOString()
      const localId = -Date.now()
      const replyId = localId - 1
      if (text !== null) this.messages.push({ id: localId, role: 'user', text, createdAt, corrected: false })
      this.messages.push({ id: replyId, role: 'advisor', text: '', createdAt, corrected: false })
      let completed = false
      let streamedError = false
      try {
        const response = await api.sendMessage(text ?? input, signal)
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
            case 'property_updated':
              // Each event contains the complete active property, not a patch.
              this.profile.property = event.data.property
              break
            case 'document_status':
              this.updateDocumentStatus(event.data)
              break
            case 'bank_picker':
              this.bankOptions = event.data.banks
              break
            case 'corrected': {
              // Prefer the saved ID; before 'done' the current reply still has a local ID.
              const reply = this.messages.find(message => message.id === event.data.messageId && message.role === 'advisor')
                ?? this.messages.find(message => message.id === replyId && message.role === 'advisor')
              if (reply) {
                reply.text = event.data.text
                reply.corrected = true
              }
              break
            }
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
        if (signal.aborted) return false
        if (errorStatus(error) === 401) {
          void useAuthStore().expire()
          return false
        }
        if (!this.error) this.error = error instanceof Error && error.message === 'Stream ended before done'
          ? copy.streamIncomplete : copy.sendError
      } finally {
        if (!signal.aborted) {
          this.messages = this.messages.filter(message => message.id !== replyId || !!message.text)
          this.sending = false
        }
      }

      if (!completed || signal.aborted) return false
      // The server is authoritative for optimistic IDs, system notes, checklist IDs and co-applicant entries.
      try {
        const messages = await api.getChat()
        if (!signal.aborted) this.messages = messages
      } catch (error) {
        if (errorStatus(error) === 401 && !signal.aborted) void useAuthStore().expire()
      }
      if (!signal.aborted) await this.refreshDocuments()
      return true
    },
  },
})