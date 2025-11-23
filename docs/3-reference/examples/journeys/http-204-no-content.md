# Journey – http-204-no-content

> HTTP journey that expects an upstream 204 No Content status and succeeds only when that status is observed.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-204-no-content.journey.yaml](../technical/http-204-no-content/http-204-no-content.journey.yaml) |
| OpenAPI (per-journey) | [http-204-no-content.openapi.yaml](../technical/http-204-no-content/http-204-no-content.openapi.yaml) |
| Arazzo workflow | [http-204-no-content.arazzo.yaml](../technical/http-204-no-content/http-204-no-content.arazzo.yaml) |

## Summary

HTTP journey that expects an upstream 204 No Content status and succeeds only when that status is observed.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-204-no-content.journey.yaml` and the `JourneyStartRequest` schema in `http-204-no-content.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-204-no-content.journey.yaml` and the `JourneyOutcome.output` schema in `http-204-no-content.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-204-no-content.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-204-no-content` journey instance. | `http204NoContent_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `http204NoContent_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-204-no-content-sequence.png" alt="http-204-no-content – sequence" width="420" />

### State diagram

<img src="diagrams/http-204-no-content-state.png" alt="http-204-no-content – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-204-no-content-activity.png" alt="http-204-no-content – activity flow" width="420" />

## Implementation notes

- See `http-204-no-content.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-204-no-content.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-204-no-content.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
