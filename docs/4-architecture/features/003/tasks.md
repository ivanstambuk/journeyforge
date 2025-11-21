# Feature 003 – Tasks

- [ ] T-003-01: Update `docs/3-reference/dsl.md` with `spec.policies.httpClientAuth` and `task.auth.policyRef` reference sections.
- [ ] T-003-02: Add an example journey (`auth-outbound-client-credentials.journey.yaml`) demonstrating OAuth2 client credentials and token reuse assumptions.
- [ ] T-003-03: Add/extend a how-to doc (`auth-third-party.md` or new) to cover outbound auth configuration patterns.
- [ ] T-003-04: Update model classes to include httpClientAuth policies and task-level auth references.
- [ ] T-003-05: Extend parser to load and validate outbound auth policies from YAML/JSON.
- [ ] T-003-06: Implement engine hooks for applying outbound auth to HTTP tasks; wire in secret store abstraction.
- [ ] T-003-07: Implement token caching behavior for OAuth2 client credentials based on policy id and effective request.
- [ ] T-003-08: Add unit tests for DSL validation, model mapping, and basic engine behaviour (using stubbed HTTP and secret store).
