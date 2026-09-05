---
name: analyze-jfr
description: Analyses a JVM profile held by a running Jeffrey Microscope — CPU, wall-clock, allocation, lock contention, trace latency, and the machine underneath: garbage collection, safepoints, JIT compilation, threads, native memory, the container and the JVM's configuration. Starts from the catalogue or from a .jfr file Jeffrey has not seen yet. Use whenever the user asks why something is slow, where the time goes, what is allocating, why GC pauses are long, what is pausing the JVM, what the JIT compiler or deoptimisation is doing, which threads are burning CPU, why memory grows outside the heap, whether the container is throttling, what a JFR recording or flamegraph shows, or mentions a Jeffrey profile, a .jfr file or async-profiler output. For a heap dump or .hprof file, analyze-heap applies instead.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a Jeffrey profile

Jeffrey holds *profiles*: one analysed JFR recording or heap dump each. Every tool reads; the one
family that writes is `recordings_`, which turns a recording *file* into a profile.

Tool names below omit the prefix your client puts in front of them —
`mcp__plugin_microscope_jeffrey__` for the Claude Code plugin, `mcp__jeffrey__` in Codex and for any
hand-registered server. The part after it is exact and camelCase:
`jfr_listTables`, not `jfr_list_tables`.

## 1. Get a `profileId`

Every tool except `profiles_list` and the `recordings_` family takes one.

**The user named a file** (`target/app.jfr`, anything with a recording extension) — it may not be
in Jeffrey yet. Check `recordings_list` or `profiles_list` for it first, because every
`recordings_analyzeFile` call imports the file again and creates another profile. If it is absent,
call `recordings_analyzeFile` with the **absolute** path. The Jeffrey process opens that path, so
the file has to be on the machine Jeffrey runs on — a container or a remote Jeffrey cannot see your
working directory. The call returns once the profile is built, which takes a while for a large
recording; that is the analysis running, not a hang.

**The recording is on a hub** — the user asked about an environment rather than a file
("production", "staging", "what recorded in the last hour"), and nothing local matches. Switch to
the **analyze-hub** skill: it finds the session across the connected hubs, pulls it in and comes
back here with a `profileId`.

**Otherwise** — `profiles_list`, pick the profile, then `profiles_summary` to learn what it can
answer before asking for it. That one call carries what `profiles_get`, `profiles_features` and
`profiles_samplerHealth` each carry a part of: `disabledFeatures` names what the profile lacks
(traces exist only if the app ran Jeffrey's instrumentation; `HEAP_DUMP` there means no dump),
`eventTypes` lists every recorded event type with its totals, and `topFindings` carries the
auto-analysis findings when they have been computed.

A profile whose `event source` column reads `HEAP_DUMP` is a heap dump: switch to the
`analyze-heap` skill, because flamegraphs and traces do not apply to it.

## 2. Pick the family that matches the question

| Family | Tools |
|---|---|
| `profiles_` | `list`, `summary` (identity, features, recorded event types and the top findings in one call — start here), `get`, `features`, `samplerHealth` (whether the samples can be trusted), `link` (the profile in the Jeffrey UI), `viewLink` (one named page) |
| `flamegraph_` | `list` (which event types this profile can graph — call it first), `export` (the call tree as Markdown) |
| `compare_` | `list` (whether two profiles are comparable at all — call it first), `movements` (what moved, ranked), `flamegraph` (the differential call tree) — the `compare-jfr` skill has the workflow |
| `traces_` | `overview`, `operations`, `notifications`, `operationExport`, `slowestTraces`, `traceExport`, `spanFlamegraphExport`, `operationFlamegraphExport`, plus `attributeKeys`/`attributeValues`/`attributeSearch` |
| `jvm_` | `sections` (call it first), `autoAnalysis`, `gc`, `gcDetail`, `safepoints`, `jit`, `threads`, `threadDumps`, `threadDump`, `nativeMemory`, `classLoading`, `exceptions`, `system`, `security`, `container`, `configuration`, `flags` — the machine underneath the application |
| `http_` | `overview`, `endpoint` — HTTP traffic, `SERVER` (served) or `CLIENT` (called out) |
| `jdbc_` | `overview`, `statementGroup`, `pools` — the queries it ran, and the pool in front of them |
| `grpc_` | `overview`, `service`, `traffic` — gRPC latency and message sizes, `SERVER` or `CLIENT` |
| `methodtracing_` | `overview`, `slowest`, `timing` — instrumented method timings (JEP 520) |
| `io_` | `overview`, `endpoints`, `slowest` — socket and file I/O, the waiting a CPU graph cannot see |
| `blocking_` | `overview`, `monitors`, `pinnedThreads` — contended locks, waits, parks, virtual-thread pinning |
| `timeline_` | `hotWindows` (when the samples landed), `zoom` (sub-second, inside one window) |
| `memory_` | `allocations` (by type, not call site), `leakCandidates` (JFR-side, needs no heap dump) |
| `jfr_` | `listTables`, `describeTable`, `describeEventType` (the fields inside one event type), `listEventTypes`, `queryEvents`, `executeQuery`, `getProfileInfo` — raw DuckDB when no purpose-built tool fits; the `jfr-sql` skill has the schema |
| `heap_` | Everything a heap dump answers — the `analyze-heap` skill has the order to run it in |
| `recordings_` | `analyzeFile` (a file not in Jeffrey yet), `analyzeRecording` (uploaded but never analysed), `status` (whether a slow analysis has finished), `list` (the Quick Analysis store) |

## 3. Choosing a flamegraph

`flamegraph_list` returns `available` — the event types this profile really recorded, each with
the export defaults for that type — and `notRecorded`, the standard groups the profiler did not
capture. Asking for a type it did not record returns an empty tree rather than an error, so check
first. Starting points:

- on-CPU time → `jdk.ExecutionSample`, or `jdk.CPUTimeSample` — JDK 25's CPU-time sampler (JEP 509)
  writes that type instead, so a recording made with it holds no `ExecutionSample` at all. Take
  whichever `flamegraph_list` offers rather than assuming the older one.
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

## 4. Read the export the way it tells you to

`flamegraph_export`, `traces_traceExport` and `traces_operationExport` return Markdown documents
that open with their own reading instructions — what `self` versus `total` means, what the frame
tags mean, what was pruned, and how to analyse that event type. Follow that preamble rather than
generic flamegraph conventions; Jeffrey's accounting is stated there in the version that matches
the code that produced it.

## Latency: work the traces first

"This endpoint is slow" is a traces question before it is a flamegraph question. When the profile has
no traces, `http_overview` and `jdbc_overview` answer the same question in aggregate — see the
technology families below. With traces, they answer it request by request:

1. `traces_overview` — totals, and how many **notifications** were raised inside traces.
   Notifications are `jeffrey.Notification` events an instrumented application emits about
   itself (a pool exhausted, a fallback taken). When any are `CRITICAL` or `HIGH`, call
   `traces_notifications` *before* exporting a trace: such a notification usually names the cause
   the span tree only shows the cost of, and its exemplar trace ids are the ones worth exporting.
2. `traces_operations` — sorted by `TOTAL_TIME` by default, which is where the wall-clock went.
   An operation is the triple `(name, kind, eventType)`, not a name alone: an inbound
   `GET /orders` and an outbound call to the same path are different operations.
3. `traces_operationExport` for the population, then `traces_slowestTraces` → `traces_traceExport`
   for one exemplar, then `traces_spanFlamegraphExport` for the frames inside a single slow span.

## The machine underneath: the `jvm_` family

Garbage collection, safepoints, JIT compilation, per-thread attribution, native memory, the
container and the JVM's own configuration are not flamegraph questions and `flamegraph_list` will
not offer them. They have their own family, and each tool renders the dashboard the Jeffrey UI
renders — the same tested builders, one call instead of six invented SQL queries.

`jvm_sections` first: a recording holds only what the profiler was told to capture, and each
section reports whether this one carries its events. A section asked for anyway is refused with the
events it needed, so an absence never arrives as a page of zeroes.

Every result carries a `nextSteps` list beside the figures, saying what that dashboard cannot
answer and which tool answers it — the same idea as the reading instructions an export opens with.
Follow it: the figures are one half of an answer, and the other half is usually in another family.

| Tool | The question it answers |
|---|---|
| `jvm_autoAnalysis` | Jeffrey's rule set over the whole recording — findings with a severity and a suggested fix. The cheapest first question about any profile. |
| `jvm_gc` | The stop-the-world budget, collections by generation and cause, what was freed, the longest collections |
| `jvm_safepoints` | The pauses that are **not** GC, and the threads that were slow to reach them |
| `jvm_jit` | Compiler totals, the slowest compilations, code cache, deoptimisation by method and reason |
| `jvm_threads` | Population and peak, top CPU and allocating threads, virtual-thread pinning |
| `jvm_nativeMemory` | RSS and its growth, direct buffers, NMT categories — memory outside the Java heap |
| `jvm_container` | cgroup limits and whether the scheduler throttled the process |
| `jvm_configuration` | What the JVM was started with, in the UI's own tabs; one section at a time |
| `jvm_flags` | The flag list with each value's **origin** — default, command line, or the JVM's own ergonomics |
| `jvm_threadDumps` | The dumps together: deadlocks, monitors threads queued on, threads stuck across dumps |
| `jvm_threadDump` | One dump in full, every thread with its state and stack |
| `jvm_gcDetail` | The GC pages beneath the overview, one at a time: tenuring, IHOP, G1 regions and evacuation failures, ZGC stalls, string tables, finalizers, reference processing, phases, PLAB |
| `jvm_classLoading` | What was loaded and who loaded it: metaspace, the loaders ranked, the slowest loads, agent redefinitions |
| `jvm_exceptions` | What the application threw, and what kinds — constructing one walks the stack, so a type thrown in a loop is a cost no frame names |
| `jvm_system` | The machine underneath: machine CPU against this JVM's own, and what the difference leaves for everything else on the box |
| `jvm_security` | TLS handshakes, the protocols and ciphers negotiated, certificates expiring or weakly signed, what was deserialized |

Four things the tools know and a hand-written query does not:

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

`jvm_autoAnalysis` reads a cache. When nothing has computed it, pass `compute: true` to run the rule
set now — it reads the whole recording through the JMC toolkit, so it is slow and asked for rather
than assumed. The other sections answer either way. For anything these do not shape — a distribution over
time, a correlation between two event types — the `jfr-sql` skill has the schema and the queries.

## The edges of the application: `http_`, `jdbc_`, `grpc_`, `methodtracing_`

Where `jvm_` answers for the machine, these answer for what the application did at its boundaries.
Each `_overview` is the whole dashboard in one call — the header totals, the entities ranked, the
status breakdown and the slowest individual operations — so reach for a drill-down only to narrow to
one endpoint, service or statement group.

| Tool | The question it answers |
|---|---|
| `http_overview` | Requests, response-time percentiles, success rate, 4xx/5xx, endpoints by traffic, slowest requests |
| `http_endpoint` | The same for one URI, taken from the endpoints list |
| `jdbc_overview` | Statement count and percentiles, the operation mix, statement groups by cost, slowest statements with their SQL |
| `jdbc_statementGroup` | The same, narrowed to one group |
| `jdbc_pools` | Configured min/max against peak and average use, threads that waited, acquisition timeouts |
| `grpc_overview` / `grpc_service` | gRPC latency overall and per service, broken down by method |
| `grpc_traffic` | Message sizes rather than timings — where an oversized payload shows up |
| `methodtracing_overview` / `_slowest` / `_timing` | Instrumented method timings: by cost, the worst invocations, the JVM's own aggregates |

Three things worth knowing before reading them:

- **"This endpoint is slow" starts here, not in a flamegraph.** `http_overview` names the endpoint
  and `jdbc_overview` says whether the database is the reason, in two calls. Go to `traces_` when
  the question is *which request* and what it did span by span, and to `flamegraph_export` once the
  question is which frames burned the time.
- **Slow requests whose statements are all fast are usually waiting for a connection.** That is
  `jdbc_pools`, and nothing in the statement view can show it — a pool with acquisition timeouts
  makes every query look fine while the request waits in front of them.
- **Both directions, and they are different questions.** `direction: SERVER` is what the application
  was asked to do; `CLIENT` is what it asked of somebody else, where a slow figure belongs to a
  dependency and the only local fixes are to call less often or stop waiting. They are gated
  separately, so "no client-side data" means the recording captured no outbound calls, not that the
  dashboard is broken. The per-second chart series are left out on purpose — the percentiles carry
  the same information, and the shape over time is what `timeline_` and the `uiLink` are for.

An event type the recording never captured is reported in words rather than as a zeroed dashboard:
"no HTTP server data" is a finding about the profiler's configuration, not a healthy service.

## When, not where: the `timeline_` family

A flamegraph of a whole recording flattens a thirty-second spike into a five-minute average, and the
spike stops being visible. `flamegraph_export`, `compare_flamegraph` and the trace exports all take
`startMs` and `endMs`, and nothing else in the surface helps you choose them.

1. `timeline_hotWindows` with the event type — the recording bucketed, the busiest windows ranked,
   and a one-line shape so a steady load, a ramp and a single burst are told apart. Pass `useWeight`
   for bytes or nanoseconds rather than sample counts.
2. `flamegraph_export` with the `startMs` and `endMs` of the window it named. That graph shows what
   the whole-recording one averaged away.
3. `timeline_zoom` when a second is too coarse — a startup, or the inside of one spike. It is the
   only view that resolves below a second.

Neither returns the raw series: what comes back is the ranked windows and the shape, because a curve
is thousands of numbers a reader cannot act on and the window bounds are the part the next tool takes.

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

## Which population, not which operation: trace attributes

`traces_operations` groups by the shape of the request. That cannot answer the question most latency
investigations actually end at — one population is slow and the rest is fine. The same endpoint,
fast for almost everyone and terrible for one tenant, has a healthy average and a p99 nobody can
locate.

1. `traces_attributeKeys` — what the spans recorded. A key is the triple `(source, owner, key)`, and
   the other two tools take all three; an `EVENT_FIELD` always has an owner, an `ATTRIBUTE` usually
   does not.
2. `traces_attributeValues` — one key split into its values, each with its own p50, p95, max and
   error count. A value whose p95 stands apart from the rest names the population. Trace counts do
   not sum to the profile's total: a trace whose spans recorded two values counts under both.
3. `traces_attributeSearch` — the individual traces carrying one value, whose ids go to
   `traces_traceExport`.

An attribute says *which* population was slow and never *why*. The span tree of one of its traces
says why.

## Memory without a heap dump

`memory_allocations` ranks the **types** allocated where the allocation flamegraph ranks the call
**sites** — the other axis, and often the one that names the problem, since `byte[]` and `char[]` at
the top read very differently from a domain class. `memory_leakCandidates` reads
`jdk.OldObjectSample`: objects the JVM watched survive collections, which is leak evidence from a
plain recording when nobody captured a dump and the process has gone. Its absence is not a clean
bill of health — that sampler is off in most profiles, and the tool says so rather than reporting
zero candidates.

## Hand the reading to the analyst

An export can run to 120,000 characters, and answering a question well often takes several. A
**`profile-analyst`** agent runs a sequence and returns only the findings — the hottest frames with
their shares, the causes named. Everything it read stays in its own context. The Claude Code plugin
ships it as `microscope:profile-analyst`; in Codex it is the custom agent from
`codex/agents/profile-analyst.toml`. If your client has no such agent, read the exports here and
keep the sequence short.

Delegate when more than one export is in play — several event types, a whole trace operation, a
deep chase down one path — and give it the `profileId` and the one question. Independent questions
go out in a single message so they run at once.

Read an export here, in this conversation, when there is exactly one and its result will be
discussed turn by turn. The analyst returns a report; it cannot answer a follow-up about a document
the conversation never saw.

Whatever it reports, keep here: mapping frames onto the checkout, the recommendation, and every
question to the user. The analyst has no file tools and never proposes changes.

## Grounding claims

The exports carry call paths and figures, not source locations. Cite the path and the numbers the
document shows. If the repository is open alongside, read the real source before naming a file,
method or line — never infer them from a frame name. The `advise-jfr` skill carries the full
profile-to-code-change workflow.

## When something fails

- `Profile … has no heap dump` → it is a JFR recording; stay with `jfr_`, `flamegraph_`, `traces_`.
- `The recording path must be absolute` → pass the full path, not a repository-relative one.
- `No such recording file` but the file is right there → Jeffrey is looking on *its own*
  filesystem. Copy or mount the recording where Jeffrey can reach it, or upload it in the UI.
- No `recordings_` tool advertised → the installation trimmed the tool list with
  `jeffrey.microscope.mcp.families`. Upload and analyse in the Jeffrey UI, then work from
  `profiles_list`.
- Every call fails to connect → Jeffrey is not running at the configured address. Point the client
  at the real `…/api/internal/mcp` endpoint: in Claude Code, `/plugin` → `microscope` → **Jeffrey MCP
  endpoint**; in Codex, the `[mcp_servers.jeffrey]` block in `~/.codex/config.toml`. Jeffrey's
  **Settings → Coding Agents (MCP)** shows the URL for the installation.
- The server answers 404 → it was started with `jeffrey.microscope.mcp.enabled=false`; it is on
  by default.

Related skills: `analyze-heap` for a heap dump, `compare-jfr` when there is a before and an after
to weigh against each other, `jfr-sql` for raw SQL against the profile, `advise-jfr` to go from a
hotspot to an edit in this repository.
