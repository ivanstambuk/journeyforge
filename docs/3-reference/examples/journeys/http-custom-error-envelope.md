# Journey – http-custom-error-envelope

> Order-style HTTP journey that exposes a single, journey-specific custom error envelope derived from canonical RFC 9457 Problem Details.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-custom-error-envelope.journey.yaml](../technical/http-custom-error-envelope/http-custom-error-envelope.journey.yaml) |
| OpenAPI (per-journey) | [http-custom-error-envelope.openapi.yaml](../technical/http-custom-error-envelope/http-custom-error-envelope.openapi.yaml) |
| Arazzo workflow | [http-custom-error-envelope.arazzo.yaml](../technical/http-custom-error-envelope/http-custom-error-envelope.arazzo.yaml) |

## Summary

Order-style HTTP journey that exposes a single, journey-specific custom error envelope derived from canonical RFC 9457 Problem Details.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-custom-error-envelope.journey.yaml` and the `JourneyStartRequest` schema in `http-custom-error-envelope.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-custom-error-envelope.journey.yaml` and the `JourneyOutcome.output` schema in `http-custom-error-envelope.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-custom-error-envelope.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-custom-error-envelope` journey instance (synchronous). | `httpCustomErrorEnvelope_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`. | `JourneyOutcome` for this journey. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpCustomErrorEnvelope_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-custom-error-envelope-sequence.png" alt="http-custom-error-envelope – sequence" width="420" />

### State diagram

<img src="diagrams/http-custom-error-envelope-state.png" alt="http-custom-error-envelope – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-custom-error-envelope-activity.png" alt="http-custom-error-envelope – activity flow" width="420" />

## Implementation notes

- See `http-custom-error-envelope.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-custom-error-envelope.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-custom-error-envelope.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
