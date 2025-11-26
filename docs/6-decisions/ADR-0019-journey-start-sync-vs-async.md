# ADR-0019 – Journey Start Semantics (Sync vs Async) and HTTP Status Mapping

Date: 2025-11-23 | Status: Proposed

## Context

JourneyForge orchestrates user-centric journeys (`kind: Journey`) and synchronous APIs (`kind: Api`) defined in the DSL
(`docs/3-reference/dsl.md`). Journeys are durable, stateful executions exposed via the Journeys API:

- `POST /api/v1/journeys/{journeyName}/start`
- `GET /api/v1/journeys/{journeyId}`
- `GET /api/v1/journeys/{journeyId}/result`
- `POST /api/v1/journeys/{journeyId}/steps/{stepId}` for external-input steps

Previous ADRs established:

- ADR-0004 – API endpoints (`kind: Api`) are synchronous, stateless HTTP endpoints with conventional HTTP status mapping.
- ADR-0007 – `spec.execution.maxDurationSec` defines a global wall-clock execution budget per journey/API invocation.
- ADR-0009 / ADR-0013 / ADR-0018 – Journeys use `JourneyStatus`, `JourneyOutcome`, external-input steps, and timers.
- ADR-0016 – `kind: Api` uses HTTP status codes derived from terminal phase + Problem Details for API clients.

However, the semantics of `POST /journeys/{journeyName}/start` for `kind: Journey` required refinement:

- Early drafts treated `/start` as always asynchronous: `202 Accepted` + `JourneyStartResponse`, with clients polling
  status and result.
- In practice, most user-facing journeys (registration, onboarding, support, etc.) need a **synchronous initial
  interaction**:
  - The user or frontend calls a single endpoint and expects either a final outcome or a concrete next step (for example
    “fill this form”, “approve this”, etc.).
  - Pure 202 + poll semantics feel heavy for UX and BFFs when the initial segment typically completes quickly.
- At the same time, JourneyForge still needs to support:
  - Long-running, heavily asynchronous flows (callbacks, timers, multi-party interactions).
  - Scheduled and offline journeys that may run well beyond normal interactive API timeouts.

We also want a clear answer to how HTTP status relates to `JourneyOutcome.phase`:

- For journeys, should HTTP status codes represent the **API call** status or the **business outcome** of the journey?
- How does this interact with synchronous start, where `/start` may return a final `JourneyOutcome` directly?

This ADR defines:

1. A per-journey `spec.lifecycle.startMode` that selects between **synchronous** and **asynchronous** start semantics.
2. The exact behaviour of `POST /journeys/{journeyName}/start` in both modes.
3. The mapping between HTTP status codes and journey outcome for journeys.
4. How the generic Journeys OpenAPI and per-journey exports describe these behaviours.

## Problem

We need a spec-first, user-centric definition for journey start semantics that balances:

- **UX needs**:
  - For most user journeys, the first call should behave like a normal synchronous API: return either a final result or a
    well-defined next step (including any step payload) in a single call.
  - Clients (especially frontends/BFFs) should not be forced into a heavy 202 + poll pattern for journeys that typically
    complete an initial segment quickly.

- **Durable orchestration needs**:
  - Some journeys are inherently long-running (timers, callbacks, SLAs, multi-party approvals) and should not block
    the start call.
  - We must still support scheduled/offline journeys that are **user-initiated** but execute mostly in the background.

- **Clarity and consistency**:
  - We already have `spec.execution.maxDurationSec` as a global execution budget; we want to avoid introducing a second
    timeout knob specific to start if possible.
  - `kind: Api` should remain clearly synchronous and stateless; we must avoid conflating Api status mapping with
    journey outcome semantics.
  - HTTP status codes on the Journeys API must be clearly defined and consistent with the envelope types
    (`JourneyStatus`, `JourneyOutcome`, `JourneyStartResponse`).

The core questions are:

1. Should journeys support both **synchronous** and **asynchronous** start, and if so, how do we model that in the DSL?
2. What does `/journeys/{journeyName}/start` return in each mode?
3. How should HTTP status codes relate to `JourneyOutcome.phase` for journeys, especially when `/start` itself returns
   a `JourneyOutcome`?

## Forces and Requirements

1. **User-centric journeys**
   - The primary scope is user-centric journeys: every journey has user context; machine-only batch jobs are out of
     scope.
   - Many journeys are “short” from the user’s perspective: they start synchronously and either complete or quickly
     reach an external-input state.

2. **Durable, long-running execution**
   - Journeys may still be long-running overall (timers, callbacks, SLAs, multi-party flows).
   - Synchronous start should not preclude the journey from continuing asynchronously after the initial response.

3. **Spec simplicity**
   - Prefer a single execution-time budget (`spec.execution.maxDurationSec`) over a second, start-specific timeout.
   - Minimise new DSL knobs, and keep journey lifecycle concerns local to `spec.lifecycle`.

4. **Single canonical start endpoint**
   - Product teams and frontends should not need to hard-code different URLs for sync vs async start.
   - There should be a **single `/journeys/{journeyName}/start`** path; behaviour varies per journey based on `startMode`.

5. **HTTP semantics**
   - For journeys, HTTP status codes should represent **API call success/failure**, not the business success/failure of
     the journey.
   - `JourneyOutcome.phase` (and `error`) is the canonical indicator of business success or failure.
   - This is intentionally different from `kind: Api`, where terminal failures are mapped to non-2xx HTTP statuses.

6. **OpenAPI and tooling**
   - Generic `journeys.openapi.yaml` should be explicit about all possible responses from `/start` (even if this yields
     a union), while per-journey OpenAPI exports can specialise based on `startMode`.
   - Typed clients generated from per-journey OAS should have precise contracts for each journey.

## Decision

### 1. `spec.lifecycle.startMode` for `kind: Journey`

Introduce a per-journey lifecycle setting:

```yaml
spec:
  lifecycle:
    cancellable: false        # optional; default true
    startMode: sync           # optional; sync (default) | async
```

- `spec.lifecycle` applies **only** to `kind: Journey`:
  - `spec.lifecycle.startMode` MUST NOT be declared for `kind: Api`; engines and tooling SHOULD treat this as a
    validation error.
- `cancellable` retains its previous semantics (user cancellation via `_links.cancel`).
- `startMode` controls how `POST /journeys/{journeyName}/start` behaves for that journey:
  - When omitted or `sync`, the journey uses **synchronous start semantics**.
  - When `async`, the journey uses **asynchronous start semantics**.

Default:

- The effective default is **`startMode: sync`**:
  - This matches the user-centric focus: most journeys are expected to have a short initial segment that should behave
    like a synchronous API call.
  - Journeys that are long-running or heavily asynchronous SHOULD explicitly opt into `startMode: async`.

### 2. Asynchronous start semantics (`startMode: async`)

For `kind: Journey` with `spec.lifecycle.startMode: async`:

- `POST /api/v1/journeys/{journeyName}/start`:
  - MUST respond with **HTTP 202** and a `JourneyStartResponse` envelope that includes at least:
    - `journeyId` – the durable instance identifier.
    - `journeyName`.
    - `statusUrl` – canonical URL for `GET /journeys/{journeyId}`.
  - Does **not** guarantee when or how much work has executed by the time the 202 is returned.
  - Represents “journey accepted” semantics; execution may start immediately or later, subject to engine scheduling.

- Clients observe progress via:
  - `GET /journeys/{journeyId}` → `JourneyStatus`.
  - `GET /journeys/{journeyId}/result` → `JourneyOutcome` once terminal.

This preserves the original purely asynchronous contract and is recommended for journeys that:

- Are long-running from the outset.
- Are heavily parallel or timer-driven.
- Are primarily background or scheduled, even though a user context still exists.

### 3. Synchronous start semantics (`startMode: sync`)

For `kind: Journey` with `spec.lifecycle.startMode: sync`:

- `POST /api/v1/journeys/{journeyName}/start` behaves as a **synchronous** start for the initial segment:

Execution model:

- The engine MUST:
  - Create a journey instance and initialise `context` from the start request (subject to any input schema and metadata
    bindings).
  - Execute from `spec.start` until **one** of the following is reached:
    1. A terminal state: `succeed` or `fail`, or
    2. The first external-input state: `wait` or `webhook`.
- Timers:
  - If the control flow from `spec.start` reaches a `timer` state **before** any terminal or external-input state, the
    journey is considered a poor fit for synchronous start.
  - Engines and tooling SHOULD emit a strong warning or treat this as a validation error when such journeys declare
    `startMode: sync`.
  - Authors SHOULD instead:
    - Use `startMode: async`, or
    - Refactor the journey to reach an external-input or terminal state before timers on the synchronous path.

Response semantics:

- When the run reaches a **terminal state (`succeed` or `fail`)** within the effective execution budget
  (`spec.execution.maxDurationSec` and any stricter platform limits):
  - The start endpoint MUST respond with:
    - **HTTP 200**, and
    - A `JourneyOutcome` envelope:
      - `phase` = `SUCCEEDED` or `FAILED`.
      - `output` and/or `error` populated according to the error model and DSL.
- When the run reaches the **first external-input state (`wait` or `webhook`)** within the execution budget:
  - The start endpoint MUST respond with:
    - **HTTP 200**, and
    - A `JourneyStatus` document describing the current status:
      - `phase` = `RUNNING`.
      - `currentState` = the external-input state id.
      - Optional `_links` and metadata as per ADR-0009.
    - Exporters MAY extend this with additional top-level fields based on the state’s `response.schema` using
      the `allOf[JourneyStatus, response.schema]` pattern from ADR-0013.
- After the synchronous start response:
  - The journey instance may continue executing asynchronously (for example after external input, timers, or additional
    steps).
  - Clients MAY later call:
    - `GET /journeys/{journeyId}` for ongoing status, and
    - `GET /journeys/{journeyId}/result` to retrieve the final `JourneyOutcome`.

Global execution deadline:

- `spec.execution.maxDurationSec` remains the **only** spec-level time budget:
  - The global deadline is enforced as per ADR-0007.
  - If the deadline is reached during synchronous start, the normal timeout semantics apply via `spec.execution.onTimeout`.
  - There is **no separate** `sync.maxDurationSec`; journeys that need longer lifetimes than is acceptable for
    synchronous start SHOULD use `startMode: async`.

Usage guidance:

- `startMode: sync` is intended for short, user-centric journeys where:
  - The initial segment is expected to complete quickly (within normal interactive HTTP timeouts), and
  - It is desirable to return either:
    - A final result (`JourneyOutcome`), or
    - The first actionable external-input step (`JourneyStatus` + step payload) from the start call.
- Long-running, heavily asynchronous, or timer-first flows SHOULD prefer `startMode: async`.
- Engines and tooling SHOULD warn or fail fast when a journey that is structurally unlikely to complete or reach
  an external-input state quickly declares `startMode: sync`.

### 4. HTTP status codes vs journey outcome

For journeys, this ADR makes the HTTP vs outcome mapping explicit:

- HTTP status codes on the Journeys API (`/start`, `/journeys/{journeyId}`, `/journeys/{journeyId}/result`,
  `/journeys/{journeyId}/steps/{stepId}`) represent the **success or failure of the HTTP call and protocol**, not the
  business success or failure of the journey.
- The business outcome is represented by the **envelope documents**:
  - `JourneyOutcome.phase` (`SUCCEEDED` vs `FAILED`) and `error`.
  - `JourneyStatus.phase` when observing an in-flight run.

In particular:

- `GET /api/v1/journeys/{journeyId}/result`:
  - MUST return **HTTP 200** whenever it successfully returns a `JourneyOutcome` document for a terminal journey,
    even when `JourneyOutcome.phase = "FAILED"`.
  - Non-2xx statuses indicate HTTP/protocol errors (for example invalid journey id, auth errors), not business failure.

- For synchronous journeys (`startMode: sync`):
  - `POST /api/v1/journeys/{journeyName}/start`:
    - MUST use **HTTP 200** when it returns either:
      - A terminal `JourneyOutcome` (even when `phase = "FAILED"`), or
      - A `JourneyStatus` describing a pause at an external-input state.
    - Clients MUST inspect `JourneyOutcome.phase` and `error` to distinguish successful vs failed outcomes.

This is **intentionally different** from `kind: Api` (ADR-0016), where:

- Terminal failures are mapped to non-2xx HTTP responses based on Problem Details and `spec.bindings.http.apiResponses`.
- HTTP status is part of the contract for classifying API success vs failure.

For journeys, the semantic unit is the **journey outcome**, not the HTTP start call.

### 5. OpenAPI: generic vs per-journey contracts

Generic Journeys OpenAPI (`docs/3-reference/openapi/journeys.openapi.yaml`):

- For `POST /journeys/{journeyName}/start`, the generic schema MUST describe both sync and async possibilities:

  - `202`:
    - Body: `JourneyStartResponse`.
  - `200`:
    - Body: `oneOf`:
      - `JourneyOutcome`, or
      - `JourneyStatus`.
    - (Step-specific payloads are not modelled in the generic schema; see per-journey exports.)

- The generic spec may include descriptive text explaining that:
  - Actual behaviour for a given journey depends on `spec.lifecycle.startMode`.
  - Async journeys will only use the `202` branch; sync journeys will only use the `200` branch.

Per-journey OpenAPI exports:

- For each concrete journey:
  - Exporters MUST inspect `spec.lifecycle.startMode` and generate a more precise contract:
    - `startMode: async`:
      - `/journeys/{journeyName}/start` → `202` + `JourneyStartResponse` only.
    - `startMode: sync`:
      - `/journeys/{journeyName}/start` → `200` only, with:
        - `oneOf[ JourneyOutcome, JourneyStatus ]` as the base, and
        - Optional `allOf[JourneyStatus, response.schema]` when the first external-input state has a declared
          `response.schema` (as per ADR-0013).

This balances:

- An explicit generic spec for tooling that works generically across journeys.
- Precise, simple per-journey contracts for clients that bind to specific journeys.

## Consequences

### Positive

- **Better UX for user-centric journeys**:
  - Journeys can behave like synchronous APIs for the initial segment, returning a final outcome or a first-step payload
    directly from `/start`.
  - Frontends/BFFs can implement flows with a single start call in the common path, without having to always orchestrate
    202 + poll.

- **Clear separation from `kind: Api`**:
  - `kind: Api` remains purely synchronous with HTTP-status-based error semantics (ADR-0016).
  - `kind: Journey` uses envelopes (`JourneyStatus`, `JourneyOutcome`) and keeps HTTP status for protocol-level success.

- **Spec simplicity**:
  - Only one execution-time budget: `spec.execution.maxDurationSec` (ADR-0007).
  - Start behaviour is a simple lifecycle knob, local to journeys.

- **Observability remains uniform**:
  - All journeys, sync or async, still expose `/journeys/{journeyId}` and `/journeys/{journeyId}/result`.
  - Operator tools and dashboards can treat them uniformly.

### Negative / Risks

- **More complex generic `/start` contract**:
  - The generic OpenAPI for `/start` must describe both 200 and 202 paths, which is more complex for generic tooling.
  - Mitigated by per-journey exports, which are precise and simple.

- **Potential misconfiguration of `startMode: sync`**:
  - Authors might mark long-running journeys as sync and experience timeouts or poor UX.
  - Mitigated by:
    - Guidance in the DSL spec.
    - Engine/tooling warnings or validation for obviously bad sync configurations (timer-first, very long
      `maxDurationSec`, etc.).

- **HTTP 200 on failed outcomes may surprise some clients**:
  - Clients that assume “HTTP 2xx means business success” will need to learn the JourneyForge rule:
    - For journeys, inspect `JourneyOutcome.phase` and `error` for business semantics.
  - This is a deliberate trade-off to keep `/result` and sync `/start` consistent and to separate protocol vs business
    concerns.

### Alternatives Considered

1. **Async-only start (`202` only)**
   - Rejected:
     - Too heavy for user-centric journeys that want a synchronous initial interaction.
     - Forces all clients into a 202 + poll pattern even for short flows.

2. **Separate `/startSync` endpoint**
   - Rejected:
     - Adds a second URL to remember and configure.
     - Pushes URL-level branching into clients instead of keeping behaviour per journey.
     - We want a single canonical `/journeys/{journeyName}/start` path.

3. **Start-mode-specific timeout (`sync.maxDurationSec`)**
   - Rejected:
     - Adds a second time budget on top of `spec.execution.maxDurationSec`.
     - Increases DSL and mental complexity for limited value.
     - Preferred solution: use `startMode: async` for journeys whose lifetime/budget is incompatible with synchronous
       start.

## References

- DSL reference: `docs/3-reference/dsl.md` – sections:
  - 2.2.1 Journeys (`kind: Journey`)
  - 2.4 Execution deadlines (`spec.execution`)
  - 2.7 Lifecycle and user cancellation (`spec.lifecycle`)
- OpenAPI:
  - Generic Journeys API: `docs/3-reference/openapi/journeys.openapi.yaml`
- Related ADRs:
  - ADR-0004 – API endpoints (`kind: Api`)
  - ADR-0007 – Execution deadlines (`spec.execution.maxDurationSec`)
  - ADR-0009 – Journeys HATEOAS links
  - ADR-0013 – External-input step responses and schemas
  - ADR-0016 – API HTTP status mapping (`kind: Api`)
  - ADR-0018 – Timer state
