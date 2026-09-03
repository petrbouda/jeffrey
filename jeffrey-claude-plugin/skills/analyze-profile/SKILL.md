---
name: analyze-profile
description: Analyse a JVM profile held by a running Jeffrey Microscope — CPU, allocation, lock contention, GC, latency, traces or a heap dump. Use whenever the question is "why is this slow", "where does the time go", "what is allocating", "what is holding memory", or when a Jeffrey profile, JFR recording or heap dump is mentioned.
allowed-tools: mcp__plugin_microscope_jeffrey__* mcp__jeffrey__*
---

# Analysing a Jeffrey profile

Jeffrey holds *profiles*: one analysed JFR recording or heap dump each. The tools read them; they
never write.

## Always start here

1. **`profiles_list`** — every profile in the installation. Nothing else works without an id from it.
2. **`profiles_features`** — what that profile can actually answer. A JFR recording usually has no
   heap dump; a heap dump has no flamegraphs; traces exist only if the app ran Jeffrey's tracing
   instrumentation. It also lists every event type recorded, with sample counts.
3. Then the family that matches the question.

Every tool except `profiles_list` takes a `profileId`.

## The families

| Family | Use it for |
|---|---|
| `profiles_` | `list`, `get` (identity, recording window, size), `features`, `link` (deep link into the Jeffrey UI) |
| `flamegraph_` | `panels` (which event types this profile can graph — call it first), `export` (the call tree as Markdown) |
| `traces_` | `overview`, `operations`, `operationExport`, `slowestTraces`, `traceExport`, `spanFlamegraphExport`, `operationFlamegraphExport` |
| `jfr_` | `listTables`, `describeTable`, `listEventTypes`, `queryEvents`, `executeQuery`, `getProfileInfo` — raw DuckDB when no purpose-built tool fits |
| `heap_` | 20 tools: `getHeapSummary`, `getClassHistogram`, `getBiggestObjects`, `getLeakSuspects`, `getPathToGCRoot`, `getDominatorTree*`, `executeQuery`, … |

Tool names are camelCase after the prefix: `jfr_listTables`, not `jfr_list_tables`.

## Reading the exports

`flamegraph_export`, `traces_traceExport` and `traces_operationExport` return Markdown documents
that **carry their own reading instructions** — what `self` versus `total` means, what the frame
tags mean, what was pruned, and how to analyse that particular event type. Read the preamble the
document gives you and follow it. Do not substitute assumptions about flamegraph conventions from
elsewhere; Jeffrey's accounting is stated precisely in the document and differs in places (its
`self` is a merged-interval computation, not a subtraction).

## Choosing a graph

`flamegraph_panels` lists the event types this profile really recorded. Asking for one it did not
record returns an empty tree rather than an error, so check first. Common starting points:

- on-CPU time → `jdk.ExecutionSample`
- allocation → `jdk.ObjectAllocationSample` (add `useWeight: true` to rank by bytes, not by call count)
- lock contention → `jdk.JavaMonitorEnter` (with `useWeight: true`, weight is nanoseconds blocked)
- wall-clock latency, including off-CPU → `jdk.WallClockSample`

`thresholdPct` controls how much detail survives pruning. Raise it for an overview, lower it to
chase a specific path.

## Working a latency question with traces

`traces_overview` → `traces_operations` (sorted by `TOTAL_TIME` by default, which is where the
wall-clock actually went) → `traces_operationExport` for the population → `traces_slowestTraces`
then `traces_traceExport` for one exemplar → `traces_spanFlamegraphExport` for the frames inside a
single slow span. An operation is identified by the triple `(name, kind, eventType)`, not by name
alone — an inbound `GET /orders` and an outbound call to the same path are different operations.

## Grounding claims

The exports contain call paths and numbers, not source locations. Cite the path and the figures the
document shows. If the repository is open alongside, read the real source before proposing a change
— do not infer file or line numbers from a profile.

## When something is missing

- A tool answers `Profile … has no heap dump` → it is a JFR recording; use `jfr_`, `flamegraph_`, `traces_`.
- Every call fails to connect → Jeffrey is not running at that address.
- The server 404s → this Jeffrey was started with `jeffrey.microscope.mcp.enabled=false`. The server
  is on by default; **Settings → Claude Code (MCP)** reports whether it is serving.
- Jeffrey is not on `http://localhost:8585` → point the plugin at the real
  `…/api/internal/mcp` endpoint: run `/plugin`, open the `microscope` plugin's configuration
  and set **Jeffrey MCP endpoint**.

For raw SQL against a profile or a heap dump, see the `jfr-sql` and `heap-sql` skills.
