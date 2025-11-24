# Feature 010 – Admin & Dev CLI

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-23 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/010/plan.md` |
| Linked tasks | `docs/4-architecture/features/010/tasks.md` |
| Roadmap entry | #009 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below (no per‑feature Clarifications), and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.
>
> CLI contract note: The `journeyforge` CLI commands and flags defined in this feature are **non‑normative in v1**. The Journeys/Admin REST APIs and DSL remain the canonical contracts; the CLI is a façade that MAY evolve as those contracts stabilise.

## Overview
Introduce a unified `journeyforge` CLI that serves both:
- Developers running journeys locally against the embedded engine JAR for ad‑hoc execution and troubleshooting.
- Operators and admins interacting with remote JourneyForge deployments via the Journeys/Admin REST APIs for lifecycle operations (start/list/status/result and, directionally, history/suspend/resume/terminate/purge).

The CLI is intentionally thin over the REST surface and engine APIs, favouring clear, scriptable commands over additional business semantics.

## Goals
- Provide a single `journeyforge` CLI binary with a clear, discoverable command structure.
- Support local development workflows by executing `.journey.yaml` specs directly against the engine (Feature 001) with simple input handling and JSON output.
- Support basic remote journey lifecycle operations over the Journeys API (Feature 002), including starting a journey, querying status/result, and listing journeys with common filters.
- Reserve directional command shapes for admin‑plane lifecycle operations (history, suspend/resume/terminate, purge) so that later Admin plane specs can map HTTP contracts onto an existing CLI surface.
- Integrate the CLI into the repository build (Gradle) and basic docs, without blocking headless usage via the REST APIs.

## Non-Goals
- No interactive TUI or rich UI in this feature; the CLI is text‑only and script‑friendly.
- No new REST endpoints or engine semantics are introduced solely for the CLI; any additional lifecycle operations must be specified in Admin/Journeys feature specs first.
- No opinionated environment management or secret storage; the CLI relies on configuration files and environment variables provided by the operator.
- No guarantee of long‑term stability for CLI command names or flags before the underlying Admin plane and Journeys APIs are fully stabilised; breaking changes MAY occur with appropriate release notes.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-010-01 | Provide a single `journeyforge` CLI entry point. | A `journeyforge` command (or platform‑equivalent) is available, with `--help` listing the top‑level subcommands for local runs and remote admin operations. | Manual and automated smoke tests can invoke `journeyforge --help` and see documented subcommands; docs under `docs/` reference the same names. | If the CLI is not on `PATH` or fails to start, it exits non‑zero with a clear error message. | Optional: basic usage metrics (command counts) MAY be emitted in future but are not required in this slice. | Build tooling, distribution plan. |
| FR-010-02 | Local journey run command. | `journeyforge run file <path>` executes a `.journey.yaml` or `.journey.json` spec locally using the embedded engine JAR, with optional `--input` (inline JSON) or `--input-file` flags, and prints a `JourneyOutcome`‑like JSON document to stdout. | Tests cover successful runs, validation failures, and engine errors; outputs are machine‑parseable JSON. | Invalid specs or engine errors cause a non‑zero exit code and a structured error message; partial stack traces are avoided by default. | CLI MAY log minimal debug traces to stderr when a `--verbose` flag is enabled. | Feature 001 (engine & DSL). |
| FR-010-03 | Remote journey lifecycle commands (basic). | `journeyforge journeys start <journeyName>` calls the Journeys API `POST /api/v1/journeys/{journeyName}/start` against a configured base URL, passes optional input, and prints the resulting `JourneyOutcome` or `JourneyStatus` JSON. `journeyforge journeys status <journeyId>` and `journeyforge journeys result <journeyId>` wrap the corresponding Journeys API GET endpoints. | Integration tests against a local or test deployment verify that CLI commands map 1:1 to documented Journeys API calls and propagate HTTP errors with useful messages. | Connection failures, auth errors, or HTTP 4xx/5xx responses result in non‑zero exit codes and clear diagnostics that include status codes and brief summaries but avoid leaking sensitive payload content. | Access logs and existing API telemetry remain the primary observability surface; CLI adds no new telemetry in this slice. | Feature 002 (Journeys API), DSL reference §2.1. |
| FR-010-04 | Remote journey listing with common filters. | `journeyforge journeys list` calls the Journeys listing API (for example `GET /api/v1/journeys`) and supports a small set of flags (`--journey-name`, `--phase`, `--subject-id`, `--tenant-id`, `--tag`) that map directly to query parameters defined by Feature 002. Results are printed in a concise, tabular or JSON format suitable for scripting. | Tests assert that CLI flags are translated into the expected HTTP query parameters and that output formats are documented and stable within a major version. | Unsupported filters or mismatched API versions are reported with clear messages suggesting compatible flags or API versions. | Existing API metrics capture list usage; CLI does not add its own metrics in this slice. | Feature 002 (tags/query spec). |
| FR-010-05 | Directional admin lifecycle commands. | The CLI exposes subcommands for future admin operations (for example `journeyforge journeys history <journeyId>`, `journeyforge journeys suspend|resume|terminate <journeyId>`, `journeyforge journeys purge --older-than <duration>`), marked as experimental and wired to whichever Admin plane/Journeys APIs are available. | Once Admin plane features define the corresponding REST endpoints, tests bind these commands to real APIs; until then, the commands can be stubbed or hidden behind an `--experimental` flag. | When an underlying API is unavailable, the CLI fails with a message that clearly distinguishes "command not implemented on server" from local CLI misuse. | Future Admin plane telemetry remains the source of truth; this feature does not define new metrics. | Roadmap entry #005 (Admin plane), Dapr workflow CLI inspiration. |
| FR-010-06 | Configuration model for local vs remote targets. | The CLI supports a simple configuration model to distinguish local engine runs from remote admin operations, for example via `--target` flags, environment variables (such as `JOURNEYFORGE_BASE_URL`), and/or a config file (for example `~/.journeyforge/config.yaml`). | Tests verify that configuration precedence (CLI flags > env vars > config file) is respected and documented; misconfiguration produces actionable messages. | Missing or invalid configuration (for example malformed base URL) causes a non‑zero exit and a clear explanation of how to fix it. | Optional logs MAY note which target was used but MUST avoid logging secrets or tokens. | Feature 001/002, ops runbooks. |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-010-01 | CLI is fast and suitable for scripting. | Developer and operator experience. | Typical commands (help, local run, status/result, list) complete in seconds against a responsive target; CLI startup overhead is minimal. | JVM startup, packaging strategy (for example native image vs JVM). | Tooling guidelines. |
| NFR-010-02 | Output is machine- and human-friendly. | Automation and debugging. | JSON output is stable and parseable; a `--output` flag (for example `json`/`table`) is documented, with `json` suitable for piping into tools like `jq`. | Underlying Journeys/Admin API response formats. | DSL reference, API specs. |
| NFR-010-03 | No secrets printed by default. | Security posture. | Access tokens, API keys, and sensitive payload content are never logged or echoed in error messages unless explicitly enabled via a debug mode; redaction is used where necessary. | Auth configuration, logging. | Security guidelines. |
| NFR-010-04 | Cross-platform distribution. | Adoption. | CLI can be built and run on at least Linux and macOS in CI; Windows support is desirable but may be best-effort initially. | Build system, packaging tooling. | Build/CI strategy. |
| NFR-010-05 | Alignment with REST and DSL. | Avoid divergence between tools and specs. | When DSL or Journeys/Admin API contracts change, CLI behaviour and docs are updated in the same or a closely-following slice; mismatches are treated as defects. | Feature 001, Feature 002, Admin plane specs. | Project constitution. |

## UI / Interaction Mock-ups
```text
$ journeyforge --help
Usage: journeyforge [COMMAND]

Commands:
  run         Run a journey spec locally using the embedded engine
  journeys    Interact with remote journeys via the Journeys API
  help        Show this message or help for a subcommand

$ journeyforge run file docs/3-reference/examples/technical/http-success/http-success.journey.yaml \
    --input '{"orderId": "123"}'
{
  "journeyName": "http-success",
  "phase": "SUCCEEDED",
  "output": { ... }
}

$ journeyforge journeys start http-success --target dev \
    --input-file ./fixtures/order-123.json
{
  "journeyId": "93d7f7a4-7a77-4e7e-9f8e-1a2b3c4d5e6f",
  "journeyName": "http-success",
  "phase": "SUCCEEDED",
  "output": { ... }
}

$ journeyforge journeys list --phase RUNNING --journey-name http-success --tag self-service
journeyId                             journeyName    phase     updatedAt
93d7f7a4-7a77-4e7e-9f8e-1a2b3c4d5e6f  http-success  RUNNING   2025-11-23T10:15:30Z

$ journeyforge journeys suspend 93d7f7a4-7a77-4e7e-9f8e-1a2b3c4d5e6f --target dev --experimental
Error: suspend journeys is not yet supported by the target API; see Admin plane roadmap #005.
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-010-01 | Developer runs a local journey spec via `journeyforge run file` with inline JSON input and receives a `JourneyOutcome` JSON document on stdout. |
| S-010-02 | Operator starts a journey via `journeyforge journeys start`, then polls `status`/`result` until a terminal phase is reached. |
| S-010-03 | Operator lists journeys filtered by name/phase/tag using `journeyforge journeys list` and receives either table or JSON output suitable for scripting. |
| S-010-04 | A directional admin command (for example `suspend`) is invoked against a deployment that does not yet implement the corresponding endpoint; the CLI reports this as an unsupported operation rather than a generic failure. |
| S-010-05 | Misconfigured base URL or missing auth results in a clear, actionable error message and non‑zero exit code. |

## Test Strategy
- Add unit tests for CLI argument parsing, configuration precedence, and error handling for both local and remote commands.
- Add integration tests that run the CLI against:
  - A local embedded engine (for `run file`) using known journey examples under `docs/3-reference/examples/`.
  - A test instance of the Journeys API (for `journeys start/status/result/list`), using fixtures aligned with Feature 002.
- Ensure tests cover both success and failure paths (invalid specs, network errors, HTTP 4xx/5xx, unsupported commands).
- Integrate a basic CLI smoke test into the build (for example a Gradle task) so that `journeyforge --help` and at least one simple subcommand run as part of CI.

## Interface & Contract Catalogue
- CLI binary:
  - `journeyforge` (or platform equivalent) with version reporting via `journeyforge --version`.
- Commands (non-normative v1 surface):
  - `journeyforge run file <path> [--input <json> | --input-file <path>] [--output json|table]`
  - `journeyforge journeys start <journeyName> [--input <json> | --input-file <path>] [--sync|--async] [--target <name>]`
  - `journeyforge journeys status <journeyId> [--target <name>]`
  - `journeyforge journeys result <journeyId> [--target <name>]`
  - `journeyforge journeys list [--journey-name <name>] [--phase <phase>] [--subject-id <id>] [--tenant-id <id>] [--tag <tag> ...] [--target <name>] [--output json|table]`
  - Directional/experimental (subject to Admin plane specs): `journeyforge journeys history`, `journeyforge journeys suspend`, `journeyforge journeys resume`, `journeyforge journeys terminate`, `journeyforge journeys purge`.
- Configuration:
  - Environment variables such as `JOURNEYFORGE_BASE_URL`, `JOURNEYFORGE_TOKEN`, and `JOURNEYFORGE_TARGET`.
  - Optional config file (for example `~/.journeyforge/config.yaml`) mapping logical targets (e.g. `dev`, `stage`, `prod`) to base URLs and auth settings.
- Modules:
  - CLI implementation module (for example `journeyforge-cli` or `app/`) depends on `journeyforge-model`, `journeyforge-parser`, `journeyforge-runtime-core`, and HTTP client libraries consistent with existing connectors.
  - Build integration via a Gradle application plugin or equivalent to produce runnable artifacts. 
