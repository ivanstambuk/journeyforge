# Journey – http-content-check

> HTTP journey that combines status and content checks via DataWeave to decide success or failure.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-content-check.journey.yaml](../technical/http-content-check/http-content-check.journey.yaml) |
| OpenAPI (per-journey) | [http-content-check.openapi.yaml](../technical/http-content-check/http-content-check.openapi.yaml) |
| Arazzo workflow | [http-content-check.arazzo.yaml](../technical/http-content-check/http-content-check.arazzo.yaml) |

## Summary

HTTP journey that combines status and content checks via DataWeave to decide success or failure.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-content-check.journey.yaml` and the `JourneyStartRequest` schema in `http-content-check.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-content-check.journey.yaml` and the `JourneyOutcome.output` schema in `http-content-check.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-content-check.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-content-check` journey instance. | `httpContentCheck_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `httpContentCheck_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-content-check-sequence.png" alt="http-content-check – sequence" width="620" />

### State diagram

<img src="diagrams/http-content-check-state.png" alt="http-content-check – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-content-check-activity.png" alt="http-content-check – activity flow" width="420" />

## Implementation notes

- See `http-content-check.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-content-check.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-content-check.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
