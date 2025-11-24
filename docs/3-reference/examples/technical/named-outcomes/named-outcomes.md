# Technical Pattern – Named outcomes & reporting

Status: Draft | Last updated: 2025-11-23

## Problem

Classify terminal outcomes with a stable vocabulary so that:
- Low-amount vs high-amount approvals can be distinguished for reporting.
- Failures due to specific business rules can be counted and analysed.

## When to use

- You want analytics or dashboards grouped by outcome labels rather than raw error codes.
- You need to distinguish different “kinds” of success (for example small vs large approvals).
- You want a stable reporting vocabulary even if internal error codes or payloads change.

## Relevant DSL features

- `spec.outcomes` with `when.phase` and predicates.
- `fail.errorCode` conventions for business-rule failures.

## Example – named-outcomes

Artifacts for this example:

- Journey: [named-outcomes.journey.yaml](named-outcomes.journey.yaml)
- OpenAPI: [named-outcomes.openapi.yaml](named-outcomes.openapi.yaml)
- Arazzo: [named-outcomes.arazzo.yaml](named-outcomes.arazzo.yaml)
- Docs (this file): [named-outcomes.md](named-outcomes.md)

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
        phase: SUCCEEDED
        predicate:
          lang: dataweave
          expr: |
            context.amount <= 1000
    SucceededHighAmount:
      when:
        phase: SUCCEEDED
        predicate:
          lang: dataweave
          expr: |
            context.amount > 1000
    FailedBusinessRule:
      when:
        phase: FAILED
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

## Variations and combinations

- Add more outcome labels (for example `SucceededMediumAmount`) without changing the journey surface.
- Combine with HTTP error mapping patterns so named outcomes also appear in your error envelope.

## Implementation notes

- Downstream analytics should rely on `spec.outcomes` labels rather than raw `errorCode` values.
- Make sure business-rule failures consistently set `fail.errorCode` so the `FailedBusinessRule` predicate remains accurate.
