# Technical Pattern – mTLS-only authN

Status: Draft | Last updated: 2025-11-25

## Problem

Use `mtlsValidate:v1` as an explicit journey state to validate client certificates and enforce a simple subject-based access rule, without relying on JWT.

## Example – auth-mtls-only

Artifacts for this example:

- Journey: [auth-mtls-only.journey.yaml](auth-mtls-only.journey.yaml)
- OpenAPI: [auth-mtls-only.openapi.yaml](auth-mtls-only.openapi.yaml)
- Arazzo: [auth-mtls-only.arazzo.yaml](auth-mtls-only.arazzo.yaml)

