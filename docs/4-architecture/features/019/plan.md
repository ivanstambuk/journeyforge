# Feature 019 – Queue/Message Binding for Journeys – Plan

Status: Draft | Last updated: 2025-11-25

## Increment 1 – DSL & spec design (this increment)
- [x] Introduce `spec.bindings.queue` for `kind: Journey` in `docs/3-reference/dsl.md`.
- [x] Capture provider-neutral semantics in `docs/4-architecture/features/019/spec.md`.

## Increment 2 – Provider mapping & configuration
- [ ] Define engine/platform configuration model for mapping logical `channel` ids to provider-specific resources (Kafka topics, SQS queues, NATS subjects, etc.).
- [ ] Document how consumer groups, retries, and dead-letter handling are configured per provider, outside the DSL.

## Increment 3 – Engine binding implementation (future)
- [ ] Design a queue binding SPI that maps consumed messages onto logical `start` and `submitStep` operations.
- [ ] Implement at least one provider-backed binding (for example Kafka) and add examples.
- [ ] Add observability hooks consistent with ADR‑0025 (metrics for consumed/failed/dead-lettered messages).
