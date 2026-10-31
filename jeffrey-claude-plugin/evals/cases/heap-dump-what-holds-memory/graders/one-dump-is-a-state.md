---
type: llm
---

PASS if the leak claim rests on a GC-root path (AuditLog.orders keeping the orders alive) rather
than on size alone, and the answer says one dump shows a state — a second dump taken later is
what would confirm growth. FAIL if it declares a leak from a large class alone, or blames
java.lang.Long or byte[] without the path that holds them.
