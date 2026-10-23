import { defineStore } from 'pinia'
import type { InboxEmail, InboxThread, InboxThreadDetail } from '~/types/api'
import { useAdvisorStore } from '~/stores/advisor'
import { useAuthStore } from '~/stores/auth'
import { consumeStream } from '~/utils/stream'
import { errorStatus } from '~/utils/errors'
import { copy } from '~/utils/copy'

// Abort streaming when leaving a thread, logging out, or starting over.
let activeAssist: AbortController | null = null

export const useInboxStore = defineStore('inbox', {
  state: () => ({
    threads: [] as InboxThread[],
    selectedThreadId: null as string | null,
    activeThread: null as InboxThreadDetail | null,
    loaded: false,
    loading: false,
    refreshQueued: false,
    opening: false,
    replyingThreadId: null as string | null,
    accepting: false,
    assistingThreadId: null as string | null,
    replyDraft: '',
    error: '',
    threadError: '',
    replyError: '',
    acceptError: '',
    advisorError: '',
    assistError: '',
  }),
  getters: {
    unreadCount: state => state.threads.filter(thread => thread.unread).length,
  },
  actions: {
    cancelAssist() {
      activeAssist?.abort()
      activeAssist = null
      this.assistingThreadId = null
    },
    async load(force = false) {
      if (this.loading) {
        if (force) this.refreshQueued = true
        return
      }
      if (this.loaded && !force) return
      this.loading = true
      this.error = ''
      const userId = useAuthStore().user?.id
      try {
        const threads = await useApi().getInbox()
        if (useAuthStore().user?.id !== userId) return
        this.threads = threads
        this.loaded = true
      } catch (error) {
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        this.error = copy.inboxLoadError
      } finally {
        this.loading = false
        if (this.refreshQueued) {
          this.refreshQueued = false
          void this.load(true)
        }
      }
    },
    async open(threadId: string) {
      if (this.selectedThreadId !== threadId) this.cancelAssist()
      this.selectedThreadId = threadId
      this.activeThread = null
      this.opening = true
      this.threadError = ''
      this.replyError = ''
      this.acceptError = ''
      this.advisorError = ''
      this.assistError = ''
      this.replyDraft = ''
      const userId = useAuthStore().user?.id
      try {
        const detail = await useApi().getInboxThread(threadId)
        if (useAuthStore().user?.id !== userId || this.selectedThreadId !== threadId) return
        this.activeThread = detail
        const thread = this.threads.find(item => item.threadId === threadId)
        if (thread) thread.unread = false
        // Thread-tagged messages are held in the same global advisor conversation.
        const advisor = useAdvisorStore()
        if (!advisor.loaded) void advisor.load()
      } catch (error) {
        if (errorStatus(error) === 401) return void useAuthStore().expire()
        if (this.selectedThreadId === threadId) this.threadError = copy.threadLoadError
      } finally {
        if (this.selectedThreadId === threadId) this.opening = false
      }
    },
    async reply(body: string): Promise<boolean> {
      const thread = this.activeThread
      const message = body.trim()
      if (!thread || !message || this.replyingThreadId || this.accepting || !['OPEN', 'OFFER', 'NEGOTIATING'].includes(thread.status)) return false
      const threadId = thread.threadId
      const userId = useAuthStore().user?.id
      const optimisticId = `pending-${Date.now()}`
      const optimistic: InboxEmail = {
        id: optimisticId, from: 'USER', type: 'reply', subject: thread.emails.at(-1)?.subject ?? 'Re: Mortgage application',
        body: message, offer: null, createdAt: new Date().toISOString(),
      }
      thread.emails.push(optimistic)
      this.replyingThreadId = threadId
      this.replyError = ''
      try {
        await useApi().replyToThread(threadId, message)
        try {
          const fresh = await useApi().getInboxThread(threadId)
          if (useAuthStore().user?.id !== userId) return true
          if (this.selectedThreadId === threadId) this.activeThread = fresh
          await this.load(true)
        } catch (error) {
          if (errorStatus(error) === 401) void useAuthStore().expire()
          else if (this.selectedThreadId === threadId) this.replyError = copy.inboxReplyRefreshError
        }
        return true
      } catch (error) {
        if (errorStatus(error) === 401) {
          void useAuthStore().expire()
          return false
        }
        try {
          const fresh = await useApi().getInboxThread(threadId)
          if (this.selectedThreadId === threadId && useAuthStore().user?.id === userId) this.activeThread = fresh
        } catch {
          if (this.activeThread?.threadId === threadId) {
            this.activeThread.emails = this.activeThread.emails.filter(email => email.id !== optimisticId)
          }
        }
        if (this.selectedThreadId === threadId) this.replyError = copy.inboxReplyError
        return false
      } finally {
        this.replyingThreadId = null
      }
    },
    async accept(threadId: string, emailId: string | number): Promise<boolean> {
      if (this.accepting || this.replyingThreadId || this.activeThread?.threadId !== threadId) return false
      const offer = this.activeThread.emails.find(email => String(email.id) === String(emailId))
      if (this.activeThread.status === 'ACCEPTED' || offer?.from !== 'BANK' || offer.type !== 'offer' || !offer.offer) return false
      this.accepting = true
      this.acceptError = ''
      this.advisorError = ''
      const userId = useAuthStore().user?.id
      try {
        await useApi().acceptOffer(threadId, emailId)
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        else this.acceptError = copy.inboxAcceptError
        this.accepting = false
        return false
      }
      try {
        const api = useApi()
        const [threads, detail] = await Promise.all([api.getInbox(), api.getInboxThread(threadId)])
        if (useAuthStore().user?.id !== userId) return true
        this.threads = threads
        this.loaded = true
        if (this.selectedThreadId === threadId) this.activeThread = detail
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        this.threads.forEach(thread => { thread.status = thread.threadId === threadId ? 'ACCEPTED' : 'CLOSED' })
        if (this.activeThread?.threadId === threadId) this.activeThread.status = 'ACCEPTED'
      } finally {
        this.accepting = false
      }
      const advisor = useAdvisorStore()
      if (!advisor.loaded) await advisor.load()
      if (!advisor.loaded || !await advisor.send({ trigger: 'OFFER_ACCEPTED', threadId })) {
        this.advisorError = copy.inboxAdvisorError
      }
      return true
    },
    /** The inbox assistant uses the shared chat history; reply drafts are never sent automatically. */
    async assist(question: string): Promise<boolean> {
      const text = question.trim()
      const threadId = this.activeThread?.threadId
      const advisor = useAdvisorStore()
      if (!threadId || !text || this.assistingThreadId || advisor.sending) return false
      if (!advisor.loaded) await advisor.load()
      if (!advisor.loaded || this.selectedThreadId !== threadId) {
        this.assistError = copy.inboxAssistLoadError
        return false
      }
      const userId = useAuthStore().user?.id
      const controller = new AbortController()
      activeAssist = controller
      this.assistingThreadId = threadId
      this.assistError = ''
      const createdAt = new Date().toISOString()
      const userIdLocal = -Date.now()
      const replyIdLocal = userIdLocal - 1
      advisor.messages.push({ id: userIdLocal, role: 'user', text, createdAt, corrected: false, threadId })
      advisor.messages.push({ id: replyIdLocal, role: 'advisor', text: '', createdAt, corrected: false, threadId })
      let completed = false
      let failed = false
      let savedReplyId: number | null = null
      try {
        const api = useApi()
        const response = await api.assistThread(threadId, text, controller.signal)
        await consumeStream(response, event => {
          if (controller.signal.aborted) return
          const reply = advisor.messages.find(message => message.id === replyIdLocal || (savedReplyId !== null && message.id === savedReplyId && message.threadId === threadId))
          switch (event.type) {
            case 'text':
              if (reply) reply.text += event.data.delta
              break
            case 'draft_reply':
              if (event.data.threadId === this.selectedThreadId && event.data.threadId === threadId) {
                this.replyDraft = event.data.body
              }
              break
            case 'profile_updated':
              advisor.mergeProfile(event.data.fields)
              break
            case 'property_updated':
              advisor.profile.property = event.data.property
              break
            case 'bank_picker':
              advisor.bankOptions = event.data.banks
              break
            case 'corrected':
              if (reply) {
                reply.text = event.data.text
                reply.corrected = true
              }
              break
            case 'done':
              if (reply) reply.id = event.data.messageId
              savedReplyId = event.data.messageId
              completed = true
              break
            case 'error':
              failed = true
              this.assistError = event.data.message
              break
          }
        }, controller.signal)
        if (!completed && !failed) throw new Error('Stream ended before done')
        if (failed) return false
        // Server owns the IDs and saves thread-tagged messages to the main conversation.
        try {
          const history = await api.getChat()
          if (!controller.signal.aborted && useAuthStore().user?.id === userId) advisor.messages = history
        } catch (error) {
          if (errorStatus(error) === 401) void useAuthStore().expire()
          // Keep the streamed messages visible if refreshing history fails.
        }
        return !controller.signal.aborted
      } catch (error) {
        if (controller.signal.aborted) return false
        if (errorStatus(error) === 401) {
          void useAuthStore().expire()
          return false
        }
        this.assistError = copy.inboxAssistError
        return false
      } finally {
        // If an interrupted request may have been saved server-side, it will reappear on reload.
        if (!completed || controller.signal.aborted) {
          advisor.messages = advisor.messages.filter(message => message.id !== replyIdLocal && message.id !== userIdLocal)
        }
        if (activeAssist === controller) {
          activeAssist = null
          this.assistingThreadId = null
        }
      }
    },
  },
})