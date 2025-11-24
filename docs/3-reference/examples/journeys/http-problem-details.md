# Journey – http-problem-details

> HTTP journey that normalises downstream errors into RFC 9457 Problem Details and can either succeed with a Problem document or fail with a mapped error code.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-problem-details.journey.yaml](../technical/http-problem-details/http-problem-details.journey.yaml) |
| OpenAPI (per-journey) | [http-problem-details.openapi.yaml](../technical/http-problem-details/http-problem-details.openapi.yaml) |
| Arazzo workflow | [http-problem-details.arazzo.yaml](../technical/http-problem-details/http-problem-details.arazzo.yaml) |

## Summary

HTTP journey that normalises downstream errors into RFC 9457 Problem Details and can either succeed with a Problem document or fail with a mapped error code.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-problem-details.journey.yaml` and the `JourneyStartRequest` schema in `http-problem-details.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-problem-details.journey.yaml` and the `JourneyOutcome.output` schema in `http-problem-details.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-problem-details.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-problem-details` journey instance (synchronous). | `httpProblemDetails_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`. | `JourneyOutcome` for this journey. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpProblemDetails_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-problem-details-sequence.png" alt="http-problem-details – sequence" width="420" />

### State diagram

<img src="diagrams/http-problem-details-state.png" alt="http-problem-details – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-problem-details-activity.png" alt="http-problem-details – activity flow" width="420" />

## Implementation notes

- See `http-problem-details.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-problem-details.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-problem-details.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
