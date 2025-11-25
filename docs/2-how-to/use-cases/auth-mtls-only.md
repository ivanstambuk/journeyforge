# Use Case – mTLS-Only Authentication

Status: Draft | Last updated: 2025-11-25

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/auth-mtls-only/auth-mtls-only.md`

## Problem

Expose a journey/API that:
- Authenticates callers using client TLS certificates (mTLS).
- Enforces a simple subject-based access rule over the validated certificate.
- Does not rely on JWT at all.

## Relevant DSL Features

- `mtlsValidate:v1` – mTLS validation task plugin (authN).
- `context.auth.mtls.*` – projected certificate metadata for expressions.
- `choice` – predicates over `context.auth.mtls.subjectDn` / other fields.
- `spec.policies.httpSecurity` – optional inbound mTLS policies when enforced at the HTTP surface.

## Example – mTLS-only subject check

Journey definition: `auth-mtls-only.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: auth-mtls-only
  version: 0.1.0
spec:
  start: validateMtls

  states:
    validateMtls:
      type: task
      task:
        kind: mtlsValidate:v1
        profile: default
      next: checkSubject

    checkSubject:
      type: choice
      choices:
        - when:
            predicate:
              # Example policy: only allow client certificates whose subject DN
              # contains "OU=Journeys".
              lang: dataweave
              expr: |
                (context.auth.mtls.subjectDn default "") contains "OU=Journeys"
          next: done
      default: subjectDenied
```

Notes:
- `mtlsValidate:v1` validates the client certificate and projects metadata into `context.auth.mtls.*`.
- The journey uses `choice` to express a simple policy over `context.auth.mtls.subjectDn`.
- On success, the journey can either return certificate metadata (as in the example) or continue to downstream tasks.

