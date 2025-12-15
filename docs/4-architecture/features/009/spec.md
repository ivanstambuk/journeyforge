# Feature 008 – Journey DSL Linter

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-23 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/009/plan.md` |
| Linked tasks | `docs/4-architecture/features/009/tasks.md` |
| Roadmap entry | #008 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below (no per‑feature Clarifications), and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a spec‑aware linter for JourneyForge journey definitions (`*.journey.yaml`) that combines:
- External, schema‑driven validation (JSON Schema + optional Spectral rules) for IDE/editor feedback and basic CI enforcement, and
- An internal, Java‑based CLI that reuses the JourneyForge parser/model to enforce deeper DSL semantics.

The DSL reference in `docs/3-reference/dsl.md` is still evolving; this feature focuses on designing the linter surface, schema layout, and integration points so that implementation can follow once the DSL stabilises.

## Goals
- Provide a JSON Schema describing the Journey DSL shape for `kind: Journey` / `kind: Api` so that editors and generic YAML tooling can validate `*.journey.yaml` files.
- Wire the schema into common editors (for example via the YAML language server) so authors get immediate feedback while editing journey specs.
- Define an optional Spectral ruleset for additional static rules that are hard to express in pure JSON Schema (naming conventions, simple cross‑field constraints).
- Specify a `journeyforge-lint` Java CLI that loads `.journey.yaml` via the parser/model and runs structural + semantic checks using the same types that the engine will use.
- Integrate the linter into the build (`./gradlew` tasks) and, optionally, into githooks so that invalid journey specs are rejected before they reach main branches.
- Keep the linter tolerant of DSL evolution by designing clear versioning and alignment rules with the DSL reference.

## Non-Goals
- No changes to the journey DSL itself in this feature; the linter must consume the DSL as defined elsewhere.
- No engine/runtime behaviour changes; this feature focuses on validation and tooling, not execution.
- No requirement to implement the full Java linter immediately; it is acceptable to ship the external schema tooling first and add deeper semantics later.
- No dedicated UI or web frontend for linting; CLI and editor integration are sufficient for this feature.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-008-01 | Provide JSON Schema for journey specs. | A JSON Schema (or small set of schemas) describes the top-level shape of JourneyForge specs (including `apiVersion`, `kind`, `metadata`, `spec`, and `states` with per-state `type`/`task.kind` variants). | Sample `.journey.yaml` files validate successfully against the schema; intentionally malformed examples are rejected with clear messages. | Schema validation in CI fails when a journey spec violates the documented DSL shape. | Optional: schema validation can emit counts of validated files, but no dedicated telemetry is required. | DSL reference. |
| FR-008-02 | Integrate schema with editor tooling. | Editors that support the YAML language server (for example VS Code) use the JourneyForge JSON Schema for files matching `*.journey.yaml`, providing inline diagnostics as authors edit. | A small “hello world” journey edited in a supported IDE shows schema errors when fields are missing or mis-typed. | Misconfigured or missing schema mappings are detected during setup docs/tests and do not break CI. | None required. | Developer tooling docs. |
| FR-008-03 | Optional Spectral ruleset for static linting. | A Spectral ruleset (or similar) defines additional rules such as naming conventions, tag formats, and simple cross-field checks that complement the JSON Schema. | Running Spectral locally or in CI over `*.journey.yaml` reports rule violations with clear rule IDs and descriptions. | CI jobs that enable Spectral fail when high-severity rules are violated; low-severity rules may be warnings only. | Lint runs may log rule usage statistics, but this is optional. | Tooling guidelines. |
| FR-008-04 | Define `journeyforge-lint` CLI for deep validation. | A Java-based CLI command (for example `journeyforge-lint lint <paths>`), hosted in this repository, loads `.journey.yaml` via the JourneyForge parser/model and runs structural and semantic checks (for example state graph invariants) consistent with the DSL reference. | Unit tests cover representative valid/invalid journeys, including state graph issues that schemas cannot catch; the CLI returns non-zero exit codes on failures and prints human-friendly diagnostics. | CI and local hooks that invoke the CLI fail fast when the linter reports errors; diagnostics are clear enough to locate the offending file and path. | CLI may emit structured output (for example JSON) for future aggregation, but this is optional in this feature. | Parser/model modules. |
| FR-008-05 | Integrate linting into Gradle and githooks. | A Gradle task (for example `journeyLint`) runs all required lint steps (schema/Spectral/CLI as configured) over the repository’s journey specs; the `qualityGate` task depends on it. Optional githooks run the linter on changed `.journey.yaml` files. | Local development and CI both invoke `journeyLint` as part of the standard workflow; failing lint blocks merges. | Misconfigured Gradle tasks or hooks fail loudly rather than silently skipping linting. | None beyond existing build logs. | Build/CI config. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-009-01 | Align linter behaviour with DSL evolution. | Avoid drift between docs and tooling. | When the DSL reference changes materially, the JSON Schema and CLI rules are updated in the same increment or shortly after; mismatches are treated as defects. | DSL reference, parser/model. | Governance docs. |
| NFR-009-02 | Keep linting fast enough for local use. | Developer experience. | Linting all journey specs completes in seconds on a typical dev machine; CI adds minimal overhead. | Gradle tasks, CLI implementation. | Build/CI strategy. |
| NFR-009-03 | Deterministic, offline linting. | CI stability and security. | Lint runs do not depend on external network calls; rule evaluations are deterministic for the same input. | JSON Schema tooling, Spectral (if used), Java CLI. | CI guidelines. |
| NFR-009-04 | Clear, actionable diagnostics. | Usability. | Error messages include file paths and meaningful paths within the YAML (for example `spec.states.call-api.task.method`), and avoid leaking sensitive content. | All lint tooling. | Developer tooling docs. |

## UI / Interaction Mock-ups
```text
# Schema-based lint via Spectral (optional)
$ spectral lint docs/3-reference/examples/http-success.journey.yaml
✔ 0 errors, 0 warnings, 0 hints, 0 infos

# Java CLI linter
$ journeyforge-lint lint docs/3-reference/examples/**/*.journey.yaml
Error: docs/3-reference/examples/invalid/unknown-state.journey.yaml
  - spec.states.decide.next: unknown state id 'missing-state'

$ echo $?
1
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-009-01 | Well-formed journey spec passes JSON Schema validation and CLI lint. |
| S-009-02 | Journey spec with structural DSL errors (for example missing `spec.start` or unknown `type`) is rejected by schema and CLI with clear messages. |
| S-009-03 | Journey spec that is structurally valid but has graph issues (for example `next` pointing to an unknown state) is accepted by schema but rejected by the CLI linter. |
| S-009-04 | Linting is wired into Gradle and fails the build when any journey spec violates configured rules. |

## Test Strategy
- Maintain a small catalog of valid and invalid journey specs under a dedicated test resources directory for this feature.
- Add automated tests for JSON Schema validation against these examples (using a standard schema validator).
- Add unit tests and integration tests for the `journeyforge-lint` CLI behaviour over the same examples, ensuring consistent diagnostics and exit codes.
- Add a Gradle-level test (or CI job) that runs `journeyLint` as part of the build to guard against configuration regressions.

## Interface & Contract Catalogue
- JSON Schema:
  - Primary entrypoint: `docs/3-reference/journeyforge-dsl-v1.schema.json` – validates both `kind: Journey` and `kind: Api` and branches on `kind` within a single schema (with shared shapes factored into `$defs` as needed).
- Spectral (optional):
  - A ruleset file under `tools/` (for example `tools/lint/journey-spectral.yaml`) defining additional static rules for journey specs.
- CLI:
  - `journeyforge-lint` Java-based command with a `lint` subcommand that accepts files and/or directories and returns non-zero on any error.
  - Optional machine-readable output mode (for example `--format json`) for future integration with other tools.
- Build integration:
  - Gradle task `journeyLint` that orchestrates schema validation, Spectral (if enabled), and the CLI linter over the repository’s journey specs.
  - Optional githooks under `githooks/` that invoke `journeyLint` on changed `.journey.yaml` files before commits or pushes.
