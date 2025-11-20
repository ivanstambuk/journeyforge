# JourneyForge Project Constitution

This document governs how JourneyForge evolves. It encodes the spec‑driven development (SDD) workflow, decision records, and repository guardrails.

- Specifications are the single source of truth. Plans/tasks/tests follow specs.
- Clarify ambiguity first. Log open questions in `docs/4-architecture/open-questions.md` and reference their IDs in chat and commits.
- Significant decisions get ADRs in `docs/6-decisions/ADR-xxxx-*.md` using `docs/templates/adr-template.md`.
- Keep increments small (≤90 minutes), verifiable, and committed with formatting + checks.
- Governance artifacts: roadmap, knowledge map, ADRs, open questions, and `_current-session.md`.

## Roles
- Maintainers: own architecture and releases.
- Contributors: propose specs/PRs aligned to the roadmap and SDD workflow.

## Quality
- Minimal gate: `spotlessApply` + `check` + secrets/commit policy.
- Expand gate as the project matures (errorprone, checkstyle, jacoco, mutation, etc.).
