# ADR-0013 – External-Input Step Responses and Schemas

Date: 2025-11-21 | Status: Proposed

## Context

External-input states (`wait`, `webhook`) currently support:
- Per-step request typing via an inline JSON Schema, and
- A generic response envelope: `POST /journeys/{journeyId}/steps/{stepId}` always returns `JourneyStatus`.

This keeps the Journeys API simple but makes it hard for UI clients and BFFs to:
- Receive step-specific response data (for example, temporary tokens, follow-up URLs, or hints for subsequent API calls) without polling the full `JourneyOutcome`, and
- Rely on typed OpenAPI contracts for those fields.

Today authors work around this by:
- Embedding all step-specific data into `context` and only surfacing it via the final `JourneyOutcome.output`, or
- Overloading `JourneyStatus.attributes` for ad-hoc fields, which is not what attributes are designed for.

We want a first-class, spec-visible way to project selected `context` fields into step responses, with JSON Schema integration for per-step response shapes, while preserving:
- The generic Journeys API (`JourneyStatus` / `JourneyOutcome`) as the platform-level contract, and
- The existing `succeed.outputVar` mental model for mapping `context` variables to HTTP response bodies.

## Decision

- Extend `wait` and `webhook` state definitions with an optional `response` block:
  - `response.outputVar: <string>` – name of a context variable whose value is projected into the step response.
  - `response.schema: <JsonSchema>` – inline JSON Schema describing the additional top-level fields contributed by `outputVar`.
- Semantics:
  - Step requests (`POST /journeys/{journeyId}/steps/{stepId}`) continue to:
    - Validate the request body against the state’s `input.schema`, when present, and
    - Drive the state machine as defined (including any `apply.mapper` and `on` branching).
  - After the state’s effects are applied, the engine:
    - Builds the `JourneyStatus` response object as today.
    - If `response.outputVar` is set and `context[outputVar]` is an object, shallow-merges its properties into the top-level JSON response, as additional fields alongside the standard `JourneyStatus` properties.
    - If `context[outputVar]` is missing or not an object, returns a plain `JourneyStatus` without extra fields.
  - Reserved `JourneyStatus` fields:
    - The following properties are reserved and MUST NOT be overridden by projected fields: `journeyId`, `journeyName`, `phase`, `currentState`, `updatedAt`, `tags`, `attributes`, `_links`.
    - DSL validation MUST reject specs that attempt to redefine these properties via `response` projections when such collisions can be detected statically; tools SHOULD warn when `response.schema` declares reserved properties.
- OpenAPI export:
  - The generic Journeys OpenAPI (`docs/3-reference/openapi/journeys.openapi.yaml`) remains unchanged.
  - Per-journey OpenAPI exports, when they encounter a `wait`/`webhook` state with `response.schema`, describe the step’s `200` response as:
    - `allOf: [ JourneyStatus, <step schema> ]`, where:
      - `JourneyStatus` is the shared component in the per-journey OAS file.
      - `<step schema>` is created from `response.schema` as an object schema that defines additional top-level properties.
- Scope:
  - This decision applies to `kind: Journey` specs and their external-input states.
  - It does not change `JourneyOutcome` or `succeed` / `fail` semantics; final responses continue to use `outputVar` and `spec.output.schema` as defined in the DSL reference.

This decision is specified normatively in:
- Feature 007 spec – `docs/4-architecture/features/007/spec.md`.
- DSL reference – `docs/3-reference/dsl.md` (§12 and step export mapping).
- OpenAPI export guideline – `docs/4-architecture/spec-guidelines/openapi-export.md`.

## Consequences

Positive:
- UI clients and BFFs can receive step-specific, typed data in a single call to the step endpoint, without waiting for the final journey outcome.
- The contract for step responses remains a single JSON document with a simple top-level structure; there is no nested `stepOutput` envelope.
- Authors reuse the familiar `outputVar` pattern to project response fields, keeping the DSL consistent between final outcomes and step responses:
  - Journey final response (global contract):
    - `spec.output.schema` types `JourneyOutcome.output`.
    - `succeed.outputVar` selects which `context.*` value is exposed as `output`.
  - Step response (per-state contract):
    - `wait/webhook.response.schema` types the additional top-level fields.
    - `wait/webhook.response.outputVar` selects which `context.*` object is merged into the step response alongside `JourneyStatus` fields.
  - Semantically, both are “(var + schema) → HTTP response”, just scoped differently (global vs per-step).
- OpenAPI exports clearly describe the combined shape of `JourneyStatus` plus step-specific fields using standard `allOf` composition.

Negative / trade-offs:
- Step responses become more flexible, which slightly increases cognitive load: clients must understand that `JourneyStatus` is the base shape, with optional additional fields per step.
- Engines and exporters need additional validation to prevent collisions between projected fields and reserved `JourneyStatus` properties.
- Step response schemas are defined per journey/step, which may lead to some duplication across specs; we accept this for now in favour of locality and explicitness.

Rationale – no `spec.response.*`:
- We intentionally did not introduce a separate `spec.response` block with `outputVar` / an additional schema:
  - Final responses already have a clear global contract via `spec.output.schema` and `succeed.outputVar`, so a second `spec.response` layer would duplicate semantics without changing behaviour.
  - The final `JourneyOutcome` is inherently global (there is only one final response per run), whereas external-input steps are inherently local (many states, each with its own response contract); having a `response` container only at the per-step level reflects that difference.
- Capturing the projection concept once at the global level (for `JourneyOutcome`) and once at the per-step level (for `JourneyStatus` plus extra fields) keeps the DSL small while still making the symmetry explicit.

Follow-ups:
- Implement parser/model support for `wait.response` and `webhook.response`.
- Implement engine behaviour for projecting `response.outputVar` into step responses.
- Extend OpenAPI exporters and examples (`wait-approval`, `payment-callback`) to demonstrate the pattern.
