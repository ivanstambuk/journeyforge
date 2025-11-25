# Feature 014 – JSONata Expression Engine

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/014/plan.md` |
| Linked tasks | `docs/4-architecture/features/014/tasks.md` |
| Roadmap entry | #006 |

## Overview

Introduce an optional JSONata-based `ExpressionEnginePlugin` registered under id `jsonata`. When enabled in engine configuration, JSONata can be selected via `lang: jsonata` at any DSL expression site.

## Goals

- Define how JSONata expressions are evaluated in the context of JourneyForge (bindings, expected result types).
- Document limitations and differences compared to DataWeave (for example, type system, function set).
- Provide a reference implementation that satisfies the Expression Engine SPI.

## Non-Goals

- No changes to DSL syntax besides allowing `lang: jsonata` where `lang` is already supported.
- No guarantee of full parity with DataWeave; some expressions may not be directly portable between engines.

## Bindings and Result Types

JSONata uses the generic expression bindings defined in Feature 012 and ADR‑0027 (`context`, `payload`, `error`, `platform`) and the per-context expectations in the DSL reference (section 10). When `lang: jsonata` is selected at an expression site:
- The engine exposes the same logical bindings as for `dataweave`:
  - `context` – current journey context value.
  - `payload` – current payload for that expression site (for example HTTP body, step payload) or `null` when not applicable.
  - `error` – canonical Problem Details object in error-handling contexts; `null` otherwise.
  - `platform` – platform metadata/configuration object.
- JSONata expressions MUST respect the same result-type contracts as DataWeave:
  - Predicates (`choice.when.predicate`, `apiResponses.when.predicate`) MUST evaluate to boolean.
  - `transform` and general mappers MUST return values that can be serialised to JSON, with additional type/shape constraints defined by the DSL for each mapper site.
  - `apiResponses.statusExpr.expr` MUST return an integer HTTP status code (100–599).
Invalid result types (for example non-boolean predicates, non-numeric status codes, non-serialisable values) are treated as JSONata runtime errors and mapped via the error codes below.

## JSONata Error Codes and Problem Mapping

JSONata engine failures are surfaced via stable `expr.code` values and Problem Details `type` URIs. Each code conceptually falls into syntax-like, runtime-like, limit, or internal/config buckets, but only the code itself is normative.

### Syntax-like errors

- `JN_PARSE_ERROR` – expression cannot be parsed (syntax error).
- `JN_UNSUPPORTED_SYNTAX` – expression uses JSONata features not supported or not enabled in this engine configuration.

### Runtime-like errors

- `JN_UNKNOWN_VARIABLE` – reference to a variable/binding that does not exist in the current evaluation context.
- `JN_PATH_NOT_FOUND` – path lookup on an object/array fails because the path does not resolve to a value.
- `JN_TYPE_MISMATCH` – value is not of the expected type for the expression site (for example non-boolean predicate, non-integer HTTP status code).
- `JN_FUNCTION_ERROR` – runtime failure inside user or library functions where no more specific code applies.
- `JN_NULL_DEREFERENCE` – dereference on `null` where a value is required.

### Limit errors

- `JN_MAX_EVAL_TIME_EXCEEDED` – evaluation exceeded the configured `maxEvalMs` limit.
- `JN_MAX_RESULT_SIZE_EXCEEDED` – result size exceeded the configured `maxOutputBytes` limit.

### Internal / configuration errors

- `JN_ENGINE_INTERNAL_ERROR` – unexpected engine failure not attributable to the authored expression.
- `JN_CONFIG_INVALID` – JSONata-specific configuration error (for example invalid engine options or module configuration).

### Mapping to Problem Details

For any JSONata evaluation error:
- The engine constructs a Problem Details document with:
  - `code` extension: set to the exact JSONata error code string (for example `"JN_PARSE_ERROR"`).
  - `type`: a stable URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/expressions/jsonata/parse-error` for `JN_PARSE_ERROR`).
  - `title` / `detail` / `status`: chosen according to ADR‑0003 and the expression context; syntax/runtime/limit/internal semantics mirror DataWeave’s treatment (for example limit and internal/config errors are treated as internal errors, typically HTTP 500 for `kind: Api`).
- For predicates, JSONata evaluation errors SHOULD be treated as internal errors unless the DSL explicitly allows author-visible failures.
- For transforms and mappers, JSONata errors MAY surface as Problems or internal errors depending on context, but MUST never be silently ignored.
Expression telemetry for JSONata evaluations SHOULD, when enabled by telemetry packs, populate `expr.code` with the appropriate `JN_*` code and MAY include `expr.site`, `expr.duration_ms`, `expr.input_bytes`, `expr.output_bytes`, `expr.limit_hit`, and `expr.limit_kind` as described in ADR‑0025. Implementations MUST NOT record JSONata expression source text or input/output payload values in telemetry by default.
