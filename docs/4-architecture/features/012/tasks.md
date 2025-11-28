# Feature 012 – Expression Engine Plugins – Tasks

Status: Draft | Last updated: 2025-11-26

## Increment 1 – SPI definition (design-only)

- T-012-01 – Finalise Expression Engine SPI contracts
  - Ensure Feature 012 spec describes `ExpressionEnginePlugin`, `ExpressionEvaluationContext`, and `ExpressionResult` in a language-agnostic way aligned with ADR‑0027.
  - Keep this increment docs-only; no Java interfaces are introduced yet.

## Increment 2 – Wiring into the engine

- T-012-02 – Implement Expression Engine SPI types
  - Add `ExpressionEnginePlugin`, `ExpressionEvaluationContext`, `ExpressionResult`, and a registry type to `journeyforge-runtime-core`.
  - Honour the bindings and purity constraints described in the DSL reference and Feature 012 spec.
- T-012-03 – Route all `lang` sites through the SPI
  - Wire all `lang`-based expression sites (choice predicates, transform states, mappers, error mappers) to use the Expression Engine SPI and registry.
  - Add integration tests using a concrete engine implementation (for example DataWeave from Feature 013 or a test engine) to verify integration across all expression sites.

## Increment 3 – Telemetry and error mapping

- T-012-04 – Integrate expression errors with the Problem model
  - Ensure expression evaluation failures produce Problem Details that fit the existing error model (ADR‑0003) and are distinguishable from plugin/connector/internal engine errors.
  - Update specs/tests so predicate failures and mapper failures behave as documented in the DSL reference.
- T-012-05 – Add metrics/traces for expression evaluation
  - Add basic metrics/traces for expression evaluations keyed by engine id and evaluation site (`expr.site`).
  - Align telemetry attributes and limits with ADR‑0025 and the expression limits described in Feature 012.
