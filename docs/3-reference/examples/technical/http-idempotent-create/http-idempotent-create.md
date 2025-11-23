# Technical Pattern – Idempotent create-if-not-exists

Status: Draft | Last updated: 2025-11-23

## Problem

Implement create-or-get semantics using GET+POST and explicit branching on HTTP status.

## Example – http-idempotent-create

Artifacts for this example:

- Journey: [http-idempotent-create.journey.yaml](http-idempotent-create.journey.yaml)
- OpenAPI: [http-idempotent-create.openapi.yaml](http-idempotent-create.openapi.yaml)
- Arazzo: [http-idempotent-create.arazzo.yaml](http-idempotent-create.arazzo.yaml)

