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
