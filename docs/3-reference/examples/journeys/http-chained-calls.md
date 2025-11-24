# Journey – http-chained-calls

> Chained HTTP journey that retrieves a user and then uses values from the first response to create an account.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-chained-calls.journey.yaml](../technical/http-chained-calls/http-chained-calls.journey.yaml) |
| OpenAPI (per-journey) | [http-chained-calls.openapi.yaml](../technical/http-chained-calls/http-chained-calls.openapi.yaml) |
| Arazzo workflow | [http-chained-calls.arazzo.yaml](../technical/http-chained-calls/http-chained-calls.arazzo.yaml) |

## Technical pattern

See [http-chained-calls.md](../technical/http-chained-calls/http-chained-calls.md).

## Summary

Chained HTTP journey that retrieves a user and then uses values from the first response to create an account.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-chained-calls.journey.yaml` and the `JourneyStartRequest` schema in `http-chained-calls.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-chained-calls.journey.yaml` and the `JourneyOutcome.output` schema in `http-chained-calls.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-chained-calls.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-chained-calls` journey instance (synchronous). | `httpChainedCalls_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`. | `JourneyOutcome` for this journey. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpChainedCalls_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-chained-calls-sequence.png" alt="http-chained-calls – sequence" width="420" />

### State diagram

<img src="diagrams/http-chained-calls-state.png" alt="http-chained-calls – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-chained-calls-activity.png" alt="http-chained-calls – activity flow" width="420" />

## Implementation notes

- See `http-chained-calls.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-chained-calls.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-chained-calls.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
