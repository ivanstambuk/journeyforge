# Use Case – Idempotent Create‑If‑Not‑Exists

Status: Draft | Last updated: 2025-11-20

## Problem

Implement a “create if missing, otherwise return existing” flow:
- Check if a resource exists.
- If it exists, return the existing resource.
- If it does not exist, create it and return the created resource.

## Relevant DSL Features

- HTTP `task` with `operationRef` for `GET` and `POST`.
- `choice` over `ok`/`status`.
- `succeed` / `fail` outcomes.

## Example – http-idempotent-create

Journey definition: `http-idempotent-create.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-idempotent-create
  version: 0.1.0
spec:
  apis:
    items:
      openApiRef: apis/items.openapi.yaml
  inputSchemaRef: schemas/http-idempotent-create-input.json
  outputSchemaRef: schemas/http-idempotent-create-output.json
  start: checkExisting
  states:
    checkExisting:
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
        resultVar: get
      next: decideExisting

    decideExisting:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.get.ok == true and context.get.status == 200
          next: alreadyExists
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.get.ok == false and context.get.status == 404
          next: create
      default: create

    alreadyExists:
      type: succeed
      outputVar: get

    create:
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
        resultVar: post
      next: decideCreate

    decideCreate:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.post.ok == true and context.post.status == 201
          next: created
      default: failed

    created:
      type: succeed
      outputVar: post

    failed:
      type: fail
      errorCode: CREATE_OR_LOOKUP_FAILED
      reason: "Create-or-get flow did not succeed"
```

Related files:
- OpenAPI: `http-idempotent-create.openapi.yaml`
- Schemas: `http-idempotent-create-input.json`, `http-idempotent-create-output.json`
