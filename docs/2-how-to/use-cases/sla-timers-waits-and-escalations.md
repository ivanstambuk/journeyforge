# Use Case – SLAs, Timers, Waits, and Escalations

Status: Draft | Last updated: 2025-11-23

This use case shows how to model **state-bound SLAs** and **escalation paths** inside a journey using:

- `wait` states with `timeoutSec + onTimeout` for review SLAs bound to a single state.
- `parallel` + `timer` for “human action OR timeout after N” races.
- Domain-level outcome codes (`status` plus `*ReasonCode`/`*Reason`) alongside the technical `JourneyOutcome.error` envelope.

It focuses on the pattern rather than a specific domain. Example journeys that use these ideas include:

- `support-case-sla` – support case with response SLA and give-up window.
- `high-value-transfer` – settlement SLA with hard timeout.
- `privacy-erasure-sla` – privacy erasure request with a 2-business-day review SLA and escalation.

## 1. State-bound SLA on a wait state

Use a `wait` state with `timeoutSec` and `onTimeout` when:
- The SLA is tied to **one manual review step** (for example, “Privacy Desk must decide within 2 business days”).
- You want a single breach path that records an outcome or escalates to another review state.

Example (simplified):

```yaml
reviewRequest:
  type: wait
  wait:
    channel: manual
    input:
      schema:
        title: ReviewDecisionInput
        type: object
        required: [decision]
        properties:
          decision:
            type: string
            enum: [APPROVE, REJECT]
          reason:
            type: string
        additionalProperties: true
    default: ingestReviewDecision
    timeoutSec: 172800            # ~2 days; model "2 business days" as needed
    onTimeout: escalatePath

ingestReviewDecision:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          reviewDecision: context.payload
        }
    target:
      kind: context
  next: routeReviewDecision

routeReviewDecision:
  type: choice
  choices:
    - when:
        predicate:
          lang: dataweave
          expr: |
            context.payload.decision == "APPROVE"
      next: approvedPath
    - when:
        predicate:
          lang: dataweave
          expr: |
            context.payload.decision == "REJECT"
      next: rejectedPath
  default: ignoreUpdate
```

Key points:
- `timeoutSec` expresses the review window in seconds; for “business days” you can:
  - Use a fixed approximation (for example 2 × 24h), or
  - Derive a value from context via a preceding `transform` that knows about your business calendar.
- `onTimeout` should route to a clear SLA breach handler (`escalatePath`) instead of silently failing.

### Escalation after SLA breach

From `onTimeout`, you typically:
- Record escalation metadata (for example `escalatedAt`, `escalationReasonCode`, `escalationReason`).
- Notify an escalation channel.
- Optionally enter a second review step.

Example:

```yaml
escalatePath:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          escalatedAt: now(),
          escalationReasonCode: "REVIEW_SLA_BREACH",
          escalationReason: "Review SLA window expired without a decision."
        }
    target:
      kind: context
      path: ""
  next: notifyEscalation
```

In `privacy-erasure-sla`, this pattern escalates from a Privacy Desk review to a Senior Privacy Officer when the 2-business-day SLA expires.

## 2. Race between human action and timer (parallel + timer)

Use `parallel` + `timer` when:
- You need a true race between **“human/system action”** and **“timeout”**.
- Either side can “win” the outcome (for example “agent resolves OR SLA escalates OR give-up auto-closes”).

Example sketch:

```yaml
waitOrTimeout:
  type: parallel
  parallel:
    branches:
	      - name: humanBranch
	        start: waitForInput
	        states:
	          waitForInput:
	            type: wait
	            wait:
	              channel: manual
	              input:
	                schema: { /* ... */ }
	            next: decideInput
	          decideInput:
	            type: choice
	            choices:
	              - when:
	                  predicate:
	                    lang: dataweave
	                    expr: |
	                      context.payload.decision == "COMPLETE"
	                next: markCompleted
	            default: waitForInput
	          markCompleted:
	            type: transform
	            transform:
	              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    outcome: {
                      status: "COMPLETED"
                    }
                  }
              target:
                kind: context
                path: ""
            next: doneCompleted
          doneCompleted:
            type: succeed
            outputVar: outcome

      - name: timeoutBranch
        start: slaTimer
        states:
          slaTimer:
            type: timer
            timer:
              duration: "PT4H"
            next: markTimedOut

          markTimedOut:
            type: transform
            transform:
              mapper:
                lang: dataweave
                expr: |
                  context ++ {
                    outcome: {
                      status: "TIMED_OUT",
                      failureReasonCode: "SLA_EXPIRED",
                      failureReason: "No completion within SLA window."
                    }
                  }
              target:
                kind: context
                path: ""
            next: doneTimedOut

          doneTimedOut:
            type: succeed
            outputVar: outcome

    join:
      strategy: anyOf
      errorPolicy: collectAll
```

Key points:
- The human branch uses `wait` without `timeoutSec`; the timer branch uses `timer.duration`.
- `join.strategy: anyOf` ensures that whichever branch reaches a terminal state first determines the outcome.
- This pattern is used in business journeys like `support-case-sla` (“agent resolves OR SLA escalates OR give-up auto-closes”) and `high-value-transfer` (“settlement callback OR settlement SLA timeout”).

## 3. Modelling outcome codes and reasons

Domain outcomes should expose **machine codes** plus optional human-readable text, separate from the technical error envelope.

Pattern:

```yaml
output:
  schema:
    title: ExampleOutcome
    type: object
    required:
      - status
    properties:
      status:
        type: string
        enum:
          - COMPLETED
          - FAILED
          - REJECTED
      failureReasonCode:
        type: string
      failureReason:
        type: string
      rejectionReasonCode:
        type: string
      rejectionReason:
        type: string
    additionalProperties: true
```

Example mapper for a failed outcome after an SLA breach:

```yaml
type: transform
transform:
  mapper:
    lang: dataweave
    expr: |
      context ++ {
        outcome: {
          status: "FAILED",
          failureReasonCode: "SLA_EXPIRED",
          failureReason: "Request not completed within configured SLA window."
        }
      }
  target:
    kind: context
    path: ""
next: journeyCompleted
```

Guidance:
- Use `status` for the coarse domain outcome.
- Use `<kind>ReasonCode` for machine-friendly identifiers (aligned with your domain enums).
- Use `<kind>Reason` for human-readable text that UIs can render directly or map to localised strings.
- Keep `JourneyOutcome.error.{code,reason}` for **technical errors** (engine timeouts, unexpected exceptions, platform failures), not for domain-level SLA transitions.

## 4. Choosing between wait timeout and parallel + timer

Use `wait.timeoutSec + onTimeout` when:
- The SLA applies naturally to a **single review state**.
- The breach path should unconditionally escalate or record a specific failure, without competing branches.
- You do not need intermediate “user action OR timeout OR give-up” races.

Use `parallel` + `timer` when:
- Multiple branches can legitimately win (for example, resolve vs escalate vs auto-close).
- You need separate timers for different deadlines (response SLA, give-up window, etc.).
- You want to reuse the same `wait` state without coupling it directly to a `timeoutSec`.

Both patterns can be combined in larger journeys. For example:
- `support-case-sla` uses `parallel` + `timer` to race **agent work**, **SLA escalation**, and **give-up auto-close**.
- `privacy-erasure-sla` uses `wait.timeoutSec + onTimeout` to express a **review SLA** on a single state and then escalates to a second review state.
