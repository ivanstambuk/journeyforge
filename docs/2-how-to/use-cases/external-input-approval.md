# Use Case – External Approval & Webhook Callbacks

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/wait-approval/wait-approval.md`

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

Journey definition: `wait-approval.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: wait-approval
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [id, name]
      properties:
        id: { type: string }
        name: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        status: { type: integer }
        ok: { type: boolean }
        body: { type: object }
      additionalProperties: true
  start: submit
  states:
    submit:
      type: task
      task:
        kind: httpCall:v1
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
        input:
          schema:
            description: Manual approval input
            type: object
            required: [decision]
            properties:
              decision:
                type: string
                enum: [approved, rejected]
              comment:
                type: string
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

## Example 2 – Payment callback webhook

Journey definition: `payment-callback.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: payment-callback
  version: 0.1.0
spec:
  apis:
    payments:
      openApiRef: apis/payments.openapi.yaml
  input:
    schema:
      type: object
      required: [paymentId, amount, currency]
      properties:
        paymentId: { type: string }
        amount: { type: number }
        currency: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        status:
          type: string
          enum: [SUCCESS, FAILED]
        paymentId: { type: string }
        amount: { type: number }
        currency: { type: string }
        reason: { type: string }
      additionalProperties: true
  start: authorize
  states:
    authorize:
      type: task
      task:
        kind: httpCall:v1
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
        input:
          schema:
            type: object
            required: [paymentId, status]
            properties:
              paymentId: { type: string }
              status:
                type: string
                enum: [SUCCESS, FAILED]
              reason: { type: string }
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
