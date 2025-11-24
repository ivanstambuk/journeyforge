# Observability & Telemetry – Configuration Guide

This runbook describes how to configure JourneyForge observability and telemetry in engine, connector, and CLI deployments. It implements the layered model defined in `docs/6-decisions/ADR-0025-observability-and-telemetry-layers.md` and assumes a generic OpenTelemetry-style backend (metrics, traces, logs).

> Governance: This document is operational, not normative for the DSL. Behavioural rules for telemetry, privacy, and layering live in ADR-0025; this runbook shows how those rules surface in configuration.

## 1. Engine & connectors – configuration surface

Engine and connector processes share a common `observability` configuration block. A representative YAML shape:

```yaml
observability:
  exporter:
    # Logical telemetry backend (OpenTelemetry-style).
    # Examples: "otlp-http", "otlp-grpc", "logging", "prometheus-only".
    type: otlp-http
    endpoint: "https://otel-gateway.example.com/v1/traces"
    # Optional credentials or headers; values must be treated as secrets.
    auth:
      type: bearerToken
      tokenRef: "otel-gateway-token"   # resolved via platform secret store

  sampling:
    traces:
      # Global trace sampling probability for the journey spans and packs.
      probability: 0.1                 # 10% (0.0–1.0)
    metrics:
      # Collection interval for metrics in seconds.
      intervalSeconds: 15

  core:
    # Core journey lifecycle metrics/traces/logs (ADR-0025 §1).
    enabled: true                      # SHOULD remain true in all environments

  packs:
    httpClient:
      enabled: true                    # Per-call HTTP spans/metrics
      # Optional granular toggles, e.g. only record latency and status:
      attributes:
        include:
          - "http.method"
          - "http.status_code"
          - "http.host"

    connectors:
      kafka:
        enabled: true                  # Event publish spans/metrics
      schedules:
        enabled: true                  # Schedule/timer execution telemetry

    cli:
      enabled: false                   # CLI metrics/spans from engine host, if applicable

  attributes:
    # Attribute allowlist beyond the built-in core set.
    # Only these prefixes/keys may be used by extension packs.
    allowed:
      - "journey.tags.env"
      - "journey.attributes.tenantId"
      - "journey.attributes.correlationId"

    # Keys that MUST always be redacted, even if allowlisted.
    redactAlways:
      - "password"
      - "secret"
      - "token"
      - "authorization"
      - "cookie"
```

Notes:

- The exact configuration mechanism (HOCON, YAML, properties) is implementation-specific; this shape is a target contract for engine/connector configuration.
- Core telemetry (`core.enabled`) SHOULD remain enabled; cost control is primarily via sampling and pack toggles.
- Secret material such as exporter tokens MUST be resolved via the platform’s secret store, not inlined in journey specs or logs.

### 1.1 Engine defaults

Recommended defaults for the engine:

- `observability.core.enabled: true`.
- `observability.packs.httpClient.enabled: true` in non-local environments; MAY be `false` in minimal local setups.
- `observability.packs.connectors.schedules.enabled: true` when scheduled journeys (ADR-0017/0018) are in use.
- `observability.sampling.traces.probability` tuned per environment (for example `1.0` in dev, `0.1` in staging, `0.01` in production).

### 1.2 Connector-specific hints

Connectors MAY define additional pack-specific options under the `packs.connectors.*` subtree, for example:

```yaml
observability:
  packs:
    connectors:
      kafka:
        enabled: true
        attributes:
          include:
            - "messaging.system"       # e.g. "kafka"
            - "messaging.destination"  # topic name (non-sensitive)
      httpAuth:
        enabled: true
        attributes:
          include:
            - "auth.policy.id"        # Feature 003 policy identifiers
```

These hints remain subject to the global `attributes.allowed` and `attributes.redactAlways` rules.

## 2. CLI – configuration surface

The `journeyforge` CLI uses a lighter configuration surface; telemetry is optional and defaults to disabled unless configured:

```yaml
cli:
  observability:
    enabled: false                    # default; opt-in only

    exporter:
      type: otlp-http
      endpoint: "https://otel-gateway.example.com/v1/traces"

    sampling:
      traces:
        probability: 1.0              # CLI commands are short-lived; full sampling is acceptable

    packs:
      usage:
        enabled: true                 # per-command spans/metrics

    attributes:
      allowed:
        - "cli.command"
        - "cli.exit_code"
      redactAlways:
        - "password"
        - "secret"
```

Example invocation flows:

- Local development: CLI telemetry disabled (default), relying only on engine-side observability.
- CI or automated tooling: enable CLI telemetry to understand usage and failure patterns for `journeyforge` commands, still without recording arguments or payloads.

## 3. Environment profiles (examples)

To balance cost and insight, deployments can maintain simple environment profiles.

### 3.1 Local development

```yaml
observability:
  exporter:
    type: logging                     # print telemetry as structured logs
  sampling:
    traces:
      probability: 1.0
  core:
    enabled: true
  packs:
    httpClient:
      enabled: true
    connectors:
      kafka:
        enabled: false
      schedules:
        enabled: false
  attributes:
    allowed: []
    redactAlways:
      - "password"
      - "secret"
      - "token"
      - "authorization"
      - "cookie"
```

### 3.2 Staging / pre-production

```yaml
observability:
  exporter:
    type: otlp-http
    endpoint: "https://otel-gateway.staging.example.com"
  sampling:
    traces:
      probability: 0.25
  core:
    enabled: true
  packs:
    httpClient:
      enabled: true
    connectors:
      kafka:
        enabled: true
      schedules:
        enabled: true
  attributes:
    allowed:
      - "journey.tags.env"
      - "journey.attributes.tenantId"
      - "journey.attributes.correlationId"
    redactAlways:
      - "password"
      - "secret"
      - "token"
      - "authorization"
      - "cookie"
```

### 3.3 Production

```yaml
observability:
  exporter:
    type: otlp-grpc
    endpoint: "https://otel-gateway.prod.example.com"
  sampling:
    traces:
      probability: 0.01               # lower sampling for cost control
  core:
    enabled: true
  packs:
    httpClient:
      enabled: true
    connectors:
      kafka:
        enabled: true
      schedules:
        enabled: true
    cli:
      enabled: false                  # often disabled in production
  attributes:
    allowed:
      - "journey.tags.env"
      - "journey.attributes.tenantId"
      - "journey.attributes.correlationId"
    redactAlways:
      - "password"
      - "secret"
      - "token"
      - "authorization"
      - "cookie"
```

## 4. Implementation notes

- Implementation MUST treat this runbook as a guide; the authoritative behavioural rules are in ADR-0025.
- When adding new telemetry packs or attributes, update ADR-0025 (if the core set changes) and extend this runbook with example configuration keys.
- Tooling and docs SHOULD avoid referencing internal configuration file formats where possible and instead refer to logical keys (for example `observability.core.enabled`, `observability.packs.httpClient.enabled`).

