# Feature 022 – Observability Packs & Telemetry SPI – Plan

Status: Draft | Last updated: 2025-11-25

## Slice 1 – SPI & event model (design + skeleton)
- [x] Define Telemetry SPI and event categories in `docs/4-architecture/features/022/spec.md`.
- [ ] Introduce `TelemetryEvent`, `TelemetrySink`, and `TelemetryHandle` types in `journeyforge-runtime-core` (interfaces only, minimal implementation).
- [ ] Emit core lifecycle events (`JourneyStarted`/`JourneyCompleted`, `StateEntered`/`StateExited`, `TaskStarted`/`TaskCompleted`/`TaskFailed`, `ExternalInputWaiting`/`ExternalInputReceived`) without sinks attached.

## Slice 2 – Logging sink (baseline)
- [ ] Implement a logging sink that writes JSON logs for core events, respecting ADR‑0025 privacy rules.
- [ ] Wire logging sink via `observability.core.enabled` and confirm it works in local runs and tests.

## Slice 3 – Traces sink (OpenTelemetry)
- [ ] Implement a traces sink that maps events onto OpenTelemetry-style spans.
- [ ] Ensure journey runs produce:
  - One top-level span per journey/API.
  - Optional child spans for states/tasks when the relevant packs are enabled.
- [ ] Plug sink into `observability.exporter` configuration.

## Slice 4 – Metrics sink (Micrometer/Prometheus)
- [ ] Implement a metrics sink that updates counters/timers for journey runs and selected packs.
- [ ] Align metric names and labels with ADR‑0025 and the observability runbook.

## Slice 5 – Pack-specific enrichment
- [ ] HTTP client pack: add attributes and spans for `httpCall:v1` events.
- [ ] Connector packs: add attributes and spans/metrics for `kafkaPublish:v1`, `schedule:v1`, timers.
- [ ] CLI pack: add spans/metrics for CLI commands as they interact with the engine.

