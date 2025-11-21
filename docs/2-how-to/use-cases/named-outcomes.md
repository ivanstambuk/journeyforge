# Use Case – Named Outcomes & Reporting

Status: Draft | Last updated: 2025-11-20

## Problem

Classify terminal outcomes with a stable vocabulary:
- Distinguish low‑amount vs high‑amount approvals.
- Identify failures due to specific business rules.

## Relevant DSL Features

- `spec.outcomes` with `when.phase` and predicates.
- `fail.errorCode` conventions.

## Example – named-outcomes

Journey definition: `named-outcomes.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: named-outcomes
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [amount]
      properties:
        amount: { type: number }
      additionalProperties: true
  output:
    schema:
      type: number
  outcomes:
    SucceededLowAmount:
      when:
        phase: Succeeded
        predicate:
          lang: dataweave
          expr: |
            context.amount <= 1000
    SucceededHighAmount:
      when:
        phase: Succeeded
        predicate:
          lang: dataweave
          expr: |
            context.amount > 1000
    FailedBusinessRule:
      when:
        phase: Failed
        predicate:
          lang: dataweave
          expr: |
            context.error.code == 'BUSINESS_RULE_FAILED'
  start: checkAmount
  states:
    checkAmount:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.amount <= 1000
          next: approve
      default: reject

    approve:
      type: succeed
      outputVar: amount

    reject:
      type: fail
      errorCode: BUSINESS_RULE_FAILED
      reason: "Amount above approved limit"
```

Related files:
- OpenAPI: `named-outcomes.openapi.yaml`
