---
type: llm
focus: last_message
---

The profile holds sampled traces of POST /checkout, each with four spans under it (price.reprice,
session.touch, coupon.discount, audit.record). The slow requests are the ones whose session.touch
span took tens of milliseconds: SessionCache.touch busy-waits about 12 ms on every 50th visit of a
customer, inside a synchronized method, so the other workers queue on its monitor.

PASS if the reply says the slowest requests spend their time in session.touch (SessionCache — its
busy-wait, or the wait for its lock), and supports it with figures read from the traces: the slow
requests' durations or the endpoint's percentiles, and the span breakdown of at least one slow trace.

FAIL if it names pricing or formatLine as what makes those requests slow, if it gives no trace
figures, or if it answers only from a whole-recording flamegraph.
