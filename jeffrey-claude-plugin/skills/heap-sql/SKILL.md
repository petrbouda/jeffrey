---
name: heap-sql
description: "Load before the first heap_executeQuery call, whichever skill is driving the analysis: the schema of a Jeffrey heap-dump index (class, instance, outbound_ref, class_instance_field, string_content, gc_root, dominator, retained_size) and the traps that return a wrong answer without an error — the string table is the name pool, not the String contents; outbound_ref.field_id counts the whole field layout; retained sizes need the current tree. Use for what the purpose-built heap tools do not answer: what objects' fields or strings hold across a class, a count or join they do not shape."
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

Tool names below omit the prefix your client puts in front of them — `mcp__plugin_microscope_jeffrey__` in Claude Code with the `microscope` plugin, `mcp__jeffrey__` in Codex or wherever the server is registered by hand as `jeffrey`, `mcp_jeffrey_` in Gemini CLI; the rest of the name is exact camelCase.

## The tables

- **`dump_metadata`** (one row) — `id_size` (4 or 8), `hprof_version`, `compressed_oops`,
  `bytes_parsed`, `record_count`, `warning_count`, `parser_version`. Worth reading once to orient.
- **`class`** — `class_id` (PK), `name` (already dot-notation, e.g. `java.util.HashMap`),
  `super_class_id`, `classloader_id`, `instance_size`, `is_array`.
- **`instance`** — `instance_id`, `class_id` (nullable for primitive arrays), `shallow_size`,
  `array_length`, `record_kind` (TINYINT: `0` instance, `1` object array, `2` primitive array).
- **`outbound_ref`** — `source_id`, `target_id`, `field_kind`, `field_id`. `field_kind` `0` is an
  instance field: `field_id` is its position in the object's whole layout — its own class's fields
  first, then each superclass's, primitive fields counted too — so for a field the object's own class
  declares it equals that field's `field_index` in `class_instance_field`. `1` is an array element
  (`field_id` the element's index), `2` a static field (`source_id` the class object). The whole
  reference graph; indexed on `target_id` only, so "who points at X" is a lookup and "what X points
  at" is a scan — cheap once, slow when repeated per row.
- **`gc_root`** — `instance_id`, `root_kind`, `thread_serial`, `frame_index`.
- **`dominator`** — `instance_id`, `dominator_id`. **Built lazily** — empty until something asks for it.
- **`retained_size`** — `instance_id`, `bytes`. Populated alongside `dominator`, so LEFT JOIN it and
  expect NULLs on a heap whose dominator tree has not been built.
- A dump indexed by an earlier Microscope can hold an older dominator tree in both tables, which the
  tools read as not built and replace on first use. Run `heap_getDominatorTreeRoots` (or
  `heap_prepare`) before querying them, so the SQL reads the tree the tools do.
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

Who holds an object, transitively — every instance within five hops that leads to it, nearest
first. `USING KEY` keeps one row per instance, so the heap's cycles end the walk instead of
repeating it, and the `recurring` table is what has been visited so far. Swap `source_id` and
`target_id` for what it keeps reachable instead:

```sql
WITH RECURSIVE walk(instance_id, depth) USING KEY (instance_id) AS (
  SELECT <objectId>::BIGINT, 0
  UNION
  SELECT r.source_id, min(w.depth) + 1
  FROM walk w JOIN outbound_ref r ON r.target_id = w.instance_id
  WHERE w.depth < 5 AND r.source_id NOT IN (SELECT instance_id FROM recurring.walk)
  GROUP BY r.source_id
)
SELECT w.depth, w.instance_id, c.name
FROM walk w JOIN instance i USING (instance_id) LEFT JOIN class c USING (class_id)
WHERE w.depth > 0 ORDER BY w.depth, w.instance_id
```

Subclasses, recursively over `super_class_id`:

```sql
WITH RECURSIVE subs AS (
  SELECT class_id FROM class WHERE name = 'java.util.AbstractMap'
  UNION ALL
  SELECT c.class_id FROM class c JOIN subs s ON c.super_class_id = s.class_id
) SELECT * FROM subs
```

## Link what you name

Whenever a reply names something Microscope can open — the profile, a flamegraph, an operation, a
trace, an endpoint, a statement group, a GC, JIT or thread page, a heap class or object, a
differential, a catalogue row, a ledger entry — make the name a Markdown link marked with `↗`:
`[trace 7e34c5994dc96208 ↗](<its uiLink>)`, and in a table a column of `[↗ open](<its uiLink>)`.
Every link carries the mark — every profile named too, the analysed one and its baseline alike, and
a page named in passing ("the exceptions page"), the links most often left bare:
`[checkout candidate ↗](<its uiLink>)` — and never the bare URL. The `↗` is what makes it a link the
user sees: a terminal that renders links shows the text alone, clickable, with the URL behind it,
and an unmarked name reads as plain words. That holds in every reply, not only in a written report:
a finding, a ranked list, a follow-up offer, a sentence in passing. **Before you send a reply, check
it: every `](` closes a link text that holds `↗`.**

- Take the link from the answer the thing came from. A row with its own `uiLink` — a trace, an
  operation, an exemplar — links with that one; otherwise the answer's `uiLink` is the page that
  shows the row. Quote the `uiLinkNote` beside it when there is one.
- No link in hand for one of the profile's pages (GC, JIT, threads, the heap views, …) →
  `profiles_viewLink`, one cheap call; the profile itself → `profiles_link`.
- Never build, edit or guess a URL, and never fetch one: the link is for the user, and one typed by
  hand opens the wrong page or none.
- An area not yet analysed is not a thing and needs no link, and a result no page shows — a raw SQL
  query — has none; say so rather than inventing one.

## Caps and caveats

`heap_executeQuery` accepts SELECT and WITH only, caps the rows at `rowCap` (100, whatever `LIMIT`
the query carries) and times out after thirty seconds — aggregate in SQL rather than fetching rows to
count them. The answer is JSON: `columns`, then `rows` with SQL `NULL` as `null`, and `capped` saying
the query matched more rows than the cap. It runs against the index directly, so
`information_schema` works and so do CTEs. An `objectId` another heap tool takes is the decimal
string of an `instance_id`.

**SQL is not the only query language here.** `heap_oql` runs Jeffrey's OQL against the object graph
the index implies: every instance of a type *including its subclasses*
(`SELECT * FROM INSTANCEOF java.util.Map`), the retained set of a selection
(`SELECT AS RETAINED SET * FROM com.acme.Cache`), a filter over an object's own fields. Those have no
SQL spelling. Reach for SQL when the question is about the tables, and OQL when it is about objects.
Shallow size is the object itself; retained size is what dies with it, and only the second answers
"who is holding this memory". Object ids are stable within one dump and meaningless across dumps.

`heap_oql` answers with a link to Jeffrey's OQL console. The console opens empty — the link cannot
carry the query, as the answer's `uiLinkNote` says — so hand the reader the query text beside the
link, to paste there.
