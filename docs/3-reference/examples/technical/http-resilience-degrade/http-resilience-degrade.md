# Technical Pattern – Resilience policies & degraded mode

Status: Draft | Last updated: 2025-11-23

## Problem

Use `httpResilience` policies to retry unstable calls and fail with a degraded status if all attempts fail.

## Example – http-resilience-degrade

Artifacts for this example:

- Journey: [http-resilience-degrade.journey.yaml](http-resilience-degrade.journey.yaml)
- OpenAPI: [http-resilience-degrade.openapi.yaml](http-resilience-degrade.openapi.yaml)
- Arazzo: [http-resilience-degrade.arazzo.yaml](http-resilience-degrade.arazzo.yaml)

