---
type: llm
---

PASS if the answer explains what Jeffrey can investigate (CPU, allocation, locks, GC, heap dumps
and so on) and says what is in it now: the nine profiles — checkout baseline, checkout candidate, checkout
heap dump, order history, and the persons service's runs (persons direct, persons dom, persons n-plus-one,
persons lock, persons exceptions) — each named, or grouped by service with the count. FAIL if it starts
analysing a profile nobody named, or leaves out what the catalogue holds.
