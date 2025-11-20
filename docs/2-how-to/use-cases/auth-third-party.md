# Use Case – Third‑Party Auth & Propagation

Status: Draft | Last updated: 2025-11-20

## Problem

Expose a journey that:
- Requires callers to be authenticated with JWT.
- Normalises identity from JWT claims into business fields on `context`.
- Propagates a subset of auth/trace information to a downstream HTTP API call.

## Relevant DSL Features

- `spec.policies.httpSecurity` and `spec.security.*` for inbound auth.
- `httpBindings` to project headers into `context`.
- `transform` to normalise auth data into business fields.
- HTTP `task` with `operationRef`.

## Example – JWT‑authenticated user info journey

Workflow: `auth-user-info.workflow.yaml`

```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: auth-user-info
  version: 0.1.0
spec:
  # Security policies: runtime validates JWT on start requests
  policies:
    httpSecurity:
      default: jwtDefault
      definitions:
        jwtDefault:
          kind: jwt
          issuer: "https://issuer.example.com"
          audience: ["journeyforge"]
          jwks:
            source: jwksUrl
            url: "https://issuer.example.com/.well-known/jwks.json"
            cacheTtlSeconds: 3600
          clockSkewSeconds: 60
          requiredClaims:
            sub:
              type: string
            scope:
              contains: ["user:read"]

  security:
    journeyPolicyRef: jwtDefault

  # Bind relevant headers into context for tracing/correlation
  httpBindings:
    start:
      headersToContext:
        traceparent: traceparent
      headersPassthrough:
        - from: traceparent
          to: traceparent

  apis:
    users:
      openApiRef: apis/users.openapi.yaml

  inputSchemaRef: schemas/auth-user-info-input.json
  outputSchemaRef: schemas/auth-user-info-output.json

  start: normaliseAuth

  states:
    normaliseAuth:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              userId: context.auth.jwt.claims.sub,
              scopes: context.auth.jwt.claims.scope default: null
            }
        target:
          kind: context
          path: ''
      next: fetchUser

    fetchUser:
      type: task
      task:
        kind: httpCall
        operationRef: users.getUserById
        params:
          path:
            userId: "${context.userId}"
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
                context.api.ok == true and context.api.status == 200
          next: done
      default: failed

    done:
      type: succeed
      outputVar: api

    failed:
      type: fail
      errorCode: USER_LOOKUP_FAILED
      reason: "User lookup failed or did not return 200"
```

Related files:
- OpenAPI: `auth-user-info.openapi.yaml`
- Schemas: `auth-user-info-input.json`, `auth-user-info-output.json`
