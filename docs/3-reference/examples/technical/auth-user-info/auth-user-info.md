# Technical Pattern – Third‑party auth & propagation

Status: Draft | Last updated: 2025-11-23

## Problem

Use inbound auth (JWT/mTLS/API key) and HTTP binding (`spec.bindings.http`) to normalise identity into `context`, then use outbound OAuth2 client credentials for downstream calls.

## Example – auth-user-info

Artifacts for this example:

- Journey: [auth-user-info.journey.yaml](auth-user-info.journey.yaml)
- OpenAPI: [auth-user-info.openapi.yaml](auth-user-info.openapi.yaml)
- Arazzo: [auth-user-info.arazzo.yaml](auth-user-info.arazzo.yaml)
