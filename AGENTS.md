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
1) Clarify ambiguity first. Log open questions in `docs/4-architecture/open-questions.md` (IDs like Q-001) and reference the ID in chat. When a question is resolved and its outcome is captured in an ADR, spec, or other authoritative doc, **immediately remove the corresponding row from `open-questions.md`** so the file never contains resolved questions.
2) Update/create the feature spec at `docs/4-architecture/features/<NNN>/spec.md` using templates under `docs/templates/`.
3) Only after the spec is current, create `plan.md` and `tasks.md` for the feature.
4) Implement in small, verifiable increments (≤90 minutes), running `./gradlew spotlessApply check` per slice.
5) For architecturally significant decisions, add an ADR under `docs/6-decisions/` referencing the spec and the relevant question ID.

This workflow applies to all architecturally significant work, including ADRs, DSL/docs under `docs/`, and code changes; it is not limited to Java implementation.

## Build & Test Commands
- Format + verify: `./gradlew --no-daemon spotlessApply check`
- Minimal CI gate: `qualityGate` task (spotlessCheck + check)
– Keep the `qualityGate` task green; treat it as the minimal CI contract for any change. When proposing new automated quality checks (for example contract tests, mutation analysis, or security/red-team suites), describe them first in the relevant feature spec/plan and only wire them into CI after agreement.

## Guardrails
- Do not change public APIs without updating specs/ADRs.
- Keep `_current-session.md` updated with session context when doing multi‑step changes.
- Prefer spec templates and uniform metadata across features.
- Never add or upgrade build or library dependencies (including Gradle plugins and BOMs) without explicit owner approval; when a change is approved, record the rationale and impact in the relevant feature spec/plan/tasks or ADR. Automated dependency PRs (for example Dependabot) are treated as scoped requests and still require owner approval before merging.
- Avoid destructive commands (for example `rm -rf`, `git reset --hard`, or force-pushes) and do not touch files outside this repository’s root unless the human explicitly requests it; stay within the project sandbox and prefer reversible operations.
- Before making risky refactors, changing persistence or storage behaviour, or altering external contracts (HTTP semantics, connector behaviour, journey execution guarantees, etc.), propose the change to the human first and wait for approval; when approval is granted, capture the rationale and scope in the relevant feature spec/plan/tasks or ADR before implementing.
- Do not introduce Java reflection in production or test sources. When existing code requires access to collaborators or internals, prefer explicit seams (constructor parameters, package-private collaborators, or dedicated test fixtures) instead of reflection.
- Treat externally visible contracts (DSL schema, HTTP APIs, connector behaviours, example journeys, and other public-facing artefacts) as greenfield: do not add legacy shims, silent fallbacks, or heuristics for “old” behaviour unless explicitly requested for a specific migration or feature, and prefer clear, versioned changes captured in specs/ADRs over hidden compatibility code.
- Respect module boundaries. Treat `journeyforge-runtime-core` as the source of truth for journey engine/state-machine behaviour and `journeyforge-model` as the canonical DSL model. Connectors (`journeyforge-connectors-*`), parsers, and CLI/app layers must delegate to these modules instead of re-implementing or bypassing engine logic or introducing parallel DSL models without an explicit spec/ADR.

## Spec Authoring Decision
- Author YAML (1.2 subset); accept YAML+JSON; generate canonical JSON snapshots. See spec-format guideline and ADR-0001.

## Key References
- Terminology: `docs/0-overview/terminology.md`
- DSL Reference: `docs/3-reference/dsl.md`
- DSL Style Guide: `docs/4-architecture/spec-guidelines/dsl-style.md`
- Spec Format & Snapshots: `docs/4-architecture/spec-guidelines/spec-format.md`
- Docs Style Guide: `docs/4-architecture/spec-guidelines/docs-style.md`
- High-signal specs manifest for LLM context: `docs/4-architecture/llms.txt`
- Analysis & Implementation Drift Gate runbook: `docs/5-operations/analysis-gate-checklist.md`
- Quality gate usage guide: `docs/5-operations/quality-gate.md`

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
- For any non-trivial change to specs, ADRs, DSL/docs, or code, the agent MUST follow a **two-phase interaction**:
  - **Phase 1 – Clarifications (questions only).**
    1. Restate the task briefly to confirm understanding.
    2. Ask numbered clarification questions (1., 2., 3.) and wait for the human’s answers; do not propose options, solutions, or implementation plans in this message.
    3. For design/architecture topics, record at least one `Q-xxx` entry in `docs/4-architecture/open-questions.md` before asking those questions; when that question is resolved and its outcome is captured in an ADR/spec/doc, remove the corresponding row from `open-questions.md` as part of the same slice of work.
  - **Phase 2 – Options & decision.**
    4. After the human has answered the clarification questions, present 2–4 options labelled A, B, C, … with short pros/cons, and explicitly ask the human to choose or refine.
    5. Only after the human confirms a choice may the agent draft or modify ADRs, specs, DSL/docs, or code, and it MUST mention the relevant `Q-xxx` ID in its chat summary for that change (do not embed `Q-xxx` IDs in normative specs/ADRs/docs; see `docs/4-architecture/open-questions.md`).
- **Low-impact/self-serve changes.** Trivial, obviously mechanical edits (typos, purely local renames, formatting-only fixes) may be performed directly after restating the task, without a full two-phase exchange, unless the human explicitly asks to follow the two-phase protocol.
- **Explicit overrides.** If the human explicitly says to skip clarifications or options (for example, “skip questions, just propose options now” or “just implement X as described”), the agent may follow that instruction but should briefly acknowledge that it is deviating from the default two-phase protocol for this interaction.
- Large artifacts (ADRs, specs, long docs) MUST be written to the filesystem and only summarised in chat, unless the human explicitly asks to see the full text.
- When the human asks to create a new example journey for a use case (for example under `docs/2-how-to/use-cases/` and `docs/3-reference/examples/`), the agent SHOULD also create matching per-journey OpenAPI and Arazzo files under `docs/3-reference/examples/oas/` and `docs/3-reference/examples/arazzo/` for that journey, unless the human explicitly says not to.

## Tracking & Documentation
- For non-trivial, agent-assisted increments, add a brief “intent log” to the relevant feature `plan.md` or `_current-session.md`, capturing the key prompts/questions, chosen options (A/B/C, etc.) and decisions, and any important commands used to validate the slice (for example `./gradlew spotlessApply check`). Keep this log lightweight (a few bullets), but ensure each increment has a trace of how it was produced and validated.

## After Completing Work
- Treat “completing work” as finishing any self-contained increment that was scoped during planning to fit within ≤90 minutes, even if actual execution takes longer.
- After each completed increment with a passing build, run `./gradlew --no-daemon spotlessApply check`, update or remove any affected entries in `docs/4-architecture/open-questions.md`, and update the relevant feature `plan.md`, ADRs, and/or `_current-session.md` with a brief summary of what changed and which questions or decisions were resolved.
- When explicitly asked to commit changes, stage the entire repository for that increment, use a commit message that clearly describes all deltas, and push before starting the next task. Avoid splitting a single logical increment across multiple commits unless the relevant spec/plan explicitly calls for it.
