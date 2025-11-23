# Journey – http-idempotent-create

> Create-or-get HTTP journey that combines GET and POST calls with idempotent semantics.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-idempotent-create.journey.yaml](../technical/http-idempotent-create/http-idempotent-create.journey.yaml) |
| OpenAPI (per-journey) | [http-idempotent-create.openapi.yaml](../technical/http-idempotent-create/http-idempotent-create.openapi.yaml) |
| Arazzo workflow | [http-idempotent-create.arazzo.yaml](../technical/http-idempotent-create/http-idempotent-create.arazzo.yaml) |

## Technical pattern

See [http-idempotent-create.md](../technical/http-idempotent-create/http-idempotent-create.md).

## Summary

Create-or-get HTTP journey that combines GET and POST calls with idempotent semantics.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-idempotent-create.journey.yaml` and the `JourneyStartRequest` schema in `http-idempotent-create.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-idempotent-create.journey.yaml` and the `JourneyOutcome.output` schema in `http-idempotent-create.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-idempotent-create.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-idempotent-create` journey instance. | `httpIdempotentCreate_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `httpIdempotentCreate_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-idempotent-create-sequence.png" alt="http-idempotent-create – sequence" width="620" />

### State diagram

<img src="diagrams/http-idempotent-create-state.png" alt="http-idempotent-create – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-idempotent-create-activity.png" alt="http-idempotent-create – activity flow" width="420" />

## Implementation notes

- See `http-idempotent-create.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-idempotent-create.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-idempotent-create.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
