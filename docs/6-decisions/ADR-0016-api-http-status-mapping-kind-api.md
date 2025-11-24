# ADR-0016 – HTTP Status Mapping for `kind: Api`

Date: 2025-11-22 | Status: Proposed

## Context

JourneyForge exposes two related HTTP surfaces:

- The generic Journeys API (`kind: Journey`), which returns envelopes such as `JourneyStartResponse`, `JourneyStatus`,
  and `JourneyOutcome` with stable status codes (for example 202 for start, 200/409 for result).
- Synchronous API endpoints (`kind: Api`), which execute a journey in a single HTTP call and return a direct response
  body derived from the journey context.

ADR-0003 defined RFC 9457 Problem Details as the canonical error model for journeys, and the DSL now exposes
`spec.errors` so journeys can:

- Normalise low-level errors (for example HTTP task results) into Problem Details, and
- Optionally map Problem Details into a single, journey-wide error envelope.

For `kind: Api`, we need a way to:

- Map runtime success and error conditions to HTTP status codes in a journey-controlled, declarative way.
- Preserve Problem Details as the canonical error model and default behaviour.
- Avoid polluting the generic Journeys API (`kind: Journey`) with per-journey HTTP status mapping.
- Keep journeys self-contained and compatible with inline-only DataWeave (ADR-0015).

Earlier discussions considered:

- Hard-coding 2xx for success and a single non-2xx for all failures.
- Mapping status purely from downstream HTTP task results.
- Extending `spec.outcomes` with HTTP status metadata.

These options either lacked runtime flexibility (only one error status), coupled journeys too tightly to downstream
HTTP codes, or overloaded `spec.outcomes` with concerns that are specific to `kind: Api`.

## Decision

For `kind: Api`, the DSL introduces an optional `spec.apiResponses` block that allows journeys to declaratively control
HTTP status codes for both success and error responses via a unified rule table.

- When `spec.apiResponses` is **omitted**:
  - Success responses behave as today:
    - `phase = SUCCEEDED` produces HTTP 200.
    - The response body is derived from `context.<outputVar>` when `outputVar` is set on the terminal `succeed` state
      or from the full `context` when `outputVar` is absent.
  - Error responses are driven by Problem Details (ADR-0003):
    - The engine MUST construct a canonical Problem object for the failure.
    - The HTTP status MUST be taken from the Problem `status` field when present and valid; otherwise, the engine
      MUST default to 500.
    - The response body MUST be:
      - Problem Details itself when `spec.errors.envelope` is omitted or `format: problemDetails`, or
      - The result of the configured `spec.errors.envelope` mapper when `format: custom` (one envelope per journey).
- When `spec.apiResponses` is **present**:
  - Journeys can provide an ordered list of rules that match on:
    - The terminal phase (`SUCCEEDED`/`FAILED`),
    - Optional error type (Problem `type`), and
    - Optional DataWeave predicates over final `context` and the canonical Problem object.
  - The first matching rule determines the HTTP status code for that invocation.
  - If no rule matches, the engine falls back to phase-specific defaults:
    - `SUCCEEDED`: HTTP 200,
    - `FAILED`: HTTP status from the Problem `status` field, or 500 when absent.
  - The response body is still:
    - Derived from `succeed.outputVar` / `context` for success, and
    - Derived from the Problem object via `spec.errors.envelope` (or Problem itself) for errors.

This design:

- Keeps journeys that already use Problem Details error semantics free from extra configuration.
- Gives `kind: Api` authors fine-grained, runtime-controlled HTTP status mapping that can:
  - Propagate or rewrite downstream HTTP status codes, and
  - Group multiple error conditions into the same HTTP status.
- Preserves the invariant that each journey has one external error envelope (Problem by default, or a single custom
  envelope via `spec.errors.envelope`), even when HTTP status varies per error.
- Leaves the generic Journeys API contract unchanged.

## Details – `spec.apiResponses` (kind: Api only)

### Shape

For journey definitions with `kind: Api`:

```yaml
spec:
  apiResponses:
    rules:
      - when:
          phase: FAILED
          errorType: "urn:subject-unauthenticated"
        status: 401

      - when:
          phase: FAILED
          errorType: "urn:subject-unauthorized"
        status: 403

      - when:
          phase: FAILED
          predicate:
            lang: dataweave
            expr: |
              payload.error.status >= 500
        status: 502

      - when:
          phase: SUCCEEDED
          predicate:
            lang: dataweave
            expr: |
              context.downstream.status == 201 or context.downstream.status == 204
        statusExpr:
          lang: dataweave
          expr: context.downstream.status

    default:
      SUCCEEDED: 200                  # optional; defaults to 200 when omitted
      FAILED: fromProblemStatus       # optional; defaults to “Problem.status or 500”
```

- `spec.apiResponses` is only valid for `kind: Api`; it MUST be rejected on other kinds.
- `rules` is an ordered list; the first rule whose `when` matches controls the status for that invocation.
- `default` contains phase-specific fallbacks; when omitted, the defaults are:
  - `SUCCEEDED = 200`
  - `FAILED = fromProblemStatus` (Problem `status` or 500 when absent).

### Rule matching

Each rule has:

```yaml
when:
  phase: SUCCEEDED | FAILED           # required
  errorType: <string>                 # optional; only meaningful when phase == FAILED
  predicate:                          # optional
    lang: dataweave
    expr: |
      ...
status: <integer>                     # optional when statusExpr is present
statusExpr:                           # optional; preferred for dynamic status
  lang: dataweave
  expr: |
    ...
```

- `when.phase`:
  - MUST be either `SUCCEEDED` or `FAILED`.
  - Filters rules to the terminal phase of the invocation.
- `when.errorType`:
  - When present, the rule only matches when the canonical Problem `type` equals the given string.
  - It MUST NOT be used on `phase: SUCCEEDED` rules; tooling SHOULD flag this as a validation error.
- `when.predicate`:
  - When present, is a DataWeave expression evaluated with:
    - `context` bound to the final journey context, and
    - `payload.error` bound to the canonical Problem object for `phase: FAILED` (and `null` for `phase: SUCCEEDED`).
  - The expression MUST return a boolean; non-boolean results are a validation error.
- `status` / `statusExpr`:
  - At least one of `status` or `statusExpr` MUST be present.
  - When both are present, tooling SHOULD treat this as a validation error.
  - `status`, when present, MUST be an integer in the valid HTTP status code range (100–599).
  - `statusExpr`, when present, MUST be a DataWeave expression that evaluates to an integer status code; engines
    SHOULD validate the result at runtime and fail fast or log when it is outside 100–599.

Matching algorithm:

1. Filter rules by `when.phase == terminalPhase`.
2. Within that subset, for each rule in list order:
   - If `when.errorType` is present and does not equal the Problem `type`, skip the rule.
   - If `when.predicate` is present and evaluates to `false`, skip the rule.
   - Otherwise, the rule matches:
     - Use `statusExpr` when present, otherwise `status`.
     - Stop; no later rules are considered.
3. If no rule matches:
   - Use `default[terminalPhase]`:
     - For `SUCCEEDED`, this is 200 when omitted.
     - For `FAILED`, this is “Problem.status or 500” when omitted.

### Defaults and omission

- When `spec.apiResponses` is omitted entirely:
  - `kind: Api` engines MUST behave as if:
    - `rules` were empty, and
    - `default.SUCCEEDED = 200`
    - `default.FAILED = fromProblemStatus`
  - This ensures that journeys that already conform to Problem Details semantics do not need any additional
    configuration.
- When `spec.apiResponses.default` is omitted:
  - Engines MUST apply the same implicit defaults as above.

### Interaction with `spec.errors` and error envelopes

- Canonical Problem Details:
  - Engines MUST continue to treat RFC 9457 Problem Details as the canonical internal error model for `kind: Api`,
    consistent with ADR-0003 and the `spec.errors` section of the DSL reference.
- `spec.errors.envelope`:
  - `spec.apiResponses` only controls HTTP status; it does **not** change the shape of the error body.
  - The error response body for `kind: Api` MUST follow the same rules as for journeys:
    - When `spec.errors.envelope` is omitted or `format: problemDetails`, the error body is the Problem object itself.
    - When `spec.errors.envelope.format: custom` is present, the error body is the result of that mapper applied to the
      canonical Problem object.
  - Engines MUST NOT vary the error envelope per caller or per rule; only the HTTP status may vary at runtime via
    `spec.apiResponses`.

### Interaction with journeys and OpenAPI export

- Journeys (`kind: Journey`):
  - `spec.apiResponses` has no effect on the generic Journeys API; paths such as `/journeys/{journeyName}/start`,
    `/journeys/{journeyId}`, and `/journeys/{journeyId}/result` keep their existing status code semantics.
  - Wrappers that adapt a journey outcome to an HTTP API MAY reuse the same `spec.apiResponses` pattern, but that is
    out of scope for this ADR.
- OpenAPI export (`kind: Api`):
  - OpenAPI exporters MAY use `spec.apiResponses` to:
    - Document the primary success status code(s) for the API endpoint.
    - Document common error status codes (for example 401, 403, 502) alongside the Problem-based or custom error
      envelope.
  - Exporters SHOULD continue to treat Problem Details as the canonical error model and only add additional schemas
    when a custom `spec.errors.envelope` is configured.

## Consequences

Positive:

- `kind: Api` journeys gain a declarative, per-journey mechanism for HTTP status mapping that:
  - Can propagate or rewrite downstream service status codes.
  - Can distinguish between security, business, and technical errors (for example 401/403/502) without changing the
    underlying error envelope.
- Journeys that already conform to Problem Details semantics incur no extra configuration; the defaults are useful and
  predictable.
- The generic Journeys API remains stable and unaffected by per-journey HTTP semantics for APIs.
- The design is compatible with inline-only DataWeave and `spec.mappers`/`mapperRef` patterns (ADR-0015).

Negative / trade-offs:

- `spec.apiResponses` introduces another configuration surface for authors to learn and tooling to validate.
- Incorrect or overly complex rule sets could make it harder to reason about the exact status code an API will return
  for a given condition.
- The mapping remains per journey; there is no cross-journey global policy layer in this ADR.

## References

- Error model and Problem Details: `docs/6-decisions/ADR-0003-error-model-rfc9457-problem-details.md`.
- API endpoints (`kind: Api`): `docs/6-decisions/ADR-0004-api-endpoints-kind-api.md`.
- Error configuration (`spec.errors`): `docs/3-reference/dsl.md` (Error configuration section).
- DataWeave reuse and inline-only decision: `docs/6-decisions/ADR-0015-dataweave-reuse-and-external-modules.md`.
