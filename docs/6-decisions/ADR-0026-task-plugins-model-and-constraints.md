# ADR-0026 – Task Plugins Model and Constraints

Date: 2025-11-25 | Status: Proposed

## Context

JourneyForge now expresses all `type: task` states via plugin-backed `task.kind` identifiers of the form `<pluginType>:v<major>` (for example `httpCall:v1`, `kafkaPublish:v1`, `schedule:v1`, `jwtValidate:v1`). The DSL reference and Feature 011 describe this direction, but several cross-cutting aspects of the plugin model remain implicit:

- Execution model for plugins (synchronous vs asynchronous).
- Where side effects and I/O are allowed to happen (engine connectors vs arbitrary libraries).
- How plugins read and write journey `context`.
- How plugins participate in the existing error and HTTP mapping model.
- How plugin configuration is split between DSL specs and engine configuration.

Without a clear decision, plugin implementations risk diverging in behaviour, security posture, and observability, and the engine could accumulate ad-hoc extension points that are hard to reason about or evolve.

## Decision

We adopt a constrained, spec-first plugin model for all `type: task` states that keeps control-flow, envelopes, and I/O ownership in the core engine while allowing modular task behaviours to evolve behind a stable SPI.

### Plugin identification

- All task plugins are identified in the DSL via `task.kind` values of the form `<pluginType>:v<major>`, where:
- `<pluginType>` is a non-empty string identifier (for example `httpCall`, `kafkaPublish`, `schedule`, `jwtValidate`).
  - `<major>` is a positive integer version.
- Core behaviours are expressed as plugins using the same pattern; for example:
  - `httpCall:v1` – outbound HTTP task.
- `kafkaPublish:v1` – Kafka event publish.
  - `schedule:v1` – schedule binding creation/update.
- The engine resolves `task.kind` into a plugin implementation via a registry keyed by `(pluginType, major)`.

### Execution model – synchronous only

- Plugins execute **synchronously** with respect to the enclosing `task` state:
  - A `task` state starts, the engine invokes the plugin once, and the state completes when the plugin returns.
  - Plugins MUST NOT register callbacks, resumptions, or “pending” results that the engine is expected to resume later.
- All long-lived or externally driven behaviour (waiting for input, timers, scheduling future runs) remains in the core DSL:
  - `wait`, `webhook`, and `timer` states.
  - `task.kind: schedule:v1` as defined in ADR-0017.
- Plugins MAY initiate work that continues after the journey completes (for example, an HTTP call that triggers an async process), but any such behaviour:
  - Is modelled as a side effect of the connector, not a resumable plugin; and
  - Does not change the lifecycle of the current journey instance beyond the plugin’s synchronous execution.

### I/O and side effects – connectors only

- Plugins MAY:
  - Read the current journey `context`.
  - Mutate `context` according to the rules below.
  - Call engine-provided connectors (for example HTTP, Kafka, future LDAP/JDBC) via the execution SPI.
- Plugins MUST NOT:
  - Open their own network sockets or HTTP clients.
  - Manage their own connection pools to databases, queues, or external systems.
  - Perform file-system I/O beyond what is explicitly exposed by the engine (for example for local testing).
- All external I/O and side effects go through engine-owned connectors and infrastructure. Connectors are responsible for:
  - Timeouts, retries, and resilience policies (aligned with ADR-0005 and HTTP connector specs).
  - Observability, metrics, and traces (aligned with ADR-0025).
  - Security and privacy constraints such as cookie handling (ADR-0012) and secret management.

### Context model – read-all, explicit targeted writes

- Plugins receive read access to the full journey `context` value for the current run.
- Plugins MUST treat `context` as the authoritative in-memory state for the journey; any business decisions must be derived from it or from request metadata passed via the SPI.
- Plugins MUST only write to `context` via explicit, plugin-defined targets that are visible in the DSL, for example:
  - `resultVar` or similar configuration fields that name a top-level variable to populate.
  - Plugin-scoped subtrees such as `jwt`, `auth`, or `metrics` when the DSL or plugin contract defines them.
- Plugins SHOULD:
  - Prefer plugin-scoped namespaces (for example `context.jwt` or a configured `claimsVar`) to avoid collisions.
  - Avoid wholesale replacement of `context` unless that is the explicit purpose of the plugin and is clearly documented.
- The engine and DSL validators MAY add additional affordances for static analysis (for example, enumerating which variables a plugin intends to write), but this ADR only requires that write targets are explicit in the plugin’s DSL surface.

### Engine scheduling & backpressure (pre-start)

- Before invoking a `TaskPlugin`, the engine MAY apply **transparent backpressure** when required resources (for example HTTP connection slots) are temporarily unavailable.
- Backpressure at this layer:
  - MUST occur **before** calling `TaskPlugin.execute` (the plugin is not running yet).
  - MUST NOT mutate journey `context` or advance control flow.
  - MAY delay the start of the task, subject to global execution limits and cancellation semantics.
- Time spent in engine queues or under backpressure **counts towards** `spec.execution.maxDurationSec`; if the deadline is exceeded before the task can start, the engine MUST treat this as a timeout according to `spec.execution.onTimeout` (for `kind: Journey`) or as an internal timeout Problem (for `kind: Api`).
- When resources become available before the deadline:
  - The engine proceeds to invoke `TaskPlugin.execute` once for that state entry.
  - From the plugin’s perspective, this is a normal invocation; any failures after this point MUST surface via the plugin’s error channel (`TaskResult` / `TaskProblem`) or `TaskPluginException`.
- Engines MUST NOT implement multi‑minute sleep/retry loops **inside** plugins or connectors that appear as a single long-running task execution; long‑lived waits and backoff policies observable at the DSL level MUST be expressed via `timer`/`wait`/`webhook`/`schedule` states and explicit control‑flow patterns.

### Error model – plugin-reported vs internal errors

- Plugin outcomes follow the existing error model (ADR-0003, ADR-0016):
  - A plugin MAY succeed, returning updated `context` according to its contract.
  - A plugin MAY signal a **plugin-reported Problem** by returning a structured RFC 9457 Problem Details document via the SPI. Plugin-reported Problems MUST include a stable, plugin-specific error `code` in Problem extension members so journeys and tooling can distinguish error conditions.
  - Plugins MUST NOT directly set HTTP status codes, headers, or `JourneyOutcome` envelopes.
- The engine is solely responsible for mapping plugin outcomes into:
  - `JourneyOutcome` documents and phases for `kind: Journey` (aligned with ADR-0023, ADR-0024).
  - HTTP response status and body for `kind: Api` (aligned with ADR-0016).
- Unexpected plugin failures (for example uncaught exceptions or configuration bugs) are treated as **internal errors**, not journey-authored failures:
  - The engine maps these to a stable internal Problem type and HTTP 500 for `kind: Api`.
  - Plugins SHOULD wrap predictable failures in their Problem channel instead of throwing.

### Configuration model – DSL-first with profiles

- Plugins take configuration from two sources:
  - The DSL `task` block (per-state behaviour, for example required scopes or where to store results).
  - An engine-level plugin configuration subtree, keyed by plugin type/version and profile name.
- The DSL is the normative source of behaviour:
  - Specs MUST declare semantic decisions such as which plugin to use, which scopes or audiences are expected, or which profile to select.
  - Engine configuration MAY provide environment-specific details (for example issuer URLs, JWK endpoints, clock skew values, connector endpoints) but MUST NOT contradict the core semantics expressed in the DSL.
- When a plugin supports named profiles:
  - The DSL MAY include a profile selector field (for example `profile`, `jwtProfile`, or similar).
  - When the field is omitted, the effective profile name is `"default"`.
  - Engine configuration MAY define multiple profiles; the `"default"` profile MUST be well-defined when the plugin is enabled.

### Security and privacy

- Plugins have access to sensitive data (for example request bodies, headers, journey context, tokens). To uphold the project’s security posture:
  - Plugins MUST NOT log raw secrets (for example access tokens, refresh tokens, passwords, private keys, cookie values).
  - Plugins MUST follow ADR-0012 for cookie handling when they interact with HTTP connectors or cookie jar semantics.
  - Plugins SHOULD minimise copying of sensitive data back into `context`; instead, they SHOULD derive stable identifiers or claims (for example subject ids, scopes, or flags) and store those.
- Engine and connector observability (ADR-0025) MAY expose additional redaction and sampling controls that apply equally to plugin spans and logs.

### Observability and telemetry

- Plugin executions participate in the observability model from ADR-0025:
  - Each plugin execution SHOULD produce a trace span with attributes including:
    - `journey.name`, `journey.version`, `journey.kind`.
    - `task.state_id`.
    - `plugin.type`, `plugin.major`.
    - Outcome (for example `success`, `business_error`, `internal_error`).
  - Metrics MAY be emitted for plugin latency, error rates, and usage counts per plugin type.
- Enabling or disabling plugin-level telemetry is an operational decision and MUST NOT change journey semantics or DSL behaviour.

### Logging and error detail policy for plugins

Logging and error detail for task plugins are configuration-driven and kept out of the DSL. Operators control plugin logging via observability configuration keys, for example:

### Logging and error detail

Engine configuration (not the DSL) controls plugin logging behaviour; for example:

```yaml
observability:
  plugins:
    default:
      logLevel: error                # error | warn | info | debug
    byType:
      jwtValidate:
        logLevel: error
      mtlsValidate:
        logLevel: warn
    byJourney:
      auth-user-info:
        logLevel: info
    byState:
      auth-user-info.validateJwt:
        logLevel: debug
```

Precedence:
- `byState.<journeyName>.<stateId>.logLevel` (highest).
- `byJourney.<journeyName>.logLevel`.
- `byType.<pluginType>.logLevel`.
- `default.logLevel` (fallback).

Semantics:
- Log levels apply uniformly across plugins:
  - `error` (recommended default):
    - Emit logs/spans only for failed executions.
    - Include structural attributes such as plugin type/version, state id, journey name/version/kind, and plugin-specific error code (for example `JWT_SIG_INVALID`, `MTLS_SUBJECT_DENIED`).
  - `warn`:
    - Everything from `error`, plus non-fatal anomalies (for example soft misconfiguration warnings).
  - `info`:
    - Everything from `warn`, plus one log/span annotation for successful executions (for example “jwtValidate succeeded for profile X”); still no payload bodies or secrets.
  - `debug`:
    - Everything from `info`, plus more fine-grained technical diagnostics (for example which constraint failed or which SAN/claim did not match), subject to the privacy rules below.
- Privacy and secrets:
  - Plugins MUST NEVER log raw secrets (for example tokens, passwords, private keys, API keys, cookie values) at any log level.
  - Payload bodies MUST NOT be logged via plugin logging.
  - At higher log levels (especially `debug`), plugins MAY log derived or identifying fields that are not themselves credentials, such as:
    - JWT `kid`, `alg`, and high-level claims like `iss`/`aud` when considered non-sensitive.
    - Certificate subject/issuer DNs, SAN entries, and fingerprints.
  - These rules apply equally to logs and trace attributes; ADR-0025’s redaction and allowlist rules remain in force.
- Problems vs logs/traces:
  - Caller-visible Problems returned by plugins SHOULD remain small and stable:
    - Always include a stable, fine-grained, plugin-specific error code (for example `JWT_SIG_INVALID`, `MTLS_CERT_UNTRUSTED`) and high-level description.
    - MUST NOT embed secrets or full tokens/certificates in `detail` or extensions.
  - Detailed failure diagnostics (for example which specific claim or SAN failed) SHOULD be recorded in logs/traces at higher log levels rather than in Problem bodies.

## Consequences

Positive:
- **Spec-first, predictable behaviour:** All tasks, including core HTTP, event publish, schedule, and JWT validation, follow the same plugin model with clearly defined execution, I/O, context, and error rules. Control-flow and HTTP/JourneyOutcome contracts remain in the DSL and engine, not in plugins.
- **Safe extensibility:** Third-party and future first-party plugins can be added without diluting the engine’s security and observability posture: they must go through connectors, use explicit write targets, and respect the existing error model.
- **Operational clarity:** Engine operators can configure plugin profiles, connectors, and telemetry independently from specs, while specs remain the authoritative description of journey behaviour.
- **Easier evolution:** The engine can evolve envelopes (JourneyOutcome, HTTP mapping), connectors, and telemetry without breaking plugin contracts, because plugins are constrained to context + Problems and cannot manipulate envelopes directly.

Negative / trade-offs:
- **Less expressive plugins:** Plugins cannot implement their own asynchronous lifecycles or arbitrary I/O; they must delegate to connectors and use DSL states for waiting. Some advanced behaviours may require more states or additional connectors instead of a single “smart” plugin.
- **More design work for plugin authors:** Plugin providers must design clear DSL surfaces (result variables, namespaces, profiles) and cannot rely on implicit context mutation or environment-only configuration.
- **Engine responsibility for envelopes:** All HTTP and JourneyOutcome mappings remain centralised in the engine, which must continue to evolve ADR-0003/ADR-0016/ADR-0024 in a consistent way as new plugins appear.

Non-normative note:
- A separate, machine-readable “plugin capabilities” descriptor (for example declaring purity, allowed connectors, or idempotency) is **explicitly out of scope for this version**. Governance for what plugins may do relies on the constraints in this ADR (connectors-only I/O, no hidden waits, explicit write targets, error model) together with normal code review and operational policy, rather than an additional capabilities metadata contract.

Related artefacts:
- **Feature 011** – Task Plugins & Execution SPI – defines the Java SPI and engine integration for plugin execution and will be updated to reference this ADR.
- **DSL reference** – `docs/3-reference/dsl.md` – defines plugin identification, task shapes, and plugin-wide DSL rules for `type: task` states.
- **Existing ADRs** – this ADR is aligned with:
  - ADR-0003 – Error model (RFC 9457 Problem Details).
  - ADR-0005 – HTTP task notify mode.
  - ADR-0012 – HTTP cookies and cookie jar handling.
  - ADR-0016 – API HTTP status mapping for `kind: Api`.
  - ADR-0017 – Scheduled journeys (`task.kind: schedule:v1`).
  - ADR-0018 – Timer state.
  - ADR-0025 – Observability and telemetry layers.
  - Project constitution – spec-first development workflow, DSL/ADR primacy, and small, testable engine core.
  - Root AGENTS guardrails – module boundaries between runtime core, connectors, and CLI; no reflection; no dependency upgrades without owner approval.
