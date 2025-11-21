# Feature 007 – External-Input Step Responses & Schemas – Plan

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-21 |

## Milestones
- M1: DSL reference and OpenAPI export guidelines updated.
- M2: Example journeys (`wait-approval`, `payment-callback`) and their OAS files updated.
- M3: Engine and exporter implementations adopt the new `response` block.

## Work Items (high level)
- Design & docs:
  - Update `docs/3-reference/dsl.md` (`wait`/`webhook` sections, step export mapping).
  - Update `docs/4-architecture/spec-guidelines/openapi-export.md` to cover step response schemas.
  - Add ADR-0013 capturing the decision and trade-offs.
- Examples:
  - Update `wait-approval` and `payment-callback` specs and schemas.
  - Regenerate/adjust corresponding OpenAPI examples.
- Implementation (later slice):
  - Extend parser/model to include `response` blocks.
  - Implement response projection in engine.
  - Extend OpenAPI exporter.

