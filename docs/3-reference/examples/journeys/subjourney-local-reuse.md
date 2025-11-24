# Journey – subjourney-local-reuse

> Demonstrates local subjourneys with explicit input/output mapping and propagate vs capture failure behaviour.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [subjourney-local-reuse.journey.yaml](../technical/subjourney-local-reuse/subjourney-local-reuse.journey.yaml) |
| OpenAPI (per-journey) | [subjourney-local-reuse.openapi.yaml](../technical/subjourney-local-reuse/subjourney-local-reuse.openapi.yaml) |
| Arazzo workflow | [subjourney-local-reuse.arazzo.yaml](../technical/subjourney-local-reuse/subjourney-local-reuse.arazzo.yaml) |

## Summary

This journey illustrates how to use local, intra-spec subjourneys (`spec.subjourneys` + `type: subjourney`) to factor reusable logic and control how child results and failures are projected back into the parent context.

High-level flow:

- The journey starts with a simple cart and customer identifier.
- It calls a `computeTotals` subjourney to calculate cart totals and discounts.
- It then calls a `riskCheck` subjourney that returns a mini-outcome (via `resultKind: outcome`) and may be captured even on failure.
- In parallel with risk, it calls an `enrichProfile` subjourney that fetches additional customer profile data but uses capture semantics so profile failures do not fail the whole journey.
- The final decision is derived from the captured risk outcome and the aggregated context, returning either APPROVED, REJECTED, or ERROR.

## Contracts at a glance

- **Input schema** – `SubjourneyLocalReuseStartRequest`:
  - `cart: { items: [{ sku, unitPrice, quantity }] }`
  - `customerId: string`
  - Optional `segment: string`, `simulateRiskFailure: boolean`, `simulateProfileFailure: boolean`.
- **Output schema** – `SubjourneyLocalReuseOutcome` exposed via `JourneyOutcome.output` with:
  - `decision: "APPROVED" | "REJECTED" | "ERROR"`.
  - `totals: { itemCount, subtotal, discountRate, total }`.
  - `risk`: mini-outcome object matching the `resultKind: outcome` shape (phase, terminationKind, output, error).
  - `profile`: object describing the enriched profile, or omitted/null when profile enrichment failed under capture semantics.
- **Named outcomes** – not used; the decision is expressed via `JourneyOutcome.output.decision` and the risk mini-outcome.

## Step overview (Arazzo workflow)

Here’s a breakdown of the steps you’ll call over the Journeys API for the primary workflow described in `subjourney-local-reuse.arazzo.yaml`.

| # | Step ID | Description | Operation ID | Parameters | Success Criteria | Outputs |
|---:|---------|-------------|--------------|------------|------------------|---------|
| 1 | `startJourney` | Start a new `subjourney-local-reuse` journey instance (synchronous). | `subjourneyLocalReuse_start` | Body: `startRequest` as defined by `SubjourneyLocalReuseStartRequest`. | `$statusCode == 200`, `phase == "SUCCEEDED"` or `"FAILED"`. | `JourneyOutcome` with `output` matching `SubjourneyLocalReuseOutcome`. |
| 2 | `getResult` | (Optional) Re-fetch the final journey outcome by id. | `subjourneyLocalReuse_getResult` | Path: `journeyId` from step 1 (or from `JourneyOutcome.journeyId`). | `$statusCode == 200` and `phase` is `SUCCEEDED` or `FAILED`. | `JourneyOutcome` for this journey. |

## Implementation notes

- The journey defines three local subjourneys under `spec.subjourneys`:
  - `computeTotals` – calculates cart totals and discounts; called with `resultKind: output` and `onFailure: propagate`, so failures behave like a parent `fail`.
  - `riskCheck` – computes a risk score and level; called with `resultKind: outcome` and `onFailure: capture`, so the parent always continues and receives a mini-outcome for branching.
  - `enrichProfile` – calls a customer API and projects a compact profile object; called with `resultKind: output` and `onFailure: capture`, so profile failures are captured rather than terminating the journey.
- Each `subjourney` state uses an explicit `input.mapper` to construct the child context from the parent context and writes results into `context.totals`, `context.risk`, and `context.profile` respectively.
- The main `decide` state branches on the captured `risk` mini-outcome (phase, output.level) to choose between APPROVED, REJECTED, and an error path, demonstrating how `resultKind: outcome` and `onFailure.behavior: capture` can be combined for rich control flow. 
