# Using Arazzo Workflows with Agents

Status: Draft | Last updated: 2025-11-22

This document explains how external, agentic clients (for example AI assistants and workflow runners) should consume the
JourneyForge Arazzo workflow descriptions defined in `arazzo-export.md`.

The focus is on **external API consumers** calling the Journeys API; internal engine workflows and downstream
integrations remain opaque.

## Concepts

- **OpenAPI** describes the HTTP surface of each journey:
  - `POST /api/v1/journeys/{journeyName}/start`
  - `GET /api/v1/journeys/{journeyId}`
  - `GET /api/v1/journeys/{journeyId}/result`
  - Optional `POST /api/v1/journeys/{journeyId}/steps/{stepId}` for external-input steps.
- **Arazzo** describes how to use that surface as a **workflow**:
  - Which operations to call.
  - In what order.
  - With which inputs and data dependencies.
- For this project:
  - Arazzo is defined only for `kind: Journey` endpoints.
  - Synchronous `/apis/{apiName}` endpoints (`kind: Api`) have OpenAPI only.

## Agent responsibilities

An external agent using these workflows SHOULD:

- Treat each Arazzo workflow as a **recipe** for achieving a specific intent over the Journeys API.
- Use the per-journey OpenAPI only for:
  - Low-level request/response shapes.
  - Validation and code generation.
- Use the Arazzo workflows for:
  - Operation sequencing.
  - Parameter and output wiring between steps.
  - Understanding happy-path versus alternative scenarios (where multiple workflows exist).

When multiple workflows are available for the same journey (for example `-happy-path`, `-failed`), the agent SHOULD
choose the workflow whose summary and success criteria align with the user’s intent.

## Worked example 1 – Simple journey (`http-success`)

User intent: _“Call the upstream items API for item `123` and give me the result when it succeeds.”_

Relevant artefacts:

- OpenAPI: `docs/3-reference/examples/oas/http-success.openapi.yaml`
- Arazzo: `docs/3-reference/examples/arazzo/http-success.arazzo.yaml`
  - Workflow: `http-success-happy-path`
  - Input:
    - `startRequest` with schema `{ inputId: string }`
  - Steps:
    - `startJourney` → `httpSuccess_start`
    - `getResult` → `httpSuccess_getResult`

Agent reasoning and behaviour:

1. **Select workflow**
   - From the workflows in `http-success.arazzo.yaml`, pick `http-success-happy-path` because it clearly matches a
     “start and read final outcome” intent.

2. **Build inputs**
   - Map the user’s item id to `startRequest.inputId`:
     - `startRequest = { inputId: "123" }`

3. **Execute steps**
   - Step `startJourney`:
     - Call `POST /api/v1/journeys/http-success/start` with body `startRequest`.
     - Read `journeyId` from the response body (as per Arazzo `outputs.journeyId`).
   - Step `getResult`:
     - Call `GET /api/v1/journeys/{journeyId}/result` using the `journeyId` from the previous step.

4. **Interpret result**
   - Use the OpenAPI `JourneyOutcome` schema to interpret the final response.
   - Return or post-process `output` for the user, and optionally expose `error` when `phase == "Failed"`.

The agent does **not** need to inspect internal journey states; Arazzo plus OpenAPI is sufficient.

## Worked example 2 – Multiparty webhook journey (`payment-callback`)

User intent: _“Initiate a payment and later read the final status once the payment provider has called back.”_

This is a multi-party flow:

- The **client** starts the journey and reads the outcome.
- The **payment provider** triggers the webhook callback; the client does not control that call.

Relevant artefacts:

- OpenAPI: `docs/3-reference/examples/oas/payment-callback.openapi.yaml`
- Arazzo: `docs/3-reference/examples/arazzo/payment-callback.arazzo.yaml`
  - Workflows:
    - `payment-callback-happy-path` – expects `output.status == "SUCCESS"`.
    - `payment-callback-failed` – expects `output.status == "FAILED"`.
  - Inputs:
    - `startRequest` with schema `{ paymentId: string, amount: number, currency: string, ... }`
  - Steps (per workflow):
    - `startJourney` → `paymentCallback_start`
    - `getStatusWhilePending` → `paymentCallback_getStatus` (optional poll)
    - `getResult` → `paymentCallback_getResult`

Agent reasoning and behaviour:

1. **Select workflow**
   - If the user’s intent is “report success or error”, the agent MAY choose `payment-callback-happy-path` as the
     principal recipe and interpret failures via the outcome.
   - If the caller explicitly wants to distinguish “FAILED” as a primary outcome, the agent MAY instead choose or
     complement with `payment-callback-failed`.

2. **Build inputs**
   - Construct `startRequest` from user parameters:
     - `startRequest = { paymentId: "...", amount: 100.00, currency: "EUR", ... }`

3. **Execute steps**
   - Step `startJourney`:
     - Call `POST /api/v1/journeys/payment-callback/start` with `startRequest`.
     - Capture `journeyId` from the response.
   - Step `getStatusWhilePending`:
     - Optionally poll `GET /api/v1/journeys/{journeyId}` to inspect `phase` while waiting.
     - For long-running payments, the agent MAY repeat this step according to its own retry/backoff policy; Arazzo
       describes only a single logical poll.
   - Step `getResult`:
     - Call `GET /api/v1/journeys/{journeyId}/result` when the agent decides it is time to read the final outcome.

4. **Interpret result with success criteria**
   - For `payment-callback-happy-path`, the workflow includes `successCriteria.paymentSucceeded` that asserts:
     - `output.status == "SUCCESS"`.
   - For `payment-callback-failed`, the workflow includes `successCriteria.paymentFailed` that asserts:
     - `output.status == "FAILED"`.
   - An agent MAY:
     - Use these criteria to decide whether the run matched the intended scenario.
     - Surface the outcome and status to the human or calling system.

The agent never calls the webhook endpoint itself; it only observes the effect of external callbacks via `status` and
`result` calls.

## Recommended agent behaviour

When using JourneyForge Arazzo workflows, agents SHOULD:

- Prefer workflows whose summaries and success criteria clearly match the user’s intent.
- Treat the workflow `inputs` schema as the primary contract for what they must supply.
- Honour the operation order and data dependencies encoded in the `steps`.
- Use the OpenAPI files referenced by `sourceDescriptions` for:
  - Validating input and response shapes.
  - Generating strongly-typed client stubs where appropriate.
- Avoid calling step endpoints for webhook states unless the workflow explicitly models them as same-party interactions
  (for example manual approval steps).

When ambiguity exists (for example, multiple applicable workflows), agents SHOULD either:

- Ask the human for clarification, or
- Choose the workflow with the most conservative semantics (for example, one that clearly surfaces failure modes).

