# Feature <NNN> – <Descriptive Name>

| Field | Value |
|-------|-------|
| Status | Draft | <!-- Specification status only (Draft/Ready/Deprecated), independent of implementation progress. -->
| Last updated | YYYY-MM-DD |
| Owners | <Name(s)> |
| Linked plan | `docs/4-architecture/features/<NNN>/plan.md` |
| Linked tasks | `docs/4-architecture/features/<NNN>/tasks.md` |
| Roadmap entry | #<workstream number> |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/UI/Telemetry sections below (no per‑feature Clarifications), and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Summarise the problem, affected modules, and user impact in 2–3 sentences.

## Goals
List the concrete outcomes this feature must deliver.

## Non-Goals
Call out adjacent topics out of scope.

## Functional Requirements
| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-<NNN>-01 | Describe behaviour. | … | … | … | … | … |

## Non-Functional Requirements
| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-<NNN>-01 | Describe constraint. | … | … | … | … |

## UI / Interaction Mock-ups
```
<ASCII mock-up>
```

## Branch & Scenario Matrix
| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-<NNN>-01 | Describe behaviour |

## Test Strategy
Describe coverage per layer (engine core, admin/journey APIs, CLI, UI, docs/contracts).

## Interface & Contract Catalogue
Document domain objects, API routes, CLI commands, telemetry events, and fixtures.
