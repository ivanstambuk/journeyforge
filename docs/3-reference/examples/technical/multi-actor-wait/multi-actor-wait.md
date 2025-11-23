# Technical Pattern – Multi-actor wait with generic step

Status: Draft | Last updated: 2025-11-23

## Problem

Model a journey where:
- Multiple actors (for example signers, approvers, reviewers) must submit decisions over time.
- You want a **single external step endpoint** (generic `submitDecision` / `submitSignature`) instead of one step per actor.
- The journey needs to track per-actor state (`PENDING`, `SIGNED`, `DECLINED`, etc.) and optionally enforce **ordering** (sequential vs parallel).
- Partial progress (some actors finished, others pending) should be visible via status while the journey remains `Running`.

## When to use

- Flows where the same type of decision is collected from multiple actors:
  - Document signing (multi-signer).
  - Multi-party approval (for example manager + compliance + finance).
  - Group acknowledgements or confirmations.
- You want:
  - A simple HTTP surface with a single `/steps/{stepId}` endpoint.
  - Per-actor progress in `context` and in the final outcome.
  - Optional sequential enforcement without creating separate `wait` states per actor.
- Related patterns:
  - [External input & approval](../../../2-how-to/use-cases/external-input-approval.md) for single-actor waits.
  - [SLAs, timers, waits, and escalations](../../../2-how-to/use-cases/sla-timers-waits-and-escalations.md) for attaching timers to review paths.

## Relevant DSL features

- `wait` – exposed as a `/steps/{stepId}` endpoint for manual input (`channel: manual`).
- `timer` + `parallel` – optional, for global expiry / timeboxing.
- `transform` – to compute per-actor updates and build final outcomes.
- `choice` – to route on aggregate actor state (for example “all signed” vs “any declined”).

See `docs/3-reference/dsl.md` sections:
- External-input states (`wait`).
- Parallel + timer.
- Transform state.

## Example – document-signing-multi-signer

Artifacts for this example:

- Journey: [document-signing-multi-signer.journey.yaml](../../business/document-signing-multi-signer/document-signing-multi-signer.journey.yaml)
- OpenAPI: [document-signing-multi-signer.openapi.yaml](../../business/document-signing-multi-signer/document-signing-multi-signer.openapi.yaml)
- Arazzo: [document-signing-multi-signer.arazzo.yaml](../../business/document-signing-multi-signer/document-signing-multi-signer.arazzo.yaml)
- Business docs: [document-signing-multi-signer.md](../../business/document-signing-multi-signer/document-signing-multi-signer.md)

### Generic wait with actorId

The journey uses a single `wait` state and a generic `submitSignature` step:

```yaml
submitSignature:
  type: wait
  wait:
    channel: manual
    input:
      schema:
        title: SubmitSignatureInput
        type: object
        required: [signerId, decision]
        properties:
          signerId:
            type: string
          decision:
            type: string
            enum: [SIGNED, DECLINED]
          comment:
            type: string
        additionalProperties: true
    apply:
      mapper:
        lang: dataweave
        expr: |
          do {
            var isSequential = (context.signingMode default "PARALLEL") == "SEQUENTIAL"
            var pendingSigners =
              (context.signers default []) filter ((s) -> s.status == "PENDING")
            var nextSignerId =
              if (isSequential and sizeOf(pendingSigners) > 0)
              then
                (pendingSigners
                  orderBy ((s1, s2) ->
                    (s1.order default 0) <=> (s2.order default 0)))[0].signerId
              else null
            var updatedSigners =
              (context.signers default []) map (s) ->
                if (s.signerId == payload.signerId
                    and s.status == "PENDING"
                    and (not isSequential or payload.signerId == nextSignerId))
                then
                  s ++ {
                    status: payload.decision,
                    signedAt: now()
                  }
                else s
            context ++ { signers: updatedSigners }
          }
    on:
      - when:
          predicate:
            lang: dataweave
            expr: |
              (context.signers default []) any ((s) -> s.status == "DECLINED")
        next: completeDeclined
      - when:
          predicate:
            lang: dataweave
            expr: |
              (context.signers default []) != []
                and (context.signers default []) all ((s) -> s.status == "SIGNED")
        next: completeFullySigned
    default: submitSignature
```

Key ideas:
- **Generic step**: all actors call the same `/steps/submitSignature` endpoint, identified by `signerId` in the payload.
- **Sequential vs parallel**:
  - When `signingMode == "SEQUENTIAL"`, only the next signer (lowest `order` among `PENDING`) is allowed to transition; other calls are effectively no-ops.
  - When `signingMode == "PARALLEL"`, any `PENDING` signer can transition.
- **Aggregate routing**:
  - `any DECLINED` → a `completeDeclined` path.
  - `all SIGNED` → a `completeFullySigned` path.
  - Otherwise → loop back to `submitSignature` (partial progress).

## Variations and combinations

- **Multi-actor approval** – reuse the same pattern with `decision: APPROVED | REJECTED` and per-actor roles.
- **Role-based ordering** – instead of a numeric `order`, derive ordering from roles (for example `MANAGER` then `COMPLIANCE`).
- **Per-actor timeouts** – combine with per-actor timers or a global expiry branch to mark pending actors as `EXPIRED`.
- **Distinct step names** – when HTTP surfaces must reflect different roles (for example `/steps/approveManager`, `/steps/approveCompliance`), keep the internal pattern but expose separate `wait` states; the data-shaping logic remains the same.

## Implementation notes

- Context shape:
  - Keep a normalised `actors[]` (or `signers[]`) array in `context` with per-actor `status` and metadata.
  - Treat this array as the single source of truth for aggregate decisions.
- Idempotence:
  - Journeys should tolerate duplicate submissions from the same actor; the guard `s.status == "PENDING"` ensures later submissions don’t re-open decisions.
- Observability:
  - Consider projecting key fields (for example current pending actors) into the `JourneyStatus` response via a `response.outputVar` on the `wait` state when needed; the core pattern here focuses on internal state and final outcome.  

