---
type: llm
---

PASS if the answer explains what Jeffrey can investigate (CPU, allocation, locks, GC, heap dumps
and so on) and names the three profiles that are there (checkout baseline, checkout candidate,
checkout heap dump). FAIL if it starts analysing a profile nobody named.
