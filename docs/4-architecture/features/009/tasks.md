# Feature 009 – Journey DSL Linter – Tasks

| ID | Description | Owner | Status |
|----|-------------|-------|--------|
| T-009-01 | Draft the overall linter architecture (external schema/Spectral + internal Java CLI) and capture decisions in this feature spec. | TBD | Todo |
| T-009-02 | Create an initial JSON Schema for JourneyForge specs (`kind: Journey` / `kind: Api`) and validate it against existing `.journey.yaml` examples. | TBD | Todo |
| T-009-03 | Add developer documentation for configuring the YAML language server / editor to use the JSON Schema for `*.journey.yaml`. | TBD | Todo |
| T-009-04 | Add an optional Spectral ruleset for additional static linting and wire it into a CI step (for example via a Gradle or GitHub Actions wrapper). | TBD | Todo |
| T-009-05 | Define and scaffold the `journeyforge-lint` Java CLI module and a Gradle `journeyLint` task that will invoke it. | TBD | Todo |
| T-009-06 | Implement semantic validation rules in the CLI (for example state graph invariants) and ensure they stay aligned with the DSL reference and parser/model. | TBD | Todo |
| T-009-07 | Add automated tests and sample specs for schema validation, Spectral rules (if enabled), and CLI lint behaviour. | TBD | Todo |

