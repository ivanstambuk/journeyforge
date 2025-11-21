# Feature 003 – Plan

Status: Draft | Last updated: 2025-11-21

## Increments
- [ ] T-003-01: Finalise outbound auth DSL shapes in `dsl.md` (httpClientAuth block + task.auth usage).
- [ ] T-003-02: Add at least one reference example journey using outbound auth (OAuth2 client credentials).
- [ ] T-003-03: Extend how-to docs with an outbound auth use case.
- [ ] T-003-04: Implement model/parser changes for httpClientAuth and task.auth.
- [ ] T-003-05: Implement engine stub for outbound auth application and token caching (behind feature flag if needed).
- [ ] T-003-06: Add tests covering DSL validation and basic engine behaviour.

## Risks & Mitigations
- Risk: Overly complex policy model in v1.
  - Mitigation: Start with a small set of `kind` values and minimal required fields; mark others as future extensions.
- Risk: Secret handling leaks into context/logs.
  - Mitigation: Keep `secretRef` as the only DSL surface; enforce logging guidelines and add tests for leakage.

## Validation
- Validate that at least one example journey can call a stubbed backend using OAuth2 client credentials.
- Validate that adding httpClientAuth to a spec that does not use it yet is a no-op for runtime behaviour.
