# Journey – auth-user-info

> JWT-authenticated user lookup using httpSecurity and httpBindings over the Journeys API.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [auth-user-info.journey.yaml](../technical/auth-user-info/auth-user-info.journey.yaml) |
| OpenAPI (per-journey) | [auth-user-info.openapi.yaml](../technical/auth-user-info/auth-user-info.openapi.yaml) |
| Arazzo workflow | [auth-user-info.arazzo.yaml](../technical/auth-user-info/auth-user-info.arazzo.yaml) |

## Technical pattern

See [auth-user-info.md](../technical/auth-user-info/auth-user-info.md).

## Summary

JWT-authenticated user lookup using httpSecurity and httpBindings over the Journeys API.

## Contracts at a glance

- **Input schema** – see `spec.input.schema` in `auth-user-info.journey.yaml` and the `JourneyStartRequest` schema in `auth-user-info.openapi.yaml`.
- **Output schema** – see `spec.output.schema` in `auth-user-info.journey.yaml` and the `JourneyOutcome.output` schema in `auth-user-info.openapi.yaml`.
- **Named outcomes** – if this journey uses `spec.outcomes`, see the journey definition for outcome labels; otherwise it relies on `JourneyOutcome.phase` and `error.code` only.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `auth-user-info.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `auth-user-info` journey instance. | `authUserInfo_start` | Body: `startRequest` as defined by JourneyStartRequest. | `$statusCode == 202` and a `journeyId` is returned. | `journeyId` for the new journey instance. |
| 2 | `getResult` | Poll for the final journey outcome once terminal. | `authUserInfo_getResult` | Path: `journeyId` from step 1. | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/auth-user-info-sequence.png" alt="auth-user-info – sequence" width="420" />

### State diagram

<img src="diagrams/auth-user-info-state.png" alt="auth-user-info – state transitions" width="320" />

### Activity diagram

<img src="diagrams/auth-user-info-activity.png" alt="auth-user-info – activity flow" width="420" />

## Implementation notes

- See `auth-user-info.journey.yaml` for the full set of states, transitions, and DataWeave expressions that implement this journey.
- The per-journey OpenAPI file `auth-user-info.openapi.yaml` describes the HTTP surface (start, status, result, and any step endpoints).
- The Arazzo workflow `auth-user-info.arazzo.yaml` documents the recommended client workflow over the Journeys API, including polling and any step calls.
