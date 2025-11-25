# ADR-0027 – Expression Engines and `lang` Extensibility

Date: 2025-11-25 | Status: Proposed

## Context

JourneyForge’s initial DSL design (ADR‑0002) standardised on DataWeave 2.x as the single expression language for:
- `choice` predicates,
- `transform` states,
- mappers under tasks, `wait`/`webhook`/`schedule`, and
- error mappers under `spec.errors`.

These expression sites are identified by `lang`/`expr` blocks in the DSL and have historically used `lang: dataweave`. As the DSL evolves we want:
- A pluggable expression model where `lang` selects a versioned expression engine, and
- The ability to add dialects such as JSONata, JOLT, or jq without rewriting the DSL.

At the same time, expressions should remain **pure**: they compute values from their inputs but do not perform external I/O or mutate engine state beyond the values they return. This ADR generalises the expression model away from any single canonical language; individual engine features (for example DataWeave in Feature 013) define which engines are available in a given deployment.

## Decision

We adopt a pluggable, pure expression engine model:
- All DSL expression sites that use `lang` are backed by an **Expression Engine plugin** identified by an engine id (for example `dataweave`, `jsonata`, `jolt`, `jq`).
- Expression engines are **pure**: they:
  - Accept an expression string and a set of bindings (for example `context`, `payload`, `error`), and
  - Return a JSON/primitive value or a typed evaluation error.
  - MUST NOT call connectors or perform external I/O.
- Engine configuration determines which expression engines are available in a given deployment; this ADR does not designate any single engine as canonical or baseline for all deployments.

Engine configuration controls which expression engines are available; the DSL remains responsible only for selecting an engine via `lang`.

## Engine configuration and limits

We define a small, cross-engine configuration model for expression engines under a shared configuration tree:

```yaml
plugins:
  expressions:
    defaults:
      maxEvalMs: <int>        # maximum wall-clock time per evaluation
      maxOutputBytes: <int>   # maximum size of the materialised result
    engines:
      <engineId>:
        maxEvalMs: <int>        # optional override for this engine
        maxOutputBytes: <int>   # optional override for this engine
        # engine-specific options MAY be added here by engine features
```

Rules:
- `plugins.expressions.defaults` defines deployment-wide default limits that apply to all expression engines unless overridden.
- `plugins.expressions.engines.<engineId>` MAY override `maxEvalMs` and/or `maxOutputBytes` for a specific engine id; when absent, defaults apply.
- `maxEvalMs`:
  - Bounds the wall-clock time an engine may spend evaluating a single expression.
  - Engines MUST either:
    - abort evaluations that exceed this budget and surface a timeout error via the canonical error model, or
    - enforce stricter internal limits.
- `maxOutputBytes`:
  - Bounds the size of the materialised expression result (for example after JSON serialisation).
  - Engines MUST either:
    - fail evaluations that would exceed this limit with a clear error, or
    - truncate in an implementation-defined way that is documented in the corresponding engine feature (for example Feature 013 for DataWeave).
- Engine features (for example Features 013–016) MAY introduce additional, engine-specific configuration under their own `plugins.expressions.engines.<engineId>` subtree but MUST NOT change the semantics of `maxEvalMs` or `maxOutputBytes`.

## Expression engine identifiers

- The `lang` field in DSL expression sites is an engine identifier:
  - Example ids: `dataweave`, `jsonata`, `jolt`, `jq`.
  - Engines are resolved by id at load time; if a spec references an id for which no engine is registered, spec validation or engine startup MUST fail with a clear message.

## Expression Engine SPI (conceptual)

We define a JVM-level SPI for expression engines, for example:

```java
public interface ExpressionEnginePlugin {
  String id();                      // e.g. "dataweave", "jsonata"
  int majorVersion();               // optional versioning hook

  ExpressionResult evaluate(
      String expr,
      ExpressionEvaluationContext ctx
  ) throws ExpressionEngineException;
}

public interface ExpressionEvaluationContext {
  JsonNode context();    // current journey context (always present)
  JsonNode payload();    // current payload (for example HTTP body or step input); null when not applicable
  JsonNode error();      // error object for error-handling contexts; null otherwise
  JsonNode platform();   // platform metadata and configuration (environment, journey, run, config)
}

public sealed interface ExpressionResult
    permits ExpressionValue, ExpressionProblem {
}

public record ExpressionValue(JsonNode value) implements ExpressionResult {}

public record ExpressionProblem(ProblemDetails problem) implements ExpressionResult {}
```

Constraints:
- Implementations MUST be pure:
  - They MAY allocate memory and compute values.
  - They MUST NOT call connectors, open sockets, access the filesystem, or perform other external I/O.
- Engines SHOULD be side-effect free with respect to engine state; any caching must be internal and not observable via DSL semantics.

## DSL usage

- All existing expression sites continue to use the same shape but treat `lang` as an engine selector:
  - `choice.when.predicate: { lang: <engineId>, expr: <string> }`.
  - `transform` states: `transform.lang: <engineId>`, `expr` or `mapper`.
  - `mapper` blocks under `task`, `wait`/`webhook`/`schedule`, and `spec.errors`:
    - `mapper.lang: <engineId>`, `expr: <string>`.
- All expression sites that use `expr` MUST declare `lang` explicitly; the DSL does not define a default expression engine. Engine availability and limits are controlled by configuration and engine registration, not by implicit defaults in specs.
- Engines:
  - When `lang` is set to `jsonata`, `jolt`, or `jq` and a corresponding engine is registered, expressions are evaluated by that engine.
  - The exact expression syntax and capabilities are defined by the engine provider; limitations compared to DataWeave MUST be documented in the engine’s feature spec.

## Engine support per expression context

This ADR defines the **common integration model** (all expression sites use `lang` + `expr` and call an Expression Engine plugin). Engine-specific feature specs (for example Features 013–016) define where each engine is valid, but the matrix below captures the expected capabilities and validation rules for this version.

Support matrix (capabilities and validation expectations):

| Expression context                         | Description                                        | DataWeave (013) | JSONata (014) | JOLT (015)           | jq (016)        |
|-------------------------------------------|----------------------------------------------------|-----------------|---------------|----------------------|-----------------|
| `choice.when.predicate`                   | Boolean predicates for branching                   | ✅              | ✅            | ❌                   | ✅             |
| `transform.mapper` / `transform.expr`     | General JSON transforms                            | ✅              | ✅            | ✅                   | ✅             |
| Task mappers (`task.*.mapper`)            | HTTP/event/cache/etc. request/response shaping     | ✅              | ✅            | ✅                   | ✅             |
| `wait.apply.mapper` / `webhook.apply`     | Context updates on external input                  | ✅              | ✅            | ✅                   | ✅             |
| Scheduler mappers (`schedule.*.mapper`)   | `startAt` / `interval` / `maxRuns` / `subjectId`   | ✅              | ✅            | ✅                   | ✅             |
| Error mappers (`spec.errors.normalisers`) | Low-level error → Problem Details                  | ✅              | ✅            | ✅                   | ✅             |
| Error envelope mapper (`spec.errors.envelope.mapper`) | Problem Details → external error body     | ✅              | ✅            | ✅                   | ✅             |
| API responses predicates (`apiResponses.when.predicate`) | HTTP mapping conditions                 | ✅              | ✅            | ❌                   | ✅             |
| API responses `statusExpr`                | Expression producing HTTP status codes             | ✅              | ✅            | ❌                   | ✅             |

Notes:
- `✅` indicates contexts that an engine implementation MUST support when that engine is provided for this deployment and is enabled at that expression site.
- `❌` indicates contexts that an engine implementation MUST NOT support; the engine and tooling MUST treat uses of that engine in those contexts as validation errors. For this version, JOLT is explicitly not allowed for predicates (`choice.when.predicate`, `apiResponses.when.predicate`) or `apiResponses.statusExpr`.
- Engine-specific feature specs (for example Features 013–016) MAY further restrict supported contexts for a given engine but MUST NOT contradict `❌` cells in this matrix.
- Engines and validators MUST reject specs that attempt to use an engine in a context that is not supported for that engine (either by this matrix or by the corresponding engine’s feature spec).

## Error handling

- Expression evaluation errors are distinguished from plugin/engine internal errors:
  - Expression engines signal evaluation errors via `ExpressionResult` or `ExpressionEngineException` with an engine-specific **code**:
    - On normal evaluation errors (syntax-like, runtime-like, limit violations), engines MUST return `ExpressionProblem` with a Problem Details object whose extensions include a stable engine-specific error `code` (for example `DW_PARSE_ERROR`, `DW_FIELD_NOT_FOUND`, `JQ_COMPILE_ERROR`) defined by each engine feature (for example Feature 013 for DataWeave).
    - Engine features SHOULD document how each code conceptually maps to error categories such as syntax error, runtime error, limit violation, or internal/engine failure so that tooling can derive higher-level groupings from codes alone.
  - `ExpressionEngineException` is reserved for internal/engine defects and configuration bugs; the engine maps these to internal Problem types, distinct from expression-authored Problems.
  - The engine maps expression Problems into the canonical error model depending on the expression site:
    - For predicates: expression Problems SHOULD normally be treated as internal technical errors from the caller’s perspective unless the DSL explicitly allows author-visible failures.
    - For transforms/mappers: expression Problems may surface as caller-visible Problems or be classified as internal errors depending on context, but MUST never be silently ignored.
- Engine implementations MUST ensure that expression errors are observable in telemetry (for example via `expr.code` and other attributes) and are not conflated with task plugin failures.

## Consequences

Positive:
- **Extensibility:** New expression languages can be added by implementing `ExpressionEnginePlugin` and registering engines under new ids, without changing DSL shapes.
- **Purity and safety:** Expressions are guaranteed to be pure; all side-effects remain within task plugins and connectors, simplifying reasoning and sandboxing.
- **Consistency:** All expression sites share the same selection and evaluation model; authors see a consistent `lang`/`expr` pattern across choice, transform, mappers, and errors.

Negative / trade-offs:
- **Multiple languages:** Allowing JSONata/JOLT/jq alongside DataWeave increases cognitive load; teams must choose a small set of engines per project.
- **Implementation effort:** Engines must implement the SPI correctly and maintain parity for core behaviours; differences in type systems or semantics between engines must be documented.
- **Tooling complexity:** IDE tooling and linters must understand which engines are available and how to validate expressions per engine.

Related artefacts:
- ADR-0002 – Expression language: DataWeave.
- Feature 012 – Expression Engine Plugins (`docs/4-architecture/features/012/spec.md`).
- DSL reference – `docs/3-reference/dsl.md` (expression sites and `lang` usage).
