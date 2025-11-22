# Open Questions

Status: Active only (do not list resolved entries)

| ID | Owner | Question | Options (A preferred) | Status | Asked | Notes |
|----|-------|----------|------------------------|--------|-------|-------|
| Q-002 | LLM | How rich should DSL-level `choice` predicates be beyond raw DataWeave? | A) DW-only with sugar; B) Lightweight predicate DSL; C) Keep DW-only | Closed | 2025-11-22 | Resolved 2025-11-22 – keep DataWeave-only predicates; clarify internal-error semantics and validation; no new predicate syntax. |
| Q-003 | LLM | What YAML style and enforcement should we adopt for journey specs and other YAML artefacts? | A) 2-space indent, no tabs, quoting guidance in spec + Spotless enforcement for whitespace; B) Prettier-based YAML formatting for full structural/quoting normalisation; C) Treat indent/quoting as guidance only | Closed | 2025-11-22 | Resolved 2025-11-22 – adopt 2-space indent, no tabs, and quoting guidance for all YAML; enforce whitespace and “no tabs” via Spotless, with the option to introduce a dedicated YAML formatter later if needed. |
| Q-004 | LLM | How should the DSL reference separate normative language semantics from engine implementation status and where should status be tracked? | A) Keep DSL docs purely normative and track implementation status in feature/status docs; B) Keep a small, structured “Implementation status” appendix in the DSL doc; C) Maintain a separate DSL feature matrix doc and link from the DSL reference | Closed | 2025-11-22 | Resolved 2025-11-22 – treat `docs/3-reference/dsl.md` as strictly normative; track implementation status per feature (for example Feature 001 under `docs/4-architecture/features/001/`) and use a hard note in the DSL reference to point readers at feature docs for engine readiness. |
| Q-005 | LLM | How should OAS and Arazzo linting be integrated into the toolchain (Spectral config, scope, and CI wiring)? | A) Spectral-driven linting via Gradle tasks for both OAS and Arazzo; B) Local Node tooling under `docs/` with `npm` scripts only; C) Minimal standalone Spectral invocation script without Gradle integration | Closed | 2025-11-22 | Resolved 2025-11-22 – Adopt Option A with two Gradle tasks: `lintOas` (lint all `docs/3-reference/**/*.openapi.yaml` using `.spectral.yaml` extending `spectral:oas`) and `lintArazzo` (lint all `docs/3-reference/examples/arazzo/*.arazzo.yaml` using `.spectral-arazzo.yaml` extending the built-in `spectral:arazzo` ruleset plus a hard array-type check for `workflows`); wire both tasks into the `qualityGate` so they run alongside `spotlessCheck` and `check`. |

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

### Q-004 – Where to track DSL implementation status
- Context: The DSL reference (`docs/3-reference/dsl.md`) currently mixes normative language semantics with engine readiness notes such as “impl TBD” in the overview table, which can blur the contract for authors.
- Question: How should we separate the normative definition of the DSL surface from the evolving implementation status, and which artefact should own the status view?
- Options:
  - A) Keep the DSL reference strictly normative (semantics only) and track implementation status per feature (for example Feature 001) and/or in a small shared “DSL implementation status” doc under `docs/4-architecture/features/`.
  - B) Keep a compact, clearly labelled “Implementation status (non-normative)” appendix in the DSL reference that summarises engine coverage, with details still owned by feature specs.
 - C) Create a separate “DSL feature matrix / implementation status” document that the DSL reference links to, keeping status out of the reference itself but still in one obvious place for readers.
 - Resolution (2025-11-22): Adopt Option A – `docs/3-reference/dsl.md` remains purely normative; engine implementation status is tracked per feature (for example Feature 001 under `docs/4-architecture/features/001/`), and the DSL reference carries a hard non-normative note at the top directing readers to feature docs for current coverage.

### Q-005 – OAS and Arazzo linting with Spectral
- Context: OpenAPI and Arazzo specs live under `docs/3-reference/examples/oas/`, `docs/3-reference/apis/`, and `docs/3-reference/examples/arazzo/`. YAML style is already enforced via Spotless, but there was no dedicated linter for OAS/Arazzo documents.
- Question: Where should Spectral configuration live, and how should OAS + Arazzo linting be wired into the existing Gradle/CI toolchain?
- Options:
  - A) Spectral-driven linting via Gradle tasks for both OAS and Arazzo.
  - B) Local Node tooling under `docs/` with `npm` scripts only.
  - C) Minimal standalone Spectral invocation script without Gradle integration.
 - Resolution (2025-11-22): Adopt Option A with two explicit Gradle tasks – `lintOas` lints all `docs/3-reference/**/*.openapi.yaml` files using `.spectral.yaml` (which extends `spectral:oas`), and `lintArazzo` lints all `docs/3-reference/examples/arazzo/*.arazzo.yaml` files using `.spectral-arazzo.yaml` wired to the official Arazzo 1.0.x JSON Schema. Both tasks are part of the `qualityGate` so that OpenAPI and Arazzo specs are linted alongside formatting and tests.
