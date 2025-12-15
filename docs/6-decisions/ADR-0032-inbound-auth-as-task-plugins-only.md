# ADR-0032 – Inbound Auth as Task Plugins Only

Date: 2025-12-15 | Status: Proposed

## Context

JourneyForge needs to model inbound authentication and authorisation for:
- `kind: Api` entrypoints (a synchronous HTTP endpoint), and
- `kind: Journey` entrypoints and step submissions (the Journeys API `/start` + `/steps/{stepId}`).

Earlier drafts explored putting inbound security controls into special-case DSL/binding constructs (for example a binding-level security policy model, and/or webhook-specific shared-secret configuration).

However, the project also has strong constraints and goals:
- Keep the DSL surface area small and versionable.
- Use the task plugin model (ADR-0026) for extensibility rather than introducing new plugin types.
- Keep error modelling consistent with the canonical Problem Details model (ADR-0003).
- Allow auth checks that may need access to current `context` (for example subject continuity checks for step submissions, or tenant-aware allowlists).
- Avoid “auto-fail” behaviour where a validation plugin implicitly terminates a run without journey-authored control flow.

The DSL reference now defines `jwtValidate:v1`, `mtlsValidate:v1`, and `apiKeyValidate:v1` as inbound auth task plugins and removes binding-level inbound security constructs (see `docs/3-reference/dsl.md` §18 and §12.2).

## Decision

JourneyForge models inbound authentication and authorisation **only** via explicit, versioned `task` plugins executed as part of the state graph:
- `jwtValidate:v1`
- `mtlsValidate:v1`
- `apiKeyValidate:v1`

The DSL MUST NOT define binding-level inbound security constructs (including any `spec.bindings.http.security` policy model) and MUST NOT define a webhook-specific `webhook.security` block.

Transport-level enforcement (gateway/ingress/service-mesh/function platform auth) remains outside the DSL and is an operational concern.

### Failure semantics (auth tasks)

Auth task plugins distinguish:
- **Business validation failures** (missing/invalid token, untrusted certificate, unknown API key), and
- **Misconfiguration/engine-side failures** (invalid profiles, unusable trust anchors, plugin configuration errors).

Rules:
- On business validation failure, auth tasks MUST NOT fail the run by themselves. They MUST write an RFC 9457 Problem Details object into `context.auth.<mechanism>.problem` (or the configured `authVar` namespace) and then continue to `next`.
- Journeys/APIs MUST enforce allow/deny decisions explicitly via normal control flow (`choice`/`fail`/loops) over `context` and the auth view.
- Misconfiguration/engine-side failures remain internal errors surfaced as Problems with separate `*_CONFIG_*` codes, aligned with ADR-0026 and ADR-0003.

### Interaction with HTTP semantics

- `kind: Api` endpoints may map auth failures to HTTP 401/403 using `spec.bindings.http.apiResponses` (ADR-0016) based on the canonical Problem Details produced by the journey.
- `kind: Journey` step submissions continue to use the Journeys API semantics (typically HTTP 200 for an accepted request). Auth failures are represented in `JourneyStatus`/`JourneyOutcome` and by remaining at or returning to an external-input state via graph logic.

## Consequences

Positive:
- Keeps inbound security modelling aligned with the task plugin model (ADR-0026): versioned, extensible, and consistent with other tasks.
- Allows context-aware and step-aware auth checks (for example enforcing that the same subject continues a run across submissions).
- Avoids a growing set of special-case DSL blocks for each auth mechanism.
- Keeps error semantics uniform via Problem Details (ADR-0003): journeys can branch on stable codes/types and still decide how to expose errors externally.

Negative / trade-offs:
- Boundary protection is not expressed purely as a binding-level contract; authors must wire auth tasks into graphs, and platform-level enforcement remains out-of-scope for the DSL.
- Exporters cannot automatically declare “required auth headers/certs” on step endpoints based on a dedicated `webhook.security` DSL block; documentation of such requirements must come from journey docs and/or higher-level gateway configuration.
- Auth checks occur after schema validation and payload stashing into `context.payload` (external-input semantics). Authors must ensure they do not accidentally treat `context.payload` as trusted until the auth decision is made.

Follow-ups:
- Keep examples and how-to guides showing a consistent “external-input → auth task → route accept/deny” pattern.
- Consider adding lint rules/guidance to detect missing auth checks on sensitive external-input states and to avoid unintended use of `context.payload` before auth decisions.
