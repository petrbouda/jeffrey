# Span API — instrumentation candidates in Jeffrey Microscope

> Companion to [`async-profiler-span-api.md`](./async-profiler-span-api.md). Where would async-profiler
> **Spans** (latency intervals correlated with profiling samples) be most valuable if Jeffrey
> Microscope ever instruments *itself*? Compiled 2026-06-05 from a deep parallel exploration of
> `jeffrey-microscope/`; **re-validated and extended 2026-06-08** (all `file:line` references
> re-grepped; §2f–§2i and the §3 hierarchy section added).

---

## 0. The most important finding first

**DB queries are already JFR-instrumented; AI/LLM calls are not.**

`shared/persistence/.../client/DatabaseClient.java` already wraps **every** SQL call in a custom
`jdk.jfr.Event` (`JdbcQueryEvent`, `JdbcStreamEvent`, `JdbcInsertEvent`, …) via
`event.begin()` / `event.commit()` (verified at `DatabaseClient.java:54,68,77,92,…`). So adding Spans
around DB code is **additive** (Spans give you the `Contextual` tag + sample correlation the plain
events lack) but it is not filling a hole.

By contrast, the **external LLM calls have zero latency instrumentation** — no JFR event, no timing.
They are the single biggest observability gap and the textbook Span use case (blocking network IO,
seconds of latency). **If you add Spans in exactly one place, make it the AI calls.**

Two adjacent gaps share that property and nest cleanly under it: the **MCP tool calls** the agentic
AI services make (each a discrete DuckDB/heap query, looped per model round-trip — §2f) and the
**post-ingest materialization** that eagerly precomputes caches (§2h). Neither is timed today, and
both form natural parent/child chains (§3) — exactly the structure the companion doc's containment
reconstruction (§10) and HTTP-rooted explorer (§12) are built to visualize.

---

## 1. Highest-leverage "instrument once" chokepoints

These are the few spots where a *single* span insertion covers a whole class of operations. Prefer
these over scattering `Span.start()/end()` across dozens of methods.

| Chokepoint | Location | Covers | Notes |
|------------|----------|--------|-------|
| **REST profile dispatch** | `core-microscope/.../web/ProfileManagerResolver.java:56` (`resolve(profileId)`) | Every `/api/internal/profiles/{profileId}/…` endpoint (~20 controllers all start with `resolver.resolve(...)`) | Even better: a Spring `HandlerInterceptor` on `/api/internal/**` using the matched URI template as the tag — one span per HTTP request, zero controller edits. **None exists today** — `MicroscopeApplication` already implements `WebMvcConfigurer` but only overrides `addResourceHandlers`/`addViewControllers`, so `addInterceptors(...)` is the single drop-in point. |
| **DB query execution** | `shared/persistence/.../DatabaseClient.java:337` (`queryStream`) and `:259` (`query`) | All streaming flamegraph/timeseries/subsecond queries + all named reads | `StatementLabel`/`groupLabel` already in scope → high-quality tag. **Already emits a JFR event** — Span is additive (adds `Contextual` correlation). |
| **gRPC binary streaming** | `core-microscope/.../client/RecordingStreamClient.java` — private helpers `streamChunksToConsumer` (~:145) / `collectChunksToResource` (~:120) | Every remote recording/artifact download (the highest-latency IO in the app) | All three public stream methods funnel through these two helpers. |

---

## 2. Top per-operation candidates (ranked)

Grouped by subsystem. **Granularity** = one span per (request / whole-file phase / sub-phase).
Tags are suggestions.

### 2a. AI / LLM — external blocking calls (rank: **HIGH**, no existing instrumentation)

| Location | Method | Tag | Why |
|----------|--------|-----|-----|
| `profiles/oql-assistant/.../service/OqlAssistantServiceImpl.java:65` | `chat()` → `chatClient.prompt()…​.call().content()` (call at :78-81) | `ai.oql.call` | Synchronous HTTPS round-trip to Claude/OpenAI; sub-second to seconds. |
| `profiles/duckdb-jfr-mcp/.../service/JfrAnalysisAssistantServiceImpl.java:165` | `executeWithTools()` → `…tools(tools).call().chatResponse()` (call at :171-173) | `ai.jfr-analysis.call` | **Agentic** — may do several model round-trips + MCP tool calls per user request. A span per `analyze()` captures the whole multi-round latency; the individual tool calls become child spans (§2f). |
| `profiles/duckdb-heapdump-mcp/.../service/HeapDumpAnalysisAssistantServiceImpl.java:113` | `executeWithTools()` → same shape (call at :116-118) | `ai.heapdump-analysis.call` | Same agentic pattern over heap-dump tools (children in §2f). |

> Note: actual module dirs are `duckdb-jfr-mcp` / `duckdb-heapdump-mcp` (CLAUDE.md still lists the
> older names `duckdb-ai-mcp` / `heap-dump-ai-mcp`). Line numbers above re-validated 2026-06-08 —
> the AI call sites had each drifted up by ~5-7 lines since the original spot-check.

### 2b. JFR ingestion pipeline (rank: **HIGH** — the heaviest write path)

| Location | Method | Granularity | Tag | Why |
|----------|--------|-------------|-----|-----|
| `profiles/profile-management/.../ProfileInitializerImpl.java:71` | `initialize(profileInfo, recordingId, recordingPath)` | whole-profile | `profile.initialize` | Top-level orchestrator for the entire ingest + post-process. **Already self-times** (`startedAt` at `:73`, `elapsed_ms` log at `:117`) — drop a span around the same window. Best single ingestion span. |
| `profiles/profile-management/.../parser/JfrRecordingEventParser.java:49` | `start(eventWriter, path)` | sub-phase | `jfr.parse_and_ingest` | Pure "parse JFR → write events to DuckDB". This is where async-profiler samples of Jeffrey itself will cluster — the span's flamegraph shows whether parse / dedup / DuckDB batching dominates. |
| `jdk-jfr-parser/.../JdkRecordingIterators.java:78` (`parallelAndWait`, called from `JfrRecordingEventParser.java:61`) | parallel chunk parse | sub-phase | `jfr.parallel_parse` | Wall time of the `CompletableFuture.allOf` over all chunk parsers. Optional per-chunk child spans at `ParallelRecordingFileIterator` (instantiated `:137`) if you want per-chunk latency. |
| `core-microscope/.../web/controllers/RecordingsController.java:174` | `analyzeRecording(recordingId)` | per-request | `recordings.analyze` | User-visible trigger that calls `profileInitializer.initialize()`. |
| `core-microscope/.../web/controllers/RecordingsController.java:102` | `uploadRecording(file, groupId)` | per-request | `recordings.upload` | Streams a JFR file from HTTP body to disk. |

### 2c. Heap-dump analysis (rank: **HIGH** — can be the single heaviest op)

| Location | Method | Granularity | Tag | Why |
|----------|--------|-------------|-----|-----|
| `profiles/heap-dump/.../parser/HprofIndex.java:102` | `build(mappedFile, path, clock)` | whole-dump | `hprof.index.build` | Two-pass mmap scan of an arbitrarily large `.hprof`, millions of rows into DuckDB. Heaviest operation in the codebase. Already self-measured with `Measuring.s`. |
| `profiles/heap-dump/.../parser/DominatorTreeBuilder.java:106` | `build(path)` | whole-dump | `hprof.dominator.build` | Semi-NCA dominator + retained sizes over the full object graph (tens of millions of nodes). Explicitly opt-in. |
| `core-microscope/.../web/controllers/profile/HeapDumpController.java:178` | `uploadHeapDump(profileId, file)` | per-request | `profile.heap.upload` | Multipart upload to disk; can be gigabytes. |
| `profiles/heap-dump/.../analyzer/heapview/LeakSuspectsAnalyzer.java:74` | `analyze(heapView)` | per-request | `heapdump.leak_suspects` | Multi-join SQL over retained sizes + per-suspect clustering. |

### 2d. Analysis / visualization compute (rank: **HIGH–MEDIUM**)

| Location | Method | Granularity | Tag | Why |
|----------|--------|-------------|-----|-----|
| `profiles/flamegraph/.../api/DbBasedFlamegraphGenerator.java:55` | `generate(params)` | per-request | `flamegraph.generate` | DB query → frame-tree build → protobuf serialize, two parallel futures joined. The canonical user-triggered compute unit. |
| `profiles/flamegraph/.../diff/DbBasedDiffgraphGenerator.java:57` | `generate(params)` | per-request | `flamegraph.diff.generate` | Four parallel futures + tree merge; heavier than single-profile. |
| `profiles/profile-guardian/.../Guardian.java:79` | `process()` | whole-profile | `guardian.process` | Four guardian groups, each a full DB scan + frame-tree traversal (~4× a flamegraph). Span the whole batch; optionally per-group at `AbstractGuardianGroup.execute()`. |
| `profiles/subsecond/.../db/api/DbBasedSubSecondGeneratorImpl.java:38` | `generate(config)` | per-request | `subsecond.generate` | Full event scan into 1 ms buckets across the recording. |
| `profiles/profile-thread/.../DbBasedThreadProvider.java:122` | `get()` | per-profile | `thread.timeline.build` | Scans ~10 event types across all threads; cost scales with thread count × event density. |

> The corresponding REST controllers (`FlamegraphController.generate`, `TimeseriesController.generate`,
> `SubSecondController.generate`, `DifferentialFlamegraphController`, etc.) are equally valid
> per-request span points — but they're all covered by the **REST chokepoint** in §1, so prefer that
> unless you want compute time isolated from query/serialize time.

### 2e. Remote gRPC calls (rank: **HIGH** for streaming, **MEDIUM** for list/metadata)

| Location | Method | Tag | Rank |
|----------|--------|-----|------|
| `core-microscope/.../client/RecordingStreamClient.java:53` | `downloadRecordings(sessionId, ids)` | `grpc.download-recordings` | High |
| `core-microscope/.../manager/workspace/RemoteRecordingsDownloadManager.java:174` | `mergeAndDownloadRecordingsWithProgress(...)` | `download.stream-and-persist` | High |
| `core-microscope/.../client/RepositoryClient.java:44/64` | `recordingSessions` / `repositoryStatistics` | `grpc.list-sessions` / `grpc.repo-stats` | Medium |
| `core-microscope/.../client/DiscoveryClient.java:68/120` | `allWorkspaces` / `allProjects` | `grpc.list-workspaces` / `grpc.list-projects` | Medium |
| `core-microscope/.../client/InstancesClient.java:46/72` | `projectInstances` / `instanceDetail` | `grpc.list-instances` / `grpc.instance-detail` | Medium |

### 2f. MCP tool calls — child spans under the agentic AI call (rank: **HIGH**, the new gap)

The agentic services (§2a) let Spring AI auto-invoke `@Tool` methods in a loop; each tool is a
discrete DuckDB/heap query with its own latency, and each **nests under the parent `ai.*.call` span**
(§3 chain B/C). Spanning them turns *"the AI call took 9 s"* into *"6 tool calls, one 5 s query
dominated."* This is the single most informative new instrumentation after the AI calls themselves.

| Tool surface | Location | Representative `@Tool` methods (line) | Child tag pattern |
|--------------|----------|---------------------------------------|-------------------|
| DuckDB JFR tools (13 `@Tool`) | `profiles/duckdb-jfr-mcp/.../tools/DuckDbMcpTools.java` | `executeQuery` :125, `queryEvents` :210, `listTables` :55, `describeTable` :78, `listEventTypes` :156, `getProfileInfo` :271, `executeModification` :310, … | `ai.jfr.tool.<name>` |
| Heap-dump tools (36 `@Tool`) | `profiles/duckdb-heapdump-mcp/.../tools/HeapDumpMcpTools.java` | `executeQuery` :753, `getLeakSuspects` :162, `getDominatorTreeRoots` :530, `getPathToGCRoot` :601, `getReferrers` :650, `getBiggestObjects` :121, `getClassHistogram` :82, … | `ai.heapdump.tool.<name>` |

> Use `endIfProfiled` here: trivial metadata tools (`listTables`, `getDumpMetadata`) enclose no
> samples and self-skip; the heavy ones (`executeQuery`, dominator/path traversals) almost always
> enclose samples and are kept.

### 2g. OQL pipeline — parse / compile / execute (rank: **MEDIUM**)

Heap-dump OQL execution is a distinct multi-stage pipeline, **separate from the LLM call** that
generated the query (it lives in its own `heapdump-oql` module):

| Location | Method (line) | Tag | Why |
|----------|---------------|-----|-----|
| `profiles/heapdump-oql/.../OqlEngine.java` | `parse` :46 / `compile` :57 / `execute` :71 | `oql.parse` / `oql.compile` / `oql.execute` | ANTLR4 parse → plan compile → execution against the heap view; three children show which stage dominates. |
| `profiles/oql-assistant/.../service/OqlAssistantServiceImpl.java` | `oqlExtractor.extract(response)` :84 | `ai.oql.extract` | Pull the OQL out of the LLM's text response (post-call, before execution). |

### 2h. Profile lifecycle & persistence phases (rank: **HIGH** — children of `profile.initialize`)

The ingestion story (§2b) has internal phases worth their own child spans under `profile.initialize`
(§3 chain A), e.g. a `duckdb.batch.flush` tag for the batch-flush phase.

| Location | Method (line) | Tag | Why |
|----------|---------------|-----|-----|
| `profile-sql-persistence/.../DuckDBProfileDatabaseManager.java` | `open` :45 / `runMigrations` :60 (`flyway.migrate()` :70) | `profile.db.open` | New per-profile DuckDB file + Flyway schema migration; first-touch cost, isolated from parsing. |
| `profile-sql-persistence/.../DuckDBBatchingWriter.java` | `sendBatch` :83 (already logs `elapsed_ms` :102) | `duckdb.batch.flush` | Per-batch COPY into DuckDB. The per-row `insert` is a hot loop (§5) — span the **flush**, not the row. |
| `profile-management/.../manager/action/ProfileDataInitializerImpl.java` | `initialize` :44 (event-types tree :55, guardian results :63, thread rows :71) | `profile.materialize.*` | Eager cache precompute after parse — 3 natural children (`…eventviewer`, `…guardian`, `…threads`). |
| `shared/persistence/.../client/DatabaseClient.java` | `walCheckpoint` :402 (`FORCE CHECKPOINT`) | `duckdb.checkpoint` | Durability flush at end of init; blocking, post-parse. |

### 2i. Workspace sync & profiler settings (rank: **MEDIUM**)

Higher-level orchestration that *wraps* the raw gRPC client calls of §2e — the natural parent of
those leaves.

| Location | Method (line) | Tag | Why |
|----------|---------------|-----|-----|
| `core-microscope/.../manager/workspace/WorkspaceManager.java` | `resolveInfo` :41 / `fetchEffectiveProfilerSettings` :82 | `workspace.resolve` | Composite remote snapshot refresh; may traverse multiple connected servers. |
| `core-microscope/.../web/ProfileManagerResolver.java` | `find` :61 | `profile.resolve` | Recordings → local DB → remote-server walk; the parent of the §2e gRPC calls (and of a REST request, §3 chain D). |
| `core-microscope/.../client/ProfilerClient.java` | `upsertProfilerSettings` :57 / `upsertSettingsAtLevel` :91 | `grpc.profiler-settings.upsert` | gRPC round-trip persisting agent settings to a remote instance. |

> Out of scope here: `jeffrey-hub`'s `ReplayStreamingSubscriber.start()/readAllFiles()` (per-file
> replay reads) are also good span points, but they live in the **server** deployment, not Microscope.

---

## 3. Natural span hierarchies (parent/child)

Async-profiler spans carry **no parent id** — Jeffrey reconstructs nesting by time-containment on one
thread (companion doc §10), and the HTTP/exchange-rooted "request explorer" (companion §12) draws
itself once parent and children are both instrumented. These are the chains where that pays off.
Legend: **✓** already emits a JFR event or is self-timed · **⊕** new span.

**Chain A — JFR ingestion** (single thread, clean lexical nesting — the ideal case):

```
profile.initialize                         ⊕ §2b  (self-timed today)
├─ profile.db.open  (Flyway migrate)       ⊕ §2h
├─ jfr.parse_and_ingest                    ⊕ §2b
│   └─ duckdb.batch.flush  × N             ⊕ §2h  (logs elapsed_ms)
├─ profile.materialize.eventviewer         ⊕ §2h
├─ profile.materialize.guardian            ⊕ §2h
├─ profile.materialize.threads             ⊕ §2h
└─ duckdb.checkpoint  (FORCE CHECKPOINT)    ⊕ §2h
```

**Chain B — agentic JFR analysis** (the most valuable hierarchy — turns one opaque AI latency into a
tool-by-tool breakdown):

```
ai.jfr-analysis.call                       ⊕ §2a  (zero instrumentation today)
└─ (Spring AI tool loop, repeated)
   ├─ ai.jfr.tool.execute_query            ⊕ §2f
   │   └─ JdbcQueryEvent / db.query        ✓ §1   (existing JFR event)
   └─ ai.jfr.tool.query_events             ⊕ §2f
```

**Chain C — heap-dump AI → OQL pipeline** (three levels deep):

```
ai.heapdump-analysis.call                  ⊕ §2a
└─ ai.heapdump.tool.leak_suspects          ⊕ §2f
   └─ oql.execute                          ⊕ §2g
      ├─ oql.parse                         ⊕ §2g
      ├─ oql.compile                       ⊕ §2g
      └─ db.query                          ✓ §1
```

**Chain D — REST read request** (the §1 interceptor is the root; mirrors a `jeffrey.HttpServerExchange`
and feeds the companion §12 request explorer):

```
http  GET /api/internal/profiles/{id}/flamegraph   ⊕ §1 interceptor (root, route-tagged)
├─ profile.resolve                         ⊕ §2i
└─ flamegraph.generate                     ⊕ §2d
   └─ db.query  (queryStream + FrameMapper) ✓ §1
```

> **Containment caveat (be honest in the UI).** Reconstructed nesting only holds when parent and
> children run on the **same thread** (companion §10). Chains **A** and **D** (synchronous handlers)
> satisfy this. Chains **B**/**C** hold only if the Spring AI tool loop executes on the calling
> thread — **verify this**; if tool calls are dispatched to a separate executor, parent↔child must be
> correlated by time overlap, not strict containment, and the tree may render as siblings.

---

## 4. Worked examples (concrete request decompositions)

Realistic counterparts to the idealized chains in §3 — the spans you'd actually emit for one
endpoint, mapped onto the **real** threading. **No parent/child here:** async-profiler spans are
per-thread, so the worker-thread spans render as their own lanes, correlated to the request by time
window (companion §11), not by id. Tag convention: **full words, no abbreviation**
(`generate`, `marshalling`, …). This list will grow per use-case.

### 4a. `POST /api/internal/profiles/{profileId}/flamegraph`

Five flat, stable-tagged spans across three threads. Two facts from the code shape the layout:

- **DuckDB query and frame-IR build are interleaved, not sequential.** `flamegraphStreamer` streams
  rows from `databaseClient.queryStream(...)` straight into the `FrameBuilder`, so query and IR-build
  alternate on one thread and **fuse into a single `flamegraph.generate` span** (separating them would
  mean materializing the result set, killing the streaming).
- **The heavy protobuf marshalling is on the worker thread, not the request thread.** `provideProto`
  runs `protoBuilder.build(frame)` (frame-IR → `FlamegraphData`) on `parallel-*`; only the small
  envelope `toByteArray()` runs on the request thread.

```
http-nio-exec-N │ [== http.flamegraph  (whole request) ==================================]
                │     ├ fork                                                  join ┐
                │     │                                                            ├ [graph.marshalling]
parallel-x      │     │ [== flamegraph.generate ==][== flamegraph.marshalling ==]  │
parallel-y      │     │ [== timeseries.generate ==]                                │
```

| Span (stable tag) | Thread | Where |
|-------------------|--------|-------|
| `http.flamegraph` | request | `core-microscope/.../web/controllers/profile/FlamegraphController.java` `generate` (or the §1 interceptor) — whole request |
| `flamegraph.generate` | `parallel-*` | `flamegraph/.../provider/FlamegraphDataProvider.java` `provideProto` → around `provideFrame()` (DuckDB query + frame-IR, fused) |
| `flamegraph.marshalling` | `parallel-*` | same method → around `protoBuilder.build(frame)` (frame-IR → protobuf) |
| `timeseries.generate` | `parallel-*` | `flamegraph/.../provider/TimeseriesDataProvider.java` `provide()` |
| `graph.marshalling` | request | `flamegraph/.../api/DbBasedFlamegraphGenerator.java` `generate` → post-join `convertTimeseries` + `toByteArray()` |

`FlamegraphDataProvider.provideProto` — the two worker-thread spans:

```java
public FlamegraphData provideProto(double minFrameThresholdPct) {
    long g = Span.start();
    Frame frame;
    try {
        frame = provideFrame();                       // DuckDB query + frame-IR build (streamed together)
    } finally {
        Span.end(g, "flamegraph.generate");
    }
    long m = Span.start();
    try {
        return resolveFlamegraphProtoBuilder(graphParameters, minFrameThresholdPct).build(frame);
    } finally {
        Span.end(m, "flamegraph.marshalling");        // frame-IR → protobuf (the heavy marshalling)
    }
}
```

`DbBasedFlamegraphGenerator.generate` — the request-thread envelope span (generation already ran on
the workers; this only assembles + serializes):

```java
CompletableFuture.allOf(flameFuture, timeseriesFuture).join();   // generation done on parallel-*
long m = Span.start();
try {
    GraphData.Builder b = GraphData.newBuilder();
    FlamegraphData fd = flameFuture.join();       if (fd != null) { b.setFlamegraph(fd); }
    TimeseriesData  td = timeseriesFuture.join(); if (td != null) { b.setTimeseries(convertTimeseries(td)); }
    return b.build().toByteArray();
} finally {
    Span.end(m, "graph.marshalling");
}
```

`TimeseriesDataProvider.provide()` gets the same `timeseries.generate` wrapper.

Notes:
- The `parallel-*` spans carry **real samples**, so each gets a span-scoped flamegraph for free
  (companion §11). `http.flamegraph` is a **latency marker** — its thread is parked in `join()`, so
  its own-thread samples are ~empty; read its contents via its time window across the pool.
- **Variant:** in `CACHE` frame-resolution mode a distinct `FramesCache.load(databaseClient)` query
  precedes the stream and is separable as `flamegraph.frames.load`; in `DATABASE` mode it's one
  stream.

---

## 5. Anti-candidates — do NOT span (hot loops → span noise)

Wrapping these would emit thousands–millions of spans per operation, drowning the profiling signal.
This is exactly what `Span.endIfProfiled(...)` exists to mitigate, but the right answer is **don't
span them at all** — span the enclosing phase instead.

| Location | Why |
|----------|-----|
| `jdk-jfr-parser/.../JfrEventReader.java:89` `onEvent(RecordedEvent)` | Once per JFR event — tens of millions per recording. The hot inner loop. |
| `profile-sql-persistence/.../SQLSingleThreadedEventWriter.java:53/81` `onEvent` / `onEventStacktrace` | Per parsed event / per stacktrace. |
| `profile-sql-persistence/.../DuckDBBatchingWriter.java:66` `insert(T)` | Per event, per table. |
| `flamegraph/.../FrameBuilder.onRecord(...)` | Per sample, inner frame-walk. |
| `frame-ir/.../FrameTraversal._traverse(...)` | Recursive per-node visitor inside every guardian check. |
| `flamegraph/.../FlameGraphProtoBuilder.buildFrame(...)` | Recursive per-frame during proto serialization. |
| `heap-dump/.../DominatorTreeBuilder` inner `computeDominatorsSemiNCA` / `computeRetained` | O(N·α(N)) per-node loops over millions of nodes. |
| `heap-dump/.../HprofPassBWalker.runWorker` / `emitInstanceRefs` | Per-instance / per-field inner loops. |
| `subsecond/.../SubSecondRecordBuilder.onRecord` / `timeseries/.../*TimeseriesBuilder.onRecord` | Per-event bucket increment. |

---

## 6. Recommended starter sets

If/when this is actionable (after PR #1755 merges and ships — see the companion doc §8):

**Tier 1 — flat coverage (4 edits, no hierarchy):**

1. **`ai.*.call`** — the 3 LLM call sites (§2a). Biggest gap, highest latency, zero existing data.
2. **`profile.initialize`** — `ProfileInitializerImpl.initialize` (§2b). One span = the whole
   ingestion story; already self-timed so it's a one-line addition.
3. **REST `HandlerInterceptor`** on `/api/internal/**` (§1) — every request tagged by route, in one
   place. Gives the "what was Jeffrey doing while serving request X" view for free.
4. **`hprof.index.build`** — `HprofIndex.build` (§2c). The heaviest single operation.

That covers external IO (AI), the heaviest write path (ingestion), the entire read surface (REST
interceptor), and the heaviest heap operation — four edits, no hot-loop noise.

**Tier 2 — hierarchy showcase (adds the parent/child story the companion doc §10/§12 visualizes):**

5. **MCP tool children** under the agentic calls (§2f) — turns each `ai.*-analysis.call` into a
   tool-by-tool flame of where the seconds went (§3 chain B). Highest insight-per-edit after Tier 1.
6. **`profile.initialize` sub-phases** (§2h) — `profile.db.open`, `duckdb.batch.flush`,
   `profile.materialize.*`, `duckdb.checkpoint`. Makes §3 chain A a real waterfall instead of one
   opaque bar, landing a `duckdb.batch.flush` tag the span UI can render.

Tier 2 is where Spans stop being "another duration metric" and start showing **structure** — pick it
if the goal is to dogfood the parent/child + request-explorer features, not just collect timings.

---

## 7. Caveats

- **Not actionable yet.** The Span API is unreleased (async-profiler PR #1755, gated on a post-v4.4
  release) — see the companion doc. Treat this as a design map, not a work order.
- **Line numbers drift.** Re-validated 2026-06-08 (the AI call sites in §2a had moved up ~5-7 lines
  since the original 2026-06-05 pass); re-grep before editing regardless.
- **DB spans are additive, not net-new** — the JFR `Jdbc*Event` instrumentation already exists.
- **Hierarchy needs same-thread execution.** Parent/child reconstruction is by time-containment on
  one thread (companion §10). Verify the Spring AI tool loop (§2f, §3 chains B/C) runs on the calling
  thread before relying on strict nesting — an executor hand-off downgrades it to time-overlap
  correlation.
- **This is Jeffrey instrumenting itself.** Distinct from Jeffrey *ingesting/visualizing* others'
  `profiler.Span` events (the integration sketched in the companion doc §9), which is the more likely
  product direction.
