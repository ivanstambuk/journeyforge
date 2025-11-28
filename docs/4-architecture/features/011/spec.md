# Feature 011 – Task Plugins & Execution SPI

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/011/plan.md` |
| Linked tasks | `docs/4-architecture/features/011/tasks.md` |
| Roadmap entry | #003 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a modular plugin model for JourneyForge task execution so that:
- All task behaviours – including core HTTP calls, Kafka publishes, and schedule bindings – are expressed in the DSL as plugin-backed tasks (`task.kind: <pluginType>:v<major>`) and are implemented through a single, versioned Task Plugin SPI; and
- The DSL surface no longer distinguishes between “built-in” and “custom” task kinds: `httpCall:v1`, `kafkaPublish:v1`, `schedule:v1`, and `jwtValidate:v1` are all just plugins with different `pluginType` and `major` values.

This feature keeps the graph and long‑lived primitives (`choice`, `parallel`, `wait`, `webhook`, `timer`, `subjourney`, `succeed`, `fail`) in the core engine while delegating side-effectful work and value transformations to plugins, paving the way for future Transform and Expression Engine plugins (Option B in earlier discussion).

## Goals
- Define the **Task Plugin execution model** and Java SPI used by the runtime engine to execute all `type: task` states.
- Extend the DSL so that **all tasks, including core behaviours**, are plugin-backed:
  - `task.kind` always uses plugin identifiers of the form `<pluginType>:v<major>`, including `httpCall:v1`, `kafkaPublish:v1`, and `schedule:v1`.
  - All non‑`kind` fields under `task` are plugin-defined configuration fields (except where this spec normatively defines core plugin contracts).
  - Task plugins are allowed in both `kind: Journey` and `kind: Api`.
- Ensure plugins receive **sufficient runtime context** to implement behaviour correctly and safely:
  - Journey context, journey metadata (name/version/kind), journey instance identity, state id, and (when applicable) HTTP request context.
- Align plugin failures with the existing **error model** (ADR‑0003) so that:
  - Plugins can surface RFC 9457 Problem Details, and
  - Engines treat plugin crashes as internal errors rather than journey-authored failures.
- Rebuild engine support for built-in `task.kind` values (`httpCall`, `kafkaPublish`, `schedule`) on top of the Task Plugin SPI, without changing existing DSL semantics.
- Document how this feature relates to future **TransformPlugin** and **ExpressionEnginePlugin** extension points (Option B), without committing to their implementation in this increment.

## Non-Goals
- No new DSL state types beyond plugin-backed `task.kind` values; `choice`, `parallel`, `wait`, `webhook`, `timer`, `subjourney`, `succeed`, and `fail` remain fixed.
- No general plugin model for persistence, timers, or storage backends; those concerns stay internal to the engine and connectors.
- No cross-language plugin SDKs in this increment; the execution SPI is Java-only, and out-of-process/worker style integrations remain out of scope.
- No configuration UI; plugin configuration is managed via engine configuration files, environment, and the existing DSL only.
- No changes to OpenAPI/Arazzo exports or the Journeys API contracts beyond what is already defined in the DSL reference and existing features.

## Module Placement and HTTP Plugins

This feature defines the Task Plugin execution model for the runtime engine; concrete plugin implementations live in their respective modules so that the core remains focused on journeys, states, and context (see ADR‑0031).

- `journeyforge-runtime-core`:
  - Owns the core journey engine, state machine, and the Task Plugin SPI (`TaskPlugin`, `TaskExecutionContext`, `TaskResult`).
  - Integrates with the Telemetry SPI (`TelemetryEvent`, `TelemetrySink`, `TelemetryHandle`) from Feature 022.
  - Does not depend on HTTP client libraries, HTTP-specific policy types, or cookie/observability helpers.
- Connector and feature modules:
  - Provide concrete plugin implementations keyed by `task.kind: <pluginType>:v<major>`.
  - Interpret DSL/model configuration relevant to their domain.
  - For HTTP:
    - `journeyforge-connectors-http` owns `httpCall:v1` and all outbound HTTP semantics:
      - Request construction from DSL/model.
      - HTTP resilience, auth, and cookie policies.
      - HTTP-level observability built on top of `TelemetryHandle`.
    - The engine discovers and invokes the HTTP plugin via the generic Task Plugin SPI; no HTTP-specific logic is embedded in `journeyforge-runtime-core`.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-011-01 | Represent all tasks as plugins in the DSL. | DSL requires `task.kind` values of the form `<pluginType>:v<major>` for all tasks, including core behaviours (`httpCall:v1`, `kafkaPublish:v1`, `schedule:v1`); fields under `task` other than `kind` are treated as plugin-defined config. | DSL validators accept plugin-shaped `task.kind` when `<pluginType>` is non-empty and `<major>` is a positive integer, and reject malformed identifiers; old forms such as `kind: httpCall` or `kind: eventPublish` are rejected. | Specs using malformed plugin ids or plugin kinds in the old format are rejected at validation/load time with clear error messages. | Spec-parsing logs and metrics can record counts of tasks per `<pluginType>` for observability. | DSL ref §5 (task); ADR‑0026 |
| FR-011-02 | Define Task Plugin SPI and registration. | Engine exposes a Java SPI (for example `TaskPlugin`) that all task behaviours implement; plugins are discovered via a registry (for example ServiceLoader plus explicit configuration) keyed by `(pluginType, major)`. | Unit tests and documentation demonstrate registration of both built-in and custom Task Plugins; configuration errors (missing plugin for a used `<pluginType>:v<major>`) are caught at startup. | If a journey refers to `task.kind: foo:v1` and no corresponding plugin is registered, engine startup fails fast or the spec load for that journey is rejected; runtime MUST NOT silently ignore or downgrade such tasks. | Engine logs plugin registration and resolution at debug level; optional metrics track plugin load failures. | Feature 001 engine overview; ADR‑0026 |
| FR-011-03 | Provide rich execution context to Task Plugins. | Task Plugin SPI exposes a `TaskExecutionContext` (or equivalent) that includes: current `context` JSON, journey metadata (`kind`, `metadata.name`, `metadata.version`), journey instance id (when available), logical state id, and incoming HTTP request context for `kind: Api` when available. | SPI documentation and unit tests assert that the context object includes the required fields; engine implementation passes through the correct values during task execution. | If required context is missing at runtime (for example journey id when the platform guarantees it), engine treats this as an internal error and fails the run with an internal Problem Details type. | Telemetry can attach journey and plugin identifiers as attributes on spans for task execution. | DSL ref §5 (bindings available to plugin implementations); ADR‑0003; ADR‑0026 |
| FR-011-04 | Align Task Plugin outcomes with the error model. | Task Plugins can return a structured result that either: (a) produces a normal context update, or (b) yields a Problem Details object representing an error; engine maps this to the canonical `JourneyOutcome`/`spec.errors` behaviour without changing the DSL. | Tests cover plugins that: succeed, produce plugin-reported Problems (Problem Details), and crash; resulting `JourneyOutcome` and HTTP responses match the rules from ADR‑0003 and ADR‑0016. | Unhandled exceptions or contract violations in Task Plugin code are treated as internal engine errors (distinct Problem Details type, HTTP 500 for `kind: Api`), not journey-authored failures. | Error metrics and logs distinguish between plugin-reported Problems and plugin runtime failures. | ADR‑0003, ADR‑0016; DSL error model §5.7 |
| FR-011-05 | Execute core behaviours via Task Plugins. | Core behaviours `httpCall:v1`, `kafkaPublish:v1`, and `schedule:v1` are implemented as first-party Task Plugins that the engine registers under reserved keys, preserving all semantics documented in Feature 001 and related specs. | Regression tests compare pre- and post-feature execution for journeys using only these core plugins to ensure identical outcomes and error handling. | Any behaviour drift for these core plugins (for example different error object shapes or scheduling semantics) is treated as a regression and must be fixed before this feature is considered done. | Telemetry continues to attribute HTTP calls, Kafka publishes, and schedules using existing names; optional plugin type/version labels may be added. | Feature 001 spec; DSL ref §5.1–5.3 |
| FR-011-06 | Allow plugin-backed tasks in both `kind: Journey` and `kind: Api`. | Engine executes plugin-backed tasks identically in both journey and API contexts; plugins see journey metadata and, for APIs, the HTTP request context, and can mutate `context` used later in the run. | Tests include journeys and APIs that use the same plugin-backed task and assert consistent behaviour from the plugin’s perspective; validation rules do not restrict plugin-backed tasks by `kind`. | If a plugin depends on context that is not available in a given `kind` (for example HTTP headers in a long-running journey resumed from storage), the plugin must fail with a clear Problem Details error; the engine must not silently ignore such issues. | Telemetry may distinguish plugin executions by `kind` (journey vs API) for analysis. | DSL ref §5 (task); Feature 001 spec |
| FR-011-07 | Prepare for Transform and Expression Engine plugins. | The Task Plugin SPI is defined in a way that future `TransformPlugin` and `ExpressionEnginePlugin` SPIs can share common context and error-handling patterns; the spec briefly documents this planned alignment but does not require implementation in this increment. | Design review for the SPI shows that context and metadata types are reusable for other plugin categories; no DSL changes are required to add Transform/Expression plugins later. | If later features discover that the Task Plugin SPI is too narrow for Transform/Expression plugins, they may extend it but should not break existing Task Plugins; this is recorded as a future open question if needed. | None required in this increment beyond design documentation. | ADR‑0027; roadmap |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-011-01 | Preserve JVM safety and stability when loading third-party plugins. | Runtime robustness. | Engine validates plugin classes on load and isolates failures to plugin boundaries; a faulty plugin cannot bring down the entire engine during journey execution beyond the current run. | Java module/classloader setup; plugin discovery mechanism. | ADR‑0026; project constitution |
| NFR-011-02 | Keep the core engine small and testable. | Maintainability. | Core engine modules have no hard compile-time dependency on specific plugin implementations beyond the SPI; built-in task plugins live in dedicated modules or packages. | Module boundaries in `journeyforge-runtime-core` and connectors. | Feature 001, AGENTS.md |
| NFR-011-03 | Clear, opinionated plugin naming and versioning. | Operational clarity. | All plugin-backed `task.kind` values use `<pluginType>:v<major>` with documented conventions for `<pluginType>`; engine logs and error messages reference these identifiers clearly. | DSL validators and engine logging. | DSL ref §5 (custom task plugins) |
| NFR-011-04 | Backwards compatibility for existing specs. | Adoption. | Rebuilding built-in tasks on top of the Task Plugin SPI introduces no breaking changes to existing journeys or APIs; all existing DSL examples and tests continue to pass unchanged. | Migration tests comparing before/after behaviour. | Feature 001 spec; DSL ref |
| NFR-011-05 | Minimal overhead for plugin resolution. | Performance. | Plugin lookup by `(pluginType, major)` and execution add negligible overhead compared to the work performed by typical tasks (HTTP, Kafka, etc.); micro-benchmarks confirm acceptable latency. | Plugin registry implementation. | Performance goals (to be refined later) |

## UI / Interaction Mock-ups
```yaml
# Example – JWT validation plugin used in an API
apiVersion: v1
kind: Api
metadata:
  name: get-account
  version: 0.1.0
spec:
  start: validateJwt
  states:
    validateJwt:
      type: task
      task:
        kind: jwtValidate:v1
        source: header:Authorization
        requiredScopes:
          - accounts:read
      next: fetchAccount

    fetchAccount:
      type: task
      task:
        kind: httpCall:v1
        operationRef: accounts.getAccountById
        params:
          path:
            accountId: "${context.accountId}"
        resultVar: accountResponse
      next: mapResponse

    mapResponse:
      type: transform
      transform:
        lang: dataweave
        expr: |
          {
            output: context.accountResponse.body,
            subject: context.jwt.subject
          }
      next: done

    done:
      type: succeed
      outputVar: output
```

## Task Plugin SPI Overview

This section summarises the core SPI types in a language-agnostic way.

- `TaskPlugin`
  - Identified by `(pluginType, major)` and bound to `task.kind: <pluginType>:v<major>` in the DSL.
  - Exposes a single `execute` operation that takes a `TaskExecutionContext` and returns a `TaskResult` or signals a `TaskPluginException`.
  - Must be pure with respect to control-flow: no resumable lifecycles; long-lived waiting is modelled via DSL states (`wait`, `webhook`, `timer`, `schedule`).
- `TaskExecutionContext`
  - Read-only view over the current journey state:
    - `context` – current JSON context object.
    - `taskConfig` – plugin configuration (all fields under `task` except `kind`).
    - `journey` – metadata for the definition (name, version, kind).
    - `instanceId` – journey instance identifier when available.
    - `stateId` – DSL state identifier.
    - `httpRequest` – HTTP request metadata for `kind: Api` when present.
  - Integration points:
    - `telemetry` – handle for attaching attributes and child events to the current telemetry activity (Feature 022).
    - `platform` – structured access to platform/configuration views aligned with ADR‑0022.
- `TaskResult`
  - Structured outcome of plugin execution:
    - `TaskSuccess` – carries an updated `context` value to be persisted by the engine.
    - `TaskProblem` – wraps an RFC 9457 Problem Details instance representing a plugin-reported failure; the engine leaves `context` unchanged and maps the Problem into envelopes (`JourneyOutcome`, HTTP status) per ADR‑0003/ADR‑0016/ADR‑0024.
- `TaskPluginException`
  - Signals unexpected plugin failures (bugs, misconfiguration) distinct from normal, journey-visible Problems.
  - The engine converts these into internal errors with stable Problem types and HTTP 5xx semantics without leaking sensitive details.
- `TaskPluginRegistry`
  - Engine-side registry responsible for resolving `(pluginType, major)` into a concrete `TaskPlugin` implementation.
  - May be backed by static configuration, ServiceLoader-style discovery, or explicit wiring, but resolution behaviour (no silent fallbacks, no best-effort downgraded versions) is governed by the functional requirements in this spec.

## Cross-cutting Task Plugin Rules

This feature adopts the constrained plugin model defined in ADR-0026 (Task Plugins Model and Constraints). The **DSL reference** (`docs/3-reference/dsl.md`) remains the normative source for language-level behaviour of `type: task` states; this section summarises the corresponding SPI and engine obligations so that implementations stay aligned with the DSL and ADRs.

- **Plugin identification**
  - Plugins are identified by `task.kind: <pluginType>:v<major>`; the registry is keyed by `(pluginType, major)`.
  - Core behaviours (`httpCall:v1`, `kafkaPublish:v1`, `schedule:v1`) are first-party plugins that follow the same rules.
- **Execution model (synchronous)**
  - `TaskPlugin.execute` is synchronous: the engine invokes the plugin once per `task` state and expects a `TaskResult` or `TaskPluginException`.
  - Plugins MUST NOT implement their own resumable lifecycles; asynchronous waiting is modelled via `wait`, `webhook`, `timer`, or `schedule` states in the DSL.
- **I/O and connectors**
  - Plugins MAY call engine/connector services exposed via `TaskExecutionContext` or auxiliary engine services.
  - Plugins MUST NOT open arbitrary sockets, manage their own HTTP clients, databases, or filesystem I/O; all external I/O goes through engine-owned connectors and infrastructure.
- **Context access and mutation**
  - `TaskExecutionContext.context()` exposes the full current journey context for read operations.
  - Plugins MUST treat context as immutable input and only mutate it via explicit, plugin-defined write targets (for example `resultVar`, `claimsVar`, or plugin-scoped subtrees).
  - Plugins SHOULD prefer plugin-scoped namespaces to avoid collisions with other states.
- **Errors and envelopes**
  - `TaskResult` is a sealed hierarchy:
    - `TaskSuccess` – successful outcome with updated context.
    - `TaskProblem` – plugin-reported failure represented as an RFC 9457 Problem Details document; the engine MUST leave journey context unchanged on this branch.
  - Plugins MUST NOT set HTTP status codes, headers, or `JourneyOutcome` fields directly; the engine remains responsible for envelope mapping (see ADR-0003, ADR-0016, ADR-0024).
  - Unexpected failures are signalled via `TaskPluginException` (or unchecked exceptions), which the engine converts into internal errors with stable Problem types.
- **Configuration and profiles**
  - Plugin behaviour is configured primarily via the DSL `task` block; engine configuration supplies environment-specific details and profiles.
  - When a plugin supports profiles, it MAY expose a profile selector field in `taskConfig`; if omitted, the effective profile is `"default"`, resolved from engine configuration.
- **Security, privacy, and observability**
  - Plugins MUST treat data from `context()` and `httpRequest()` as potentially sensitive and MUST NOT log raw secrets such as tokens, passwords, or cookie values.
  - Plugins and connectors participate in the layered observability model from ADR-0025; plugin executions SHOULD emit spans enriched with journey and plugin attributes and MAY publish metrics for latency and error rates.

These rules constrain the SPI so that future plugin types and implementations (including first-party and third-party plugins) remain compatible with the spec-first DSL, error model, and observability design.

## Plugin Profiles (Engine Configuration)

Task plugins use engine-side profiles to provide global defaults for their behaviour, with per-task overrides in the DSL where needed. Profiles are part of engine configuration, not the DSL surface; this section describes their conceptual shapes and how they interact with DSL fields.

### JWT validation profiles (`jwtValidate`)

Conceptual configuration shape:

```yaml
plugins:
  jwtValidate:
    profiles:
      default:
        issuer: "https://issuer.example.com"
        audience: ["journeyforge"]
        jwks:
          url: "https://issuer.example.com/.well-known/jwks.json"
          cacheTtlSeconds: 3600
        clockSkewSeconds: 60

        # Optional validation behaviour
        mode: required                  # required | optional
        anonymousSubjects:
          - "00000000-0000-0000-0000-000000000000"

        # Optional default token source
        source:
          location: header              # header | query | cookie
          name: Authorization
          scheme: Bearer

        # Optional default claim constraints (shape mirrors DSL overrides)
        requiredClaims: { ... }
```

Rules:
- Profiles are keyed by arbitrary names (for example `default`, `admin`, `public`).
- The `profile` field on `jwtValidate:v1` in DSL selects a profile; when omitted, the effective profile MUST be `"default"`.
- For each JWT-related field (for example `issuer`, `audience`, `jwks`, `clockSkewSeconds`, `mode`, `anonymousSubjects`, `source`, `requiredClaims`):
  - If the field is present in the DSL task, its value overrides the profile for that task.
  - Otherwise, the engine uses the profile value when present.
  - If neither DSL nor profile provides a required field (for example no usable key source), this is a configuration error and MUST surface as an internal Problem, not a journey-authored failure.
- Field names and semantics in profiles SHOULD mirror DSL overrides so the mapping is obvious.

### mTLS validation profiles (`mtlsValidate`)

Conceptual configuration shape:

```yaml
plugins:
  mtlsValidate:
    profiles:
      default:
        trustAnchors:
          - pem: |
              -----BEGIN CERTIFICATE-----
              ...
              -----END CERTIFICATE-----
          - pem: |
              -----BEGIN CERTIFICATE-----
              ...
              -----END CERTIFICATE-----

        allowAnyFromTrusted: true       # default behaviour when no filters

        # Optional default filters
        allowSubjects:
          - "CN=journey-client,OU=Journeys,O=Example Corp,L=Zagreb,C=HR"
        allowSans:
          - dns: api.example.com
        allowSerials:
          - "01AB..."
```

Rules:
- Profiles are keyed by arbitrary names under `plugins.mtlsValidate.profiles`.
- The `profile` field on `mtlsValidate:v1` selects a profile; when omitted, the effective profile MUST be `"default"`.
- Inline `trustAnchors.pem` entries provide root/sub-CA certificates; engines MAY support additional configuration forms in future but MUST at least support this shape.
- For each mTLS-related field (`trustAnchors`, `allowAnyFromTrusted`, `allowSubjects`, `allowSans`, `allowSerials`):
  - If the field is present in the DSL task, its value overrides the profile for that task.
  - Otherwise, the engine uses the profile value when present.
  - When neither profile nor DSL provides filters, the effective behaviour is determined solely by `allowAnyFromTrusted`:
    - If `true`, any certificate that chains to a trusted anchor is accepted.
    - If `false`, certificates that chain correctly but do not match any filters are rejected.
- Implementations MAY support additional fields per profile but MUST NOT change the semantics of the standard fields.

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-011-01 | Journey with only built-in tasks behaves identically before and after Task Plugin SPI introduction. |
| S-011-02 | Journey with `task.kind: jwtValidate:v1` and a registered JWT plugin executes successfully, mutating `context` and allowing subsequent states to branch on JWT claims. |
| S-011-03 | API with `task.kind: jwtValidate:v1` and invalid/missing token triggers a Problem Details error surfaced consistently with ADR‑0003/ADR‑0016. |
| S-011-04 | Spec referencing `task.kind: foo:v1` fails validation or load when no `foo:v1` plugin is registered. |
| S-011-05 | Plugin throws an unexpected exception; engine converts this into an internal error (distinct Problem type, HTTP 500) without corrupting engine state. |
| S-011-06 | Future Transform/Expression plugins can reuse the same context and error-handling patterns without changing DSL surface. |

## Test Strategy
- **DSL validation tests**
  - Add cases for valid and invalid plugin-shaped `task.kind` values (empty `pluginType`, non-integer/zero/negative `major`, missing `v` prefix).
  - Ensure specs using plugin-backed tasks fail validation when the engine is configured without a corresponding plugin runtime.
- **Engine unit tests**
  - Introduce test-only Task Plugins that:
    - Echo input context to output with modifications.
    - Return Problem Details objects to simulate plugin-reported Problems.
    - Throw exceptions to exercise internal error handling.
  - Verify that the engine:
    - Resolves plugins correctly by `(pluginType, major)`.
    - Passes the expected context, metadata, instance id, state id, and HTTP request information into the plugin.
    - Maps plugin outcomes into `JourneyOutcome`/HTTP responses according to ADR‑0003 and ADR‑0016.
- **Regression tests for built-in tasks**
  - Before introducing the Task Plugin SPI, capture expected behaviours for `httpCall`, `kafkaPublish`, and `schedule` via integration tests; re-run them after refactoring onto plugins to ensure behaviour is unchanged.
- **End-to-end scenarios**
  - Add example journeys/APIs that use a sample `jwtValidate` plugin and assert:
    - Successful paths (valid token, required scopes).
    - Failure paths (invalid token, missing scopes) yield stable Problem Details and HTTP statuses.

## Interface & Contract Catalogue
- **DSL**
  - `task.kind: <pluginType>:v<major>` – plugin-backed task identifier; `<pluginType>` is a non-empty string, `<major>` is a positive integer.
  - `task.<field>` (other than `kind`) – plugin-defined configuration fields whose names and semantics are owned by the plugin provider.
  - `task.kind: jwtValidate:v1` – JWT validation task plugin; see `docs/3-reference/dsl.md` section 18.6 for DSL shape and behaviour.
- **Engine SPI (Java, indicative)**
  - `TaskPlugin` – main interface for plugin-backed task implementations.
  - `TaskExecutionContext` – immutable view of journey context, task config, journey metadata, instance id, state id, and HTTP request context (when applicable).
  - `TaskResult` – structured outcome type supporting normal context updates and Problem Details-based errors.
  - `TaskPluginException` – base type for plugin-specific failures distinguishable from engine bugs.
  - `TaskPluginRegistry` – configuration/lookup mechanism keyed by `(pluginType, major)`.
- **Telemetry**
  - Task execution spans enriched with:
    - `journey.name`, `journey.version`, `journey.kind`.
    - `task.state_id`.
    - `plugin.type`, `plugin.major`.
  - Error metrics distinguishing plugin-reported Problems from plugin runtime failures.
  - Plugin logging and error detail are driven by observability configuration (`observability.plugins.*` keys) and follow the logging policy defined in ADR-0025/ADR-0026 (no telemetry controls in the DSL).
- **Examples**
  - New example journeys/APIs demonstrating a `jwtValidate:v1` plugin and, optionally, another simple plugin (for example `calculateDiscount:v1`) once the engine SPI is implemented.

Non-normative note
- A separate, machine-readable “plugin capabilities” descriptor (for example declaring purity, allowed connectors, or idempotency) is **explicitly out of scope for v1**. Governance for what plugins may do relies on the normative constraints in ADR‑0026 (connectors-only I/O, no hidden waits, error model) and normal code review/operational policy, not on an additional capabilities metadata layer.
