# Feature 008 – Plan – Scheduled journeys (`task.kind: schedule:v1`)

Status: Draft | Last updated: 2025-11-22

## Increments
- [ ] T-008-01: Finalise ADR-0017 for scheduled journeys and `task.kind: schedule:v1` semantics (based on the earlier journey-as-job scheduling design discussion and this feature spec). (≤90 min)
- [ ] T-008-02: Extend DSL model/parser with `task.kind: schedule:v1` and validation rules (including non-interactive scheduled path check). (≤90 min)
- [ ] T-008-03: Implement engine-side schedule binding model and in-memory scheduler to trigger journeys at `startAt`/`interval` with evolving context. (≤90 min)
- [ ] T-008-04: Add tests covering schedule creation, duplicate handling (`onExisting`), and run behaviour up to `maxRuns`. (≤90 min)
- [ ] T-008-05: Update reference docs (DSL §5) and at least one example journey to illustrate scheduling. (≤90 min)

## Risks & Mitigations
- Misuse of scheduling for high-frequency jobs → enforce coarse-grained minimum interval and document limits in ADR-0017.
- Confusion between interactive and scheduled paths → keep `task.kind: schedule:v1` clearly non-interactive and document patterns in examples.
- Implementation complexity in scheduler → start with in-memory, single-node scheduler and document limitations; defer distributed/HA scheduler to a later feature.

## Validation
- DSL: specs with and without `task.kind: schedule:v1` validate cleanly; invalid schedule blocks produce clear errors.
- Engine: end-to-end tests show that scheduled runs start at the correct state, preserve evolving context, and stop after `maxRuns`.
- Docs: DSL reference and examples accurately describe the scheduling pattern; ADR-0017 is accepted and any temporary scheduling design notes are cleaned up.
