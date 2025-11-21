# Feature 001 – Core HTTP Engine + DSL

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-19 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/001/plan.md` |
| Linked tasks | `docs/4-architecture/features/001/tasks.md` |
| Roadmap entry | #001 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md` (for example Q-001), encode resolved answers directly in the Requirements/NFR/Behaviour/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a spec‑first engine that executes YAML/JSON journey definitions using the full DSL surface defined in `docs/3-reference/dsl.md`. The DSL includes state types such as `task` (HTTP call and cache operations), `choice`, `transform`, `wait`, `webhook`, `parallel`, `succeed`, and `fail`, plus configuration blocks for schemas, policies, and error handling. Execution is initially synchronous and in‑memory, suitable for local runs and unit tests, and lays the foundation for later persistence, timers, and parallelism.

## Goals
- Parse and validate the DSL from YAML/JSON into model types.
- Execute a journey definition synchronously starting at `spec.start`, transitioning via `next`, creating a journey instance.
- Provide HTTP `task` execution with `operationRef`/raw bindings, structured result recording, optional resilience policies, and error mapping hooks.
- Provide branching via `choice` using DataWeave 2.x predicates (must evaluate to boolean).
- Support `transform` states for context/value shaping.
- Reserve DSL shapes for external-input (`wait`/`webhook`), parallel, cache, and policy blocks, even if their full engine semantics are implemented incrementally.
- Support terminal `succeed` and `fail` states with structured result/errata.
- Provide a tiny runner (CLI or unit test harness) to execute a journey file and print the final outcome.

## Non-Goals
- No persistence or resume across process restarts.
- No distributed scheduler or durable timers.
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
        kind: httpCall
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

### Semantics
- Context: a mutable JSON‑like map available as `context`. The engine initialises it with `{}` unless a caller supplies an initial value. State transitions may add/update fields.
- `task.kind = httpCall`:
  - Interprets `method`, `url`, `headers`, `timeoutMs`, optional `body` (for non‑GET). The `url` supports simple `${context.<dotPath>}` interpolation. The response body is parsed as JSON when `Content-Type` indicates JSON; otherwise stored as a string. If `resultVar` is set, a structured result is stored under `context.<resultVar>` with fields: `status?`, `ok`, `headers`, `body`, optional `error`.
  - Outcome recording: network errors or non‑2xx responses are captured in the result object (`ok=false`, `status` or `error`), and execution continues to `next`. Use `choice` to decide terminal states.
- `choice`:
  - Evaluate `when.predicate` with `context` bound; the expression must return boolean. The first true predicate transitions via `next`.
  - If no branch matches, engine transitions to `default`.
- `succeed`:
  - Terminal state. If `outputVar` is set and exists in `context`, the engine returns that value as the journey output; otherwise returns the full `context`.
- `fail`:
  - Terminal state with `errorCode` and human‑readable `reason`.

### Validation
- `spec.start` must refer to an existing state id.
- State ids must be unique. All `next` references must target existing states.
- `choice` must define either a matching `choices` array or a `default`.
- `task.kind` must be `httpCall`.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-001-01 | Parse DSL from YAML/JSON into model types. | Valid file yields model tree. | Missing `start` or unknown `type` rejected. | Parser error with line/column. | Log spec filename and error summary. | docs/ideas |
| FR-001-02 | Execute journey synchronously from `start`. | Returns `Succeed` output or `Fail` record. | Unknown `next` rejected at validation. | Terminates on first error. | Trace start/stop with state ids. | docs/ideas |
| FR-001-03 | HTTP `task` execution. | Performs request with timeout; stores structured result (`status`, `ok`, `headers`, `body`, optional `error`) under `resultVar`; continues to `next`. | Disallow body on GET; require method+url. | No auto‑termination; branch explicitly in a following `choice`. | Log url, method, duration (no secrets). | docs/ideas |
| FR-001-04 | `choice` branching. | Branches on DataWeave predicate; first true wins. | DW predicate must return boolean. | No match → `default`. | Trace chosen branch id. | docs/ideas |
| FR-001-05 | Terminal states. | `succeed`/`fail` end execution. | Return output per semantics. | N/A | Trace terminal outcome. | docs/ideas |
| FR-001-06 | Runner entrypoint (temporary). | Run a journey file with initial context. | Validates then executes. | Non‑zero exit on failure. | Print outcome JSON. | docs/ideas |

## Non‑Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-001-01 | Java 25 toolchain for all modules. | LTS target. | Build uses JDK 25 in CI. | Gradle toolchains + CI setup. | Roadmap |
| NFR-001-02 | Deterministic unit tests. | CI stability. | No network in unit tests. | Use stubs for HTTP in tests. | Roadmap |
| NFR-001-03 | Minimal logs only. | Signal/noise. | Single‑line per state at INFO. | SLF4J backend TBD. | Roadmap |

## UI / Interaction Mock‑ups
N/A for this feature slice (CLI/tests only).

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-001-01 | HTTP 200 JSON → `resultVar` set → `choice` → `succeed`. |
| S-001-02 | HTTP 500 → result.ok=false; `choice` routes to `fail`. |
| S-001-03 | No choice matched → transition to `default` → `fail`. |

## Test Strategy
- Parser: round‑trip parse/validate valid and invalid specs.
- Engine: state‑by‑state execution with a stubbed HTTP client (no real network in CI).
- Runner: happy‑path and failure exit codes.

## Interface & Contract Catalogue
### Domain Objects (model, package suggestions)
- `io.journeyforge.model.Workflow`, `State` (sealed: `TaskState`, `ChoiceState`, `SucceedState`, `FailState`).
- `io.journeyforge.model.TaskSpec.HttpCall`.

### Runner
- Temporary CLI entry (to be hosted in `journeyforge-cli` later). For now, a simple Java `main` in tests or the existing `app` module is acceptable.
