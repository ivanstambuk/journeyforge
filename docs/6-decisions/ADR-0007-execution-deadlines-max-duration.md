# ADR-0007 – Execution Deadlines and Global maxDurationSec

Date: 2025-11-20 | Status: Proposed

## Context
Workflows and API endpoints already support per-state timeouts:
- HTTP `task` states use `timeoutMs` (or `spec.defaults.http.timeoutMs`) to bound individual calls.
- External-input states (`wait`, `webhook`) use `timeoutSec` and `onTimeout` to control how long a step waits for an event.
- The DataWeave evaluator is expected to enforce its own timeouts and resource limits.

However, there is no spec-visible way to express a **single overall execution budget** for a journey or API invocation. This has several drawbacks:
- A misconfigured loop or a long chain of calls can run for an unbounded amount of time unless the platform hard-cuts the process.
- SLOs and operational expectations (for example “this flow should complete within 30 seconds”) cannot be captured in the workflow spec itself.
- Runtimes cannot easily clamp per-state timeouts to a remaining budget because the desired global deadline is not part of the DSL.

We want:
- A small, declarative way for authors to specify “how long this workflow/API is allowed to run”.
- A clear, predictable **timeout outcome** when that budget is exceeded.
- Semantics that work for both `kind: Workflow` (Journeys API) and `kind: Api` (synchronous HTTP endpoints), and that integrate with the existing error model (ADR‑0003).

This ADR is related to open question Q-007 in `docs/4-architecture/open-questions.md`.

## Decision
We introduce an optional `spec.execution` block that defines a global execution budget and a terminal timeout mapping.

Shape (DSL):

```yaml
spec:
  execution:
    maxDurationSec: 30           # overall wall-clock budget for this run
    onTimeout:
      errorCode: JOURNEY_TIMEOUT
      reason: "Overall execution time exceeded maxDurationSec"
```

Key points:
- `spec.execution` is allowed for both `kind: Workflow` and `kind: Api`.
- `maxDurationSec`:
  - Required when `spec.execution` is present.
  - Integer ≥ 1, representing wall-clock seconds from the moment the run is accepted until it completes.
  - Represents the *desired* budget; platform-level limits may still enforce stricter caps.
- `onTimeout`:
  - Required when `spec.execution` is present.
  - Contains `errorCode` and `reason`, which shape the timeout failure in the same way as a `fail` state:
    - For journeys, they map to `JourneyOutcome.phase = Failed` and `JourneyOutcome.error.{code,reason}`.
    - For APIs, they map to the error envelope exposed over HTTP, possibly transformed by `spec.errors` / `spec.outcomes`.

Execution semantics:
- Global deadline:
  - When `maxDurationSec` is configured, the engine starts a logical “deadline timer” when the run begins:
    - Workflows: when `/api/v1/journeys/{workflowName}/start` accepts the request.
    - APIs: when the HTTP request reaches the API endpoint.
  - If the elapsed wall-clock time reaches or exceeds `maxDurationSec` before the state machine hits `succeed` or `fail`, the run is considered timed out.
- Per-state timeouts:
  - Blocking operations which already support timeouts (`httpCall.timeoutMs`, `wait.timeoutSec`, `webhook.timeoutSec`) SHOULD be clamped to the remaining budget:
    - Conceptually: `effectiveTimeout = min(configuredTimeout, remainingBudget)`.
    - If the remaining budget is ≤ 0, the operation SHOULD NOT start and the run MUST be treated as timed out.
  - For operations without explicit per-state timeouts, runtimes MAY:
    - Interrupt long-running work when the global deadline is reached, or
    - Detect the timeout immediately after the operation completes, before scheduling the next state.
- Timeout outcome:
  - When the global deadline is reached, the engine stops scheduling new states and terminates the run using `spec.execution.onTimeout`:
    - For `kind: Workflow`: produce a `JourneyOutcome` with `phase = Failed` and `error.{code,reason}` taken from `onTimeout`.
    - For `kind: Api`: terminate the HTTP request with a non‑2xx response that exposes the same `code` and `reason`.
  - Exporters and runtimes:
    - MAY map global execution timeouts to HTTP 504 Gateway Timeout by default.
    - MAY use `spec.errors` / `spec.outcomes` (when present) to choose HTTP status codes and shapes for the error payload, as long as the resulting `error.code` remains a stable identifier (ADR‑0003).
- Platform limits:
  - Platform- or environment-level maximums (for example “no run longer than 60 seconds in this cluster”) MAY further restrict execution time.
  - Runtimes MAY clamp `spec.execution.maxDurationSec` to a configured upper bound; authors should not rely on being able to exceed platform caps by setting a large value.

Specification updates:
- The DSL reference (`docs/3-reference/dsl.md`) is updated to:
  - Introduce section “2c. Execution deadlines (spec.execution)” with the above shape, semantics, and validation rules.
  - Mention that execution may also terminate due to a global execution timeout, in addition to `succeed`/`fail` and runtime errors.
  - Refine `kind: Api` control-flow guidance to state that, when `spec.execution.maxDurationSec` is present, implementations MUST enforce this budget and fail with `spec.execution.onTimeout` when it is exceeded.

Implementation guidance:
- Feature 001 runtimes MAY initially treat `spec.execution` as unsupported (for example, by rejecting specs that declare it) if full deadline enforcement is not yet implemented.
- Runtimes that claim support for the execution-deadline feature SHOULD:
  - Enforce the global deadline as described above.
  - Expose timeout events in telemetry (logs/metrics/traces) using a stable reason or error code (for example `JOURNEY_TIMEOUT`).

## Consequences
- Pros:
  - Authors can express SLO-like expectations (“this journey should complete within N seconds”) directly in the DSL.
  - Global execution limits reduce the risk of runaway workflows and APIs hanging indefinitely.
  - Runtimes get a clear contract for clamping per-state timeouts and surfacing timeouts as a first-class error condition.
  - The timeout outcome reuses the existing error model (`fail` semantics, ADR‑0003) instead of introducing a new error envelope.
- Cons:
  - Adds another axis of configuration that may conflict with platform-level limits; tooling must help authors understand the effective budget.
  - Implementations must integrate deadline tracking with their schedulers, HTTP clients, and external-input handling, which increases runtime complexity.
  - Cases where the deadline expires while a state is mid-flight require careful handling to avoid partial side effects and confusing user experience.

Overall, this ADR makes global execution budgets explicit in the DSL while keeping the surface area small and aligned with the existing error and outcome model.
