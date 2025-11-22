# ADR-0015 – DataWeave Reuse and External Modules

Date: 2025-11-22 | Status: Proposed

## Context

JourneyForge uses DataWeave 2.x as the canonical expression and transform language (see ADR-0002). Early drafts of the DSL and docs mentioned two forms of reuse:

- Inline expressions via `expr` in predicates and mappers.
- External modules via `exprRef` fields and DataWeave imports inside `expr` that point to `.dwl` files.

At the same time, the project constitution and spec-format decisions strongly prefer self-contained, reviewable specs:

- Journey definitions (`kind: Journey` / `kind: Api`) are the primary artefacts; everything else (plans, tasks, tests, code) follows specs.
- Specs should be easy to review in isolation, and canonical JSON snapshots should contain all behaviour that matters for validation and reasoning.

This ADR captures reuse scenarios where external DataWeave modules (referenced via `exprRef` or imports) might be attractive in the future, and then makes an explicit v1 decision **not** to support them at the DSL level.

Related:

- ADR-0002 – Expression Language: DataWeave.
- ADR-0003 – Error Model and RFC 9457 Problem Details.
- ADR-0004 – API Endpoints (`kind: Api`).
- ADR-0006 – Event Publish Tasks for Kafka.
- `docs/3-reference/dsl.md` – JourneyForge DSL Reference.
- `docs/4-architecture/open-questions.md` – see Q-003.

## Reuse Scenarios (Recorded for Future Design)

The following subsections describe concrete reuse scenarios that could benefit from shared DataWeave modules in a large installation (for example, hundreds or thousands of journeys). Each scenario includes an illustrative snippet showing how a future design **might** look if the DSL allowed external modules and `exprRef`. These snippets are for discussion only; they are **not** valid v1 DSL.

### 1. Global HTTP Error Normalisation

Many journeys call downstream HTTP APIs and want to normalise non-2xx responses into a canonical RFC 9457 Problem Details object before journey-specific handling (see ADR-0003).

A future reuse pattern could define a shared HTTP-error normaliser and reuse it via `exprRef` from multiple HTTP tasks:

```yaml
spec:
  mappers:
    httpErrorToProblem:
      lang: dataweave
      exprRef: "mappers/http-error-to-problem.dwl"   # hypothetical DW module

  errors:
    canonicalFormat: rfc9457
    normalisers:
      httpDefault:
        mapper:
          lang: dataweave
          exprRef: "mappers/http-error-to-problem.dwl"

states:
  call_upstream:
    type: task
    task:
      kind: httpCall
      operationRef: orders.getOrder
      resultVar: httpResult
      errorMapping:
        when: nonOk
        mapperRef: httpErrorToProblem
        target:
          kind: context
          path: problem
    next: handle_error
```

Example input and output (conceptual):

- Input (`result` object from an HTTP task when `ok == false`):

```json
{
  "status": 503,
  "ok": false,
  "headers": {
    "Content-Type": "application/json",
    "X-Request-Id": "abc-123"
  },
  "error": {
    "type": "UPSTREAM_UNAVAILABLE",
    "message": "Service temporarily unavailable"
  }
}
```

- Output (Problem Details stored at `context.problem`):

```json
{
  "type": "https://example.com/probs/http-error",
  "title": "UPSTREAM_UNAVAILABLE",
  "status": 503,
  "detail": "Service temporarily unavailable",
  "instance": "abc-123"
}
```

Example DataWeave module (hypothetical contents for `mappers/http-error-to-problem.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  type: "https://example.com/probs/http-error",
  title: result.error.type default "HTTP error",
  status: result.status default 500,
  detail: result.error.message default "Upstream error",
  instance: result.headers."X-Request-Id" default null
}
```

Potential benefit:

- One canonical HTTP-result → Problem Details mapping reused across journeys.

### 2. Per-API Error Mappers

For each downstream API (for example Orders, Payments, Users), journeys may want a tailored normaliser from that API’s error payload into the canonical Problem Details shape or into a slightly richer internal error object.

Hypothetical pattern:

```yaml
spec:
  mappers:
    ordersErrorToProblem:
      lang: dataweave
      exprRef: "apis/orders/error-to-problem.dwl"
    paymentsErrorToProblem:
      lang: dataweave
      exprRef: "apis/payments/error-to-problem.dwl"

states:
  call_orders:
    type: task
    task:
      kind: httpCall
      operationRef: orders.getOrder
      resultVar: ordersResult
      errorMapping:
        when: nonOk
        mapperRef: ordersErrorToProblem
        target:
          kind: context
          path: lastOrderError
    next: decide
```

Example input and output (conceptual):

- Input (`result` from the Orders API on error):

```json
{
  "status": 404,
  "ok": false,
  "body": {
    "errorCode": "ORDER_NOT_FOUND",
    "message": "Order 123 was not found"
  }
}
```

- Output (Problem Details stored at `context.lastOrderError`):

```json
{
  "type": "https://example.com/probs/orders-error",
  "title": "ORDER_NOT_FOUND",
  "status": 404,
  "detail": "Order 123 was not found",
  "extensions": {
    "orderId": "123"
  }
}
```

Example DataWeave module (hypothetical contents for `apis/orders/error-to-problem.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  type: "https://example.com/probs/orders-error",
  title: result.body.errorCode default "Orders error",
  status: result.status default 500,
  detail: result.body.message default result.error.message,
  extensions: {
    orderId: context.id
  }
}
```

Potential benefit:

- Per-API error semantics captured once and reused across journeys and teams.

### 3. Per-API Success / Body Projections

Journeys often need to project raw HTTP responses into canonical domain views (for example `OrderView`, `UserProfile`) that are reused across multiple journeys.

Hypothetical pattern:

```yaml
spec:
  mappers:
    ordersBodyToOrderView:
      lang: dataweave
      exprRef: "apis/orders/body-to-order-view.dwl"

states:
  call_orders:
    type: task
    task:
      kind: httpCall
      operationRef: orders.getOrder
      resultVar: ordersHttp
    next: project_order

  project_order:
    type: transform
    transform:
      mapperRef: ordersBodyToOrderView
      target:
        kind: var
      resultVar: order
    next: done
```

Example input and output (conceptual):

- Input (`result` from Orders API on success):

```json
{
  "status": 200,
  "ok": true,
  "body": {
    "id": "ORD-123",
    "status": "CONFIRMED",
    "customerId": "C-42",
    "totalAmount": 100.50,
    "currency": "EUR",
    "internalFlag": true
  }
}
```

- Output (canonical `OrderView` stored at `context.order`):

```json
{
  "id": "ORD-123",
  "status": "CONFIRMED",
  "customerId": "C-42",
  "totalAmount": 100.50,
  "currency": "EUR"
}
```

Example DataWeave module (hypothetical contents for `apis/orders/body-to-order-view.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  id: result.body.id,
  status: result.body.status,
  customerId: result.body.customerId,
  totalAmount: result.body.totalAmount,
  currency: result.body.currency
}
```

Potential benefit:

- Consistent domain-level projections of upstream APIs across journeys.

### 4. Observability and Loggable Errors

Large systems often want a consistent “loggable error” or “observability envelope” (for example correlation IDs, trace IDs, error codes, sources) constructed from `context` and attached to logs or events.

Hypothetical pattern:

```yaml
spec:
  mappers:
    buildLoggableError:
      lang: dataweave
      exprRef: "observability/loggable-error.dwl"

states:
  map_error_for_logging:
    type: transform
    transform:
      mapperRef: buildLoggableError
      target:
        kind: context
        path: lastLoggableError
    next: fail_with_error
```

Example input and output (conceptual):

- Input (`context.problem` and `context.correlationId` before mapping):

```json
{
  "problem": {
    "type": "https://example.com/probs/upstream-error",
    "title": "Upstream failure",
    "status": 502,
    "detail": "Gateway timeout"
  },
  "correlationId": "corr-123"
}
```

- Output (`context.lastLoggableError`):

```json
{
  "errorCode": "https://example.com/probs/upstream-error",
  "message": "Upstream failure",
  "status": 502,
  "correlationId": "corr-123"
}
```

Example DataWeave module (hypothetical contents for `observability/loggable-error.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  errorCode: context.problem.type default "unknown_error",
  message: context.problem.title default context.problem.detail,
  status: context.problem.status default 500,
  correlationId: context.correlationId default null
}
```

Potential benefit:

- Centralised observability shape reused across many journeys, events, or log sinks.

### 5. Identity and Subject Mapping

Journeys that rely on authenticated callers may need to turn tokens/claims into a canonical `{subjectId, tenantId, roles}` shape stored in `context`, reused everywhere identity is needed.

Hypothetical pattern:

```yaml
spec:
  mappers:
    principalFromClaims:
      lang: dataweave
      exprRef: "identity/principal-from-claims.dwl"

states:
  derive_principal:
    type: transform
    transform:
      mapperRef: principalFromClaims
      target:
        kind: context
        path: principal
    next: doWork
```

Example input and output (conceptual):

- Input (`context.rawTokenClaims` and headers):

```json
{
  "rawTokenClaims": {
    "sub": "user-123",
    "tenantId": "tenant-456",
    "roles": ["USER", "ADMIN"]
  },
  "headers": {
    "X-Subject-Id": "fallback-user",
    "X-Tenant-Id": "fallback-tenant"
  }
}
```

- Output (`context.principal`):

```json
{
  "subjectId": "user-123",
  "tenantId": "tenant-456",
  "roles": ["USER", "ADMIN"]
}
```

Example DataWeave module (hypothetical contents for `identity/principal-from-claims.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  subjectId: context.rawTokenClaims.sub default context.headers."X-Subject-Id",
  tenantId: context.rawTokenClaims.tenantId default context.headers."X-Tenant-Id",
  roles: (context.rawTokenClaims.roles default []) as Array<String>
}
```

Potential benefit:

- Consistent identity handling across journeys, aligned with ADR-0010 (initiating subject and principal).

### 6. Metadata / Tag Extraction

Journeys may want to populate tags and attributes from the payload or context (for example order IDs, customer IDs, channels) using a shared mapping.

Hypothetical pattern:

```yaml
spec:
  mappers:
    metadataFromPayload:
      lang: dataweave
      exprRef: "metadata/from-payload.dwl"

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

states:
  apply_metadata:
    type: transform
    transform:
      mapperRef: metadataFromPayload
      target:
        kind: context
        path: metadata
    next: doWork
```

Example input and output (conceptual):

- Input (`context` fragment with order and customer):

```json
{
  "order": { "id": "ORD-123" },
  "customer": { "id": "C-42" },
  "channel": "web"
}
```

- Output (`context.metadata`):

```json
{
  "tags": {
    "channel": "web"
  },
  "attributes": {
    "orderId": "ORD-123",
    "customerId": "C-42"
  }
}
```

Example DataWeave module (hypothetical contents for `metadata/from-payload.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  tags: {
    channel: context.channel
  },
  attributes: {
    orderId: context.order.id,
    customerId: context.customer.id default null
  }
}
```

Potential benefit:

- Shared metadata extraction logic across journeys, including enforcement of consistent tag/attribute semantics.

### 7. Canonical Domain Projections

When multiple upstream systems represent the same domain concept (for example Customer, Account), journeys may need canonical projections that hide per-system quirks.

Hypothetical pattern:

```yaml
spec:
  mappers:
    customerFromCrm:
      lang: dataweave
      exprRef: "domain/customer-from-crm.dwl"
    customerFromBilling:
      lang: dataweave
      exprRef: "domain/customer-from-billing.dwl"

states:
  project_customer_from_crm:
    type: transform
    transform:
      mapperRef: customerFromCrm
      target:
        kind: var
      resultVar: customer
    next: continue
```

Example input and output (conceptual):

- Input (`result` from CRM system on success):

```json
{
  "status": 200,
  "ok": true,
  "body": {
    "customerId": "C-42",
    "email": "alice@example.com",
    "status": "ACTIVE"
  }
}
```

- Output (`context.customer`):

```json
{
  "id": "C-42",
  "email": "alice@example.com",
  "status": "ACTIVE",
  "source": "crm"
}
```

Example DataWeave module (hypothetical contents for `domain/customer-from-crm.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  id: result.body.customerId,
  email: result.body.email,
  status: result.body.status,
  source: "crm"
}
```

Potential benefit:

- Canonical domain objects reused as building blocks across many journeys.

### 8. Event Envelope Builders

Event-driven integrations often require a consistent event envelope (for example CloudEvents-like or a house “domainEvent” structure) around various payloads, reused whenever journeys publish events (see ADR-0006).

Hypothetical pattern:

```yaml
spec:
  mappers:
    buildOrderEvent:
      lang: dataweave
      exprRef: "events/order-event.dwl"

states:
  publish_order_event:
    type: task
    task:
      kind: eventPublish
      eventPublish:
        transport: kafka
        topic: orders.events
        value:
          mapperRef: buildOrderEvent
    next: done
```

Example input and output (conceptual):

- Input (`context.order` and `context.journeyName`):

```json
{
  "order": {
    "id": "ORD-123",
    "status": "CREATED"
  },
  "journeyName": "order-create"
}
```

- Output (event payload published to Kafka):

```json
{
  "type": "orders.orderCreated",
  "id": "ORD-123",
  "source": "journey/order-create",
  "data": {
    "id": "ORD-123",
    "status": "CREATED"
  }
}
```

Example DataWeave module (hypothetical contents for `events/order-event.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  type: "orders.orderCreated",
  id: context.order.id,
  source: "journey/" ++ (context.journeyName default "orders"),
  data: context.order
}
```

Potential benefit:

- Single, governed event envelope reused across many journeys and topics.

### 9. Audit and Activity Events

Compliance or analytics may require audit events that state “who did what, when, on what resource”. These can be constructed from `context`, journey metadata, and outcome.

Hypothetical pattern:

```yaml
spec:
  mappers:
    auditEventFromContext:
      lang: dataweave
      exprRef: "audit/audit-event-from-context.dwl"

states:
  publish_audit:
    type: task
    task:
      kind: eventPublish
      eventPublish:
        transport: kafka
        topic: audit.events
        value:
          mapperRef: auditEventFromContext
    next: done
```

Example input and output (conceptual):

- Input (`context.principal`, `context.audit`, and `context` metadata):

```json
{
  "principal": { "subjectId": "user-123" },
  "audit": {
    "action": "CREATE",
    "resource": "order:ORD-123",
    "outcome": "SUCCESS"
  },
  "journeyName": "order-create",
  "correlationId": "corr-123"
}
```

- Output (audit event payload):

```json
{
  "actor": "user-123",
  "action": "CREATE",
  "resource": "order:ORD-123",
  "outcome": "SUCCESS",
  "metadata": {
    "journeyName": "order-create",
    "correlationId": "corr-123"
  }
}
```

Example DataWeave module (hypothetical contents for `audit/audit-event-from-context.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  actor: context.principal.subjectId,
  action: context.audit.action,
  resource: context.audit.resource,
  outcome: context.audit.outcome,
  metadata: {
    journeyName: context.journeyName,
    correlationId: context.correlationId default null
  }
}
```

Potential benefit:

- Shared, consistent audit event structure across journeys and domains.

### 10. PII Scrubbing and Redaction

Journeys may need to remove or mask sensitive data from error payloads, logs, or outbound events before they leave the engine.

Hypothetical pattern:

```yaml
spec:
  mappers:
    scrubPiiFromProblem:
      lang: dataweave
      exprRef: "security/scrub-pii-from-problem.dwl"

states:
  normalise_error:
    type: transform
    transform:
      mapper:
        lang: dataweave
        expr: |
          // build Problem Details from HTTP result
          ...
      target:
        kind: context
        path: problem
    next: scrub_pii

  scrub_pii:
    type: transform
    transform:
      mapperRef: scrubPiiFromProblem
      target:
        kind: context
        path: problem
    next: fail_with_problem
```

Example input and output (conceptual):

- Input (`context.problem` before scrubbing):

```json
{
  "type": "https://example.com/probs/upstream-error",
  "title": "Upstream failure",
  "status": 500,
  "detail": "User john.doe@example.com could not be loaded",
  "extensions": {
    "userEmail": "john.doe@example.com"
  }
}
```

- Output (`context.problem` after scrubbing):

```json
{
  "type": "https://example.com/probs/upstream-error",
  "title": "Upstream failure",
  "status": 500,
  "detail": "*** redacted ***",
  "extensions": {
    "userEmail": "john.doe@example.com",
    "hasPii": true
  }
}
```

Example DataWeave module (hypothetical contents for `security/scrub-pii-from-problem.dwl`):

```dwl
%dw 2.0
output application/json
---
context.problem ++ {
  detail: "*** redacted ***",
  extensions: (context.problem.extensions default {}) ++ {
    hasPii: true
  }
}
```

Potential benefit:

- Centralised redaction logic reused wherever sensitive data may appear.

### 11. Version and Adaptor Mappers

When migrating from legacy APIs to new ones or between schema versions, journeys may need adaptors between versions (for example v1 → v2).

Hypothetical pattern:

```yaml
spec:
  mappers:
    orderV1ToV2:
      lang: dataweave
      exprRef: "adapters/order-v1-to-v2.dwl"

states:
  adapt_order:
    type: transform
    transform:
      mapperRef: orderV1ToV2
      target:
        kind: var
      resultVar: orderV2
    next: call_new_api
```

Example input and output (conceptual):

- Input (`context.orderV1`):

```json
{
  "id": "ORD-123",
  "total": 120.0,
  "currency": "USD",
  "state": "PAID"
}
```

- Output (`context.orderV2`):

```json
{
  "id": "ORD-123",
  "version": "v2",
  "amount": 120.0,
  "currency": "USD",
  "status": "PAID"
}
```

Example DataWeave module (hypothetical contents for `adapters/order-v1-to-v2.dwl`):

```dwl
%dw 2.0
output application/json
---
{
  id: context.orderV1.id,
  version: "v2",
  amount: context.orderV1.total,
  currency: context.orderV1.currency,
  status: context.orderV1.state
}
```

Potential benefit:

- Shared adaptors that encapsulate version differences and are reused across journeys.

## Decision

For v1 of the JourneyForge DSL, we **ban** external DataWeave modules and `exprRef` references in the DSL surface:

- DSL fields:
  - The DSL MUST NOT support `exprRef` fields anywhere (for example in `transform.mapper`, `choice` predicates, HTTP `errorMapping`, event `value.mapper`, `wait.apply`, `webhook.apply`, or any other mapper surfaces).
  - All DataWeave expressions in the DSL MUST be authored inline via `expr`.
- DataWeave imports:
  - Authors MUST NOT rely on importing external `.dwl` modules from within `expr` for reusable behaviour in v1.
  - The supported and documented model is that each journey spec is self-contained from the perspective of behaviour described by the DSL.
- Reuse mechanisms:
  - Reuse within a journey MUST use DSL constructs such as `spec.mappers` and `mapperRef`, with inline `expr`.
  - Cross-journey reuse, when needed, SHOULD be accomplished via higher-level mechanisms (for example spec templates, generators, or copy-and-adapt patterns), not via DSL-level references to external modules.
- Error handling:
  - `spec.errors` remains strictly inline-only as already defined in the DSL reference:
    - `normalisers` and `envelope` mappers MUST use inline `expr`.
    - Cross-journey error mappings via shared `.dwl` files are out of scope for v1.

This decision answers Q-003 in `docs/4-architecture/open-questions.md` with Option A: keep v1 strictly inline-only and revisit external module/import support only after we have more evidence from real-world reuse needs.

## Consequences

Positive:

- Self-contained specs:
  - Journey definitions remain the primary, self-contained artefacts; reviewers and tools can understand journey behaviour from the spec alone, without chasing `.dwl` modules.
  - Canonical JSON snapshots and validation tooling can reason about all expressions without external dependencies.
- Simpler DSL surface:
  - The DSL does not need to define path semantics, resolution rules, or lifecycle for `.dwl` modules.
  - Validators can enforce a simple rule: mappers and predicates use `expr` only, never `exprRef`.
- Governance and security:
  - It is easier to audit and reason about what expressions run inside the engine when all of them live directly in specs.
  - There is no need to manage a separate “module library” lifecycle or worry about untracked changes to `.dwl` files.

Negative / trade-offs:

- Reuse friction:
  - Large installations with many journeys cannot yet factor shared transformations into centrally managed DW modules.
  - Teams may need to duplicate or generate common mappers across specs until a richer reuse mechanism is designed.
- No first-class module system:
  - Reuse scenarios recorded in this ADR (global error normalisation, per-API mappers, event envelopes, identity/metadata extraction, PII scrubbing, adaptors, etc.) remain manual for now.
  - Any future module/import system must be designed carefully to preserve the benefits of self-contained specs while addressing these reuse needs.

Future work:

- When there is stronger evidence from real deployments that a module system is warranted, we can:
  - Revisit Q-003 in `docs/4-architecture/open-questions.md`.
  - Propose a new ADR that designs an explicit, data-modelled reuse mechanism (for example named mapper libraries or DSL-level modules) rather than reintroducing raw `exprRef` to arbitrary `.dwl` files.
  - Update ADR-0002 and the DSL reference to describe that mechanism and its constraints.
