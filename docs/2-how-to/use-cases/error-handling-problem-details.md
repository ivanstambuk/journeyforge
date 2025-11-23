# Use Case – Error Handling & RFC 9457 Problem Details

Status: Draft | Last updated: 2025-11-23

This use case explains *when* to use HTTP error handling with RFC 9457 Problem Details and points to the canonical technical pattern and example journeys.

## Problem

Handle upstream failures and timeouts in a structured way:
- Record them as data in `context`.
- Normalise them into RFC 9457 Problem Details.
- Decide whether to return a Problem document as output or fail the journey with a mapped error code.

## Where to start

For the full pattern and all example journeys, see:

- Technical pattern: `docs/3-reference/examples/technical/http-error-handling/http-error-handling.md`

That pattern covers:
- Branching on generic HTTP failures (`http-failure-branch`).
- Distinguishing timeouts from other errors (`http-timeout-branch`).
- Normalising errors into RFC 9457 Problem Details and using them as either outputs or failure codes (`http-problem-details`).
