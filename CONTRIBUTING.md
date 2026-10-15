# Contributing (team of 3)

This is a small student team, so this stays light - the goal is just to stop work from
living only in chat history or one person's head.

## Workflow

1. **Every piece of work is an issue first.** Use the *Feature/task*, *Bug report*, or
   *Research* template. Assign it, and put it in the matching milestone. Read
   `docs/vision.md` first: work on the **core demo path** comes before the extras.
2. **Branch per issue**: `area/short-description` (e.g. `frontend/landing-hub`,
   `agent/glossary-gate`). Matches the `area:*` labels: `frontend`, `backend`, `agent`,
   `docs`, `infra`.
3. **Open a PR against `main`**, reference the issue (`Closes #N`), and fill in the PR
   template's checklist - it isn't decoration, it's specifically there to catch the two
   things that have bitten us before: forgetting to update `docs/decisions.md` when a
   design decision gets made, and letting `frontend/app/types/api.ts` drift out of sync
   with `backend/.../agent/AgentDtos.java`.
4. **At least one other teammate reviews before merging**, even on a 3-person team -
   mainly so more than one person has seen every part of the codebase by demo time.

## Decisions vs. issues

- An **issue** is a unit of work to build.
- A **decision** (an architecture or design choice the team settled on) goes in
  `docs/decisions.md` as a dated entry, and in `docs/agent-architecture.md` too if it
  changes how the agent backend works. Do this in the same PR that implements the
  decision, not after. `docs/decisions.md` is what a mentor, a teammate, or any of our own
  future AI sessions should be able to read to get full context without needing this
  team's chat history.

## Labels

- `area:frontend` / `area:backend` / `area:agent` / `area:docs` / `area:infra`
- `type:feature` / `type:bug` / `type:research` / `type:chore`

## Local setup

See the root `README.md` "Getting started" section (Docker or local dev), and
`backend/README.md` / `frontend/README.md` for per-service detail.
