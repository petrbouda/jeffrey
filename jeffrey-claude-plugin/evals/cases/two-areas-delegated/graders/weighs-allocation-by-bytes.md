---
type: llm
---

PASS if the answer ranks allocation by bytes (or says what its shares are of) and names
PriceCalculator.formatLine / String.format as the dominant allocation path, and names
SessionCache.busyWait as the dominant CPU frame. FAIL if either hotspot is wrong or allocation is
ranked by sample count alone without saying so.
