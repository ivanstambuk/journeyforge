# ADR-0020 – Local subjourney state v1 (`type: subjourney`)

Date: 2025-11-23 | Status: Proposed

## Context

JourneyForge already has a fairly rich DSL surface:
- State types such as `task` (HTTP, event publish, schedule), `choice`, `transform`, `wait`, `webhook`, `parallel`, `timer`, `succeed`, and `fail`.
- Top-level journeys and APIs (`kind: Journey` / `kind: Api`) with explicit semantics for context, schemas, HTTP bindings, execution deadlines, and global compensation (`spec.compensation`, ADR-0008).
- Scheduled journeys (`task.kind: schedule`, ADR-0017) and in-journey timers (`type: timer`, ADR-0018).

However, there is no first-class notion of **subjourneys/subflows** in the DSL. Authors who want to:
- Factor out reusable fragments (for example, authentication, KYC, risk evaluation),
- Reuse a non-trivial sequence of states from multiple branches within a single journey/API, or
- Structure a large journey into named phases for readability,

must currently:
- Duplicate the same sequence of states in multiple places, or
- Create separate journey definitions and trigger them indirectly (for example via HTTP or external orchestration), even when the intent is purely internal to one spec.

This has several drawbacks:
- **Poor intra-spec modularity** – large journeys become hard to navigate and reason about because every phase is inlined into `spec.states`.
- **Accidental divergence** – duplicated fragments (for example “step-up MFA on web vs mobile branch”) can drift over time.
- **Awkward control-flow patterns** – “restart this section” or “loop back into this phase” require long-distance `next` wiring that makes the graph harder to follow.

Prior design work (including ADR-0008 and `docs/ideas`) already anticipated **subjourney and SAGA-like patterns** as a future direction. A separate analysis compared how other systems handle subworkflows/subjourneys (Google Cloud Workflows subworkflows, Camunda call activities, Temporal child workflows, Netflix Conductor sub-workflows, identity products such as Transmit Mosaic and Ping inner trees). The key observations were:
- There is a useful distinction between **local subflows** (defined within the same spec, often sharing environment) and **external subworkflows/journeys** (separate definitions with their own lifecycle and version binding).
- For a **user-journey engine** like JourneyForge, the most immediately valuable use cases are:
  - Breaking a single user journey into **named phases** (auth, risk, KYC, profile, post-provisioning).
  - Reusing **non-trivial fragments** (for example a step-up MFA flow) from multiple branches **within one spec**.
  - Modelling “restart this section” / “loop back to this phase” without duplicating states or wiring long cross-graph transitions.
- Cross-spec subjourneys, async subjourney modes, and rich version-binding introduce significantly more complexity (deployment, observability, error propagation, parent/child lifecycle) and are not required to get strong value for user-centric flows.

We therefore need a way to express **local, intra-spec subjourneys** that:
- Are first-class in the DSL (named, typed state with explicit semantics).
- Provide **explicit input and output mapping** using DataWeave, avoiding hidden coupling via shared mutable context.
- Support a simple but useful **failure model** (propagate vs capture) so journeys can either fail fast or handle subjourney failures explicitly.
- Are deliberately scoped to **local + synchronous** behaviour in v1, leaving room for richer external/async/compensation semantics later.

## Decision

We introduce a **local, synchronous subjourney state (`type: subjourney`)** and a corresponding `spec.subjourneys` block. This v1 focuses on **intra-spec reuse only** and is intentionally limited in scope:
- Subjourneys are **local to a single spec** (`spec.subjourneys`).
- `type: subjourney` is **synchronous only**: the parent state completes when the subjourney reaches a terminal state.
- The parent and child have separate contexts:
  - A subjourney’s initial context is produced by an **explicit DataWeave input mapper**.
  - The child’s result is written back into the parent context via `resultVar` and `resultKind`.
- Failure behaviour is controlled by a small `onFailure.behavior` switch:
  - `propagate` (default) – child failures behave like a parent `fail`.
  - `capture` – child failures are captured into a result variable, and control continues at `next`.

This ADR defines:
1. The DSL shape for `spec.subjourneys` (local subjourney definitions).
2. The DSL shape and semantics for `type: subjourney` states.
3. Validation rules and scope for v1 (what is allowed, and explicitly what is out of scope).
4. Rationale in terms of user-journey modelling and intra-spec reuse.

### 1. DSL shape – local subjourneys (`spec.subjourneys`)

We extend the top-level `spec` block with an optional `subjourneys` map that defines **local subjourney graphs** within the same spec.

Shape:

```yaml
apiVersion: v1
kind: Journey | Api
metadata:
  name: <string>
  version: <semver>
spec:
  start: <stateId>
  states:
    <stateId>: <State>

  subjourneys:                   # optional; map<string, SubjourneyDefinition>
    <subjourneyId>:
      input:                     # optional; contract only, not required for execution
        schema: <JsonSchema>     # inline JSON Schema (2020-12) for the subjourney's initial context
      output:                    # optional; contract only
        schema: <JsonSchema>     # inline JSON Schema (2020-12) for the subjourney's "output" value

      start: <stateId>           # required; first state within this subjourney
      states:                    # required; map<string, State> for this subjourney
        <stateId>: <State>       # same state surface as parent, subject to `kind` constraints
```

Semantics:
- `spec.subjourneys`:
  - Keys (`<subjourneyId>`) are local names for subjourneys within this spec.
  - Definitions are **not visible across specs** in v1 and cannot be referenced by other journeys/APIs.
- `input.schema` / `output.schema`:
  - Optional, but strongly recommended for reusable subjourneys.
  - Provide contracts for tooling (validation, documentation, code generation), similar to `spec.input`/`spec.output`.
  - Engines MAY validate subjourney input/output against these schemas when configured to do so, but schema enforcement is not required for conformance.
- `start` and `states`:
  - Define a self-contained state-machine subgraph.
  - The state surface inside a subjourney is the same as at top level, subject to the owning spec’s `kind`:
    - For `kind: Journey`:
      - All state types are allowed (including `wait`, `webhook`, `timer`, cache, policies, etc.).
    - For `kind: Api`:
      - `wait`, `webhook`, and `timer` states MUST NOT be used inside subjourneys (same constraints as top-level `states` for APIs).

### 2. DSL shape – subjourney state (`type: subjourney`)

We introduce a new state type:

```yaml
<stateId>:
  type: subjourney
  subjourney:
    ref: <string>                # required; name under spec.subjourneys

    input:                       # optional; default is empty object {}
      mapper:
        lang: dataweave
        expr: |
          // parent context -> child context (must be an object)
          {
            cart: context.cart,
            user: context.user
          }

    resultVar: <string>          # optional; where to store subjourney result in parent context
    resultKind: output | outcome # optional; default is output

    onFailure:                   # optional; default behavior = propagate
      behavior: propagate | capture

  next: <stateId>                # normal `next` pointer; required for non-terminal states
```

Notes:
- `ref`:
  - Must refer to a key in `spec.subjourneys` in the same spec.
  - Engines and tooling MUST treat unknown `ref` values as validation errors.
- `input.mapper`:
  - Optional. When omitted, the child context defaults to an empty object `{}` (isolation by default).
  - When present:
    - Evaluated as a DataWeave expression with at least `context` bound to the parent journey/API context.
    - MUST evaluate to a JSON object. Engines SHOULD treat non-object results as validation or runtime errors.
- `resultVar`:
  - Optional. When present, the final result of the subjourney call is written to `context.<resultVar>` in the parent.
  - When absent, the subjourney’s result is ignored (aside from failure propagation or capture).
- `resultKind`:
  - Controls what is written to `resultVar` when set:
    - `output` (default):
      - On subjourney success, `context.<resultVar>` receives the subjourney’s **output value** (see semantics below).
      - On subjourney failure:
        - With `onFailure.behavior = capture`, `context.<resultVar>` receives `null` by default (unless the engine chooses to include additional metadata in future revisions).
        - With `behavior = propagate`, the parent state fails and `resultVar` MAY remain unset.
    - `outcome`:
      - `context.<resultVar>` receives a **mini-outcome object** that mirrors the `outcome` shape used by `spec.compensation` (ADR-0008), adapted for subjourneys:
        ```json
        {
          "phase": "SUCCEEDED" or "FAILED",
          "terminationKind": "Success | Fail | Timeout | Cancel | RuntimeError",
          "output": <any or null>,         // on success, same as subjourney output; otherwise null
          "error": {
            "code": "string or null",
            "reason": "string or null"
          }
        }
        ```
- `onFailure.behavior`:
  - `propagate` (default):
    - If the subjourney terminates with `phase = SUCCEEDED`, the state is considered successful and `next` is taken.
    - If the subjourney terminates with `phase = FAILED` (including timeout/cancel/runtime error mapped via `terminationKind`):
      - The parent behaves as if it encountered a `fail` state at this `stateId`, using the subjourney’s `error` as the canonical error.
      - `JourneyOutcome.phase` and error semantics follow the normal rules for failures.
  - `capture`:
    - The parent state is always considered successful from a control-flow perspective, regardless of subjourney success or failure.
    - `next` is taken unconditionally.
    - When `resultVar` is set:
      - If `resultKind = output`:
        - On success: `context.<resultVar>` receives the subjourney’s output value.
        - On failure: `context.<resultVar>` is set to `null`.
      - If `resultKind = outcome`:
        - `context.<resultVar>` receives the mini-outcome object described above, regardless of success or failure.

### 3. Semantics – execution model for local subjourneys

Conceptually, a `type: subjourney` state behaves like a synchronous function call to a named subgraph inside the same spec.

Execution steps:
1. **Input mapping**:
   - When the parent journey/API enters a `type: subjourney` state:
     - If `subjourney.input.mapper` is present:
       - Evaluate the DataWeave expression with `context` bound to the parent context.
       - The result MUST be a JSON object; this becomes the child subjourney’s initial `context`.
     - If `subjourney.input` is omitted:
       - The child subjourney’s initial `context` is `{}` (empty object).

2. **Subjourney execution**:
   - The engine executes the subjourney from `subjourneys[ref].start` using `subjourneys[ref].states` as the state map.
   - Execution proceeds according to the normal DSL semantics for the owning `kind`:
     - For `kind: Journey`, the subjourney may include `wait`, `webhook`, `timer`, and other long-lived states.
     - For `kind: Api`, the subjourney is subject to the same constraints as the top-level API (no `wait`/`webhook`/`timer`; all paths must reach a terminal state without external input).
   - The engine treats the subjourney as part of the same run:
     - No separate `journeyId` is created for local subjourneys.
     - Observability (logs, traces, metrics) SHOULD still capture subjourney boundaries (for example, span names including the subjourney id).

3. **Determining the subjourney result**:
  - When the subjourney terminates, the engine derives a conceptual mini-outcome:
     - `phase`:
       - `SUCCEEDED` when the subjourney ends in a `succeed` state.
       - `FAILED` when the subjourney ends in a `fail` state or terminates due to timeout/cancel/runtime error.
     - `terminationKind`:
       - Mirrors the categorisation used for `spec.compensation` outcomes (`Success`, `Fail`, `Timeout`, `Cancel`, `RuntimeError`), adapted for subjourneys.
     - `output`:
       - When `phase = SUCCEEDED`:
         - If the terminal `succeed` state declares `outputVar` and the corresponding field exists in the subjourney context, `output` is `context[outputVar]`.
         - Otherwise, `output` is the final subjourney `context` (deep copy).
       - When `phase = FAILED`: `output` is `null`.
     - `error`:
       - When `phase = FAILED`, contains the canonical error (`code`/`reason`) derived from the failing state or timeout/cancel configuration.
       - When `phase = SUCCEEDED`, `error` is `null`.

4. **Writing back into the parent context**:
   - If `resultVar` is not set:
     - The parent context is unaffected (aside from any global effects such as compensation or logging).
   - If `resultVar` is set:
     - When `resultKind = output` (default):
       - On success (`phase = SUCCEEDED`): `context.<resultVar>` is set to `output` as defined above.
       - On failure (`phase = FAILED`):
         - With `behavior = capture`: `context.<resultVar>` is set to `null`.
         - With `behavior = propagate`: `context.<resultVar>` MAY remain unset or MAY be set to `null` at engine discretion; callers SHOULD NOT rely on it.
     - When `resultKind = outcome`:
       - `context.<resultVar>` is set to the mini-outcome object (success or failure), regardless of `behavior`.

5. **Parent control flow**:
   - If `onFailure.behavior = propagate` and `phase = FAILED`:
     - The parent run behaves as if it encountered a `fail` state at this `stateId`:
       - `JourneyOutcome.phase` and error details reflect the subjourney’s failure.
       - `spec.compensation`, when present, observes this failure as the main run’s outcome (including `terminatedAtState` pointing to the `subjourney` state).
   - If `onFailure.behavior = capture`, or if `behavior = propagate` and `phase = SUCCEEDED`:
     - The `subjourney` state is considered successful and execution continues via `next`.

Interaction with `spec.execution.maxDurationSec`:
- Subjourneys do **not** introduce their own execution budget in v1.
- The global `spec.execution.maxDurationSec` applies to the entire run (including all subjourneys).
- Engines MAY choose to expose the remaining budget as a binding to DataWeave in future revisions, but this is out of scope for v1.

### 4. Scope of v1 and explicit non-goals

This ADR deliberately constrains v1 subjourneys to keep semantics small and predictable while still unlocking intra-spec reuse. The following are **in scope** for v1:
- Local subjourneys (`spec.subjourneys`) referenced via `subjourney.ref`.
- Synchronous execution only (no `mode` field).
- Explicit DataWeave input mapping and simple result mapping via `resultVar` and `resultKind`.
- A minimal failure model with `onFailure.behavior: propagate | capture`.
- Use in both `kind: Journey` and `kind: Api`, subject to each `kind`’s existing state-type constraints.

The following are **explicitly out of scope for v1** and may be revisited in future ADRs:
- **Cross-spec subjourneys**:
  - No `journeyRef` or equivalent mechanism to call other journey/API definitions.
  - No version-binding semantics (latest/deployment/tag/explicit) for child journeys/APIs.
- **Asynchronous subjourney modes**:
  - No `mode: async` for subjourney states.
  - No parent/child lifecycle policies (for example, parent-close policies).
- **Per-subjourney compensation**:
  - No per-call compensation hooks on `type: subjourney`.
  - Compensation remains coarse-grained via `spec.compensation` (ADR-0008).
- **APIs starting persistent journeys via subjourney**:
  - `kind: Api` subjourneys must not start long-lived `kind: Journey` instances in v1.
  - Subjourneys within `kind: Api` behave like local control-flow structuring, not separate durable journeys.
- **Additional mapping structures**:
  - No `result.target`/`transform.target`-style block for subjourneys in v1; `resultVar` is the only write-back mechanism.
  - No shorthand for copying the entire parent context into the child; authors should use explicit input mappers when needed.

These non-goals are recorded here so that future work on external subjourneys, async modes, richer mapping, or per-subjourney compensation can build directly on this ADR and the prior comparative analysis without rediscovering the constraints.

### 5. Rationale and intra-spec reuse scenarios

The reduced-scope, local-only v1 is justified primarily by user-journey modelling needs and by the desire to keep semantics simple while gaining meaningful modularity:

- **Structuring large journeys into phases**:
  - Complex user journeys (for example account onboarding, account recovery, high-value transaction approval) naturally decompose into phases such as `primaryAuth`, `riskAssessment`, `kycVerification`, `postProvisioning`.
  - Local subjourneys let authors keep the main `spec.states` focused on high-level transitions between phases, while each phase lives in its own named subjourney graph.

- **Reusing non-trivial fragments within one spec**:
  - The same subflow (for example a “step-up MFA” segment) may be needed from multiple branches inside a single journey:
    - After login when risk is high.
    - Before high-value payments.
    - Before sensitive profile changes.
  - Without subjourneys, authors must duplicate these state sequences or build awkward long-distance transitions; local subjourneys provide a clean reuse mechanism without cross-spec dependencies.

- **Multi-channel variants with shared capabilities**:
  - Journeys that support multiple channels (web, native app, call-centre) often branch early by channel, but still share common capabilities like risk evaluation or KYC checks.
  - Local subjourneys allow channel-specific entry segments but a shared `riskAndKyc` subjourney, keeping common logic centralised while preserving channel-specific handling.

- **Clean restart / loop-back patterns**:
  - Some flows need to “send the user back through a section” (for example, collect more documents after KYC failure, then retry decisioning).
  - Using subjourneys, the main graph can simply transition back into the named subjourney instead of duplicating states or wiring complex loops within a flattened state map.

- **Keeping compensation and clean-up structured**:
  - Even within `spec.compensation`, there may be reusable fragments (for example “cancel all downstream reservations then publish a compensation event”).
  - Local subjourneys provide a way to express these without introducing cross-spec compensation journeys or per-subjourney SAGA semantics in v1.

These scenarios are all **intra-spec** and do not require cross-spec subjourneys, async modes, or version binding. They justify a local, synchronous v1 that is valuable on its own and provides a clear foundation for future, more advanced subjourney capabilities.

## Consequences

Positive:
- **Improved modularity and readability**:
  - Large journeys and APIs can be structured into named phases and reusable fragments without leaving a single spec.
  - The main `states` map can focus on high-level control flow, enhancing maintainability.
- **Explicit contracts for reusable fragments**:
  - `spec.subjourneys` may declare input/output schemas, enabling better validation, documentation, and tooling.
  - Explicit DataWeave input mapping avoids hidden data dependencies and encourages deliberate interface design.
- **Consistent error semantics**:
  - Subjourney failures reuse the existing `JourneyOutcome`/Problem semantics (via the mini-outcome), aligning with global compensation and error-handling conventions.
- **Future-friendly foundation**:
  - The v1 design keeps subjourneys local and synchronous, but the concepts (ref, input mapping, mini-outcome) are compatible with potential future features such as external subjourneys, async modes, or per-subjourney compensation.

Negative / trade-offs:
- **Additional DSL complexity**:
  - Authors must learn `spec.subjourneys` and `type: subjourney` in addition to the existing state types.
  - Engines, validators, and tooling must be updated to understand and visualise subjourneys.
- **No cross-spec reuse yet**:
  - Reuse is limited to a single spec; cross-spec subjourneys (for example shared KYC journeys used across services) still require separate patterns (for example dedicated journeys/APIs invoked via HTTP) until future ADRs expand the model.
- **No per-subjourney compensation or async modes in v1**:
  - Authors who need per-fragment SAGA semantics or long-running child journeys must continue to rely on existing primitives (`spec.compensation`, `wait`, `webhook`, `timer`, scheduled journeys) and/or top-level orchestration.

Overall, this ADR introduces a **small but powerful** addition to the DSL that unlocks intra-spec reuse and clearer structure for user-centric journeys, while explicitly documenting the limits of v1 and the possible future directions for subjourneys.
