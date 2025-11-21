# ADR-0003 – Error Model and RFC 9457 Problem Details

Date: 2025-11-19 | Status: Accepted

## Context
JourneyForge journeys call downstream HTTP APIs and expose their own outcomes via the Journeys API (`JourneyOutcome`). We need a consistent error model that:
- Works across HTTP tasks, journey failures, and future admin/journey APIs.
-- Avoids every journey definition inventing a bespoke error envelope.
- Plays well with OpenAPI and existing ecosystem tools.

The current DSL defines:
- `fail` states with `{ errorCode, reason }`.
- `JourneyOutcome.error` with `{ code, reason }` and room for extension.
- HTTP tasks that capture downstream errors as structured data in `context.<resultVar>`.

We considered several options:
- Keep an ad-hoc `{code,reason}` model only.
- Adopt RFC 9457 "problem details" as the canonical error shape.
- Allow per-journey pluggable error formats.

## Decision
- RFC 9457 "problem details" is the canonical internal error model for HTTP and journey errors.
  - Implementations MUST be able to represent errors as a Problem Details object with fields `type`, `title`, `status`, `detail`, `instance`, plus extension members.
  - Downstream HTTP error responses MAY already use `application/problem+json`; when they do not, they can be normalised into a Problem Details object.
- The DSL-level `fail` and `JourneyOutcome.error` remain a small envelope but adopt Problem Details semantics:
  - `fail.errorCode` maps to `JourneyOutcome.error.code` and MUST be a stable identifier for the error condition; by default, it SHOULD be the Problem Details `type` URI (or a URI-like identifier).
  - `fail.reason` maps to `JourneyOutcome.error.reason` and SHOULD be derived from Problem Details `title` or `detail`.
  - `JourneyOutcome.error` MAY include additional members (for example `status`, `instance`, or other extensions) derived from the underlying Problem Details object.
- Workflows are expected to use explicit `transform` + DataWeave mappers to:
  - Normalise arbitrary downstream error payloads (custom JSON, plain text, etc.) into a canonical Problem Details object.
  - Map a Problem Details object into alternative client-facing error envelopes when required.
- The DSL does not introduce a dedicated `spec.errors` block; instead:
  - Shared DataWeave modules (`.dwl` files) SHOULD be used as reusable error mappers (for example "HTTP result → Problem Details", "Problem Details → client error").
  - Future DSL versions MAY add configuration for global error mappers once usage patterns are clearer.

## Consequences
- Consistency:
  - All error handling can be described in terms of a single, standard JSON shape (Problem Details), reducing conceptual overhead for users and implementers.
  - `JourneyOutcome.error.code` and `fail.errorCode` have well-defined semantics rather than being arbitrary strings.
- Interoperability:
  - Workflows interoperate cleanly with APIs that already use RFC 9457, and can expose RFC 9457 documents directly via `succeed` when appropriate.
  - OpenAPI contracts can describe error responses using standard Problem Details schemas when we add richer error mapping to exports.
- Flexibility:
  - Existing journey definitions can continue to expose simple `{code,reason}` errors while internally using Problem Details.
  - Journey definitions that need domain- or client-specific error formats can derive them from the canonical Problem Details object via `transform` states.
- Implementation impact:
  - Engine implementations and tooling must implement helpers and/or libraries for constructing, validating, and logging Problem Details objects.
  - The DSL reference has been updated to describe the recommended mapping between `fail`, `JourneyOutcome.error`, and RFC 9457 Problem Details.

## Related
- Journeys API & `JourneyOutcome` schema: `docs/3-reference/openapi/journeys.openapi.yaml`.
- DSL Reference: `docs/3-reference/dsl.md`.
@@
- Feature 001 – Core HTTP Engine + DSL: `docs/4-architecture/features/001/spec.md`.
- OpenAPI Export – Journeys API guideline: `docs/4-architecture/spec-guidelines/openapi-export.md`.
