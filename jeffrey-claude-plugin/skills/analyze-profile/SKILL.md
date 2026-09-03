---
name: analyze-profile
description: Analyse a JVM profile with a running Jeffrey Microscope — CPU, allocation, lock contention, GC, latency, traces or a heap dump — including analysing a .jfr or .hprof file that is not in Jeffrey yet. Use whenever the question is "why is this slow", "where does the time go", "what is allocating", "what is holding memory", or when a Jeffrey profile, a JFR recording or a heap dump file is mentioned.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a Jeffrey profile

Jeffrey holds *profiles*: one analysed JFR recording or heap dump each. The analysis tools read
them and never write. The one family that writes is `recordings_`, which turns a recording *file*
into a profile.

## Start from a file, or from the catalogue

**If the user named a file** — `target/app.jfr`, `heap.hprof`, anything with a recording extension —
it may not be in Jeffrey yet. Call **`recordings_analyzeFile`** with its **absolute** path; it
imports the file, builds the profile, and returns the `profileId` the rest of this skill needs. Then
carry on from step 2 below.

Two constraints, both easy to trip on:

- The path is opened by the **Jeffrey process**, so the file must be on the machine Jeffrey runs on.
  A container or a remote Jeffrey cannot see your working directory.
- Each call imports the file **again** and builds another profile. If the same file may already be
  analysed, check `recordings_list` or `profiles_list` first and reuse the id.

The call returns only once the profile is built, which for a large recording takes a while — that is
the analysis running, not a hang.

**Otherwise start from the catalogue:**

1. **`profiles_list`** — every profile in the installation. Nothing else works without an id from it.
2. **`profiles_features`** — what that profile can actually answer. A JFR recording usually has no
   heap dump; a heap dump has no flamegraphs; traces exist only if the app ran Jeffrey's tracing
   instrumentation. It also lists every event type recorded, with sample counts.
3. Then the family that matches the question.

Every tool except `profiles_list` and the `recordings_` family takes a `profileId`.

## The families

| Family | Use it for |
|---|---|
| `profiles_` | `list`, `get` (identity, recording window, size), `features`, `link` (deep link into the Jeffrey UI) |
| `flamegraph_` | `panels` (which event types this profile can graph — call it first), `export` (the call tree as Markdown) |
| `traces_` | `overview`, `operations`, `notifications`, `operationExport`, `slowestTraces`, `traceExport`, `spanFlamegraphExport`, `operationFlamegraphExport` |
| `jfr_` | `listTables`, `describeTable`, `listEventTypes`, `queryEvents`, `executeQuery`, `getProfileInfo` — raw DuckDB when no purpose-built tool fits |
| `heap_` | 20 tools: `getHeapSummary`, `getClassHistogram`, `getBiggestObjects`, `getLeakSuspects`, `getPathToGCRoot`, `getDominatorTree*`, `executeQuery`, … |
| `recordings_` | `analyzeFile` (a file not in Jeffrey yet), `analyzeRecording` (one already uploaded but never analysed), `list` (the Quick Analysis store) |

Tool names are camelCase after the prefix: `jfr_listTables`, not `jfr_list_tables`.

## Reading the exports

`flamegraph_export`, `traces_traceExport` and `traces_operationExport` return Markdown documents
that **carry their own reading instructions** — what `self` versus `total` means, what the frame
tags mean, what was pruned, and how to analyse that particular event type. Read the preamble the
document gives you and follow it. Do not substitute assumptions about flamegraph conventions from
elsewhere; Jeffrey's accounting is stated precisely in the document and differs in places (its
`self` is a merged-interval computation, not a subtraction).

## Choosing a graph

`flamegraph_list` lists under `available` the event types this profile really recorded, each with
the export defaults for that type; `notRecorded` names the groups the profiler did not capture.
Asking for one it did not record returns an empty tree rather than an error, so check first. Common
starting points:

- on-CPU time → `jdk.ExecutionSample`
- allocation → `jdk.ObjectAllocationSample` (add `useWeight: true` to rank by bytes, not by call count)
- lock contention → `jdk.JavaMonitorEnter` (with `useWeight: true`, weight is nanoseconds blocked)
- wall-clock latency, including off-CPU → `profiler.WallClockSample` — async-profiler's event, so
  it does **not** carry the `jdk.` prefix its neighbours here do

`thresholdPct` controls how much detail survives pruning. Raise it for an overview, lower it to
chase a specific path.

## Working a latency question with traces

`traces_overview` → `traces_operations` (sorted by `TOTAL_TIME` by default, which is where the
wall-clock actually went) → `traces_operationExport` for the population → `traces_slowestTraces`
then `traces_traceExport` for one exemplar → `traces_spanFlamegraphExport` for the frames inside a
single slow span. An operation is identified by the triple `(name, kind, eventType)`, not by name
alone — an inbound `GET /orders` and an outbound call to the same path are different operations.

Read the application's own account before the timing. `traces_overview` reports how many
**notifications** — `jeffrey.Notification` events an instrumented application emits about itself,
a pool exhausted, a fallback taken — were raised inside traces, and how many were `CRITICAL` or
`HIGH`. When there are any, call `traces_notifications` (filter by `severity`, `type`, `source`, or
the operation triple) before exporting a trace: a CRITICAL notification usually names the cause
the span tree only shows the cost of, and its exemplar trace ids are the traces worth exporting.
The operation and trace exports carry their own Notifications section, and its instructions say
how to weigh each severity.

## Grounding claims

The exports contain call paths and numbers, not source locations. Cite the path and the figures the
document shows. If the repository is open alongside, read the real source before proposing a change
— do not infer file or line numbers from a profile.

## When something is missing

- A tool answers `Profile … has no heap dump` → it is a JFR recording; use `jfr_`, `flamegraph_`, `traces_`.
- `recordings_analyzeFile` says the path must be absolute → pass the full path, not a repo-relative one.
- It says there is no such file, but the file is right there → Jeffrey is looking on *its own*
  filesystem. Copy or mount the recording somewhere Jeffrey can reach, or upload it in the UI.
- No `recordings_` tool is advertised at all → this Jeffrey was started with
  `jeffrey.microscope.mcp.ingest.enabled=false`. Upload and analyse the recording in the Jeffrey UI,
  then work from `profiles_list`.
- Every call fails to connect → Jeffrey is not running at that address.
- The server 404s → this Jeffrey was started with `jeffrey.microscope.mcp.enabled=false`. The server
  is on by default; **Settings → Claude Code (MCP)** reports whether it is serving.
- Jeffrey is not on `http://localhost:8585` → point the plugin at the real
  `…/api/internal/mcp` endpoint: run `/plugin`, open the `microscope` plugin's configuration
  and set **Jeffrey MCP endpoint**.

For raw SQL against a profile or a heap dump, see the `jfr-sql` and `heap-sql` skills. To go from
a profile to a code change — hot frames mapped to this repository, a recommendation, an edit and a
re-profile — see the `advise` skill.
