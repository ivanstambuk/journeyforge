# DSL Style Guide

Status: Draft | Last updated: 2025-11-19

Use this guide when authoring workflow specs.

## Filenames
- `<workflow-name>.workflow.yaml` (preferred) or `.json`.

## Naming
- State IDs: `snake_case` or `kebab-case` (be consistent per project).
- Variables/`resultVar`: `lowerCamel` or `snake_case`.

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
    ```; flow-style `{ ... }` mappings are not allowed in workflow YAML or examples.

## Comments
- Keep human‑readable comments in YAML. JSON snapshots omit comments by design.

## Interpolation
- Use `${context.<dotPath>}`; avoid nesting.
- Validate that every placeholder resolves; missing values must fail validation.

## Branching
- Prefer a `default` branch in `choice`.
- Use DataWeave predicates for `choice`; prefer short, readable expressions or `exprRef` files for complex logic.

## HTTP calls
- Disallow body on GET.
- Use absolute URLs with scheme.
- Keep headers simple strings; do not inject secrets.


## Canonical type names
- Use `choice` for branch states (canonical). Do not use `decide`.

## Terminal outcomes
- Use `succeed`/`fail` for terminal results.
- Tasks (including HTTP) never auto‑terminate a workflow; branch explicitly after tasks.
