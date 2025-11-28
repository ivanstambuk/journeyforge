# journeyforge-connectors-http

This module hosts the HTTP connector and `httpCall:v1` Task Plugin implementation for JourneyForge.

It is **not** a normative design document; the source of truth for behaviour and contracts lives in:
- DSL reference – `docs/3-reference/dsl.md` (HTTP task, policies, cookies).
- Feature specs:
  - Feature 001 – core HTTP engine & DSL.
  - Feature 003 – outbound HTTP auth policies.
  - Feature 004 – HTTP cookies & journey cookie jar.
  - Feature 011 – Task Plugins & Execution SPI.
  - Feature 022 – Observability Packs & Telemetry SPI.
- ADRs:
  - ADR‑0012 – HTTP cookies & cookie jar.
  - ADR‑0025 – Observability and telemetry layers.
  - ADR‑0026 – Task plugins model and constraints.
  - ADR‑0029 – Inbound bindings and `spec.bindings.http`.
  - ADR‑0031 – Runtime core vs connectors module boundary.

Implementation work in this module should follow those specs/ADRs; keep this README as a lightweight pointer only.
