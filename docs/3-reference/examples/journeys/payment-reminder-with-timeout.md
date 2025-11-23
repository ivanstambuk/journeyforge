# Journey – payment-reminder-with-timeout

> One-shot payment reminder journey that waits up to 24 hours for confirmation before timing out using a `timer` state in parallel with a manual `wait`.

## Quick links

| Artifact | File |
|---------|------|
| Journey definition | [payment-reminder-with-timeout.journey.yaml](../technical/payment-reminder-with-timeout/payment-reminder-with-timeout.journey.yaml) |
| OpenAPI (per-journey) | [payment-reminder-with-timeout.openapi.yaml](../technical/payment-reminder-with-timeout/payment-reminder-with-timeout.openapi.yaml) |
| Arazzo workflow | [payment-reminder-with-timeout.arazzo.yaml](../technical/payment-reminder-with-timeout/payment-reminder-with-timeout.arazzo.yaml) |

## Technical pattern

See [payment-reminder-with-timeout.md](../technical/payment-reminder-with-timeout/payment-reminder-with-timeout.md).

## Summary

This journey models a simple payment reminder with a bounded waiting window:

- It sends a payment reminder to the user via `payments.sendPaymentReminder`.
- It then enters a `parallel` state with two branches:
  - A manual `wait` branch that accepts a `paymentStatus` update (for example from a client or back office).
  - A `timer` branch that waits 24 hours before declaring the reminder timed out.
- Whichever branch completes first determines the final outcome:
  - If the user confirms payment before the timer fires, the journey succeeds with `status: "PAID"`.
  - If the timer fires first, the journey succeeds with `status: "TIMED_OUT"`.

The example demonstrates how to combine `type: timer` with `type: wait` using `parallel` to implement “user action OR timeout” without abusing `wait.timeoutSec`.

