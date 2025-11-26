# Feature 019 – Queue/Message Binding for Journeys

| Field | Value |
|-------|-------|
| Status | Draft |
| Last updated | 2025-11-25 |
| Owners | TBD |
| Linked plan | `docs/4-architecture/features/019/plan.md` |
| Linked tasks | `docs/4-architecture/features/019/tasks.md` |
| Roadmap entry | #011 |

> Guardrail: This specification is the single normative source of truth for the feature. Track high‑ and medium‑impact questions in `docs/4-architecture/open-questions.md` (none currently open for this feature), encode resolved answers directly in the Requirements/NFR/Behaviour sections below, and use ADRs under `docs/6-decisions/` (in particular ADR‑0006 and ADR‑0029) for architecturally significant clarifications.

## Overview

Introduce a queue/message inbound binding for `kind: Journey` definitions via `spec.bindings.queue`. The binding provides a provider-neutral way to consume messages from systems such as Kafka, SQS, NATS, Azure Service Bus, SNS, or Pub/Sub and map them into:
- Journey start events (new instances).
- External-input step submissions (`wait`/`webhook` states).

The DSL intentionally avoids provider-specific concepts (topics, queues, subscriptions); it deals only with **logical channels**, which the engine/platform maps onto concrete messaging infrastructure.

This feature is **DSL/API design only**; engine implementation of consumers, offsets, retries, and dead-letter handling will be delivered in later slices.

Primary references:
- DSL reference: `docs/3-reference/dsl.md` (section 17 – Inbound Bindings, queue/message binding).
- Event publish ADR: `docs/6-decisions/ADR-0006-event-publish-kafka.md` (outbound Kafka task plugin).
- Inbound bindings ADR: `docs/6-decisions/ADR-0029-inbound-bindings-and-spec-bindings-http.md`.

## Goals

- Define a minimal, provider-neutral DSL surface for queue/message bindings:
  - `spec.bindings.queue.starts[*].channel` – logical channels that start new journey instances.
  - `spec.bindings.queue.steps.<stepId>.channel` – logical channels that deliver step input for specific external-input states.
- Ensure the engine remains transport-agnostic:
  - Queue binding must map onto the same logical operations as the HTTP Journeys API (`start`, `submitStep`).
- Keep provider details in platform configuration:
  - The DSL must not reference Kafka topics, SQS queue URLs, NATS subjects, etc., directly; those live in engine/platform config keyed by logical `channel` ids.

## Non-Goals

- No DSL for configuring consumer groups, partitions, offsets, replay windows, retries, or dead-letter queues; those are operational concerns.
- No new state types or control-flow constructs for messaging; journeys continue to use `task`/`choice`/`transform`/`wait`/`webhook`/`succeed`/`fail`.
- No outbound changes: `kafkaPublish:v1` and other event-publish plugins remain the way journeys emit messages.

## DSL (normative)

### 1. `spec.bindings.queue` for `kind: Journey`

For journey definitions, authors MAY declare a queue/message binding:

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: order-orchestration
  version: 0.1.0
spec:
  bindings:
    queue:
      starts:
        - channel: orders.created
        - channel: orders.retried

      steps:
        approveOrder:
          channel: orders.approvals
        compensateOrder:
          channel: orders.compensations
```

Constraints:
- `spec.bindings.queue`:
  - MUST NOT be present for `kind: Api`.
  - MAY be present for `kind: Journey` alongside HTTP and WebSocket bindings.
- `starts[*].channel`:
  - Each `channel` MUST be a non-empty string.
  - Channels are logical identifiers; their mapping to provider-specific resources is defined in engine/platform configuration.
- `steps.<stepId>`:
  - Keys under `steps` MUST be state ids of external-input states (`type: wait` or `type: webhook`) in `spec.states`.
  - Each `steps.<stepId>.channel` MUST be a non-empty string.

### 2. Conceptual semantics

#### 2.1 Start channels

For each `starts[*].channel`:
- The engine subscribes to the logical channel (via provider-specific configuration).
- Each consumed message is treated as a **start event** for the journey definition:
  - The message payload and metadata are mapped into an initial `context` according to:
    - A provider-neutral mapping model (payload/headers/attributes), and
    - The queue binding configuration for the deployment.
  - The resulting `context` MUST be validated against `spec.input.schema` when present; invalid messages fail fast according to the canonical error model and operational configuration (for example dead-letter).
  - A new journey instance is created and executed from `spec.start` with that initial `context`.
- This feature assumes **fire-and-forget** semantics for queue starts:
  - There is no notion of a synchronous reply to the sender at the DSL level.
  - Request/response messaging patterns (for example using `reply-to` and correlation ids) are expressed using normal journey behaviour and outbound event-publish tasks.

#### 2.2 Step channels

For each `steps.<stepId>.channel`:
- The engine subscribes to the logical channel and treats messages as **step submission events** targeting the external-input state `<stepId>`.
- For each message:
  - The engine MUST be able to determine:
    - Which journey instance the message targets (`journeyId` or equivalent), and
    - That the targeted instance is currently paused at `<stepId>`.
  - The exact mapping from message metadata to `journeyId` (for example from headers, attributes, or payload fields) is defined in the queue binding configuration and corresponding engine documentation, not in the DSL.
  - The message payload is validated against the state’s `input.schema` when present and applied as if it were submitted via the HTTP Journeys API step endpoint.

If the engine cannot resolve a message to a specific journey instance and step (for example missing or stale identifiers), behaviour is implementation-defined but SHOULD follow clear, documented policies (for example dead-letter or rejection).

## Behaviour (conceptual)

- Queue/message bindings are **additional entry points** into the same journey definitions:
  - HTTP and WebSocket bindings expose explicit APIs.
  - Queue bindings consume from messaging infrastructure.
- The journey graph, state semantics, and outcome model remain unchanged.
- Outbound messaging (for example via `kafkaPublish:v1`) is unaffected; queue binding is the inbound counterpart.

## Non-Functional Requirements

| ID | Requirement | Driver | Notes |
|----|-------------|--------|-------|
| NFR-019-01 | Keep DSL provider-neutral. | Portability. | No provider-specific resource names or options in `spec.bindings.queue`; all such details live in engine/platform config. |
| NFR-019-02 | Preserve a clear mapping to existing logical operations. | Architecture. | Queue bindings must only use the existing `start` and `submitStep` semantics; no queue-specific control-flow in the DSL. |
| NFR-019-03 | Avoid conflating queue semantics with error handling. | Clarity. | Error/retry/dead-letter policies belong to engine/platform configuration, not the queue binding DSL surface. |

