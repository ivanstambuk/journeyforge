# ADR-0004 – Synchronous API Endpoints (`kind: Api`)

Date: 2025-11-20 | Status: Proposed

## Context

JourneyForge defines a primary journeys kind:
- `kind: Journey` – journeys that are started via `/api/v1/journeys/{journeyName}/start`, identified by a `journeyId`, and observed via `/journeys/{journeyId}` and `/journeys/{journeyId}/result`.

This works well for long-lived or externally-driven journeys (for example, `wait`/`webhook` steps, manual approvals, callbacks). However, many frontend and BFF scenarios only need:
- A single synchronous HTTP request.
- One or more downstream HTTP calls (fan-out, chaining, or simple mapping).
- A composed response in the same HTTP call.

For these cases:
- Exposing a Journeys-style API (with `journeyId`, status polling, and `/result`) feels heavy and non-idiomatic.
- Clients want a “pure REST” endpoint that looks like a normal JSON API: `POST /api/v1/apis/<name>` with a direct JSON body and response.

We also want to:
  - Reuse the existing DSL surface (states, policies, error model) and engine.
- Avoid introducing a completely separate DSL or execution model for “API endpoints”.

## Decision

We introduce a second top-level kind:
- `kind: Api` – synchronous HTTP endpoints that reuse the same state machine model as journeys but do not surface journeys.

Shape:
- Both kinds share the same core shape:
  ```yaml
  apiVersion: v1
  kind: Journey | Api
  metadata:
    name: <string>
    version: <semver>
  spec:
    start: <stateId>
    states:
      <stateId>: <State>
  ```
- `kind: Api` adds:
  ```yaml
  spec:
    route:
      path: <string>   # e.g. /apis/http-chained-calls; defaults to /apis/{metadata.name}
      method: <string> # POST only in the initial version
    input:
      schema: <JsonSchema>     # optional but recommended – inline JSON Schema for request body
    output:
      schema: <JsonSchema>     # optional but recommended – inline JSON Schema for success response
  ```

Execution semantics (`kind: Api`):
- One HTTP request creates an ephemeral `context`, executes the state machine from `spec.start` until a terminal state, and returns a single HTTP response.
- No `journeyId` is created or exposed; there are no status/result endpoints for `kind: Api`.
- The request body is deserialised as JSON and used to initialise `context` (validated against `spec.input.schema` when present); `spec.bindings.http.start` can further project headers into `context`.
- On `succeed`:
  - The engine returns a 2xx response with the body taken from `context.<outputVar>` (when set) or the full `context` otherwise.
- On `fail`:
  - The engine returns a non-2xx response with an error payload derived from `errorCode` / `reason`, aligned with the RFC 9457 guidance in ADR-0003.
  - The structure of the error payload MUST follow the journey’s error configuration:
    - When `spec.errors.envelope` is omitted or uses `format: problemDetails`, the error body MUST use the Problem Details shape.
    - When `spec.errors.envelope.format: custom` is present, the error body MUST be produced by the journey’s configured envelope mapper.
  - HTTP status code selection remains an implementation concern (see Q-001 in `docs/4-architecture/open-questions.md`); implementations MAY consult `spec.outcomes` and canonical Problem Details `status` to choose appropriate status codes.

Constraints (`kind: Api`):
- State surface:
  - `wait` and `webhook` states MUST NOT be used in `kind: Api` specs (they require persistent journeys and external events).
  - All other states (`task`, `choice`, `transform`, `parallel`, `succeed`, `fail`, cache operations, policies) are allowed.
- Control flow:
  - Every execution path from `spec.start` MUST eventually reach a terminal `succeed` or `fail` without external input.
- HTTP surface:
  - Canonical base path is `/api/v1/apis/{apiName}`.
  - `spec.route.path` may override the path (still under `/api/v1`); `spec.route.method` defaults to `POST`.

OpenAPI export:
- The OpenAPI export guideline is extended to cover `kind: Api`:
  - Paths: `POST /api/v1/apis/{apiName}` for synchronous execution.
  - Request body schema: taken directly from `spec.input.schema` (no `context` envelope).
  - Success response schema: taken from `spec.output.schema` when present.
  - Error response schema: at minimum `{ code: string, reason: string, ... }`, with room to promote RFC 9457 Problem Details to a shared schema later.
- A concrete example (`http-chained-calls-api`) is added under `docs/3-reference/examples/` with a matching per-API OpenAPI spec under `docs/3-reference/examples/oas/`.

## Consequences

Positive:
- Frontend and BFF consumers get first-class REST APIs that feel like normal synchronous HTTP endpoints, without journey ids or status polling.
- Authors can model both journeys and simple APIs using the same DSL surface and mental model (states, choices, transforms, policies).
- Implementations can reuse the same engine, policies (`httpResilience`, `httpSecurity`), and error model (ADR-0003) across journeys and APIs.
- The OpenAPI story becomes clearer: per-journey OAS for `kind: Journey`, and per-API OAS for `kind: Api`.

Negative / trade-offs:
- The engine must support an additional HTTP surface (`/api/v1/apis/...`) and routing semantics for `kind: Api`.
- We now have two kinds at the top level; tooling (e.g., editors, validators, exporters) must understand both.
- Error status mapping for `kind: Api` (how `fail` / `spec.outcomes` map to HTTP status codes) is only partially specified and will need refinement as we build implementations.

Related:
- DSL Reference: `docs/3-reference/dsl.md` (top-level shape and `kind: Api` semantics).
- OpenAPI Export Guideline: `docs/4-architecture/spec-guidelines/openapi-export.md`.
- Example API spec and OAS: `docs/3-reference/examples/http-chained-calls-api.journey.yaml`, `docs/3-reference/examples/oas/http-chained-calls-api.openapi.yaml`.
- Error Model and Problem Details: `docs/6-decisions/ADR-0003-error-model-rfc9457-problem-details.md`.
