# The machine underneath: the `jvm_` family

Garbage collection, safepoints, JIT compilation, threads, native memory, the container and the
JVM's own configuration.

## The sections

Garbage collection, safepoints, JIT compilation, per-thread attribution, native memory, the
container and the JVM's own configuration are not flamegraph questions and `flamegraph_list` will
not offer them. They have their own family, and each tool renders the dashboard the Jeffrey UI
renders — the same tested builders, one call instead of six invented SQL queries.

`jvm_sections` first: a recording holds only what the profiler was told to capture, and each
section reports whether this one carries its events. A section asked for anyway answers
`status: NOT_RECORDED` with a `reason` naming the events it needed, so an absence never arrives as a
page of zeroes.

Every result carries a `followUp` beside the figures, saying what that dashboard cannot answer and
which tool answers it — `nextTools` as ready calls, `guidance` for the rest — the same idea as the
reading instructions an export opens with. Follow it: the figures are one half of an answer, and the
other half is usually in another family.

| Tool | The question it answers |
|---|---|
| `jvm_autoAnalysis` | Jeffrey's rule set over the whole recording — findings with a severity and a suggested fix. Reading it is cheap once computed (`topFindings` in `profiles_summary` already carries them); computing it is slow — see below. |
| `jvm_gc` | The stop-the-world budget, collections by generation and cause, what was freed, the longest collections |
| `jvm_safepoints` | The pauses that are **not** GC, and the threads that were slow to reach them |
| `jvm_jit` | Compiler totals, the slowest compilations, code cache, deoptimisation by method and reason |
| `jvm_threads` | Population and peak, top CPU and allocating threads, virtual-thread pinning |
| `jvm_nativeMemory` | RSS and its growth, direct buffers, NMT categories — memory outside the Java heap |
| `jvm_container` | cgroup limits and whether the scheduler throttled the process |
| `jvm_configuration` | What the JVM was started with, in the UI's own tabs; one `section` at a time (`JVM_INFORMATION` … `VIRTUALIZATION_INFORMATION`) |
| `jvm_flags` | The flag list with each value's **origin** — default, command line, or the JVM's own ergonomics |
| `jvm_threadDumps` | The dumps together: recurring deadlocks, monitors threads queued on, threads stuck across dumps; a cut keeps the latest dumps and counts the rest in `omittedRows` |
| `jvm_threadDump` | One dump in full, every thread with its state and stack |
| `jvm_gcDetail` | The GC pages beneath the overview, one `page` at a time: `TENURING`, `IHOP`, `G1` (regions, evacuation failures), `ZGC` (stalls), `STRING_TABLES`, `FINALIZERS`, `REFERENCES`, `PHASES`, `PLAB`, `CONFIGURATION` |
| `jvm_classLoading` | What was loaded and who loaded it: metaspace, the loaders ranked, the slowest loads, agent redefinitions |
| `jvm_exceptions` | What the application threw, and what kinds — constructing one walks the stack, so a type thrown in a loop is a cost no frame names; the application's throws (libraries included) are split from the JVM's own class-loading and linking noise |
| `jvm_exceptionSites` | Each throw site with its stack; given your packages as `framePrefixes`, the frame of yours that led to it. Tracing exceptions to source is the `analyze-exceptions` skill |
| `jvm_system` | The machine underneath: machine CPU against this JVM's own, and what the difference leaves for everything else on the box |
| `jvm_security` | TLS handshakes, the protocols and ciphers negotiated, certificates expiring or weakly signed, what was deserialized |

What the tools know and a hand-written query does not:

- **The cause of GC is allocation.** Pauses are the symptom; no GC event names the code that
  produced the garbage. Once `jvm_gc` shows the budget matters, the allocation flamegraph —
  `jdk.ObjectAllocationSample`, `useWeight: true` — is what names the call paths. A heap dump
  answers the other half, what is *retained* rather than churned; that is `analyze-heap`.
- **"GC looks fine and we still have pauses"** is `jvm_safepoints`. Every VM operation stops the
  application the same way a collection does, and a thread slow to yield holds all the others
  there — `_thread_in_Java` means a loop the JIT stripped the safepoint poll out of,
  `_thread_in_native` a call the JVM cannot interrupt.
- **An empty compilation list means nothing compiled *slowly*,** not that nothing compiled: the
  event is threshold-gated, and the compiler statistics are there either way. A method
  deoptimising over and over ran interpreted for part of the recording, and the reason
  (`unstable_if`, `class_check`) is the pointer into the source.
- **Before proposing any flag,** read `jvm_flags` — it is the only place that separates a flag
  somebody set from one the JVM's ergonomics chose, and the two justify very different advice.
  `jvm_configuration` is the companion read: the collector, heap and compiler settings that resulted.
  A tuning claim is worth making against those values, never against a deployment manifest.
- **"Is it my JVM or the box?"** is `jvm_system`, and it is worth asking before trusting any
  flamegraph. It reports the machine's CPU and this JVM's separately, and the gap is everything else
  running there. A profile whose own CPU is modest while the machine is saturated describes an
  application being starved, not a slow one.
- **"It stopped responding"** is `jvm_threadDumps`, not a flamegraph. A deadlock or a pool all
  blocked on one lock produces no samples worth graphing; it produces threads sitting still, which
  only the dumps show.

`jvm_autoAnalysis` reads a cache: `status` is `COMPUTED`, `NOT_COMPUTED` or `CANNOT_COMPUTE` (the
recording is gone, or the profile is not a JFR recording). On `NOT_COMPUTED` its `followUp` offers
`compute: true`, which runs the rule set now — it reads the whole recording through the JMC
toolkit, so it is slow and asked for rather than assumed; past its wait it answers with an
`operationId` for `operations_status`. The other sections answer either way. For anything these do not shape — a distribution over
time, a correlation between two event types — the `jfr-sql` skill has the schema and the queries.
