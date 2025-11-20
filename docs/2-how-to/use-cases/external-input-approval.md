# Use Case – External Approval & Webhook Callbacks

Status: Draft | Last updated: 2025-11-20

## Problem

Model long‑running approval flows that:
- Start synchronously.
- Pause while waiting for human or third‑party decisions.
- Resume when a step payload is submitted.

## Relevant DSL Features

- `wait` state for manual/external input.
- `webhook` state for third‑party callbacks.
- `/journeys/{journeyId}/steps/{stepId}` step subresources from the OpenAPI exporter.
- `choice` and `transform` for applying and branching on payloads.

## Example 1 – Manual approval

Workflow: `wait-approval.workflow.yaml`

```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: wait-approval
  version: 0.1.0
spec:
  inputSchemaRef: schemas/wait-approval-input.json
  outputSchemaRef: schemas/wait-approval-output.json
  start: submit
  states:
    submit:
      type: task
      task:
        kind: httpCall
        operationRef: items.createItem
        params:
          headers:
            Accept: application/json
        body:
          mapper:
            lang: dataweave
            expr: |
              {
                id: context.id,
                name: context.name
              }
        timeoutMs: 5000
        resultVar: api
      next: waitForApproval

    waitForApproval:
      type: wait
      wait:
        channel: manual
        inputSchemaRef: schemas/wait-approval-approve-input.json
        apply:
          mapper:
            lang: dataweave
            expr: |
              context ++ { 'approval': payload }
        on:
          - when:
              predicate:
                lang: dataweave
                expr: |
                  payload.decision == 'approved'
            next: finish
        default: rejected

    finish:
      type: succeed
      outputVar: api

    rejected:
      type: fail
      errorCode: REJECTED
      reason: \"Approval was rejected\"
```

Related files:
- OpenAPI: `wait-approval.openapi.yaml`
- Schemas: `wait-approval-input.json`, `wait-approval-output.json`, `wait-approval-approve-input.json`

## Example 2 – Payment callback webhook

Workflow: `payment-callback.workflow.yaml`

```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: payment-callback
  version: 0.1.0
spec:
  apis:
    payments:
      openApiRef: apis/payments.openapi.yaml
  inputSchemaRef: schemas/payment-callback-input.json
  outputSchemaRef: schemas/payment-callback-output.json
  start: authorize
  states:
    authorize:
      type: task
      task:
        kind: httpCall
        operationRef: payments.authorizePayment
        params:
          headers:
            Accept: application/json
        body:
          mapper:
            lang: dataweave
            expr: |
              {
                amount: context.amount,
                currency: context.currency,
                id: context.id
              }
        timeoutMs: 5000
        resultVar: auth
      next: waitCallback

    waitCallback:
      type: webhook
      webhook:
        inputSchemaRef: schemas/payment-callback-webhook-input.json
        security:
          kind: sharedSecretHeader
          header: X-Webhook-Secret
          secretRef: secret://webhook/payments-callback
        apply:
          mapper:
            lang: dataweave
            expr: |
              context ++ { 'webhook': payload }
        on:
          - when:
              predicate:
                lang: dataweave
                expr: \"payload.status == 'SUCCESS'\"
            next: captured
        default: failed

    captured:
      type: succeed
      outputVar: webhook

    failed:
      type: fail
      errorCode: PAYMENT_FAILED
      reason: "Payment callback indicated failure or did not arrive as expected"
```

Related files:
- OpenAPI: `payment-callback.openapi.yaml`
- Schemas: `payment-callback-input.json`, `payment-callback-output.json`, `payment-callback-webhook-input.json`
