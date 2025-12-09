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
| Q-008 | Human+Agent | Feature 004 – what is the default behaviour for `spec.cookies.returnToClient` when it is absent or when `mode` is omitted? | **A (recommended)**: Treat `spec.cookies.returnToClient` as optional; when the block is absent, engines MUST NOT return any cookies to the client, and when present `mode` is required and must be set explicitly; **B**: Allow implicit defaults – for example, when `spec.cookies.returnToClient` is present but `mode` is omitted, treat it as `mode: filtered` or `mode: allFromAllowedDomains`, so authors get cookie return behaviour without always specifying a mode; **C**: Require `spec.cookies.returnToClient` (with explicit `mode`) whenever a cookie jar is configured, so there is no jar-only configuration. | Open | 2025-12-09 | Governs safety defaults for returning cookies to clients; affects how surprising cookie emission is for specs that enable jars but omit `returnToClient` details. |
