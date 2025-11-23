# Journey – payment-callback

> Journey that models a payment with a webhook callback and shared secret, combining start, callback, and final result retrieval.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [payment-callback.journey.yaml](../technical/payment-callback/payment-callback.journey.yaml) |
| OpenAPI (per-journey) | [payment-callback.openapi.yaml](../technical/payment-callback/payment-callback.openapi.yaml) |
| Arazzo workflow | [payment-callback.arazzo.yaml](../technical/payment-callback/payment-callback.arazzo.yaml) |

## Summary

Journey that models a payment with a webhook callback and shared secret, combining start, callback, and final result retrieval.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `payment-callback.journey.yaml` and the `JourneyStartRequest` schema in `payment-callback.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `payment-callback.journey.yaml` and the `JourneyOutcome.output` schema in `payment-callback.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `payment-callback.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `payment-callback` journey instance. | `paymentCallback_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `waitForCallback` | Webhook callback that notifies the journey of the final payment status. | `paymentCallback_waitForCallback` | Path: `journeyId`; header: `X-Webhook-Secret`; body: payment status payload. | `$statusCode == 200` and callback accepted; journey status updated to SUCCESS or FAILED. | Updated `JourneyStatus` after processing the payment callback. |
| 3 | `getResult` | Poll for the final journey outcome once terminal. | `paymentCallback_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/payment-callback-sequence.png" alt="payment-callback – sequence" width="420" />

### State diagram

<img src="diagrams/payment-callback-state.png" alt="payment-callback – state transitions" width="320" />

### Activity diagram

<img src="diagrams/payment-callback-activity.png" alt="payment-callback – activity flow" width="420" />

## Implementation notes

- See `payment-callback.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `payment-callback.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `payment-callback.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
