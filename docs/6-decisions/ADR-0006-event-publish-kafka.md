# ADR-0006 – Kafka Publish Task Plugin (`kafkaPublish:v1`)

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

We extend `task` to support a Kafka-specific publish plugin `kind: kafkaPublish:v1`. This plugin is dedicated to Kafka topics; other pub-sub systems (for example Azure Service Bus, SNS, Pub/Sub) would be modelled as separate plugins rather than additional transports under a single generic event publish kind.

Shape:

```yaml
type: task
task:
  kind: kafkaPublish:v1
  connectionSecretRef: secret://kafka/connections/default   # optional – Kafka connection config
  topic: orders.events                                      # required – Kafka topic name

  # Optional key – literal/interpolated string OR mapper object
  key: "${context.orderId}"
  # or:
  # key:
  #   mapper:
  #     lang: dataweave
  #     expr: <expression>

  # Required payload – mapper for event value
  value:
    mapper:
      lang: dataweave
      expr: <expression>

  # Optional Kafka record headers – values are strings with interpolation
  headers:
    <k>: <string|interpolated>

  # Optional JSON Schemas for key and value
  keySchemaRef: <string>                                    # optional – JSON Schema for the key
  valueSchemaRef: <string>                                  # optional but recommended – JSON Schema for the payload
next: <stateId>
```

Semantics:
- The engine evaluates `key` (if present) and `value.mapper` with `context` bound to the current journey context:
  - `key` MAY be a string (including interpolated strings) or a `mapper` object; in both cases the result is serialised according to engine configuration (typically string/bytes) and used as the Kafka record key.
  - `value.mapper` is required and produces the event payload object; engines typically serialise this as JSON.
- It publishes a record to Kafka with `{topic, key, value, headers}`:
  - Cluster and connection details, including TLS and SASL/OAUTH/mTLS authentication, are configured via `connectionSecretRef` or a default engine-level connection, not in the DSL.
  - Serialisation format (for example JSON vs Avro) is an engine concern; the DSL describes logical JSON values.
- The task does not produce a `resultVar`; the journey instance cannot branch on publish outcomes.
- On successful publish, execution continues to `next`.
- On repeated publish failure (after any configured retries), the engine may:
  - Treat this as an engine execution error that fails the journey/API call, or
  - Apply operator-configured policies; the DSL does not expose partial success states.

Schema integration:
- `valueSchemaRef` points at a JSON Schema that describes the payload shape (for example `schemas/order-updated-event.json`).
  - Engine implementations SHOULD validate the mapped payload against this schema before publishing.
  - Tooling MAY use it for schema registry integration or IDE support.
- `keySchemaRef` plays the same role for the key when used.

Validation rules:
- `task.kind` for Kafka publish MUST be `kafkaPublish:v1` in this version.
- For `kafkaPublish:v1`:
  - `topic` is required and non-empty.
  - `value.mapper` is required with `lang: dataweave` and an inline `expr`.
  - `key` (when present) MUST be either a string (including interpolated strings) or an object with `mapper.lang` and `mapper.expr`.
  - `headers` (when present) is a map from header name to string/interpolated value.
  - `keySchemaRef` / `valueSchemaRef` (when present) must be non-empty strings.
  - `connectionSecretRef` (when present) MUST be a `secretRef` string; when absent, engines MUST fall back to a configured default connection.
- `resultVar`, `errorMapping`, and `resiliencePolicyRef` MUST NOT be used with `kafkaPublish:v1`.

## Consequences

Positive:
- Journeys can emit Kafka events as first-class side effects, without having to route through HTTP proxies just to reach Kafka.
- The DSL stays compact by reusing the `task` pattern and DataWeave mappers.
- Schema-conscious journey definitions can validate event payloads and integrate cleanly with schema registries and event consumers.

Negative / trade-offs:
- Engine implementations must integrate with Kafka (or provide an abstraction that maps `kafkaPublish` to a configured broker).
- Because `kafkaPublish` does not expose outcomes to the DSL, debugging failed publishes relies on logs/metrics rather than control-flow states.

Future work:
- Add dedicated plugins for other event systems (for example Azure Service Bus, SNS, Pub/Sub) rather than multiplexing multiple transports into a single generic event plugin.
- Add higher-level patterns for “exactly-once” or transactional publishing when the underlying platform supports it.

## Transport vs message-level authentication (events)

Kafka and other messaging systems introduce two distinct layers of authentication/authorisation that this ADR treats separately:

- **Transport/broker auth** – how producers/consumers connect to and are authorised by the broker:
  - Kafka: cluster credentials, TLS, SASL/OAUTH/SCRAM, ACLs per topic and principal.
  - Other brokers (for example Azure Service Bus, SNS, Pub/Sub) have equivalent constructs (connection strings, IAM roles, access keys).
  - These concerns are handled entirely in engine/platform configuration (for example via `connectionSecretRef` and connector configuration), not in the journey DSL. If a caller is not allowed to publish to or consume from a topic/queue, messages never reach the engine.

- **Message-level auth** – how consumers interpret and validate the *contents* of messages:
  - For example, validating JWTs or signatures carried in headers/payload, or enforcing per-message authorisation rules.
  - These concerns are modelled explicitly in journeys using normal DSL constructs:
    - Inbound HTTP/WebSocket: `jwtValidate:v1`, `mtlsValidate:v1`, HTTP security policies, and `spec.bindings.http` projections into `context`.
    - Inbound queue bindings (see Feature 019): mapping message metadata into `context` followed by auth/guard states as needed.
  - Event-publish tasks like `kafkaPublish:v1` do not embed message-level auth; they produce events whose consumers are responsible for applying their own validation and authorisation logic.

This separation keeps:
- Cluster/broker security as an operational concern expressed in connector config and secrets, and
- Business-level identity and authorisation as explicit, spec-first behaviour inside journeys and APIs.
