# Agent Playbook – JourneyForge

Project TL;DR: Spec‑first API journey orchestrator in Java 25. DSL and specs live under `docs/`; the engine and connectors live under `journeyforge-*` modules; CLI lives in `app/` (temporary) or `journeyforge-cli/` later. Read the constitution in `docs/6-decisions/project-constitution.md` before acting.

## Agent‑Facing Repo Map
- Modules (initial)
  - `journeyforge-model/` – DSL model classes.
  - `journeyforge-parser/` – YAML/JSON parsing → model.
  - `journeyforge-runtime-core/` – state machine engine.
  - `journeyforge-connectors-http/` – HTTP connector and policies.
  - `app/` – temporary CLI runner (to be renamed `journeyforge-cli/`).
- Docs
  - `docs/` – constitution, roadmap, specs/plans/tasks, ADRs, ops runbooks, templates.
- Tooling
  - `githooks/` – managed hooks; configure via `git config core.hooksPath githooks`.
  - `.github/workflows/ci.yml` – Java 25 minimal quality gate (spotless + check, gitlint, gitleaks).

## Before You Code (SDD Workflow)
1) Clarify ambiguity first. Log questions in `docs/4-architecture/open-questions.md` (IDs like Q-001) and reference the ID in chat/commits.
2) Update/create the feature spec at `docs/4-architecture/features/<NNN>/spec.md` using templates under `docs/templates/`.
3) Only after the spec is current, create `plan.md` and `tasks.md` for the feature.
4) Implement in small, verifiable increments (≤90 minutes), running `./gradlew spotlessApply check` per slice.
5) For architecturally significant decisions, add an ADR under `docs/6-decisions/` referencing the spec and the relevant question ID.

This workflow applies to all architecturally significant work, including ADRs, DSL/docs under `docs/`, and code changes; it is not limited to Java implementation.

## Build & Test Commands
- Format + verify: `./gradlew --no-daemon spotlessApply check`
- Minimal CI gate: `qualityGate` task (spotlessCheck + check)

## Guardrails
- Do not change public APIs without updating specs/ADRs.
- Keep `_current-session.md` updated with session context when doing multi‑step changes.
- Prefer spec templates and uniform metadata across features.

## Spec Authoring Decision
- Author YAML (1.2 subset); accept YAML+JSON; generate canonical JSON snapshots. See spec-format guideline and ADR-0001.

## Key References
- Terminology: `docs/0-overview/terminology.md`
- DSL Reference: `docs/3-reference/dsl.md`
- DSL Style Guide: `docs/4-architecture/spec-guidelines/dsl-style.md`
- Spec Format & Snapshots: `docs/4-architecture/spec-guidelines/spec-format.md`

DataWeave: JourneyForge uses DataWeave 2.x as the canonical expression language (predicates now; transforms later).

DataWeave authoring: When adding or updating DataWeave mappers and predicates in YAML, prefer multiline block scalars for `expr` values:

```yaml
expr: |
  {
    orderId: context.orderId,
    compensation: "completed",
    cause: outcome
  }
```

Single-line `expr: "..."` is still valid but should be avoided in new specs and gradually normalised to the multiline form in existing docs/examples.

## LLM Interaction Protocol
- For any non-trivial change to specs, ADRs, DSL/docs, or code, the agent MUST follow this sequence:
  1. Restate the task briefly to confirm understanding.
  2. Ask numbered clarification questions (1., 2., 3.) and, for design/architecture topics, record at least one `Q-xxx` entry in `docs/4-architecture/open-questions.md` before proposing any change.
  3. Present 2–4 options labelled A, B, C, … with short pros/cons, and explicitly ask the human to choose or refine.
  4. Only after the human confirms a choice may the agent draft or modify ADRs, specs, DSL/docs, or code, and it MUST reference the relevant `Q-xxx` ID in those changes.
- Large artifacts (ADRs, specs, long docs) MUST be written to the filesystem and only summarised in chat, unless the human explicitly asks to see the full text.
- When the human asks to create a new example journey for a use case (for example under `docs/2-how-to/use-cases/` and `docs/3-reference/examples/`), the agent SHOULD also create matching per-journey OpenAPI and Arazzo files under `docs/3-reference/examples/oas/` and `docs/3-reference/examples/arazzo/` for that journey, unless the human explicitly says not to.
