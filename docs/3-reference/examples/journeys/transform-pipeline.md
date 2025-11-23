# Journey – transform-pipeline

> Journey that runs a multi-step DataWeave transform pipeline for orders.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [transform-pipeline.journey.yaml](../technical/transform-pipeline/transform-pipeline.journey.yaml) |
| OpenAPI (per-journey) | [transform-pipeline.openapi.yaml](../technical/transform-pipeline/transform-pipeline.openapi.yaml) |
| Arazzo workflow | [transform-pipeline.arazzo.yaml](../technical/transform-pipeline/transform-pipeline.arazzo.yaml) |

## Technical pattern

See [transform-pipeline.md](../technical/transform-pipeline/transform-pipeline.md).

## Summary

Journey that runs a multi-step DataWeave transform pipeline for orders.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `transform-pipeline.journey.yaml` and the `JourneyStartRequest` schema in `transform-pipeline.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `transform-pipeline.journey.yaml` and the `JourneyOutcome.output` schema in `transform-pipeline.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `transform-pipeline.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `transform-pipeline` journey instance. | `transformPipeline_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `transformPipeline_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/transform-pipeline-sequence.png" alt="transform-pipeline – sequence" width="620" />

### State diagram

<img src="diagrams/transform-pipeline-state.png" alt="transform-pipeline – state transitions" width="320" />

### Activity diagram

<img src="diagrams/transform-pipeline-activity.png" alt="transform-pipeline – activity flow" width="420" />

## Implementation notes

- See `transform-pipeline.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `transform-pipeline.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `transform-pipeline.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
