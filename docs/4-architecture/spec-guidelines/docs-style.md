# Docs Style Guide

Status: Draft | Last updated: 2025-11-23

Use this guide when writing or updating Markdown docs in `docs/` (how‑tos, examples, feature specs, ADRs, reference docs). It complements the DSL‑specific rules in `dsl-style.md` and the YAML rules in `spec-format.md`.

## 1. Document metadata

- Start every doc with:
  - `# <Title>`
  - `Status: Draft | Last updated: YYYY-MM-DD` (or `Accepted`/`Deprecated` as appropriate).
- Keep `Last updated` in sync when making meaningful content changes (not for trivial whitespace fixes).
- When a doc is clearly normative (reference/spec/ADR), state that in the introduction (for example, “This document is normative for …”).

## 2. Headings & structure

- Prefer clear, descriptive headings; avoid very deep nesting.
- For reference/spec‑like docs that need stable citations (for example the DSL reference), use numbered sections (`1.`, `2.1`, `2.2`, …) and keep those numbers stable over time.
- Avoid lettered sub‑sections like `2a`, `2b`; use decimal numbering instead (for example `2.1`, `2.2`).
- Only introduce a manual table of contents when the doc is long and reference‑heavy (like `dsl.md`); otherwise rely on the renderer’s built‑in outline.

## 3. Cross‑references & citations

When referring to other normative docs, use a short, consistent citation style:

- General pattern:
  - `<Alias> §<section>` for sections, optionally linked.
  - Examples: `DSL §2.4`, `ADR‑0008 §3.2`, `Feature 004 §2.1`.
- Aliases:
  - `DSL` → `docs/3-reference/dsl.md`.
  - `ADR‑NNNN` → `docs/6-decisions/ADR-NNNN-*.md` (for example `ADR‑0008`).
  - `Feature NNN` → `docs/4-architecture/features/NNN/spec.md` (for example `Feature 004`).
- First mention in a doc:
  - Include both the alias and the file, for example:  
    - `See DSL §2.4 (docs/3-reference/dsl.md).`  
    - `See ADR‑0008 §2.3 (docs/6-decisions/ADR-0008-global-compensation-journey.md).`
- Subsequent mentions in the same doc:
  - Just use the alias and section: `DSL §2.4`, `ADR‑0008 §2.3`, `Feature 004 §3.1`.
- When you need a clickable link, wrap the whole citation:
  - `[DSL §2.4](docs/3-reference/dsl.md#dsl-2-4-execution-deadlines-specexecution)`.
  - `[ADR‑0008 §2.3](docs/6-decisions/ADR-0008-global-compensation-journey.md#section-2-3)` (or the heading’s slug).
- Avoid vague phrases like “see below” or “see the previous section” when a section number is available; prefer `DSL §5.7`‑style references.

## 4. Links, paths & code

- Use repository‑relative paths in Markdown links (no absolute filesystem paths, no `file://` URLs).
  - Good: `[DSL](docs/3-reference/dsl.md)`.
  - Good: `[Loan application journey](docs/3-reference/examples/loan-application.journey.yaml)`.
- Wrap file paths, identifiers, and code in backticks:
  - `docs/3-reference/dsl.md`, `spec.execution.maxDurationSec`, `JourneyOutcome`.
- For inline commands or snippets, use backticks; for multi‑line examples, use fenced code blocks with a language tag (`bash`, `yaml`, `json`, etc.).
- Do not paste large generated artefacts (full OpenAPI exports, long JSON examples) into docs; link to files under `docs/3-reference/openapi/` or `docs/3-reference/examples/` instead and provide a small illustrative snippet if needed.

## 5. Terminology & wording

- Follow `docs/0-overview/terminology.md` for model terms (journey definition/instance, engine, Administrative API, Journeys API, etc.).
- When introducing a term that is not already in the terminology doc, either:
  - Add it there as part of the same change, or
  - Clearly mark it as provisional and add an open question in `open-questions.md`.
- Avoid inventing synonyms for core concepts (for example, do not use “workflow” or “process” for journeys) except when quoting external systems.
- Use active, precise language in normative sections (for example, “The engine MUST …”, “The journey definition configures …”). Reserve softer language (“may”, “can”) for non‑normative guidance.

## 6. Examples & how‑tos

- When a how‑to or example journey depends on a particular DSL feature, link back to the relevant spec section using the citation style above, for example:
  - “This use case exercises parallel branches and joins (DSL §16).”
  - “For timeout semantics, see DSL §2.4.”
- Keep example journeys, their per‑journey OpenAPI files, and Arazzo workflows in sync (see `docs/2-how-to/business-journeys/index.md` and the agent playbook for the expected layout).

## 7. Status & evolution

- When a style rule in this guide needs to change in a non‑trivial way (for example, changing the citation pattern), capture the rationale in an ADR and update this file to match.
- Older docs that use slightly different citation styles or heading patterns do not need to be mass‑edited immediately, but SHOULD be normalised when you make other substantial edits to them.

