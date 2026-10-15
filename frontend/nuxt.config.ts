// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  // Prototype: a single-page app, no server-side rendering.
  ssr: false,

  modules: ['@nuxt/ui', '@pinia/nuxt'],

  css: ['~/assets/css/main.css'],

  // Banking register reads calmer in light mode by default; still toggleable.
  colorMode: {
    preference: 'light',
    fallback: 'light',
  },

  runtimeConfig: {
    public: {
      // Where the Spring Boot API lives. In dev the Nitro proxy below forwards
      // "/api" there, so the browser only ever talks to the Nuxt origin.
      apiBase: '/api',
    },
  },

  nitro: {
    // Dev-only: forward /api to the backend so there are no CORS surprises locally.
    devProxy: {
      '/api': {
        target: 'http://localhost:8080/api',
        changeOrigin: true,
      },
    },
    // Built server (e.g. running in docker-compose): same idea, server-to-server, so the
    // browser only ever talks to this app's own origin. Defaults to the "backend" service
    // name on the compose network; override with API_PROXY_TARGET if that's not it.
    routeRules: {
      '/api/**': { proxy: `${process.env.API_PROXY_TARGET ?? 'http://backend:8080'}/api/**` },
    },
  },

  app: {
    head: {
      title: 'Intelligent Mortgage Journey',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'A chat-based mortgage advisor, from "could I get a loan?" to an accepted offer.' },
      ],
    },
  },
})
