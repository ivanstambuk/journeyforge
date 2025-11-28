# Feature 004 – HTTP Cookies & Journey Cookie Jar

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-21 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/004/plan.md` |
| Linked tasks | `docs/4-architecture/features/004/tasks.md` |
| Roadmap entry | #004 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a first‑class HTTP cookie model for journeys and API endpoints so that the engine can:
- Maintain a per‑run cookie jar populated from downstream `Set-Cookie` responses, scoped to configured domains.
- Attach matching cookies automatically to outbound HTTP tasks.
- Optionally return selected cookies to the client as `Set-Cookie` on successful terminal responses.

This feature is opt‑in per spec via `spec.cookies` and does not change behaviour for existing journeys that do not declare a cookie configuration.

## Goals
- Add a `spec.cookies` block to the DSL to configure:
  - Which cookie domains are tracked in a per‑run cookie jar.
  - How cookies from the jar may be returned to the caller as `Set-Cookie` on success.
- Extend HTTP `task` (`kind: httpCall:v1`) with a `cookies.useJar` flag to control whether the jar applies to a given task.
- Define precise semantics for:
  - Jar population from downstream `Set-Cookie` (including deletions).
  - Domain/path scoping and wildcard domain patterns.
  - Automatic `Cookie` header synthesis for outbound HTTP tasks when `useJar` is enabled.
  - Emission of `Set-Cookie` on terminal `succeed` responses based on allow‑list and filters.
- Keep inbound cookies explicit: the jar must not unconditionally ingest the client’s `Cookie` header; journeys that need inbound cookies work with them via existing header bindings and transforms.

## Non-Goals
- No generic, cross‑journey cookie store or persistence; the jar is strictly per journey instance or per API invocation.
- No DataWeave binding that exposes the jar content directly to DSL expressions in this increment.
- No automatic transformation of inbound `Cookie` into the jar; inbound cookies are only available via explicit header bindings or engine‑specific mechanisms.
- No support for cookie partitioning or advanced browser features (e.g. CHIPS) beyond standard domain/path/secure attributes in this increment.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-004-01 | Opt‑in cookie configuration per spec. | Journey and API specs may declare `spec.cookies.jar` and `spec.cookies.returnToClient`; specs without this block behave exactly as before. | DSL validation ensures that when `spec.cookies` is present, nested fields follow the documented shapes and enums. | Invalid shapes (unknown fields, invalid enums, malformed regex) cause spec validation failures with clear error messages. | Count how many specs enable `spec.cookies` and which domains are configured (names only, no values). | DSL ref, Feature 004 |
| FR-004-02 | Per‑run cookie jar populated from downstream `Set-Cookie`. | For specs with `spec.cookies.jar`, every HTTP task response (`kind: httpCall:v1`, non‑notify) is parsed for `Set-Cookie`; cookies whose effective domain matches an allowed pattern are stored or deleted in the per‑run jar using “last writer wins”. | Validation ensures `spec.cookies.jar.domains[*].pattern` is non‑empty and uses supported domain wildcard semantics. | Engine ignores cookies whose domains are not in the allow‑list; malformed `Set-Cookie` headers may be logged and skipped but must not crash execution. | Emit debug/trace logs (with values redacted) when cookies are added/updated/removed in the jar; per‑domain counters for jar writes. | Cookie RFCs, DSL ref §Cookies |
| FR-004-03 | Domain/path‑scoped attachment of jar cookies to outbound HTTP tasks. | For tasks where `cookies.useJar` is enabled and no explicit `Cookie` header is configured, the engine attaches a `Cookie` header built from jar entries whose `(domain,path)` scope matches the outbound request URL and secure flag. | DSL validation ensures that `task.cookies` is only used under HTTP tasks and that `useJar` (when present) is boolean. | Misconfiguration (e.g. `task.cookies` on non‑HTTP tasks) is rejected at validation; missing jar config means `useJar` has no effect. | Traces show which cookies (names only) were attached per outbound call; counts of requests that used the jar. | DSL ref §Cookies |
| FR-004-04 | Respect explicit `Cookie` headers over jar attachment. | When a `Cookie` header is configured via `spec.defaults.http.headers` or `task.headers.Cookie`, the engine uses that value as‑is and does not apply jar cookies for that task, regardless of `useJar`. | Validation ensures header interpolation remains unchanged and that `useJar` does not affect explicit headers. | There is no automatic merging; authors who need jar + inbound cookies must construct combined headers explicitly. | Telemetry can distinguish between calls that used jar vs explicit cookie headers. | Feature discussion |
| FR-004-05 | Return selected cookies to the client on success. | For specs with `spec.cookies.returnToClient`, when a run terminates in `succeed`, the engine emits `Set-Cookie` headers for jar cookies whose domains are in the allow‑list and whose names pass the configured mode (`none`, `allFromAllowedDomains`, or `filtered` by name/regex), including deletions. | Validation ensures enums are valid, regex patterns compile under the chosen engine flavour, and that the `include` block (when present) is well‑formed. | When config is invalid, spec validation fails; at runtime, failure to emit `Set-Cookie` must not crash execution (worst case: no cookies returned). | Metrics on how many cookies are returned per journey/API and which modes are used; traces may include cookie names (no values). | DSL ref §Cookies |
| FR-004-06 | Support cookie deletion semantics. | When a downstream `Set-Cookie` denotes deletion (e.g. `Max-Age=0` or past `Expires`), the jar removes that cookie and records enough metadata to emit a corresponding deletion `Set-Cookie` back to the client when `returnToClient` would have included that cookie. | Tests verify that deletion from a backend results in a deletion `Set-Cookie` towards the client on the next successful response, given matching filters. | If deletion cannot be projected (for example conflicting configs), the jar still removes the cookie locally; no stale cookies are attached to downstream calls. | Telemetry can count deletion events per domain; logs should not include cookie values. | Cookie RFCs |
| FR-004-07 | API parity for `kind: Journey` and `kind: Api`. | The cookie jar semantics apply identically to long‑lived journey runs and single‑shot API invocations; jar lifetime is per run and discarded after the terminal state. | Conformance tests exercise both admin and journeys surfaces where applicable. | Misalignment (e.g. jar leaking across runs) is treated as a critical bug. | Same telemetry dimensions apply for journeys and APIs. | DSL ref |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-004-01 | Cookie values are never logged or exposed via DSL. | Security & privacy. | Code/log scans show only cookie names and domains, never raw values, in logs and metrics. | Logging configuration, HTTP connector implementation. | Security posture |
| NFR-004-02 | Cookie handling does not significantly regress HTTP performance. | Latency & throughput. | Benchmark HTTP tasks with and without jar enabled; overhead for parsing and attachment stays within an acceptable bound (for example, <5% for common cases). | HTTP client, cookie parser. | Performance requirements |
| NFR-004-03 | Backwards compatibility. | Adoption. | Specs without `spec.cookies` behave exactly as before; regression tests verify identical outcomes for existing examples. | DSL parser and engine behaviour. | Roadmap |

## UI / Interaction Mock-ups
```
# DSL snippet – enable cookie jar and return selected cookies
spec:
  cookies:
    jar:
      domains:
        - pattern: "api.example.com"
        - pattern: ".example.com"

    returnToClient:
      mode: filtered
      include:
        names: ["session", "csrfToken"]
        namePatterns:
          - "^x-app-"

  states:
    callBackend:
      type: task
      task:
        kind: httpCall:v1
        operationRef: backend.getSomething
        cookies:
          useJar: true        # default, may be omitted
      next: done

    done:
      type: succeed
      outputVar: result
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-004-01 | Backend sets a cookie on an allowed domain; subsequent HTTP task to the same domain automatically attaches it, and a `succeed` response returns it to the client when filters match. |
| S-004-02 | Backend sets a cookie on a non‑allowed domain; the jar ignores it, it is not attached to further calls, and never appears in `Set-Cookie` to the client. |
| S-004-03 | A backend deletes a cookie via `Set-Cookie` deletion semantics; the jar removes it and, on the next successful response, the engine emits a deletion `Set-Cookie` towards the client when filters match. |
| S-004-04 | A task sets `cookies.useJar: false` and an explicit `Cookie` header; only the explicit header is used, jar cookies are not attached. |
| S-004-05 | `returnToClient.mode: none`; jar is used for downstream calls but no cookies are ever returned to the client. |
| S-004-06 | `returnToClient.mode: filtered` with both `names` and `namePatterns` configured; cookies whose names match either list are returned, others are not. |

## Test Strategy
- DSL tests:
  - Validate `spec.cookies` and `task.cookies` shapes, including enums and regex compilation.
  - Ensure specs without `spec.cookies` parse as before.
- Engine tests:
  - Jar population from `Set-Cookie`, including domain/path scoping and deletion semantics.
  - Attachment of jar cookies to outbound HTTP calls with and without explicit `Cookie` headers.
  - Behaviour across both `kind: Journey` and `kind: Api`.
- Integration tests:
  - Combined flows where multiple HTTP tasks and terminal `succeed` interact with cookie jar and `returnToClient`.
- Regression tests:
  - Existing HTTP examples (that do not use cookies) to verify unchanged behaviour.

## Interface & Contract Catalogue
- DSL / Spec:
  - `spec.cookies.jar.domains[*].pattern: string` – domain allow‑list with support for leading‑dot subdomain wildcards.
  - `spec.cookies.returnToClient.mode: "none" | "allFromAllowedDomains" | "filtered"`.
  - `spec.cookies.returnToClient.include.names?: string[]`.
  - `spec.cookies.returnToClient.include.namePatterns?: string[]` (Java‑style regex).
  - HTTP task extension: `task.cookies.useJar?: boolean` (defaults to `true` when `spec.cookies.jar` is present).
- Engine behaviour:
  - Per‑run cookie jar keyed by `(domain, path, name)` with last‑writer‑wins semantics and deletion handling.
  - Automatic `Cookie` header synthesis for eligible HTTP tasks.
  - Emission of `Set-Cookie` on terminal success according to configuration.
