# Plugin Profiles – Engine Configuration

This runbook describes how to configure engine-side profiles for task plugins such as `jwtValidate:v1` and `mtlsValidate:v1`. It complements the normative contracts in:
- DSL reference: `docs/3-reference/dsl.md` (sections 5 and 18).
- Feature 011: `docs/4-architecture/features/011/spec.md`.
- ADR-0026: Task Plugins Model and Constraints.

Profiles provide global defaults for plugin behaviour; individual tasks in the DSL may override specific fields as described in Feature 011.

## 1. Configuration root

Plugin profiles live under a dedicated `plugins` configuration tree:

```yaml
plugins:
  jwtValidate:
    profiles: {}
  mtlsValidate:
    profiles: {}
  # future plugins...
```

Profile names are simple strings (for example `default`, `admin`, `internalCallbacks`). Journeys and APIs refer to profiles via the `profile` field on plugin-backed tasks; when the field is omitted, the effective profile name is `"default"`.

Observability/logging controls for plugins use a separate `observability.plugins.*` tree; see `docs/5-operations/observability-telemetry.md` and ADR‑0025/ADR‑0026.

## 2. JWT validation profiles

Example configuration:

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

        mode: required            # required | optional
        anonymousSubjects:
          - "00000000-0000-0000-0000-000000000000"

        source:
          location: header        # header | query | cookie
          name: Authorization
          scheme: Bearer

        # Optional default claim constraints; shape mirrors DSL overrides
        requiredClaims: {}

      admin:
        issuer: "https://issuer.example.com"
        audience: ["journeyforge-admin"]
        jwks:
          url: "https://issuer.example.com/.well-known/jwks.json"
          cacheTtlSeconds: 300
        clockSkewSeconds: 30
        mode: required
```

Semantics:
- Engines MUST:
  - Provide at least one profile named `"default"` when `jwtValidate:v1` is enabled.
  - Treat profile fields as defaults for the corresponding DSL fields on `jwtValidate:v1` tasks.
- Effective value for each field is resolved as:
  - If the field is present in the DSL `task` block, use the DSL value.
  - Else, if the field is present in the selected profile, use the profile value.
  - Else, apply a documented engine default or treat the configuration as invalid and surface an internal configuration error.
- Field names and meanings in profiles MUST match those in the DSL:
  - `issuer`, `audience`, `jwks.url`, `jwks.cacheTtlSeconds`, `clockSkewSeconds`, `mode`, `anonymousSubjects`, `source.*`, `requiredClaims`.

## 3. mTLS validation profiles

Example configuration:

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

        allowAnyFromTrusted: true       # accept any cert chaining to trustAnchors

        # Optional default filters
        allowSubjects:
          - "CN=journey-client,OU=Journeys,O=Example Corp,L=Zagreb,C=HR"
        allowSans:
          - dns: api.example.com
        allowSerials:
          - "01AB..."

      internalCallbacks:
        trustAnchors:
          - pem: |
              -----BEGIN CERTIFICATE-----
              ...
              -----END CERTIFICATE-----
        allowAnyFromTrusted: false
        allowSubjects:
          - "CN=callback-client,O=Example Corp,C=HR"
```

Semantics:
- Engines MUST:
  - Support inline PEM-encoded `trustAnchors` under `pem`.
  - Provide at least one `"default"` profile when `mtlsValidate:v1` is enabled.
- Effective value for each field is resolved as:
  - DSL override on `mtlsValidate:v1` task, if present.
  - Else, profile value when present.
  - Else, for filters, no filters are applied and behaviour falls back to `allowAnyFromTrusted`.
- When no filters are configured in either DSL or profile:
  - If `allowAnyFromTrusted` is `true`, any certificate that chains to a configured trust anchor is accepted.
  - If `allowAnyFromTrusted` is `false`, certificates that chain correctly but do not match any filters are rejected.

## 4. Relationship with DSL

- DSL defines per-task behaviour and explicit overrides:
  - `jwtValidate:v1` and `mtlsValidate:v1` tasks can set fields such as `profile`, `source`, `trustAnchors`, `allowSubjects`, etc.
- Profiles define global defaults for these fields:
  - Journeys reference profiles by name; profile shapes are stable across deployments.
- The override rule for all plugin profiles is:

> **Effective field value = DSL override if set, otherwise profile value if set, otherwise engine default/error.**

Profiles are intended to be stable, operational configuration; changes to profiles may alter behaviour across multiple journeys that refer to them. For per-journey or per-state special cases, prefer DSL overrides on the relevant task.

## 5. Cache plugin configuration (`cache:v1`)

The cache task plugin uses a **single logical cache per deployment** and does not have per-profile configuration. Instead, engines expose a small provider-agnostic configuration block under `plugins.cache`:

```yaml
plugins:
  cache:
    provider: inMemory | redis | cloudCache   # one per deployment
    defaultTtlSeconds: 300                    # default TTL when DSL omits ttlSeconds
    maxMemoryBytes: 1073741824                # optional soft limit (bytes)
    evictionPolicy: lru | lfu | ttl | random | noeviction
    keyPrefix: "journeyforge:dev:"            # optional key namespace prefix

    # provider-specific wiring
    providerConfig:
      redis:
        uri: "redis://cache.example.com/0"
        connectTimeoutMs: 100
        readTimeoutMs: 200
      inMemory: {}
```

Semantics:
- `provider` is chosen once per deployment; DSL (`task.kind: cache:v1`) never mentions concrete technologies.
- `defaultTtlSeconds` applies when a `cache:v1` task omits `ttlSeconds`; per-call `ttlSeconds` in the DSL overrides this value.
- `maxMemoryBytes` and `evictionPolicy` describe desired cache behaviour under pressure; providers may approximate these according to their own capabilities.
- `keyPrefix` namespaces all keys written by JourneyForge so multiple deployments can safely share the same physical cache.

Provider-specific details live entirely under `providerConfig.*` and are outside the DSL; see ADR-0028 and the DSL reference (section 15) for the normative cache plugin contract.
