# Feature 017 – WebSocket Binding for Journeys – Plan

Status: Draft | Last updated: 2025-11-25

## Increment 1 – DSL & API design (this increment)
- [x] Define `spec.bindings.websocket` shape in `docs/3-reference/dsl.md`.
- [x] Extend ADR-0029 with a WebSocket binding sketch and relationship to HTTP bindings.
- [x] Capture feature-level specification in `docs/4-architecture/features/017/spec.md`.

## Increment 2 – WebSocket Journeys API reference
- [ ] Introduce a dedicated Journeys WebSocket API reference doc:
  - Message envelopes for start, step submissions, status, and outcomes.
  - Error envelopes and mapping to the canonical error model.
- [ ] Align message shapes with existing `JourneyStatus` / `JourneyOutcome` schemas where practical.

## Increment 3 – Engine implementation (future)
- [ ] Design engine-side WebSocket binding SPI that maps frames onto logical operations (`start`, `submitStep`).
- [ ] Implement a minimal WebSocket server binding in the runtime, reusing existing HTTP security for the handshake.
- [ ] Add observability hooks consistent with ADR‑0025.
