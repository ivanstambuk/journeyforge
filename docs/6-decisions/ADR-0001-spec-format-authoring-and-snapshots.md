# ADR-0001 – Spec Authoring Format and Snapshots

Date: 2025-11-19 | Status: Accepted

## Context
JourneyForge uses a spec‑first workflow. We need a human‑friendly authoring format with stable machine artifacts for CI and downstream tools.

## Decision
- Author in YAML (YAML 1.2 subset), allow both YAML and JSON inputs.
- Treat the YAML as the primary, authoritative artefact; JSON forms (when produced) are derived and non-normative.
- Disallow YAML anchors/aliases and implicit type coercions; validate with JSON Schema.

## Consequences
- Human‑readable specs with comments and concise structure.
- Implementations MAY generate canonical JSON snapshots for tooling or CI, but they are not required and are not part of the public contract.
- Parser must enforce the allowed YAML subset and error clearly on unsupported features.

## Related
- Spec guideline: `docs/4-architecture/spec-guidelines/spec-format.md`.
- Roadmap Feature 001 (parser/runner).
