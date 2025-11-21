# ADR-0009 – Journeys Hypermedia Links (`_links`)

Date: 2025-11-20 | Status: Proposed

## Context
The Journeys API exposes journey instances via JSON envelopes:
- `JourneyStartResponse` – returned when a journey is started.
- `JourneyStatus` – returned when polling a journey’s status.
- `JourneyOutcome` – returned when retrieving the terminal result.

Callers often need to know **what they can do next**:
- Where to poll for status and results.
- Which step endpoints are currently active for external input (`wait` / `webhook`).
- How to discover related operations without hard-coding URIs in clients.

The DSL already defines:
- The set of states and which ones are external-input (`wait`, `webhook`).
- Per-journey semantics (start, result, steps).

We want to:
- Expose this information as **hypermedia links** in responses, without changing the core envelope shape.
- Make it **on by default** for all journey definitions, but allow authors to disable it at the journey level when needed.

## Decision
We introduce an optional hypermedia links mechanism for journeys, backed by a small configuration block:

```yaml
spec:
  httpSurface:
    links:
      enabled: false    # optional; default behaviour is as if true when omitted
```

When links are enabled (the default), the Journeys API responses MAY include a HAL-style `_links` object:

```json
{
  "journeyId": "123",
  "journeyName": "wait-approval",
  "phase": "Running",
  "currentState": "waitForApproval",
  "_links": {
    "self":   { "href": "/api/v1/journeys/123", "method": "GET" },
    "result": { "href": "/api/v1/journeys/123/result", "method": "GET" },
    "waitForApproval": {
      "href": "/api/v1/journeys/123/steps/waitForApproval",
      "method": "POST"
    }
  }
}
```

Key points:
- `_links` is **optional** and additive; omitting it does not change existing semantics.
- Links are **on by default** (when `spec.httpSurface.links` is absent); journey definitions can opt-out with `enabled: false`.
- The link relation names are intentionally simple:
  - `self` – the current resource.
  - `result` – the final outcome resource for the journey.
  - Additional rels for active step endpoints MAY reuse the state id (for example `waitForApproval`), or an implementation MAY adopt a more structured naming convention later.
 - For self-service journeys that support user cancellation:
   - Cancellation is expressed as a normal failure mode with its own stable error code (for example `JOURNEY_CANCELLED`), not as a separate phase in the schema.
   - `_links.cancel` SHOULD be present whenever a journey is `Running` and cancellable, and MUST be omitted once the journey becomes terminal.
   - `_links.cancel` MUST be idempotent: repeated calls when the journey is already terminal or cancelled MUST NOT change the outcome.

Specification updates:
- DSL reference (`docs/3-reference/dsl.md`):
  - New section “2e. HTTP surface & hypermedia links (spec.httpSurface.links)” describing:
    - The `spec.httpSurface.links.enabled` flag.
    - Default behaviour (links enabled when the block is absent).
    - How `_links` is attached to `JourneyStartResponse`, `JourneyStatus`, and `JourneyOutcome`.
- Journeys OpenAPI (`docs/3-reference/openapi/journeys.openapi.yaml`):
  - `JourneyStartResponse`, `JourneyStatus`, and `JourneyOutcome` gain an optional `_links` property:
    - `_links` is an object whose values are `Link` objects with `{ href: string, method?: string }`.
  - A reusable `Link` schema is added under `components.schemas`.
- OpenAPI export guideline (`docs/4-architecture/spec-guidelines/openapi-export.md`):
  - Canonical schema definitions are updated to include optional `_links` in all journey envelopes.
  - `Links` is described as a HAL-style object and tied to `spec.httpSurface.links.enabled`.

Engine guidance:
- For journeys where links are **enabled** (default):
  - `JourneyStartResponse` SHOULD include:
    - `_links.self` – the canonical status URL for the started journey.
    - `_links.result` – the canonical result URL for that journey.
  - `JourneyStatus` SHOULD include:
    - `_links.self` – the status URL being queried.
    - `_links.result` – the corresponding result URL.
    - One link per active external-input state (for example `/steps/{stepId}`) when such states exist.
     - When the journey is cancelled via `_links.cancel`, the resulting outcome SHOULD use a dedicated cancellation error code (for example `JOURNEY_CANCELLED`) and follow the general error model (phase + error envelope).
  - `JourneyOutcome` SHOULD include:
    - `_links.self` – the result URL for that journey.
- For journeys where `spec.httpSurface.links.enabled == false`:
  - Implementations SHOULD omit `_links` from these envelopes, even if the generic Journeys API and OpenAPI schemas still declare `_links` as optional.

## Consequences
- Pros:
  - Makes next actions discoverable for clients without hard-coded knowledge of all endpoints.
  - Aligns the Journeys API with HATEOAS principles while keeping envelopes backwards compatible.
  - Provides a per-journey escape hatch for environments that prefer bare responses.
- Cons:
  - Adds some response size and conceptual complexity (clients must understand `_links`).
  - Implementations must maintain link generation logic in sync with route and spec evolution.
  - Link relation naming is intentionally light and may need refinement in later versions.

Overall, this decision gives JourneyForge a clean, opt-out hypermedia layer that sits naturally on top of the existing Journeys API, driven by the DSL spec rather than ad-hoc conventions.
