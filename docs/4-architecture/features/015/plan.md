# Feature 015 – JOLT Expression Engine – Plan

## Increments

- Increment 1 – Mapping JOLT to the SPI
  - Define how to represent JOLT specs within `expr`/config.
  - Decide on supported use-cases (e.g. transform-only) and document them.

- Increment 2 – Implementation
  - Implement a JOLT-based expression engine and register it under `lang: jolt`.
  - Add configuration for enabling/disabling JOLT.

- Increment 3 – Documentation and examples
  - Add examples of JOLT usage in `transform` and mappers.
  - Document when to prefer JOLT vs DataWeave/JSONata.
