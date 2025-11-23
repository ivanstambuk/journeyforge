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

- 2025-11-22: Resolved Q-013 by adopting Option A for the E-commerce order orchestration (split shipment) journey and adding the `ecommerce-order-split-shipment` example, with matching per-journey OpenAPI and Arazzo specs plus Orders, Inventory, and Shipping backend APIs wired into the business journey catalog.

- 2025-11-22: Resolved Q-014 by adopting Option A for the B2B purchase order (multi-level approval) journey and adding the `b2b-purchase-order` example, with matching per-journey OpenAPI and Arazzo specs plus a Purchase Orders backend API wired into the business journey catalog.

- 2025-11-22: Resolved Q-015 by adopting Option A for the Insurance Claim – FNOL to settlement journey and adding the `insurance-claim-fnol-to-settlement` example, with matching per-journey OpenAPI and Arazzo specs plus Claims and Payments backend APIs wired into the business journey catalog.

- 2025-11-22: Resolved Q-017 by adopting Option A for the Travel Booking – flight + hotel + car bundle journey and adding the `travel-booking-bundle` example, with matching per-journey OpenAPI and Arazzo specs plus Flights, Hotels, Cars, and Payments backend APIs wired into the business journey catalog.

- 2025-11-22: Resolved Q-019 by adopting Option A for the Customer Onboarding – individual KYC journey and adding the `customer-onboarding-kyc` example, with matching per-journey OpenAPI and Arazzo specs plus KYC, AML, Customers, and Accounts backend APIs wired into the business journey catalog.
