# Use Case – Content Transformation Pipeline

Status: Draft | Last updated: 2025-11-20

## Problem

Transform input data through multiple steps:
- Normalise an incoming order.
- Enrich it with derived fields (for example tax).
- Project the final structure for downstream consumers.

## Relevant DSL Features

- `transform` state with context replacement and var targets.
- `inputSchemaRef` / `outputSchemaRef`.

## Example – transform-pipeline

Workflow: `transform-pipeline.workflow.yaml`

```yaml
apiVersion: v1
kind: Workflow
metadata:
  name: transform-pipeline
  version: 0.1.0
spec:
  inputSchemaRef: schemas/transform-pipeline-input.json
  outputSchemaRef: schemas/transform-pipeline-output.json
  start: normalise
  states:
    normalise:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            {
              order: {
                id: context.id,
                amount: context.amount,
                currency: context.currency default: 'USD'
              }
            }
        target:
          kind: context
          path: ''
      next: enrich

    enrich:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            context ++ {
              order: context.order ++ {
                tax: (context.amount * 0.25)
              }
            }
        target:
          kind: context
          path: ''
      next: project

    project:
      type: transform
      transform:
        mapper:
          lang: dataweave
          expr: |
            {
              id: context.order.id,
              total: context.order.amount + context.order.tax,
              currency: context.order.currency
            }
        target:
          kind: var
        resultVar: summary
      next: done

    done:
      type: succeed
      outputVar: summary
```

Related files:
- OpenAPI: `transform-pipeline.openapi.yaml`
- Schemas: `transform-pipeline-input.json`, `transform-pipeline-output.json`
