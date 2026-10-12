# Decision log

Short, dated entries. Newest first. One entry per decision that would otherwise get
re-litigated.

## 2026-10-12 — Hub topic cards are static content (#39)

- **Reverses part of 2026-09-23**: "What is a loan?" and "Top 10 mistakes when applying"
  no longer call `ExplainAgent`. The content is general and the same for everyone, so an
  LLM call only added a wait, an answer that changed between clicks, and token cost.
- **Source of truth stays `Explanations`**: the cards show the `what-is-a-loan` /
  `top-mortgage-mistakes` entries the store already loads from `GET /api/explanations`, so
  there's no copy in the frontend. Their related-topic chips open built-in definitions too.
  The agent is only a fallback if the definitions failed to load, and only then does the
  modal say "Answered by the assistant".
- The two texts were rewritten now that they're the main content, not a stub fallback.

## 2026-10-12 — "How much can I borrow?" calculator (#29)

- **Same rules as the journey**: the max loan is the smallest of the income limit
  (DTI ≤ 40%), the stress-test limit (≥ €300 left at rate + 3 pp) and, when a down
  payment is given, the LTV limit. The 40% / €300 limits are now constants on
  `AffordabilityCalculator` (`MAX_DTI_PCT`, `MIN_STRESS_BUFFER`) that `LoanProfileAssembler`
  also uses, so the calculator can't promise a loan the journey then flags. A round-trip
  test checks that.
- **LTV: a typical 85%, not the best mock bank's 95%** (`TYPICAL_MAX_LTV_PCT`), so the
  estimate doesn't assume the most generous lender.
- **Gross income for now**: #31 switches affordability to net income with new limits.
  Until then the calculator asks for gross income to match the journey; #31 only has to
  change the shared constants and the labels.
- **Hub layout**: the four cards sit in one row on wide screens (two columns below `lg`),
  and the hub container widens from `max-w-4xl` to `max-w-6xl` so everything still fits
  the viewport.
- Living costs/dependants and carrying the inputs into "Start the journey" are left out.

## 2026-10-11 — Dropped Anthropic, OpenAI only

- **Why**: we only run the agents on OpenAI now, so the Anthropic path (the default since
  2026-09-24) was dead code, and the default config and docs pointed people at
  `ANTHROPIC_API_KEY`, a key we don't use.
- **What changed**: the Anthropic Spring AI starter and the `spring.ai.anthropic` config
  are gone, and so is the `AI_PROVIDER` switch. `LlmConfiguredCondition` checks
  `OPENAI_API_KEY` only; no key -> stub, as before. `/api/agent/status` reports `stub`
  or `openai`, and `'claude'` is gone from the frontend's `AgentMode`.
- **Not reversed**: the agents still talk to Spring AI's provider-neutral `ChatClient`, so
  adding another provider later means adding its starter and key check back, not
  rewriting agents. The earlier entries below that mention Claude are kept as history.

## 2026-10-10 — Co-applicant affordability uses household finances

- **Household-level calculations**: when `hasCoApplicant` is true, `LoanProfileAssembler`
  adds a second `LoanProfile.Applicant` and includes both applicants' gross monthly income
  and existing monthly debt payments in DTI, the stressed monthly buffer, the stress-test
  result and related risk indicators. When it is false, stale co-applicant answers do not
  affect affordability.
- **Collect the debts we count**: the `co-applicant` step now asks for
  `coApplicantMonthlyDebtPayments` as well as name and gross monthly income; entering 0
  is valid. This matches the step's promise that the second applicant's debts count too.
- **No incomplete affordability figures for agents**: `ProfileFacts` omits DTI, the
  monthly buffer after stress and the stress-test result until the needed loan, primary
  applicant and co-applicant income/debt answers are entered. Its `basedOn` lists include
  the co-applicant's income and debt keys when applicable. The full `LoanProfile` can
  still use defaults for an in-progress application; agents receive `ProfileFacts`, not
  those default-based figures.
- **Profile and UI contract**: the profile contains both applicants, including the
  co-applicant's name (employment is null because it is not collected), and the summary
  displays both people and household totals.

## 2026-10-02 — ExplainAgent glossary-only gate

- **Strict glossary boundary**: explain requests whose topic is not present in
  `Explanations.java` now return a fixed "This topic is not in our glossary yet."
  response.
- **No LLM fallback**: the gate runs in `AgentOrchestrator` before the specialist and
  verifier are called, so unsupported topics cannot be improvised from general model
  knowledge.
- **Zero-token misses**: unsupported topics consume no LLM tokens.
- **Stub parity**: `StubAgentService` returns the same fixed response for unsupported
  topics, so behavior is consistent with and without an API key.

## 2026-09-24 — Token usage tracking and a daily token budget

- **Why**: the OpenAI key has a 5M tokens/day limit on `gpt-5-mini` (resets at midnight
  Estonian time), and the team can't see the key owner's dashboard.
- **How**: a Spring AI advisor (`TokenUsageAdvisor`) is added to every `ChatClient` via a
  `ChatClientBuilderCustomizer`, so all agents are covered without touching them; each
  agent names itself once in `LlmSpecialistAgent`. Every call logs a `[tokens]` line and
  adds to `TokenUsageTracker`'s per-day, per-agent totals, exposed at
  `GET /api/agent/usage`. Streamed chat replies report usage via OpenAI's
  `stream-options.include-usage`.
- **Budget**: `APP_DAILY_TOKEN_BUDGET` (default 4.5M, ~10% headroom) with a day boundary
  at midnight `Europe/Tallinn`. Warn at 80%; once spent, the advisor refuses calls and
  `AgentOrchestrator` answers from `StubAgentService` until midnight, so the demo keeps
  working.
- **Limitation**: totals are in-memory and reset on backend restart. The provider's
  dashboard stays the source of truth; persisting the count is a possible follow-up.

## 2026-09-24 — OpenAI as a switchable alternative to Claude

- **Why**: the mentor asked whether an OpenAI key works. The agents only use Spring AI's
  provider-neutral `ChatClient`, so no agent code had to change.
- **How it's selected**: both Spring AI starters are on the classpath.
  `AI_PROVIDER` (-> `spring.ai.model.chat`, default `anthropic`) picks which one gets
  auto-configured; set `AI_PROVIDER=openai` plus `OPENAI_API_KEY` (optionally
  `OPENAI_MODEL`, default `gpt-5-mini`). No key for the selected provider -> stub, as
  before. `/api/agent/status` now reports `stub`, `claude` or `openai`, and the UI badge
  names the right assistant.
- **Gating bug fixed on the way**: the agent beans were guarded by
  `@ConditionalOnBean(ChatClient.Builder.class)`, which is evaluated before Spring AI's
  auto-configuration runs, so the real agents never activated even with a key set.
  Replaced with `LlmConfiguredCondition`, which checks the selected provider's
  `spring.ai.<provider>.api-key` property instead. Covered by
  `AgentProviderSelectionTest`.
- **Caveat**: the system prompts were written against Claude. Expect some drift in tone
  and in how strictly the JSON-shaped outputs are followed on OpenAI models; Claude
  stays the default for the demo.

## 2026-09-23 — Landing hub, standalone calculator, and a bolder visual direction

- **Landing hub built** (closes the 09-17 item below): the single start button is
  replaced by a hub — a prominent "Start the journey" CTA, plus "What is a loan?" and
  "Top 10 mistakes when applying" cards that call `ExplainAgent` (not static copy) and
  open in a modal with clickable related-topic chips. `Explanations.java` gained
  `what-is-a-loan` and `top-mortgage-mistakes` glossary entries so both read well in stub
  mode too.
- **Standalone loan calculator**: a third hub card, backed by a new
  `POST /api/calculator/estimate` endpoint (pure math, no persistence, no agent call —
  wraps `AffordabilityCalculator.monthlyPayment()`). Debounced price/down-payment inputs
  show a live monthly payment, rate, and LTV before any application exists.
- **Visual direction overturned**: the 16-Sep-decided muted "calm, not-modern-fintech"
  tone (see the visual-direction note this superseded) read as too pale and boring in
  practice. Replaced with a bold saturated blue primary ("current"), a violet/gold accent
  pair, a gradient hero, and card hover/shadow treatment — still built entirely from Nuxt
  UI theme tokens and scoped component CSS, not a freeform redesign. Treat "bold, not
  muted" as the working direction unless explicitly revisited again.
- **No page-level scrolling**: both the landing page and the journey page now fit the
  viewport without scrolling the outer chrome. The layout shell is a fixed `h-screen`
  flex column with `overflow-hidden`; only specific content-heavy inner panels (the
  review step's stacked summary cards, the assistant's chat log) scroll internally when
  their content doesn't fit. Treat this as the default expectation for future page layout
  work.

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
