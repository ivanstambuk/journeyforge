# Journey – http-post-json

> HTTP journey that performs a JSON POST, expects a 201 Created response, and returns the normalised result.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-post-json.journey.yaml](../technical/http-post-json/http-post-json.journey.yaml) |
| OpenAPI (per-journey) | [http-post-json.openapi.yaml](../technical/http-post-json/http-post-json.openapi.yaml) |
| Arazzo workflow | [http-post-json.arazzo.yaml](../technical/http-post-json/http-post-json.arazzo.yaml) |

## Summary

HTTP journey that performs a JSON POST, expects a 201 Created response, and returns the normalised result.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-post-json.journey.yaml` and the `JourneyStartRequest` schema in `http-post-json.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-post-json.journey.yaml` and the `JourneyOutcome.output` schema in `http-post-json.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-post-json.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-post-json` journey instance (synchronous). | `httpPostJson_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`; for the happy path, the POST returns a 201 result in `output`. | `JourneyOutcome` for this journey. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpPostJson_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-post-json-sequence.png" alt="http-post-json – sequence" width="420" />

### State diagram

<img src="diagrams/http-post-json-state.png" alt="http-post-json – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-post-json-activity.png" alt="http-post-json – activity flow" width="420" />

## Implementation notes

- See `http-post-json.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-post-json.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-post-json.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
