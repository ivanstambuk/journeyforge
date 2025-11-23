# Technical Pattern – Trace/header propagation

Status: Draft | Last updated: 2025-11-23

## Problem

Capture inbound trace IDs and propagate them to all downstream HTTP tasks without repeating headers in every state.

## Example – http-header-interpolation

Artifacts for this example:

- Journey: [http-header-interpolation.journey.yaml](http-header-interpolation.journey.yaml)
- OpenAPI: [http-header-interpolation.openapi.yaml](http-header-interpolation.openapi.yaml)
- Arazzo: [http-header-interpolation.arazzo.yaml](http-header-interpolation.arazzo.yaml)

