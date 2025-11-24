# Feature 010 – Admin & Dev CLI – Tasks

- [ ] T-010-01: Refine and freeze the initial CLI command groups (`run`, `journeys`) and configuration options in `spec.md`, aligning names/flags with existing REST and engine concepts.
- [ ] T-010-02: Create a new CLI module (for example `journeyforge-cli`) or extend `app/` with a `journeyforge` entry point and shared argument parsing infrastructure.
- [ ] T-010-03: Implement the local `run file` command that loads `.journey.yaml`/`.journey.json` via the parser, executes the journey using the engine, and prints a `JourneyOutcome`-like JSON document.
- [ ] T-010-04: Implement remote `journeys start`/`status`/`result` commands that call the Journeys API using a configurable base URL and auth settings.
- [ ] T-010-05: Implement `journeys list` with filters for journey name, phase, subject/tenant, and tags, mapping flags to the query parameters defined by Feature 002.
- [ ] T-010-06: Add configuration support (env vars and optional config file) for named targets (dev/stage/prod) and document precedence rules.
- [ ] T-010-07: Add unit and integration tests for CLI parsing, config, local runs, and remote calls; wire a basic CLI smoke test into Gradle/CI.
- [ ] T-010-08: Once Admin plane lifecycle endpoints are specified, implement experimental `journeys history`/`suspend`/`resume`/`terminate`/`purge` commands and mark them clearly as such in help text and docs.
- [ ] T-010-09: Document CLI usage in a dedicated how-to or reference section under `docs/`, including examples for both local and remote usage.

