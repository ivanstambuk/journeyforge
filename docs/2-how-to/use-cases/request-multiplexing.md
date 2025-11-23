# Use Case – Request Multiplexing & Composition

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-chained-calls/http-chained-calls.md`

## Problem

Combine multiple upstream HTTP calls into a single journey:
- Fetch data from one or more services.
- Optionally chain calls (output of one feeds the next).
- Decide whether to succeed or fail based on combined results.

## Relevant DSL Features

- Data-driven branching pattern: `task` writes results into `context.<resultVar>`, and a `choice` state branches on that data.
- `task` with `kind: httpCall` and `resultVar`.
- `choice` with DataWeave predicates over `context.<resultVar>`.
- `succeed` / `fail` with explicit error codes.
- Optional `parallel` for future concurrent branches.

## Example 1 – Chained calls (user → account)

Journey definition: `http-chained-calls.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-chained-calls
  version: 0.1.0
spec:
  apis:
    users:
      openApiRef: apis/users.openapi.yaml
    accounts:
      openApiRef: apis/accounts.openapi.yaml
  input:
    schema:
      type: object
      required: [userId]
      properties:
        userId: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        status: { type: integer }
        ok: { type: boolean }
        headers:
          type: object
          additionalProperties: { type: string }
        body:
          type: object
          properties:
            accountId: { type: string }
            ownerId: { type: string }
            email:
              type: string
              format: email
          additionalProperties: true
        error:
          type: object
          properties:
            type: { type: string }
            message: { type: string }
          additionalProperties: true
      additionalProperties: true
  start: lookup
  states:
    lookup:
      type: task
      task:
        kind: httpCall
        operationRef: users.getUserById
        params:
          path:
            userId: "${context.userId}"
          headers:
            Accept: application/json
        timeoutMs: 5000
        resultVar: a
      next: route

    route:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.a.ok == true and context.a.body.active == true
          next: create
      default: inactive

    create:
      type: task
      task:
        kind: httpCall
        operationRef: accounts.createAccount
        params:
          headers:
            Accept: application/json
        body:
          mapper:
            lang: dataweave
            expr: |
              {
                ownerId: context.userId,
                email: context.a.body.email
              }
        timeoutMs: 5000
        resultVar: b
      next: decide_create

    decide_create:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.b.ok == true and context.b.status == 201
          next: done
      default: create_failed

    done:
      type: succeed
      outputVar: b

    inactive:
      type: fail
      errorCode: USER_INACTIVE
      reason: "User not active; create is not allowed"

    create_failed:
      type: fail
      errorCode: CREATE_FAILED
      reason: "Account creation failed or did not return 201"
```

Related files:
- OpenAPI: `http-chained-calls.openapi.yaml`

## Example 2 – Aggregating errors across calls

Journey definition: `http-aggregate-errors.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-aggregate-errors
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [id]
      properties:
        id: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        status: { type: integer }
        ok: { type: boolean }
        headers:
          type: object
          additionalProperties: { type: string }
        body: {}
        error:
          type: object
          properties:
            type: { type: string }
            message: { type: string }
          additionalProperties: true
      additionalProperties: true
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
  start: call_a
  states:
    call_a:
      type: task
      task:
        kind: httpCall
        method: GET
        url: "https://api.example.com/serviceA/${context.id}"
        headers:
          Accept: application/json
        timeoutMs: 5000
        resultVar: a
      next: call_b

    call_b:
      type: task
      task:
        kind: httpCall
        method: GET
        url: "https://api.example.com/serviceB/${context.id}"
        headers:
          Accept: application/json
        timeoutMs: 5000
        resultVar: b
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                (context.a.ok == false) or (context.b.ok == false)
          next: failed
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.a.ok == true and context.b.ok == true
          next: done
      default: failed

    done:
      type: succeed
      outputVar: b

    failed:
      type: fail
      errorCode: UPSTREAM_FAILURE
      reason: "One or more upstream calls failed"
```

Related files:
- OpenAPI: `http-aggregate-errors.openapi.yaml`

## Example 3 – Conditional composition

Journey definition: `http-conditional-composition.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-conditional-composition
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      required: [featureFlag, id, name]
      additionalProperties: true
      properties:
        featureFlag: { type: string }
        id: { type: string }
        name: { type: string }
  output:
    schema:
      type: object
      properties:
        status: { type: integer }
        ok: { type: boolean }
        headers:
          type: object
          additionalProperties: { type: string }
        body: {}
        error:
          type: object
          properties:
            type: { type: string }
            message: { type: string }
          additionalProperties: true
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
  start: route
  states:
    route:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.useBeta == true
          next: call_beta
      default: call_default

    call_default:
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
                id: context.inputId default: null,
                name: context.name default: null
              }
        timeoutMs: 5000
        resultVar: api
      next: decide

    call_beta:
      type: task
      task:
        kind: httpCall
        method: POST
        url: "https://beta.api.example.com/items"
        headers:
          Content-Type: application/json
          Accept: application/json
          X-Env: "beta"
          X-Feature-Flag: "${context.featureFlag}"
        body: |
          {"id": "${context.id}", "name": "${context.name}-beta"}
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
                context.api.ok == true and (context.api.status == 200 or context.api.status == 201)
          next: done
      default: failed

    done:
      type: succeed
      outputVar: api

    failed:
      type: fail
      errorCode: CREATE_FAILED
      reason: "POST did not return success"
```

Related files:
- OpenAPI: `http-conditional-composition.openapi.yaml`
- Schemas: `http-conditional-composition-input.json`, `http-conditional-composition-output.json`

## Future: Parallel multiplexing

Once you adopt the `parallel` state, you can:
- Define a branch per upstream.
- Use `join.mapper` to build an aggregate result from `branches.<name>`.
- Apply the same `choice` + `succeed`/`fail` patterns over the aggregated object.

See `dsl.md` section “Parallel State” for the state shape and join semantics.
