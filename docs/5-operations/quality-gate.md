# Quality Gate – JourneyForge

The JourneyForge quality gate ties together formatting and verification tasks that protect journeys, DSL parsing, runtime, and connectors. Run it locally for features that touch engine or connector behaviour and rely on CI for enforcement on pushes and pull requests.

## Commands
- **Format before commit:** `./gradlew --no-daemon spotlessApply`
  - Runs the configured formatter via Spotless across Java sources so diffs satisfy the gate.
  - Execute this before every commit or at least before the final increment for a feature.
- **Minimal CI gate:** `./gradlew --no-daemon qualityGate`
  - Aggregates `spotlessCheck` and `check` (see `.github/workflows/ci.yml` for details).
  - Run this locally for features that change journeys, DSL parsing, runtime engine, or connectors; for documentation-only or trivial examples, the full gate may be optional as agreed in the feature plan.

Gradle will fail fast if any underlying task fails; check the failing sub-task in the output to see which guard triggered.

## Usage Guidelines
- Keep `qualityGate` green; treat it as the minimal CI contract for any change.
- When a feature spec or plan introduces new quality requirements (for example contract tests, mutation analysis, additional architecture checks), update this runbook once the corresponding Gradle tasks are wired in.
- For large feature increments, consider running module-scoped `check` tasks (for example runtime or connector modules) during iteration and the full `qualityGate` before marking the feature complete.

Keep this document in sync whenever thresholds, task wiring, or report locations change.
