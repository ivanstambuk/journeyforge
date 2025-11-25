# Journey – http-success

> Simple HTTP 200 success path with JSON body, returning a normalised `{ status, ok, headers, body, error }` envelope from an upstream `GET` call.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [http-success.journey.yaml](../technical/http-success/http-success.journey.yaml) |
| OpenAPI (per-journey) | [http-success.openapi.yaml](../technical/http-success/http-success.openapi.yaml) |
| Arazzo workflow | [http-success.arazzo.yaml](../technical/http-success/http-success.arazzo.yaml) |

## Summary

This journey issues a single `GET` request to an upstream HTTP service, checks that the call succeeded, and then returns a structured HTTP result object to the caller. It is the smallest end-to-end example of:

- Making an HTTP call via `task.kind: httpCall:v1`.
- Branching on `context.api.ok`.
- Surfacing the HTTP envelope as the journey’s final `output`.
 - Using `spec.platform.config` to inject an environment-specific `itemsApiBaseUrl` into the journey via `platform.config.itemsApiBaseUrl`.

## Contracts at a glance

- **Input schema** – `JourneyStartRequest` with required `inputId: string`; additional properties are allowed.
- **Output schema** – `HttpSuccessOutput` with:
  - `status: integer` – HTTP status from the upstream call.
  - `ok: boolean` – normalised success flag derived from status.
  - `headers: object<string,string>` – selected response headers.
  - `body: any` – raw JSON response body.
  - `error: { type: string, message?: string, ... }` – populated only on error paths.
- **Named outcomes** – not used for this journey; success/failure are expressed via `JourneyOutcome.phase` and `error.code`.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the happy path described in `http-success.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `http-success` journey instance (synchronous). | `httpSuccess_start` | Body: `startRequest` with `inputId`. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`; for the happy path, `output.ok == true`. | `JourneyOutcome` with `output` matching `HttpSuccessOutput`. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `httpSuccess_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `Succeeded` or `Failed`. | `JourneyOutcome` for this journey. |

## Graphical overview

### Sequence diagram

<img src="diagrams/http-success-sequence.png" alt="http-success – sequence" width="420" />

### State diagram

<img src="diagrams/http-success-state.png" alt="http-success – state transitions" width="320" />

### Activity diagram

<img src="diagrams/http-success-activity.png" alt="http-success – activity flow" width="420" />

## Implementation notes

- The journey definition in `http-success.journey.yaml` uses a `task` state (`call_api`) that performs the upstream `GET` and writes the HTTP result into `context.api`.
- A `choice` state (`decide`) evaluates `context.api.ok == true` to decide whether to proceed to `done` or fail with `HTTP_NOT_OK`.
- The `succeed` state (`done`) exposes the `api` object as the final `output` for the journey, which is reflected in the `JourneyOutcome.output` schema in `http-success.openapi.yaml`.
