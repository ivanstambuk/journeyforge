# Journey – cache-user-profile

> Cache-aware profile lookup that combines a transform pipeline with cacheGet/cachePut operations.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [cache-user-profile.journey.yaml](../technical/cache-user-profile/cache-user-profile.journey.yaml) |
| OpenAPI (per-journey) | [cache-user-profile.openapi.yaml](../technical/cache-user-profile/cache-user-profile.openapi.yaml) |
| Arazzo workflow | [cache-user-profile.arazzo.yaml](../technical/cache-user-profile/cache-user-profile.arazzo.yaml) |

## Technical pattern

See [cache-user-profile.md](../technical/cache-user-profile/cache-user-profile.md).

## Summary

Cache-aware profile lookup that combines a transform pipeline with cacheGet/cachePut operations.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `cache-user-profile.journey.yaml` and the `JourneyStartRequest` schema in `cache-user-profile.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `cache-user-profile.journey.yaml` and the `JourneyOutcome.output` schema in `cache-user-profile.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `cache-user-profile.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `cache-user-profile` journey instance. | `cacheUserProfile_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `cacheUserProfile_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/cache-user-profile-sequence.png" alt="cache-user-profile – sequence" width="420" />

### State diagram

<img src="diagrams/cache-user-profile-state.png" alt="cache-user-profile – state transitions" width="320" />

### Activity diagram

<img src="diagrams/cache-user-profile-activity.png" alt="cache-user-profile – activity flow" width="420" />

## Implementation notes

- See `cache-user-profile.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `cache-user-profile.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `cache-user-profile.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
