// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  // Prototype: a single-page app, no server-side rendering.
  ssr: false,

  modules: ['@nuxt/ui', '@pinia/nuxt'],

  css: ['~/assets/css/main.css'],

  runtimeConfig: {
    public: {
      // Where the Spring Boot API lives. In dev the Nitro proxy below forwards
      // "/api" there, so the browser only ever talks to the Nuxt origin.
      apiBase: '/api',
    },
  },

  // Dev-only: forward /api to the backend so there are no CORS surprises locally.
  nitro: {
    devProxy: {
      '/api': {
        target: 'http://localhost:8080/api',
        changeOrigin: true,
      },
    },
  },

  app: {
    head: {
      title: 'Intelligent Mortgage Journey',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'A guided, agent-supported mortgage application journey.' },
      ],
    },
  },
})
