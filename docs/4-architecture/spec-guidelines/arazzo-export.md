# Arazzo Export – Journeys API Workflows

Status: Draft | Last updated: 2025-11-22

This document defines how each JourneyForge journey exports an **Arazzo** 1.x workflow description for external consumers.
The Arazzo files sit alongside per-journey OpenAPI 3.1 contracts and describe canonical client workflows over the
`/api/v1/journeys/...` surface.

## Goals

- Provide a stable, machine-readable workflow description per journey (`kind: Journey`), focused on **happy-path** flows.
- Keep Arazzo documents **self-contained** (no cross-file `$ref`) while remaining aligned with the per-journey OpenAPI.
- Make workflows suitable for:
  - Use-case oriented documentation.
  - Workflow-level tests.
  - LLM / agent runners that call the Journeys API deterministically.

Non-goals (for now):
- Modelling internal orchestration details (downstream HTTP calls, cache operations, etc.).
- Exhaustively modelling all failure branches or edge cases per journey.

## Scope

### What Arazzo describes

- Arazzo workflows describe **external client interactions** with the journey:
  - `POST /api/v1/journeys/{journeyName}/start`
  - `GET /api/v1/journeys/{journeyId}/result`
  - `GET /api/v1/journeys/{journeyId}` (when polling makes sense).
  - `POST /api/v1/journeys/{journeyId}/steps/{stepId}` for external-input steps.
- Workflows do **not** describe:
  - Internal states (`task`, `choice`, `transform`, etc.).
  - Downstream APIs referenced via `operationRef` in the journey spec.

### Journey kinds

- Initial scope: `kind: Journey` only.
- Out of scope: `kind: Api` endpoints.
  - Synchronous `/api/v1/apis/{apiName}` endpoints are described **only** via OpenAPI (see `openapi-export.md`).
  - Arazzo workflows are not generated for `kind: Api` as part of the initial design; this may be revisited if a clear
    agent-use case emerges.

## File layout and naming

- Arazzo examples live under:
  - `docs/3-reference/examples/arazzo/`
- Per-journey naming:
  - OpenAPI: `docs/3-reference/examples/oas/<journeyName>.openapi.yaml`
  - Arazzo: `docs/3-reference/examples/arazzo/<journeyName>.arazzo.yaml`
- Workflow identifiers inside the file:
  - Use `<journeyName>-happy-path` for the canonical workflow.
  - Additional workflows for the same journey (for example cancellation or timeout flows) SHOULD use suffixes such as
    `-cancel`, `-timeout`, or `-alt`.

## Canonical workflow shape

Each Arazzo file:

- Targets a single journey.
- Declares Arazzo version `1.0.x` (currently `1.0.1`).
- Declares at least one canonical **happy-path** workflow.

Top-level structure (illustrative):

```yaml
arazzo: 1.0.1
info:
  title: JourneyForge – http-success workflows
  version: 0.1.0
  summary: Happy-path client workflow for the http-success journey.
sourceDescriptions:
  - name: httpSuccess
    type: openapi
    url: ../oas/http-success.openapi.yaml
workflows:
  http-success-happy-path:
    summary: Start the http-success journey and read the final outcome.
    sourceDescriptions:
      - httpSuccess
    inputs:
      startRequest:
        description: Initial payload for the http-success journey.
        schema:
          type: object
          required:
            - inputId
          properties:
            inputId:
              type: string
          additionalProperties: true
    steps:
      - name: startJourney
        description: Start a new http-success journey instance.
        call:
          source: httpSuccess
          operationId: httpSuccess_start
        parameters:
          requestBody: $inputs.startRequest
        outputs:
          journeyId: $response.body.journeyId
      - name: getResult
        description: Retrieve the final outcome once the journey is terminal.
        call:
          source: httpSuccess
          operationId: httpSuccess_getResult
        parameters:
          path:
            journeyId: $steps.startJourney.outputs.journeyId
```

Notes:
- The schema under `inputs.startRequest.schema` is **inlined** and must mirror the journey’s per-journey `JourneyStartRequest`
  schema defined in the corresponding OpenAPI file. Tooling SHOULD keep them in sync.
- `operationId` values in the Arazzo file MUST match those used in the per-journey OpenAPI.
- Arrays and objects in Arazzo examples SHOULD avoid inline `{}` / `[]` forms for readability.

## Multiple workflows per journey

Cardinality:

- Every journey MUST have exactly one canonical workflow named `<journeyName>-happy-path`.
- Additional workflows for the same journey are **optional** and SHOULD be added only when they represent a
  meaningfully different client intent, for example:
  - Fire-and-forget versus “start and wait for completion”.
  - Normal approval flow versus explicit cancellation.
  - Webhook completes successfully versus webhook timeout with compensation.

Guidance:

- For simple, effectively synchronous journeys (for example `http-success`), a **single** happy-path workflow is
  sufficient; adding extra workflows would create noise without additional signal for human or agent consumers.
- For journeys with richer lifecycle semantics (for example `wait-approval`, `payment-callback`), it is RECOMMENDED to
  add 1–2 additional workflows that:
  - Capture distinct external behaviours (for example “approve”, “cancel”, “timeout”).
  - Remain small and focused rather than enumerating every possible edge case.
- Tooling that generates Arazzo SHOULD:
  - Always emit the `<journeyName>-happy-path` workflow.
  - Only emit additional workflows when the journey model explicitly exposes distinct external scenarios that warrant
    separate recipes for API consumers or agents.

## Polling, wait, and webhook patterns

Workflows SHOULD model polling and step calls **only when they make sense** from the perspective of the workflow’s
primary client.

- When the journey has no external wait or callback semantics (for example `http-success`), the canonical workflow MAY be
  a simple two-step sequence: `start` → `result`.
- When the journey includes **manual or same-party wait states** (for example `wait-approval`):
  - Workflows SHOULD include:
    - `POST /api/v1/journeys/{journeyId}/steps/{stepId}` calls representing the client (or its UI) providing input to
      the wait step.
    - Optional `GET /api/v1/journeys/{journeyId}` or `/result` calls when realistic behaviour involves polling.
- When the journey includes **webhook states** where the caller and the webhook sender are different parties (for
  example `payment-callback`):
  - Consumer-focused workflows SHOULD NOT model the webhook `POST /steps/{stepId}` call as a step; it is treated as an
    external event triggered by another system.
  - Instead, workflows SHOULD focus on the client’s behaviour:
    - Starting the journey.
    - Optionally polling status while waiting.
    - Reading the final outcome once the webhook has been processed (success or failure).
- Workflows SHOULD NOT introduce artificial polling on journeys that are effectively synchronous from the client
  perspective.

## Inputs and schemas

- For canonical “happy-path” workflows:
  - Workflow `inputs` SHOULD expose a small number of high-level parameters.
  - At least one input SHOULD correspond to the journey start request body.
- Self-contained requirement:
  - Arazzo files MUST NOT use `$ref` to external files, including the per-journey OpenAPI.
  - Instead, they inline the relevant JSON Schema fragments.
- Schema alignment:
  - For `kind: Journey`, the journey’s per-journey OpenAPI remains the normative description of the HTTP surface.
  - Arazzo input schemas MUST be kept structurally compatible with the corresponding OpenAPI components.
  - When divergence is needed for ergonomics (for example derived inputs or pre-shaped aggregates), the workflow MUST
    still call operations in a way that is valid according to the OpenAPI specification.

## OperationIds and binding

- Per-journey OpenAPI files under `docs/3-reference/examples/oas/` MUST declare `operationId` values on all paths that are
  referenced from Arazzo workflows.
- Recommended naming for journeys:
  - Start: `httpSuccess_start` for `POST /journeys/http-success/start`.
  - Status: `httpSuccess_getStatus` for `GET /journeys/{journeyId}`.
  - Result: `httpSuccess_getResult` for `GET /journeys/{journeyId}/result`.
  - Step calls: `<journeyName>_<stepId>` for `POST /journeys/{journeyId}/steps/{stepId}`.
- Arazzo `call.operationId` MUST match the OpenAPI `operationId`.

## Examples and future work

- Example Arazzo files:
  - `docs/3-reference/examples/arazzo/http-success.arazzo.yaml` – simple two-step journey (start + result).
  - `docs/3-reference/examples/arazzo/wait-approval.arazzo.yaml` – manual approval with approve and reject workflows.
  - `docs/3-reference/examples/arazzo/payment-callback.arazzo.yaml` – multi-party webhook flow with success and failure
    workflows.

Future work:
- Add generator/validation tooling to ensure that:
  - Every journey OpenAPI file under `docs/3-reference/examples/oas/` has a matching Arazzo file under
    `docs/3-reference/examples/arazzo/`.
  - Arazzo `sourceDescriptions` and `operationId` values stay consistent with the corresponding OpenAPI documents.
  - Arazzo input schemas stay structurally aligned with the per-journey `JourneyStartRequest` (or equivalent) schemas.
