---
type: llm
---

PASS if the answer measures the pauses against the recording — roughly 130–155 ms of pause in a 30.6 s
recording (about 135 ms of young pauses, plus about 16 ms for the heap-dump Full GC when it is
counted), about half a percent of wall clock or less (or the throughput/overhead figure, 99.5% /
0.5%) — and concludes
that GC pauses are not what hurts this service, treating the Full GC the rules flagged as a single
event of a few tens of milliseconds rather than a diagnosis. FAIL if it calls GC a problem, recommends GC flags or tuning,
or quotes pause figures without the recording length they are a share of.
