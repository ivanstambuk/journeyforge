# DSL Examples (DataWeave predicates)

- http-success.journey.yaml – routes to success when `api.ok == true`.
- http-failure-branch.journey.yaml – routes to failure when `api.ok == false`.
- http-content-check.journey.yaml – combines HTTP status/content checks via DataWeave.

All examples assume `resultVar` stores the structured HTTP result object `{ status?, ok, headers, body, error? }` and that the journey runner supplies `context.inputId`.
- http-post-json.journey.yaml – POST with JSON body and 201 check.
- http-header-interpolation.journey.yaml – inject `traceparent` header from context and verify echo.
- http-timeout-branch.journey.yaml – branch on timeout via `api.error.type == 'TIMEOUT'`.
- choice-multi-branch.journey.yaml – three-branch decision: approved/review/technical fail/default.
- http-put-delete.journey.yaml – update an item via PUT, then DELETE it; branch on each result.
- http-chained-calls.journey.yaml – GET a user, then POST an account using values from the first response.
- http-conditional-composition.journey.yaml – route to alternate endpoint and headers/body based on context.
- http-204-no-content.journey.yaml – expect 204 No Content and succeed if seen.
- http-aggregate-errors.journey.yaml – call A then B; fail if either call failed.
- http-problem-details.journey.yaml – normalise downstream errors into RFC 9457 Problem Details; can either succeed with a Problem document or fail with a mapped `errorCode`.
- auth-user-info.journey.yaml – JWT-authenticated user lookup using httpSecurity + httpBindings.
- credit-decision-parallel.journey.yaml – call risk/limits/KYC in parallel and join results into a credit decision.
- http-resilience-degrade.journey.yaml – unstable upstream with resilience policy and degraded failure mode.
- http-idempotent-create.journey.yaml – create-or-get flow using GET+POST with idempotent semantics.
- transform-pipeline.journey.yaml – multi-step DataWeave transform pipeline for orders.
- multitenant-routing.journey.yaml – route to different upstreams based on tenant id.
- named-outcomes.journey.yaml – classify outcomes via spec.outcomes (low/high amounts, business rule failure).
- sync-wrapper-wait.journey.yaml – wait state with timeout modelling a long-running operation.
 - http-notify-audit.journey.yaml – fire-and-forget HTTP notification using `task.mode: notify` for audit-style side effects.
 - event-publish-kafka.journey.yaml – publish an ORDER_UPDATED event to Kafka using `task.kind: eventPublish` with key/value schemas.
 - http-chained-calls-api.journey.yaml – synchronous API endpoint variant of http-chained-calls using `kind: Api`.
- http-compensation.journey.yaml – order-style journey definition with a global compensation journey defined via `spec.compensation`.
- auth-outbound-client-credentials.journey.yaml – outbound OAuth2 client-credentials auth using `spec.policies.httpClientAuth`.

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

- payment-callback.journey.yaml – payment with webhook callback and shared secret.
- oas/payment-callback.openapi.yaml
- apis/payments.openapi.yaml
- cache-user-profile.journey.yaml – cache-aware profile lookup using transform + cacheGet/cachePut.
- oas/cache-user-profile.openapi.yaml
Per-API OpenAPI specs:
- oas/http-chained-calls-api.openapi.yaml
