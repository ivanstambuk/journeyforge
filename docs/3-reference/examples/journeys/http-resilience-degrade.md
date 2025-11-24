# Journey – http-resilience-degrade

> HTTP journey that exercises resilience policies against an unstable upstream and degrades gracefully on repeated failures.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-resilience-degrade.journey.yaml](../technical/http-resilience-degrade/http-resilience-degrade.journey.yaml) |
| OpenAPI (per-journey) | [http-resilience-degrade.openapi.yaml](../technical/http-resilience-degrade/http-resilience-degrade.openapi.yaml) |
| Arazzo workflow | [http-resilience-degrade.arazzo.yaml](../technical/http-resilience-degrade/http-resilience-degrade.arazzo.yaml) |

## Technical pattern

See [http-resilience-degrade.md](../technical/http-resilience-degrade/http-resilience-degrade.md).

## Summary

HTTP journey that exercises resilience policies against an unstable upstream and degrades gracefully on repeated failures.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-resilience-degrade.journey.yaml` and the `JourneyStartRequest` schema in `http-resilience-degrade.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-resilience-degrade.journey.yaml` and the `JourneyOutcome.output` schema in `http-resilience-degrade.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-resilience-degrade.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-resilience-degrade` journey instance (synchronous). | `httpResilienceDegrade_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`. | `JourneyOutcome` for this journey. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpResilienceDegrade_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-resilience-degrade-sequence.png" alt="http-resilience-degrade – sequence" width="420" />

### State diagram

<img src="diagrams/http-resilience-degrade-state.png" alt="http-resilience-degrade – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-resilience-degrade-activity.png" alt="http-resilience-degrade – activity flow" width="420" />

## Implementation notes

- See `http-resilience-degrade.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-resilience-degrade.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-resilience-degrade.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
