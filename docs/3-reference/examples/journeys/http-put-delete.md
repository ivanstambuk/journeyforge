# Journey – http-put-delete

> HTTP journey that updates an item via PUT and then deletes it via DELETE, branching on each result.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-put-delete.journey.yaml](../technical/http-put-delete/http-put-delete.journey.yaml) |
| OpenAPI (per-journey) | [http-put-delete.openapi.yaml](../technical/http-put-delete/http-put-delete.openapi.yaml) |
| Arazzo workflow | [http-put-delete.arazzo.yaml](../technical/http-put-delete/http-put-delete.arazzo.yaml) |

## Summary

HTTP journey that updates an item via PUT and then deletes it via DELETE, branching on each result.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-put-delete.journey.yaml` and the `JourneyStartRequest` schema in `http-put-delete.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-put-delete.journey.yaml` and the `JourneyOutcome.output` schema in `http-put-delete.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-put-delete.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-put-delete` journey instance. | `httpPutDelete_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `httpPutDelete_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-put-delete-sequence.png" alt="http-put-delete – sequence" width="420" />

### State diagram

<img src="diagrams/http-put-delete-state.png" alt="http-put-delete – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-put-delete-activity.png" alt="http-put-delete – activity flow" width="420" />

## Implementation notes

- See `http-put-delete.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-put-delete.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-put-delete.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
