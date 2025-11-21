# Feature 004 – Plan (HTTP Cookies & Journey Cookie Jar)

## Slice 1 – DSL & docs
- Extend `docs/3-reference/dsl.md` with a Cookies section covering:
  - `spec.cookies.jar` (domains, behaviour).
  - `spec.cookies.returnToClient`.
  - `task.cookies.useJar`.
- Add at least one example journey under `docs/3-reference/examples/` that uses the cookie jar.
- Add a short how‑to under `docs/2-how-to/use-cases/` showing a realistic cookie scenario.

## Slice 2 – Engine model & parsing
- Extend model types and parser to support `spec.cookies` and `task.cookies`.
- Add validation rules for enums, patterns, and placement.

## Slice 3 – HTTP connector integration
- Implement per‑run cookie jar with domain/path scoping and deletion semantics.
- Integrate jar attachment logic into the HTTP connector.
- Implement `returnToClient` behaviour for both journeys and APIs.

## Slice 4 – Tests & hardening
- Add unit/integration tests per the Feature 004 spec.
- Add regression tests for existing HTTP examples.

