# Use Case – HTTP Notification (Fire-and-Forget)

Status: Draft | Last updated: 2025-11-20

## Problem

Emit an HTTP notification as a side effect without blocking on the response or branching on the outcome:
- Send a best-effort audit/notification call to an external HTTP endpoint.
- Continue the journey regardless of whether the notification eventually succeeds.
- Keep the workflow semantics simple: no `resultVar`, no error handling logic for the notification.

## Relevant DSL Features

- `task` with `kind: httpCall` and `mode: notify` for fire-and-forget semantics.
- `body.mapper` with DataWeave to build the notification payload from `context`.
- `succeed` to complete the journey without depending on the HTTP result.
- `inputSchemaRef` / `outputSchemaRef` to validate the incoming context and shape the output.

## Example – http-notify-audit

Workflow: `http-notify-audit.workflow.yaml`

```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: http-notify-audit
  version: 0.1.0
spec:
  inputSchemaRef: schemas/http-notify-audit-input.json
  outputSchemaRef: schemas/http-notify-audit-output.json

  start: notify
  states:
    notify:
      type: task
      task:
        kind: httpCall
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
      # return the (unchanged) context; this workflow does not depend on the HTTP outcome
```

Notes:
- The audit call is “fire-and-forget” from the workflow’s perspective: it does not define `resultVar` or `errorMapping`, and the journey cannot branch on notification success or failure.
- Runtimes may still log failures or apply retries internally, but control flow always proceeds from `notify` to `done`.

Related files:
- Workflow: `http-notify-audit.workflow.yaml`
- Schemas: `http-notify-audit-input.json`, `http-notify-audit-output.json`

