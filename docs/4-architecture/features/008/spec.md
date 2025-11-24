# Feature 008 – Scheduled journeys (`task.kind: schedule`) – folded into Feature 001

| Field | Value |
|-------|-------|
| Status | Folded into Feature 001 |
| Last updated | 2025-11-23 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/001/plan.md` |
| Linked tasks | `docs/4-architecture/features/001/tasks.md` |
| Roadmap entry | (folded into #001) |

This feature has been **folded into Feature 001 – Core HTTP Engine + DSL**. Its design intent and requirements are now captured in:

- `docs/6-decisions/ADR-0017-scheduled-journeys-task-schedule.md` – normative design for `task.kind: schedule`.
- `docs/3-reference/dsl.md` – DSL reference for `task.kind: schedule` under the States section.
- `docs/4-architecture/features/001/spec.md` – core engine feature spec, including:
  - Goals that cover parsing/executing `task.kind: schedule`.
  - Semantics for scheduled journeys in the “Semantics (scope for this feature)” section.
  - Functional requirement **FR-001-08 Scheduled journeys**.

The original dedicated spec for Feature 008 (scheduled journeys) has been superseded by these documents. This file is retained as a historical pointer only and MUST NOT be treated as normative; use Feature 001’s spec and ADR‑0017 instead.

