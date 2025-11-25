# Technical Pattern – Combined JWT + mTLS authN/authZ

Status: Draft | Last updated: 2025-11-25

## Problem

Require both a valid client certificate and a valid JWT on the same entry path, then enforce a simple combined authorisation rule via expressions over `context.auth.mtls.*` and `context.auth.jwt.*`.

## Example – auth-jwt-mtls

Artifacts for this example:

- Journey: [auth-jwt-mtls.journey.yaml](auth-jwt-mtls.journey.yaml)
- OpenAPI: [auth-jwt-mtls.openapi.yaml](auth-jwt-mtls.openapi.yaml)
- Arazzo: [auth-jwt-mtls.arazzo.yaml](auth-jwt-mtls.arazzo.yaml)

