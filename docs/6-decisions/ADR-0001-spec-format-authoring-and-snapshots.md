# ADR-0001 – Spec Authoring Format and Snapshots

Date: 2025-11-19 | Status: Accepted

## Context
JourneyForge uses a spec‑first workflow. We need a human‑friendly authoring format with stable machine artifacts for CI and downstream tools.

## Decision
- Author in YAML (YAML 1.2 subset), allow both YAML and JSON inputs.
- Generate canonical JSON snapshots on validate/build for determinism (sorted keys, UTF‑8/LF).
- Disallow YAML anchors/aliases and implicit type coercions; validate with JSON Schema.

## Consequences
- Human‑readable specs with comments and concise structure.
- Stable JSON artefacts for caching, diffs, and automation.
- Parser must enforce the allowed YAML subset and error clearly on unsupported features.

## Related
- Spec guideline: `docs/4-architecture/spec-guidelines/spec-format.md`.
- Roadmap Feature 001 (parser/runner).
