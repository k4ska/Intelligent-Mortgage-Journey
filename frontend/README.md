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
├── assets/css/main.css       Tailwind + Nuxt UI entrypoint
├── types/api.ts             TypeScript mirror of the backend's 3 contracts
├── composables/useApi.ts    the only place that knows API URLs
├── stores/application.ts    Pinia store: flow definition + current application + draft answers
├── layouts/default.vue      header / shell
├── pages/
│   ├── index.vue            landing → creates an application, routes to the journey
│   └── journey/[id].vue     the journey: progress bar, step, assist panel, final summary
└── components/
    ├── FlowStep.vue         renders one step's fields + validation + nav
    ├── FieldInput.vue       renders a single field by its type
    ├── AssistPanel.vue      explanations + streaming chat
    └── ProfileSummary.vue   the review screen: read-back, profile, offers, documents
```

## How the journey works

1. `index.vue` calls `POST /api/applications` and routes to `/journey/{id}`.
2. The store loads `GET /api/flow` once and `GET /api/applications/{id}`.
3. `FlowStep` renders `store.currentStep` from the flow definition — no step is hard-coded.
4. "Continue" posts the draft answers to `POST /api/applications/{id}/steps`; the backend
   decides the next step (adaptive flow) and returns the updated application.
5. "Check my answers" calls `POST /api/agent/validate` and shows the issues inline.
6. Field "What's this?" buttons call `POST /api/agent/explain` into the assist panel.
7. When the application is `complete`, `ProfileSummary` shows the read-back
   (`/agent/review`), the standardized profile (`/profile`) and mock offers (`/offers`).
