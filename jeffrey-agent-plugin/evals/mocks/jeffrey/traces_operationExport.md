---
type: fixed
expect:
  name: ["GET /orders/{customer}", "GET /persons/count", "GET /persons/{count}", "POST /checkout"]
---

{{file:fixtures/traces_operationExport-{input.eventType}-{input.kind}-{input.profileId}.txt}}
