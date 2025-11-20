# Open Questions

Status: Active only (do not list resolved entries)

| ID | Owner | Question | Options (A preferred) | Status | Asked | Notes |
|----|-------|----------|------------------------|--------|-------|-------|
| Q-001 | TBD | What is the minimal YAML DSL surface (Task/Choice/Succeed/Fail semantics)? | A) ASL‑inspired minimal subset B) Ad‑hoc Task graph | Open | 2025-11-19 | Link to spec section when decided |

## Question Details

### Q-001 – DSL scope
- Context: Keep the initial slice small to enable an early runner.
- Options:
  - Option A (recommended): ASL‑inspired minimal subset (Task/Choice/Succeed/Fail; Input/Result/Output paths later).
  - Option B: Ad‑hoc Task graph without strict semantics.
- Decision: <pending>
- Follow‑ups: Update Feature 001 spec; add tests.

| Q-005 | TBD | HTTP error handling: treat non-2xx as fail, or allow expectedStatus? | A) All non-2xx => fail B) Allow expectedStatus: [200,204] C) Allow per-range policy later | Open | 2025-11-19 | See details below |
| Q-006 | TBD | Canonical error model for journeys and HTTP tasks (RFC 9457 vs custom) | A) Use RFC 9457 Problem Details as canonical internal error shape and map to JourneyOutcome.error via transformer B) Keep custom `{code,reason}` only C) Allow per-workflow pluggable error models | Open | 2025-11-19 | See details below |
| Q-007 | TBD | Global execution deadlines for journeys and APIs (spec.execution.maxDurationSec and onTimeout semantics)? | A) Per-spec `spec.execution` block with `maxDurationSec` and timeout error mapping (preferred) B) Runtime/platform-only deadlines with no DSL surface C) Multi-level deadlines (platform, environment, workflow) with precedence rules | Open | 2025-11-20 | See details below (ADR-0007) |

### Q-005 – HTTP error handling
- Option A (recommended): non-2xx => fail with HTTP_ERROR.
- Option B: support 'expectedStatus' array; non-listed => fail.
- Option C: defer to a future policy block for per-range handling.

### Q-007 – Execution deadlines and maxDurationSec
- Context: We need a spec-visible way to express an overall execution budget for journeys and APIs, complementing per-state timeouts (`httpCall.timeoutMs`, `wait.timeoutSec`, `webhook.timeoutSec`) and platform-level limits.
- Options:
  - Option A (recommended): add an optional `spec.execution` block with `maxDurationSec` and an `onTimeout` mapping; runtimes clamp per-state timeouts to the remaining budget and surface a well-defined timeout error when the budget is exceeded.
  - Option B: rely solely on runtime/platform-level deadlines; keep the DSL agnostic to global execution limits.
  - Option C: support multi-level deadlines (platform, environment, workflow) with precedence rules; the DSL may express a desired budget but the effective limit is resolved at deployment time.
- Decision: <pending>
- Follow-ups: ADR-0007; refine DSL reference and OpenAPI export guidelines once deadline enforcement patterns are validated.
