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
| Q-016 | LLM | How should journey example diagrams be generated, stored, and referenced so that VS Code Markdown previews and static docs always have up-to-date images? | A) Generate PNG diagrams from PlantUML sources under tools/PlantUML and commit them to docs/3-reference/examples/journeys/diagrams; B) Generate diagrams on the fly as part of a docs build without committing PNGs; C) Rely on an external docs site that renders PlantUML directly from .puml files | Open | 2025-11-22 | Seeded while fixing missing diagrams for b2b-purchase-order and aligning journey documentation with the repo’s PlantUML tooling. |
| Q-021 | LLM | How should we partition and template JourneyForge examples between technical feature building blocks and higher-level business journeys so that future examples are consistent and easy to generate? | A) Keep all specs in docs/3-reference/examples but split human docs into separate technical-pattern and business-journey catalogs under docs/2-how-to; B) Move all business-journey specs and docs into a dedicated docs/3-reference/business-journeys area while keeping technical examples in docs/3-reference/examples; C) Collapse to a single unified examples catalog with tags for “technical” vs “business” and derive both indexes from it. | Open | 2025-11-23 | Seeded while analysing current examples in docs/2-how-to and docs/3-reference and planning templates for future technical and business examples. |
| Q-022 | LLM | How should we design and enforce a DSL-aware YAML linter for .journey.yaml files that aligns with docs/3-reference/dsl.md and ADRs while providing both IDE feedback and CI enforcement? | A) Hybrid approach: JSON Schema + YAML LS/Spectral for editor feedback plus a Java-based `journeyforge-lint` CLI in this repo for deeper graph/semantics; B) Rely solely on external schema/Spectral tooling for both IDE and CI; C) Build only an internal Java linter that handles both structure and semantics without external schema tooling. | Open | 2025-11-23 | Seeded while exploring options for JourneyForge DSL linting across editors and CI and balancing external tooling with repo-internal validation. |
| Q-023 | LLM | Which existing business journey should we extend first with a timer-based “X OR timeout after N” pattern, and what should the primary semantics be (hard error vs SLA/escalation vs soft outcome) for that example? | A) Extend `high-value-transfer` with a settlement timeout in parallel with the settlement webhook and treat expiry as a dedicated failure outcome; B) Extend `subscription-lifecycle` with a post-cancel grace period timer in parallel with reactivation so that cancellation becomes permanent after N days; C) Extend `travel-booking-bundle` with a post-trip feedback or compensation timer while keeping the main outcome Succeeded. | Open | 2025-11-23 | Seeded while planning timer extensions for existing journeys to illustrate different timeout semantics (hard failure vs SLA vs soft follow-up) using a single primary example. |

## Question Details

### Q-020 – Data pipeline – ETL for daily orders
- Context: We want a business journey that models a daily orders ETL pipeline (extract, transform, load) as a long-lived JourneyForge journey, reusing the existing transform-pipeline example and business-journey patterns while keeping the external API surface small and focused.
- Question: How should we structure the daily orders ETL journey across extract, transform, and load phases (including optional upstream file drops or downstream completion notifications) while keeping it aligned with the “single long-lived journey with a small set of steps” pattern?
- Options:
  - A) Simple single-run daily ETL journey with `start` triggering extract+transform+load as mostly internal `task`/`transform` states, plus `getStatus`/`getResult` and an optional `retriggerDay` step to re-run a specific date.
  - B) Long-lived ETL journey that models both scheduled runs and manual re-runs within the same instance, including a `wait` state for upstream file availability and optional webhook for downstream completion, with explicit history in the outcome.
  - C) Split journeys for “daily extract+transform+load” and “ad-hoc reprocessing” that share backend APIs but remain separate journey definitions, each with its own start/status/result surface.
- Resolution: Pending – to be decided as we add the `data-pipeline-daily-orders` journey example and its per-journey OpenAPI and Arazzo specs.
