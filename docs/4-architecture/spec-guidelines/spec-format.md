# Spec Format – Authoring & Canonicalization

Decision (2025-11-19): Author journeys in YAML (1.2 subset), accept both YAML (`.yaml`/`.yml`) and JSON (`.json`) as inputs. YAML is the primary artefact; JSON forms are derived.

JSON Schema dialect
- Inline schemas in the DSL (for example `spec.input.schema`, `spec.output.schema`, and step-level `*.schema` blocks) use JSON Schema 2020-12 semantics by default.
- Authors SHOULD NOT repeat `$schema` in each inline schema; tools and engines MUST treat these blocks as 2020-12 unless a future version of the DSL introduces an explicit override.

## Authoring
- Use YAML 1.2 subset; treat YAML as a friendly surface over a strict JSON data model.
- Disallow YAML anchors/aliases and implicit type coercions; parsers must reject them.
- Prefer explicit strings/numbers/booleans; avoid nulls unless required by the schema.
- Keep comments in YAML; generated JSON artefacts (when produced) may omit comments.

## Ingestion
- Accept files with `.yaml`, `.yml`, or `.json`.
- Validate against the JSON Schema for the DSL before execution.
- Normalise: environment‑specific substitutions are out of scope for now.

## Style Guide
- Filenames: `<journey-name>.journey.yaml` (or `.json`).
- Top‑level fields: `apiVersion`, `kind`, `metadata`, `spec`.
- No secrets in specs; reference them indirectly (future: `secret://...`).

## Validation & CI
- Lint in PRs: schema validation on the authored YAML.
- CI MAY store derived JSON artefacts as build artefacts; commits should not re‑write YAML based on canonicalization alone.

## Future Work
- Richer expressions for `choice` (guarded by schema feature flag).
- YAML style rules (indent=2, no tabs, quoted scalars policy) – TBD.
