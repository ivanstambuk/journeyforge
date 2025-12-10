# Feature Specs – Refinement Backlog (Internal Aid)

> Note: This file is a working checklist for specification refinement only. Feature specs under `docs/4-architecture/features/<NNN>/spec.md`, their `plan.md` / `tasks.md`, ADRs under `docs/6-decisions/`, and the DSL reference in `docs/3-reference/dsl.md` remain the authoritative sources of truth.

## Pass 1 – Core Engine, Plugins, Observability

- [x] Feature 001 – Core HTTP Engine + DSL
- [x] Feature 011 – Task Plugins & Execution SPI
- [x] Feature 022 – Observability Packs & Telemetry SPI

## Pass 2 – Metadata, Auth, Cookies, External Input

- [x] Feature 002 – Journey Metadata, Tags & Query
- [x] Feature 003 – Outbound HTTP Auth & Secrets
- [x] Feature 004 – HTTP Cookies & Journey Cookie Jar
- [x] Feature 007 – External-Input Step Responses & Schemas

## Pass 3 – Tooling, Expression Engines, Bindings

- [ ] Feature 009 – Journey DSL Linter
- [ ] Feature 010 – Admin & Dev CLI
- [ ] Feature 012 – DataWeave Transform State
- [ ] Feature 013 – Expression Engines – Core Model
- [ ] Feature 014 – Expression Engine – DataWeave
- [ ] Feature 015 – Expression Engine – HTTP External
- [ ] Feature 016 – jq Expression Engine
- [ ] Feature 017 – WebSocket Binding for Journeys
- [ ] Feature 018 – gRPC Binding for APIs
- [ ] Feature 019 – Queue/Message Binding for Journeys
- [ ] Feature 020 – CLI/Batch Binding for Journeys and APIs
- [ ] Feature 021 – Cloud-Function Binding for Journeys and APIs
