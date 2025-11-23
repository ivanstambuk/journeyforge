# ADR-0008 – Global Compensation Journey (spec.compensation)

Date: 2025-11-20 | Status: Proposed

## Context
JourneyForge journeys often coordinate side-effecting operations across multiple systems: HTTP mutations, database writes, emitted events, and so on. When a journey fails, times out, or is cancelled, operators frequently need to **undo or mitigate** some of these effects:
- Cancel charges or reservations in downstream systems.
- Release inventory or locks.
- Publish compensating events for consumers.

Without a first-class compensation construct, authors must:
- Thread compensation logic manually through the main state machine (for example via extra branches and states after every failure), or
- Maintain separate “rollback journeys” or auxiliary specs out-of-band and wire them up imperatively from the engine or callers.

This has drawbacks:
- Compensation flows are harder to discover and reason about; they are not co-located with the main journey spec.
- There is no standard way to access the final `context` and the reason for termination when performing rollback.
- Engine implementations cannot provide consistent telemetry or guarantees around when and how compensation runs.

Separately, we already plan subjourney support and SAGA-like patterns (see `docs/ideas`), but those are more fine-grained. We need a **coarse-grained, optional global compensation path** that can be attached to any journey definition or API.

While implementing business journeys such as `travel-booking-bundle`, we also identified a need to run compensation-style cleanup for **partial-success** outcomes (for example, when some segments are confirmed and others are not) without changing the final `JourneyOutcome.phase` from `Succeeded`. This follow-up requirement is reflected in the updated semantics below.

## Decision
We introduce an optional `spec.compensation` block that defines a **global compensation journey** for a journey definition or API:

```yaml
spec:
  execution:
    maxDurationSec: 60
    onTimeout:
      errorCode: ORDER_TIMEOUT
      reason: "Order orchestration exceeded 60s"

  compensation:
    mode: async                  # async (default) | sync
    start: rollback
    alsoFor:                     # optional; success-only triggers (for example partial success)
      - when:
          predicate:
            lang: dataweave
            expr: |
              output.overallStatus == "PARTIALLY_CONFIRMED"
    states:
      rollback:
        type: task
        task:
          kind: httpCall
          operationRef: billing.cancelCharge
          resultVar: cancelResult
        next: undo_inventory

      undo_inventory:
        type: task
        task:
          kind: httpCall
          operationRef: inventory.releaseReservation
          resultVar: inventoryResult
        next: notify

      notify:
        type: task
        task:
          kind: eventPublish
          eventPublish:
            transport: kafka
            topic: order.compensation
            value:
              mapper:
                lang: dataweave
                expr: |
                  {
                    orderId: context.orderId,
                    compensation: "completed",
                    cause: outcome
                  }
        next: done

      done:
        type: succeed
```

Core semantics:
- `spec.compensation` is **optional** and available for both `kind: Journey` and `kind: Api`.
- When the main run terminates with a **non-successful outcome** (fail, timeout, cancel, runtime error) and `spec.compensation` is present, the engine:
  - Starts a compensation journey using `compensation.states` and `compensation.start`.
  - Executes it with:
    - `context`: a deep copy of the main journey’s context at termination.
    - `outcome`: structured metadata about how and why the main run ended.
- Successful runs:
  - By default (when `alsoFor` is absent), successful runs behave as in the original version of this ADR and do **not** trigger compensation.
  - When `alsoFor` is present, the engine:
    - Evaluates each `alsoFor` rule after a successful termination (`JourneyOutcome.phase = Succeeded`), in a deterministic order (for example insertion order).
    - Treats a rule as matching when its predicate (when present) evaluates to `true` using the final journey context and bindings such as `output` (for example when `output.overallStatus == "PARTIALLY_CONFIRMED"`).
    - Triggers compensation once if at least one rule matches, using the same `mode` semantics as for non-successful outcomes.
    - Does nothing when no rule matches.

Bindings for compensation:
- `context` – same shape as the main journey `context`, copied at termination.
- `outcome` – a read-only object available to predicates and mappers in the compensation states, conceptually:
  ```json
  {
    "phase": "Succeeded or Failed",
    "terminationKind": "Success | Fail | Timeout | Cancel | RuntimeError",
    "error": {
      "code": "string or null",
      "reason": "string or null"
    },
    "terminatedAtState": "stateId or null",
    "journeyId": "string or null",
    "journeyName": "string"
  }
  ```

Sync vs async:
- `mode: async` (default when omitted):
  - Main run returns immediately after it terminates (API response is sent / `JourneyOutcome` is written).
  - Compensation runs in a separate journey instance in the background.
  - Compensation success/failure does not change the original outcome but SHOULD be logged/traced.
- `mode: sync`:
  - Main run does not complete until the compensation journey finishes.
  - For APIs, the HTTP response still reflects the main outcome (for failures, the error code/reason, possibly mapped to HTTP status via `spec.errors` / `spec.outcomes`), not the result of compensation.
- For journeys, `JourneyOutcome.phase` and `JourneyOutcome.error` continue to describe the original outcome from the main run (for example a Failed outcome or a Succeeded partial-success outcome); compensation errors may be attached as extensions but MUST NOT change `phase`.

The DSL reference (`docs/3-reference/dsl.md`) is updated with a new section “2.5 Global compensation (spec.compensation)” capturing this shape and semantics.

## Consequences
- Pros:
  - Makes compensation journeys first-class and discoverable in the same spec file as the main journey/API.
  - Provides a consistent way to access both the final `context` and termination metadata, simplifying rollback logic.
  - Supports both synchronous and asynchronous compensation modes, matching different operational needs.
  - Aligns with future subjourney and SAGA features without requiring them for basic global compensation.
- Cons:
  - Adds another axis of behaviour that the engine must implement carefully (especially with retries, idempotency, and crash recovery).
  - Compensation execution is inherently best-effort; the DSL cannot guarantee that all external side effects are fully undone.
  - Error handling for compensation itself (partial failures) needs clear telemetry and possibly future extensions (for example, surfacing compensation status in `JourneyOutcome`).

Implementation guidance:
- Engine implementations that do not yet support compensation journeys SHOULD:
  - Reject specs that declare `spec.compensation`, or
  - Treat `spec.compensation` as unsupported and clearly document this limitation.
- Engine implementations that support compensation SHOULD:
  - Start compensation journeys reliably when main runs fail, time out, or are cancelled.
  - Tag compensation instances with a `parentJourneyId` (or similar) to allow correlation.
  - Expose metrics and traces for compensation execution (latency, success/failure, skipped/no-op runs).
