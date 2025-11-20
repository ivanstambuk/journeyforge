# DSL Examples (DataWeave predicates)

- http-success.workflow.yaml – routes to success when `api.ok == true`.
- http-failure-branch.workflow.yaml – routes to failure when `api.ok == false`.
- http-content-check.workflow.yaml – combines HTTP status/content checks via DataWeave.

All examples assume `resultVar` stores the structured HTTP result object `{ status?, ok, headers, body, error? }` and that the workflow runner supplies `context.inputId`.
- http-post-json.workflow.yaml – POST with JSON body and 201 check.
- http-header-interpolation.workflow.yaml – inject `traceparent` header from context and verify echo.
- http-timeout-branch.workflow.yaml – branch on timeout via `api.error.type == 'TIMEOUT'`.
- choice-multi-branch.workflow.yaml – three-branch decision: approved/review/technical fail/default.
- http-put-delete.workflow.yaml – update an item via PUT, then DELETE it; branch on each result.
- http-chained-calls.workflow.yaml – GET a user, then POST an account using values from the first response.
- http-conditional-composition.workflow.yaml – route to alternate endpoint and headers/body based on context.
- http-204-no-content.workflow.yaml – expect 204 No Content and succeed if seen.
- http-aggregate-errors.workflow.yaml – call A then B; fail if either call failed.
- http-problem-details.workflow.yaml – normalise downstream errors into RFC 9457 Problem Details; can either succeed with a Problem document or fail with a mapped `errorCode`.
- auth-user-info.workflow.yaml – JWT-authenticated user lookup using httpSecurity + httpBindings.
- credit-decision-parallel.workflow.yaml – call risk/limits/KYC in parallel and join results into a credit decision.
- http-resilience-degrade.workflow.yaml – unstable upstream with resilience policy and degraded failure mode.
- http-idempotent-create.workflow.yaml – create-or-get flow using GET+POST with idempotent semantics.
- transform-pipeline.workflow.yaml – multi-step DataWeave transform pipeline for orders.
- multitenant-routing.workflow.yaml – route to different upstreams based on tenant id.
- named-outcomes.workflow.yaml – classify outcomes via spec.outcomes (low/high amounts, business rule failure).
- sync-wrapper-wait.workflow.yaml – wait state with timeout modelling a long-running operation.
 - http-notify-audit.workflow.yaml – fire-and-forget HTTP notification using `task.mode: notify` for audit-style side effects.
 - event-publish-kafka.workflow.yaml – publish an ORDER_UPDATED event to Kafka using `task.kind: eventPublish` with key/value schemas.
 - http-chained-calls-api.workflow.yaml – synchronous API endpoint variant of http-chained-calls using `kind: Api`.
 - http-compensation.workflow.yaml – order-style workflow with a global compensation journey defined via `spec.compensation`.

OpenAPI (per-journey) examples:
- oas/http-success.openapi.yaml
- oas/http-chained-calls.openapi.yaml

Generic Journeys API:
- ../openapi/journeys.openapi.yaml

Per-journey OpenAPI specs:
- oas/choice-multi-branch.openapi.yaml
- oas/http-204-no-content.openapi.yaml
- oas/http-aggregate-errors.openapi.yaml
- oas/http-chained-calls.openapi.yaml
- oas/http-conditional-composition.openapi.yaml
- oas/http-content-check.openapi.yaml
- oas/http-failure-branch.openapi.yaml
- oas/http-header-interpolation.openapi.yaml
- oas/http-post-json.openapi.yaml
- oas/http-put-delete.openapi.yaml
- oas/http-success.openapi.yaml
- oas/http-timeout-branch.openapi.yaml
- oas/http-problem-details.openapi.yaml
- oas/auth-user-info.openapi.yaml
- oas/credit-decision-parallel.openapi.yaml
- oas/http-resilience-degrade.openapi.yaml
- oas/http-idempotent-create.openapi.yaml
- oas/transform-pipeline.openapi.yaml
- oas/multitenant-routing.openapi.yaml
- oas/named-outcomes.openapi.yaml
- oas/sync-wrapper-wait.openapi.yaml

API Catalog:
- apis/accounts.openapi.yaml
- apis/items.openapi.yaml
- apis/serviceA.openapi.yaml
- apis/serviceB.openapi.yaml
- apis/users.openapi.yaml

- oas/wait-approval.openapi.yaml

- payment-callback.workflow.yaml – payment with webhook callback and shared secret.
- oas/payment-callback.openapi.yaml
- apis/payments.openapi.yaml
- cache-user-profile.workflow.yaml – cache-aware profile lookup using transform + cacheGet/cachePut.
- oas/cache-user-profile.openapi.yaml
Per-API OpenAPI specs:
- oas/http-chained-calls-api.openapi.yaml
