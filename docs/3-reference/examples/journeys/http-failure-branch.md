# Journey – http-failure-branch

> HTTP journey that routes to failure when the normalised `api.ok` flag is false.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-failure-branch.journey.yaml](../technical/http-failure-branch/http-failure-branch.journey.yaml) |
| OpenAPI (per-journey) | [http-failure-branch.openapi.yaml](../technical/http-failure-branch/http-failure-branch.openapi.yaml) |
| Arazzo workflow | [http-failure-branch.arazzo.yaml](../technical/http-failure-branch/http-failure-branch.arazzo.yaml) |

## Summary

HTTP journey that routes to failure when the normalised `api.ok` flag is false.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-failure-branch.journey.yaml` and the `JourneyStartRequest` schema in `http-failure-branch.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-failure-branch.journey.yaml` and the `JourneyOutcome.output` schema in `http-failure-branch.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-failure-branch.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-failure-branch` journey instance (synchronous). | `httpFailureBranch_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`, with failures reflected in `JourneyOutcome.error` and `output`. | `JourneyOutcome` for this journey. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpFailureBranch_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-failure-branch-sequence.png" alt="http-failure-branch – sequence" width="420" />

### State diagram

<img src="diagrams/http-failure-branch-state.png" alt="http-failure-branch – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-failure-branch-activity.png" alt="http-failure-branch – activity flow" width="420" />

## Implementation notes

- See `http-failure-branch.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-failure-branch.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-failure-branch.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
