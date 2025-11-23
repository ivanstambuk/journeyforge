# Journey – multitenant-routing

> HTTP journey that routes to different upstream services based on a tenant identifier.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [multitenant-routing.journey.yaml](../technical/multitenant-routing/multitenant-routing.journey.yaml) |
| OpenAPI (per-journey) | [multitenant-routing.openapi.yaml](../technical/multitenant-routing/multitenant-routing.openapi.yaml) |
| Arazzo workflow | [multitenant-routing.arazzo.yaml](../technical/multitenant-routing/multitenant-routing.arazzo.yaml) |

## Technical pattern

See [multitenant-routing.md](../technical/multitenant-routing/multitenant-routing.md).

## Summary

HTTP journey that routes to different upstream services based on a tenant identifier.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `multitenant-routing.journey.yaml` and the `JourneyStartRequest` schema in `multitenant-routing.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `multitenant-routing.journey.yaml` and the `JourneyOutcome.output` schema in `multitenant-routing.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `multitenant-routing.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `multitenant-routing` journey instance. | `multitenantRouting_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `multitenantRouting_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/multitenant-routing-sequence.png" alt="multitenant-routing – sequence" width="420" />

### State diagram

<img src="diagrams/multitenant-routing-state.png" alt="multitenant-routing – state transitions" width="320" />

### Activity diagram

<img src="diagrams/multitenant-routing-activity.png" alt="multitenant-routing – activity flow" width="420" />

## Implementation notes

- See `multitenant-routing.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `multitenant-routing.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `multitenant-routing.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
