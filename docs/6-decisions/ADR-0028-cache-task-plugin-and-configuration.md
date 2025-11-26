# ADR-0028 – Cache Task Plugin and Configuration

Date: 2025-11-25 | Status: Accepted

## Context

JourneyForge v1 includes cache operations in the DSL (section 15 of `docs/3-reference/dsl.md`) to avoid redundant upstream calls and to share cached data across journey executions. Earlier drafts modelled caches as:

- Per-spec resources under `spec.resources.caches` (for example `defaultCache`, `hotItems`), and
- Two dedicated task kinds `cacheGet` / `cachePut` that referenced these resources via `cacheRef`.

Since then:

- The Task Plugin model (Feature 011, ADR-0026) has been adopted for all `type: task` states, so cache behaviour should also be expressed as a plugin.
- Platform binding and configuration (ADR-0022) clarified the separation between DSL and deployment-specific configuration.

We need a cache design that:

- Fits the Task Plugin model (`task.kind: <pluginType>:v<major>`).
- Keeps cache provider choice and operational settings in engine configuration, not in specs.
- Keeps the DSL small while still allowing per-journey/per-state TTL overrides.

## Decision

We model caching via a single cache task plugin and a single logical cache per deployment.

### 1. DSL: `task.kind: cache:v1`

- All cache behaviour is expressed via the task plugin `task.kind: cache:v1`.
- There is no `spec.resources.caches` block and no `cacheRef` in the DSL.
- Cache operations are selected via an `operation` field:

```yaml
type: task
task:
  kind: cache:v1
  operation: get | put
  key:
    mapper:
      lang: <engineId>           # e.g. dataweave
      expr: <expression>

  # Only for operation: put
  value:
    mapper:
      lang: <engineId>
      expr: <expression>

  # Optional per-call TTL override (seconds)
  ttlSeconds: <int>

  # Only for operation: get
  resultVar: <identifier>
next: <stateId>
```

Semantics:

- `operation: get`
  - Computes a cache key from `key.mapper`.
  - Looks up the key in the configured cache.
  - When present and not expired, deserialises the stored JSON and assigns it to `context.<resultVar>`.
  - When absent or expired, assigns `null` to `context.<resultVar>`.
- `operation: put`
  - Computes a cache key and value from `key.mapper` / `value.mapper`.
  - Stores the value in the configured cache under the key with the effective TTL.
  - Does not write any additional fields into `context`.

Validation rules:

- `task.kind` MUST be `cache:v1`.
- `operation` is required and MUST be `get` or `put`.
- For `operation: get`:
  - `resultVar` is required and MUST match the normal identifier pattern.
  - `value` is not used and MAY be omitted.
  - `ttlSeconds`, when present, is ignored for reads.
- For `operation: put`:
  - `value` is required.
  - `resultVar` MUST be omitted.
  - `ttlSeconds`, when present, MUST be an integer ≥ 1.

This keeps cache behaviour within the Task Plugin model while giving journeys a simple, explicit surface for cache reads and writes.

### 2. Cache lifetime and scope

- There is one **logical cache per deployment** from the DSL’s perspective.
- Journeys share this cache; isolation is achieved via key design:
  - Per-journey patterns use keys that include a journey-specific identifier (for example `journeyId`).
  - Cross-journey patterns use stable business identifiers (for example `userId`, `paymentId`).
- The cache is logically external to any single journey:
  - Its lifetime is independent of individual runs.
  - It is shared across engine instances according to the chosen provider.

### 3. Engine configuration (provider and defaults)

Cache provider selection and operational settings live entirely in engine/platform configuration, not in specs. Conceptually:

```yaml
plugins:
  cache:
    provider: inMemory | redis | cloudCache    # exactly one per deployment
    defaultTtlSeconds: 300                     # default entry TTL when DSL omits ttlSeconds
    maxMemoryBytes: 1073741824                 # optional soft memory limit (1 GiB)
    evictionPolicy: lru | lfu | ttl | random | noeviction
    keyPrefix: "journeyforge:dev:"             # optional key namespace prefix

    # provider-specific wiring (examples)
    providerConfig:
      redis:
        uri: "redis://cache.example.com/0"
        connectTimeoutMs: 100
        readTimeoutMs: 200
      inMemory:
        # typically no extra fields
```

Rules:

- The **provider** is chosen once per deployment; specs never reference it.
- `defaultTtlSeconds`:
  - Used when a `cache:v1` task omits `ttlSeconds`.
  - Applies uniformly across journeys.
- `ttlSeconds` on a `cache:v1` task overrides `defaultTtlSeconds` for that operation only.
- `maxMemoryBytes`, `evictionPolicy`, and `keyPrefix` are operational concerns:
  - They define how the cache behaves under memory pressure and how keys are namespaced.
  - They are not visible in the DSL and may be approximated differently by different providers.
- Provider-specific fields live under `providerConfig.*` and are intentionally left out of the DSL.

### 4. Relationship to other ADRs and features

- **ADR-0026 (Task Plugins Model and Constraints)**:
  - `cache:v1` is a normal task plugin:
    - Synchronous execution per state.
    - I/O only via engine-owned connectors.
    - Explicit context writes (`resultVar` for reads only).
- **ADR-0022 (Platform Binding and Per-Definition Configuration)**:
  - Cache provider and operational settings are part of engine configuration, not `spec.platform.config` or `context`.
- **DSL reference and features**:
  - Section 15 of `docs/3-reference/dsl.md` now defines the normative shape and semantics of `cache:v1`.
  - Existing cache examples (for example `cache-user-profile`) are updated to use `cache:v1` with `operation: get|put`.

## Consequences

Positive:

- **Spec-first and plugin-aligned**:
  - Caching is expressed via a single Task Plugin (`cache:v1`) consistent with other task kinds.
  - The DSL no longer needs `spec.resources.caches`, `cacheRef`, or special-case task kinds.
- **Provider-agnostic DSL**:
  - Specs do not mention Redis, in-memory, or cloud-specific cache products.
  - Provider choice and connection details are purely operational.
- **Simple per-journey tuning**:
  - Journeys can override TTL per state via `ttlSeconds` without worrying about providers or cache instances.
- **Shared cache by design**:
  - Keys determine isolation; using journey ids vs business ids is an explicit modelling choice.

Negative / trade-offs:

- **Single logical cache**:
  - There is no notion of multiple named caches in the DSL; modelling “hot vs cold” caches must be done via key design and TTL, not separate cacheRef ids.
  - If multiple logical caches become important later, a new plugin version or additional configuration fields will be required.
- **Operational responsibility**:
  - Operators are responsible for sizing and tuning the cache (memory limits, eviction policy) via engine configuration.
  - Misconfiguration can affect many journeys at once.

Rejected alternatives:

- **Per-spec `spec.resources.caches` with `cacheRef`**:
  - Rejected to keep provider selection and cache topology out of the DSL and to avoid per-spec cache definitions.
- **Multiple task kinds (`cacheGet` / `cachePut`)**:
  - Rejected in favour of a single `cache:v1` plugin with an `operation` field, to align with the Task Plugin model and reduce the surface area.

