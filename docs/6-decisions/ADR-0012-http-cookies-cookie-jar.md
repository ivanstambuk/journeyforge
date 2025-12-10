# ADR-0012 – HTTP Cookies & Per-Run Cookie Jar

Date: 2025-11-21 | Status: Proposed

## Context

JourneyForge already models HTTP tasks with structured results and supports header bindings, but HTTP cookies are not first-class:
- Downstream services often use cookies (session ids, CSRF tokens, feature flags) as part of their protocol.
- Clients may also need to receive cookies (or cookie deletions) from journeys and API endpoints.
- Today, authors would have to treat cookies as raw headers and manually transform them, which is error-prone and obscures behaviour.

We want a way to:
- Maintain a per-run cookie store that honours HTTP cookie rules (domain, path, secure, deletion) without leaking across runs.
- Attach appropriate cookies automatically to outbound HTTP tasks, while keeping explicit `Cookie` headers in control when present.
- Optionally return selected cookies to the caller as `Set-Cookie` on successful terminal responses, with clear configuration and security boundaries.

The feature must remain opt-in and must not change behaviour for existing specs that do not declare cookie configuration.

## Decision

We introduce a first-class cookie configuration in the DSL and a per-run cookie jar in the engine:

- DSL surface:
  - Add `spec.cookies.jar` with an allow-list of cookie domains:
    - `spec.cookies.jar.domains[*].pattern` supports:
      - Exact host patterns (e.g. `api.example.com`).
      - Leading-dot subdomain patterns (e.g. `.example.com`), which match subdomains of `example.com` but not `example.com` itself.
  - Add `spec.cookies.returnToClient` to control which cookies from the jar are emitted as `Set-Cookie` on terminal `succeed`:
    - `mode: none | allFromAllowedDomains | filtered` (required whenever `spec.cookies.returnToClient` is configured).
    - Optional `include.names` and `include.namePatterns` (Java regex) when `mode: filtered`.
  - Extend HTTP tasks (`kind: httpCall:v1`) with `task.cookies.useJar?: boolean`:
    - Defaults to `true` when `spec.cookies.jar` exists.
    - When `false`, the jar is not consulted for that task.

- Jar semantics:
  - Scope:
    - One jar per journey instance for `kind: Journey`.
    - One jar per API invocation for `kind: Api`.
    - Jars are created at run start and destroyed at terminal state; there is no cross-run sharing.
  - Population:
    - Jar is populated only from downstream HTTP task responses (`kind: httpCall:v1` with `mode != notify`), by parsing `Set-Cookie` headers.
    - Inbound client `Cookie` headers (start/step) are never ingested into the jar; journeys that need inbound cookies must bind and use them explicitly.
    - Effective domain and path are derived according to RFC 6265 style rules:
      - `Domain` attribute when present; otherwise request host.
      - `Path` attribute when present; otherwise derived from request path.
    - Only cookies whose domains match `spec.cookies.jar.domains` are stored; others are discarded and not surfaced via structured result headers.
    - Cookies are stored with `(domain, path, name)` keys with last-writer-wins semantics.
    - Deletion cookies (e.g. `Max-Age=0` or expired `Expires`) remove entries from the jar and are tracked to allow emitting matching deletion `Set-Cookie` towards the client when configured.
  - Notify:
    - HTTP tasks with `mode: notify` may read jar cookies for outbound requests but do not populate the jar from their responses.

- Attachment to outbound HTTP tasks:
  - For eligible HTTP tasks (jar present, `useJar` true or omitted, no explicit `Cookie` header configured):
    - The engine selects cookies from the jar whose domain/path/scheme scope matches the outbound URL (following RFC 6265 style domain and path rules, and `Secure` semantics).
    - It synthesises a `Cookie` header from those cookies.
  - If a `Cookie` header is configured explicitly (in `task.headers` or defaults), it always wins and jar cookies are not attached for that request.

- Returning cookies to the caller:
  - When `spec.cookies.returnToClient` is present and a run terminates in `succeed`, the engine selects cookies from the jar whose domains match `spec.cookies.jar.domains` and:
    - Emits `Set-Cookie` for all of them when `mode: allFromAllowedDomains`.
    - Emits `Set-Cookie` only for cookies whose names are in `include.names` or match `include.namePatterns` when `mode: filtered`.
    - Emits deletion `Set-Cookie` for cookies that the jar observed as deleted, subject to the same filters.
  - `returnToClient` is success-only; `fail` responses do not emit `Set-Cookie` from the jar.

- Defaults:
  - When `spec.cookies.jar` is configured but `spec.cookies.returnToClient` is omitted, the jar is used only for downstream HTTP calls; engines MUST NOT emit `Set-Cookie` from the jar back to clients. Returning cookies is always an explicit, opt-in choice via `spec.cookies.returnToClient.mode`.

Cookie interpretation (parsing, domain/path matching, deletion semantics) is aligned with RFC 6265 (or its successors) and must be implemented consistently in the HTTP connector.

## Consequences

Positive:
- HTTP cookies become first-class and predictable:
  - Jar behaviour is configured explicitly via `spec.cookies` and is disabled by default for existing specs.
  - Domain/path scoping and deletion semantics follow established HTTP cookie rules.
- Journey authors get concise patterns for:
  - Stateful interactions with cookie-based backends.
  - Returning selected cookies to clients without manual header mangling.
- The separation between transport concerns and business logic remains:
  - Inbound cookies stay explicit (via header bindings).
  - Jar content is not directly exposed to DataWeave in this increment.

Negative / trade-offs:
- Engine and HTTP connector complexity increases:
  - Correct implementation of RFC 6265 style semantics is non-trivial and requires careful testing.
  - Additional configuration combinations (domains, modes, filters) increase the surface area.
- There is no built-in way for journeys to inspect or manipulate jar content directly; if that becomes necessary, a future feature would need to expose a safe, read-only view to expressions.

Follow-ups:
- Implement the DSL described in Feature 004 and the corresponding engine behaviour.
- Add conformance tests to ensure cookie handling matches RFC 6265 style rules and the DSL semantics.
