# JourneyForge DSL – Reference

Status: Draft | Last updated: 2025-11-20

This document normatively defines the JourneyForge workflow DSL supported by Feature 001. It aims to be explicit about behaviour and limitations so we can refine before implementation.

## 1. Overview
- Purpose: describe small, synchronous API workflows in a human-friendly format (YAML 1.2 subset) with a precise JSON model.
- Files: `.workflow.yaml` / `.workflow.yml` or `.workflow.json`.
- States: `task` (HTTP call), `choice` (branch), `transform` (DataWeave mapping), `parallel` (branches with join), `wait` (external input), `webhook` (callback input), `succeed` (terminal), `fail` (terminal).
- Expressions & transforms: DataWeave 2.x is the canonical language for predicates and (future) transform nodes.
- Execution: starts at `spec.start`, mutates a JSON `context`, and terminates on `succeed`/`fail`, a global execution timeout (`spec.execution.maxDurationSec`), or a runtime error. When `spec.compensation` is present, a separate compensation journey MAY run after non-successful termination.

### 1a. State types and surface

The DSL surface defines the following state types and configuration blocks. All of them belong to the same language; runtimes may implement them in stages, but the spec treats them as a single coherent DSL.

| State type / construct                    | Description                                      | Notes in spec                                          |
|------------------------------------------|--------------------------------------------------|--------------------------------------------------------|
| `task` (`kind: httpCall`)                | HTTP call with structured result recording       | Fully specified, including `operationRef` and errors   |
| `choice`                                 | Branch on predicates                             | Fully specified, DataWeave predicates only             |
| `succeed`                                | Terminal success                                 | Fully specified                                        |
| `fail`                                   | Terminal failure with error code/reason          | Fully specified; aligned with RFC 9457 Problem Details |
| `transform`                              | DataWeave mapping into context/vars              | Fully specified                                        |
| `wait`                                   | Manual/external input                            | DSL shape + REST export defined; long-running impl TBD |
| `webhook`                                | Callback input                                   | DSL shape + REST export defined; security impl TBD     |
| `parallel`                               | Parallel branches with join                      | DSL shape + join contract defined; concurrency impl TBD|
| Cache resources/tasks (`cacheGet`/`cachePut`) | Named caches and cache operations           | DSL shape defined; cache backend semantics TBD         |
| Policies (`httpResilience`, `httpSecurity`) | Resiliency/auth configuration                 | Configuration model defined; enforcement impl TBD      |

## 2. Top-level shape

## 2a. API catalog (OpenAPI binding)
To reference downstream services by OpenAPI operationId instead of raw URLs, a workflow or API endpoint may declare an API catalog and use `operationRef` in HTTP tasks.

Catalog shape (under `spec.apis`):
```yaml
apis:
  users:
    openApiRef: apis/users.openapi.yaml
  accounts:
    openApiRef: apis/accounts.openapi.yaml
```

Use in tasks: `operationRef: <apiName>.<operationId>`
```yaml
apiVersion: v1                  # required
kind: Workflow | Api            # required
metadata:                       # required
  name: <string>
  version: <semver>
spec:                           # required
  start: <stateId>              # required
  states:                       # required: map<string, State>
    <stateId>: <State>
```

Constraints
- `apiVersion` must be `v1`.
- `kind` must be either:
  - `Workflow` – a long‑lived journey exposed via the Journeys API, or
  - `Api` – a synchronous HTTP endpoint (single request/response) with no visible journey id.
- `metadata.name` must be a DNS‑label‑like string `[a-z0-9]([-a-z0-9]*[a-z0-9])?`.
- `spec.states` must contain `spec.start`.

### 2a.i. Workflows (`kind: Workflow`)

Workflows are “journeys”:
- Initiated via `/api/v1/journeys/{workflowName}/start` (see `docs/3-reference/openapi/journeys.openapi.yaml` and the OpenAPI export guideline).
- Identified by a `journeyId` and observed via `/journeys/{journeyId}` and `/journeys/{journeyId}/result`.
- May use all state types, including long‑lived external input (`wait`, `webhook`).

The semantics of `succeed`/`fail` for workflows are defined in sections 5.4–5.6, which describe the `JourneyOutcome` envelope.

### 2a.ii. API endpoints (`kind: Api`)

API endpoints reuse the same state machine model but are exposed as synchronous, stateless HTTP endpoints:
- HTTP surface:
  - Canonical base path: `/api/v1/apis/{apiName}`.
  - The actual path and method are controlled by `spec.route` (see below).
- Call model:
  - One HTTP request creates an ephemeral context, executes from `spec.start` until a terminal state, and returns a single HTTP response.
  - No `journeyId` is created or exposed; there are no status/result polling endpoints for `kind: Api`.

Additional shape for `kind: Api`:

```yaml
apiVersion: v1
kind: Api
metadata:
  name: <string>
  version: <semver>
spec:
  route:                        # optional; controls REST surface
    path: <string>              # e.g. /apis/get-user-public; defaults to /apis/{metadata.name}
    method: <string>            # e.g. POST; the initial version supports POST only
  inputSchemaRef: <string>      # optional but strongly recommended
  outputSchemaRef: <string>     # optional but strongly recommended
  start: <stateId>
  states:
    <stateId>: <State>
```

Constraints for `kind: Api`
- `spec.route.path`:
  - If omitted, the canonical path is `/apis/{metadata.name}` underneath the common base `/api/v1`.
  - MUST be an absolute path starting with `/` and without a host.
- `spec.route.method`:
  - If omitted, defaults to `POST`.
  - The initial version supports `POST` only; future versions MAY allow `GET`/`PUT` where semantics are clear.
- State surface:
  - `wait` and `webhook` states MUST NOT be used in `kind: Api` specs (they require external events and persistent journeys).
  - All other state types (`task`, `choice`, `transform`, `parallel`, `succeed`, `fail`, cache operations, policies) are allowed.
- Control flow:
  - Every execution path starting from `spec.start` MUST eventually reach a terminal `succeed` or `fail` state without requiring external input.
  - Implementations SHOULD reject or fail fast when a non‑terminal loop would cause an API call to hang indefinitely.
  - When `spec.execution.maxDurationSec` is present, implementations MUST enforce this budget: if overall execution time exceeds the configured duration, the run MUST terminate as a timeout failure using `spec.execution.onTimeout`.

Context and results for `kind: Api`
- Context initialisation:
  - The HTTP request body is deserialised as JSON and used to initialise the workflow `context` (subject to `inputSchemaRef` validation, when present).
  - `httpBindings.start` MAY further project headers into `context` and/or provide outbound header defaults.
- Successful responses:
  - Reaching `succeed` terminates execution and produces a 2xx HTTP response.
  - The response body is taken from `context.<outputVar>` when `outputVar` is set on the `succeed` state; otherwise the full `context` is used.
- Error responses:
  - Reaching `fail` terminates execution and produces a non‑2xx HTTP response.
  - `errorCode` and `reason` follow the RFC 9457 alignment rules in section 5.6.
  - Implementations MAY use `spec.errors` and `spec.outcomes` (when present) to choose HTTP status codes and to shape the error payload (for example, emitting an RFC 9457 Problem Details document).

## 2b. Defaults (spec.defaults)

Workflows may define per-journey defaults to reduce repetition. Defaults apply when specific fields are omitted at state level.

```yaml
spec:
  defaults:
    http:
      timeoutMs: 10000              # default timeout for httpCall tasks when task.timeoutMs is omitted
      headers:                      # baseline headers merged into each httpCall task.headers
        Accept: application/json
    tasks:
      resiliencePolicyRef: standard # default HTTP resilience policy id when task.resiliencePolicyRef is omitted
```

Semantics
- `spec.defaults.http.timeoutMs`:
  - When set, and when a `task` with `kind: httpCall` omits `timeoutMs`, engines SHOULD use this value instead of the hard-coded default (10 000 ms).
  - If both the task-level `timeoutMs` and `spec.defaults.http.timeoutMs` are omitted, the default remains 10 000 ms as described in the HTTP task section.
- `spec.defaults.http.headers`:
  - For each `task` with `kind: httpCall`, the effective headers are computed as:
    - Start with `spec.defaults.http.headers` (if present), then
    - Overlay `task.task.headers` (task-level values win on key conflicts).
  - Defaults MUST NOT override explicit task headers.
- `spec.defaults.tasks.resiliencePolicyRef`:
  - When set, and when an HTTP `task` omits `resiliencePolicyRef`, engines SHOULD behave as if the task had `resiliencePolicyRef` equal to this default.
  - This interacts with `spec.policies.httpResilience.default`: if both are present, task-level `resiliencePolicyRef` wins, then `spec.defaults.tasks.resiliencePolicyRef`, then `spec.policies.httpResilience.default`.

Validation
- `timeoutMs` (under `spec.defaults.http`) must be an integer ≥ 1 when present.
- `headers` must be a map of string keys to string values.
- `resiliencePolicyRef`, when present, SHOULD refer to an id under `spec.policies.httpResilience.definitions` (or a platform-level policy); unknown ids are a validation warning for tools, but may be resolved at deployment time.

## 2c. Execution deadlines (spec.execution)

Workflows and API endpoints may define a global execution budget to avoid unbounded run times. The execution block expresses a spec-visible wall-clock limit and how timeouts are surfaced to callers.

```yaml
spec:
  execution:
    maxDurationSec: 30           # overall wall-clock budget for this run
    onTimeout:
      errorCode: JOURNEY_TIMEOUT
      reason: "Overall execution time exceeded maxDurationSec"
```

Semantics
- Max duration:
  - When `spec.execution.maxDurationSec` is present, it defines the maximum wall-clock time (in whole seconds) that a single journey or API invocation is allowed to execute from start to terminal outcome.
  - The timer starts when:
    - `kind: Workflow`: the journey is accepted by the Journeys API `start` endpoint.
    - `kind: Api`: the HTTP request is accepted by the API endpoint (for example `/api/v1/apis/{apiName}` or `spec.route.path`).
  - Runtimes MUST treat reaching this deadline as a failure even if the state machine has not yet reached a terminal `succeed`/`fail` state.
- Interaction with per-state timeouts:
  - For blocking operations that already have a timeout (`httpCall.timeoutMs`, `wait.timeoutSec`, `webhook.timeoutSec`), runtimes SHOULD clamp the effective timeout to the remaining global budget.
  - Conceptually, the effective timeout is `min(configuredTimeout, remainingBudget)`; when the remaining budget is ≤ 0, the operation SHOULD NOT start and the run MUST be treated as timed out.
  - When no per-state timeout is configured, runtimes MAY still interrupt long-running operations when the global deadline expires, or detect the timeout immediately after the operation completes.
- Timeout outcome:
  - When the global deadline is reached, the engine MUST stop scheduling new states and complete the run as a failure using `spec.execution.onTimeout`.
  - For `kind: Workflow`:
    - The resulting `JourneyOutcome` has `phase = Failed` and `error` populated from `onTimeout.errorCode` and `onTimeout.reason` (following the Problem Details alignment rules in section 5.6).
  - For `kind: Api`:
    - The engine terminates the HTTP request with a non‑2xx response that reflects the same error code and reason.
    - Exporters and runtimes MAY map execution timeouts to HTTP 504 Gateway Timeout by default, or use `spec.errors` and `spec.outcomes` (when present) for finer control.
- Relationship with platform limits:
  - Platform- or environment-level maximums MAY further restrict execution time; runtimes MAY clamp `spec.execution.maxDurationSec` to a configured upper bound.
  - Setting a large `maxDurationSec` does not guarantee that a run is allowed to execute that long; platform limits take precedence.

Validation
- `spec.execution` is optional.
- When present:
  - `maxDurationSec` is required and MUST be an integer ≥ 1.
  - `onTimeout` is required and MUST contain:
    - `errorCode`: non-empty string, recommended to be a stable identifier (for example, a Problem Details `type` URI).
    - `reason`: non-empty string describing the timeout condition for humans (operators, API clients).
 - `spec.execution` MAY be used for both `kind: Workflow` and `kind: Api` specs.

## 2d. Global compensation (spec.compensation)

Some workflows and APIs need a global “compensation journey” that runs when the main execution does not succeed, to undo or mitigate side effects (for example, HTTP mutations, database writes, or emitted events). The `spec.compensation` block allows authors to attach such a compensation path to a workflow in a declarative, opt‑in way.

At a high level:
- The main journey executes as usual from `spec.start` until it reaches `succeed`/`fail`, hits a global execution timeout (`spec.execution.maxDurationSec`), or is cancelled.
- When the main run terminates in any non‑success state (fail, timeout, cancel, runtime error), and `spec.compensation` is present, the engine may start a separate compensation journey using the embedded compensation state machine.

Shape:

```yaml
spec:
  execution:
    maxDurationSec: 60
    onTimeout:
      errorCode: ORDER_TIMEOUT
      reason: "Order orchestration exceeded 60s"

  compensation:
    mode: async                  # optional; async (default) | sync
    start: rollback              # required; compensation start state id
    states:                      # required; map<string, State> (same shapes as top-level)
      rollback:
        type: task
        task:
          kind: httpCall
          operationRef: billing.cancelCharge
          resultVar: cancelResult
        next: undo_inventory

      undo_inventory:
        type: task
        task:
          kind: httpCall
          operationRef: inventory.releaseReservation
          resultVar: inventoryResult
        next: notify

      notify:
        type: task
        task:
          kind: eventPublish
          eventPublish:
            transport: kafka
            topic: order.compensation
            value:
              mapper:
                lang: dataweave
                expr: |
                  {
                    orderId: context.orderId,
                    compensation: "completed",
                    cause: outcome
                  }
        next: done

      done:
        type: succeed
```

Semantics
- Trigger conditions:
  - `spec.compensation` is optional; when absent, no global compensation is performed.
  - When present, compensation is triggered when the main run terminates with a non‑success outcome:
    - It reaches a terminal `fail` state.
    - It hits the global execution deadline and terminates using `spec.execution.onTimeout`.
    - It is cancelled via a runtime/admin API.
    - It ends due to a runtime/internal error.
  - Successful runs (terminal `succeed` without error) MUST NOT trigger compensation.
- Compensation journey:
  - The `compensation.states` map defines a separate state machine, using the same state types and configuration shapes as the top-level workflow (`task`, `choice`, `transform`, `parallel`, `wait`, `webhook`, `succeed`, `fail`, etc.), subject to the same `kind: Workflow` / `kind: Api` constraints.
  - `compensation.start` identifies the first state in this map.
- Compensation runs with its own control flow and may itself succeed or fail; these outcomes are not visible to the main caller but SHOULD be logged and traced by runtimes.
- Context and bindings:
  - When starting the compensation journey, the engine MUST:
    - Provide `context` as a deep copy of the main journey’s context at the moment of termination.
    - Provide an additional read‑only binding `outcome` to all DataWeave predicates and mappers in the compensation states.
  - The `outcome` object has the conceptual shape:
    ```json
    {
      "phase": "Failed",
      "terminationKind": "Fail | Timeout | Cancel | RuntimeError",
      "error": {
        "code": "string or null",
        "reason": "string or null"
      },
      "terminatedAtState": "stateId or null",
      "journeyId": "string or null",
      "workflowName": "string"
    }
    ```
  - Compensation logic can branch on `outcome` (for example, run different undo steps for cancellation vs timeout) and use the copied `context` to determine which side effects to revert.
- Mode (`sync` vs `async`):
  - `mode` controls whether the caller waits for compensation to finish.
  - `async` (default when `mode` is omitted):
    - The main run returns as soon as it terminates (for example, the HTTP response is sent or `JourneyOutcome` is written).
    - The engine then starts a separate compensation journey instance in the background.
    - Compensation runs independently; its success/failure does not affect the previously returned outcome.
  - `sync`:
    - The main run does not complete until compensation finishes.
    - For `kind: Api`:
      - The HTTP response is sent only after the compensation journey terminates.
      - The response still reflects the original failure (for example, from `fail` or `spec.execution.onTimeout`), not the result of compensation.
    - For `kind: Workflow`:
      - `JourneyOutcome` is only finalised after compensation finishes.
      - `JourneyOutcome.phase` and `JourneyOutcome.error` continue to represent the original failure; compensation errors MAY be recorded in telemetry or as extensions but MUST NOT change `phase`.
- Relationship with sub-workflows:
  - `spec.compensation` describes a coarse-grained, global compensation journey for the entire run.
  - Future features (for example a `subworkflow` state or per-step `compensate` blocks) may provide more fine-grained SAGA semantics; these are complementary and not required to use `spec.compensation`.

Validation
- `spec.compensation` is optional.
- When present:
  - `mode`, when provided, MUST be either `sync` or `async`; if omitted, the effective mode is `async`.
  - `start` is required and MUST refer to a key in `compensation.states`.
  - `states` is required and MUST be a non-empty map of state definitions with the same validation rules as top-level states.
  - Compensation states MUST obey the same constraints as the main spec for the given `kind` (for example, no `wait`/`webhook` in `kind: Api`).
  - Tooling SHOULD flag specs where `spec.compensation` is declared but the runtime does not support compensation journeys.

## 2e. HTTP surface & hypermedia links (spec.httpSurface.links)

Journeys (`kind: Workflow`) are exposed via the Journeys API, which returns JSON envelopes such as `JourneyStartResponse`, `JourneyStatus`, and `JourneyOutcome`. To make the next legal actions discoverable, implementations MAY expose a HAL-like `_links` object alongside the core fields.

Shape (per spec, opt-out at journey level):

```yaml
spec:
  httpSurface:
    links:
      enabled: false              # optional; default is true when omitted
```

Semantics
- Links concept:
  - When enabled, responses from the Journeys API include a `_links` object that describes related resources and next actions.
  - `_links` is a map from link relation (for example `self`, `result`, `waitForApproval`) to a link descriptor.
  - A link descriptor has the conceptual shape:
    ```json
    {
      "href": "/api/v1/journeys/123/steps/waitForApproval",
      "method": "POST"
    }
    ```
    where `href` is a URI (relative or absolute) and `method` is the HTTP method to use when following the link (for example `GET`, `POST`, `DELETE`).
- Default behaviour:
  - If `spec.httpSurface.links` is absent, the effective configuration is as if `enabled: true` were set.
  - When links are enabled, runtimes SHOULD:
    - Include `_links.self` on:
      - `JourneyStartResponse` (typically pointing to the status URL for the new journey),
      - `JourneyStatus` (the status resource itself), and
      - `JourneyOutcome` (the result resource).
    - Include `_links.result` on `JourneyStartResponse` and `JourneyStatus`, pointing to `/journeys/{journeyId}/result` for that journey.
    - Include one link per active external-input state (for example a `wait` or `webhook` state) that can currently receive input, pointing to `/journeys/{journeyId}/steps/{stepId}`.
      The link relation name for these step links MUST equal the state id (for example `waitForApproval`, `confirmPayment`).
    - When the journey is `Running` and is user‑cancellable (see `spec.lifecycle.cancellable`), include a `_links.cancel` entry pointing to the canonical cancellation step:
      - `href: "/api/v1/journeys/{journeyId}/steps/cancel"`
      - `method: "POST"`
    - The `_links.cancel` action MUST be idempotent: repeated invocations when the journey is already terminal or already cancelled MUST NOT change the outcome; implementations MAY still log additional cancel attempts.
  - Link relation names are otherwise implementation-defined, but `self` and `result` SHOULD be reserved for the canonical resources described above.
- Opt-out:
  - When `spec.httpSurface.links.enabled == false`, runtimes SHOULD omit the `_links` object from:
    - `JourneyStartResponse`,
    - `JourneyStatus`, and
    - `JourneyOutcome`
    for that workflow, even if the platform would otherwise include links globally.
  - Exporters SHOULD continue to describe `_links` as an optional property in the generic Journeys OpenAPI schema; implementations that honour `enabled: false` simply omit it at runtime for the relevant workflow.
- `kind: Api`:
  - The `spec.httpSurface.links` block is primarily defined for journeys; synchronous APIs (`kind: Api`) MAY also expose `_links` in their responses, but this spec does not prescribe a specific link vocabulary for them.

Validation
- `spec.httpSurface` is optional.
- When present:
  - `links`, when present, must be an object.
  - `links.enabled`, when present, must be a boolean.

### Example – `_links` on `JourneyStatus`
When links are enabled (the default), a running journey might expose status like:

```json
{
  "journeyId": "abc123",
  "workflowName": "order-orchestration",
  "phase": "Running",
  "currentState": "waitForApproval",
  "updatedAt": "2025-11-20T10:15:30Z",
  "_links": {
    "self": { "href": "/api/v1/journeys/abc123", "method": "GET" },
    "result": { "href": "/api/v1/journeys/abc123/result", "method": "GET" },
    "cancel": {
      "href": "/api/v1/journeys/abc123/steps/cancel",
      "method": "POST"
    },
    "waitForApproval": {
      "href": "/api/v1/journeys/abc123/steps/waitForApproval",
      "method": "POST"
    }
  }
}
```

This matches the generic Journeys schema (`JourneyStatus` and `Link`) defined in
`docs/3-reference/openapi/journeys.openapi.yaml` and illustrates how `_links` describes the
canonical resources (`self`, `result`), the user‑cancellation action (`cancel`), and active step endpoints.

## 2f. Lifecycle and user cancellation (spec.lifecycle)

Some journeys are long‑running, user‑facing flows where the end user should be able to cancel their own run (for example, abandoning a multi‑step order orchestration). The optional `spec.lifecycle` block lets authors control whether a journey is user‑cancellable.

Shape:

```yaml
spec:
  lifecycle:
    cancellable: false            # optional; default is true when omitted
```

Semantics
- `spec.lifecycle` is optional.
- `cancellable`:
  - When omitted or `true`, the journey is considered user‑cancellable while it is `Running`.
  - When explicitly `false`, self‑service clients MUST NOT be offered a cancel action for this journey.
- Interaction with Journeys API and `_links`:
  - When `spec.httpSurface.links` are enabled and `cancellable != false`:
    - While `JourneyStatus.phase == "Running"`, runtimes SHOULD expose a `_links.cancel` entry as described in section 2e, pointing to the canonical cancellation step `/api/v1/journeys/{journeyId}/steps/cancel`.
    - Once the journey is terminal (`Succeeded` or `Failed`), `_links.cancel` MUST be omitted.
  - When `cancellable == false`, runtimes MUST omit `_links.cancel` even if the journey is `Running`.
- Cancellation semantics (conceptual):
  - When a runtime honours a user‑initiated cancellation, it SHOULD:
    - Stop scheduling new states for the run.
    - Complete the run as a failure with a stable error code such as `JOURNEY_CANCELLED` and a human‑readable reason (for example, "Cancelled by user").
    - Treat the cancellation as a non‑success outcome for the purposes of `spec.compensation`, using `terminationKind = "Cancel"` in the compensation `outcome` binding (see section 2d).

Validation
- When present:
  - `cancellable`, when present, MUST be a boolean.

### Example – cancelled `JourneyOutcome`
A cancelled journey might produce an outcome like:

```json
{
  "journeyId": "abc123",
  "workflowName": "order-orchestration",
  "phase": "Failed",
  "output": null,
  "error": {
    "code": "JOURNEY_CANCELLED",
    "reason": "Cancelled by user"
  },
  "_links": {
    "self": { "href": "/api/v1/journeys/abc123/result", "method": "GET" }
  }
}
```

Here cancellation is represented as a failure with a stable error code, aligned with the execution and compensation semantics above.

## 3. Context and paths
- `context` is a per-journey mutable JSON object initialised to `{}` unless a caller provides an initial value. It lives only for that journey instance and is not shared across journeys.
- Dot‑paths reference nested fields: `a.b.c` reads `context.a.b.c`.
- Arrays: the DSL does not support array indexing in paths (no `a[0]`). Future versions may add it.

## 4. Interpolation
- String fields annotated as “interpolated” support `${context.<dotPath>}` placeholders.
- Interpolation is supported in HTTP `url`, `headers` values, and `body` (when `body` is a string). Interpolation of non‑string values coerces to JSON string.
- Unknown/missing variables produce a validation error (not an empty string).

## 5. States

### 5.1 `task` (HTTP call)
```yaml
type: task
task:
  kind: httpCall                # only supported `task.kind` for HTTP in this version
  mode: requestResponse         # optional; requestResponse (default) | notify
  # Choose exactly one of the following bindings:
  # (A) OpenAPI operation binding
  operationRef: <apiName>.<operationId>
  params:                          # optional parameter/header mapping
    path:
      <name>: <string|interpolated>
    query:
      <name>: <string|interpolated>
    headers:
      <k>: <string|interpolated>
  body: <string|object|mapper>     # optional; disallowed on GET
  accept: application/json         # optional; default application/json
  timeoutMs: <int, default 10000>
  resultVar: <identifier>          # stores structured result under context.<resultVar>
  resiliencePolicyRef: <id>        # optional; reference to an HTTP resilience policy defined under spec.policies.httpResilience
  errorMapping:                     # optional – conditional transform for error results
    when: nonOk                     # only nonOk is supported (result.ok == false)
    mapper:
      lang: dataweave
      expr|exprRef: <expression>    # same shape as transform.mapper
    target:                         # same shape/semantics as transform.target
      kind: context                 # context | var
      path: problem                 # when kind == context; dot-path in context
    resultVar: problem              # when kind == var; name under context.resultVar
  # (B) Raw HTTP binding (fallback)
  # method: GET|POST|PUT|DELETE
  # url: <interpolated string>
  # headers:
  #   <k>: <interpolated>
next: <stateId>                 # required
```

Semantics
- Request: constructed from `method`, `url`, `headers`, optional `body`.
- Body: if an object in YAML/JSON, it is serialised as JSON with `application/json` unless `Content-Type` overrides.
- `mode` controls how the workflow observes the HTTP outcome:
  - `requestResponse` (default):
    - The engine sends the HTTP request and waits for a response (or timeout/error).
    - It then builds a structured result object and stores it at `context.<resultVar>`.
    - Callers can branch on or transform this result via `choice`, `transform`, or `errorMapping`.
  - `notify` (fire-and-forget):
    - The engine sends the HTTP request but does not wait for a response body and does not construct a result object.
    - The workflow does not observe HTTP status, headers, or body; any network or protocol errors are implementation-defined (typically logged) but MUST NOT change control flow.
    - Execution continues immediately to `next`.
- Response handling in `requestResponse` mode (no auto-termination):
  - 2xx status: build a result object with `ok=true`; if `Content-Type` indicates JSON, parse to JSON; else store as string.
  - Non‑2xx: build a result object with `ok=false` and include `status`, headers, and body (parsed when possible).
  - Network errors/timeouts: build a result object with `ok=false` and `error` details (e.g., `{type: TIMEOUT|NETWORK, message: "..."}`).
  - The engine never auto‑terminates due to HTTP outcome; execution always continues to `next`. Use `choice` (or future policies) to branch on the recorded result.
- Conditional error mapping (`errorMapping`):
  - After constructing and storing the HTTP result object at `context.<resultVar>`, if `errorMapping` is present and `when: nonOk`, the engine conceptually evaluates `errorMapping.mapper` only when `context.<resultVar>.ok == false`.
  - The mapper evaluates with:
    - `context` bound to the current workflow context, and
    - `result` bound to the structured HTTP result object stored at `context.<resultVar>`.
  - The mapper result is then written according to `errorMapping.target` and `errorMapping.resultVar` using the same rules as the `transform` state:
    - `target.kind == context` and `target.path` set → assign at `context.<path>`.
    - `target.kind == var` and `resultVar` set → assign at `context.<resultVar>`.
    - If both `target.path` and `resultVar` are omitted, the mapper result replaces the full `context`.
  - This is convenience sugar for a conditional `transform` that runs only on error results; any behaviour achievable via `errorMapping` can also be expressed as an explicit `choice` + `transform` + `next` sequence.
- Result object shape (stored at `context.<resultVar>`):
  ```json
  {
    "status": 200,                // integer HTTP status (absent if network error)
    "ok": true,                   // boolean
    "headers": {"Content-Type": "application/json"},
    "body": {},                   // JSON value or string
    "error": { "type": "TIMEOUT", "message": "..." } // optional
  }
  ```

Validation
- `method` and `url` are required; `url` must be absolute (`http://` or `https://`).
- `body` present with `GET` → validation error.
- `resultVar` must match `[A-Za-z_][A-Za-z0-9_]*` when `mode` is `requestResponse`.
- When `mode` is `notify`:
  - `resultVar` MUST be omitted (there is no result).
  - `errorMapping` MUST be omitted (there is no error result to map).
  - `resiliencePolicyRef` MAY be ignored by runtimes; retries have no observable effect on workflow behaviour.
- HTTP outcomes (status/timeouts) do not terminate execution; you must branch explicitly. In `notify` mode the workflow cannot branch on call outcomes because they are not observable.
### 5.1b `task` (event publish)

In addition to HTTP calls, `task` can publish events to external transports such as Kafka.

```yaml
type: task
task:
  kind: eventPublish
  eventPublish:
    transport: kafka                 # initial implementation: only "kafka" is supported
    topic: orders.events             # required
    key:                             # optional – mapper for Kafka key
      mapper:
        lang: dataweave
        expr|exprRef: <expression>
    value:                           # required – mapper for event payload
      mapper:
        lang: dataweave
        expr|exprRef: <expression>
    headers:                         # optional – Kafka record headers
      <k>: <string|interpolated>
    keySchemaRef: <string>           # optional – JSON Schema for the key
    valueSchemaRef: <string>         # optional but recommended – JSON Schema for the payload
next: <stateId>
```

Semantics
- Transport and topic:
  - `eventPublish.transport` identifies the event transport; in the initial implementation it MUST be `kafka`.
  - `eventPublish.topic` is the Kafka topic name; cluster/connection details are provided out of band by the runtime.
- Key and value:
  - `eventPublish.key.mapper` (when present) is evaluated with `context` bound to the current workflow context; the result is serialised according to runtime configuration (typically string/bytes) and used as the Kafka record key.
  - `eventPublish.value.mapper` is required and produces the event payload object; runtimes typically serialise this as JSON.
- Headers:
  - `eventPublish.headers` is an optional map from header name to interpolated string value; values are evaluated against `context` and attached as Kafka record headers.
- Schemas:
  - `eventPublish.keySchemaRef` (optional) points to a JSON Schema that describes the logical shape of the key. Runtimes and tooling MAY use this for validation or schema registry integration.
  - `eventPublish.valueSchemaRef` (optional but recommended) points to a JSON Schema that describes the event payload. When present, runtimes SHOULD validate the mapped payload against this schema before publishing and MAY register it with an event/schema registry.
- Control flow:
  - Publishing is fire-and-forget from the workflow’s perspective: the task does not write a `resultVar`, and no control-flow decisions are based on publish outcomes.
  - On publish failures after any configured retries, implementations MAY treat this as a runtime error (failing the journey/API call); the DSL does not surface partial success states.

Validation
- `task.kind` may be `httpCall` or `eventPublish`.
- For `kind: eventPublish`:
  - `eventPublish.transport` is required and must be `kafka`.
  - `eventPublish.topic` is required and must be a non-empty string.
  - `eventPublish.value` is required and must contain a `mapper` object with `lang: dataweave` and `expr` or `exprRef`.
  - `eventPublish.key` (when present) must contain a `mapper` object with `lang: dataweave` and `expr` or `exprRef`.
  - `eventPublish.headers` (when present) must be a map of header names to string or interpolated string values.
  - `eventPublish.keySchemaRef` and `eventPublish.valueSchemaRef` (when present) must be non-empty strings referring to JSON Schema documents.
- `resultVar`, `errorMapping`, and `resiliencePolicyRef` MUST NOT be used with `kind: eventPublish`; event publishes do not produce structured results for branching or error mapping.

### 5.2 `choice` (branch)
```yaml
type: choice
choices:
  - when:
      predicate:
        lang: dataweave
        expr: |
          context.item.status == 'OK' and context.item.price < 100
    next: <stateId>
default: <stateId>              # optional but recommended
```

Semantics
- Evaluate branches in order; the first predicate that evaluates to `true` wins.
- DataWeave predicate: evaluate `when.predicate.expr` with `context` bound to the current workflow context. The expression must return a boolean; non‑boolean results are a validation error.
- If no branch matches, transition to `default` if present; otherwise validation error.

### 5.3 `succeed`
```yaml
type: succeed
outputVar: <identifier>         # optional
```
Semantics
- Terminal success. If `outputVar` is set and exists in `context`, return its value; else return full `context`.

### 5.4 `fail`
```yaml
type: fail
errorCode: <string>
reason: <string>
```
Semantics
- Terminal failure with a code and human message.
- `errorCode` MUST be a stable identifier for the error condition (for example a URI or string key); by default it SHOULD align with the RFC 9457 Problem Details `type` when a Problem object is available.
- `reason` SHOULD be a concise, human-friendly summary suitable for operators and API consumers (often derived from a Problem Details `title` or `detail`).

### 5.5 Error handling patterns

- Task-level errors as data
  - `task` states (for example `httpCall`) never auto-terminate a workflow. They always store a structured result in `context.<resultVar>` (including `status`, `ok`, `headers`, `body`, optional `error`) and then continue to `next`.
  - Error handling is expressed explicitly via `choice` predicates and/or `transform` states that inspect these result objects.
- Mapping to journey outcome
  - `succeed` produces `JourneyOutcome.phase = Succeeded` with `output` taken from `context.<outputVar>` when set, otherwise from the full `context`.
  - `fail` produces `JourneyOutcome.phase = Failed` with `error.code = errorCode` and `error.reason = reason`. The HTTP status of downstream calls is not reflected in the `/result` status code.
- Exposing raw downstream errors
  - To surface a downstream HTTP result directly, use `succeed` with `outputVar` pointing at the HTTP `resultVar` so callers can inspect `status`, `body`, and `error` themselves.
- Inspect and normalise errors
  - Use `choice` predicates over `context.<resultVar>` (for example `ok`, `status`, `body` fields, `error.type`) to decide between `succeed` and `fail`.
  - Optionally insert a `transform` that builds a normalised error object (for example `context.normalisedError`) before either succeeding with `outputVar: normalisedError` or failing with a concise `errorCode`.
- Aggregating errors from multiple calls
  - Sequential aggregation: perform multiple `task` calls (each with its own `resultVar`), then use a `choice` and optional `transform` to combine their outcomes into a single success result or a single `fail` state (for example, "one or more upstream calls failed").
  - Parallel aggregation: when using `parallel`, branches write their own results; the `join.mapper` can aggregate branch-level results into a single `context.<resultVar>` that a subsequent `choice` uses to decide whether to `succeed` or `fail`. Concurrency and detailed error propagation for `parallel` are defined under the Parallel feature.

### 5.6 Error model and RFC 9457 (Problem Details)

- Default error model (journey outcome)
  - `succeed` produces a `JourneyOutcome` with `phase = Succeeded` and `output` taken from `context.<outputVar>` (when set) or from the full `context`.
  - `fail` produces a `JourneyOutcome` with `phase = Failed` and `error` populated; `error.code` MUST be a stable identifier for the error condition (for example a URI or string), and `error.reason` SHOULD be a human-readable message.
- RFC 9457 alignment (recommended)
  - Implementations are encouraged to treat RFC 9457 “problem details” as the canonical internal error shape for HTTP and journey errors, with fields such as `type`, `title`, `status`, `detail`, `instance`, plus extensions.
  - A common mapping is:
    - `errorCode` ≈ Problem Details `type` (a URI or stable identifier).
    - `reason` ≈ Problem Details `title` or `detail` (human-readable summary).
  - Workflows MAY keep the full Problem Details object in `context` (for example `context.problem`) even when only `code` and `reason` are exposed in `JourneyOutcome.error`.
- Normalising downstream errors to RFC 9457
  - Downstream HTTP APIs can return arbitrary error formats; workflows can normalise these into a Problem Details object using `transform` states and DataWeave mappers.
  - Typical pattern:
    - `task` captures the raw HTTP error into `context.api`.
    - A `choice` routes on `context.api.ok == false`.
    - A `transform` builds `context.problem` with RFC 9457 fields derived from `context.api.status`, `context.api.body`, and `context.api.error`.
    - The workflow then either:
      - `succeed`s with `outputVar: problem` to return a pure Problem Details document, or
      - `fail`s, using `errorCode` from `context.problem.type` and `reason` from `context.problem.detail` or `title`.
- Mapping RFC 9457 to other error formats
  - When clients expect a non-RFC error format, workflows can map a canonical Problem Details object into a client-specific error envelope via `transform` states (for example `context.clientError`) and then:
    - `succeed` with `outputVar: clientError`, or
    - `fail` with a compact `errorCode`/`reason` while still keeping `context.problem` and/or `context.clientError` available for logging and diagnostics.
- Reuse via shared mappers
  - To keep error handling consistent across workflows, authors SHOULD define shared DataWeave modules (`.dwl` files) that implement common mappers such as “downstream error → Problem Details” and “Problem Details → client error”.
  - These modules are referenced from `transform` and `choice` expressions via `exprRef`, so the canonical error logic lives in one place even though the DSL has no dedicated `spec.errors` block.

## 19. Error configuration (spec.errors)

The `spec.errors` block allows workflows to centralise how they normalise and expose errors, building on the canonical RFC 9457 Problem Details model (see ADR‑0003).

```yaml
spec:
  errors:
    canonicalFormat: rfc9457
    normalisers:
      httpDefault:
        mapper:
          lang: dataweave
          exprRef: "errors/http-to-problem.dwl"
    clients:
      backendClient:
        mapper:
          lang: dataweave
          exprRef: "errors/problem-to-backend.dwl"
```

Shape
- `canonicalFormat`: when present, MUST be `rfc9457`. Other values are reserved.
- `normalisers`: map of ids to mappers that convert low-level error data (for example HTTP results) into Problem Details objects.
- `clients`: map of ids to mappers that convert canonical Problem Details objects into client-specific error envelopes.

Semantics (guidance)
- `spec.errors` provides named mappers that can be reused from:
  - `transform` states (via `mapperRef` to entries under `normalisers`/`clients`),
  - HTTP task `errorMapping.mapperRef`, and
  - future runtime integration points.
- Runtimes MAY:
  - Use a configured normaliser (for example `spec.errors.normalisers.httpDefault`) as the default `errorMapping` for HTTP tasks that do not specify one.
  - Use a configured client mapper (for example `spec.errors.clients.backendClient`) when projecting internal Problem Details objects into the final `JourneyOutcome.error` representation, as long as the resulting `error.code` remains a stable identifier and aligns with ADR‑0003.

Validation
- `canonicalFormat`, when present, must be the string `rfc9457`.
- `normalisers` and `clients` must be maps of ids to mapper objects with the same shape as `spec.mappers` entries (`lang`, `expr`/`exprRef`).
- Tools SHOULD flag references to unknown normaliser/client ids in `mapperRef` or other configuration as validation errors.

## 6. Example
```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: example
  version: 0.1.0
spec:
  start: call_api
  states:
    call_api:
      type: task
      task:
        kind: httpCall
        method: GET
        url: "https://api.example.com/items/${context.inputId}"
        headers:
          Accept: application/json
        timeoutMs: 10000
        resultVar: item
      next: decide

    decide:
      type: choice
      choices:
        - when:
          predicate:
            lang: dataweave
            expr: |
              context.item.ok == true
          next: success
      default: failure

    success:
      type: succeed
      outputVar: item

    failure:
      type: fail
      errorCode: NOT_OK
      reason: "Item not OK"
```

## 7. Limitations (explicit non-capabilities)
- Terminal success/failure are explicit via `succeed`/`fail`; tasks never auto‑terminate a workflow.
- No runtime enforcement for retries, circuit breakers, bulkheads, or authentication policies; only configuration via resilience policies is defined.
- Feature 001 runtimes do not implement timers (`wait`), external input (`webhook`), parallelism (`parallel`/`map`), or sub‑workflows; these states are reserved for Features 003/004 and SHOULD be rejected or treated as unsupported.
- No array indexing in dot‑paths (no `a[0]`), and no alternate expression languages (all predicates/mappers are DataWeave).
- No environment‑variable substitution or secret references (future features may add).
- No persistence/resume across process restarts.

## 8. Naming & terminology
- State identifiers are arbitrary keys under `spec.states` (e.g., `call_api`, `decide`). They do not imply special behaviour.
- The branch state type is `choice` (canonical, ASL-aligned). The spec and snapshots use `choice`; no alias is defined.

## 9. Forward-compatibility notes
- Policies block (auth/resiliency) will be added in a future feature.
- DataWeave is the canonical expression language. Future convenience operators (e.g., `in`, numeric comparisons) may be added as sugar and compiled to DataWeave.

## 10. DataWeave – Expressions & Mappers
- Language: DataWeave 2.x (expressions authored inline via `expr` or referenced via `exprRef` to an external `.dwl` file).
- Binding: `context` variable is bound to the current workflow context JSON value.
- Predicates: used in `choice` branches via `when.predicate`. The expression must evaluate to a boolean.
- Transforms: `transform` states use DataWeave to compute values written into `context` or into variables under `context.<resultVar>`, according to the semantics in the Transform state section.
- Determinism & safety: expressions must be pure (no I/O); the evaluator must enforce timeouts and resource limits.

### 10.1 Reusable mappers (`spec.mappers` and `mapperRef`)

To avoid repeating the same DataWeave snippets across multiple states, workflows can define named mappers under `spec.mappers` and reference them via `mapperRef`.

```yaml
spec:
  mappers:
    normaliseHttpError:
      lang: dataweave
      exprRef: "mappers/http-error-to-problem.dwl"
    buildProfile:
      lang: dataweave
      expr: |
        {
          id: context.user.id,
          email: context.remote.body.email
        }
```

Usage in a state:

```yaml
type: transform
transform:
  mapperRef: buildProfile          # sugar for inlining the mapper from spec.mappers.buildProfile
  target:
    kind: var
  resultVar: profile
next: done
```

Semantics
- `spec.mappers` is a map from mapper id to a mapper definition with the same shape as `transform.mapper` (`lang`, `expr`, `exprRef`).
- Anywhere the DSL allows a `mapper` object (for example `transform.mapper`, HTTP `body.mapper`, cache `key.mapper`, `errorMapping.mapper`), authors MAY use `mapperRef: <id>` instead:
  - The referenced mapper MUST be defined under `spec.mappers.<id>`.
  - Conceptually, `mapperRef: foo` is equivalent to copying `spec.mappers.foo` inline at that location.
- A state MUST NOT mix `mapper` and `mapperRef` in the same location; this is a validation error.

Validation
- `spec.mappers` must be a map of ids to mapper objects; each mapper must declare `lang: dataweave` and either `expr` or `exprRef`.
- `mapperRef` values must be non-empty strings that resolve to an existing entry in `spec.mappers`.

## 11. Schemas (optional)
- `inputSchemaRef`: JSON Schema (2020-12) that validates the initial `context` provided at journey start.
- `outputSchemaRef`: JSON Schema for the terminal output returned by `succeed` (or the overall `context` if `outputVar` is omitted).
- `contextSchemaRef`: JSON Schema for the logical shape of `context` during journey execution (superset of fields that may appear over time).
- Exporter behaviour:
  - When `inputSchemaRef` is present, the OpenAPI exporter references it as the request body `context` schema in the per-journey OAS.
  - When `outputSchemaRef` is present, the exporter refines `JourneyOutcome.output` to `$ref` that schema in the per-journey OAS.
  - `contextSchemaRef` is primarily for tooling and validation; the exporter MAY expose it as an additional schema component, but it does not change the wire format of `JourneyStartRequest` or `JourneyOutcome`.

Example:

```yaml
spec:
  inputSchemaRef: schemas/order-input.json
  outputSchemaRef: schemas/order-output.json
  contextSchemaRef: schemas/order-context.json
```

Usage notes
- Use `inputSchemaRef` for what callers must send at start.
- Use `outputSchemaRef` for what successful journeys return as `JourneyOutcome.output`.
- Use `contextSchemaRef` to describe the full, evolving shape of `context` (including internal fields like `remote`, `cachedUser`, `problem`), so linters and editors can validate `context.<path>` usage in DataWeave expressions.

### OpenAPI operation binding (operationRef)
- `operationRef`: resolves `<apiName>` in `spec.apis` and `<operationId>` in the referenced OAS.
- Server selection: the first OAS server is used by default; future features may allow server variables/overrides.
- Params mapping: `params.path/query/headers` provide values for OAS params by name. Missing required params is a validation error.
- Body mapping: if `body` is a string, it is sent as-is; if an object, it is JSON-encoded; if a `mapper` object with `lang: dataweave` and `expr|exprRef` is provided, the mapper result becomes the JSON body.
- `accept` selects the response media type; default `application/json`.
- Cannot mix `operationRef` with raw `method`/`url` in the same task.

## 12. External-Input States (wait/webhook)

External-input states pause the workflow and require a step submission to continue. Submissions are sent to `/journeys/{journeyId}/steps/{stepId}` where `stepId` equals the state id.

Bindings available to DataWeave expressions during step handling:
- `context`: the current workflow context JSON object.
- `payload`: the submitted step input JSON (validated against `inputSchemaRef`).

### 12.1 `wait` (manual/external input)
```yaml
type: wait
wait:
  channel: manual                       # manual input from authenticated client
  inputSchemaRef: <path-to-json-schema> # required
  apply:                                 # optional – update context before branching
    mapper:
      lang: dataweave
      expr|exprRef
  on:                                     # ordered branch evaluation
    - when:
        predicate:
          lang: dataweave
          expr: |
            payload.decision == 'approved'
      next: approved
  default: rejected                       # optional
  timeoutSec: <int>                       # optional
  onTimeout: <stateId>                    # required if timeoutSec is set
```

Semantics
- When entering a `wait` state, the journey phase is `Running`, and the step subresource is considered active.
- A submission must target the active step; otherwise respond 409 Conflict.
- The request body is validated against `inputSchemaRef`; invalid → 400 with schema errors.
- If `apply.mapper` is provided, it runs with `context` and `payload` and replaces `context` with the mapper result.
- The `on` array is evaluated in order; first predicate returning `true` selects `next`. If none match, `default` is used; if absent, the spec is invalid.
- If `timeoutSec` elapses without submission, transition to `onTimeout`.

Validation
- `wait.channel` must be `manual`; use `webhook` state for callback semantics.
- `inputSchemaRef` is required.
- If `timeoutSec` is set, `onTimeout` is required.
- Either `on` (non-empty) or `default` must be present.

### 12.2 `webhook` (callback input)
```yaml
type: webhook
webhook:
  inputSchemaRef: <path-to-json-schema>  # required
  security:                               # optional – minimal guard
    kind: sharedSecretHeader
    header: X-Webhook-Secret
    secretRef: secret://webhook/<name>
  apply:
    mapper:
      lang: dataweave
      expr|exprRef
  on:
    - when:
        predicate:
          lang: dataweave
          expr: |
            payload.status == 'OK'
      next: success
  default: failure
  timeoutSec: <int>
  onTimeout: <stateId>
```

Semantics
- Same as `wait`, but intended for third-party callbacks.
- If `security.kind == sharedSecretHeader`, the exporter documents the header; enforcement is implementation-defined.

### 12.3 Export mapping (steps)
- For each external-input state, the exporter emits:
  - `POST /journeys/{journeyId}/steps/{stepId}` with request body schema = `inputSchemaRef`.
  - 200 response schema = `JourneyStatus`.
- Journeys without external-input states do not emit `/steps/{stepId}` paths.

## 13. Resilience Policies (HTTP)

Resilience policies define reusable behaviour for HTTP tasks: retries, backoff, basic circuit-breaker thresholds, and bulkhead-style concurrency limits.

Location in spec:
```yaml
spec:
  policies:
    httpResilience:
      default: standard             # optional default policy id
      definitions:
        standard:
          maxAttempts: 3            # total attempts (1 original + 2 retries)
          retryOn:                  # conditions that permit retry
            - NETWORK_ERROR
            - HTTP_5XX
          backoff:
            kind: exponential       # fixed|exponential
            baseDelayMs: 100
            maxDelayMs: 2000
            jitter: full            # none|full
          subject-step-guard.work:
            enabled: true
            failureRateThreshold: 0.5
            slidingWindowSize: 20
            openTimeoutSec: 30
          bulkhead:
            maxConcurrent: 50
            queueSize: 100
        aggressive:
          maxAttempts: 5
          retryOn: [NETWORK_ERROR, HTTP_5XX, HTTP_429]
```

Usage from an HTTP task:
```yaml
stateId:
  type: task
  task:
    kind: httpCall
    operationRef: service.someOperation
    resiliencePolicyRef: standard
```

Semantics (configuration)
- `maxAttempts`: max number of attempts per task execution (original + retries).
- `retryOn`: list of conditions under which a retry may be scheduled (e.g., NETWORK_ERROR, HTTP_5XX, HTTP_429).
- `backoff`: strategy used to schedule retries; details of exact jitter and clamping are implementation-specific.
- `circuitBreaker`: parameters for a simple failure-rate circuit breaker; this is defined but not enforced.
- `bulkhead`: concurrency limit per policy; this is defined but not enforced.
- If `resiliencePolicyRef` is omitted, the engine (once implemented) should use `spec.policies.httpResilience.default` if set, or an environment-level default.

Validation
- `spec.policies.httpResilience.definitions` must be a map of ids to policy objects.
- `resiliencePolicyRef` must refer to an existing id in `definitions` (or to a platform-level policy); unknown ids are a validation error.
- `maxAttempts` must be an integer ≥ 1.

Notes
 - This section defines the configuration model only; runtime enforcement belongs to the policy implementation in the runtime and is out of scope for the DSL reference.

## 14. Transform State (DataWeave)

The `transform` state uses DataWeave to compute a new value and either assign it into the context or expose it via a variable. It is the primary building block for non-HTTP data shaping.

```yaml
type: transform
transform:
  mapper:
    lang: dataweave
    expr: |
      {
        id: context.id,
        status: 'OK'
      }   # or exprRef: transforms/example.dwl
  target:
    kind: context                 # context | var
    path: data.enriched           # used when kind == context; dot-path in context
  resultVar: enriched             # used when kind == var; name under context.resultVar
next: <stateId>
```

Semantics
- `mapper` evaluates with `context` bound to the current workflow context and returns a JSON value.
- If `target.kind == context` (default) and `target.path` is provided, the value is written at `context.<path>` (overwriting any existing value).
- If `target.kind == var`, the value is stored under `context.<resultVar>`; other context fields remain unchanged.
- If neither `target.path` nor `resultVar` is set, the mapper result replaces the entire `context`.

Validation
- `transform.mapper.lang` must be `dataweave`.
- Exactly one of (`target.path`, `resultVar`) may be omitted; if both are omitted, `context` replacement semantics apply.
- `resultVar`, when used, must match `[A-Za-z_][A-Za-z0-9_]*`.

Example – normalising an HTTP error into RFC 9457 Problem Details:

```yaml
normalise_error:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        {
          type: 'https://example.com/probs/upstream-error',
          title: 'Upstream service failure',
          status: context.api.status default: 500,
          detail: context.api.error.message default: 'Upstream error'
        }
    target:
      kind: context
      path: problem
  next: fail_with_problem

fail_with_problem:
  type: fail
  errorCode: "https://example.com/probs/upstream-error"
  reason: "Upstream service failure"
```


## 15. Cache Resources & Operations

Caches are modelled as named resources plus cache-focused task kinds. This section defines the configuration and wiring; concrete cache implementations and eviction behaviour belong to the runtime.

### 15.1 Cache resources

```yaml
spec:
  resources:
    caches:
      defaultCache:
        kind: inMemory             # inMemory | external
        maxEntries: 10000          # optional
        ttlSeconds: 300            # default TTL for entries
      hotItems:
        kind: external
        provider: redis            # non-normative hint; implementation-defined
```

### 15.2 Cache operations (task kinds)

```yaml
# Read from cache
type: task
task:
  kind: cacheGet
  cacheRef: defaultCache
  key:
    mapper:
      lang: dataweave
      expr: |
        context.userId
  resultVar: cachedUser            # value stored at context.cachedUser
next: <stateId>

# Write to cache
otherState:
  type: task
  task:
    kind: cachePut
    cacheRef: defaultCache
    key:
      mapper:
        lang: dataweave
        expr: |
          context.userId
    value:
      mapper:
        lang: dataweave
        expr: |
          context.profile
    ttlSeconds: 600                # optional override
  next: <stateId>
```

Semantics (configuration)
- `cacheRef` must refer to a named cache under `spec.resources.caches`.
- `key.mapper` computes a string key from the current `context`.
- `value.mapper` computes the value to be stored; the engine serialises it as JSON.
- `cacheGet` will (once implemented) set `resultVar` to the stored value or `null` when missing.
- `cachePut` will upsert the entry with optional TTL override.

Validation
- `spec.resources.caches` must be a map of ids to cache definitions.
- `cacheRef` must refer to an existing cache id.
- `ttlSeconds`, when present, must be an integer ≥ 1.

Notes
- This section defines the DSL surface only; cache implementations and eviction policies are out of scope and owned by Feature 006.

### 15.3 Context vs Cache

- **Context**
  - JSON object attached to a single journey instance.
  - Mutated by `task`, `transform`, `wait`/`webhook`, etc.
  - Exists only for the lifetime of that journey and is never visible to other journeys.
- **Cache**
  - Declared under `spec.resources.caches` as named resources (for example, `defaultCache`, `hotItems`).
  - Logically an external key–value store whose lifetime is independent of any single journey.
  - Accessed via `cacheGet`/`cachePut` using `cacheRef` and a key mapper.
  - By default it is cross‑journey: any journey that knows the `cacheRef` and key can read/write entries.
- **Scope patterns**
  - Per‑journey behaviour: use journey‑specific keys (for example, `${context.journeyId}`) so only that instance’s entries are used, even though the underlying cache is shared.
  - Cross‑journey behaviour: use stable business identifiers (for example, `userId`, `paymentId`) as keys so multiple journeys can benefit from shared entries.



## 16. Parallel State (branches with join)

The `parallel` state executes multiple branches in parallel (or logically in parallel) and then joins their results before continuing. Each branch is a self-contained sub-state-machine with its own `start` and `states` map.

```yaml
type: parallel
parallel:
  branches:
    - name: limits
      start: limitsCall
      states:
        limitsCall:
          type: task
          task:
            kind: httpCall
            operationRef: core.checkLimits
            params:
              path:
                userId: "${context.userId}"
              headers:
                Accept: application/json
            timeoutMs: 2000
            resultVar: limitsResult
          next: branchEnd
        branchEnd:
          type: succeed
    - name: risk
      start: riskCall
      states:
        riskCall:
          type: task
          task:
            kind: httpCall
            operationRef: risk.scoreUser
            params:
              path:
                userId: "${context.userId}"
              headers:
                Accept: application/json
            timeoutMs: 2000
            resultVar: riskResult
          next: branchEnd
        branchEnd:
          type: succeed
  join:
    strategy: allOf              # allOf | anyOf | firstCompleted
    errorPolicy: collectAll      # collectAll | failFast | ignoreErrors (guidance for Feature 004)
    mapper:
      lang: dataweave
      expr: |
        {
          limits: branches.limits,
          risk: branches.risk
        }
    resultVar: decision          # stored at context.decision
next: decide
```

Semantics (configuration)
- `branches` is a list of branch definitions. Each branch has:
  - `name`: identifier used in join mapping (e.g., `branches.limits`).
  - `start`: state id within the branch-local `states` map.
  - `states`: a map of state ids to state definitions, using the same shapes as top-level states.
- When entering a `parallel` state, the engine conceptually executes each branch from its local `start` until it reaches a terminal state (`succeed`/`fail`) or an error.
- The engine then evaluates the `join` clause:
  - `strategy: allOf` – wait for all branches to reach a terminal state (success or failure) before evaluating `join.mapper`.
  - `strategy: anyOf` – once at least one branch reaches a terminal state, the engine MAY evaluate `join.mapper` early; other branches may continue or be cancelled according to implementation.
  - `strategy: firstCompleted` – evaluate `join.mapper` as soon as the first branch completes; other branches MAY be cancelled.
  - `errorPolicy` provides guidance for how branch-level errors influence join behaviour:
    - `collectAll` – allow branches to complete regardless of success/failure and surface all branch outcomes in `branches` for `join.mapper` to inspect.
    - `failFast` – engines MAY short-circuit remaining branches when a branch clearly fails (implementation-defined), but MUST still provide enough information in `branches` for `join.mapper` to make a decision.
    - `ignoreErrors` – engines treat branch failures as data; `join.mapper` is evaluated regardless, and no automatic fail is triggered by errors alone.
  - `mapper` is a DataWeave expression evaluated with bindings:
    - `context`: the original journey context (or an implementation-defined merged view).
    - `branches`: a map from branch name to that branch’s final result/context snapshot.
  - The mapper result is written to `context.<resultVar>` when `resultVar` is set.
- After join, the parallel state transitions to `next`.

Validation
- Branch `start` must refer to a state id within the branch-local `states` map.
- Branch state ids are local to the branch and must be unique within that branch.
- `join.strategy` must be one of `allOf`, `anyOf`, or `firstCompleted`; Feature 004 will define the exact runtime semantics for `anyOf`/`firstCompleted` in more detail.
- `join.errorPolicy` must be one of `collectAll`, `failFast`, or `ignoreErrors` when present; it is advisory and primarily guides runtime implementations and readers.
- `join.mapper.lang` must be `dataweave` when provided.

Notes
- This section defines the DSL shape and join semantics only; concurrency, scheduling, and detailed error propagation are implemented under Feature 004 (Parallel).

## 17. HTTP Bindings (Inbound)

HTTP bindings describe how inbound HTTP metadata (headers and query params) are projected into `context`, and optionally passed through directly to outbound HTTP calls.

### 17.1 Start request bindings

```yaml
spec:
  httpBindings:
    start:
      headersToContext:
        X-User-Id: userId         # header name -> context field
        X-Tenant-Id: tenantId
      headersPassthrough:
        - from: traceparent       # inbound header name
          to: traceparent         # outbound header name
      queryToContext:
        tenant: tenantId          # query param name -> context field
        debug: debugFlag
```

Semantics
- `headersToContext`: for each `headerName: contextField` entry, when a start request is invoked (`POST /journeys/{workflowName}/start` for `kind: Workflow`, or `POST /apis/{apiName}` / `spec.route.path` for `kind: Api`), if the request has the header, its value is copied to `context.<contextField>`. Missing headers are ignored; requiredness should be expressed via JSON Schema on `context`, not here.
- `headersPassthrough`: for each mapping, the engine conceptually propagates the inbound header value from the start request to all subsequent HTTP tasks as the specified outbound header, *even if it is not stored in `context`*.
  - This is syntactic sugar for header value propagation; it behaves as if the value flowed via an internal, reserved context field.
- `queryToContext`: for each `paramName: contextField` entry, when the start request is invoked, if the request has the query parameter, its (string) value is copied to `context.<contextField>`. Missing params are ignored; requiredness should be expressed via JSON Schema on `context` or a dedicated input schema, not here.

### 17.2 Step request bindings

```yaml
spec:
  httpBindings:
    steps:
      waitForCallback:
        headersToContext:
          X-Request-Id: lastRequestId
        headersPassthrough:
          - from: traceparent
            to: traceparent
        queryToContext:
          retry: retryFlag
```

Semantics
- Applied when `POST /journeys/{journeyId}/steps/{stepId}` is called for the configured `stepId`.
- `headersToContext` behaves as for the start request: copy inbound headers into `context` before evaluating `wait`/`webhook` predicates or mappers.
- `headersPassthrough` behaves as for start: propagate header values from the step request to subsequent HTTP tasks as outbound headers, without requiring an explicit `context` field.
- `queryToContext` behaves as for the start request: copy inbound query parameter values into `context` before evaluating `wait`/`webhook` predicates or mappers.

### 17.3 Usage guidance
- Use `headersToContext` when the header:
  - Influences branching, transformations, or other behaviour.
  - Needs to be logged, inspected, or included in downstream payloads.
  - Should be part of the journey’s replay/debug story (visible in `context`).
- Use `headersPassthrough` when the header:
  - Is purely transport-level (for example, tracing, correlation), and
  - Does not need to be read by the workflow itself.
- It is valid to use both: bind a header into `context` and also pass it through, if you need both visibility and propagation.

Validation
- `httpBindings.start.headersToContext` and `httpBindings.steps.*.headersToContext` must map header names to non-empty context field names.
- `httpBindings.start.queryToContext` and `httpBindings.steps.*.queryToContext`, when present, must map query parameter names to non-empty context field names.
- `headersPassthrough` entries must provide `from` and `to` header names (non-empty strings).
- Step ids under `httpBindings.steps` must refer to external-input states (`wait`/`webhook`).

Notes
- This section defines the binding model; concrete enforcement and header sets are implemented in the runtime/API layer.

## 18. HTTP Security Policies (Auth)

HTTP security policies define reusable authentication constraints for inbound HTTP requests (start and step calls). They are configured under `spec.policies.httpSecurity` and attached via `securityPolicyRef` at journey and step levels.

### 18.1 Policy definitions

```yaml
spec:
  policies:
    httpSecurity:
      default: jwtDefault      # optional default policy id
      definitions:
        jwtDefault:
          kind: jwt
          issuer: "https://issuer.example.com"
          audience: ["journeyforge"]
          jwks:
            source: jwksUrl
            url: "https://issuer.example.com/.well-known/jwks.json"
            cacheTtlSeconds: 3600
          clockSkewSeconds: 60
          requiredClaims:
            sub:
              type: string
            scope:
              contains: ["journeys:read"]
        clientCertDefault:
          kind: mtls
          trustAnchors:
            - pemRef: trust/roots/root-ca.pem
            - pemRef: trust/roots/sub-ca.pem
          allowSubjects:
            - "CN=journey-client,OU=Journeys,O=Example Corp,L=Zagreb,C=HR"
          requireClientCert: true
        apiKeyDefault:
          kind: apiKey
          location: header        # header | query
          name: X-Api-Key
          keys:
            - secretRef: secret://apikeys/backend-service
```

Kinds
- `jwt` – JSON Web Token validation policy.
  - `issuer`, `audience`: expected issuer and audience(s).
  - `jwks`: where to obtain verification keys (JWKS URL or static key set).
  - `clockSkewSeconds`: allowed skew when validating `exp`/`nbf`.
  - `requiredClaims`: shape and constraints for specific claims (implementation-defined schema).
- `mtls` – client certificate policy.
  - `trustAnchors`: list of root/sub-CA PEM refs to trust.
  - `allowSubjects`: list of allowed subject DNs; certificate chain must validate against `trustAnchors` and subject must match one of these.
  - `requireClientCert`: whether a client certificate is mandatory.
- `apiKey` – API key policy.
  - `location`: where to read the key (header or query).
  - `name`: header or query parameter name.
  - `keys`: list of references to key material (static or externally resolved).

### 18.2 Attaching policies

```yaml
spec:
  security:
    journeyPolicyRef: jwtDefault          # applied to all inbound endpoints by default
    start:
      securityPolicyRef: jwtDefault      # optional override for start
    steps:
      waitForCallback:
        securityPolicyRef: clientCertDefault
```

Semantics
- `journeyPolicyRef` (if set) applies to `POST /journeys/{workflowName}/start` and all step endpoints unless overridden.
- `securityPolicyRef` under `start` overrides `journeyPolicyRef` specifically for the start endpoint.
- `securityPolicyRef` under `steps.<stepId>` overrides both for that particular step endpoint.
- If no policy is resolved for an endpoint, authentication behaviour is implementation-defined (for example, relying on upstream gateway enforcement).

### 18.3 Validation
- `spec.policies.httpSecurity.definitions` must be a map of ids to policy objects.
- Any `journeyPolicyRef` / `securityPolicyRef` must refer to an existing id in `definitions` (or to a platform-level policy); unknown ids are a validation error.
- JWT policies must configure a JWKS or key source; client-certificate policies must specify at least one `trustAnchors` entry.

### 18.4 Usage guidance
- Use HTTP security policies when:
  - You need specs to be explicit about how journeys are authenticated (JWT, mTLS, API keys).
  - You want the same authentication behaviour reused across multiple journeys or steps.
- Combine with `httpBindings` when:
  - You also need to project authentication metadata into `context` (for example, userId from a JWT claim) or forward headers downstream.
  - `httpSecurity` enforces *who* can call; `httpBindings` controls *how* inbound metadata is made available to the workflow and downstream calls.

Notes
- This section defines the configuration model only; runtime enforcement belongs to the security implementation in the runtime and is out of scope for the DSL reference.

### 18.5 Mapping auth into workflow context

After successful policy validation, the runtime MUST populate one of the following views under `context.auth` so DataWeave expressions and `transform` states can use authentication data:

- JWT policies (`kind: jwt`):
  - `context.auth.jwt.header` – JOSE header (non-sensitive fields only, for example `alg`, `kid`, `typ`).
  - `context.auth.jwt.claims` – decoded claims object as JSON (e.g., `sub`, `scope`, `aud`, `iss`).
- mTLS policies (`kind: mtls`):
  - `context.auth.mtls.subjectDn` – subject distinguished name of the validated client certificate.
  - `context.auth.mtls.issuerDn` – issuer DN (optional).
  - `context.auth.mtls.fingerprintSha256` – certificate fingerprint (optional, for correlation/logging).
- API key policies (`kind: apiKey`):
  - `context.auth.apiKey.keyId` – stable identifier for the validated key (for example derived from `secretRef`); the raw key MUST NOT be exposed.

Mapping auth data into shorter context fields can be done via a `transform` state. For example:

```yaml
auth-normalise:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ { 'userId': context.auth.jwt.claims.sub }
    target:
      kind: context
      path: ''   # replace root context when using expr with context ++ { ... }
  next: nextState
```

or for client certificates:

```yaml
mtls-normalise:
  type: transform
  transform:
    mapper:
      lang: dataweave
      expr: |
        context ++ { 'clientSubjectDn': context.auth.mtls.subjectDn }
    target:
      kind: context
      path: ''
  next: nextState
```

This keeps `context` as the canonical place for data that influences journey behaviour, while `httpSecurity` governs authentication and `httpBindings` governs how inbound metadata becomes available to the workflow and downstream calls.

## 20. Named Outcomes (spec.outcomes)

Named outcomes provide a way to classify terminal journey results into a small, stable vocabulary for clients, dashboards, and telemetry, without changing execution semantics.

```yaml
spec:
  outcomes:
    SucceededWithCacheHit:
      when:
        phase: Succeeded
        predicate:
          lang: dataweave
          expr: |
            context.cachedUser != null
    FailedUpstream:
      when:
        phase: Failed
        predicate:
          lang: dataweave
          expr: |
            context.error.code == 'https://example.com/probs/upstream-error'
```

Shape
- `spec.outcomes` is a map of outcome ids to classification rules.
- Each outcome has:
  - `when.phase`: `Succeeded` or `Failed` (must match `JourneyOutcome.phase`).
  - `when.predicate`: optional DataWeave predicate evaluated with `context` bound to the final workflow context and `output`/`error` available as bindings if the runtime supplies them.

Semantics (classification only)
- Outcomes do not affect execution: the workflow still terminates on `succeed`/`fail` as usual.
- After a journey reaches a terminal phase, a runtime MAY:
  - Evaluate outcomes in a deterministic order (for example, insertion order or lexicographic by id).
  - Select the first outcome whose `when.phase` matches and whose predicate evaluates to `true`.
  - Record the selected outcome id for telemetry or include it as an additional field (for example `outcomeId`) in `JourneyOutcome` without changing existing fields.
- If no outcome matches, the journey remains unclassified from the DSL’s perspective.

Validation
- Outcome ids must be unique strings.
- `when.phase` must be either `Succeeded` or `Failed`.
- If `when.predicate` is present, it must declare `lang: dataweave` and the expression must return boolean.

Usage notes
- Use outcomes to give names to common scenarios such as “SucceededWithCacheHit”, “SucceededWithoutCache”, “FailedUpstream”, “RejectedByPolicy”.
- Keep outcome predicates simple and stable; they should refer to durable semantics (for example `error.code`) rather than transient implementation details.
