# Feature 014 – JSONata Expression Engine – Plan

## Increments

- Increment 1 – Design and SPI mapping
  - Map JSONata concepts (paths, functions) onto the ExpressionEnginePlugin contract.
  - Decide on supported subset and document limitations vs DataWeave.

- Increment 2 – Implementation and wiring
  - Implement the JSONata expression engine plugin.
  - Add configuration to enable/disable JSONata and register it under `lang: jsonata`.

- Increment 3 – Examples and docs
  - Add small DSL examples using `lang: jsonata` in `choice`, `transform`, and mappers.
  - Extend expression docs to highlight JSONata usage and trade-offs.
