# Intelligent Mortgage Journey

A mortgage journey as a **conversation**: you sit down with an AI advisor, talk about
what you want, and it helps you from "could I even get a loan?" all the way to an
accepted bank offer.

University semester project (team of 3) built for an SEB project brief. It is an
interactive **prototype**, not production software. **Not affiliated with any bank. All
personal data, documents and bank offers are simulated.**

> **Pivot, 2026-10-15.** The project changed from a guided form to a chat-based advisor.
> The full picture is in **[`docs/vision.md`](docs/vision.md)**, and the high-level scheme
> in **[`docs/architecture.md`](docs/architecture.md)**. The code in this repo still
> mostly reflects the form-based version until it's reworked.

---

## Problem

Applying for a mortgage is one of the most important and complex financial journeys a
customer goes through. It is widely perceived as difficult, time-consuming, and
confusing: unclear terminology, many required inputs, and a fragmented experience across
banks and channels. Many people don't even know where to start, or whether they could
get a loan at all.

## What we're building

A conversation with an advisor agent, instead of a form:

1. The user logs in, and the agent opens with *"Hi, how can I help you?"*
2. Through natural conversation, the agent learns about the user's situation and goals.
3. The user can paste a kv.ee link; the agent reads the type, price and location (or
   asks the user for them if the listing can't be read).
4. The agent explains whether the user can afford it, and if not, what they could afford.
   Charts show how interest rates (Euribor + margin) change the picture.
5. The agent asks for the documents a bank needs and explains where to get them; uploaded
   documents fill in the user's profile.
6. When everything is ready, the agent offers to go to the banks. The user picks one or
   more real Estonian banks, each played by a **bank agent**.
7. The banks reply by email in an in-app inbox. The advisor helps the user answer,
   compare and negotiate the offers, until they accept one.

**The screen:** documents on the left, chat in the middle, the auto-filled profile and
property on the right, and an inbox tab for the bank emails.

## Guiding constraints (from the brief)

- Prioritise **user experience and clarity**, not full regulatory accuracy.
- Use **mock or simplified data** where needed.
- Clearly **demonstrate how the agent improves the journey**. This is the main thing
  being evaluated, alongside a strong final demo.

### In scope

Chat-based advisor · auto-filled profile and property panels · kv.ee listing lookup ·
affordability with Estonian-style rules · document upload with confirmation · bank agents
and an in-app inbox · negotiation · login with saved progress · responsive web + mobile.

### Out of scope

Smart-ID or any real identity check · real bank applications, APIs or emails · real
credit decisions · refinancing · KredEx · native mobile apps.

See [`docs/vision.md`](docs/vision.md) for the full scope, the **core demo path vs.
extras** split, and what is mocked and how.

---

## Tech stack

| Layer      | Choice                                                                 |
| ---------- | --------------------------------------------------------------------- |
| Frontend   | Vue 3 + Nuxt (run as an SPA, `ssr: false`), Nuxt UI, Pinia for state  |
| Backend    | Java + Spring Boot, REST + Server-Sent Events                        |
| LLM        | OpenAI via [Spring AI](https://docs.spring.io/spring-ai/reference/) `ChatClient` (API key stays server-side) |
| Database   | PostgreSQL in Docker with Flyway migrations; in-memory H2 for local development and tests |
| Data       | Mock services: documents, interest rates, bank catalog; kv.ee listings read live (best effort) |
| Email      | Stored in our own database at first; [agentmail.to](https://agentmail.to) later |

---

## Getting started

### Option A — Docker (closest to how the mentor/server will run it)

```bash
cp .env.example .env          # fill in OPENAI_API_KEY, or leave blank for the stub
docker compose up --build
```

Frontend: http://localhost:3000 · Backend: http://localhost:8080. PostgreSQL data is
stored in the `mortgage_pgdata` Docker volume, so restarting the backend does not remove
users or journey progress. Add `-d` to run detached; `docker compose down` to stop.
Changed code needs a rebuild: `docker compose up -d --build`.

To start from an empty database, use `docker compose down -v` (this deletes the local
PostgreSQL volume). Flyway applies schema migrations when the backend starts.

### Option B — Local dev (hot reload)

```bash
# 1. Backend — http://localhost:8080 (runs on in-memory H2, no DB setup needed)
cd backend && ./gradlew bootRun

# 2. Frontend — http://localhost:3000  (proxies /api to the backend)
cd frontend && npm install --legacy-peer-deps && npm run dev
```

Open http://localhost:3000.

**No API key yet?** The app runs anyway, in stub mode: the agents give canned replies. `GET
/api/agent/status` shows `stub` or `openai`. To use the real agents, set
`OPENAI_API_KEY=sk-...` (optionally `OPENAI_MODEL`, default `gpt-5-mini`) in `.env`
(Docker) or the environment (local dev) — no code change either way.

**Token usage:** every LLM call logs a `[tokens]` line in the backend's console (agent,
model, input/output tokens, today's running total). `GET /api/agent/usage` returns today's
totals per agent. There's a daily budget (`APP_DAILY_TOKEN_BUDGET`, default 4,500,000,
resetting at midnight Europe/Tallinn): the log warns at 80%, and once it's spent the agents
give canned replies until midnight. The count is in-memory, so a backend restart resets it.

`npm install` needs `--legacy-peer-deps` — npm 10.9.x crashes on Nuxt UI's optional-peer
set otherwise.

See `backend/README.md` and `frontend/README.md` for per-service detail (API surface,
package/folder layout).

---

## Where things are documented

This README covers the pitch and how to run the thing. Everything else lives in `docs/`,
which is the source of truth over any one person's chat history:

- **[`docs/vision.md`](docs/vision.md)**: what we're building and why, the journey step
  by step, the screen, scope, and the core path vs. extras. **Start here.**
- **[`docs/decisions.md`](docs/decisions.md)**: dated log of settled decisions, newest
  first.
- **[`docs/architecture.md`](docs/architecture.md)**: the high-level scheme: the two
  orchestrators and their agents, the data, the API contracts, the key flows and the
  work split. Read it second.

When the team settles something new, add a dated entry to `docs/decisions.md` (and update
`docs/vision.md` or `docs/architecture.md` if it changes them) in the same PR. Don't
leave it only in a chat transcript someone else can't see. Current build status and open
work live as GitHub issues, not as a status paragraph here.

---

## API contracts

The frontend and backend meet at three contracts, defined in
[`docs/architecture.md`](docs/architecture.md#5-api-contracts):

1. **The REST endpoints**: auth, chat, profile, documents, banks and inbox.
2. **The stream events**: one SSE stream per advisor turn carries the reply text *and*
   events for the panels (`profile_updated`, `property_updated`, `document_status`,
   `chart`, `bank_picker`, `draft_reply`, `corrected`).
3. **The data**: profile fields (value, source, status), the property, documents, the
   frozen application snapshot and the email threads.

Until the code is reworked, `backend/README.md` describes the API as it is today. Keep the
backend DTOs and `frontend/app/types/api.ts` in sync when either changes.

---

## Repository structure

```
/backend                    Spring Boot 4 / Java 25 API (Gradle wrapper included)
/backend/Dockerfile         multi-stage build -> runnable jar
/frontend                   Nuxt 4 SPA (Vue 3, Nuxt UI, Pinia)
/frontend/Dockerfile        multi-stage build -> Nitro node server
/docs                       vision, decision log, agent architecture reference
docker-compose.yml          backend + frontend + PostgreSQL services
.env.example                copy to .env for the OpenAI API key
```

`OPENAI_API_KEY` is read server-side only, either from the environment or a gitignored
`.env` (`docker compose` also reads a `.env` in this directory automatically).

---

## Team & responsibilities

| Area | Who | Owns |
| --- | --- | --- |
| Advisor side | **k4ska** | The advisor orchestrator, the advisor agent and its tools, keeping it honest (phases, number check, test conversations), the document reader, stream events, profile fields, the property and listing lookup, affordability and chart data |
| Frontend | **janmatvejev** | The three-panel layout, login, chat streaming, panels, document upload, bank picker, inbox, charts, phone layout |
| Bank side + platform | **lightbtw** | Auth, database, application snapshot, email gateway (database, then agentmail), the bank orchestrator, bank agents, bank rules and offers, verifier, inbox endpoints |

Details, including what each person deletes from the form-based version, are in
[`docs/architecture.md`](docs/architecture.md#8-work-split).

---

## Next steps

1. Agree on the vision ([`docs/vision.md`](docs/vision.md)). ✓
2. Agree on the high-level scheme ([`docs/architecture.md`](docs/architecture.md)). ✓
3. Build the **walking skeleton**: log in → the advisor greets → one fact fills the
   profile panel → pick one bank → an email lands in the inbox.
4. Then deepen each area along the core demo path, followed by the extras.
