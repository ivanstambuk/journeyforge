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

- 2025-11-23: For Q-002 (sync vs async example journeys), adopted Option C (matrix doc + per-example classification), added a start-mode/HTTP-status matrix to `docs/3-reference/examples/README.md`, and migrated the following examples to use synchronous HTTP 200 start semantics aligned with the DSL (relying on the default `startMode: sync`): (a) HTTP-only technical journeys where `/start` now returns `JourneyOutcome` directly – `http-success`, `http-post-json`, `http-204-no-content`, `http-failure-branch`, `http-timeout-branch`, `http-aggregate-errors`, `http-conditional-composition`, `http-content-check`, `http-header-interpolation`, `http-idempotent-create`, `http-resilience-degrade`, `http-custom-error-envelope`, `http-problem-details`, `http-put-delete`, `http-chained-calls`, `cache-user-profile`, `transform-pipeline`, `named-outcomes`, `metadata-from-payload`, `multitenant-routing`, `auth-user-info`, `credit-decision-parallel`, `notification-throttle`, and `recurring-payment`; (b) external-input technical journeys where `/start` now returns `JourneyStatus` at the first external-input state – `sync-wrapper-wait`, `wait-approval`, `wait-multiple-callbacks`, `payment-callback`, and `payment-reminder-with-timeout`; and (c) business journeys where `/start` now returns either `JourneyOutcome` or `JourneyStatus` depending on whether they complete or hit a first wait/webhook step – `business-hours-payment-authorization`, `customer-onboarding-kyc`, `loan-application`, `b2b-purchase-order`, `customer-onboarding-kyc-kyb`, `kyc-async-timeout-fallback`, `high-value-transfer`, and `email-verification`. Updated per-journey OpenAPI, journey docs, Arazzo workflows where relevant, and sequence/activity diagrams so client patterns consistently treat `/start` as HTTP 200 (with `getResult` described as an optional re-fetch where present), and marked the corresponding rows in the examples matrix as migrated.

- 2025-11-24: Continued Q-002 migration for business journeys by updating the remaining examples to the new synchronous `/start` semantics and aligning per-journey OpenAPI, docs, Arazzo, and diagrams: (a) configuration/scheduling journeys – `daily-orders-batch-close` and `monthly-invoicing-batch` now use HTTP 200 with `JourneyOutcome` for the initial configuration run; and (b) external-input/webhook journeys – `insurance-claim-fnol-to-settlement`, `subscription-lifecycle`, `ecommerce-order-split-shipment`, `travel-booking-bundle`, `market-open-order-submission`, and `document-signing-multi-signer` now use HTTP 200 with `JourneyStatus` at their first wait/webhook/timer branches. Updated the examples matrix in `docs/3-reference/examples/README.md` so all migrated business journeys are marked `Migrated? = Yes`.

- 2025-11-24: Resolved Q-004 by adopting Option A for subjourney examples: added a focused technical example journey `subjourney-local-reuse` under `docs/3-reference/examples/technical/` (with matching per-journey OpenAPI and Arazzo specs under both the technical example directory and the central `docs/3-reference/examples/oas` / `docs/3-reference/examples/arazzo` trees), and refactored the `support-case-sla` business journey to wrap its SLA/agent/give-up parallel block in a local `caseLifecycle` subjourney (called via the `runCaseLifecycle` `subjourney` state) with behaviour-preserving propagate-style failure. Updated `support-case-sla.md` implementation notes and the examples matrix in `docs/3-reference/examples/README.md` accordingly.

- 2025-11-24: Documented the `subjourney-local-reuse` example by adding `docs/3-reference/examples/journeys/subjourney-local-reuse.md` (summary, contracts, Arazzo step overview, and implementation notes for `spec.subjourneys` and `type: subjourney` usage) and wiring it into the use-case catalog via a new “Local subjourneys and reuse” row in `docs/2-how-to/use-cases/index.md` that links to the journey definition, per-journey OpenAPI/Arazzo specs, and the new journeys doc.

- 2025-11-24: Resolved Q-005 by adopting Option A for the first true async examples: flipped `kyc-async-timeout-fallback` to `spec.lifecycle.startMode: async` with HTTP 202 + `JourneyStartResponse` semantics at `/journeys/{journeyName}/start` and updated its per-journey OpenAPI, Arazzo workflows, docs, and sequence diagram; added two new technical journeys (`async-fire-and-observe` for pure polling and `async-callback-sla` for callback+SLA races) and one new business journey (`async-report-generation` as the business fire-and-observe counterpart) with matching per-journey OpenAPI and Arazzo specs plus journey docs; and extended the examples matrix in `docs/3-reference/examples/README.md` to classify these journeys as `async/202` while keeping client patterns aligned with ADR-0019 and the DSL lifecycle section.

- 2025-11-24: Adopted ADR-0023 (Journey phase enum uppercase) and ADR-0024 (JourneyOutcome top-level status and projection), updated `docs/3-reference/dsl.md` so `JourneyStatus.phase`/`JourneyOutcome.phase` use `RUNNING`/`SUCCEEDED`/`FAILED`, introduced a required top-level `status` field on `JourneyOutcome` that mirrors `output.status`, and added `spec.output.response` so journeys can project selected business fields into the final outcome envelope; aligned the generic Journeys OpenAPI schema and kept the quality gate green.

- 2025-11-24: Resolved Q-006 by adopting a layered observability model (Option C) and recording ADR-0025 (Observability and telemetry layers), which defines a small, always-on core of journey lifecycle metrics/traces/logs plus optional extension packs for HTTP, connectors, schedules, CLI, and linting; telemetry configuration stays out of the DSL and is driven by deployment config, with strict default-deny privacy and redaction guardrails and an OpenTelemetry-style attribute model.

- 2025-11-24: Added `docs/5-operations/observability-telemetry.md` as an operational guide for configuring the layered telemetry model from ADR-0025, sketching concrete `observability.*` configuration keys and example YAML profiles for engine, connectors, and the `journeyforge` CLI across local, staging, and production environments.
