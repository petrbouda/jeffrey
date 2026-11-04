---
type: fixed
expect:
  name: ["GET /orders/{customer}", "POST /checkout"]
---

{{file:fixtures/traces_operationExport-{input.eventType}-{input.kind}-{input.profileId}.txt}}
