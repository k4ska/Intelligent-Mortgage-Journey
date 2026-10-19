# Architecture: the high-level scheme

> Agreed by the team on 2026-10-15, after the pivot to a chat-based advisor. This is the
> **target design**: the code still mostly reflects the form-based version until it's
> reworked. [`vision.md`](vision.md) says *what* we build; this doc says *how* the pieces
> fit together. The dated log of *why* is in [`decisions.md`](decisions.md).
>
> It replaces the old `docs/agent-architecture.md`, which described the form-era agents
> (still in git history).

## 1. The big picture

```
┌──────────────────────────── FRONTEND (Nuxt) ─────────────────────────────┐
│  Login   │  Documents panel  │  Chat  │  Profile + Property  │  Inbox    │
└──────┬──────────────┬─────────────┬───────────────┬──────────────┬───────┘
       │ REST         │ upload      │ SSE stream    │ (pushed via  │ REST + SSE
       │              │             │ (text+events) │  the stream) │ (assist)
┌──────▼──────────────▼─────────────▼───────────────▼──────────────▼───────┐
│                         BACKEND (Spring Boot)                            │
│                                                                          │
│  Auth (Spring Security, session cookie) ── users                         │
│                                                                          │
│  ┌──────────── ADVISOR ORCHESTRATOR (works for the user) ─────────────┐  │
│  │  Advisor agent ──tools──► profile · listings · affordability ·     │  │
│  │                           charts · documents · glossary · banks    │  │
│  │  Document reader agent                                             │  │
│  └─────────────────────────────┬──────────────────────────────────────┘  │
│                                │ application snapshot / emails           │
│                         ┌──────▼───────┐                                 │
│                         │ Email gateway│  v1: our database               │
│                         │  (interface) │  v2: agentmail.to               │
│                         └──────▲───────┘                                 │
│  ┌──────────── BANK ORCHESTRATOR (works for the banks) ───┴────────────┐  │
│  │  Bank agent ×5 (SEB, Swedbank, LHV, Luminor, Coop Pank)             │  │
│  │  Offer calculator (bank rules, in code)                             │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  Shared: verifier (chat turns + bank emails) · token budget + usage      │
│          tracking · stub fallback · database                             │
└──────────────────────────────────────────────────────────────────────────┘
```

### The wall

The two orchestrators only meet at the **email gateway**. The bank side never sees the
user's conversation with the advisor; it only sees the frozen application snapshot and
its own email thread, like a real bank. This is enforced in code (the bank side has no
access to conversation data), not only in prompts. It keeps the negotiation honest: a
bank can't know that the user said "I'd accept anything" in the chat.

### Three rules that hold everywhere

1. **Numbers come from code, words from the LLM.** Affordability, chart data, offer
   amounts, rates and payments are calculated by our code. Agents choose *what* to say
   and *when* to call a tool, never invent a figure.
2. **Agents only see facts.** As with today's `ProfileFacts`: an agent gets what the
   user actually said or confirmed, never defaults filling a gap.
3. **The user stays in control.** The user confirms values read from documents, picks
   the banks, and sends every email themselves. The advisor only drafts.

## 2. The agents

All agents keep the current base: `LlmSpecialistAgent` (one `ChatClient` with its own
system prompt, named in the token log), extended so an agent **can register tools**.
The token budget (`TokenUsageAdvisor`, `TokenUsageTracker`) and the daily limit stay as
they are.

### Advisor orchestrator (works for the user)

**1. Advisor agent.** The single brain the user talks to, in two places: the main chat,
and next to an email thread in the inbox. Both write to the **same conversation**
(inbox messages are tagged with their thread), so it remembers everything.

- **Sees:** the conversation, profile facts, the active property, the document
  checklist, and the user's bank emails.
- **Outputs:** a streamed reply plus events for the panels (see section 5).
- **Kept on rails** by phases, a number check and the verifier: see section 3.
- **Tools** (Spring AI tool calling; each tool runs our code and may push an event):

| Tool | What it does | Event it pushes |
| --- | --- | --- |
| `updateProfile(fields)` | Saves facts from the chat (source `CHAT`), or confirms uncertain ones | `profile_updated` |
| `lookupListing(url)` | One honest fetch of a kv.ee listing, reading its structured data (type, price, location, size, rooms, year built). Blocked or not kv.ee → "couldn't read it, ask the user" | `property_updated` |
| `updateProperty(fields)` | Saves type, price and location the user gives (or that the advisor picks out of pasted page text, marked uncertain) | `property_updated` |
| `checkAffordability()` | Runs `AffordabilityCalculator` on the profile and property | — |
| `suggestAffordableRange()` | What price range the user could afford instead | — |
| `showChart(type, params)` | Calculates chart data (e.g. rate vs. monthly payment) | `chart` |
| `requestDocument(type)` | Marks a document as needed, with where to get it in real life | `document_status` |
| `explainTerm(topic)` | Looks up the `Explanations` glossary (the old `ExplainAgent`'s job) | — |
| `checkReadiness()` | Plain-code check: are all required fields and documents confirmed? Returns what's missing | — |
| `showBankPicker()` | Only allowed when `checkReadiness()` passes | `bank_picker` |
| `draftReply(threadId, body)` | Inbox only: puts a draft in the reply box; the user edits and sends | `draft_reply` |

**2. Document reader agent.** Called when a document is uploaded. We never really read
the file.

- **Sees:** the document type and the profile facts so far.
- **Outputs:** believable values that fit what the user already said, saved as profile
  fields with source `DOCUMENT` and status `UNCERTAIN`. The advisor then asks the user
  to confirm them in the chat.

### Bank orchestrator (works for the banks)

**3. Bank agent.** One class, configured five times with each bank's rules and tone.

- **Sees:** the application snapshot, its own thread, and its bank's rules. Nothing else.
- **Code decides, the agent writes.** The **offer calculator** works out, in code,
  whether the bank declines (for example, the down payment is below its minimum), the
  offer (amount, term, Euribor, margin, rate, monthly payment, conditions), and in
  negotiation, the lowest margin allowed this round. The agent decides how to respond
  within those limits and writes the email.
- **Outputs** a structured email: `type` (question | offer | counter-offer |
  confirmation | decline), subject, body, and the offer if there is one.
- **Negotiation limits:** each bank has a lowest margin and a maximum number of rounds
  (default 2). After that: "this is our final offer".
- **Answers straight away**: the user's reply call waits for the bank's answer.

### Shared

**4. Verifier agent.** Kept from today, and used by both orchestrators (it lives in the
shared `agent` package, outside the wall). It checks:

- **bank emails**: do the numbers in the text match the offer object and the bank's
  rules?
- **the advisor's drafted replies**: no made-up numbers, calm tone.
- **flagged advisor chat turns**: see section 3.

It still fails open: if the verifier call itself errors, the draft goes through
unchanged.

### Without an API key (or when the daily budget is spent)

The stub gives a **canned reply** ("The assistant is offline, please add an API key")
for the advisor and bank agents. We don't script fake conversations.

### What happens to today's agents

| Today | Becomes |
| --- | --- |
| `AgentOrchestrator` | Split into `AdvisorOrchestrator` and `BankOrchestrator` |
| `ChatAgent` | The advisor agent, with tools and events |
| `ExplainAgent` + `Explanations` | The `explainTerm` tool (glossary kept) |
| `ValidateAgent`, `ReviewAgent` | Dropped; readiness is a plain-code check |
| `VerifierAgent` | Kept, checks flagged chat turns, bank emails and drafts |
| `StubAgentService` | Canned "offline" replies |
| `LoanProfileAssembler`, `ProfileFacts` | Kept; built from profile field rows instead of form answers |
| `AffordabilityCalculator`, `RateService`, `BankCatalogService` | Kept; bank catalog moves to the real bank names and gains the bank rules |

## 3. Keeping the advisor honest

The user talks to one advisor, so it does jobs that used to be separate specialists. No
setup can guarantee an LLM never hallucinates (the old one couldn't either), so we make
it hard to get things wrong, catch what slips through, and measure the rest. The
specialists' exact tasks live on as tools and code; these four layers replace the
focus they gave.

| What could go wrong | What protects it |
| --- | --- |
| Invented numbers (max loan, payments, rates) | Numbers come from tools; the **number check** catches any that don't |
| Wrong definitions (Euribor, LTV) | `explainTerm` reads our glossary |
| Jumping ahead (going to the banks too early) | **Phases** in code; `showBankPicker` needs `checkReadiness` to pass |
| A wrong value in the profile | It shows in the panel with its source; the user corrects it in the chat |
| Promises or bad advice ("you'll surely get it") | The prompt, plus the **verifier** on flagged turns |
| Made-up offers | The offer calculator, plus the verifier on bank emails |

### Layer 1: phases in code

The advisor orchestrator works out the conversation's **phase from the stored data**
(the model never chooses it). Each phase adds a short focus instruction to the advisor's
prompt and decides which tools it gets. Tools unlock cumulatively, so a natural
conversation can still jump around (for example, pasting a listing link in the first
message).

| Phase | When (worked out from the data) | Focus | Tools added |
| --- | --- | --- | --- |
| Getting to know you | Key facts missing (income, savings, buying alone or not, other loans) | Ask simple questions, one at a time | `updateProfile`, `lookupListing`, `updateProperty`, `explainTerm` |
| Property | Key facts known, no property yet | Ask what they have in mind, or suggest a range | `checkAffordability`, `suggestAffordableRange`, `showChart` |
| Affordability | A property is set, affordability not yet discussed | Explain the result in plain language | (same) |
| Documents | Affordability discussed | Ask for documents, explain where to get them | `requestDocument`, `checkReadiness` |
| Ready | `checkReadiness` passes | Tell the user they're ready, offer to go to the bank | `showBankPicker` |
| At the banks | The application is sent | Help with the inbox: explain, compare, negotiate | `draftReply` (inbox only) |

### Layer 2: the number check (plain code, after every turn)

When the advisor's reply finishes, code pulls every € amount and % out of the text and
checks that each one appeared in that turn's tool results, the profile, the property, the
user's own messages, or the user's bank emails (rounding within 1% allowed, so "about
€210,000" passes for €209,734). It's deterministic, free and instant.

### Layer 3: the verifier on flagged turns

A turn is **flagged** when the number check fails, or when it talks about money (it
called `checkAffordability`, `suggestAffordableRange` or `showChart`, or it compares
offers). The text has already streamed to the user, so the stream stays open while the
verifier checks the finished reply:

- approved → `done`, nothing changes;
- rejected → a `corrected` event replaces the message with the verifier's safe version,
  and the UI marks it as corrected, then `done`.

Unflagged turns (most small talk and questions) end straight away, so the chat stays fast.

### Layer 4: test conversations (measuring it)

About 20 scripted conversations run against the real advisor, each with checks on the
outcome. For example:

- a user with a low income → the advisor suggests a cheaper range, with no invented
  numbers;
- a user who gives contradictory numbers → the profile ends up with the corrected value;
- a user who pushes for a promise → no promise is given;
- a user who asks to go to the bank too early → no bank picker.

They live under `backend/src/test/.../advisor/scenarios/` and run with a separate Gradle
task (`./gradlew advisorEval`), not in the normal `./gradlew test`, because they need an
API key and cost tokens. We run them after prompt or tool changes and keep the results,
so we can show how often the advisor gets things right.

## 4. The data

```
User ─────────── email, password (hashed)
 │
 ├─ Conversation ── exactly one per user ("start over" wipes everything below)
 │    └─ Message ── role (user | advisor | system), text, threadId (if in the inbox),
 │                  attachments (chart data, bank picker), time
 │
 ├─ ProfileField ── one row per fact:
 │      key (netIncome, savings, dependants, coApplicant.netIncome, ...)
 │      value · source (CHAT | LISTING | DOCUMENT) · status (UNCERTAIN | CONFIRMED)
 │      sourceDocument (if it came from a document)
 │
 ├─ Property ───── the active one. Required: type (APARTMENT | HOUSE | PLOT |
 │                 NEW_BUILD), price, location. Only when read from the listing:
 │                 size, rooms, year built. Plus the listing link, and for each value
 │                 a source (LISTING | CHAT) and status (UNCERTAIN | CONFIRMED)
 │
 ├─ Document ───── type (ID, PAYSLIP, BANK_STATEMENT, DOWN_PAYMENT_PROOF, VALUATION,
 │                 PURCHASE_AGREEMENT, ...), status (NEEDED | UPLOADED | CONFIRMED),
 │                 file name only (the file itself isn't stored), values read from it
 │
 └─ Application ── a FROZEN snapshot of profile + property + document list at the
      │            moment of sending, plus the chosen banks. No updated re-sends.
      └─ EmailThread ── one per bank: status (OPEN | OFFER | NEGOTIATING | ACCEPTED |
           │             DECLINED | CLOSED), negotiation round
           └─ Email ──── from (BANK | USER), type, subject, body, offer (amount, term,
                         Euribor, margin, rate, monthly payment, conditions,
                         valid until), read/unread

Seed data (the same for everyone, loaded at startup):
  Bank ───────── SEB, Swedbank, LHV, Luminor, Coop Pank: base margin, lowest margin,
                 max LTV, min down payment %, strictness, max negotiation rounds, tone
```

- **One row per profile fact** makes "where did this come from" and "is it confirmed"
  easy for every field. The `LoanProfile` used by the calculator is **built** from these
  rows.
- **The application is a frozen snapshot**: the bank sees exactly what was sent, even
  if the conversation changes things afterwards.

## 5. API contracts

These are the agreements that let frontend and backend work in parallel. Keep the
backend DTOs and `frontend/app/types/api.ts` in sync.

### Auth (Spring Security, session cookie)

```
POST /api/auth/register   { email, password }  -> user
POST /api/auth/login      { email, password }  -> user   (sets the session cookie)
POST /api/auth/logout
GET  /api/auth/me                               -> user | 401
```

### Chat: one streaming endpoint for every advisor turn

```
GET  /api/chat                        -> message history
POST /api/chat/messages   (SSE)       { text }                                 the user typed
                                      { trigger: "DOCUMENT_UPLOADED", documentId }
                                      { trigger: "APPLICATION_SENT",  applicationId }
                                      { trigger: "OFFER_ACCEPTED",    threadId }
POST /api/chat/reset                  -> start over (wipes conversation, profile,
                                         property, documents, applications)
```

Triggers let the advisor react to things that happen outside the chat, through the same
stream and code path.

### Stream events (shared by the chat and the inbox assist)

```
text              { delta }                                 a piece of the reply
profile_updated   { fields: [{ key, value, source, status }] }
property_updated  { property: { listingUrl, fields: [{ key, value, source, status }] } }
                    keys: type (APARTMENT | HOUSE | PLOT | NEW_BUILD), price, location,
                    size (m²), rooms, yearBuilt
document_status   { type, status, whereToGet }
chart             { chartType, title, data }
bank_picker       { banks: [{ id, name }] }                 card under the chat
draft_reply       { threadId, body }                        fills the inbox reply box
corrected         { messageId, text }                       the verifier replaced a flagged
                                                            reply (see section 3)
done              { messageId }
error             { message }
```

### Panels

```
GET  /api/profile                     -> fields + active property (null if none yet)
GET  /api/documents                   -> checklist with statuses
POST /api/documents/{type}  (file)    -> document; the frontend then sends the
                                         DOCUMENT_UPLOADED trigger to the chat
```

### Banks and inbox

```
GET  /api/banks                       -> the 5 banks
POST /api/applications   { bankIds }  -> freezes the snapshot and sends it;
                                         each bank agent answers right away
GET  /api/inbox                       -> threads (bank, status, last email, unread)
GET  /api/inbox/{threadId}            -> the emails in the thread
POST /api/inbox/{threadId}/reply   { body }      -> the user's email + the bank's answer
                                                    (the UI shows "SEB is typing..."
                                                    while it waits)
POST /api/inbox/{threadId}/accept  { emailId }   -> thread ACCEPTED, bank sends a
                                                    confirmation, other threads CLOSED
POST /api/inbox/{threadId}/assist  (SSE) { text } -> the advisor next to the thread
```

### Kept from today

`GET /api/agent/status` and `GET /api/agent/usage`. The calculator endpoints
(`/api/calculator/*`) are removed; their maths lives on as advisor tools.

## 6. Key flows

**Look up a listing**
1. The user pastes a kv.ee link → `lookupListing` makes one request with an honest user
   agent and a 5-second timeout. Only `kv.ee` links; no crawling, no other pages, no
   pages `robots.txt` disallows (images, history, print views, rentals, ...). Results are
   cached per link.
2. It worked → the type, price and location (plus size, rooms, year built) are saved with
   source `LISTING` → `property_updated`, and the advisor carries on.
3. Blocked (kv.ee's bot check) or failed → the tool says so, and the advisor asks the user
   for the type, price and location, or to paste the page text. Values the user states
   are `CHAT` + `CONFIRMED`; values the advisor picks out of pasted text are `CHAT` +
   `UNCERTAIN` until the user confirms them, like document values.

**Upload a document**
1. The user uploads a payslip → `POST /api/documents/PAYSLIP` → status `UPLOADED`.
2. The frontend sends `{ trigger: "DOCUMENT_UPLOADED" }` to the chat.
3. The document reader returns `netIncome: 2350` → saved as `DOCUMENT`, `UNCERTAIN` →
   `profile_updated` (the field shows a "?").
4. The advisor: "I read €2,350 net from your payslip, is that right?"
5. The user says "yes" → `updateProfile` sets it `CONFIRMED` → `profile_updated`, and the
   document is `CONFIRMED`.

**Go to the banks**
1. `checkReadiness()` passes → the advisor says the user is ready and calls
   `showBankPicker()` → the card appears under the chat.
2. The user ticks SEB and LHV → `POST /api/applications` → snapshot frozen.
3. The bank orchestrator runs each bank: the offer calculator decides offer, question
   or decline → the bank agent writes the email → the verifier checks it → it's stored
   through the email gateway.
4. The frontend sends `{ trigger: "APPLICATION_SENT" }` → the advisor: "I sent it to SEB
   and LHV, here's what they said...".

**Negotiate**
1. In the inbox, the user asks the advisor to push for a lower margin → `draftReply`
   puts a draft in the reply box.
2. The user edits it and sends → `POST /reply`.
3. The offer calculator gives this round's lowest allowed margin → the bank agent
   decides and writes a counter-offer (or "final offer") → verifier → returned.

**Accept**
1. `POST /accept` → thread `ACCEPTED`, the bank sends a confirmation ("visit your branch
   to sign"), the other threads become `CLOSED`.
2. `{ trigger: "OFFER_ACCEPTED" }` → the advisor sums up the next steps (notary, down
   payment, home insurance) and offers to draft a polite "no thanks" for each other bank,
   which the user sends. Closed threads accept that last message but get no bank answer.

## 7. Backend package layout (target)

```
com.seb.mortgage
├── auth          users, Spring Security, session login
├── advisor       AdvisorOrchestrator, AdvisorAgent + tools, phases, number check,
│                 DocumentReaderAgent, chat controller, stream events
├── conversation  Conversation, Message
├── profile       ProfileField, LoanProfile + assembler, ProfileFacts
├── property      Property, kv.ee listing reader
├── documents     Document, upload controller
├── bank          BankOrchestrator, BankAgent, bank rules, offer calculator,
│                 applications, inbox controller
├── email         EmailGateway interface, DatabaseEmailGateway (v1),
│                 AgentMailGateway (v2)
├── agent         shared agent base (LlmSpecialistAgent), VerifierAgent, token budget,
│                 stub
├── affordability AffordabilityCalculator, RateService
└── config        CORS, security
```

## 8. Work split

| | **Advisor side**: k4ska | **Frontend**: janmatvejev | **Bank side + platform**: lightbtw |
| --- | --- | --- | --- |
| **Owns** | `AdvisorOrchestrator`, the advisor agent and its tools, phases, the number check, the verifier on chat turns, the test conversations, the document reader, stream events, profile fields, the property and listing lookup, connecting the affordability calculator, chart data | Three-panel layout, login pages, chat streaming, events updating the panels (including showing corrected replies), document upload, bank picker card, inbox + its advisor panel, charts, phone layout | Auth, database setup, application snapshot, email gateway (database, then agentmail), `BankOrchestrator`, bank agents and bank rules, offer calculator, verifier on emails, inbox endpoints |
| **Deletes** | `ExplainAgent`, `ValidateAgent`, `ReviewAgent`, `ChatAgent`, the old `AgentOrchestrator` | Form pages, step sidebar, landing hub | `flow/`, `application/`, the calculator endpoints |
| **Can start without the others by** | Testing tools and the stream without a UI | Faking the stream and endpoints | Testing bank agents with a hand-made snapshot |

### First milestone: the walking skeleton

The thinnest possible version of the whole path, before any part goes deep:

1. Log in → the advisor says "Hi, how can I help you?"
2. The user mentions their income → one tool updates the profile → the field appears in
   the right panel.
3. The bank picker appears → the user picks one bank → an email shows up in the inbox.

Once that runs end to end, every contract has been proven and each person deepens their
own column along the core demo path in [`vision.md`](vision.md).

## 9. Open questions

- The exact numbers in each bank's rules (margins, max LTV, min down payment,
  strictness), so the five banks feel different.
- Where the real 6-month Euribor would come from (or a fixed mock value).
- The phone layout: the side panels probably become tabs or drawers.
