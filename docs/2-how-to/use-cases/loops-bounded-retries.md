# Use Case – Bounded Loops & Retries

Status: Draft | Last updated: 2025-11-20

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/approval-loop/approval-loop.md`

## Problem

Model business flows that need to repeat a step several times (approvals, document uploads, status checks, notifications) without risking unbounded loops that hang API calls or journeys indefinitely.

We want to:
- Keep control flow readable and explicit.
- Bound loops structurally (attempt counters) and by time (`spec.execution.maxDurationSec` / per-state timeouts).
- Use `httpResilience` for per-call retries instead of hand-rolled HTTP retry loops where possible.

## Relevant DSL Features

- `choice` with `default` for branching and loop exit.
- `transform` to maintain counters and flags in `context`.
- `wait` / `webhook` for external-input driven loops (journeys only).
- HTTP `task` with `resultVar` for observable outcomes and `httpResilience` policies for retries.
- `spec.execution.maxDurationSec` and `spec.execution.onTimeout` for global time limits.

## Example – Manual approval loop (Journey)

Journey definition (conceptual): 

This journey:
- Waits for an approval decision.
- Allows a bounded number of "needs more info" cycles.
- Times out if no decision arrives in time.

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: approval-loop
  version: 0.1.0
spec:
  execution:
    maxDurationSec: 3600
    onTimeout:
      errorCode: APPROVAL_TIMEOUT
      reason: "No decision within 60 minutes"

  input:
    schema:
      type: object
      required: [decision]
      properties:
        decision: { type: string }
      additionalProperties: true

  start: init
  states:
    init:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              approvalAttempts: (context.approvalAttempts default: 0) as Number
            }
        target:
          kind: context
          path: ''
      next: waitForApproval

    waitForApproval:
      type: wait
      wait:
        channel: manual
        input:
          schema:
            type: object
            required: [decision]
            properties:
              decision: { type: string }
        timeoutSec: 300
        onTimeout: timedOut
        apply:
          mapper:
            lang: dataweave
            expr: |
              context ++ { decision: payload.decision }
      on:
        - when:
            predicate:
              lang: dataweave
              expr: |
                payload.decision == "approved"
          next: approved
        - when:
            predicate:
              lang: dataweave
              expr: |
                payload.decision == "needs_more_info"
          next: incrementAttempts
      default: rejected

    incrementAttempts:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              approvalAttempts: (context.approvalAttempts default: 0) + 1
            }
        target:
          kind: context
          path: ''
      next: decideLoop

    decideLoop:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.approvalAttempts < 3
          next: notifyAndReenter
      default: rejected

    notifyAndReenter:
      type: task
      task:
        kind: httpCall
        operationRef: notifications.sendApprovalReminder
        timeoutMs: 5000
        resultVar: reminder
      next: waitForApproval

    approved:
      type: succeed
      outputVar: decision

    rejected:
      type: fail
      errorCode: APPROVAL_REJECTED
      reason: "Approval rejected or attempts exhausted"

    timedOut:
      type: fail
      errorCode: APPROVAL_TIMEOUT
      reason: "No approval decision before timeout"
```

This pattern combines:
- An explicit attempt counter on `context`.
- A `choice` gate (`decideLoop`) that cleanly exits when attempts are exhausted.
- A global execution timeout as a backstop.

## Example – Bounded polling of downstream status (Api)

Journey (conceptual): 

This API:
- Accepts a request with a `jobId`.
- Polls a downstream status endpoint up to `maxPolls` times.
- Returns success, failure, or a "still running" outcome without unbounded blocking.

```yaml
apiVersion: v1
kind: Api
metadata:
  name: poll-status-api
  version: 0.1.0
spec:
  execution:
    maxDurationSec: 10
    onTimeout:
      errorCode: POLL_TIMEOUT
      reason: "Status polling exceeded 10 seconds"

  input:
    schema:
      type: object
      required: [jobId]
      properties:
        jobId: { type: string }
      additionalProperties: true

  start: init
  states:
    init:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              pollCount: 0,
              maxPolls: 3
            }
        target:
          kind: context
          path: ''
      next: poll

    poll:
      type: task
      task:
        kind: httpCall
        operationRef: jobs.getStatus
        params:
          path:
            jobId: "${context.jobId}"
        timeoutMs: 2000
        resiliencePolicyRef: standard
        resultVar: status
      next: decideStatus

    decideStatus:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.status.ok == true and context.status.body.state == "COMPLETED"
          next: done
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.status.ok == true and context.status.body.state == "FAILED"
          next: failed
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.pollCount + 1 < context.maxPolls
          next: incrementAndRepoll
      default: stillRunning

    incrementAndRepoll:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              pollCount: context.pollCount + 1
            }
        target:
          kind: context
          path: ''
      next: poll

    done:
      type: succeed
      outputVar: status

    failed:
      type: fail
      errorCode: JOB_FAILED
      reason: "Downstream job failed"

    stillRunning:
      type: succeed
      outputVar: status
```

Here, loops are:
- Bounded by an explicit `maxPolls`.
- Backed by `execution.maxDurationSec`.
- Using `httpResilience` for low-level HTTP retries, rather than looping purely for retries.

## Variations

The same pattern applies to:
- **Document collection loops** – model `waitForDocumentUpload` (as a `wait`/`webhook` state) → `validateDocument` (as a `task`/`transform` state) → `choice` on validation result. When documents are missing or invalid, send a "fix your document" email/notification (`task.httpCall` or `eventPublish`), increment `context.documentAttempts`, and loop back to `waitForDocumentUpload` until attempts are exhausted or an overall timeout fires, then fail with a clear error.
- **Notification retry loops** – model `sendNotification` as a `task` (HTTP call or `eventPublish`) followed by a `choice` on `context.notificationResult.ok` (or an ack payload when present). On failure branches, increment `context.notifyAttempts`, optionally record the last error, and loop back into `sendNotification` up to a small, explicit cap before either failing the journey/API call or handing off to compensation.
- **Multi-step customer interactions** – use `waitForCustomerInput` (`wait`) → `applyChanges` (`transform` / `task`) → `choice` on the requested action. Let `change` / `edit` loop back to `waitForCustomerInput` (incrementing `context.iteration`), and treat `confirm` / `cancel` paths as terminal exits. Guard the loop with a maximum iterations counter and `spec.execution.maxDurationSec` so a stuck client cannot hold the journey open indefinitely.
