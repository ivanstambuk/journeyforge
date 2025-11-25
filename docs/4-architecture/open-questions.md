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
| Q-022 | LLM | How should cache operations (`cacheGet` / `cachePut`) be modelled in the plugin era – as plugin-backed task kinds (with explicit versions) or as special-case core behaviours? | A: Treat `cacheGet` / `cachePut` as normal task plugins (`task.kind: cacheGet:v1` / `cachePut:v1`) sharing a common cache resource model; B: Single `cache:v1` plugin with an `operation: get|put`; C: Keep them as non-plugin core behaviours. | Open | 2025-11-25 | Raised from IDE prompt “cacheget/cacheput as plugins?”. |
| Q-023 | LLM | Where should cache instances be configured – per-journey (`spec.resources.caches`), globally in platform config, or via a hybrid of both? | A: Global logical caches in engine config only, journeys just use `cacheRef` ids; B: Hybrid – global cache registry with optional per-journey overrides/hints in `spec.resources.caches`; C: Spec-local caches only as currently sketched in DSL §15. | Open | 2025-11-25 | Raised from discussion about sharing caches across journeys and mapping environments (in-memory in lower envs, Redis/Cosmos in prod) without exposing providers in DSL. |
