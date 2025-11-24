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
| Q-001 | ivan | How should JourneyForge support modular/extensible “plugin” building blocks (new DSL tasks/blocks implemented as separate Java artifacts) while preserving the spec-first DSL model and engine simplicity? | A) Strictly curated extension points with versioned contracts and limited surface for third-party DSL blocks; B) More open plugin SDK allowing arbitrary new block types with sandboxing/validation; C) No DSL-level plugins, only engine integration patterns (external services, transforms) | Open | 2025-11-24 | Initial exploration of plugin/SDK model for new DSL vocabulary and modular interpreters. |
