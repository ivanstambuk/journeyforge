# Implementation Roadmap

Last updated: 2025-11-19

Guiding principles
- Spec‑first. Update the spec before plans/tasks or code.
- Small, verifiable increments. Run `./gradlew --no-daemon spotlessApply check` after each slice.
- Track ambiguities in `open-questions.md`; reflect decisions in specs/ADRs.

## Features (initial seed)

| ID | Name | Status | Scope Snapshot |
|----|------|--------|----------------|
| 001 | DSL + Core HTTP engine | Planned | Full YAML/JSON DSL surface (all state types and config blocks) + synchronous runner + HTTP call node |
| 002 | Persistence + runtime REST | Planned | Instance store, start/query APIs |
| 003 | OpenAPI/Arazzo integration | Planned | Import OAS, reference `operationId`, Arazzo exporter |
| 004 | UI bridge (designer) | Planned | Graph API for React Flow/Atlas over runtime/admin APIs |
| 005 | Admin plane | Planned | Versioned definitions, environments, RBAC, telemetry |
| 006 | LDAP/JDBC/Kafka connectors | Planned | Additional connectors and fixtures |

Note: IDs and scope will be refined as specs mature.
