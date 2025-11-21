# Feature 007 – External-Input Step Responses & Schemas

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-21 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/007/plan.md` |
| Linked tasks | `docs/4-architecture/features/007/tasks.md` |
| Roadmap entry | #002 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Extend external-input states (`wait`, `webhook`) with an explicit response projection so that step calls can:
- Accept typed request bodies via per-step `input.schema`, and
- Return additional, typed top-level fields alongside the generic `JourneyStatus` envelope,
without introducing a second results envelope or nested response property.

Per-journey OpenAPI exports are updated to describe step responses using `allOf` over the generic `JourneyStatus` schema and a step-specific JSON Schema referenced from DSL.

## Goals
- Add an optional `response` block to `wait` and `webhook` state definitions.
- Allow authors to declare which context variable is projected into step responses via `response.outputVar`, reusing the `outputVar` concept from `succeed`.
- Allow authors to attach a JSON Schema for the additional top-level fields via `response.schema`, and surface it in per-journey OpenAPI exports.
- Keep the generic Journeys API response contracts (`JourneyStatus`, `JourneyOutcome`) unchanged for platform-level tooling.
- Ensure reserved `JourneyStatus` fields (`journeyId`, `journeyName`, `phase`, `currentState`, `updatedAt`, `tags`, `attributes`, `_links`) cannot be overridden by step response projections.

## Non-Goals
- No change to the final result envelope (`JourneyOutcome`) or `succeed`/`fail` semantics in this slice.
- No per-step request envelope changes beyond existing `input.schema`; step requests continue to use the step-specific input schema only.
- No support for streaming or multi-part step responses; step calls remain single JSON documents.
- No UI work in this slice beyond updating reference docs and example OpenAPI specs.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-007-01 | Model `response` projection on `wait`/`webhook`. | DSL defines an optional `response` block under `wait`/`webhook` with fields `outputVar` and `schema`. | Specs that include `response` under other state types are rejected; `outputVar` must match the allowed identifier pattern; `schema` must be a valid JSON Schema object. | Spec validation errors clearly identify invalid response blocks or fields. | None required beyond existing spec validation logs. | DSL ref §12 |
| FR-007-02 | Project context vars into step responses. | When handling `POST /journeys/{journeyId}/steps/{stepId}` for a state with `response.outputVar`, the engine evaluates the state as usual, then, if `context[outputVar]` is an object, shallow-merges its fields into the JSON representation of `JourneyStatus` before returning the response. | Engines MUST treat collisions between projected fields and reserved `JourneyStatus` fields as a validation error at spec load time when detectable; tooling SHOULD warn when the response schema declares reserved property names. | If `context[outputVar]` is missing or not an object, the engine falls back to a plain `JourneyStatus` body without additional fields. | Step-level metrics MAY be extended later to record when extra response fields are used. | ADR-0013 |
| FR-007-03 | OpenAPI: describe step responses with additional fields. | Per-journey OpenAPI exporters, when they see `response.schema` on a `wait`/`webhook` state, emit a step response schema using `allOf: [ JourneyStatus, <step schema> ]` and reference it from the corresponding `/journeys/{journeyId}/steps/{stepId}` `200` response. | Exporters validate that `response.schema` is a valid JSON Schema object and that the resulting document is an object schema. | Export failures surface as CI/spec generation errors, not runtime surprises. | Export steps may add metrics/logs about how many journeys use step response schemas. | OpenAPI spec guidelines |
| FR-007-04 | Backwards compatibility for journeys without `response`. | Journeys that do not use `wait`/`webhook`, or that omit the `response` block on those states, keep the existing behaviour where step responses are plain `JourneyStatus`. | Conformance tests and existing examples (without `response`) continue to validate against unchanged OpenAPI specs. | Any regression in step response wiring is treated as a breaking change and must be reverted. | Regression tests can compare before/after JSON contracts for unchanged specs. | DSL ref, journeys.openapi.yaml |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-007-01 | Preserve simplicity of the Journeys API. | Adoption and cognitive load. | Generic `journeys.openapi.yaml` remains stable; step response enhancements only appear in per-journey OpenAPI. | OpenAPI exporter implementation. | ADR-0013 |
| NFR-007-02 | Keep step response projections explicit and predictable. | Debuggability. | Step response fields always originate from an explicit context variable (`response.outputVar`); there is no implicit inclusion of arbitrary `context`. | DSL validation, engine implementation. | DSL ref §12 |

## UI / Interaction Mock-ups
```yaml
# DSL snippet – manual approval step that returns extra fields to the client
spec:
  states:
    waitForApproval:
      type: wait
      wait:
        channel: manual
        input:
          schema: <JsonSchema>
        response:
          outputVar: stepResponse
          schemaRef: schemas/wait-approval-step-response.json
        apply:
          mapper:
            lang: dataweave
            expr: |
              context ++ {
                approval: payload,
                stepResponse: {
                  decision: payload.decision,
                  approvedBy: payload.approvedBy
                }
              }
        on:
          - when:
              predicate:
                lang: dataweave
                expr: |
                  payload.decision == "approved"
            next: finish
        default: rejected
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-007-01 | `wait` state with `response.outputVar` and object value returns `JourneyStatus` plus extra top-level fields. |
| S-007-02 | `wait` state with `response` omitted behaves exactly as before; step response is plain `JourneyStatus`. |
| S-007-03 | `response.outputVar` points to a non-object value; engine returns plain `JourneyStatus` (no merge). |
| S-007-04 | `response.outputVar` schema attempts to redefine reserved fields; spec validation fails. |

## Test Strategy
- Add DSL validation tests covering valid/invalid `response` blocks on `wait`/`webhook`, including reserved field collisions.
- Extend OpenAPI exporter tests to assert that per-journey OAS files for journeys with response projections use `allOf` over `JourneyStatus` and the declared `schemaRef`.
- Add end-to-end tests (once an engine exists) where step calls return both `JourneyStatus` fields and projected response fields, and ensure that final `JourneyOutcome` behaviour is unchanged.

## Interface & Contract Catalogue
- DSL:
  - `wait.response.outputVar: <string>` – name of a context variable whose object value is projected into the top level of step responses.
  - `wait.response.schema: <JsonSchema>` – JSON Schema that describes the additional top-level fields contributed by `outputVar`.
  - `webhook.response.outputVar` / `webhook.response.schema` – same semantics for webhook callbacks.
- Journeys API (per-journey exports only):
  - Step responses MAY be described as `allOf` of `JourneyStatus` and a step-specific schema derived from `response.schema`.
- Examples:
  - `docs/3-reference/examples/wait-approval.journey.yaml` and its OAS companion updated to demonstrate `wait.response`.
  - `docs/3-reference/examples/payment-callback.journey.yaml` and OAS updated to demonstrate `webhook.response`.
