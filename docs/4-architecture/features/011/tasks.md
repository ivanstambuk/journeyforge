# Feature 011 – Task Plugins & Execution SPI – Tasks

Status: Draft | Last updated: 2025-11-26

## Increment 1 – SPI design & docs

- T-011-01 – Finalise SPI and module boundary docs
  - Ensure Feature 011 spec explicitly describes the core/connector split and references HTTP plugins living in `journeyforge-connectors-http`.
  - Cross‑check with Feature 022 (Telemetry SPI) so that `TaskExecutionContext` exposes `TelemetryHandle` consistently.
- T-011-02 – Sketch SPI types (design‑only)
  - In docs (no `.java` yet), outline the expected shapes of:
    - `TaskPlugin`
    - `TaskExecutionContext`
    - `TaskResult` and its main variants
  - Capture these in the Feature 011 spec; add a runtime-core dev note only once Java types are introduced.
- T-011-03 – Design HTTP plugin responsibilities
  - Refine the responsibilities of `HttpCallTaskPlugin` and its internal helpers (HTTP client, resilience, auth, cookies, observability) in documentation only.
  - Keep `journeyforge-runtime-core` free of HTTP‑specific types; all HTTP behaviour remains in `journeyforge-connectors-http`.

## Increment 2 – Core SPI implementation

- T-011-04 – Implement Task Plugin SPI interfaces
  - Add `TaskPlugin`, `TaskExecutionContext`, `TaskResult`, and `TaskPluginException` interfaces/types to `journeyforge-runtime-core` according to the Feature 011 spec and ADR‑0026.
  - Ensure no plugin implementations are introduced in this increment; focus on SPI and registry only.
- T-011-05 – Implement plugin registry and resolution
  - Implement a `TaskPluginRegistry` keyed by `(pluginType, major)` with strict resolution semantics (no silent fallbacks or downgrade).
  - Add validation paths so that journeys referencing unknown `task.kind` values fail fast at spec load or engine startup.
- T-011-06 – Wire engine execution to SPI
  - Route all `type: task` state execution through the Task Plugin SPI and registry.
  - Add engine-level tests that exercise plugin invocation, context propagation, and error handling (success, plugin-reported Problem, unexpected exception).

## Increment 3 – First‑party core plugins

- T-011-07 – Rebuild `httpCall:v1` on Task Plugin SPI
  - Implement `HttpCallTaskPlugin` in `journeyforge-connectors-http` following the structure described in the Feature 011 plan and existing HTTP specs (Features 001, 003, 004).
  - Ensure behaviour (result object shape, error mapping, notify vs request/response) matches the DSL reference and existing examples.
- T-011-08 – Rebuild `kafkaPublish:v1` and `schedule:v1` on Task Plugin SPI
  - Implement first‑party plugins for Kafka publish and schedule binding creation/update in their respective modules.
  - Confirm semantics align with Features 001, 006, 017 and the DSL reference.
- T-011-09 – Backwards-compatibility and performance tests
  - Add regression tests to compare pre‑ and post‑Feature 011 behaviour for journeys/APIs using only built-in tasks.
  - Add lightweight performance checks to confirm plugin resolution overhead is negligible relative to task work (HTTP, Kafka, schedule).
