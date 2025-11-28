# Analysis & Implementation Drift Gate – JourneyForge

Use this checklist twice for each feature with a spec under `docs/4-architecture/features/<NNN>/spec.md`:

- **Analysis Gate (pre-implementation)** – after spec/plan/tasks exist but before coding.
- **Implementation Drift Gate (pre-completion)** – once all planned tasks are done and the latest build is green.

Together, these gates enforce the project constitution and keep specs, plans, tasks, and code aligned for journeys, DSL features, and connectors.

## Inputs
- Feature spec – `docs/4-architecture/features/<NNN>/spec.md`
- Feature plan – `docs/4-architecture/features/<NNN>/plan.md`
- Feature tasks – `docs/4-architecture/features/<NNN>/tasks.md`
- Open questions log – `docs/4-architecture/open-questions.md`
- Constitution – `docs/6-decisions/project-constitution.md`
- `_current-session.md` intent log and any feature-specific runbooks

---

## Analysis Gate (Pre-Implementation)

Run this section **before** implementation for the feature starts.

1. **Specification completeness**
   - [ ] Objectives plus functional and non-functional requirements are populated for the feature.
   - [ ] Resolved medium- and high-impact questions for this feature are reflected directly in the spec’s normative sections (requirements, behaviour/flows, telemetry/quality/policy).
   - [ ] For UI-impacting work, the spec includes ASCII mock-ups where applicable (see `docs/4-architecture/spec-guidelines/ui-ascii-mockups.md` when introduced).

2. **Open questions review**
   - [ ] No blocking `Open` entries remain for this feature in `docs/4-architecture/open-questions.md`. If any exist, pause and obtain clarification.
   - [ ] For architecturally significant decisions (journey engine semantics, connector behaviour, cross-module boundaries, security/telemetry strategies, major NFR trade-offs), ADRs exist or are planned, and the spec/open-questions entries point to the corresponding ADR IDs.

3. **Plan alignment**
   - [ ] Feature plan references the correct spec and tasks files (`spec.md`, `plan.md`, `tasks.md` under the same feature directory).
   - [ ] Dependencies, acceptance criteria, and success signals in the plan match the specification wording.

4. **Tasks coverage**
   - [ ] Every functional requirement, non-functional requirement, and key scenario/journey path maps to at least one task.
  - [ ] Tasks sequence tests before implementation and keep planned increments ≤90 minutes by outlining logical, self-contained increments (execution may run longer if needed).
   - [ ] Planned tests enumerate success, validation, and failure branches for journeys/DSL/connectors, with failing cases queued before or alongside implementation.

5. **Constitution compliance**
   - [ ] No planned work violates constitutional principles (spec-first, clarification gate, test-first, documentation sync, dependency and contract guardrails).
   - [ ] Planned increments minimise new control-flow complexity by extracting validation/normalisation into small helpers and keeping each change close to straight-line logic.

6. **Tooling readiness**
   - [ ] Commands needed to validate the feature (for example `./gradlew --no-daemon spotlessApply check` or narrower module scopes) are documented in the feature plan or `_current-session.md`.
   - [ ] Any quality or governance hooks relevant to this feature are noted (for example `qualityGate` usage once extended).
   - [ ] Analysis Gate result is recorded in the feature plan (copy this checklist with pass/fail notes and date).

Only proceed to implementation when every checkbox is satisfied or explicitly deferred with owner approval captured in the feature plan.

---

## Implementation Drift Gate (Pre-Completion)

Run this section once all planned tasks are complete and the latest build is green.

1. **Preconditions**
   - [ ] Feature tasks are all marked complete and associated specs/plans reflect the final implementation.
   - [ ] Latest `./gradlew --no-daemon spotlessApply check` (or documented narrower suite) has passed within this increment.

2. **Cross-artifact validation**
   - [ ] Every medium- and high-impact spec requirement (functional, non-functional, key scenarios/journey paths) maps to executable code and tests; the drift notes in the feature plan cite spec sections against classes/tests.
   - [ ] No implementation or tests lack an originating spec requirement or plan task; any undocumented work is captured as a follow-up task or spec addition.
   - [ ] Feature plan and tasks remain consistent with the shipped implementation (dependencies, acceptance criteria, sequencing).

3. **Divergence handling**
   - [ ] Medium- and high-impact gaps or over-deliveries are logged as new entries in `docs/4-architecture/open-questions.md` for user direction.
   - [ ] Low-impact or low-level drift (typos, minor wording, formatting) is corrected directly before finalising the report; document the fix in the plan without escalating.
   - [ ] Follow-up tasks or spec updates are drafted for any outstanding divergences awaiting approval.

4. **Coverage confirmation**
   - [ ] Tests exist for each success, validation, and failure branch enumerated in the spec for journeys, DSL constructs, and connectors, and their latest run is green.
   - [ ] Any missing coverage is documented with explicit tasks and blockers in the feature plan.

5. **Report & retrospective**
   - [ ] Implementation Drift Gate outcome is added to the feature plan (or a dedicated section) with checklist results, artefact links, and reviewer(s).
   - [ ] Any lessons or reusable guidance (for example DSL idioms, journey patterns, connector policies) are captured so downstream features can adopt them.

Do not mark the feature complete until all medium/high-impact divergences are resolved through updated specs, approved tasks, or explicit user sign-off recorded in the plan.
