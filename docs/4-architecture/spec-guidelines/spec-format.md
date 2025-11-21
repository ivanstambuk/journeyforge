# Spec Format – Authoring & Canonicalization

Decision (2025-11-19): Author journeys in YAML (1.2 subset), accept both YAML (`.yaml`/`.yml`) and JSON (`.json`) as inputs, and generate a canonical JSON snapshot on validate/build.

## Authoring
- Use YAML 1.2 subset; treat YAML as a friendly surface over a strict JSON data model.
- Disallow YAML anchors/aliases and implicit type coercions; parsers must reject them.
- Prefer explicit strings/numbers/booleans; avoid nulls unless required by the schema.
- Keep comments in YAML; JSON snapshots are generated artifacts and may omit comments.

## Ingestion
- Accept files with `.yaml`, `.yml`, or `.json`.
- Validate against the JSON Schema for the DSL before execution.
- Normalise: environment‑specific substitutions are out of scope for now.

## Canonical JSON Snapshot
- Command: `journeyforge validate --snapshot` (CLI to be introduced).
- Output file: write `<name>.journey.json` alongside source (configurable).
- Canonicalization rules:
  - UTF‑8, LF line endings.
  - Sorted object keys for stable diffs.
  - No trailing spaces; no insignificant whitespace.

## Style Guide
- Filenames: `<journey-name>.journey.yaml` (or `.json`).
- Top‑level fields: `apiVersion`, `kind`, `metadata`, `spec`.
- No secrets in specs; reference them indirectly (future: `secret://...`).

## Validation & CI
- Lint in PRs: schema validation + canonical snapshot generation.
- CI stores snapshots as build artefacts; commits should not re‑write YAML.

## Future Work
- Richer expressions for `choice` (guarded by schema feature flag).
- YAML style rules (indent=2, no tabs, quoted scalars policy) – TBD.
