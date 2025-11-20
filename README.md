# JourneyForge

Spec‑first API journey orchestrator (Java 25). Workflows are authored in YAML (1.2 subset) and executed by a runtime engine with HTTP‑centric tasks. This repository follows a spec‑driven development (SDD) workflow.

## Quick Links
- DSL Reference: `docs/3-reference/dsl.md`
- DSL Style Guide: `docs/4-architecture/spec-guidelines/dsl-style.md`
- Spec Format & Snapshots: `docs/4-architecture/spec-guidelines/spec-format.md`
- Feature 001 (Core HTTP + DSL): `docs/4-architecture/features/001/spec.md`
- Roadmap: `docs/4-architecture/roadmap.md`
- Agent Playbook: `AGENTS.md`

## Build
- Requires JDK 25.
- Format + verify: `./gradlew --no-daemon spotlessApply check`
- CI gate (local): `./gradlew --no-daemon qualityGate`

## Docs
- SDD governance: `docs/6-decisions/project-constitution.md` and ADRs under `docs/6-decisions/`.
- Templates for new features: `docs/templates/*`.
