# Feature 020 – CLI/Batch Binding – Plan

Status: Draft | Last updated: 2025-11-25

## Increment 1 – DSL & spec design (this increment)
- [x] Introduce `spec.bindings.cli` in `docs/3-reference/dsl.md`.
- [x] Capture CLI/batch binding semantics in `docs/4-architecture/features/020/spec.md`.

## Increment 2 – CLI documentation & tooling
- [ ] Update CLI docs to reference `spec.bindings.cli` and clarify how journeys/APIs are invoked from the command line.
- [ ] Ensure CLI tooling can surface which definitions declare CLI bindings (for example listing runnable journeys/APIs).

## Increment 3 – Job orchestration guidance (future)
- [ ] Add ops runbooks describing how to wire CLI invocations into schedulers (cron, CI, Kubernetes `CronJob`, etc.) using the conceptual contract defined in this feature.
