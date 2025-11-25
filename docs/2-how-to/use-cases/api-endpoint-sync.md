# Use Case – Synchronous API Endpoint (No Journeys)

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-chained-calls-api/http-chained-calls-api.md`

## Problem

Expose a fully RESTful API that:
- Accepts a JSON payload in a single HTTP call.
- Fans out to one or more downstream HTTP APIs.
- Optionally applies DataWeave mapping or filtering.
- Returns a composed result in the same HTTP response.

There is no concept of journey initiation, status polling, or `journeyId` – from the client perspective this is just a normal synchronous HTTP endpoint.

## Relevant DSL Features

- `kind: Api` for synchronous, stateless HTTP endpoints.
- `spec.route` for controlling the external path and method.
- `spec.apis` and `operationRef` for OpenAPI-bound HTTP tasks.
- `task` (HTTP call) with structured result objects (`{status?, ok, headers, body, error?}`).
- `choice` to branch on HTTP outcomes (for example `ok` and `status`).
- `transform` for DataWeave-based response mapping.
- `spec.defaults` for shared HTTP headers and timeouts.
- `spec.errors` / `spec.outcomes` (optional) to standardise failure envelopes and, in combination with implementations, map failures to stable error codes and HTTP statuses.

## Example – http-chained-calls-api

API definition: `http-chained-calls-api.journey.yaml`

```yaml
apiVersion: v1
kind: Api
metadata:
  name: http-chained-calls-api
  version: 0.1.0
spec:
  route:
    path: /apis/http-chained-calls
    method: POST

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

  defaults:
    http:
      timeoutMs: 5000
      headers:
        Accept: application/json

  start: lookup
  states:
    lookup:
      type: task
      task:
        kind: httpCall:v1
        operationRef: users.getUserById
        params:
          path:
            userId: "${context.userId}"
        resultVar: user
      next: route

    route:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.user.ok == true and context.user.body.active == true
          next: create
      default: inactive

    create:
      type: task
      task:
        kind: httpCall:v1
        operationRef: accounts.createAccount
        body:
          mapper:
            lang: dataweave
            expr: |
              {
                ownerId: context.userId,
                email: context.user.body.email
              }
        resultVar: account
      next: decideCreate

    decideCreate:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.account.ok == true and context.account.status == 201
          next: done
      default: create_failed

    done:
      type: succeed
      outputVar: account

    inactive:
      type: fail
      errorCode: USER_INACTIVE
      reason: "User not active; account creation is not allowed"

    create_failed:
      type: fail
      errorCode: CREATE_FAILED
      reason: "Account creation failed or did not return 201"
```

Notes:
- From the client’s perspective this is a single `POST /api/v1/apis/http-chained-calls` call that either returns the account creation result or a mapped error.
- The engine reuses the same state machine as journeys, but does not expose `journeyId` or status/result polling endpoints for `kind: Api`.

Related files:
- Workflow spec: `http-chained-calls-api.journey.yaml`
- OpenAPI: `http-chained-calls-api.openapi.yaml`
- Schemas: `http-chained-calls-input.json`, `http-chained-calls-output.json`
