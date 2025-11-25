# ADR-0006 – Event Publish Tasks for Kafka

Date: 2025-11-20 | Status: Proposed

## Context

JourneyForge journeys sometimes need to emit events to external systems (for example, Kafka topics) as part of their execution:
- Notify downstream systems that an order has changed.
- Emit audit or activity events.
- Drive other services via event-driven architectures.

Today, the DSL supports HTTP side effects via `task` with `kind: httpCall:v1` (including `mode: notify` for fire-and-forget HTTP). However:
- HTTP is not always the right transport; many teams use Kafka or other brokers as their primary integration channel.
- Modelling event emission as an HTTP call to a gateway is possible but hides intent and couples the journey to a particular HTTP façade.

We want a first-class way to describe “publish an event to Kafka” while:
- Reusing the existing `task` pattern and DataWeave mappers.
- Keeping the state machine surface small.

## Decision

We extend `task` to support a new `kind: eventPublish:v1` with Kafka as the initial transport.

Shape:

```yaml
type: task
task:
  kind: eventPublish:v1
  transport: kafka                 # initial implementation: only "kafka"
  topic: orders.events             # required
  key:                             # optional – mapper for Kafka key
    mapper:
      lang: dataweave
      expr: <expression>
  value:                           # required – mapper for event payload
    mapper:
      lang: dataweave
      expr: <expression>
  headers:                         # optional – Kafka record headers
    <k>: <string|interpolated>
  keySchemaRef: <string>           # optional – JSON Schema for the key
  valueSchemaRef: <string>         # optional but recommended – JSON Schema for the payload
next: <stateId>
```

Semantics:
- The engine evaluates `key.mapper` (when present) and `value.mapper` with `context` bound to the current journey context.
- It publishes a record to Kafka with `{topic, key, value, headers}`:
  - Cluster and connection details are configured at engine configuration time, not in the DSL.
  - Serialisation (e.g. JSON, Avro) is an engine concern; the DSL describes logical JSON objects.
- The task does not produce a `resultVar`; the journey instance cannot branch on publish outcomes.
- On successful publish, execution continues to `next`.
- On repeated publish failure (after any configured retries), the engine may:
  - Treat this as an engine execution error that fails the journey/API call, or
  - Apply operator-configured policies; the DSL does not expose partial success states.

Schema integration:
- `eventPublish.valueSchemaRef` points at a JSON Schema that describes the payload shape (for example `schemas/order-updated-event.json`).
  - Engine implementations SHOULD validate the mapped payload against this schema before publishing.
  - Tooling MAY use it for schema registry integration or IDE support.
- `eventPublish.keySchemaRef` plays the same role for the key when used.

Validation rules:
- `task.kind` for event publishing MUST be `eventPublish:v1` in this version.
- For `eventPublish:v1`:
  - `transport` is required and must be `kafka` in the initial version.
  - `topic` is required and non-empty.
  - `value.mapper` is required with `lang: dataweave` and an inline `expr`.
  - `key.mapper` (when present) must follow the same mapper rules.
  - `headers` (when present) is a map from header name to string/interpolated value.
  - `keySchemaRef` / `valueSchemaRef` (when present) must be non-empty strings.
- `resultVar`, `errorMapping`, and `resiliencePolicyRef` MUST NOT be used with `eventPublish:v1`.

## Consequences

Positive:
- Journeys can emit Kafka events as first-class side effects, without having to route through HTTP proxies just to reach Kafka.
- The DSL stays compact by reusing the `task` pattern and DataWeave mappers.
- Schema-conscious journey definitions can validate event payloads and integrate cleanly with schema registries and event consumers.

Negative / trade-offs:
- Engine implementations must integrate with Kafka (or provide an abstraction that maps `eventPublish` to a configured broker).
- Because `eventPublish` does not expose outcomes to the DSL, debugging failed publishes relies on logs/metrics rather than control-flow states.

Future work:
- Extend `transport` beyond `kafka` (e.g., `sns`, `sqs`, `pubsub`) while keeping the logical shape of `eventPublish` consistent.
- Add higher-level patterns for “exactly-once” or transactional publishing when the underlying platform supports it.
