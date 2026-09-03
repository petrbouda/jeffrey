---
name: jfr-sql
description: Write DuckDB SQL against a Jeffrey profile's JFR database — the events, event_types, threads, stacktraces and frames tables — with ready queries for garbage collection and JIT compilation, which have no purpose-built tools. Use when jfr_executeQuery or jfr_queryEvents is needed because no flamegraph, trace or heap tool answers the question, or when the question is about GC pauses, heap growth across collections, compilation or deoptimisation.
allowed-tools: mcp__plugin_microscope_jeffrey__jfr_* mcp__jeffrey__jfr_*
---

# Querying a profile's JFR database

Each profile is one DuckDB database. Reach it with `jfr_executeQuery` (SELECT/WITH only, row-capped)
or `jfr_queryEvents` for the common "give me events of this type" case.

Prefer a purpose-built tool where one exists — `flamegraph_export` beats reconstructing a call tree
by hand, and the `heap_` family beats querying the heap index. SQL is for the questions they do not
cover: distributions over time, correlations between event types, cardinality of a field.

## Query `events`, never `events_raw`

`jfr_listTables` shows both. `events` is a view over `events_raw` that splices back the one large
string field the parser pools out of each row. Querying `events_raw` silently returns truncated JSON
in `fields`, with no error to warn you.

## Do not guess column names

Call `jfr_describeTable('events')` first. In particular the duration column is `duration` — not
`duration_ns`, not `duration_ms`.

## The tables

- **`events`** — one row per event. `event_type`, `start_timestamp` (TIMESTAMPTZ),
  `start_timestamp_from_beginning` (BIGINT millis since recording start — sargable, prefer it for
  relative time filters), `duration`, `samples`, `weight`, `weight_entity`, `stacktrace_hash`,
  `thread_hash`, `fields` (JSON).
- **`event_types`** — `name`, `label`, `description`, `categories`. Metadata for every type present.
- **`threads`** — `thread_hash` (PK), `name`, `os_id` (null for virtual threads), `java_id`, `is_virtual`.
- **`stacktraces`** — `stacktrace_hash` (PK), `type_id`, `frame_hashes` (BIGINT array, ordered
  **root-first** — see the stacks idiom below), `tag_ids` (INTEGER array; `0` marks an idle stack,
  `1` an unsafe allocation — the tags the flamegraph's `excludeIdle` option filters on, matched with
  `list_has_any(tag_ids, [0])`).
- **`frames`** — `frame_hash` (PK), `class_name`, `method_name`, `frame_type`, `line_number`,
  `bytecode_index`, `hidden_class_id`.

Derived from the events when the profile carries Jeffrey Tracing, and empty otherwise:

- **`traces`** — one row per trace: `trace_id` (PK), `root_name`, `root_kind`, `root_event_type`
  (together the operation), `start_timestamp`, `start_timestamp_from_beginning`, `duration`,
  `span_count`, `error_count`.
- **`trace_spans`** — one row per span: `trace_id`, `span_id`, `parent_span_id`, `name`, `kind`,
  `status`, `error_type`, `start_timestamp_from_beginning`, `duration`, `self_duration`,
  `thread_hash`, `event_type`, `attributes` (JSON text).
- **`trace_notifications`** — what the application said inside a trace: `trace_id`, `span_id`
  (NULL when no span was open), `notification_id`, `start_timestamp_from_beginning`, `type`,
  `severity`, `category`, `source`, `attributes`, and `message_ref` into
  **`trace_notification_messages`** (`message_id`, `message_text`), which holds each distinct
  sentence once. Prefer `traces_notifications` for the grouped reading; SQL is for a question it
  does not shape, such as notifications per minute.

## Idioms that matter

- **Durations are nanoseconds.** `duration / 1000000` → milliseconds.
- **Event-specific data is JSON** in `fields`: `fields->>'key'`, or `json_extract(fields, '$.key')`.
  Cast before comparing numerically: `CAST(fields->>'bytesRead' AS BIGINT) > 100`.
- **Timestamps**: `epoch_ms(start_timestamp)` for epoch millis.
- **Stacks**: `UNNEST(frame_hashes)` and join to `frames`. The array is stored **root-first**: the
  first element is the thread entry point (`Thread.run`) and the **last** is the leaf that was
  actually executing. Taking the first element as "the hot method" is the mistake to avoid — it
  returns the entry point on every stack in the recording. Keep the position with
  `generate_subscripts(frame_hashes, 1)` beside the `UNNEST`, and order by it descending for the
  topmost-first reading a stack trace normally has:

  ```sql
  WITH positioned AS (
    SELECT UNNEST(frame_hashes) AS frame_hash,
           generate_subscripts(frame_hashes, 1) AS depth
    FROM stacktraces WHERE stacktrace_hash = ?
  )
  SELECT f.class_name, f.method_name FROM positioned p
  JOIN frames f USING (frame_hash) ORDER BY p.depth DESC
  ```

  For the leaf alone, `frame_hashes[-1]` — DuckDB lists are 1-based, and negative indices count
  from the end.
- **Hidden classes**: `class_name` never carries the per-run address of a JEP 371 hidden class
  (lambda proxy, method-handle form, indified string concat). The address lives in `hidden_class_id`,
  so `hidden_class_id IS NOT NULL` selects hidden frames — a null check, not a `LIKE`.
- **GROUP BY**: every non-aggregated column in the SELECT must appear in it.
  `SELECT event_type, COUNT(*) FROM events` is invalid; add `GROUP BY event_type`.

## Event types worth knowing

CPU `jdk.ExecutionSample`, `jdk.NativeMethodSample` · allocation `jdk.ObjectAllocationSample`,
`jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB` · GC `jdk.GarbageCollection`,
`jdk.GCPhasePause`, `jdk.GCHeapSummary`, `jdk.YoungGarbageCollection`, `jdk.OldGarbageCollection`,
`jdk.G1GarbageCollection` · threading `jdk.ThreadPark`, `jdk.JavaMonitorEnter`,
`jdk.JavaMonitorWait` · I/O `jdk.FileRead`, `jdk.FileWrite`, `jdk.SocketRead`, `jdk.SocketWrite` ·
JIT `jdk.Compilation`, `jdk.Deoptimization`, `jdk.CompilerStatistics`, `jdk.CodeCacheStatistics` ·
tracing `jeffrey.Notification` (the application's own reports; its `fields` carry `traceId`,
`type`, `severity`, `message`), plus the span event types listed by `jfr_listEventTypes`.

`jfr_listEventTypes` gives the ones this profile actually recorded, with counts — use it rather than
assuming.

## Garbage collection has no purpose-built tool

Neither GC nor JIT has an MCP family: `flamegraph_`, `traces_` and `heap_` do not cover them, so
these queries are the whole route. (Jeffrey's UI has full dashboards for both — `profiles_link`
opens them when the interactive version would be quicker than a query.)

`jdk.GarbageCollection` is one row per collection, carrying `gcId`, `name` (the collector, `…Full`
for a full GC), `cause`, `sumOfPauses` and `longestPause`.

**Rank by `sumOfPauses`, never by `duration`.** For ZGC, Shenandoah and G1's concurrent cycles the
event's `duration` spans phases the application ran straight through, so ordering by it reports
pauses that never happened. `sumOfPauses` and `longestPause` are the stop-the-world figures; use
`duration` only where they are absent.

```sql
SELECT fields->>'name' AS collector,
       fields->>'cause' AS cause,
       COUNT(*) AS collections,
       SUM(CAST(fields->>'sumOfPauses' AS BIGINT)) / 1000000.0 AS total_pause_ms,
       MAX(CAST(fields->>'longestPause' AS BIGINT)) / 1000000.0 AS worst_pause_ms
FROM events
WHERE event_type = 'jdk.GarbageCollection'
GROUP BY collector, cause
ORDER BY total_pause_ms DESC
```

Heap before and after each collection — `jdk.GCHeapSummary` is **two** rows per `gcId`, told apart
by `when`, so pivot rather than joining the table to itself:

```sql
WITH summaries AS (
  SELECT CAST(fields->>'gcId' AS BIGINT) AS gc_id,
         fields->>'when' AS phase,
         CAST(fields->>'heapUsed' AS BIGINT) AS heap_used
  FROM events WHERE event_type = 'jdk.GCHeapSummary'
)
SELECT gc_id,
       MAX(heap_used) FILTER (WHERE phase = 'Before GC') AS before_bytes,
       MAX(heap_used) FILTER (WHERE phase = 'After GC') AS after_bytes
FROM summaries
GROUP BY gc_id
ORDER BY gc_id
```

A live set that climbs across `after_bytes` is a retention problem — a heap dump answers it, this
recording does not.

Where a pause went inside one collection: `jdk.GCPhasePause` (children in `jdk.GCPhasePauseLevel1`
through `Level4`), grouped by `fields->>'name'` and filtered on `fields->>'gcId'`.
`jdk.GCPhaseConcurrent` holds the phases outside the pause.

One row each, read before proposing a flag: `jdk.GCConfiguration`, `jdk.GCHeapConfiguration`,
`jdk.GCSurvivorConfiguration`, `jdk.GCTLABConfiguration`. Collector-specific pressure lives in
`jdk.ZAllocationStall` (threads that waited for memory — ZGC's latency symptom, not a pause),
`jdk.EvacuationFailed` and `jdk.TenuringDistribution`.

Nothing here names the code that produced the garbage. Once the pause budget shows GC matters, the
allocation flamegraph does — `flamegraph_export` on `jdk.ObjectAllocationSample` with
`useWeight: true`.

## JIT compilation and deoptimisation

`jdk.Compilation` is one row per compilation: `method`, `compileLevel`, `compileId`, `codeSize`,
`isOsr`, `succeded` (JFR's own spelling — false means the method fell back to the interpreter).
Method-typed fields are flattened by the parser to `fully.qualified.Type#name`, in `jdk.Compilation`
and `jdk.Deoptimization` alike, so grouping by `fields->>'method'` gives a string that can be
grepped for in a checkout — no struct to unpack.
The event fires only above the recording's threshold setting, so no rows means nothing compiled
*slowly*; `jdk.CompilerStatistics` has the totals regardless (`compileCount`, `bailoutCount`,
`invalidatedCount`, `osrCompileCount`, `totalTimeSpent`).

```sql
SELECT fields->>'method' AS method,
       CAST(fields->>'compileLevel' AS INTEGER) AS level,
       duration / 1000000.0 AS compile_ms,
       CAST(fields->>'codeSize' AS BIGINT) AS code_size,
       fields->>'succeded' AS succeeded
FROM events
WHERE event_type = 'jdk.Compilation'
ORDER BY duration DESC
LIMIT 25
```

Deoptimisation is where the JIT turns into a latency problem. Group by method **and** reason: one
method deoptimised repeatedly ran interpreted for part of the recording, and the reason
(`unstable_if`, `class_check`, `null_check`) says what to look for in the source.

```sql
SELECT fields->>'method' AS method,
       fields->>'reason' AS reason,
       fields->>'action' AS action,
       COUNT(*) AS deopts
FROM events
WHERE event_type = 'jdk.Deoptimization'
GROUP BY method, reason, action
ORDER BY deopts DESC
LIMIT 25
```

`jdk.CodeCacheStatistics` and `jdk.CodeCacheFull` answer whether compilation stopped altogether —
a full code cache leaves the application at interpreted speed for the rest of the run — and
`jdk.CompilerQueueUtilization` shows the queues backing up, which reads as a slow warm-up rather
than a steady-state cost.

## Results are capped

`jfr_executeQuery` caps rows and total characters and says so when it truncates. Aggregate in SQL
rather than pulling rows back to count them.

## One statement, and no way out of the database

`jfr_executeModification` is not exposed to external clients: data cleanup and frame renaming happen
in the Jeffrey UI. Two rules follow from how the read tools are sandboxed, and both fail loudly
rather than silently:

- **One statement per call.** Anything after a semicolon is refused before the query runs. Send the
  `SELECT` on its own.
- **The engine has no filesystem.** The profile database is opened with DuckDB's external file
  access off, so `read_text`, `read_csv`, `read_parquet`, `glob` and `ATTACH` all fail. Nothing is
  lost for analysis — every table you need is in this database — but a query that reaches for one
  comes back with a permission error rather than a confusing empty result.
