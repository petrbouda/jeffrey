---
type: llm
focus: last_message
---

In "persons dom" the JVM's own machinery is fine: G1 collected 24 times with about 249 ms of pause in
about 76 s (throughput about 99.7%, no full GC), safepoints paused about 121 ms in total, no virtual
threads pinned. What is not fine is the container: it is limited to one CPU core (cgroup v2, quota 100 ms
per 100 ms period) and the CFS scheduler throttled it in 118 of 301 periods (about 39%), parking it for
about 2.7 s; on that one core the JIT compiler also spent some 33 s compiling (11,528 compilations).
Scaling the service up means looking at that CPU limit first.

PASS if the reply reads the JVM's sections (GC and at least the container), says GC is not the problem
(with a figure), and names the CPU throttling with its figures (the throttled periods or share, the
parked time, or the one-core limit) as what to address before scaling. FAIL if it calls the JVM healthy
without mentioning the throttling, invents a GC or heap problem the figures do not show, or answers
without figures.
