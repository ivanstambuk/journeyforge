# Current Session Log

- 2025-11-19: Initialized SDD scaffolding and CI baselines for JourneyForge.

- 2025-11-19: Scaffolded Feature 001 spec/plan/tasks.

- 2025-11-19: Decided Option A for spec authoring (YAML authorship + JSON snapshots).

- 2025-11-19: Added DSL reference + style guide; logged Q-003..Q-005.

- 2025-11-19: Wired DSL reference into README, AGENTS, ReadMe.LLM, knowledge map, and llms.txt.

- 2025-11-19: Resolved Q-003 – branch state name set to `choice` (canonical).

- 2025-11-19: Removed equals-based branching; DataWeave predicates are mandatory for choice.

- 2025-11-19: Added examples: conditional composition, 204 No Content, and error aggregation.

- 2025-11-19: Added OpenAPI export guideline and sample OAS for http-success and http-chained-calls, plus generic Journeys API.

- 2025-11-22: Resolved Q-002 – kept `choice` predicates DataWeave-only, clarified internal-error semantics for predicate runtime failures (generic internal error + HTTP 500), and tightened guidance on compile-time validation/tooling; removed the “Richer expressions for choice” future-work bullet from spec-format.md.

- 2025-11-22: Resolved Q-004 by making `docs/3-reference/dsl.md` strictly normative (semantics only) and pointing readers to Feature 001 docs for implementation status; resolved Q-005 by adding Spectral-based OAS and Arazzo linting via `lintOas`/`lintArazzo` Gradle tasks wired into `qualityGate` using a repo-root `.spectral.yaml`.
