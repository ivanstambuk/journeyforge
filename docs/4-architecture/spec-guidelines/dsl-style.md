# DSL Style Guide

Status: Draft | Last updated: 2025-11-21

Use this guide when authoring journey specs.

## Filenames
- `<journey-name>.journey.yaml` (preferred) or `.json`.

## Naming
- State IDs: `snake_case` or `kebab-case` (be consistent per project).
- Variables/`resultVar`: `lowerCamel` or `snake_case`.
- `metadata.name`: DNS-label-like (`[a-z0-9]([-a-z0-9]*[a-z0-9])?`); stable identifier.
- `metadata.description`: short, human-readable summary (1–2 sentences, ≤200 characters), no secrets or PII.

## Indentation & quoting
- Indent by 2 spaces; no tabs.
- Quote strings that contain `${...}` or `:`.
- For YAML mappings, always use block style:
  - Prefer
    ```yaml
    headers:
      Accept: application/json
    ```
    over flow style
    ```yaml
    headers: { Accept: application/json }
    ```; flow-style `{ ... }` mappings are not allowed in journey YAML or examples.

## Comments
- Keep human‑readable comments in YAML. JSON snapshots omit comments by design.

## Interpolation
- Use `${context.<dotPath>}`; avoid nesting.
- Validate that every placeholder resolves; missing values must fail validation.

## Branching
- Prefer a `default` branch in `choice`.
- Use DataWeave predicates for `choice`; prefer short, readable expressions or `exprRef` files for complex logic.

## Loops
- Avoid unbounded loops in the state graph; every cycle SHOULD be structurally bounded (for example, via an explicit max-attempt counter) or clearly convergent.
- For `kind: Api`, prefer loop-free control flow. If a loop is necessary (for example, bounded polling), it MUST be structurally bounded and SHOULD rely on `httpResilience` policies for HTTP retries instead of hand-rolled HTTP retry loops.
- For `kind: Journey`, only use loops that are driven by external input (`wait`/`webhook`) or have explicit bounds, and always guard them with `spec.execution.maxDurationSec` (and, when appropriate, a max-attempt counter in `context`).

## HTTP calls
- Disallow body on GET.
- Use absolute URLs with scheme.
- Keep headers simple strings; do not inject secrets.


## Canonical type names
- Use `choice` for branch states (canonical). Do not use `decide`.

## Terminal outcomes
- Use `succeed`/`fail` for terminal results.
- Tasks (including HTTP) never auto‑terminate a journey; branch explicitly after tasks.
