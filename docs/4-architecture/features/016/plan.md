# Feature 016 – jq Expression Engine – Plan

## Slices

- Slice 1 – jq integration design
  - Decide how jq programs are passed/configured via `expr`.
  - Clarify evaluation model (single JSON value vs streams) in the context of JourneyForge.

- Slice 2 – Implementation
  - Implement a jq-based expression engine plugin and register it under `lang: jq`.
  - Add configuration for enabling/disabling jq.

- Slice 3 – Examples and tests
  - Add examples showing jq usage in predicates and transforms.
  - Add tests for jq expressions across supported sites.

