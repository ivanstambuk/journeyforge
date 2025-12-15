# Feature 001 – Core HTTP Engine + DSL

| Field | Value |
|-------|-------|
| Status | Ready |
| Last updated | 2025-12-09 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/001/plan.md` |
| Linked tasks | `docs/4-architecture/features/001/tasks.md` |
| Roadmap entry | #001 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a spec‑first engine that parses and validates YAML/JSON journey definitions against the full DSL surface defined in `docs/3-reference/dsl.md`, and executes the subset supported by the current engine increment. The DSL includes state types such as `task` (HTTP call, cache operations, and `schedule` via `task.kind: <pluginType>:v<major>`), `choice`, `transform`, `wait`, `webhook`, `parallel`, `timer`, `subjourney`, `succeed`, and `fail`, plus configuration blocks for schemas, policies, and error handling. Execution is initially synchronous and in‑memory, suitable for local runs and unit tests, and lays the foundation for later persistence, durable scheduling, timers, and parallelism.

## Goals
- Parse and validate the DSL from YAML/JSON into model types, including `spec.subjourneys` and `task.kind: schedule:v1`.
- Execute a journey definition synchronously starting at `spec.start`, transitioning via `next`, creating a journey instance.
- Provide HTTP `task` execution with `operationRef`/raw bindings, structured result recording, optional resilience policies, and error mapping hooks.
- Provide branching via `choice` using expression engines selected via `lang` predicates (for example `lang: dataweave`) that must evaluate to boolean.
- Support `transform` states for context/value shaping.
- Support local subjourneys via `type: subjourney` and `spec.subjourneys` as defined in ADR-0020 and the DSL reference.
- Support scheduled journeys via `task.kind: schedule:v1` for `kind: Journey` as defined in ADR-0017 and the DSL reference, including creation/update of schedule bindings and non-interactive scheduled runs.
- Support terminal `succeed` and `fail` states with structured result/errata.
- Provide a tiny runner (CLI or unit test harness) to execute a journey file and print the final outcome.

## Non-Goals
- No durable persistence or resume across process restarts for interactive `kind: Journey` runs in this feature. Engines implementing only Feature 001 MUST reject specs that require durable constructs (`wait`/`webhook`/`timer`) until durable instance storage and full Journeys API semantics are delivered (Feature 002).
- No advanced retries, circuit breakers, or auth enforcement beyond what is required for a minimal local engine; richer policy enforcement will be refined in later features.

## DSL (normative)

See also: `docs/3-reference/dsl.md` and `docs/4-architecture/spec-guidelines/dsl-style.md`.

### Top‑level
```yaml
apiVersion: v1
kind: Journey
metadata:
  name: example
  version: 0.1.0
  description: Simple HTTP GET with success/failure branching.
spec:
  start: call-api                     # required, state id
  states:                             # map<string, State>
    call-api:
      type: task
      task:
        kind: httpCall:v1
        method: GET                   # GET | POST | PUT | DELETE
        url: "https://httpbin.org/get?i=${context.inputId}"
        headers: { Accept: application/json }
        timeoutMs: 10000              # optional, default 10000
        resultVar: apiResponse        # body parsed as JSON, stored at context.apiResponse
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.apiResponse.ok == true and context.apiResponse.body.url == "https://httpbin.org/get?i=42"
          next: success
      default: failure

    success:
      type: succeed
      outputVar: result               # optional; copies context.apiResponse to context.result

    failure:
      type: fail
      errorCode: NOT_MATCHED
      reason: "URL didn’t match"
```

### Semantics (scope for this feature)
- Context:
  - A mutable JSON‑like map available as `context`. The engine initialises it with `{}` unless a caller supplies an initial value. State transitions may add/update fields.
- Core states:
  - `task.kind = httpCall:v1`:
    - Interprets `method`, `url`, `headers`, `timeoutMs`, optional `body` (for non‑GET). The `url` supports simple `${context.<dotPath>}` interpolation. The response body is parsed as JSON when `Content-Type` indicates JSON; otherwise stored as a string. If `resultVar` is set, a structured result is stored under `context.<resultVar>` with fields: `status?`, `ok`, `headers`, `body`, optional `error`.
    - Outcome recording: network errors or non‑2xx responses are captured in the result object (`ok=false`, `status` or `error`), and execution continues to `next`. Use `choice` to decide terminal states.
  - `choice`:
    - Evaluate `when.predicate` with `context` bound; the expression must return boolean. The first true predicate transitions via `next`.
    - If no branch matches, engine transitions to `default`.
  - `succeed`:
    - Terminal state. If `outputVar` is set and exists in `context`, the engine returns that value as the journey output; otherwise returns the full `context`.
  - `fail`:
    - Terminal state with `errorCode` and human‑readable `reason`.
- Subjourneys (`type: subjourney`):
  - Engine parses and validates `spec.subjourneys` and `type: subjourney` states as defined in ADR‑0020 and the DSL reference.
  - Entering a `subjourney` state:
    - Evaluates the optional `input.mapper` to produce the child context (or `{}` when omitted).
    - Executes the referenced subjourney graph to a terminal state.
    - Derives either an output value or a mini‑outcome object depending on `resultKind`, and writes it to `context.<resultVar>` when configured.
    - Applies `onFailure.behavior`:
      - `propagate` – treat failed subjourneys as parent failures at that state id.
      - `capture` – always continue to `next`, leaving it to subsequent states to branch on the captured outcome.
- Scheduled journeys (`task.kind: schedule:v1`):
  - Engine parses and validates `task.kind: schedule:v1` states for `kind: Journey` as defined in ADR‑0017 and the DSL reference.
  - Executing a schedule task:
    - Evaluates the `schedule` block against the current `context` (including `start`, optional `startAt`, `interval`, `maxRuns`, optional `subjectId`, optional `context`, and `onExisting`).
    - Creates or updates an internal schedule binding according to `onExisting`, then continues to `next` (the interactive run does not block on future scheduled runs).
  - Scheduled runs:
    - Are non‑interactive runs of the same journey definition that start from the configured `start` state at later times.
    - Reuse the previous run’s final `context` as the starting `context` for the next run.
    - Are constrained to non‑interactive paths (no `wait`/`webhook` reachable from the scheduled start state), enforced by validation as per ADR‑0017.

### Validation
- `spec.start` must refer to an existing state id.
- State ids must be unique. All `next` references must target existing states.
- `choice` must define either a matching `choices` array or a `default`.
- `task.kind` must be `httpCall:v1` for HTTP tasks in this feature.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-001-01 | Parse DSL from YAML/JSON into model types. | Valid file yields model tree. | Missing `start` or unknown `type` rejected. | Parser error with line/column. | Log spec filename and error summary. | docs/ideas |
| FR-001-02 | Execute journey synchronously from `start`. | Returns `Succeed` output or `Fail` record. | Unknown `next` rejected at validation. | Terminates on first error. | Trace start/stop with state ids. | docs/ideas |
| FR-001-03 | HTTP `task` execution. | Performs request with timeout; stores structured result (`status`, `ok`, `headers`, `body`, optional `error`) under `resultVar`; continues to `next`. | Disallow body on GET; require method+url. | No auto‑termination; branch explicitly in a following `choice`. | Log url, method, duration (no secrets). | docs/ideas |
| FR-001-04 | `choice` branching. | Branches based on a predicate evaluated by the expression engine selected via `lang`; first true branch wins. | Predicate expression must return boolean; invalid types fail validation or evaluation according to ADR‑0027/Feature 012. | No match → `default`. | Trace chosen branch id. | DSL ref; ADR‑0027; Features 012–016 |
| FR-001-05 | Terminal states. | `succeed`/`fail` end execution. | Return output per semantics. | N/A | Trace terminal outcome. | docs/ideas |
| FR-001-06 | Runner entrypoint (temporary). | Run a journey file with initial context. | Validates then executes. | Non‑zero exit on failure. | Print outcome JSON. | docs/ideas |
| FR-001-07 | Local subjourneys. | Engine parses and executes `spec.subjourneys` and `type: subjourney` states according to ADR‑0020; entering a subjourney executes the referenced subgraph with an isolated child context and returns control (and optionally a result) to the parent. | Specs that reference unknown `subjourney.ref` values, produce non-object input contexts, or violate `kind` constraints for subjourney states are rejected at validation. | Engine errors when subjourney execution fails in ways not described by ADR‑0020 surface as internal errors; journey-authored failures follow normal `fail` semantics. | Traces include subjourney boundaries (for example span names including the subjourney id); metrics can attribute time spent in subjourneys. | ADR‑0020, DSL ref |
| FR-001-08 | Model `task.kind: schedule:v1` in the DSL. | DSL defines a `task` variant with `kind: schedule:v1` and scheduling fields `start`, optional `startAt`, `interval`, `maxRuns`, optional `subjectId`, optional `context`, and optional `onExisting`. | Specs that use `task.kind: schedule:v1` outside `kind: Journey`, omit required fields, or reference unknown states are rejected; `interval` and `startAt` literals must have valid formats, and any mappers must be valid expressions for their declared `lang` engine id. | Spec validation returns clear errors pointing at the invalid schedule configuration and field. | Spec validation logs may include counts of journeys using scheduling. | ADR‑0017, DSL ref, engine design docs |
| FR-001-09 | Create schedule bindings at runtime from `task.kind: schedule:v1` tasks. | When a journey executes a `task.kind: schedule:v1`, the engine evaluates the configured fields against the current `context`, creates or updates an internal schedule binding per `onExisting` semantics, and the journey instance continues to `next`. | Engines enforce that `start` refers to an existing state id, `interval` resolves to a supported duration, `maxRuns` resolves to a positive integer, and (when present) `subjectId` resolves to a non-empty string. | If validation fails at runtime (for example mapper errors or invalid values), the journey run fails with a clear internal error; if a duplicate schedule exists and `onExisting: fail`, the schedule task fails and the journey must branch accordingly. | Schedule creation/update is logged with journey name, subjectId, and a redacted view of schedule parameters; metrics track how many schedules are created/updated. | ADR‑0017, engine design docs |
| FR-001-10 | Execute scheduled runs with evolving context. | For each active schedule binding, the engine starts a new journey instance at the configured `start` state when `startAt`/`interval` fire, initialising `context` from the binding’s `lastContext` (first run) or from the previous run’s final `context` (subsequent runs). | Engines enforce `maxRuns` by incrementing a run counter and preventing further runs once the cap is reached; scheduled runs must be non-interactive paths (no `wait`/`webhook` reachable from `start`), validated statically. | If executing the scheduled path hits a `wait`/`webhook` despite validation, the engine treats this as an internal error and fails the run; if schedule execution fails repeatedly, engines may surface schedule health metrics but MUST NOT silently change journey behaviour. | Metrics record schedule run counts, success/failure rates, and last-run timestamps per schedule; logs include journey and schedule identifiers for debugging. | ADR‑0017 |
| FR-001-11 | Handle duplicate schedules via `schedule.onExisting`. | `schedule.onExisting` controls what happens when a schedule for the same journey/subject/start combination already exists: `fail` rejects with an error, `upsert` updates the existing binding, and `addAnother` creates an additional binding. | Spec validation checks that `onExisting`, when present, is one of the allowed enum values; engine validation checks that the dedup key (journey, subjectId, start) is well-defined. | Misconfigured `onExisting` values lead to spec errors; runtime attempts to upsert/add schedules with missing `subjectId` (when required) fail with clear errors. | Telemetry records which `onExisting` mode is used and may flag unusually high numbers of schedules per subject. | ADR‑0017 |
| FR-001-12 | Enforce non-interactive scheduled paths. | Before accepting a spec that uses `task.kind: schedule:v1`, engines and tooling perform a reachability analysis from `schedule.start` (or `start` in the flattened form) and reject specs where reachable states include `wait` or `webhook`. | Validation tooling surfaces the offending state ids in error messages; journeys without `task.kind: schedule:v1` are unaffected. | If static validation is bypassed and a scheduled run hits a `wait`/`webhook`, the engine treats this as a platform error and fails the run with an internal error code. | Engines MAY log a distinct error when a scheduled run encounters a forbidden state type. | ADR‑0017, DSL ref §5 |

## Non‑Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-001-01 | Java 25 toolchain for all modules. | LTS target. | Build uses JDK 25 in CI. | Gradle toolchains + CI setup. | Roadmap |
| NFR-001-02 | Deterministic unit tests. | CI stability. | No network in unit tests. | Use stubs for HTTP in tests. | Roadmap |
| NFR-001-03 | Minimal logs only. | Signal/noise. | Single‑line per state at INFO. | SLF4J backend TBD. | Roadmap |
| NFR-001-04 | Coarse-grained, predictable scheduling only. | Operational simplicity. | Engine enforces minimum supported interval (for example ≥ 1 minute) and rejects schedules with too-small intervals; monitors detect abnormal schedule densities. | Engine scheduler implementation. | ADR‑0017 |
| NFR-001-05 | Schedules are always context-backed and time-bounded. | Avoid runaway jobs and surprise behaviour. | No schedule binding is accepted without initial context and `maxRuns`; attempts to create such schedules fail validation. | Engine scheduler implementation. | ADR‑0017 |
| NFR-001-06 | Preserve DSL clarity and avoid surface creep. | Spec readability and maintainability. | `task.kind: schedule:v1` is the only scheduling construct in DSL; there is no generic `spec.schedule` block, no scheduled APIs, and no new journey kinds. | DSL reference, linting. | ADR‑0017 |

## Observability

Observability for this feature follows the layered model in `docs/6-decisions/ADR-0025-observability-and-telemetry-layers.md` and is configured via engine/connector/CLI configuration (see `docs/5-operations/observability-telemetry.md`), not via the journey DSL.

- **Core telemetry (required for Feature 001)**
  - Journey lifecycle metrics:
    - `journey_runs_started_total{journeyName, journeyVersion, kind}`.
    - `journey_runs_completed_total{journeyName, journeyVersion, kind, phase}` where `phase ∈ {SUCCEEDED, FAILED}`.
    - `journey_run_duration_seconds{journeyName, journeyVersion, kind, phase}`.
  - A single span per journey/API execution with at least:
    - `journey.name`, `journey.version`, `journey.kind`.
    - `journey.phase` (per ADR‑0023) and `journey.status` (per ADR‑0024).
    - `journey.error_code` when terminal outcome includes an error.
  - Minimal structured logs for terminal journey events and major engine errors using the same bounded attribute set.

- **Extension packs (optional for this feature)**
  - Engine and connectors MAY provide additional HTTP, connector, schedule, and CLI telemetry packs as defined in ADR‑0025, but enabling them is an operational decision and MUST NOT change journey behaviour or DSL semantics.
  - This feature does not require any specific pack to be on by default beyond the core layer; recommended defaults are described in the observability runbook.

- **Privacy and DSL surface**
  - Telemetry MUST obey the default‑deny and redaction rules from ADR‑0025: no payload bodies, header values, or arbitrary `context` fields in metrics, traces, or logs by default; deployments may use attribute allowlists in configuration to expose a small, explicit set of additional attributes.
  - The DSL remains observability‑agnostic in this feature: there is no `spec.telemetry` block or per‑journey telemetry configuration; journeys are observed based on engine configuration and conventions only.

## UI / Interaction Mock‑ups

```yaml
# Example: interactive journey step that enables a recurring job
spec:
  start: configureAndSchedule
  states:
    configureAndSchedule:
      type: task
      task:
        kind: schedule:v1
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
        kind: httpCall:v1
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
| S-001-01 | HTTP 200 JSON → `resultVar` set → `choice` → `succeed`. |
| S-001-02 | HTTP 500 → result.ok=false; `choice` routes to `fail`. |
| S-001-03 | No choice matched → transition to `default` → `fail`. |
| S-001-04 | Interactive journey configures a valid schedule; engine creates a schedule binding; subsequent runs execute from `start` with evolving context until `maxRuns` is reached. |
| S-001-05 | Interactive journey attempts to schedule with missing or invalid `interval`/`maxRuns`; schedule task fails with a clear error and the journey can route to an error-handling path. |
| S-001-06 | Journey defines `task.kind: schedule:v1` where the scheduled path reaches a `wait` state; spec validation rejects the journey. |
| S-001-07 | `onExisting: fail` and a schedule already exists for the same journey/subject/start; schedule task fails and does not change existing schedule. |
| S-001-08 | `onExisting: upsert` and a schedule already exists; schedule parameters and initial context are updated; subsequent runs follow the new cadence and context. |

## Test Strategy
- Parser: round‑trip parse/validate valid and invalid specs.
- Engine: state‑by‑state execution with a stubbed HTTP client (no real network in CI).
- Runner: happy‑path and failure exit codes.
 - Scheduling: unit tests for DSL parsing/validation of `task.kind: schedule:v1` (including mixed literal/mapper usage and invalid combinations), engine-level tests for schedule creation, duplicate handling via `onExisting`, scheduled runs up to `maxRuns`, and enforcement of non-interactive scheduled paths.

## Interface & Contract Catalogue
### Domain Objects (model, package suggestions)
- `io.journeyforge.model.Workflow`, `State` (sealed: `TaskState`, `ChoiceState`, `SucceedState`, `FailState`).
- `io.journeyforge.model.TaskSpec.HttpCall`.
 - `io.journeyforge.model.TaskSpec.Schedule` and associated `ScheduleSpec`/binding types.

### Runner
- Temporary CLI entry (to be hosted in `journeyforge-cli` later). For now, a simple Java `main` in tests or the existing `app` module is acceptable.
- Scheduling and engine:
  - Internal schedule binding model linking journey definitions, subject ids, cadence, and evolving `context`.
  - Scheduler component that triggers new journey instances based on bindings, enforcing `interval` and `maxRuns` as per ADR‑0017.
- API surface (future increment, not in this feature):
  - Read-only endpoints for listing schedules per subject and cancelling schedules, keyed by schedule id or `(journey, subjectId, start)`, to be defined alongside the admin plane features.
