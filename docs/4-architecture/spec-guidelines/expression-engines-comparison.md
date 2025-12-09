# Expression Engines – Comparison and Usage Guide

Status: Draft | Last updated: 2025-12-09

This document complements ADR‑0002 (DataWeave) and ADR‑0027 (Expression Engines and `lang` extensibility). It compares the expression engines we reference in JourneyForge – DataWeave, JSONata, JOLT, and jq – beyond their usage in the DSL, so we can choose engines consciously per deployment and per use case.

> Scope: This guide is descriptive and non‑normative. Normative rules for which engines are allowed at which DSL sites remain in ADR‑0027 and Features 012–016 plus the DSL reference. This document does not change those rules; it explains the engines, their strengths/limitations, and typical usage patterns.

## 1. Summary Matrix

| Engine id | Origin / ecosystem | Primary domain | Data model & types | Syntax style | Typical strengths | Typical limitations |
|-----------|--------------------|----------------|--------------------|--------------|-------------------|---------------------|
| `dataweave` | MuleSoft DataWeave 2.x | General JSON/XML/CSV transforms, predicates, mapping | Rich typed model (objects, arrays, strings, numbers, booleans, dates, binary, etc.) | Functional expression language with mapping and operator syntax | Very expressive mapping & shaping, strong standard library, good for complex transforms and mixed formats | Heavier engine; syntax and type system are more complex; implementation is typically JVM‑based and tied to Mule/DataWeave tooling |
| `jsonata` | JSONata (jsonata.org) | JSON query and transform | JSON values (objects, arrays, primitives) | Expression language combining selectors, functions, and comprehensions | Lightweight, concise queries and transforms over JSON, good for computed fields and small maps | JSON‑only; fewer built‑in facilities than DataWeave; complex logic can become dense |
| `jolt` | JOLT (Bazaarvoice OSS) | Declarative JSON‑to‑JSON transformation | JSON values, driven by transformation specs | JSON‑encoded spec (“shift”, “default”, “remove”, etc.) rather than inline expressions | Great for repeatable structural reshaping when the mapping can be captured declaratively in a spec | Not a general predicate language; awkward for ad‑hoc conditions; specs can be verbose and harder to reason about for complex logic |
| `jq` | jq (stedolan/jq) | JSON filtering, querying, transformation (CLI‑friendly) | JSON values with streaming support | Functional, Unix‑filter‑style language with pipelines and filters | Powerful for querying, filtering, aggregations, and composing pipelines; good CLI story; works well for predicates and transforms over JSON | JSON‑only; error messages can be terse; embedding properly (timeouts, limits) requires care; syntax is unfamiliar to many API engineers |

## 2. Common Terminology

These terms are defined normatively in `docs/0-overview/terminology.md` and are repeated here for convenience:

- **Expression engine** – a pluggable, pure evaluation component selected via `lang: <engineId>` at DSL expression sites (`choice` predicates, `transform` states, mappers, error mappers). Engines accept an expression string plus bindings (`context`, `payload`, `error`, `platform`) and return JSON/primitive values or evaluation errors.
- **Engine id** – the string identifier for an expression engine used in `lang` (for example `dataweave`, `jsonata`, `jolt`, `jq`). Engine configuration decides which ids are registered in a given installation. Unknown ids must cause validation/startup failures rather than silent fallbacks.

JourneyForge treats all engine ids on equal footing at the terminology level; ADR‑0027 and Features 012–016 decide which engines are supported where in the DSL.

## 3. DataWeave (`dataweave`)

### 3.1 Overview

- General‑purpose expression language and transformation engine originally from MuleSoft’s integration stack.citeturn0search0
- Strongly typed; supports rich data types (objects, arrays, primitives, dates, times, binary); oriented around mapping input structures into output structures.
- In JourneyForge:
  - Engine id: `dataweave`.
  - Behaviour and bindings are specified by ADR‑0002, ADR‑0027, Feature 012, and Feature 013.

### 3.2 Syntax snapshot

Example: project and rename fields from a JSON object:

```dw
{
  fullName: payload.firstName ++ " " ++ payload.lastName,
  isVip: payload.segment == "VIP"
}
```

Example: filter and map an array:

```dw
payload.items
  filter (item -> item.active)
  map (item -> item.id)
```

### 3.3 Strengths

- Very expressive for JSON/XML/CSV and multi‑format transformations; extensive standard library (string/date/math/list functions).citeturn0search0
- Good fit for complex mapping and “data‑shaping” use cases with nested objects, conditional fields, and derived values.
- Rich type system and schema‑like constructs make it easier to validate and maintain contracts in large specs.

### 3.4 Limitations / considerations

- Heavier runtime and ecosystem compared to JSONata/jq; primarily JVM‑oriented.
- Syntax has a learning curve, especially for teams without MuleSoft background.
- Not designed as a minimal/sandboxed embedded expression for tiny control‑plane tasks; more of a full mapping language.

### 3.5 Typical JourneyForge usage

- Complex mapping in `transform` states and mappers (for example building backend payloads, aggregating results).
- Non‑trivial predicates where richer operations (e.g. date math, list operations) are needed.
- Error mapping (`spec.errors`) when envelopes need structured reshaping.

## 4. JSONata (`jsonata`)

### 4.1 Overview

- Lightweight JSON query and transformation language with functional flavour.citeturn0search1
- Designed for querying and transforming JSON documents with concise syntax; supports variables, functions, and higher‑order operations.
- In JourneyForge:
  - Engine id: `jsonata`.
  - Generic behaviour is governed by ADR‑0027 and Feature 012; engine‑specific behaviour by Feature 014.

### 4.2 Syntax snapshot

Example: project and rename fields:

```jsonata
{
  "fullName": firstName & " " & lastName,
  "isVip": segment = "VIP"
}
```

Example: filter and map an array:

```jsonata
items[active = true].id
```

### 4.3 Strengths

- Compact and readable for JSON‑centric mappings.
- Expression‑oriented design fits both predicates and transforms.
- Engine is relatively small compared to DataWeave; can be embedded in various runtimes.citeturn0search1

### 4.4 Limitations / considerations

- JSON‑only; no first‑class XML/CSV structures.
- Fewer built‑in facilities than DataWeave; very complex flows may become dense or harder to maintain.
- Tooling/ecosystem is smaller than jq/DataWeave (fewer debuggers, fewer ready‑made recipes).

### 4.5 Typical JourneyForge usage

- Predicates and transforms over JSON payloads where a lighter syntax than DataWeave is preferred.
- Simple mappers in HTTP tasks, cache lookups, or Kafka key construction.

## 5. JOLT (`jolt`)

### 5.1 Overview

- Java‑based JSON transformation library where transformations are declared as JSON specs (e.g. “shift”, “remove”, “default”).citeturn0search2
- Very good at “structural” transformations: re‑shaping JSON trees, renaming keys, moving data between paths.
- In JourneyForge:
  - Engine id: `jolt`.
  - ADR‑0027 and Feature 015 restrict JOLT to transform/mapping sites; it is intentionally not used for boolean predicates or `statusExpr` in the DSL.

### 5.2 Syntax snapshot

Example: simple `shift` spec to rename `oldName` → `newName`:

```json
[
  {
    "operation": "shift",
    "spec": {
      "oldName": "newName"
    }
  }
]
```

The input JSON is treated as the root; `spec` describes how to move fields into the output.

### 5.3 Strengths

- Explicit, declarative specs for structural changes; easy to reason about when mappings are stable and mostly one‑to‑one.citeturn0search2
- Well‑suited for “plumbing” transforms (e.g. backend → API response) where the shape change is the main concern.
- Specs are themselves JSON, which can be stored/versioned alongside other config.

### 5.4 Limitations / considerations

- Not a free‑form expression language; specs are limited to what the transformation operations support.
- Awkward for predicates and complex conditional logic; that’s why JourneyForge keeps JOLT away from predicate sites in ADR‑0027.
- Specs can become hard to read for more complex transforms (multiple operations, wildcards).

### 5.5 Typical JourneyForge usage

- Structural response shaping (`transform` or mappers) when teams prefer declarative JSON specs over expression code.
- Situations where the mapping is stable and mostly about path rewrites or field moves, not heavy business logic.

## 6. jq (`jq`)

### 6.1 Overview

- Command‑line JSON processor and functional language for filtering, transforming, and aggregating JSON data.citeturn0search3
- Designed as a composable Unix‑style filter (`jq <program>`), but the core language is also embeddable in applications.
- In JourneyForge:
  - Engine id: `jq`.
  - Generic behaviour is governed by ADR‑0027 and Feature 012; engine‑specific behaviour by Feature 016.

### 6.2 Syntax snapshot

Example: project and rename fields:

```jq
{ fullName: .firstName + " " + .lastName,
  isVip: .segment == "VIP" }
```

Example: filter and map an array:

```jq
.items | map(select(.active == true) | .id)
```

### 6.3 Strengths

- Very powerful for querying/filtering/aggregating JSON, including streaming and large inputs.citeturn0search3
- Good fit for predicates and transforms where teams are comfortable with jq’s functional style.
- Mature ecosystem, documentation, and community examples.

### 6.4 Limitations / considerations

- JSON‑only; no first‑class XML/CSV structures.
- Syntax can be unfamiliar and terse; debugging complex filters takes practice.
- When embedded, needs careful configuration for timeouts, resource limits, and safe library usage (mirroring the limits in ADR‑0027).

### 6.5 Typical JourneyForge usage

- Predicates and transforms over JSON payloads for teams already invested in jq tooling and expertise.
- Cases where jq’s pipeline style maps naturally to the required logic (filters, aggregations, small derived structures).

## 7. Choosing an Engine per Use Case

These are non‑normative guidelines when deciding which engine id to use in a spec (subject to ADR‑0027’s site‑level rules):

- Prefer `dataweave` when:
  - You need rich, multi‑format mapping (JSON + XML/CSV) or complex transformations.
  - You want strong typing and a large, general‑purpose standard library.
- Prefer `jsonata` when:
  - Your data is JSON‑only and you want a concise, relatively lightweight expression language.
  - You are primarily writing predicates and simple transforms that should remain very compact.
- Prefer `jolt` when:
  - The main requirement is structural reshaping of JSON documents using declarative specs.
  - You want transformation configuration to remain plain JSON without embedding expressions.
- Prefer `jq` when:
  - Your team already uses jq heavily (CLI, tooling) and wants to reuse that expertise.
  - You need strong querying/filtering/aggregation over JSON, and are comfortable with its filter syntax.

JourneyForge itself does not declare a single “canonical” or “baseline” engine. Each installation can enable a subset of engines and choose per‑journey conventions, while ADR‑0027 and the DSL reference ensure that all engines follow the same purity, error‑handling, and limit rules.

