# Use Case – `kind: Api` Error Mapping with `spec.errors` + `spec.apiResponses`

Status: Draft | Last updated: 2025-11-22

Related: ADR-0003, ADR-0016

## Problem

Expose a synchronous API endpoint (`kind: Api`) that:

- Normalises arbitrary downstream HTTP errors into RFC 9457 Problem Details.
- Uses a single, journey-wide error envelope (Problem itself or a custom format).
- Maps different runtime conditions (for example unauthenticated subject, forbidden subject, upstream failures) to
  different HTTP status codes, while optionally propagating upstream 2xx/4xx/5xx where appropriate.

This use case builds on:

- `spec.errors`:
  - Canonical Problem Details as the internal error model.
  - Optional per-source normalisers to build Problem objects from raw HTTP results.
  - A single envelope (Problem or custom) for all external error bodies per journey.
- `spec.apiResponses` (kind: Api only):
  - Declarative, ordered rules to map final `phase` + canonical Problem object + context to HTTP status codes.
  - Problem-Details-based defaults when omitted.

## Relevant DSL Features

- `kind: Api` journeys:
  - `spec.input.schema` / `spec.output.schema` for request/response bodies.
  - `start` / `states` with `task`, `choice`, `transform`, `succeed`, `fail`.
- HTTP tasks:
  - `task.kind: httpCall`, `operationRef`, `resultVar`.
  - `resultVar` HTTP result shape (`status`, `ok`, `headers`, `body`, `error`).
- Error handling:
  - `spec.errors.canonicalFormat: rfc9457` (implicit default).
  - `spec.errors.normalisers` for HTTP result → Problem Details.
  - `spec.errors.envelope.format: problemDetails` or `custom` (single envelope per journey).
- API response mapping (`kind: Api`):
  - `spec.apiResponses.rules`:
    - `when.phase: SUCCEEDED | FAILED`.
    - Optional `when.errorType` (Problem `type`) and DW `predicate`.
    - `status` or `statusExpr` to set HTTP status.
  - `spec.apiResponses.default`:
    - `SUCCEEDED`: default 200.
    - `FAILED`: default Problem `status` or 500.

## Example – API with Problem-based errors and custom status mapping

Journey definition (sketch): `http-custom-error-envelope-api.journey.yaml`

- `kind: Api`.
- Calls a downstream Orders service.
- Normalises non-2xx results into Problem Details via inline DW.
- Exposes a custom error envelope derived from Problem.
- Uses `spec.apiResponses` to:
  - Return 401/403 for security errors.
  - Return 502 for upstream 5xx.
  - Propagate 201/204 on success when present.

Key ideas (YAML sketch, not exhaustive):

```yaml
apiVersion: v1
kind: Api
metadata:
  name: http-custom-error-envelope-api
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [id]
      properties:
        id: { type: string }
  output:
    schema:
      type: object
      properties:
        item: {}
  errors:
    canonicalFormat: rfc9457
    normalisers:
      httpDefault:
        mapper:
          lang: dataweave
          expr: |
            {
              type: 'https://example.com/probs/upstream-error',
              title: 'Upstream service failure',
              status: context.api.status default: 500,
              detail: context.api.error.message default: 'Upstream error'
            }
    envelope:
      format: custom
      mapper:
        lang: dataweave
        expr: |
          // input: canonical Problem Details object
          {
            code: payload.type,
            message: payload.detail default payload.title,
            status: payload.status default 500
          }

  apiResponses:
    rules:
      - when:
          phase: FAILED
          errorType: "urn:subject-unauthenticated"
        status: 401
      - when:
          phase: FAILED
          errorType: "urn:subject-unauthorized"
        status: 403
      - when:
          phase: FAILED
          predicate:
            lang: dataweave
            expr: |
              payload.error.status >= 500
        status: 502
      - when:
          phase: SUCCEEDED
          predicate:
            lang: dataweave
            expr: |
              context.api.status == 201 or context.api.status == 204
        statusExpr:
          lang: dataweave
          expr: context.api.status
    default:
      SUCCEEDED: 200
      FAILED: fromProblemStatus
```

States (sketch):

- `call_orders`:
  - HTTP `task` with `resultVar: api`.
- `normalise_error`:
  - `choice` on `context.api.ok`.
  - On success, go to `project_success`.
  - On failure, apply `spec.errors.normalisers.httpDefault` to produce Problem.
- `project_success`:
  - `transform` to build `context.output` from `context.api.body`.
  - `succeed` with `outputVar: output`.
- `fail_with_error`:
  - `fail` with `errorCode` and `reason` derived from Problem.

## How it fits together

- Normalisation:
  - HTTP errors are converted into canonical Problem Details via `spec.errors.normalisers`.
- Envelope:
  - The outward error body is always either Problem or the custom envelope from `spec.errors.envelope.mapper`.
  - There is exactly one error envelope per journey.
- HTTP status:
  - `spec.apiResponses.rules` and defaults drive HTTP status codes for both success and failure:
    - Security errors explicitly map to 401/403 via `errorType`.
    - Upstream 5xx map to 502 via a DW predicate over `payload.error.status`.
    - Successful 201/204 responses propagate upstream status via `statusExpr`.
    - All other successes use 200; all other failures use Problem `status` or 500.

## When to use this pattern

- You need a `kind: Api` endpoint that:
  - Respects Problem Details as the canonical error model.
  - Exposes a stable, journey-wide error envelope (Problem or custom).
  - Has clear, declarative HTTP status semantics per error/success condition.
- You want to:
  - Rewrite or group downstream errors into your own HTTP status vocabulary.
  - Preserve or selectively propagate upstream 2xx/4xx/5xx codes where appropriate.
