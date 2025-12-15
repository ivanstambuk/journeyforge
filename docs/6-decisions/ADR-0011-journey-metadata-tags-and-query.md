# ADR-0011 – Journey Metadata (Tags & Attributes) and Query Model

Date: 2025-11-20 | Status: Proposed

## Context

JourneyForge needs a consistent way to attach lightweight metadata to journey definitions and individual
journey instances so that:
- Users can list “my open journeys”.
- Operators can filter journeys by tenant, journey name, status, and correlation ids (order, payment, CRM case).
- The platform can eventually enforce policies such as per-subject concurrency limits and
data classification rules.

Today, the only structured data surface that the DSL defines is the mutable journey
`context`. That is appropriate for business logic but problematic for platform-level
features:
- There is no stable, cross-journey location for identity, tenant, or correlation keys.
- `context` is intentionally mutable, making it unsafe as the source of truth for ownership
  or policy enforcement.

Existing ADRs cover adjacent decisions:
- ADR-0001: spec format and snapshots.
- ADR-0003: error model and Problem Details.
- ADR-0004: `kind: Api` endpoints.
- ADR-0007: execution deadlines.
- ADR-0008: global compensation journeys.
- ADR-0009: HATEOAS links for journeys.
- ADR-0010: “Subject and Principal Identity”, which explicitly rejected introducing a
  **first-class** subject field in the DSL or Journeys envelopes, and recommended treating
  subject identity as platform-specific metadata.

We now want:
- A standard way to classify journey definitions and journey instances using tags.
- A small, explicit set of instance-level attributes for identity, tenant, and correlation.
- A query model for listing journeys based on those attributes and tags.
- A way to support mixed-mode authentication (JWT required vs optional) without breaking
  self-service “my journeys” semantics or introducing a first-class subject field.

## Decision

We introduce a metadata model for journeys with three layers plus supporting configuration and
query conventions.

### 1. Definition-level tags (`metadata.tags`)

Journey and API specs may declare tags under `metadata.tags`:

- Shape:
  - `metadata.tags: string[]` on both `kind: Journey` and `kind: Api`.
- Semantics:
  - Tags are small sets of short, non-sensitive identifiers (no PII/secrets), with a
    recommendation to use `kebab-case` (for example, `self-service`, `kyc`, `pii`,
    `financial`, `credit-decision`).
  - Definition tags classify the spec itself: domain, criticality, data-classification, etc.
- Uses:
  - Discovery and grouping in docs, CLI, and admin/UIs.
  - OpenAPI export (for example, operation tags).
  - Coarse-grained governance and reporting.
- Limits:
  - The maximum number of tags per spec is controlled via the `MetadataLimits` document
    (see below), with a recommended default of ≤ 10.

### 2. Instance-level tags and attributes

The engine’s journey model gains two metadata surfaces:
- `journey.tags: string[]`
- `journey.attributes: Map<String,String>`

#### 2.1 Instance tags (`journey.tags`)

- Semantics:
  - Instance tags are derived from:
    - Definition tags (`metadata.tags`).
    - Explicit tag sourcing rules in the journey spec (`spec.metadata.bindings.tags`) that can lift
      values from payload, headers, or W3C `baggage`.
      - Clients **do not** set tags directly in the start payload; they send only the normal
        JSON body. Tags are derived by the engine according to the spec.
- Use cases:
  - Filter journeys by high-level labels (`self-service`, `kyc`, `onboarding`, etc.).
  - Drive dashboards and coarse policy (for example, “all `kyc` journeys in `RUNNING` phase”).
- Immutability:
  - For v1, instance tags are immutable after the journey is started.
  - Post-v1 enrichment (if introduced) must be append-only (new tags only, no removals).
- Limits:
  - Instance tag cardinality and maximum value length are controlled by `MetadataLimits`,
    with recommended defaults of ≤ 16 tags and ≤ 40 characters per tag.

#### 2.2 Instance attributes (`journey.attributes`)

- Semantics:
  - `attributes` is a small map for queryable metadata and correlation:
    - Identity: `subjectId`.
    - Multi-tenant scoping: `tenantId`.
    - Channel: `channel` (for example, `web`, `mobile`).
    - Initiator: `initiatedBy` (`user`, `system`, `admin`).
    - Correlation: `orderId`, `paymentIntentId`, `crmCaseId`, etc.
    - Attributes are populated by the engine and/or journey logic based on explicit, visible configuration; clients
      never send an `attributes` field.
  - Sourcing inputs:
    - Start request payload/headers/W3C `baggage` via `spec.metadata.bindings`.
    - Journey-authored authentication/authorisation logic:
      - Auth task plugins (`jwtValidate:v1`, `mtlsValidate:v1`, `apiKeyValidate:v1`) project validated signals into
        `context.auth.*`, and
      - Journeys explicitly persist selected values into `journey.attributes` using write-once attribute writes (DSL
        `transform.target.kind: attributes`).
- Reserved attribute keys:
  - `subjectId` – canonical owner identity, when available, for self-service queries and per-subject limits.
  - `tenantId` – logical tenant id.
  - `channel` – origin channel.
  - `initiatedBy` – `user` / `system` / `admin`.
  - Additional correlation keys (for example, `orderId`, `paymentIntentId`, `crmCaseId`)
    are recommended and may be reserved by documentation rather than schema.
- Immutability:
  - For v1, attribute keys are write-once:
    - Bindings and journeys MAY set an attribute key only when it is absent.
    - Attempts to overwrite an existing key are rejected (no silent overwrite).
  - This allows anonymous start → authenticated completion journeys to bind `subjectId` later (for example after an
    in-journey login step) while keeping attributes stable for querying once set.
  - Future enrichment, if introduced beyond write-once, must be explicitly specified and auditable.
- Limits:
  - Attribute cardinality and key/value lengths are controlled by `MetadataLimits`, with
    recommended defaults of ≤ 16 keys, key length ≤ 32 characters, value length ≤ 256
    characters.

This model respects ADR-0010 by:
- Keeping subject identity in `attributes.subjectId` (generic metadata) rather than adding a
  new first-class `subject` field to the DSL or Journeys envelopes.
- Allowing identity-aware features without changing the core DSL surface.

### 3. Attribute and tag sourcing in journey definitions

Journey definitions declare how to populate tags and attributes from the start request
payload, headers, and `baggage` using an explicit bindings block under `spec.metadata`:

```yaml
spec:
  metadata:
    bindings:
      tags:
        fromPayload:
          - path: channel
          - path: segment
        fromHeaders:
          - header: X-Journey-Tag
        fromBaggage:
          - key: journey_tag

      attributes:
        fromPayload:
          orderId:
            path: order.id
          customerId:
            path: customer.id
        fromHeaders:
          tenantId:
            header: X-Tenant-Id
          channel:
            header: X-Channel
        fromBaggage:
          correlationId:
            key: correlation_id
          experiment:
            key: exp
```

- Only journey-definition-local configuration is supported for v1; there are no shared/global
  metadata policies.
- The engine evaluates these bindings exactly once per journey instance, when processing the
  start request for a `kind: Journey` journey, and uses them to populate `journey.tags` and
  `journey.attributes` subject to `MetadataLimits`.
- Metadata bindings are one source of tags/attributes. Journeys may also set attributes explicitly in-graph using
  write-once attribute writes (`transform.target.kind: attributes`), typically after auth task plugins.

### 4. Mixed-mode JWT validation and anonymous subjects

To support journeys that can be invoked both with and without JWTs, JWT validation is modelled via the `jwtValidate:v1` task plugin and engine configuration profiles rather than `kind: jwt` HTTP security policies.

- JWT validation profiles define issuer/audience/JWKS settings and other claim constraints. The DSL does not define a
  dedicated security policy block; validation and token sourcing details are engine configuration concerns.

Subject mapping for journeys:
- The engine does **not** implicitly derive `attributes.subjectId` from JWT claims.
- Journeys that want to bind a canonical owner identity SHOULD:
  - Validate tokens explicitly via `jwtValidate:v1`, and
  - Persist the chosen owner identity explicitly via a write-once attribute write (for example mapping
    `context.auth.jwt.claims.sub` to `attributes.subjectId`).
- When no authenticated subject is available (missing token, invalid token, or a subject treated as anonymous by the
  journey), `attributes.subjectId` remains unset.

This builds on ADR-0010 by still avoiding a first-class subject field and instead using generic attributes and JWT validation configuration.

### 5. `MetadataLimits` configuration document

To avoid hard-coded limits in code while keeping behaviour predictable, we introduce a small
configuration document that is part of the DSL/config surface:

```yaml
apiVersion: v1
kind: MetadataLimits
metadata:
  name: metadata-limits
spec:
  definitionTags:
    maxCount: 10

  instanceTags:
    maxCount: 16
    maxLength: 40

  attributes:
    maxKeys: 16
    maxKeyLength: 32
    maxValueLength: 256
```

- The engine MUST read this document at startup; if missing, it:
  - Either fail fast with a clear error, or
  - Use documented built-in defaults equivalent to the example above, with strong guidance
    to make limits explicit for production.
- Validation:
  - Workflow specs and journey operations use `MetadataLimits` to validate:
    - `metadata.tags` length.
    - Number and length of instance tags.
    - Number of attribute keys and key/value lengths.

### 6. Query model for journeys

We standardise a simple query model for listing journeys, backed by tags and attributes:

- Endpoint:
  - `GET /api/v1/journeys` – operator-oriented listing.
  - This ADR does not define a dedicated self-service listing endpoint (for example `GET /api/v1/my/journeys`).
    Any “my journeys” experience is provided via the same listing endpoint with platform-level authn/authz and
    filtering semantics, or via future, explicitly specified API surfaces.
- Filters (thin shims over fields/attributes):
  - `journeyName` – filters by journey name.
  - `phase` – `RUNNING`, `SUCCEEDED`, `FAILED`.
  - `subjectId` – maps to `attributes.subjectId`.
  - `tenantId` – maps to `attributes.tenantId`.
  - `tag` – repeatable; all specified tags must be present (AND semantics).
  - Correlation ids:
    - `orderId` → `attributes.orderId`.
    - `paymentIntentId` → `attributes.paymentIntentId`.
    - `crmCaseId` → `attributes.crmCaseId`.
- Semantics:
  - All provided filters are combined with logical AND.
  - These filters map directly to indices over `journeyName`, `phase`, tags, and selected
    attributes.

This yields a minimal but useful query surface aligned with the metadata model.

## Consequences

Pros:
- Provides a consistent metadata model for journey definitions and journeys:
  - Definition tags for classification and docs.
  - Instance tags and attributes for correlation, tenancy, and identity-aware features.
- Enables key scenarios:
  - “My open journeys” via `attributes.subjectId`.
  - Tenant-scoped and region-scoped views via `attributes.tenantId` and `attributes.region`.
  - Correlation with external systems via `attributes.orderId`, `attributes.paymentIntentId`,
    `attributes.crmCaseId`.
  - Per-subject concurrency limits using `attributes.subjectId`.
  - Data classification and governance via `metadata.tags` and attributes such as
    `dataClassification`.
- Keeps the DSL surface small and generic:
  - No new first-class `subject` field, consistent with ADR-0010.
  - Subject/tenant/etc. are expressed as attributes and query parameters, not structural
    changes to core envelopes.
- Avoids hard-coded numeric limits by externalising them into `MetadataLimits`, which can be
  tuned per engine configuration (per installation).
- JWT validation task plugins allow journeys to handle both authenticated and anonymous calls cleanly without
  confusing ownership or “my journeys” semantics.

Cons:
- Adds conceptual and implementation complexity:
  - The engine must implement tag/attribute storage, enforce `MetadataLimits`, and map
    metadata into indices.
  - Workflow authors must understand and correctly configure sourcing rules for tags and
    attributes.
- Does not automatically prevent sensitive data from being placed in tags/attributes:
  - Producers (spec authors and calling systems) remain responsible for avoiding PII and
    secrets in metadata.
- Append-only enrichment and self-service query endpoints require additional feature work:
  - This ADR sets the direction but does not define specific enrichment APIs or complete
    operator/self-service routing patterns.

Overall, this decision introduces a coherent metadata and query model for journeys that
enables important platform capabilities while respecting prior decisions about not
introducing a first-class subject field into the DSL or generic Journeys envelopes.
