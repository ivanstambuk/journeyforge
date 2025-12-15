# ADR-0021 – No loop state in DSL v1

Date: 2025-11-23 | Status: Proposed

## Context

Several workflow / orchestration systems provide a dedicated loop or map construct at the language level:
- Google Cloud Workflows: `for` over lists and ranges (including `parallel for`) with loop-local variables, `break`/`continue`, and rich HTTP integration.
- Amazon States Language and similar engines: `Map` / `ForEach` states for data-parallel processing.

These constructs serve two main purposes:
- **Data-level iteration** – summing over arrays, transforming collections, filtering elements, and so on.
- **Orchestration-level iteration** – running a sequence of states per item (often including HTTP calls, waits, and error handling), sometimes with parallel fan-out.

JourneyForge’s v1 DSL already covers much of the practical ground for user-centric journeys without a dedicated loop state:
- **Data-level iteration** is handled by DataWeave:
  - Authors can use `map`, `filter`, `reduce`, and other collection operators inside `transform` mappers and HTTP `body.mapper` without any extra DSL surface.
- **Orchestration-level iteration** can be expressed via:
  - Explicit `choice` + `transform` + `task` + `next` wiring using counters and indices in `context`.
  - Local `subjourney` graphs (ADR-0020) that encapsulate “process one item” behaviour reused from a loop-like controller.
  - Existing patterns like `wait-multiple-callbacks`, which model “wait for N callbacks” with a single external-input state and an aggregate in `context`, rather than dynamic fan-out.

At the same time, adding a dedicated loop or map state would introduce non-trivial semantic complexity:
- **Scoping rules** – loop-local variables, interaction with `context`, and how to reason about mutable state inside and outside the loop.
- **Control-flow keywords** – semantics for `break` / `continue` equivalents and how they interact with `subjourney`, `parallel`, and timers.
- **Error handling and observability** – how per-iteration failures are surfaced (aggregate errors vs per-item), and how they are represented in the error model.
- **Parallelism interaction** – if a future loop state were combined with `parallel` or used as a parallel loop, it would overlap with the already-defined `parallel` state and resilience/bulkhead policies.

For user-centric journeys (the primary target for JourneyForge v1), these trade-offs are not clearly justified:
- Typical loops involve relatively small collections (for example a handful of items, document variants, or participants) rather than hundreds or thousands of elements.
- The most common data-iteration needs are already satisfied by DataWeave expressions.
- When orchestration-level loops do appear, they are usually easier to reason about as explicit control flow with clearly named states, or as a combination of “controller” states plus a reusable subjourney.

We therefore need to decide whether to:
- Introduce a loop / map state now (and freeze semantics early), or
- Explicitly commit to not having such a state in DSL v1 and instead document how to express loop patterns with the existing surface.

## Decision

For DSL v1, JourneyForge **will not introduce a dedicated loop / for / map state** (for example `type: for`, `type: map`, or `parallel.for` at the state level).

Instead:
- **Data-level iteration** remains the responsibility of DataWeave used inside mappers (`transform`, HTTP `body.mapper`, cache operations, error mappers, and so on).
- **Orchestration-level iteration** is expressed via existing state types:
  - Explicit `choice` + `transform` + `task` + `next` control flow that maintains counters, indices, and accumulators in `context`.
  - Local subjourneys (`type: subjourney`, ADR-0020) that encapsulate per-item behaviour when a loop-like controller would otherwise duplicate state graphs.
  - External-input patterns like `wait-multiple-callbacks`, which use a single `wait` state in a loop with aggregates in `context` rather than dynamic fan-out.

This decision is intentionally proactive:
- It **discourages** adding a loop state to DSL v1.
- It treats DataWeave and existing orchestration primitives as the canonical way to describe iteration in user-centric journeys.
- It leaves room for revisiting a loop/map state in a future major evolution of the DSL if JourneyForge expands into heavy data-parallel workloads where the trade-off becomes more favourable.

## Consequences

### Positive consequences

- **Simpler mental model for user-centric journeys**
  - Authors reason about a small, stable set of state types: `task`, `choice`, `transform`, `parallel`, `wait`, `webhook`, `timer`, `subjourney`, `succeed`, `fail`.
  - There is no extra layer of loop-specific scoping or control keywords to learn.

- **Clear separation between data and orchestration**
  - DataWeave expressions handle pure data iteration (lists, maps, reductions).
  - The DSL’s control-flow constructs handle orchestration steps (HTTP calls, waits, timers, and subjourneys).
  - This separation keeps the language more orthogonal and reduces overlap with the expression language.

- **Fewer semantic edge cases**
  - No need to define:
    - Loop-local variable rules and how they interact with `context`.
    - Semantics for `break` / `continue` in the presence of `subjourney`, `parallel`, and timers.
    - Aggregate error envelopes for “per-iteration” failures.
  - Existing error-handling mechanisms (`spec.errors`, RFC 9457-style Problem Details, `fail` states) remain sufficient.

- **Reuse of existing building blocks**
  - Local subjourneys provide a structured way to reuse “process one item” logic without a loop state.
  - `parallel` already covers the primary “few-branch parallelism” use cases; it does not need to be combined with a loop surface for v1.

### Negative / neutral consequences

- **More verbose specs for some patterns**
  - Patterns that would be a single loop in other systems require more explicit states in JourneyForge.
  - However, for user-centric journeys with small `N`, the additional verbosity is typically acceptable and can improve readability.

- **Less convenient for heavy data-parallel workloads**
  - If JourneyForge is later used for large data-parallel processing within a single journey instance, the absence of a loop/map state may become inconvenient.
  - In such scenarios, it may be preferable to delegate heavy fan-out to external systems (for example batch processors, queues, or dedicated workflow engines) and keep JourneyForge focused on user-centric orchestration.

### How to express loop patterns with current features

This section illustrates how common loop-like patterns can be expressed using the existing DSL surface. These examples are illustrative and do not define new syntax.

#### 1. Data-only loops (sum over a list or map)

Instead of a DSL-level `for`:

```yaml
# Hypothetical (not part of the DSL)
sumLoop:
  type: for
  for:
    value: v
    in: ${context.values}
  steps:
    - add:
        type: transform
        transform:
          mapper:
            expr: context ++ { sum: (context.sum default 0) + v }
        next: continue
```

Use DataWeave inside a single `transform`:

```yaml
sumValues:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          sum: (context.values default []) reduce ((acc, v) -> acc + v) default 0
        }
    target:
      kind: context
      path: ""
  next: nextState
```

#### 2. Orchestration loop over items with HTTP calls

Instead of:

```yaml
# Hypothetical (not part of the DSL)
processItems:
  type: for
  for:
    value: item
    in: ${context.items}
  steps:
    - callService:
        type: task
        task:
          kind: httpCall:v1
          operationRef: backend.processItem
          body:
            mapper:
              expr: item
          resultVar: lastResult
    - accumulate:
        type: transform
        transform:
          mapper:
            expr: |
              context ++ {
                results: (context.results default []) ++ [context.lastResult]
              }
```

Use explicit control flow with an index in `context`:

```yaml
initLoop:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          index: 0,
          results: []
        }
    target:
      kind: context
      path: ""
  next: loopCheck

loopCheck:
  type: choice
  choices:
    - when:
        predicate:
          lang: dataweave
          expr: |
            (context.index default 0) < sizeOf(context.items default [])
      next: processOne
  default: loopDone

processOne:
  type: task
  task:
    kind: httpCall:v1
    operationRef: backend.processItem
    body:
      mapper:
        lang: dataweave
        expr: |
          (context.items default [])[context.index]
    timeoutMs: 5000
    resultVar: lastResult
  next: accumulateResult

accumulateResult:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          results: (context.results default []) ++ [context.lastResult],
          index: (context.index default 0) + 1
        }
    target:
      kind: context
      path: ""
  next: loopCheck

loopDone:
  type: succeed
  outputVar: results
```

For more complex per-item behaviour, authors can factor `processOne` and `accumulateResult` into a local subjourney and call it from a “controller” graph that maintains `index` and `results`.

#### 3. “Wait for N callbacks” (external-input loop)

Instead of a loop that dynamically creates N distinct callback steps, JourneyForge uses a single external-input state in a loop, with counters and aggregates in `context`. The `wait-multiple-callbacks` technical example shows this pattern in full.

Simplified sketch:

```yaml
init:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          callbacks: [],
          receivedCount: 0
        }
    target:
      kind: context
      path: ""
  next: waitForCallback

waitForCallback:
  type: wait
  wait:
    channel: manual
    input:
      schema:
        type: object
        properties:
          payload:
            type: object
        additionalProperties: true
    default: ingestCallback

ingestCallback:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ {
          callbacks: (context.callbacks default []) ++ [context.payload],
          receivedCount: (context.receivedCount default 0) + 1
        }
    target:
      kind: context
  next: routeCallback

routeCallback:
  type: choice
  choices:
    - when:
        predicate:
          lang: dataweave
          expr: |
            (context.receivedCount default 0) >= (context.expectedCount default 0)
      next: done
  default: waitForCallback

done:
  type: succeed
  outputVar: callbacks
```

This pattern:
- Avoids dynamic fan-out (`waitForCallback-0`, `waitForCallback-1`, …).
- Keeps the external surface stable (`/steps/waitForCallback`), which is often easier for client integrations.
- Aligns with the DSL limitation that there is no dynamic parallel loop or map state.

### Future evolution

This ADR does not permanently forbid loop or map states in all future versions of the DSL. It does, however, set a clear expectation for v1:
- New specs and examples SHOULD use DataWeave + explicit control flow (and subjourneys) for loop-like patterns.
- Contributors SHOULD NOT introduce a loop/for/map state lightly; any future proposal must clearly justify the added complexity with concrete, recurring use cases that are not well served by existing patterns.
