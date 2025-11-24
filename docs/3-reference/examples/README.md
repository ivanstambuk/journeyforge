# DSL Examples

This directory contains example JourneyForge specs and related artifacts.

- Inline `spec.input.schema` / `spec.output.schema` blocks in these examples are JSON Schema 2020-12 and are authored to mirror OpenAPI 3.1 component schemas.
- All HTTP examples assume `resultVar` stores `{ status?, ok, headers, body, error? }` and that the journey runner supplies `context.inputId`.

Over time, examples are being partitioned into:

- `docs/3-reference/examples/technical/` – small, focused **technical patterns** that illustrate specific DSL features (for example named outcomes, HTTP error handling, timers, waits, webhooks, scheduling).
- `docs/3-reference/examples/business/` – richer **business journeys** that model end-to-end business flows with dedicated docs and diagrams.

Existing examples at the root of `docs/3-reference/examples/` (for example `http-success.journey.yaml`, `loan-application.journey.yaml`) will be migrated into these subdirectories in small increments.

For human-friendly documentation:

- Technical patterns are catalogued under `docs/2-how-to/use-cases/index.md`.
- Business journeys are catalogued under `docs/2-how-to/business-journeys/index.md` and documented in `docs/3-reference/examples/journeys/` (to be migrated alongside specs).

## Start modes and HTTP statuses – examples matrix

The Journeys API supports both synchronous (`startMode: sync`, HTTP 200) and
asynchronous (`startMode: async`, HTTP 202) starts (see ADR-0019 and the DSL
reference). Examples use these modes deliberately to illustrate patterns rather
than defaulting everything to 202.

The table below classifies each example journey by the **semantics of its
`/journeys/{journeyName}/start` call** (the first segment only). It is the
**authoritative map** for how individual example specs, OpenAPI, and docs
should be wired and whether they have been migrated to the new sync/async
semantics.

| Journey id | Category | Pattern | Start mode | HTTP start status | Migrated? |
|-----------|----------|---------|------------|-------------------|-----------|
| http-success | technical | Simple synchronous HTTP 200 | sync | 200 | Yes |
| http-post-json | technical | JSON POST with success path | sync | 200 | Yes |
| http-204-no-content | technical | 204 no-content response | sync | 200 | Yes |
| http-failure-branch | technical | HTTP failure branch handling | sync | 200 | Yes |
| http-aggregate-errors | technical | Multiple HTTP error aggregation | sync | 200 | Yes |
| http-conditional-composition | technical | Conditional fan-out composition | sync | 200 | Yes |
| http-content-check | technical | Content-based post-processing | sync | 200 | Yes |
| http-header-interpolation | technical | Header interpolation | sync | 200 | Yes |
| http-idempotent-create | technical | Idempotent create semantics | sync | 200 | Yes |
| http-timeout-branch | technical | HTTP timeout handling | sync | 200 | Yes |
| http-resilience-degrade | technical | Resilience / degrade behaviour | sync | 200 | Yes |
| http-notify-audit | technical | Notify + audit in-band | sync | 200 | No |
| http-cookie-jar | technical | HTTP cookie jar usage | sync | 200 | No |
| http-custom-error-envelope | technical | Custom error envelope mapping | sync | 200 | Yes |
| http-problem-details | technical | RFC 9457 problem details mapping | sync | 200 | Yes |
| http-put-delete | technical | PUT/DELETE operations | sync | 200 | Yes |
| http-chained-calls | technical | Chained outgoing HTTP calls | sync | 200 | Yes |
| http-chained-calls-api | technical | Chained calls with API binding | sync | 200 | Yes |
| cache-user-profile | technical | Cache lookup with HTTP fallback | sync | 200 | Yes |
| transform-pipeline | technical | Transform pipeline | sync | 200 | Yes |
| choice-multi-branch | technical | Choice with multiple branches | sync | 200 | Yes |
| named-outcomes | technical | Named outcomes (`spec.outcomes`) | sync | 200 | Yes |
| event-publish-kafka | technical | Kafka event publish | sync | 200 | No |
| metadata-from-payload | technical | Metadata sourced from payload | sync | 200 | Yes |
| multitenant-routing | technical | Multitenant routing | sync | 200 | Yes |
| subject-step-guard | technical | Subject-aware step guard | sync | 200 | No |
| auth-outbound-client-credentials | technical | Outbound client-credentials auth | sync | 200 | No |
| auth-user-info | technical | Authenticated user-info call | sync | 200 | Yes |
| credit-decision-parallel | technical | Parallel credit decision branches | sync | 200 | Yes |
| sync-wrapper-wait | technical | Sync wrapper with internal wait | sync | 200 | Yes |
| wait-approval | technical | Manual approval via wait step | sync | 200 | Yes |
| wait-multiple-callbacks | technical | Multiple callbacks via webhooks | sync | 200 | Yes |
| payment-callback | technical | Payment authorization + callback | sync | 200 | Yes |
| approval-loop | technical | Multi-actor approval loop | sync | 200 | No |
| notification-throttle | technical | Throttled notification schedule | sync | 200 | Yes |
| payment-reminder-with-timeout | technical | Reminder with timeout + wait | sync | 200 | Yes |
| poll-status-api | technical | Poll external status API | sync | 200 | No |
| recurring-payment | technical | Recurring payment with schedule | sync | 200 | Yes |
| subjourney-local-reuse | technical | Local subjourneys (propagate vs capture) | sync | 200 | Yes |
| async-fire-and-observe | technical | Async fire-and-observe (polling) | async | 202 | Yes |
| async-callback-sla | technical | Async callback + SLA race | async | 202 | Yes |
| b2b-purchase-order | business | B2B purchase order orchestration | sync | 200 | Yes |
| business-hours-payment-authorization | business | Business-hours payment auth | sync | 200 | Yes |
| customer-onboarding-kyc | business | Customer onboarding KYC | sync | 200 | Yes |
| customer-onboarding-kyc-kyb | business | Customer onboarding KYC + KYB | sync | 200 | Yes |
| kyc-async-timeout-fallback | business | KYC async with timeout fallback | async | 202 | Yes |
| async-report-generation | business | Async report generation (polling) | async | 202 | Yes |
| loan-application | business | Loan application journey | sync | 200 | Yes |
| high-value-transfer | business | High-value transfer with approvals | sync | 200 | Yes |
| email-verification | business | Email verification flow | sync | 200 | Yes |
| post-delivery-feedback | business | Post-delivery feedback collection | sync | 200 | Yes |
| insurance-claim-fnol-to-settlement | business | Insurance claim FNOL to settlement | sync | 200 | Yes |
| support-case-sla | business | Support case with SLA | sync | 200 | Yes |
| privacy-erasure-sla | business | Privacy erasure with SLA | sync | 200 | Yes |
| daily-orders-batch-close | business | Daily orders batch close | sync | 200 | Yes |
| monthly-invoicing-batch | business | Monthly invoicing batch | sync | 200 | Yes |
| subscription-lifecycle | business | Subscription lifecycle | sync | 200 | Yes |
| ecommerce-order-split-shipment | business | Ecommerce order split shipment | sync | 200 | Yes |
| travel-booking-bundle | business | Travel booking bundle | sync | 200 | Yes |
| market-open-order-submission | business | Market-open order submission | sync | 200 | Yes |
| healthcare-appointment-referral | business | Healthcare appointment referral | sync | 200 | Yes |
| document-signing-multi-signer | business | Multi-signer document signing | sync | 200 | Yes |

Authoring rules for examples:
- Labels in this table describe **only the `/start` call**:
  - **sync/200** – `POST /journeys/{journeyName}/start` returns HTTP 200 with
    either a `JourneyOutcome` (terminal) or a `JourneyStatus` for the first
    external-input state.
  - **async/202** – `POST /journeys/{journeyName}/start` returns HTTP 202 with
    a `JourneyStartResponse`; callers must use follow-up calls to see the first
    outcome or status.
- Internal waits, webhooks, timers, schedules, or long-running SLAs **after**
  the start response do not, by themselves, make a journey “async” in this
  table; only the `/start` status does.

## Outcome reasons in example journeys

Some business journeys expose domain-level “reason” information in their `spec.output.schema` (for example why a request was rejected or ineligible) in addition to the technical `JourneyOutcome.error` envelope.

Authoring guidance:
- Prefer **code + text pairs** for domain reasons:
  - For failures: `failureReasonCode` (stable machine code) + `failureReason` (human-readable text).
  - For rejections: `rejectionReasonCode` + `rejectionReason`.
  - For other domain outcomes (for example ineligibility, settlement decisions), follow the same pattern (`<kind>ReasonCode` + `<kind>Reason`).
- Keep codes stable and short (for example `ERASURE_ENGINE_ERROR`, `PRIVACY_DESK_REJECTED`, `SUBJECT_OR_REGION_INELIGIBLE`) and document them in journey-specific docs.
- Use the Problem Details-oriented `JourneyOutcome.error.{code,reason}` for **technical/runtime errors**; domain reason fields belong in the journey’s `output` schema and should not duplicate the technical error envelope.

## API Catalog – downstream OpenAPI used by `operationRef`

- apis/accounts.openapi.yaml
- apis/items.openapi.yaml
- apis/serviceA.openapi.yaml
- apis/serviceB.openapi.yaml
- apis/users.openapi.yaml
- apis/payments.openapi.yaml
- apis/fraud.openapi.yaml
- apis/sanctions.openapi.yaml
- apis/transfers.openapi.yaml
- apis/subscriptions.openapi.yaml
- apis/orders.openapi.yaml
- apis/inventory.openapi.yaml
- apis/shipping.openapi.yaml
- apis/purchase-orders.openapi.yaml
- apis/claims.openapi.yaml
- apis/flights.openapi.yaml
- apis/hotels.openapi.yaml
- apis/cars.openapi.yaml
- apis/kyc-checks.openapi.yaml
- apis/aml-screening.openapi.yaml
- apis/customers.openapi.yaml
- apis/providers.openapi.yaml

Per-API OpenAPI specs:

- oas/http-chained-calls-api.openapi.yaml
