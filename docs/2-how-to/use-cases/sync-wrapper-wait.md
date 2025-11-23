# Use Case – Sync Wrapper over Wait/Steps

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/sync-wrapper-wait/sync-wrapper-wait.md`

## Problem

Model a long‑running operation that:
- Starts via a single request.
- Waits for an external completion event.
- Exposes a clear timeout failure if the event never arrives in time.

## Relevant DSL Features

- `wait` with `timeoutSec` and `onTimeout`.
- Step subresources (`/steps/{stepId}`).
- `succeed` / `fail` for DONE vs timeout.

## Example – sync-wrapper-wait

Journey definition: `sync-wrapper-wait.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: sync-wrapper-wait
  version: 0.1.0
spec:
  input:
    schema:
      type: object
      properties:
        status: { type: string }
      additionalProperties: true
  output:
    schema:
      type: object
      properties:
        status: { type: string }
      additionalProperties: true
  start: startLongRunning
  states:
    startLongRunning:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ { 'startedAt': now() }
        target:
          kind: context
          path: ''
      next: waitStep

    waitStep:
      type: wait
      wait:
        channel: manual
        input:
          schema:
            type: object
            properties:
              status: { type: string }
            additionalProperties: true
        timeoutSec: 10
        onTimeout: timedOut
        apply:
          mapper:
            lang: dataweave
            expr: |
              context ++ { 'event': payload }
        on:
          - when:
              predicate:
                lang: dataweave
                expr: |
                  payload.status == 'DONE'
            next: done

    done:
      type: succeed
      outputVar: event

    timedOut:
      type: fail
      errorCode: OPERATION_TIMED_OUT
      reason: "No completion event received before timeout"
```

Related files:
- OpenAPI: `sync-wrapper-wait.openapi.yaml`
