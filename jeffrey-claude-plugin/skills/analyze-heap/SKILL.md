---
name: analyze-heap
description: Work a heap dump with a running Jeffrey Microscope — what is holding the memory, what is leaking, which class loader never went away, where the waste is — including a .hprof file that is not in Jeffrey yet. Use whenever the question is "what is holding memory", "why is the heap growing", "why did this OOM", "what is leaking", or when retained size, a dominator tree, GC roots or a heap dump file are mentioned.
allowed-tools: mcp__plugin_microscope_jeffrey__heap_* mcp__plugin_microscope_jeffrey__profiles_* mcp__plugin_microscope_jeffrey__recordings_* mcp__jeffrey__heap_* mcp__jeffrey__profiles_* mcp__jeffrey__recordings_*
---

# Analysing a heap dump

A parsed heap dump is a profile like any other, but it answers a different question from a JFR
recording. A recording says where the time went; a dump says what was alive at one instant and
what was keeping it alive. Every tool here reads; none of them changes the dump.

## 1. Resolve the profile

**If the user named a file** — `heap.hprof`, `dump.hprof.gz`, anything with a heap-dump extension —
call **`recordings_analyzeFile`** with its **absolute** path. It imports the file, parses it and
returns the `profileId` everything below needs. Two constraints:

- The path is opened by the **Jeffrey process**, so the file must be on the machine Jeffrey runs
  on. A container or a remote Jeffrey cannot see your working directory.
- Each call imports the file **again** and builds another profile. Check `recordings_list` or
  `profiles_list` first if the dump may already be in Jeffrey.

Parsing a large dump takes a while and the call returns only when it is done — that is the work,
not a hang.

**Otherwise start from the catalogue:** `profiles_list`, where the **`event source`** column reads
`HEAP_DUMP` for the profiles this skill applies to. Then `profiles_features`, which lists
`HEAP_DUMP` under `disabledFeatures` when the profile has no dump or its index is not ready.

## 2. Orient before analysing

- **`heap_getDumpMetadata`** — HPROF version, id size, compressed-oops flag, record count and
  `warning_count`. One call, and it explains a lot later.
- **`heap_getHeapSummary`** — total live bytes and instances, class count, GC-root count.

A non-zero `warning_count` means the parser skipped, truncated or recovered records, so the totals
are a lower bound. When the numbers look wrong, read the `parse_warning` table (the `heap-sql`
skill) before concluding anything from them.

## 3. Shallow is not retained

The distinction decides every answer here, so settle it before reading a number:

- **Shallow size** is the object itself — its header and fields, nothing it points at.
- **Retained size** is everything that would be freed if the object were collected: the object plus
  what only it keeps alive.

"What is holding this memory" is always a retained question. A class histogram ranked by shallow
size answers a different one — it tells you what there is a lot of, not who is responsible for it.

## 4. Build the dominator tree before any retained question

Retained sizes and the dominator tree are computed **lazily**. Until something builds them, the
`dominator` and `retained_size` tables are empty and every retained figure is missing rather than
zero.

**`heap_getDominatorTreeRoots`** is what builds them. Call it once, early, before anything that
ranks by retained size. This is exactly what the tools mean when they say *"this analysis may need
to be run first"*, and skipping it is the usual reason a heap session stalls on empty results.

## 5. Pick the route

| Question | Sequence |
|---|---|
| What is using the heap at all | `heap_getClassHistogram`, then `heap_getTopConsumers` for the same picture grouped by package and class loader |
| What is leaking | `heap_getLeakSuspects` → `heap_getPathToGCRoot` on the object it names → `heap_getReferrers` to walk outwards |
| Which single objects are the biggest | `heap_getBiggestObjects` for the flat ranking, `heap_getDominatorTreeRoots` → `heap_getDominatorTreeChildren` to walk down into one |
| Redeploys leak, or classes look duplicated | `heap_getClassLoaderLeakChains` — the canonical Tomcat-redeploy diagnostic; it names the loader, the GC-root path keeping it alive, and the pattern that matched (ThreadLocal, JDBC driver, JNI global, ServiceLoader, static logger, context class loader) |
| Where the waste is | `heap_getStringAnalysis` (duplicates, oversized strings), `heap_getCollectionAnalysis` (empty, singleton and oversized collections with their fill ratios) |
| What is in one particular class | `heap_browseClassInstances` → `heap_getInstanceDetail` → `heap_getPathToGCRoot` |
| Who is rooting all this | `heap_getGCRootSummary`, and `heap_getThreads` when a thread is the suspect |

Tool names are camelCase after the prefix: `heap_getLeakSuspects`, not `heap_get_leak_suspects`.

`heap_getPathToGCRoot` is the tool that turns an observation into a cause: the histogram says a
class is large, the GC-root path says *why those instances are still reachable*. Do not report a
leak without one.

## 6. Grounding claims

- Cite the **class name, the retained bytes and the GC-root path** — the three together are what
  makes a claim checkable.
- Object ids are stable within one dump and **meaningless across dumps**. Never carry an id from
  one dump into a question about another; carry the class name and the path instead.
- A dump shows a state, not a trend. Two dumps taken apart are what shows growth; one dump alone
  cannot distinguish a leak from a large working set, so say which one you are claiming.
- If the repository is open alongside, read the real source of the retaining field before naming a
  cause. Do not infer a code path from a class name.

## 7. When something is missing

- `Profile … has no heap dump` → it is a JFR recording. Use the `jfr_`, `flamegraph_` and `traces_`
  families; the `analyze-profile` skill covers them.
- `The heap dump of profile … is still being indexed` → open it once in the Jeffrey UI to build the
  index, then try again.
- Retained sizes come back empty → the dominator tree has not been built. Go back to step 4.
- A report says its analysis has not been run → run it once and re-read; the results are cached
  afterwards.
- No `recordings_` tool is advertised → this Jeffrey was started with
  `jeffrey.microscope.mcp.ingest.enabled=false`. Upload the dump in the UI and work from
  `profiles_list`.
- Every call fails to connect → Jeffrey is not running at that address. Point the plugin at the
  real `…/api/internal/mcp` endpoint with `/plugin`; **Settings → Claude Code (MCP)** shows the URL
  for your installation.

Jeffrey's OQL engine is not exposed over MCP; OQL has to be run in the Jeffrey UI. For a question
none of the reports above cover, drop to DuckDB SQL over the heap index — see the `heap-sql` skill.
