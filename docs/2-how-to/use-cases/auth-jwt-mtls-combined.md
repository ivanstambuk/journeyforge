# Use Case – Combined JWT + mTLS AuthN/AuthZ

Status: Draft | Last updated: 2025-11-25

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/auth-jwt-mtls/auth-jwt-mtls.md`

## Problem

Expose a journey/API that:
- Requires both a valid client TLS certificate and a valid JWT on the same entry path.
- Expresses authorisation rules over both `context.auth.mtls.*` and `context.auth.jwt.*`.
- Keeps JWT/mTLS validation as explicit `task` states, not implicit gateway configuration.

## Relevant DSL Features

- `mtlsValidate:v1` – mTLS validation task plugin (authN).
- `jwtValidate:v1` – JWT validation task plugin (authN).
- `context.auth.mtls.*` and `context.auth.jwt.*` – projected auth data for expressions.
- `choice` – combined predicates over certificate metadata and JWT claims.
- HTTP binding (`spec.bindings.http`) – to propagate trace headers and other metadata downstream.

## Example – Combined policy for admin clients

Journey definition: `auth-jwt-mtls.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: auth-jwt-mtls
  version: 0.1.0
spec:
  start: validateMtls

  states:
    validateMtls:
      type: task
      task:
        kind: mtlsValidate:v1
        profile: default
      next: validateJwt

    validateJwt:
      type: task
      task:
        kind: jwtValidate:v1
        profile: default
      next: checkCombinedPolicy

    checkCombinedPolicy:
      type: choice
      choices:
        - when:
            predicate:
              # Example combined authZ: require that JWT scopes include "admin"
              # and that the client certificate subject DN contains "OU=AdminClients".
              lang: dataweave
              expr: |
                (context.auth.jwt.claims.scope default "") contains "admin"
                  and (context.auth.mtls.subjectDn default "") contains "OU=AdminClients"
          next: done
      default: accessDenied
```

Notes:
- The journey separates authN from authZ:
  - `mtlsValidate:v1` and `jwtValidate:v1` handle authentication and projection into `context.auth.mtls.*` and `context.auth.jwt.*`.
  - `checkCombinedPolicy` expresses the combined authorisation rule as a predicate.
- You can extend this pattern with additional predicates or subjourneys for different roles/tenants while keeping the core auth tasks reusable.
