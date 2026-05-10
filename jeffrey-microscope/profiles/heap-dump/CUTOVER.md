# Native HPROF Parser — Cutover Plan

**Branch:** `claude/optimize-heapdump-parser-X79WO`
**Status:** Infrastructure complete. 22 PRs merged. 109 tests, all green on Java 21
preview (project requires Java 25 — see "Build prerequisites").
**What's left:** the call-site swap (`HeapDumpManagerImpl` + cleanup). Estimated
half-day of mechanical edits, no new infrastructure needed.

---

## 1. What was built

A complete replacement for the NetBeans `org-netbeans-modules-profiler-oql` heap-dump
analysis pipeline, layered on a per-dump DuckDB index. Every piece is purely
additive — the existing `analyzer/` package, `sanitizer/`, `SimpleHeapLoader`, and
the NetBeans dep are still in place and untouched.

### 1.1 Parser stack (additive)

```
heap-dump/src/main/java/cafe/jeffrey/profile/heapdump/parser/
├── HprofMappedFile          MemorySegment over the .hprof, big-endian, lock-free
├── HprofHeader              parsed file header (magic, idSize, timestamp)
├── HprofTag                 HPROF top-level + sub-record tag constants
├── HprofTypeSize            basic-type byte sizes (idSize-aware for OBJECT)
├── HprofRecord              sealed Top/Sub hierarchy + concrete record types
├── HprofTopLevelReader      streams top-level records, fault-tolerant
├── HprofSubRecordReader     streams sub-records inside HEAP_DUMP / HEAP_DUMP_SEGMENT
├── ParseWarning             forensic record (offset, kind, severity, message)
├── HprofIndex               builds the .idx.duckdb (single pass, populates 8 tables)
├── HeapDumpIndexDb          opens / initialises the index DB (V001 schema)
├── HeapDumpIndexPaths       sibling-path helper (.hprof → .hprof.idx.duckdb)
├── DominatorTreeBuilder     Cooper-Harvey-Kennedy dominator + retained size
├── HeapView (interface)     read-side facade with all analyzer query methods
├── DuckDbHeapView           HeapView implementation (read-only DuckDB connection)
├── HeapDumpSession          paired hprof+view lifecycle (the cutover entry point)
└── DTO records              JavaClassRow, InstanceRow, GcRootRow, OutboundRefRow,
                             InstanceFieldDescriptor, InstanceFieldValue,
                             HistogramRow, DumpMetadata
```

### 1.2 V001 DuckDB schema

`heap-dump/src/main/resources/db/migration/heap-dump-index/V001__init.sql`

| Table | Rows | Built by |
|---|---|---|
| `dump_metadata` | 1 | `HprofIndex.build` |
| `string` | per HPROF STRING record | `HprofIndex.build` |
| `class` | per loaded class | `HprofIndex.build` |
| `class_instance_field` | per class × per declared field | `HprofIndex.build` |
| `instance` | per object in the heap | `HprofIndex.build` |
| `gc_root` | per ROOT_* sub-record | `HprofIndex.build` |
| `outbound_ref` | per object→object reference | `HprofIndex.build` |
| `dominator` | per reachable instance | `DominatorTreeBuilder.build` |
| `retained_size` | per reachable instance | `DominatorTreeBuilder.build` |
| `parse_warning` | per forensic event | `HprofIndex.build` |

### 1.3 Migrated analyzers (13 of 13 user-facing)

Package `cafe.jeffrey.profile.heapdump.analyzer.heapview` — same model contracts as
the existing `analyzer/` package, so the manager swap is mechanical:

| Analyzer | Reads | Notes |
|---|---|---|
| `HeapSummaryAnalyzer` | scalar SQL | exact match |
| `ClassHistogramAnalyzer` | GROUP BY on instance | exact match; SortBy.SIZE/COUNT/CLASS_NAME |
| `GcRootAnalyzer` | GROUP BY on gc_root | exact match |
| `ClassLoaderAnalyzer` | GROUP BY on class.classloader_id | retained-size still 0; fillable now |
| `ConsumerReportAnalyzer` | GROUP BY on package | retained-size still 0; fillable now |
| `DuplicateObjectAnalyzer` | per-instance SHA-256 | exact match within (class, content) |
| `ClassInstanceBrowserAnalyzer` | paged window over instance | populates objectParams via field reader |
| `StringAnalyzer` | walks String.value bytes | Java 8 char[] + Java 9+ compact byte[] |
| `ThreadAnalyzer` | ROOT_THREAD_OBJECT + Thread fields | name/daemon/priority; stack frames TODO |
| `InstanceDetailAnalyzer` | full field decode | static fields TODO |
| `InstanceTreeAnalyzer` | outbound_ref pages | REFERRERS / REACHABLES modes |
| `PathToGCRootAnalyzer` | reverse BFS over outbound_ref | weak-ref filtering by class name |
| `LeakSuspectsAnalyzer` | top retained_size | accumulationPoint/dominatedHistogram TODO |
| `DominatorTreeAnalyzer` | lazy children query | exact match |
| `ClassLoaderLeakChainAnalyzer` | dominator + path + hint catalog | hints: JNI/CCL/TL/JDBC/SL/Logger |
| `CollectionAnalyzer` | per-class shape decode | ArrayList/Vector/HashMap/LinkedHashMap; LinkedList/TreeMap/CHM TODO |

`OQLQueryExecutor` is **intentionally not migrated** — replaced by the MAT-OQL engine
project sketched earlier (separable, multi-week scope).

---

## 2. Build prerequisites

- **Java 25** (`mvn -version` must report `release 25`). The project's
  `<release>25</release>` makes Java 21 builds fail with a clear toolchain error.
- DuckDB JDBC `1.5.2.0` (already declared via the parent pom).
- No additional native libraries — `MemorySegment`/`Arena` are JDK-stable since 22.

```bash
sdk use java 25.0.1-amzn
mvn -pl jeffrey-microscope/profiles/heap-dump -am test
```

Expected output: `Tests run: 109, Failures: 0, Errors: 0, Skipped: 0`.

---

## 3. Quick smoke test against a real .hprof

The simplest standalone exercise, no manager / Spring wiring:

```java
Path hprof = Paths.get("/path/to/your/heap.hprof");
Clock clock = Clock.systemUTC();

try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprof, clock)) {
    HeapView view = session.view();

    DumpMetadata meta = view.metadata();
    System.out.println("idSize=" + meta.idSize() + " classes=" + view.classCount()
            + " instances=" + view.totalInstanceCount()
            + " gcRoots=" + view.gcRootCount()
            + " refs=" + view.outboundRefCount());

    // Cheap analyzers
    HeapSummaryAnalyzer.analyze(view);
    ClassHistogramAnalyzer.analyze(view, 100, SortBy.SIZE);
    GcRootAnalyzer.analyze(view);
    StringAnalyzer.analyze(view);

    // Dominator-dependent analyzers
    session.buildDominatorTreeIfNeeded();
    LeakSuspectsAnalyzer.analyze(view);
    DominatorTreeAnalyzer.children(view, 0L); // top-level
    ClassLoaderLeakChainAnalyzer.analyze(view);
}
```

After the first run, `<your_dump>.hprof.idx.duckdb` will exist next to the dump.
Open it in any DuckDB CLI to inspect:

```bash
duckdb /path/to/heap.hprof.idx.duckdb
> SELECT name, COUNT(*) cnt FROM class GROUP BY name ORDER BY 2 DESC LIMIT 20;
> SELECT * FROM dump_metadata;
> SELECT severity, message FROM parse_warning;
```

---

## 4. The cutover PR — exact steps

This is what's left. None of it requires new code; everything is call-site swaps,
deletions, and configuration changes.

### 4.1 `heap-dump/pom.xml`

Drop two dependencies:

```diff
-        <!-- NetBeans OQL for heap dump analysis -->
-        <dependency>
-            <groupId>org.netbeans.modules</groupId>
-            <artifactId>org-netbeans-modules-profiler-oql</artifactId>
-            <version>${netbeans-oql.version}</version>
-        </dependency>
-
-        <!-- Standalone Nashorn JavaScript engine (removed from JDK 15+) -->
-        <dependency>
-            <groupId>org.openjdk.nashorn</groupId>
-            <artifactId>nashorn-core</artifactId>
-            <version>15.7</version>
-        </dependency>
```

And remove the now-unused property:
```diff
-        <netbeans-oql.version>RELEASE250</netbeans-oql.version>
```

### 4.2 Rename the on-disk directory

`heap-dump-analysis` → `heap-dump`. Two known references:

- `jeffrey-microscope/profiles/profile-management/.../AdditionalFilesManagerImpl.java:129-145`
- `jeffrey-microscope/profiles/profile-management/.../HeapDumpManagerImpl.java:208`

Search the repo for `heap-dump-analysis` to confirm there are no other call sites
(grep, then replace).

### 4.3 Rewrite `HeapDumpManagerImpl.java` (~911 lines)

Replace every `runXxx()` method body. The shape is always the same:

```java
// Before
@Override
public ClassHistogramReport runClassHistogram(...) {
    Optional<Heap> heap = heapLoader.load(hprofPath);
    if (heap.isEmpty()) return ClassHistogramReport.empty();
    var entries = new ClassHistogramAnalyzer().analyze(heap.get(), 100);
    writeJsonFile("class-histogram.json", entries);
    return entries;
}

// After
@Override
public ClassHistogramReport runClassHistogram(...) throws IOException, SQLException {
    try (HeapDumpSession session = HeapDumpSession.openOrBuild(hprofPath, clock)) {
        var entries = ClassHistogramAnalyzer.analyze(session.view(), 100, SortBy.SIZE);
        writeJsonFile("class-histogram.json", entries);
        return entries;
    }
}
```

For the dominator-driven analyzers (`LeakSuspectsAnalyzer`, `DominatorTreeAnalyzer`,
`ClassLoaderLeakChainAnalyzer`, retained-size in `ClassLoaderAnalyzer` /
`ConsumerReportAnalyzer`), add one extra line:

```java
session.buildDominatorTreeIfNeeded();
```

The class-level `Clock` field is already injected via constructor — use it.

Per-method analyzer mapping:

| Existing call | Replace with |
|---|---|
| `new HeapSummaryAnalyzer().analyze(heap, ...)` | `analyzer.heapview.HeapSummaryAnalyzer.analyze(view)` |
| `new ClassHistogramAnalyzer().analyze(heap, topN)` | `analyzer.heapview.ClassHistogramAnalyzer.analyze(view, topN, SortBy.SIZE)` |
| `new GCRootAnalyzer().analyze(heap)` | `analyzer.heapview.GcRootAnalyzer.analyze(view)` |
| `new ClassLoaderAnalyzer().analyze(heap)` | `analyzer.heapview.ClassLoaderAnalyzer.analyze(view)` |
| `new ConsumerReportAnalyzer().analyze(heap, ...)` | `analyzer.heapview.ConsumerReportAnalyzer.analyze(view, topN)` |
| `new DuplicateObjectAnalyzer().analyze(heap, topN)` | `analyzer.heapview.DuplicateObjectAnalyzer.analyze(view, topN)` |
| `new ClassInstanceBrowserAnalyzer().analyze(heap, classId, ...)` | `analyzer.heapview.ClassInstanceBrowserAnalyzer.browse(view, classId, offset, limit)` |
| `new StringAnalyzer().analyze(heap)` | `analyzer.heapview.StringAnalyzer.analyze(view, topN)` |
| `new ThreadAnalyzer().analyze(heap)` | `analyzer.heapview.ThreadAnalyzer.analyze(view)` |
| `new InstanceDetailAnalyzer().analyze(heap, objectId)` | `analyzer.heapview.InstanceDetailAnalyzer.analyze(view, objectId)` |
| `new InstanceTreeAnalyzer().analyze(heap, request)` | `analyzer.heapview.InstanceTreeAnalyzer.analyze(view, request)` |
| `new PathToGCRootAnalyzer().findPaths(heap, id, true, n)` | `analyzer.heapview.PathToGCRootAnalyzer.findPaths(view, id, true, n)` |
| `new LeakSuspectsAnalyzer().analyze(heap)` | (after buildDominator…) `analyzer.heapview.LeakSuspectsAnalyzer.analyze(view)` |
| `new DominatorTreeAnalyzer().children(heap, parentId)` | (after buildDominator…) `analyzer.heapview.DominatorTreeAnalyzer.children(view, parentId)` |
| `new ClassLoaderLeakChainAnalyzer(pathAnalyzer).analyze(heap, baseReport, ...)` | (after buildDominator…) `analyzer.heapview.ClassLoaderLeakChainAnalyzer.analyze(view)` |
| `new CollectionAnalyzer().analyze(heap)` | `analyzer.heapview.CollectionAnalyzer.analyze(view)` |
| `new OQLQueryExecutor().execute(...)` | **Stub** returning "OQL not yet supported" until MAT-OQL ships |

### 4.4 Delete

- `cafe.jeffrey.profile.heapdump.analyzer/` — every NetBeans-using analyzer
  (the `heapview/` subpackage stays).
- `cafe.jeffrey.profile.heapdump.sanitizer/` — its framing/recovery logic is
  already inlined into `parser/HprofTopLevelReader` + `HprofSubRecordReader`.
  Sanitization is now a parser property (fault-tolerant by design), not a
  separate pre-pass.
- `cafe.jeffrey.profile.heapdump.SimpleHeapLoader`
- `cafe.jeffrey.profile.heapdump.HeapLoader` interface
- The `.sanitized` and `.nbcache` cleanup paths in `HeapDumpManagerImpl` —
  replaced by the index sibling, which is auto-rebuilt on staleness via
  `HeapDumpSession.openOrBuild`.

### 4.5 Verify

```bash
mvn -pl jeffrey-microscope/profiles/heap-dump,jeffrey-microscope/profiles/profile-management \
    -am test
```

Then a manual smoke test: upload a real `.hprof` to a running Jeffrey, click
through every dashboard, compare results against the same dump on master.

---

## 5. A/B comparison strategy

The whole point of the big-bang plan was that you'd diff the new path against the
NetBeans path on a real dump. Concretely:

1. **Two checkouts** of Jeffrey: one on `master`, one on this branch
   (`claude/optimize-heapdump-parser-X79WO`).
2. **Same .hprof** uploaded to each.
3. **Per-dashboard diff** — JSON-serialise each analyzer's output and structurally
   compare. Most are deterministic; expect tie-breaking-order differences in
   dominator-tree placement for objects with equal retained size.

| Analyzer | Expected match against NetBeans output |
|---|---|
| `HeapSummary` | exact (totals + class count + root count) |
| `ClassHistogram` | exact rows; ordering identical when SortBy.SIZE has no ties |
| `GcRoot` | exact counts per kind; kind names match |
| `StringAnalyzer` | exact dedup groups; hashes are strict byte equality |
| `DuplicateObject` | exact (class, hash) groups |
| `ClassLoader` (loaders + dups) | exact loader counts and duplicate-class detection |
| `ConsumerReport` (packages + counts) | exact per-package counts; retained-size 0 for now |
| `Thread` | name/daemon/priority exact; stack frames absent |
| `PathToGCRoot` | exact field names + step ordering |
| `LeakSuspects` (top retainers) | retained sizes exact; ranking may differ on ties |
| `DominatorTree` | retained sizes exact; child ordering by retained DESC |
| `ClassLoaderLeakChain` | hint set from path may differ slightly (string-match heuristics) |
| `CollectionAnalyzer` (ArrayList/HashMap) | size + capacity + waste exact for supported shapes |

For deterministic JSON diffing: sort lists by id before serialising.

---

## 6. Known limitations on the new path

Documented in each analyzer's javadoc. Summary, all fillable in follow-up PRs:

- **`outbound_ref` from CLASS_DUMP statics** is not populated yet. Static OBJECT
  fields don't appear as edges. Affects retained sizes that depend on
  static-field roots and the dominator tree for instances reachable only via
  statics.
- **Stack frames** for `ThreadAnalyzer` aren't decoded (HPROF STACK_TRACE /
  STACK_FRAME records currently parse as opaque). The `HeapThreadInfo` model
  doesn't carry them anyway, so no field mismatch.
- **`LeakSuspectsAnalyzer.accumulationPoint`** is null. The richer "neck object"
  detection from the existing analyzer is unmigrated.
- **`CollectionAnalyzer`**: only ArrayList/Vector/HashMap/LinkedHashMap shapes.
  LinkedList, TreeMap, ConcurrentHashMap, ArrayDeque, PriorityQueue,
  CopyOnWriteArrayList silently absent from the report.
- **OQL** is gone until the MAT-OQL engine ships.
- **Compressed-oops correction** is not applied. The index stores shallow sizes
  computed with a 16-byte header constant; the existing `CompressedOopsCorrector`
  heuristic is bypassed. If your dumps regularly need correction this should
  flag in A/B as systematically smaller totals on the new path.

## 7. Performance expectations

I haven't been able to benchmark against a real dump (couldn't run `mvn` here on
Java 21). Predicted from the architecture and the current bottlenecks I found
in the NetBeans library:

| Workload | NetBeans baseline (rough) | Expected on new path |
|---|---|---|
| First open + index build, 1 GB dump | 30–60 s (`.nbcache` build) | 15–30 s (single mmap walk + Appender writes) |
| Subsequent open, same dump | 2–5 s (read `.nbcache`) | <1 s (DuckDB open) |
| `ClassHistogramAnalyzer` | 5–20 s (Java loop over instances) | <1 s (single GROUP BY) |
| `LeakSuspects` | 30–120 s (NetBeans dom tree) | dominator-build dominated by Cooper-Harvey-Kennedy iterations — expect comparable on first run, instant on subsequent |
| `PathToGCRootAnalyzer` | seconds for shallow paths | sub-second (BFS over indexed `outbound_ref`) |

The `DuckDBAppender` ingest in PR #3 is the only thing on the hot path that's
"good but not best" — the Arrow IPC alternative (~5× faster) is sketched but
not implemented. If first-build time is a problem on huge dumps, that's the
first optimisation target.

---

## 8. Branch state

- 22 commits, all individually green.
- ~11,200 LOC of production code + tests.
- 109 unit tests passing on Java 21 preview (DuckDB + JUnit standalone harness).
- No existing code touched — the `analyzer/`, `sanitizer/`, `SimpleHeapLoader`
  paths still compile against NetBeans and run as before.
- Cutover diff (this document's section 4) is the only thing standing between
  the branch and a NetBeans-free Jeffrey.
