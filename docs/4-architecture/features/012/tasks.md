# Feature 012 – Expression Engine Plugins – Tasks

- [ ] Define `ExpressionEnginePlugin` SPI and context/result types.
- [ ] Wire all `lang`-based expression sites (choice predicates, transform states, mappers, error mappers) through the SPI.
- [ ] Add tests using a concrete engine implementation (for example DataWeave from Feature 013 or a test engine) to verify integration across all expression sites.
- [ ] Integrate expression engine errors with the Problem Details error model.
- [ ] Add basic metrics/traces for expression evaluations keyed by engine id.
