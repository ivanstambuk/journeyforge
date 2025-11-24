# Feature 010 – Admin & Dev CLI – Plan

Status: Draft | Last updated: 2025-11-23

## Increments
- [ ] T-010-01: Finalise CLI command surface and configuration model based on Feature 010 spec and Admin/Journeys API specs (start/status/result/list).
- [ ] T-010-02: Scaffold `journeyforge` CLI module (for example `journeyforge-cli` or `app/` extension) and wire basic `--help`/`--version` handling.
- [ ] T-010-03: Implement local `run file` command using the embedded engine and existing journey examples as fixtures.
- [ ] T-010-04: Implement remote `journeys start/status/result` commands against the Journeys API with JSON output.
- [ ] T-010-05: Implement `journeys list` with a small set of filters mapped to Feature 002 query parameters and table/JSON output modes.
- [ ] T-010-06: Introduce experimental lifecycle commands (`history`, `suspend`, `resume`, `terminate`, `purge`) wired to Admin plane/Journeys APIs once those contracts are specified.
- [ ] T-010-07: Add tests and Gradle integration (basic CLI smoke tests, packaging, and cross-platform build checks).
- [ ] T-010-08: Update docs (CLI reference/how-to) and examples to demonstrate local and remote usage.

## Risks & Mitigations
- Risk: CLI surface diverges from evolving Journeys/Admin APIs.  
  - Mitigation: Keep the CLI explicitly non-normative in v1; update Feature 010 spec and CLI behaviour alongside Admin plane/Journeys API changes and avoid adding semantics not present in REST/engine layers.
- Risk: JVM startup time makes CLI feel sluggish.  
  - Mitigation: Start with plain JVM packaging but keep an eye on GraalVM/native-image or similar for future optimisation; bias towards short-lived commands and minimal work in `main`.
- Risk: Configuration complexity (targets, auth) confuses users.  
  - Mitigation: Start with a minimal configuration model (env vars + single config file), document precedence clearly, and add richer helpers only when needed.

## Validation
- `./gradlew --no-daemon spotlessApply check` green with CLI module and tests included in the build.
- Manual smoke tests for `journeyforge --help`, `journeyforge run file`, and `journeyforge journeys start/status/result/list` against a dev deployment.
- Automated tests covering argument parsing, configuration precedence, and representative success/failure cases for local and remote commands.
- Docs under `docs/` (for example a CLI how-to) accurately describe the implemented commands and flags.

## Intent Log
- 2025-11-23: Initial CLI feature spec and roadmap entry added via Codex session, based on Q-003 (scope of CLI façade vs Journeys/Admin REST APIs and UI) with Option A (“single Admin + Dev CLI feature”) selected. 

