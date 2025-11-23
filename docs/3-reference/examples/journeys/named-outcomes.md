# Journey – named-outcomes

> Journey that classifies outcomes via `spec.outcomes` into low/high amount buckets and business-rule failures.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [named-outcomes.journey.yaml](../technical/named-outcomes/named-outcomes.journey.yaml) |
| OpenAPI (per-journey) | [named-outcomes.openapi.yaml](../technical/named-outcomes/named-outcomes.openapi.yaml) |
| Arazzo workflow | [named-outcomes.arazzo.yaml](../technical/named-outcomes/named-outcomes.arazzo.yaml) |

## Technical pattern

See [named-outcomes.md](../technical/named-outcomes/named-outcomes.md).

## Summary

Journey that classifies outcomes via `spec.outcomes` into low/high amount buckets and business-rule failures.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `named-outcomes.journey.yaml` and the `JourneyStartRequest` schema in `named-outcomes.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `named-outcomes.journey.yaml` and the `JourneyOutcome.output` schema in `named-outcomes.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `named-outcomes.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `named-outcomes` journey instance. | `namedOutcomes_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `namedOutcomes_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/named-outcomes-sequence.png" alt="named-outcomes – sequence" width="620" />

### State diagram

<img src="diagrams/named-outcomes-state.png" alt="named-outcomes – state transitions" width="320" />

### Activity diagram

<img src="diagrams/named-outcomes-activity.png" alt="named-outcomes – activity flow" width="420" />

## Implementation notes

- See `named-outcomes.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `named-outcomes.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `named-outcomes.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
