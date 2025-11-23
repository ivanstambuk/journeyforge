# Technical Pattern – External approval & callbacks

Status: Draft | Last updated: 2025-11-23

## Problem

Pause journeys at `wait`/`webhook` states, resume on manual approval or third‑party callbacks, and expose `/steps/{stepId}` endpoints.

## Example – wait-approval

Artifacts for this example:

- Journey: [wait-approval.journey.yaml](wait-approval.journey.yaml)
- OpenAPI: [wait-approval.openapi.yaml](wait-approval.openapi.yaml)
- Arazzo: [wait-approval.arazzo.yaml](wait-approval.arazzo.yaml)

