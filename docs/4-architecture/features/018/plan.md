# Feature 018 – gRPC Binding for APIs – Plan

Status: Draft | Last updated: 2025-11-25

## Slice 1 – DSL & spec design (this work)
- [x] Define `spec.bindings.grpc` for `kind: Api` in `docs/3-reference/dsl.md`.
- [x] Capture gRPC binding semantics in `docs/4-architecture/features/018/spec.md`.
- [x] Ensure ADR-0029 mentions gRPC as a binding category and points to this feature.

## Slice 2 – gRPC API reference
- [ ] Author a dedicated Journeys gRPC API reference:
  - Service/method naming conventions.
  - Proto message shapes for Api requests/responses.
  - Mapping between proto messages and DSL `spec.input.schema` / `spec.output.schema`.
  - Error/status mapping between Problem/HTTP status and gRPC status codes.

## Slice 3 – Engine binding implementation (future)
- [ ] Design a gRPC binding SPI that maps unary calls onto the existing `kind: Api` engine entry points.
- [ ] Implement a minimal gRPC server binding in the runtime, reusing existing security and observability contracts.
- [ ] Add examples that expose selected `kind: Api` specs via both HTTP and gRPC (dual binding).

