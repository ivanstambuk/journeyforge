# Journey – http-timeout-branch

> HTTP journey that branches on timeouts via `api.error.type == "TIMEOUT"`.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-timeout-branch.journey.yaml](../technical/http-timeout-branch/http-timeout-branch.journey.yaml) |
| OpenAPI (per-journey) | [http-timeout-branch.openapi.yaml](../technical/http-timeout-branch/http-timeout-branch.openapi.yaml) |
| Arazzo workflow | [http-timeout-branch.arazzo.yaml](../technical/http-timeout-branch/http-timeout-branch.arazzo.yaml) |

## Summary

HTTP journey that branches on timeouts via `api.error.type == "TIMEOUT"`.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `http-timeout-branch.journey.yaml` and the `JourneyStartRequest` schema in `http-timeout-branch.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `http-timeout-branch.journey.yaml` and the `JourneyOutcome.output` schema in `http-timeout-branch.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `http-timeout-branch.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-timeout-branch` journey instance. | `httpTimeoutBranch_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `httpTimeoutBranch_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-timeout-branch-sequence.png" alt="http-timeout-branch – sequence" width="620" />

### State diagram

<img src="diagrams/http-timeout-branch-state.png" alt="http-timeout-branch – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-timeout-branch-activity.png" alt="http-timeout-branch – activity flow" width="420" />

## Implementation notes

- See `http-timeout-branch.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `http-timeout-branch.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `http-timeout-branch.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
