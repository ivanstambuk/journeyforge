# Technical Pattern – Synchronous API endpoint (no journeys)

Status: Draft | Last updated: 2025-11-23

## Problem

Expose a single HTTP endpoint (`kind: Api`) that calls one or more downstream APIs and returns a composed result without journey IDs or status polling.

## Example – http-chained-calls-api

Artifacts for this example:

- Journey: [http-chained-calls-api.journey.yaml](http-chained-calls-api.journey.yaml)
- OpenAPI: [http-chained-calls-api.openapi.yaml](http-chained-calls-api.openapi.yaml)

