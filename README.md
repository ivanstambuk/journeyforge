# JourneyForge

Spec‑first durable journey engine (Java 25) for user‑centric API workflows. Journey definitions (`kind: Journey`) are authored as configuration in a YAML (1.2 subset) DSL (“journey‑as‑config”, not workflow‑as‑code) and executed by the JourneyForge engine with HTTP‑centric tasks, waits, webhooks, schedules, and compensation. The repository follows a spec‑driven development (SDD) workflow.

## Quick Links
- DSL Reference: [docs/3-reference/dsl.md](docs/3-reference/dsl.md)
- DSL Style Guide: [docs/4-architecture/spec-guidelines/dsl-style.md](docs/4-architecture/spec-guidelines/dsl-style.md)
- Spec Format & Snapshots: [docs/4-architecture/spec-guidelines/spec-format.md](docs/4-architecture/spec-guidelines/spec-format.md)
- Feature 001 (Core HTTP + DSL): [docs/4-architecture/features/001/spec.md](docs/4-architecture/features/001/spec.md)
- Roadmap: [docs/4-architecture/roadmap.md](docs/4-architecture/roadmap.md)
- Agent Playbook: [AGENTS.md](AGENTS.md)

## Build
- Requires JDK 25.
- Format + verify: `./gradlew --no-daemon spotlessApply check`
- CI gate (local): `./gradlew --no-daemon qualityGate`

## Docs
- SDD governance: [docs/6-decisions/project-constitution.md](docs/6-decisions/project-constitution.md) and ADRs under [docs/6-decisions/](docs/6-decisions/).
- Templates for new features: [docs/templates/](docs/templates/).
