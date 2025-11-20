# JourneyForge DSL – Use Case Catalog

Status: Draft | Last updated: 2025-11-20

This catalog collects concrete JourneyForge use cases and links them to example workflows, OpenAPI exports, and DSL patterns. Each use case has its own page under `docs/2-how-to/use-cases/` with end‑to‑end samples.

## Use Cases

| Use case | Description | Example specs |
|----------|-------------|---------------|
| [Third‑party auth & propagation](auth-third-party.md) | Use inbound auth (JWT/mTLS/API key) and `httpBindings` to normalise identity into `context` and forward it to downstream services. | (TBD) |
| [Synchronous API endpoint (no journeys)](api-endpoint-sync.md) | Expose a single HTTP endpoint (`kind: Api`) that calls one or more downstream APIs and returns a composed result without journey ids or status polling. | [`http-chained-calls-api.workflow.yaml`](../../3-reference/examples/http-chained-calls-api.workflow.yaml) |
| [HTTP notification (fire-and-forget)](http-notify-audit.md) | Trigger best-effort audit/notification calls using `task.mode: notify` and continue without observing the HTTP outcome. | [`http-notify-audit.workflow.yaml`](../../3-reference/examples/http-notify-audit.workflow.yaml) |
| [Event publish to Kafka](event-publish-kafka.md) | Emit domain events to Kafka using `task.kind: eventPublish` with key/value mappers and JSON Schemas. | [`event-publish-kafka.workflow.yaml`](../../3-reference/examples/event-publish-kafka.workflow.yaml) |
| [Request multiplexing & composition](request-multiplexing.md) | Call multiple HTTP APIs and combine their responses, including chained calls and conditional composition. | [`http-chained-calls.workflow.yaml`](../../3-reference/examples/http-chained-calls.workflow.yaml), [`http-aggregate-errors.workflow.yaml`](../../3-reference/examples/http-aggregate-errors.workflow.yaml), [`http-conditional-composition.workflow.yaml`](../../3-reference/examples/http-conditional-composition.workflow.yaml) |
| [Trace/header propagation](header-propagation.md) | Capture inbound trace IDs and propagate them to all downstream HTTP tasks without repeating headers in every state. | [`http-header-interpolation.workflow.yaml`](../../3-reference/examples/http-header-interpolation.workflow.yaml) |
| [Cache‑aware profile lookup](cache-user-profile.md) | Use `transform` + `cacheGet`/`cachePut` to avoid redundant upstream calls by caching user data. | [`cache-user-profile.workflow.yaml`](../../3-reference/examples/cache-user-profile.workflow.yaml) |
| [External approval & callbacks](external-input-approval.md) | Pause journeys at `wait`/`webhook` states, resume on manual approval or third‑party callbacks, and expose `/steps/{stepId}` endpoints. | [`wait-approval.workflow.yaml`](../../3-reference/examples/wait-approval.workflow.yaml), [`payment-callback.workflow.yaml`](../../3-reference/examples/payment-callback.workflow.yaml) |
| [Error handling & Problem Details](error-handling-problem-details.md) | Detect upstream failures/timeouts, normalise them into RFC 9457 Problem Details, and either return or fail with mapped error codes. | [`http-failure-branch.workflow.yaml`](../../3-reference/examples/http-failure-branch.workflow.yaml), [`http-timeout-branch.workflow.yaml`](../../3-reference/examples/http-timeout-branch.workflow.yaml), [`http-problem-details.workflow.yaml`](../../3-reference/examples/http-problem-details.workflow.yaml) |
| [Parallel credit decision](parallel-credit-decision.md) | Call risk/limits/KYC services in parallel and make a combined approve/reject decision. | [`credit-decision-parallel.workflow.yaml`](../../3-reference/examples/credit-decision-parallel.workflow.yaml) |
| [Resilience policies & degraded mode](http-resilience-degrade.md) | Use httpResilience policies to retry unstable calls and fail with a degraded status if all attempts fail. | [`http-resilience-degrade.workflow.yaml`](../../3-reference/examples/http-resilience-degrade.workflow.yaml) |
| [Idempotent create‑if‑not‑exists](http-idempotent-create.md) | Implement create‑or‑get semantics using GET+POST and explicit branching on HTTP status. | [`http-idempotent-create.workflow.yaml`](../../3-reference/examples/http-idempotent-create.workflow.yaml) |
| [Transform‑only pipeline](transform-pipeline.md) | Run a multi‑step DataWeave transform pipeline without any HTTP calls. | [`transform-pipeline.workflow.yaml`](../../3-reference/examples/transform-pipeline.workflow.yaml) |
| [Multi‑tenant routing](multitenant-routing.md) | Route to different upstream APIs based on tenant id from headers. | [`multitenant-routing.workflow.yaml`](../../3-reference/examples/multitenant-routing.workflow.yaml) |
| [Named outcomes & reporting](named-outcomes.md) | Classify outcomes with spec.outcomes to distinguish success/failure reasons. | [`named-outcomes.workflow.yaml`](../../3-reference/examples/named-outcomes.workflow.yaml) |
| [Sync wrapper over wait](sync-wrapper-wait.md) | Use a wait state with timeout to represent long‑running operations with clear timeout semantics. | [`sync-wrapper-wait.workflow.yaml`](../../3-reference/examples/sync-wrapper-wait.workflow.yaml) |
| [Global compensation journey](global-compensation.md) | Attach a global compensation path that runs when a journey fails, times out, or is cancelled, using `spec.compensation` with access to final context and termination metadata. | [`http-compensation.workflow.yaml`](../../3-reference/examples/http-compensation.workflow.yaml) |
| [Subject-scoped self-service steps](subject-self-service.md) | Capture the subject from JWT at start, re-check it on follow-up steps, and fail with a security error on mismatch. | [`subject-step-guard.workflow.yaml`](../../3-reference/examples/subject-step-guard.workflow.yaml) (TBD) |

For each use case, the dedicated page explains:
- The problem and context.
- The relevant DSL features (`task`, `choice`, `transform`, `wait`/`webhook`, `parallel`, `spec.errors`, etc.).
- How the example workflow is structured, with key YAML snippets.
- How the per‑journey / per‑API OpenAPI export (`docs/3-reference/examples/oas/*.openapi.yaml`) surfaces the behaviour.
