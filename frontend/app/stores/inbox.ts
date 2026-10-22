import { defineStore } from 'pinia'
import type { InboxEmail, InboxThread, InboxThreadDetail } from '~/types/api'
import { useAdvisorStore } from '~/stores/advisor'
import { useAuthStore } from '~/stores/auth'
import { errorStatus } from '~/utils/errors'
import { copy } from '~/utils/copy'

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
    error: '',
    threadError: '',
    replyError: '',
    acceptError: '',
    advisorError: '',
  }),
  getters: {
    unreadCount: state => state.threads.filter(thread => thread.unread).length,
  },
  actions: {
    async load(force = false) {
      // A forced refresh during a load (e.g. right after sending an application) runs
      // once that load finishes, so new threads are never missed.
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
      // The latest click wins: a slower earlier request is ignored when it returns.
      this.selectedThreadId = threadId
      this.activeThread = null
      this.opening = true
      this.threadError = ''
      this.replyError = ''
      this.acceptError = ''
      this.advisorError = ''
      const userId = useAuthStore().user?.id
      try {
        // GET marks the thread as read on the backend. Only clear the dot on success.
        const detail = await useApi().getInboxThread(threadId)
        if (useAuthStore().user?.id !== userId || this.selectedThreadId !== threadId) return
        this.activeThread = detail
        const thread = this.threads.find(item => item.threadId === threadId)
        if (thread) thread.unread = false
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
        // The server waits for the bank agent's reply; the typing indicator remains
        // visible throughout that request while our optimistic email is shown.
        await useApi().replyToThread(threadId, message)
        // POST succeeded: never encourage a second reply just because refreshing
        // the messages fails (the bank might already have processed this one).
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
        // A failed response may have saved the email on the server. Prefer a fresh
        // history over leaving an unconfirmed optimistic message behind.
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

      // Acceptance succeeded. From here, refresh/trigger errors must not imply that
      // the user can accept again (the backend rejects a second acceptance).
      try {
        const api = useApi()
        const [threads, detail] = await Promise.all([api.getInbox(), api.getInboxThread(threadId)])
        if (useAuthStore().user?.id !== userId) return true
        this.threads = threads
        this.loaded = true
        if (this.selectedThreadId === threadId) this.activeThread = detail
      } catch (error) {
        if (errorStatus(error) === 401) void useAuthStore().expire()
        // Retain the server-confirmed accepted status even if refresh is offline.
        this.threads.forEach(thread => { thread.status = thread.threadId === threadId ? 'ACCEPTED' : 'CLOSED' })
        if (this.activeThread?.threadId === threadId) this.activeThread.status = 'ACCEPTED'
      } finally {
        this.accepting = false
      }

      // Notify the advisor without a user chat bubble. Failure does not roll back
      // acceptance, since the bank's decision was committed first.
      const advisor = useAdvisorStore()
      if (!advisor.loaded) await advisor.load()
      if (!advisor.loaded || !await advisor.send({ trigger: 'OFFER_ACCEPTED', threadId })) {
        this.advisorError = copy.inboxAdvisorError
      }
      return true
    },
  },
})