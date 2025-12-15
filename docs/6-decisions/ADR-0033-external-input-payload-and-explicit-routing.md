# ADR-0033 – External-Input Payload Stashing and Explicit Routing

Date: 2025-12-15 | Status: Proposed

## Context

External-input states (`wait`, `webhook`) are the primary mechanism for pausing a run and resuming it later via step submissions (`POST /journeys/{journeyId}/steps/{stepId}`).

Earlier drafts embedded additional behaviour inside the external-input state itself (for example:
inline “apply/mapper” transforms and inline branching on submission). This created special-case semantics that:
- Made the external-input states “do too much” (ingest + transform + route),
- Reduced composability (it was harder to insert reusable tasks between ingest and transform, such as auth checks, payload validation tasks, normalisation, auditing, or throttling), and
- Increased DSL surface area and implementation complexity for behaviour that can be expressed with existing state types (`task`, `transform`, `choice`).

At the same time, JourneyForge intentionally models inbound authentication/authorisation via task plugins executed in the graph (ADR-0032). External-input submissions therefore need a simple, consistent “ingest then run normal states” model so that auth tasks can run immediately after submission.

The DSL reference now specifies external-input payload stashing into `context.payload` and routes via state-level `next` and subsequent normal states (see `docs/3-reference/dsl.md` §12).

## Decision

External-input submissions follow a minimal, two-phase model:

1. **Validate request body against schema**
   - The engine validates the submitted JSON value against `wait.input.schema` / `webhook.input.schema`.
   - If validation fails, the engine returns HTTP 400 with schema errors and the journey remains at the same active step. The state machine does not advance.

2. **On successful validation, stash payload and continue via normal states**
   - The engine MUST copy the submitted JSON value into `context.payload` (overwriting any previous value).
   - The engine MUST then transition to the external-input state’s state-level `next`.
   - All subsequent behaviour (auth checks, transformations, routing, rejection/looping, step response projection) is expressed via ordinary state types (`task`, `transform`, `choice`, `fail`, `succeed`, `parallel`, etc.).

Payload lifecycle:
- `context.payload` is a per-submission scratch space.
- While the run is paused at an external-input state, `context.payload` MUST be absent: when the engine enters a `wait` or `webhook` state (including re-entry via loops), it MUST clear `context.payload`. Journeys that need to retain any part of a submission across re-entry MUST explicitly copy it into a stable context subtree.

The DSL MUST NOT define inline branching or inline transformation constructs inside `wait`/`webhook` beyond:
- `input.schema` (validation),
- Optional `response` projection configuration for step responses (ADR-0013), and
- Timeout wiring (`timeoutSec` / `onTimeout`).

### Rejection and resubmission

If a submission is schema-valid but rejected by journey logic (for example auth fails, business rules deny it, or it is a duplicate submission), journeys express resubmission by routing back to the same external-input state via normal control flow.

## Consequences

Positive:
- External-input states become simpler and uniform: they only define schema validation, payload stashing, step response projection, and timeouts.
- Authors can insert reusable steps in a predictable place (for example “external-input → auth task → route accept/deny → transform ingest → route business”).
- Reduces DSL surface area and avoids special-case constructs that duplicate existing state types.
- Aligns with the task plugin model and composition goals (ADR-0026, ADR-0032).
- Reduces risk of persisting untrusted or stale submission payloads while waiting; journeys must opt in to retaining any submission data beyond the current synchronous processing.

Negative / trade-offs:
- Graphs become slightly more verbose: patterns that were previously inline now require explicit `transform`/`choice` states.
- Schema-valid submissions write into `context.payload` before any auth/business checks run. Journeys must treat `context.payload` as untrusted until their validation/auth steps have passed.
- Journeys that want to retain the last submission across re-entry (for example for UI “prefill” or audit) must explicitly copy the payload (or selected fields) into a stable context subtree before routing back to the external-input state.

Follow-ups:
- Keep examples and how-to docs showing the recommended post-submission pipeline (auth → ingest → branch).
- Consider adding lint guidance to discourage using `context.payload` as a long-lived domain field without an explicit ingest step.
