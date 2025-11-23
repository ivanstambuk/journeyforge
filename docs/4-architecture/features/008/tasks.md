# Feature 008 – Tasks – Scheduled journeys (`task.kind: schedule`)

- [ ] T-008-01: Draft ADR-0017 for scheduled journeys and `task.kind: schedule` semantics, capturing the journey-as-job scheduling design decisions for Feature 008. 
- [ ] T-008-02: Update `docs/3-reference/dsl.md` with the `task.kind: schedule` shape, semantics, and validation rules. 
- [ ] T-008-03: Extend the model/parser to support `task.kind: schedule` and the `schedule` block. 
- [ ] T-008-04: Implement an in-memory schedule binding store and scheduler in the runtime engine, with evolving-context behaviour. 
- [ ] T-008-05: Add engine tests for schedule creation, duplicate handling via `onExisting`, and scheduled runs up to `maxRuns`. 
- [ ] T-008-06: Add an example journey demonstrating interactive configuration plus a scheduled path, and update docs to reference it. 

Notes:
- Prefer many small tasks over large ones.
- Keep tests before code.
