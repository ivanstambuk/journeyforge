# Journey – choice-multi-branch

> Three-branch decision journey that classifies requests as approved, review, technical failure, or default based on DataWeave predicates.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [choice-multi-branch.journey.yaml](../technical/choice-multi-branch/choice-multi-branch.journey.yaml) |
| OpenAPI (per-journey) | [choice-multi-branch.openapi.yaml](../technical/choice-multi-branch/choice-multi-branch.openapi.yaml) |
| Arazzo workflow | [choice-multi-branch.arazzo.yaml](../technical/choice-multi-branch/choice-multi-branch.arazzo.yaml) |

## Summary

Three-branch decision journey that classifies requests as approved, review, technical failure, or default based on DataWeave predicates.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `choice-multi-branch.journey.yaml` and the `JourneyStartRequest` schema in `choice-multi-branch.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `choice-multi-branch.journey.yaml` and the `JourneyOutcome.output` schema in `choice-multi-branch.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `choice-multi-branch.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `choice-multi-branch` journey instance. | `choiceMultiBranch_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `choiceMultiBranch_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/choice-multi-branch-sequence.png" alt="choice-multi-branch – sequence" width="620" />

### State diagram

<img src="diagrams/choice-multi-branch-state.png" alt="choice-multi-branch – state transitions" width="320" />

### Activity diagram

<img src="diagrams/choice-multi-branch-activity.png" alt="choice-multi-branch – activity flow" width="420" />

## Implementation notes

- See `choice-multi-branch.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `choice-multi-branch.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `choice-multi-branch.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
