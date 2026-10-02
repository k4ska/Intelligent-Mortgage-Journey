# Agent architecture

This is the detailed reference for how the "agent" assist layer is built. `docs/decisions.md`
has the dated log of *why* we arrived here; this doc is the current-state picture, kept up
to date as the implementation changes. All code referenced lives under
`backend/src/main/java/com/seb/mortgage/agent/`.

## What "agent" means in this codebase

Each agent is deliberately small: **one LLM call (Claude by default, or OpenAI, via Spring AI's `ChatClient`),
wrapped in a narrow system prompt that defines one job, fed only the context it needs for
that job.** There is no autonomous tool-calling loop, no agent that decides on its own to
call other agents or external tools in an open-ended way. The "agentic" behaviour is the
routing between fixed steps, not improvisation within a step - this keeps behaviour
predictable for grading and for a live demo, while still giving the UX benefits (plain
language, judgement calls, follow-up questions) that a single hardcoded rules engine
couldn't.

## The five agents

| Agent | File | Job | Grounded on |
| --- | --- | --- | --- |
| Explain | `ExplainAgent` | Plain-language explanation of a mortgage term/field | The glossary entry from `Explanations.java`, plus the applicant's profile |
| Validate | `ValidateAgent` | Flags anything weakening the application at one step | The applicant's profile only |
| Review | `ReviewAgent` | Final read-back + concerns, at the end of the journey | The applicant's profile only |
| Chat | `ChatAgent` | Free-form Q&A about the applicant's own application | The applicant's profile only |
| Verifier | `VerifierAgent` | Independent check of another agent's draft before it reaches the client | The draft output + the applicant's profile |

All five extend `LlmSpecialistAgent`, a ~10-line base class whose only job is turning a
`ChatClient.Builder` + a system prompt into a `ChatClient`. That's the entire shared
contract between agents - nothing else is shared, on purpose, so each agent's behaviour
is fully explained by its own system prompt and cannot leak into another agent's job.

## Routing and verification: `AgentOrchestrator`

`AgentOrchestrator` implements the `AgentService` interface the rest of the backend calls
(`AgentController` -> `AgentService`). For each request it:

1. Builds the applicant's `LoanProfile` as JSON (the one piece of context every agent
   gets - see "Grounding" below).
2. Calls the matching specialist to get a draft.
3. For **explain / validate / review** (synchronous, structured responses): passes the
   draft's text through `VerifierAgent.verify(taskDescription, draftText, profileJson)`
   and returns the verifier's approved-or-rewritten text instead of the specialist's raw
   output.
4. For **chat**: returns the specialist's stream directly, unverified.

```
Client
  │
  ▼
AgentController  ──────────────────────────────────────────────┐
  │                                                             │
  ▼                                                             │
AgentOrchestrator                                               │
  │                                                              │
  ├─ explain ──▶ ExplainAgent  ──▶ VerifierAgent ──▶ response    │
  ├─ validate ─▶ ValidateAgent ──▶ VerifierAgent ──▶ response    │
  ├─ review ───▶ ReviewAgent   ──▶ VerifierAgent ──▶ response    │
  └─ chat ─────▶ ChatAgent ───────────────────────▶ streamed reply
```

### Why chat skips the verifier

`chat` streams token-by-token over SSE so the user sees words as they're generated -
there's no complete draft to hand the verifier before the first tokens have already
reached the browser. Buffering the whole reply first to verify it would throw away the
one thing that makes chat feel responsive. `ChatAgent`'s own system prompt is the only
safeguard on this endpoint today. If the demo turns up bad chat answers, the fix is
either to accept the latency and verify before streaming, or to verify after the fact and
show a correction - not yet decided (see `docs/decisions.md`, 2026-09-17).

### Fail-open verifier

If the verifier's own call throws (network error, bad response, etc.), `VerifierAgent`
returns the original draft unverified rather than raising an error - a broken verifier
should never take the whole journey down. Each specialist still has its own narrow system
prompt as a first line of defence even when the verifier is unavailable.

## Grounding: the "strict info base"

Our mentor's guidance was to keep the agents on a strict, curated info base rather than
letting them answer from general model knowledge - this keeps answers controllable and
keeps token usage down (small, fixed context beats a model reasoning through everything
it knows). Two grounding sources exist today:

- **`Explanations.java`** - a hand-written glossary (`Map<String, Entry>`) of mortgage
  terms, each with plain-language text and related-topic keys. `ExplainAgent` includes
  the matching entry as "reference wording to stay consistent with" whenever one exists.
- **The applicant's `LoanProfile`**, serialised to JSON - the one source of truth for any
  number an agent states. `ValidateAgent`, `ReviewAgent` and `ChatAgent` are grounded on
  this alone; `VerifierAgent` checks every draft's numbers against it.

**Open follow-up from the mentor conversation, not yet implemented:** tightening
`ExplainAgent` so a topic with no glossary entry returns a fixed "not in our glossary yet"
message instead of letting Claude improvise from general knowledge - this would also
mean zero LLM calls (and zero grounding risk) for anything outside the known vocabulary.
Track this alongside expanding `Explanations.java` to cover every term/field the flow
actually surfaces.

## Follow-up / clarifying questions

Mentor guidance: an agent should be able to ask the applicant something back when it
can't do its job well with what it's been given, instead of guessing. This is implemented
as a **bounded, single-round** mechanism, not an open loop the agent controls:

- `ExplainResponse`, `ValidateResponse` and `ReviewResponse` each carry a nullable
  `followUpQuestion` field (`AgentDtos.java`).
- Each specialist's system prompt instructs it to give its best partial answer *and* set
  `followUpQuestion` to one short clarifying question when the input is too ambiguous or
  incomplete - never to withhold an answer entirely.
- `followUpQuestion` is not verifier-gated; it passes through from the specialist's draft
  unchanged (it's a question, not a claim, so it carries much lower grounding risk than
  the main answer).
- The frontend is expected to show it as a small inline prompt under the agent's answer;
  the applicant's reply is sent back as extra context on a normal follow-up call to the
  same endpoint - **not** as a new open-ended chat thread. (Frontend piece not yet built.)

## Recipe: adding a new agent

Adding a sixth specialist is meant to be mechanical:

1. **New class** in `com.seb.mortgage.agent`, extending `LlmSpecialistAgent`: define a
   `SYSTEM` prompt (one job, stated narrowly) and a constructor that calls
   `super(chatClientBuilder, SYSTEM)`. Add whatever method(s) it needs.
2. **New `@Bean`** in `AgentConfig`, guarded by the same condition every other agent
   bean uses (`@Conditional(LlmConfiguredCondition.class)`), so it only gets built when
   the selected provider (Anthropic or OpenAI) has an API key configured.
3. **Wire it in**: call it from `AgentOrchestrator` (through `VerifierAgent` if its output
   is client-facing prose), and if it's a new client-facing capability, add its
   request/response shapes to `AgentDtos` and a route in `AgentController`. If it's stub-
   able, add a matching branch to `StubAgentService` so the app still runs with no API
   key.

No framework or registry needed for this - the whole point of keeping each agent to one
small class + one bean + one line of routing is that step 3 stays a few-line diff no
matter how many agents exist.

## Token-efficiency notes

- System prompts are short and job-specific rather than one large prompt trying to cover
  every task - fewer input tokens per call, and less chance of one task's instructions
  bleeding into another's behaviour.
- Grounding via a small glossary + the applicant's own (small) profile JSON is far
  cheaper than large reference dumps or letting the model "think through" domain
  knowledge from scratch.
- The planned glossary-only gate on `ExplainAgent` (see above) would skip the LLM call
  entirely for anything outside the known vocabulary - the cheapest possible response is
  the one that never calls the model.
- On OpenAI's GPT-5 family, hidden reasoning tokens were most of each reply's output and
  most of the wait. Each agent's reasoning effort is set separately
  (`app.agent.reasoning-effort.*`, env `AGENT_REASONING_*`): `low` everywhere except the
  final read-back (`medium`). Anthropic ignores the setting.
- The verifier answers with `approved` plus a `rewrite` only when it rejects a draft, so
  an approved draft (the common case) is not generated a second time.
- Verification currently costs one extra call per explain/validate/review request. If
  cost becomes a concern, the cheapest lever is a smaller/cheaper model for the verifier
  specifically (it's a narrow classification-and-rewrite task, not open-ended
  generation) - not yet done, `application.yml` currently points every agent at the same
  `claude-sonnet-5` model.
