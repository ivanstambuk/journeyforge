# ADR-0010 – Subject and Principal Identity

Date: 2025-11-20 | Status: Proposed

## Context

Journeys (`kind: Journey`) are long‑running executions that are typically “owned” by some
principal (for example, an end user or service). Today, most platforms derive that principal
from the authentication context (for example the `sub` claim in a JSON Web Token) and may copy
it into the journey `context` for business logic.

We want to enable platform‑level capabilities such as:
- “Only the subject can cancel this journey in self‑service flows.”
- “List all journeys for subject X.”
- “Allow only one running journey per subject for this journey definition.”

One option would be to model subject entirely inside the journey `context` (for example,
copying the JWT `sub` claim into `context.subject` at start) and let each spec define its own
shape. However, this has important drawbacks:

- Platform features can’t depend on it reliably:
  - There is no stable, spec‑wide location for the subject; different journey definitions could use
    different paths or semantics (`subject`, `userId`, `applicant.id`, etc.).
  - The journey `context` is mutable by design; journey logic can overwrite or delete any
    field, including whatever is used to represent the subject.
- Features like “only the initiating subject can cancel”, “list all journeys for a subject”,
  or “at most one running journey per subject” would become conventions rather than guarantees.
  The engine would have no trustworthy source of truth for the principal of record.

To support these capabilities safely, we need a subject concept that is:
- Defined once for all journeys.
- Tied to the authentication context at start (or otherwise assigned by the platform).
- Immutable for the lifetime of a journey.
- Separate from the mutable journey `context`.

## Decision

We do **not** introduce a first‑class `subject` field in the DSL or the generic Journeys
envelopes. Identity for journeys is handled in two layers:

- Workflow layer:
  - Journey definitions SHOULD normalise identity into `context` in a consistent, well‑documented
    way (for example using an initial `transform` that writes `context.identity.subject` or a
    similar agreed path from JWT claims, headers, or request body).
  - This normalised identity is intended for business logic and for future platform features,
    but it remains ordinary `context` data from the DSL’s point of view.
- Platform layer (out of scope for the DSL):
  - This ADR explicitly forbids introducing any **first‑class subject field** or standardised,
    cross‑journey “journey subject” concept at the platform/Journeys API level in the DSL.
  - Platforms MAY implement their own identity‑based policies as internal implementation
    details (for example, using private metadata or store keys), but these MUST NOT surface as
    standard fields in the generic Journeys envelopes or as guarantees that other platforms
    can rely on, and MUST NOT be described as a first-class, cross-journey “subject” concept.

The generic DSL and Journeys OpenAPI schemas therefore remain unchanged with respect to
subject/identity; this ADR only recommends a pattern for normalising identity into `context`.

## Consequences

Pros:
- Avoids introducing a new first‑class field into the DSL and generic API surface while still
  acknowledging the need for subject‑like identity at the platform level.
- Keeps identity derivation spec‑driven at the journey-definition level: journey definitions define how identity is
  normalised into `context`, making the pattern discoverable without constraining platforms.
- Allows different platforms to choose how they derive and encode identity (JWT `sub`,
  account ids, tenants, composite keys) while still following a common “normalise into
  `context` first” pattern if they wish.
- Keeps multi‑party modelling purely in `spec.security` and `context`, rather than merging it
  into a single “subject” field.

Cons:
- Platform features like “only the subject can cancel” and “single running journey per
  subject” must be implemented as platform policy, not as guarantees that can be inferred
  solely from the DSL spec.
- Subject‑centric behaviour is less portable between platforms, since the identity key lives
  in metadata and conventions rather than a standardised field.

Overall, this decision preserves the flexibility of the DSL and makes clear that a first‑class
journey subject concept is out of scope; any identity‑aware behaviour remains purely
implementation‑specific and invisible to the generic DSL and Journeys API schemas.
