# Agent Playbook – JourneyForge

Project TL;DR: Spec‑first API workflow orchestrator in Java 25. DSL and specs live under `docs/`; runtime engine and connectors live under `journeyforge-*` modules; CLI lives in `app/` (temporary) or `journeyforge-cli/` later. Read the constitution in `docs/6-decisions/project-constitution.md` before acting.

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
