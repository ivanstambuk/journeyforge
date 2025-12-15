# JourneyForge DSL – Reference

Status: Draft | Last updated: 2025-11-23

This document is **normative** for the JourneyForge journey DSL (for `kind: Journey` and `kind: Api`) supported by Feature 001. It defines language surface and semantics only; **engine implementation status is non-normative and tracked in feature specs** (for example `docs/4-architecture/features/001/spec.md`).

This document normatively defines the JourneyForge journey DSL (for `kind: Journey` and `kind: Api`) supported by Feature 001. It aims to be explicit about behaviour and limitations so we can refine before implementation.

## Contents

- [1. Overview](#dsl-1-overview)
- [2. Top-level shape](#dsl-2-top-level-shape)
- [3. Context and paths](#dsl-3-context-and-paths)
- [4. Interpolation](#dsl-4-interpolation)
- [5. States](#dsl-5-states)
- [6. Example](#dsl-6-example)
- [7. Limitations (explicit non-capabilities)](#dsl-7-limitations)
- [8. Naming & terminology](#dsl-8-naming-and-terminology)
- [9. Forward-compatibility notes](#dsl-9-forward-compatibility-notes)
- [10. DataWeave – Expressions & Mappers](#dsl-10-dataweave)
- [11. Schemas (optional)](#dsl-11-schemas)
- [12. External-Input States (wait/webhook)](#dsl-12-external-input-states)
- [13. Resilience Policies (HTTP)](#dsl-13-resilience-policies)
- [14. Transform State (DataWeave)](#dsl-14-transform-state)
- [15. Cache Resources & Operations](#dsl-15-cache)
- [16. Parallel State (branches with join)](#dsl-16-parallel-state)
- [17. Inbound Bindings (spec.bindings)](#dsl-17-bindings)
- [18. Inbound Auth (task plugins)](#dsl-18-inbound-auth)
- [19. Outbound HTTP Auth (httpClientAuth)](#dsl-19-outbound-http-auth)
- [20. Named Outcomes (spec.outcomes)](#dsl-20-named-outcomes)
- [21. Metadata limits (MetadataLimits)](#dsl-21-metadata-limits)
- [22. HTTP Cookies (spec.cookies)](#dsl-22-http-cookies)
- [23. Journey Access Binding](#dsl-23-journey-access-binding)
- [24. Error configuration (spec.errors)](#dsl-24-error-configuration)

<a id="dsl-1-overview"></a>
## 1. Overview
- Purpose: describe small, synchronous API journeys (defined by `kind: Journey`) in a human-friendly format (YAML 1.2 subset) with a precise JSON model.
- Files: `.journey.yaml` / `.journey.yml` or `.journey.json`.
- States: `task` (HTTP, event publish, schedule), `choice` (branch), `transform` (DataWeave mapping), `parallel` (branches with join), `wait` (external input), `webhook` (callback input), `timer` (durable in-journey delay, journeys only), `subjourney` (local subflow call), `succeed` (terminal), `fail` (terminal).
- Expressions & transforms: expressions are authored via `lang`/`expr` pairs and evaluated by pluggable expression engines (see section 10 and ADR‑0027). DataWeave 2.x (`lang: dataweave`) is one supported engine; additional engines (for example JSONata, JOLT, jq) MAY be supported via the expression engine plugin model when configured.
- Execution: starts at `spec.start`, mutates a JSON `context`, and terminates on `succeed`/`fail`, a global execution timeout (`spec.execution.maxDurationSec`), or an engine execution error. When `spec.compensation` is present, a separate compensation journey MAY run after non-successful termination and, optionally, after selected successful terminations.

### 1.1 State types and surface

The DSL surface defines the following state types and configuration blocks. All of them belong to the same language; the engine may support them in stages, but the spec treats them as a single coherent DSL.

| State type / construct                    | Description                                      | Notes in spec                                          |
|------------------------------------------|--------------------------------------------------|--------------------------------------------------------|
| `task` (`kind: httpCall:v1`)             | HTTP call with structured result recording       | Fully specified, including `operationRef` and errors   |
| `task` (`kind: kafkaPublish:v1`)         | Publish events to a Kafka topic                  | Fully specified for Kafka in this version              |
| `task` (`kind: schedule:v1`)             | Create/update schedule bindings for future runs  | Fully specified for `kind: Journey`                    |
| `task` (`kind: jwtValidate:v1`)          | JWT validation and claim projection              | Core task plugin; see section 18.6                     |
| `task` (`kind: mtlsValidate:v1`)         | mTLS client certificate validation               | Core task plugin; see section 18.7                     |
| `task` (`kind: apiKeyValidate:v1`)       | API key validation and key-id projection         | Core task plugin; see section 18.8                     |
| `choice`                                 | Predicate-based, data-driven branching on context | Fully specified; predicates use pluggable expression engines via `lang` |
| `succeed`                                | Terminal success                                 | Fully specified                                        |
| `fail`                                   | Terminal failure with error code/reason          | Fully specified; aligned with RFC 9457 Problem Details |
| `transform`                              | Expression-based mapping into context/vars       | Fully specified; uses expression engines via `lang`    |
| `wait`                                   | Manual/external input                            | DSL shape + REST step surface defined                  |
| `webhook`                                | Callback input                                   | DSL shape + callback surface defined                   |
| `timer`                                  | Durable time-based delay within a journey        | Journeys only; non-interactive, see section 12.3       |
| `subjourney`                             | Local subjourney call within one spec            | Fully specified for v1; local, synchronous only (ADR-0020) |
| `parallel`                               | Parallel branches with join                      | DSL shape + join contract defined                      |
| `task` (`kind: cache:v1`)                | Cache lookup/store against a global cache   | DSL shape defined; cache semantics described in section 15 |
| `task` (`kind: xmlDecode:v1`)            | Decode XML string in context into JSON      | DSL shape defined; semantics described in section 5.3      |
| Policies (`httpResilience`, `httpClientAuth`) | Resiliency/auth configuration (outbound)     | Configuration surface defined; policy semantics described in sections 13 and 19 |

<a id="dsl-2-top-level-shape"></a>
## 2. Top-level shape

### 2.1 Inline JSON Schemas

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

### 2.2 API catalog (OpenAPI binding)
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
  subjourneys:                  # optional: map<string, SubjourneyDefinition>
    <subjourneyId>:
      start: <stateId>          # required for each subjourney
      states:                   # required: map<string, State> for the subjourney
        <stateId>: <State>
  bindings:                     # optional: map<string, BindingConfig>
    http: <HttpBindingConfig>   # HTTP inbound binding; see section 17
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
- `spec.bindings`, when present:
  - MUST be a mapping from binding ids to binding configuration objects.
  - MAY contain an `http` entry describing the HTTP inbound binding for the definition (see section 17).
  - MUST NOT contain entries for bindings that are not supported by the current DSL version or engine implementation.
- `spec.subjourneys`, when present:
  - Defines local subjourney graphs that are only visible within the same spec.
  - Keys under `spec.subjourneys` are local identifiers (for example `collectShipping`, `riskAndKyc`) and MUST be unique within that map.
  - Each subjourney MUST declare its own `start` and `states` map, using the same state surface as top-level `spec.states` and obeying the same `kind` constraints (for example, no `wait`/`webhook`/`timer` in `kind: Api`).
  - States within a subjourney MUST NOT be referenced directly from top-level states via `next`; they are only entered via `type: subjourney` states (see section 5.8).

#### 2.2.1 Journeys (`kind: Journey`)

`kind: Journey` specs are “journey definitions”:
- Initiated via `/api/v1/journeys/{journeyName}/start` (see `docs/3-reference/openapi/journeys.openapi.yaml` and the OpenAPI export guideline).
- Identified by a `journeyId` and observed via `/journeys/{journeyId}` and `/journeys/{journeyId}/result`.
- May use all state types, including long‑lived external input (`wait`, `webhook`).
 - For the start call, the HTTP request body is deserialised as JSON and used directly as the initial `context` object for the journey (subject to any input schema the journey definition declares).
 - Start semantics are controlled by `spec.lifecycle.startMode` (see section 2.7):
   - When `startMode: async`:
     - `POST /api/v1/journeys/{journeyName}/start` MUST respond with `202 Accepted` and a `JourneyStartResponse` envelope containing at least `journeyId`, `journeyName`, and a status URL.
     - The engine MAY start executing the journey instance immediately or later; callers observe progress via `GET /journeys/{journeyId}` and `GET /journeys/{journeyId}/result`.
   - When `startMode: sync` (default when omitted):
     - `POST /api/v1/journeys/{journeyName}/start` executes the journey instance synchronously from `spec.start` until either:
       - a terminal state (`succeed` or `fail`), or
       - the first external‑input state (`wait` or `webhook`) is reached.
     - When the run reaches a terminal state within the applicable execution budget (`spec.execution.maxDurationSec` and platform limits), the endpoint MUST respond with HTTP 200 and a `JourneyOutcome` document whose `phase` reflects success (`SUCCEEDED`) or failure (`FAILED`).
     - When the run pauses at the first external‑input state within the execution budget, the endpoint MUST respond with HTTP 200 and a `JourneyStatus` document describing the current journey status; engines and exporters MAY extend this with additional top‑level fields for that step when the journey definition declares a step‑specific response schema.
     - Engines SHOULD treat journeys whose control flow reaches a `timer` state before any terminal or external‑input state as a poor fit for synchronous start and SHOULD emit a validation warning or error when such journeys declare `startMode: sync`; authors SHOULD prefer `startMode: async` or refactor such flows.

HTTP status and journey outcome
- For journeys, HTTP status codes on the Journeys API surface (including `/start`, `/journeys/{journeyId}`, and `/journeys/{journeyId}/result`) indicate the success or failure of the *API call* itself, not the business outcome of the journey.
- `GET /api/v1/journeys/{journeyId}/result` MUST return HTTP 200 when it successfully returns a `JourneyOutcome` document for a terminal journey, even when `JourneyOutcome.phase = "FAILED"`.
- For synchronous journeys (`startMode: sync`), `POST /api/v1/journeys/{journeyName}/start` likewise uses HTTP 200 for both successful and failed terminal outcomes; clients MUST inspect `JourneyOutcome.phase` (and `error`) to distinguish success from failure.
- Journeys do not support a per-journey HTTP status mapping block (there is no `apiResponses`-equivalent for the Journeys API endpoints). Tools and clients SHOULD treat non-200 responses from the Journeys API as transport/protocol errors (for example invalid requests, conflicts, or platform-level authentication/authorisation failures), not as journey-defined business outcomes.

The semantics of `succeed`/`fail` for journeys are defined in sections 5.4–5.7, which describe the `JourneyOutcome` envelope.

#### 2.2.2 API endpoints (`kind: Api`)

API endpoints reuse the same state machine model but are exposed as synchronous, stateless HTTP endpoints:
- HTTP surface:
  - Canonical base path: `/api/v1/apis/{apiName}`.
  - The actual path and method are controlled by the HTTP binding (`spec.bindings.http.route`, see below).
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
  bindings:                     # optional; controls inbound bindings such as HTTP
    http:
      route:                    # optional; controls HTTP surface
        path: <string>          # e.g. /apis/get-user-public; defaults to /apis/{metadata.name}
        method: <string>        # e.g. POST; the initial version supports POST only
  input:                        # optional but strongly recommended
    schema: <JsonSchema>        # inline JSON Schema (2020-12) for request body
  output:                       # optional but strongly recommended
    schema: <JsonSchema>        # inline JSON Schema (2020-12) for successful response body
  bindings:
    http:
      apiResponses:             # optional; HTTP status mapping rules for kind: Api only
        rules:
          - when:
              phase: FAILED
              errorType: <string>   # optional; Problem.type to match
              predicate:            # optional; expression predicate over context + error
                lang: <engineId>    # e.g. dataweave
                expr: <expr>
            status: <integer>       # literal HTTP status code
            # or:
            # statusExpr:
            #   lang: <engineId>
            #   expr: <expr>        # expression that evaluates to an HTTP status code
        default:                    # optional; per-phase fallbacks when no rule matches
          SUCCEEDED: 200            # optional; defaults to 200 when omitted
          FAILED: fromProblemStatus # optional; defaults to Problem.status or 500 when omitted
  start: <stateId>
  states:
    <stateId>: <State>
```

Constraints for `kind: Api`
- `spec.bindings.http.route.path`:
  - If omitted, the canonical path is `/apis/{metadata.name}` underneath the common base `/api/v1`.
  - MUST be an absolute path starting with `/` and without a host.
- `spec.bindings.http.route.method`:
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
  - `spec.bindings.http.start` MAY further project headers into `context` and/or provide outbound header defaults.
- Successful responses:
  - Reaching `succeed` terminates execution and produces a 2xx HTTP response.
  - The response body is taken from `context.<outputVar>` when `outputVar` is set on the `succeed` state; otherwise the full `context` is used.
- Error responses:
  - Reaching `fail` terminates execution and produces a non‑2xx HTTP response.
  - `errorCode` and `reason` follow the RFC 9457 alignment rules in section 5.7.
  - The structure of the error payload for a given journey or API MUST follow the journey’s error configuration:
    - When `spec.errors.envelope` is omitted or uses `format: problemDetails`, the error body MUST use the Problem Details shape.
    - When `spec.errors.envelope.format: custom` is present, the error body MUST be produced by the journey’s configured envelope mapper.
  - HTTP status code selection for `kind: Api` is controlled by `spec.bindings.http.apiResponses` when present:
    - Engines MUST evaluate `spec.bindings.http.apiResponses.rules` in order; the first rule whose `when` clause matches the terminal phase and (for failures) the canonical Problem object determines the HTTP status via `status` or `statusExpr`.
    - When `spec.bindings.http.apiResponses` is omitted or when no rule matches:
      - For `phase = SUCCEEDED`, the engine MUST use HTTP 200.
      - For `phase = FAILED`, the engine MUST use the Problem `status` field when present and valid, or 500 when absent.
  - The error envelope itself remains governed by `spec.errors.envelope` and MUST be uniform for a given journey or API; HTTP status mapping (`spec.bindings.http.apiResponses`) MUST NOT change the error body shape, only the HTTP status code.

### 2.3 Defaults (spec.defaults)

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
  - When set, and when a `task` with `kind: httpCall:v1` omits `timeoutMs`, engines SHOULD use this value instead of the hard-coded default (10 000 ms).
  - If both the task-level `timeoutMs` and `spec.defaults.http.timeoutMs` are omitted, the default remains 10 000 ms as described in the HTTP task section.
- `spec.defaults.http.headers`:
  - For each `task` with `kind: httpCall:v1`, the effective headers are computed as:
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

### 2.3.1 Platform configuration (spec.platform.config)

Journey and API definitions may declare a per-definition configuration contract under `spec.platform.config`. At runtime, the platform injects concrete values for these keys into the read-only `platform.config` object available to expressions.

```yaml
spec:
  platform:
    config:
      publicBaseUrl:
        type: string
        required: true
        description: "Base URL for user-facing links"
      emailFrom:
        type: string
        required: true
        description: "From address for notification emails"
      risk:
        type: object
        required: false
        description: "Environment-specific risk settings"
```

Semantics
- `spec.platform.config` is optional.
- For each key `k` under `spec.platform.config`, engines MUST bind a corresponding runtime value at `platform.config.k` for that journey or API in any environment where it is enabled.
- `required: true`:
  - The platform MUST provide a value for `k` before the journey or API can run in a given environment.
  - If a required key has no value in the current environment, the journey/API definition MUST be rejected at deployment/initialisation time, or the run MUST fail fast before executing any states.
- `required: false`:
  - The value MAY be omitted; expressions using `platform.config.k` MUST handle absence (for example via `default`).
- `type`:
  - Allowed values are `string`, `integer`, `number`, `boolean`, and `object`.
  - Engines and tools SHOULD validate that the effective runtime value for `platform.config.k` conforms to the declared type; mismatches are a configuration error.
  - For `type: object`, the DSL does not impose a nested schema in this version; authors treat the value as a generic JSON object.
- `description`:
  - Optional free-text description for operators and tooling; engines MAY surface it in generated documentation or UIs but it has no behavioural effect.
- Engines MUST NOT inject undeclared keys into `platform.config`, and tools SHOULD warn when specs reference `platform.config.*` keys that are not declared under `spec.platform.config`.

Usage examples
- Build a verification link using an environment-specific base URL:

  ```yaml
  expr: |
    platform.config.publicBaseUrl ++
      "/verify?token=" ++ context.verification.token
  ```

- Use a configured risk threshold with a default:

  ```yaml
  expr: |
    (context.riskScore default 0.0) >=
      (platform.config.riskThreshold default 0.8)
  ```

Guidance
- `platform.environment` and `platform.journey.*` are primarily for diagnostics and simple branching (for example enabling debug paths outside production).
- `platform.config.*` is the preferred surface for per-journey configuration knobs that vary by environment (base URLs, thresholds, feature flags, etc.); authors SHOULD prefer explicit `platform.config` keys over inspecting `platform.environment` directly for such behaviour.

### 2.4 Execution deadlines (spec.execution)

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
    - `kind: Api`: the HTTP request is accepted by the API endpoint (for example `/api/v1/apis/{apiName}` or `spec.bindings.http.route.path` when present).
  - Engine implementations MUST treat reaching this deadline as a failure even if the state machine has not yet reached a terminal `succeed`/`fail` state.
- Interaction with per-state timeouts:
  - For blocking operations that already have a timeout (`httpCall.timeoutMs`, `wait.timeoutSec`, `webhook.timeoutSec`), the engine SHOULD clamp the effective timeout to the remaining global budget.
  - Conceptually, the effective timeout is `min(configuredTimeout, remainingBudget)`; when the remaining budget is ≤ 0, the operation SHOULD NOT start and the run MUST be treated as timed out.
  - When no per-state timeout is configured, the engine MAY still interrupt long-running operations when the global deadline expires, or detect the timeout immediately after the operation completes.
  - Timeout outcome:
  - When the global deadline is reached, the engine MUST stop scheduling new states and complete the run as a failure using `spec.execution.onTimeout`.
  - For `kind: Journey`:
    - The resulting `JourneyOutcome` has `phase = FAILED` and `error` populated from `onTimeout.errorCode` and `onTimeout.reason` (following the Problem Details alignment rules in section 5.7).
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

### 2.5 Global compensation (spec.compensation)

Some journeys and APIs need a global “compensation journey” that runs when the main execution does not succeed, to undo or mitigate side effects (for example, HTTP mutations, database writes, or emitted events). In some cases, authors also want to run compensation‑style cleanup for selected successful outcomes (for example partial successes) while keeping `JourneyOutcome.phase = SUCCEEDED`. The `spec.compensation` block allows authors to attach such a compensation path to a journey definition in a declarative, opt‑in way.

At a high level:
- The main journey executes as usual from `spec.start` until it reaches `succeed`/`fail`, hits a global execution timeout (`spec.execution.maxDurationSec`), or is cancelled.
- When the main run terminates in any non‑success state (fail, timeout, cancel, engine execution error), and `spec.compensation` is present, the engine starts a separate compensation journey using the embedded compensation state machine.
- Optionally, authors can declare that compensation SHOULD ALSO run for certain successful outcomes (for example, when the final output indicates partial success). In those cases the main journey still terminates with `JourneyOutcome.phase = SUCCEEDED`, and compensation runs as a follow‑up journey that can inspect the final `context` and `output`.

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
    alsoFor:                     # optional; success-only triggers for compensation
      - when:
          predicate:             # optional; expression, evaluated after a successful run
            lang: <engineId>     # e.g. dataweave
            expr: |
              output.overallStatus == "PARTIALLY_CONFIRMED"
    states:                      # required; map<string, State> (same shapes as top-level)
      rollback:
        type: task
        task:
          kind: httpCall:v1
          operationRef: billing.cancelCharge
          resultVar: cancelResult
        next: undo_inventory

      undo_inventory:
        type: task
        task:
          kind: httpCall:v1
          operationRef: inventory.releaseReservation
          resultVar: inventoryResult
        next: notify

      notify:
        type: task
        task:
          kind: kafkaPublish:v1
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
  - When present, compensation is always triggered when the main run terminates with a non‑success outcome:
    - It reaches a terminal `fail` state.
    - It hits the global execution deadline and terminates using `spec.execution.onTimeout`.
    - It is cancelled via an engine/admin API.
    - It ends due to an internal engine error.
  - Successful runs (terminal `succeed` without error) MAY ALSO trigger compensation when `alsoFor` is declared:
    - After the main run terminates successfully and `JourneyOutcome.phase = SUCCEEDED`, the engine evaluates each entry in `alsoFor` in a deterministic order (for example, insertion order).
    - For each `alsoFor` rule:
      - If `when.predicate` is present, the engine evaluates it with the final journey context and bindings such as `output` available; if it returns `true`, the rule matches.
      - If `when.predicate` is omitted, the rule matches unconditionally for successful runs.
    - If at least one rule matches, the engine triggers compensation exactly once for that run (regardless of how many rules match), using the same `mode` semantics as for non‑success outcomes.
    - If no rules match, no compensation is performed for that successful run.
- Compensation journey:
  - The `compensation.states` map defines a separate state machine, using the same state types and configuration shapes as the top-level journey definition (`task`, `choice`, `transform`, `parallel`, `wait`, `webhook`, `succeed`, `fail`, etc.), subject to the same `kind: Journey` / `kind: Api` constraints.
  - `compensation.start` identifies the first state in this map.
- Compensation runs with its own control flow and may itself succeed or fail; these outcomes are not visible to the main caller but SHOULD be logged and traced by the engine.
- Context and bindings:
  - When starting the compensation journey, the engine MUST:
    - Provide `context` as a deep copy of the main journey’s context at the moment of termination.
    - Provide an additional read‑only binding `outcome` to all expressions in the compensation states.
  - The `outcome` object has the conceptual shape:
    ```json
    {
      "phase": "SUCCEEDED or FAILED",
      "terminationKind": "Success | Fail | Timeout | Cancel | RuntimeError",
      "error": {
        "code": "string or null",
        "reason": "string or null"
      },
      "terminatedAtState": "stateId or null",
      "journeyId": "string or null",
      "journeyName": "string"
    }
    ```
  - Compensation logic can branch on `outcome` (for example, run different undo steps for cancellation vs timeout vs partial success) and use the copied `context` (and, where supported by the engine, bindings such as `output`) to determine which side effects to revert or clean up.
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
      - `JourneyOutcome.phase` and `JourneyOutcome.error` continue to represent the original outcome from the main run (for example, `phase = FAILED` for failures, `phase = SUCCEEDED` for partial-success runs); compensation errors MAY be recorded in telemetry or as extensions but MUST NOT change `phase`.
- Relationship with sub-journeys:
  - `spec.compensation` describes a coarse-grained, global compensation journey for the entire journey/API run.
  - Future features (for example per-step `compensate` blocks or more fine-grained subjourney-level compensation) may provide more fine-grained SAGA semantics; these are complementary and not required to use `spec.compensation` or v1 local `subjourney` states.

Validation
- `spec.compensation` is optional.
- When present:
  - `mode`, when provided, MUST be either `sync` or `async`; if omitted, the effective mode is `async`.
  - `start` is required and MUST refer to a key in `compensation.states`.
  - `states` is required and MUST be a non-empty map of state definitions with the same validation rules as top-level states.
  - Compensation states MUST obey the same constraints as the main spec for the given `kind` (for example, no `wait`/`webhook` in `kind: Api`).
  - Tooling SHOULD flag specs where `spec.compensation` is declared but the engine does not support compensation journeys.

### 2.6 HTTP surface & hypermedia links (spec.httpSurface.links)

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
    - When the journey is `RUNNING` and is user‑cancellable (see `spec.lifecycle.cancellable`), include a `_links.cancel` entry pointing to the canonical cancellation step:
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
  "phase": "RUNNING",
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

### 2.7 Lifecycle and user cancellation (spec.lifecycle)

Some journeys are long‑running, user‑facing flows where the end user should be able to cancel their own run (for example, abandoning a multi‑step order orchestration). Journeys also differ in how their start endpoint behaves: some are started asynchronously (fire‑and‑observe via status/result), while others behave like synchronous API calls for their initial segment. The optional `spec.lifecycle` block lets authors control both user cancellation and start semantics for `kind: Journey` specs.

Shape:

```yaml
spec:
  lifecycle:
    cancellable: false        # optional; default is true when omitted
    startMode: sync           # optional; sync (default) | async
```

Semantics
- `spec.lifecycle` is optional and only applies to `kind: Journey`:
  - `spec.lifecycle.startMode` MUST NOT be declared for `kind: Api` specs; engines and tooling SHOULD treat this as a validation error.
- `cancellable`:
  - When omitted or `true`, the journey is considered user‑cancellable while it is `RUNNING`.
  - When explicitly `false`, self‑service clients MUST NOT be offered a cancel action for this journey.
- `startMode` (journey start behaviour):
  - When omitted or set to `sync`, the journey uses synchronous start semantics as described in section 2.2.1:
    - `POST /api/v1/journeys/{journeyName}/start` executes the journey instance synchronously from `spec.start` until it either:
      - reaches a terminal state (`succeed` or `fail`), or
      - reaches the first external‑input state (`wait` or `webhook`).
    - If a terminal state is reached within the applicable execution budget (`spec.execution.maxDurationSec` and platform limits), the start endpoint MUST respond with HTTP 200 and a `JourneyOutcome` whose `phase` reflects success or failure.
    - If an external‑input state is reached within the execution budget, the start endpoint MUST respond with HTTP 200 and a `JourneyStatus` describing the current journey status (plus any step‑specific fields described by the journey’s exported OpenAPI).
    - Synchronous start does not change the overall journey semantics: the instance may still continue asynchronously after the initial response, and clients MAY later call `/journeys/{journeyId}` and `/journeys/{journeyId}/result` to observe subsequent progress and the final outcome.
  - When `startMode: async`, the journey uses asynchronous start semantics:
    - `POST /api/v1/journeys/{journeyName}/start` MUST respond with HTTP 202 and a `JourneyStartResponse` that identifies the new journey instance.
    - The engine MAY execute the journey instance immediately or schedule it for later execution; clients observe progress via `GET /journeys/{journeyId}` and the final outcome via `GET /journeys/{journeyId}/result`.
  - Usage guidance:
    - `startMode: sync` is intended for short, user‑centric journeys where the initial segment of execution is expected to complete quickly (for example, within normal interactive API timeouts) and it is desirable to return either a final outcome or the first external‑input step in the start response.
    - Journeys that are expected to be long‑running, heavily asynchronous, or that reach `timer` states before any terminal or external‑input state SHOULD use `startMode: async` or be refactored into a different shape.
    - Engine implementations and tooling SHOULD warn or fail fast when a journey that is structurally unlikely to complete or reach an external‑input state quickly declares `startMode: sync`.
- Interaction with Journeys API and `_links`:
  - When `spec.httpSurface.links` are enabled and `cancellable != false`:
    - While `JourneyStatus.phase == "RUNNING"`, the engine SHOULD expose a `_links.cancel` entry as described in section 2.6, pointing to the canonical cancellation step `/api/v1/journeys/{journeyId}/steps/cancel`.
    - Once the journey is terminal (`SUCCEEDED` or `FAILED`), `_links.cancel` MUST be omitted.
  - When `cancellable == false`, the engine MUST omit `_links.cancel` even if the journey is `RUNNING`.
- Cancellation semantics (conceptual):
  - When an engine honours a user‑initiated cancellation, it SHOULD:
    - Stop scheduling new states for the run.
    - Complete the run as a failure with a stable error code such as `JOURNEY_CANCELLED` and a human‑readable reason (for example, "Cancelled by user").
    - Treat the cancellation as a non‑success outcome for the purposes of `spec.compensation`, using `terminationKind = "Cancel"` in the compensation `outcome` binding (see section 2.5).

Validation
- When present:
  - `cancellable`, when present, MUST be a boolean.
  - `startMode`, when present, MUST be either `sync` or `async`.

### Example – cancelled `JourneyOutcome`
A cancelled journey might produce an outcome like:

```json
{
  "journeyId": "abc123",
  "journeyName": "order-orchestration",
  "phase": "FAILED",
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

### 2.8 Journey metadata (tags & attributes)

Journey definitions and journey instances expose lightweight metadata for classification and querying. The DSL
distinguishes:
- Definition-level tags (`metadata.tags`) on journey definitions.
- Instance-level tags (`journey.tags`) and attributes (`journey.attributes`) on journey instances, populated by the engine according to explicit rules.

#### 2.8.1 Definition tags (`metadata.tags`)

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

#### 2.8.2 Instance tags (`journey.tags`)

- Concept:
  - Each journey instance may carry a set of tags derived from:
    - The definition’s `metadata.tags`.
    - Explicit tag bindings declared under `spec.metadata.bindings.tags` (see 2g.iv).
  - Clients do not set instance tags directly in the start request; the engine derives them
    from the configured journey definition.
- Semantics:
  - Instance tags are intended for:
    - Filtering and grouping in operator dashboards.
    - Coarse-grained policy and reporting (for example, “all `kyc` journeys in `RUNNING` phase”).
  - For v1, instance tags are immutable after the journey is started.
- Limits:
  - The maximum number of instance tags per journey and maximum tag length are controlled by
    `MetadataLimits` (section 21).

#### 2.8.3 Instance attributes (`journey.attributes`)

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
  - `subjectId` – identity of the caller/subject for this journey instance, when available
    from configured metadata bindings (for example from an authenticated header or baggage value).
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

#### 2.8.4 Metadata bindings (spec.metadata.bindings)

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

<a id="dsl-3-context-and-paths"></a>
## 3. Context and paths
- `context` is a per-journey mutable JSON object initialised to `{}` unless a caller provides an initial value. It lives only for that journey instance and is not shared across journeys.
- Dot‑paths reference nested fields: `a.b.c` reads `context.a.b.c`.
- Arrays: the DSL does not support array indexing in paths (no `a[0]`). Future versions may add it.

<a id="dsl-4-interpolation"></a>
## 4. Interpolation and expression engines
- String fields annotated as “interpolated” support `${context.<dotPath>}` placeholders.
- Interpolation is supported in HTTP `url`, `headers` values, and `body` (when `body` is a string). Interpolation of non‑string values coerces to JSON string.
- Unknown/missing variables produce a validation error (not an empty string).
- Wherever the DSL embeds expressions using a `lang`/`expr` pair (for example `choice.when.predicate`, `transform`, mappers under tasks/`wait`/`webhook`/`schedule`, and error mappers), `lang` selects an expression engine:
  - Example engines: `dataweave` (DataWeave 2.x; see ADR-0002), `jsonata`, `jolt`, `jq` – pure JSON expression engines selectable via `lang` when enabled in engine configuration.
- Engines are resolved by id at load time via the expression engine plugin model (see Feature 012 and ADR-0027); if a spec references a `lang` value with no registered engine, validation MUST fail.

<a id="dsl-5-states"></a>
## 5. States

### 5.1 `task` (HTTP call – `httpCall:v1`)
```yaml
type: task
task:
  kind: httpCall:v1             # core HTTP plugin (type httpCall, major 1)
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
      lang: <engineId>              # e.g. dataweave
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
    - `payload` bound to the structured HTTP result object stored at `context.<resultVar>`, with `error` set to `null` and `platform` available as for other expression sites.
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
- `task.kind` for HTTP calls MUST be `httpCall:v1` in this version.
- `method` and `url` are required; `url` must be absolute (`http://` or `https://`).
- `body` present with `GET` → validation error.
- `resultVar` must match `[A-Za-z_][A-Za-z0-9_]*` when `mode` is `requestResponse`.
- When `mode` is `notify`:
  - `resultVar` MUST be omitted (there is no result).
  - `errorMapping` MUST be omitted (there is no error result to map).
  - `resiliencePolicyRef` MAY be ignored by the engine; retries have no observable effect on journey behaviour.
 - Outbound auth (`auth.policyRef`):
  - When present, the engine resolves `auth.policyRef` against `spec.policies.httpClientAuth.definitions` (or platform-level equivalents) and applies the resulting policy to the outbound HTTP request (for example by adding an `Authorization` header or attaching a client certificate).
  - When absent, the engine MUST NOT apply any outbound auth policy implicitly; the request is sent without additional outbound auth (subject to implementation defaults), regardless of whether `spec.policies.httpClientAuth.default` is set.
- HTTP outcomes (status/timeouts) do not terminate execution; you must branch explicitly. In `notify` mode the journey instance cannot branch on call outcomes because they are not observable.
### 5.2 `task` (Kafka publish – `kafkaPublish:v1`)

In addition to HTTP calls, `task` can publish events to Kafka topics via the `kafkaPublish:v1` task plugin.

```yaml
type: task
task:
  kind: kafkaPublish:v1
  connectionSecretRef: secret://kafka/connections/default   # optional – Kafka connection config
  topic: orders.events                                      # required – Kafka topic name

  # Optional key – literal/interpolated string OR mapper object
  key: "${context.orderId}"
  # or:
  # key:
  #   mapper:
  #     lang: <engineId>                                    # e.g. dataweave
  #     expr: <expression>

  # Required payload – mapper for event value
  value:
    mapper:
      lang: <engineId>                                      # e.g. dataweave
      expr: <expression>

  # Optional Kafka record headers – values are strings with interpolation
  headers:
    <k>: <string|interpolated>

  # Optional JSON Schemas for key and value
  keySchemaRef: <string>                                    # optional – JSON Schema for the key
  valueSchemaRef: <string>                                  # optional but recommended – JSON Schema for the payload
next: <stateId>
```

Semantics
- Connection and topic:
  - `connectionSecretRef` (optional) is a `secretRef` that identifies an engine-managed Kafka connection configuration (bootstrap servers, TLS/SASL/OAUTH/mTLS settings, client id, etc.).
  - When `connectionSecretRef` is omitted, the engine MUST use its configured default Kafka connection for `kafkaPublish:v1`.
  - `topic` is the Kafka topic name; it MUST be a non-empty string. Cluster/connection details and authentication are provided entirely by the engine via the resolved connection configuration; they never appear in the DSL.
- Key and value:
  - `key` MAY be either:
    - A string value (including interpolated strings) that is evaluated against `context` and serialised according to engine configuration (typically string/bytes), or
    - An object with a `mapper` as shown above; in that case, the mapper is evaluated with `context` bound to the current journey context and the result is serialised and used as the Kafka record key.
  - `value` is required and MUST contain a `mapper` object; the mapper is evaluated with `context` bound to the current journey context and produces the event payload object. Engines typically serialise this as JSON before publishing.
- Headers:
  - `headers` is an optional map from header name to interpolated string value; values are evaluated against `context` and attached as Kafka record headers.
  - Header values MUST NOT contain raw secrets; secret material is always resolved via `secretRef` in engine configuration, not in DSL specs.
- Schemas:
  - `keySchemaRef` (optional) points to a JSON Schema that describes the logical shape of the key. Engine implementations and tooling MAY use this for validation or schema registry integration.
  - `valueSchemaRef` (optional but recommended) points to a JSON Schema that describes the event payload. When present, engines SHOULD validate the mapped payload against this schema before publishing and MAY register it with an event/schema registry.
- Control flow:
  - Publishing is fire-and-forget from the journey’s perspective: the task does not write a `resultVar`, and no control-flow decisions are based on publish outcomes.
  - On publish failures after any configured retries, implementations MAY treat this as an engine execution error (failing the journey/API call); the DSL does not surface partial success states.

Validation
- `task.kind` for Kafka publish MUST be `kafkaPublish:v1` in this version.
- For `kafkaPublish:v1`:
  - `topic` is required and MUST be a non-empty string.
  - `value` is required and MUST contain a `mapper` object with `lang` set to a supported expression engine id (for example `dataweave`) and an inline `expr`.
  - `key`, when present:
    - MAY be a string value (including interpolated strings), or
    - MAY be an object containing a `mapper` with `lang` set to a supported expression engine id and an inline `expr`.
  - `headers`, when present, MUST be a map of header names to string or interpolated string values.
  - `keySchemaRef` and `valueSchemaRef`, when present, MUST be non-empty strings referring to JSON Schema documents.
  - `connectionSecretRef`, when present, MUST be a `secretRef` string; when absent, engines MUST fall back to a configured default connection.
- `resultVar`, `errorMapping`, and `resiliencePolicyRef` MUST NOT be used with `kafkaPublish:v1`; Kafka publishes do not produce structured results for branching or error mapping.

### 5.3 `task` (XML decode – `xmlDecode:v1`)

`task.kind: xmlDecode:v1` allows journeys to decode XML strings stored in `context` into JSON values that the rest of the DSL can work with. It is a pure transformation: there is no external I/O; the plugin only reads from and writes to `context`.

```yaml
type: task
task:
  kind: xmlDecode:v1

  # Source – required: context field containing XML as a string
  sourceVar: rawXml                    # context.rawXml must be a string

  # Target – where to write the decoded JSON
  target:
    kind: context                      # context | var
    path: input                        # when kind == context; dot-path in context
  resultVar: parsedXml                 # when kind == var; name under context.resultVar
next: <stateId>
```

Semantics
- Source:
  - The plugin reads `context.<sourceVar>`; at runtime this MUST be a string containing XML.
  - If the value is absent or not a string, the plugin MUST fail the task with a clear error mapped to the canonical error model.
- Decode:
  - The plugin parses the XML string using the engine’s XML parser.
  - It maps the parsed XML tree to a JSON value using a deterministic XML→JSON mapping defined by the engine (for example element/attribute handling, text nodes, and arrays). This mapping is implementation-defined but MUST be documented and stable.
- Target:
  - When `target.kind == context` (default):
    - If `target.path` is provided:
      - The decoded JSON value is written at `context.<path>`, overwriting any existing value.
    - If `target.path` is omitted:
      - The decoded JSON value MUST be an object and replaces the entire `context`.
  - When `target.kind == var`:
    - The decoded JSON value MAY be any JSON value (object, array, primitive) and is stored under `context.<resultVar>`. Other context fields remain unchanged.
- Control flow:
  - On successful decode, execution continues to `next`.
  - On parse/mapping failure, the plugin fails the task; the engine maps this to a Problem Details error (for example `type: "urn:xml-decode:parse-error"`) consistent with ADR‑0003 and ADR‑0026.

Validation
- `task.kind` for XML decode MUST be `xmlDecode:v1` in this version.
- `sourceVar`:
  - Required; MUST be a non-empty identifier.
  - At runtime, `context.<sourceVar>` MUST be a string; engines SHOULD treat non-string values as a validation/runtime error.
- `target.kind`:
  - When omitted, defaults to `context`.
  - When `kind: context` and `target.path` is omitted, the decoded value MUST be an object.
- `resultVar`:
  - Required when `target.kind == var`.
  - MUST match the usual identifier pattern (`[A-Za-z_][A-Za-z0-9_]*`).

Usage guidance
- Inbound XML via HTTP:
  - Authors MAY set up journeys that accept XML payloads by modelling the raw payload as a string in `context` (for example via an adapter or by treating the initial context as a string) and then using `xmlDecode:v1` as an early state to obtain a JSON representation.
  - Subsequent `choice`/`transform` states should operate on the decoded JSON under `context`.
- XML responses from HTTP tasks:
  - When an HTTP task (`httpCall:v1`) returns XML in `context.<resultVar>.body`, a subsequent `xmlDecode:v1` state can decode that string into JSON for further processing.

### 5.4 `task` (schedule – `schedule:v1`)

`task.kind: schedule:v1` allows an interactive journey instance to create or update a **schedule binding** that will trigger future, non-interactive runs of the same journey starting from a specific state, with evolving `context` across runs.

```yaml
type: task
task:
  kind: schedule:v1
  start: scheduledStart            # required – entry state for scheduled runs

  # Optional start time – omit ⇒ "start as soon as possible"
  startAt: "2025-01-01T00:00:00Z"  # RFC 3339 timestamp string
  # or:
  # startAt:
  #   mapper:
  #     lang: <engineId>           # e.g. dataweave
  #     expr: context.billing.firstRunAt

  # Required cadence – ISO-8601 duration
  interval: "P1D"                  # once per day
  # or:
  # interval:
  #   mapper:
  #     lang: <engineId>           # e.g. dataweave
  #     expr: context.billing.interval

  # Required run bound
  maxRuns: 12
  # or:
  # maxRuns:
  #   mapper:
  #     lang: <engineId>           # e.g. dataweave
  #     expr: context.billing.maxRuns

  # Optional subject binding
  subjectId:
    mapper:
      lang: <engineId>             # e.g. dataweave
      expr: context.userId

  # Optional initial context snapshot for the FIRST scheduled run;
  # later runs use the previous run's final context
  context:
    mapper:
      lang: <engineId>             # e.g. dataweave
      expr: context - ["sensitiveStuff"]

  # Optional behaviour when a schedule already exists for this journey/subject/start
  # fail (default), upsert, or addAnother
  onExisting: fail | upsert | addAnother
next: <stateId>
```

Semantics
- Scope:
  - `task.kind: schedule:v1` is only allowed in `kind: Journey` specs.
  - It MUST NOT be used in `kind: Api` specs.
- Creation and update:
  - When a journey executes a `task.kind: schedule:v1` state, the engine evaluates the scheduling fields against the current `context`:
    - `start` MUST resolve to a valid state id in `spec.states`.
    - `startAt`:
      - When omitted, the effective start time is “as soon as possible” according to the scheduler.
      - When a string, it MUST be an RFC 3339 timestamp.
      - When a mapper, it MUST evaluate to an RFC 3339 timestamp string.
    - `interval`:
      - Required; when a string, it MUST be a supported ISO-8601 duration.
      - When a mapper, it MUST evaluate to a supported ISO-8601 duration string.
    - `maxRuns`:
      - Required; when a literal, it MUST be a positive integer.
      - When a mapper, it MUST evaluate to a positive integer.
    - `subjectId`:
      - Optional; when present, the mapper MUST evaluate to a non-empty string at runtime.
    - `context`:
      - Optional; when present, the mapper result becomes the initial context snapshot for the FIRST scheduled run.
      - When absent, the full current `context` is used as the initial snapshot.
  - The engine then creates or updates a schedule binding based on `onExisting`:
    - Conceptual identity key: `(journeyName, journeyVersion, subjectId, startStateId)`.
    - `onExisting: fail` (or omitted):
      - If a binding already exists for the identity key, the schedule task fails and no binding is created or changed.
    - `onExisting: upsert`:
      - If a binding exists, update its `startAt`, `interval`, `maxRuns`, `subjectId`, and initial `lastContext` from the new schedule.
      - If none exists, create a new binding.
    - `onExisting: addAnother`:
      - Always create a new binding, even if another exists with the same identity key.
  - After handling the binding, the journey continues to `next`; the schedule task itself is not terminal.

- Scheduled runs:
  - Each schedule binding stores:
    - Journey identity (name/version).
    - Scheduled entry state id (`start`).
    - `startAt`, `interval`, `maxRuns`, and `runsExecuted`.
    - `lastContext` (the evolving context snapshot).
  - First scheduled run:
    - At or after `startAt`, the scheduler creates a new journey instance.
    - Initial `context` is the binding’s `lastContext`, which is:
      - the `context` mapper result when provided, or
      - the full `context` snapshot from the scheduling journey instance when `context` is omitted.
    - Execution starts at the state referenced by `schedule.start`.
  - On completion of each scheduled run:
    - The engine sets the binding’s `lastContext` to the run’s final `context`.
    - The engine increments `runsExecuted`.
  - Subsequent runs:
    - Each run’s initial `context` is the binding’s current `lastContext`, i.e. the final `context` from the previous run.
  - When `runsExecuted >= maxRuns`, the scheduler MUST NOT start further runs for that binding.

- Non-interactive path constraint:
  - For each `task.kind: schedule:v1`, engines and tools MUST verify that all states reachable from `start` are non-interactive:
    - No `type: wait` states.
    - No `type: webhook` states.
  - Specs that violate this rule SHOULD be rejected at validation time, with errors that reference the offending state ids.
  - If static validation is not available and a scheduled run reaches a `wait` or `webhook` at runtime, the engine MUST treat this as an internal engine error and fail the run.

Validation
- `task.kind` for scheduling MUST be `schedule:v1` in this version.
- For `schedule:v1`:
  - `start` is required and MUST be a state id in `spec.states`.
  - `interval` is required and MUST be either:
    - A string representing a supported ISO-8601 duration, or
    - A `mapper` that evaluates to such a string.
  - `maxRuns` is required and MUST be either:
    - A positive integer literal, or
    - A `mapper` that evaluates to a positive integer.
  - `startAt`, when present, MUST be either:
    - A string representing an RFC 3339 timestamp, or
    - A `mapper` that evaluates to such a string.
  - `subjectId`, when present, MUST be a `mapper` object with `lang` set to a supported expression engine id (for example `dataweave`) and an inline `expr`.
  - `context`, when present, MUST be a `mapper` object with `lang` set to a supported expression engine id and an inline `expr`.
  - `onExisting`, when present, MUST be one of `fail`, `upsert`, or `addAnother`.
  - `task.kind: schedule:v1` MUST NOT be used in `kind: Api` specs.
  - Engines and tools SHOULD perform a reachability check from `start` and reject specs where reachable states include `wait` or `webhook`.

#### Task plugins

All `task` states are implemented via plugins. The DSL expresses both core behaviours (`httpCall:v1`, `kafkaPublish:v1`, `schedule:v1`) and custom behaviours (for example `jwtValidate:v1`) using the same `task.kind: <pluginType>:v<major>` pattern.

```yaml
type: task
task:
  kind: jwtValidate:v1          # plugin id + major version
  source: header:Authorization  # plugin-defined field
  requiredScopes:               # plugin-defined field
    - accounts:read
next: <stateId>
```

Shape
- All tasks use the same `type: task` wrapper and `task` block.
- `task.kind`:
  - MUST have the form `<pluginType>:v<major>` where:
    - `<pluginType>` is a non-empty identifier chosen by the plugin provider (for example `httpCall`, `kafkaPublish`, `schedule`, `jwtValidate`, `mtlsValidate`).
    - `<major>` is a positive integer (major version). Minor and patch versions are handled by engine/plugin wiring and are not expressed in the DSL.
  - Core plugin types for this version are:
    - `httpCall:v1` – HTTP call with structured result recording (section 5.1).
    - `kafkaPublish:v1` – Kafka event publish (section 5.2).
    - `schedule:v1` – Schedule binding creation/update (section 5.3).
    - `jwtValidate:v1` – JWT validation task plugin (section 18.6).
    - `mtlsValidate:v1` – mTLS client certificate validation task plugin (section 18.7).
- All other fields directly under `task` (for example `method`, `url`, `operationRef` for `httpCall:v1`, or `source`, `requiredScopes` for `jwtValidate:v1`) are **plugin-defined configuration fields**:
  - Their names, types, and semantics are defined by the plugin’s own contract, not by this DSL reference, except where explicitly documented for core plugins.
  - The DSL does not reserve additional field names for plugins in this version; engines MAY introduce platform-specific validation for particular plugins.
- Plugin-backed tasks are allowed in both `kind: Journey` and `kind: Api` specs; they obey the same control-flow rules as other `task` states (they execute once and then transition to `next`).

Semantics
- From the DSL’s perspective, `task` states are opaque operations with constrained behaviour:
  - They MAY read from the current journey `context`.
  - They MAY update `context`, but only via explicit, plugin-defined write targets that are visible in the DSL surface (for example `resultVar` or a documented plugin namespace such as `jwt` or `auth`).
  - They MUST NOT change control-flow wiring (`next` is always taken on successful engine execution) and MUST NOT introduce implicit external-input pauses; long-lived waits remain the domain of `wait`/`webhook`/`timer` and schedule bindings.
  - They execute synchronously with respect to the enclosing state: each time a `task` state is entered, the plugin runs once to completion; asynchronous waiting MUST be modelled via `wait`/`webhook`/`timer`/`schedule` states, not hidden inside plugins.
- Engines and tooling:
  - MUST resolve `task.kind` values of the form `<pluginType>:v<major>` to a configured plugin runtime.
  - MUST fail validation when `task.kind` uses a plugin id for which no corresponding plugin is available in the runtime configuration.

Bindings available to plugin implementations (runtime note)
- When executing a task plugin, engines MUST provide plugin implementations with at least:
  - The current journey context JSON object (`context`).
  - The `platform` binding as a structured view, with the same fields and semantics as expressions see under `platform` (see ADR‑0022).
  - Journey definition metadata (`apiVersion`, `kind`, `metadata.name`, `metadata.version`).
  - The current journey instance identity when available (for example `journeyId` for `kind: Journey` runs).
  - The logical state id of the task (so plugins can emit diagnostics that reference the DSL).
  - For `kind: Api` synchronous invocations, the incoming HTTP request context when available (method, path, headers; body when applicable).
- Engines MAY provide additional runtime information (for example correlation ids, platform-specific user identity) but this is outside the scope of the DSL reference.

Error handling
- Task plugins MUST integrate with the canonical error model defined in this document:
  - On success, plugins behave as normal task states and the run continues to `next`.
  - On plugin-reported failure, plugins surface errors in terms of RFC 9457 Problem Details; the engine maps these into `JourneyOutcome` and HTTP responses according to the error model and HTTP-mapping ADRs. Engines MUST treat `context` as unchanged on this branch.
  - Engines MUST treat unhandled exceptions or violations in plugin code as internal engine errors, not journey-authored failures; these are mapped to a stable internal Problem type and HTTP 500 for `kind: Api`.

Configuration, security, and observability
- Plugin behaviour is configured primarily via the DSL `task` block; when a plugin supports engine-defined profiles, omission of a profile selector in the DSL MUST be treated as selecting a well-defined `"default"` profile from engine configuration.
- Plugins MAY cause external side effects (for example HTTP calls, event publishes), but from the DSL’s perspective these effects are always mediated by engine-provided connectors and infrastructure; plugin DSL surfaces MUST NOT assume direct access to arbitrary sockets, databases, or filesystem paths.
- Data available to plugins via `context` and HTTP request bindings MAY contain PII and secrets; plugins and engines MUST honour the project’s security posture (for example cookie and secret-handling rules from ADR-0012 and observability guardrails from ADR-0025/ADR-0026) and MUST NOT use the DSL to expose or log raw secret values. Logging levels and telemetry detail for plugins are controlled via deployment configuration (`observability.plugins.*`), not per-journey DSL fields.
- Implementation references (non-normative):
  - Feature 011 – Task Plugins & Execution SPI – defines the engine-side plugin interfaces and execution model that realise these DSL rules.
  - ADR-0026 – Task Plugins Model and Constraints – captures the plugin constraints adopted by the engine.
  - ADR-0031 – Runtime Core vs Connector Modules Boundary – describes how plugin implementations (including HTTP) are placed in core vs connector modules.

### 5.4 `choice` (branch)
Choice states enable data-driven branching: tasks and other states write results into `context`, and predicates over that data decide which `next` state is taken.

```yaml
type: choice
choices:
  - when:
      predicate:
        lang: <engineId>               # e.g. dataweave
        expr: |
          context.item.status == 'OK' and context.item.price < 100
    next: <stateId>
default: <stateId>              # optional but recommended
```

Semantics
- Evaluate branches in order; the first predicate that evaluates to `true` wins.
- Predicate: evaluate `when.predicate.expr` with `context` bound to the current journey context using the selected expression engine (`lang`). The expression must return a boolean; non‑boolean results are a validation error at spec validation / compile time.
- Predicate runtime errors: when evaluating `when.predicate.expr` raises a runtime error at execution time, the engine MUST treat this as an internal engine error (not a journey‑authored failure):
  - The run terminates as a failure with a canonical internal error code (for example a Problem Details `type` such as `urn:journeyforge:error:internal`) and HTTP status 500 for externally visible APIs.
  - For `kind: Journey`, the resulting `JourneyOutcome` MUST have `phase = FAILED` and `error.code` set to the same internal error identifier; platform logging and telemetry SHOULD carry more detailed diagnostics.
  - For `kind: Api`, the HTTP response MUST use the same internal error identifier as the Problem `type` / `JourneyOutcome.error.code` and status 500.
  - This outcome indicates a bug or misconfiguration in the journey or platform; well‑behaved journeys SHOULD avoid triggering it in normal operation.
- If no branch matches, transition to `default` if present; otherwise validation error.

### 5.4 `succeed`
```yaml
type: succeed
outputVar: <identifier>         # optional
```
Semantics
- Terminal success. If `outputVar` is set and exists in `context`, return its value; else return full `context`.

### 5.5 `fail`
```yaml
type: fail
errorCode: <string>
reason: <string>
```
Semantics
- Terminal failure with a code and human message.
- `errorCode` MUST be a stable identifier for the error condition (for example a URI or string key); by default it SHOULD align with the RFC 9457 Problem Details `type` when a Problem object is available.
- `reason` SHOULD be a concise, human-friendly summary suitable for operators and API consumers (often derived from a Problem Details `title` or `detail`).

### 5.6 Error handling patterns

- Task-level errors as data
- `task` states (for example `httpCall`) never auto-terminate a journey. They always store a structured result in `context.<resultVar>` (including `status`, `ok`, `headers`, `body`, optional `error`) and then continue to `next`.
  - Error handling is expressed explicitly via `choice` predicates and/or `transform` states that inspect these result objects.
- Mapping to journey outcome
- `succeed` produces `JourneyOutcome.phase = SUCCEEDED` with `output` taken from `context.<outputVar>` when set, otherwise from the full `context`.
- `fail` produces `JourneyOutcome.phase = FAILED` with `error.code = errorCode` and `error.reason = reason`. The HTTP status of downstream calls is not reflected in the `/result` status code.
- Exposing raw downstream errors
  - To surface a downstream HTTP result directly, use `succeed` with `outputVar` pointing at the HTTP `resultVar` so callers can inspect `status`, `body`, and `error` themselves.
- Inspect and normalise errors
  - Use `choice` predicates over `context.<resultVar>` (for example `ok`, `status`, `body` fields, `error.type`) to decide between `succeed` and `fail`.
  - Optionally insert a `transform` that builds a normalised error object (for example `context.normalisedError`) before either succeeding with `outputVar: normalisedError` or failing with a concise `errorCode`.
- Aggregating errors from multiple calls
  - Sequential aggregation: perform multiple `task` calls (each with its own `resultVar`), then use a `choice` and optional `transform` to combine their outcomes into a single success result or a single `fail` state (for example, "one or more upstream calls failed").
  - Parallel aggregation: when using `parallel`, branches write their own results; the `join.mapper` can aggregate branch-level results into a single `context.<resultVar>` that a subsequent `choice` uses to decide whether to `succeed` or `fail`. Concurrency and detailed error propagation for `parallel` are defined under the Parallel feature.

### 5.7 Error model and RFC 9457 (Problem Details)

- Default error model (journey outcome)
- `succeed` produces a `JourneyOutcome` with `phase = SUCCEEDED` and `output` taken from `context.<outputVar>` (when set) or from the full `context`.
- `fail` produces a `JourneyOutcome` with `phase = FAILED` and `error` populated; `error.code` MUST be a stable identifier for the error condition (for example a URI or string), and `error.reason` SHOULD be a human-readable message.
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

<a id="dsl-6-example"></a>
## 6. Example
	
The `spec.errors` block allows journeys to centralise, per journey, how they normalise and expose errors, building on the canonical RFC 9457 Problem Details model (see ADR‑0003).
	
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
	  - Each mapper MUST declare `lang` set to a supported expression engine id (for example `dataweave`) and an inline `expr`.
	- `envelope`:
	  - Optional single configuration that defines the external error envelope for the journey.
	  - `format` controls the envelope:
	    - When omitted or set to `problemDetails`, the externally visible error structure for the journey MUST be the Problem Details shape.
	    - When set to `custom`, the `envelope` block MUST include a `mapper` that transforms Problem Details objects into a single, stable error body structure for this journey; the mapper MUST declare `lang` set to a supported expression engine id and an inline `expr`.
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
	- When `envelope.format` is `custom`, `envelope.mapper` MUST be present and must declare `lang` set to a supported expression engine id (for example `dataweave`) and an inline `expr`.
	- Tools SHOULD flag references to unknown normaliser ids from HTTP tasks or other configuration as validation errors.

### 5.8 `subjourney` (local intra-spec reuse)

```yaml
type: subjourney
subjourney:
  ref: collectShipping
  input:
    mapper:
      lang: <engineId>                   # e.g. dataweave
      expr: |
        {
          cart: context.cart,
          user: context.user
        }
  resultVar: shipping
  resultKind: output           # optional; output (default) | outcome
  onFailure:
    behavior: propagate        # optional; propagate (default) | capture
next: computeTaxes
```

Semantics
- Purpose:
  - `subjourney` states provide **local, intra-spec reuse** and structuring. They behave like synchronous calls to named subgraphs defined under `spec.subjourneys`.
- Target:
  - `ref` MUST refer to a key under `spec.subjourneys` in the same spec.
  - Unknown `ref` values are validation errors.
  - Subjourneys are **not visible across specs** in this version; there is no cross-spec `journeyRef`.
- Input context:
  - When `input.mapper` is present:
    - Evaluate the expression with `context` bound to the parent journey/API context, using the selected expression engine (`lang`).
    - The result MUST be a JSON object; it becomes the subjourney’s initial `context`.
  - When `input` is omitted:
    - The subjourney’s initial `context` is `{}` (empty object). Authors SHOULD declare explicit input mappers for reusable subjourneys so contracts are visible.
- Execution and `kind` constraints:
  - The engine executes the subjourney from `spec.subjourneys[ref].start` using `spec.subjourneys[ref].states` as the state map.
  - For `kind: Journey`:
    - Subjourneys MAY use the full journey state surface (including `wait`, `webhook`, `timer`, `parallel`, cache, and policies), subject to any feature-specific constraints.
  - For `kind: Api`:
    - Subjourneys MUST obey the same constraints as top-level APIs:
      - No `wait`, `webhook`, or `timer` states (no external-input or durable-delay states).
      - Every control-flow path starting from the subjourney’s `start` MUST eventually reach a terminal `succeed` or `fail` without requiring external input.
  - Local subjourneys do not create separate `journeyId` values or additional HTTP endpoints; they are internal control-flow constructs for a single journey/API invocation.
- Result value:
  - If `resultVar` is omitted:
    - No result is written back into the parent `context`; only success/failure behaviour applies.
  - If `resultVar` is set:
    - When `resultKind` is omitted or `output`:
      - On subjourney success (`phase = SUCCEEDED`), `context.<resultVar>` is set to the subjourney’s output:
        - If the subjourney’s terminal `succeed` state declares `outputVar` and that field exists, the output is `subjourneyContext[outputVar]`.
        - Otherwise, the output is the final subjourney `context` (deep copy).
      - On subjourney failure (`phase = FAILED`) and `onFailure.behavior = capture`, `context.<resultVar>` is set to `null`.
    - When `resultKind: outcome`:
      - `context.<resultVar>` is set to a **mini-outcome object** mirroring the `outcome` shape used by `spec.compensation`, adapted for subjourneys:
        ```json
        {
          "phase": "SUCCEEDED" or "FAILED",
          "terminationKind": "Success | Fail | Timeout | Cancel | RuntimeError",
          "output": <any or null>,
          "error": {
            "code": "string or null",
            "reason": "string or null"
          }
        }
        ```
      - This object is produced for both success and failure and is available for branching via subsequent `choice`/`transform` states.
- Failure behaviour (`onFailure.behavior`):
  - When `behavior` is omitted or `propagate`:
    - If the subjourney terminates with `phase = FAILED`:
      - The `subjourney` state behaves as if it were a `fail` state at this `stateId`:
        - The run terminates as a failure with the mini-outcome’s `error` as the canonical error.
        - `spec.compensation`, when present, observes this failure as the main run’s outcome (including `terminatedAtState` pointing to this `subjourney` state).
    - If the subjourney terminates with `phase = SUCCEEDED`:
      - The `subjourney` state is considered successful and execution continues to `next`.
  - When `behavior: capture`:
    - The `subjourney` state is always considered successful from a control-flow perspective:
      - Execution always continues to `next`, regardless of subjourney success or failure.
      - Authors are expected to branch on the captured result in later states, especially when `resultKind: outcome`.
- Execution budget:
  - Subjourneys do not define their own execution deadlines in this version.
  - The global `spec.execution.maxDurationSec` budget applies to the entire run (including all subjourneys); engines MAY expose remaining budget to DataWeave in future versions, but this is out of scope for the current DSL.

Local subjourneys and `type: subjourney` are fully specified for v1 in this section and ADR‑0020. Future work may extend this with cross-spec subjourneys, asynchronous modes, per-subjourney compensation, or richer mapping constructs.

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
        kind: httpCall:v1
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
            lang: <engineId>               # e.g. dataweave
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

<a id="dsl-7-limitations"></a>
## 7. Limitations (explicit non-capabilities)
- Terminal success/failure are explicit via `succeed`/`fail`; tasks never auto‑terminate a journey.
- Enforcement for retries, circuit breakers, bulkheads, or authentication policies is not defined here; only configuration via resilience policies is specified.
- Engine feature increments MAY implement only a subset of the DSL surface at any given time. When a state type or construct (for example `wait`, `webhook`, `timer`, `parallel`, `subjourney`, cache operations, or scheduling) is not yet supported by a given engine, specs using it SHOULD be rejected or explicitly documented as unsupported until the corresponding feature spec is implemented (this includes durable constructs like `wait`/`webhook`/`timer` when an engine build does not yet support persistence across restarts).
- No array indexing in dot‑paths (no `a[0]`), and no alternate expression languages beyond those explicitly supported by the expression engine plugin model.
- No generic environment-variable lookup or direct secret access from expressions; secrets only appear as opaque `secretRef` identifiers in dedicated policy/task configuration surfaces.
- No dynamic parallel loops / map state: the DSL does not support runtime‑determined fan‑out into N parallel branches or step endpoints (for example “callback‑0 … callback‑N‑1”). Patterns such as “wait for N callbacks” are expressed via a single external‑input state (`wait`/`webhook`) that can be revisited in a loop, using counters/aggregates in `context` to decide when all expected callbacks have been processed. See ADR‑0021 for rationale and loop pattern examples.

Secrets & `secretRef`: Secrets in the DSL are referenced only via `secretRef` fields (for example in outbound HTTP client-auth policies, Kafka connection configuration for `kafkaPublish:v1`, and any future secret-consuming policies or task kinds). Engines resolve `secretRef` values against an implementation-defined secret store; raw secret material is never exposed to DataWeave expressions, interpolation, `context`, or logs.

<a id="dsl-8-naming-and-terminology"></a>
## 8. Naming & terminology
- State identifiers are arbitrary keys under `spec.states` (e.g., `call_api`, `decide`). They do not imply special behaviour.
- The branch state type is `choice` (canonical, ASL-aligned). The spec and snapshots use `choice`; no alias is defined.

<a id="dsl-9-forward-compatibility-notes"></a>
## 9. Forward-compatibility notes
- Policies block (auth/resiliency) will be added in a future feature.


<a id="dsl-10-dataweave"></a>
## 10. Expressions & Mappers
- Expression engines: expressions are authored inline via `expr` and evaluated by pluggable expression engines selected via `lang: <engineId>`; DataWeave 2.x (`lang: dataweave`) is one supported engine (see Feature 013 and ADR‑0002). v1 does not support DSL-level references to external `.dwl` modules; see ADR-0015.
- `lang` is required at every expression site that uses `expr`; the DSL does not define a default expression engine. Engine availability and limits are controlled by configuration and engine registration, not by implicit defaults in specs.
- Additional engines: JSONata, JOLT, jq MAY be supported as pure expression engines via the expression engine plugin model; when enabled, they can be selected via `lang: jsonata` / `lang: jolt` / `lang: jq` at any expression site.
- Bindings:
  - `context` variable is bound to the current journey context JSON value.
  - `payload` variable is bound to the “current payload” when the expression is associated with a body or step payload (for example HTTP request/response bodies, external-input step submissions); when there is no such payload for the expression site, `payload` is `null` or absent.
  - `error` variable is bound only in error-handling contexts (for example `spec.errors` mappers, error-aware `apiResponses` rules) and represents the low-level error being normalised; for all other expression sites `error` is `null` or absent.
  - `platform` variable is bound to a read-only JSON object that exposes platform metadata and configuration for the current run:
    - `platform.environment` – logical environment identifier (for example `dev`, `staging`, `prod-eu`).
    - `platform.journey` – metadata for the current definition:
      - `name` – `metadata.name` for this journey or API.
      - `kind` – `"Journey"` or `"Api"`.
      - `version` – `metadata.version`.
    - `platform.run` – metadata for the current run:
      - `id` – journey instance identifier for `kind: Journey`, or an implementation-defined request/run id for `kind: Api`.
      - `startedAt` – RFC 3339 timestamp for when this run started.
      - `traceparent` – W3C Trace Context `traceparent` value associated with this run, taken from the last inbound request (when available).
    - `platform.config` – per-journey configuration object populated from `spec.platform.config` (see section 2.3.1).
- Predicates: used in `choice` branches via `when.predicate`. The expression must evaluate to a boolean.
- Transforms: `transform` states use the selected expression engine (for example DataWeave) to compute values written into `context` or into variables under `context.<resultVar>`, according to the semantics in the Transform state section.
- Determinism & safety: expressions must be pure (no I/O); the evaluator must enforce timeouts and resource limits.
- Validation and tooling: journey compilers and linters SHOULD validate expressions (including `choice` predicates and mappers) against any declared schemas (`spec.context.schema`, `spec.input.schema`, step‑level `*.schema`, etc.) when available, and MUST fail fast at spec validation / compile time when an expression can be statically determined to:
  - Use invalid or non-existent paths, or
  - Produce a non‑boolean result where a predicate is required.

#### 10.0 Examples – engine snippets

When additional engines such as DataWeave, JSONata, JOLT, or jq are configured, authors can select them via `lang` at any expression site. The bindings (`context`, `payload`, `error`, `platform`) remain the same; only the expression syntax changes.

Example – `choice` predicate using DataWeave:

```yaml
type: choice
choices:
  - when:
      predicate:
        lang: dataweave
        expr: |
          context.amount < 100 and context.status == "PENDING"
    next: smallOrder
default: largeOrNonPending
```

Example – `choice` predicate using JSONata:

```yaml
type: choice
choices:
  - when:
      predicate:
        lang: jsonata
        expr: "context.amount < 100 and context.status = 'PENDING'"
    next: smallOrder
default: largeOrNonPendingOrOther
```

Example – `transform` mapper using JOLT:

```yaml
type: transform
transform:
  mapper:
    lang: jolt
    expr: |
      [
        {
          "operation": "shift",
          "spec": {
            "context": {
              "user": "user",
              "cart": "cart"
            }
          }
        }
      ]
  target:
    kind: var
  resultVar: projected
next: nextState
```

Example – `transform` mapper using jq:

```yaml
type: transform
transform:
  mapper:
    lang: jq
    expr: |
      { id: .context.id, total: .context.cart.total }
  target:
    kind: var
  resultVar: summary
next: nextState
```

### 10.2 Bindings per expression context

Across all expression sites that use `lang`/`expr`, engines provide a consistent set of bindings:
- `context` and `platform` are always available.
- `payload` is available when the expression is associated with a request/response body or external-input payload; otherwise it is `null` or absent.
- `error` is available only in error-handling contexts; otherwise it is `null` or absent.

Specific contexts use these bindings as follows:

| Expression site                                          | Description                                           | `context` | `payload`                                | `error`                            | `platform` |
|----------------------------------------------------------|-------------------------------------------------------|-----------|------------------------------------------|------------------------------------|-----------|
| `choice.when.predicate` (all states)                    | Branching based on current state of the journey      | ✅        | ✅ when a current payload is in scope    | ❌ (`null`/absent)                 | ✅        |
| `transform.expr` / `transform.mapper`                   | Transform state expressions and reusable mappers     | ✅        | ✅ when associated with a body/payload   | ❌ (`null`/absent)                 | ✅        |
| Task/step mappers (`task.*.mapper`, `wait`/`webhook`)   | Request/response shaping and external-input updates  | ✅        | ✅ (HTTP bodies, step payloads, etc.)    | ❌ (`null`/absent)                 | ✅        |
| Scheduler mappers (`schedule.*.mapper`)                 | Mapping schedule attributes (`startAt`, `subjectId`) | ✅        | ✅ when a payload is defined for mapping | ❌ (`null`/absent)                 | ✅        |
| Error mappers (`spec.errors.normalisers` and envelope)  | Error normalisation and envelope mapping             | ✅        | ✅ when a payload is part of the error   | ✅ (low-level error being handled) | ✅        |
| API response predicates (`apiResponses.when.predicate`) | HTTP mapping conditions for `kind: Api`             | ✅        | ✅ when the outcome/payload is available | ✅ for error-phase rules           | ✅        |
| API `statusExpr` (`apiResponses.statusExpr.expr`)       | Expression producing HTTP status codes               | ✅        | ✅ when the outcome/payload is available | ✅ for error-phase rules           | ✅        |

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
- `spec.mappers` must be a map of ids to mapper objects; each mapper must declare `lang` set to a supported expression engine id (for example `dataweave`) and an inline `expr`.
- `mapperRef` values must be non-empty strings that resolve to an existing entry in `spec.mappers`.

<a id="dsl-11-schemas"></a>
## 11. Schemas & Final Response (optional)
- `spec.input.schema`: inline JSON Schema (2020-12) that validates the initial `context` provided at journey start.
- `spec.output.schema`: inline JSON Schema for the terminal output returned by `succeed` (or the overall `context` if `outputVar` is omitted).
- `spec.output.response`: optional block that controls how additional business fields are projected into the top-level `JourneyOutcome` alongside the standard envelope fields.
- `spec.context.schema`: inline JSON Schema for the logical shape of `context` during journey execution (superset of fields that may appear over time).
- Exporter behaviour:
  - When `spec.input.schema` is present, the OpenAPI exporter uses it as the request body schema in the per-journey OAS.
  - When `spec.output.schema` is present, the exporter specialises `JourneyOutcome.output` to that schema in the per-journey OAS (by inlining or via an internal component).
  - When `spec.output.response` is present, the exporter projects the additional top-level fields into the per-journey `JourneyOutcome` schema via `allOf(JourneyOutcome, spec.output.response.schema)`.
  - `spec.context.schema` is primarily for tooling and validation; the exporter MAY expose it as an additional schema component, but it does not change the wire format of `JourneyStartRequest` or `JourneyOutcome` beyond the `status` field and any explicit projections configured under `spec.output.response`.

Shape:

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
        reportId: { type: string }
        # other business fields...
    response:                         # optional – project extra fields into JourneyOutcome
      outputVar: result               # context.result object is merged into the top-level JourneyOutcome
      schema:                         # JSON Schema for the additional top-level fields
        type: object
        required: [status]
        properties:
          status:
            type: string
            description: "Business status code; MUST mirror JourneyOutcome.output.status."
          reportId:
            type: string
  context:
    schema:
      type: object
      additionalProperties: true
```

Semantics
- `JourneyOutcome.output`:
  - As defined in sections 5.4–5.7, `succeed`/`fail` produce a terminal `JourneyOutcome` where `output` is taken from `context.<outputVar>` (when the `succeed` state declares an `outputVar`) or from the full `context`.
  - Authors SHOULD declare `spec.output.schema` and include a `status` property; this `status` is the canonical business outcome code for the journey (for example `JOB_COMPLETED`, `REPORT_FAILED`).
- Top-level `JourneyOutcome.status`:
  - `JourneyOutcome` exposes a required top-level `status: string` field that represents the business outcome code for the run.
  - Engines MUST ensure that, when `JourneyOutcome.output` is an object with a `status` property, `JourneyOutcome.status` has the same value (`status` mirrors `output.status`).
  - When `output` is not an object or does not contain `status`, engines MUST still provide a non-empty `status` code; the mapping from the final context/output to this code is platform-specific but MUST be stable for a given journey definition.
  - Consumers SHOULD treat:
    - `phase` as the engine lifecycle (`RUNNING` / `SUCCEEDED` / `FAILED`).
    - `status` as the primary business outcome code (`JOB_COMPLETED`, `REPORT_FAILED`, etc.).
- `spec.output.response` (projection into `JourneyOutcome`):
  - Optional block that mirrors the `wait.response` / `webhook.response` projection rules but applies to the final `JourneyOutcome` instead of `JourneyStatus`:
    - When `spec.output.response.outputVar` is set and `context.<outputVar>` is an object, its properties are shallow-merged into the top level of the JSON `JourneyOutcome` alongside the standard fields.
    - If `context.<outputVar>` is absent or not an object, the response is a plain `JourneyOutcome` without extra projected fields (other than the mandatory `status`).
    - The following top-level properties are reserved and MUST NOT be overridden by projected fields: `journeyId`, `journeyName`, `phase`, `status`, `output`, `error`, `tags`, `attributes`, `_links`.
  - `spec.output.response.schema` MUST be a JSON Schema object describing the additional top-level fields produced by the projection; exporters use it via `allOf(JourneyOutcome, schema)`.

Usage notes
- Use `spec.input.schema` for what callers must send at start.
- Use `spec.output.schema` for what successful journeys return as `JourneyOutcome.output`, including the canonical business `status`.
- Use `spec.output.response` when clients benefit from having selected business fields (for example `status`, `overallStatus`, `reportId`) available as top-level fields on `JourneyOutcome` in addition to the nested `output`.
- Use `spec.context.schema` to describe the full, evolving shape of `context` (including internal fields like `remote`, `cachedUser`, `problem`), so linters and editors can validate `context.<path>` usage in expressions.

### OpenAPI operation binding (operationRef)
- `operationRef`: resolves `<apiName>` in `spec.apis` and `<operationId>` in the referenced OAS.
- Server selection: the first OAS server is used by default; future features may allow server variables/overrides.
- Params mapping: `params.path/query/headers` provide values for OAS params by name. Missing required params is a validation error.
- Body mapping: if `body` is a string, it is sent as-is; if an object, it is JSON-encoded; if a `mapper` object with `lang` set to a supported expression engine id (for example `dataweave`) and `expr` is provided, the mapper result becomes the JSON body.
- `accept` selects the response media type; default `application/json`.
- Cannot mix `operationRef` with raw `method`/`url` in the same task.

<a id="dsl-12-external-input-states"></a>
## 12. External-Input & Timer States (wait/webhook/timer)

External-input and timer states pause the journey instance and resume it later, either due to an external submission or when a durable timer fires.

External-input states (`wait`, `webhook`) expose a step surface: they require a step submission to continue. Submissions are sent to `/journeys/{journeyId}/steps/{stepId}` where `stepId` equals the state id.

Step payload handling for external-input submissions:
- The submitted step body is validated against the state’s `input.schema`.
- After validation succeeds, the engine MUST copy the submitted JSON value into `context.payload` (overwriting any previous value at that path).
- Subsequent states executed as part of the same step submission can read the submitted payload from `context.payload`.
- While the run is paused at an external-input state (`wait`/`webhook`), `context.payload` MUST be absent: when the engine enters a `wait` or `webhook` state (including re-entry via loops), it MUST clear `context.payload` to avoid persisting untrusted or stale submission data across idle periods. Journeys that want to retain any part of a submission across re-entry MUST explicitly copy it into a stable context subtree.

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
  timeoutSec: <int>                       # optional
  onTimeout: <stateId>                    # required if timeoutSec is set
next: <stateId>                           # required
```

Semantics
- When entering a `wait` state, the journey phase is `RUNNING`, and the step subresource is considered active.
- A submission must target the active step; otherwise respond 409 Conflict.
- The request body is validated against `wait.input.schema` when present; invalid → 400 with schema errors and the journey remains at the same active step.
- After request body validation succeeds, the engine MUST copy the submitted step payload into `context.payload` before executing subsequent states. This write is a shallow assignment (it overwrites any previous value at `context.payload`).
- After payload ingestion, the engine continues execution by transitioning to the state’s `next`.
- To branch based on the submitted payload, use a subsequent `choice` state and inspect `context.payload`.
- Step responses:
  - After processing the step submission (including payload ingestion and any synchronous execution that follows), the engine builds a `JourneyStatus` object to describe the updated journey state.
  - When `response.outputVar` is set and `context.<outputVar>` is an object, its properties are shallow-merged into the top level of the JSON response alongside the standard `JourneyStatus` fields.
  - If `context.<outputVar>` is absent or not an object, the response is a plain `JourneyStatus` without extra fields.
  - The following top-level properties are reserved and MUST NOT be overridden by projected fields: `journeyId`, `journeyName`, `phase`, `currentState`, `updatedAt`, `tags`, `attributes`, `_links`.
- If `timeoutSec` elapses without submission, transition to `onTimeout`.

Usage guidance
- Treat `context.payload` as a per-submission scratch space populated for the synchronous processing that follows a step submission. Prefer a follow-up `transform` to project only the required fields into a stable domain subtree (and optionally drop `payload` from `context`).
  - Example (DataWeave): keep only a few fields and remove the raw payload:

    ```yaml
    ingestStep:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            (context - "payload") ++ {
              approval: {
                decision: context.payload.decision,
                comment: context.payload.comment
              }
            }
        target:
          kind: context
      next: routeStep
    ```

Validation
- `wait.channel` must be `manual`; use `webhook` state for callback semantics.
- `wait.input.schema` is required.
- If `timeoutSec` is set, `onTimeout` is required.
- `next` is required and MUST refer to a state id in `spec.states`.
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
  timeoutSec: <int>
  onTimeout: <stateId>
next: <stateId>                           # required
```

Semantics
- Same as `wait`, but intended for third-party callbacks.
- Webhook authentication/authorisation is expressed via auth task plugins (for example `apiKeyValidate:v1`) executed after the webhook step submission; the DSL does not define a special `webhook.security` block.
- Step responses follow the same projection rules as `wait` when `response.outputVar` is configured: the engine returns a `JourneyStatus` body with additional top-level fields taken from `context.<outputVar>` when it is an object.

Validation
- `webhook.input.schema` is required.
- If `timeoutSec` is set, `onTimeout` is required.
- `next` is required and MUST refer to a state id in `spec.states`.
- `response` (when present) follows the same rules as for `wait.response`: `outputVar` must be a valid variable name, `schema` a JSON Schema object, and projected properties MUST NOT collide with reserved `JourneyStatus` fields.

### 12.3 `timer` (durable in-journey delay)

Timer states represent non-interactive, durable delays inside a single `kind: Journey` instance. They provide pure time-based control flow (“sleep until duration/until”) without exposing a step endpoint.

Shape (journeys only):

```yaml
type: timer
timer:
  duration: <string|mapper>             # ISO-8601 duration, e.g. "PT5M" (xor with until)
  # xor:
  # until: <string|mapper>              # RFC 3339 timestamp, e.g. "2025-12-31T23:59:00Z"
next: <stateId>
```

Semantics
- Journeys only:
  - `type: timer` is only valid in specs with `kind: Journey`.
  - Timer states MAY be used in both interactive runs and scheduled runs created via `task.kind: schedule`.
- XOR between `duration` and `until`:
  - Exactly one of `timer.duration` or `timer.until` MUST be present.
  - Both fields, when present, MAY be either:
    - A literal string, or
    - A `mapper` object with `lang` set to a supported expression engine id (for example `dataweave`) and `expr` that evaluates to the effective string at runtime.
- `duration`:
  - When a literal, MUST be an ISO-8601 duration string (for example `PT5M`, `PT1H`, `P1D`).
  - When a mapper, MUST evaluate to such a duration string at runtime.
- `until`:
  - When a literal, MUST be an RFC 3339 timestamp string.
  - When a mapper, MUST evaluate to such a timestamp string at runtime.
- `next`:
  - Required; MUST refer to a state id in the same journey.

Execution model
- Entering a timer:
  - When a journey instance enters a `type: timer` state, the engine:
    - Evaluates `duration` or `until` according to the XOR rule.
    - Computes an absolute due time `dueAt` from:
      - `now + duration` when `duration` is used, or
      - the parsed timestamp when `until` is used.
    - Persists:
      - The journey instance state (including `context`, `currentState`, and metadata).
      - A durable timer record that includes `journeyId`, the timer state id, and `dueAt`.
    - Returns control to the host platform; no worker thread or HTTP request is blocked while waiting for the timer.
- Firing a timer:
  - When `dueAt` is reached (or shortly after, depending on scheduler behaviour), the scheduler:
    - Locates the timer record.
    - Loads the journey instance if it is still `RUNNING`.
    - If the instance is still at the same timer state id, resumes execution and transitions to `next`.
  - If the journey has already reached a terminal outcome (for example due to cancellation or a global execution timeout), the engine MUST treat the timer record as stale and MUST NOT resume the run.
- Global execution deadlines:
  - Timer states do not extend or override `spec.execution.maxDurationSec`:
    - If a timer’s effective `dueAt` is after the global deadline, the journey MAY still be accepted, but the run will terminate via the global timeout if it fails to complete before the deadline.
  - Reaching the global execution deadline while a timer is active uses the existing timeout semantics (`spec.execution.onTimeout`); there is no separate timer-specific error.

Interaction, cancellation, and parallelism
- Non-interactive:
  - Timer states do not expose a step endpoint.
  - They do not have `input.schema`, `response`, `timeoutSec`, or `onTimeout`.
  - Timer states do not implicitly mutate `context` (for example they do not add `firedAt` by default).
- Cancellation:
  - Journey-level cancellation (for example via `_links.cancel` when `spec.lifecycle.cancellable == true`) remains available while paused at a timer.
  - Cancelling a journey at a timer terminates the run with the usual cancellation outcome; the engine MUST discard any associated timer record.
- Parallel flows:
  - To model patterns like “wait for user input OR timeout after N minutes”, authors SHOULD use `type: parallel`:
    - One branch uses `type: timer` to represent the timeout path.
    - Another branch uses `type: wait` or `type: webhook` (or other states) to represent user/system interactions.
  - A subsequent join or `choice`/`transform` state can inspect `context` to decide which branch “won”.

Validation
- `type: timer` MUST NOT be used in specs with `kind: Api`.
- For `type: timer`:
  - Exactly one of `timer.duration` or `timer.until` MUST be present.
  - `timer.duration`, when present:
    - MUST be either:
      - A string that is a valid ISO-8601 duration, or
      - A `mapper` that evaluates to such a string.
  - `timer.until`, when present:
    - MUST be either:
      - A string that is a valid RFC 3339 timestamp, or
      - A `mapper` that evaluates to such a string.
  - `next` is required and MUST refer to a state id in `spec.states`.

### 12.4 Export mapping (steps)
- For each external-input state (`wait`, `webhook`), the exporter emits:
  - `POST /journeys/{journeyId}/steps/{stepId}` with request body schema = the state’s `input.schema`, when present.
  - `200` response schema:
    - When `response.schema` is absent: `JourneyStatus` as defined in `docs/3-reference/openapi/journeys.openapi.yaml`.
    - When `response.schema` is present: an `allOf` composition of `JourneyStatus` and the step-specific schema taken from `response.schema`, so that additional top-level fields are described explicitly.
- Journeys without external-input states do not emit `/steps/{stepId}` paths.

<a id="dsl-13-resilience-policies"></a>
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
    kind: httpCall:v1
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

<a id="dsl-14-transform-state"></a>
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
- When `target.kind == context` (default):
  - If `target.path` is provided, the mapper result MUST be a JSON object; it is written at `context.<path>` (overwriting any existing value).
  - If neither `target.path` nor `resultVar` is set, the mapper result MUST be a JSON object and replaces the entire `context`.
- When `target.kind == var`, the mapper result MAY be any JSON value (object, array, string, number, boolean, or null) and is stored under `context.<resultVar>`; other context fields remain unchanged.

Validation
- `transform.mapper.lang` must be set to a supported expression engine id (for example `dataweave`).
- Exactly one of (`target.path`, `resultVar`) may be omitted; if both are omitted, `context` replacement semantics apply.
- `resultVar`, when used, must match `[A-Za-z_][A-Za-z0-9_]*`.

### 14.1 Transform & expression style guidance

- Prefer **object-returning** transforms when updating `context`:
  - Use `target.kind: context` with a `path` when you are updating a specific subtree.
  - Avoid whole-context replacement except when the mapper is clearly modelling “new logical context”.
- Use `target.kind: var` for **scalars/arrays or temporary values**:
  - Map intermediate values into `context.<resultVar>` and read them from predicates or subsequent transforms.
  - This keeps `context` structured and avoids repeatedly reshaping the root.
- Keep expressions **pure** and focused:
  - All side effects (HTTP calls, events, cache writes, schedules) belong in task plugins, not expressions.
  - Use transforms and mappers only to compute values; do not model external I/O or retries in expressions.
- Keep predicates **small and stable**:
  - Prefer short, readable predicates in `choice` states that test a handful of fields.
  - For more complex logic, factor the mapping into a named mapper under `spec.mappers` and reuse it via `mapperRef` or write the value into `context` in a prior `transform` state, then branch on that value.

Example – normalising an HTTP error into RFC 9457 Problem Details (using DataWeave):

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


<a id="dsl-15-cache"></a>
## 15. Cache Task Plugin (`cache:v1`)

The cache task plugin provides a simple, cross-provider cache surface:
- DSL exposes a single logical cache per deployment via `task.kind: cache:v1`.
- The engine configures the concrete cache provider (in-memory, Redis, cloud cache, etc.) and its operational knobs.

### 15.1 Cache operations (task.kind: cache:v1)

```yaml
# Read from cache
type: task
task:
  kind: cache:v1
  operation: get
  key:
    mapper:
      lang: <engineId>           # e.g. dataweave
      expr: |
        context.userId
  resultVar: cachedUser          # value stored at context.cachedUser
next: <stateId>

# Write to cache
otherState:
  type: task
  task:
    kind: cache:v1
    operation: put
    key:
      mapper:
        lang: <engineId>         # e.g. dataweave
        expr: |
          context.userId
    value:
      mapper:
        lang: <engineId>         # e.g. dataweave
        expr: |
          context.profile
    ttlSeconds: 600              # optional per-call TTL override (seconds)
  next: <stateId>
```

Semantics (configuration)
- `operation`:
  - Must be `get` or `put`.
- `key.mapper`:
  - Evaluated with `context` bound to the current journey context.
  - Computes a string key; engines MUST coerce the mapper result to a string.
- `value.mapper` (for `operation: put`):
  - Evaluated with `context` bound to the current journey context.
  - Computes the value to be stored; the engine serialises it as JSON.
- `ttlSeconds`:
  - Optional per-call TTL override (seconds).
  - When omitted, the engine uses the default TTL from cache plugin configuration.

Semantics (operation)
- `operation: get`:
  - The plugin looks up the computed key in the configured cache.
  - When an entry is present and not expired, it deserialises the JSON value and assigns it to `context.<resultVar>`.
  - When no entry is present (or it has expired), it assigns `null` to `context.<resultVar>`.
- `operation: put`:
  - The plugin stores the computed value under the computed key with the effective TTL.
  - It does not write anything back into `context` beyond any engine-provided diagnostics.

Validation
- `task.kind` MUST be `cache:v1`.
- `operation` is required and MUST be either `get` or `put`.
- For `operation: get`:
  - `resultVar` is required and MUST match `[A-Za-z_][A-Za-z0-9_]*`.
  - `value` and `ttlSeconds` MAY be omitted; if present, they are ignored.
- For `operation: put`:
  - `value` is required.
  - `resultVar` MUST be omitted.
  - `ttlSeconds`, when present, MUST be an integer ≥ 1.

Notes
- This section defines the DSL surface only; cache provider choice and operational settings (default TTL, memory limits, eviction policy, key prefix) are configured at the engine level under the cache plugin configuration and are not exposed in the DSL.

### 15.2 Context vs Cache

- **Context**
  - JSON object attached to a single journey instance.
  - Mutated by `task`, `transform`, `wait`/`webhook`, etc.
  - Exists only for the lifetime of that journey and is never visible to other journeys.
- **Cache**
  - A single logical key–value store configured per deployment via the cache plugin.
  - Logically external to any single journey; its lifetime is independent of individual runs.
  - Accessed via `task.kind: cache:v1` using a key mapper and optional value/TTL.
  - By default it is cross‑journey: any journey that knows the key structure can read/write entries.
- **Scope patterns**
  - Per‑journey behaviour: use journey‑specific keys (for example, `${context.journeyId}`) so only that instance’s entries are used, even though the underlying cache is shared.
  - Cross‑journey behaviour: use stable business identifiers (for example, `userId`, `paymentId`) as keys so multiple journeys can benefit from shared entries.



<a id="dsl-16-parallel-state"></a>
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
            kind: httpCall:v1
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
            kind: httpCall:v1
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

<a id="dsl-17-bindings"></a>
## 17. Inbound Bindings (spec.bindings)

Inbound bindings describe how external transports (HTTP and, in this version, experimentally WebSocket, gRPC, queue/message consumers, and CLI/batch invocations) expose journeys and APIs and how inbound metadata is projected into `context`. The engine core remains transport-agnostic; bindings adapt transports onto the logical operations “start journey/API” and “submit external-input step”.

HTTP binding (`spec.bindings.http`) is normative for both `kind: Journey` and `kind: Api`. WebSocket binding (`spec.bindings.websocket`) is introduced as an experimental binding for `kind: Journey` only. gRPC binding (`spec.bindings.grpc`) is introduced as an experimental binding for `kind: Api` only. Queue/message binding (`spec.bindings.queue`) is introduced as an experimental binding for `kind: Journey` only. CLI binding (`spec.bindings.cli`) is introduced as an experimental binding for both `kind: Journey` and `kind: Api`. Cloud-function binding (`spec.bindings.function`) is introduced as an experimental binding for both `kind: Journey` and `kind: Api`.

### 17.1 HTTP binding – overview

HTTP binding configuration lives under `spec.bindings.http`:

```yaml
spec:
  bindings:
    http:
      route:                    # kind: Api only – HTTP surface
        path: <string>          # e.g. /apis/get-user-public; defaults to /apis/{metadata.name}
        method: <string>        # e.g. POST; initial version supports POST only

      start:                    # start request bindings
        headersToContext:
          X-User-Id: userId
          X-Tenant-Id: tenantId
        headersPassthrough:
          - from: traceparent
            to: traceparent
        queryToContext:
          tenant: tenantId
          debug: debugFlag

      steps:                    # step request bindings, keyed by external-input state id
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
- Start bindings:
  - `spec.bindings.http.start` applies when a start request is invoked:
    - `POST /api/v1/journeys/{journeyName}/start` for `kind: Journey`, or
    - `POST /api/v1/apis/{apiName}` (or `spec.bindings.http.route.path` when present) for `kind: Api`.
  - `headersToContext`: for each `headerName: contextField` entry, if the request has the header, its value is copied to `context.<contextField>`. Missing headers are ignored; requiredness should be expressed via JSON Schema on the journey `context`, not here.
  - `headersPassthrough`: for each mapping, the engine conceptually propagates the inbound header value from the start request to subsequent HTTP tasks as the specified outbound header, *even if it is not stored in `context`*.
    - This is syntactic sugar for header value propagation; it behaves as if the value flowed via an internal, reserved field.
    - Passthrough values are request-scoped:
      - They apply only to outbound HTTP tasks executed while processing the current inbound request (start or step submission), and
      - They MUST NOT be persisted in the journey instance across `wait`/`webhook` boundaries or across process restarts.
      Use `headersToContext` (and/or an explicit `transform`) for header values that must persist across multiple requests in the same journey.
  - `queryToContext`: for each `paramName: contextField` entry, if the request has the query parameter, its (string) value is copied to `context.<contextField>`. Missing params are ignored; requiredness should be expressed via JSON Schema on `context` or a dedicated input schema, not here.
- Step bindings:
  - `spec.bindings.http.steps.<stepId>` applies when `POST /api/v1/journeys/{journeyId}/steps/{stepId}` is called for the configured `stepId`.
  - `headersToContext` behaves as for the start request: copy inbound headers into `context` before evaluating `wait`/`webhook` predicates or mappers.
  - `headersPassthrough` behaves as for start: propagate header values from the step request to subsequent HTTP tasks executed for that step submission, without requiring an explicit `context` field.
  - `queryToContext` behaves as for the start request: copy inbound query parameter values into `context` before evaluating `wait`/`webhook` predicates or mappers.

### 17.2 Usage guidance
- Use `spec.bindings.http.start.headersToContext` / `.steps.*.headersToContext` when the header:
  - Influences branching, transformations, or other behaviour.
  - Needs to be logged, inspected, or included in downstream payloads.
  - Should be part of the journey’s replay/debug story (visible in `context`).
- Use `spec.bindings.http.start.headersPassthrough` / `.steps.*.headersPassthrough` when the header:
  - Is purely transport-level (for example, tracing, correlation), and
  - Does not need to be read by the journey definition itself.
- It is valid to use both: bind a header into `context` and also pass it through, if you need both visibility and propagation.

Validation
- `spec.bindings.http.start.headersToContext` and `spec.bindings.http.steps.*.headersToContext` must map header names to non-empty context field names.
- `spec.bindings.http.start.queryToContext` and `spec.bindings.http.steps.*.queryToContext`, when present, must map query parameter names to non-empty context field names.
- `headersPassthrough` entries must provide `from` and `to` header names (non-empty strings).
- Step ids under `spec.bindings.http.steps` must refer to external-input states (`wait`/`webhook`).

Notes
- This section defines the HTTP binding model; concrete enforcement and header sets are implemented in the engine/API layer.

### 17.3 WebSocket binding – overview (Journeys only, experimental)

WebSocket binding allows journeys to be driven over a long-lived, bidirectional WebSocket connection instead of individual HTTP calls. It is only valid for `kind: Journey` in this version; `kind: Api` remains HTTP-only.

Shape:

```yaml
spec:
  bindings:
    websocket:
      endpoint:                   # optional – where clients connect
        path: <string>            # e.g. /ws/journeys/auth-user-info; defaults to /ws/journeys/{metadata.name}
        subprotocol: <string>     # optional WebSocket subprotocol identifier

      start:                      # how to start journeys over this binding
        messageType: <string>     # e.g. startJourney

      steps:                      # how to submit external-input steps over this binding
        <stepId>:
          messageType: <string>   # e.g. submitStep
```

Semantics (high level)
- Applicability:
  - `spec.bindings.websocket` is only valid for `kind: Journey`; it MUST be rejected for `kind: Api`.
  - A journey MAY configure both HTTP and WebSocket bindings; they are alternative ways to reach the same logical journey definition.
- Endpoint:
  - If `endpoint.path` is omitted, the canonical path is `/ws/journeys/{metadata.name}`.
  - If `endpoint.subprotocol` is set, clients SHOULD negotiate this subprotocol when opening the WebSocket; journeys SHOULD treat connections without the expected subprotocol as invalid.
- Start messages:
  - `start.messageType` defines the logical message type used to initiate new journey instances over WebSocket.
  - The exact JSON envelope for start messages (fields, routing metadata, payload) is defined by the WebSocket Journeys API reference and the feature spec for WebSocket bindings; conceptually, each start message maps to the same logical “start journey” operation as `POST /api/v1/journeys/{journeyName}/start`.
  - The initial `context` for a journey started via WebSocket is derived from the message payload in the same way it is derived from the HTTP request body for the Journeys API, subject to `spec.input.schema` validation when present.
- Step messages:
  - `steps.<stepId>.messageType` defines the logical message type used when submitting external-input payloads to a specific `wait`/`webhook` state.
  - The exact JSON envelope for step messages is defined by the WebSocket Journeys API reference; conceptually, each such message maps to the same logical “submit step” operation as `POST /api/v1/journeys/{journeyId}/steps/{stepId}`.
- Outgoing messages:
  - When a journey with a WebSocket binding is started over WebSocket, the engine MAY emit:
    - Status messages when the journey changes state (for example updates to `JourneyStatus`), and
    - Outcome messages when the journey reaches a terminal outcome (`JourneyOutcome`),
    over the same WebSocket connection.
  - The exact JSON shapes and routing rules for status/outcome messages are defined by the WebSocket Journeys API reference and the corresponding feature spec; the DSL only records that WebSocket is an inbound binding for start and step submissions.

Validation
- `spec.bindings.websocket`:
  - MUST NOT be present for `kind: Api`.
  - MAY be present for `kind: Journey` alongside `spec.bindings.http`.
- `endpoint.path`, when present, MUST be a non-empty string starting with `/` and without a host.
- `start.messageType`, when present, MUST be a non-empty string.
- `steps.<stepId>.messageType`, when present, MUST be a non-empty string, and `<stepId>` MUST refer to an external-input state (`wait`/`webhook`) in `spec.states`.

Notes
- This section defines the DSL shape for WebSocket binding and its relationship to existing HTTP semantics. Concrete message envelopes, error mapping, and reconnection semantics are defined in the WebSocket binding feature spec and Journeys WebSocket API reference.

### 17.5 Queue/message binding – overview (Journeys only, experimental)

Queue/message binding allows journeys to be triggered by messages consumed from external messaging systems (for example Kafka, SQS, NATS, Azure Service Bus, SNS, Pub/Sub). It is only valid for `kind: Journey` in this version; `kind: Api` remains request/response over HTTP or gRPC.

The DSL treats queue/message bindings in a provider-agnostic way: journeys declare logical channels; engine/platform configuration maps those channels onto concrete providers, topics, subscriptions, or queues.

Shape:

```yaml
spec:
  bindings:
    queue:
      starts:
        - channel: <string>      # logical channel that starts new journey instances

      steps:
        <stepId>:
          channel: <string>      # logical channel for step submissions to this state
```

Semantics (high level)
- Applicability:
  - `spec.bindings.queue` is only valid for `kind: Journey`; it MUST be rejected for `kind: Api`.
  - A journey MAY configure HTTP, WebSocket, and queue bindings simultaneously; they are alternative ways to reach the same logical definition.
- Channels:
  - `channel` identifies a logical message source (for example `orders.created`, `orders.approvals`) that the engine/platform maps to a concrete provider/topic/subscription.
  - Channel-to-provider mapping (Kafka topics, SQS queue names, NATS subjects, etc.) is defined in engine/platform configuration, not in the DSL.
- Starts:
  - For each entry in `starts`:
    - Messages consumed from the corresponding `channel` are treated as **start events** for this journey definition.
    - The message payload and attributes are mapped to the initial `context` according to the queue binding feature spec and platform configuration, subject to validation against `spec.input.schema` when present.
    - Each message results in a new journey instance (fire-and-forget semantics); request/response patterns over messaging (for example `reply-to`) are modelled via normal journey behaviour and outbound connectors, not special queue-binding semantics.
- Steps:
  - For each `steps.<stepId>.channel`:
    - Messages consumed from that `channel` are treated as **step submission events** for the external-input state identified by `<stepId>`.
    - The engine MUST be able to determine which journey instance the message targets (for example via `journeyId` in message metadata); the exact mapping is defined in the queue binding feature spec and engine configuration.
    - Message payloads are validated against the state’s `input.schema` when present and applied as if they were submitted via the HTTP Journeys API step endpoint.

Validation
- `spec.bindings.queue`:
  - MUST NOT be present for `kind: Api`.
  - MAY be present for `kind: Journey` alongside other bindings.
- `starts[*].channel` MUST be non-empty strings.
- `steps.<stepId>.channel` MUST be non-empty strings, and `<stepId>` MUST refer to an external-input state (`wait`/`webhook`) in `spec.states`.

Notes
- This section defines the DSL shape and high-level semantics for queue/message binding. Provider-specific details (Kafka vs SQS vs NATS, consumer groups, retry/dead-letter policies, etc.) and the exact mapping between message metadata and `context`/`journeyId` are defined in the queue binding feature spec and engine/platform configuration.

### 17.4 gRPC binding – overview (Apis only, experimental)

gRPC binding allows `kind: Api` definitions to be exposed as unary gRPC methods instead of (or in addition to) HTTP endpoints. It is only valid for `kind: Api` in this version; `kind: Journey` continues to use the HTTP Journeys API (and optionally WebSocket) as its external surface.

Shape:

```yaml
spec:
  bindings:
    grpc:
      service: <string>           # fully-qualified gRPC service name, e.g. journeys.api.UserApi
      method: <string>            # unary method name for this Api, e.g. GetUserPublic
```

Semantics (high level)
- Applicability:
  - `spec.bindings.grpc` is only valid for `kind: Api`; it MUST be rejected for `kind: Journey`.
  - An API MAY configure both HTTP and gRPC bindings; they are alternative ways to reach the same logical definition.
- Service and method:
  - `service` names the gRPC service that exposes this Api; when omitted, tooling MAY derive it from `metadata.name` using a platform-specific naming convention.
  - `method` names the unary RPC method that maps to this Api; when omitted, tooling MAY derive it from `metadata.name` using a platform-specific convention.
- Request/response mapping:
  - Each gRPC call to the configured `service/method` is conceptually equivalent to a single HTTP call to the Api’s HTTP endpoint:
    - The gRPC request message is mapped to the initial `context` according to `spec.input.schema` and the gRPC API reference for this binding.
    - Execution proceeds from `spec.start` to a terminal state (`succeed`/`fail`) without external input, as required for `kind: Api`.
    - The gRPC response message is mapped from the final result (`spec.output.schema`, `spec.errors`, and any configured status mapping) according to the gRPC API reference.
- Status codes:
  - Mapping between HTTP status codes (selected via `spec.bindings.http.apiResponses` when present) and gRPC status codes is defined in the gRPC API reference and platform configuration; the DSL does not introduce a separate gRPC status-mapping block.

Validation
- `spec.bindings.grpc`:
  - MUST NOT be present for `kind: Journey`.
  - MAY be present for `kind: Api` alongside `spec.bindings.http`.
- `service`, when present, MUST be a non-empty string.
- `method`, when present, MUST be a non-empty string.

Notes
- This section defines the DSL shape for gRPC binding and its relationship to existing Api semantics. Concrete `.proto` definitions, message field mappings, and status-code translation rules are defined in the gRPC binding feature spec and the Journeys gRPC API reference.

### 17.6 CLI binding – overview (Journeys and Apis, experimental)

CLI binding models local, non-networked invocation of journeys and APIs via a command-line interface or batch job runner. It is valid for both `kind: Journey` and `kind: Api` and is primarily descriptive: it documents that a definition is intended to be runnable via the JourneyForge CLI and batch tools.

Shape:

```yaml
spec:
  bindings:
    cli: {}   # reserved for CLI/batch invocation; no fields in v1
```

Semantics (high level)
- Applicability:
  - `spec.bindings.cli` MAY be present for both `kind: Journey` and `kind: Api`.
  - The CLI MAY still run journeys/APIs that omit `spec.bindings.cli`; presence is advisory metadata, not a hard requirement.
- Invocation:
  - CLI tools (for example `journeyforge run` or `journeyforge api`) use:
    - `spec.input.schema` to validate and interpret input provided via stdin or files.
    - `spec.output.schema` and the `JourneyOutcome`/API response model to shape output printed to stdout.
  - Each CLI invocation is conceptually equivalent to:
    - Starting a journey instance and waiting for a terminal outcome (or status snapshot), or
    - Invoking a `kind: Api` and returning its response, without exposing HTTP or gRPC transport details.
- Jobs:
  - Batch schedulers (cron, CI/CD, Kubernetes `CronJob`, etc.) invoke the same CLI commands on a schedule or as part of pipelines.
  - From the engine’s perspective, there is no difference between a human and a scheduler invoking the CLI; both are covered by the CLI binding.

Validation
- `spec.bindings.cli`, when present, MUST be either an empty object or a future extension object defined by CLI/ops features; this version does not define any fields.

Notes
- This section defines the DSL hook for CLI/batch invocation. Concrete CLI commands, flags, and process-level contracts (stdin/stdout/exit codes) are defined in CLI documentation and operations runbooks, not in the DSL.

### 17.7 Cloud-function binding – overview (Journeys and Apis, experimental)

Cloud-function binding models deployments where journeys and APIs are exposed via cloud function platforms such as AWS Lambda, Google Cloud Functions, or Azure Functions. It reuses the logical semantics of HTTP and `kind: Api`/`kind: Journey` entrypoints; the function platform and its triggers are operational concerns.

Shape:

```yaml
spec:
  bindings:
    function: {}   # reserved for cloud-function deployment; no fields in v1
```

Semantics (high level)
- Applicability:
  - `spec.bindings.function` MAY be present for both `kind: Journey` and `kind: Api`.
  - The engine and DSL semantics are unchanged; cloud-function bindings are thin adapters on top of existing entrypoints.
- Invocation:
  - For HTTP-triggered functions (for example API Gateway + Lambda, HTTP-triggered GCF/Azure Functions):
    - The provider-specific event object (headers, path, body) is normalised to the same logical HTTP request shape used by `spec.bindings.http`.
    - The binding invokes the same logical operations as the HTTP binding:
      - `start` for journeys,
      - `callApi` for `kind: Api`.
    - The function return value is constructed from the resulting `JourneyOutcome`/`JourneyStatus` or Api response in a provider-specific way (for example Lambda proxy integration response).
  - For non-HTTP triggers (for example some Pub/Sub or queue-triggered functions), cloud-function bindings SHOULD reuse the semantics of queue bindings (see §17.5) rather than defining new behaviour.
- Deployment:
  - `spec.bindings.function` is provider-neutral; it does not name AWS/GCP/Azure resources or trigger types.
  - Mapping from this binding to concrete function deployments (function names, handlers, triggers, IAM roles, etc.) is defined in engine/platform configuration and deployment tooling.

Validation
- `spec.bindings.function`, when present, MUST be an object; in this version it MUST NOT define any fields.

Notes
- This section defines the DSL hook for cloud-function deployment and its relationship to existing HTTP and queue bindings. Provider-specific deployment details (API Gateway configuration, function runtimes, triggers) and error/status mapping rules are defined in cloud-function binding feature specs and deployment documentation, not in the DSL.

<a id="dsl-18-inbound-auth"></a>
## 18. Inbound Auth (task plugins)

JourneyForge models inbound authentication and authorisation as explicit, versioned `task` plugins executed as part of the state graph (for example `jwtValidate:v1`, `mtlsValidate:v1`, `apiKeyValidate:v1`). The DSL does not define a binding-level `spec.bindings.http.security` policy model.

Transport-level enforcement (gateway/ingress/service-mesh/function platform auth) is outside the DSL. Journeys/APIs that require authentication MUST include appropriate auth task states in their graphs.

### 18.1 Where auth runs

- `kind: Api`: auth tasks are typically the first states in the graph, before any downstream calls.
- `kind: Journey`:
  - For `/start`, journeys MAY validate caller identity at start if they need a subject-bound instance.
  - For external-input steps (`wait`, `webhook`), journeys SHOULD validate on each step submission by placing auth tasks immediately after the external-input state.

### 18.2 Pattern for external-input steps

When a step submission is accepted (schema-valid), the engine copies the submitted body into `context.payload` (§12). Auth tasks can then inspect inbound credentials and decide whether to accept the submission.

Typical pattern:

```yaml
waitForInput:
  type: wait
  wait:
    channel: manual
    input: { schema: <JsonSchema> }
  next: checkAuth

checkAuth:
  type: task
  task:
    kind: jwtValidate:v1
  next: routeAuth

routeAuth:
  type: choice
  choices:
    - when:
        predicate:
          lang: dataweave
          expr: |
            context.auth.jwt.problem == null
      next: mainFlow
  default: waitForInput
```

### 18.3 Failure semantics (auth tasks)

- Business validation failures (missing/invalid token, untrusted certificate, unknown API key) MUST NOT fail the run by themselves. Instead, auth tasks MUST write a Problem Details object into `context.auth.<mechanism>.problem` (or the configured `authVar` namespace), and journeys route/deny explicitly via normal control flow.
- Misconfiguration and engine-side issues (for example missing profile, invalid trust anchors, unusable key material) are internal configuration errors and MUST surface as internal Problems (failing the run) rather than being treated as “unauthenticated”.

### 18.4 Mapping inbound metadata into `context`

- Use the HTTP binding (`spec.bindings.http.start.headersToContext`, `spec.bindings.http.steps.*.headersToContext`, and query equivalents) when the journey needs specific inbound headers/query params in `context` for downstream calls or decision logic.
- Auth task plugins MUST NOT expose raw secret material (for example API key values) into `context` or logs; they may expose only non-sensitive identifiers (for example `keyId`) and Problem Details objects on failure (see §7 and ADR‑0025/ADR‑0026).

### 18.5 Mapping auth into journey context

Authentication data becomes available to journeys via:
- JWT validation task plugin `jwtValidate:v1` (section 18.6), which writes into `context.auth.jwt.*` (or a caller-configured namespace) when validation succeeds and writes a Problem Details view into `*.problem` when validation fails.
- mTLS validation task plugin `mtlsValidate:v1` (section 18.7), which writes into `context.auth.mtls.*` (or a caller-configured namespace) when validation succeeds and writes a Problem Details view into `*.problem` when validation fails.
- API key validation task plugin `apiKeyValidate:v1` (section 18.8), which writes into `context.auth.apiKey.*` (or a caller-configured namespace) when validation succeeds and writes a Problem Details view into `*.problem` when validation fails.

The engine MUST populate the following views under `context.auth` so DataWeave expressions and `transform` states can use authentication data:

- JWT validation (`jwtValidate:v1` with default target):
  - `context.auth.jwt.header` – JOSE header (non-sensitive fields only, for example `alg`, `kid`, `typ`).
  - `context.auth.jwt.claims` – decoded claims object as JSON (e.g., `sub`, `scope`, `aud`, `iss`).
  - `context.auth.jwt.problem` – RFC 9457 Problem Details object describing the most recent JWT validation failure (when validation fails). When `problem` is present, `header` and `claims` MUST be absent. When validation succeeds, `problem` MUST be absent.
- mTLS validation (`mtlsValidate:v1` with default target):
  - `context.auth.mtls.subjectDn` – subject distinguished name of the validated client certificate.
  - `context.auth.mtls.issuerDn` – issuer DN (optional).
  - `context.auth.mtls.fingerprintSha256` – certificate fingerprint (optional, for correlation/logging).
  - `context.auth.mtls.problem` – RFC 9457 Problem Details object describing the most recent mTLS validation failure. When `problem` is present, `subjectDn`/`issuerDn`/`fingerprintSha256` MUST be absent. When validation succeeds, `problem` MUST be absent.
- API key validation (`apiKeyValidate:v1` with default target):
  - `context.auth.apiKey.keyId` – stable identifier for the validated key; the raw key MUST NOT be exposed.
  - `context.auth.apiKey.problem` – RFC 9457 Problem Details object describing the most recent API key validation failure. When `problem` is present, `keyId` MUST be absent. When validation succeeds, `problem` MUST be absent.

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

This keeps `context` as the canonical place for data that influences journey behaviour, while:
- `jwtValidate:v1`, `mtlsValidate:v1`, and `apiKeyValidate:v1` govern inbound authentication, and
- the HTTP binding (`spec.bindings.http`) governs how inbound metadata becomes available to the journey instance and downstream calls.

### 18.6 JWT validation task plugin (`jwtValidate:v1`)

Journeys use the `jwtValidate:v1` task plugin to perform JWT validation as part of the state graph. This is useful for:
- Enforcing JWT-based authentication for entry paths and internal steps (for example as the first state on a journey/API).
- Applying additional, step-level JWT checks beyond transport-level gateway enforcement.
- Extracting claims into `context` to drive downstream logic.

Shape (under a `type: task` state):

```yaml
checkAuth:
  type: task
  task:
    kind: jwtValidate:v1

    # Optional profile selector – when omitted, defaults to "default"
    profile: default

    # Optional source override; when omitted, the profile’s source is used
    # source:
    #   location: header       # header | query | cookie
    #   name: Authorization
    #   scheme: Bearer        # optional; strip this prefix when present

    # Optional override for where to store auth data
    # - when omitted, plugin writes under context.auth.jwt.*
    # - when set, plugin writes under context.<authVar>.jwt.*
    authVar: auth
  next: mainFlow
```

Fields
- `profile`:
  - Optional string; when omitted, the effective profile name is `"default"`.
  - Profiles are defined in engine configuration under a plugin-specific subtree (for example `plugins.jwtValidate.profiles.<name>`); they typically capture issuer/audience/JWKS settings, clock skew, required claims, and default token source.
- `source` (optional override):
  - `location`: where to read the token from:
    - `header` – read from an HTTP header.
    - `query` – read from a query parameter.
    - `cookie` – read from a cookie.
  - `name`: header, query parameter, or cookie name.
  - `scheme` (optional): string prefix to strip from the raw value (for example `Bearer` for `Authorization` headers).
  - When `source` is omitted in the DSL, the plugin uses the source configured for the selected `profile`. If neither the DSL nor the profile provides a usable source, the configuration is invalid and MUST be treated as an internal configuration error.
- `authVar` (optional override):
  - When omitted, successful validation populates `context.auth.jwt.header` and `context.auth.jwt.claims` as described in section 18.5.
  - When set to a non-empty string (for example `auth`), successful validation populates `context.<authVar>.jwt.header` and `context.<authVar>.jwt.claims`. Engines MAY still mirror selected fields into `context.auth` for compatibility, but the primary write target MUST be the configured namespace.

Semantics
- Execution:
  - `jwtValidate:v1` executes synchronously as a normal `task` state and MUST NOT introduce implicit waits; it reads the current HTTP request context (when available) and the current `context`.
  - It MAY be used in both `kind: Journey` and `kind: Api` specs. When no HTTP request binding is available (for example in a purely internal state), the plugin behaves as if the token were missing.
- Token sourcing:
  - The effective token source is determined by:
    1. `task.source` when present; otherwise
    2. The configured `source` for the selected `profile`.
  - The plugin reads the raw token value from the effective source and, when `scheme` is present, strips the `"<scheme> "` prefix (case-insensitive) before validation.
- Validation:
  - Validation behaviour (issuer, audience, key material, claim constraints, clock skew) is driven by the selected profile and engine configuration, not by the DSL.
  - On successful validation, the plugin MUST populate:
    - `*.header` with non-sensitive JOSE header fields (for example `alg`, `kid`, `typ`).
    - `*.claims` with the decoded claims object (for example `sub`, `scope`, `aud`, `iss`).
  - The plugin MUST honour privacy rules from ADR-0025 and MUST NOT log raw token values or embed them in `context`.
- Authorisation:
  - `jwtValidate:v1` is responsible only for authentication (validating the token and projecting claims into `context.auth.jwt.*` or `context.<authVar>.jwt.*`); journeys express authorisation rules explicitly via predicates and `choice`/`fail` states over `context` and `context.auth.*`.
  - Common patterns include:
    - Normalising subject and scopes/roles into business fields (for example `context.userId`, `context.scopes`) via `transform` states, and
    - Using `choice` predicates to enforce scopes/roles/subject-based rules (for example self-service checks as in `subject-step-guard`).

Error handling
- On business validation failure (missing/invalid token), `jwtValidate:v1` MUST NOT fail the run by itself. Instead, it MUST write a Problem Details object to:
  - `context.auth.jwt.problem` when `authVar` is omitted, or
  - `context.<authVar>.jwt.problem` when `authVar` is set,
  and then continue to `next`. Journeys and APIs MUST implement the desired behaviour explicitly via `choice`/`transform` (for example: treat `JWT_TOKEN_MISSING` as anonymous, loop back to a `wait` state, or terminate via `fail`).
- The plugin MUST avoid stale auth data:
  - When `*.problem` is present, `*.header` and `*.claims` MUST be absent.
  - When validation succeeds and `*.header`/`*.claims` are present, `*.problem` MUST be absent.
- The plugin MUST use the following stable, fine-grained error codes for its primary business failure modes so journeys can branch on them or map them via `apiResponses`/`spec.errors`:
  - `JWT_TOKEN_MISSING` – no token present at the configured source.
  - `JWT_TOKEN_MALFORMED` – token cannot be parsed as a valid JWT.
  - `JWT_SIG_INVALID` – signature validation failed.
  - `JWT_KID_NOT_FOUND` – key id not found in the configured key set.
  - `JWT_EXPIRED` – token `exp` is in the past (beyond allowed clock skew).
  - `JWT_NOT_YET_VALID` – token `nbf` is in the future (beyond allowed clock skew).
  - `JWT_AUDIENCE_MISMATCH` – token `aud` does not match expected audience(s).
  - `JWT_ISSUER_MISMATCH` – token `iss` does not match expected issuer.
  - `JWT_CLAIMS_INVALID` – required claim constraints are not satisfied.
- These error codes are conveyed via Problem Details using both:
  - A stable `code` extension member set to the exact error code string (for example `"JWT_SIG_INVALID"`), and
  - A stable `type` URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/plugins/jwt/sig-invalid`).
  Journeys SHOULD treat unknown codes as generic JWT validation failures.
- Misconfiguration and engine-side issues (for example missing profile or unusable token source) are considered internal configuration errors and MUST surface as internal Problems with separate `JWT_CONFIG_*` codes (for example `JWT_CONFIG_PROFILE_MISSING`, `JWT_CONFIG_KEYS_UNUSABLE`, `JWT_CONFIG_INVALID`), not as journey-authored business failures.
### 18.7 mTLS validation task plugin (`mtlsValidate:v1`)

Journeys use the `mtlsValidate:v1` task plugin to validate client TLS certificates as part of the state graph. This is useful for:
- Enforcing mTLS-based authentication for entry paths (for example as the first state on a journey/API).
- Applying additional, step-level mTLS checks beyond gateway enforcement.
- Extracting certificate metadata into `context` to drive downstream logic.

Shape (under a `type: task` state):

```yaml
checkClientCert:
  type: task
  task:
    kind: mtlsValidate:v1

    # Optional profile selector – when omitted, defaults to "default"
    profile: default

    # Optional override for trusted roots – inline PEMs, no external refs
    # trustAnchors:
    #   - pem: |
    #       -----BEGIN CERTIFICATE-----
    #       ...
    #       -----END CERTIFICATE-----
    #   - pem: |
    #       -----BEGIN CERTIFICATE-----
    #       ...
    #       -----END CERTIFICATE-----

    # Optional global rule: how to treat certificates that chain to trustAnchors
    # When omitted, the profile’s setting is used.
    # allowAnyFromTrusted: true | false

    # Optional filters; when provided, all present filters use AND semantics.
    # If omitted, profile defaults apply; if both profile and task omit filters,
    # behaviour falls back to allowAnyFromTrusted.
    # allowSubjects:
    #   - "CN=journey-client,OU=Journeys,O=Example Corp,L=Zagreb,C=HR"
    # allowSans:
    #   - dns: api.example.com
    #   - dns: callbacks.example.com
    # allowSerials:
    #   - "01AB..."

    # Optional override for where to store auth data
    # - when omitted, plugin writes under context.auth.mtls.*
    # - when set, plugin writes under context.<authVar>.mtls.*
    authVar: auth
  next: nextState
```

Fields
- `profile`:
  - Optional string; when omitted, the effective profile name is `"default"`.
  - Profiles are defined in engine configuration under a plugin-specific subtree (for example `plugins.mtlsValidate.profiles.<name>`); they typically capture default `trustAnchors`, `allowAnyFromTrusted`, and optional default filters.
- `trustAnchors` (optional override):
  - List of inline PEM-encoded certificates under `pem`.
  - When present, overrides the profile’s `trustAnchors` for this task.
- `allowAnyFromTrusted` (optional override):
  - Boolean; when set, overrides the profile’s default for this task.
  - Controls whether certificates that chain to one of the `trustAnchors` are accepted even when no additional filters are configured.
- `allowSubjects` (optional):
  - List of allowed subject distinguished names (strings).
  - When present, the validated certificate’s subject DN MUST match one of these entries.
- `allowSans` (optional):
  - List of allowed Subject Alternative Names; each entry MAY specify:
    - `dns`: allowed DNS name.
    - `ip`: allowed IP address (string form).
    - `uri`: allowed URI SAN.
  - When present, at least one SAN on the validated certificate MUST match one of these entries.
- `allowSerials` (optional):
  - List of allowed certificate serial numbers, expressed as strings (for example hex).
  - When present, the validated certificate’s serial number MUST match one of these entries.
- `authVar` (optional override):
  - When omitted, successful validation populates `context.auth.mtls.subjectDn`, `context.auth.mtls.issuerDn`, and `context.auth.mtls.fingerprintSha256` as described in section 18.5.
  - When set to a non-empty string (for example `auth`), successful validation populates `context.<authVar>.mtls.*` with the same fields. Engines MAY still mirror selected fields into `context.auth.mtls` for compatibility, but the primary write target MUST be the configured namespace.

Semantics
- Execution:
  - `mtlsValidate:v1` executes synchronously as a normal `task` state and MUST NOT introduce implicit waits; it reads the current HTTP request context (when available) and the current `context`.
  - It MAY be used in both `kind: Journey` and `kind: Api` specs. When no client certificate binding is available, the plugin behaves as if the certificate were missing.
- Certificate sourcing:
  - The plugin reads the client certificate chain (when present) from the HTTP request context provided by the engine; the exact binding is engine-defined and not surfaced in the DSL.
- Validation:
  - The effective `trustAnchors`, `allowAnyFromTrusted`, and filter sets are computed by taking the selected profile and applying any task-level overrides.
  - The plugin MUST:
    - Verify that the presented certificate chain is valid and roots in one of the effective `trustAnchors`.
    - Enforce all present filters:
      - If `allowSubjects` is set, the subject DN MUST match one of the entries.
      - If `allowSans` is set, at least one SAN on the certificate MUST match an entry.
      - If `allowSerials` is set, the certificate serial MUST match an entry.
  - When no filters are configured (neither in profile nor task), the effective behaviour falls back to `allowAnyFromTrusted`:
    - If `true`, any certificate that chains to a trusted anchor is accepted.
    - If `false`, a certificate that chains correctly but has no matching filters is rejected.
  - On successful validation, the plugin MUST populate:
    - `*.subjectDn` – subject distinguished name of the validated client certificate.
    - `*.issuerDn` – issuer distinguished name (optional).
    - `*.fingerprintSha256` – certificate fingerprint (optional, for correlation/logging).
  - The plugin MUST honour privacy rules from ADR-0025 and MUST NOT log raw certificate bytes.
- Authorisation:
  - `mtlsValidate:v1` is responsible only for validating client certificates and projecting selected metadata into `context.auth.mtls.*` or `context.<authVar>.mtls.*`; journeys express any additional authorisation rules (for example subject-based access, per-tenant SAN policies) via predicates and `choice`/`fail` states over `context` and `context.auth.*`.

Error handling
- On business validation failure (missing/invalid/untrusted certificate), `mtlsValidate:v1` MUST NOT fail the run by itself. Instead, it MUST write a Problem Details object to:
  - `context.auth.mtls.problem` when `authVar` is omitted, or
  - `context.<authVar>.mtls.problem` when `authVar` is set,
  and then continue to `next`. Journeys and APIs MUST implement the desired behaviour explicitly via `choice`/`transform` (for example: loop back to a `wait` state, accept specific certificate failures as anonymous, or terminate via `fail`).
- The plugin MUST avoid stale auth data:
  - When `*.problem` is present, `*.subjectDn`/`*.issuerDn`/`*.fingerprintSha256` MUST be absent.
  - When validation succeeds and `*.subjectDn` is present, `*.problem` MUST be absent.
- The plugin MUST use the following stable, fine-grained error codes for its primary business failure modes so journeys can branch on them or map them via `apiResponses`/`spec.errors`:
  - `MTLS_CERT_MISSING` – no client certificate was presented.
  - `MTLS_CERT_UNPARSEABLE` – certificate could not be parsed.
  - `MTLS_CERT_UNTRUSTED` – certificate chain does not validate against the effective `trustAnchors`.
  - `MTLS_CERT_REVOKED` – certificate is known to be revoked (when revocation checking is enabled and fails).
  - `MTLS_SUBJECT_DENIED` – subject DN does not match any `allowSubjects` entry.
  - `MTLS_SAN_DENIED` – no SAN on the certificate matches any `allowSans` entry.
  - `MTLS_SERIAL_DENIED` – serial number does not match any `allowSerials` entry.
- These error codes are conveyed via Problem Details using both:
  - A stable `code` extension member set to the exact error code string (for example `"MTLS_CERT_UNTRUSTED"`), and
  - A stable `type` URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/plugins/mtls/cert-untrusted`).
  Journeys SHOULD treat unknown codes as generic mTLS validation failures.
- Misconfiguration and engine-side issues (for example missing profile or unusable `trustAnchors` configuration) are considered internal configuration errors and MUST surface as internal Problems with separate `MTLS_CONFIG_*` codes (for example `MTLS_CONFIG_PROFILE_MISSING`, `MTLS_CONFIG_TRUST_ANCHORS_INVALID`, `MTLS_CONFIG_INVALID`), not as journey-authored business failures.

### 18.8 API key validation task plugin (`apiKeyValidate:v1`)

Journeys use the `apiKeyValidate:v1` task plugin to validate API keys as part of the state graph. This is useful for:
- Enforcing API-key-based authentication for entry paths and step submissions (for example as the first state on a journey/API, or immediately after a `wait`/`webhook` state).
- Projecting a non-sensitive key identifier into `context` for auditing and routing.

Shape (under a `type: task` state):

```yaml
checkApiKey:
  type: task
  task:
    kind: apiKeyValidate:v1

    # Optional profile selector – when omitted, defaults to "default"
    profile: default

    # Optional source override; when omitted, the profile’s source is used
    # source:
    #   location: header       # header | query
    #   name: X-Api-Key

    # Optional override for where to store auth data
    # - when omitted, plugin writes under context.auth.apiKey.*
    # - when set, plugin writes under context.<authVar>.apiKey.*
    authVar: auth
  next: nextState
```

Fields
- `profile`:
  - Optional string; when omitted, the effective profile name is `"default"`.
  - Profiles are defined in engine configuration under a plugin-specific subtree (for example `plugins.apiKeyValidate.profiles.<name>`); they typically capture key material references and a default key source.
- `source` (optional override):
  - `location`: where to read the API key from:
    - `header` – read from an HTTP header.
    - `query` – read from a query parameter.
  - `name`: header or query parameter name.
  - When `source` is omitted in the DSL, the plugin uses the source configured for the selected `profile`. If neither the DSL nor the profile provides a usable source, the configuration is invalid and MUST be treated as an internal configuration error.
- `authVar` (optional override):
  - When omitted, successful validation populates `context.auth.apiKey.keyId` as described in section 18.5.
  - When set to a non-empty string (for example `auth`), successful validation populates `context.<authVar>.apiKey.*`. Engines MAY still mirror selected fields into `context.auth` for compatibility, but the primary write target MUST be the configured namespace.

Semantics
- Execution:
  - `apiKeyValidate:v1` executes synchronously as a normal `task` state and MUST NOT introduce implicit waits; it reads the current HTTP request context (when available) and the current `context`.
  - It MAY be used in both `kind: Journey` and `kind: Api` specs. When no HTTP request binding is available, the plugin behaves as if the key were missing.
- Key sourcing:
  - The effective key source is determined by:
    1. `task.source` when present; otherwise
    2. The configured `source` for the selected `profile`.
  - The plugin reads the raw key value from the effective source. Engines SHOULD treat empty-string keys as missing.
- Validation:
  - Validation behaviour (key material, hashing, rotation, and any allowed key identifiers) is driven by the selected profile and engine configuration, not by the DSL.
  - On successful validation, the plugin MUST populate `*.keyId` with a stable identifier for the validated key. The raw key MUST NOT be exposed via `context` or logs.
  - The plugin MUST honour privacy rules from ADR-0025 and MUST NOT log raw API key values.
- Authorisation:
  - `apiKeyValidate:v1` is responsible only for authentication (validating the key and projecting a key identifier); journeys express authorisation rules explicitly via predicates and `choice`/`fail` states over `context` and `context.auth.*`.

Error handling
- On business validation failure (missing/invalid key), `apiKeyValidate:v1` MUST NOT fail the run by itself. Instead, it MUST write a Problem Details object to:
  - `context.auth.apiKey.problem` when `authVar` is omitted, or
  - `context.<authVar>.apiKey.problem` when `authVar` is set,
  and then continue to `next`.
- The plugin MUST avoid stale auth data:
  - When `*.problem` is present, `*.keyId` MUST be absent.
  - When validation succeeds and `*.keyId` is present, `*.problem` MUST be absent.
- The plugin MUST use the following stable, fine-grained error codes for its primary business failure modes so journeys can branch on them or map them via `apiResponses`/`spec.errors`:
  - `APIKEY_MISSING` – no key present at the configured source.
  - `APIKEY_INVALID` – key does not match any configured key material.
- These error codes are conveyed via Problem Details using both:
  - A stable `code` extension member set to the exact error code string (for example `"APIKEY_INVALID"`), and
  - A stable `type` URI that is 1:1 with the code (for example `https://journeyforge.dev/problem/plugins/apikey/invalid`).
  Journeys SHOULD treat unknown codes as generic API key validation failures.
- Misconfiguration and engine-side issues (for example missing profile or unusable key material configuration) are considered internal configuration errors and MUST surface as internal Problems with separate `APIKEY_CONFIG_*` codes (for example `APIKEY_CONFIG_PROFILE_MISSING`, `APIKEY_CONFIG_KEYS_UNUSABLE`, `APIKEY_CONFIG_INVALID`), not as journey-authored business failures.

<a id="dsl-19-outbound-http-auth"></a>
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
  - `spec.policies.httpClientAuth.default` is an optional documentation/authoring hint for the spec and tooling; it MUST NOT cause the engine to apply any outbound auth policy implicitly to HTTP tasks that omit `auth.policyRef`.
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
- Secret-bearing fields in outbound HTTP auth policies are opaque `secretRef` identifiers; engines MUST NOT expose resolved secret material to DataWeave expressions, interpolation, `context`, or logs; see section 7 (“Secrets & `secretRef`”) for the generic secrets model.

<a id="dsl-20-named-outcomes"></a>
## 20. Named Outcomes (spec.outcomes)

Named outcomes provide a way to classify terminal journey results into a small, stable vocabulary for clients, dashboards, and telemetry, without changing execution semantics.

```yaml
spec:
  outcomes:
    SucceededWithCacheHit:
      when:
        phase: SUCCEEDED
        predicate:
          lang: dataweave
          expr: |
            context.cachedUser != null
    FailedUpstream:
      when:
        phase: FAILED
        predicate:
          lang: dataweave
          expr: |
            context.error.code == 'https://example.com/probs/upstream-error'
```

Shape
- `spec.outcomes` is a map of outcome ids to classification rules.
- Each outcome has:
  - `when.phase`: `SUCCEEDED` or `FAILED` (must match `JourneyOutcome.phase`).
  - `when.predicate`: optional DataWeave predicate evaluated with `context` bound to the final journey context and `output`/`error` available as bindings if the engine supplies them.

Semantics (classification only)
- Outcomes do not affect execution: the journey still terminates on `succeed`/`fail` as usual.
- After a journey reaches a terminal phase, an engine MAY:
  - Evaluate outcomes in a deterministic order (for example, insertion order or lexicographic by id).
  - Select the first outcome whose `when.phase` matches and whose predicate evaluates to `true`.
  - Record the selected outcome id for telemetry or include it as an additional field (for example `outcomeId`) in `JourneyOutcome` without changing existing fields.
- If no outcome matches, the journey remains unclassified from the DSL’s perspective.

<a id="dsl-21-metadata-limits"></a>
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

  execution:
    defaultMaxDurationSec: 86400       # optional; default lifetime for specs without spec.execution
    maxDurationUpperBoundSec: 604800   # optional; hard upper bound for spec.execution.maxDurationSec
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
  - `execution.defaultMaxDurationSec`:
    - Optional; when present, expresses the desired default wall-clock lifetime (in whole seconds) for a single journey instance or API invocation when the journey/API spec does not declare `spec.execution`.
    - Engines that support global execution limits SHOULD enforce a deadline of at most this value for such specs, applying the execution-deadline semantics from section “2.4 Execution deadlines (spec.execution)” with an engine-defined timeout error mapping when `spec.execution.onTimeout` is not available.
    - The effective default for a given installation is conceptually `min(defaultMaxDurationSec, maxDurationUpperBoundSec, platformCaps)`; platform limits continue to take precedence.
  - `execution.maxDurationUpperBoundSec`:
    - Optional; when present, defines a hard upper bound (in whole seconds) that engines MUST apply when interpreting `spec.execution.maxDurationSec` in journey/API definitions.
    - When a spec sets `spec.execution.maxDurationSec` greater than this value, engines MUST clamp the effective global budget to `execution.maxDurationUpperBoundSec` (or stricter platform caps) while still using the spec’s `spec.execution.onTimeout` for the timeout outcome as described in section “2.4 Execution deadlines (spec.execution)” and ADR‑0007.
    - When absent, the effective upper bound is determined solely by platform/installation limits; authors MAY still use large `spec.execution.maxDurationSec` values, but engines MAY clamp them further based on configuration outside of `MetadataLimits`.

Validation
- Tools and the engine SHOULD treat violations of `MetadataLimits` as validation errors when
  possible (for example, during spec compilation or static analysis).
- During execution, attempts to add tags or attributes that would exceed the configured
  limits SHOULD cause the operation to fail fast with a clear error; behaviour is
  implementation-defined but MUST be documented.
- When `execution` is present:
  - `defaultMaxDurationSec`, when present, MUST be an integer ≥ 1 representing seconds.
  - `maxDurationUpperBoundSec`, when present, MUST be an integer ≥ 1 representing seconds.
  - Tools SHOULD warn when `defaultMaxDurationSec` exceeds `maxDurationUpperBoundSec`, as the effective default will be clamped down to the upper bound or stricter platform limits.

Validation
- Outcome ids must be unique strings.
- `when.phase` must be either `SUCCEEDED` or `FAILED`.
- If `when.predicate` is present, it must declare `lang` set to a supported expression engine id (for example `dataweave`) and the expression must return boolean.

Usage notes
- Use outcomes to give names to common scenarios such as “SucceededWithCacheHit”, “SucceededWithoutCache”, “FailedUpstream”, “RejectedByPolicy”.
- Keep outcome predicates simple and stable; they should refer to durable semantics (for example `error.code`) rather than transient implementation details.

<a id="dsl-22-http-cookies"></a>
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
  - `mode` (required when `spec.cookies.returnToClient` is present):
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
  - HTTP behaviour remains defined solely by other sections (`task`/plugins and `spec.bindings.http`).
  - No cookies are emitted back to clients from a jar (there is no jar and no `returnToClient` configuration).

Validation
- `spec.cookies.jar.domains[*].pattern` must be non‑empty strings.
- When `spec.cookies.returnToClient` is present, `spec.cookies.returnToClient.mode` MUST be present and one of `none`, `allFromAllowedDomains`, `filtered`.
- `spec.cookies.returnToClient.include.names`, when present, must be arrays of strings.
- `spec.cookies.returnToClient.include.namePatterns`, when present, must be arrays of strings that compile as Java‑style regular expressions.

### 22.2 Jar population from downstream Set-Cookie

For specs with `spec.cookies.jar` present, the engine maintains a per‑run cookie jar that is populated from downstream HTTP task responses only.

Sources
- HTTP task responses (`kind: httpCall:v1`, non‑notify):
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
- HTTP tasks with `kind: httpCall:v1` and `mode: notify` (see §5.1 and ADR‑0005) do not populate the cookie jar:
  - Responses to `notify` calls are ignored for jar purposes.
  - Jar cookies may still be attached to `notify` outbound requests (see §22.3).

Inbound cookies
- The jar does not ingest inbound `Cookie` headers from start or step requests:
  - If journeys need to work with inbound cookies, they must use existing mechanisms (`spec.bindings.http.start.headersToContext`, `spec.bindings.http.steps.*.headersToContext`, plus `transform` states) and, if desired, construct `Cookie` headers explicitly for outbound calls.

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
      kind: httpCall:v1
      operationRef: backend.getOrder
      cookies:
        useJar: false            # optional; default true
      # headers:
      #   Cookie: "${context.explicitCookie}"  # explicit header wins over jar
```

Shape
- `task.cookies` is allowed only when `task.kind: httpCall:v1`.
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

When `spec.cookies.returnToClient` is present, the engine may emit `Set-Cookie` headers towards the client on successful terminal responses. When `spec.cookies.jar` is configured but `spec.cookies.returnToClient` is omitted, the cookie jar is used only for downstream HTTP calls; engines MUST NOT emit `Set-Cookie` from the jar to clients.

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

<a id="dsl-23-journey-access-binding"></a>
## 23. Journey Access Binding

<a id="dsl-24-error-configuration"></a>
## 24. Error configuration (spec.errors)

This section summarises the minimal, normative rules for journey access binding. For background and rationale, see
ADR-0014 (`docs/6-decisions/ADR-0014-journey-access-binding-and-session-semantics.md`).

Rules:
- The DSL does **not** define a dedicated access-binding block (for example `spec.access`, `spec.accessBinding`, or a
  first-class `participants` structure). No such blocks are allowed in the generic DSL surface.
- Journeys that need access control or subject/participant binding MUST model the required identity and attributes in
  ordinary `context` fields (for example `context.identity.*`, `context.participants.*`) and/or metadata, using the
  existing DSL constructs (`transform`, `choice`, `wait`, `webhook`, etc.).
- When external identity is present (for example from a JWT or client certificate), journey definitions that rely on it
  SHOULD project it into `context` explicitly (for example via `spec.bindings.http.start.headersToContext` followed by
  a `transform`), so that journey logic can make clear, data-driven decisions.
- For any external-input interaction (journey start, `wait` state, `webhook` state, or `POST /steps/{stepId}` call),
  journey definitions that care about access control MUST implement their own checks over `context` and/or request
  data (for example via predicates, guards, or preceding `choice` states). The engine does not infer access rules from
  any special DSL fields.
- `journeyId` is a resume token only. Specs and implementations MUST NOT treat possession of a `journeyId` by itself as
  sufficient authorisation to read or mutate a journey; any additional access requirements MUST be modelled and enforced
  by journey logic as described above.
