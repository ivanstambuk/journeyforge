# Use Case – Journey Metadata (Tags & Attributes)

Status: Draft | Last updated: 2025-11-21

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/metadata-from-payload/metadata-from-payload.md`

## Problem

We want to:
- Attach lightweight metadata to journeys so that:
  - Users can list “my open journeys”.
  - Operators can filter by journey name, phase, tenant, and correlation ids (order, payment, CRM case).
- Populate this metadata from:
  - The request payload (body).
  - Named headers (`X-Tenant-Id`, `X-Channel`, etc.).
  - The W3C `baggage` header.
- Do this in a spec-first way using `spec.metadata.bindings` plus existing DSL primitives (`context`, `httpBindings`, `transform`, `httpSecurity`), without assuming any implicit projection from `context` to attributes.

This use case focuses on how journey definitions shape both:
- `journey.attributes`, via `spec.metadata.bindings` evaluated once at journey start, reading directly from the start request, and
- (optionally) `context`, via `httpBindings` and `transform` states where the journey’s business logic needs those fields.

Any `transform` states shown below are for internal `context` shape only; they do not change how `journey.attributes` is populated.

## Relevant DSL Features

- `context` initialisation:
  - The start request body is deserialised as JSON and used as the initial `context` object.
- `httpBindings` (DSL §17):
  - `httpBindings.start.headersToContext` / `queryToContext` to bind inbound headers/query params into `context`.
- `transform` states:
  - Use DataWeave to normalise values into well-known `context` fields for later use.
- `metadata.tags` (DSL §2a and §2g):
  - Definition-level tags for journey definitions and `kind: Api` specs.
- HTTP security (DSL §18):
  - JWT/mTLS policies populate `context.auth.*` and can be used to derive `subjectId` and other identity attributes.

## Example – From Payload (Order & Customer IDs + Channel Tag)

Journey definition (conceptual):

Goal:
- Lift `order.id` and `customer.id` from the start payload and expose them as attributes on the journey instance via explicit bindings.
- Lift `channel` from the start payload and expose it as a dynamic tag on the journey instance.

Start request:

```json
{
  "order": { "id": "ord-123" },
  "customer": { "id": "cust-999" },
  "channel": "web"
}
```

DSL pattern:

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: metadata-from-payload
  version: 0.1.0
  description: >
    Demonstrates metadata bindings sourced from the start payload.
  tags:
    - self-service
    - kyc
spec:
  metadata:
    bindings:
      tags:
        fromPayload:
          - path: channel
      attributes:
        orderId:
          path: order.id
        customerId:
          path: customer.id

  start: doWork
  states:
    doWork:
      type: succeed
      outputVar: order
```

Behaviour:
- The caller sends a natural JSON payload.
- The engine initialises `context` from that payload.
- At journey start, `spec.metadata.bindings` is evaluated once:
  - `metadata.bindings.tags.fromPayload` reads `channel` from the start body and appends it as
    a tag.
  - `metadata.bindings.attributes.fromPayload` binds `order.id` and `customer.id` from the
    start request body into `journey.attributes.orderId` and `journey.attributes.customerId`.

Resulting metadata (conceptual):

```json
{
  "tags": ["self-service", "kyc", "web"],
  "attributes": {
    "orderId": "ord-123",
    "customerId": "cust-999"
  }
}
```

- If the journey logic also needs these values inside `context` (for example, `context.orderId`),
  authors can add a separate `transform` state to copy or normalise them; that transform is
  orthogonal to the attribute binding and does not change when `journey.attributes` is set.

## Example – From Headers (Tenant & Channel)

Journey definition (conceptual): 

Goal:
- Capture tenant and channel information from headers and expose them as attributes on the journey instance.
- Surface the channel as a dynamic tag, in addition to any definition-level tags.

Inbound request:

```http
POST /api/v1/journeys/metadata-from-headers/start
X-Tenant-Id: tenant-001
X-Channel: web
Content-Type: application/json

{
  "payload": { "foo": "bar" }
}
```

DSL pattern:

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: metadata-from-headers
  version: 0.1.0
  description: >
    Demonstrates metadata bindings sourced from HTTP headers.
  tags:
    - self-service
spec:
  metadata:
    bindings:
      tags:
        fromHeaders:
          - header: X-Channel
      attributes:
        tenantId:
          header: X-Tenant-Id
        channel:
          header: X-Channel

  start: doWork
  states:
    doWork:
      type: succeed
      outputVar: payload
```

Behaviour:
- At journey start, `spec.metadata.bindings` is evaluated once:
  - `metadata.bindings.tags.fromHeaders` reads `X-Channel` and appends its value as a tag.
  - `metadata.bindings.attributes.fromHeaders` binds the header values into
    `journey.attributes.tenantId` and `journey.attributes.channel`.

Resulting metadata (conceptual):

```json
{
  "tags": ["self-service", "web"],
  "attributes": {
    "tenantId": "tenant-001",
    "channel": "web"
  }
}
```

- If the journey logic also needs these values inside `context`, authors can add `httpBindings`
  and/or `transform` states as in the payload example; these are optional and do not affect
  when or how `journey.tags` / `journey.attributes` are populated.
- Definition tags (`metadata.tags`) still classify the journey definition itself; bindings
  control which values are exposed as attributes.

## Example – From Baggage (Correlation & Experiment)

Journey definition (conceptual): 

Goal:
- Use the W3C `baggage` header to capture correlation ids and experiment flags, and expose
  them as attributes on the journey instance.

Inbound request:

```http
POST /api/v1/journeys/metadata-from-baggage/start
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00
baggage: correlation_id=abc123,exp=checkout-a,journey_tag=onboarding-a
Content-Type: application/json

{
  "id": "req-123"
}
```

DSL pattern:

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: metadata-from-baggage
  version: 0.1.0
  description: >
    Demonstrates metadata bindings sourced from W3C baggage.
spec:
  metadata:
    bindings:
      tags:
        fromBaggage:
          - key: journey_tag
      attributes:
        correlationId:
          key: correlation_id
        experiment:
          key: exp

  start: doWork
  states:
    doWork:
      type: succeed
      outputVar: id
```

Behaviour:
- At journey start, `spec.metadata.bindings` is evaluated once:
  - `metadata.bindings.tags.fromBaggage` reads the `journey_tag` baggage entry and appends its
    value as a tag.
  - `metadata.bindings.attributes.fromBaggage` reads `correlation_id` and `exp` baggage entries
    and binds their values to `journey.attributes.correlationId` and
    `journey.attributes.experiment`.

Resulting metadata (conceptual):

```json
{
  "tags": ["onboarding-a"],
  "attributes": {
    "correlationId": "abc123",
    "experiment": "checkout-a"
  }
}
```

- If the journey logic also needs parsed baggage values inside `context`, authors can add
  `httpBindings.start.headersToContext` plus a `transform` state (similar to the earlier
  example) to produce `context.correlationId` and `context.experiment`; those steps remain
  orthogonal to how `journey.tags` and `journey.attributes` are computed.

## Variations

- **Tags from derived context:**
  - Dynamic tags can be derived from the start payload, headers, or `baggage` via
    `metadata.bindings.tags`. If journey logic also needs these values in `context`, use a
    `transform` state to normalise them (for example, `context.channel`, `context.segment`).
- **Multi-tenant + subject metadata:**
  - Combine the header and payload patterns above with JWT-based `httpSecurity`:
    - Use JWT policies to populate `context.auth.jwt.claims.sub`.
    - Use a `transform` state to normalise `subjectId`, `tenantId`, `channel` into top-level `context` fields.

These patterns conform to the current DSL surface and illustrate how to structure journey
definitions so that tag and attribute bindings are explicit, spec-driven, and evaluated only
once at journey start.
