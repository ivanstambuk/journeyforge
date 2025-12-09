# Feature 012 – Expression Engine Plugins

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/012/plan.md` |
| Linked tasks | `docs/4-architecture/features/012/tasks.md` |
| Roadmap entry | #004 |

> Guardrail: This specification is the single normative source of truth for expression engine plugins. Track medium- and high-impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour sections below, and record architecturally significant clarifications in ADRs under `docs/6-decisions/` (see ADR-0027).

## Overview

Introduce a pluggable expression engine model so that all DSL expression sites that currently use `lang` for expressions can be backed by a versioned Expression Engine plugin. This allows JourneyForge to:
- Keep a single, pure evaluation model for all predicates and mappers, and
- Add or swap expression dialects (for example DataWeave, JSONata, JOLT, jq) in a uniform way without changing the DSL surface beyond `lang` values.

This feature:
- Defines an Expression Engine SPI used by the engine to evaluate expressions in:
  - `choice` predicates,
  - `transform` states,
  - `mapper` blocks in tasks, `wait`/`webhook`/`schedule`, and
  - error mappers under `spec.errors` and similar blocks.
- Keeps expression evaluation **pure** (no external I/O or side effects beyond producing values) and delegates all side-effectful behaviour to task plugins and connectors.

## Goals

- Define a **pure Expression Engine SPI** that all expression dialects (DataWeave, JSONata, JOLT, jq) implement.
- Allow DSL authors to select expression engines via `lang: <engineId>` wherever `lang: dataweave` is currently supported.
- Integrate the expression engine SPI across all expression sites (choice predicates, transform states, mappers, error mappers) without changing their DSL shape.
- Leave concrete engine implementations and baseline-engine requirements to language-specific features (for example DataWeave in Feature 013).

## Non-Goals

- No changes to side-effectful task plugins (Feature 011); expression engines cannot call connectors or perform external I/O.
- No new DSL state types; `choice`, `transform`, `mapper` blocks, and error mappers remain the same syntactically.
- No cross-language expression migration tooling in this feature; authors are responsible for choosing and maintaining expression dialects.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-012-01 | Pluggable expression engines via `lang`. | All DSL locations that accept `lang` (choice predicates, transform states, mappers, error mappers) treat `lang` as a selector for a registered expression engine (for example `dataweave`, `jsonata`, `jolt`, `jq`), and all expression blocks that embed `expr` MUST declare `lang` explicitly (no DSL-level default engine). | Engine configuration registers one or more expression engines; tests cover choosing different engines via `lang` and verifying correct dispatch. | If the DSL references `lang: <engineId>` and no corresponding engine is registered, spec validation or engine startup fails with a clear configuration error; runtime MUST NOT silently fall back to a different engine. | Telemetry tags expression evaluations with `expr.engine=id` and MAY track counts/latency per engine. | DSL ref §4, §10; ADR-0027 |
| FR-012-02 | Pure expression engine SPI. | Expression engines implement a SPI that accepts an expression string plus bindings (`context`, `payload`, `error`, `platform`) and returns an `ExpressionResult` that is either a value (JSON or primitive) or a Problem Details object representing an evaluation error; engines cannot perform external I/O or mutate engine state outside the returned value. | SPI docs and tests assert that expression implementations do not have access to connector services and that `ExpressionResult` never mutates `context`; composition with task plugins uses only values returned from expressions. | If an expression engine attempts to reach connectors, mutate global state, or surface non-canonical errors, this is treated as a bug and fails the run with an internal error; implementations must be corrected to comply. | Telemetry distinguishes expression evaluation errors (for example syntax/runtime errors) from engine/plugin internal errors. | ADR-0027 |
| FR-012-03 | Support additional dialects as optional engines. | Engines MAY ship with additional expression engines (for example DataWeave, JSONata, JOLT, jq) and register them under distinct ids (for example `dataweave`, `jsonata`, `jolt`, `jq`); DSL authors can opt into them by setting `lang` appropriately. | Tests cover evaluating simple and moderately complex expressions in each engine; docs describe any expressiveness limitations or differences between engines (for example when contrasting `dataweave` and `jsonata`). | When an engine is not available at runtime but is referenced in DSL, spec validation fails with a clear message; there is no runtime fallback. | Telemetry MAY aggregate counts per engine id to understand usage across journeys. | ADR-0027; DSL ref §4, §10 |
| FR-012-04 | Cross-engine configuration and limits. | Expression engines are configured under `plugins.expressions` with deployment-wide `defaults` and optional per-engine overrides in `engines.<engineId>`, supporting at minimum `maxEvalMs` and `maxOutputBytes` as cross-engine limits. | Configuration reference and tests cover applying defaults and per-engine overrides and assert that engines enforce `maxEvalMs`/`maxOutputBytes` for representative expression sites. | When configuration is missing or invalid (for example negative limits), the engine MUST fail fast at startup; when evaluations exceed configured limits, engines MUST fail the evaluation with a clear error or apply stricter internal limits, but MUST NOT silently ignore configured bounds. | Telemetry and logs SHOULD surface when evaluations are terminated due to expression limits (for example `expr.limited=true`, `expr.limit=maxEvalMs`). | ADR-0027 |
| FR-012-05 | Cross-engine error taxonomy. | All expression engines classify evaluation errors using stable, engine-specific `code` values; engine features document how each code maps to conceptual categories such as syntax error, runtime error, limit violation, or internal/engine failure so that tooling can derive higher-level groupings from codes alone. | SPI and engine docs specify how errors from each engine implementation are mapped into engine-specific codes; tests assert correct classification for representative failure cases (syntax-like, runtime-like, limit, internal/bug). | Misclassified or uncategorised errors are treated as internal engine errors and MUST be fixed; engines MUST NOT surface raw stack traces or unstructured failures to DSL authors. | Telemetry and Problem Details SHOULD expose `expr.code` (or equivalent) so that expression failures can be distinguished from plugin/connector failures. | ADR-0027, ADR-0003, ADR-0025, ADR-0026 |

## Expression Engine SPI Overview

This section summarises the core Expression Engine SPI types in a language-agnostic way. Concrete Java types live in the runtime once Feature 012 proceeds to implementation.

- `ExpressionEnginePlugin`
  - Identified by an engine id (for example `dataweave`, `jsonata`, `jolt`, `jq`) and a major version.
  - Exposes a single `evaluate` operation that takes:
    - An expression string.
    - An `ExpressionEvaluationContext` (bindings).
    - Optional evaluation options (for example maxEvalMs, maxOutputBytes) derived from configuration.
  - Returns an `ExpressionResult` representing either a value or a Problem-style failure.
  - Must be pure: cannot reach engine connectors or mutate engine/journey state; it can only compute values from its inputs.

- `ExpressionEvaluationContext`
  - Read-only view over data available at the expression site, aligned with the DSL bindings:
    - `context` – current journey context JSON value.
    - `payload` – “current payload” for the site (for example HTTP body, external-input step payload); `null`/absent when not applicable.
    - `error` – low-level error value only in error-mapping sites (for example `spec.errors` mappers); `null`/absent elsewhere.
    - `platform` – read-only JSON object exposing platform metadata and configuration (see ADR‑0022 and DSL reference §10).
  - Integration points:
    - `telemetry` – a handle for attaching attributes and child events for expression evaluation when enabled (Feature 022); engines MAY use this for aggregated metrics/traces but expression engines do not talk directly to exporters.

- `ExpressionResult`
  - Structured outcome of expression evaluation:
    - `ExpressionValue` – wraps a JSON/primitive value to be written into `context` or a variable according to the DSL site rules.
    - `ExpressionProblem` – wraps an RFC 9457 Problem Details instance representing an evaluation failure (syntax error, runtime error, limit violation); evaluation does not change journey context.
  - Expression failures are mapped into journey behaviour according to the DSL site:
    - For predicates, evaluation failures are treated as internal engine errors (as described in the DSL reference).
    - For mappers/transforms, behaviour follows the existing error model and `spec.errors` configuration.

- `ExpressionEngineRegistry`
  - Engine-side registry responsible for resolving `lang: <engineId>` into a concrete `ExpressionEnginePlugin` implementation.
  - Resolution MUST be strict:
    - Unknown `lang` values cause validation or startup failures.
    - Engines MUST NOT fall back to a default engine when a requested `engineId` is unavailable.

## Interface & Contract Catalogue

--- **DSL**
  - `lang: <engineId>` on:
    - `choice.when.predicate`,
    - `transform.expr` / `transform.mapper`,
    - `mapper` blocks under tasks, `wait`/`webhook`/`schedule`,
    - error mappers under `spec.errors` and similar.
  - Engine ids such as `dataweave`, `jsonata`, `jolt`, and `jq` are defined by language-specific features (for example Features 013–016) and ADRs (for example ADR-0002, ADR-0027); this feature only defines the generic plugin and wiring model.
- **Engine SPI (conceptual)**
  - `ExpressionEnginePlugin` – main interface for expression engines (engine id, version, evaluate method) as summarised above.
  - `ExpressionEvaluationContext` – bindings provided to expression engines (`context`, `payload`, `error`, `platform`, plus optional telemetry handle) with availability per expression context defined in the DSL reference.
  - `ExpressionResult` – success/failure wrapper:
    - `ExpressionValue` – successful evaluation with a JSON/primitive value.
    - `ExpressionProblem` – evaluation failure represented as an RFC 9457 Problem Details object; evaluation does not change journey context.
- **Configuration model**
  - Cross-engine configuration tree: `plugins.expressions` with:
    - `defaults.maxEvalMs` / `defaults.maxOutputBytes` – deployment-wide default limits for evaluation time and materialised result size.
    - `engines.<engineId>.maxEvalMs` / `.maxOutputBytes` – optional per-engine overrides.
  - Engine features (for example Features 013–016) MAY introduce additional, engine-specific configuration fields under `plugins.expressions.engines.<engineId>` but MUST honour the semantics of the shared limits.
- **Telemetry**
  - When expression evaluations are instrumented by engine/connector telemetry packs, spans or metrics SHOULD be enriched with:
    - `expr.code` – stable engine-specific error/result code for failed evaluations (for example `DW_PARSE_ERROR`, `JN_PATH_NOT_FOUND`).
    - optional `expr.site` – coarse site label (for example `predicate`, `transform`, `mapper`, `errorMapper`, `statusExpr`).
    - optional `expr.duration_ms`, `expr.input_bytes`, `expr.output_bytes`, `expr.limit_hit`, and `expr.limit_kind` as described in ADR‑0025.
  - Expression telemetry MUST obey the privacy and redaction rules in ADR‑0025 and MUST NOT record expression source text or input/output payload values by default.

## Test Strategy

- Add tests that:
  - Confirm DSL validation catches unknown `lang` values when no matching engine is registered.
  - Exercise expressions via a concrete engine (for example a DataWeave or test engine implementation) using the Expression Engine SPI and compare results with pre-SPI behaviour where applicable.
  - Exercise additional engines when configured (for example JSONata) to ensure they behave as documented.
- Ensure that expression engine errors are mapped into the existing error model (for example Problem Details for runtime errors) and are distinguishable from plugin/engine internal errors.

## Related

- ADR-0002 – Expression language: DataWeave.
- ADR-0025 – Observability and telemetry layers.
- ADR-0026 – Task Plugins Model and Constraints.
- ADR-0027 – Expression Engines and `lang` Extensibility (companion ADR for this feature).
