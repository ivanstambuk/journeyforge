# JourneyForge DSL – Reference

Status: Draft | Last updated: 2025-11-21

This document is **normative** for the JourneyForge journey DSL (for `kind: Journey` and `kind: Api`) supported by Feature 001. It defines language surface and semantics only; **engine implementation status is non-normative and tracked in feature specs** (for example `docs/4-architecture/features/001/spec.md`). See Q-004 in `docs/4-architecture/open-questions.md` for background.

This document normatively defines the JourneyForge journey DSL (for `kind: Journey` and `kind: Api`) supported by Feature 001. It aims to be explicit about behaviour and limitations so we can refine before implementation.

## 1. Overview
- Purpose: describe small, synchronous API journeys (defined by `kind: Journey`) in a human-friendly format (YAML 1.2 subset) with a precise JSON model.
- Files: `.journey.yaml` / `.journey.yml` or `.journey.json`.
- States: `task` (HTTP call), `choice` (branch), `transform` (DataWeave mapping), `parallel` (branches with join), `wait` (external input), `webhook` (callback input), `succeed` (terminal), `fail` (terminal).
- Expressions & transforms: DataWeave 2.x is the canonical language for predicates and (future) transform nodes.
- Execution: starts at `spec.start`, mutates a JSON `context`, and terminates on `succeed`/`fail`, a global execution timeout (`spec.execution.maxDurationSec`), or an engine execution error. When `spec.compensation` is present, a separate compensation jo urney MAY run after non-successful termination.

### 1a. State types and surface

The DSL surface defines the following state types and configuration blocks. All of them belong to the same language; the engine may support them in stages, but the spec treats them as a single coherent DSL.

| State type / construct                    | Description                                      | Notes in spec                                          |
|------------------------------------------|--------------------------------------------------|--------------------------------------------------------|
| `task` (`kind: httpCall`)                | HTTP call with structured result recording       | Fully specified, including `operationRef` and errors   |
| `choice`                                 | Branch on predicates                             | Fully specified, DataWeave predicates only             |
| `succeed`                                | Terminal success                                 | Fully specified                                        |
| `fail`                                   | Terminal failure with error code/reason          | Fully specified; aligned with RFC 9457 Problem Details |
| `transform`                              | DataWeave mapping into context/vars              | Fully specified                                        |
| `wait`                                   | Manual/external input                            | DSL shape + REST callback surface defined              |
| `webhook`                                | Callback input                                   | DSL shape + callback surface and security hooks defined|
| `parallel`                               | Parallel branches with join                      | DSL shape + join contract defined                      |
| Cache resources/tasks (`cacheGet`/`cachePut`) | Named caches and cache operations           | DSL shape defined; cache semantics described in section 15 |
| Policies (`httpResilience`, `httpSecurity`, `httpClientAuth`) | Resiliency/auth configuration (inbound & outbound) | Configuration surface defined; policy semantics described in sections 17–19 |

## 2. Top-level shape

### 2a. Inline JSON Schemas

The DSL embeds JSON Schemas directly in a few well-known locations:
- `spec.input.schema` – logical input object for the journey/API.
- `spec.output.schema` – logical output object when declared.
- `spec.context.schema` – optional shape for the mutable context.
- `wait.input.schema`, `wait.response.schema` – per-step request/response payloads.
- `webhook.input.schema`, `webhook.response.schema` – callback payloads.

Semantics
- All inline `*.schema` blocks use JSON Schema 2020-12 semantics.
- Authors SHOULD omit `$schema` and `$id` inside these blocks; tooling and engines MUST assume the 2020-12 meta-schema by default.
- Schemas SHOULD be authored to closely mirror OpenAPI 3.1 component schemas:
  - Top-level `type`, `properties`, `required`, `additionalProperties`, etc.
  - Optional `title` MAY be used to carry a human-readable name; exporters MAY reuse it for component names (for example, `JourneyStartRequest`).

Example (input schema):
```yaml
spec:
  input:
    schema:
      title: JourneyStartRequest
      type: object
      required: [inputId]
      properties:
        inputId:
          type: string
      additionalProperties: true
```

Example (output schema):
```yaml
spec:
  output:
    schema:
      title: HttpSuccessOutput
      type: object
      required: [ok]
      properties:
        status:
          type: integer
        ok:
          type: boolean
        headers:
          type: object
          additionalProperties:
            type: string
        body: {}
        error:
          type: object
          properties:
            type:
              type: string
            message:
              type: string
          required: [type]
          additionalProperties: true
      additionalProperties: true
```

## 2b. API catalog (OpenAPI binding)
To reference downstream services by OpenAPI operationId instead of raw URLs, a journey definition (`kind: Journey`) or API endpoint may declare an API catalog and use `operationRef` in HTTP tasks.

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
kind: Journey | Api            # required
metadata:                       # required
  name: <string>
  version: <semver>
  description: <string>         # optional; human-readable summary
spec:                           # required
  start: <stateId>              # required
  states:                       # required: map<string, State>
    <stateId>: <State>
```

Constraints
- `apiVersion` must be `v1`.
- `kind` must be either:
  - `Journey` – a long‑lived journey exposed via the Journeys API, or
  - `Api` – a synchronous HTTP endpoint (single request/response) with no visible journey id.
- `metadata.name` must be a DNS‑label‑like string `[a-z0-9]([-a-z0-9]*[a-z0-9])?`.
- `metadata.description`, when present:
  - MUST be a non-empty string.
  - MUST NOT contain secrets or PII.
  - SHOULD be a short, human-readable summary of the journey or API (for example, one or two sentences).
  - MUST NOT be used for routing, authorisation, or behaviour; engines and tooling MAY surface it in UIs, CLIs, and generated documentation (for example, OpenAPI descriptions).
- `metadata.tags`, when present:
  - MUST be an array of strings.
  - MUST NOT contain secrets or PII.
  - SHOULD use short, human-readable identifiers (recommended `kebab-case`, for example `self-service`, `kyc`, `financial`).
  - MUST respect the maximum count configured via the `MetadataLimits` document (see section 21); tools SHOULD treat specs that exceed this as validation errors.
- `spec.states` must contain `spec.start`.

### 2a.i. Journeys (`kind: Journey`)

`kind: Journey` specs are “journey definitions”:
- Initiated via `/api/v1/journeys/{journeyName}/start` (see `docs/3-reference/openapi/journeys.openapi.yaml` and the OpenAPI export guideline).
- Identified by a `journeyId` and observed via `/journeys/{journeyId}` and `/journeys/{journeyId}/result`.
- May use all state types, including long‑lived external input (`wait`, `webhook`).
 - For the start call, the HTTP request body is deserialised as JSON and used directly as the initial `context` object for the journey (subject to any input schema the journey definition declares).

The semantics of `succeed`/`fail` for journeys are defined in sections 5.4–5.6, which describe the `JourneyOutcome` envelope.

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
  description: <string>         # optional; human-readable summary
spec:
  route:                        # optional; controls REST surface
    path: <string>              # e.g. /apis/get-user-public; defaults to /apis/{metadata.name}
    method: <string>            # e.g. POST; the initial version supports POST only
  input:                        # optional but strongly recommended
    schema: <JsonSchema>        # inline JSON Schema (2020-12) for request body
  output:                       # optional but strongly recommended
    schema: <JsonSchema>        # inline JSON Schema (2020-12) for successful response body
  apiResponses:                 # optional; HTTP status mapping rules for kind: Api only
    rules:
      - when:
          phase: Failed
          errorType: <string>   # optional; Problem.type to match
          predicate:            # optional; DataWeave predicate over context + error
            lang: dataweave
            expr: <expr>
        status: <integer>       # literal HTTP status code
        # or:
        # statusExpr:
        #   lang: dataweave
        #   expr: <expr>        # DataWeave expression that evaluates to an HTTP status code
    default:                    # optional; per-phase fallbacks when no rule matches
      Succeeded: 200            # optional; defaults to 200 when omitted
      Failed: fromProblemStatus # optional; defaults to Problem.status or 500 when omitted
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
  - The HTTP request body is deserialised as JSON and used to initialise the journey `context` for that invocation (subject to `spec.input.schema` validation, when present).
  - `httpBindings.start` MAY further project headers into `context` and/or provide outbound header defaults.
- Successful responses:
  - Reaching `succeed` terminates execution and produces a 2xx HTTP response.
  - The response body is taken from `context.<outputVar>` when `outputVar` is set on the `succeed` state; otherwise the full `context` is used.
- Error responses:
  - Reaching `fail` terminates execution and produces a non‑2xx HTTP response.
  - `errorCode` and `reason` follow the RFC 9457 alignment rules in section 5.6.
  - The structure of the error payload for a given journey or API MUST follow the journey’s error configuration:
    - When `spec.errors.envelope` is omitted or uses `format: problemDetails`, the error body MUST use the Problem Details shape.
    - When `spec.errors.envelope.format: custom` is present, the error body MUST be produced by the journey’s configured envelope mapper.
  - HTTP status code selection for `kind: Api` is controlled by `spec.apiResponses` when present:
    - Engines MUST evaluate `spec.apiResponses.rules` in order; the first rule whose `when` clause matches the terminal phase and (for failures) the canonical Problem object determines the HTTP status via `status` or `statusExpr`.
    - When `spec.apiResponses` is omitted or when no rule matches:
      - For `phase = Succeeded`, the engine MUST use HTTP 200.
      - For `phase = Failed`, the engine MUST use the Problem `status` field when present and valid, or 500 when absent.
  - The error envelope itself remains governed by `spec.errors.envelope` and MUST be uniform for a given journey or API; `spec.apiResponses` MUST NOT change the error body shape, only the HTTP status code.

## 2b. Defaults (spec.defaults)

Journey definitions may define per-journey defaults to reduce repetition. Defaults apply when specific fields are omitted at state level.

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
- `resiliencePolicyRef`, when present, SHOULD refer to an id under `spec.policies.httpResilience.definitions` (or a platform-level policy); unknown ids are a validation warning for tools, but may be resolved by the platform or at engine configuration time.

## 2c. Execution deadlines (spec.execution)

Journeys and API endpoints may define a global execution budget to avoid unbounded run times. The execution block expresses a spec-visible wall-clock limit and how timeouts are surfaced to callers.

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
    - `kind: Journey`: the journey is accepted by the Journeys API `start` endpoint.
    - `kind: Api`: the HTTP request is accepted by the API endpoint (for example `/api/v1/apis/{apiName}` or `spec.route.path`).
  - Engine implementations MUST treat reaching this deadline as a failure even if the state machine has not yet reached a terminal `succeed`/`fail` state.
- Interaction with per-state timeouts:
  - For blocking operations that already have a timeout (`httpCall.timeoutMs`, `wait.timeoutSec`, `webhook.timeoutSec`), the engine SHOULD clamp the effective timeout to the remaining global budget.
  - Conceptually, the effective timeout is `min(configuredTimeout, remainingBudget)`; when the remaining budget is ≤ 0, the operation SHOULD NOT start and the run MUST be treated as timed out.
  - When no per-state timeout is configured, the engine MAY still interrupt long-running operations when the global deadline expires, or detect the timeout immediately after the operation completes.
- Timeout outcome:
  - When the global deadline is reached, the engine MUST stop scheduling new states and complete the run as a failure using `spec.execution.onTimeout`.
  - For `kind: Journey`:
    - The resulting `JourneyOutcome` has `phase = Failed` and `error` populated from `onTimeout.errorCode` and `onTimeout.reason` (following the Problem Details alignment rules in section 5.6).
  - For `kind: Api`:
    - The engine terminates the HTTP request with a non‑2xx response that reflects the same error code and reason.
    - Exporters and the engine MAY map execution timeouts to HTTP 504 Gateway Timeout by default, or use `spec.outcomes` and canonical Problem Details `status` as inputs when choosing HTTP status codes for timeouts.
- Relationship with platform limits:
  - Platform- or environment-level maximums MAY further restrict execution time; the engine MAY clamp `spec.execution.maxDurationSec` to a configured upper bound.
  - Setting a large `maxDurationSec` does not guarantee that a run is allowed to execute that long; platform limits take precedence.

Validation
- `spec.execution` is optional.
- When present:
  - `maxDurationSec` is required and MUST be an integer ≥ 1.
  - `onTimeout` is required and MUST contain:
    - `errorCode`: non-empty string, recommended to be a stable identifier (for example, a Problem Details `type` URI).
    - `reason`: non-empty string describing the timeout condition for humans (operators, API clients).
 - `spec.execution` MAY be used for both `kind: Journey` and `kind: Api` specs.

## 2d. Global compensation (spec.compensation)

Some journeys and APIs need a global “compensation journey” that runs when the main execution does not succeed, to undo or mitigate side effects (for example, HTTP mutations, database writes, or emitted events). The `spec.compensation` block allows authors to attach such a compensation path to a journey definition in a declarative, opt‑in way.

At a high level:
- The main journey executes as usual from `spec.start` until it reaches `succeed`/`fail`, hits a global execution timeout (`spec.execution.maxDurationSec`), or is cancelled.
- When the main run terminates in any non‑success state (fail, timeout, cancel, engine execution error), and `spec.compensation` is present, the engine may start a separate compensation journey using the embedded compensation state machine.

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
    - It is cancelled via an engine/admin API.
    - It ends due to an internal engine error.
  - Successful runs (terminal `succeed` without error) MUST NOT trigger compensation.
- Compensation journey:
  - The `compensation.states` map defines a separate state machine, using the same state types and configuration shapes as the top-level journey definition (`task`, `choice`, `transform`, `parallel`, `wait`, `webhook`, `succeed`, `fail`, etc.), subject to the same `kind: Journey` / `kind: Api` constraints.
  - `compensation.start` identifies the first state in this map.
- Compensation runs with its own control flow and may itself succeed or fail; these outcomes are not visible to the main caller but SHOULD be logged and traced by the engine.
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
      "journeyName": "string"
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
    - For `kind: Journey`:
      - `JourneyOutcome` is only finalised after compensation finishes.
      - `JourneyOutcome.phase` and `JourneyOutcome.error` continue to represent the original failure; compensation errors MAY be recorded in telemetry or as extensions but MUST NOT change `phase`.
- Relationship with sub-journeys:
  - `spec.compensation` describes a coarse-grained, global compensation journey for the entire journey/API run.
  - Future features (for example a `subjourney` state or per-step `compensate` blocks) may provide more fine-grained SAGA semantics; these are complementary and not required to use `spec.compensation`.

Validation
- `spec.compensation` is optional.
- When present:
  - `mode`, when provided, MUST be either `sync` or `async`; if omitted, the effective mode is `async`.
  - `start` is required and MUST refer to a key in `compensation.states`.
  - `states` is required and MUST be a non-empty map of state definitions with the same validation rules as top-level states.
  - Compensation states MUST obey the same constraints as the main spec for the given `kind` (for example, no `wait`/`webhook` in `kind: Api`).
  - Tooling SHOULD flag specs where `spec.compensation` is declared but the engine does not support compensation journeys.

## 2e. HTTP surface & hypermedia links (spec.httpSurface.links)

Journeys (`kind: Journey`) are exposed via the Journeys API, which returns JSON envelopes such as `JourneyStartResponse`, `JourneyStatus`, and `JourneyOutcome`. To make the next legal actions discoverable, implementations MAY expose a HAL-like `_links` object alongside the core fields.

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
  - When links are enabled, the engine SHOULD:
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
  - When `spec.httpSurface.links.enabled == false`, the engine SHOULD omit the `_links` object from:
    - `JourneyStartResponse`,
    - `JourneyStatus`,
    - `JourneyOutcome`
    for that journey, even if the platform would otherwise include links globally.
  - Exporters SHOULD continue to describe `_links` as an optional property in the generic Journeys OpenAPI schema; implementations that honour `enabled: false` simply omit it at execution time for the relevant journey.
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
  "journeyName": "order-orchestration",
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
    - While `JourneyStatus.phase == "Running"`, the engine SHOULD expose a `_links.cancel` entry as described in section 2e, pointing to the canonical cancellation step `/api/v1/journeys/{journeyId}/steps/cancel`.
    - Once the journey is terminal (`Succeeded` or `Failed`), `_links.cancel` MUST be omitted.
  - When `cancellable == false`, the engine MUST omit `_links.cancel` even if the journey is `Running`.
- Cancellation semantics (conceptual):
  - When an engine honours a user‑initiated cancellation, it SHOULD:
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
  "journeyName": "order-orchestration",
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

## 2g. Journey metadata (tags & attributes)

Journey definitions and journey instances expose lightweight metadata for classification and querying. The DSL
distinguishes:
- Definition-level tags (`metadata.tags`) on journey definitions.
- Instance-level tags (`journey.tags`) and attributes (`journey.attributes`) on journey instances, populated by the engine according to explicit rules.

### 2g.i. Definition tags (`metadata.tags`)

- Shape:
  - Optional `metadata.tags: string[]` for both `kind: Journey` and `kind: Api`.
- Semantics:
  - Tags classify the spec (for example, `self-service`, `kyc`, `pii`, `financial`,
    `credit-decision`).
  - Tags are non-sensitive; they MUST NOT contain PII or secrets.
  - Tags SHOULD be short, human-readable identifiers; `kebab-case` is recommended
    (for example, `self-service`, `cache-user-profile`).
- Limits:
  - The maximum number of tags per spec is controlled via the `MetadataLimits` document
    (see section 21).

### 2g.ii. Instance tags (`journey.tags`)

- Concept:
  - Each journey instance may carry a set of tags derived from:
    - The definition’s `metadata.tags`.
    - Explicit tag bindings declared under `spec.metadata.bindings.tags` (see 2g.iv).
  - Clients do not set instance tags directly in the start request; the engine derives them
    from the configured journey definition.
- Semantics:
  - Instance tags are intended for:
    - Filtering and grouping in operator dashboards.
    - Coarse-grained policy and reporting (for example, “all `kyc` journeys in `Running` phase”).
  - For v1, instance tags are immutable after the journey is started.
- Limits:
  - The maximum number of instance tags per journey and maximum tag length are controlled by
    `MetadataLimits` (section 21).

### 2g.iii. Instance attributes (`journey.attributes`)

- Concept:
  - `journey.attributes` is a small `Map<String,String>` attached to each journey for
    identity, tenancy, and correlation metadata, for example:
    - `subjectId` – canonical owner identity.
    - `tenantId` – logical tenant id.
    - `channel` – origin channel (`web`, `mobile`, etc.).
    - `initiatedBy` – `user`, `system`, or `admin`.
    - Correlation ids such as `orderId`, `paymentIntentId`, `crmCaseId`.
- Population:
  - Attributes are populated by the engine based on explicit bindings declared under
    `spec.metadata.bindings.attributes` (see 2g.iv), using values from:
    - The start request payload.
    - Named headers (for example, `X-Tenant-Id`, `X-Channel`).
    - The W3C `baggage` header (key/value entries).
  - Clients do not send an `attributes` property in the start request; they provide only the
    normal JSON payload and headers.
- Reserved keys:
  - `subjectId` – identity of the subject derived from the validated JWT at journey start
    (for example `context.auth.jwt.claims.sub`), when present and not configured as
    anonymous by the HTTP security policy.
  - `tenantId` – logical tenant identifier.
  - `channel` – origin channel.
  - `initiatedBy` – who initiated the journey (`user`, `system`, `admin`).
- Semantics:
  - Attributes are intended for:
    - Self-service queries (“my journeys”) via `subjectId`.
    - Multi-tenant and operator views via `tenantId` and `region`.
    - Correlation with external systems via `orderId`, `paymentIntentId`, `crmCaseId`, etc.
  - For v1, attributes are immutable after the journey is started.
- Limits:
  - The maximum number of attribute keys and key/value lengths are controlled by
    `MetadataLimits` (section 21).

### 2g.iv. Metadata bindings (spec.metadata.bindings)

Journey definitions can declare how to bind values from the start request into journey
instance tags and attributes. Bindings are evaluated exactly once, when handling the start
request for a `kind: Journey` journey (`POST /api/v1/journeys/{journeyName}/start`); they
do not depend on later state transitions.

Shape:

```yaml
spec:
  metadata:
    bindings:
      tags:
        fromPayload:
          - path: channel
          - path: segment
        fromHeaders:
          - header: X-Journey-Tag
        fromBaggage:
          - key: journey_tag

      attributes:
        fromPayload:
          orderId:
            path: order.id
          customerId:
            path: customer.id
        fromHeaders:
          tenantId:
            header: X-Tenant-Id
          channel:
            header: X-Channel
        fromBaggage:
          correlationId:
            key: correlation_id
          experiment:
            key: exp
```

Semantics
- Evaluation timing:
  - The engine evaluates `spec.metadata.bindings` exactly once per journey instance, when
    processing the start request (`/journeys/{journeyName}/start`) for `kind: Journey`.
  - Bindings do not reference `context`; they read directly from the start request payload
    and headers.
- Tag bindings:
  - `tags.fromPayload`:
    - Each entry is an object with a `path` field, a dot path evaluated against the start
      request body.
    - When the path resolves to a non-empty string value, that value is added to
      `journey.tags` (subject to `MetadataLimits.instanceTags`).
  - `tags.fromHeaders`:
    - Each entry is an object with a `header` field naming an inbound header.
    - When the header is present and non-empty, its value is added to `journey.tags`.
  - `tags.fromBaggage`:
    - Each entry is an object with a `key` field naming a baggage key.
    - The engine parses the `baggage` header according to the W3C Baggage format and, when
      the key is present, adds its value as a tag.
- Attribute bindings:
  - `attributes.fromPayload`:
    - A map from attribute name to an object with a `path` field.
    - For each `attributeName: { path: <dotPath> }`, the engine evaluates the path against
      the start request body. When a value is present, it is converted to a string (if
      needed) and written to `journey.attributes[attributeName]`.
  - `attributes.fromHeaders`:
    - A map from attribute name to an object with a `header` field.
    - For each `attributeName: { header: <headerName> }`, when the header is present, its
      value is written to `journey.attributes[attributeName]`.
  - `attributes.fromBaggage`:
    - A map from attribute name to an object with a `key` field.
    - For each `attributeName: { key: <baggageKey> }`, when the baggage key is present, its
      value is written to `journey.attributes[attributeName]`.
- Interaction with limits:
  - When bindings would cause `journey.tags` or `journey.attributes` to exceed the limits in
    `MetadataLimits`, the engine MUST either:
    - Reject the start request with a clear error, or
    - Truncate additional tags/attributes in a documented way. Engines SHOULD prefer
      explicit rejection in production settings.

Validation
- When present, `spec.metadata.bindings` MUST be an object.
- `bindings.tags.fromPayload`, when present, MUST be an array of objects with a non-empty
  `path` string.
- `bindings.tags.fromHeaders`, when present, MUST be an array of objects with a non-empty
  `header` string.
- `bindings.tags.fromBaggage`, when present, MUST be an array of objects with a non-empty
  `key` string.
- `bindings.attributes.fromPayload`, `fromHeaders`, `fromBaggage`, when present, MUST be
  maps from attribute names (non-empty strings) to objects with the corresponding `path`,
  `header`, or `key` fields.
- Attribute names used in bindings SHOULD follow the same naming guidance as other
  attribute keys (`lowerCamel` / `snake_case`) and are subject to `MetadataLimits`.

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
  auth:                            # optional; outbound auth policy binding
    policyRef: <id>                # reference to an outbound auth policy defined under spec.policies.httpClientAuth
  errorMapping:                     # optional – conditional transform for error results
    when: nonOk                     # only nonOk is supported (result.ok == false)
    mapper:
      lang: dataweave
      expr: <expression>            # same shape as transform.mapper
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
- Request: constructed from `method`, `url`, `headers`, optional `body`, and any configured outbound auth.
- Body: if an object in YAML/JSON, it is serialised as JSON with `application/json` unless `Content-Type` overrides.
- `mode` controls how the journey instance observes the HTTP outcome:
  - `requestResponse` (default):
    - The engine sends the HTTP request and waits for a response (or timeout/error).
    - It then builds a structured result object and stores it at `context.<resultVar>`.
    - Callers can branch on or transform this result via `choice`, `transform`, or `errorMapping`.
  - `notify` (fire-and-forget):
    - The engine sends the HTTP request but does not wait for a response body and does not construct a result object.
    - The journey instance does not observe HTTP status, headers, or body; any network or protocol errors are implementation-defined (typically logged) but MUST NOT change control flow.
    - Execution continues immediately to `next`.
- Naming and relationship to compensation:
  - `task.mode` uses HTTP-centric names (`requestResponse` vs `notify`) to describe whether the journey instance observes an HTTP result or simply fires a request and proceeds.
  - This is distinct from `spec.compensation.mode` (`sync` vs `async`), which controls whether the *caller* waits for the compensation journey to finish; compensation MAY still run asynchronously even when individual HTTP tasks use `requestResponse`.
- Response handling in `requestResponse` mode (no auto-termination):
  - 2xx status: build a result object with `ok=true`; if `Content-Type` indicates JSON, parse to JSON; else store as string.
  - Non‑2xx: build a result object with `ok=false` and include `status`, headers, and body (parsed when possible).
  - Network errors/timeouts: build a result object with `ok=false` and `error` details (e.g., `{type: TIMEOUT|NETWORK, message: "..."}`).
  - The engine never auto‑terminates due to HTTP outcome; execution always continues to `next`. Use `choice` (or future policies) to branch on the recorded result.
- Conditional error mapping (`errorMapping`):
  - After constructing and storing the HTTP result object at `context.<resultVar>`, if `errorMapping` is present and `when: nonOk`, the engine conceptually evaluates `errorMapping.mapper` only when `context.<resultVar>.ok == false`.
  - The mapper evaluates with:
    - `context` bound to the current journey context, and
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
  - `resiliencePolicyRef` MAY be ignored by the engine; retries have no observable effect on journey behaviour.
- Outbound auth (`auth.policyRef`):
  - When present, the engine resolves `auth.policyRef` against `spec.policies.httpClientAuth.definitions` (or platform-level equivalents) and applies the resulting policy to the outbound HTTP request (for example by adding an `Authorization` header or attaching a client certificate).
  - When absent, and when `spec.policies.httpClientAuth.default` is set, the engine MAY use the default policy as if `auth.policyRef` were set to that id.
  - When neither a task-level `auth.policyRef` nor a usable default can be resolved, the request is sent without additional outbound auth (subject to implementation defaults).
- HTTP outcomes (status/timeouts) do not terminate execution; you must branch explicitly. In `notify` mode the journey instance cannot branch on call outcomes because they are not observable.
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
        expr: <expression>
    value:                           # required – mapper for event payload
      mapper:
        lang: dataweave
        expr: <expression>
    headers:                         # optional – Kafka record headers
      <k>: <string|interpolated>
    keySchemaRef: <string>           # optional – JSON Schema for the key
    valueSchemaRef: <string>         # optional but recommended – JSON Schema for the payload
next: <stateId>
```

Semantics
- Transport and topic:
  - `eventPublish.transport` identifies the event transport; in the initial implementation it MUST be `kafka`.
  - `eventPublish.topic` is the Kafka topic name; cluster/connection details are provided out of band by the engine and its configuration.
- Key and value:
  - `eventPublish.key.mapper` (when present) is evaluated with `context` bound to the current journey context; the result is serialised according to engine configuration (typically string/bytes) and used as the Kafka record key.
  - `eventPublish.value.mapper` is required and produces the event payload object; engines typically serialise this as JSON.
- Headers:
  - `eventPublish.headers` is an optional map from header name to interpolated string value; values are evaluated against `context` and attached as Kafka record headers.
- Schemas:
  - `eventPublish.keySchemaRef` (optional) points to a JSON Schema that describes the logical shape of the key. Engine implementations and tooling MAY use this for validation or schema registry integration.
  - `eventPublish.valueSchemaRef` (optional but recommended) points to a JSON Schema that describes the event payload. When present, engines SHOULD validate the mapped payload against this schema before publishing and MAY register it with an event/schema registry.
- Control flow:
  - Publishing is fire-and-forget from the journey’s perspective: the task does not write a `resultVar`, and no control-flow decisions are based on publish outcomes.
  - On publish failures after any configured retries, implementations MAY treat this as an engine execution error (failing the journey/API call); the DSL does not surface partial success states.

Validation
- `task.kind` may be `httpCall` or `eventPublish`.
- For `kind: eventPublish`:
  - `eventPublish.transport` is required and must be `kafka`.
  - `eventPublish.topic` is required and must be a non-empty string.
  - `eventPublish.value` is required and must contain a `mapper` object with `lang: dataweave` and an inline `expr`.
  - `eventPublish.key` (when present) must contain a `mapper` object with `lang: dataweave` and an inline `expr`.
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
- DataWeave predicate: evaluate `when.predicate.expr` with `context` bound to the current journey context. The expression must return a boolean; non‑boolean results are a validation error at spec validation / compile time.
- Predicate runtime errors: when evaluating `when.predicate.expr` raises a DataWeave runtime error at execution time, the engine MUST treat this as an internal engine error (not a journey‑authored failure):
  - The run terminates as a failure with a canonical internal error code (for example a Problem Details `type` such as `urn:journeyforge:error:internal`) and HTTP status 500 for externally visible APIs.
  - For `kind: Journey`, the resulting `JourneyOutcome` MUST have `phase = Failed` and `error.code` set to the same internal error identifier; platform logging and telemetry SHOULD carry more detailed diagnostics.
  - For `kind: Api`, the HTTP response MUST use the same internal error identifier as the Problem `type` / `JourneyOutcome.error.code` and status 500.
  - This outcome indicates a bug or misconfiguration in the journey or platform; well‑behaved journeys SHOULD avoid triggering it in normal operation.
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
- `task` states (for example `httpCall`) never auto-terminate a journey. They always store a structured result in `context.<resultVar>` (including `status`, `ok`, `headers`, `body`, optional `error`) and then continue to `next`.
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
  - Journeys MAY keep the full Problem Details object in `context` (for example `context.problem`) even when only `code` and `reason` are exposed in `JourneyOutcome.error`.
- Normalising downstream errors to RFC 9457
  - Downstream HTTP APIs can return arbitrary error formats; journeys can normalise these into a Problem Details object using `transform` states and DataWeave mappers.
  - Typical pattern:
    - `task` captures the raw HTTP error into `context.api`.
    - A `choice` routes on `context.api.ok == false`.
    - A `transform` builds `context.problem` with RFC 9457 fields derived from `context.api.status`, `context.api.body`, and `context.api.error`.
    - The journey then either:
      - `succeed`s with `outputVar: problem` to return a pure Problem Details document, or
      - `fail`s, using `errorCode` from `context.problem.type` and `reason` from `context.problem.detail` or `title`.
	- Mapping RFC 9457 to other error formats
	  - When a journey needs a non‑RFC error format for its external error responses, it MUST:
	    - Normalise errors into a canonical Problem Details object, and
	    - Map that Problem Details object into a single, journey‑specific error envelope (see `spec.errors.envelope`).
	  - All external error responses for a given journey MUST share the same top‑level error structure; a journey MUST NOT expose different error envelope shapes at runtime for different callers or error paths.
	- Reuse via shared mappers
	  - Journeys MAY still use shared DataWeave modules (`.dwl` files) for common transformations in general, but `spec.errors` is defined in terms of inline mappers in this version (see Q-002 in `docs/4-architecture/open-questions.md`).

## 19. Error configuration (spec.errors)
	
	The `spec.errors` block allows journeys to centralise, per journey, how they normalise and expose errors, building on the canonical RFC 9457 Problem Details model (see ADR‑0003 and Q-002 in `docs/4-architecture/open-questions.md`).
	
	```yaml
	spec:
	  errors:
	    canonicalFormat: rfc9457    # optional; defaults to rfc9457 when omitted
	    normalisers:
	      httpDefault:
	        mapper:
	          lang: dataweave
	          expr: |
	            // HTTP result → Problem Details
	            ...
	      ordersApi:
	        mapper:
	          lang: dataweave
	          expr: |
	            // Orders API error → Problem Details
	            ...
	    envelope:
	      format: problemDetails    # default when envelope is omitted
	      # When format: custom, define a mapper from Problem Details to the journey’s external error body:
	      # format: custom
	      # mapper:
	      #   lang: dataweave
	      #   expr: |
	      #     ...
	```
	
	Shape
	- `spec.errors` is optional; when omitted:
	  - The canonical internal error model for the journey is still RFC 9457 Problem Details.
	  - The externally visible error structure for the journey MUST be the Problem Details shape (or a compact `{code,reason}` view over it as defined by ADR‑0003).
	- `canonicalFormat`:
	  - When present, MUST be the string `rfc9457`.
	  - When omitted, it implicitly defaults to `rfc9457`; other values are reserved for future versions.
	- `normalisers`:
	  - Optional map of ids to mapper objects that convert low‑level error data (for example HTTP result objects) into Problem Details objects.
	  - Each mapper MUST declare `lang: dataweave` and an inline `expr`.
	- `envelope`:
	  - Optional single configuration that defines the external error envelope for the journey.
	  - `format` controls the envelope:
	    - When omitted or set to `problemDetails`, the externally visible error structure for the journey MUST be the Problem Details shape.
	    - When set to `custom`, the `envelope` block MUST include a `mapper` that transforms Problem Details objects into a single, stable error body structure for this journey.
	  - The DSL does not allow multiple envelopes per journey; there is at most one `envelope` block.
	
	Semantics
	- Canonical model:
	  - Engines and tooling MUST treat RFC 9457 Problem Details as the canonical internal error model for all journeys, regardless of whether `spec.errors` is present.
	  - All error handling logic (normalisation and envelope mapping) operates in terms of Problem Details objects.
	- Normalisation:
	  - `spec.errors.normalisers` provides per‑journey, inline mappers that journeys MAY reference explicitly from error‑producing operations (for example HTTP task `errorMapping`).
	  - Engines MUST NOT implicitly pick or apply a normaliser based solely on its presence in `spec.errors`; usage MUST be explicit in the journey spec (for example via an id reference from `errorMapping` or future, clearly defined configuration fields).
	- Envelope:
	  - For a given journey, the externally visible error structure MUST be uniform across all error paths:
	    - When `envelope` is omitted or `format: problemDetails`, all external error responses MUST use the Problem Details shape (with `JourneyOutcome.error` reflecting the canonical `{code,reason,...}` view).
	    - When `envelope.format: custom` is declared, the journey MUST use the configured inline mapper to transform Problem Details objects into the journey’s external error body; all external error responses for that journey MUST use this single structure.
	  - Engines MUST NOT vary the external error envelope for a journey at runtime based on caller identity, headers, or other request metadata.
	
	Validation
	- `canonicalFormat`, when present, MUST be the string `rfc9457`.
	- `normalisers` must be a map of ids to mapper objects with the same shape as `transform.mapper` entries (`lang`, inline `expr` only).
	- `envelope.format`, when present, MUST be either `problemDetails` or `custom`.
	- When `envelope.format` is `custom`, `envelope.mapper` MUST be present and must declare `lang: dataweave` and an inline `expr`.
	- Tools SHOULD flag references to unknown normaliser ids from HTTP tasks or other configuration as validation errors.

## 6. Example
```yaml
apiVersion: v1
kind: Journey
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
- Terminal success/failure are explicit via `succeed`/`fail`; tasks never auto‑terminate a journey.
- Enforcement for retries, circuit breakers, bulkheads, or authentication policies is not defined here; only configuration via resilience policies is specified.
- Feature 001 of the engine does not support timers (`wait`), external input (`webhook`), parallelism (`parallel`/`map`), or sub‑journeys; these states are reserved for Features 003/004 and SHOULD be rejected or treated as unsupported.
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
- Language: DataWeave 2.x (expressions authored inline via `expr`; v1 does not support DSL-level references to external `.dwl` modules; see ADR-0015 and Q-003).
- Binding: `context` variable is bound to the current journey context JSON value.
- Predicates: used in `choice` branches via `when.predicate`. The expression must evaluate to a boolean.
- Transforms: `transform` states use DataWeave to compute values written into `context` or into variables under `context.<resultVar>`, according to the semantics in the Transform state section.
- Determinism & safety: expressions must be pure (no I/O); the evaluator must enforce timeouts and resource limits.
- Validation and tooling: journey compilers and linters SHOULD validate DataWeave expressions (including `choice` predicates and mappers) against any declared schemas (`spec.context.schema`, `spec.input.schema`, step‑level `*.schema`, etc.) when available, and MUST fail fast at spec validation / compile time when an expression can be statically determined to:
  - Use invalid or non-existent paths, or
  - Produce a non‑boolean result where a predicate is required.

### 10.1 Reusable mappers (`spec.mappers` and `mapperRef`)

To avoid repeating the same DataWeave snippets across multiple states, journey definitions can define named mappers under `spec.mappers` and reference them via `mapperRef`.

```yaml
spec:
  mappers:
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
- `spec.mappers` is a map from mapper id to a mapper definition with the same shape as `transform.mapper` (`lang`, `expr`).
- Anywhere the DSL allows a `mapper` object (for example `transform.mapper`, HTTP `body.mapper`, cache `key.mapper`, `errorMapping.mapper`), authors MAY use `mapperRef: <id>` instead:
  - The referenced mapper MUST be defined under `spec.mappers.<id>`.
  - Conceptually, `mapperRef: foo` is equivalent to copying `spec.mappers.foo` inline at that location.
- A state MUST NOT mix `mapper` and `mapperRef` in the same location; this is a validation error.

Validation
- `spec.mappers` must be a map of ids to mapper objects; each mapper must declare `lang: dataweave` and an inline `expr`.
- `mapperRef` values must be non-empty strings that resolve to an existing entry in `spec.mappers`.

## 11. Schemas (optional)
- `spec.input.schema`: inline JSON Schema (2020-12) that validates the initial `context` provided at journey start.
- `spec.output.schema`: inline JSON Schema for the terminal output returned by `succeed` (or the overall `context` if `outputVar` is omitted).
- `spec.context.schema`: inline JSON Schema for the logical shape of `context` during journey execution (superset of fields that may appear over time).
- Exporter behaviour:
  - When `spec.input.schema` is present, the OpenAPI exporter uses it as the request body schema in the per-journey OAS.
  - When `spec.output.schema` is present, the exporter specialises `JourneyOutcome.output` to that schema in the per-journey OAS (by inlining or via an internal component).
  - `spec.context.schema` is primarily for tooling and validation; the exporter MAY expose it as an additional schema component, but it does not change the wire format of `JourneyStartRequest` or `JourneyOutcome`.

Example:

```yaml
spec:
  input:
    schema:
      type: object
      required: [orderId]
      properties:
        orderId: { type: string }
  output:
    schema:
      type: object
      required: [status]
      properties:
        status: { type: string }
  context:
    schema:
      type: object
      additionalProperties: true
```

Usage notes
- Use `spec.input.schema` for what callers must send at start.
- Use `spec.output.schema` for what successful journeys return as `JourneyOutcome.output`.
- Use `spec.context.schema` to describe the full, evolving shape of `context` (including internal fields like `remote`, `cachedUser`, `problem`), so linters and editors can validate `context.<path>` usage in DataWeave expressions.

### OpenAPI operation binding (operationRef)
- `operationRef`: resolves `<apiName>` in `spec.apis` and `<operationId>` in the referenced OAS.
- Server selection: the first OAS server is used by default; future features may allow server variables/overrides.
- Params mapping: `params.path/query/headers` provide values for OAS params by name. Missing required params is a validation error.
- Body mapping: if `body` is a string, it is sent as-is; if an object, it is JSON-encoded; if a `mapper` object with `lang: dataweave` and `expr` is provided, the mapper result becomes the JSON body.
- `accept` selects the response media type; default `application/json`.
- Cannot mix `operationRef` with raw `method`/`url` in the same task.

## 12. External-Input States (wait/webhook)

External-input states pause the journey instance and require a step submission to continue. Submissions are sent to `/journeys/{journeyId}/steps/{stepId}` where `stepId` equals the state id.

Bindings available to DataWeave expressions during step handling:
- `context`: the current journey context JSON object.
- `payload`: the submitted step input JSON (validated against the state’s `input.schema`, when present).

### 12.1 `wait` (manual/external input)
```yaml
type: wait
wait:
  channel: manual                       # manual input from authenticated client
  input:                                # required – schema for step payload
    schema: <JsonSchema>                # inline JSON Schema (2020-12)
  response:                             # optional – project extra fields into step response
    outputVar: <string>                 # context.<outputVar> object is merged into the top-level response
    schema: <JsonSchema>                # JSON Schema for the additional top-level fields
  apply:                                # optional – update context before branching
    mapper:
      lang: dataweave
      expr
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
- The request body is validated against `wait.input.schema` when present; invalid → 400 with schema errors.
- If `apply.mapper` is provided, it runs with `context` and `payload` and replaces `context` with the mapper result.
- Step responses:
  - After applying the `wait` state (including any `apply.mapper` and branching), the engine builds a `JourneyStatus` object to describe the updated journey state.
  - When `response.outputVar` is set and `context.<outputVar>` is an object, its properties are shallow-merged into the top level of the JSON response alongside the standard `JourneyStatus` fields.
  - If `context.<outputVar>` is absent or not an object, the response is a plain `JourneyStatus` without extra fields.
  - The following top-level properties are reserved and MUST NOT be overridden by projected fields: `journeyId`, `journeyName`, `phase`, `currentState`, `updatedAt`, `tags`, `attributes`, `_links`.
- The `on` array is evaluated in order; first predicate returning `true` selects `next`. If none match, `default` is used; if absent, the spec is invalid.
- If `timeoutSec` elapses without submission, transition to `onTimeout`.

Validation
- `wait.channel` must be `manual`; use `webhook` state for callback semantics.
- `wait.input.schema` is required.
- If `timeoutSec` is set, `onTimeout` is required.
- Either `on` (non-empty) or `default` must be present.
- When present:
  - `response.outputVar` must be a non-empty string matching the variable identifier pattern (`[A-Za-z_][A-Za-z0-9_]*`).
  - `response.schema` must be a JSON Schema object describing additional top-level fields.
  - Specs MUST NOT declare projected properties that collide with reserved `JourneyStatus` fields; tools SHOULD treat such collisions as validation errors when detectable.

### 12.2 `webhook` (callback input)
```yaml
type: webhook
webhook:
  input:                                 # required – schema for callback payload
    schema: <JsonSchema>                 # inline JSON Schema (2020-12)
  response:                              # optional – project extra fields into step response
    outputVar: <string>                  # context.<outputVar> object is merged into the top-level response
    schema: <JsonSchema>                 # JSON Schema for the additional top-level fields
  security:                               # optional – minimal guard
    kind: sharedSecretHeader
    header: X-Webhook-Secret
    secretRef: secret://webhook/<name>
  apply:
    mapper:
      lang: dataweave
      expr
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
- Step responses follow the same projection rules as `wait` when `response.outputVar` is configured: the engine returns a `JourneyStatus` body with additional top-level fields taken from `context.<outputVar>` when it is an object.

Validation
- `webhook.input.schema` is required.
- `response` (when present) follows the same rules as for `wait.response`: `outputVar` must be a valid variable name, `schema` a JSON Schema object, and projected properties MUST NOT collide with reserved `JourneyStatus` fields.

### 12.3 Export mapping (steps)
- For each external-input state, the exporter emits:
  - `POST /journeys/{journeyId}/steps/{stepId}` with request body schema = the state’s `input.schema`, when present.
  - `200` response schema:
    - When `response.schema` is absent: `JourneyStatus` as defined in `docs/3-reference/openapi/journeys.openapi.yaml`.
    - When `response.schema` is present: an `allOf` composition of `JourneyStatus` and the step-specific schema taken from `response.schema`, so that additional top-level fields are described explicitly.
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
- This section defines the configuration model only; enforcement belongs to the policy implementation in the engine and is out of scope for the DSL reference.

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
      }
  target:
    kind: context                 # context | var
    path: data.enriched           # used when kind == context; dot-path in context
  resultVar: enriched             # used when kind == var; name under context.resultVar
next: <stateId>
```

Semantics
- `mapper` evaluates with `context` bound to the current journey context and returns a JSON value.
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

Caches are modelled as named resources plus cache-focused task kinds. This section defines the configuration and wiring; concrete cache implementations and eviction behaviour belong to the engine.

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
- `join.strategy` must be one of `allOf`, `anyOf`, or `firstCompleted`; Feature 004 will define the exact engine semantics for `anyOf`/`firstCompleted` in more detail.
- `join.errorPolicy` must be one of `collectAll`, `failFast`, or `ignoreErrors` when present; it is advisory and primarily guides the engine and readers.
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
- `headersToContext`: for each `headerName: contextField` entry, when a start request is invoked (`POST /journeys/{journeyName}/start` for a `kind: Journey` journey definition, or `POST /apis/{apiName}` / `spec.route.path` for `kind: Api`), if the request has the header, its value is copied to `context.<contextField>`. Missing headers are ignored; requiredness should be expressed via JSON Schema on the journey `context`, not here.
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
  - Does not need to be read by the journey definition itself.
- It is valid to use both: bind a header into `context` and also pass it through, if you need both visibility and propagation.

Validation
- `httpBindings.start.headersToContext` and `httpBindings.steps.*.headersToContext` must map header names to non-empty context field names.
- `httpBindings.start.queryToContext` and `httpBindings.steps.*.queryToContext`, when present, must map query parameter names to non-empty context field names.
- `headersPassthrough` entries must provide `from` and `to` header names (non-empty strings).
- Step ids under `httpBindings.steps` must refer to external-input states (`wait`/`webhook`).

Notes
- This section defines the binding model; concrete enforcement and header sets are implemented in the engine/API layer.

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
          mode: required                  # optional; required (default) | optional
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
          anonymousSubjects:              # optional; subjects considered "anonymous"
            - "00000000-0000-0000-0000-000000000000"
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
  - `mode`: controls whether credentials are required for this endpoint:
    - `required` (default): a missing or invalid token MUST cause the request to be rejected (for example, 401/403); the journey instance does not start.
    - `optional`: a missing token is allowed and treated as anonymous; an invalid token MUST still cause rejection. When no token is present, `context.auth.jwt` remains unset and no subject is derived.
  - `issuer`, `audience`: expected issuer and audience(s).
  - `jwks`: where to obtain verification keys (JWKS URL or static key set).
  - `clockSkewSeconds`: allowed skew when validating `exp`/`nbf`.
  - `requiredClaims`: shape and constraints for specific claims (implementation-defined schema).
  - `anonymousSubjects`: optional list of subject values that should be treated as anonymous even when the token is otherwise valid (for example, an all-zero UUID used by some gateways). When `sub` matches one of these values, the engine MUST NOT derive a canonical owner (`attributes.subjectId`) from it.
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
- `journeyPolicyRef` (if set) applies to `POST /journeys/{journeyName}/start` and all step endpoints unless overridden.
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
  - `httpSecurity` enforces *who* can call; `httpBindings` controls *how* inbound metadata is made available to the journey instance and downstream calls.

Notes
- This section defines the configuration model only; enforcement belongs to the security implementation in the engine and is out of scope for the DSL reference.

## 19. Outbound HTTP Auth (httpClientAuth)

Outbound HTTP auth policies define how HTTP tasks authenticate *to* downstream services (for example, using static bearer tokens, OAuth2 client credentials, or mTLS client certificates). They are configured under `spec.policies.httpClientAuth` and referenced from HTTP `task` definitions.

### 19.1 Policy definitions

```yaml
spec:
  policies:
    httpClientAuth:
      default: backendDefault        # optional default policy id
      definitions:
        backendDefault:
          kind: oauth2ClientCredentials
          tokenEndpoint: https://auth.example.com/oauth2/token
          auth:
            method: clientSecretPost   # clientSecretPost | clientSecretBasic | tlsClientAuth
            clientId: orders-service
            clientSecretRef: secret://oauth/clients/orders-service
            # or, for mTLS client auth at the token endpoint:
            # clientCertRef: secret://certs/orders-service
          form:
            grant_type: client_credentials
            scope: orders.read orders.write
            audience: https://api.example.com
        staticToken:
          kind: bearerStatic
          tokenRef: secret://tokens/static-backend-token
        mtlsClient:
          kind: mtlsClient
          clientCertRef: secret://certs/backend-client
          # optional outbound trust anchors for this client
          trustAnchors:
            - pemRef: trust/roots/root-ca.pem
```

Kinds
- `bearerStatic` – static bearer token auth.
  - `tokenRef`: `secretRef` pointing to a secret that resolves to the bearer token value.
- `oauth2ClientCredentials` – OAuth2 client credentials flow.
  - `tokenEndpoint`: URL of the token endpoint.
  - `auth.method`: how the client authenticates to the token endpoint:
    - `clientSecretPost`: send `client_id` and `client_secret` in the form body.
    - `clientSecretBasic`: send `Authorization: Basic base64(client_id:client_secret)`.
    - `tlsClientAuth`: authenticate with mTLS using a client certificate.
  - `auth.clientId`: OAuth2 client id.
  - `auth.clientSecretRef`: `secretRef` for client secret (required for secret-based methods).
  - `auth.clientCertRef`: `secretRef` for client cert/key (required for `tlsClientAuth`).
  - `form`: x-www-form-urlencoded payload fields to send to the token endpoint; must include `grant_type: client_credentials` (either explicitly or implied by the engine).
- `mtlsClient` – outbound client certificate on data-plane calls.
  - `clientCertRef`: `secretRef` for the client certificate/key pair.
  - `trustAnchors`: optional list of `pemRef` entries describing trusted roots for outbound TLS.

Semantics
- Policy resolution:
  - `spec.policies.httpClientAuth.default` (when set) provides a default policy id for HTTP tasks that do not specify their own `auth.policyRef`.
  - `definitions` is a map from policy id to policy object; unknown `kind` values are invalid.
- Secret references:
  - All secret-bearing fields (`tokenRef`, `auth.clientSecretRef`, `auth.clientCertRef`) are opaque `secretRef` identifiers.
  - The DSL never exposes raw secret values; engines resolve `secretRef` against an implementation-defined secret store.
- OAuth2 token acquisition:
  - For policies with `kind: oauth2ClientCredentials`, the engine obtains an access token by calling `tokenEndpoint` with the configured `auth` method and `form` payload.
  - The engine MUST treat `grant_type` as `client_credentials`; other grant types are invalid in this version.
- Token caching and refresh:
  - Engines MUST cache access tokens per effective token request (at minimum: policy id, `tokenEndpoint`, `auth.method`, and `form` payload) and reuse them across journey instances until expiry.
  - Engines MUST determine token lifetime from standard token metadata when available (for example the `exp` claim for JWT access tokens and/or `expires_in` fields in token responses) and apply a small pre‑expiry skew when deciding whether a token is still valid. When lifetime cannot be determined, caching behaviour is implementation-defined but MUST NOT violate auth server contracts.
  - When a cached token is available and not expired (after applying skew), the engine MUST reuse it rather than calling the token endpoint again.
  - For data-plane HTTP calls that use a `kind: oauth2ClientCredentials` policy, when the downstream service returns `401 Unauthorized`, the engine MUST treat this as a potential token invalidation and:
    - Discard the cached token for that effective request.
    - Obtain a fresh access token once.
    - Retry the original HTTP request once with the new token.
    - If the retry still fails with `401`, surface the error to the journey (for example as an HTTP error in the task result) and emit telemetry for diagnosis; the engine MUST NOT perform further automatic retries for that call.
  - Implementations MAY provide configuration to disable the automatic `401` refresh/retry behaviour for specific policies or HTTP clients; such configuration is engine- or platform-level and not part of the DSL surface.

Validation
- `spec.policies.httpClientAuth.definitions` must be a map of ids to policy objects.
- Each policy must specify a supported `kind`.
- `bearerStatic` policies must set a non-empty `tokenRef`.
- `oauth2ClientCredentials` policies must set `tokenEndpoint`, `auth.method`, and `auth.clientId` and at least one of `auth.clientSecretRef` or `auth.clientCertRef` consistent with `auth.method`.
- `mtlsClient` policies must set `clientCertRef`.

Notes
- This section defines the configuration model only; token acquisition, secret storage, and caching live in the engine and secret store.

### 18.5 Mapping auth into journey context

After successful policy validation, the engine MUST populate one of the following views under `context.auth` so DataWeave expressions and `transform` states can use authentication data:

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

This keeps `context` as the canonical place for data that influences journey behaviour, while `httpSecurity` governs authentication and `httpBindings` governs how inbound metadata becomes available to the journey instance and downstream calls.

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
  - `when.predicate`: optional DataWeave predicate evaluated with `context` bound to the final journey context and `output`/`error` available as bindings if the engine supplies them.

Semantics (classification only)
- Outcomes do not affect execution: the journey still terminates on `succeed`/`fail` as usual.
- After a journey reaches a terminal phase, an engine MAY:
  - Evaluate outcomes in a deterministic order (for example, insertion order or lexicographic by id).
  - Select the first outcome whose `when.phase` matches and whose predicate evaluates to `true`.
  - Record the selected outcome id for telemetry or include it as an additional field (for example `outcomeId`) in `JourneyOutcome` without changing existing fields.
- If no outcome matches, the journey remains unclassified from the DSL’s perspective.

## 21. Metadata limits (MetadataLimits)

To keep metadata predictable and avoid unbounded growth, operators configure limits for
tags and attributes in the engine configuration via a dedicated configuration document:

```yaml
apiVersion: v1
kind: MetadataLimits
metadata:
  name: metadata-limits
spec:
  definitionTags:
    maxCount: 10

  instanceTags:
    maxCount: 16
    maxLength: 40

  attributes:
    maxKeys: 16
    maxKeyLength: 32
    maxValueLength: 256
```

Semantics
- `MetadataLimits` is part of the configuration surface for the engine; it is not referenced
  by individual journey definitions but governs validation and enforcement for tags and attributes.
- At startup, the engine MUST load `MetadataLimits` from a well-known location. When it is
  missing:
  - Implementations MAY fail fast with a clear error, or
  - Use documented built-in defaults equivalent to the example above, with a strong
    recommendation to make limits explicit for production.
- The limits apply as follows:
  - `definitionTags.maxCount`:
    - Maximum number of entries in `metadata.tags` per spec.
  - `instanceTags.maxCount`:
    - Maximum number of effective tags on a single journey instance (after combining
      `metadata.tags` and spec-derived tags).
  - `instanceTags.maxLength`:
    - Maximum length, in characters, of a single tag value.
  - `attributes.maxKeys`:
    - Maximum number of keys in `journey.attributes` for a single journey.
  - `attributes.maxKeyLength`:
    - Maximum length, in characters, of an attribute key.
  - `attributes.maxValueLength`:
    - Maximum length, in characters, of an attribute value.

Validation
- Tools and the engine SHOULD treat violations of `MetadataLimits` as validation errors when
  possible (for example, during spec compilation or static analysis).
- During execution, attempts to add tags or attributes that would exceed the configured
  limits SHOULD cause the operation to fail fast with a clear error; behaviour is
  implementation-defined but MUST be documented.

Validation
- Outcome ids must be unique strings.
- `when.phase` must be either `Succeeded` or `Failed`.
- If `when.predicate` is present, it must declare `lang: dataweave` and the expression must return boolean.

Usage notes
- Use outcomes to give names to common scenarios such as “SucceededWithCacheHit”, “SucceededWithoutCache”, “FailedUpstream”, “RejectedByPolicy”.
- Keep outcome predicates simple and stable; they should refer to durable semantics (for example `error.code`) rather than transient implementation details.

## 22. HTTP Cookies (spec.cookies)

The cookies configuration allows journey definitions and API endpoints to use HTTP cookies in a controlled way, aligned with standard HTTP cookie semantics (RFC 6265 or successors):
- Maintain a per‑run cookie jar populated from downstream `Set-Cookie` responses.
- Attach matching cookies automatically to outbound HTTP tasks.
- Optionally return selected cookies to the client as `Set-Cookie` on successful terminal responses.

The cookie jar is opt‑in per spec and strictly scoped to the lifetime of a single journey instance or API invocation.

### 22.1 Top-level configuration (spec.cookies)

```yaml
spec:
  cookies:
    jar:
      domains:
        - pattern: "api.example.com"   # exact host only
        - pattern: ".example.com"      # subdomains of example.com; not example.com itself

    returnToClient:
      mode: none | allFromAllowedDomains | filtered
      include:
        names: ["session", "csrfToken"]
        namePatterns:
          - "^x-app-"                  # Java regex on cookie name
```

Shape
- `spec.cookies` is optional. When omitted, cookie handling behaves exactly as in earlier sections: HTTP headers and bodies follow the existing rules, and there is no cookie jar.
- `spec.cookies.jar` (optional):
  - `domains`: array of objects with:
    - `pattern: string` – required, non‑empty.
      - Exact host pattern (no leading dot), for example `api.example.com`, matches only that host.
      - Subdomain pattern (leading dot), for example `.example.com`, matches hosts that end with `.example.com` and are not exactly `example.com` (for example `api.example.com`, `foo.bar.example.com`).
  - The jar is considered enabled for the spec when `spec.cookies.jar` is present, even if `domains` is empty (in that case, no cookies are stored or attached).
- `spec.cookies.returnToClient` (optional):
  - `mode`:
    - `none` – do not emit any `Set-Cookie` headers to the client.
    - `allFromAllowedDomains` – emit `Set-Cookie` for all cookies in the jar whose domains match `jar.domains`.
    - `filtered` – emit `Set-Cookie` only for cookies selected via `include`.
  - `include` (optional; used only when `mode: filtered`):
    - `names?: string[]` – explicit allow‑list of cookie names.
    - `namePatterns?: string[]` – allow‑list of Java‑style regular expressions on cookie names.
    - Selection is by union: a cookie is included if its name is in `names` or matches at least one regex in `namePatterns`. Either list may be empty.

Semantics
- The cookie jar is per run:
  - For `kind: Journey`, one jar per journey instance.
  - For `kind: Api`, one jar per API invocation.
  - Jars are created at run start and destroyed at the terminal state.
- When `spec.cookies` is omitted:
  - No jar is created.
  - HTTP behaviour remains defined solely by other sections (`task`, `httpBindings`, `httpSecurity`, etc.).

Validation
- `spec.cookies.jar.domains[*].pattern` must be non‑empty strings.
- `spec.cookies.returnToClient.mode`, when present, must be one of `none`, `allFromAllowedDomains`, `filtered`.
- `spec.cookies.returnToClient.include.names`, when present, must be arrays of strings.
- `spec.cookies.returnToClient.include.namePatterns`, when present, must be arrays of strings that compile as Java‑style regular expressions.

### 22.2 Jar population from downstream Set-Cookie

For specs with `spec.cookies.jar` present, the engine maintains a per‑run cookie jar that is populated from downstream HTTP task responses only.

Sources
- HTTP task responses (`kind: httpCall`, non‑notify):
  - For each response, the engine:
    - Parses all `Set-Cookie` headers.
    - Computes the effective `domain` and `path` using HTTP cookie rules (RFC 6265 style):
      - If the cookie declares a `Domain` attribute, use that value.
      - Otherwise, use the request host as the domain.
      - If the cookie declares a `Path` attribute, use that; otherwise derive a default path from the request path (for example, the containing directory or `/`).
    - Applies the domain allow‑list:
      - If the effective domain does not match any `jar.domains[*].pattern`, the cookie is discarded:
        - It is not stored in the jar.
        - It is not exposed as `Set-Cookie` in the structured HTTP result object.
      - If the effective domain matches a pattern, the cookie is applied to the jar.
  - Cookie keys:
    - The jar stores cookies keyed by `(domain, path, name)` with last‑writer‑wins semantics.
    - When a downstream cookie denotes deletion (for example via `Max-Age=0` or an `Expires` value in the past), the jar removes any existing cookie with the same `(domain, path, name)` and records that a deletion has occurred so that response shaping can emit a deleting `Set-Cookie` if configured.

Notify mode
- HTTP tasks with `kind: httpCall` and `mode: notify` (see §5.1 and ADR‑0005) do not populate the cookie jar:
  - Responses to `notify` calls are ignored for jar purposes.
  - Jar cookies may still be attached to `notify` outbound requests (see §22.3).

Inbound cookies
- The jar does not ingest inbound `Cookie` headers from start or step requests:
  - If journeys need to work with inbound cookies, they must use existing mechanisms (`httpBindings.start.headersToContext`, `httpBindings.steps.*.headersToContext`, plus `transform` states) and, if desired, construct `Cookie` headers explicitly for outbound calls.

Validation
- Cookie parsing and domain/path derivation are implementation details but MUST follow RFC 6265 style HTTP cookie rules (or successors).
- Specs that configure `spec.cookies.jar` must still validate correctly even if no downstream `Set-Cookie` headers are present at runtime.

### 22.3 Attaching jar cookies to outbound HTTP tasks

HTTP tasks may opt into or out of cookie jar attachment via a per‑task `cookies` block:

```yaml
states:
  callBackend:
    type: task
    task:
      kind: httpCall
      operationRef: backend.getOrder
      cookies:
        useJar: false            # optional; default true
      # headers:
      #   Cookie: "${context.explicitCookie}"  # explicit header wins over jar
```

Shape
- `task.cookies` is allowed only when `task.kind: httpCall`.
- `task.cookies.useJar?: boolean` – when present:
  - `true`: enable jar attachment for this task (default when `spec.cookies.jar` exists and no explicit `Cookie` header overrides).
  - `false`: disable jar attachment for this task.

Semantics
- For any HTTP task where:
  - `spec.cookies.jar` is present, and
  - `task.cookies.useJar` is omitted or `true`, and
  - No explicit `Cookie` header is configured for the request (neither in `task.headers.Cookie` nor in `spec.defaults.http.headers.Cookie`),
  the engine:
  - Resolves the outbound request URL (after applying `operationRef` or `url`).
  - Computes the request `host` and `path`.
  - Selects all cookies in the jar whose:
    - Domain matches the request host according to the patterns in `jar.domains`:
      - Exact host pattern matches only that host.
      - Subdomain pattern matches hosts that end in the pattern and are not the bare parent domain.
    - Path is a prefix of the request path.
    - `Secure` attribute, when present, is honoured (only attached over HTTPS).
  - Synthesises a `Cookie` header of the form:
    - `Cookie: name1=value1; name2=value2; ...`
- For tasks where `task.cookies.useJar: false`:
  - The jar is not consulted; no jar cookies are attached.
- Explicit `Cookie` headers:
  - If a `Cookie` header is configured via `task.headers.Cookie` or `spec.defaults.http.headers.Cookie`, that value is used as‑is and the jar is not applied for that request, regardless of `useJar`.
  - There is no automatic merging of jar cookies with explicit `Cookie` values; authors who need such merging must construct the header explicitly.

Notify mode
- For `mode: notify` HTTP tasks:
  - Attachment rules are the same as above: if `useJar` is enabled and no explicit `Cookie` header is set, jar cookies may be attached to the outbound request.
  - Responses to `notify` calls do not mutate the jar.

Validation
- `task.cookies` is invalid on non‑HTTP tasks and MUST be rejected at validation time.
- `task.cookies.useJar`, when present, must be boolean.

### 22.4 Returning cookies to the client on success (returnToClient)

When `spec.cookies.returnToClient` is present, the engine may emit `Set-Cookie` headers towards the client on successful terminal responses.

Scope
- `returnToClient` applies only when a run terminates in `succeed`:
  - For `kind: Journey`, when producing the final HTTP response that wraps the `JourneyOutcome`.
  - For `kind: Api`, when producing the synchronous API response.
- When a run terminates in `fail`, the cookie jar still influences downstream calls during execution, but `returnToClient` does not emit `Set-Cookie` headers.

Selection
- The engine considers cookies in the jar whose effective domains match the allow‑list in `spec.cookies.jar.domains`.
- Depending on `mode`:
  - `none`:
    - No `Set-Cookie` headers are emitted from the jar.
  - `allFromAllowedDomains`:
    - All cookies in the jar for allowed domains are emitted as `Set-Cookie` headers.
    - When the jar has recorded that a cookie was deleted (see §22.2), the engine emits a deletion `Set-Cookie` towards the client (for example with `Max-Age=0` or a past `Expires`) so that the client removes it.
  - `filtered`:
    - Only cookies whose names satisfy at least one of:
      - `name` is in `include.names`, or
      - `name` matches at least one regex in `include.namePatterns` (matched using Java‑style regular expressions),
      are emitted as `Set-Cookie` headers.
    - Deletions are emitted only for cookies that would otherwise be selected by the filter.

Domain behaviour
- It is valid for `Set-Cookie` emitted to the client to target any domain allowed by `spec.cookies.jar.domains`, not just the host that the client used to call the engine, subject to RFC 6265 style HTTP cookie rules.

Validation
- When `spec.cookies.returnToClient` is present but `spec.cookies.jar` is absent, implementations SHOULD treat this as a configuration error (there is no jar to source cookies from).
- Invalid regex patterns in `namePatterns` MUST be reported as spec validation errors.

## 23. Journey Access Binding

This section summarises the minimal, normative rules for journey access binding. For background and rationale, see
ADR-0014 (`docs/6-decisions/ADR-0014-journey-access-binding-and-session-semantics.md`).

Rules:
- The DSL does **not** define a dedicated access-binding block (for example `spec.access`, `spec.accessBinding`, or a
  first-class `participants` structure). No such blocks are allowed in the generic DSL surface.
- Journeys that need access control or subject/participant binding MUST model the required identity and attributes in
  ordinary `context` fields (for example `context.identity.*`, `context.participants.*`) and/or metadata, using the
  existing DSL constructs (`transform`, `choice`, `wait`, `webhook`, etc.).
- When external identity is present (for example from a JWT or client certificate), journey definitions that rely on it
  SHOULD project it into `context` explicitly (for example via `httpBindings.start.headersToContext` followed by a
  `transform`), so that journey logic can make clear, data-driven decisions.
- For any external-input interaction (journey start, `wait` state, `webhook` state, or `POST /steps/{stepId}` call),
  journey definitions that care about access control MUST implement their own checks over `context` and/or request
  data (for example via predicates, guards, or preceding `choice` states). The engine does not infer access rules from
  any special DSL fields.
- `journeyId` is a resume token only. Specs and implementations MUST NOT treat possession of a `journeyId` by itself as
  sufficient authorisation to read or mutate a journey; any additional access requirements MUST be modelled and enforced
  by journey logic as described above.
