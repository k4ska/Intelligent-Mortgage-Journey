import { defineStore } from 'pinia'
import type { ChatInput } from '~/composables/useApi'
import type { BankOption, ChartType, ChatMessage, ConversationSummary, DocumentType, DocumentView, Profile, ProfileField, StreamEvent } from '~/types/api'
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
    conversations: [] as ConversationSummary[],
    activeConversationId: null as string | null,
    switchingConversation: false,
    profile: { fields: [], property: null } as Profile,
    documents: [] as DocumentView[],
    bankOptions: [] as BankOption[], // #53 will render these as an interactive picker.
    /** Charts the buttons under the chat can show now (#160). */
    chartTypes: [] as ChartType[],
    showingChart: null as ChartType | null,
    loaded: false,
    loading: false,
    sending: false,
    applying: false,
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
        // Initialize the active chat first. Concurrent requests on a new account
        // could otherwise both try to create the initial greeting/conversation.
        const conversations = await api.getConversations()
        const [messages, profile, documents] = await Promise.all([api.getChat(), api.getProfile(), api.getDocuments()])
        this.conversations = conversations
        this.activeConversationId = conversations.find(chat => chat.active)?.id ?? null
        this.messages = messages
        this.profile = profile
        this.documents = documents
        this.loaded = true
        void this.refreshChartTypes()
      } catch (error) {
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        this.error = copy.loadError
      } finally { this.loading = false }
    },
    async refreshConversations() {
      try {
        const conversations = await useApi().getConversations()
        this.conversations = conversations
        this.activeConversationId = conversations.find(chat => chat.active)?.id ?? null
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
      }
    },
    /** Changing the selected chat is blocked while any advisor/inbox operation is active. */
    async changeConversation(change: () => Promise<unknown>): Promise<boolean> {
      const inbox = useInboxStore()
      if (!this.loaded || this.loading || this.switchingConversation || this.sending || this.applying || this.uploading ||
          this.showingChart || inbox.assistingThreadId || inbox.replyingThreadId || inbox.accepting) return false
      this.switchingConversation = true
      try {
        await change()
        this.$reset()
        this.switchingConversation = true
        await this.load()
        if (!this.loaded) return false
        inbox.$reset()
        void inbox.load(true)
        return true
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        else {
          this.$reset()
          void this.load()
          this.error = copy.conversationSwitchError
        }
        return false
      } finally {
        this.switchingConversation = false
      }
    },
    async newConversation(): Promise<boolean> {
      return this.changeConversation(() => useApi().newConversation())
    },
    async selectConversation(id: string): Promise<boolean> {
      if (id === this.activeConversationId) return true
      return this.changeConversation(() => useApi().selectConversation(id))
    },
    async refreshChartTypes() {
      try {
        this.chartTypes = await useApi().getChartTypes()
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        // No buttons is fine; the user can still ask the advisor for a chart.
      }
    },
    /** A chart button (#160): instant and computed in code, no advisor turn. */
    async showChart(chartType: ChartType): Promise<boolean> {
      if (!this.loaded || this.sending || this.showingChart || this.switchingConversation || useInboxStore().assistingThreadId) return false
      this.showingChart = chartType
      this.error = ''
      try {
        this.messages.push(await useApi().showChart(chartType))
        void this.refreshConversations()
        return true
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        else {
          this.error = copy.chartError
          void this.refreshChartTypes()
        }
        return false
      } finally {
        this.showingChart = null
      }
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
        this.documentError = copy.documentUploadError
        await this.refreshDocuments()
      } finally {
        this.uploading = null
      }
    },
    /** Start over (#70): the server wipes everything; the chat is back to the greeting. */
    async startOver(): Promise<boolean> {
      // A reply still streaming must not land in the new conversation.
      this.cancel()
      useInboxStore().cancelAssist()
      try {
        const api = useApi()
        const messages = await api.resetChat()
        const [documents, conversations] = await Promise.all([api.getDocuments(), api.getConversations()])
        this.$reset()
        this.messages = messages
        this.documents = documents
        this.conversations = conversations
        this.activeConversationId = conversations.find(chat => chat.active)?.id ?? null
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
      if ((text !== null && !text) || this.switchingConversation || this.sending || useInboxStore().assistingThreadId || !this.loaded || (text !== null && this.uploading)) return false
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
            case 'text_restart': {
              const reply = this.messages.find(message => message.id === replyId)
              if (reply) reply.text = ''
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
            case 'chart': {
              const reply = this.messages.find(message => message.id === replyId)
              if (reply) reply.chart = event.data
              break
            }
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
      if (!signal.aborted) await Promise.all([this.refreshDocuments(), this.refreshChartTypes(), this.refreshConversations()])
      return true
    },
  },
})