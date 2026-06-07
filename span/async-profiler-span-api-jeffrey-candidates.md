# Span API — instrumentation candidates in Jeffrey Microscope

> Companion to [`async-profiler-span-api.md`](./async-profiler-span-api.md). Where would async-profiler
> **Spans** (latency intervals correlated with profiling samples) be most valuable if Jeffrey
> Microscope ever instruments *itself*? Compiled 2026-06-05 from a deep parallel exploration of
> `jeffrey-microscope/`. All `file:line` references spot-checked against the working tree.

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

---

## 1. Highest-leverage "instrument once" chokepoints

These are the few spots where a *single* span insertion covers a whole class of operations. Prefer
these over scattering `Span.start()/end()` across dozens of methods.

| Chokepoint | Location | Covers | Notes |
|------------|----------|--------|-------|
| **REST profile dispatch** | `core-microscope/.../web/ProfileManagerResolver.java:56` (`resolve(profileId)`) | Every `/api/internal/profiles/{profileId}/…` endpoint (~20 controllers all start with `resolver.resolve(...)`) | Even better: a Spring `HandlerInterceptor` on `/api/internal/**` using the matched URI template as the tag — one span per HTTP request, zero controller edits. None exists today. |
| **DB query execution** | `shared/persistence/.../DatabaseClient.java:337` (`queryStream`) and `:259` (`query`) | All streaming flamegraph/timeseries/subsecond queries + all named reads | `StatementLabel`/`groupLabel` already in scope → high-quality tag. **Already emits a JFR event** — Span is additive (adds `Contextual` correlation). |
| **gRPC binary streaming** | `core-microscope/.../client/RemoteRecordingStreamClient.java` — private helpers `streamChunksToConsumer` (~:145) / `collectChunksToResource` (~:120) | Every remote recording/artifact download (the highest-latency IO in the app) | All three public stream methods funnel through these two helpers. |

---

## 2. Top per-operation candidates (ranked)

Grouped by subsystem. **Granularity** = one span per (request / whole-file phase / sub-phase).
Tags are suggestions.

### 2a. AI / LLM — external blocking calls (rank: **HIGH**, no existing instrumentation)

| Location | Method | Tag | Why |
|----------|--------|-----|-----|
| `profiles/oql-assistant/.../service/OqlAssistantServiceImpl.java:80` | `chat()` → `chatClient.prompt()…​.call().content()` | `ai.oql.call` | Synchronous HTTPS round-trip to Claude/OpenAI; sub-second to seconds. |
| `profiles/duckdb-jfr-mcp/.../service/JfrAnalysisAssistantServiceImpl.java:172` | `executeWithTools()` → `…tools(tools).call().chatResponse()` | `ai.jfr-analysis.call` | **Agentic** — may do several model round-trips + MCP tool calls per user request. A span per `analyze()` captures the whole multi-round latency. |
| `profiles/duckdb-heapdump-mcp/.../service/HeapDumpAnalysisAssistantServiceImpl.java:117` | `executeWithTools()` → same shape | `ai.heapdump-analysis.call` | Same agentic pattern over heap-dump tools. |

> Note: actual module dirs are `duckdb-jfr-mcp` / `duckdb-heapdump-mcp` (CLAUDE.md still lists the
> older names `duckdb-ai-mcp` / `heap-dump-ai-mcp`).

### 2b. JFR ingestion pipeline (rank: **HIGH** — the heaviest write path)

| Location | Method | Granularity | Tag | Why |
|----------|--------|-------------|-----|-----|
| `profiles/profile-management/.../ProfileInitializerImpl.java:71` | `initialize(profileInfo, recordingId, recordingPath)` | whole-profile | `profile.initialize` | Top-level orchestrator for the entire ingest + post-process. **Already self-times** (`startedAt` at `:73`, `elapsed_ms` log at `:117`) — drop a span around the same window. Best single ingestion span. |
| `profiles/profile-management/.../parser/JfrRecordingEventParser.java:49` | `start(eventWriter, path)` | sub-phase | `jfr.parse_and_ingest` | Pure "parse JFR → write events to DuckDB". This is where async-profiler samples of Jeffrey itself will cluster — the span's flamegraph shows whether parse / dedup / DuckDB batching dominates. |
| `jdk-jfr-parser/.../JdkRecordingIterators.java` (`parallelAndWait`, called from `JfrRecordingEventParser.java:61`) | parallel chunk parse | sub-phase | `jfr.parallel_parse` | Wall time of the `CompletableFuture.allOf` over all chunk parsers. Optional per-chunk child spans at `ParallelRecordingFileIterator` if you want per-chunk latency. |
| `core-microscope/.../web/controllers/RecordingsController.java:173` | `analyzeRecording(recordingId)` | per-request | `recordings.analyze` | User-visible trigger that calls `profileInitializer.initialize()`. |
| `core-microscope/.../web/controllers/RecordingsController.java:100` | `uploadRecording(file, groupId)` | per-request | `recordings.upload` | Streams a JFR file from HTTP body to disk. |

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
| `core-microscope/.../client/RemoteRecordingStreamClient.java:53` | `downloadRecordings(sessionId, ids)` | `grpc.download-recordings` | High |
| `core-microscope/.../manager/workspace/RemoteRecordingsDownloadManager.java:174` | `mergeAndDownloadRecordingsWithProgress(...)` | `download.stream-and-persist` | High |
| `core-microscope/.../client/RemoteRepositoryClient.java:44/64` | `recordingSessions` / `repositoryStatistics` | `grpc.list-sessions` / `grpc.repo-stats` | Medium |
| `core-microscope/.../client/RemoteDiscoveryClient.java:68/120` | `allWorkspaces` / `allProjects` | `grpc.list-workspaces` / `grpc.list-projects` | Medium |
| `core-microscope/.../client/RemoteInstancesClient.java:46/72` | `projectInstances` / `instanceDetail` | `grpc.list-instances` / `grpc.instance-detail` | Medium |

---

## 3. Anti-candidates — do NOT span (hot loops → span noise)

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

## 4. Recommended minimal starter set

If/when this is actionable (after PR #1755 merges and ships — see the companion doc §8), a high-value
first pass with ~6 span points:

1. **`ai.*.call`** — the 3 LLM call sites (§2a). Biggest gap, highest latency, zero existing data.
2. **`profile.initialize`** — `ProfileInitializerImpl.initialize` (§2b). One span = the whole
   ingestion story; already self-timed so it's a one-line addition.
3. **REST `HandlerInterceptor`** on `/api/internal/**` (§1) — every request tagged by route, in one
   place. Gives the "what was Jeffrey doing while serving request X" view for free.
4. **`hprof.index.build`** — `HprofIndex.build` (§2c). The heaviest single operation.

That covers external IO (AI), the heaviest write path (ingestion), the entire read surface (REST
interceptor), and the heaviest heap operation — with four edits, no hot-loop noise.

---

## 5. Caveats

- **Not actionable yet.** The Span API is unreleased (async-profiler PR #1755, gated on a post-v4.4
  release) — see the companion doc. Treat this as a design map, not a work order.
- **Line numbers drift.** Spot-checked 2026-06-05; re-grep before editing.
- **DB spans are additive, not net-new** — the JFR `Jdbc*Event` instrumentation already exists.
- **This is Jeffrey instrumenting itself.** Distinct from Jeffrey *ingesting/visualizing* others'
  `profiler.Span` events (the integration sketched in the companion doc §9), which is the more likely
  product direction.
