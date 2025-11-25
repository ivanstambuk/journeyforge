# Feature 016 – jq Expression Engine

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/016/plan.md` |
| Linked tasks | `docs/4-architecture/features/016/tasks.md` |
| Roadmap entry | #008 |

## Overview

Introduce an optional jq-based `ExpressionEnginePlugin` registered under id `jq`. jq is a widely-used JSON query and transformation language; this feature scopes its use within JourneyForge as a pure expression engine.

## Goals

- Map jq programs onto the ExpressionEnginePlugin API.
- Define supported usage contexts (predicates vs transforms vs mappers).
- Document behavioural differences vs DataWeave/JSONata/JOLT (for example, streaming vs materialised JSON, type system).

## Non-Goals

- No DSL syntax changes beyond allowing `lang: jq` at expression sites.
- No requirement for jq to provide identical semantics to DataWeave; differences must be documented but not hidden.

## Bindings and Result Types

jq uses the generic expression bindings defined in Feature 012 and ADR‑0027 (`context`, `payload`, `error`, `platform`) and the per-context expectations in the DSL reference (section 10).

- Bindings:
  - The engine exposes the same logical bindings as for `dataweave` and `jsonata`:
    - `context` – current journey context value.
    - `payload` – current payload for that expression site (for example HTTP body, step payload) or `null` when not applicable.
    - `error` – canonical Problem Details object in error-handling contexts; `null` otherwise.
    - `platform` – platform metadata/configuration object.
- Supported sites:
  - jq MAY be used at any expression site where `lang` is allowed and the engine has enabled jq for that context (subject to ADR‑0027’s support matrix). In particular, jq is intended to support:
    - Predicates (`choice.when.predicate`, `apiResponses.when.predicate`).
    - Transforms and general mappers.
    - Error mappers (`spec.errors`) when configured.
- Result types:
  - Predicates MUST evaluate to boolean.
  - `transform` and general mappers MUST return values that can be serialised to JSON, with additional type/shape constraints defined by the DSL for each mapper site.
  - `apiResponses.statusExpr.expr` MUST return an integer HTTP status code (100–599).
When jq produces a value of the wrong type for its context, or a value that cannot be represented as JSON for the target site, the engine treats this as a jq runtime error and maps it via the error codes below.

## jq Error Codes and Problem Mapping

jq engine failures are surfaced via stable `expr.code` values and Problem Details `type` URIs.

### Syntax-like errors

- `JQ_PARSE_ERROR` – jq program cannot be parsed.
- `JQ_UNSUPPORTED_SYNTAX` – jq program uses features not supported or not enabled in this engine configuration.

### Runtime-like errors

- `JQ_RUNTIME_ERROR` – generic runtime error in jq evaluation where no more specific code applies.
- `JQ_UNKNOWN_VARIABLE` – reference to a variable/binding that does not exist in the current evaluation context.
- `JQ_TYPE_MISMATCH` – value is not of the expected type for the expression site (for example non-boolean predicate, non-integer HTTP status code).
- `JQ_FUNCTION_ERROR` – runtime failure inside jq functions or filters.
- `JQ_NULL_DEREFERENCE` – dereference on `null` where a non-null value is required.
- `JQ_INDEX_OUT_OF_BOUNDS` – array index is outside the valid range.

### Limit errors

- `JQ_MAX_EVAL_TIME_EXCEEDED` – jq evaluation exceeded the configured `maxEvalMs` limit.
- `JQ_MAX_RESULT_SIZE_EXCEEDED` – result size exceeded the configured `maxOutputBytes` limit.

### Internal / configuration errors

- `JQ_ENGINE_INTERNAL_ERROR` – unexpected engine failure not attributable to the authored jq program.
- `JQ_CONFIG_INVALID` – jq-specific configuration error (for example invalid engine options or library loading configuration).

### Mapping to Problem Details

For any jq evaluation error:
- The engine constructs a Problem Details document with:
  - `code` extension: set to the exact jq error code string (for example `"JQ_PARSE_ERROR"`).
  - `type`: a stable URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/expressions/jq/parse-error` for `JQ_PARSE_ERROR`).
  - `title` / `detail` / `status`: chosen according to ADR‑0003 and the expression context; limit and internal/config errors are treated as internal errors (for example HTTP 500 for `kind: Api`).
- For predicates, jq evaluation errors SHOULD be treated as internal errors unless the DSL explicitly allows author-visible failures.
- For transforms and mappers, jq errors MAY surface as Problems or internal errors depending on context, but MUST never be silently ignored.
Expression telemetry for jq evaluations SHOULD, when enabled by telemetry packs, populate `expr.code` with the appropriate `JQ_*` code and MAY include `expr.site`, `expr.duration_ms`, `expr.input_bytes`, `expr.output_bytes`, `expr.limit_hit`, and `expr.limit_kind` as described in ADR‑0025. Implementations MUST NOT record jq program text or input/output payload values in telemetry by default.
