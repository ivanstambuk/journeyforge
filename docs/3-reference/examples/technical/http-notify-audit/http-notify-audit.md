# Technical Pattern – HTTP notification (fire-and-forget)

Status: Draft | Last updated: 2025-11-23

## Problem

Trigger best-effort audit/notification calls using `task.mode: notify` and continue without observing the HTTP outcome.

## Example – http-notify-audit

Artifacts for this example:

- Journey: [http-notify-audit.journey.yaml](http-notify-audit.journey.yaml)

