# Intelligent Mortgage Journey

An agent-supported mortgage application journey that turns a complex financial form into
a guided, understandable, cross-device experience.

University semester project (team of 3) built for an SEB project brief. It is an
interactive **prototype**, not production software.

---

## Problem

Applying for a mortgage is one of the most important and complex financial journeys a
customer goes through. It is widely perceived as difficult, time-consuming, and
confusing: unclear terminology, many required inputs, and a fragmented experience across
banks and channels.

## Goal

Design and prototype a mortgage application journey that:

- provides a guided, user-friendly application experience,
- uses an agent-like approach to assist and support the user,
- demonstrates how complex financial forms can be simplified,
- works seamlessly across web and mobile.

Education is delivered **inline within the journey** (just-in-time explanations tied to
the field or concept in front of the user), not as a separate learning phase. A
secondary, mostly conceptual ambition is a standardized "loan profile" that could
conceptually be shared across multiple banks.

## Guiding constraints (from the brief)

- Prioritise **user experience and clarity**, not full regulatory accuracy.
- Use **mock or simplified data** where needed.
- Clearly **demonstrate how the agent improves the journey** — this is the main thing
  being evaluated, alongside a strong final demo.

### In scope

Adaptive multi-step application flow · agent assist layer (explain / validate / read-back
/ chat) · smart pre-fill and suggestions · plain-language validation · standardized loan
profile · a mock multi-bank comparison screen · standalone loan calculator · responsive
web + mobile · usability testing · final demo.

### Out of scope

Real bank APIs · real KYC / identity verification · real credit decisioning · native
mobile apps · production-grade persistence and auth.

---

## Tech stack

| Layer      | Choice                                                                 |
| ---------- | --------------------------------------------------------------------- |
| Frontend   | Vue 3 + Nuxt (run as an SPA, `ssr: false`), Nuxt UI, Pinia for state  |
| Backend    | Java + Spring Boot, REST + Server-Sent Events                        |
| LLM        | OpenAI via [Spring AI](https://docs.spring.io/spring-ai/reference/) `ChatClient` (API key stays server-side) |
| Database   | In-memory H2 by default; Postgres available via a `docker-compose` profile |
| Data       | Mock services: property valuation, interest rates, document checklist, bank catalog |

---

## Getting started

### Option A — Docker (closest to how the mentor/server will run it)

```bash
cp .env.example .env          # fill in OPENAI_API_KEY, or leave blank for the stub
docker compose up --build
```

Frontend: http://localhost:3000 · Backend: http://localhost:8080. Everything (H2, both
services) runs in containers — nothing else to install. Add `-d` to run detached; `docker
compose down` to stop. Changed code needs a rebuild: `docker compose up -d --build`.

### Option B — Local dev (hot reload)

```bash
# 1. Backend — http://localhost:8080  (runs on in-memory H2, no DB setup needed)
cd backend && ./gradlew bootRun

# 2. Frontend — http://localhost:3000  (proxies /api to the backend)
cd frontend && npm install --legacy-peer-deps && npm run dev
```

Open http://localhost:3000.

**No API key yet?** The app runs anyway: the agent falls back to a deterministic stub
(canned explanations, rule-based validation, a templated read-back). `GET
/api/agent/status` shows `stub` or `openai`. To use the real agents, set
`OPENAI_API_KEY=sk-...` (optionally `OPENAI_MODEL`, default `gpt-5-mini`) in `.env`
(Docker) or the environment (local dev) — no code change either way.

**Token usage:** every LLM call logs a `[tokens]` line in the backend's console (agent,
model, input/output tokens, today's running total). `GET /api/agent/usage` returns today's
totals per agent. There's a daily budget (`APP_DAILY_TOKEN_BUDGET`, default 4,500,000,
resetting at midnight Europe/Tallinn): the log warns at 80%, and once it's spent the agents
answer with the stub until midnight. The count is in-memory, so a backend restart resets it.

`npm install` needs `--legacy-peer-deps` — npm 10.9.x crashes on Nuxt UI's optional-peer
set otherwise.

See `backend/README.md` and `frontend/README.md` for per-service detail (API surface,
package/folder layout).

---

## Where things are documented

This README covers the pitch and how to run the thing. Everything else — *why* the
project is built the way it is, and how the pieces actually work — lives in `docs/`,
which is the source of truth over any one person's chat history:

- **[`docs/decisions.md`](docs/decisions.md)** — dated log of settled decisions, newest
  first. Read this first when picking the project back up.
- **[`docs/agent-architecture.md`](docs/agent-architecture.md)** — the current-state
  reference for the agent backend: what each of the five agents does, how grounding and
  verification work, why chat is exempt from verification, and the recipe for adding a
  new agent.
- **[`docs/personas.md`](docs/personas.md)** — the two demo personas the UX and final
  demo are built around.

When the team settles something new, add a dated entry to `docs/decisions.md` (and update
`docs/agent-architecture.md` if it changes how the agent backend works) in the same PR —
don't leave it only in a chat transcript someone else can't see. Current build status and
open work live as GitHub issues, not as a status paragraph here.

---

## The three contracts

These are the interfaces between the three developers' areas, frozen early so work can
proceed in parallel. Full DTOs live in the source files referenced below — keep
`backend/.../agent/AgentDtos.java` and `frontend/app/types/api.ts` in sync when either
changes.

### 1. Flow definition schema

Served by the backend as JSON (`FlowDefinition.java`); the frontend renders it
generically.

```jsonc
{
  "steps": [
    {
      "id": "property",
      "title": "About the property",
      "fields": [
        { "key": "address", "type": "text", "label": "Property address", "helpKey": "property-address" },
        { "key": "price",   "type": "money", "label": "Purchase price",  "validation": { "min": 1 } }
      ],
      "next": [
        { "if": { "field": "hasCoApplicant", "eq": true }, "goto": "co-applicant" },
        { "default": "income" }
      ]
    }
  ]
}
```

### 2. Agent API

```
GET  /api/agent/status                   -> { mode: "stub" | "openai" }
GET  /api/agent/usage                    -> today's token usage vs. the daily budget, per agent
POST /api/agent/chat      (SSE stream)   { applicationId, message }         -> streamed reply
POST /api/agent/explain                  { applicationId, topic | fieldKey } -> { explanation, relatedTopics[], followUpQuestion }
POST /api/agent/validate                 { applicationId, stepId }           -> { status, issues[], followUpQuestion }
POST /api/agent/review                   { applicationId }                   -> { summary, concerns[], followUpQuestion }
```

`followUpQuestion` is `null` unless the agent couldn't do its job well with what it was
given — if set, show it as a small inline prompt and send the reply back as extra context
on a normal follow-up call, not as a new chat thread.

### 3. Loan profile model

The standardized output; feeds the mock multi-bank comparison screen.

```jsonc
{
  "applicants": [ { "employment": "...", "monthlyIncome": 0 } ],
  "property":    { "type": "...", "value": 0, "location": "..." },
  "loanRequest": { "amount": 0, "termYears": 0, "rateType": "...", "purpose": "..." },
  "financials":  { "downPayment": 0, "ltv": 0, "dti": 0, "stressTestResult": "..." },
  "riskIndicators": [ "..." ],
  "documents": [ "..." ]
}
```

The multi-bank ambition is kept to a **single comparison screen** fed by this profile —
no bank integrations. Two standalone calculator endpoints, `POST /api/calculator/estimate`
(price/down-payment/rate/term → monthly payment) and `POST /api/calculator/max-loan`
(income/debts/down payment → largest loan), sit outside these three contracts on
purpose — they're pure math with no persistence or agent involvement, used by the landing
page before an application exists.

---

## Repository structure

```
/backend                    Spring Boot 4 / Java 25 API (Gradle wrapper included)
/backend/Dockerfile         multi-stage build -> runnable jar
/frontend                   Nuxt 4 SPA (Vue 3, Nuxt UI, Pinia)
/frontend/Dockerfile        multi-stage build -> Nitro node server
/docs                       decision log, agent architecture reference, demo personas
docker-compose.yml          backend + frontend services (+ optional Postgres profile)
.env.example                copy to .env for the OpenAI API key
```

`OPENAI_API_KEY` is read server-side only, either from the environment or a gitignored
`.env` (`docker compose` also reads a `.env` in this directory automatically).

---

## Team & responsibilities

| Area              | Owns                                                                                     |
| ----------------- | --------------------------------------------------------------------------------------- |
| Frontend          | Flow renderer, assist panel, Pinia state, responsive / mobile, comparison screen        |
| Agent service     | Spring AI integration, per-specialist prompt design, the verifier, SSE streaming, explanation content, read-back |
| Core backend      | Flow engine + config, application / profile services, mock data services, persistence, OpenAPI contract, validation rules |

One person owns the OpenAPI spec so the frontend can generate types from it.

---

## Roadmap

| Weeks | Focus                                                                                                  |
| ----- | ---------------------------------------------------------------------------------------------------- |
| 1–2   | Pain-point research, personas, flow map, **freeze the three contracts**, repo skeleton, one vertical slice (a step + assist panel + one agent call, end to end) |
| 3–7   | Full flow, all agent behaviours, mock data, validation, comparison screen                            |
| 8–10  | Usability testing with real people, iterate on clarity, responsive polish                           |
| 11–13 | Demo scripting (both personas), write-up, buffer                                                     |
| 14    | Final demo                                                                                          |

See **[`docs/personas.md`](docs/personas.md)** for the two people the final demo is built
around, and GitHub's issues/milestones for what's currently in progress.
