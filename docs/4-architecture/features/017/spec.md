# Feature 017 – WebSocket Binding for Journeys

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/017/plan.md` |
| Linked tasks | `docs/4-architecture/features/017/tasks.md` |
| Roadmap entry | #009 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md` (none currently open for this feature), encode resolved answers directly in the Requirements/NFR/Behaviour sections below, and use ADRs under `docs/6-decisions/` (in particular ADR‑0029) for architecturally significant clarifications.

## Overview

Introduce a WebSocket inbound binding for `kind: Journey` definitions via `spec.bindings.websocket`. The binding provides a first-class, spec-visible way to drive journeys over a long-lived WebSocket connection while reusing the same logical engine operations as the HTTP Journeys API:
- Start new journey instances.
- Submit external-input step payloads.
- Optionally receive status/outcome updates over the same connection.

This feature is **DSL/API design only**; engine implementation and transport details will be delivered in later slices.

Primary references:
- DSL reference: `docs/3-reference/dsl.md` (section 17 – Inbound Bindings).
- Inbound bindings ADR: `docs/6-decisions/ADR-0029-inbound-bindings-and-spec-bindings-http.md`.

## Goals

- Define a minimal, explicit DSL surface for WebSocket bindings:
  - `spec.bindings.websocket.endpoint.path` / `.subprotocol`.
  - `spec.bindings.websocket.start.messageType`.
  - `spec.bindings.websocket.steps.<stepId>.messageType` for external-input states.
- Keep the engine core transport-agnostic:
  - WebSocket binding must map onto the same logical operations as the HTTP Journeys API (`start`, `submitStep`), not introduce WebSocket-specific control-flow semantics in the DSL.
- Document the high-level message model:
  - How start and step messages conceptually look (envelopes, payload field).
  - How status and outcome messages are surfaced back to clients.
- Ensure the DSL clearly constrains applicability:
  - WebSocket binding is valid only for `kind: Journey`.
  - Journeys may configure both HTTP and WebSocket bindings in parallel.

## Non-Goals

- No engine implementation of WebSocket servers, message routing, or backpressure in this feature.
- No changes to `kind: Api`; APIs remain HTTP-only for now.
- No new state types or control-flow constructs tied specifically to WebSockets.
- No per-message security model beyond reuse of existing HTTP security configuration for the handshake; JWT/mTLS remain task plugins, and HTTP security policies remain bound to HTTP endpoints.

## DSL (normative)

### 1. `spec.bindings.websocket`

For `kind: Journey` definitions, journeys MAY declare a WebSocket binding:

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: <string>
  version: <semver>
spec:
  bindings:
    websocket:
      endpoint:
        path: <string>            # optional; defaults to /ws/journeys/{metadata.name}
        subprotocol: <string>     # optional WebSocket subprotocol id

      start:
        messageType: <string>     # logical message type for start messages

      steps:
        <stepId>:
          messageType: <string>   # logical message type for step submissions
```

Constraints:
- `spec.bindings.websocket`:
  - MUST NOT be present for `kind: Api`.
  - MAY be present for `kind: Journey` alongside `spec.bindings.http`.
- `endpoint.path`:
  - When present, MUST be a non-empty string starting with `/` and MUST NOT include a host.
  - When absent, the canonical path is `/ws/journeys/{metadata.name}`.
- `start.messageType`:
  - When present, MUST be a non-empty string.
  - If omitted, tooling MAY treat this as a configuration error; this feature spec recommends requiring it for clarity.
- `steps`:
  - Keys under `steps` MUST be state ids of external-input states (`type: wait` or `type: webhook`) defined under `spec.states`.
  - Each `steps.<stepId>.messageType` MUST be a non-empty string.

### 2. Conceptual message model (design-only)

This feature does not prescribe a specific wire-level JSON schema, but it defines conceptual message roles that the WebSocket Journeys API MUST support:

- **Start messages** – initiate a journey run:
  - Use `start.messageType` as the logical message type (for example in a `type` field).
  - Carry:
    - A journey identifier (typically implied by the WebSocket endpoint and/or `metadata.name`).
    - A JSON payload that becomes the initial `context`, validated against `spec.input.schema` when present.
  - Map 1:1 to the logical semantics of `POST /api/v1/journeys/{journeyName}/start`.

- **Step submission messages** – submit payloads to external-input states:
  - Use `steps.<stepId>.messageType` as the logical message type.
  - Carry:
    - A journey instance identifier (`journeyId`), and
    - A JSON payload for the external-input state, validated against that state’s `input.schema` when present.
  - Map 1:1 to the logical semantics of `POST /api/v1/journeys/{journeyId}/steps/{stepId}`.

- **Status and outcome messages** – observe journey progress:
  - WebSocket bindings SHOULD support publishing:
    - Status updates that reflect `JourneyStatus` for a given `journeyId`.
    - Outcome messages that reflect terminal `JourneyOutcome` for a given `journeyId`.
  - The exact envelope shapes (for example `type`, `journeyId`, `payload`) will be defined in the Journeys WebSocket API reference.

The DSL itself does not embed these message schemas; it only declares that a given journey is reachable via WebSockets with the specified endpoint and message type identifiers.

## Behaviour (conceptual)

- Connection:
  - Clients connect to `endpoint.path` (or the canonical path when omitted) using the standard WebSocket handshake.
  - If `endpoint.subprotocol` is configured, clients SHOULD negotiate it; servers MAY reject connections that do not.
- Authentication:
  - Authentication and TLS termination for the WebSocket handshake are handled by the HTTP layer and existing HTTP security mechanisms; this feature introduces no new security DSL.
  - Journeys that require JWT/mTLS should continue to use `jwtValidate:v1` / `mtlsValidate:v1` tasks inside the graph.
- Routing:
  - The combination of:
    - WebSocket endpoint path, and
    - Message type (`start.messageType` / `steps.<stepId>.messageType`),
    is sufficient for the binding implementation to route messages to the correct logical operation.

## Non-Functional Requirements

| ID | Requirement | Driver | Notes |
|----|-------------|--------|-------|
| NFR-017-01 | Keep DSL impact minimal and additive. | Backwards compatibility. | Journeys without `spec.bindings.websocket` continue to behave exactly as before. |
| NFR-017-02 | Preserve engine transport-agnosticism. | Architecture. | All WebSocket semantics must be expressed in terms of existing logical operations (`start`, `submitStep`, `getStatus`, `getOutcome`). |
| NFR-017-03 | Avoid over-specifying message envelopes. | Flexibility. | Details of JSON frames live in a separate WebSocket API reference; DSL only captures binding configuration. |

## Open Questions (for future slices)

This feature intentionally keeps WebSocket message envelopes and server behaviour high-level. If future slices need more precision, they should introduce new questions in `open-questions.md`, for example:
- How should reconnection and resume semantics be expressed (if at all) at the DSL or API layer?
- Should journeys be able to opt into or out of status/outcome streaming over WebSockets explicitly?

