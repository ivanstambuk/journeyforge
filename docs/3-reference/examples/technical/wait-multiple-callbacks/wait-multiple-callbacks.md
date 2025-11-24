# Technical Pattern – wait-multiple-callbacks

Status: Draft | Last updated: 2025-11-23

## Problem

Model journeys that need to accept **N callback submissions**, where **N is only known at runtime**, without introducing dynamic parallel loops or per-callback step ids. The goal is to keep the DSL surface small while still handling “wait for N callbacks, then continue” using existing `wait` semantics.

## When to use

- You know how many callbacks you expect for a journey instance (`expectedCount`), but not at design time.
- You are happy to expose a **single** step endpoint (for example `/steps/waitForCallback`) and call it multiple times.
- You want to aggregate callback payloads or counts before proceeding to the rest of the journey.
- You explicitly do **not** need dynamic fan-out into N independent steps or branches.

## Relevant DSL features

- `type: wait` – external-input step that can be revisited in a loop.
- `transform` – to initialise counters and aggregates in `context`.
- `spec.input.schema` / `spec.output.schema` – to describe start and outcome contracts.
- `JourneyStatus` and `/steps/{stepId}` – standard step surface from the Journeys API.

See also:
- DSL reference – External-Input & Timer States (`wait`/`webhook`).
- DSL reference – Limitations (no dynamic parallel loops / map state).

## Example – wait-multiple-callbacks

Artifacts for this example:

- Journey: [wait-multiple-callbacks.journey.yaml](wait-multiple-callbacks.journey.yaml)
- OpenAPI: [wait-multiple-callbacks.openapi.yaml](wait-multiple-callbacks.openapi.yaml)
- Arazzo: [wait-multiple-callbacks.arazzo.yaml](wait-multiple-callbacks.arazzo.yaml)

High-level behaviour:

- Start request supplies:
  - `expectedCount` – how many callbacks must be processed before completion.
  - Optional `correlationId` – propagated into the journey context for observability.
- The journey initialises:
  - `context.callbacks = []`.
  - `context.receivedCount = 0`.
- The `waitForCallback` state:
  - Exposes `/steps/waitForCallback` with a flexible `payload` object.
  - On each submission:
    - Appends `payload` to `context.callbacks`.
    - Increments `context.receivedCount`.
  - Branches:
    - When `receivedCount < expectedCount` – loops back to `waitForCallback`.
    - When `receivedCount >= expectedCount` – moves to completion.
- A final transform projects an outcome object with:
  - `expectedCount`, `receivedCount`, aggregated `callbacks`, and `correlationId`.

```yaml
# Simplified sketch of the core loop
states:
  waitForCallback:
    type: wait
    wait:
      channel: manual
      input:
        schema:
          type: object
          properties:
            payload:
              type: object
          additionalProperties: true
      apply:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              callbacks: (context.callbacks default []) ++ [payload],
              receivedCount: (context.receivedCount default 0) + 1
            }
      on:
        - when:
            predicate:
              lang: dataweave
              expr: |
                (context.receivedCount default 0) >= (context.expectedCount default 0)
          next: projectOutcome
      default: waitForCallback
```

## Variations and combinations

- **Timeouts per callback window** – combine with a `timer` in a `parallel` state if you need “N callbacks OR timeout”.
- **Per-callback validation** – add extra `choice` or `transform` states around `waitForCallback` to validate or normalise each payload before storing it.
- **Per-participant correlation** – extend the callback input schema with participant identifiers and derive aggregation keys in DataWeave.

## Implementation notes

- All callbacks for a given journey instance share the same step id (`waitForCallback`); idempotence and retry handling should be considered at the API adapter level.
- Because there is no dynamic parallel loop, the engine processes callbacks one at a time per journey instance; horizontal parallelism comes from running many journey instances in parallel, not from spawning N branches inside one instance.

