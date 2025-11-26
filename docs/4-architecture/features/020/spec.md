# Feature 020 – CLI/Batch Binding for Journeys and APIs

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/020/plan.md` |
| Linked tasks | `docs/4-architecture/features/020/tasks.md` |
| Roadmap entry | #012 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md` (none currently open for this feature), encode resolved answers directly in the Requirements/NFR/Behaviour sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview

Introduce a CLI/batch inbound binding for `kind: Journey` and `kind: Api` definitions via `spec.bindings.cli`. The binding provides a spec-visible way to indicate that a definition is runnable via local CLI commands and batch job runners (cron, CI, Kubernetes `CronJob`, etc.), while reusing the same logical semantics as HTTP/gRPC bindings:
- For `kind: Journey`: start a journey instance from input provided on stdin or files and print a `JourneyOutcome` or `JourneyStatus`.
- For `kind: Api`: run the Api and print its response.

This feature is **DSL/API design only**; concrete CLI commands and process contracts are defined in CLI documentation and ops runbooks.

Primary references:
- DSL reference: `docs/3-reference/dsl.md` (section 17 – Inbound Bindings).
- Inbound bindings ADR: `docs/6-decisions/ADR-0029-inbound-bindings-and-spec-bindings-http.md`.

## Goals

- Define a minimal, future-proof DSL hook for CLI/batch invocation:
  - `spec.bindings.cli` with no fields in v1; presence is advisory metadata.
- Clarify the conceptual mapping between CLI invocations and existing logical operations:
  - For journeys: equivalent to calling the Journeys API start endpoint and waiting for a result/status.
  - For APIs: equivalent to a single HTTP/gRPC call.
- Keep transport details (process/flags/stdin/stdout) outside the DSL.

## Non-Goals

- No new state types or execution semantics tied specifically to CLI or jobs.
- No attempt to encode CLI flags, environment variables, or job scheduling into the DSL.
- No changes to the canonical HTTP/gRPC/queue binding semantics.

## DSL (normative)

### 1. `spec.bindings.cli`

Definitions MAY declare that they are intended to be runnable via CLI/batch tools:

```yaml
spec:
  bindings:
    cli: {}
```

Constraints:
- `spec.bindings.cli`:
  - MAY be present for both `kind: Journey` and `kind: Api`.
  - MUST be an object; in this version it MUST NOT define any fields.
- The absence of `spec.bindings.cli` does **not** prevent CLI tools from running the definition; presence is a declarative signal for tooling and operators.

### 2. Conceptual CLI contract

While concrete CLI syntax is out of scope, we standardise the conceptual contract:

- Input:
  - A CLI tool identifies the target definition (journey/API name and version) and environment.
  - Input for the definition is provided as JSON (or YAML) that MUST conform to `spec.input.schema` when present:
    - Either via stdin, or
    - Via a referenced file path.
- Execution:
  - For `kind: Journey`, the CLI invokes the logical “start journey” operation with the provided input as initial `context` and waits:
    - Either for a terminal `JourneyOutcome`, or
    - For a `JourneyStatus` snapshot, depending on CLI options and start mode.
  - For `kind: Api`, the CLI invokes the logical Api call with the provided input and waits for a single response.
- Output:
  - Successful CLI invocations write a JSON representation of:
    - `JourneyOutcome` (or `JourneyStatus`) for journeys, or
    - The Api response body for `kind: Api`,
    to stdout.
  - CLI exit codes distinguish:
    - “CLI/engine failed” (for example invalid spec, configuration error, unhandled exception) vs
    - “Journey/API ran successfully but outcome may be FAILED” (which is a normal business result).

These semantics mirror the HTTP/gRPC bindings, but over a local process boundary instead of a network protocol.

### 3. CLI command sketch (design-only)

This subsection sketches CLI commands and flags that make stdin→stdout chaining ergonomic. Exact command names and option syntax are CLI/tooling concerns, but implementations SHOULD follow these patterns.

#### 3.1 Journeys – start

Conceptual command:

```bash
journeyforge journey start <journeyName> \
  [--version <semver>] \
  [--env <environmentId>] \
  [--wait-until terminal|first-wait|none] \
  [--input-file <path>]
```

Rules:
- Input:
  - When `--input-file` is provided, the CLI reads JSON/YAML from that file.
  - Otherwise, it reads JSON from stdin.
  - In both cases, input must conform to `spec.input.schema` when present.
- `--wait-until`:
  - `terminal`:
    - Run from `spec.start` until a terminal state (`succeed`/`fail`) or a global timeout.
    - Print a `JourneyOutcome` JSON document to stdout.
  - `first-wait`:
    - Run until either a terminal state or the first external-input state (`wait`/`webhook`) is reached.
    - If terminal: print `JourneyOutcome`.
    - If paused: print `JourneyStatus` for the paused instance.
  - `none`:
    - Start the journey asynchronously.
    - Print a minimal `JourneyStartResponse` (for example `journeyId`, `journeyName`, status URL) to stdout.

#### 3.2 Journeys – submit external-input step

Conceptual command:

```bash
journeyforge journey submit-step \
  --journey-id <journeyId> \
  --step-id <stepId> \
  [--wait-until terminal|next-wait|none] \
  [--input-file <path>]
```

Rules:
- Input:
  - Step payload is read from `--input-file` (JSON/YAML) or stdin.
  - Payload must conform to the step’s `input.schema` when present.
- `--wait-until`:
  - `terminal`:
    - Resume the journey from the specified step and run until terminal state or timeout.
    - Print a `JourneyOutcome` JSON document to stdout.
  - `next-wait`:
    - Resume the journey and run until either:
      - The next external-input state is reached, or
      - A terminal state is reached.
    - Print `JourneyStatus` or `JourneyOutcome` accordingly.
  - `none`:
    - Submit the step and return immediately after the engine accepts the payload.
    - Print a minimal acknowledgement or updated `JourneyStatus` snapshot.

#### 3.3 Journeys – status and outcome helpers

For pipelines that prefer explicit polling, the CLI MAY also surface helper commands:

```bash
journeyforge journey status --journey-id <journeyId>   # prints JourneyStatus
journeyforge journey outcome --journey-id <journeyId>  # prints JourneyOutcome (terminal only)
```

These map directly onto the logical `getStatus` / `getOutcome` operations exposed by the Journeys API.

#### 3.4 APIs – invoke

Conceptual command:

```bash
journeyforge api call <apiName> \
  [--version <semver>] \
  [--env <environmentId>] \
  [--input-file <path>]
```

Rules:
- Input is read from `--input-file` or stdin and validated against `spec.input.schema` when present.
- Output is the Api’s response body, shaped by `spec.output.schema` and `spec.errors`, printed as JSON to stdout.
- Exit code differentiates “CLI/engine failure” (non-zero) from “Api returned a FAILED outcome” (zero exit, status encoded in the JSON).

## Behaviour (conceptual)

- CLI and job runners are just **another binding** onto the same logical engine operations:
  - No additional auth semantics are introduced; process-level auth is handled by OS/user policies.
  - Observability follows normal engine telemetry rules; CLI tooling may add thin wrappers (for example structured logs).
- Jobs:
  - Schedulers invoke the same CLI commands on a schedule; from the engine’s perspective this is indistinguishable from a human-driven CLI call.

## Non-Functional Requirements

| ID | Requirement | Driver | Notes |
|----|-------------|--------|-------|
| NFR-020-01 | Keep DSL hook minimal and stable. | Stability. | `spec.bindings.cli` must remain an additive, optional metadata block; future fields, if any, must be backwards compatible. |
| NFR-020-02 | Preserve a single behavioural model. | Simplicity. | CLI binding must not introduce semantics that cannot be expressed via HTTP/gRPC bindings. |
