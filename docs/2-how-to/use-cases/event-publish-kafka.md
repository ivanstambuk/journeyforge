# Use Case – Event Publish to Kafka

Status: Draft | Last updated: 2025-11-20

## Problem

Emit domain events to Kafka from a journey so other systems can react asynchronously:
- Publish an `ORDER_UPDATED` event whenever an order changes.
- Use the journey `context` to build the event payload.
- Keep event emission as a side effect: the journey does not branch on publish outcome.

## Relevant DSL Features

- `task` with `kind: eventPublish` for Kafka event emission.
- `eventPublish.transport: kafka` and `eventPublish.topic` to define the target topic.
- DataWeave mappers for `eventPublish.key.mapper` and `eventPublish.value.mapper`.
- Optional `eventPublish.headers` for record headers (for example `traceparent`, source, etc.).
- `eventPublish.keySchemaRef` and `eventPublish.valueSchemaRef` to attach JSON Schemas to the key and payload.
- `succeed` to complete the journey without depending on event broker responses.

## Example – event-publish-kafka

Journey definition: `event-publish-kafka.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: event-publish-kafka
  version: 0.1.0
spec:
  inputSchemaRef: schemas/event-publish-kafka-input.json
  outputSchemaRef: schemas/event-publish-kafka-output.json

  start: emitEvent
  states:
    emitEvent:
      type: task
      task:
        kind: eventPublish
        eventPublish:
          transport: kafka
          topic: orders.events
          key:
            mapper:
              lang: dataweave
              expr: |
                context.orderId
          value:
            mapper:
              lang: dataweave
              expr: |
                {
                  eventType: "ORDER_UPDATED",
                  orderId: context.orderId,
                  amount: context.amount,
                  status: context.status
                }
          headers:
            traceparent: "${context.traceparent}"
            source: "journeyforge"
          keySchemaRef: schemas/event-publish-kafka-key.json
          valueSchemaRef: schemas/event-publish-kafka-value.json
      next: done

    done:
      type: succeed
      # The journey does not rely on publish outcome; it can simply return context or a subset.
```

Notes:
- `eventPublish` does not define `resultVar`, and the journey instance cannot branch on whether the Kafka publish succeeded or failed; it is a side effect.
- When configured, the engine validates the mapped payload against `valueSchemaRef` before publishing and integrates with a schema registry using the same schemas.

Related files:
- Journey definition: `event-publish-kafka.journey.yaml`
- Schemas: `event-publish-kafka-input.json`, `event-publish-kafka-output.json`, `event-publish-kafka-key.json`, `event-publish-kafka-value.json`
