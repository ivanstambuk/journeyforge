# Feature 022 – Observability Packs & Telemetry SPI

| Field | Value |
|-------|-------|
| Status | Ready |
| Last updated | 2025-12-09 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/022/plan.md` |
| Linked tasks | `docs/4-architecture/features/022/tasks.md` |
| Roadmap entry | #014 |

> Guardrail: This specification is the single normative source of truth for the observability SPI and packs. Track medium- and high-impact questions in `docs/4-architecture/open-questions.md` when they arise, encode resolved answers directly in the Requirements/NFR/Behaviour sections below, and keep ADR‑0025 as the authoritative design record for privacy, layering, and DSL boundaries.

## Overview

ADR‑0025 defines JourneyForge’s observability model:
- A small, always-on core layer of journey lifecycle metrics/traces/logs.
- Optional extension packs for deeper visibility (HTTP client, connectors, CLI, lint, etc.).
- Strict privacy/redaction rules and no telemetry configuration in the DSL.

This feature turns that design into a concrete **Telemetry SPI** and **observability pack model** in the runtime:
- The engine emits **semantic events** for key lifecycle transitions.
- Observability packs subscribe as **sinks** to these events and export to metrics/traces/logs.
- Task plugins and bindings can enrich telemetry via a **TelemetryHandle** in their execution context.

The DSL remains observability-agnostic; `observability.*` configuration in engine/connector/CLI processes controls packs and exporters.

Primary references:
- ADR‑0025 – Observability and Telemetry Layers.
- Observability runbook – `docs/5-operations/observability-telemetry.md`.

## Goals

- Define a small, stable **Telemetry SPI**:
  - `TelemetryEvent` – semantic events emitted by the engine.
  - `TelemetrySink` – subscribers that consume events.
  - `TelemetryHandle` – context passed to engine components for enrichment.
- Implement the **core event stream** in `journeyforge-runtime-core`:
  - Events for journey, state, task, and external-input lifecycle transitions.
  - Core attribute set aligned with ADR‑0023/ADR‑0024 and ADR‑0025.
- Provide built-in **sinks/packs**:
  - Logging sink for JSON logs.
  - OpenTelemetry-style traces sink.
  - Metrics sink (Micrometer/Prometheus-compatible).
- Wire sinks via `observability.*` configuration (no DSL changes).

## Non-Goals

- No journey- or state-level telemetry controls in the DSL (`spec.telemetry` remains out of scope).
- No requirement to ship every pack in this feature; packs beyond the core/logging may be completed in follow-up increments.
- No commitment to specific exporter libraries; the SPI must support OTLP/Prometheus/logging, but concrete dependencies can evolve.

## Telemetry SPI

### 1. Telemetry events

The engine emits a stream of `TelemetryEvent` instances as it executes journeys and APIs. This feature defines **categories** and a minimal attribute set; concrete Java/Kotlin types are defined in runtime-core.

Core event categories:
- `JourneyStarted` – journey or Api run accepted by the engine.
- `JourneyCompleted` – run reached terminal state (`succeed`/`fail`/timeout/internal error).
- `StateEntered` / `StateExited` – entered/exited a state (all types).
- `TaskStarted` / `TaskCompleted` / `TaskFailed` – plugin-backed `task` states.
- `ExternalInputWaiting` / `ExternalInputReceived` – `wait`/`webhook` activation and submission.

Each event includes:
- Identifiers:
  - `journeyId` (for `kind: Journey`) or a run id for `kind: Api`.
  - `journeyName`, `journeyVersion`, `kind`.
  - `stateId` and `stateType` (for state/task/external-input events).
- Outcome and timing:
  - For lifecycle completions: `phase`, `status` (for journeys/APIs), optional `errorCode`.
  - Event timestamp and optional duration (for “Completed” events).
- Telemetry context id:
  - A handle that allows sinks to group related events (for example trace/span ids).

Events **must not** carry payloads or arbitrary `context` values; they expose only structural/categorical metadata as per ADR‑0025.

### 2. TelemetrySink

`TelemetrySink` is the abstraction for observability packs and exporters:

- Methods (conceptual):
  - `onEvent(TelemetryEvent event)` – consume an event.
  - Optional lifecycle hooks (`start()`, `shutdown()`).
- Sinks are registered at engine startup based on `observability.*` configuration:
  - Logging sink – always on (for core logs) or controlled via `observability.core.enabled`.
  - Traces sink – enabled when `observability.exporter.type` is OTEL-compatible.
  - Metrics sink – enabled when metrics collection is configured.
- Sinks are **implementation modules**, for example:
  - `journeyforge-observability-logging`
  - `journeyforge-observability-otel`
  - `journeyforge-observability-metrics`

This feature defines the SPI and core sink responsibilities; concrete modules can be implemented in follow-up increments.

### 3. TelemetryHandle

Engine components (task plugins, bindings, expression engines, etc.) interact with telemetry via a `TelemetryHandle`:

- Provided in execution context:
  - `TaskExecutionContext` (Feature 011) and similar engine contexts include a `TelemetryHandle` for the current journey/API run.
  - Bindings (HTTP, queue, CLI, WebSocket, function) receive a handle for the current request/run and may pass it into connector calls.
- Capabilities (conceptual):
  - Attach attributes to the current activity (for example a span or logical operation), using a stable, kebab-case naming convention for attribute keys (for example `journey.name`, `task.state_id`, `plugin.type`, `http.method`).
  - Emit child events where necessary (for example HTTP client attempts within a task), reusing the same `TelemetryEvent` model where practical.
  - Respect the same privacy rules (no payload bodies, no arbitrary `context` fields; no raw secrets such as tokens, passwords, private keys, or cookie values).

Usage from plugins and bindings:
- Plugins and bindings **do not** talk directly to exporters or sinks.
- They:
  - Annotate the current telemetry context via `TelemetryHandle` (for example set journey/plugin/http attributes on the active span or activity).
  - Optionally emit plugin- or binding-specific child events for richer packs to consume.
- Sinks remain responsible for:
  - Mapping attributes onto logs, traces, and metrics.
  - Enforcing allowlists/redaction per ADR‑0025 and the observability runbook.

### 4. Task plugin telemetry (normative rules)

Task plugins use the Telemetry SPI via `TelemetryHandle` but MUST follow additional constraints so that observability remains consistent and safe:

- Attribute usage:
  - Plugins MAY attach attributes that describe:
    - Journey and state context (for example `journey.name`, `journey.version`, `journey.kind`, `task.state_id`, `plugin.type`, `plugin.major`).
    - Connector-specific metadata that is explicitly non-sensitive (for example HTTP method, status code, target host; Kafka topic name).
  - Plugins MUST NOT attach attributes that include:
    - Payload bodies or arbitrary `context` subtrees.
    - Raw secrets such as tokens, passwords, API keys, private keys, cookie values.
  - All attributes remain subject to `observability.attributes.allowed` and `observability.attributes.redactAlways` configuration.
- Events and spans:
  - Plugins SHOULD emit telemetry that correlates clearly with `TaskStarted`/`TaskCompleted`/`TaskFailed` events (for example spans tagged with `plugin.type` and `task.state_id`).
  - Plugins MUST NOT create spans/events that contradict the core journey lifecycle semantics (for example pretending a task succeeded when it returned a Problem).
- Logging and Problems:
  - Plugin logging levels and detail are configured via observability configuration (for example `observability.plugins.*`) and MUST obey ADR‑0025/ADR‑0026 privacy rules.
  - Caller-visible Problems remain the primary channel for business errors; telemetry MAY include structural error codes (for example plugin-specific `code` values) but MUST NOT embed full Problem documents or sensitive details by default.

## Behaviour

### 1. Event emission points

This feature defines when the engine must emit semantic events:

- Journey/API lifecycle:
  - `JourneyStarted` – when a run is accepted (after validation, before first state).
  - `JourneyCompleted` – when a run reaches a terminal state or fails due to timeout/internal error.
- State lifecycle:
  - `StateEntered` – just before executing a state.
  - `StateExited` – immediately after a state finishes (success or failure).
- Task lifecycle:
  - `TaskStarted` – before invoking a `TaskPlugin`.
  - `TaskCompleted` / `TaskFailed` – after plugin execution, with `task.kind` and error classification when applicable.
- External-input lifecycle:
  - `ExternalInputWaiting` – when a run pauses at a `wait`/`webhook`.
  - `ExternalInputReceived` – when a matching step submission is accepted and applied.

These events form the canonical record that packs and sinks observe; they may be enriched by plugins/bindings using `TelemetryHandle`.

### 2. Packs and sinks

Packs consume events and emit telemetry according to ADR‑0025 and the observability runbook.

Examples:
- **Core logging pack**:
  - Emits JSON logs for `JourneyStarted`/`JourneyCompleted`/`JourneyFailed` and major engine errors.
  - Uses only the core attribute set.
- **HTTP client pack**:
  - Listens for `TaskStarted`/`TaskCompleted` events with `task.kind = httpCall:v1`.
  - Emits spans/metrics with attributes such as `http.method`, `http.status_code`, target host, and resilience policy id.
- **Connector packs**:
  - Kafka – spans/metrics for `kafkaPublish:v1` outcomes (topic, success/failure).
  - Schedules/timers – spans/metrics for schedule bindings and timer firings.
- **CLI pack**:
  - Observes CLI-level events (command start/finish) and maps them to spans/metrics/logs.

All packs:
- Are enabled/disabled via `observability.packs.*` configuration.
- Must obey attribute allowlists and redaction rules from ADR‑0025 and the runbook.

## Configuration

Configuration is described in `docs/5-operations/observability-telemetry.md` and referenced here for completeness:

- `observability.exporter.*` – exporter type and endpoint.
- `observability.sampling.*` – trace/metric sampling and intervals.
- `observability.core.*` – core layer toggle (should remain enabled).
- `observability.packs.*` – pack-specific toggles and attribute include lists.
- `observability.attributes.allowed` / `.redactAlways` – attribute allowlists and redaction.

This feature requires that runtime-core reads these settings and wires TelemetrySinks accordingly.

## Non-Functional Requirements

| ID | Requirement | Driver | Notes |
|----|-------------|--------|-------|
| NFR-022-01 | Event model stability | Operator tooling, dashboards | The core event types and attributes must remain stable across minor versions; additive changes are allowed, breaking changes require a new major. |
| NFR-022-02 | Low overhead at core | Performance | Event emission must be lightweight; packs that add heavy processing must be clearly optional and documented. |
| NFR-022-03 | Privacy guarantees | Security, governance | SPI and packs must obey ADR‑0025 privacy/redaction rules; violations are considered defects. |
