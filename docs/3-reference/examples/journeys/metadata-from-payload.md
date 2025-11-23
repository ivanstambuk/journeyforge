# Journey – metadata-from-payload

> Journey that lifts tags and attributes from the start payload into metadata bindings.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [metadata-from-payload.journey.yaml](../technical/metadata-from-payload/metadata-from-payload.journey.yaml) |
| OpenAPI (per-journey) | [metadata-from-payload.openapi.yaml](../technical/metadata-from-payload/metadata-from-payload.openapi.yaml) |
| Arazzo workflow | [metadata-from-payload.arazzo.yaml](../technical/metadata-from-payload/metadata-from-payload.arazzo.yaml) |

## Technical pattern

See [metadata-from-payload.md](../technical/metadata-from-payload/metadata-from-payload.md).

## Summary

Journey that lifts tags and attributes from the start payload into metadata bindings.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `metadata-from-payload.journey.yaml` and the `JourneyStartRequest` schema in `metadata-from-payload.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `metadata-from-payload.journey.yaml` and the `JourneyOutcome.output` schema in `metadata-from-payload.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `metadata-from-payload.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `metadata-from-payload` journey instance. | `metadataFromPayload_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `metadataFromPayload_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/metadata-from-payload-sequence.png" alt="metadata-from-payload – sequence" width="620" />

### State diagram

<img src="diagrams/metadata-from-payload-state.png" alt="metadata-from-payload – state transitions" width="320" />

### Activity diagram

<img src="diagrams/metadata-from-payload-activity.png" alt="metadata-from-payload – activity flow" width="420" />

## Implementation notes

- See `metadata-from-payload.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `metadata-from-payload.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `metadata-from-payload.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
