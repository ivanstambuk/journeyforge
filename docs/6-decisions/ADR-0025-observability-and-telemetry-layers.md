# ADR-0025 – Observability and Telemetry Layers

Date: 2025-11-24 | Status: Proposed

## Context

JourneyForge is intended to be a spec-first, API-centric platform with strong operational guarantees. As the engine, connectors, and CLI are implemented, they will need:

- Reliable observability for journeys, APIs, and background work (schedules, timers, async jobs).
- Clear lifecycle and business outcome signals aligned with ADR-0023 (uppercase `phase`) and ADR-0024 (top-level `status` and `spec.output.response` projections).
- A consistent contract for operators using OpenTelemetry-style backends (metrics, traces, logs) without leaking PII or internal payloads.

At the same time:

- The DSL (`docs/3-reference/dsl.md`) is intentionally focused on behaviour and contracts, not on telemetry configuration.
- Feature specs already reference “Telemetry & traces” columns and metrics (for example Features 001–004, 008–010), but there is no central definition of:
  - Which signals are always-on vs optional.
  - How redaction and privacy guardrails are enforced.
  - How JourneyForge maps journey/engine concepts onto OpenTelemetry attributes.
- Different deployments may have very different telemetry cost and privacy budgets; some need rich traces, others only basic counters.

We need a coherent telemetry model that:

- Provides a **small, stable core** of automatic observability for all deployments.
- Allows **configurable extension packs** for deeper visibility where needed.
- Keeps **telemetry configuration out of the DSL**, using deployment configuration and conventions instead.
- Defines **strict privacy and redaction rules** at the platform level.

## Decision

We adopt a **layered observability model** with a small always-on core and configurable extension packs, built on a generic OpenTelemetry-style backend. Telemetry is configured at deployment/runtime level, not in the DSL.

### 1. Core telemetry layer (always on, strictly bounded)

The core layer is enabled in all deployments and is intentionally small and stable. It provides:

- **Journey lifecycle metrics**
  - Counters for journeys and APIs:
    - `journey_runs_started_total{journeyName, journeyVersion, kind}`.
    - `journey_runs_completed_total{journeyName, journeyVersion, kind, phase}` where `phase ∈ {SUCCEEDED, FAILED}` per ADR-0023.
  - Latency histograms for end-to-end journey execution where applicable:
    - `journey_run_duration_seconds{journeyName, journeyVersion, kind, phase}`.
- **Journey lifecycle traces**
  - One span per journey/API execution, with a minimal, fixed attribute set, for example:
    - `journey.name`, `journey.version`, `journey.kind` (`Journey` or `Api`).
    - `journey.phase` (final `phase`).
    - `journey.status` (top-level business `status` from ADR-0024).
    - `journey.error_code` (when present).
  - Optional linking to caller correlation ids where available (for example via headers), but correlation configuration is defined outside the DSL.
- **Minimal logs**
  - Single-line structured logs for terminal journey events and major engine errors, carrying only the same bounded set of attributes as the core traces.

The core layer:

- **MUST NOT** include payload bodies, HTTP headers values, or arbitrary `context` fields.
- **MUST NOT** include per-state spans or per-HTTP-call spans; these belong to extension packs.
- **MUST** use stable metric and attribute names documented alongside this ADR so operators can rely on them.

### 2. Extension telemetry packs (config-driven, optional)

Beyond the core, additional observability is provided via **extension packs** that can be enabled or disabled per deployment via engine/connector/CLI configuration. Examples include:

- **HTTP client pack**
  - Per-call spans and metrics for outbound HTTP tasks (`task.kind: httpCall`), including:
    - `http.method`, `http.status_code`, target host, retry count, resilience policy id.
  - Counts and latencies of HTTP calls per journey/task id.
- **Connector packs**
  - Kafka/event publish (ADR-0006): spans/metrics for publish attempts, retries, failures.
  - Schedule/timer execution (ADR-0017, ADR-0018): spans/metrics for schedule creations, firings, and failures.
  - Auth policies (Feature 003): metrics keyed by auth policy id (no secrets).
- **CLI pack**
  - Spans/metrics for `journeyforge` CLI commands: command name, exit status, latency.
- **Lint/analysis pack**
  - If enabled, metrics for linter runs, schema validations, and rule failures (for example from Feature 009).

Properties of extension packs:

- Each pack has a clearly documented **scope**, **cost**, and **attribute vocabulary**.
- Packs are **enabled purely via deployment configuration** (for example `observability.packs.httpClient: enabled`); the DSL and journey specs remain unchanged.
- Packs may emit per-state or per-call spans and richer metrics but **must respect the privacy and redaction rules** defined below.

### 3. Privacy, redaction, and attribute allowlists

The telemetry design itself defines privacy and redaction guardrails:

- **Default-deny for sensitive data**
  - Telemetry exporters and instrumented components **MUST NOT** record:
    - Request or response bodies (including JSON, form data, and binary payloads).
    - Raw HTTP header values, especially `Authorization`, `Cookie`, or similar.
    - Arbitrary values from `context` or external-input payloads.
  - Only **structural and categorical attributes** (names, ids, enums, sizes, durations, counts) are allowed by default.
- **Attribute allowlists**
  - Deployments may configure a small allowlist, for example:
    - `observability.allowedAttributes: ["journey.tags.env", "journey.attributes.tenantId"]`.
  - Only attributes matching configured prefixes/keys may be emitted by extension packs beyond the core set.
  - Allowlist configuration lives in deployment/runtime config, not in the DSL.
- **Automatic redaction**
  - Telemetry emitters **MUST** treat known sensitive keys as redacted even if they match an allowlist (for example anything named `password`, `token`, `secret`, or headers named `authorization`, `cookie`, etc.).
  - Where redaction is applied, values SHOULD be replaced with a fixed placeholder (for example `"***"`), not partially masked real values.

These rules apply equally to metrics, traces, and logs.

### 4. Configuration surface (no DSL hooks)

Telemetry is configured via engine/connector/CLI configuration and CI/build tooling, not via journey specs:

- **Engine/connector configuration**
  - Exporter configuration (endpoints, credentials, protocols).
  - Global sampling (for example traces sampling probability, metrics collection intervals).
  - Enabled extension packs (for example `observability.packs.httpClient = on|off`).
  - Attribute allowlists and explicit redaction overrides where necessary.
- **CLI configuration**
  - Simple flags or environment variables to enable/disable CLI telemetry where present.
- **Tooling/CI integration**
  - Specs and ADRs may refer to telemetry expectations (for example “emit journey outcome metrics”), but no spec introduces a new DSL surface for telemetry.

The DSL (`docs/3-reference/dsl.md`) and journey specs remain observability-agnostic; they only define behaviour and business semantics that telemetry *derives* from (journey names, phases, outcomes, error codes, etc.).

### 5. OpenTelemetry alignment

The platform uses a **generic OpenTelemetry-style model**:

- Metrics, traces, and logs use OTLP-compatible data models and naming where practical.
- Attribute keys follow OpenTelemetry conventions when they overlap (for example `http.method`, `http.status_code`, `exception.type`), and JourneyForge-specific attributes use a `journey.*` or `journeyforge.*` prefix.
- The implementation MAY use different concrete exporters (Prometheus, OTLP/HTTP, OTLP/gRPC, logging-only) without changing the logical contract described in this ADR.

## Consequences

Positive:

- **Predictable baseline observability**: every deployment gets a minimal, stable core of journey lifecycle metrics, traces, and logs without configuration or DSL changes.
- **Clear privacy story**: strong, centralised redaction and allowlist rules reduce the risk of accidental PII leakage into telemetry.
- **Operational flexibility**: extension packs allow operators to dial telemetry depth up or down per environment without touching journey definitions.
- **DSL simplicity**: telemetry concerns remain out of the DSL; journey authors focus on behaviour and contracts, not observability wiring.
- **Alignment with existing decisions**: telemetry attributes naturally reflect `phase` (ADR-0023) and `status` plus final outcome projections (ADR-0024) without requiring additional DSL surface.

Negative / trade-offs:

- **Implementation complexity**: the platform must maintain a clear separation between the core layer and extension packs, and keep the core small and stable over time.
- **Limited journey-level control**: authors cannot make individual journeys “dark” via DSL configuration; observability is controlled by operators at deployment time.
- **Cost tuning is configuration-heavy**: deployments that want very lean telemetry must tune pack enablement, sampling, and metric collection intervals explicitly.

Follow-ups:

- Introduce a concise Observability & Telemetry section in the engine/connector/CLI design docs that:
  - Enumerates core metrics and attributes.
  - Lists available extension packs and their attribute vocabularies.
  - Documents configuration keys for enabling packs and managing allowlists.
- Ensure future feature specs (for example persistence, admin plane) refer to this ADR when defining telemetry expectations instead of inventing new ad-hoc telemetry semantics.

