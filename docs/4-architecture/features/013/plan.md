# Feature 013 – DataWeave Expression Engine – Plan

## Increments

- Increment 1 – SPI implementation
  - Implement `ExpressionEnginePlugin` for DataWeave.
  - Provide an `ExpressionEvaluationContext` adapter that exposes `context`, `payload`, `error`, and `platform`.

- Increment 2 – Wiring and regression
  - Replace any inlined DataWeave evaluators with the plugin.
  - Add regression tests for all `lang: dataweave` expression sites.

- Increment 3 – Telemetry and hardening
  - Ensure expression errors integrate with the Problem Details model.
  - Add basic metrics/traces for DataWeave evaluations (engine id, success/failure, latency buckets).
