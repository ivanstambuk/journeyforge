<!-- Template for business/domain journeys -->

# Journey – <Journey Name>

> Short summary of what this journey does, which upstream systems it touches, and the primary success outcome.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [<journey-id>.journey.yaml](<journey-id>.journey.yaml) |
| OpenAPI (per-journey) | [<journey-id>.openapi.yaml](<journey-id>.openapi.yaml) |
| Arazzo workflow | [<journey-id>.arazzo.yaml](<journey-id>.arazzo.yaml) |
| Docs (this page) | [<journey-id>.md](<journey-id>.md) |

## Summary

- Audience and primary business use cases.
- Short narrative that explains when to use this journey and what problem it solves.
- Key actors and systems involved (for example customer, operator, risk engine, core system).
- Actors & systems:
  - Client-side callers (for example web/mobile app, back-office system).
  - Human roles (for example operator, supervisor) that call journey step endpoints.
  - External systems the journey calls via `task` (for example notifications, risk engine, escalation system).
  - Journeys API as the orchestrator and HTTP surface.

## Contracts at a glance

- **Input schema** – key fields from `spec.input.schema` (for example `inputId`, `tenantId`, `subjectId`).
- **Output schema** – key fields from `spec.output.schema` or the projected `JourneyOutcome.output` for this journey.
- **Named outcomes** – if `spec.outcomes` is used, list the main outcome labels and what they mean.

## Step overview (Arazzo + HTTP surface)

Here’s a breakdown of the steps to call over the Journeys API, aligned with the Arazzo workflow and the HTTP-visible step/webhook endpoints defined in the journey DSL.

Authoring guidance (for humans and AI agents):
- The main Arazzo workflow for a business journey SHOULD expose **4–10 externally visible phases** (steps) along the happy path.
- Steps SHOULD correspond to distinct business phases (for example intake, triage, assignment, in-progress, approval, fulfillment, escalation, follow-up), not just “start” and “result”.
- When the underlying DSL state graph has more than three meaningful phases, prefer modelling and documenting them as separate steps instead of collapsing them into a minimal 2–3 step wrapper.
- It is acceptable (and encouraged) to introduce additional well-named steps (for example intermediate status checks or decision points) when they help make the journey behaviour clearer to integrators.
- Every step in this table MUST correspond to:
  - A concrete Arazzo step in the per-journey `.arazzo.yaml`, and
  - A concrete HTTP endpoint on the Journeys API (start, status, result, or a step/webhook endpoint) defined by the journey DSL.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `<stepId>` | Short description of the step | `` `<operationId>` `` | Path/query/body parameters | `$statusCode == 2xx` and any journey-level checks | Notable outputs (for example `journeyId`) |
| 2 | `…` |  |  |  |  |  |

## Scenarios and variations

- Main “happy path” scenario.
- Important alternative flows (for example early rejection, expiry, manual override).

## Graphical overview

Diagrams for this journey live under:

- `docs/3-reference/examples/business/<journey-id>/diagrams/`

When adding or updating a business journey, the AI agent **must**:
- Add or update the corresponding `*.puml` files under the `diagrams/` folder (sequence, state, activity, internal-state).
- Run PlantUML locally to generate `*.png` files using the shared theme, for example:
  - `java -jar tools/plantuml.jar docs/3-reference/examples/business/<journey-id>/diagrams/*.puml`
- Ensure the generated PNG files are committed alongside the `.puml` sources so VS Code previews and static docs render correctly.

### Sequence diagram

Client- and system-facing view of every Arazzo step plus any HTTP-visible step/webhook endpoints for this journey (no generic phases).

Authoring guidance for actors:
- Use between **2 and 5 actors** in the sequence diagram (for example client, operator UI, external systems, and the Journeys API).
- Actors MAY be human (customer, operator) or non-human (payment provider, risk engine, core system), but there should be at least one client-side actor and the Journeys API.
- Every actor shown MUST correspond to at least one explicit interaction with the journey:
  - Either a call **from** the journey (for example `type: task` with `kind: httpCall` or `eventPublish` to that system), or
  - A call **into** the journey (for example `start`/`status`/`result` endpoints or `wait`/`webhook` step calls).
- Human ↔ external-system interactions that do not touch the journey MUST NOT be shown unless they directly lead to a call into the journey (for example a user action in an external system that triggers a webhook into the journey).
- Every step in the Step overview table SHOULD appear as a labelled interaction in at least one diagram (sequence or activity) so integrators can see how to drive the journey over HTTP.
- When you need to show important out-of-band interactions (for example a client talking to an agent before the agent calls a step), prefer PlantUML notes attached to the relevant actors instead of drawing extra messages that have no direct call into or out of the journey.

<img src="diagrams/<journey-id>-sequence.png" alt="<Journey Name> – sequence" width="620" />

### State diagram (optional)

Journey state transitions driven by Arazzo steps and HTTP-visible step/webhook endpoints (internal DSL-only transitions may be omitted).

Authoring guidance:
- Focus on HTTP-visible phases (start, status, steps/webhooks, result) and how they drive the journey between major states.
- Ensure the main steps from the Step overview table can be mapped to the states and transitions shown here.

<img src="diagrams/<journey-id>-state.png" alt="<Journey Name> – state transitions" width="320" />

### Activity diagram (optional)

End-to-end activity flow from the client or business process point of view, including all Arazzo steps and any HTTP step/webhook endpoints.

<img src="diagrams/<journey-id>-activity.png" alt="<Journey Name> – activity flow" width="420" />

## Internal workflow (DSL state graph)

Internal view of the journey’s DSL (`spec.states`), showing every state (task, choice, wait, webhook, succeed, fail, etc.) and its transitions.

<img src="diagrams/<journey-id>-internal-state.png" alt="<Journey Name> – internal DSL state graph" width="620" />

## Implementation notes

- Pointers into `journeyforge-runtime-core` and connector modules for this journey.
- Important policies or behaviours:
  - Phases: list the main business phases and which states/steps implement them (for example classification, assignment, in-progress, resolution, escalation).
  - Timers & SLAs: describe any `timer` usage, how durations are derived (for example from priority), and what happens when timers fire vs when they do not.
  - Parallelism: when `parallel` is used, describe each branch and how `join.strategy` (for example `anyOf` vs `allOf`) influences which branch “wins”.
  - Other concerns: retries, compensation, resilience, metadata, subject handling, scheduling.
- Related how-to patterns:
  - Link to relevant technical use-case docs under `docs/2-how-to/use-cases/` when this journey showcases a particular DSL pattern (for example timer-based timeout with parallel + timer).
