---
type: fixed
expect:
  name: ["GET /orders/{customer}", "POST /checkout"]
---

{{file:fixtures/traces_slowestTraces-{input.eventType}-{input.kind}-{input.profileId}.txt}}
