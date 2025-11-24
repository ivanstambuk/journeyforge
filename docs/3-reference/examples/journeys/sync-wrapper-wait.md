# Journey – sync-wrapper-wait

> Journey that wraps a long-running operation using a `wait` state and a completion event submitted via a step endpoint.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [sync-wrapper-wait.journey.yaml](../technical/sync-wrapper-wait/sync-wrapper-wait.journey.yaml) |
| OpenAPI (per-journey) | [sync-wrapper-wait.openapi.yaml](../technical/sync-wrapper-wait/sync-wrapper-wait.openapi.yaml) |
| Arazzo workflow | [sync-wrapper-wait.arazzo.yaml](../technical/sync-wrapper-wait/sync-wrapper-wait.arazzo.yaml) |

## Technical pattern

See [sync-wrapper-wait.md](../technical/sync-wrapper-wait/sync-wrapper-wait.md).

## Summary

Journey that wraps a long-running operation using a `wait` state and a completion event submitted via a step endpoint.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `sync-wrapper-wait.journey.yaml` and the `JourneyStartRequest` schema in `sync-wrapper-wait.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `sync-wrapper-wait.journey.yaml` and the `JourneyOutcome.output` schema in `sync-wrapper-wait.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `sync-wrapper-wait.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `sync-wrapper-wait` journey instance (synchronous to the first wait step). | `syncWrapperWait_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "RUNNING"`, and `currentState` points at `waitStep`. | `JourneyStatus` at the first external-input state. |
| 2 | `submitCompletionEvent` | Submit a completion event for the long-running operation via the `waitStep` endpoint. | `syncWrapperWait_waitStep` | Path: `journeyId`; body: completion event payload. | `$statusCode == 200` and journey moves out of the waiting state. | Updated `JourneyStatus` after the completion event. |
| 3 | `getResult` | Poll for the final journey outcome once terminal. | `syncWrapperWait_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/sync-wrapper-wait-sequence.png" alt="sync-wrapper-wait – sequence" width="420" />

### State diagram

<img src="diagrams/sync-wrapper-wait-state.png" alt="sync-wrapper-wait – state transitions" width="320" />

### Activity diagram

<img src="diagrams/sync-wrapper-wait-activity.png" alt="sync-wrapper-wait – activity flow" width="420" />

## Implementation notes

- See `sync-wrapper-wait.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `sync-wrapper-wait.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `sync-wrapper-wait.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
