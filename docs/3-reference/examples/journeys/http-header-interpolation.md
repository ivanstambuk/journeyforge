# Journey – http-header-interpolation

> HTTP journey that injects a `traceparent` header from context, verifies it is echoed back, and returns the normalised response.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-header-interpolation.journey.yaml](../technical/http-header-interpolation/http-header-interpolation.journey.yaml) |
| OpenAPI (per-journey) | [http-header-interpolation.openapi.yaml](../technical/http-header-interpolation/http-header-interpolation.openapi.yaml) |
| Arazzo workflow | [http-header-interpolation.arazzo.yaml](../technical/http-header-interpolation/http-header-interpolation.arazzo.yaml) |

## Technical pattern

See [http-header-interpolation.md](../technical/http-header-interpolation/http-header-interpolation.md).

## Summary

HTTP journey that injects a `traceparent` header from context, verifies it is echoed back, and returns the normalised response.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-header-interpolation.journey.yaml` and the `JourneyStartRequest` schema in `http-header-interpolation.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-header-interpolation.journey.yaml` and the `JourneyOutcome.output` schema in `http-header-interpolation.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-header-interpolation.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-header-interpolation` journey instance. | `httpHeaderInterpolation_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `httpHeaderInterpolation_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-header-interpolation-sequence.png" alt="http-header-interpolation – sequence" width="620" />

### State diagram

<img src="diagrams/http-header-interpolation-state.png" alt="http-header-interpolation – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-header-interpolation-activity.png" alt="http-header-interpolation – activity flow" width="420" />

## Implementation notes

- See `http-header-interpolation.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-header-interpolation.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-header-interpolation.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
