# Use Case – Resilience Policies & Degraded Mode

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-resilience-degrade/http-resilience-degrade.md`

## Problem

Wrap an unstable upstream HTTP call with resilience policies:
- Retry on transient failures.
- If all attempts fail, degrade gracefully with a clear error instead of propagating a raw failure.

## Relevant DSL Features

- `spec.policies.httpResilience` and `resiliencePolicyRef`.
- HTTP `task` result objects (`ok`, `status`, `error`).
- `choice` + `succeed` / `fail` decision.

## Example – http-resilience-degrade

Journey definition: `http-resilience-degrade.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-resilience-degrade
  version: 0.1.0
spec:
  apis:
    serviceA:
      openApiRef: apis/serviceA.openapi.yaml
  input:
    schema:
      type: object
      additionalProperties: true
      properties:
        id: { type: string }
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
  policies:
    httpResilience:
      default: standard
      definitions:
        standard:
          maxAttempts: 3
          retryOn: [NETWORK_ERROR, HTTP_5XX]
          backoff:
            initialDelayMs: 200
            maxDelayMs: 2000
            factor: 2.0
  start: call_upstream
  states:
    call_upstream:
      type: task
      task:
        kind: httpCall:v1
        operationRef: serviceA.unstableOperation
        params:
          headers:
            Accept: application/json
        timeoutMs: 2000
        resultVar: api
        resiliencePolicyRef: standard
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.api.ok == true
          next: success
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.api.ok == false
          next: degraded
      default: degraded

    success:
      type: succeed
      outputVar: api

    degraded:
      type: fail
      errorCode: DEGRADED_UPSTREAM
      reason: "Upstream unstableOperation failed after retries"
```

Related files:
- OpenAPI: `http-resilience-degrade.openapi.yaml`
- Schemas: `http-resilience-degrade-input.json`, `http-resilience-degrade-output.json`
