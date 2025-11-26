# ADR-0030 – XML Decode Task Plugin (`xmlDecode:v1`)

Date: 2025-11-25 | Status: Proposed

## Context

JourneyForge’s DSL and engine are intentionally JSON-centric:
- `context` is a JSON object.
- `spec.input.schema` / `spec.output.schema` / `spec.context.schema` are JSON Schemas.
- HTTP tasks (`httpCall:v1`) and bindings treat JSON as the primary body format, using JSON Schema and OpenAPI.

At the same time, many real-world HTTP APIs and message flows use XML payloads, including SOAP-wrapped messages. For inbound XML, we want:
- A spec-first way to express “take this XML and turn it into JSON” without hiding behaviour in the HTTP binding.
- A plugin model aligned with other tasks (`httpCall:v1`, `kafkaPublish:v1`, `schedule:v1`) rather than a second, XML-native control-flow model.

We considered two options for supporting XML input:
- A. Extend the HTTP binding with a `bodyFormat` knob (for example `json|xml|soap`) and transparently decode XML into JSON before the journey sees it.
- B. Keep HTTP binding simple and introduce an explicit task plugin (`xmlDecode:v1`) that journeys call to decode XML strings into JSON in `context`.

Option B aligns better with JourneyForge’s goal of keeping behaviour explicit in the DSL.

## Decision

We introduce an XML decode task plugin `kind: xmlDecode:v1`:
- It is a pure transformation task:
  - Input: an XML string taken from a `context` field.
  - Output: a JSON value written back into `context` (or stored under a variable).
- It is implemented as a normal `task` plugin, using the same `task.kind: <pluginType>:v<major>` pattern as other plugins.
- It does **not** perform any external I/O; it is a computational plugin only.

### DSL shape

New state type entry in the DSL reference:

- `task` (`kind: xmlDecode:v1`) – Decode an XML string from `context` into a JSON value.

Shape (under `type: task`):

```yaml
type: task
task:
  kind: xmlDecode:v1

  # Source – where to read XML from in context
  sourceVar: rawXml                  # required; context.<sourceVar> must be a string

  # Target – where to write the decoded JSON
  target:
    kind: context                    # context | var
    path: input                      # when kind == context; dot-path in context
  resultVar: parsedXml               # when kind == var; name under context.resultVar
next: <stateId>
```

Semantics:
- When entering an `xmlDecode:v1` task:
  - The plugin reads `context.<sourceVar>`; it MUST be a string containing XML.
  - The plugin parses the XML using the engine’s XML parser and maps it to a JSON value using a documented XML→JSON mapping (for example element/attribute rules).
- Writing the result:
  - When `target.kind == context`:
    - If `target.path` is provided:
      - The decoded JSON value is written at `context.<path>`, overwriting any existing value.
    - If `target.path` is omitted:
      - The decoded JSON value MUST be an object and replaces the entire `context`.
  - When `target.kind == var`:
    - The decoded JSON value MAY be any JSON value (object/array/primitive) and is stored under `context.<resultVar>`.
- Error handling:
  - If the XML cannot be parsed or mapped, the plugin MUST fail the task with a canonical Problem Details error (for example `type: "urn:xml-decode:parse-error"`); behaviour is aligned with the Task Plugin model (ADR‑0026) and error model (ADR‑0003).

Validation:
- `task.kind` MUST be `xmlDecode:v1` when using this plugin.
- `sourceVar`:
  - Required; MUST be a non-empty identifier.
  - At runtime, `context.<sourceVar>` MUST be a string; otherwise the plugin MUST fail with a clear error.
- `target.kind`:
  - When omitted, defaults to `context`.
  - When `kind: context` and `target.path` is omitted, the plugin requires that the decoded value is an object.
- `resultVar`:
  - Required when `target.kind == var`; MUST match the usual identifier pattern (`[A-Za-z_][A-Za-z0-9_]*`).

Usage patterns:
- Inbound XML via HTTP:
  - Start with `spec.input.schema` that accepts a string body or a wrapper object.
  - Use `xmlDecode:v1` as an early state to decode the raw XML body into JSON.
  - Subsequent states (`choice`, `transform`, etc.) operate on JSON in `context`, as usual.
- XML responses from HTTP tasks:
  - Use `httpCall:v1` with `resultVar` and treat `context.<resultVar>.body` as an XML string when `Content-Type` is XML.
  - Feed that string into `xmlDecode:v1` to obtain a JSON view of the XML response for further processing.

This keeps XML handling explicit in the journey definition and avoids hidden format switches in the HTTP binding.

## Consequences

Positive:
- **Explicit, spec-first XML handling**:
  - Journeys that work with XML can express the decoding step in the state graph, making behaviour visible and testable.
  - The DSL remains JSON-based; XML is just an alternative wire format that is decoded into JSON early.
- **Aligned with Task Plugin model**:
  - `xmlDecode:v1` uses the same plugin pattern as `httpCall:v1`, `kafkaPublish:v1`, etc.
  - It is pure and side-effect free, simplifying reasoning and testing.
- **Extensible to SOAP**:
  - SOAP envelopes are XML; journeys can decode them via `xmlDecode:v1` and then use `transform` states to unwrap the `<Body>` and normalise faults into the existing error model. A future SOAP-specific plugin can build on this base if needed.

Negative / trade-offs:
- **More explicit steps for XML journeys**:
  - Authors must add an explicit decode step instead of relying on an auto-decoding HTTP binding flag; this is intentional to keep semantics visible.
- **XML→JSON mapping semantics**:
  - The engine must define and document a deterministic mapping from XML to JSON; different engines or libraries may need compatibility tests to ensure consistent behaviour.

## Alternatives considered

### A. HTTP binding `bodyFormat` flag

We considered extending `spec.bindings.http` with a `bodyFormat` flag:

```yaml
spec:
  bindings:
    http:
      start:
        bodyFormat: json | xml | soap
```

Under this model, the HTTP binding would transparently decode XML into JSON before `context` is initialised. This was rejected as the primary mechanism because:
- It hides an important transformation behind configuration rather than an explicit state.
- It couples XML decoding tightly to HTTP, whereas XML may also appear in other contexts (for example HTTP task responses or queue messages).
- It complicates the HTTP binding semantics and the mental model for authors.

We may still revisit a `bodyFormat` hint as syntactic sugar on top of `xmlDecode:v1` in a future feature, but it is not the primary XML mechanism.

## Follow-ups

- Update `docs/3-reference/dsl.md` to:
  - Add `xmlDecode:v1` to the state type table.
  - Define the `xmlDecode:v1` task plugin shape and semantics.
- Add a feature spec for implementing `xmlDecode:v1` in the engine and tests that cover representative XML→JSON mappings.

