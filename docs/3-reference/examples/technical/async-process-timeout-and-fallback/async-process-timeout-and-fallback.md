# Technical Pattern – Async process timeout and fallback

Status: Draft | Last updated: 2025-11-23

## Problem

Model an async external process where:
- A primary provider runs work in the background and reports completion via callback.
- The journey must enforce an SLA (“complete within N or give up”), marking timeouts explicitly.
- After a timeout, the journey should optionally wait for provider recovery with backoff and then switch to a fallback provider.
- Late callbacks from a provider MUST NOT disturb a decision once the journey has moved on.

## When to use

- You start an async job (for example KYC check, credit decision, batch job) with a provider that responds via webhook-style callback.
- You need to distinguish **completed within SLA**, **timed out**, and **failed** outcomes in a single journey definition.
- You want to model a **recovery window** with retries/backoff and finally switch to a **fallback provider** when the primary remains unhealthy.
- Related patterns:
  - [Timer-based timeout with parallel + timer](../../../2-how-to/use-cases/timer-parallel-timeout.md) for simpler “user action OR timeout” races.
  - [SLAs, timers, waits, and escalations](../../../2-how-to/use-cases/sla-timers-waits-and-escalations.md) for state-bound SLAs on review steps.

## Relevant DSL features

- `task.kind: httpCall` – start async jobs, probe health, and call fallback providers.
- `webhook` – accept async callbacks for job completion.
- `timer` – represent SLA windows and backoff delays.
- `parallel` – race callback branches against SLA timers using `join.strategy: anyOf`.
- `choice` + `transform` – express recovery loops and build structured outcomes.

See `docs/3-reference/dsl.md` sections:
- External input and timers (`wait` / `webhook` / `timer`).
- Parallel state and join strategies.
- HTTP tasks and result handling.

## Example – kyc-async-timeout-fallback

Artifacts for this example:

- Journey: [kyc-async-timeout-fallback.journey.yaml](../../business/kyc-async-timeout-fallback/kyc-async-timeout-fallback.journey.yaml)
- OpenAPI: [kyc-async-timeout-fallback.openapi.yaml](../../business/kyc-async-timeout-fallback/kyc-async-timeout-fallback.openapi.yaml)
- Arazzo: [kyc-async-timeout-fallback.arazzo.yaml](../../business/kyc-async-timeout-fallback/kyc-async-timeout-fallback.arazzo.yaml)
- Business docs: [kyc-async-timeout-fallback.md](../../business/kyc-async-timeout-fallback/kyc-async-timeout-fallback.md)

### Core race – callback vs SLA timer

Simplified parallel race:

```yaml
primaryKycRace:
  type: parallel
  parallel:
    branches:
      - name: callbackBranch
        start: waitForPrimaryKyc
        states:
          waitForPrimaryKyc:
            type: webhook
            webhook:
              input:
                schema: { /* jobId, status, reasons[] */ }
              apply:
                mapper:
                  lang: dataweave
                  expr: |
                    context ++ { primaryKycCallback: payload }
              on:
                - when:
                    predicate:
                      lang: dataweave
                      expr: |
                        context.outcome != null
                  next: ignorePrimaryCallback
                - when:
                    predicate:
                      lang: dataweave
                      expr: |
                        payload.status == "APPROVED"
                  next: completeFromPrimaryApproved
              default: completeFromPrimaryRejected

          ignorePrimaryCallback:
            type: transform
            transform:
              mapper: { lang: dataweave, expr: context }
              target: { kind: context, path: "" }
            next: donePrimaryCallback

          completeFromPrimaryApproved:
            type: transform
            transform:
              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    outcome: {
                      status: "COMPLETED_PRIMARY",
                      completionSource: "PRIMARY_CALLBACK"
                    }
                  }
              target: { kind: context, path: "" }
            next: donePrimaryCallback

          donePrimaryCallback:
            type: succeed
            outputVar: outcome

      - name: slaBranch
        start: primarySlaTimer
        states:
          primarySlaTimer:
            type: timer
            timer:
              duration: "PT5M"
            next: markPrimaryTimedOut

          markPrimaryTimedOut:
            type: transform
            transform:
              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    outcome: {
                      status: "TIMED_OUT",
                      completionSource: "PRIMARY_SLA_TIMER",
                      timeoutReasonCode: "PRIMARY_SLA_EXPIRED"
                    }
                  }
              target: { kind: context, path: "" }
            next: startRecoveryOrFallback

    join:
      strategy: anyOf
      errorPolicy: collectAll
```

Key aspects:
- The SLA timer lives in its own branch; whichever branch reaches a terminal state first determines the journey outcome.
- The callback branch checks `context.outcome` and routes to `ignorePrimaryCallback` so late callbacks after the SLA branch wins are a no-op.

### Recovery loop with backoff and fallback

After a timeout, a recovery loop uses `timer` for backoff and `task` for health checks:

```yaml
recoveryLoop:
  type: choice
  choices:
    - when:
        predicate:
          lang: dataweave
          expr: |
            context.recoveryAttempt < 3
      next: waitBeforeRetry
  default: startFallback

waitBeforeRetry:
  type: timer
  timer:
    duration:
      mapper:
        lang: dataweave
        expr: |
          if (context.recoveryAttempt == 0) "PT30S"
          else if (context.recoveryAttempt == 1) "PT2M"
          else "PT5M"
  next: retryPrimaryHealth

retryPrimaryHealth:
  type: task
  task:
    kind: httpCall
    method: GET
    url: https://primary.example.com/health
    timeoutMs: 2000
    resultVar: primaryHealth
  next: evaluatePrimaryHealth

evaluatePrimaryHealth:
  type: choice
  choices:
    - when:
        predicate:
          lang: dataweave
          expr: |
            primaryHealth.ok == true
      next: startFallback
  default: incrementRecoveryAttempt
```

The fallback path then starts a second async job and uses a `webhook` to complete from the fallback provider, mirroring the primary branch but without another SLA race.

## Variations and combinations

- **Pure timeout, no fallback** – omit the recovery loop and fallback branch; the SLA branch sets a timeout outcome and the journey completes.
- **State-bound SLAs on human review** – use `wait.timeoutSec + onTimeout` when the SLA is tied to a single review state rather than a provider callback.
- **Multiple fallbacks** – extend the recovery loop to chain more than one fallback provider, or to route based on error codes.

## Implementation notes

- Late callbacks:
  - Always guard `webhook` handlers with a context check (for example `context.outcome != null`) to avoid changing the outcome after a timeout or fallback decision.
- Observability:
  - Surface `status`, `completionSource`, and reason codes in the journey output so downstream systems and dashboards can distinguish:
    - Primary vs fallback completion.
    - SLA-driven timeouts vs provider-driven failures.
- Idempotence:
  - Providers should treat repeated start calls and callbacks as idempotent when possible; journeys should avoid assuming exactly-once delivery of callbacks.

