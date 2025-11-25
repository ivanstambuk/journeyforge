# Use Case – HTTP Notification (Fire-and-Forget)

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-notify-audit/http-notify-audit.md`

## Problem

Emit an HTTP notification as a side effect without blocking on the response or branching on the outcome:
- Send a best-effort audit/notification call to an external HTTP endpoint.
- Continue the journey regardless of whether the notification eventually succeeds.
- Keep the journey semantics simple: no `resultVar`, no error handling logic for the notification.

## Relevant DSL Features

- `task` with `kind: httpCall:v1` and `mode: notify` for fire-and-forget semantics.
- `body.mapper` with DataWeave to build the notification payload from `context`.
- `succeed` to complete the journey without depending on the HTTP result.
- `spec.input.schema` / `spec.output.schema` to validate the incoming context and shape the output.

## Example – http-notify-audit

Journey definition: `http-notify-audit.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-notify-audit
  version: 0.1.0
  description: >
    Fire-and-forget HTTP audit notification for order updates.
spec:
  input:
    schema:
      type: object
      required: [orderId]
      properties:
        orderId: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        orderId: { type: string }
      additionalProperties: true
  start: notify
  states:
    notify:
      type: task
      task:
        kind: httpCall:v1
        mode: notify
        method: POST
        url: "https://api.example.com/audit"
        headers:
          Accept: application/json
        body:
          mapper:
            lang: dataweave
            expr: |
              {
                event: "ORDER_UPDATED",
                orderId: context.orderId
              }
      next: done

    done:
      type: succeed
      # return the (unchanged) context; this journey does not depend on the HTTP outcome
```

Notes:
- The audit call is “fire-and-forget” from the journey’s perspective: it does not define `resultVar` or `errorMapping`, and the journey cannot branch on notification success or failure.
- Engine implementations MAY log or retry the notification internally, but those behaviours are non-observable in the DSL: control flow always proceeds from `notify` to `done` regardless of HTTP success/failure.

Related files:
- Journey definition: `http-notify-audit.journey.yaml`
