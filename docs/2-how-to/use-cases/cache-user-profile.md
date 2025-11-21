# Use Case – Cache‑Aware User Profile Lookup

Status: Draft | Last updated: 2025-11-20

## Problem

Avoid hitting a downstream user profile service on every request by:
- Normalising input.
- Checking a cache keyed by user identity.
- Falling back to a remote call on cache miss.
- Writing the result back to the cache.

## Relevant DSL Features

- `transform` for context normalisation.
- Cache resources under `spec.resources.caches`.
- `task` kinds `cacheGet` and `cachePut`.
- HTTP `task` with `operationRef`.
- `choice` and `succeed`.

## Example – cache-user-profile

Journey definition: `cache-user-profile.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: cache-user-profile
  version: 0.1.0
spec:
  apis:
    users:
      openApiRef: apis/users.openapi.yaml
  resources:
    caches:
      defaultCache:
        kind: inMemory
        maxEntries: 10000
        ttlSeconds: 300
  input:
    schema:
      type: object
      required: [userId]
      properties:
        userId: { type: string }
        country: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        id: { type: string }
        name: { type: string }
        email:
          type: string
          format: email
        country: { type: string }
      additionalProperties: true
  start: normalise
  states:
    normalise:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            {
              user: {
                id: context.userId,
                country: context.country default: null
              }
            }
        target:
          kind: context
          path: user
      next: readCache

    readCache:
      type: task
      task:
        kind: cacheGet
        cacheRef: defaultCache
        key:
          mapper:
            lang: dataweave
            expr: |
              context.user.id
        resultVar: cachedUser
      next: chooseSource

    chooseSource:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.cachedUser != null
          next: useCache
      default: fetchRemote

    useCache:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context.cachedUser
        target:
          kind: var
        resultVar: profile
      next: done

    fetchRemote:
      type: task
      task:
        kind: httpCall
        operationRef: users.getUserById
        params:
          path:
            userId: "${context.user.id}"
          headers:
            Accept: application/json
        timeoutMs: 5000
        resultVar: remote
      next: storeCache

    storeCache:
      type: task
      task:
        kind: cachePut
        cacheRef: defaultCache
        key:
          mapper:
            lang: dataweave
            expr: |
              context.user.id
        value:
          mapper:
            lang: dataweave
            expr: |
              context.remote.body
        ttlSeconds: 600
      next: fromRemote

    fromRemote:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context.remote.body
        target:
          kind: var
        resultVar: profile
      next: done

    done:
      type: succeed
      outputVar: profile
```

Related files:
- OpenAPI: `cache-user-profile.openapi.yaml`
- Schemas: `cache-user-profile-input.json`, `cache-user-profile-output.json`
