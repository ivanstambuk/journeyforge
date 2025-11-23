# Technical Pattern – HTTP error handling & Problem Details

Status: Draft | Last updated: 2025-11-23

## Problem

Handle upstream HTTP failures and timeouts in a structured way so that:
- Failures are recorded as data in `context`.
- Errors are normalised into RFC 9457 Problem Details.
- Journeys can either return a Problem document as output or fail with a mapped error code.

## When to use

- You call downstream HTTP APIs and need consistent error behaviour across journeys.
- You want observability into downstream status codes and error types (including timeouts).
- You want a single canonical error shape (Problem Details) regardless of upstream payloads.

## Relevant DSL features

- HTTP `task` result objects (`status`, `ok`, `headers`, `body`, `error`).
- `choice` for branching on success, failure, and timeout.
- `transform` to build Problem Details documents.
- `fail` with `errorCode` / `reason` aligned with Problem `type` / `title` / `detail`.
- `spec.errors.normalisers` and `task.errorMapping` for shared and inline normalisation.

## Example 1 – Branch on generic failure (`http-failure-branch`)

Artifacts for this example:

- Journey: [http-failure-branch.journey.yaml](../http-failure-branch/http-failure-branch.journey.yaml)
- OpenAPI: [http-failure-branch.openapi.yaml](../http-failure-branch/http-failure-branch.openapi.yaml)
- Arazzo: [http-failure-branch.arazzo.yaml](../http-failure-branch/http-failure-branch.arazzo.yaml)

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-failure-branch
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [inputId]
      additionalProperties: true
      properties:
        inputId: { type: string }
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

This pattern:
- Uses `resultVar: api` to capture the full HTTP result.
- Branches on `api.ok` to decide between success and a generic downstream failure.

## Example 2 – Timeouts vs success (`http-timeout-branch`)

Artifacts for this example:

- Journey: [http-timeout-branch.journey.yaml](../http-timeout-branch/http-timeout-branch.journey.yaml)
- OpenAPI: [http-timeout-branch.openapi.yaml](../http-timeout-branch/http-timeout-branch.openapi.yaml)
- Arazzo: [http-timeout-branch.arazzo.yaml](../http-timeout-branch/http-timeout-branch.arazzo.yaml)

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-timeout-branch
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [id]
      additionalProperties: true
      properties:
        id: { type: string }
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

This pattern:
- Uses `api.error.type == 'TIMEOUT'` to distinguish timeouts from other failures.
- Fails with a dedicated `TIMEOUT` error code when the downstream doesn’t respond in time.

## Example 3 – Normalising to RFC 9457 Problem Details (`http-problem-details`)

Artifacts for this example:

- Journey: [http-problem-details.journey.yaml](../http-problem-details/http-problem-details.journey.yaml)
- OpenAPI: [http-problem-details.openapi.yaml](../http-problem-details/http-problem-details.openapi.yaml)
- Arazzo: [http-problem-details.arazzo.yaml](../http-problem-details/http-problem-details.arazzo.yaml)

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-problem-details
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [id]
      additionalProperties: true
      properties:
        id: { type: string }
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

This pattern:
- Builds a Problem Details document from the HTTP result.
- Offers two modes:
  - “Soft” mode returns the Problem as a successful output.
  - “Hard” mode fails the journey with the Problem `type` as the `errorCode`.

## Variations and combinations

- Combine generic failure branching with Problem Details so all failures produce a canonical error.
- Use `spec.errors.normalisers` to centralise Problem building logic across journeys.

## Implementation notes

- When using `errorCode` values derived from Problem `type`, keep them stable over time for easier reporting and alerting.

