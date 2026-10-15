import { defineStore } from 'pinia'
import type { AuthUser } from '~/composables/useApi'
import { useAdvisorStore } from '~/stores/advisor'
import { copy } from '~/utils/copy'

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
      useAdvisorStore().$reset()
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
      try {
        // Register creates the user; login establishes the session cookie.
        const api = useApi()
        await api.register(email, password)
        this.user = await api.login(email, password)
        this.initialized = true
        await navigateTo('/')
      } catch (error: unknown) {
        const status = (error as { status?: number; statusCode?: number }).status
          ?? (error as { statusCode?: number }).statusCode
        this.error = status === 409 ? copy.emailExists : copy.registerError
      } finally { this.busy = false }
    },
    async logout() {
      this.busy = true
      try { await useApi().logout() }
      finally {
        this.clear()
        this.busy = false
        await navigateTo('/login')
      }
    },
  },
})