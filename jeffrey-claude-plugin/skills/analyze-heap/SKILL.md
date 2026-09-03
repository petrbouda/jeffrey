---
name: analyze-heap
description: Analyses a heap dump held by a running Jeffrey Microscope — what is holding the memory, what is leaking, which class loader survived a redeploy, where the waste is — starting from the catalogue or from a .hprof file Jeffrey has not seen yet. Use whenever the user asks what is holding memory, why the heap keeps growing, why the JVM ran out of memory, what is leaking, or mentions retained size, a dominator tree, GC roots, a heap dump or an .hprof file.
allowed-tools: mcp__plugin_microscope_jeffrey__heap_* mcp__plugin_microscope_jeffrey__profiles_* mcp__plugin_microscope_jeffrey__recordings_* mcp__jeffrey__heap_* mcp__jeffrey__profiles_* mcp__jeffrey__recordings_*
---

# Analysing a heap dump

A parsed heap dump is a profile like any other, but it answers a different question from a JFR
recording: a recording says where the time went, a dump says what was alive at one instant and
what kept it alive. Every tool here reads; none changes the dump.

Tool names below omit the server prefix — `mcp__plugin_microscope_jeffrey__` for the plugin,
`mcp__jeffrey__` for a hand-registered server. The part after it is exact and camelCase:
`heap_getLeakSuspects`, not `heap_get_leak_suspects`.

## 1. Get a `profileId`

**The user named a file** (`heap.hprof`, `dump.hprof.gz`) — check `recordings_list` or
`profiles_list` for it first, because every `recordings_analyzeFile` call imports the file again
and creates another profile. If absent, call `recordings_analyzeFile` with the **absolute** path.
The Jeffrey process opens that path, so the file has to be on the machine Jeffrey runs on. The
call returns once the dump is parsed, which takes a while for a large dump; that is the work, not
a hang.

**Otherwise** — `profiles_list`, where the `event source` column reads `HEAP_DUMP` for the profiles
this skill applies to. `profiles_features` lists `HEAP_DUMP` under `disabledFeatures` when the
profile has no dump or its index is not ready yet.

## 2. Orient before analysing

- `heap_getDumpMetadata` — HPROF version, id size, compressed-oops flag, record count and
  `warning_count`. A non-zero `warning_count` means the parser skipped or truncated records, so
  every total below is a lower bound; read the `parse_warning` table (the `heap-sql` skill)
  before concluding anything from odd numbers.
- `heap_getHeapSummary` — live bytes and instances, class count, GC-root count.

## 3. Shallow is not retained

- **Shallow size** is the object itself: header and fields, nothing it points at.
- **Retained size** is everything that would be freed if the object were collected.

"What is holding this memory" is always a retained question. A histogram ranked by shallow size
answers a different one — what there is a lot of, not who is responsible for it.

## 4. Two kinds of heap tools

**Computed on demand.** The first call does the work and later calls reuse it: `heap_getHeapSummary`,
`heap_getClassHistogram`, `heap_getDominatorTreeRoots` / `heap_getDominatorTreeChildren`,
`heap_getPathToGCRoot`, `heap_getReferrers`, `heap_browseClassInstances`, `heap_getInstanceDetail`,
`heap_getThreads`, `heap_getGCRootSummary`, and the SQL tools.

Retained sizes and the dominator tree are built lazily by **`heap_getDominatorTreeRoots`**. Until
it has run once, the `dominator` and `retained_size` tables are empty and every retained figure is
*missing*, not zero. Call it once, early, before anything that ranks by retained size — skipping it
is the usual reason a heap session stalls on empty results.

**Pre-computed reports.** Jeffrey computes these when the user opens the report in the UI and
stores the result; over MCP they can only be read. Each answers `… has not been run yet` until then:

| Tool | Report to run in the Jeffrey UI |
|---|---|
| `heap_getLeakSuspects` | Leak Suspects |
| `heap_getBiggestObjects` | Biggest Objects |
| `heap_getClassLoaderLeakChains` | Class Loader Analysis |
| `heap_getTopConsumers` | Top Consumers |
| `heap_getStringAnalysis` | String Analysis |
| `heap_getCollectionAnalysis` | Collection Analysis |

When one answers that way, do not retry it. Give the user the `profiles_link` URL and the report's
name, and carry on with the on-demand route for the same question meanwhile — the table in step 5
names it — so the answer does not wait on the UI.

## 5. Pick the route

| Question | Sequence | Without the report |
|---|---|---|
| What is using the heap at all | `heap_getClassHistogram`, then `heap_getTopConsumers` for the same picture by package and class loader | The histogram alone |
| What is leaking | `heap_getLeakSuspects` → `heap_getPathToGCRoot` on the object it names → `heap_getReferrers` to walk outwards | `heap_getDominatorTreeRoots` → `heap_getPathToGCRoot` on the largest roots |
| Which single objects are the biggest | `heap_getBiggestObjects` for the flat ranking; `heap_getDominatorTreeRoots` → `heap_getDominatorTreeChildren` to walk into one | The dominator tree already is the ranking |
| Redeploys leak, or classes look duplicated | `heap_getClassLoaderLeakChains` — names the loader, the GC-root path keeping it alive and the pattern that matched (ThreadLocal, JDBC driver, JNI global, ServiceLoader, static logger, context class loader) | `heap_browseClassInstances` on the loader class → `heap_getPathToGCRoot` |
| Where the waste is | `heap_getStringAnalysis` (duplicate and oversized strings), `heap_getCollectionAnalysis` (empty, singleton and oversized collections with fill ratios) | Histogram by `COUNT`, then `heap_browseClassInstances` |
| What is in one particular class | `heap_browseClassInstances` → `heap_getInstanceDetail` → `heap_getPathToGCRoot` | — |
| Who is rooting all this | `heap_getGCRootSummary`; `heap_getThreads` when a thread is the suspect | — |

`heap_getPathToGCRoot` turns an observation into a cause: the histogram says a class is large, the
path says *why those instances are still reachable*. Do not report a leak without one. The paths
skip weak and soft references, so an object reachable only through a `WeakHashMap` or a soft cache
shows no path — that is the answer, not an error.

## 6. Grounding claims

- Cite the **class name, the retained bytes and the GC-root path** together; the three are what
  makes a claim checkable.
- Object ids are stable within one dump and meaningless across dumps. Carry the class name and
  the path between dumps, never an id.
- A dump shows a state, not a trend. One dump cannot distinguish a leak from a large working set;
  say which of the two you are claiming, and ask for a second dump taken later when it matters.
- If the repository is open alongside, read the real source of the retaining field before naming
  a cause. Do not infer a code path from a class name.

## When something fails

- `Profile … has no heap dump` → it is a JFR recording; the `analyze-profile` skill applies.
- `… is still being indexed` → open the profile once in the Jeffrey UI to build the index, then retry.
- Retained sizes come back empty → the dominator tree has not been built; go back to step 4.
- `… has not been run yet` → a pre-computed report; step 4 says what to do.
- No `recordings_` tool advertised → this Jeffrey runs with `jeffrey.microscope.mcp.ingest.enabled=false`;
  upload the dump in the UI and work from `profiles_list`.
- Every call fails to connect → Jeffrey is not running at the configured address. Point the plugin
  at the real `…/api/internal/mcp` endpoint: `/plugin` → `microscope` → **Jeffrey MCP endpoint**.

Jeffrey's OQL engine is not exposed over MCP. For a question none of the tools above cover, drop
to DuckDB SQL over the heap index — the `heap-sql` skill has the schema.
