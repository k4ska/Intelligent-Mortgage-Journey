# Frontend — Intelligent Mortgage Journey

Nuxt 4 (Vue 3), SPA mode, Nuxt UI and Pinia. Issue #52 implements the first frontend walking skeleton: login/register/logout, the three-panel app shell, streaming chat and a profile that updates from stream events.

## Run with Docker

From the repository root:

```bash
docker compose up --build
```

Open http://localhost:3000.

Environment variables are configured in the root `.env` file (see root `.env.example`). No separate frontend `.env` is needed.

## Frontend mock mode

Until backend authentication (#49) is merged, enable the mock API in the root `.env`:

```env
NUXT_PUBLIC_MOCK_API=true
```

Docker Compose passes this variable to the frontend container. Recreate the containers after changing it.

In mock mode, register with any email/password. Demo accounts, messages and profile facts are stored locally in the browser. Passwords are not validated or stored.

For a live example, send `I earn 2350 euros a month`. The mock emits `profile_updated` before streaming the reply and keeps the results after reload.

Set `NUXT_PUBLIC_MOCK_API=false` to use the real backend endpoints. Do not use mock mode in production.

## Structure

- `layouts/default.vue` — Top bar, navigation and prototype notice
- `pages/` — Chat, login, registration and inbox placeholder
- `components/` — Documents, chat and profile panels
- `stores/auth.ts` — Authentication state
- `stores/advisor.ts` — Chat history, profile and stream events
- `composables/useApi.ts` — Backend endpoints and mock switch
- `middleware/auth.global.ts` — Authentication guard
- `mocks/advisor.ts` — Fake auth, chat and profile
- `utils/copy.ts` — Centralized UI text
- `utils/stream.ts` — SSE stream parser
- `types/api.ts` — Shared backend DTOs

## Streaming chat

Chat uses `fetch` with a stream reader for `POST /api/chat/messages`. Supported events are `text`, `profile_updated`, `bank_picker`, `done` and `error`.

Events are dispatched to the Pinia advisor store. Profile updates are applied immediately, even before the first text token.

The shell uses three independently scrollable panels. Documents, bank selection, functional inbox and mobile layout are handled in later issues.
