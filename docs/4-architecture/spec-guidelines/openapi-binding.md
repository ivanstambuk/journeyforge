# OpenAPI Binding – Using operationId in HTTP tasks

Status: Draft | Last updated: 2025-11-19

## Summary
- Declare downstream APIs under `spec.apis` with `openApiRef` (YAML OAS 3.1).
- In HTTP `task`, prefer `operationRef: <apiName>.<operationId>` over raw URLs.
- Map path/query/headers via `params`; build request bodies with strings/objects or DataWeave mappers.

## Resolution rules
- Resolve `<apiName>` to the catalog entry; load OAS.
- Lookup `<operationId>` across all paths; unique match required (validation error otherwise).
- Build the absolute URL from the exactly one `servers` entry (server variables are out of scope for the initial version).
- Apply `params.path` to path template variables, `params.query` as query params, and `params.headers` as headers.
- If `body` is present, use it only for operations with a requestBody; type validation against OAS is deferred to a future feature.

## Examples
```yaml
spec:
  apis:
    users: { openApiRef: apis/users.openapi.yaml }
    accounts: { openApiRef: apis/accounts.openapi.yaml }
  states:
    lookup:
      type: task
      task:
        kind: httpCall
        operationRef: users.getUserById
        params:
          path: { userId: "${context.userId}" }
          headers: { Accept: application/json }
        timeoutMs: 5000
        resultVar: a
      next: route
```
