# ADR-0014 – Journey Access Binding and Session Semantics

Date: 2025-11-22 | Status: Proposed

## Context

JourneyForge orchestrates long-running, multi-step journeys (`kind: Journey`) and synchronous APIs (`kind: Api`)
defined in the DSL (see `docs/3-reference/dsl.md`). Journeys are durable, stateful executions that:

- May span multiple HTTP calls and long wall-clock durations.
- May start anonymously and later bind to an authenticated subject (for example registration or login flows).
- May involve multiple devices for the same user.
- May involve multiple parties (requester, approver, external systems).
- Must scale to millions of concurrently active journeys with horizontal scalability.

The current HTTP surface for journeys is:

- `POST /api/v1/journeys/{journeyName}/start`
- `GET /api/v1/journeys/{journeyId}`
- `GET /api/v1/journeys/{journeyId}/result`
- `POST /api/v1/journeys/{journeyId}/steps/{stepId}` for external-input steps

and the engine already models:

- A durable **journey instance** identified by `journeyId`, with execution context, current state, timers, and outcome.
- Server-side storage for that instance (no client-side embedding of the full state).

What is **not** yet explicitly defined is:

- Whether JourneyForge should introduce an additional, explicit **journey session** concept separate from the
  journey instance (for example a server-side session table or client-side session token).
- How to express and enforce which subjects (users, services, roles) are allowed to interact with a given journey over
  time, across devices and parties.
- How to support **anonymous start → authenticated completion** and **identity journeys** (where the journey itself
  performs authentication) without making the engine a general-purpose HTTP session manager.

Relevant existing decisions include:

- ADR-0010 – Subject and Principal Identity:
  - Avoids a first-class, cross-journey `subject` field in the generic DSL and API surface.
  - Encourages normalising identity into `context` and/or platform metadata.
- ADR-0012 – HTTP Cookies & Per-Run Cookie Jar:
  - Introduces a per-run cookie jar for interactions with downstream HTTP services.
  - Does **not** define any browser/session cookie behaviour between clients and JourneyForge itself.

This ADR defines how JourneyForge handles “session-like” semantics for journeys, clarifies the role of `journeyId`,
and introduces **journey access binding** as the central concept for controlling who may start, resume, or act on
journeys over time.

## Problem

We need a clear, spec-first answer to:

- Do we need a separate **journey session** abstraction beyond the journey instance?
- How should clients resume and continue a journey across multiple HTTP calls without relying on cookies or framework
  sessions?
- Where and how do we express **who** is allowed to interact with a journey (subjects, roles, parties), including:
  - Anonymous start and later subject binding.
  - Multi-device, cross-device flows.
  - Multi-party flows (requester/approver, customer/agent, merchant/payment provider).
- How do we ensure the engine remains:
  - Horizontally scalable.
  - HTTP-stateless in the sense of not owning browser-like sessions.
  - Compatible with external identity and access management systems (IdPs, API gateways, reverse proxies).

There is also a naming and conceptual problem:

- The term “session” is overloaded (browser sessions, backend service sessions, workflow runs).
- We want to avoid conflating:
  - **Durable journey execution state** (which the engine must own).
  - **User/application sessions** (which are typically owned by identity providers or gateways).

## Forces and Requirements

The design must satisfy the following forces:

1. **No engine-managed HTTP session layer**
   - JourneyForge MUST NOT introduce a generic HTTP session layer (for example cookie-based sessions tied to
     `JSESSIONID`-style identifiers).
   - Clients include browsers, mobile apps, backend services, and AI agents; they may use cookies or tokens internally,
     but those are outside the engine’s scope.

2. **Subject binding belongs inside journeys**
   - The binding between a journey instance and the subjects allowed to act on it MUST be expressed and enforced in the
     journey model and engine runtime.
   - This binding MUST NOT be hidden entirely inside a reverse proxy or API gateway, even when those components perform
     authentication.

3. **Anonymous → authenticated and identity journeys**
   - Journeys MUST support starting without an authenticated subject (for example registration, login, or public flows).
   - Journeys MUST be able to:
     - Perform identity and credential checks internally (password, OTP, risk signals).
     - Bind a subject and/or roles mid-journey.
     - Emit identity attributes in the final outcome that external systems can use to mint user sessions.

4. **Multi-device and multi-party**
   - The model MUST support:
     - Resuming a journey on another device for the same subject.
     - Handover between different subjects (for example requester → approver).
   - It MUST do so without a central “session server” inside the engine.

5. **Scale and operational simplicity**
   - The system MUST handle millions of active journeys and high request rates.
   - Engine frontends SHOULD remain stateless in HTTP/session terms: each request should carry all information needed to
     authorise and process it (identity + `journeyId` + step).
   - Any state beyond the journey instance itself increases complexity and must be justified.

6. **Security**
   - `journeyId` MUST NOT serve as an authentication token.
   - Access control MUST always consider:
     - The journey and step being accessed.
     - The caller’s identity when present (e.g. JWT, client certificate).
     - Journey-level access rules.
   - The design MUST support anonymous journeys and use identity only when required by the spec.

## Decision

### 1. No separate “journey session” resource

JourneyForge will **not** introduce an additional, engine-level “journey session” concept beyond the journey instance.

- There will be no separate server-side session table of the form `sessionId → journeyId + subject`.
- There will be no engine-issued, generic client-side session token for journeys.

Instead:

- The **journey instance** is the **only durable, stateful object** that behaves like a session:
  - Identified by `journeyId`.
  - Holds execution context, current state, timers, and outcome.
  - Also holds any security/access metadata needed for that journey.

Any user/session management beyond this (for example browser sessions or SSO) is explicitly left to external systems
(identity providers, session managers, frontends, gateways).

### 2. `journeyId` is a resume token, not proof of identity

`journeyId` is treated as a **high-entropy, opaque resume token** that uniquely identifies a journey instance.

- It MUST be:
  - Unguessable (cryptographically strong randomness).
  - Stable for the lifetime of the journey and for as long as results are retained.
- It MAY be transmitted between devices or systems as the mechanism to “resume” a journey.

However:

- `journeyId` **never** serves as a proof of identity or authorisation on its own.
- All access control decisions MUST consider:
  - `journeyId` (which journey is being accessed).
  - The caller’s identity when available (e.g. JWT subject, client certificate subject, API key principal).
  - The journey’s own access binding rules (see below).

In other words:

- Knowing a `journeyId` is necessary to access a journey instance.
- It is not sufficient by itself when the journey’s access policy requires a subject or role.

### 3. Introduce “journey access binding” as a first-class concept

JourneyForge will introduce **journey access binding** as a first-class architectural concept.

**Definition (conceptual):**

- Journey access binding is the binding between:
  - A given journey instance, and
  - The set of participants (subjects and roles) that are allowed to interact with that journey,
  - Together with the rules that determine which participant may perform which operation at which point in the journey.

Access binding is implemented entirely by journey logic using context-level data; this ADR explicitly does **not**
introduce any new dedicated DSL block or top-level access-control surface.

- Journeys store whatever subject and attribute information they need in `context` (for example
  `context.identity.*` or `context.participants.*`), using existing DSL constructs.
- At every external-input step (including start), the journey compares the current caller's identity/attributes with
  that stored context to decide whether to proceed.
- Participant slots such as `requester`, `approver`, `payer`, `observer` are modelling conventions expressed via
  ordinary context fields; the DSL does not define a special `participants` structure beyond that.

The engine MUST evaluate access binding on every external call:

- `POST /journeys/{journeyName}/start`
- `POST /journeys/{journeyId}/steps/{stepId}`
- `GET /journeys/{journeyId}`
- `GET /journeys/{journeyId}/result`

given:

- The journey instance (including its access binding state).
- The requested operation (route and step).
- The caller’s identity (if any).

If the binding rules deny access, the engine MUST respond with an appropriate error (for example `403` with a
machine-readable error body consistent with the error model ADR).

### 4. Authentication remains external, identity flows are supported

Authentication is assumed to be performed by external components (for example:

- API gateways that validate JSON Web Tokens.
- Reverse proxies that terminate TLS and evaluate client certificates.
- Backend services that call journeys with mTLS and service-level credentials.

These components present identity to JourneyForge via:

- Standard HTTP headers (for example `Authorization`, `X-Subject`, `X-Tenant`), and/or
- Out-of-band mechanisms that the platform decides (for example environment, sidecar, or trust context).

JourneyForge:

- Consumes that identity as input (e.g. via bindings into `context`).
- Applies journey access binding rules using that identity.
- Does **not** issue browser sessions or general-purpose tokens.

For **identity journeys** (for example authentication flows):

- Journeys MAY start anonymously (no identity provided).
- The journey may:
  - Validate credentials, OTPs, or other factors.
  - Look up accounts, tenants, and risk signals.
  - Decide whether and how to bind a subject and roles into its access binding state.
- The journey’s result MAY include identity attributes (subject IDs, assurance level, claims) that an external identity
  system uses to mint user sessions.

JourneyForge itself remains agnostic about how those sessions are created or managed outside the journey.

### 5. Multi-device, cross-device, and multi-party semantics

Multi-device and multi-party interactions are handled entirely via:

- `journeyId` as the resume token, and
- Journey access binding as the authorisation mechanism.

Examples:

- **Same user, new device**
  - The user obtains a deep link, QR code, or out-of-band message containing `journeyId` (or a short URL that embeds it).
  - On the new device, the user may:
    - Remain anonymous (if the journey allows it), or
    - Authenticate and present a JWT or other identity token.
  - Journey access binding checks whether this subject is allowed to act at the current step.

- **Multi-party (requester/approver)**
  - The journey defines participant slots (for example `requester`, `approver`).
  - When started, the journey binds `requester` based on the initiating call (or leaves it anonymous).
  - At some point, the journey generates and sends an invitation for an approver, typically via email/SMS/notification:
    - This invitation includes either:
      - A link that encodes `journeyId`, or
      - An additional invitation token that JourneyForge validates before binding the approver.
  - When the approver acts (for example `POST /steps/waitForApproval`), journey access binding checks that:
    - The invitation or subject is valid for the `approver` slot.
    - The step is permitted for that role.

- **Handover between users or devices**
  - Handover is implemented by the journey itself:
    - It creates or updates participant bindings.
    - It issues new invitations when it is safe to do so.
  - No separate engine-managed session object is needed for the handover; all semantics live in the journey state.

### 6. HTTP statelessness of engine APIs

The engine’s HTTP APIs remain stateless in the sense that:

- Each request carries everything the engine needs to process it:
  - `journeyId` and optionally `stepId`.
  - Any external identity tokens or headers the platform chooses to provide.
  - Any additional invitation or handover tokens the journey requires.
- The engine does **not**:
  - Maintain opaque session identifiers in cookies for the client.
  - Rely on sticky sessions at the load balancer level.

All durable, per-run state is captured in the journey instance and its access binding data.

### 7. `journeyId` format, entropy, and opacity

`journeyId` is the public, stable identifier for a journey instance and acts as the resume token in all HTTP surfaces.
Its format and entropy are defined as follows:

- Generation:
  - `journeyId` MUST be generated using at least 128 bits of cryptographically secure randomness.
  - Implementations MUST use a CSPRNG (cryptographically secure pseudo-random number generator) for id generation.
- Encoding:
  - The canonical encoding is base64url without padding over 128 bits of randomness.
  - Encoded ids MUST:
    - Be exactly 22 characters long.
    - Use only the URL-safe base64url alphabet: `[A-Za-z0-9_-]`.
  - Implementations MAY internally represent the underlying 128-bit value in other formats, but the externally visible
    `journeyId` in HTTP APIs and storage MUST follow this base64url, 22-character, URL-safe form.
- Opacity:
  - Consumers MUST treat `journeyId` as an opaque identifier with no semantic meaning or ordering.
  - No semantics (for example timestamps, sharding information, subject identity, or environment) may be encoded in
    `journeyId` in a way that clients can depend on; such concerns belong in separate fields (for example `createdAt`,
    tags, attributes, or dedicated metadata).
- Indexing and lookup:
  - `journeyId` is optimised for uniqueness and security, not ordering.
  - Ordering and range queries SHOULD use dedicated fields such as `createdAt` or attributes rather than relying on any
    lexicographic properties of `journeyId`.

## Consequences

### Positive

- **Clarity of responsibilities**
  - JourneyForge owns journey instances and journey access binding.
  - External identity providers and gateways own authentication and user sessions.
  - The term “session” is not overloaded in the engine; when present, it refers to journey execution in a narrow sense.

- **Spec-first modelling of access**
  - Access binding is expressed in the journey DSL / metadata and is therefore:
    - Reviewable.
    - Testable.
    - Exportable (for example via Arazzo workflows to external agents).

- **No new DSL surface for access binding**
  - Access binding is implemented as journey logic over context fields defined in specs; there is no dedicated DSL
    access-control block.
  - This keeps the generic DSL surface aligned with ADR-0010 while still allowing identity-aware behaviour in journeys.

- **Support for complex flows**
  - Anonymous → authenticated transitions, identity journeys, multi-device, and multi-party scenarios are all modelled
    without introducing an engine-level session resource.
  - Journeys can implement identity flows and then hand off to “real” session managers without stepping outside the
    spec-first approach.

- **Scalability and simplicity**
  - There is no additional in-memory or persistent session table beyond the journey store.
  - Horizontal scaling is straightforward: any node that can access the journey store and has identity input can serve
    any request.

### Negative / Trade-offs

- **No built-in user session abstraction**
  - Consumers expecting the engine to manage browser or user sessions will have to integrate with external session
    management components.
  - JourneyForge does not provide a one-click “user session” feature; this is by design.

- **Access binding complexity**
  - Access binding rules can become complex for rich multi-party flows.
  - Care is needed to design a DSL surface that is expressive enough but not overwhelming.

- **Responsibility split**
  - Correct end-to-end behaviour depends on:
    - External identity systems correctly authenticating callers and exposing identity to JourneyForge.
    - Journeys correctly modelling and enforcing access binding.
  - Misconfiguration on either side can lead to confusing outcomes (for example anonymous callers reaching steps that
    were intended to be protected but are not modelled as such).

## Rejected Alternatives

### Alternative 1 – Engine-managed server-side journey sessions

Introduce a separate `journeySession` resource:

- `sessionId → journeyId + subject + metadata`
- Session ID stored in a cookie or header; engine resolves session to journey on each call.

**Reasons rejected:**

- Adds a new persistent concept with unclear lifecycle relative to the journey:
  - When does the session expire vs. the journey?
  - What if multiple sessions refer to the same journey?
- Requires an additional distributed store and its own scaling, replication, and revocation policies.
- Blurs the boundary between journey execution and user/session management, which belongs to identity systems.

### Alternative 2 – Engine-issued client-side session tokens for journeys

Issue signed tokens (e.g. JWTs) that embed `journeyId` and subject, validated by the engine on each call.

**Reasons rejected:**

- Overlaps heavily with external identity providers, which are better suited to issue and manage tokens.
- Introduces key management, rotation, and replay considerations into the engine.
- Still requires journey-internal access binding rules; does not remove the need for spec-first modelling.

### Alternative 3 – Delegate all subject semantics to API gateways

Let gateways or reverse proxies fully decide who can call which journey operations, with the engine unaware of subjects.

**Reasons rejected:**

- Makes multi-party and multi-device semantics invisible to the journey definition and engine:
  - Harder to reason about and test.
  - Harder to document for agents and clients (Arazzo workflows would lack access semantics).
- Violates the spec-first principle: crucial behaviour would live in opaque gateway rules rather than in journey specs.

### Alternative 4 – Dedicated DSL block for access binding

Introduce a new, dedicated DSL surface (for example `spec.access` or `spec.accessBinding`) to describe participants,
roles, and per-step access rules declaratively.

**Reasons rejected:**

- Conflicts with the intent of ADR-0010 to avoid a first-class, cross-journey subject concept in the generic DSL.
- Adds a second, specialised access-control sub-DSL when the same behaviour can be expressed using existing constructs
  (context fields + DataWeave predicates in journey logic).
- The decision owner explicitly requires access control and subject binding to be implemented by journeys themselves via
  context data and comparisons in external-input steps, without introducing a new DSL access-binding block.

## Follow-ups

If this ADR is accepted, follow-up work includes:

1. **DSL reference updates**
   - Add a “Journey Access Binding” section to `docs/3-reference/dsl.md` that:
     - Describes recommended patterns for storing identity/participant information in `context`.
     - Explains how journeys implement access checks using existing DSL constructs (for example `wait`/`webhook`
       predicates) rather than a dedicated access-binding block.
     - Provides examples for anonymous start, subject binding, and multi-party flows using these patterns.

2. **Arazzo and agent guidance**
   - Update `docs/4-architecture/spec-guidelines/arazzo-agents.md` to:
     - Clarify that `journeyId` acts as a resume token.
     - Explain how agents should reason about who may call which steps (via access binding, not cookies).

3. **Runtime enforcement**
   - Ensure the runtime:
     - Evaluates access binding on all external-input operations.
     - Emits structured error responses (aligned with the error model ADR) when access is denied.

4. **Examples and templates**
   - Add example journeys and Arazzo workflows demonstrating:
     - Anonymous start → authenticated completion.
     - Identity journeys that perform credential checks and produce identity attributes.
     - Multi-party approval flows with explicit participant roles and invitations.
