# ADR-0005 – HTTP Task Notify Mode (Fire-and-Forget)

Date: 2025-11-20 | Status: Proposed

## Context

In the DSL, `task` states with `kind: httpCall` are defined as synchronous, request/response operations:
- The engine sends an HTTP request, waits for a response (or timeout/error), and records a structured result object at `context.<resultVar>`.
- Journey definitions branch on or transform this result using `choice`, `transform`, and `errorMapping`.

This is appropriate when the journey instance needs to *react* to the HTTP outcome (status, body, errors). However, some scenarios only need to:
- Trigger a downstream HTTP side-effect (for example, send a notification, audit, or webhook).
- Not block the journey on the HTTP response, and
- Not change control flow based on whether the call eventually succeeds or fails.

From the journey’s perspective, these are “fire-and-forget” calls: send the request and conceptually move on.

We want a way to describe this intent in the DSL without introducing a completely separate connector type.

## Decision

We extend `task` with `kind: httpCall` to support a `mode` field:

```yaml
type: task
task:
  kind: httpCall
  mode: requestResponse | notify   # optional; default requestResponse
  # ...
```

Semantics:
- `mode: requestResponse` (default; existing behaviour):
  - The engine sends the HTTP request and waits for a response (or timeout/error).
  - It builds the structured result object `{status?, ok, headers, body, error?}` and stores it at `context.<resultVar>`.
  - Workflows can inspect `context.<resultVar>` via `choice`, `transform`, or `errorMapping`.
- `mode: notify` (fire-and-forget from the journey’s perspective):
  - The engine sends the HTTP request but does not expose any HTTP outcome to the journey:
    - No `resultVar` is written.
    - `errorMapping` is not evaluated.
  - Execution proceeds immediately to `next`, regardless of network or protocol errors.
  - Implementations may still log failures, apply retries, or enforce policies, but these concerns are invisible to the DSL state machine.

Validation rules:
- When `mode` is omitted, it is treated as `requestResponse`.
- When `mode: notify`:
  - `resultVar` MUST be omitted.
  - `errorMapping` MUST be omitted.
  - `resiliencePolicyRef` MAY be ignored by the engine; retries are allowed as an implementation detail but MUST NOT affect control flow.

## Consequences

Positive:
- Journey definitions can capture common “send-and-continue” patterns (notifications, audit pings, best-effort webhooks) without wasting context fields or pretending to care about the HTTP response.
- The DSL surface remains small: we add a single `mode` flag instead of a new state type or connector.
-- Implementations can optimise `notify` calls (for example, buffering or asynchronous IO) while keeping the state machine semantics simple: from the journey’s point of view, the call never blocks on a response.

Negative / trade-offs:
- The “fire-and-forget” behaviour is best-effort: since the journey does not see HTTP outcomes, failures can only be observed via logs/metrics. This is by design but must be clearly documented.
- Engine implementations must be careful not to leak implementation details (for example, by partially populating a result object) in `notify` mode; the contract is that the journey never sees call outcomes.

Future considerations:
- A dedicated “event” connector (for example, Kafka/queue publish) may later be introduced as a separate `kind` under `task` (e.g., `kind: eventPublish`) for non-HTTP transports. For now, we focus on HTTP and keep event-style semantics scoped to `mode: notify`.
