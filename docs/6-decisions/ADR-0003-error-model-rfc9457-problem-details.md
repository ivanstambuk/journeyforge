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
- Per-journey custom error envelopes are allowed, but they are **always derived from the canonical Problem Details
  model**:
  - Internally, engines and tooling operate on Problem Details; there is no per-journey replacement of the canonical
    internal model.
  - Journeys that need a different caller-facing error shape MUST:
    - First normalise any low-level error data into a Problem Details object, and
    - Then apply a journey-level envelope mapper that takes a Problem Details object (or a `{code,reason,...}` view of
      it) and produces the desired error body for that journey.
- The DSL introduces a dedicated `spec.errors` block to make
  these mappings explicit and per-journey:
  - `spec.errors.canonicalFormat` declares the canonical internal error model for the journey; in this version it is
    implicitly `rfc9457` when omitted and MUST be `rfc9457` when present.
  - `spec.errors.normalisers` allows a journey to declare inline, per-source mappers that convert low-level error data
    (for example HTTP result objects) into Problem Details objects. These are selected explicitly from HTTP tasks and
    other error-producing operations.
  - `spec.errors.envelope` allows a journey to declare a single, journey-wide error envelope:
    - When omitted, the externally visible error structure for the journey MUST be the canonical Problem Details shape.
    - When present with `format: custom`, it MUST declare an inline mapper that transforms Problem Details objects into
      a single, stable error body structure for that journey.
  - Engines MUST NOT vary the externally visible error structure for a given journey at runtime based on the caller or
    other request metadata; one journey has exactly one external error envelope (Problem Details by default).
  - Shared DataWeave modules and `exprRef`-based reuse remain possible in general, but `spec.errors` itself is defined
    in terms of inline mappers in this version; cross-journey sharing of error mappers is out of scope until usage
    patterns are clearer and justified by real reuse.

## Consequences
- Consistency:
  - All error handling can be described in terms of a single, standard JSON shape (Problem Details), reducing conceptual overhead for users and implementers.
  - `JourneyOutcome.error.code` and `fail.errorCode` have well-defined semantics rather than being arbitrary strings.
- Interoperability:
  - Workflows interoperate cleanly with APIs that already use RFC 9457, and can expose RFC 9457 documents directly via `succeed` when appropriate.
  - OpenAPI contracts can describe error responses using standard Problem Details schemas when we add richer error mapping to exports.
- Flexibility:
  - Existing journey definitions can continue to expose simple `{code,reason}` error views while internally using Problem Details as the canonical model.
  - Journey definitions that need domain-specific error formats (for example, a custom error envelope for an orders API) can derive them from the canonical Problem Details object via `transform` states or the journey-level envelope mapper (`spec.errors.envelope`), while still preserving a single external error structure per journey.
- Implementation impact:
  - Engine implementations and tooling must implement helpers and/or libraries for constructing, validating, and logging Problem Details objects.
  - The DSL reference has been updated to describe the recommended mapping between `fail`, `JourneyOutcome.error`, and RFC 9457 Problem Details.

### Example – Custom error envelope derived from Problem Details

One common pattern is for a journey to expose a domain-specific error envelope to its caller while still using Problem
Details internally. For example, a journey that implements an "order lookup" API might expose errors of the form:

```json
{
  "errorId": "order-not-found",
  "message": "Order 123 was not found",
  "category": "BUSINESS",
  "status": 404,
  "details": {
    "orderId": "123"
  }
}
```

This envelope is **not** the canonical internal model; it is derived from it. A typical mapping is:

- `errorId` – derived from Problem Details `type` or `JourneyOutcome.error.code` (for example `order-not-found`).
- `message` – derived from Problem Details `title` or `detail`.
- `status` – copied from Problem Details `status`.
- `details.*` – populated from Problem Details extension members or from additional context.
- `category` – a domain-level classification (for example `BUSINESS`, `TECHNICAL`, `SECURITY`), chosen by the journey.

The journey would:

1. Normalise any downstream HTTP error into a Problem Details object and store it in `context` (for example
   `context.lastError.problemDetails`).
2. In a `transform` state near the end of the journey, build the custom envelope from that Problem Details object and
   any relevant context:
   - Set `context.output.error` to the custom shape shown above.
3. Use a `succeed` or `fail` state that:
   - Returns `context.output` as the body of the final HTTP response.
   - Sets `JourneyOutcome.error` based on the underlying Problem Details (for platform-level observability and
     consistency).

Example journey sketch (simplified DSL):

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: order-lookup
  version: 0.1.0
spec:
  start: fetchOrder
  states:
    fetchOrder:
      type: task
      task:
        kind: httpCall:v1
        operationRef: upstream.getOrder
        resultVar: httpResult
      next: handleResult

    handleResult:
      type: choice
      choices:
        - when:
            lang: dataweave
            expr: |
              context.httpResult.status == 200
          next: succeedWithOrder
      default: mapErrorEnvelope

    mapErrorEnvelope:
      type: transform
      transform:
        lang: dataweave
        expr: |
          {
            output: {
              error: {
                errorId: context.httpResult.problem.type default "order-error",
                message: context.httpResult.problem.title default context.httpResult.problem.detail,
                category: "BUSINESS",
                status: context.httpResult.problem.status,
                details: {
                  orderId: context.input.orderId
                }
              }
            },
            lastError: context.httpResult.problem
          }
      next: failWithMappedError

    failWithMappedError:
      type: fail
      errorCode: order-not-found
      reason: "Order lookup failed"

    succeedWithOrder:
      type: succeed
      outputVar: httpResult.body
```

Notes:
- Internally, `context.httpResult.problem` is assumed to hold the canonical Problem Details object derived from the
  upstream HTTP error (for example via earlier normalisation).
- `mapErrorEnvelope` builds a domain-specific error envelope under `context.output.error` while preserving the canonical
  Problem Details in `context.lastError`.
- `failWithMappedError` uses `errorCode`/`reason` to drive `JourneyOutcome.error`, which remains aligned with the
  underlying Problem Details, while the HTTP response body can expose `context.output` (including the custom envelope)
  to the caller.

## Related
- Journeys API & `JourneyOutcome` schema: `docs/3-reference/openapi/journeys.openapi.yaml`.
- DSL Reference: `docs/3-reference/dsl.md`.
@@
- Feature 001 – Core HTTP Engine + DSL: `docs/4-architecture/features/001/spec.md`.
- OpenAPI Export – Journeys API guideline: `docs/4-architecture/spec-guidelines/openapi-export.md`.
