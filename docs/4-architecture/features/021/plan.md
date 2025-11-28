# Feature 021 – Cloud-Function Binding – Plan

Status: Draft | Last updated: 2025-11-25

## Increment 1 – DSL & spec design (this increment)
- [x] Introduce `spec.bindings.function` in `docs/3-reference/dsl.md`.
- [x] Capture cloud-function binding semantics in `docs/4-architecture/features/021/spec.md`.

## Increment 2 – Provider-specific mapping docs
- [ ] Add ops documentation for:
  - AWS Lambda + API Gateway / Function URLs → HTTP binding.
  - Google Cloud Functions / Cloud Run (HTTP) → HTTP binding.
  - Azure Functions (HTTP triggers) → HTTP binding.
- [ ] Describe how non-HTTP triggers (Pub/Sub, SQS, Service Bus, etc.) reuse queue-binding semantics via function wrappers.

## Increment 3 – Tooling & code generation (future)
- [ ] Define code generation patterns for creating provider-specific function wrappers from DSL definitions that declare `spec.bindings.function`.
- [ ] Ensure generated functions delegate to the engine’s HTTP/queue entrypoints without duplicating semantics.
