# OpenAPI Export – Journeys API

Status: Draft | Last updated: 2025-11-21

This document defines how each JourneyForge journey exports an OpenAPI 3.1 YAML contract for external consumers.
It also introduces the export shape for synchronous API endpoints (`kind: Api`).

For workflow-level descriptions over these OpenAPI surfaces (for example, “start journey → wait step → result” flows),
see also `docs/4-architecture/spec-guidelines/arazzo-export.md`.

## Goals
- Provide a stable REST surface for:
  - Journeys (`kind: Journey`):
    - Initiation (start a journey instance)
    - Stepping (post events to a running instance)
    - Completion (retrieve final outcome)
  - Synchronous APIs (`kind: Api`):
    - Single-request, single-response HTTP endpoints without journey ids or status polling.
- Keep the surface uniform across journeys and APIs; per-journey / per-API variants only specialize names/schemas.

## Paths (canonical)

### Journeys (`kind: Journey`)
- POST `/api/v1/journeys/{journeyName}/start` → `JourneyStartResponse`
- GET `/api/v1/journeys/{journeyId}` → `JourneyStatus`
- GET `/api/v1/journeys/{journeyId}/result` → `JourneyOutcome` (200 only when terminal)

### Step subresources (when the journey declares external-input steps)
- POST `/api/v1/journeys/{journeyId}/steps/{stepId}` → step response
  - `stepId` equals the state id of the external-input step (e.g., `waitForOtp`, `waitForApproval`, `waitForCallback`).
  - Request body:
    - Exported directly from the state’s `input.schema` (inline JSON Schema in the DSL).
    - There is no `JourneyStepRequest` envelope in per-journey OAS; the payload is the step’s logical input object.
  - `200` response:
    - Generic: `JourneyStatus` (canonical status envelope).
    - Per-journey:
      - When the state does not declare `response.schema`, the response schema is just `JourneyStatus`.
      - When the state declares `response.schema`, the exporter uses `allOf` to compose:
        - `JourneyStatus`, and
        - an inline object schema generated from `response.schema`, so that additional top-level fields are documented.

Examples:
- `wait-approval` and `payment-callback` OpenAPI specs under `docs/3-reference/examples/oas/` show step endpoints with per-step request and `JourneyStatus + allOf(step schema)` responses.

Note: even though the current implementation runs synchronously, the REST model is asynchronous to remain forward‑compatible. A synchronous `execute` endpoint may be added as a convenience later.

### API endpoints (`kind: Api`)
- POST `/api/v1/apis/{apiName}` → synchronous execution
  - Request body: journey input (no `context` envelope).
  - Response:
    - `200` (or other 2xx) on `succeed` with body derived from `outputVar` / `context`.
    - Non-2xx on `fail` with error payload derived from `errorCode` / `reason` and, optionally, RFC 9457 Problem Details.

Note: the initial version only specifies `POST` for API endpoints. Future versions MAY allow `GET`/`PUT` when semantics are clear (for example, read-only APIs).

## Schemas (canonical)
- `JourneyStartRequest`: conceptually “initial context object”; per-journey OAS specialise this to the journey’s `spec.input.schema`.
- `JourneyStartResponse`: `{ journeyId: string, journeyName: string, statusUrl: string, _links?: Links }`
- `JourneyStatus`: `{ journeyId, journeyName, phase: enum[Running,Succeeded,Failed], currentState: string, updatedAt: string(date-time), _links?: Links }`
- `JourneyStepRequest`: `{ eventType: string, payload?: object }`
- `JourneyOutcome`: `{ journeyId, journeyName, phase: enum[Succeeded,Failed], output?: any, error?: { code: string, reason: string }, _links?: Links }`
- `Links` (HAL-style, non-normative): `{ [rel: string]: { href: string, method?: string } }`

Notes on `_links`
- `_links` is optional in all envelopes and is intended for HATEOAS-style navigation:
  - `self` – the current resource (for example `/api/v1/journeys/{journeyId}`).
  - `result` – the final outcome resource (for example `/api/v1/journeys/{journeyId}/result`).
  - Step links – when a `wait`/`webhook` state is active, implementations MAY expose links for the relevant `/steps/{stepId}` endpoints; the link relation name MUST equal the external-input state id (for example `waitForOtp`, `confirm`).
  - Cancel link – when a journey is `Running` and user‑cancellable (see `spec.lifecycle.cancellable`), implementations SHOULD expose `_links.cancel` pointing to the canonical cancellation step `/api/v1/journeys/{journeyId}/steps/cancel` with `method: POST`. When the journey is terminal or `cancellable == false`, `_links.cancel` MUST be omitted.
- Presence is controlled per-journey via `spec.httpSurface.links.enabled`:
  - When omitted or `true`, the engine SHOULD include `_links` as described above.
  - When explicitly `false`, the engine SHOULD omit `_links` for that journey, even though the generic schema still declares `_links` as optional.

### API endpoints (`kind: Api`)

API endpoints reuse the same logical JSON Schemas as journeys but without the `Journey*` envelope:
- Request body:
  - If `spec.input.schema` is present on the `kind: Api` spec, the exporter uses that schema directly as the request body schema for `/apis/{apiName}`.
  - If absent, the request body defaults to an untyped `object`.
- Successful response:
  - If `spec.output.schema` is present, the exporter uses that schema as the `200` response body.
  - If absent, the response body defaults to an untyped `object`.
- Error response:
  - Exporters MUST follow the journey’s error configuration:
    - When `spec.errors.envelope` is omitted or uses `format: problemDetails`, the error body SHOULD be described using a Problem Details schema (for example a shared `ProblemDetails` component).
    - When `spec.errors.envelope.format: custom` is present, the error body SHOULD be described using the journey’s custom envelope schema.
  - `JourneyOutcome.error.{code,reason}` remains available as the stable error code/reason view over the canonical Problem Details object (ADR‑0003); exporters MAY surface this view in documentation, but it is not a separate envelope.

## Per-journey / per-API binding
- For journeys (`kind: Journey`):
  - The exporter emits one OAS YAML per journey under `docs/3-reference/examples/oas/<journey>.openapi.yaml` using the canonical paths with `journeyName` constant baked in.
  - `info.title` is set to `JourneyForge – <journey>`; tags include `<journey>`.
- For synchronous APIs (`kind: Api`):
  - The exporter emits one OAS YAML per API under `docs/3-reference/examples/oas/<api>.openapi.yaml` using the canonical `/apis/{apiName}` path (or the explicit `spec.route.path`).
  - `info.title` is set to `JourneyForge – <api> (Api)`; tags include `<api>`.

## Future work
- Add per‑journey input/output JSON Schemas by sampling or from declared `spec.input.schema`/`spec.output.schema` in the spec.
- Extend events to named signals for `wait`/webhook states when introduced.
- Define a shared `ProblemDetails` schema and formalise how `kind: Api` exports error responses (including documenting status code mapping driven by `spec.apiResponses` and its Problem-Details-based defaults).

## Schema integration and bundling
- The DSL exposes inline JSON Schemas via:
  - `spec.input.schema`, `spec.output.schema`, `spec.context.schema`, and
  - `wait.input.schema`, `wait.response.schema`, `webhook.input.schema`, `webhook.response.schema`.
- Per-journey OpenAPI exporters:
  - Read these inline schemas from the journey definition.
  - Materialise them as **inline components** under `components.schemas` in the per-journey OAS.
  - Specialise `JourneyOutcome.output` via `allOf`:
    - First branch: the generic `JourneyOutcome` envelope (inlined into the per-journey file).
    - Second branch: an object schema derived from `spec.output.schema` (when present).
- Bundling rules for examples and exported OAS:
  - Per-journey OAS files are **self-contained**:
    - No `$ref` to external JSON files (for example `../schemas/*.json`).
    - No `$ref` to `journeys.openapi.yaml`; generic `Journey*` and `Link` shapes are copied inline.
  - Component naming:
    - Journeys:
      - `JourneyStartRequest`, `JourneyStartResponse`, `JourneyStatus`, `JourneyStepRequest` (when needed), `JourneyOutcome`, `Link`.
    - APIs:
      - Journey-specific names such as `Input`, `Output`, `Error` for `/apis/{apiName}`.
  - Tooling MAY still treat `docs/3-reference/openapi/journeys.openapi.yaml` as the normative generic contract, but per-journey exports must not depend on it via cross-file `$ref`.
