# Choosing a flamegraph, and when

Which event type to graph for a question, and how to pick the window a graph is drawn over.

## Which event type

Export the type the question needs straight away — `profiles_summary`'s `eventTypes` already says
what was recorded. A type the profile did not record answers `status: NOT_RECORDED`, naming the
types it did, with the export of the one that answers the same question in `followUp.nextTools`.
`flamegraph_list` names every recorded type with its export defaults, and the standard groups the
profiler did not capture; call it when those matter, not before every export. Starting points:

- on-CPU time → `jdk.ExecutionSample`, or `jdk.CPUTimeSample` — JDK 25's CPU-time sampler (JEP 509).
  Both of the JDK's settings leave it off and `ExecutionSample` on, so a recording holds either or
  both, depending on how it was started. Ask for either; a `NOT_RECORDED` answer offers the other.
- allocation → `jdk.ObjectAllocationSample`, with `useWeight: true` to rank by bytes rather than call count
- lock contention → `jdk.JavaMonitorEnter`, with `useWeight: true` (weight is nanoseconds blocked);
  `jdk.JavaMonitorWait`, `jdk.ThreadPark`, `jdk.ThreadSleep` and `jdk.VirtualThreadPinned` graph the
  same way
- wall-clock latency including off-CPU → `profiler.WallClockSample` — async-profiler's event, so it
  does **not** carry the `jdk.` prefix its neighbours do
- native allocation → `profiler.Malloc`, and `jeffrey.NativeLeak` for what was never freed —
  async-profiler's `nativemem` mode, the flamegraph counterpart to `jvm_nativeMemory`
- one instrumented method's callers → `jdk.MethodTrace`

`thresholdPct` decides how much survives pruning: raise it for an overview, lower it to chase one
specific path.

## When, not where: the `timeline_` family

A flamegraph of a whole recording flattens a thirty-second spike into a five-minute average, and the
spike stops being visible. `flamegraph_export`, `compare_movements` and `compare_flamegraph` take
`startEpochMs` and `endEpochMs`, and nothing else in the surface helps you choose them.

1. `timeline_hotWindows` with the event type — the recording bucketed, the busiest windows ranked,
   and a one-line shape so a steady load, a ramp and a single burst are told apart. Pass `useWeight`
   for bytes or nanoseconds rather than sample counts.
2. `flamegraph_export` with the `startEpochMs` and `endEpochMs` of the window it named — the call for
   the busiest one is already in its `followUp.nextTools`. That graph shows what the
   whole-recording one averaged away.
3. `timeline_zoom` when a second is too coarse — a startup, or the inside of one spike. It is the
   only view that resolves below a second.

Neither returns the raw series: what comes back is the ranked windows and the shape, because a curve
is thousands of numbers a reader cannot act on and the window bounds are the part the next tool takes.
