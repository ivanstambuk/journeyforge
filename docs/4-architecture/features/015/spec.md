# Feature 015 – JOLT Expression Engine

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/015/plan.md` |
| Linked tasks | `docs/4-architecture/features/015/tasks.md` |
| Roadmap entry | #007 |

## Overview

Introduce an optional JOLT-based `ExpressionEnginePlugin` registered under id `jolt`. JOLT is primarily oriented around JSON transformations; this feature scopes how JOLT can be used as an expression engine within JourneyForge.

## Goals

- Define how JOLT specifications map into the ExpressionEnginePlugin API.
- Clarify which DSL expression sites are a good fit for JOLT (primarily transforms and mappers, not boolean predicates).
- Document limitations and differences vs DataWeave and JSONata.

## Non-Goals

- JOLT does not need to support all possible expression sites; boolean predicates may remain DataWeave/JSONata-only if a JOLT mapping is not natural.
- No DSL syntax changes beyond allowing `lang: jolt` where appropriate.

## Bindings, Supported Sites, and Result Types

JOLT is primarily a JSON transformation language. Within JourneyForge, it uses the generic expression bindings (`context`, `payload`, `error`, `platform`) but is intentionally limited to transform/mapping sites.

- Bindings:
  - The engine constructs a JSON input document for JOLT that exposes the same logical bindings as other engines:
    - `context` – current journey context value.
    - `payload` – current payload for the expression site (for example HTTP body, step payload) or `null` when not applicable.
    - `error` – canonical Problem Details object in error-handling contexts; `null` otherwise.
    - `platform` – platform metadata/configuration object.
  - JOLT specifications can read from these fields using normal JOLT path mechanisms.

- Supported expression sites for `lang: jolt`:
  - ✅ `transform.expr` / `transform.mapper` and reusable mappers (`spec.mappers`).
  - ✅ Task mappers (`task.*.mapper`).
  - ✅ Scheduler mappers (`schedule.*.mapper`).
  - ❌ Predicates (`choice.when.predicate`, `apiResponses.when.predicate`) – using `lang: jolt` at predicate sites is a validation error.
  - ❌ `apiResponses.statusExpr.expr` – using `lang: jolt` for HTTP status expressions is a validation error.

- Result types:
  - For supported sites, the result of applying a JOLT specification MUST be JSON-serialisable and must satisfy the same type/shape constraints as other engines at that site (for example, body vs headers vs key).
  - When a JOLT transform yields a value that cannot be represented as JSON for the target site, or when the specification itself is invalid, the engine treats this as a JOLT error and maps it via the error codes below.

## JOLT Error Codes and Problem Mapping

JOLT engine failures are surfaced via stable `expr.code` values and Problem Details `type` URIs.

### Syntax/spec errors

- `JOLT_SPEC_PARSE_ERROR` – JOLT specification cannot be parsed.
- `JOLT_SPEC_INVALID` – JOLT specification is structurally invalid or unsupported in this engine configuration.

### Runtime-like errors

- `JOLT_TRANSFORM_ERROR` – JOLT transform failed at runtime for the given input (for example unexpected shapes that the spec cannot handle).
- `JOLT_TYPE_MISMATCH` – result type is incompatible with the expected type for the expression site.

### Limit errors

- `JOLT_MAX_EVAL_TIME_EXCEEDED` – JOLT evaluation exceeded the configured `maxEvalMs` limit.
- `JOLT_MAX_RESULT_SIZE_EXCEEDED` – result size exceeded the configured `maxOutputBytes` limit.

### Internal / configuration errors

- `JOLT_ENGINE_INTERNAL_ERROR` – unexpected engine failure not attributable to the authored specification or input.
- `JOLT_CONFIG_INVALID` – JOLT-specific configuration error (for example invalid engine options or feature flags).

### Mapping to Problem Details

For any JOLT evaluation error:
- The engine constructs a Problem Details document with:
  - `code` extension: set to the exact JOLT error code string (for example `"JOLT_SPEC_INVALID"`).
  - `type`: a stable URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/expressions/jolt/spec-invalid` for `JOLT_SPEC_INVALID`).
  - `title` / `detail` / `status`: chosen according to ADR‑0003 and the expression context; limit and internal/config errors are treated as internal errors (for example HTTP 500 for `kind: Api`).
- Because JOLT is not used for predicates or `statusExpr`, JOLT errors only arise in transform/mapping contexts; they may surface as Problems or internal errors depending on where they occur, but MUST never be silently ignored.
Expression telemetry for JOLT evaluations SHOULD, when enabled by telemetry packs, populate `expr.code` with the appropriate `JOLT_*` code and MAY include `expr.site`, `expr.duration_ms`, `expr.input_bytes`, `expr.output_bytes`, `expr.limit_hit`, and `expr.limit_kind` as described in ADR‑0025. Implementations MUST NOT record JOLT specification text or input/output payload values in telemetry by default.
