# ADR-0017 – Scheduled journeys (`task.kind: schedule:v1`)

Date: 2025-11-22 | Status: Proposed

## Context

Some business journeys need to run periodically without an interactive caller, often impersonating a subject (for example a user) based on previously granted authorisation such as a refresh token. Examples include:
- Recurring payments or billing cycles per customer.
- Periodic data synchronisation for linked accounts.
- Batch-style clean-up or reporting jobs that operate per subject.

Today, JourneyForge supports:
- Long-lived `kind: Journey` definitions with external-input states (`wait`, `webhook`).
- Single-run journeys initiated via `/journeys/{journeyName}/start`.
- No built-in scheduling model for “journey as job”, and no stateful per-subject schedules.

This leads to several problems:
- Journeys that want periodic behaviour must abuse `wait.timeoutSec` or external schedulers, or build “controller journeys” that manage subjourneys.
- There is no first-class way to ensure scheduled runs are:
  - Always backed by explicit initial context (for example `userId`, `refreshTokenRef`).
  - Non-interactive once scheduled (no `wait`/`webhook` on the scheduled path).
  - Time-bounded (no infinite schedules; bounded by `maxRuns`).
  - Able to retain evolving state across runs within `context`.
- Scheduling semantics are not visible in DSL, making it harder to reason about how journeys operate over time.

We considered three modelling options during the original journey-as-job scheduling design discussion:
- Option A – a separate `ScheduledJourneyBinding` runtime resource (no DSL change).
- Option B – a new `kind: ScheduledJourney` that delegates to a worker `kind: Journey`.
- Option C – a dedicated `task.kind: schedule` that allows a normal journey to schedule non-interactive runs of itself starting at a specific state, with evolving `context` across runs.

Options A and B add extra kinds/resources and push scheduling semantics away from the journey definition. Option C keeps scheduling opt-in, explicit, and co-located with the interactive journey that knows the subject and initial context.

## Decision

We adopt **Option C**: introduce a `task.kind: schedule` for `kind: Journey` that creates **schedule bindings** at runtime. A schedule binding causes the engine to start future, non-interactive runs of the same journey from a specified state with **evolving context** across runs. In the plugin-only DSL, this is expressed as `task.kind: schedule:v1`.

High-level decision:
- The DSL gains a new `task.kind: schedule` shape under `type: task` (expressed as `schedule:v1` in the plugin naming scheme).
- Scheduling is **only** available for `kind: Journey`, not `kind: Api`.
- Scheduling is **explicit and context-driven**: an interactive journey instance executes the schedule task, which:
  - Selects the scheduled entry state (`start`).
  - Defines cadence and bounds (`startAt`, `interval`, `maxRuns`).
  - Provides initial context for the first scheduled run (via an optional `context` mapper).
  - Optionally identifies the subject (`subjectId`).
  - Chooses duplicate handling behaviour (`onExisting`).
- The engine:
  - Creates a schedule binding at runtime when the schedule task runs.
  - Starts scheduled runs at the configured state with initial `context` taken from the binding.
  - After each run, updates the binding’s stored `context` with the run’s final `context`.
  - Enforces `maxRuns` and non-interactive semantics for the scheduled path.

### 1. DSL shape – `task.kind: schedule` / `schedule:v1`

We extend the `task` state with a new `kind`:

```yaml
type: task
task:
  kind: schedule:v1
  start: <stateId>                   # required – entry state for scheduled runs

  # Optional start time; omit → start as soon as possible
  startAt: <string|mapper>           # RFC 3339 timestamp string or mapper resolving to one

  # Required cadence – coarse-grained ISO-8601 duration
  interval: <string|mapper>          # e.g. "P1D", "P1M"

  # Required run bound
  maxRuns: <int|mapper>              # positive integer (literal or mapper)

  # Optional subject binding for listing/cancellation
  subjectId:                         # optional but recommended
    mapper:
      lang: dataweave
      expr: <expression>             # must resolve to a non-empty string when present

  # Optional initial context snapshot for the FIRST scheduled run
  # Later runs use the previous run's final context
  context:
    mapper:
      lang: dataweave
      expr: <expression>             # default: use full current context when omitted

  # Optional duplicate behaviour when a schedule already exists
  onExisting: fail | upsert | addAnother
next: <stateId>
```

Key properties:
- `start` is a state id in the same journey that acts as the entry point for scheduled runs.
- `startAt`:
  - When omitted, the effective start time is “as soon as possible” according to the scheduler.
  - When present as a string, it MUST be an RFC 3339 timestamp.
  - When present as a mapper, it MUST evaluate to an RFC 3339 timestamp string.
- `interval`:
  - Required; represents a coarse-grained cadence (for example daily, weekly, monthly).
  - When a string, MUST be a supported ISO-8601 duration.
  - When a mapper, it MUST evaluate to a supported ISO-8601 duration string.
- `maxRuns`:
  - Required; may be a literal positive integer or a mapper that evaluates to a positive integer.
  - Provides a hard cap on the number of scheduled runs per schedule binding.
- `subjectId`:
  - Optional; usually derived from context (for example `context.userId`).
  - Used as part of the schedule binding identity/key so schedules can be listed and cancelled per subject.
- `context`:
  - Optional; defines the initial context snapshot for the first scheduled run.
  - When omitted, the full current `context` of the interactive journey instance is used.
- `onExisting`:
  - Optional enum controlling duplicate schedule behaviour (see below).

### 2. Engine model – schedule bindings and evolving context

The engine maintains **schedule bindings** internally, conceptually:

```json
{
  "scheduleId": "string",
  "journeyName": "string",
  "journeyVersion": "string",
  "startStateId": "string",
  "subjectId": "string or null",
  "startAt": "RFC3339 timestamp",
  "interval": "ISO-8601 duration",
  "maxRuns": 12,
  "runsExecuted": 0,
  "lastContext": { "json": "object" }
}
```

Bindings are created or updated when `task.kind: schedule` executes:
- The engine evaluates `schedule.start`, `startAt`, `interval`, `maxRuns`, `subjectId`, and `context` mappers against the current `context`.
- It computes an identity key, for example:
  - `(journeyName, journeyVersion, subjectId, startStateId)`.
- It applies `onExisting`:
  - `fail` (default when omitted): if a binding exists for the identity key, the schedule task fails; no binding is created or changed.
  - `upsert`: if a binding exists, update it in place (including schedule parameters and `lastContext`); otherwise create a new binding.
  - `addAnother`: always create a new binding, even if another exists with the same key.
- For the binding’s initial `lastContext`:
  - If `schedule.context` is present, use the mapper result.
  - Otherwise, snapshot the full current `context`.
- The journey instance then continues to `next` as a normal task; the schedule creation is an effect, not a terminal outcome.

Scheduled runs use **evolving context**:
- First scheduled run:
  - At or after `startAt`, the scheduler creates a new journey instance.
  - Initial `context` for the run is the binding’s `lastContext` (the snapshot from the schedule task).
  - Execution starts at the state identified by `schedule.start`.
- On completion of each run:
  - The engine updates the binding’s `lastContext` to the run’s final `context`.
  - The engine increments `runsExecuted`.
- Subsequent runs:
  - Each scheduled run’s initial `context` is the binding’s current `lastContext`, i.e. the final `context` from the previous run.
- When `runsExecuted >= maxRuns`, the binding is considered inactive and the scheduler MUST NOT start further runs.

This design matches the desired behaviour from the original journey-as-job scheduling discussion: “write operations in context get preserved; next scheduled run starts from the state at the end of the previous run.”

### 3. Non-interactive scheduled paths

Scheduled runs must be **non-interactive**:
- The state referenced by `schedule.start` and all states reachable from it **MUST NOT** be `type: wait` or `type: webhook`.
- Engines and tools SHOULD perform a static reachability analysis when loading specs:
  - If any reachable state from `schedule.start` is a `wait` or `webhook` state, spec validation MUST fail.
- If static validation is bypassed or not available and a scheduled run reaches a `wait`/`webhook` at runtime, the engine MUST:
  - Treat this as an internal engine error (not a journey-authored failure).
  - Fail the run with a canonical internal error code and record telemetry.

Interactive journeys may still contain `wait` / `webhook` states reachable from `spec.start`; the non-interactive constraint applies **only** to paths reachable from `schedule.start`.

### 4. Scope and limits

- Scheduling is only allowed for `kind: Journey`, not for `kind: Api`.
- Schedules are coarse-grained:
  - Engines MAY enforce a minimum supported `interval` (for example ≥ 1 minute).
  - Extremely small intervals SHOULD be rejected at validation time.
- Every schedule binding MUST have:
  - Explicit cadence (`interval`) and bound (`maxRuns`).
  - Initial `context` (via `schedule.context` or full `context` snapshot).
  - There is no support for context-less schedules.
- Schedule management APIs (for listing/cancelling schedules per subject) are out of scope for this ADR and are expected to be defined alongside the core engine and future admin features.

## Consequences

Positive:
- **Explicit, context-driven scheduling**:
  - Journeys explicitly decide when and how to schedule, using current `context` to define cadence, bounds, and subject identity.
  - There are no hidden or global schedules attached to journey definitions.
- **Evolving state across runs**:
  - Scheduled runs naturally preserve and evolve `context` (for example `lastRunAt`, cumulative counters, or checkpoints) without needing external orchestrators.
- **Non-interactive guarantees**:
  - Scheduled paths are guaranteed non-interactive, avoiding surprising prompts for user input during background runs.
- **Guardrails against misuse**:
  - `maxRuns` and required initial `context` prevent infinite or context-less schedules.
  - `interval` validation and minimums discourage high-frequency misuse.
- **Reuses the existing journey model**:
  - No new journey kind; schedule semantics are encoded via a single new `task.kind`.

Negative / trade-offs:
- Adds complexity to engine implementations:
  - The engine must maintain a schedule binding store and a scheduler loop.
  - Requires careful handling of failures, retries, and idempotence for scheduled runs.
- Potential for misuse remains:
  - Authors can still configure many schedules per subject (`onExisting: addAnother`) unless higher-level policies limit this.
  - Poorly chosen intervals/maxRuns can create large numbers of scheduled runs; operational limits and monitoring are needed.
- No built-in distributed/HA scheduler:
  - This ADR assumes an engine-level scheduler; highly available, distributed scheduling is deferred to later features and may require additional design.

Related artefacts:
- **Feature 001** – Core HTTP Engine + DSL – includes `task.kind: schedule` support within the core engine scope.
- **DSL reference** – updated `docs/3-reference/dsl.md` section 5 (States) to include `task.kind: schedule` shape and semantics.
