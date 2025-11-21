# Use Case – Error Handling & RFC 9457 Problem Details

Status: Draft | Last updated: 2025-11-20

## Problem

Handle upstream failures and timeouts in a structured way:
- Record them as data in `context`.
- Normalise them into RFC 9457 Problem Details.
- Decide whether to return a Problem document as output or fail the journey with a mapped error code.

## Relevant DSL Features

- HTTP `task` result objects (`status`, `ok`, `headers`, `body`, `error`).
- `choice` for decision logic.
- `transform` to build Problem Details.
- `fail` with `errorCode` / `reason` aligned to Problem `type` / `title`/`detail`.
- `spec.errors` and `task.errorMapping` for shared and inline normalisation.

## Example 1 – Branch on generic failure

Journey definition: `http-failure-branch.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-failure-branch
  version: 0.1.0
spec:
  inputSchemaRef: schemas/http-failure-branch-input.json
  outputSchemaRef: schemas/http-failure-branch-output.json
  apis:
    items:
      openApiRef: apis/items.openapi.yaml
    users:
      openApiRef: apis/users.openapi.yaml
    accounts:
      openApiRef: apis/accounts.openapi.yaml
    serviceA:
      openApiRef: apis/serviceA.openapi.yaml
    serviceB:
      openApiRef: apis/serviceB.openapi.yaml
  start: call_api
  states:
    call_api:
      type: task
      task:
        kind: httpCall
        method: GET
        url: "https://api.example.com/missing/${context.inputId}"
        headers:
          Accept: application/json
        timeoutMs: 5000
        resultVar: api
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.api.ok == false
          next: failed
      default: done

    done:
      type: succeed
      outputVar: api

    failed:
      type: fail
      errorCode: DOWNSTREAM_ERROR
      reason: "Downstream returned non-2xx or network error"
```

Related files:
- OpenAPI: `http-failure-branch.openapi.yaml`
- Schemas: `http-failure-branch-input.json`, `http-failure-branch-output.json`

## Example 2 – Timeouts vs success

Journey definition: `http-timeout-branch.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-timeout-branch
  version: 0.1.0
spec:
  inputSchemaRef: schemas/http-timeout-branch-input.json
  outputSchemaRef: schemas/http-timeout-branch-output.json
  apis:
    items:
      openApiRef: apis/items.openapi.yaml
    users:
      openApiRef: apis/users.openapi.yaml
    accounts:
      openApiRef: apis/accounts.openapi.yaml
    serviceA:
      openApiRef: apis/serviceA.openapi.yaml
    serviceB:
      openApiRef: apis/serviceB.openapi.yaml
  start: slow_call
  states:
    slow_call:
      type: task
      task:
        kind: httpCall
        method: GET
        url: "https://api.example.com/slow/${context.id}"
        headers:
          Accept: application/json
        timeoutMs: 1   # intentionally tiny for illustration
        resultVar: api
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.api.ok == false and context.api.error.type == 'TIMEOUT'
          next: degraded
      default: proceed

    degraded:
      type: fail
      errorCode: TIMEOUT
      reason: "Downstream timeout"

    proceed:
      type: succeed
      outputVar: api
```

Related files:
- OpenAPI: `http-timeout-branch.openapi.yaml`
- Schemas: `http-timeout-branch-input.json`, `http-timeout-branch-output.json`

## Example 3 – Normalising to RFC 9457 Problem Details

Journey definition: `http-problem-details.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-problem-details
  version: 0.1.0
spec:
  inputSchemaRef: schemas/http-problem-details-input.json
  outputSchemaRef: schemas/http-problem-details-output.json
  apis:
    items:
      openApiRef: apis/items.openapi.yaml
  start: call_api
  states:
    call_api:
      type: task
      task:
        kind: httpCall
        operationRef: items.getItemById
        params:
          path:
            id: "${context.id}"
          headers:
            Accept: application/json
        timeoutMs: 5000
        resultVar: api
      next: decide_error

    decide_error:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.api.ok == false
          next: error_mode
      default: done

    error_mode:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.soft == true
          next: normalise_soft
      default: normalise_hard

    normalise_soft:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            {
              type: 'https://example.com/probs/upstream-error',
              title: 'Upstream service failure',
              status: context.api.status default: 500,
              detail: context.api.error.message default: 'Upstream error'
            }
        target:
          kind: var
        resultVar: problem
      next: return_problem

    return_problem:
      type: succeed
      outputVar: problem

    normalise_hard:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            {
              type: 'https://example.com/probs/upstream-error',
              title: 'Upstream service failure',
              status: context.api.status default: 500,
              detail: context.api.error.message default: 'Upstream error'
            }
        target:
          kind: var
        resultVar: problem
      next: fail_with_problem

    fail_with_problem:
      type: fail
      errorCode: "https://example.com/probs/upstream-error"
      reason: "Upstream service failure"
```

Related files:
- OpenAPI: `http-problem-details.openapi.yaml`
- Schemas: `http-problem-details-input.json`, `http-problem-details-output.json`
