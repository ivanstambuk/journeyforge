# Feature 001 – Plan

Status: Draft | Last updated: 2025-11-23

## Increments
- [ ] T-001-01: Define model types (Workflow, State variants, HttpCall spec) in `journeyforge-model`.
- [ ] T-001-02: Implement YAML/JSON parser and validation in `journeyforge-parser`.
- [ ] T-001-03: Add pluggable HTTP client (minimal, Java 25 `HttpClient`) in `journeyforge-connectors-http`.
- [ ] T-001-04: Implement synchronous engine executor in `journeyforge-runtime-core` (no persistence).
- [ ] T-001-05: Provide a temporary runner (in `app/`) that loads a file + optional initial context and prints the outcome.
- [ ] T-001-06: Tests: parser validation cases; engine happy/failure paths using a stub HTTP client.
- [ ] T-001-07: Docs: example journey under `docs/3-reference/` and README pointers.
 - [ ] T-001-08: Finalise ADR-0017 for scheduled journeys and `task.kind: schedule:v1` semantics (based on the earlier journey-as-job scheduling design discussion and the merged Feature 001 spec). (≤90 min)
 - [ ] T-001-09: Extend DSL model/parser with `task.kind: schedule:v1` and validation rules (including non-interactive scheduled path check). (≤90 min)
 - [ ] T-001-10: Implement engine-side schedule binding model and in-memory scheduler to trigger journeys at `startAt`/`interval` with evolving context. (≤90 min)
 - [ ] T-001-11: Add tests covering schedule creation, duplicate handling (`onExisting`), and run behaviour up to `maxRuns`. (≤90 min)
 - [ ] T-001-12: Update reference docs (DSL §5) and at least one example journey to illustrate scheduling. (≤90 min)

## Risks & Mitigations
- Minimal expression evaluation for `choice` could expand quickly → constrain to `equals` on a `var` dot‑path (documented in spec) and add open question for richer expressions.
- Network in CI would flake tests → use stubbed HTTP client interface injected into the engine.
- Misuse of scheduling for high-frequency jobs → enforce coarse-grained minimum interval and document limits in ADR-0017; keep initial scheduler in-memory and single-node.
- Confusion between interactive and scheduled paths → keep `task.kind: schedule:v1` clearly non-interactive and document patterns in examples; validate non-interactive scheduled paths statically where possible.
- Implementation complexity in scheduler → start with in-memory, single-node scheduler and document limitations; defer distributed/HA scheduler to a later feature slice (for example under persistence/admin features).

## Validation
- `./gradlew spotlessApply check` green.
- End‑to‑end: run sample journey file that performs a GET against a stub and returns `succeed`.
- DSL: specs with and without `task.kind: schedule:v1` validate cleanly; invalid schedule blocks produce clear errors.
- Engine: end-to-end tests show that scheduled runs start at the correct state, preserve evolving context, and stop after `maxRuns`, and that `schedule.onExisting` behaviours (`fail`, `upsert`, `addAnother`) work as specified.
- Docs: DSL reference and examples accurately describe the scheduling pattern; ADR-0017 is accepted and any temporary scheduling design notes are cleaned up.
