# ADR-0029 – Inbound Bindings and `spec.bindings.http`

Date: 2025-11-25 | Status: Proposed

## Context

JourneyForge previously defined HTTP-centric behaviour for journeys and APIs directly in the DSL without a generic bindings concept:
- `kind: Journey` is exposed via the Journeys HTTP API (`/api/v1/journeys/{journeyName}/start`, `/journeys/{journeyId}`, `/steps/{stepId}`, etc.).
- `kind: Api` described its synchronous HTTP endpoint surface via inline route and status-mapping blocks.
- Inbound HTTP metadata was projected into `context` via an inline HTTP binding block.

At the same time, the project wants:
- A minimal, transport-agnostic engine core that reasons about journeys, states, context, and outcomes without being tied to HTTP; and
- A clear plugin model for inbound bindings so that HTTP is the first binding, but others (for example WebSocket, gRPC, CLI, message/queue consumers) can be added later without changing the core engine.

The pre-ADR layout made HTTP a special case baked into the DSL surface:
- There was no generic concept of “bindings” that can host multiple inbound transports.
- HTTP binding configuration was scattered across:
  - Per-spec blocks for header/query projection into `context` and header passthrough.
  - Inline route/status-mapping blocks for synchronous `kind: Api` endpoints.
- Other potential bindings (WebSocket, gRPC, CLI, queues) have no first-class home in the DSL; they would have to be configured entirely outside the spec or via ad hoc fields.

We need a coherent way to:
- Group HTTP-specific inbound binding configuration under a single, extensible root.
- Make room for future bindings (WebSocket, gRPC, CLI, queue) without forcing a new DSL construct each time.
- Keep journeys and APIs behaviourally binding-agnostic: control flow, states, and context semantics should not depend on which bindings are attached.

## Decision

We introduce a generic `spec.bindings` block on journey/API definitions and move HTTP inbound binding configuration under `spec.bindings.http`.

### 1. `spec.bindings` – top-level

All journey and API definitions may declare an optional `spec.bindings` block:

```yaml
spec:
  bindings:
    http:   # HTTP inbound binding (this ADR)
      ...
    # websocket: ...  # reserved for future features
    # grpc: ...       # reserved for future features
    # cli: ...        # reserved for future features
```

Rules:
- `spec.bindings` is optional for both `kind: Journey` and `kind: Api`.
- Keys under `spec.bindings` are binding identifiers; in this version only `http` is defined.
- Future features MAY introduce additional binding ids (for example `websocket`, `grpc`, `cli`, `queue`) with their own binding-specific configuration blocks.
- The engine core remains transport-agnostic:
  - It exposes a logical API for `start`, `submitStep`, `getStatus`, and `getOutcome`.
  - Binding implementations (HTTP or others) adapt their transport to this logical API.

### 2. HTTP binding – `spec.bindings.http`

The HTTP binding groups all inbound HTTP configuration that was previously scattered across dedicated HTTP binding blocks and inline route/status-mapping blocks for `kind: Api`.

#### 2.1 Shape

```yaml
spec:
  bindings:
    http:
      route:                  # kind: Api only
        path: <string>        # e.g. /apis/get-user-public; defaults to /apis/{metadata.name}
        method: <string>      # initial version: POST only

      start:                  # start request bindings
        headersToContext:
          <Header-Name>: <contextField>
        headersPassthrough:
          - from: <Header-Name>
            to: <Header-Name>
        queryToContext:
          <paramName>: <contextField>

      steps:                  # step request bindings, keyed by external-input state id
        <stepId>:
          headersToContext:
            <Header-Name>: <contextField>
          headersPassthrough:
            - from: <Header-Name>
              to: <Header-Name>
          queryToContext:
            <paramName>: <contextField>

      apiResponses:           # kind: Api only – HTTP status mapping rules
        rules:
          - when:
              phase: SUCCEEDED | FAILED
              errorType: <string>   # optional; ProblemDetails.type
              predicate:            # optional expression over outcome/context/error
                lang: <engineId>
                expr: <expr>
            status: <int>           # HTTP status code
            # or:
            # statusExpr:
            #   lang: <engineId>
            #   expr: <expr>        # evaluates to HTTP status code
        default:
          SUCCEEDED: 200            # optional; per-phase defaults
          FAILED: fromProblemStatus
```

Constraints:
- `spec.bindings.http` is valid for both `kind: Journey` and `kind: Api`, with the following additional rules:
  - `route` and `apiResponses` are only valid for `kind: Api`; they MUST be rejected on `kind: Journey`.
  - `start` and `steps` are valid for both `kind: Journey` and `kind: Api`.
- `steps.<stepId>` under `spec.bindings.http.steps` MUST refer to external-input states (`wait` / `webhook`).

#### 2.2 Semantics (high level)

- Start and step bindings:
- `start` applies when an HTTP client invokes the start endpoint for a journey (`POST /api/v1/journeys/{journeyName}/start`) or API (`POST /api/v1/apis/{apiName}` or `spec.bindings.http.route.path`).
  - `steps.<stepId>` applies when an HTTP client submits to the HTTP step endpoint for an external-input state (`POST /journeys/{journeyId}/steps/{stepId}`).
  - `headersToContext`, `headersPassthrough`, and `queryToContext` behave as described in the HTTP binding section of the DSL reference; only the configuration location changes.
- HTTP surface for journeys:
  - The canonical Journeys API surface (`/api/v1/journeys/{journeyName}/start`, `/journeys/{journeyId}`, `/journeys/{journeyId}/result`, `/journeys/{journeyId}/steps/{stepId}`) remains as previously defined.
  - `spec.bindings.http` controls how these HTTP calls project metadata into `context` and how headers are propagated. Inbound authentication is expressed via task plugins in the state graph (see DSL reference §18).
- HTTP surface for APIs:
  - `spec.bindings.http.route` replaces the previous `spec.route` block for `kind: Api` and controls path/method under the `/api/v1` base.
  - `spec.bindings.http.apiResponses` replaces the previous `spec.apiResponses` block and continues to control HTTP status mapping for `kind: Api` without changing the error envelope shape.

### 3. WebSocket binding – sketch for future work

This ADR intentionally scopes the normative DSL changes to HTTP. However, the same `spec.bindings` mechanism can host a WebSocket binding in a future feature. The WebSocket binding would reuse the same logical engine operations (start, submit step, observe status/outcome) over a persistent, bidirectional channel.

Conceptual (non-normative) shape:

```yaml
spec:
  bindings:
    websocket:
      endpoint:
        path: /ws/journeys            # optional; defaults to an engine-level base path
        subprotocol: journeyforge.v1  # optional WebSocket subprotocol identifier

      start:
        messageType: startJourney     # logical message type for starts

      steps:
        <stepId>:
          messageType: submitStep     # logical message type for step submissions
```

Conceptual semantics:
- Connection:
  - Clients open a WebSocket connection to the configured `endpoint.path` (or a platform default) and negotiate the `subprotocol` when present.
  - Authentication/authorisation for the handshake is handled by the surrounding platform (gateway/ingress/service mesh) and is outside the DSL; the WebSocket binding assumes an authenticated, authorised connection.
- Incoming messages:
  - `start` messages carry a journey/API identifier and a JSON payload; the binding maps them to the same logical “start” operation as the HTTP binding.
  - `steps.<stepId>.messageType` defines the logical message type used when submitting external-input payloads for a given step id; payloads are mapped to the same logical “submit step” operation as the HTTP binding.
- Outgoing messages:
  - The binding can emit `status` and `outcome` messages over the same connection when journey instances change state or complete, instead of requiring HTTP polling; the exact envelope shapes are a future design concern.

This sketch is provided to illustrate how additional bindings fit under `spec.bindings` without changing the core engine contract. A future feature/ADR will define the WebSocket binding shape and semantics normatively once concrete use cases are prioritised.

## Consequences

Positive:
- **Cleaner extensibility for inbound bindings**
  - HTTP is now one binding under `spec.bindings.http`; future bindings (WebSocket, gRPC, CLI, queues) have a clear home under `spec.bindings.<id>`.
  - The engine core can stay transport-agnostic and reason only in terms of runs, steps, and outcomes, while binding plugins adapt external transports to the core API.
- **Consolidated HTTP configuration**
  - HTTP route, metadata projection, and `kind: Api` HTTP status mapping live under a single, well-scoped configuration subtree (`spec.bindings.http`).
  - Docs and tools can refer to “HTTP binding” as a single concept.
- **Room for per-binding evolution**
  - Future features can extend `spec.bindings.http` (for example additional header/query mapping options) without touching other parts of the DSL.

Negative / trade-offs:
- **DSL refactor required**
  - Existing examples, how-tos, and feature specs must be updated to the new `spec.bindings.http` locations as this ADR is applied.
  - Authors familiar with the previous locations will need to adjust to `spec.bindings.http`.
- **Binding-specific surface still HTTP-centric**
  - In this ADR, only HTTP is defined under `spec.bindings.*`; other bindings remain future work and will require their own ADRs and DSL additions.

Follow-ups:
- Update `docs/3-reference/dsl.md` to:
  - Introduce `spec.bindings` in the top-level shape.
  - Move HTTP binding content under `spec.bindings.http`.
  - Clarify that `route`/`apiResponses` are `kind: Api`-only and that HTTP binding is optional but recommended for HTTP-exposed definitions.
- Update how-to docs and examples to use `spec.bindings.http.*` instead of the deprecated shapes.
- When additional bindings (for example WebSocket or gRPC) are introduced, add focused ADRs that define their `spec.bindings.<id>` shapes and semantics.
