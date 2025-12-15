# Feature 003 – Outbound HTTP Auth & Secrets

| Field | Value |
|-------|-------|
| Status | Ready |
| Last updated | 2025-12-10 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/003/plan.md` |
| Linked tasks | `docs/4-architecture/features/003/tasks.md` |
| Roadmap entry | #003 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a spec‑first model for outbound HTTP authentication and secret references so that journey definitions can:
- Attach reusable auth policies to HTTP tasks (for example OAuth2 client credentials, static bearer tokens, and mTLS client certs).
- Refer to secrets via opaque `secretRef` strings without inlining sensitive material in DSL specs.
- Rely on the engine to cache and reuse short‑lived tokens (such as access tokens) across journey instances until expiry.

This feature extends the DSL and engine behaviour for *outbound* HTTP only; inbound authentication/authorisation is expressed via auth task plugins in the state graph and surrounding platform enforcement (see `docs/3-reference/dsl.md`, section 18).

## Goals
- Add a `spec.policies.httpClientAuth` block to the DSL to configure outbound HTTP auth policies.
- Allow HTTP `task` (`kind: httpCall:v1`) states to reference outbound auth policies via a small `auth` sub‑block.
- Define policy kinds for:
  - Static bearer tokens sourced from a secret.
  - OAuth2 client‑credentials flows with flexible form payloads and multiple token endpoint auth methods (client secret, mTLS).
  - Outbound client certificates for mutual TLS.
- Introduce a clear `secretRef` contract for resolving secrets (tokens, client secrets, client certs/keys) from an engine‑managed secret store.
- Define caching behaviour for OAuth2 access tokens so that tokens obtained with identical effective requests can be reused across journey instances until expiry.

## Non-Goals
- No UI or admin API for managing the secret store in this increment; we only define the DSL and engine contract for resolving `secretRef` values.
- No refresh token handling beyond basic access‑token caching.
- No support for arbitrary OAuth grants (password, device code, etc.); this increment focuses on `client_credentials`.
- No per‑task inline secrets; all sensitive material must flow via `secretRef`.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-003-01 | Model outbound HTTP auth policies in DSL. | Specs declare `spec.policies.httpClientAuth` with `default` and `definitions[<id>]` entries; `kind` distinguishes policy types. | Invalid shapes (missing required fields, unknown `kind`) are rejected at spec validation. | Engine refuses to start journeys with invalid outbound auth config, with clear error messages. | Log which policy ids are loaded at startup (no secrets), emit metrics per policy usage. | Roadmap notes |
| FR-003-02 | Attach outbound auth policies to HTTP tasks. | HTTP `task` definitions may specify `auth.policyRef`; when present, engine applies the referenced outbound auth configuration to the HTTP request. | Unknown `policyRef` or incompatible policy kind yields validation error. | Execution fails fast if `policyRef` cannot be resolved or applied. | Include policy id (not secret values) in debug logs and traces for outbound calls. | DSL ref |
| FR-003-03 | Support static bearer token auth. | Policy `kind: bearerStatic` reads a secret via `secretRef` and sends `Authorization: Bearer <token>` on matching HTTP tasks. | Secret resolution contract is well‑defined; missing secrets are surfaced as configuration/runtime errors. | Engine fails the call (and journey) with a clear error when the referenced secret cannot be resolved. | Count calls using static bearer auth per policy id; never log token values. | Security requirements |
| FR-003-04 | Support OAuth2 client credentials. | Policy `kind: oauth2ClientCredentials` configures `tokenEndpoint`, token endpoint auth method, and form payload (including `grant_type=client_credentials` plus scopes/audience/extra params). Engine obtains an access token and uses it as a bearer token for matching HTTP tasks. | Spec validation ensures required fields are present and that `grant_type` is `client_credentials` when specified. | Token acquisition failures (4xx/5xx/network) result in a structured journey error; engine may retry according to resilience policies. | Emit metrics for token acquisition attempts, successes, and failures per policy id; redact token bodies from logs. | OAuth2 spec |
| FR-003-05 | Support outbound client certificates (mTLS). | Policy `kind: mtlsClient` references a client certificate/key via `clientCertRef` and optionally outbound `trustAnchors`. Engine attaches the client cert to matching HTTP calls. | Validate that `clientCertRef` is non‑empty; engines may perform additional static checks. | Connection failures due to cert issues are surfaced as HTTP errors in the usual way. | Metrics for mTLS usage per policy id, optional fingerprint hashing for debugging (no raw certs in logs). | TLS/mTLS guidance |
| FR-003-06 | Define secret resolution contract. | `secretRef` strings are treated as opaque identifiers; engine resolves them against an implementation‑specific secret store. No DSL construct can read or manipulate raw secrets. | Spec validation ensures only allowed fields accept `secretRef`; secrets never appear in `context`. | Attempts to interpolate or expose secrets into `context` or logs are rejected by design. | Security audits confirm zero occurrences of raw secrets in DSL‑driven data. | Security requirements |
| FR-003-07 | Token caching semantics. | Engines cache access tokens per effective token request (at minimum: policy id, token endpoint, auth method, form payload) and reuse them across journey instances until expiry, applying a small pre‑expiry skew based on `exp`/`expires_in` when available. | Behaviour is documented in DSL/feature spec; conformance tests validate reuse vs refresh. | Cache misses or mis‑configurations result only in extra token calls, not incorrect authorisation. | Metrics for cache hit/miss per policy id; token TTL distributions. | Performance requirements |
| FR-003-08 | Automatic refresh on 401. | For policies with `kind: oauth2ClientCredentials`, when a data‑plane HTTP call using that policy receives `401 Unauthorized`, the engine discards the cached token, obtains a new access token once, and retries the HTTP call once with the new token; a second `401` is surfaced as an error without further automatic retries. | Behaviour is covered by tests that simulate expired/revoked tokens and 401 responses from downstream services. | Journeys see a single HTTP task result reflecting either success with a refreshed token or a final 401‑derived error; idempotency concerns are documented and can be mitigated by disabling the behaviour via engine configuration. | Metrics for 401-triggered refresh attempts, successes, and failures per policy id; logs that correlate 401s with refresh/retry decisions (no tokens). | Performance & reliability requirements |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-003-01 | Secrets are never exposed via DSL or logs. | Security posture. | Code and config scanning show only `secretRef` identifiers in specs/logs; no raw secrets. | Secret store implementation, logging policy. | Security requirements |
| NFR-003-02 | Token acquisition overhead is amortised. | Latency & throughput. | For repeated calls under the same policy, majority of HTTP tasks reuse cached tokens rather than calling the token endpoint each time. | HTTP client, cache implementation. | Performance requirements |
| NFR-003-03 | Backwards compatibility with existing specs. | Adoption. | Specs that do not use `httpClientAuth` continue to behave exactly as before. | DSL parser, engine configuration. | Roadmap |

## UI / Interaction Mock-ups
```
# Example policy snippet in a journey definition
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
            scope: orders.read orders.write

# Usage on a task
spec:
  states:
    createOrder:
      type: task
      task:
        kind: httpCall:v1
        operationRef: orders.create
        auth:
          policyRef: backendDefault
      next: done
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-003-01 | HTTP task with `auth.policyRef` uses static bearer token; request succeeds and journey continues as usual. |
| S-003-02 | HTTP task with OAuth2 policy obtains token once, then multiple journey instances reuse it until expiry. |
| S-003-03 | Token endpoint returns 401/400; journey records a structured error and does not leak token response body. |
| S-003-04 | Secret referenced by `secretRef` is missing; engine fails fast with configuration error. |
| S-003-05 | mTLS client policy attaches correct certificate; upstream requires client cert and accepts the request. |

## Test Strategy
- DSL validation tests for `spec.policies.httpClientAuth` shapes, including invalid `kind` and missing required fields.
- Engine tests for:
  - Static bearer auth: outbound request carries `Authorization: Bearer` header with token obtained via secret store stub.
  - OAuth2 client credentials: token acquisition flow, error handling, and caching semantics.
  - mTLS client cert attachment using stubbed HTTP client.
- Security tests to ensure secrets never appear in `context`, logs, or error messages.
- Performance tests (benchmarks) to verify token caching efficiency under repeated calls.

## Interface & Contract Catalogue
- DSL / Spec:
  - `spec.policies.httpClientAuth.default: <policyId>` (optional hint for spec authors and tooling; engines MUST NOT apply this policy implicitly to HTTP tasks that omit `task.auth.policyRef`).
  - `spec.policies.httpClientAuth.definitions[<id>]` with supported `kind` values:
    - `bearerStatic` (fields: `tokenRef: secretRef`).
    - `oauth2ClientCredentials` (fields: `tokenEndpoint`, `auth.method`, `auth.clientId`, `auth.clientSecretRef?`, `auth.clientCertRef?`, `form.*`).
    - `mtlsClient` (fields: `clientCertRef`, optional `trustAnchors`).
  - HTTP task extension:
    - `task.auth.policyRef: <id>`.
- Engine configuration:
  - Secret store contract keyed by `secretRef` (opaque `string`).
  - Optional engine‑level cache configuration for token reuse (TTL skew, max entries).
- Telemetry:
  - Metrics and traces keyed by outbound auth policy id (no secret material).
