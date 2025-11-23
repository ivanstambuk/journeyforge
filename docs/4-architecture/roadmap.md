# Implementation Roadmap

Last updated: 2025-11-23

Guiding principles
- Spec‑first. Update the spec before plans/tasks or code.
- Small, verifiable increments. Run `./gradlew --no-daemon spotlessApply check` after each slice.
- Track ambiguities in `open-questions.md`; reflect decisions in specs/ADRs.

## Features (initial seed)

| ID | Name | Status | Scope Snapshot |
|----|------|--------|----------------|
| 001 | DSL + Core HTTP engine | Planned | Full YAML/JSON DSL surface (all state types and config blocks) + synchronous runner + HTTP call node |
| 002 | Persistence + Journeys API | Planned | Instance store, start/query APIs |
| 003 | OpenAPI/Arazzo integration | Planned | Import OAS, reference `operationId`, Arazzo exporter |
| 004 | UI bridge (designer) | Planned | Graph API for React Flow/Atlas over journey/admin APIs |
| 005 | Admin plane | Planned | Versioned definitions, environments, RBAC, telemetry |
| 006 | LDAP/JDBC/Kafka connectors | Planned | Additional connectors and fixtures |
| 007 | External-input step responses & schemas | Planned | Project additional typed fields into `wait`/`webhook` step responses and describe them via per-step JSON Schemas and per-journey OpenAPI (`JourneyStatus` + step schema). |
| 008 | Scheduled journeys (`task.kind: schedule`) | Planned | Allow journeys to schedule future non-interactive runs of themselves from a given state with evolving context, bounded cadence, and clear subject binding. |
| 009 | Journey DSL linter | Planned | Provide schema-based and DSL-aware linting for `.journey.yaml` definitions, with editor integration and a Java CLI integrated into Gradle/CI. |

Note: IDs and scope will be refined as specs mature.
