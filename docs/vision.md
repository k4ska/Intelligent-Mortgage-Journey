# Vision: a mortgage conversation, not a form

> Settled with the mentor on 2026-10-15 (see `docs/decisions.md`). This document is the
> source of truth for *what* we are building. *How* the pieces fit together is the
> high-level scheme, which comes next and lives in its own doc.

## The idea in one paragraph

Picture someone sitting down at a table with a helpful advisor. They aren't even sure
yet whether they want a loan or could afford one. They just start talking: "Could I even
get a loan?", "I have this apartment in mind", "Can I afford it?". The advisor, our chat
agent, asks simple questions, looks at the listing they bring, works out what they can
afford, and gathers the documents a bank will need. When everything is in place, it
offers to take the application to one or more banks. The banks (also agents) reply by
email, and the advisor helps the user answer, compare and negotiate the offers until
they accept one.

The point of the prototype is **the process and how the agent helps the user through
it**, not a real implementation. Personal data, documents, listings and banks are all
mocked.

## The journey, step by step

1. **Log in.** The user logs in with an email and password. Their progress (conversation,
   profile, documents, bank emails) is saved, so they can come back later. A new user
   starts from scratch.
2. **The agent opens the conversation** with: *"Hi, how can I help you?"* The user
   answers in their own words. There are no starter buttons and no form.
3. **Getting to know the user.** Through natural conversation the agent asks simple
   questions: what are you looking for, do you have something specific in mind, what do
   you earn, do you have savings for a down payment, are you buying alone or with
   someone, do you have other loans. Every fact it learns appears in the profile panel on
   the right.
4. **The property.** At any point the user can paste a kv.ee link. The agent "reads" the
   listing and fills in the property panel: price, size, rooms, location, and monthly
   building costs where available. (We use mock listings; see Scope.)
5. **Can I afford it?** The agent checks affordability with the existing affordability
   rules (net income, the Eesti Pank-style 50% payment limit, the stress test, living
   costs and credit limits). It explains the result in plain language:
   - if the property is too expensive, it explains why and describes what kind of
     property the user could afford (price range, size, area), and the user can switch
     to a different listing;
   - if there's room, it says so too.

   Only **one property is active at a time**, but the user can swap it as often as they
   like.
6. **Graphs when they help.** The agent can show charts in the chat. The main one
   shows how changes in the interest rate (Euribor plus the bank's margin) affect the
   loan amount and the monthly payment. The agent explains Euribor, why it moves, and what
   that means for the user.
7. **Documents.** The agent tells the user which documents a bank will need and **where
   to get each one in real life**. The user uploads them in the documents panel on the
   left.
   - **Any file works.** The agent "reads" it, makes up believable values that match what
     the user already said in the chat, and **asks the user to confirm them** ("I read
     €2,350 net salary from your payslip, is that right?").
   - Confirmed values fill in the profile panel.
   - If the user is only exploring and doesn't have the documents, the agent explains
     which ones will matter later, and the conversation carries on without them.
8. **Ready for the bank.** When the agent has everything a bank application needs, it
   tells the user they're ready and asks if they'd like to go to the bank now.
9. **Choosing banks.** The user picks one bank or several (to compare offers). The banks
   are the real Estonian banks: SEB, Swedbank, LHV, Luminor and Coop Pank. Each one is
   played by a bank agent.
10. **The inbox.** Each bank agent replies **straight away** in the inbox tab. A bank may
    first ask for a missing document or a clarification, then sends an offer (amount,
    rate as Euribor + margin, term, monthly payment, conditions).
11. **Replying with the advisor's help.** The user answers each email in the inbox. The
    advisor helps them: explains the offer, compares offers from different banks, drafts
    replies and helps negotiate (for example asking for a lower margin). The bank agent
    responds to the negotiation.
12. **Accepting an offer.** The user accepts one offer. The bank agent sends a
    confirmation ("please visit your branch to sign the contract"), and the advisor sums
    up the next steps in real life: the notary, paying the down payment, home insurance.
    **The demo ends here.**

## The screen

```
┌──────────────────────────────────────────────────────────────────────────┐
│  [ Chat ]  [ Inbox (2) ]                                      user ▾     │
├───────────────┬──────────────────────────────────┬───────────────────────┤
│  DOCUMENTS    │              CHAT                │  YOUR PROFILE         │
│               │                                  │  Net income  €2,350 📄│
│  ✓ ID card    │  Agent: Hi, how can I help you?  │  Savings     €30,000 💬│
│  ✓ Payslips   │  You:   Could I even get a loan? │  Co-applicant  none 💬│
│  ○ Bank       │  Agent: Let's find out. What...  │  ...                  │
│    statement  │                                  │───────────────────────│
│  ○ Valuation  │  [chart: rate vs. monthly        │  THE PROPERTY         │
│    report     │   payment]                       │  2-room, Kalamaja 🏠  │
│  [ Upload ]   │                                  │  Price  €189,000      │
│               │  [ type a message...       ]     │  Building costs €95/mo│
└───────────────┴──────────────────────────────────┴───────────────────────┘
```

- **Left, documents panel.** The documents a bank will need, each with its status (not
  needed yet / needed / uploaded / confirmed), an upload button, and a short note on
  where to get it in real life.
- **Middle, chat.** The conversation with the advisor, including charts.
- **Right, profile and property.** Filled in **automatically** from the conversation,
  the listing and the documents.
  - Every value shows **where it came from**: the chat, the listing, or a document.
  - Values the agent isn't sure about are **marked as uncertain** until the user confirms
    them.
  - The user **corrects values through the chat** ("no, my salary is actually €2,400"),
    not by editing the fields.
- **Inbox tab.** The email threads with the banks, with the advisor available next to
  each thread to explain and draft replies.
- A visible note says: **"Prototype. Not affiliated with any bank. Offers are
  simulated."**

## Documents the agent asks for

What it's called (in Estonian), and where you'd get it in real life. The agent adapts the
list to the user (for example, co-applicant documents only if there is a co-applicant).

| Document | Where to get it in real life |
| --- | --- |
| ID card or passport (*isikut tõendav dokument*) | Your own ID card or passport |
| Proof of income: payslips or an income statement | Your employer, or the Tax and Customs Board (EMTA) e-services |
| Bank statement (*konto väljavõte*), last 6 months | Your internet bank |
| Proof of down payment funds | Your internet bank (savings account statement) |
| Valuation report (*hindamisakt*) | A bank-approved licensed valuator, ordered after you pick the property |
| Purchase agreement or reservation (*broneerimisleping*) | The seller or the real estate agent |
| The same income and ID documents for a co-applicant | The co-applicant |

## Scope

### In scope
- Buying a home, with an **optional co-applicant**.
- One active property at a time, which the user can switch.
- Several banks at once, with offers to compare.
- Login with email and password; progress is saved per user.
- **English** for now. Estonian is a planned later addition, so don't hard-code text in a
  way that makes translation hard.

### Mocked (on purpose)
- **Personal data and documents.** Any uploaded file is accepted; the agent invents
  matching values and the user confirms them.
- **kv.ee listings.** A set of prepared mock listings (realistic price, details, photo,
  building costs) behind a few kv.ee-style links. A link we don't recognise gets: "I
  couldn't read that listing. Can you tell me the price and a few details?"
- **Banks.** Real Estonian bank names, but every reply, offer and decision comes from our
  bank agents.
- **Email.** At first the bank emails are messages stored in our own database and shown in
  the inbox tab. Once the core works, we switch to **agentmail.to** (the mentor's
  suggestion) so real emails are sent between agentmail addresses. The switch should only
  replace the email-sending part, not the chat or the bank agents.
- **Euribor.** The real current 6-month Euribor if we can get it easily, otherwise a fixed
  mock value. Either way the charts show "what if" scenarios.

### Out of scope
- Smart-ID, Mobile-ID or any real identity check.
- Applying through real bank websites or APIs, and real credit decisions.
- Sending emails to real banks.
- Refinancing an existing loan.
- KredEx guarantees.

## Core demo path and extras

The plan is big for three people, so it's split in two. The **core path** must work end
to end before any extra is started. With only the core done, we still have a complete
story to demo.

**Core path**
1. Login, and the saved conversation.
2. Chat with the advisor, which fills in the profile panel (with sources).
3. Pasting a mock kv.ee link fills in the property panel.
4. An affordability answer in plain language, with the "you could afford something like..."
   advice.
5. Document upload in the left panel, values confirmed in the chat.
6. "You're ready, shall we go to the bank?" and then choosing one or more banks.
7. The bank agent in the inbox: asks for something missing, then sends an offer.
8. The user replies with the advisor's help, accepts an offer, and gets the confirmation and next steps.

**Extras** (in rough priority order)
1. Charts in the chat (interest rate / Euribor scenarios).
2. Negotiating with the bank agent.
3. Comparing offers side by side.
4. agentmail.to for real emails.
5. The real Euribor value.
6. Estonian language.

## What we keep from the form-based version

The backend foundations carry over. The scheme will decide exactly how:

- Spring Boot, Spring AI (OpenAI), Docker, the token budget and usage tracking.
- The **multi-agent setup**: an orchestrator, specialist agents and an independent
  verifier. The bank agents and the document reader become new specialists.
- `AffordabilityCalculator` and its Estonian-style rules, `RateService` and
  `BankCatalogService` (moved to the real bank names).
- `LoanProfile` as the shared state that the chat fills in and the banks receive.

The multi-step form, the flow definition (`flow/`), the step-roadmap sidebar and the form
pages are dropped.

## Open questions

These will be answered in the high-level scheme or along the way.

- Which kv.ee listings to prepare as mocks, and how many (3–5 across price ranges seems
  right).
- Exact rules for each bank agent (margin, max LTV, how strict it is, how far it goes in
  negotiation), so the banks feel different.
- Where the real Euribor value would come from, if we use one.
- How the profile, documents and inbox panels look on a phone (the panels likely become
  tabs or drawers).
