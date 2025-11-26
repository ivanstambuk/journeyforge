# Technical Pattern – Cache-aware profile lookup

Status: Draft | Last updated: 2025-11-23

## Problem

Use `transform` + the cache task plugin (`task.kind: cache:v1`, `operation: get|put`) to avoid redundant upstream calls by caching user data.

## Example – cache-user-profile

Artifacts for this example:

- Journey: [cache-user-profile.journey.yaml](cache-user-profile.journey.yaml)
- OpenAPI: [cache-user-profile.openapi.yaml](cache-user-profile.openapi.yaml)
- Arazzo: [cache-user-profile.arazzo.yaml](cache-user-profile.arazzo.yaml)
