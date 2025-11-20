# Use Case – Parallel Credit Decision (Fan‑out & Join)

Status: Draft | Last updated: 2025-11-20

## Problem

Evaluate a credit decision by:
- Calling multiple upstream services in parallel (risk, limits, KYC).
- Aggregating their results.
- Approving only when all checks succeed; otherwise rejecting with a clear error.

## Relevant DSL Features

- `parallel` state with named branches.
- `join.strategy = allOf` and `join.errorPolicy`.
- `choice` over the aggregated `branches` map.
- `succeed` / `fail` outcomes.

## Example – credit-decision-parallel

Workflow: `credit-decision-parallel.workflow.yaml`

```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: credit-decision-parallel
  version: 0.1.0
spec:
  apis:
    risk:
      openApiRef: apis/serviceA.openapi.yaml
    limits:
      openApiRef: apis/serviceB.openapi.yaml
    kyc:
      openApiRef: apis/users.openapi.yaml
  inputSchemaRef: schemas/credit-decision-parallel-input.json
  outputSchemaRef: schemas/credit-decision-parallel-output.json
  start: evaluate
  states:
    evaluate:
      type: parallel
      parallel:
        branches:
          - name: risk
            start: riskCall
            states:
              riskCall:
                type: task
                task:
                  kind: httpCall
                  operationRef: risk.scoreUser
                  params:
                    headers:
                      Accept: application/json
                  timeoutMs: 2000
                  resultVar: result
                next: done
              done:
                type: succeed
          - name: limits
            start: limitsCall
            states:
              limitsCall:
                type: task
                task:
                  kind: httpCall
                  operationRef: limits.checkLimits
                  params:
                    headers:
                      Accept: application/json
                  timeoutMs: 2000
                  resultVar: result
                next: done
              done:
                type: succeed
          - name: kyc
            start: kycCall
            states:
              kycCall:
                type: task
                task:
                  kind: httpCall
                  operationRef: kyc.getUserById
                  params:
                    path:
                      userId: "${context.userId}"
                    headers:
                      Accept: application/json
                  timeoutMs: 2000
                  resultVar: result
                next: done
              done:
                type: succeed
        join:
          strategy: allOf
          errorPolicy: collectAll
          mapper:
            lang: dataweave
            expr: |
              {
                risk: branches.risk.result,
                limits: branches.limits.result,
                kyc: branches.kyc.result
              }
          resultVar: decisions
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                decisions.risk.ok == true and decisions.limits.ok == true and decisions.kyc.ok == true
          next: approve
        - when:
            predicate:
              lang: dataweave
              expr: |
                decisions.risk.ok == false or decisions.limits.ok == false or decisions.kyc.ok == false
          next: reject
      default: reject

    approve:
      type: succeed
      outputVar: decisions

    reject:
      type: fail
      errorCode: CREDIT_REJECTED
      reason: "One or more upstream checks failed"
```

Related files:
- OpenAPI: `credit-decision-parallel.openapi.yaml`
- Schemas: `credit-decision-parallel-input.json`, `credit-decision-parallel-output.json`
