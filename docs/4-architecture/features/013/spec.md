# Feature 013 – DataWeave Expression Engine

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/013/plan.md` |
| Linked tasks | `docs/4-architecture/features/013/tasks.md` |
| Roadmap entry | #005 |

> Guardrail: This specification is the normative source of truth for the DataWeave expression engine integration. It builds on the generic expression engine model in Feature 012 and ADR-0027 and the language choice in ADR-0002.

## Overview

Implement a DataWeave-backed `ExpressionEnginePlugin` and wire all `lang: dataweave` expression sites in the DSL to use it. Preserve existing DataWeave semantics for predicates and mappers while moving evaluation behind the pure expression engine SPI defined in Feature 012.

## Goals

- Provide a production-grade DataWeave 2.x expression engine implementation that satisfies the `ExpressionEnginePlugin` contract.
- Ensure that all existing DSL expression locations using `lang: dataweave` behave identically before and after the refactor (modulo documented bug fixes).
- Establish DataWeave as the baseline expression engine implementation for this version of the DSL, while keeping the expression engine model open to additional dialects via the Expression Engine SPI.

## Non-Goals

- No changes to the DSL syntax for expressions beyond what is already defined in `docs/3-reference/dsl.md`.
- No external `.dwl` module loading or expression reuse beyond what is already covered in ADR-0015 and the DSL; this feature only changes the execution mechanism.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-013-01 | Implement DataWeave `ExpressionEnginePlugin`. | Engine registers a DataWeave expression engine under id `dataweave`; all `lang: dataweave` expression sites are evaluated via this engine. | Unit tests exercise representative predicates and mappers and assert correct values. | If engine lookup fails for `dataweave`, specs using `lang: dataweave` are rejected; there is no runtime fallback. | Expression evaluation telemetry, when emitted, includes `expr.code` values drawn from the DataWeave error code set and follows ADR‑0025/ADR‑0027 rules for expression telemetry. | ADR-0002, ADR-0027, Feature 012 |
| FR-013-02 | Preserve existing semantics. | For predicates, transforms, mappers, and error handlers, evaluation results and error behaviour match pre-SPI behaviour. | Regression tests capture existing journeys/specs and compare outcomes before/after the engine integration. | Behavioural differences are treated as regressions and must be fixed or explicitly documented in ADR-0002/0027. | Telemetry continues to reflect DataWeave usage consistently. | ADR-0002 |
| FR-013-03 | Define DataWeave bindings and error taxonomy. | DataWeave expressions see a well-defined set of bindings (`context`, `payload`, `error`, `platform`) and return values of the expected type for each expression site (boolean for predicates, JSON/primitive for mappers/transforms). | SPI and engine docs enumerate bindings and expected result types per expression site; tests assert that invalid result types (for example non-boolean predicates) are rejected. | When an expression returns an invalid type (for example non-boolean predicate), spec validation or runtime evaluation fails with a clear error mapped to the canonical error model. | Telemetry MAY classify DataWeave evaluation failures using the DataWeave-specific `expr.code` values and derived groupings (for example syntax-like vs runtime-like vs limit vs internal). | ADR-0002, ADR-0027 |
| FR-013-04 | Map DataWeave evaluation errors into Problem Details. | DataWeave engine failures (syntax errors, runtime exceptions, limit violations, internal errors) are assigned stable, DataWeave-specific `code` values that are mapped into Problem Details according to ADR‑0003; the feature spec documents how each `code` conceptually corresponds to syntax-like, runtime-like, limit, or internal/engine failures. | Tests cover representative failure cases and assert that Problem Details `type`/`code` (including `expr.code` attributes or equivalents) are stable and distinguish syntax-like vs runtime-like vs limit vs internal errors. | Engines MUST NOT leak raw expressions or sensitive values into Problem Details `detail`; detailed diagnostics go to logs/traces per ADR‑0025/0026. | Error metrics MAY be broken down by DataWeave error code, with higher-level groupings derived from code-to-category mappings. | ADR‑0003, ADR‑0025, ADR‑0026, ADR‑0027 |

## Test Strategy

- Add regression suites over:
  - `choice` predicates,
  - `transform` states,
  - task/`wait`/`webhook`/`schedule` mappers,
  - `spec.errors` normalisers and envelopes.
- Validate that syntax and runtime errors surface as expected and are distinguishable from engine bugs.

## Bindings and Result Types (DataWeave-specific)

DataWeave uses the generic expression bindings defined in the DSL (see `docs/3-reference/dsl.md`, section 10) and ADR‑0027. This section refines those rules for the `dataweave` engine.

### Top-level bindings

At every `lang: dataweave` expression site, the following DW variables are available:
- `context` – DW Object representing the current journey context JSON value.
- `payload` – DW value representing the current payload; when there is no meaningful payload for the expression site it is `null`.
- `error` – DW Object representing a canonical Problem Details document; only populated in error-handling contexts (for example `spec.errors` mappers and error-phase `apiResponses` rules). In all other contexts it is `null`.
- `platform` – DW Object exposing platform metadata and configuration:
  - `platform.environment: String`
  - `platform.journey: { name: String, kind: String, version: String }`
  - `platform.run: { id: String, startedAt: String, traceparent?: String }`
  - `platform.config: Object` (from `spec.platform.config`).

### Per-context expectations

For each expression site that uses `lang: dataweave`:
- `choice.when.predicate`:
  - Bindings: `context`, `payload` (when a current payload is in scope), `platform`; `error = null`.
  - Result: MUST be `Boolean`. Any other result type is treated as a DataWeave runtime error.
- `transform.expr` / `transform.mapper` and reusable mappers (`spec.mappers`):
  - Bindings: `context`, `payload` (when associated with a body/payload), `platform`; `error = null`.
  - Result: any DW value that can be serialised to JSON (object, array, string, number, boolean, null). Non-serialisable values (for example functions) are invalid.
- Task/step mappers (`task.*.mapper`, `wait.apply.mapper`, `webhook.apply.mapper`):
  - Bindings: `context`, `payload` (HTTP bodies, step payloads, etc.), `platform`; `error = null`.
  - Result: JSON-serialisable DW value whose shape is governed by the DSL for that mapper (for example body vs header vs key).
- Scheduler mappers (`schedule.*.mapper`):
  - Bindings: `context`, `payload` (when present), `platform`; `error = null`.
  - Result: values of the appropriate type per scheduler field:
    - `startAt` / `until`: String containing a valid RFC 3339 timestamp.
    - `interval`: String containing a valid ISO‑8601 duration.
    - `maxRuns`: Number (integer).
    - `subjectId`: String.
- Error normalisers (`spec.errors.normalisers.*.mapper`):
  - Bindings: `context` (journey context at failure time), `error` (canonical Problem), `payload` (when the normaliser is defined to depend on it), `platform`.
  - Result: DW Object representing a Problem Details document (at minimum `type`, `title`, `status`, and any additional fields required by ADR‑0003).
- Error envelope mapper (`spec.errors.envelope.mapper`):
  - Bindings: `context`, `error` (canonical Problem), `payload` (when needed), `platform`.
  - Result: DW Object representing the external error envelope declared by `spec.errors.envelope`.
- API response predicates (`apiResponses.when.predicate`):
  - Bindings: `context`, `platform`, and:
    - For success-phase rules: `error = null`, `payload` is any outcome/payload value the exporter defines for the rule.
    - For failure-phase rules: `error` is the canonical Problem; `payload` MAY be the same Problem or a mapped body as defined by the exporter.
  - Result: MUST be `Boolean`.
- API status expressions (`apiResponses.statusExpr.expr`):
  - Bindings: same as `apiResponses.when.predicate`.
  - Result: MUST be a Number that can be treated as an integer HTTP status code in the 100–599 range.

When a DataWeave expression produces a value of the wrong type for its context (for example non-boolean predicate, non-numeric status code, or non-serialisable value where JSON is required), the engine treats this as a DataWeave runtime error and maps it via the error codes below.

## DataWeave Error Codes and Problem Mapping

DataWeave engine failures are exposed via stable, engine-specific `expr.code` values and Problem Details `type` URIs. Each code conceptually falls into one of four groups (syntax-like, runtime-like, limit, internal), but only the code itself is normative; category can be derived from the code.

### Syntax-like errors

- `DW_PARSE_ERROR` – expression cannot be parsed (syntax error).
- `DW_UNSUPPORTED_SYNTAX` – expression uses DataWeave features not supported or not enabled in this engine configuration.

### Runtime-like errors

- `DW_UNKNOWN_VARIABLE` – reference to a variable or binding that does not exist in the current evaluation context.
- `DW_FIELD_NOT_FOUND` – object field lookup on an existing object fails because the field is absent.
- `DW_INDEX_OUT_OF_BOUNDS` – array index is outside the valid range.
- `DW_TYPE_MISMATCH` – value is not of the expected type for the expression site (for example non-boolean predicate, non-integer HTTP status code).
- `DW_FUNCTION_ERROR` – runtime failure inside user code or library functions where no more specific code applies (for example domain errors, invalid arguments).
- `DW_NULL_DEREFERENCE` – dereference on `null` where a value is required.

### Limit errors

- `DW_MAX_EVAL_TIME_EXCEEDED` – evaluation exceeded the configured `maxEvalMs` limit.
- `DW_MAX_RESULT_SIZE_EXCEEDED` – result size exceeded the configured `maxOutputBytes` limit.

### Internal / configuration errors

- `DW_ENGINE_INTERNAL_ERROR` – unexpected engine failure not attributable to the authored expression (for example engine bug or underlying library failure).
- `DW_CONFIG_INVALID` – DataWeave-specific configuration error (for example invalid module configuration or incompatible engine options).

### Mapping to Problem Details

For any DataWeave evaluation error:
- The engine constructs a Problem Details document with:
  - `code` extension: set to the exact DataWeave error code string (for example `"DW_PARSE_ERROR"`).
  - `type`: a stable URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/expressions/dataweave/parse-error` for `DW_PARSE_ERROR`).
  - `title` / `detail` / `status`: chosen according to ADR‑0003 and the expression context:
    - Syntax-like and runtime-like DW errors are normally treated as internal technical errors from the caller’s perspective unless the DSL explicitly allows surfacing them.
    - Limit and internal/config errors are treated as internal errors (for example HTTP 500 for `kind: Api`) and MUST NOT leak raw expressions or sensitive values in `detail`.
- For predicates, DataWeave evaluation errors SHOULD be treated as internal errors (not as journey-authored business Problems) unless the DSL explicitly allows author-visible failures.
- For transforms and mappers, DataWeave errors MAY surface as Problems or internal errors depending on context, but MUST never be silently ignored.

Engine telemetry SHOULD record `expr.engine=dataweave` and `expr.code=<DW_* code>` for failed evaluations so that operators can distinguish DataWeave failures from other engine or plugin errors.
