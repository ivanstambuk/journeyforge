# Use Case – JWT Scopes/Roles AuthZ

Status: Draft | Last updated: 2025-11-25

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/auth-jwt-scopes/auth-jwt-scopes.md`

## Problem

Expose a journey that:
- Authenticates callers via JWT using `jwtValidate:v1`.
- Enforces authorisation based on JWT scopes/roles before calling a downstream API.
- Keeps the authN vs authZ split clear: `jwtValidate:v1` for token validation, `choice` predicates over `context.auth.jwt.*` for authZ.

## Relevant DSL Features

- `jwtValidate:v1` – JWT validation task plugin (authN).
- `context.auth.jwt.*` – projected header/claims for expressions.
- `choice` – predicates over JWT claims (scopes/roles/subject).
- HTTP `task` with `operationRef` for downstream calls.
- HTTP binding (`spec.bindings.http`) – to propagate trace headers without repeating them in every state.

## Example – JWT scopes/roles check before user lookup

Journey definition: `auth-jwt-scopes.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: auth-jwt-scopes
  version: 0.1.0
spec:
  bindings:
    http:
      start:
        headersToContext:
          traceparent: traceparent
      headersPassthrough:
        - from: traceparent
          to: traceparent

  apis:
    users: { openApiRef: apis/users.openapi.yaml }

  start: validateJwt

  states:
    validateJwt:
      type: task
      task:
        kind: jwtValidate:v1
        profile: default
      next: checkScopes

    checkScopes:
      type: choice
      choices:
        - when:
            predicate:
              # Simple scope check: allow when the scope string
              # contains either "read:users" or "admin".
              # Real deployments SHOULD use a more robust helper.
              lang: dataweave
              expr: |
                (context.auth.jwt.claims.scope default "") contains "read:users"
                  or (context.auth.jwt.claims.scope default "") contains "admin"
          next: fetchUser
      default: scopeDenied
```

Notes:
- `jwtValidate:v1` is responsible only for validating the token and projecting claims into `context.auth.jwt.*`.
- The authZ rule lives in the `choice` predicate, which inspects `context.auth.jwt.claims.scope` to decide whether the caller may proceed.
- Downstream user lookup is a normal `httpCall:v1` task that reads `context.auth.jwt.claims.sub` as the user id and propagates `traceparent` via the HTTP binding (`spec.bindings.http`).
