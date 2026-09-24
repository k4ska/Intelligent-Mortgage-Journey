# Backend — Intelligent Mortgage Journey

Spring Boot 4 (Java 25) REST API. In-memory H2, Claude (or OpenAI) via Spring AI.

## Run

```bash
./gradlew bootRun
# API on http://localhost:8080
# H2 console on http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:mortgage, user: sa)
```

The app runs **without** an API key — the agent falls back to a deterministic stub.
To use the real Claude-backed agent, set the key before starting:

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew bootRun
```

To use OpenAI instead:

```bash
export AI_PROVIDER=openai
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-5-mini   # optional, this is the default
./gradlew bootRun
```

`GET /api/agent/status` reports `{"mode":"stub"}`, `{"mode":"claude"}` or `{"mode":"openai"}`.

**Token usage:** every LLM call logs a `[tokens]` line in the backend's console (agent,
model, input/output tokens, today's running total). `GET /api/agent/usage` returns today's
totals per agent. There's a daily budget (`APP_DAILY_TOKEN_BUDGET`, default 4,500,000,
resetting at midnight Europe/Tallinn): the log warns at 80%, and once it's spent the agents
answer with the stub until midnight. The count is in-memory, so a backend restart resets it.

## Test

```bash
./gradlew test
```

## API surface

| Method & path | Purpose |
| --- | --- |
| `GET /api/flow` | Full flow definition (contract 1) — the frontend renders the journey from this |
| `POST /api/flow/next` | `{ currentStepId, answers }` → next step, or `{ complete: true }` |
| `POST /api/applications` | Create an application → `201` with the application view |
| `GET /api/applications/{id}` | Current state (answers, current step, complete) |
| `POST /api/applications/{id}/steps` | `{ stepId, answers }` → merges answers, advances the flow |
| `GET /api/applications/{id}/profile` | Standardized loan profile (contract 3) |
| `GET /api/applications/{id}/offers` | Mock multi-bank offers derived from the profile |
| `GET /api/agent/status` | `{ mode }` — `stub`, `claude` or `openai` |
| `GET /api/agent/usage` | Today's LLM token usage vs. the daily budget, per agent |
| `POST /api/agent/explain` | `{ applicationId, topic \| fieldKey }` → `{ explanation, relatedTopics[], followUpQuestion }` |
| `POST /api/agent/validate` | `{ applicationId, stepId }` → `{ status, issues[], followUpQuestion }` |
| `POST /api/agent/review` | `{ applicationId }` → `{ summary, concerns[], followUpQuestion }` |
| `POST /api/agent/chat` | `{ applicationId, message }` → `text/event-stream` of tokens |
| `POST /api/calculator/estimate` | `{ propertyPrice, downPayment, annualRatePct?, termYears? }` → `{ loanAmount, monthlyPayment, ltv, annualRatePct, termYears }` — pure math, no persistence, usable before an application exists |

`followUpQuestion` is `null` unless the agent needs to ask the applicant something before
it can do the job well — see `../docs/agent-architecture.md`.

## Package layout

```
com.seb.mortgage
├── flow         flow definition records, JSON loader, next-step logic  (contract 1)
├── application  LoanApplication entity, service, controller
├── profile      LoanProfile record + assembler                         (contract 3)
├── agent        multi-agent backend: specialists + verifier + orchestrator, DTOs,
│                controller                                              (contract 2)
├── mockdata     property valuation, rates, documents, banks, affordability maths
└── config       CORS
```

See `../docs/agent-architecture.md` for how the `agent` package is put together —
what each agent does, how grounding/verification work, and the recipe for adding a new
agent.

The three contracts (`FlowDefinition`, `AgentDtos`, `LoanProfile`) are the interfaces
between the three developers' areas — change them deliberately.

## Editing the journey

`src/main/resources/flow/flow-definition.json` — add steps, fields, and branching rules
here; no code change needed for straightforward additions. Branching rule ops: `eq`,
`ne`, `gt`, `gte`, `lt`, `lte`, `in`.
