---
name: heap-sql
description: Query a Jeffrey heap-dump index with DuckDB SQL — the class, instance, outbound_ref, gc_root, dominator and retained_size tables. Use when heap_executeQuery is needed because the purpose-built heap tools do not answer the question.
allowed-tools: mcp__plugin_jeffrey_microscope__heap_* mcp__jeffrey__heap_*
---

# Querying a heap-dump index

A parsed heap dump becomes its own DuckDB index, separate from the profile's JFR database.

## Try the purpose-built tools first

`heap_getHeapSummary`, `heap_getClassHistogram`, `heap_getBiggestObjects`, `heap_getLeakSuspects`,
`heap_getClassLoaderLeakChains`, `heap_getTopConsumers`, `heap_getStringAnalysis`,
`heap_getCollectionAnalysis`, `heap_getDominatorTreeRoots` / `Children`, `heap_getPathToGCRoot`,
`heap_getReferrers`, `heap_browseClassInstances`, `heap_getInstanceDetail`.

Several of these are pre-computed reports; reproducing them in SQL is slower and easier to get
wrong. Reach for `heap_executeQuery` only for a question they do not cover. `heap_listTables` and
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
  the contents of Java `String` instances.

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
