# Feature 001 – Plan

Status: Draft | Last updated: 2025-11-19

## Increments
- [ ] T-001-01: Define model types (Workflow, State variants, HttpCall spec) in `journeyforge-model`.
- [ ] T-001-02: Implement YAML/JSON parser and validation in `journeyforge-parser`.
- [ ] T-001-03: Add pluggable HTTP client (minimal, Java 25 `HttpClient`) in `journeyforge-connectors-http`.
- [ ] T-001-04: Implement synchronous runtime executor in `journeyforge-runtime-core` (no persistence).
- [ ] T-001-05: Provide a temporary runner (in `app/`) that loads a file + optional initial context and prints the outcome.
- [ ] T-001-06: Tests: parser validation cases; runtime happy/failure paths using a stub HTTP client.
- [ ] T-001-07: Docs: example workflow under `docs/3-reference/` and README pointers.

## Risks & Mitigations
- Minimal expression evaluation for `choice` could expand quickly → constrain to `equals` on a `var` dot‑path (documented in spec) and add open question for richer expressions.
- Network in CI would flake tests → use stubbed HTTP client interface injected into runtime.

## Validation
- `./gradlew spotlessApply check` green.
- End‑to‑end: run sample workflow file that performs a GET against a stub and returns `succeed`.

