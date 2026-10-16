import { defineStore } from 'pinia'
import type { InboxThread, InboxThreadDetail } from '~/types/api'
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
    error: '',
    threadError: '',
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
  },
})