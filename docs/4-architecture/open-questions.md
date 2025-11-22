# Open Questions

Status: Active only (do not list resolved entries)

| ID | Owner | Question | Options (A preferred) | Status | Asked | Notes |
|----|-------|----------|------------------------|--------|-------|-------|
| Q-002 | LLM | How rich should DSL-level `choice` predicates be beyond raw DataWeave? | A) DW-only with sugar; B) Lightweight predicate DSL; C) Keep DW-only | Closed | 2025-11-22 | Resolved 2025-11-22 – keep DataWeave-only predicates; clarify internal-error semantics and validation; no new predicate syntax. |
| Q-003 | LLM | What YAML style and enforcement should we adopt for journey specs and other YAML artefacts? | A) 2-space indent, no tabs, quoting guidance in spec + Spotless enforcement for whitespace; B) Prettier-based YAML formatting for full structural/quoting normalisation; C) Treat indent/quoting as guidance only | Closed | 2025-11-22 | Resolved 2025-11-22 – adopt 2-space indent, no tabs, and quoting guidance for all YAML; enforce whitespace and “no tabs” via Spotless, with the option to introduce a dedicated YAML formatter later if needed. |

## Question Details

### Q-002 – How rich should DSL-level `choice` predicates be beyond raw DataWeave?
- Context: `choice` currently requires full DataWeave predicates; `spec-format.md` calls out “Richer expressions for `choice`” as future work.
- Question: Should the DSL introduce additional predicate surfaces (syntax sugar or a small expression language) on top of DataWeave, and if so, how far should it go?
- Options:
  - A) Keep DataWeave as the only language but add small, well-scoped syntax sugar (e.g., simple comparisons or `in` operators) that compiles to DW.
  - B) Introduce a lightweight, journey-specific predicate DSL that still compiles to DataWeave but hides DW syntax for most authors.
  - C) Keep the current “DataWeave-only” surface and treat “richer expressions” as documentation/examples only (no new syntax).
 - Resolution (2025-11-22): For Feature 001, keep `choice` predicates as DataWeave-only without additional predicate syntax. Clarify that non-boolean results are a compile-time validation error, and that DataWeave runtime errors in predicates are treated as internal engine errors with a generic internal error identifier and HTTP 500 surfaced to callers. Tooling should focus on stronger static validation rather than expanding the predicate language.

### Q-003 – YAML style rules and enforcement
- Context: `spec-format.md` currently calls out “YAML style rules (indent=2, no tabs, quoted scalars policy)” as future work, and the project relies on YAML as the primary authored form for journeys, examples, and OpenAPI files.
- Question: How strict and tool-enforced should the YAML style be across the repo, and which formatter (if any) should be treated as the canonical style authority?
- Options:
  - A) Define a clear house style in `spec-format.md` (2-space indent, no tabs, safe quoting rules, block scalars for DataWeave) and enforce whitespace/no-tabs via Spotless for all YAML/JSON/Markdown, keeping formatter choice minimal for now.
  - B) Integrate a dedicated YAML formatter (for example, Prettier via Spotless) to fully normalise indentation, quoting, and wrapping for all YAML artefacts, accepting a stronger tooling dependency.
  - C) Document YAML style as guidance only, relying on schema validation and canonical JSON snapshots for correctness, with no automated enforcement beyond basic whitespace trimming.
- Resolution (2025-11-22): Adopt Option A for Feature 001 – YAML style is normatively defined in `spec-format.md` (2-space indent, no tabs, conservative quoting, block scalars for DataWeave), and Spotless enforces whitespace + “no tabs” on all YAML-related files. The team may revisit Option B later if a stronger YAML formatter becomes desirable.
