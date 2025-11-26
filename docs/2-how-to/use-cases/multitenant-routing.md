# Use Case – Multi‑Tenant Routing

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/multitenant-routing/multitenant-routing.md`

## Problem

Route requests to different upstream backends based on tenant identity:
- Read tenant information from inbound headers.
- Route to the appropriate API/catalog per tenant.
- Propagate tenant header downstream.

## Relevant DSL Features

- `spec.bindings.http.start.headersToContext`.
- `choice` based on `context.tenantId`.
- HTTP `task` with different `operationRef` targets.

## Example – multitenant-routing

Journey definition: `multitenant-routing.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: multitenant-routing
  version: 0.1.0
spec:
  apis:
    itemsA:
      openApiRef: apis/items.openapi.yaml
    itemsB:
      openApiRef: apis/serviceA.openapi.yaml
  input:
    schema:
      type: object
      required: [id]
      properties:
        id: { type: string }
      additionalProperties: true
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
      additionalProperties: true
  bindings:
    http:
      start:
        headersToContext:
          X-Tenant-Id: tenantId
  start: routeTenant
  states:
    routeTenant:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.tenantId == 'A'
          next: callTenantA
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.tenantId == 'B'
          next: callTenantB
      default: unsupported

    callTenantA:
      type: task
      task:
        kind: httpCall:v1
        operationRef: itemsA.getItemById
        params:
          path:
            id: "${context.id}"
          headers:
            Accept: application/json
            X-Tenant-Id: "${context.tenantId}"
        timeoutMs: 5000
        resultVar: api
      next: decide

    callTenantB:
      type: task
      task:
        kind: httpCall:v1
        operationRef: itemsB.getItem
        params:
          headers:
            Accept: application/json
            X-Tenant-Id: "${context.tenantId}"
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
                context.api.ok == true
          next: done
      default: failed

    done:
      type: succeed
      outputVar: api

    failed:
      type: fail
      errorCode: TENANT_CALL_FAILED
      reason: "Tenant-specific call failed"

    unsupported:
      type: fail
      errorCode: TENANT_UNSUPPORTED
      reason: "Unsupported tenant id"
```

Related files:
- OpenAPI: `multitenant-routing.openapi.yaml`
