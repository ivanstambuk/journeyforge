# Feature 002 – Journey Metadata, Tags & Query

| Field | Value |
|-------|-------|
| Status | Ready |
| Last updated | 2025-12-10 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/002/plan.md` |
| Linked tasks | `docs/4-architecture/features/002/tasks.md` |
| Roadmap entry | #002 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md`, encode resolved answers directly in the Requirements/NFR/Behaviour/Telemetry sections below, and use ADRs under `docs/6-decisions/` for architecturally significant clarifications.

## Overview
Introduce a metadata model and query surface for journeys that allows:
- Workflow specs to declare definition‑level tags (`metadata.tags`) for classification and governance.
- Journey instances to carry tags and attributes for identity, tenancy, and correlation.
- The Journeys API to expose these fields and support filtered listing (for operators and self‑service “my journeys” flows).

This feature also defines configurable limits for tags/attributes via a `MetadataLimits` document, and extends HTTP security policies to support mixed‑mode JWT authentication (required vs optional, anonymous subjects) without introducing a first‑class subject field into the DSL.

Primary references:
- ADR: `docs/6-decisions/ADR-0011-journey-metadata-tags-and-query.md`.
- DSL reference: `docs/3-reference/dsl.md` (sections 2.1, 2.8, 18, 21).
- Journeys OpenAPI: `docs/3-reference/openapi/journeys.openapi.yaml`.
 - How-to use case: `docs/2-how-to/use-cases/journey-metadata-tags-attributes.md`.

## Goals
- Add `metadata.tags` to the DSL for `kind: Journey` and `kind: Api`, with clear semantics and limits.
- Introduce instance‑level `journey.tags` and `journey.attributes` in the engine’s journey model and Journeys API, with reserved keys for subject/tenant/correlation.
- Provide a `MetadataLimits` configuration document to externalise limits for definition tags, instance tags, and attributes.
- Extend HTTP security policies under `spec.bindings.http.security` with `mode: required|optional` and `anonymousSubjects` to support mixed‑mode authentication and safe subject mapping.
- Add an operator‑oriented `GET /journeys` listing endpoint with filters over journey name, phase, tags, and selected attributes, and keep room for a self‑service “my journeys” endpoint built on top of `subjectId`.

## Non-Goals
- No append‑only enrichment APIs for tags/attributes in this increment (only v1 immutability is implemented; enrichment is directional).
- No shared/global metadata policies or reuse mechanism beyond journey‑local configuration; reuse will be considered only if duplication becomes a real problem.
- No typed attribute system (all attribute values are strings) and no ad‑hoc query language beyond a small, fixed set of query parameters.
- No separate “internal‑only” metadata channel; tags/attributes are observable metadata by design.

## Functional Requirements

| ID | Requirement | Success path | Validation path | Failure path | Telemetry & traces | Source |
|----|-------------|--------------|-----------------|--------------|--------------------|--------|
| FR-002-01 | Support `metadata.tags` on journeys/APIs. | Specs can declare `metadata.tags: string[]` for classification; tags exported in docs/UIs. | Reject specs where `metadata.tags` is not an array of strings or exceeds `MetadataLimits.definitionTags.maxCount`. | Spec validation error with clear message. | Expose `metadata.tags` via reference docs and OpenAPI export. | ADR-0011 |
| FR-002-02 | Record instance tags (`journey.tags`). | The engine derives instance tags from `metadata.tags` and spec-defined sourcing (payload/headers/baggage) and exposes them in `JourneyStatus`/`JourneyOutcome`. | Enforce `MetadataLimits.instanceTags.maxCount`/`maxLength` at validation/engine level. | Start or execution fails fast when tag limits are exceeded. | Log per-journey tag counts (debug/trace), no tag values at INFO. | ADR-0011 |
| FR-002-03 | Record instance attributes (`journey.attributes`). | The engine populates attributes (subjectId, tenantId, channel, correlation ids) from configured sources and exposes them in `JourneyStatus`/`JourneyOutcome`. | Attribute keys/values validated against `MetadataLimits.attributes.*`; reserved keys validated for shape when present. | Start or execution fails fast when attribute limits are exceeded or reserved key types are violated. | Emit metrics on usage of reserved attributes (subjectId, tenantId, orderId, etc.). | ADR-0011 |
| FR-002-04 | Load and enforce `MetadataLimits`. | At startup, the engine loads a `MetadataLimits` document and uses it for validation; in its absence, uses documented defaults equivalent to the reference. | Static config validation rejects malformed `MetadataLimits`; spec validation uses loaded limits. | Startup fails with clear error if `MetadataLimits` is malformed or missing when required. | Log effective limits at startup; include limit breaches in error telemetry. | ADR-0011 |
| FR-002-05 | Extend JWT HTTP security policies for mixed‑mode auth. | JWT policies honour `mode: required|optional` and `anonymousSubjects`; when a valid non‑anonymous subject is present, `attributes.subjectId` is derived; otherwise it is unset. | Validation rejects unknown `mode` values and invalid `anonymousSubjects` entries; unit tests cover required vs optional behaviour. | Requests with invalid tokens are rejected; missing tokens are rejected only when `mode=required`. | Trace whether a given journey run is authenticated, anonymous, or anonymous-with-token. | ADR-0011, DSL §18 |
| FR-002-06 | Provide operator journeys listing with filters. | `GET /api/v1/journeys` supports filters over `journeyName`, `phase`, `subjectId`, `tenantId`, `tag`, `orderId`, `paymentIntentId`, `crmCaseId`, returning `JourneyStatus` items with `tags` and `attributes`. | OpenAPI validation and contract tests ensure the schema matches DSL semantics. | Invalid filter values yield 4xx responses; unsupported combinations are rejected with clear errors. | Expose query usage metrics (which filters are common, result counts). | ADR-0011, journeys.openapi.yaml |
| FR-002-07 | Enable “my journeys” via subjectId (directional). | A self‑service listing endpoint (for example `/api/v1/my/journeys`) may filter on `attributes.subjectId` using the caller JWT `sub`; auth layer hides or ignores explicit `subjectId` query params for self‑service callers. | End-to-end tests confirm that anonymous and “anonymous subject” journeys never appear in self‑service results. | Requests from unauthenticated clients or with anonymous subjects are rejected or yield empty lists, depending on policy. | Log per-subject query usage (aggregate, no PII); ensure no subjectId is logged at INFO. | ADR-0011 |

## Primary Use Cases

This section captures the main scenarios that motivate the metadata model and query surface.

### “My Open Journeys” (Self-Service)

Problem:
- A signed-in user wants to list their active self-service journeys (for example, pending approvals, incomplete KYC flows).

Proposed pattern:
- On journey start, the engine:
- Validates the JWT using the configured HTTP security policy when a JWT policy is attached.
  - Extracts the subject (for example, `context.auth.jwt.claims.sub`) when present and not in the policy’s `anonymousSubjects` list.
  - Sets `attributes.subjectId` to that value internally (not visible in the start request contract). When no valid, non-anonymous subject is available (for example, anonymous tokens with an all-zero UUID or no token in an `optional` policy), `attributes.subjectId` remains unset.
  - Optionally sets `attributes.initiatedBy = "user"` and adds a `self-service` tag.
- The Journeys API exposes a query like:
- `GET /api/v1/journeys?subjectId=<sub>&phase=RUNNING`
- Result items include:
  - `journeyId`
  - `journeyName`
- `phase` (RUNNING)
  - optionally `tags` and selected `attributes`.

This avoids searching inside arbitrary `context` payloads and gives a consistent, engine-level notion of “journeys owned by this subject”.

### Tenant-Scoped & Region-Scoped Views

Problem:
- Operators need to view and manage journeys per tenant or region (for example, SRE dashboards, multi-tenant SaaS).

Proposed pattern:
- Set `attributes.tenantId` and optional `attributes.region` at journey start, typically via the attribute sourcing rules (for example, from headers such as `X-Tenant-Id`, from a `region` baggage key, or from well-known payload fields).
- Support queries like:
- `GET /api/v1/journeys?tenantId=<tenant>&phase=RUNNING`
- Use tags for high-level labels (`metadata.tags: [payments, kyc]`) and attributes for concrete IDs.

### Correlation With External Systems

Problem:
- Support quickly finding all journeys related to an external resource (order, payment, CRM case) without scanning `context`.

Proposed pattern:
- When starting a journey, copy known IDs into attributes, for example:
  - `attributes.orderId`
  - `attributes.paymentIntentId`
  - `attributes.crmCaseId`
- Support queries like:
  - `GET /api/v1/journeys?orderId=<id>`

This is particularly useful for support tooling and incident response.

### Analytics & Reporting

Problem:
- Build aggregated views (success rates, latencies) by domain, channel, or criticality.

Proposed pattern:
- Use definition-level `metadata.tags` for coarse grouping (`kyc`, `onboarding`, `criticality:high`, `pii`, `financial`).
- Use attributes for dimensions like `channel`, `tenantId`, `region`, `segment`, `dataClassification`.
- For example, track metrics such as “success rate for `kyc` journeys started by mobile users in EU” using:
  - tags: `["self-service", "kyc"]`,
  - attributes: `channel = "mobile"`, `region = "eu-west-1"`, `segment = "premium"`.
- Metrics and reporting layers can aggregate by tags and attributes without needing full `context`.

### Per-Subject Concurrency Limits (For Refinement)

Problem:
- Enforce limits like “at most 3 `loan-application` journeys in `RUNNING` phase per subject”.

Proposed pattern (conceptual):
- Use `attributes.subjectId` as the canonical owner identifier.
- Before starting a new journey, the engine (or a control-plane policy service) checks a query such as:
  - `WHERE journeyName = 'loan-application' AND attributes.subjectId = <sub> AND phase = 'RUNNING'`.
- If the count exceeds a configured threshold (for example, 3), the start request is rejected or redirected.

This fits naturally with the attributes model; the exact enforcement mechanism (pre-start hook vs separate policy layer) is left for a future feature spec.

### Data Classification & Governance

Problem:
- Apply consistent governance rules (RBAC, retention, masking) based on what kind of data a journey touches, without depending on ad hoc naming conventions.

Proposed pattern:
- Use definition-level `metadata.tags` for coarse classification (for example, `[pii, financial]`).
- Use instance-level attributes such as:
  - `attributes.dataClassification = "confidential"`,
  - `attributes.environment = "prod"`.
- Policy engines and ops tooling can then:
  - Restrict access to journeys with `metadata.tags` containing `pii` or `financial`.
  - Apply stricter retention or masking rules when `dataClassification` is `confidential`.

These classifications can be sourced from payload, headers, or baggage via the attribute/tag sourcing configuration, but are always controlled explicitly by spec/policy rather than inferred by convention.

### Journeys listing API (`GET /api/v1/journeys`)

This feature normatively defines the operator listing API for journeys based on the tags/attributes model, without introducing a general-purpose query language.

- **Endpoint:**
  - `GET /api/v1/journeys`
  - Returns a paginated list of journeys with fields such as `journeyId`, `journeyName`, `phase`, `updatedAt`, and optionally `tags` and a safe subset of `attributes`.

- **Core filters (attribute-backed):**
  - `subjectId` – filters by `attributes.subjectId` (used for “my open journeys”).
  - `tenantId` – filters by `attributes.tenantId` (tenant-scoped dashboards).
  - `journeyName` – filters by `journeyName` (for example, `loan-application`).
  - `phase` – filters by `phase` (`RUNNING`, `SUCCEEDED`, `FAILED`).

- **Tag filters:**
  - `tag` – repeatable query parameter; when present multiple times, all specified tags MUST be present on the journey (AND semantics).
- Used for queries such as “all `self-service` journeys” or “all `kyc` journeys in `RUNNING` phase”.

- **Semantics:**
  - All provided filters are combined with logical AND:
    - for example, `subjectId=alice&phase=RUNNING&journeyName=loan-application` filters to Alice’s running loan applications.
  - Query parameters map directly to indices over `journeyName`, `phase`, `tags`, and selected attributes (`subjectId`, `tenantId`).
  - Pagination, sorting, and response shapes are defined in the Journeys API reference; this feature spec focuses on which dimensions are exposed, not on list mechanics.

## Non-Functional Requirements

| ID | Requirement | Driver | Measurement | Dependencies | Source |
|----|-------------|--------|-------------|--------------|--------|
| NFR-002-01 | Query operations remain performant at moderate scale. | Operator UX. | Filtering by `journeyName`, `phase`, `subjectId`, `tenantId` behaves as index-backed lookups for 10^4–10^6 journeys. | DB/index design in the engine’s storage layer. | ADR-0011 |
| NFR-002-02 | Metadata overhead stays bounded. | Storage & payload size. | Per-journey metadata size respects `MetadataLimits` and keeps wire size reasonable. | `MetadataLimits` config + engine enforcement. | ADR-0011 |
| NFR-002-03 | Backwards compatibility for existing specs. | Smooth adoption. | Specs that do not declare `metadata.tags` or metadata mappings continue to work unchanged. | Versioning of DSL and the engine’s journey model. | ADR-0011 |
| NFR-002-04 | Security posture is unchanged or improved. | Governance. | No new secrets are stored in tags/attributes; reviews confirm metadata usage follows “no PII in tags” guidance. | Code review and documentation. | ADR-0011 |

## UI / Interaction Mock-ups
```
GET /api/v1/journeys?journeyName=loan-application&phase=RUNNING&tenantId=tenant-001&tag=self-service

Response:
{
  "items": [
    {
      "journeyId": "93d7f7a4-7a77-4e7e-9f8e-1a2b3c4d5e6f",
      "journeyName": "loan-application",
      "phase": "RUNNING",
      "currentState": "waitForApproval",
      "updatedAt": "2025-11-20T10:15:30Z",
      "tags": ["self-service", "kyc"],
      "attributes": {
        "subjectId": "user-123",
        "tenantId": "tenant-001",
        "channel": "web"
      },
      "_links": {
        "self": { "href": "/api/v1/journeys/93d7f7a4-7a77-4e7e-9f8e-1a2b3c4d5e6f", "method": "GET" },
        "result": { "href": "/api/v1/journeys/93d7f7a4-7a77-4e7e-9f8e-1a2b3c4d5e6f/result", "method": "GET" }
      }
    }
  ],
  "nextPageToken": null
}
```

## Branch & Scenario Matrix

| Scenario ID | Description / Expected outcome |
|-------------|--------------------------------|
| S-002-01 | Workflow defines `metadata.tags` within limits; journey instances reflect those tags in `JourneyStatus`/`JourneyOutcome`. |
| S-002-02 | Workflow configures attribute sourcing from headers/payload; attributes appear in `JourneyStatus` and can be queried by `tenantId` and `orderId`. |
| S-002-03 | JWT policy `mode=required`: missing token → request rejected; `subjectId` never set. |
| S-002-04 | JWT policy `mode=optional` and no token: journey starts, `subjectId` unset, journey is visible only to operator queries (not self‑service). |
| S-002-05 | JWT policy `anonymousSubjects` includes nil UUID; such tokens are treated as anonymous and do not set `subjectId`. |
| S-002-06 | Attempts to exceed `MetadataLimits` (too many tags/attributes or oversize values) are rejected with clear errors. |

## Test Strategy
- DSL validation tests:
  - `metadata.tags` happy‑path and invalid shapes/limits.
  - `MetadataLimits` loading and malformed/missing config behaviour.
  - JWT policy `mode` and `anonymousSubjects` parsing and validation.
- Engine behaviour tests:
  - Tag/attribute sourcing from payload, headers, and `baggage` using representative specs.
  - Mixed‑mode JWT: `required` vs `optional`, anonymous vs real subjects, and `subjectId` derivation.
  - Limits enforcement: journeys that approach and exceed `MetadataLimits`.
- API contract tests:
  - `GET /journeys` filters (`journeyName`, `phase`, `subjectId`, `tenantId`, `tag`).
  - `JourneyStatus`/`JourneyOutcome` schemas including `tags` and `attributes`.
- Security tests:
  - Ensure that tags/attributes are never populated with raw JWTs or secrets.
  - Verify that self‑service “my journeys” endpoints (when implemented) cannot be used to enumerate other subjects’ journeys.

## Interface & Contract Catalogue

### DSL / Spec
- `metadata.tags: string[]` on `kind: Journey` and `kind: Api`.
- `spec.metadata.bindings.tags` and `spec.metadata.bindings.attributes` for sourcing tags/attributes from payload, headers, and `baggage`.

### Configuration
- `apiVersion: v1`, `kind: MetadataLimits` document controlling:
  - `spec.definitionTags.maxCount`.
  - `spec.instanceTags.maxCount`, `spec.instanceTags.maxLength`.
  - `spec.attributes.maxKeys`, `spec.attributes.maxKeyLength`, `spec.attributes.maxValueLength`.

### Journeys API
- `GET /api/v1/journeys/{journeyName}/start`:
  - Start payload remains “raw context JSON”; no explicit `tags`/`attributes` fields.
- `GET /api/v1/journeys`:
  - Query parameters: `journeyName`, `phase`, `subjectId`, `tenantId`, `tag`.
  - Response body: paginated collection of `JourneyStatus` records.
- `JourneyStatus` schema:
  - Adds `tags: string[]` and `attributes: { [key: string]: string }` alongside existing fields.
- `JourneyOutcome` schema:
  - Adds `tags: string[]` and `attributes: { [key: string]: string }` for final outcomes.
