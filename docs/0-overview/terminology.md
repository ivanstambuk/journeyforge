# JourneyForge – Terminology

Status: Draft | Last updated: 2025-11-21

This document defines common terms used across the JourneyForge docs and specs so we can use
consistent vocabulary.

## Core concepts

- **Journey definition**
  - A journey specification with `kind: Journey` authored in the JourneyForge DSL
    (`.journey.yaml` / `.journey.json`).
  - It describes the states, transitions, policies, and metadata that apply to all instances
    of that journey definition.
  - “Journey definition” is the canonical model term for specs with `kind: Journey`.

- **Journey instance**
  - A single execution of a journey definition.
  - Created when a client calls `POST /api/v1/journeys/{journeyName}/start`.
  - Identified by a `journeyId` in the Journeys API (`JourneyStatus`, `JourneyOutcome`).
  - Has its own `context`, tags, attributes, phase (`RUNNING`, `SUCCEEDED`, `FAILED`), and
    timestamps.

- **Engine**
  - The JourneyForge execution engine: the component that loads journey definitions,
    creates journey instances, and drives state transitions according to the DSL semantics.
  - Preferred wording in new docs: “the engine” / “the JourneyForge engine”. When you need to
    talk about multiple concrete implementations, “engine implementation(s)” is acceptable.
  - Do not introduce “runtime” as a generic noun in new prose. Legacy proper names or paths
    that contain `runtime` (for example module names, existing API paths) may remain unchanged,
    but do not introduce new ones.

- **Administrative API** (control plane)
  - The API surface for managing definitions, policies, environments, and governance.
  - Preferred term for the control/admin plane; avoid “runtime REST” wording.
  - Typical paths in docs/examples: `/api/v1/admin/...` or `/admin/...` (exact paths may evolve; keep the term “Administrative API” stable).

- **Journeys API** (execution/data plane)
  - The API surface for starting, querying, cancelling, and interacting with journey instances.
  - Preferred term for the execution/data plane; avoid “runtime REST” wording.
  - Typical paths in docs/examples: `/api/v1/journeys/...`; legacy examples may still show `/runtime/...` as proper names—do not introduce new ones.

## Access & interaction

- **Journey access binding**
  - The binding between a journey instance and the set of participants (subjects and roles) that are allowed to
    interact with it over time, plus the rules that determine which participant may perform which operation at which
    point in the journey.
  - Access binding is *modelled by journey authors* using ordinary `context` fields (for example
    `context.identity.*`, `context.participants.*`) and/or instance metadata (`attributes.*`), not by a generic,
    binding-level security policy model in the DSL.

- **Access check**
  - Any journey-authored or platform-authored logic that decides whether a caller is allowed to perform an operation.
  - In JourneyForge DSL discussions, “access check” typically means authn/authz expressed via task plugins +
    predicates, not an implicit engine policy.

- **Mutating interaction**
  - An interaction that may advance or otherwise change a journey instance (for example:
    `POST /api/v1/journeys/{journeyName}/start` or `POST /api/v1/journeys/{journeyId}/steps/{stepId}`).
  - Mutating access control is expressed **in-graph** using explicit journey-authored control flow (tasks + `choice`
    + routing).

- **Read interaction** (read-only query)
  - An interaction that reads journey instance state without advancing it (for example:
    `GET /api/v1/journeys/{journeyId}` or `GET /api/v1/journeys/{journeyId}/result`).
  - Read access control is expressed via a **read-access guard** (see below) and/or via platform/boundary controls.

- **In-graph** (journey-authored control flow)
  - Logic expressed directly in the journey state graph using normal state types (`task`, `choice`, `transform`,
    `subjourney`, etc.).
  - Example: `jwtValidate:v1` followed by a `choice` that routes to allow/deny branches.

- **Guard** / **access guard**
  - A reusable access check unit that returns an allow/deny decision (often along with Problem Details for deny).
  - Guards are typically applied at external-input boundaries (start/step submissions) and for read endpoints.

- **Guard subjourney**
  - A local subjourney (`spec.subjourneys`) that exists primarily to implement a guard/access check.
  - Guard internals run in the subjourney’s own context and only a minimal decision is returned to the caller.

- **Read-access guard**
  - An access guard evaluated by the engine for Journeys API read endpoints before returning `JourneyStatus` or
    `JourneyOutcome`.
  - In v1 discussions, the read-access guard is expressed as a guard subjourney referenced from
    `spec.access.read.guardRef`.

- **Deny semantics** (conceal vs explicit)
  - How the engine responds when an access check denies:
    - **Conceal**: respond as if the resource does not exist (commonly HTTP 404).
    - **Explicit deny**: respond as “forbidden” (commonly HTTP 403).

## External-input & request segments

- **External-input state**
  - A state that pauses a journey instance and requires a later submission to continue (`type: wait` or
    `type: webhook`).

- **External-input step endpoint**
  - The HTTP subresource exposed for an external-input state:
    `POST /api/v1/journeys/{journeyId}/steps/{stepId}`, where `stepId` equals the state id.

- **Step submission**
  - An HTTP request that targets an external-input step endpoint and supplies the step payload to resume execution.

- **Request segment** (per-request execution segment)
  - The slice of execution that occurs while processing one inbound request (a start request or a step submission),
    running synchronously until the journey either:
    - reaches the next external-input state, or
    - terminates (`succeed`/`fail`).
  - “Request-scoped” data is visible only within a request segment and is not persisted across external-input
    boundaries.

## Execution data & lifetimes

- **Context**
  - The mutable JSON object that represents a journey instance’s execution state.
  - Persisted as part of the journey instance across `wait`/`webhook` boundaries, except for explicitly
    request-scoped fields (see `context.payload`).

- **Step payload** (`context.payload`)
  - The validated request body of a start/step submission as stashed by the engine into `context.payload` for the
    duration of the request segment.
  - While a journey is paused at an external-input state, `context.payload` is absent; journeys that need to retain
    any part of a submission across re-entry must explicitly copy selected fields into a stable `context` subtree.

- **Durable vs request-scoped**
  - **Durable**: data persisted with the journey instance (most of `context`, plus tags/attributes).
  - **Request-scoped**: data that is available only while processing a single inbound request segment (for example
    `context.payload` and header passthrough values).

## Behaviour – explicit vs implicit

- **Explicit behaviour**
  - Behaviour that is directly and visibly configured in a journey definition or in
    documented configuration objects:
    - Example: a `transform` state that sets `context.orderId`.
    - Example: auth task plugins (`jwtValidate:v1`, `mtlsValidate:v1`, `apiKeyValidate:v1`).
    - Example: `MetadataLimits` values for tag/attribute caps.
  - When we say “explicit”, we mean “clearly encoded in DSL/config and visible in the spec”.

- **Engine-level invariants (implicit behaviour)**
  - Behaviour the engine applies uniformly to all journey instances once certain conditions
    hold, even if there is no per-journey block for it, but which is still documented in the
    DSL or ADRs.
  - Examples:
    - Deriving `attributes.subjectId` via metadata bindings from a trusted inbound header or
      W3C baggage key at journey start.
    - Enforcing execution deadlines from `spec.execution.maxDurationSec`.
  - These invariants are not optional “magic”; they are part of the documented semantics of
    the engine. They should be described using clear requirements (for example, “the engine
    derives …” or “the engine MUST …”), not vague statements about unspecified behaviour.

## Wording conventions

- Use **“journey definition”** for the DSL spec (`kind: Journey`).
- Use **“journey instance”** for a concrete run identified by `journeyId`; avoid new phrases
  like “workflow run” or “workflow instance” in normative text.
- Use **“engine”** for the executing component.
- Use **“Administrative API”** for the control plane and **“Journeys API”** for the execution/data plane.
- When describing behaviour, prefer clear verbs:
  - “The engine derives …”, “The engine enforces …”, “The journey definition configures …”.
  - Avoid ambiguous phrasing like “the engine may map …” for core semantics.

### Canonical term map (use vs avoid)

When writing new docs/specs, prefer the “Use” column and avoid introducing new occurrences of
the “Avoid” column except when explicitly quoting legacy identifiers or external systems.

- Journey spec:
  - Use: “journey definition”, “DSL spec (`kind: Journey`)".
  - Avoid: “workflow definition”, “process definition”, “flow definition”, “pipeline definition”, “journey spec” as model terms on their own.
- Instance/run:
  - Use: “journey instance”.
  - Avoid: “workflow run”, “workflow instance”, “process run”, “flow run”, bare “run” when referring to the model.
- Execution engine:
  - Use: “the engine”, “the JourneyForge engine”, “engine implementation(s)” (when comparing implementations).
  - Avoid: “runtime”, “runtime engine” in prose.
- APIs and planes:
  - Use: “Administrative API (control plane)”, “Journeys API (execution/data plane)”, “admin plane”, “journey (execution) plane”.
  - Avoid: “runtime REST”, “journey REST”, “admin REST” as model terms.
- Environment / installation:
  - Use: “the engine and its configuration”, “engine configuration”, “installation”, “platform” (when you mean the broader product around the engine).
  - Avoid: “deployment” as a first-class model term.
- Identity:
  - Use: “principal” (conceptual owner such as an end user or service), `attributes.subjectId` / `attributes.tenantId` / `context` as defined in ADR‑0010 and ADR‑0011.
  - Avoid: inventing new model terms like “owner”, “user id”, “customer id” without mapping them explicitly through `context`/attributes.

## Expression engines – terminology

- **Expression engine**
  - A pluggable, pure evaluation component selected via `lang: <engineId>` at DSL expression sites (`choice` predicates, `transform` states, mappers, error mappers).
  - Implemented via the Expression Engine SPI from ADR‑0027 / Feature 012; examples include engines identified by `dataweave`, `jsonata`, `jolt`, and `jq`.
  - Expression engines compute values from their inputs (`context`, `payload`, `error`, `platform`) and return JSON/primitive results or evaluation errors; they do not perform external I/O or control-flow.

- **Engine id**
  - The string identifier used in `lang` to select an expression engine, for example `dataweave`, `jsonata`, `jolt`, `jq`.
  - Resolved by the engine’s Expression Engine registry; if a spec references an unknown engine id, validation or startup MUST fail rather than falling back to another engine.

- **Usage conventions**
  - In docs/specs, prefer “expression engine” / “engine id” over calling any concrete engine “canonical” or “baseline”.
  - When referring to a concrete engine, use phrases like “the `dataweave` expression engine” or “a `jsonata` expression engine” instead of implying that one engine is the default for all deployments.

Notes:
- Do not introduce new terms such as “deployment” as if they were part of the model. When
  you mean “a concrete installation plus its configuration”, talk explicitly about “the
  engine and its configuration” instead.
- Do not introduce synonyms such as “workflow”, “process”, “flow”, or “pipeline” as model
  terms for journey definitions or instances. These words MAY appear only when quoting
  legacy identifiers or external systems.
- When referring to planes/surfaces, use “admin plane” and “journey (execution) plane”
  rather than “runtime plane”.
- If you must cite a legacy identifier that includes `runtime`, treat it as a proper name
  and avoid adding new `runtime*` terms.
- This document is the golden source for terminology. Any new terminology agreements must be
  captured here immediately, and subsequent docs should follow it without exception.
- Process for improving terminology:
  - When a better term or clearer definition is identified, propose it (for example via
    `docs/4-architecture/open-questions.md` and/or an ADR) and update this document at the
    same time.
  - When drafting or reviewing docs, if a term feels ambiguous or overloaded, suggest an
    alternative and add/adjust the definition here so we avoid future discussions.
  - Before merging new docs/specs, run a quick search (for example with `rg`) for
    “runtime”, “deployment”, “journey REST”, “admin REST”, “workflow run”, “workflow instance”,
    and new uses of “workflow”, “process”, “flow”, or “pipeline”
    to ensure they only appear in this document or as explicit legacy/proper names.
