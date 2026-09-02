---
name: jfr-sql
description: Write DuckDB SQL against a Jeffrey profile's JFR database — the events, event_types, threads, stacktraces and frames tables. Use when jfr_executeQuery or jfr_queryEvents is needed because no purpose-built flamegraph, trace or heap tool answers the question.
allowed-tools: mcp__plugin_jeffrey_microscope__jfr_* mcp__jeffrey__jfr_*
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
  top-frame first).
- **`frames`** — `frame_hash` (PK), `class_name`, `method_name`, `frame_type`, `line_number`,
  `bytecode_index`, `hidden_class_id`.

## Idioms that matter

- **Durations are nanoseconds.** `duration / 1000000` → milliseconds.
- **Event-specific data is JSON** in `fields`: `fields->>'key'`, or `json_extract(fields, '$.key')`.
  Cast before comparing numerically: `CAST(fields->>'bytesRead' AS BIGINT) > 100`.
- **Timestamps**: `epoch_ms(start_timestamp)` for epoch millis.
- **Stacks**: `UNNEST(frame_hashes)` and join to `frames`. Preserve ordinality if the order matters —
  index 0 is the topmost frame.
- **Hidden classes**: `class_name` never carries the per-run address of a JEP 371 hidden class
  (lambda proxy, method-handle form, indified string concat). The address lives in `hidden_class_id`,
  so `hidden_class_id IS NOT NULL` selects hidden frames — a null check, not a `LIKE`.
- **GROUP BY**: every non-aggregated column in the SELECT must appear in it.
  `SELECT event_type, COUNT(*) FROM events` is invalid; add `GROUP BY event_type`.

## Event types worth knowing

CPU `jdk.ExecutionSample`, `jdk.NativeMethodSample` · allocation `jdk.ObjectAllocationSample`,
`jdk.ObjectAllocationInNewTLAB`, `jdk.ObjectAllocationOutsideTLAB` · GC `jdk.GCPhasePause`,
`jdk.YoungGarbageCollection`, `jdk.OldGarbageCollection`, `jdk.G1GarbageCollection` · threading
`jdk.ThreadPark`, `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait` · I/O `jdk.FileRead`,
`jdk.FileWrite`, `jdk.SocketRead`, `jdk.SocketWrite` · JIT `jdk.Compilation`, `jdk.CompilerPhase`.

`jfr_listEventTypes` gives the ones this profile actually recorded, with counts — use it rather than
assuming.

## Results are capped

`jfr_executeQuery` caps rows and total characters and says so when it truncates. Aggregate in SQL
rather than pulling rows back to count them.

## No writes

`jfr_executeModification` is not exposed to external clients. Data cleanup and frame renaming happen
in the Jeffrey UI.
