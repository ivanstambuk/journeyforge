# Spec Format – Authoring & Canonicalization

Decision (2025-11-19): Author journeys in YAML (1.2 subset), accept both YAML (`.yaml`/`.yml`) and JSON (`.json`) as inputs. YAML is the primary artefact; JSON forms are derived.

JSON Schema dialect
- Inline schemas in the DSL (for example `spec.input.schema`, `spec.output.schema`, and step-level `*.schema` blocks) use JSON Schema 2020-12 semantics by default.
- Authors SHOULD NOT repeat `$schema` in each inline schema; tools and engines MUST treat these blocks as 2020-12 unless a future version of the DSL introduces an explicit override.
 - For journeys that expose a meaningful business outcome, `spec.output.schema` SHOULD include a `status` property that represents the canonical business outcome code for the run; engines mirror this to the top-level `JourneyOutcome.status` field.

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

## YAML Style Rules
(Related: see ADR-0015 for the v1 decision to keep expressions inline-only.)
- Indentation
  - Use 2 spaces per indentation level.
  - Tabs are not allowed anywhere in YAML files.
  - Sequence items are written as:
    ```yaml
    items:
      - name: foo
        enabled: true
      - name: bar
        enabled: false
    ```
- Scalars and quoting
  - Treat YAML as a strict JSON surface:
    - Booleans: `true` / `false` (lowercase).
    - Numbers: plain decimal; avoid leading `+` and leading zeros (except for `0`).
  - Prefer unquoted scalars only for obviously safe identifiers (for example `http-custom-error-envelope-api`, `users`, `SUCCEEDED`).
  - Always quote scalars when they:
    - Could be interpreted as special YAML values (`on`, `off`, `yes`, `no`, `y`, `n`, `null`, `~`) or as timestamps/IPv6 addresses.
    - Start with `0` and are more than one digit (for example `"01"`, `"007"`).
    - Contain characters that are ambiguous in YAML (`#`, `:`, `@`, leading/trailing spaces, or similar punctuation).
  - Use double quotes for quoted scalars by default.
- Multiline values and code
  - Use block scalars for any multiline value, especially DataWeave expressions:
    ```yaml
    expr: |
      {
        orderId: context.orderId,
        compensation: "completed",
        cause: outcome
      }
    ```
  - Avoid folded (`>`) scalars for code; prefer literal (`|`) so that newlines are preserved.
- Documents and anchors
  - Do not use YAML document markers (`---`, `...`) in journey specs and examples unless strictly necessary.
  - YAML anchors and aliases are disallowed; parsers MUST reject them.
- Whitespace
  - Trim trailing whitespace on all lines.
  - End each file with a single newline.

## Validation & CI
- Lint in PRs: schema validation on the authored YAML.
- CI MAY store derived JSON artefacts as build artefacts; commits should not re‑write YAML based on canonicalization alone.
  - Spotless configuration MUST enforce, at minimum, “no tabs” and trailing‑whitespace rules for all YAML files in this repository.
