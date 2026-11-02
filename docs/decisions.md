# Decision log

Short, dated entries. Newest first. One entry per decision that would otherwise get
re-litigated.

## 2026-10-30 - Negotiating the margin with the banks

- Only the margin is negotiated; the amount and term stay. Each ask lowers it one step
  (`OfferCalculator.lowestMargin` for the round, in each bank's rules), mentioning another
  bank's offer goes to the bank's lowest margin at once, and the lowest is "our final
  offer". Asking again gets "this is the best we can do".
- A small `NegotiationAgent` (minimal reasoning) only classifies the reply: asks for a
  better offer or not, mentions another bank or not. It never sets numbers. Without an
  API key, or if it fails, keywords decide.
- The bank's email is a template in code, checked by `BankEmailVerifier`, not written by
  the model: for a demo, reliable beats varied.
- A counter-offer is an `offer` email, so accepting it works as before; only the newest
  offer in a thread can be accepted (409 for an older one).

## 2026-10-30 - The documents panel is back, with one-click sample uploads

- Documents stay optional, but the panel is visible again with every document. Each one has
  an Upload button that sends a stand-in file (nobody reads it; the document reader makes
  up the values), and the advisor reads them back for the user to confirm as before.
- `POST /api/documents/{type}` accepts a document the advisor never asked for, so the 409
  "hasn't asked for this document yet" is gone.

## 2026-10-30 - The user writes their own reply to a bank

- The inbox reply box is a text field again, and `POST /api/inbox/{threadId}/reply` stores
  the user's `body` (400 if blank) instead of a fixed confirmation. The advisor's
  `draftReply` (#80) fills that box, and the user edits and sends it. The bank still
  answers straight away with its fixed confirmation.

## 2026-10-30 - Documents are optional before applying

- The prototype no longer shows the documents panel from the start and does not require
  ID, passport, payslip, bank statement or proof of down payment before sending an
  application. Users can skip the document stage and continue to the banks.
- Document upload and reading code remains available for a later milestone, but it is not
  part of the pre-application readiness gate.

## 2026-10-30 - Deterministic bank emails and stored offers

- Bank email wording is generated from the calculator decision without an LLM in stub
  mode. A deterministic verifier compares every numeric token in an offer email with the
  calculator's amount, term, rates, payment and validity date; a mismatch is replaced by
  a safe template.
- Offers are stored as JSON on the email and returned by the inbox detail endpoint. New
  offer and decline emails update the thread status and unread flag. Migration V6 adds
  the nullable offer column; V5 remains available for the advisor-thread changes.

## 2026-10-24 - The advisor next to a bank thread (#80)

- `POST /api/inbox/{threadId}/assist` lives in the advisor package
  (`InboxAssistController`), not in `bank.InboxController`: the bank package may not
  call the advisor. It runs the same turn as the chat, in the same conversation; both
  messages get the thread's id (`message.thread_id`, null in the chat), and the advisor
  reads them as `[next to the email thread with LHV] ...`.
- The advisor reads the user's bank emails through `EmailGateway` (new read methods),
  the one place the two sides meet. Once the user has applied, the threads (the last 6
  emails each) are in the JSON the advisor gets each step, so the number check and the
  verifier accept figures from the banks' emails.
- `draftReply(body)` has no thread id: it drafts for the thread the user is asking next
  to, so a draft can't land in another bank's reply box. It is only offered in inbox
  turns at the banks; in the chat the advisor says to ask next to the thread. A draft
  must pass the number check (figures from the bank's emails, the profile or the user's
  words), then the verifier checks the tone; a rejected draft is replaced by its rewrite.
  Accepted and declined threads get no drafts; closed ones do (the last "no thanks").
- The `APPLICATION_SENT` and `OFFER_ACCEPTED` triggers each add a `system` note to the
  conversation, like an upload. `OFFER_ACCEPTED` needs the thread to be `ACCEPTED` (409
  otherwise); that status comes with #78.

## 2026-10-21 - The application snapshot has the property and documents (#86)

- `Application.snapshotJson` is `{ fields, property, documents }`, the same shape as
  `OfferCalculator.Snapshot`. `property` is `null` when the user has none; `documents` are
  `{ type, status }`, only `UPLOADED` and `CONFIRMED` ones. A document that was only asked
  for (`NEEDED`) isn't something the user sent, and a needed valuation would otherwise make
  the bank ask for it instead of making it an offer condition.
- `bank.ApplicationSnapshots` freezes and reads it. It reads the property and documents
  through `PropertySnapshotProvider` and `DocumentSnapshotProvider`, like the profile, so
  the bank package still imports nothing from the advisor side.
- Applications sent before this stored only the list of profile fields. They still read,
  as fields with no property and no documents; no migration.

## 2026-10-21 - Advisor test conversations (#74)

- Scenarios live in `backend/src/test/java/com/seb/mortgage/advisor/scenarios/`, tagged
  `eval`. `./gradlew test` skips them; `./gradlew advisorEval` runs them against the real
  model (needs `OPENAI_API_KEY`), with `-Pruns=N` to repeat each one. It prints a pass rate
  per scenario and writes it to `backend/build/advisor-eval.md`; a failure prints the whole
  conversation.
- They run in-process: the orchestrator's `send` and `documentUploaded` are the same calls
  the chat endpoint makes, and an upload goes through `DocumentService` like the upload
  endpoint. To skip the first turns, a scenario can set the property and mark affordability
  as discussed.
- What a turn did is read from what we already have: the events, the profile and document
  rows, and the advisor's own "tools [...]" log line. A `corrected` event counts as the
  advisor getting something wrong (an invented number, a promise).
- A failing scenario doesn't fail the build: the pass rate is the result. Results to
  compare against are in `docs/advisor-eval.md`; add a row there after a prompt change.

## 2026-10-21 - Why each document is needed, and honest tips

- Each `DocumentType` has a one-line `why` (what the bank uses it for) and `tips` (how to
  make it count). Both are in `GET /api/documents`, the `document_status` event and the
  `requestDocument` note, so the panel can show them and the advisor reads them from our
  text instead of making its own up.
- Tips are only about real preparation and being open with the bank: in the months before
  applying, avoid new consumer loans, stay out of overdraft, close unused credit cards
  (banks count the limit), keep the down payment in your own account, and have an
  explanation ready for large or unusual transfers or a family gift.
- Never suggesting changes to a document is a rule in the advisor's prompt and in the
  verifier's checks. The verifier only sees flagged turns (an unsourced figure or a money
  tool), so the prompt is the main guard; #74's test conversations should include a user
  who asks to delete transactions from a statement.
## 2026-10-21 - Phases in code and checkReadiness (#73)

- `Phases` works out the phase from the stored data, first match wins: **at the banks** (an
  application exists), **ready** (`checkReadiness` passes), **documents** (affordability
  was discussed, or a document was already asked for), **getting to know you** (income,
  savings, co-applicant or other loans missing), **property** (no property yet),
  **affordability** (otherwise).
- "Affordability discussed" is a timestamp on the conversation, set the first time
  `checkAffordability` or `suggestAffordableRange` returns a real result (not "can't tell
  yet"). Start over deletes it with the conversation.
- The phase is read again before every act round, so one turn can still save the last key
  fact, then check affordability, then ask for documents (#94). A tool the model calls
  outside its phase isn't run; it gets "isn't available yet, at this stage".
- Every act and reply call gets a note: the phase, its focus, and, before **ready**, what
  is still needed for the banks. So asking for the banks too early gets a kind "not yet"
  with what is missing, without the bank picker even being offered.
- The reply call keeps the focus of the phase the turn **started** in (with fresh
  readiness). In a live run, the reply right after an affordability result was told to
  "ask for documents", had no tools to do it, and wrote a made-up list with
  "(requestDocument)" in it. Now that turn explains the result, and the next one asks.
- Tools per phase (cumulative):

  | Phase | Tools added |
  | --- | --- |
  | Getting to know you | `updateProfile`, `lookupListing`, `updateProperty`, `explainTerm` |
  | Property | `checkAffordability`, `suggestAffordableRange` |
  | Affordability | (same) |
  | Documents | `requestDocument`, `confirmDocument`, `checkReadiness` |
  | Ready | `showBankPicker` |
  | At the banks | (same; `draftReply` comes with the inbox) |

- `checkReadiness` needs: every fact the affordability check uses, confirmed; a property
  with type, price and location, confirmed; and **ID, payslip, bank statement and proof of
  down payment** confirmed, plus the co-applicant's ID and payslip with a co-applicant. A
  value read from a document counts only once the user confirmed it. The **valuation and
  purchase agreement are not required**: banks make their offer on condition of them, so
  the advisor may ask for them once a home is picked, but they don't hold up the
  application.
- `showBankPicker` checks readiness itself too, replacing the skeleton's "income is known"
  rule (#50).
- Only a document the advisor asked for can be uploaded (409 otherwise). An early upload
  couldn't be confirmed, since `confirmDocument` belongs to the documents phase, and it
  would let the user skip the affordability conversation.

## 2026-10-20 - Documents and the document reader (#72)

- A `Document` row per user and type (`NEEDED | UPLOADED | CONFIRMED`) keeps only the file
  name; the checklist shows `NOT_NEEDED_YET` for documents nobody has asked for.
  Co-applicant documents only apply with a co-applicant.
- The document reader copies a value the user already gave exactly, and asks the LLM only
  for the missing ones (checked: a usable number, at most 1,000,000, income above 0). So a
  read value can never contradict the chat. Without an API key nothing is read, not even
  the copies, and the turn gives the offline reply.
- Read values are saved as `DOCUMENT` + `UNCERTAIN` with the document type, even when they
  copy a confirmed chat value: the user confirms what the document says, as in the vision.
  Until then the affordability check counts them as unconfirmed.
- "Yes" goes through a new `confirmDocument` tool, not `updateProfile`: a made-up value was
  never written by the user, so the quote rule would refuse it. The quote has to come from
  the user's latest message, so an old "yes" or the upload turn itself can't confirm
  anything. A correction is an ordinary `updateProfile` save, and a document whose read
  values are all confirmed or corrected becomes `CONFIRMED`.
- The upload turn is the advisor's usual turn, answering a system note about what was read
  instead of a user message. The note is stored as a `system` message, so later turns know
  about the upload.
- `requestDocument` takes all the types in one call, and its description lists the only
  documents there are. In a live run with one type per call, the advisor asked for two and
  then described other documents in its own words ("last 3 months", "employment contract").

## 2026-10-20 - Number check and verifier on flagged turns (#67)

- After each reply, `NumberCheck` finds every € amount and % (`€2,350`, `2 350 €`,
  `210k`, `3.5%`) and looks for it, within 1%, in the turn's tool results, the profile and
  property, the user's messages and the advisor's earlier replies. Earlier replies count
  because they already passed this check, so the advisor can repeat itself.
- The turn goes to the verifier when a figure comes from nowhere or when it called
  `checkAffordability` or `suggestAffordableRange`. The verifier gets the reply, the
  figures that failed, and the profile, property, tool results and user messages as facts.
  If it rejects the reply, its safe version replaces the saved message and a `corrected`
  event is sent before `done`. Other turns end without the extra call.
- It fails open, like the verifier already did: an error in the check or the verifier
  keeps the reply that was shown.
- The frontend replaces the reply's text on `corrected`; marking it as corrected is #69.

## 2026-10-20 - Up to three act rounds per advisor turn (#94)

- A turn with tools may act up to 3 times before the reply: each later round sees the
  earlier calls and what they returned (as tool messages, without the text written next to
  them), so the advisor can save a fact and then check affordability with it in one turn.
  The reply call is unchanged from #90: no tools, no tool messages, just a note of all tool
  results, so the doubled replies and notes to itself can't come back.
- Measured by replaying two conversations 5 times each against gpt-5-mini (same prompt):
  one round reached a full affordability answer in 0 of 5 runs, three rounds in 5 of 5,
  with 0 broken replies in 40 turns either way and no invented values. The cost is about
  2.7 act calls per turn with tools: roughly twice the tokens per conversation (14k to 28k).
- The prompt no longer forbids saving 0 when the user says they have none ("no other
  loans"): that answer is a value the user gave, and the code already accepted it.
- `showBankPicker` is only called when the user asks to apply; later rounds once called it
  unasked.

## 2026-10-20 - Affordability tools (#65)

- `checkAffordability` and `suggestAffordableRange` need every fact the calculator uses:
  net income, savings, monthly debt payments, credit card limits, co-applicant (and their
  income if yes), dependants and monthly living costs, plus the property price for the
  check. A missing or uncertain value is reported and asked for, never assumed (rule 2):
  no debts or limits until the user says "none". Living costs still count at least the
  household minimum, as banks do.
- The checks assume the variable rate plus the banks' middle base margin (4.35% + 1.75% =
  6.10%) over 25 years, so the advisor and the bank offers judge a loan almost the same
  way. The range also shows the 30-year maximum, the longest term Eesti Pank allows.
- `creditCardLimits` and `monthlyLivingCosts` are new profile keys; `OfferCalculator` counts
  them too, so the banks see the same costs as the advisor.
- "What you could afford instead" gives the price range and, when the listing has a size,
  the m² it buys at that listing's price per m². A cheaper area is described in words
  only: we have no price data per area.

## 2026-10-19 - Prototype bank rules and offer terms

- Bank terms are deterministic seed data, not real bank policy: SEB, Swedbank, LHV and
  Luminor use 85% maximum LTV and 15% minimum down payment; Coop Pank uses 90% and 10%.
- Base margins range from 1.65% to 1.85%, with negotiation floors from 1.35% to 1.55%.
  Every bank allows two negotiation rounds. Swedbank and Coop are relaxed, SEB is
  standard, and LHV and Luminor are strict; strictness affects bank-agent tone and is
  not a hidden change to the calculator's numbers.
- The calculator offers the requested property price minus savings when it passes the
  bank's LTV and the shared affordability rules. It declines insufficient down payment,
  LTV or affordability, and asks a question when required snapshot facts are missing.
  Offers use the variable rate plus the bank's base margin, 25 years, annuity payments,
  and a 30-day validity period. These are prototype values for the demo.

## 2026-10-19 - PostgreSQL persistence in Docker

- Docker Compose uses PostgreSQL by default, with a named volume for users, conversations,
  profiles, properties, applications and email threads to survive backend restarts.
- Flyway owns the schema in every environment; Hibernate validates it instead of creating
  or dropping tables. Local development and tests keep using in-memory H2 with the same
  migrations.
- `docker compose down -v` is the explicit clean-start operation and removes the local
  PostgreSQL volume.

## 2026-10-16 - Session auth and prototype CSRF

- Authentication uses Spring Security's server-side session cookie and BCrypt password
  hashes; JWT is intentionally out of scope for the prototype.
- CSRF is disabled because the SPA is same-origin behind the development/API proxy. It
  must be revisited before exposing the backend outside that deployment shape.

## 2026-10-15 — Real kv.ee listings, best effort; the user fills the gaps

- **Reverses the mock listings** from the vision (2026-10-15 pivot). When the user pastes
  a kv.ee link, `lookupListing` makes one honest request for that page and reads its
  schema.org structured data. If that fails, the advisor asks the user.
- **Why it's allowed**: kv.ee's terms don't mention scraping, and its `robots.txt` allows
  listing pages for generic bots and explicitly allows AI assistants fetching a page for
  a user (`ChatGPT-User`, `Claude-Web`, ...). It only blocks AI *training* crawlers
  (`GPTBot`, `CCBot`, `Google-Extended`, ...). Our use, one page because the user asked,
  nothing kept for training, is on the allowed side.
- **Why it's only best effort**: kv.ee runs a bot check. With an honest user agent, one
  test listing loaded (a house in Haabneeme: price 549 000 EUR, type, address, 251.7 m²,
  7 rooms, built 2001, all in the structured data); the next listing got a 403 challenge
  page. **We never try to get around the bot check** (no faked browser, no solving the
  challenge).
- **Rules for the fetch**: only `kv.ee` links, one request per pasted link, an honest
  user agent, a 5-second timeout, cached per link; no crawling, no other pages, no image
  pages (`robots.txt` disallows them). Prices are read in code from the structured data,
  never by the LLM from raw HTML.
- **What the property needs**: **type** (apartment, house, plot, new build), **price**
  and **location**. Size, rooms and year built are kept when the listing provides them,
  but the advisor never asks for them.
- **When the link can't be read**: the advisor asks for the type, price and location, or
  for the page text pasted into the chat. Values the user states are `CONFIRMED`; values
  the advisor picks out of pasted text are `UNCERTAIN` until the user confirms them.
- **Monthly building or upkeep costs are dropped**: most kv.ee listings don't show them.
  Affordability still counts the household's living costs.
- **Later, if needed**: reliable listing data would need kv.ee's cooperation (an API or
  partner access), something the mentor or SEB could ask about.

## 2026-10-15 — Advisor skeleton (#50)

- **One endpoint per turn**: `POST /api/chat/messages` streams SSE events; the advisor's
  tools push `profile_updated` and `bank_picker` into the same stream while the reply is
  still being written. Tools are passed per request (`.tools(...)`), with one tools object
  per turn, so a tool can only act for the user who's talking.
- **Memory**: every turn sends the last 40 messages plus the profile fields (as JSON) to
  the advisor.
- **A fixed list of profile keys** (`ProfileKey`): the advisor can't invent new ones.
  Values are stored in one plain form (`"2350"`, `"yes"`), and "2,350" is rejected
  rather than guessed (it means 2.35 in Estonian). Facts the user states in the chat are
  saved as `CHAT` + `CONFIRMED`.
- **Skeleton readiness rule**: `showBankPicker` only works once `netIncome` is known;
  the real `checkReadiness` comes later.
- **Current user**: a `CurrentUser` interface; until auth (#49) provides the real one,
  `DevCurrentUserConfig` makes every request one demo user. It backs off as soon as #49
  adds its own `CurrentUser` bean. Users are identified by a `UUID`.
- **For the bank side**: `ProfileSnapshotProvider.snapshot(userId)` returns the profile
  fields, so the bank code never touches the advisor's tables.
- **Messages** get an increasing numeric id, which is also their order (two messages can
  share a timestamp).
- **Without an API key** (or once the budget is spent), the turn streams "The assistant
  is offline. Please add an API key."
- `LlmSpecialistAgent`, `LlmConfiguredCondition` and `AgentConfig.withReasoningEffort`
  are now public so agents can live in their own packages. New setting:
  `AGENT_REASONING_ADVISOR` (default `low`).

## 2026-10-15 — Form-based code removed (#48)

- **Deleted in one go**, before the walking skeleton, instead of each person deleting
  their own part: the old orchestrator used the old application code, so separate
  deletions would have broken each other's builds.
- **Backend**: `flow/`, `application/`, the explain/validate/review/chat agents and
  endpoints, `AgentOrchestrator`, `AgentService`, `AgentDtos`, `StubAgentService` (all
  its methods were form tasks; #50 adds the canned "offline" reply), the calculator
  endpoints, `PropertyValuationService`, `DocumentChecklistService` and
  `LoanProfileAssembler`. `ProfileFacts` lost its per-step view.
- **Kept**: the verifier, the token budget, per-agent reasoning effort (only the
  verifier's key is left in `application.yml`), the `Explanations` glossary,
  `AffordabilityCalculator` (its "how much can I borrow?" tests now test it directly),
  `RateService`, `BankCatalogService`, `LoanProfile` and `ProfileFacts`.
- **Agent mode**: `GET /api/agent/status` now reads a small `AgentMode` bean (`openai`
  with a key, `stub` without) instead of `AgentService.mode()`.
- **Frontend**: the journey page, landing hub, calculators, assist panel, flow store and
  their types are gone; `index.vue` is a placeholder until #52. `utils/numbers.ts` stays
  for euro formatting. The old SSE reader in `useApi.ts` is in git history if #52 wants
  it as a starting point.

## 2026-10-15 — High-level scheme for the chat-based advisor

The full scheme is in **[`docs/architecture.md`](architecture.md)**, which replaces
`docs/agent-architecture.md`. The decisions in it:

- **The advisor uses tools** (Spring AI tool calling): `updateProfile`, `lookupListing`,
  `checkAffordability`, `showChart`, `requestDocument`, `explainTerm`, `checkReadiness`,
  `showBankPicker`, `draftReply` and others. **Reverses 2026-09-08**: agents were one LLM
  call with no tools. Numbers still come from our code, never from the model.
- **Two orchestrators** with a wall between them: `AdvisorOrchestrator` (advisor +
  document reader, works for the user) and `BankOrchestrator` (bank agents + verifier,
  works for the banks). They only meet at the email gateway; banks never see the chat.
- **One stream per advisor turn** (SSE) carries both the reply text and events that
  update the panels live (`profile_updated`, `property_updated`, `document_status`,
  `chart`, `bank_picker`, `draft_reply`). Uploads, sending applications and accepting
  an offer come back into the chat as **triggers** on the same endpoint.
- **Keeping one advisor honest**: since one agent now does the jobs the specialists
  used to do, four layers keep it on rails (section 3 of the architecture doc):
  1. **Phases** worked out in code from the stored data decide the advisor's focus and
     which tools it gets.
  2. A plain-code **number check** after every turn makes sure each € amount and % in
     the reply came from a tool, the profile or the user.
  3. The **verifier checks flagged chat turns** (the number check failed, or the turn
     talked about money) after streaming. A rejected turn is replaced via a
     `corrected` event. **Reverses 2026-09-17**, where chat was never verified: chat
     is now the whole product, not a side panel.
  4. About 20 **test conversations** (`./gradlew advisorEval`, kept out of the normal
     test run) measure how often the advisor gets it right.
- **The verifier** is shared by both sides: flagged chat turns, bank emails (do the
  numbers match the offer and the rules?) and the advisor's drafted replies.
- **Stub**: a canned "assistant is offline" reply; no scripted fake conversations.
- **The advisor is one brain in two places**: the main chat and the inbox assist write
  to the same conversation (inbox messages tagged with their thread).
- **Bank agents**: one class with five configs (real bank names). Code decides
  eligibility, the offer and the lowest margin per negotiation round; the agent writes
  the email within those limits. Banks can decline; at most 2 negotiation rounds by
  default; banks answer straight away (the reply call waits).
- **The user stays in control**: confirms document values, picks banks on a picker card
  under the chat, and sends every email. The advisor only drafts.
- **Data**: one conversation per user plus "start over"; profile stored one row per fact
  (value, source, status); uploaded files are not stored (name only); the application
  is a frozen snapshot with no updated re-sends; accepting an offer closes the other
  threads, and the advisor offers to draft a "no thanks" to each.
- **Auth**: Spring Security with a session cookie, not JWT.
- **Removed**: `ValidateAgent` and `ReviewAgent` (readiness is a plain-code check), the
  flow definition, and the `/api/calculator/*` endpoints (their maths become tools).
- **Work split**: k4ska takes the advisor side, janmatvejev the frontend, lightbtw the bank
  side and platform. The first milestone is a **walking skeleton** through the whole
  path.

## 2026-10-15 — Pivot: a chat-based advisor replaces the form

- **Why**: after a meeting with the mentor, the project takes a 180° turn. Instead of a
  guided form with agent help on the side, the whole journey is a **conversation** with
  an advisor agent, like sitting down with a person. The full vision is in
  **[`docs/vision.md`](vision.md)**.
- **What the product is now**: the user talks to the advisor, which asks questions,
  reads a (mock) kv.ee listing, checks affordability, collects documents, and then takes
  the application to one or more real Estonian banks. The banks are played by **bank
  agents** that reply by email in an in-app inbox; the advisor helps the user answer,
  compare and negotiate the offers until they accept one.
- **Layout**: documents panel on the left, chat in the middle, profile and property on
  the right (auto-filled, with the source of each value and uncertain values marked).
  The inbox is its own tab. The user corrects the profile through the chat only.
- **Settled details**:
  - The agent opens with "Hi, how can I help you?"; no starter prompts.
  - Any uploaded file is accepted; the agent makes up matching values and asks the user
    to confirm them, and explains where to get each document in real life.
  - One property at a time, which can be switched; the agent suggests what the user
    could afford if it's too expensive.
  - Real bank names (SEB, Swedbank, LHV, Luminor, Coop Pank), with a visible "Prototype,
    not affiliated with any bank" note. Bank agents reply straight away; they may ask
    for something missing before sending an offer, and they can negotiate.
  - Email starts as messages in our own database; **agentmail.to** (mentor's suggestion)
    replaces that once the core works, behind the same interface.
  - Login with email and password so progress is saved per user.
  - Purchase with an optional co-applicant. English first, Estonian later. Real 6-month
    Euribor if easy, otherwise mocked.
  - **Out of scope**: Smart-ID or real identity checks, real bank applications or
    emails, refinancing, KredEx.
- **Core path vs. extras**: the vision splits the work into a core demo path that must
  work end to end first, and extras (charts, negotiation, offer comparison, agentmail,
  real Euribor, Estonian).
- **Dropped**: the multi-step form, the flow definition schema (`flow/`), the
  step-roadmap sidebar, the landing hub, and the two demo personas (`docs/personas.md`
  is deleted). The entries below describe the form-based version and are kept as
  history.
- **Kept**: Spring Boot + Spring AI (OpenAI), the multi-agent orchestrator + verifier
  pattern, the token budget, `AffordabilityCalculator` and its rules, the rate and bank
  services, and `LoanProfile` as the shared state.
- **Next**: a high-level scheme of the whole application (components, agents, data and
  the split of work between k4ska, janmatvejev and lightbtw), before any issues or code.
  `docs/agent-architecture.md` and the README's API contracts will be rewritten from it.

## 2026-10-12 — Living costs and credit limits count in affordability (#32)

- **New fields**: `dependants` ("Who is applying?", asked once per household),
  `totalCreditLimits` (income step) and `coApplicantCreditLimits` (co-applicant step), so
  both applicants' limits count, and optional `monthlyHouseholdCosts` (income step,
  everyday costs excluding rent and loans).
- **Living costs** = what was entered, but never below a minimum of **€300 per adult +
  €200 per dependant** a month. They come off the stress-test buffer
  (`net income − debts − living costs − stressed payment`), not the 50% DTI, which
  stays about loan payments.
- **Credit limits** count as **3% of the total limit a month** of debt, used or not, in
  DTI, stressed DTI and the buffer.
- **Prototype values, unverified**: no Estonian bank publishes its minimum living costs
  or credit-limit share (SEB only says family size matters), so these are named constants
  on `AffordabilityCalculator` to swap for real figures.
- `LoanProfile` gains `household` and per-applicant `creditLimits`; `Financials` gains
  `monthlyLivingCosts` and `creditLimitMonthlyCost`. Affordability figures now wait for
  dependants and credit limits; household costs are optional and never block them.
- The max-loan calculator takes the same inputs behind a collapsed "Household and credit
  cards" section; left closed, the minimum living costs still count.

## 2026-10-12 — Affordability on net income, Eesti Pank-style limits (#31)

- **Net, not gross**: income fields ask for monthly net income (after tax), because that's
  what Estonian lenders assess. `monthlyGrossIncome` is now `monthlyNetIncome`, and
  `LoanProfile.applicants[].monthlyGrossIncome` is now `monthlyNetIncome`.
- **Limits follow Eesti Pank's borrower rules for housing loans**, checked against Eesti
  Pank and ESRB sources: all loan payments may take at most **50% of net income**, worked out
  at a **stressed rate of the contract rate + 2 pp, at least 6%**. Constants on
  `AffordabilityCalculator`: `MAX_DTI_PCT = 50`, `STRESS_DELTA_PCT = 2`, `STRESS_RATE_FLOOR_PCT = 6`.
  The 15% exemption quota and the 30-year maturity cap are not modelled.
- **Stress test**: FAIL when stressed payments are above 50% of net income; TIGHT when less
  than €300 is left after debts and the stressed payment (`MIN_STRESS_BUFFER`, kept for
  #32's living costs; with a 50% cap it only bites on very low incomes); otherwise PASS.
  `Financials` gains `stressedDti`, the figure the limit applies to; `dti` stays the
  ratio at today's rate.
- **Self-employed income is last year's net profit ÷ 12** (team decision). The flow hides
  monthly net income for self-employed applicants via a new field-level `showIf`, and the
  backend drops a hidden field's answer like it drops off-path steps.
- The max-loan calculator (#29) uses the same constants, so it moved with the journey;
  LTV (85%, 90% with KredEx) is left to #33.

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

See the "Open questions" section of [`docs/vision.md`](vision.md). The two earlier ones
are settled: we model Estonian rules (2026-10-12), and the multi-bank idea became bank
agents with an inbox (2026-10-15).
