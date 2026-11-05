---
type: fixed
expect:
  name: ["GET /orders/{customer}", "POST /checkout"]
---

{{file:fixtures/traces_operationFlamegraphExport-{input.eventType}-{input.graphEventType}-{input.kind}-{input.profileId}.txt}}
