# Kafka Publish Plugin – Engine Configuration (Non‑Normative)

This runbook sketches how to configure the Kafka publish task plugin (`task.kind: kafkaPublish:v1`) at the engine level. It complements the normative contracts in:
- DSL reference: `docs/3-reference/dsl.md` (section 5.2 – `kafkaPublish:v1`).
- ADR-0006: Kafka Publish Task Plugin.
- ADR-0026: Task Plugins Model and Constraints.
- Feature 011: `docs/4-architecture/features/011/spec.md`.

The goals are:
- Keep **credentials and cluster wiring in the secret store + engine config**, never in specs.
- Let journeys select a Kafka connection via `connectionSecretRef` when they need a non-default cluster/principal, while most tasks rely on a single default.

## 1. Configuration root

Kafka publish configuration lives under `plugins.kafkaPublish` in engine configuration:

```yaml
plugins:
  kafkaPublish:
    # Default connection used when a task omits connectionSecretRef
    defaultConnectionSecretRef: secret://kafka/connections/default
```

Semantics:
- `defaultConnectionSecretRef`:
  - MUST be a `secretRef` understood by the engine’s secret store implementation.
  - Is used whenever a `kafkaPublish:v1` task does not set `connectionSecretRef`.
- The DSL **never** contains raw credentials or bootstrap servers; it only carries `connectionSecretRef` strings.

## 2. Task-level override via connectionSecretRef

A journey or API can override the default connection on a per-task basis:

```yaml
states:
  publishToDefaultCluster:
    type: task
    task:
      kind: kafkaPublish:v1
      # No connectionSecretRef → uses plugins.kafkaPublish.defaultConnectionSecretRef
      topic: orders.events
      value:
        mapper:
          lang: dataweave
          expr: |
            {
              eventType: "ORDER_UPDATED",
              orderId: context.orderId
            }
    next: done

  publishToPartnerCluster:
    type: task
    task:
      kind: kafkaPublish:v1
      connectionSecretRef: secret://kafka/connections/partner-x
      topic: partner.orders
      value:
        mapper:
          lang: dataweave
          expr: |
            {
              eventType: "PARTNER_ORDER_CREATED",
              orderId: context.orderId
            }
    next: done
```

Semantics:
- When `connectionSecretRef` is present on the task, the engine MUST use that connection configuration for this publish.
- When it is omitted, the engine MUST fall back to `plugins.kafkaPublish.defaultConnectionSecretRef`.

## 3. Secret payload examples (informative)

The content of each Kafka connection secret is implementation-defined but typically encodes:
- `bootstrapServers` and client identification.
- TLS / mTLS configuration.
- SASL / OAUTHBEARER / Kerberos credentials.

Example – SASL/SCRAM connection (conceptual secret value):

```json
{
  "bootstrapServers": "kafka-1:9092",
  "clientId": "journeyforge-orders",
  "security": {
    "protocol": "SASL_SSL",
    "mechanism": "SCRAM-SHA-512",
    "username": "orders-service",
    "password": "********"
  },
  "retries": {
    "maxAttempts": 3,
    "backoffMs": 100
  }
}
```

Example – mTLS-based connection:

```json
{
  "bootstrapServers": "kafka-mtls:9093",
  "clientId": "journeyforge-orders",
  "security": {
    "protocol": "SSL",
    "clientCertRef": "secret://certs/kafka/orders-service",
    "truststoreRef": "secret://certs/kafka/cluster-ca"
  }
}
```

Notes:
- These JSON snippets illustrate **typical fields** only; actual shapes are an engine concern and are not part of the DSL contract.
- Nested `clientCertRef`/`truststoreRef` inside the secret payload are resolved by the engine’s secret store; they do not appear in DSL.

## 4. Observability and troubleshooting

Observability for `kafkaPublish:v1` follows the generic plugin rules in ADR‑0025/ADR‑0026:
- Use `observability.plugins.*` to control log levels and spans, for example:

```yaml
observability:
  plugins:
    byType:
      kafkaPublish:
        logLevel: info
```

- At `info` or `debug` level, implementations MAY log:
  - Plugin type/version, topic, and journey metadata.
  - Connection identifier (for example the `connectionSecretRef` string), but **never** credentials.
- Payloads, keys, and raw secrets MUST NOT be logged; only derived, non-sensitive identifiers are allowed.  
