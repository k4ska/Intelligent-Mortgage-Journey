# Decision log

Short, dated entries. Newest first. One entry per decision that would otherwise get
re-litigated.

## 2026-09-17 — Multi-agent backend with an independent verifier

- **Agent backend**: replaced the single `ClaudeAgentService` (one `ChatClient`, four
  methods) with a multi-agent design. `AgentOrchestrator` routes each request to a
  specialist with its own narrowly-scoped `ChatClient` and system prompt -
  `ExplainAgent`, `ValidateAgent`, `ReviewAgent`, `ChatAgent` - one per agent task, no
  autonomous tool-calling loop (unchanged from the 09-08 decision).
- **Verification**: `VerifierAgent` is a separate agent, built on its own `ChatClient`
  and system prompt (not shared with any specialist), that checks a specialist's draft
  before the orchestrator returns it to the client - no fabricated numbers outside the
  applicant profile, no product/lender recommendation, no advice, tone stays calm and
  plain-language. If it rejects a draft it rewrites it into a safe fallback rather than
  blocking the response; if the verifier call itself fails, the orchestrator fails open
  and returns the specialist's draft unverified rather than breaking the journey.
- **Chat is not verified**: `/api/agent/chat` streams token-by-token, so there is no
  complete draft to check before the first tokens reach the user. Explain/validate/review
  are synchronous and structured, so those three go through the verifier; chat relies on
  `ChatAgent`'s own system prompt only. Revisit if the demo surfaces bad chat answers.
- **Frontend structure** (not yet built): landing page becomes a hub with a few entry
  options (e.g. "What is a loan?", "Top 10 mistakes when applying for a loan", "Start the
  journey") rather than a single start button; the journey page gets a persistent
  step-roadmap sidebar so the user always knows where they are in the flow.

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
