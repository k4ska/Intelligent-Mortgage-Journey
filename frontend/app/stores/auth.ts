import { defineStore } from 'pinia'
import type { AuthUser } from '~/composables/useApi'
import { useAdvisorStore } from '~/stores/advisor'
import { copy } from '~/utils/copy'
import { errorStatus } from '~/utils/errors'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as AuthUser | null, initialized: false, busy: false, error: '' }),
  getters: { isAuthenticated: state => !!state.user },
  actions: {
    async restore() {
      if (this.initialized) return
      try { this.user = await useApi().getMe() }
      catch { this.user = null }
      finally { this.initialized = true }
    },
    clear() {
      this.user = null
      this.initialized = true
      const advisor = useAdvisorStore()
      advisor.cancel()
      advisor.$reset()
    },
    /** The server session is gone (any API call returned 401): drop local state and re-login. */
    async expire() {
      if (!this.user) return
      this.clear()
      await navigateTo({ path: '/login', query: { expired: '1' } })
    },
    async login(email: string, password: string) {
      this.busy = true
      this.error = ''
      try {
        this.user = await useApi().login(email, password)
        this.initialized = true
        await navigateTo('/')
      } catch {
        this.error = copy.authError
      } finally { this.busy = false }
    },
    async register(email: string, password: string) {
      this.busy = true
      this.error = ''
      const api = useApi()
      try {
        await api.register(email, password)
      } catch (error) {
        this.error = errorStatus(error) === 409 ? copy.emailExists : copy.registerError
        this.busy = false
        return
      }
      // Register creates the user; login establishes the session cookie. If only the
      // login fails, the account exists, so send the user to log in instead of retrying.
      try {
        this.user = await api.login(email, password)
        this.initialized = true
        await navigateTo('/')
      } catch {
        await navigateTo({ path: '/login', query: { registered: '1' } })
      } finally { this.busy = false }
    },
    async logout() {
      this.busy = true
      try {
        await useApi().logout()
      } catch (error) {
        // A 401 means the session is already gone; anything else may leave it valid.
        if (errorStatus(error) !== 401) {
          useToast().add({ title: copy.logoutError, color: 'error', icon: 'i-lucide-circle-alert' })
          return
        }
      } finally { this.busy = false }
      this.clear()
      await navigateTo('/login')
    },
  },
})
