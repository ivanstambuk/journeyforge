# Use Case – Third‑Party Auth & Propagation

Status: Draft | Last updated: 2025-11-20

## Problem

Expose a journey that:
- Requires callers to be authenticated with JWT.
- Normalises identity from JWT claims into business fields on `context`.
- Propagates a subset of auth/trace information to a downstream HTTP API call.
- Optionally authenticates *outbound* HTTP calls to a third-party API using OAuth2 client credentials.

## Relevant DSL Features

- `spec.policies.httpSecurity` and `spec.security.*` for inbound auth.
- `httpBindings` to project headers into `context`.
- `transform` to normalise auth data into business fields.
- HTTP `task` with `operationRef`.
- `spec.policies.httpClientAuth` and `task.auth.policyRef` for outbound auth.

## Example – JWT‑authenticated user info journey (inbound only)

Journey definition: `auth-user-info.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: auth-user-info
  version: 0.1.0
spec:
  # Security policies: the engine validates JWT on start requests
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

## Example – Outbound OAuth2 client credentials

Journey definition: `auth-outbound-client-credentials.journey.yaml`

This journey assumes:
- The engine exposes a secret store that can resolve `secret://oauth/clients/orders-service` to a client secret.
- Multiple journey instances may reuse the same access token until it expires.

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: auth-outbound-client-credentials
  version: 0.1.0
spec:
  policies:
    httpClientAuth:
      default: backendDefault
      definitions:
        backendDefault:
          kind: oauth2ClientCredentials
          tokenEndpoint: https://auth.example.com/oauth2/token
          auth:
            method: clientSecretPost
            clientId: orders-service
            clientSecretRef: secret://oauth/clients/orders-service
          form:
            grant_type: client_credentials
            scope: orders.read

  apis:
    backend:
      openApiRef: apis/backend.openapi.yaml

  inputSchemaRef: schemas/auth-outbound-client-credentials-input.json
  outputSchemaRef: schemas/auth-outbound-client-credentials-output.json

  start: callBackend

  states:
    callBackend:
      type: task
      task:
        kind: httpCall
        operationRef: backend.getOrder
        auth:
          policyRef: backendDefault
        params:
          path:
            orderId: "${context.orderId}"
          headers:
            Accept: application/json
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
      errorCode: OUTBOUND_CALL_FAILED
      reason: "Backend call failed or did not return 200"
```

Notes:
- The outbound auth policy is reusable across journeys and tasks; only `policyRef` bindings are journey-local.
- Engines should cache the OAuth2 access token per policy/effective request and reuse it until expiry (see DSL §19).
