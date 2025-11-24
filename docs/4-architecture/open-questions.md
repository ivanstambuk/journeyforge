# Open Questions

Use this file **only** to capture currently open medium‑ or high‑impact architecture/design questions before starting work. It is a **temporary scratchpad for open questions**, not a permanent record of decisions.

Hard rules:
- This table may only contain rows whose status is `Open`.
- When a question is resolved, its outcome **must be captured in an ADR, spec, or other authoritative doc**, and the corresponding row **must be deleted** from this table.
- Resolved questions **must not be archived** in the “Question Details” section; any details for a question must be removed when its row is removed.
- Question IDs (for example `Q-016`) are **local to this file and chat transcripts** and **must never be referenced from ADRs, specs, examples, or README/docs**. Authoritative documents must stand on their own without pointing back here.
- This file is **never** a source of truth; once a question is resolved, this file should contain no remaining record of it.

<!-- Add new rows below with Status set to Open only. Remove the row once resolved and documented elsewhere. -->

| ID | Owner | Question | Options (A preferred) | Status | Asked | Notes |
|----|-------|----------|------------------------|--------|-------|-------|
| Q-005 | ivan | Should `JourneyOutcome` support projecting selected business status fields (for example `status`, `overallStatus`) to the top level alongside `phase`, similar to how start/step responses can project fields into `JourneyStatus`? | A) Keep business status strictly under `output` and treat `JourneyOutcome` as a stable envelope with no top-level business status projection; B) Allow optional, derived top-level status fields on `JourneyOutcome` that are deterministically sourced from `output` (for example `status`, `overallStatus`) without changing the canonical `output` contract; C) Generalise the projection model so journeys can declare which `output` properties are also exposed as top-level fields on `JourneyOutcome` (with clear constraints to avoid envelope pollution). | Open | 2025-11-24 | Business status surfacing vs envelope purity; alignment with start/step response projection and dashboard/query use cases. |
