# Waiting, and memory without a heap dump

The time a CPU graph cannot see, and allocation and leak evidence from a plain recording.

## Waiting rather than running: `io_` and `blocking_`

A thread blocked on a socket read or a monitor is not on-CPU, so it contributes no samples and a CPU
flamegraph reports the application as idle rather than as waiting. These two families are where that
time is.

- `io_overview` with kind `SOCKET` or `FILE`, then `io_endpoints` for the targets and `io_slowest`
  for the individual operations. "What is this talking to, and how slow is it."
- `blocking_overview` for contended monitors, waits, parks, sleeps and virtual-thread pinning, then
  `blocking_monitors` — aggregated per lock, which names the monitor rather than the call site that
  happened to hit it — and `blocking_pinnedThreads` for Loom.

Both report whether the event type was recorded at all, because these events are threshold-gated: a
recording can hold none because nothing blocked for long enough as well as because the profiler was
never asked. The two are different findings and the tools distinguish them.

## Memory without a heap dump

`memory_allocations` ranks the **types** allocated where the allocation flamegraph ranks the call
**sites** — the other axis, and often the one that names the problem, since `byte[]` and `char[]` at
the top read very differently from a domain class. `memory_leakCandidates` reads
`jdk.OldObjectSample`: objects the JVM watched survive collections, which is leak evidence from a
plain recording when nobody captured a dump and the process has gone. Its absence is not a clean
bill of health — that sampler is off in most profiles, and the tool answers `NOT_RECORDED` rather
than reporting zero candidates.
