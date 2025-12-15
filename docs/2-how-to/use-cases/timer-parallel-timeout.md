# Use Case – Timer-Based Timeout with Parallel + Timer State

Status: Draft | Last updated: 2025-11-23

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/payment-reminder-with-timeout/payment-reminder-with-timeout.md`

## Problem

Model a bounded waiting window where:
- A manual or external confirmation can arrive via a `wait` step.
- If confirmation does not arrive within a fixed time window (for example 24 hours), the journey should complete along a timeout path.
- The timeout is expressed as a first-class timer inside the journey, not as a `wait.timeoutSec` branch or an external scheduler.

## Relevant DSL Features

- `timer` state for durable in-journey delays (journeys only).
- `parallel` state with named branches and `join.strategy = anyOf`.
- `wait` for manual/external input via `/steps/{stepId}`.
- `transform` + `succeed` for shaping a final outcome.

## Example – payment-reminder-with-timeout

Journey definition: `payment-reminder-with-timeout.journey.yaml`

This journey:
- Sends a payment reminder via `payments.sendPaymentReminder`.
- Enters a `parallel` state with two branches:
  - `waitBranch` – a `wait` state that accepts a `paymentStatus` update and marks the outcome as `PAID`.
  - `timeoutBranch` – a `timer` state that waits 24 hours and then marks the outcome as `TIMED_OUT`.
- Uses `join.strategy: anyOf` so whichever branch completes first determines the final outcome.

Key control-flow fragment:

```yaml
waitOrTimeout:
  type: parallel
  parallel:
    branches:
      - name: waitBranch
        start: waitForPayment
        states:
          waitForPayment:
            type: wait
            wait:
              channel: manual
              input:
                schema:
                  title: PaymentConfirmationInput
                  type: object
                  required: [paymentStatus]
                  properties:
                    paymentStatus:
                      type: string
                      enum: [PAID]
                  additionalProperties: true
              default: ingestPaymentConfirmation

          ingestPaymentConfirmation:
            type: transform
            transform:
              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    paymentStatus: context.payload.paymentStatus
                  }
              target: { kind: context, path: "" }
            next: paymentReceived
          paymentReceived:
            type: transform
            transform:
              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    outcome: {
                      status: "PAID",
                      userId: context.userId,
                      paymentId: context.paymentId,
                      amount: context.amount,
                      currency: context.currency
                    }
                  }
              target:
                kind: context
                path: ""
            next: journeyDonePaid
          journeyDonePaid:
            type: succeed
            outputVar: outcome

      - name: timeoutBranch
        start: paymentTimer
        states:
          paymentTimer:
            type: timer
            timer:
              duration: "PT24H"
            next: timeoutFired
          timeoutFired:
            type: transform
            transform:
              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    outcome: {
                      status: "TIMED_OUT",
                      userId: context.userId,
                      paymentId: context.paymentId,
                      amount: context.amount,
                      currency: context.currency
                    }
                  }
              target:
                kind: context
                path: ""
            next: journeyDoneTimeout
          journeyDoneTimeout:
            type: succeed
            outputVar: outcome

    join:
      strategy: anyOf
      errorPolicy: collectAll
```

Related files:
- Journey: `docs/3-reference/examples/payment-reminder-with-timeout.journey.yaml`
- OpenAPI: `docs/3-reference/examples/oas/payment-reminder-with-timeout.openapi.yaml`
- Arazzo: `docs/3-reference/examples/arazzo/payment-reminder-with-timeout.arazzo.yaml`
- DSL reference:
  - Timer state – `docs/3-reference/dsl.md` section 12.3 (`type: timer`).
  - Parallel state – `docs/3-reference/dsl.md` section 16 (`type: parallel`).
