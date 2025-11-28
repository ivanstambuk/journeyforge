# Feature 011 – Task Plugins & Execution SPI – Plan

Status: Draft | Last updated: 2025-11-26

## Increments

- Increment 1 – SPI design & docs
  - Define Task Plugin SPI and execution context in the spec.
  - Record module boundaries for core vs connector plugins (including HTTP).
  - Align Feature 011 spec with observability Feature 022 (TelemetryHandle in execution context).
- Increment 2 – Core SPI implementation
  - Introduce Task Plugin SPI types in `journeyforge-runtime-core` (no plugin implementations yet).
  - Wire engine execution paths to discover and invoke plugins via the SPI.
- Increment 3 – First‑party core plugins
  - Implement first‑party plugins for `httpCall:v1`, `kafkaPublish:v1`, and `schedule:v1` in their respective modules.
  - Ensure plugin behaviour matches existing DSL semantics and error model.
  - For `httpCall:v1` in `journeyforge-connectors-http`, follow this internal structure (non‑normative, implementation guidance only):
    - `HttpCallTaskPlugin`:
      - Implements the Task Plugin SPI and is invoked for `task.kind: httpCall:v1`.
      - Reads plugin configuration from `TaskExecutionContext.taskConfig()` (operationRef/URL/method/headers/body plus references to resilience/auth/cookie policies).
      - Uses `context`, journey metadata, and `TelemetryHandle` to drive connector calls and observability.
      - Returns `TaskSuccess` with an HTTP result object (status, ok flag, headers, body, optional structured error) written into context, or `TaskProblem` derived via the canonical error model and `errorMapping`.
    - Internal helpers inside `journeyforge-connectors-http` (not visible to `journeyforge-runtime-core`):
      - `HttpRequestBuilder` – maps DSL/model configuration and context into concrete HTTP requests.
      - `HttpClient` – thin abstraction over the chosen HTTP client library.
      - `HttpResilienceEngine` – applies retry/backoff/circuit‑breaker/bulkhead behaviour based on `spec.policies.httpResilience`.
      - `HttpAuthApplier` – interprets `spec.policies.httpClientAuth` and decorates requests with outbound auth (bearer, mTLS, etc.).
      - `HttpCookieJar` – manages per‑run cookies according to `spec.cookies` and task‑level cookie configuration.
      - `HttpObservability` – uses `TelemetryHandle` to attach HTTP‑specific attributes (method, status_code, host, retry count, policy ids) and child events for packs.

## Intent Log (this session)

- 2025-11-26 – HTTP connector plugin design refinement:
  - Reaffirmed boundary: `journeyforge-runtime-core` owns only generic SPIs (TaskPlugin, Telemetry), all HTTP semantics live in `journeyforge-connectors-http` (Option A as captured in ADR‑0031).
  - Chose a single cohesive `HttpCallTaskPlugin` as the public entry point, with internal helpers for HTTP client, resilience, auth, cookies, and observability.
  - Initially added a module‑level design note under `journeyforge-connectors-http/README.md`; later reduced that README to a non‑normative pointer and captured the implementation layout above in this plan instead.

## Analysis Gate – 2025-11-26 (Pre-Implementation)

Summary against `analysis-gate-checklist.md`:
- Specification completeness – PASS
  - Feature 011 spec defines overview, functional requirements (FR-011-01…07), non-functional requirements (NFR-011-01…05), behaviour, and SPI/telemetry integration.
  - Architecturally significant decisions (plugin constraints, module boundaries, observability integration) are captured in ADR‑0026, ADR‑0031, and Feature 022.
- Open questions review – PASS
  - `docs/4-architecture/open-questions.md` contains no open entries for this feature.
  - Past options (core vs connectors) are resolved in ADR‑0031.
- Plan alignment – PASS
  - This plan references the correct spec/tasks files and breaks work into three increments matching the spec’s scope.
  - Increment 1 covers SPI design/docs, Increment 2 covers core SPI implementation, Increment 3 covers first‑party plugins.
- Tasks coverage – PASS
  - Tasks T-011-01…T-011-09 cover all FRs and NFRs at a high level (design, SPI implementation, built‑in plugin migration, regression/performance testing).
  - Tasks emphasise tests and regression checks alongside implementation.
- Constitution compliance – PASS
  - Work respects spec‑first and module-boundary guardrails (no HTTP types in runtime‑core, no reflection, no dependency changes implied).
  - Increments are structured as ≤90‑minute logical units where practical.
- Tooling readiness – PASS
  - Validation command for this feature remains `./gradlew --no-daemon spotlessApply check` (and `qualityGate` once wired); no additional tooling is required at this stage.
