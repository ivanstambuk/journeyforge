# Journey – http-conditional-composition

> HTTP journey that conditionally composes different upstream endpoints, headers, and bodies based on context.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-conditional-composition.journey.yaml](../technical/http-conditional-composition/http-conditional-composition.journey.yaml) |
| OpenAPI (per-journey) | [http-conditional-composition.openapi.yaml](../technical/http-conditional-composition/http-conditional-composition.openapi.yaml) |
| Arazzo workflow | [http-conditional-composition.arazzo.yaml](../technical/http-conditional-composition/http-conditional-composition.arazzo.yaml) |

## Summary

HTTP journey that conditionally composes different upstream endpoints, headers, and bodies based on context.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-conditional-composition.journey.yaml` and the `JourneyStartRequest` schema in `http-conditional-composition.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-conditional-composition.journey.yaml` and the `JourneyOutcome.output` schema in `http-conditional-composition.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-conditional-composition.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-conditional-composition` journey instance. | `httpConditionalComposition_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `httpConditionalComposition_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-conditional-composition-sequence.png" alt="http-conditional-composition – sequence" width="620" />

### State diagram

<img src="diagrams/http-conditional-composition-state.png" alt="http-conditional-composition – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-conditional-composition-activity.png" alt="http-conditional-composition – activity flow" width="420" />

## Implementation notes

- See `http-conditional-composition.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-conditional-composition.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-conditional-composition.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
