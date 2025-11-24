# ADR-0018 – In-journey timer state (`type: timer`)

Date: 2025-11-23 | Status: Proposed

## Context

Today, JourneyForge supports:
- External-input states:
  - `type: wait` with `wait.timeoutSec` for manual/external input.
  - `type: webhook` with `webhook.timeoutSec` for callback input.
- Global execution deadlines:
  - `spec.execution.maxDurationSec` and `spec.execution.onTimeout` (ADR-0007).
- Top-level scheduling for future runs:
  - `task.kind: schedule` for scheduled journeys (ADR-0017).

However, there is no dedicated in-journey timer/sleep primitive. Authors who want to “pause a running journey instance for N minutes” must:
- Abuse `wait.timeoutSec` by creating a dummy external-input step with a timeout branch, or
- Push timing concerns into top-level scheduling or external schedulers, even when they only need an intra-instance delay.

This leads to several problems:
- Overloaded semantics for `wait.timeoutSec`:
  - It becomes unclear whether a given `wait` is truly waiting for user input, or merely acting as a timer.
- Awkward modelling patterns:
  - Authors may create “fake” `wait` states with schemas that are never used, just to get a timeout path.
- Poor separation of concerns:
  - Simple “sleep for N” requirements end up entangled with journey start/schedule semantics, encouraging controller journeys and boilerplate.

Other workflow engines (for example BPMN engines like Camunda, Temporal, and Netflix/Orkes Conductor) converge on a small set of durable wait primitives:
- Pure timers (sleep until duration or due time).
- External input/messages (human tasks, callbacks, signals).
- Top-level scheduling for creating new workflow instances.

JourneyForge already has the second and third primitives. This ADR decides whether and how to add the first as a dedicated state.

## Decision

We introduce a dedicated **`type: timer` state** for `kind: Journey` that represents a non-interactive, durable time-based delay inside a single journey instance.

High-level decision:
- The DSL gains a new state type: `type: timer`.
- Timer states:
  - Are only allowed in `kind: Journey` (including scheduled runs created via `task.kind: schedule`).
  - Represent a **durable in-journey pause**: execution stops at the timer, the instance is persisted, and a durable timer is scheduled.
  - Are **non-interactive**: they expose no step endpoint, no `input.schema`, and no payload.
  - Provide exactly one outcome: “timer fired”, which transitions to the configured `next` state.
- Modelling guidelines:
  - Use `type: timer` for pure delays inside a journey instance.
  - Use `type: wait` / `type: webhook` for external input with optional timeout semantics.
  - Use `task.kind: schedule` for top-level scheduling that creates future runs.

This keeps the three concerns (timer, external input, top-level scheduling) explicit and separate in the DSL.

## 1. DSL shape – `type: timer`

We extend the state model with a new state type:

```yaml
type: timer
timer:
  duration: <string|mapper>             # ISO-8601 duration, e.g. "PT5M" (xor with until)
  # xor:
  # until: <string|mapper>              # RFC 3339 timestamp, e.g. "2025-12-31T23:59:00Z"
next: <stateId>
```

Key properties:
- Journeys only:
  - `type: timer` is only valid in specs with `kind: Journey`.
  - Timer states are allowed on both interactive runs and scheduled runs created via `task.kind: schedule`.
- XOR rule:
  - Exactly one of `timer.duration` or `timer.until` MUST be present.
  - Both may accept either a literal string or a DataWeave mapper with `lang: dataweave` and `expr` that returns the effective string.
- `duration`:
  - When present as a literal string, it MUST be an ISO-8601 duration (for example `PT5M`, `PT1H`, `P1D`).
  - When present as a mapper, it MUST evaluate to such a duration string at runtime.
- `until`:
  - When present as a literal string, it MUST be an RFC 3339 timestamp.
  - When present as a mapper, it MUST evaluate to such a timestamp string at runtime.
- `next`:
  - Required; MUST refer to a valid state id in the same journey.

## 2. Semantics – durable in-journey pause

### 2.1 Entering a timer state

When a journey instance enters a `type: timer` state:
- The engine evaluates `timer.duration` or `timer.until` according to the XOR rule:
  - If `duration` is used:
    - Evaluate any mapper (if present) to a duration string.
    - Compute an absolute due time `dueAt = now + duration`, using the engine’s clock.
  - If `until` is used:
    - Evaluate any mapper (if present) to a timestamp string.
    - Parse the RFC 3339 timestamp as `dueAt`.
- The engine persists:
  - The journey instance state (context, currentState, metadata).
  - A durable timer record that includes `journeyId`, the timer state id, and `dueAt`.
- The engine returns control to the host platform:
  - No worker thread or HTTP request is blocked waiting for the timer.
  - The journey remains observable via status/result APIs as usual.

Conceptually, the journey is “paused at a timer”, but operationally this is a durable wait state, not a blocking sleep.

### 2.2 Firing a timer

When `dueAt` is reached (or shortly after, depending on scheduler behaviour):
- The scheduler identifies the timer record and loads the corresponding journey instance.
- If the journey is still `RUNNING` and its `currentState` is the same timer state:
  - The engine resumes execution and transitions to the configured `next` state.
- If the journey is no longer running (for example it was cancelled or timed out globally):
  - The engine MUST treat the timer record as stale and discard it; it MUST NOT start a new run or resurrect a terminated instance.

The timer state itself does not mutate `context` implicitly. Any state that follows `next` observes whatever `context` existed at the time the timer was armed.

### 2.3 Relationship with global execution deadlines

Global execution deadlines are defined via `spec.execution.maxDurationSec` and `spec.execution.onTimeout` (ADR-0007). Timer semantics must respect this:
- The timer does not extend the global deadline:
  - If the effective `dueAt` is after the global deadline, the journey may still be accepted, but the instance will terminate via the global timeout if it fails to complete before the deadline.
- Engines SHOULD document how they clamp or handle timers that are effectively “after the global deadline”, but this ADR does not introduce a new validation rule that rejects such specs.
- When a journey terminates via the global execution timeout while parked at a timer, the outcome uses the existing timeout semantics (`terminationKind = Timeout`), not a special timer-specific error.

## 3. Interaction, cancellation, and parallelism

Timer states are non-interactive: there is no step endpoint, no `input.schema`, and no `onTimeout` on the state itself. Interaction during a timer is expressed via other DSL constructs:

- Journey-level cancellation:
  - While a journey is paused at a timer, any existing cancellation mechanisms continue to apply:
    - For example, when `spec.lifecycle.cancellable == true`, `_links.cancel` allows callers to cancel the journey.
  - Cancellation semantics do not change:
    - Cancelling a journey at a timer state terminates the instance with the usual cancellation outcome (for example `terminationKind = Cancel`).
    - The engine MUST discard any associated timer record as part of termination.

- Parallel flows:
  - To model “timer or user action” patterns, authors SHOULD use `type: parallel`:
    - One branch can be a `type: timer` that represents a timeout path.
    - Another branch can use `type: wait` or `type: webhook` (or other states) to represent user/system actions.
  - Join semantics (for example, which branch “wins” or how the result is merged) are expressed explicitly via downstream states (for example `transform` or `choice` states that inspect context).

This mirrors patterns from BPMN engines (timers vs message events) and Temporal (timer vs signal) while keeping the DSL surface small and explicit.

## 4. Scope and limits

- `kind: Journey` only:
  - Timer states are only allowed in `kind: Journey`.
  - Specs with `kind: Api` MUST NOT declare `type: timer` states.
- Coarse-grained timing:
  - The DSL does not prescribe a minimum or maximum duration.
  - Engines MAY enforce operational limits (for example minimum resolution or maximum duration) via configuration or platform policy.
- Durability:
  - Timers MUST be durable:
    - They MUST survive engine restarts and deployments.
    - After a restart, if `dueAt` has already passed, the engine MUST fire the timer as soon as possible (subject to scheduler and load).
  - Engines MAY batch or coalesce timers internally; this ADR does not require exact scheduling precision.
- No implicit context fields:
  - Timer states do not automatically add fields such as `firedAt` or `lastTimerDuration` to `context`.
  - Authors who need such metadata can add explicit `transform` states around the timer.

## 5. Alternatives considered

### B) Channel-based timers on `type: wait`

One option considered during earlier design work was to extend the existing `wait` state with a special channel (for example `wait.channel: timer`) and additional fields for duration/target time.

We reject this option because:
- It overloads `wait`:
  - `wait` currently represents external input, backed by step endpoints and `input.schema`.
  - Adding a `channel: timer` variant that disables input and step behaviour makes the state harder to reason about.
- Validation and documentation become more complex:
  - Many validation rules for `wait` (for example `input.schema` required, `timeoutSec` plus `onTimeout`) would not apply when `channel: timer`.
- It blurs the distinction between “waiting for a user/system action” and “sleeping until a time”.

Keeping `type: wait` focused on external input and `type: timer` focused on time-based control flow yields a clearer DSL.

### C) No timer state; only `wait.timeoutSec` and top-level scheduling

Another option considered was to avoid timers in the DSL entirely and rely on:
- `wait.timeoutSec` to model “fail over to a branch after N seconds”, and
- `task.kind: schedule` or external schedulers to model time-driven behaviour.

We reject this option because:
- It continues the overloading of `wait.timeoutSec`:
  - Authors must create artificial external-input states with schemas that are never used, just to get a timeout.
- It pushes simple in-journey delays into top-level scheduling:
  - Authors are forced to create controller journeys or schedules for internal pauses, which increases boilerplate and cognitive load.

Adding a small, explicit `type: timer` state provides a better modelling tool with minimal surface area.

## 6. Consequences

Positive:
- **Clear separation of concerns**:
  - `type: timer` for pure time-based delays within a journey instance.
  - `type: wait` / `type: webhook` for external input with optional timeouts.
  - `task.kind: schedule` for creating future runs.
- **Durable, non-blocking timers**:
  - Engines can implement timers as durable records, avoiding blocked threads or long-lived HTTP requests.
- **Better expressiveness**:
  - Authors can describe “sleep until duration/until” semantics directly in the DSL, without fake steps or controller journeys.
- **Predictable interaction semantics**:
  - While a timer is active, interaction is via parallel branches and journey-level cancellation, matching patterns from other workflow engines without introducing new protocol surfaces.

Negative / trade-offs:
- Adds a new state type:
  - Engine implementations and tooling must be updated to understand `type: timer`.
  - Exporters have no HTTP-visible surface for timers; they are internal control-flow nodes only.
- Operational complexity:
  - Durable timers require a scheduler/timer subsystem and careful handling for large numbers of long-lived timers.

## 7. References

- ADR-0007 – execution deadlines and `spec.execution.maxDurationSec`.
- ADR-0017 – scheduled journeys (`task.kind: schedule`).
- DSL reference – `docs/3-reference/dsl.md` sections on states, external-input states, and execution deadlines (updated to include `type: timer` semantics).
