<!-- Template for technical DSL building-block examples -->

# Technical Pattern – <Pattern Name>

Status: Draft | Last updated: <YYYY-MM-DD>

## Problem

Short description of the problem this pattern solves and when it appears in real integrations.

## When to use

- Situations where this pattern is a good fit.
- Related patterns that may be better for adjacent problems.

## Relevant DSL features

- Key JourneyForge DSL features used (for example `task.kind: httpCall`, `spec.outcomes`, `wait` / `webhook`, `parallel`, `timer`, `task.kind: schedule`).
- Links to the relevant sections in `docs/3-reference/dsl.md`.

## Example – <journey-id>

Artifacts for this example:

- Journey: [<journey-id>.journey.yaml](<journey-id>.journey.yaml)
- OpenAPI: [<journey-id>.openapi.yaml](<journey-id>.openapi.yaml)
- Arazzo: [<journey-id>.arazzo.yaml](<journey-id>.arazzo.yaml)
- Docs (this file): [<journey-id>.md](<journey-id>.md)

```yaml
# Inline journey snippet – keep small and focused
# For larger examples, prefer showing only the relevant states.
```

## Variations and combinations

- How this pattern composes with other patterns.
- Common variations (for example different error handling, retries, compensation).

## Implementation notes

- Engine/runtime behaviours to be aware of.
- Operational considerations (timeouts, retries, idempotence, observability).
