# Feature 008 – Journey DSL Linter – Plan

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-23 |

## Increments
- [ ] T-008-01: Design linter architecture (schema + Spectral + Java CLI) and align it with the current DSL reference.
- [ ] T-008-02: Introduce initial JSON Schema for `kind: Journey` / `kind: Api` and validate it against existing examples.
- [ ] T-008-03: Document editor/YAML language server integration for `*.journey.yaml` files.
- [ ] T-008-04: Add an optional Spectral ruleset for additional static checks and wire it into a simple CI step.
- [ ] T-008-05: Define the `journeyforge-lint` CLI surface and Gradle integration without requiring a full implementation yet.
- [ ] T-008-06: Implement the Java CLI linter and semantic rules once the DSL stabilises sufficiently.

## Risks & Mitigations
- Risk: DSL surface changes frequently while the feature is being designed.  
  - Mitigation: Focus early increments on schema layout and integration points; defer heavy semantic rules and CLI implementation to a later slice once DSL changes slow down.
- Risk: Divergence between JSON Schema, Spectral rules, and Java CLI semantics.  
  - Mitigation: Treat the CLI (using the parser/model) as the source of truth for deep semantics and keep schemas/rulesets as thin, documented layers aligned via shared examples.
- Risk: Linting adds too much overhead to local development or CI.  
  - Mitigation: Optimise file discovery and parsing, and allow configuration for running only on changed files in pre-commit hooks.

## Validation
- All journey examples in `docs/3-reference/examples/` pass the schema and (once implemented) CLI lint without changes, or any required changes are explicitly documented.
- The Gradle `journeyLint` task runs successfully as part of `qualityGate` on CI.
- At least one supported editor (for example VS Code) demonstrates inline schema diagnostics for `*.journey.yaml` files following the documented configuration.
