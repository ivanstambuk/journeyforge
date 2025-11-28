# ADR-0002 – Expression Language: DataWeave

Date: 2025-11-19 | Status: Accepted

## Context
JourneyForge needs a single, powerful expression/transform language for predicates and mapping. Early options included JSONPath (selectors) and JSONata. We elected to pick one canonical language to avoid fragmentation.

## Decision
- Adopt DataWeave 2.x as the canonical expression language from the beginning.
- In the initial version of the DSL:
  - `choice` supports a DataWeave predicate form (`when.predicate: { lang: dataweave, expr }`) that must evaluate to boolean; expressions are authored inline in journey specs.
  - Equality checks remain as sugar for readability and can be compiled to equivalent DataWeave.
- Transforms will use DataWeave as well (in a later feature that introduces a `transform` state).

## Consequences
- One language to document, test, and govern across predicates and transforms.
- Readers can use the equals sugar or author full DataWeave predicates when needed.
- Implementation must provide a secure evaluator with timeouts and resource limits.

## Notes
- Implementation details (e.g., embedding vs external CLI) are non‑normative and will be specified in the corresponding feature.
- External DataWeave modules and DSL-level `exprRef` fields are explicitly out of scope for this version; see ADR-0015 for recorded reuse scenarios and the v1 decision to keep expressions inline-only.
- Later ADRs (for example ADR‑0027) generalise the expression model to a pluggable Expression Engine SPI; DataWeave remains the baseline engine for `lang: dataweave`, but deployments MAY register additional engines alongside it.
