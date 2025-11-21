# Use Case – Subject-Scoped Self-Service Steps

Status: Draft | Last updated: 2025-11-20

## Problem

Design self-service journeys where:
- A single principal logically “owns” the journey when it is started.
- Follow-up steps (for example, a confirmation) MUST be executed by the same principal.
- If a different principal calls a follow-up step, the journey should return a clear security
  error instead of proceeding.

We want a pattern where:
- The journey captures the subject from the JSON Web Token when it is started and stores it in
  `context` as the “owner” id.
- On a later step call, the journey reads the subject from the current JSON Web Token again and
  compares it with the stored owner id.
- On mismatch, the journey fails with a security error that can be surfaced to the frontend.

## Relevant DSL Features

- `spec.policies.httpSecurity` and `spec.security.journeyPolicyRef` for enforcing that only
  authenticated users can start journeys.
- `transform` states for normalising identity from auth context into `context` (for example
  copying `context.auth.jwt.claims.sub`).
- `wait` state for external/manual input and step endpoints.
- `choice` and `fail` states to compare stored and current subjects and return a security error.

## Example – subject-step-guard

Journey definition: `subject-step-guard.journey.yaml`

This journey shows how to:
- Capture the subject from the JWT when the journey starts (`ownerUserId`).
- Capture the subject again when a follow-up step is called (`stepUserId`).
- Compare both and fail with a security error when they differ.

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: subject-step-guard
  version: 0.1.0
spec:
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
              sub: { type: string }
            scope: { contains: ["journey:self-service"] }

  security:
    journeyPolicyRef: jwtDefault

  start: captureOwner

  states:
    captureOwner:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              ownerUserId: context.auth.jwt.claims.sub
            }
        target:
          kind: context
          path: ''
      next: waitForUserAction

    waitForUserAction:
      type: wait
      wait:
        channel: manual
        input:
          schema:
            type: object
            properties:
              # step-specific payload fields, e.g. approval decision
              decision: { type: string }
            additionalProperties: true
        apply:
          mapper:
            lang: dataweave
            expr: |
              context ++ {
                stepUserId: context.auth.jwt.claims.sub,
                lastPayload: payload
              }
        on:
          - when:
              predicate:
                lang: dataweave
                expr: |
                  context.stepUserId == context.ownerUserId
            next: proceed
        default: subjectMismatch

    proceed:
      type: succeed
      outputVar: lastPayload

    subjectMismatch:
      type: fail
      errorCode: SUBJECT_MISMATCH
      reason: "Authenticated principal does not match journey owner"
```

In this pattern, the journey itself enforces that only the same subject who started the
journey can successfully execute the follow-up step. Any attempt by a different subject
results in a `SUBJECT_MISMATCH` failure that can be surfaced as a security error to the
frontend.
