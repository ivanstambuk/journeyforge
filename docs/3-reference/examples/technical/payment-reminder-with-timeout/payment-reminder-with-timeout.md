# Technical Pattern – Timer-based timeout with parallel + timer

Status: Draft | Last updated: 2025-11-23

## Problem

Use `timer` and `parallel` to model “user action OR timeout after N” without overloading `wait.timeoutSec` or relying on external schedulers.

## Example – payment-reminder-with-timeout

Artifacts for this example:

- Journey: [payment-reminder-with-timeout.journey.yaml](payment-reminder-with-timeout.journey.yaml)
- OpenAPI: [payment-reminder-with-timeout.openapi.yaml](payment-reminder-with-timeout.openapi.yaml)
- Arazzo: [payment-reminder-with-timeout.arazzo.yaml](payment-reminder-with-timeout.arazzo.yaml)

