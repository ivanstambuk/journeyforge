# Use Case – HTTP Cookie Jar & Return to Client

Status: Draft | Last updated: 2025-11-21

## Where to start

For the full pattern and example journey, see:

- Technical pattern: `docs/3-reference/examples/technical/http-cookie-jar/http-cookie-jar.md`

## Problem

Maintain HTTP cookies set by downstream services during a journey and:
- Attach them automatically to subsequent HTTP calls for allowed domains.
- Optionally return selected cookies to the caller as `Set-Cookie` on success.

## Relevant DSL Features

- `spec.cookies.jar` for per-run cookie jar and domain allow-list.
- `task.cookies.useJar` for per-task control over jar attachment.
- `spec.cookies.returnToClient` to emit `Set-Cookie` on terminal `succeed`.

## Example – http-cookie-jar

Journey definition: `http-cookie-jar.journey.yaml`

```yaml
apiVersion: v1
kind: Journey
metadata:
  name: http-cookie-jar
  version: 0.1.0
spec:
  cookies:
    jar:
      domains:
        - pattern: "api.example.com"
        - pattern: ".example.com"

    returnToClient:
      mode: filtered
      include:
        names: ["session"]

  start: login
  states:
    login:
      type: task
      task:
        kind: httpCall:v1
        method: POST
        url: "https://api.example.com/login"
        headers:
          Accept: application/json
        timeoutMs: 5000
        resultVar: loginResult
      next: getProfile

    getProfile:
      type: task
      task:
        kind: httpCall:v1
        method: GET
        url: "https://api.example.com/profile"
        cookies:
          useJar: true
        headers:
          Accept: application/json
        timeoutMs: 5000
        resultVar: profile
      next: decide

    decide:
      type: choice
      choices:
        - when:
            predicate:
              lang: dataweave
              expr: |
                context.profile.ok == true and context.profile.status == 200
          next: done
      default: failed

    done:
      type: succeed
      outputVar: profile

    failed:
      type: fail
      errorCode: PROFILE_FAILED
      reason: "Profile call failed or did not return 200"
```

Notes:
- The `login` call is expected to return a `Set-Cookie` for an allowed domain; the jar ingests it.
- The `getProfile` call automatically attaches the relevant cookies from the jar because `useJar` is enabled and no explicit `Cookie` header is configured.
- On successful completion (`done`), `returnToClient.mode: filtered` causes the engine to emit `Set-Cookie` for the `session` cookie (and any other cookies selected by name/regex) back to the caller.
