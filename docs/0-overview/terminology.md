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

## Behaviour – explicit vs implicit

- **Explicit behaviour**
  - Behaviour that is directly and visibly configured in a journey definition or in
    documented configuration objects:
    - Example: a `transform` state that sets `context.orderId`.
    - Example: auth task plugins (`jwtValidate:v1`, `mtlsValidate:v1`) or HTTP security policies under `spec.bindings.http.security` that declare API key policies.
    - Example: `MetadataLimits` values for tag/attribute caps.
  - When we say “explicit”, we mean “clearly encoded in DSL/config and visible in the spec”.

- **Engine-level invariants (implicit behaviour)**
  - Behaviour the engine applies uniformly to all journey instances once certain conditions
    hold, even if there is no per-journey block for it, but which is still documented in the
    DSL or ADRs.
  - Examples:
    - Deriving `attributes.subjectId` from a validated JWT subject when `jwtValidate:v1`
      succeeds and the subject is not listed as anonymous in its effective configuration.
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
