# Feature 018 – gRPC Binding for APIs

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/018/plan.md` |
| Linked tasks | `docs/4-architecture/features/018/tasks.md` |
| Roadmap entry | #010 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md` (none currently open for this feature), encode resolved answers directly in the Requirements/NFR/Behaviour/Telemetry sections below, and use ADRs under `docs/6-decisions/` (in particular ADR‑0029) for architecturally significant clarifications.

## Overview

Introduce a gRPC inbound binding for `kind: Api` definitions via `spec.bindings.grpc`. The binding provides a spec-visible way to expose synchronous APIs as unary gRPC methods while reusing the same logical engine semantics as HTTP-bound `kind: Api`:
- One request creates an ephemeral context, executes the state graph from `spec.start` to a terminal state, and returns a single response.

This feature is **DSL/API design only**; engine implementation of gRPC servers and `.proto` generation will be delivered in later slices.

Primary references:
- DSL reference: `docs/3-reference/dsl.md` (section 17 – Inbound Bindings).
- Inbound bindings ADR: `docs/6-decisions/ADR-0029-inbound-bindings-and-spec-bindings-http.md`.
- API endpoints ADR: `docs/6-decisions/ADR-0004-api-endpoints-kind-api.md`.

## Goals

- Define a minimal DSL surface for gRPC bindings:
  - `spec.bindings.grpc.service` – fully-qualified service name.
  - `spec.bindings.grpc.method` – unary method name for this Api.
- Keep the engine core transport-agnostic:
  - gRPC binding must map onto the same logical `kind: Api` semantics as HTTP binding (`spec.bindings.http`).
- Sketch the request/response mapping model:
  - How gRPC request messages are mapped to initial `context` via `spec.input.schema`.
  - How terminal results (`spec.output.schema`, `spec.errors`, `spec.bindings.http.apiResponses`) are mapped back to gRPC responses and status codes.

## Non-Goals

- No engine implementation of gRPC servers, reflection, or load balancing in this feature.
- No DSL for defining `.proto` schemas; proto definitions are an export/view over the existing DSL, not a source of truth.
- No new error model: gRPC bindings reuse the existing `spec.errors` + Problem-Details semantics as the canonical internal model.
- No gRPC binding for `kind: Journey` in this feature; journeys remain HTTP/WebSocket driven.

## DSL (normative)

### 1. `spec.bindings.grpc` for `kind: Api`

For API definitions, authors MAY declare a gRPC binding:

```yaml
apiVersion: v1
kind: Api
metadata:
  name: get-user-public
  version: 0.1.0
spec:
  bindings:
    grpc:
      service: journeys.api.UserApi    # fully-qualified gRPC service
      method: GetUserPublic            # unary RPC method name
```

Constraints:
- `spec.bindings.grpc`:
  - MUST NOT be present for `kind: Journey`.
  - MAY be present for `kind: Api` alongside `spec.bindings.http`.
- `service`:
  - When present, MUST be a non-empty string representing a valid gRPC service identifier.
  - When absent, tooling MAY derive a default from `metadata.name` using a documented naming convention (for example a common `journeys.api` prefix).
- `method`:
  - When present, MUST be a non-empty string representing a valid gRPC method identifier.
  - When absent, tooling MAY derive a default from `metadata.name`.

### 2. Conceptual request/response mapping

This feature does not fix a single `.proto` schema, but it defines the conceptual mapping:

- Request:
  - Each unary call to `<service>/<method>` is conceptually equivalent to a single HTTP call to the Api’s HTTP endpoint.
  - The gRPC request message is mapped to the initial `context` for the Api according to:
    - The fields defined by `spec.input.schema`, and
    - The gRPC API reference for this binding (which specifies how proto fields map to JSON properties).
  - The resulting `context` MUST be validated against `spec.input.schema` when present; invalid inputs fail according to the existing error model for `kind: Api`.
- Execution:
  - From the engine’s perspective, a gRPC invocation is a normal `kind: Api` run:
    - Starts at `spec.start`.
    - Executes until a terminal `succeed` or `fail` state is reached, or a timeout occurs per `spec.execution`.
    - MUST NOT require external input (`wait`/`webhook`).
- Response:
  - Successful runs (`terminal succeed`) map to gRPC responses whose payload is derived from the final `context` and `spec.output.schema`, mirroring HTTP behaviour.
  - Failed runs (`terminal fail` or timeouts) map to gRPC error status codes and response messages derived from:
    - The canonical Problem object (via `spec.errors`), and
    - Platform-defined mapping between Problem/HTTP status and gRPC status codes.

Status mapping:
- This feature does not introduce a new status-mapping block for gRPC:
  - APIs SHOULD continue to use `spec.bindings.http.apiResponses` to express their desired HTTP status semantics.
  - Platform-level configuration and the gRPC API reference define how HTTP statuses and Problem types are translated into gRPC status codes.

## Behaviour (conceptual)

- A single gRPC call to `<service>/<method>` starts and completes an Api run.
- There is no streaming or server push in this feature; only unary RPCs are in scope.
- gRPC metadata (headers/trailers) may carry additional information (for example correlation ids or error details) but the authoritative business payloads and errors are still shaped by `spec.output.schema` and `spec.errors`.

## Non-Functional Requirements

| ID | Requirement | Driver | Notes |
|----|-------------|--------|-------|
| NFR-018-01 | Keep DSL additive and backwards compatible. | Stability. | APIs without `spec.bindings.grpc` continue to be HTTP-only. |
| NFR-018-02 | Preserve a single source of truth for contracts. | Spec-first. | `.proto` definitions must be generated from DSL (`spec.input.schema` / `spec.output.schema`), not hand-authored. |
| NFR-018-03 | Avoid binding DSL to a specific gRPC deployment model. | Flexibility. | Decisions about servers, load balancing, and service discovery remain in platform config and are out of scope for this feature. |

