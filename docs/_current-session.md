# Current Session Log

- 2025-11-19: Initialized SDD scaffolding and CI baselines for JourneyForge.

- 2025-11-19: Scaffolded Feature 001 spec/plan/tasks.

- 2025-11-19: Decided Option A for spec authoring (YAML authorship + JSON snapshots).

- 2025-11-19: Added DSL reference + style guide; logged initial open questions about branch naming, spec format, and OAS/Arazzo linting.

- 2025-11-19: Wired DSL reference into README, AGENTS, ReadMe.LLM, knowledge map, and llms.txt.

- 2025-11-19: Resolved the branch state naming question – branch state name set to `choice` (canonical).

- 2025-11-19: Removed equals-based branching; DataWeave predicates are mandatory for choice.

- 2025-11-19: Added examples: conditional composition, 204 No Content, and error aggregation.

- 2025-11-19: Added OpenAPI export guideline and sample OAS for http-success and http-chained-calls, plus generic Journeys API.

- 2025-11-22: Resolved the “richer expressions for choice” question – kept `choice` predicates DataWeave-only, clarified internal-error semantics for predicate runtime failures (generic internal error + HTTP 500), and tightened guidance on compile-time validation/tooling; removed the “Richer expressions for choice” future-work bullet from spec-format.md.

- 2025-11-22: Resolved the “DSL doc vs implementation status” question by making `docs/3-reference/dsl.md` strictly normative (semantics only) and pointing readers to Feature 001 docs for implementation status; also resolved the “OAS/Arazzo linting” question by adding Spectral-based OAS and Arazzo linting via `lintOas`/`lintArazzo` Gradle tasks wired into `qualityGate` using a repo-root `.spectral.yaml`.

- 2025-11-22: Adopted Option A for the E-commerce order orchestration (split shipment) journey and added the `ecommerce-order-split-shipment` example, with matching per-journey OpenAPI and Arazzo specs plus Orders, Inventory, and Shipping backend APIs wired into the business journey catalog.

- 2025-11-22: Adopted Option A for the B2B purchase order (multi-level approval) journey and added the `b2b-purchase-order` example, with matching per-journey OpenAPI and Arazzo specs plus a Purchase Orders backend API wired into the business journey catalog.

- 2025-11-22: Adopted Option A for the Insurance Claim – FNOL to settlement journey and added the `insurance-claim-fnol-to-settlement` example, with matching per-journey OpenAPI and Arazzo specs plus Claims and Payments backend APIs wired into the business journey catalog.

- 2025-11-22: Adopted Option A for the Travel Booking – flight + hotel + car bundle journey and added the `travel-booking-bundle` example, with matching per-journey OpenAPI and Arazzo specs plus Flights, Hotels, Cars, and Payments backend APIs wired into the business journey catalog.

- 2025-11-22: Adopted Option A for the Customer Onboarding – individual KYC journey and added the `customer-onboarding-kyc` example, with matching per-journey OpenAPI and Arazzo specs plus KYC, AML, Customers, and Accounts backend APIs wired into the business journey catalog.

- 2025-11-23: For the sync vs async example journeys question, adopted Option C (matrix doc + per-example classification), added a start-mode/HTTP-status matrix to `docs/3-reference/examples/README.md`, and migrated the following examples to use synchronous HTTP 200 start semantics aligned with the DSL (relying on the default `startMode: sync`): (a) HTTP-only technical journeys where `/start` now returns `JourneyOutcome` directly – `http-success`, `http-post-json`, `http-204-no-content`, `http-failure-branch`, `http-timeout-branch`, `http-aggregate-errors`, `http-conditional-composition`, `http-content-check`, `http-header-interpolation`, `http-idempotent-create`, `http-resilience-degrade`, `http-custom-error-envelope`, `http-problem-details`, `http-put-delete`, `http-chained-calls`, `cache-user-profile`, `transform-pipeline`, `named-outcomes`, `metadata-from-payload`, `multitenant-routing`, `auth-user-info`, `credit-decision-parallel`, `notification-throttle`, and `recurring-payment`; (b) external-input technical journeys where `/start` now returns `JourneyStatus` at the first external-input state – `sync-wrapper-wait`, `wait-approval`, `wait-multiple-callbacks`, `payment-callback`, `payment-reminder-with-timeout`; and (c) business journeys where `/start` now returns either `JourneyOutcome` or `JourneyStatus` depending on whether they complete or hit a first wait/webhook step – `business-hours-payment-authorization`, `customer-onboarding-kyc`, `loan-application`, `b2b-purchase-order`, `customer-onboarding-kyc-kyb`, `kyc-async-timeout-fallback`, `high-value-transfer`, and `email-verification`. Updated per-journey OpenAPI, journey docs, Arazzo workflows where relevant, and sequence/activity diagrams so client patterns consistently treat `/start` as HTTP 200 (with `getResult` described as an optional re-fetch where present), and marked the corresponding rows in the examples matrix as migrated.

- 2025-11-24: Continued the sync vs async migration for business journeys by updating the remaining examples to the new synchronous `/start` semantics and aligning per-journey OpenAPI, docs, Arazzo, and diagrams: reused the canonical `JourneyStatus`/`JourneyOutcome` envelopes from `docs/3-reference/openapi/journeys.openapi.yaml` (with uppercase `phase` enums and required top-level `status`), updated all Arazzo workflows to rely on `phase` for lifecycle and top-level `status` for business outcomes (no `output.status` checks), and refreshed spec/style guidance so `JourneyOutcome.status` is the primary business outcome code while `output` remains the full business payload. Kept `qualityGate` green.

- 2025-11-24: Logged the DSL v1 scope question (scoped SLAs/timeouts, per-subjourney compensation, and human-task metadata without becoming a general BPM/Temporal/Conductor-style engine), and created `docs/0-overview/comparison.md` as a high-level “JourneyForge vs Camunda 8 vs Orkes Conductor vs Temporal vs AWS Step Functions” comparison table clarifying which capabilities are core, which are handled via patterns/platform, and which gaps are intentionally out of scope. Linked the comparison doc from `README.md` under Quick Links.

- 2025-11-25: Resolved the plugin extensibility question for `type: task` by adopting a constrained, spec-first Task Plugin model (connectors-only I/O, synchronous execution per state, read-all + explicit targeted context writes, DSL-first configuration with engine profiles and implicit `"default"` profile, and Problems-vs-internal-error separation) and recording ADR-0026 (Task Plugins Model and Constraints). Updated Feature 011 (`docs/4-architecture/features/011/spec.md`) with cross-cutting plugin rules, tightened the Task plugins section in `docs/3-reference/dsl.md`, and removed the corresponding open question row from `docs/4-architecture/open-questions.md`.

- 2025-11-25: Migrated JWT and mTLS validation to plugin-backed tasks by introducing `jwtValidate:v1` and `mtlsValidate:v1` as core task plugins in `docs/3-reference/dsl.md` (with profile-driven defaults, optional DSL overrides, and fine-grained Problem codes), removing `kind: jwt` and `kind: mtls` from inbound `httpSecurity` policies, and updating examples and how-to docs (`auth-user-info`, `subject-step-guard`, `auth-third-party`, `subject-self-service`) to use `jwtValidate:v1`/`mtlsValidate:v1` as first-class auth states instead of policies. Cleaned up references in Feature 002 and ADR-0011 and removed the corresponding open question rows from `docs/4-architecture/open-questions.md`.

- 2025-11-25: Standardised engine-side plugin profiles for `jwtValidate` and `mtlsValidate` by defining conceptual profile shapes under `plugins.<pluginType>.profiles.<name>` in Feature 011 and adding `docs/5-operations/plugin-profiles.md` as an ops runbook with YAML examples. Profiles supply global defaults (issuer/audience/JWKS, mode, anonymousSubjects, trustAnchors, allowAnyFromTrusted, optional filters) while DSL fields on `jwtValidate:v1` / `mtlsValidate:v1` provide per-task overrides.

- 2025-11-25: Extended `docs/0-overview/comparison.md` with a new “Extensibility & plugins” row comparing custom task/node plugins, expression engines, connectors, and UI/modeler extension points across JourneyForge, Camunda 8, Orkes Conductor, Temporal, AWS Step Functions, and Google Cloud Workflows, reflecting the constrained Task Plugin and Expression Engine models adopted in ADR-0026 and ADR-0027.

- 2025-11-25: Fixed YAML structure in `docs/3-reference/examples/business/customer-onboarding-kyc-kyb/customer-onboarding-kyc-kyb.journey.yaml` by correcting indentation for the `kyc` branch task under `runChecksInParallel.parallel.branches`, allowing Prettier/spotless YAML formatting to pass and keeping `qualityGate` green.

- 2025-11-25: Standardised cache behaviour as a Task Plugin by replacing the previous `spec.resources.caches` + `cacheGet`/`cachePut` model with a single `cache:v1` task plugin in `docs/3-reference/dsl.md` (section 15) that supports `operation: get|put`, per-call `ttlSeconds`, and a single logical cache per deployment. Recorded ADR-0028 (Cache Task Plugin and Configuration) to keep provider choice and operational settings in engine configuration, updated cache-related docs and the `cache-user-profile` journey/example to use `cache:v1`, and removed the corresponding open question rows from `docs/4-architecture/open-questions.md`.

- 2025-11-25: Specialised event publishing into a Kafka-specific task plugin `kafkaPublish:v1` with secret-backed connection configuration: updated `docs/3-reference/dsl.md` to replace `eventPublish:v1` with `kafkaPublish:v1` (including `connectionSecretRef`, literal-or-mapper `key`, and fire-and-forget semantics), refreshed ADR-0006 to describe the Kafka publish plugin, aligned Feature 011 and ADR-0026 with the new plugin name, migrated examples/how-to docs (`event-publish-kafka`, `http-compensation`, global compensation, loop patterns) to `kafkaPublish:v1`, and removed the corresponding open question rows from `docs/4-architecture/open-questions.md`.
