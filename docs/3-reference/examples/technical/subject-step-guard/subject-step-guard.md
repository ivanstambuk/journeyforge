# Technical Pattern – Subject-scoped self-service steps

Status: Draft | Last updated: 2025-11-23

## Problem

Capture the subject from JWT at start, re-check it on follow-up steps, and fail with a security error on mismatch.

## Example – subject-step-guard

Artifacts for this example:

- Journey: [subject-step-guard.journey.yaml](subject-step-guard.journey.yaml)

