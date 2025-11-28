# ADR-0031 – Runtime Core vs Connector Modules Boundary

Date: 2025-11-26 | Status: Proposed

## Context

JourneyForge needs a clear layering boundary between the runtime engine and protocol- or connector-specific behaviour.

Several existing and planned features touch this boundary:
- Feature 001 – core DSL model and HTTP engine.
- Feature 003 – outbound HTTP auth policies.
- Feature 004 – HTTP cookies and the journey cookie jar.
- Feature 011 – Task Plugins & Execution SPI.
- Feature 019 – Queue/message bindings.
- Feature 020 – CLI/batch binding.
- Feature 022 – Observability Packs & Telemetry SPI.

Related ADRs:
- ADR‑0022 – Platform binding and config.
- ADR‑0025 – Observability and telemetry layers.
- ADR‑0026 – Task plugins model and constraints.
- ADR‑0029 – Inbound bindings and `spec.bindings.http`.

We considered where to place HTTP semantics (client, resilience policies, outbound auth, cookies, HTTP observability) and how strongly to isolate them from the runtime core:

- Option A – **Pure SPI core, HTTP in connectors**
  - `journeyforge-runtime-core` exposes only generic SPIs and engine concerns:
    - Journey execution and state machine.
    - Task Plugin SPI (`TaskPlugin`, `TaskExecutionContext`, `TaskResult`).
    - Telemetry SPI (`TelemetryEvent`, `TelemetrySink`, `TelemetryHandle`).
    - Expression engine and other extension SPIs (per ADR‑0026/0027).
  - `journeyforge-connectors-http` owns all outbound HTTP behaviour:
    - `httpCall:v1` plugin implementation.
    - HTTP client abstraction and concrete client(s).
    - HTTP resilience, auth, and cookie policies and their mapping from the DSL/model.
    - HTTP-specific observability that enriches Telemetry events via `TelemetryHandle`.
  - Other connectors (queue, function, gRPC, etc.) follow the same pattern: they depend on the core SPIs but the core does not depend on them.

- Option B – **Shared generic policy abstractions in core**
  - Core defines generic resilience/auth/cookie SPIs and configuration types used by HTTP and other connectors.
  - HTTP and future connectors share common policy implementations.
  - Risk: core becomes shaped by transport/connector concerns and harder to evolve independently.

- Option C – **HTTP baked into the core**
  - Core embeds a first-party HTTP client and policy stack; connector modules are thin wrappers.
  - Simplifies an initial implementation but tightly couples the engine to a specific HTTP stack and policy model.

## Decision

We adopt **Option A – Pure SPI core, HTTP in connectors**:

- `journeyforge-runtime-core`:
  - Owns journey execution, the state machine, and generic extension SPIs:
    - Task Plugin SPI and execution context.
    - Telemetry SPI.
    - Expression engine and other non-protocol-specific plugins.
  - Has **no dependency on HTTP client libraries** or HTTP-specific policy/cookie/observability types.
  - Discovers and invokes plugins (including HTTP) purely via the generic SPIs.

- `journeyforge-connectors-http`:
  - Depends on `journeyforge-runtime-core` SPIs and the DSL model, but the core does not depend on this module.
  - Owns all outbound HTTP semantics:
    - `httpCall:v1` Task Plugin implementation.
    - HTTP client abstraction and concrete implementation(s).
    - Interpretation of HTTP resilience, outbound-auth, and cookie configuration from the DSL/model.
    - HTTP-level observability that uses `TelemetryHandle` from the core to attach HTTP attributes and spans/metrics via observability packs.

- Other connector-style modules (queue/message, cloud-function, gRPC, CLI helpers, future connectors) must follow the same pattern:
  - Implement their behaviour behind the core SPIs.
  - Keep protocol-specific semantics, client stacks, and observability details out of `journeyforge-runtime-core`.

Feature 011 and Feature 022 specs, along with the `journeyforge-connectors-http` module documentation, treat this ADR as the authoritative description of the core vs connector boundary.

## Consequences

Positive:
- Keeps `journeyforge-runtime-core` focused on journeys, states, and context, with a small, stable set of SPIs.
- Avoids hard coupling to any specific HTTP client, resilience library, or auth stack; HTTP concerns can evolve inside `journeyforge-connectors-http`.
- Makes it explicit that connectors (HTTP, queue, function, gRPC, etc.) are peers that depend on the core, not special cases embedded in the engine.
- Simplifies observability layering: the core emits semantic Telemetry events; connector modules enrich them via `TelemetryHandle` without pulling observability dependencies into the core.

Negative / trade-offs:
- Shared behaviour across connectors (for example resilience or auth patterns reused by HTTP and non-HTTP connectors) may initially be duplicated or live in connector modules until a truly generic abstraction is warranted.
- Some refactors that change connector behaviour (for example adding a new HTTP client or changing retry strategies) will span both DSL/model and connector module code, and must be coordinated across specs and ADRs.

Follow-ups:
- Feature 011 and Feature 022 may reference this ADR when describing the Task Plugin and Telemetry SPIs and their usage by connectors.
- New connector modules and features that introduce protocol-specific behaviour must explicitly confirm that their design respects this boundary, or add a new ADR if they need an exception.

