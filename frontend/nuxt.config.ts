// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-07-15',
  devtools: { enabled: true },

  // Prototype: a single-page app, no server-side rendering.
  ssr: false,

  modules: ['@nuxt/ui', '@pinia/nuxt'],

  css: ['~/assets/css/main.css'],

  colorMode: {
    preference: 'light',
    fallback: 'light',
  },

  runtimeConfig: {
    public: {
      // Same-origin API requests are forwarded to the backend by Nitro.
      apiBase: '/api',

      // Disabled by default. Docker Compose passes NUXT_PUBLIC_MOCK_API
      // from the root .env to override this at runtime.
      mockApi: false,
    },
  },

  nitro: {
    // Local development outside Docker.
    devProxy: {
      '/api': {
        target: 'http://localhost:8080/api',
        changeOrigin: true,
      },
    },

    // Built Docker server: forward requests over the Compose network.
    routeRules: {
      '/api/**': {
        proxy: `${process.env.API_PROXY_TARGET ?? 'http://backend:8080'}/api/**`,
      },
    },
  },

  app: {
    head: {
      title: 'Intelligent Mortgage Journey',
      meta: [
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        {
          name: 'description',
          content: 'A chat-based mortgage advisor, from "could I get a loan?" to an accepted offer.',
        },
      ],
    },
  },
})
