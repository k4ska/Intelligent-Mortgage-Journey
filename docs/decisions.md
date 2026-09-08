# Decision log

Short, dated entries. Newest first. One entry per decision that would otherwise get
re-litigated.

## 2026-09-08 — Foundations laid

- **Backend**: Spring Boot 4.1 on Java 25, Gradle. Spring AI 2.0 for the Claude
  integration. In-memory H2 (`create-drop`) for now; `docker-compose.yml` has a Postgres
  service for later.
- **Frontend**: Nuxt 4 in SPA mode (`ssr: false`), Nuxt UI, Pinia. Dev server proxies
  `/api` to the backend.
- **Jackson**: Spring Boot 4 uses Jackson 3 (`tools.jackson.*`) as the autoconfigured
  mapper. Backend code imports from `tools.jackson.databind`, not `com.fasterxml`.
- **npm**: install the frontend with `--legacy-peer-deps` — npm 10.9.x crashes on Nuxt
  UI's optional-peer set otherwise.
- **Agent design**: scripted flow + four LLM assists (explain / validate / review /
  chat), not an autonomous tool-calling loop. A `StubAgentService` gives canned answers
  when no API key is set, so the whole app is buildable and demoable without one; a
  `ClaudeAgentService` takes over automatically when `ANTHROPIC_API_KEY` is present.
- **Model**: `claude-sonnet-5` configured in `application.yml` — cheap enough for a
  student prototype, strong enough for explanations and the read-back. Revisit if the
  read-back quality isn't there.
- **API key**: to be provided by the project mentor. Until then everything runs on the
  stub.

## Open questions

- Which market's mortgage rules to loosely model (affects field labels and the mock
  affordability maths). Currently generic / euro-denominated.
- Whether the "multi-bank" screen stays a single mock comparison or grows a second
  standardized-profile consumer.
