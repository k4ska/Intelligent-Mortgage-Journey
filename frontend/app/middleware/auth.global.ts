import { useAuthStore } from '~/stores/auth'

export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()
  await auth.restore()
  const guestPage = to.path === '/login' || to.path === '/register'
  if (!auth.isAuthenticated && !guestPage) return navigateTo('/login')
  if (auth.isAuthenticated && guestPage) return navigateTo('/')
})