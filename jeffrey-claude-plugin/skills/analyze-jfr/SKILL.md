---
name: analyze-jfr
description: Analyses a JVM profile held by a running Jeffrey Microscope — CPU, wall-clock, allocation, lock contention, garbage collection, JIT compilation and trace latency — starting from the catalogue or from a .jfr file Jeffrey has not seen yet. Use whenever the user asks why something is slow, where the time goes, what is allocating, why GC pauses are long or the heap keeps filling, what the JIT compiler or deoptimisation is doing, what a JFR recording or flamegraph shows, or mentions a Jeffrey profile, a .jfr file or async-profiler output. For a heap dump or .hprof file, analyze-heap applies instead.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a Jeffrey profile

Jeffrey holds *profiles*: one analysed JFR recording or heap dump each. Every tool reads; the one
family that writes is `recordings_`, which turns a recording *file* into a profile.

Tool names below omit the server prefix — `mcp__plugin_microscope_jeffrey__` for the plugin,
`mcp__jeffrey__` for a hand-registered server. The part after it is exact and camelCase:
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

**Otherwise** — `profiles_list`, pick the profile, then `profiles_features` to learn what it can
answer before asking for it: `disabledFeatures` names what the profile lacks (traces exist only if
the app ran Jeffrey's instrumentation; `HEAP_DUMP` there means no dump), and `eventTypes` lists
every recorded event type with its sample and weight totals.

A profile whose `event source` column reads `HEAP_DUMP` is a heap dump: switch to the
`analyze-heap` skill, because flamegraphs and traces do not apply to it.

## 2. Pick the family that matches the question

| Family | Tools |
|---|---|
| `profiles_` | `list`, `get` (identity, recording window, size, source commit), `features`, `link` (deep link into the Jeffrey UI) |
| `flamegraph_` | `list` (which event types this profile can graph — call it first), `export` (the call tree as Markdown) |
| `compare_` | `list` (whether two profiles are comparable at all — call it first), `movements` (what moved, ranked), `flamegraph` (the differential call tree) — the `compare-jfr` skill has the workflow |
| `traces_` | `overview`, `operations`, `notifications`, `operationExport`, `slowestTraces`, `traceExport`, `spanFlamegraphExport`, `operationFlamegraphExport` |
| `jfr_` | `listTables`, `describeTable`, `listEventTypes`, `queryEvents`, `executeQuery`, `getProfileInfo` — raw DuckDB when no purpose-built tool fits, and the **only** route to garbage collection and JIT compilation; the `jfr-sql` skill has the schema |
| `heap_` | Everything a heap dump answers — the `analyze-heap` skill has the order to run it in |
| `recordings_` | `analyzeFile` (a file not in Jeffrey yet), `analyzeRecording` (uploaded but never analysed), `list` (the Quick Analysis store) |

## 3. Choosing a flamegraph

`flamegraph_list` returns `available` — the event types this profile really recorded, each with
the export defaults for that type — and `notRecorded`, the standard groups the profiler did not
capture. Asking for a type it did not record returns an empty tree rather than an error, so check
first. Starting points:

- on-CPU time → `jdk.ExecutionSample`
- allocation → `jdk.ObjectAllocationSample`, with `useWeight: true` to rank by bytes rather than call count
- lock contention → `jdk.JavaMonitorEnter`, with `useWeight: true` (weight is nanoseconds blocked)
- wall-clock latency including off-CPU → `profiler.WallClockSample` — async-profiler's event, so it
  does **not** carry the `jdk.` prefix its neighbours do

`thresholdPct` decides how much survives pruning: raise it for an overview, lower it to chase one
specific path.

## 4. Read the export the way it tells you to

`flamegraph_export`, `traces_traceExport` and `traces_operationExport` return Markdown documents
that open with their own reading instructions — what `self` versus `total` means, what the frame
tags mean, what was pruned, and how to analyse that event type. Follow that preamble rather than
generic flamegraph conventions; Jeffrey's accounting is stated there in the version that matches
the code that produced it.

## Latency: work the traces first

"This endpoint is slow" is a traces question before it is a flamegraph question:

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

## GC and JIT: no family of their own

Neither subsystem has a purpose-built family, so `flamegraph_list` will not offer them and nothing
in the tool list points at them. Over MCP they are reached as events: `jfr_listEventTypes` for what
this recording carries, then `jfr_queryEvents` or `jfr_executeQuery` — the `jfr-sql` skill has the
schema and the queries. Jeffrey's UI has full Garbage Collection and JIT Compilation dashboards
(timeseries, pause distribution, G1 and ZGC deep dives, code cache, deoptimisation) that are not
exported over MCP, so hand the user `profiles_link` when the interactive version answers faster
than a query would.

**Garbage collection.** `jdk.GarbageCollection` is one row per collection: `gcId`, `name` (the
collector — `…Full` marks a full GC), `cause`, `sumOfPauses`, `longestPause`.

- **Rank by `sumOfPauses`, never by the event's `duration`.** For ZGC, Shenandoah and G1's
  concurrent cycles the duration covers phases the application ran straight through; only
  `sumOfPauses` and `longestPause` are stop-the-world. Fall back to `duration` only when those
  fields are absent.
- Start with the pause budget: total stop-the-world time against the recording window from
  `profiles_get`. That is what decides whether GC is the problem at all, and it is the number to
  quote.
- Then the causes. `Allocation Rate` and `G1 Evacuation Pause` point back at allocating code;
  `System.gc()` and `Diagnostic Command` mean something is collecting by hand; `Metadata GC
  Threshold` is a class-loading problem wearing a GC costume.
- Reclaimed bytes and whether the live set is growing: `jdk.GCHeapSummary`, two rows per `gcId`
  told apart by `when` (`Before GC` / `After GC`).
- Where one pause went: `jdk.GCPhasePause` and its `Level1`–`Level4` children per `gcId`;
  `jdk.GCPhaseConcurrent` for the work outside the pause.
- Before proposing a flag, read the configuration the JVM actually ran with — `jdk.GCConfiguration`,
  `jdk.GCHeapConfiguration`, `jdk.GCSurvivorConfiguration`, `jdk.GCTLABConfiguration`.
- Collector-specific pressure: `jdk.ZAllocationStall` (a thread waited for memory — the ZGC
  symptom that surfaces as latency, not as a pause), `jdk.EvacuationFailed` for G1,
  `jdk.TenuringDistribution` for survivor sizing.

**The cause of GC is allocation.** Pauses are the symptom; the code that produced the garbage is
the fix, and no GC event names it. Once the budget shows GC matters, go to the allocation
flamegraph — `jdk.ObjectAllocationSample` with `useWeight: true` — for the call paths. A heap dump
of the same application answers the other half, what is *retained* rather than what is churned;
that is the `analyze-heap` skill.

**JIT compilation.** `jdk.Compilation` is one row per compilation: `method`, `compileLevel`,
`compileId`, `codeSize`, `isOsr`, and `succeded` (JFR's own spelling; false means the method fell
back to the interpreter). The parser flattens every method-typed field to `fully.qualified.Type#name`,
so a `method` value here can be grepped for in the checkout as it stands.

- The event only fires above the recording's threshold setting, so no rows means nothing compiled
  *slowly*, not that nothing compiled. `jdk.CompilerStatistics` carries the totals either way —
  `compileCount`, `bailoutCount`, `invalidatedCount`, `osrCompileCount`, `totalTimeSpent`.
- `jdk.Deoptimization` is where the JIT becomes a latency problem: `method`, `lineNumber`, `bci`,
  `reason`, `action`. One method deoptimised repeatedly (group by both `method` and `reason`) ran
  interpreted for part of the recording, and `unstable_if` or `class_check` on a hot method is
  worth chasing into the source.
- `jdk.CodeCacheStatistics` and `jdk.CodeCacheFull`: once the code cache fills, compilation stops
  and the application quietly settles at interpreted speed for the rest of its life.
- `jdk.CompilerQueueUtilization` shows the compiler queues backing up, which is a slow warm-up
  rather than a steady-state problem.
- Compiler threads also show up in the CPU flamegraph. Heavy JIT work at the start of a recording
  is warm-up; the same work sustained to the end is churn, and the deoptimisation events usually
  say why.

## Hand the reading to the analyst

An export can run to 120,000 characters, and answering a question well often takes several. The
plugin ships a subagent, **`microscope:profile-analyst`**, that runs a sequence and returns only
the findings — the hottest frames with their shares, the causes named. Everything it read stays in
its own context.

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
- No `recordings_` tool advertised → this Jeffrey runs with `jeffrey.microscope.mcp.ingest.enabled=false`.
  Upload and analyse in the Jeffrey UI, then work from `profiles_list`.
- Every call fails to connect → Jeffrey is not running at the configured address. Point the plugin
  at the real `…/api/internal/mcp` endpoint: `/plugin` → `microscope` → **Jeffrey MCP endpoint**;
  Jeffrey's **Settings → Claude Code (MCP)** shows the URL for the installation.
- The server answers 404 → it was started with `jeffrey.microscope.mcp.enabled=false`; it is on
  by default.

Related skills: `analyze-heap` for a heap dump, `compare-jfr` when there is a before and an after
to weigh against each other, `jfr-sql` for raw SQL against the profile, `advise-jfr` to go from a
hotspot to an edit in this repository.
