# ADR-0022 – Platform Binding and Per-Definition Configuration

Date: 2025-11-23 | Status: Accepted

## Context

JourneyForge needs a way for journeys and APIs to see *where* they are running and to consume environment-specific configuration, without turning the DSL into a generic configuration loader or exposing secrets into expressions.

Prior to this ADR:
- DataWeave expressions only had a single root binding, `context`, plus `payload` at external-input states.
- The DSL explicitly disallowed environment-variable substitution and generic secret access in expressions.
- Secrets were already modelled as opaque `secretRef` identifiers on specific policy surfaces (for example HTTP security and outbound HTTP auth), with engines responsible for resolving them against a secret store.

There were several competing forces:
- Authors occasionally need deployment metadata (for example the logical environment) for diagnostics and simple branching.
- Journeys and APIs benefit from per-definition knobs (for example base URLs, thresholds, feature toggles) that vary by environment but are *not* part of the user/tenant context.
- Operators need a clear contract for which configuration values a given definition requires, and the system should be able to validate that configuration before runs start.
- The DSL must remain analysable and deterministic; ad hoc environment lookups and direct secret access from expressions make behaviour hard to reason about and easy to misconfigure.

We therefore need a first-class way to:
- Expose a curated set of platform metadata into DataWeave.
- Declare per-definition configuration keys in specs and surface them into expressions in a controlled way.
- Keep secrets reference-only and out of expressions, `context`, and logs.

## Decision

JourneyForge introduces:
- A new DataWeave binding, `platform`, available to all expressions for both `kind: Journey` and `kind: Api`.
- A new per-definition configuration contract under `spec.platform.config`, whose keys are exposed as `platform.config.*`.

At the same time, the DSL continues to **forbid**:
- Generic environment-variable or configuration lookup functions (for example by arbitrary key name).
- Direct secret access from expressions; secrets remain `secretRef`-only on dedicated surfaces.

### 1. `platform` binding

The `platform` binding is a read-only JSON object injected into DataWeave for every run:

- It is available anywhere `context` is available (predicates, mappers, error mappers, etc.).
- It is defined for both `kind: Journey` and `kind: Api`.
- All fields are read-only from the DSL’s point of view; attempts to assign to `platform.*` are a validation error.

Shape (abstracted; see the DSL reference for exact details):

- `platform.environment: string`
  - Logical environment identifier (for example `dev`, `staging`, `prod-eu`).
  - Intended for diagnostics and simple environment-aware branching when necessary.

- `platform.journey: object`
  - `name: string` – `metadata.name` of the current journey or API.
  - `kind: string` – `"Journey"` or `"Api"`.
  - `version: string` – `metadata.version`.

- `platform.run: object`
  - `id: string` – journey instance identifier for journeys, or an implementation-defined request/run id for APIs.
  - `startedAt: string` – RFC 3339 timestamp when this run started.
  - `traceparent: string | null` – W3C Trace Context `traceparent` value associated with this run, taken from the last inbound request when available; `null` or absent when not available.

- `platform.config: object`
  - Populated from `spec.platform.config` for the current definition (see below).
  - Keys are fixed by the definition; engines MUST NOT inject undeclared keys.

The DSL does **not** define a `platform.region` or other environment-specific dimensions beyond those above; platforms may encode such detail into `platform.environment` if needed.

### 2. `spec.platform.config` → `platform.config.*`

Definitions may declare a configuration contract under `spec.platform.config`:

- `spec.platform.config` is optional for both journeys and APIs.
- Each key under `spec.platform.config` defines:
  - A logical configuration name for this definition.
  - A simple type (`string`, `integer`, `number`, `boolean`, `object`).
  - Whether it is required in all environments where the definition is enabled.
  - An optional human-readable description.

At runtime:
- For each declared key `k`, the platform injects a corresponding value at `platform.config.k`.
- For `required: true` keys:
  - The platform MUST ensure a value is present before the definition can run in a given environment.
  - Missing required values are a deployment/initialisation error, or a fast-fail error before any states execute.
- For `required: false` keys:
  - Values may be omitted; expressions must handle absence explicitly (for example via `default`).
- Engines and tooling SHOULD validate that values conform to the declared type; mismatches are configuration errors.
- Engines MUST NOT inject keys into `platform.config` that are not declared under `spec.platform.config`.

DataWeave expressions access these values directly as `platform.config.<key>`.

### 3. No generic environment/config lookup, no secrets in expressions

The DSL explicitly **does not** introduce:
- Generic lookup functions such as `get_env("NAME")` or `platform.config.get("key")`.
- Any mechanism for expressions to retrieve the values behind `secretRef` identifiers.

All environment/config access from expressions is via:
- Fixed `platform.*` metadata fields, and
- Statically declared `spec.platform.config` keys surfaced as `platform.config.*`.

Secrets:
- Remain modelled as opaque `secretRef` identifiers on dedicated configuration surfaces (for example inbound HTTP security and outbound HTTP auth).
- Are resolved by engines against an implementation-defined secret store.
- MUST NOT surface as values in `platform`, `platform.config`, `context`, or expression-visible bindings.
- MAY be consumed by specialised tasks or policies that produce non-secret outputs (for example tokens or signatures) without exposing raw secret material.

### 4. Allowed environment-based branching

The DSL allows predicates and mappers to read `platform.environment` and other metadata fields:
- Branching on `platform.environment` is permitted (for example enabling non-production debug flows).
- Guidance in the DSL reference and, later, linters will discourage overuse and highlight environment-based branching in security-sensitive locations.

Per-definition configuration that varies by environment (for example base URLs, thresholds, feature flags) should generally use `platform.config.*` keys rather than inspecting `platform.environment` directly.

## Consequences

### Positive consequences

- **Clear separation of concerns**
  - `context` continues to hold user, tenant, and business data.
  - `platform` holds deployment metadata and per-definition configuration.
  - Secrets are referenced only via `secretRef` on dedicated policy/task surfaces.

- **Explicit configuration contracts**
  - `spec.platform.config` documents exactly which configuration keys a definition depends on.
  - Operators can validate configuration at deploy time instead of discovering missing values at runtime.
  - Tooling can surface per-definition config requirements and help generate environment-specific config manifests.

- **Preserved analysability and determinism**
  - There is no generic “bag of environment variables” accessible to expressions.
  - Static analysis and linters can see all dependencies on `platform.config.*` from the spec alone.
  - Behaviour is less prone to accidental drift caused by undeclared environment variables.

- **Controlled environment awareness**
  - Authors have a simple, well-documented way to see which environment they are in and basic run metadata (`environment`, `journey`, `run`, `traceparent`).
  - This supports diagnostics, logging, and limited environment-based branching without opening a generic configuration channel.

### Negative / neutral consequences

- **Additional concepts for authors**
  - Authors must understand both `context` and `platform` and when to use each.
  - Per-definition configuration requires declaring keys under `spec.platform.config` instead of accessing configuration ad hoc.

- **Responsibility for operators**
  - Operators must provision configuration values for `spec.platform.config` keys in each environment where a definition is enabled.
  - Misconfigured or missing values are surfaced as deployment or fast-fail errors; this is intentional but requires good operational practices.

- **Limited expressiveness compared to generic env lookups**
  - Authors cannot dynamically fetch arbitrary environment variables or config keys based on runtime values.
  - When a large, evolving set of configuration values is required, `spec.platform.config` may feel verbose; however, this verbosity is a deliberate trade-off for explicitness.

## Alternatives considered

### 1. Generic environment/config lookup

One option was to add a function or binding that allowed expressions to fetch arbitrary environment or configuration keys by name (for example a generic key-value store).

This was rejected because:
- It would make behavioural dependencies on environment/config invisible at the DSL level.
- It would encourage using journeys and APIs as ad hoc config loaders.
- It would make it easy to accidentally treat configuration values as secrets or vice versa, eroding the separation in the secrets model.

### 2. No platform binding; push everything through `context`

Another option was to keep environment and configuration completely outside the DSL, pushing any environment-specific data into `context` via adapters or pre-processing.

This was rejected because:
- It makes it harder to distinguish between user/tenant data and deployment metadata.
- It places all responsibility for configuration discovery on callers and adapter layers, without a spec-visible contract.
- It makes common patterns (for example base URLs, risk thresholds) harder to express consistently across definitions.

### 3. Platform binding without per-definition config

We also considered exposing only metadata (for example environment, journey name/version, run id) via `platform`, and leaving all configuration to external systems.

This was rejected as incomplete because:
- It does not give definitions a first-class way to declare and document their configuration needs.
- It would likely lead to ad hoc patterns where configuration is smuggled into `context`, reducing clarity and analysability.

## Follow-ups

- The DSL reference has been updated to:
  - Introduce the `platform` binding and document its shape.
  - Describe `spec.platform.config` and its mapping to `platform.config.*`.
  - Tighten the limitations section to explicitly forbid generic environment/secret access from expressions while clarifying the `secretRef`-only model.
- Future work:
  - Extend the linter to:
    - Validate that `platform.config.*` references correspond to declared keys.
    - Optionally warn on heavy use of `platform.environment` in security-sensitive predicates.
  - Provide operational guidance and tooling for managing configuration for `spec.platform.config` across environments.

