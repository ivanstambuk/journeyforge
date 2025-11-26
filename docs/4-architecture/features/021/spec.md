# Feature 021 – Cloud-Function Binding for Journeys and APIs

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/021/plan.md` |
| Linked tasks | `docs/4-architecture/features/021/tasks.md` |
| Roadmap entry | #013 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md` (none currently open for this feature), encode resolved answers directly in the Requirements/NFR/Behaviour sections below, and use ADRs under `docs/6-decisions/` (in particular ADR‑0029 and ADR‑0004) for architecturally significant clarifications.

## Overview

Introduce a cloud-function inbound binding for `kind: Journey` and `kind: Api` definitions via `spec.bindings.function`. The binding captures, at the DSL level, that a definition is intended to be exposed via cloud function platforms (for example AWS Lambda, Google Cloud Functions, Azure Functions) while reusing the existing HTTP / queue semantics:

- For HTTP-triggered functions:
  - Normalise provider events to the HTTP binding model and invoke the same logical `start` / `callApi` operations.
- For non-HTTP triggers:
  - Reuse queue binding semantics (Feature 019) rather than introducing new behaviour.

This feature is **DSL/API design only**; provider-specific deployment details (function names, triggers, IAM roles) and runtime integration are handled by engine/platform configuration and tooling.

Primary references:
- DSL reference: `docs/3-reference/dsl.md` (section 17 – Inbound Bindings, cloud-function binding).
- Inbound bindings ADR: `docs/6-decisions/ADR-0029-inbound-bindings-and-spec-bindings-http.md`.
- API endpoints ADR: `docs/6-decisions/ADR-0004-api-endpoints-kind-api.md`.

## Goals

- Define a minimal, provider-neutral DSL hook for cloud-function deployment:
  - `spec.bindings.function` with no fields in v1; presence is advisory metadata.
- Clarify how cloud-function bindings relate to:
  - HTTP binding (`spec.bindings.http`) for HTTP-triggered functions.
  - Queue binding (`spec.bindings.queue`) for message-triggered functions.
- Avoid encoding provider-specific details (Lambda config, GCF run times, Azure trigger bindings) in the DSL.

## Non-Goals

- No provider-specific fields or annotations in `spec.bindings.function`.
- No new state types or control-flow semantics tied to cloud functions.
- No attempt to standardise error/status mapping for all providers; implementations should build on HTTP/queue semantics and provider documentation.

## DSL (normative)

### 1. `spec.bindings.function`

Definitions MAY declare that they are intended to be deployed as cloud functions:

```yaml
spec:
  bindings:
    function: {}
```

Constraints:
- `spec.bindings.function`:
  - MAY be present for both `kind: Journey` and `kind: Api`.
  - MUST be an object; in this version it MUST NOT define any fields.
- The absence of `spec.bindings.function` does not prevent platforms from wrapping a definition as a cloud function; presence is a declarative signal for tooling, deployment generators, and operators.

### 2. Conceptual mapping – HTTP-triggered functions

For HTTP-triggered cloud functions (for example API Gateway + Lambda, HTTP-triggered GCF/Azure Functions):

- **Incoming event**:
  - The provider delivers an event object containing HTTP-equivalent fields (method, path, headers, body, query params).
  - The cloud-function binding normalises this to the same logical request shape used by the HTTP binding (`spec.bindings.http`).
- **Routing to engine operations**:
  - For `kind: Journey`:
    - HTTP-equivalent calls that match the Journeys API start endpoint are mapped to the logical `start` operation.
  - For `kind: Api`:
    - HTTP-equivalent calls that match the Api endpoint are mapped to the logical `callApi` operation.
- **Response**:
  - The engine produces a `JourneyOutcome`, `JourneyStatus`, or Api response.
  - The binding maps this result into the provider’s response format (for example Lambda proxy integration response, GCF HTTP response message) using provider-specific conventions.

From the DSL’s perspective, HTTP-triggered functions behave like an alternative deployment of the HTTP binding; no additional semantics are introduced.

### 3. Conceptual mapping – non-HTTP triggers

For non-HTTP triggers that deliver messages or events (for example Pub/Sub triggers, SQS-triggered Lambdas):

- Cloud-function bindings SHOULD reuse queue binding semantics:
  - Treat the provider event as a message delivered on a logical `channel`.
  - Map it onto the same logical `start` or `submitStep` operations defined by `spec.bindings.queue`.
- The mapping from provider event fields (payload, attributes) to `channel` / `journeyId` / `stepId` / `context` is defined by engine/platform configuration and the queue binding feature spec, not by `spec.bindings.function` itself.

## Behaviour (conceptual)

- Cloud-function bindings are **deployment-level wrappers** around existing logical entrypoints:
  - They do not change journey or Api semantics.
  - They enable function platforms to host those entrypoints without exposing a long-running HTTP server or queue consumer directly.
- The same journey or Api may be exposed via:
  - HTTP binding only.
  - Cloud-function binding only.
  - Both, depending on deployment needs.

## Non-Functional Requirements

| ID | Requirement | Driver | Notes |
|----|-------------|--------|-------|
| NFR-021-01 | Keep DSL additive and provider-neutral. | Portability. | `spec.bindings.function` must not encode provider-specific configuration; deployment-specific wiring lives in platform config and tooling. |
| NFR-021-02 | Align behaviour with existing bindings. | Simplicity. | HTTP-triggered functions must reuse HTTP binding semantics; message-triggered functions must reuse queue binding semantics. |
| NFR-021-03 | Support code generation/tooling. | DevEx. | Presence of `spec.bindings.function` should be sufficient for tooling to generate provider-specific function wrappers and configuration. |

