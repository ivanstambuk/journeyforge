# Technical Pattern – JWT scopes/roles authZ

Status: Draft | Last updated: 2025-11-25

## Problem

Use `jwtValidate:v1` to authenticate the caller, then enforce authorisation based on JWT scopes/roles before calling a downstream user API.

## Example – auth-jwt-scopes

Artifacts for this example:

- Journey: [auth-jwt-scopes.journey.yaml](auth-jwt-scopes.journey.yaml)
- OpenAPI: [auth-jwt-scopes.openapi.yaml](auth-jwt-scopes.openapi.yaml)
- Arazzo: [auth-jwt-scopes.arazzo.yaml](auth-jwt-scopes.arazzo.yaml)

