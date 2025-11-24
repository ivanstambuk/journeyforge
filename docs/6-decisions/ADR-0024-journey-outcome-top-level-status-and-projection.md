# ADR-0024 – JourneyOutcome Top-Level Status and Business Field Projection

Date: 2025-11-24 | Status: Accepted

## Context

The Journeys API exposes a `JourneyOutcome` envelope as the canonical result of a terminal journey run. Prior to this decision:

- `JourneyOutcome` exposed engine-level lifecycle via `phase` (see ADR-0023) and carried business data inside a nested `output` object.
- Business outcome codes (for example `JOB_COMPLETED`, `REPORT_FAILED`, `KYC_COMPLETED_PRIMARY`) were typically modeled as `output.status`, and richer structures (for example `overallStatus`, `reportId`, etc.) also lived inside `output`.
- Clients and dashboards that needed to react to business outcomes had to drill into `output.status` (and other nested fields), while status-like engine fields such as `phase` and error codes were already top-level.
- External-input step responses (`wait` / `webhook`) already supported projecting selected business fields into the top-level `JourneyStatus` response via `wait.response` / `webhook.response`:
  - `response.outputVar` selects a context object.
  - Its properties are shallow-merged into the JSON response alongside the standard `JourneyStatus` fields, with reserved fields protected.

This created a few usability and consistency issues:

- Asymmetry between step responses and final outcomes:
  - Step responses could project business fields to the top level of `JourneyStatus`.
  - Final outcomes kept all business fields nested inside `output`.
- Client experience and dashboards:
  - Common UI and reporting use cases wanted a flat, predictable way to read the business outcome code and key fields from `JourneyOutcome` without always traversing `output.*`.
- Semantics clarity:
  - ADR-0023 clarified that `phase` is the engine lifecycle indicator and business status codes live elsewhere, but it did not define a first-class top-level home for the business status itself.

We want to:

- Make it trivial for clients to read the business outcome code from `JourneyOutcome` without guessing journey-specific schema details.
- Preserve the existing projection pattern for step responses and reuse it for final outcomes in a consistent way.
- Keep `output` as the canonical source of business data while allowing a small, controlled set of top-level business fields derived from it.

## Decision

We extend the `JourneyOutcome` envelope and the DSL as follows.

### 1. Top-level `status` on `JourneyOutcome`

- `JourneyOutcome` gains a **required** top-level `status: string` field.
- Semantics:
  - `status` is the **business outcome status code** for the journey instance (for example `JOB_COMPLETED`, `REPORT_FAILED`, `KYC_COMPLETED_PRIMARY`).
  - When `JourneyOutcome.output` is an object with a `status` property:
    - Engines MUST set `JourneyOutcome.status` to the same value as `output.status`.
    - Tooling SHOULD treat `output.status` as the canonical source of truth, with `status` as a strict mirror.
  - When `output` is not an object or does not contain `status`:
    - Engines MUST still provide a non-empty, stable `status` value for the outcome; the mapping from the final context/output to this code is platform-specific but MUST be deterministic for a given journey definition.
- Client guidance:
  - Treat `phase` as the engine lifecycle (`RUNNING` / `SUCCEEDED` / `FAILED`).
  - Treat `status` as the primary business outcome code.
  - Continue to use `error.code` and `error.reason` for error semantics; `status` does not replace error codes for failures.

The generic Journeys OpenAPI (`docs/3-reference/openapi/journeys.openapi.yaml`) is updated to:

- Require `status` in `JourneyOutcome.required`.
- Describe `status` as a string field that mirrors `JourneyOutcome.output.status` when present.

### 2. Spec-level projection of business fields into `JourneyOutcome`

We introduce an optional `spec.output.response` block for `kind: Journey` that mirrors the existing `wait.response` / `webhook.response` pattern but applies to the final `JourneyOutcome` instead of `JourneyStatus`:

```yaml
spec:
  output:
    schema: <JsonSchema>          # existing – shape of JourneyOutcome.output
    response:                     # new – project extra fields into JourneyOutcome
      outputVar: result           # context.result object is merged into the top-level JourneyOutcome
      schema:                     # JSON Schema for additional top-level fields
        type: object
        required: [status]
        properties:
          status:
            type: string
            description: "Business status code; MUST mirror JourneyOutcome.output.status."
          reportId:
            type: string
```

Semantics:

- `spec.output.response.outputVar`:
  - When set and `context.<outputVar>` is an object at the end of the run, its properties are shallow-merged into the top-level JSON `JourneyOutcome` alongside the standard envelope fields.
  - If `context.<outputVar>` is absent or not an object, `JourneyOutcome` is returned without any projected fields (other than the mandatory `status`).
- `spec.output.response.schema`:
  - MUST be a JSON Schema object describing the additional top-level fields produced by the projection.
  - The per-journey OpenAPI definition for `JourneyOutcome` uses `allOf(JourneyOutcome, schema)` to include these fields.
- Reserved fields:
  - Projected properties MUST NOT collide with reserved `JourneyOutcome` fields:
    - `journeyId`, `journeyName`, `phase`, `status`, `output`, `error`, `tags`, `attributes`, `_links`.
  - Implementations and tooling MUST reject specs that attempt to override reserved fields via projection.

Design intent:

- `spec.output.response` is a **mirror of the existing step-response projection model**:
  - Step responses: `wait.response` / `webhook.response` project fields into `JourneyStatus`.
  - Final outcomes: `spec.output.response` projects fields into `JourneyOutcome`.
- Authors can choose which additional business fields (for example `overallStatus`, `reportId`, `jobType`) should appear as top-level fields in the final outcome, while keeping the full business object under `output`.

### 3. Authoring guidance

- Authors SHOULD:
  - Define `spec.output.schema` with a required `status` property for journeys that return a meaningful business outcome.
  - Ensure that terminal `succeed`/`fail` paths populate `output.status` consistently for all outcomes.
  - Use `spec.output.response` when clients benefit from a flatter view over key business fields in `JourneyOutcome`.
- Consumers SHOULD:
  - Rely on `status` (not `phase`) for business status dashboards and domain-specific decision logic.
  - Continue to use `output` when they need the full, rich business object.

## Consequences

Positive:

- **Improved client ergonomics**:
  - Clients and dashboards can read `phase` and `status` directly from the top-level `JourneyOutcome` object, without having to know the shape of `output`.
  - Async patterns such as `async-fire-and-observe`, `async-callback-sla`, and `async-report-generation` can expose clear business status codes (for example `JOB_COMPLETED`, `REPORT_COMPLETED`) in a consistent place.
- **Consistency with step responses**:
  - The projection model for final outcomes is aligned with the existing `wait.response` / `webhook.response` pattern for `JourneyStatus`.
  - The same reserved-field and schema-driven rules apply to both step responses and final outcomes.
- **Preserves `output` as canonical**:
  - Business data and schema remain centred on `JourneyOutcome.output`, with top-level fields treated as projections/shortcuts derived from it.

Negative / trade-offs:

- **Breaking wire-format change**:
  - `JourneyOutcome` now requires a top-level `status` field.
  - Existing consumers and stored JSON documents that did not expect `status` must be updated or migrated.
- **Additional complexity in the spec**:
  - Authors must understand both `spec.output.schema` and `spec.output.response` when modeling complex journeys.
  - Tools must enforce reserved-field rules and keep `status` aligned with `output.status`.
- **Potential over-projection**:
  - Without good documentation and linting, authors could project too many fields into `JourneyOutcome`, making the envelope noisier.

Overall, this decision makes `JourneyOutcome` more ergonomic and consistent with step responses by providing a first-class, top-level business `status` and a controlled way to surface additional business fields, while keeping `output` as the canonical source of business data.
