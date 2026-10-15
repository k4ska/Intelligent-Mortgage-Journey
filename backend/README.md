# Backend — Intelligent Mortgage Journey

Spring Boot 4 (Java 25) REST API. In-memory H2, OpenAI via Spring AI.

## Run

```bash
./gradlew bootRun
# API on http://localhost:8080
# H2 console on http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:mortgage, user: sa)
```

The app runs **without** an API key, in stub mode (the agents give canned replies).
To use the real OpenAI-backed agents, set the key before starting:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_MODEL=gpt-5-mini   # optional, this is the default
./gradlew bootRun
```

`GET /api/agent/status` reports `{"mode":"stub"}` or `{"mode":"openai"}`.

**Token usage:** every LLM call logs a `[tokens]` line in the backend's console (agent,
model, input/output tokens, today's running total). `GET /api/agent/usage` returns today's
totals per agent. There's a daily budget (`APP_DAILY_TOKEN_BUDGET`, default 4,500,000,
resetting at midnight Europe/Tallinn): the log warns at 80%, and once it's spent the agents
give canned replies until midnight. The count is in-memory, so a backend restart resets it.

## Test

```bash
./gradlew test
```

## API surface

The form-based endpoints were removed in #48. What's left today:

| Method & path | Purpose |
| --- | --- |
| `GET /api/agent/status` | `{ mode }`: `stub` or `openai` |
| `GET /api/agent/usage` | Today's LLM token usage vs. the daily budget, per agent |

The chat-based advisor adds auth, chat, profile, documents, banks and inbox endpoints;
they're specified in `../docs/architecture.md` §5 and built in the walking skeleton
issues (#49–#53). Add each one to this table as it lands.

## Package layout

```
com.seb.mortgage
├── agent        shared agent base: LlmSpecialistAgent, VerifierAgent, token budget
│                (TokenUsageAdvisor, TokenUsageTracker), AgentMode, the Explanations
│                glossary, status/usage controller
├── profile      LoanProfile and ProfileFacts records
├── mockdata     AffordabilityCalculator, RateService, BankCatalogService
└── config       CORS
```

The target layout (advisor, bank, email, auth, conversation, documents, ...) is in
`../docs/architecture.md` §7. New packages are added there as the skeleton is built.
