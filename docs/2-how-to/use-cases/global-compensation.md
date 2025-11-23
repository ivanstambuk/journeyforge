# Use Case – Global Compensation Journey

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-compensation/http-compensation.md`

## Problem

Model a journey that:
- Performs a sequence of side-effecting operations (for example, payment authorization and inventory reservation).
- May fail, time out, or be cancelled part-way through.
- Needs a consistent “undo” path that:
  - Sees the final `context` of the main journey.
  - Knows *why* the main journey ended (fail vs timeout vs cancel).
  - Can call downstream systems to compensate (cancel charges, release inventory, publish compensation events).

## Relevant DSL Features

- `spec.execution` (global deadline) for bounding overall execution time.
- `spec.compensation` for defining a global compensation journey:
  - `mode: async | sync` to control whether callers wait for compensation.
  - `start` and `states` for the compensation state machine.
  - `context` and `outcome` bindings inside compensation states.
- `task` (HTTP call) and `eventPublish` for performing compensating actions.
- `choice` and `fail` to express main journey success vs failure.

## Example – http-compensation

Journey definition: `http-compensation.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-compensation
  version: 0.1.0
spec:
  execution:
    maxDurationSec: 30
    onTimeout:
      errorCode: ORDER_TIMEOUT
      reason: "Order orchestration exceeded 30s"

  apis:
    billing:
      openApiRef: apis/billing.openapi.yaml
    inventory:
      openApiRef: apis/inventory.openapi.yaml

  compensation:
    mode: async
    start: rollback
    states:
      rollback:
        type: task
        task:
          kind: httpCall
          operationRef: billing.cancelCharge
          params:
            path:
              orderId: "${context.orderId}"
          resultVar: cancelResult
        next: undo_inventory

      undo_inventory:
        type: task
        task:
          kind: httpCall
          operationRef: inventory.releaseReservation
          params:
            path:
              orderId: "${context.orderId}"
          resultVar: inventoryResult
        next: notify

      notify:
        type: task
        task:
          kind: eventPublish
          eventPublish:
            transport: kafka
            topic: orders.compensation
            value:
              mapper:
                lang: dataweave
                expr: |
                  {
                    orderId: context.orderId,
                    terminationKind: outcome.terminationKind,
                    error: outcome.error
                  }
        next: done

      done:
        type: succeed

  start: place_order
  states:
    place_order:
      type: task
      task:
        kind: httpCall
        operationRef: billing.authorizePayment
        params:
          body:
            mapper:
              lang: dataweave
              expr: |
                {
                  orderId: context.orderId,
                  amount: context.amount
                }
        resultVar: billingResult
      next: reserve_inventory

    reserve_inventory:
      type: task
      task:
        kind: httpCall
        operationRef: inventory.reserveItems
        params:
          body:
            mapper:
              lang: dataweave
              expr: |
                {
                  orderId: context.orderId,
                  items: context.items
                }
        resultVar: inventoryResult
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.billingResult.ok == true and context.inventoryResult.ok == true
          next: done
      default: failed

    done:
      type: succeed
      outputVar: inventoryResult

    failed:
      type: fail
      errorCode: ORDER_FAILED
      reason: "Order orchestration failed; compensation journey will attempt rollback"
```

### How it behaves

- Main journey:
  - Starts at `place_order`, authorises payment via `billing.authorizePayment`, then reserves inventory via `inventory.reserveItems`.
  - If both calls succeed (`ok == true`), the journey ends in `done` with `inventoryResult` as the output.
  - If either call fails or times out, the journey ends in `failed` with `errorCode: ORDER_FAILED`.
- Global execution deadline:
  - If the overall journey exceeds 30 seconds, the engine terminates it using `spec.execution.onTimeout` (`ORDER_TIMEOUT`).
- Compensation journey:
  - Because `spec.compensation` is present and `mode: async`, any non-successful termination (fail or timeout) triggers the compensation journey:
    - `rollback` calls `billing.cancelCharge` to cancel the payment.
    - `undo_inventory` calls `inventory.releaseReservation` to release reserved stock.
    - `notify` publishes an `orders.compensation` event to Kafka, using `outcome.terminationKind` and `outcome.error` to describe why the main journey ended.
  - Compensation runs in the background; the caller sees the main failure immediately, while operators can observe compensation status via logs, metrics, and traces.

### When to use this pattern

Use `spec.compensation` when:
- Your journey performs multi-step mutations across external systems.
- You need a consistent, centralised rollback path that has access to the final `context` and termination metadata.
- You want to keep compensation logic close to the main journey definition, rather than wiring it up separately in platform code.

You can switch `mode` to `sync` when callers must wait until compensation completes (for example, internal admin flows), while keeping `async` as the default for user-facing APIs where latency matters more than immediate rollback.
