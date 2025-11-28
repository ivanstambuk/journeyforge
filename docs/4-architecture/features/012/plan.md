# Feature 012 – Expression Engine Plugins – Plan

## Increments

- Increment 1 – SPI definition
  - Define `ExpressionEnginePlugin`, `ExpressionEvaluationContext`, and `ExpressionResult` contracts in the spec.
  - Describe registry and configuration model for expression engines (no Java interfaces in this increment).

- Increment 2 – Wiring into the engine
  - Route all `lang`-based expression sites (choice predicates, transforms, mappers, error mappers) through the Expression Engine SPI.
  - Add integration tests using a concrete engine implementation (for example DataWeave from Feature 013 or a test engine).

- Increment 3 – Telemetry and error mapping
  - Integrate expression engine errors into the existing error model (Problem Details).
  - Add basic metrics/traces for expression evaluation (engine id, success/failure).
