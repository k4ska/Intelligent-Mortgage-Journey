# Frontend — Intelligent Mortgage Journey

Nuxt 4 (Vue 3), SPA mode, Nuxt UI, Pinia.

## Run

```bash
npm install --legacy-peer-deps    # see note below
npm run dev                        # http://localhost:3000
```

The backend must be running on `http://localhost:8080`. The Nuxt dev server proxies
`/api/*` there (configured in `nuxt.config.ts`), so the browser only talks to the Nuxt
origin and there are no CORS issues in development.

> **`--legacy-peer-deps`**: npm 10.9.x crashes on Nuxt UI's large optional-peer set
> (`Cannot read properties of null (reading 'edgesOut')`). `--legacy-peer-deps` avoids
> the buggy resolution path. Alternatively use pnpm.

## Structure

```
app/
├── app.vue                  UApp + layout + page
├── app.config.ts            Nuxt UI theme (primary colour, etc.)
├── assets/css/main.css      Tailwind + Nuxt UI entrypoint
├── types/api.ts             TypeScript mirror of the backend API
├── composables/useApi.ts    the only place that knows API URLs
├── utils/numbers.ts         number and euro formatting
├── layouts/default.vue      header / shell, with the prototype note
└── pages/
    └── index.vue            placeholder: backend status, until the skeleton lands
```

The form-based journey (flow renderer, assist panel, landing hub, calculators) was removed
in #48. The chat-based app (login, the three-panel layout with documents, chat and
profile, and the inbox) is built in the walking skeleton issues #52 and #53, against the
contracts in `../docs/architecture.md` §5.

The layout shell fits the viewport without page-level scrolling; keep it that way, and let
only specific inner panels (like the chat log) scroll.
