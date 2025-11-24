# ADR-0023 – Journey Phase Enum Uppercase

Date: 2025-11-24 | Status: Accepted

## Context

The Journeys API exposes two engine-level lifecycle envelopes:
- `JourneyStatus` – used when polling a journey’s current state.
- `JourneyOutcome` – used when retrieving the final result.

Both envelopes currently expose a `phase` field that reflects engine lifecycle:
- `JourneyStatus.phase`: `"RUNNING" | "SUCCEEDED" | "FAILED"`.
- `JourneyOutcome.phase`: `"SUCCEEDED" | "FAILED"`.

At the same time, business outcomes and error codes are consistently expressed using SCREAMING_SNAKE_CASE:
- `output.status`: `"JOB_COMPLETED"`, `"REPORT_FAILED"`, `"KYC_COMPLETED_PRIMARY"`, etc.
- `error.code`: `"REPORT_JOB_TIMEOUT"`, `"ASYNC_JOB_FAILED"`, etc.

This creates a small but persistent inconsistency:
- Engine lifecycle phases use TitleCase strings.
- Business-level statuses and error codes use SCREAMING_SNAKE_CASE.

Consumers are expected to:
- Use `phase` as the primary signal for journey lifecycle (in progress vs terminal, success vs failure).
- Use `output.status` and `error.code` for domain-specific meaning.

For authors and clients, it is desirable that any field that conceptually behaves like a “status code” uses a consistent visual style (SCREAMING_SNAKE_CASE), especially when wiring dashboards and UI states that react to both engine lifecycle and domain outcomes.

## Decision

We change the canonical `phase` enum values for journeys from TitleCase to SCREAMING_SNAKE_CASE:

- `JourneyStatus.phase` is now one of:
  - `RUNNING`
  - `SUCCEEDED`
  - `FAILED`
- `JourneyOutcome.phase` is now one of:
  - `SUCCEEDED`
  - `FAILED`

Implications:
- The JSON wire format for `phase` changes for both status and outcome envelopes.
- The DSL reference (`docs/3-reference/dsl.md`) and generic Journeys OpenAPI (`docs/3-reference/openapi/journeys.openapi.yaml`) are updated to use the new enum values.
- Example journeys, diagrams, and per-journey OpenAPI files are updated to:
  - Use the new uppercase `phase` values in JSON snippets and PlantUML diagrams.
  - Describe filters and examples that reference `phase` using `RUNNING` / `SUCCEEDED` / `FAILED`.
- Business outcome fields (`output.status`) and error codes (`error.code`) remain SCREAMING_SNAKE_CASE and are unaffected by this change.

Guidance for clients:
- Always treat `phase` as the primary lifecycle indicator:
  - `RUNNING` – journey is in progress (status endpoint / step responses).
  - `SUCCEEDED` – journey has completed successfully (final outcome).
  - `FAILED` – journey has completed with a failure (final outcome).
- Use `output.status` and `error.code` for domain-level decisions and UI messaging.
- Never infer engine lifecycle from `output.status`; `phase` remains the authoritative lifecycle field.

## Consequences

Positive:
- Visual consistency: all status-like fields (`phase`, `output.status`, `error.code`) now share the same SCREAMING_SNAKE_CASE convention.
- Clearer mental model for consumers: `phase` reads like a status code while remaining a small, fixed engine-level enum.
- Aligns example journeys and docs (including async patterns) with a consistent naming convention, reducing cognitive friction.

Negative / trade-offs:
- This is a breaking change to the JSON wire format:
  - Existing consumers that compare `phase` to `"Running"`, `"Succeeded"`, or `"Failed"` must be updated to use the new values.
  - Any stored data or dashboards that filter on `phase` using the old strings must be migrated.
- Implementations must update:
  - Validation logic for `phase` enum values.
  - Storage and query layers that filter by `phase` (for example, `WHERE phase = 'Running'` → `WHERE phase = 'RUNNING'`).
- Documentation and examples require a coordinated update so there is no mixture of TitleCase and uppercase phase values.

Overall, this decision prefers early, explicit alignment of engine lifecycle naming with the established SCREAMING_SNAKE_CASE convention for statuses, at the cost of a one-time breaking change while the project is still in a draft/early phase.
