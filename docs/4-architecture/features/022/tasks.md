# Feature 022 – Observability Packs & Telemetry SPI – Tasks

Status: Draft | Last updated: 2025-11-26

## Tasks

- T-022-01 – Finalise Telemetry SPI design
  - Keep `docs/4-architecture/features/022/spec.md` aligned with ADR‑0025 and ADR‑0026 and ensure `TelemetryHandle` usage is consistent with the Feature 011 Task Plugin SPI and bindings specs.
  - Clarify attribute naming conventions and privacy rules (no payload bodies, no secrets) for Telemetry events and attributes.
- T-022-02 – Add Telemetry SPI types and core events
  - Add `TelemetryEvent`, `TelemetrySink`, and `TelemetryHandle` types to `journeyforge-runtime-core` and emit core events.
  - Verify that Task and state lifecycle events carry the identifiers and attributes described in the spec.
- T-022-03 – Implement logging sink
  - Implement a logging sink and wire it via `observability.core.*` configuration.
  - Confirm JSON logs include core identifiers and respect redaction rules.
- T-022-04 – Implement traces and metrics sinks
  - Implement traces sink (OpenTelemetry) and metrics sink (Micrometer/Prometheus) as separate modules.
  - Align metric names, labels, and span attributes with ADR‑0025 and the observability runbook.
- T-022-05 – Pack-specific enrichment
  - Add pack-specific enrichment for HTTP client, connectors, and CLI, controlled via `observability.packs.*`.
  - Ensure HTTP/connector packs use `TelemetryHandle` instead of talking directly to exporters.
