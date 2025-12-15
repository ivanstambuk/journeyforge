# Use Case – Trace & Header Propagation

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-header-interpolation/http-header-interpolation.md`

## Problem

Propagate inbound tracing or correlation headers (for example W3C / OpenTelemetry `traceparent`) to all downstream HTTP calls without rewriting every `task.headers` block.

## Relevant DSL Features

- `spec.bindings.http.start.headersToContext` and `headersPassthrough`.
- `spec.bindings.http.steps.*` for step‑level bindings.
- HTTP `task` headers.

Note: `headersPassthrough` is request-scoped. It applies only to outbound HTTP tasks executed while processing the current inbound request (start or step submission) and does not persist across `wait`/`webhook` boundaries. Use `headersToContext` (or an explicit `transform`) for values that must persist across multiple requests in the same journey.

## Example – Header interpolation

Journey definition: `http-header-interpolation.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-header-interpolation
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [traceparent]
      additionalProperties: true
      properties:
        traceparent: { type: string }
  output:
    schema:
      type: object
      properties:
        status: { type: integer }
        ok: { type: boolean }
        headers:
          type: object
          additionalProperties: { type: string }
        body: {}
        error:
          type: object
          properties:
            type: { type: string }
            message: { type: string }
          additionalProperties: true
  apis:
    items:
      openApiRef: apis/items.openapi.yaml
    users:
      openApiRef: apis/users.openapi.yaml
    accounts:
      openApiRef: apis/accounts.openapi.yaml
    serviceA:
      openApiRef: apis/serviceA.openapi.yaml
    serviceB:
      openApiRef: apis/serviceB.openapi.yaml
  start: call
  states:
    call:
      type: task
      task:
        kind: httpCall:v1
        method: GET
        url: "https://api.example.com/trace"
        headers:
          Accept: application/json
          traceparent: "${context.traceparent}"
        timeoutMs: 5000
        resultVar: api
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.api.ok == true and context.api.body.traceparent == context.traceparent
          next: ok
      default: not_ok

    ok:
      type: succeed
      outputVar: api

    not_ok:
      type: fail
      errorCode: TRACE_MISMATCH
      reason: "Trace id not echoed by service or call failed"
```

Related files:
- OpenAPI: `http-header-interpolation.openapi.yaml`
- Schemas: `http-header-interpolation-input.json`, `http-header-interpolation-output.json`
