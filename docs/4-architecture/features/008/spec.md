# Feature 008 – Scheduled journeys (`task.kind: schedule`)

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-22 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/008/plan.md` |
| Linked tasks | `docs/4-architecture/features/008/tasks.md` |
| Roadmap entry | #002 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below (no per‑feature Clarifications), and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Allow journeys to schedule non-interactive, periodic “job runs” of themselves using a dedicated `task.kind: schedule`. Scheduling is explicit and context-driven: an interactive journey instance chooses when and how to schedule, provides initial context, and the engine runs later instances starting from a specific state with evolving context across runs. Scheduling is available only for `kind: Journey` (not `kind: Api`) and is designed around coarse-grained cadences (for example daily or monthly).

## Goals
- Define a `task.kind: schedule` DSL shape and semantics for `kind: Journey` that lets an interactive journey schedule future, non-interactive runs of the same journey starting from a named state.
- Ensure scheduled runs are time-bounded via `maxRuns`, and are always backed by explicit initial context provided by the scheduling journey instance (no context-less schedules).
- Make scheduled runs reuse the final `context` of the previous run as the initial `context` for the next run, so journeys can accumulate state across runs without external orchestration.
- Provide a clear subject binding (for example `subjectId` based on `context.userId`) so schedules can be listed and cancelled per subject.
- Keep scheduling logic out of `kind: Api` and out of generic top-level DSL blocks, so the main DSL stays focused on behaviour and schedule semantics remain clearly opt-in and explicit.

## Non-Goals
- No new `kind: ScheduledJourney` or “system journey” kind in this slice; scheduling reuses the existing `kind: Journey` surface and state machine.
- No cron-like top-level `spec.schedule` block; all scheduling configuration lives under the `task.kind: schedule` state that creates the schedule.
- No dedicated “timer state” for in-journey sleeps; timers for scheduled runs are implemented by the engine’s scheduler between runs, not as states inside the journey.
- No support for scheduled runs of `kind: Api` in this slice; scheduled journeys are always long-lived `kind: Journey` definitions.
- No UI implementation for schedule management; this slice defines DSL and engine semantics, plus the minimum API hooks required to list and cancel schedules.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-008-01 | Model `task.kind: schedule` in the DSL. | DSL defines a `task` variant with `kind: schedule` and a nested `schedule` block containing `start`, optional `startAt`, `interval`, `maxRuns`, optional `subjectId`, optional `context`, and optional `onExisting`. | Specs that use `task.kind: schedule` outside `kind: Journey`, omit required fields, or reference unknown states are rejected; `interval` and `startAt` literals must have valid formats, and mappers must be valid DataWeave. | Spec validation returns clear errors pointing at the invalid schedule block and field. | Spec validation logs may include counts of journeys using scheduling. | DSL ref §5 (states), ADR-0017 |
| FR-008-02 | Create schedule bindings at runtime from `kind: schedule` tasks. | When a journey executes a `task.kind: schedule`, the engine evaluates the configured mappers against the current `context`, creates or updates an internal schedule binding per `onExisting` semantics, and the journey instance continues to `next`. | Engines enforce that `start` refers to an existing state id, `interval` resolves to a supported duration, `maxRuns` resolves to a positive integer, and (when present) `subjectId` resolves to a non-empty string. | If validation fails at runtime (for example mapper errors or invalid values), the journey run fails with a clear internal error; if a duplicate schedule exists and `onExisting: fail`, the schedule task fails and the journey must branch accordingly. | Schedule creation/update is logged with journey name, subjectId, and a redacted view of schedule parameters; metrics track how many schedules are created/updated. | ADR-0017, engine design docs |
| FR-008-03 | Execute scheduled runs with evolving context. | For each active schedule binding, the engine starts a new journey instance at the configured `start` state when `startAt`/`interval` fire, initialising `context` from the binding’s `lastContext` (first run) or from the previous run’s final `context` (subsequent runs). | Engines enforce `maxRuns` by incrementing a run counter and preventing further runs once the cap is reached; scheduled runs must be non-interactive paths (no `wait`/`webhook` reachable from `start`), validated statically. | If executing the scheduled path hits a `wait`/`webhook` despite validation, the engine treats this as an internal error and fails the run; if schedule execution fails repeatedly, engines may surface schedule health metrics but MUST NOT silently change journey behaviour. | Metrics record schedule run counts, success/failure rates, and last-run timestamps per schedule; logs include journey and schedule identifiers for debugging. | ADR-0017 |
| FR-008-04 | Allow duplicate-schedule handling via `onExisting`. | `schedule.onExisting` controls what happens when a schedule for the same journey/subject/start combination already exists: `fail` rejects with an error, `upsert` updates the existing binding, and `addAnother` creates an additional binding. | Spec validation checks that `onExisting`, when present, is one of the allowed enum values; engine validation checks that the dedup key (journey, subjectId, start) is well-defined. | Misconfigured `onExisting` values lead to spec errors; runtime attempts to upsert/add schedules with missing `subjectId` (when required) fail with clear errors. | Telemetry records which `onExisting` mode is used and may flag unusually high numbers of schedules per subject. | ADR-0017 |
| FR-008-05 | Enforce non-interactive scheduled paths. | Before accepting a spec that uses `task.kind: schedule`, engines and tooling perform a reachability analysis from `schedule.start` and reject specs where reachable states include `wait` or `webhook`. | Validation tooling surfaces the offending state ids in error messages; journeys without `task.kind: schedule` are unaffected. | If static validation is bypassed and a scheduled run hits a `wait`/`webhook`, the engine treats this as a platform error and fails the run with an internal error code. | Engines MAY log a distinct error when a scheduled run encounters a forbidden state type. | DSL ref §5, ADR-0017 |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-008-01 | Coarse-grained, predictable scheduling only. | Operational simplicity. | Engine enforces minimum supported interval (for example ≥ 1 minute) and rejects schedules with too-small intervals; monitors detect abnormal schedule densities. | Engine scheduler implementation. | ADR-0017 |
| NFR-008-02 | Schedules are always context-backed and time-bounded. | Avoid runaway jobs and surprise behaviour. | No schedule binding is accepted without initial context and `maxRuns`; attempts to create such schedules fail validation. | Engine scheduler implementation. | ADR-0017 |
| NFR-008-03 | Preserve DSL clarity and avoid surface creep. | Spec readability and maintainability. | `task.kind: schedule` is the only scheduling construct in DSL; there is no generic `spec.schedule` block, no scheduled APIs, and no new journey kinds. | DSL reference, linting. | ADR-0017 |

## UI / Interaction Mock-ups
```
// Example: interactive journey step that enables a recurring job

spec:
  start: configureAndSchedule
  states:
    configureAndSchedule:
      type: task
      task:
        kind: schedule
        schedule:
          start: scheduledStart
          interval: "P1D"              # run once per day
          maxRuns: 30                  # at most 30 runs
          subjectId:
            mapper:
              lang: dataweave
              expr: context.userId
          context:
            mapper:
              lang: dataweave
              expr: context - ["sensitiveStuff"]
      next: interactiveDone

    interactiveDone:
      type: succeed

    scheduledStart:
      type: task
      task:
        kind: httpCall
        operationRef: payments.charge
        resultVar: paymentResult
      next: updateState

    updateState:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              lastRunAt: now(),
              lastStatus: paymentResult.status
            }
      next: scheduledDone

    scheduledDone:
      type: succeed
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-008-01 | Interactive journey configures a valid schedule; engine creates a schedule binding; subsequent runs execute from `start` with evolving context until `maxRuns` is reached. |
| S-008-02 | Interactive journey attempts to schedule with missing or invalid `interval`/`maxRuns`; schedule task fails with a clear error and the journey can route to an error-handling path. |
| S-008-03 | Journey defines `task.kind: schedule` where the scheduled path reaches a `wait` state; spec validation rejects the journey. |
| S-008-04 | `onExisting: fail` and a schedule already exists for the same journey/subject/start; schedule task fails and does not change existing schedule. |
| S-008-05 | `onExisting: upsert` and a schedule already exists; schedule parameters and initial context are updated; subsequent runs follow the new cadence and context. |

## Test Strategy
- Unit tests for DSL parsing/validation of `task.kind: schedule` (including mixed literal/mapper usage and invalid combinations).
- Engine-level tests for schedule creation, duplicate handling, and evolving-context behaviour across runs.
- Integration-level tests (runner or REST API) that start a journey, schedule it, and then simulate scheduled runs to verify non-interactive paths and `maxRuns` enforcement.
- Documentation/tests to ensure journeys that do not use `task.kind: schedule` are unaffected.

## Interface & Contract Catalogue
- DSL:
  - `task.kind: schedule` and nested `schedule` block (`start`, optional `startAt`, `interval`, `maxRuns`, optional `subjectId`, optional `context`, optional `onExisting`).
- Engine:
  - Internal schedule binding model linking journey definitions, subject ids, cadence, and evolving `context`.
  - Scheduler component that triggers new journey instances based on bindings.
- API surface (future slice):
  - Read-only endpoints for listing schedules per subject and cancelling schedules, keyed by schedule id or `(journey, subjectId, start)`.
