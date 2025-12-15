# JourneyForge Project Constitution

Status: Accepted | Last updated: 2025-12-14

This document governs how JourneyForge evolves. It encodes the spec‑driven development (SDD) workflow, decision records, and repository guardrails.

- Specifications are the single source of truth. Plans/tasks/tests follow specs.
- Store feature specs under `docs/4-architecture/features/<NNN>/spec.md` with traceable identifiers (for example “Feature 001”), and ensure plans, tasks, and tests reference the owning feature ID.
- Clarify ambiguity first. Log open questions in `docs/4-architecture/open-questions.md` and reference their IDs in chat and commits.
- When documenting options for an open question in `docs/4-architecture/open-questions.md`, always list options in preference order so Option A is the recommended path, Option B the next-best alternative, and so on, making the preferred direction immediately visible.
- When a medium- or high-impact question is resolved, first update the governing spec’s normative sections (requirements, behaviour/flows, telemetry/quality/policy), then update or add an ADR for architecturally significant clarifications, and only then tidy `open-questions.md`, plans, and tasks so the spec remains the single source of truth for behaviour.
- Significant decisions get ADRs in `docs/6-decisions/ADR-xxxx-*.md` using `docs/templates/adr-template.md`.
- Keep increments small (≤90 minutes), verifiable, and committed with formatting + checks.
- Governance artifacts: roadmap, knowledge map, ADRs, open questions, and `_current-session.md`.
- When a medium- or high-impact feature ships, mirror the approved change across the roadmap, knowledge map, and any relevant feature plans/runbooks so there is a traceable link from specs and ADRs to implementation and docs.

## ADR Lifecycle
- `Proposed`: candidate design decision under review.
- `Accepted`: binding decision for specs and implementation.
- `Deprecated`: no longer recommended; kept for history and migration guidance.
- `Superseded`: replaced by a newer ADR (which should explain the change).

Before implementation work begins for a feature:
- The governing feature spec SHOULD be `Status: Ready`.
- Any ADRs that the spec treats as normative prerequisites SHOULD be `Status: Accepted`.

## Roles
- Maintainers: own architecture and releases.
- Contributors: propose specs/PRs aligned to the roadmap and SDD workflow.

## Quality
- Minimal gate: `spotlessApply` + `check` + secrets/commit policy.
- Expand gate as the project matures (errorprone, checkstyle, jacoco, mutation, etc.).
- For new or changed behaviour, enumerate success, validation, and failure branches in the spec or plan and stage failing tests for each path before or alongside implementation; run `./gradlew spotlessApply check` after every self-contained increment, and treat a red build as a stop-the-line condition until fixed or explicitly quarantined in the spec or plan.
- Before marking a feature complete, briefly cross-check the governing spec’s normative sections, ADRs, and implementation/tests to ensure every documented requirement has corresponding behaviour and tests and that no code ships without documented intent; record any mismatches as follow-up tasks or spec updates.
- Use the Analysis Gate and Implementation Drift Gate in `docs/5-operations/analysis-gate-checklist.md` before implementation and before marking a feature complete, and keep the `qualityGate` usage described in `docs/5-operations/quality-gate.md` aligned with the project’s active quality expectations.
