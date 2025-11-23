# Technical Pattern – HTTP cookie jar & return to client

Status: Draft | Last updated: 2025-11-23

## Problem

Maintain HTTP cookies set by downstream services during a journey and:
- Attach them automatically to subsequent HTTP calls for allowed domains.
- Optionally return selected cookies to the caller as `Set-Cookie` on success.

## Example – http-cookie-jar

Artifacts for this example:

- Journey: [http-cookie-jar.journey.yaml](http-cookie-jar.journey.yaml)

