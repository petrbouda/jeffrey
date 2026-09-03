---
name: heap-sql
description: Query a Jeffrey heap-dump index with DuckDB SQL — the class, instance, outbound_ref, gc_root, dominator and retained_size tables. Use when heap_executeQuery is needed because the purpose-built heap tools do not answer the question.
allowed-tools: mcp__plugin_microscope_jeffrey__heap_* mcp__jeffrey__heap_*
---

# Querying a heap-dump index

A parsed heap dump becomes its own DuckDB index, separate from the profile's JFR database.

## Try the purpose-built tools first

The `heap_` family already answers the usual questions with pre-computed reports — the summary, the
histogram, the dominator tree, leak suspects, class-loader leak chains, GC-root paths. The
`analyze-heap` skill covers which one answers what, and the order to run them in. Reproducing those
reports in SQL is slower and easier to get wrong.

Reach for `heap_executeQuery` only for a question they do not cover. `heap_listTables` and
`heap_describeTable` give the live schema.

## The tables

- **`dump_metadata`** (one row) — `id_size` (4 or 8), `hprof_version`, `compressed_oops`,
  `bytes_parsed`, `record_count`, `warning_count`, `parser_version`. Worth reading once to orient.
- **`class`** — `class_id` (PK), `name` (already dot-notation, e.g. `java.util.HashMap`),
  `super_class_id`, `classloader_id`, `instance_size`, `is_array`.
- **`instance`** — `instance_id`, `class_id` (nullable for primitive arrays), `shallow_size`,
  `array_length`, `record_kind` (TINYINT: `0` instance, `1` object array, `2` primitive array).
- **`outbound_ref`** — `source_id`, `target_id`, `field_kind`, `field_id`. The whole reference graph;
  indexed both directions.
- **`gc_root`** — `instance_id`, `root_kind`, `thread_serial`, `frame_index`.
- **`dominator`** — `instance_id`, `dominator_id`. **Built lazily** — empty until something asks for it.
- **`retained_size`** — `instance_id`, `bytes`. Populated alongside `dominator`, so LEFT JOIN it and
  expect NULLs on a heap whose dominator tree has not been built.
- **`string`** — `string_id`, `value`. The HPROF **UTF-8 string pool** (class and field names), *not*
  the contents of Java `String` instances — those are in `string_content`.
- **`string_content`** — `instance_id`, `content_length`, `content`. The decoded text of every
  `java.lang.String` *instance*: this is the table to query for what a string actually says.
  `content_length` is always the full character count, but `content` is NULL for a string longer
  than the index's large-content threshold, so a predicate on `content` silently misses the long
  ones. Size questions belong on `content_length`, which covers every string.
- **`class_instance_field`** — `class_id`, `field_index`, `name`, `basic_type`. Field names per
  class, in declaration order, and the only way to name a field in SQL. An object's full layout is
  its own class's rows plus every ancestor's, most-derived first; `field_index` is positional
  within one class, not across the hierarchy.
- **`class_interface`** — `class_id`, `interface_class_id`. Which interfaces a class implements.
- **`stack_frame`** and **`stack_trace_frame`** — the thread stacks the dump recorded.
  `stack_frame` has `frame_id` (PK), `class_name` (already resolved), `method_name`,
  `method_signature`, `source_file`, `line_number` (raw HPROF: `-1` no info, `-2` compiled,
  `-3` native); `stack_trace_frame` has `trace_serial`, `thread_serial`, `frame_index` (0-based,
  **topmost first**, the opposite of the JFR side) and `frame_id`.
- **`parse_warning`** — `file_offset`, `record_kind`, `severity` (`0` info, `1` warn, `2` error),
  `message`. What the parser skipped, truncated or recovered; `dump_metadata.warning_count` is
  the count of these, and reading them explains a dump whose totals look wrong.

## Idioms

Class histogram by instance count:

```sql
SELECT c.name, COUNT(*) AS instances, SUM(i.shallow_size) AS bytes
FROM instance i JOIN class c USING (class_id)
GROUP BY c.name ORDER BY bytes DESC LIMIT 50
```

Top retained classes — needs the dominator tree, so run `heap_getDominatorTreeRoots` once first:

```sql
SELECT c.name, SUM(r.bytes) AS retained
FROM instance i JOIN class c USING (class_id)
LEFT JOIN retained_size r USING (instance_id)
GROUP BY c.name ORDER BY retained DESC NULLS LAST LIMIT 25
```

What one object points at: `SELECT target_id FROM outbound_ref WHERE source_id = <objectId>`.

Subclasses, recursively over `super_class_id`:

```sql
WITH RECURSIVE subs AS (
  SELECT class_id FROM class WHERE name = 'java.util.AbstractMap'
  UNION ALL
  SELECT c.class_id FROM class c JOIN subs s ON c.super_class_id = s.class_id
) SELECT * FROM subs
```

## Caps and caveats

`heap_executeQuery` accepts SELECT/WITH only and enforces a row cap and a timeout — aggregate in SQL.
Shallow size is the object itself; retained size is what dies with it, and only the second answers
"who is holding this memory". Object ids are stable within one dump and meaningless across dumps.

Jeffrey's OQL engine is not exposed over MCP; OQL queries have to be run in the Jeffrey UI.
