# Step Design Guideline

Status: Draft | Last updated: 2025-11-19

## Naming
- State ids (steps): `waitForApproval`, `waitForCallback`, `enterOtp` – verbs describing the external input.
- Schemas: `<workflow>-<step>-input.json` for step payload, `<workflow>-output.json` for final output.
- Avoid reusing the same `stepId` for different semantics within a workflow.

## Schema design
- Keep step schemas small and focused on what the client must send.
- Prefer enums for decision-like fields (e.g. `decision: approved|rejected`).
- Mark only truly required fields as `required` in JSON Schema; keep the rest optional.

## Idempotency
- Treat `POST /journeys/{journeyId}/steps/{stepId}` as idempotent for a given `Idempotency-Key` header.
- Clients:
  - Generate a UUID per logical attempt and send `Idempotency-Key`.
  - Retry with the same key on network errors.
- Engine (future behaviour):
  - Cache the first successful handling per `(journeyId, stepId, Idempotency-Key)` and return the same `JourneyStatus` for repeats.

## HTTP semantics for step clients
- 200 OK: step accepted and applied; body is `JourneyStatus`.
- 400 Bad Request: payload fails JSON Schema validation.
- 401/403: authentication/authorization failures (enforced by the surrounding service).
- 404: `journeyId` not found.
- 409 Conflict: journey is not at the addressed `stepId`.

## Versioning
- Changing a step’s schema in a breaking way should be accompanied by:
  - New `stepId` or new workflow version.
  - Updated JSON Schemas and OAS documentation.
