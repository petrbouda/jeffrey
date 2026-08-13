# Trace Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Trace Operations page aggregate traces by root name, and give each operation a three-tab drill-down (Flamegraphs, Metrics Timeline, Slowest Traces) matching the async-profiler Spans page.

**Architecture:** The operations query moves from `trace_spans GROUP BY name` to `traces GROUP BY root_name`, so a row is a trace type rather than a span name. Two new reads — the traces of one operation, and the `(thread, window)` intervals its traces cover — feed a detail view that reuses the existing `SpanScopedGraphParameters` + `flamegraphManager().generate(...)` flamegraph path unchanged. The detail is selected through a `?operation=` query parameter because operation names contain `/` and `{}` and cannot be a path segment.

**Tech Stack:** Java 25, Spring Boot 4 (Spring MVC), DuckDB 1.5, JUnit 5 + Mockito + `@DuckDBTest`, Vue 3 + TypeScript + Vite, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-13-trace-operations-design.md`

## Global Constraints

- Java 25. Every new/modified Java file carries the AGPL header with year **2026** (copy from any existing file in the module).
- **Braces always** — every `if`/`else`/`for`/`while` body wrapped in `{ }`, in Java *and* TypeScript. No inline single-line bodies.
- Annotations on classes/fields/methods go on their own line. Parameter annotations (`@PathVariable`, `@RequestParam`, `@RequestBody`) stay inline.
- No stereotype annotations and no `@Autowired`. `@RestController` is the sole exception and is already present on `TracesController`.
- No inline string/number literals in logic — anything matched, compared, or used as a config value becomes a `private static final` constant with a descriptive name.
- SLF4J logging in the `"Description: key1={} key2={}"` form, no commas between pairs.
- Frontend: design tokens only (no hex colours, no literal `box-shadow`/`border-radius`); check `@shared` for an existing component before writing markup; `<script setup lang="ts">` with typed `defineProps`/`defineEmits`.
- **Do not commit.** This repo's CLAUDE.md forbids committing unless the user explicitly asks. Each task ends with verification, not a commit. Leave changes in the working tree and report.

**Build and test commands (this machine):**

```bash
# Backend module tests (the -am is required: sibling jars in ~/.m2 are stale)
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/profiles/profile-sql-persistence -am test \
  -Dtest=JdbcTraceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false

# Frontend build (esbuild — no type-check, but catches import/export/SFC errors)
cd jeffrey-microscope/pages-microscope && npm run build

# Frontend unit tests
cd jeffrey-microscope/pages-microscope && npx vitest run <path>
```

`npm run lint` is broken in this environment (ESLint 9 vs legacy `.eslintrc.json`) — do not rely on it. `npx prettier --write <file>` works for formatting.

**Already done before this plan** (in the working tree, uncommitted):

- `components/trace/TraceOperationList.vue` — `PercentileSpread` rail, legend and scoped styles removed so rows match `SpanTagList.vue`.
- `views/profiles/navigation/profileNavConfig.ts:224` — sidebar item renamed `Operations` → `Trace Operations`.

---

### Task 1: Aggregate operations by trace type

Changes the meaning of an operation from "span name" to "trace type". On the test fixture this drops `flamegraph.generate` and `listSpans` from the results and leaves the two root names.

**Files:**
- Modify: `jeffrey-microscope/profiles/profile-sql-persistence/src/main/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepository.java` (the `OPERATIONS` constant, ~line 231, and `operations(int)`, ~line 349)
- Modify: `jeffrey-microscope/profiles/profile-persistence-api/src/main/java/cafe/jeffrey/provider/profile/api/TraceOperationRecord.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/model/trace/TraceOperationRow.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/TraceManagerImpl.java:110-122`
- Test: `jeffrey-microscope/profiles/profile-sql-persistence/src/test/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepositoryTest.java` (replace `aggregatesOperations`, ~line 229)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `TraceOperationRecord(String name, String kind, long count, long errorCount, long spanCount, long totalNanos, long p50Nanos, long p95Nanos, long maxNanos)` and the identically-shaped `TraceOperationRow`. Task 5 mirrors `spanCount` into TypeScript.

- [ ] **Step 1: Rewrite the failing test**

Replace the whole `aggregatesOperations` test in `JdbcTraceRepositoryTest` (inside the existing `@Nested` class that holds it) with:

```java
        @Test
        @DisplayName("operations aggregate traces by root name, ignoring nested spans")
        void aggregatesOperations(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            Map<String, TraceOperationRecord> byName = repository.operations(10).stream()
                    .collect(Collectors.toMap(TraceOperationRecord::name, Function.identity()));

            assertEquals(2, byName.size(), "one row per trace type, not per span name");
            assertFalse(byName.containsKey("flamegraph.generate"),
                    "a nested span is not a trace type and must not be listed");
            assertFalse(byName.containsKey("listSpans"),
                    "a nested span is not a trace type and must not be listed");

            TraceOperationRecord slowest = byName.get("POST /api/internal/profiles/{profileId}/flamegraph");
            assertEquals(1, slowest.count(), "one trace of this type");
            assertEquals(3, slowest.spanCount(), "root plus its two children");
            assertEquals(120 * MS, slowest.totalNanos(), "the whole trace, not the root span alone");
            assertEquals(120 * MS, slowest.maxNanos());
            assertEquals(1, slowest.errorCount(), "the trace contains a failed span");

            TraceOperationRecord health = byName.get("GET /api/internal/health");
            assertEquals(1, health.count());
            assertEquals(1, health.spanCount());
            assertEquals(5 * MS, health.totalNanos());
        }
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/profiles/profile-sql-persistence -am test \
  -Dtest=JdbcTraceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile error — `TraceOperationRecord` has no `spanCount()`.

- [ ] **Step 3: Add `spanCount` to the record**

In `TraceOperationRecord.java`, replace the record declaration and its Javadoc:

```java
/**
 * Latency of one trace type across the profile — the aggregate view that answers "which kind of
 * request is generally slow", as opposed to "which single trace was slow".
 * <p>
 * A trace type is identified by its root span's name. Nested spans are not types of their own: they
 * are explored through the trace's span tree, not through this list.
 *
 * @param name       the root operation name the traces share
 * @param kind       {@code SERVER}, {@code CLIENT} or {@code INTERNAL}
 * @param count      how many traces of this type the profile holds
 * @param errorCount how many of them contain at least one failed span
 * @param spanCount  spans across all of them, which separates a one-span type from a deep one
 * @param totalNanos summed trace duration, for ranking by total time rather than by average
 * @param p50Nanos   median trace duration
 * @param p95Nanos   95th percentile trace duration
 * @param maxNanos   slowest trace of the type
 */
public record TraceOperationRecord(
        String name,
        String kind,
        long count,
        long errorCount,
        long spanCount,
        long totalNanos,
        long p50Nanos,
        long p95Nanos,
        long maxNanos) {
}
```

Apply the same field list and an equivalent Javadoc to `TraceOperationRow.java` (its existing doc comment says "Latency of one operation name across every trace" — replace it with the paragraph above, minus the `@param` block if the record has none today; keep whatever comment style the file already uses).

- [ ] **Step 4: Rewrite the SQL**

In `JdbcTraceRepository.java`, replace the `OPERATIONS` constant:

```java
    /*
     * One row per trace type, keyed by the root span's name. Aggregated over `traces` rather than
     * `trace_spans` because an operation is a kind of trace: grouping spans would list names that
     * only ever appear nested, which no trace can be opened at.
     *
     * Grouped by name alone with ANY_VALUE(kind) rather than by (name, kind): a name that somehow
     * carried two kinds would otherwise split into two rows the UI cannot tell apart.
     */
    //language=SQL
    private static final String OPERATIONS = """
            SELECT
                root_name                                           AS name,
                ANY_VALUE(root_kind)                                AS kind,
                COUNT(*)                                            AS count,
                COUNT(*) FILTER (WHERE error_count > 0)             AS error_count,
                SUM(span_count)                                     AS span_count,
                SUM(duration)                                       AS total_ns,
                CAST(QUANTILE_CONT(duration, 0.5) AS BIGINT)        AS p50_ns,
                CAST(QUANTILE_CONT(duration, 0.95) AS BIGINT)       AS p95_ns,
                MAX(duration)                                       AS max_ns
            FROM traces
            GROUP BY root_name
            ORDER BY total_ns DESC
            LIMIT :limit
            """;
```

- [ ] **Step 5: Map the new column**

In `JdbcTraceRepository.operations(int)`, add `spanCount` to the row mapper in record order:

```java
                (rs, _) -> new TraceOperationRecord(
                        rs.getString("name"),
                        rs.getString("kind"),
                        rs.getLong("count"),
                        rs.getLong("error_count"),
                        rs.getLong("span_count"),
                        rs.getLong("total_ns"),
                        rs.getLong("p50_ns"),
                        rs.getLong("p95_ns"),
                        rs.getLong("max_ns")));
```

- [ ] **Step 6: Carry the field through the manager**

In `TraceManagerImpl.operations(int)`:

```java
    @Override
    public List<TraceOperationRow> operations(int limit) {
        return traceRepository.operations(limit).stream()
                .map(operation -> new TraceOperationRow(
                        operation.name(),
                        operation.kind(),
                        operation.count(),
                        operation.errorCount(),
                        operation.spanCount(),
                        operation.totalNanos(),
                        operation.p50Nanos(),
                        operation.p95Nanos(),
                        operation.maxNanos()))
                .toList();
    }
```

Also update the `operations(int)` Javadoc on `TraceRepository` and `TraceManager` — both currently say "by operation name across every trace"; they now aggregate traces by root name.

- [ ] **Step 7: Run the test to verify it passes**

Same command as Step 2. Expected: PASS. The pre-existing `summarisesTheProfile` test in the same class will now FAIL on `distinctOperations` — that is Task 2's job; leave it failing and note it.

---

### Task 2: Make the overview describe trace types and carry a total

`distinctOperations` counts span names today, which would contradict the list after Task 1. The header also needs a profile-wide duration sum, because the current UI derives its "Total" tile from the capped row list.

**Files:**
- Modify: `jeffrey-microscope/profiles/profile-sql-persistence/src/main/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepository.java` (the `OVERVIEW` constant, ~line 210-228, and `overview()`, ~line 330-345)
- Modify: `jeffrey-microscope/profiles/profile-persistence-api/src/main/java/cafe/jeffrey/provider/profile/api/TraceOverviewRecord.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/model/trace/TraceOverview.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/TraceManagerImpl.java:68-81`
- Test: `jeffrey-microscope/profiles/profile-sql-persistence/src/test/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepositoryTest.java` (`summarisesTheProfile`, ~line 245)

**Interfaces:**
- Consumes: Task 1's trace-type semantics.
- Produces: `TraceOverviewRecord(long totalTraces, long totalSpans, long errorTraces, long errorSpans, long avgNanos, long p95Nanos, long p99Nanos, long maxNanos, long totalNanos, int distinctOperations)` and the identically-shaped `TraceOverview`. Task 8 reads `totalNanos` and `distinctOperations` from the JSON.

- [ ] **Step 1: Update the failing assertions**

In `summarisesTheProfile`, replace the `distinctOperations` assertion and add a total:

```java
            assertEquals(2, overview.distinctOperations(), "distinct trace types, not span names");
            assertEquals(125 * MS, overview.totalNanos(), "120ms plus 5ms");
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/profiles/profile-sql-persistence -am test \
  -Dtest=JdbcTraceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile error — no `totalNanos()` on `TraceOverviewRecord`.

- [ ] **Step 3: Add the field to both records**

In `TraceOverviewRecord.java`, insert `long totalNanos` before `distinctOperations`, extend `EMPTY` by one zero, and fix the two Javadoc lines:

```java
 * @param maxNanos           slowest trace
 * @param totalNanos         summed trace duration across the profile
 * @param distinctOperations distinct trace types, matching what the Trace Operations view ranks
 */
public record TraceOverviewRecord(
        long totalTraces,
        long totalSpans,
        long errorTraces,
        long errorSpans,
        long avgNanos,
        long p95Nanos,
        long p99Nanos,
        long maxNanos,
        long totalNanos,
        int distinctOperations) {

    /** What an untraced profile reports: every counter zero rather than a null-riddled row. */
    public static final TraceOverviewRecord EMPTY =
            new TraceOverviewRecord(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
}
```

Apply the same insertion and Javadoc fix to `TraceOverview.java` (no `EMPTY` there).

- [ ] **Step 4: Fix the SQL**

In the `OVERVIEW` constant, replace the `distinct_operations` sub-select and add the sum. The comment above the constant explaining that operations are counted off `trace_spans` must go — it now states the opposite of the truth:

```java
                COALESCE(SUM(duration), 0)                                  AS total_ns,
                COUNT(DISTINCT root_name)                                   AS distinct_operations
            FROM traces
            """;
```

(The `distinct_operations` line replaces `(SELECT COUNT(DISTINCT name) FROM trace_spans) AS distinct_operations`; `total_ns` is a new line before it. Keep every other column as it is.)

- [ ] **Step 5: Map it**

In `JdbcTraceRepository.overview()`, add `rs.getLong("total_ns")` before `rs.getInt("distinct_operations")`. In `TraceManagerImpl.overview()`, add `overview.totalNanos()` in the same position.

- [ ] **Step 6: Run the whole trace test class**

Same command as Step 2. Expected: PASS, including Task 1's test and `overviewOfAnUntracedProfileIsZeroed`.

---

### Task 3: Read the traces of one operation

One call feeds both the timeline and the slowest list in the detail view. Ordered by start time (not duration) because the timeline needs chronological coverage; the UI sorts by duration itself.

**Files:**
- Modify: `jeffrey-microscope/profiles/profile-persistence-api/src/main/java/cafe/jeffrey/provider/profile/api/TraceRepository.java`
- Modify: `jeffrey-microscope/profiles/profile-sql-persistence/src/main/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepository.java`
- Modify: `shared/persistence/src/main/java/…/StatementLabel.java` (the file holding `TRACE_OPERATIONS`, `TRACE_OVERVIEW` — find with `find . -name StatementLabel.java -not -path '*/target/*'`)
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/TraceManager.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/TraceManagerImpl.java`
- Modify: `jeffrey-microscope/core-microscope/src/main/java/cafe/jeffrey/microscope/core/web/controllers/profile/TracesController.java`
- Test: `jeffrey-microscope/profiles/profile-sql-persistence/src/test/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepositoryTest.java`

**Interfaces:**
- Consumes: `TraceSummaryRecord` and `TraceRow` (both already exist, unchanged).
- Produces: `TraceRepository.tracesOfOperation(String rootName, int limit) → List<TraceSummaryRecord>`; `TraceManager.tracesOfOperation(String rootName, int limit) → List<TraceRow>`; `GET /api/internal/profiles/{profileId}/traces/operation/traces?name=…&limit=…`. Task 5 calls the endpoint.

- [ ] **Step 1: Write the failing test**

Add to the same `@Nested` class as `aggregatesOperations`:

```java
        @Test
        @DisplayName("the traces of an operation exclude other types and honour the limit")
        void listsTracesOfOneOperation(DataSource dataSource) throws SQLException {
            JdbcTraceRepository repository = derived(dataSource);

            List<TraceSummaryRecord> traces = repository
                    .tracesOfOperation("POST /api/internal/profiles/{profileId}/flamegraph", 10);

            assertEquals(1, traces.size());
            assertEquals(SLOW_TRACE, traces.getFirst().traceId());
            assertEquals(120 * MS, traces.getFirst().durationNanos());
            assertEquals(3, traces.getFirst().spanCount());

            assertTrue(repository.tracesOfOperation("flamegraph.generate", 10).isEmpty(),
                    "a nested span name roots no trace");
            assertTrue(repository.tracesOfOperation("GET /api/internal/health", 0).isEmpty(),
                    "a zero limit returns nothing rather than everything");
        }
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/profiles/profile-sql-persistence -am test \
  -Dtest=JdbcTraceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile error — no `tracesOfOperation` on `JdbcTraceRepository`.

- [ ] **Step 3: Declare it on the interface**

In `TraceRepository.java`, after `slowestTraces(int)`:

```java
    /**
     * Lists the traces of one type — every trace whose root span carries {@code rootName} — in the
     * order they ran.
     * <p>
     * Chronological rather than slowest-first because the caller plots them over time as well as
     * ranking them; ranking a list it already holds is cheaper than a second query.
     *
     * @param rootName the trace type, as listed by {@link #operations(int)}
     * @param limit    maximum number of traces to return
     */
    List<TraceSummaryRecord> tracesOfOperation(String rootName, int limit);
```

- [ ] **Step 4: Implement the query**

In `JdbcTraceRepository.java`, add the constant next to `SLOWEST_TRACES`:

```java
    //language=SQL
    private static final String TRACES_OF_OPERATION = """
            SELECT
                trace_id,
                root_name,
                root_kind,
                start_timestamp_from_beginning          AS start_ms,
                EPOCH_MS(start_timestamp)               AS start_epoch_ms,
                duration                                AS duration_ns,
                span_count,
                error_count
            FROM traces
            WHERE root_name = :root_name
            ORDER BY start_timestamp
            LIMIT :limit
            """;
```

and the method next to `slowestTraces`:

```java
    @Override
    public List<TraceSummaryRecord> tracesOfOperation(String rootName, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("root_name", rootName)
                .addValue("limit", limit);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_TRACES,
                TRACES_OF_OPERATION,
                params,
                (rs, _) -> new TraceSummaryRecord(
                        rs.getLong("trace_id"),
                        rs.getString("root_name"),
                        rs.getString("root_kind"),
                        rs.getLong("start_ms"),
                        rs.getLong("start_epoch_ms"),
                        rs.getLong("duration_ns"),
                        rs.getInt("span_count"),
                        rs.getInt("error_count")));
    }
```

Add `TRACE_OPERATION_TRACES,` to `StatementLabel` beside the existing `TRACE_OPERATIONS`.

- [ ] **Step 5: Run the test to verify it passes**

Same command as Step 2. Expected: PASS.

- [ ] **Step 6: Expose it through the manager**

In `TraceManager.java`, after `slowestTraces(int)`:

```java
    /**
     * @param rootName the trace type to list
     * @param limit    maximum number of traces to return
     * @return the traces of one type, in the order they ran
     */
    List<TraceRow> tracesOfOperation(String rootName, int limit);
```

In `TraceManagerImpl.java`, beside `slowestTraces`:

```java
    @Override
    public List<TraceRow> tracesOfOperation(String rootName, int limit) {
        return traceRepository.tracesOfOperation(rootName, limit).stream()
                .map(TraceManagerImpl::toRow)
                .toList();
    }
```

- [ ] **Step 7: Add the endpoint**

In `TracesController.java`, add the constant beside `DEFAULT_OPERATIONS_LIMIT`:

```java
    private static final String DEFAULT_OPERATION_TRACES_LIMIT = "1000";
```

and the handler after `operations`:

```java
    /**
     * The traces of one type. Feeds both the timeline and the slowest list of the operation
     * drill-down, which is why it is ordered by time rather than by duration.
     * <p>
     * The name travels as a query parameter, not a path segment: operation names contain slashes
     * and braces ({@code GET /api/internal/profiles/{profileId}/heap/instances}).
     */
    @GetMapping("/operation/traces")
    public List<TraceRow> operationTraces(
            @PathVariable("profileId") String profileId,
            @RequestParam("name") String name,
            @RequestParam(value = "limit", defaultValue = DEFAULT_OPERATION_TRACES_LIMIT) int limit) {
        LOG.debug("Listing traces of an operation: profileId={} name={} limit={}", profileId, name, limit);
        return resolver.resolve(profileId).traceManager().tracesOfOperation(name, limit);
    }
```

`@RequestParam` is already imported in this file; verify before adding an import.

- [ ] **Step 8: Verify the backend still compiles**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/core-microscope -am compile
```

Expected: BUILD SUCCESS.

---

### Task 4: Scope a flamegraph to a whole operation

Produces the `(thread, window)` intervals every trace of a type covers, then reuses the existing panel and flamegraph machinery. One interval per `(trace, thread)`: the trace window bounds all its spans (verified — no span in the reference profile extends past its trace), and the per-thread split is what keeps another thread's samples out of an async trace's graph.

**Files:**
- Modify: `jeffrey-microscope/profiles/profile-persistence-api/src/main/java/cafe/jeffrey/provider/profile/api/TraceRepository.java`
- Modify: `jeffrey-microscope/profiles/profile-sql-persistence/src/main/java/cafe/jeffrey/provider/profile/jdbc/JdbcTraceRepository.java`
- Modify: `shared/persistence/src/main/java/…/StatementLabel.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/TraceManager.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/manager/TraceManagerImpl.java`
- Create: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/resources/request/GenerateTraceOperationFlamegraphRequest.java`
- Modify: `jeffrey-microscope/profiles/profile-management/src/main/java/cafe/jeffrey/profile/resources/request/SpanFlamegraphOptions.java` (the `permits` clause)
- Modify: `jeffrey-microscope/core-microscope/src/main/java/cafe/jeffrey/microscope/core/web/controllers/profile/TracesController.java`
- Test: `jeffrey-microscope/profiles/profile-management/src/test/java/cafe/jeffrey/profile/manager/TraceManagerImplTest.java`

**Interfaces:**
- Consumes: `SpanInterval(long threadHash, long fromEpochMillis, long toEpochMillis)`, `TraceSpanRecord`, `SpanScopedGraphParameters.of(ProfileInfo, SpanFlamegraphOptions, List<SpanInterval>)`, `JfrFlamegraphPanelProvider.panels(...)` — all existing.
- Produces: `TraceRepository.spansOfOperation(String rootName) → List<TraceSpanRecord>`; `TraceManager.operationIntervals(String rootName) → List<SpanInterval>`; `GET …/traces/operation/panels?name=…`; `POST …/traces/operation/flamegraph`. Task 7 calls both endpoints.

- [ ] **Step 1: Write the failing test**

Add a new `@Nested` class at the end of `TraceManagerImplTest` (the existing `spanOnThread` helper is reused; it pins every span to `TRACE`, which is what makes the single-trace case exact):

```java
    @Nested
    @DisplayName("Operation intervals")
    class OperationIntervals {

        private static final String OPERATION = "POST /orders";

        @Test
        @DisplayName("one interval per thread, spanning that thread's work in the trace")
        void groupsByThread() {
            when(traceRepository.spansOfOperation(OPERATION)).thenReturn(List.of(
                    span(1, null, "root", 0, 100),
                    span(2, 1L, "child", 10, 20),
                    spanOnThread(3, 1L, "async", 60, 20, OTHER_THREAD)));

            List<SpanInterval> intervals = new TraceManagerImpl(traceRepository)
                    .operationIntervals(OPERATION);

            assertEquals(2, intervals.size(), "the two threads the trace touched");
            SpanInterval main = intervals.stream()
                    .filter(interval -> interval.threadHash() == THREAD).findFirst().orElseThrow();
            assertEquals(0, main.fromEpochMillis());
            assertEquals(100, main.toEpochMillis(), "the root's window covers its same-thread child");

            SpanInterval other = intervals.stream()
                    .filter(interval -> interval.threadHash() == OTHER_THREAD).findFirst().orElseThrow();
            assertEquals(60, other.fromEpochMillis());
            assertEquals(80, other.toEpochMillis());
        }

        @Test
        @DisplayName("an operation with no spans yields no intervals rather than a null window")
        void handlesAnUnknownOperation() {
            when(traceRepository.spansOfOperation("nope")).thenReturn(List.of());

            assertTrue(new TraceManagerImpl(traceRepository).operationIntervals("nope").isEmpty());
        }
    }
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/profiles/profile-management -am test \
  -Dtest=TraceManagerImplTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: compile error — no `spansOfOperation` / `operationIntervals`.

- [ ] **Step 3: Add the repository read**

In `TraceRepository.java`:

```java
    /**
     * Returns every span of every trace of one type, so the caller can reduce them to the windows a
     * flamegraph is scoped to. Ordered by start time, which is what makes the reduction stable.
     *
     * @param rootName the trace type, as listed by {@link #operations(int)}
     */
    List<TraceSpanRecord> spansOfOperation(String rootName);
```

In `JdbcTraceRepository.java`, add the constant beside `SPANS_OF_TRACE`. It is the same projection with a different predicate, so the existing row mapper is reused verbatim:

```java
    //language=SQL
    private static final String SPANS_OF_OPERATION = """
            SELECT
                s.trace_id                              AS trace_id,
                s.span_id                               AS span_id,
                s.parent_span_id                        AS parent_span_id,
                s.name                                  AS name,
                s.kind                                  AS kind,
                s.status                                AS status,
                s.error_type                            AS error_type,
                CAST(s.attributes AS VARCHAR)           AS attributes,
                s.start_timestamp_from_beginning        AS start_ms,
                EPOCH_MS(s.start_timestamp)             AS start_epoch_ms,
                s.duration                              AS duration_ns,
                COALESCE(s.thread_hash, 0)              AS thread_hash,
                th.name                                 AS thread_name,
                s.event_type                            AS event_type
            FROM trace_spans s
            JOIN traces t ON t.trace_id = s.trace_id
            LEFT JOIN threads th ON s.thread_hash = th.thread_hash
            WHERE t.root_name = :root_name
            ORDER BY s.start_timestamp
            """;
```

and the method, copying the mapper lambda from the existing `spansOf(long)`:

```java
    @Override
    public List<TraceSpanRecord> spansOfOperation(String rootName) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("root_name", rootName);

        return databaseClient.query(
                StatementLabel.TRACE_OPERATION_SPANS,
                SPANS_OF_OPERATION,
                params,
                traceSpanMapper());
    }
```

If `spansOf(long)` today inlines its mapper lambda rather than exposing one, extract it into a `private static RowMapper<TraceSpanRecord> traceSpanMapper()` and have both methods use it — do not duplicate the fourteen-column mapping.

Add `TRACE_OPERATION_SPANS,` to `StatementLabel`.

- [ ] **Step 4: Reduce the spans to intervals**

In `TraceManager.java`, after `spanIntervals`:

```java
    /**
     * Reduces every trace of one type to the {@code (thread, window)} intervals a flamegraph can be
     * scoped to, so the graph shows exactly the samples taken while traces of that type were running.
     * <p>
     * One interval per {@code (trace, thread)}: a trace's window bounds all of its spans, and
     * splitting by thread is what keeps a concurrently-running thread's samples out of the graph
     * when a trace hands work off.
     *
     * @param rootName the trace type to scope to
     * @return the intervals, or empty when no trace has that root name
     */
    List<SpanInterval> operationIntervals(String rootName);
```

In `TraceManagerImpl.java`, beside `spanIntervals`:

```java
    @Override
    public List<SpanInterval> operationIntervals(String rootName) {
        Map<ThreadWindow, long[]> windows = new LinkedHashMap<>();
        for (TraceSpanRecord span : traceRepository.spansOfOperation(rootName)) {
            ThreadWindow key = new ThreadWindow(span.traceId(), span.threadHash());
            long[] window = {span.startEpochMillis(), endMillisOf(span)};
            windows.merge(key, window, (existing, candidate) -> new long[]{
                    Math.min(existing[0], candidate[0]),
                    Math.max(existing[1], candidate[1])});
        }

        return windows.entrySet().stream()
                .map(entry -> new SpanInterval(
                        entry.getKey().threadHash(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }
```

and the key record beside the existing private records at the bottom of the class:

```java
    /** Identifies one thread's stretch of one trace — the unit an operation's intervals reduce to. */
    private record ThreadWindow(long traceId, long threadHash) {
    }
```

Add `java.util.LinkedHashMap` to the imports (`Map` is already imported).

- [ ] **Step 5: Run the test to verify it passes**

Same command as Step 2. Expected: PASS.

- [ ] **Step 6: Add the request record**

Create `GenerateTraceOperationFlamegraphRequest.java` with the standard AGPL header, then:

```java
package cafe.jeffrey.profile.resources.request;

import cafe.jeffrey.shared.common.model.Type;
import cafe.jeffrey.profile.common.config.GraphComponents;

/**
 * Request for a flamegraph scoped to every trace of one type. Carries no time range or thread — the
 * backend derives both from the traces of {@code name}, so the graph contains only the samples those
 * traces cover.
 *
 * @param name the trace type, identified by its root span's operation name
 */
public record GenerateTraceOperationFlamegraphRequest(
        String name,
        Type eventType,
        boolean useThreadMode,
        Boolean useWeight,
        boolean excludeNonJavaSamples,
        boolean excludeIdleSamples,
        boolean onlyUnsafeAllocationSamples,
        GraphComponents components) implements SpanFlamegraphOptions {
}
```

In `SpanFlamegraphOptions.java`, add the new type to the `permits` clause and mention it in the interface Javadoc alongside the other three.

- [ ] **Step 7: Add the two endpoints**

In `TracesController.java`, after the span-scoped handlers:

```java
    /**
     * Which event types recorded samples inside the traces of one type, with their real counts, so
     * the drill-down offers only flamegraphs that exist.
     */
    @GetMapping("/operation/panels")
    public List<FlamegraphPanel> operationPanels(
            @PathVariable("profileId") String profileId,
            @RequestParam("name") String name) {
        LOG.debug("Building operation-scoped flamegraph panels: profileId={} name={}", profileId, name);
        ProfileManager profileManager = resolver.resolve(profileId);

        List<SpanInterval> intervals = profileManager.traceManager().operationIntervals(name);
        if (intervals.isEmpty()) {
            return List.of();
        }
        return panelProvider.panels(
                profileManager.flamegraphManager().eventSummaries(intervals), PanelContext.PRIMARY);
    }

    /**
     * A flamegraph of the samples taken while any trace of one type was running — "what does this
     * kind of request spend its time on", as opposed to the single-span graph next door.
     */
    @PostMapping(value = "/operation/flamegraph", produces = FlamegraphController.PROTOBUF_MEDIA_TYPE)
    public byte[] operationFlamegraph(
            @PathVariable("profileId") String profileId,
            @RequestBody GenerateTraceOperationFlamegraphRequest request) {
        LOG.debug("Generating operation flamegraph: profileId={} name={} eventType={}",
                profileId, request.name(), request.eventType());
        ProfileManager profileManager = resolver.resolve(profileId);

        List<SpanInterval> intervals = profileManager.traceManager().operationIntervals(request.name());
        if (intervals.isEmpty()) {
            throw Exceptions.resourceNotFound("Operation has no samples to show: " + request.name());
        }

        GraphParameters params = SpanScopedGraphParameters.of(profileManager.info(), request, intervals);
        return profileManager.flamegraphManager().generate(params);
    }
```

Add the import for `GenerateTraceOperationFlamegraphRequest`. Every other type used here is already imported by the neighbouring span handlers — verify rather than assume.

- [ ] **Step 8: Verify the backend compiles and both trace test classes pass**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/core-microscope -am compile

JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl jeffrey-microscope/profiles/profile-sql-persistence -am test \
  -Dtest=JdbcTraceRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false
```

Expected: BUILD SUCCESS, tests PASS.

---

### Task 5: Frontend API surface

Types and clients only — no rendering yet, so this task is verified by the build rather than by a view.

**Files:**
- Modify: `jeffrey-microscope/pages-microscope/src/services/api/model/trace/TraceModels.ts`
- Modify: `jeffrey-microscope/pages-microscope/src/services/api/ProfileTracesClient.ts`
- Create: `jeffrey-microscope/pages-microscope/src/services/api/TraceOperationFlamegraphClient.ts`

**Interfaces:**
- Consumes: Task 1's `spanCount`, Task 2's `totalNanos`, Task 3's `/operation/traces`, Task 4's `/operation/panels` and `/operation/flamegraph`.
- Produces: `ProfileTracesClient.getOperationTraces(name, limit?) → Promise<TraceRow[]>`, `.getOperationPanels(name) → Promise<FlamegraphPanel[]>`, and `TraceOperationFlamegraphClient`. Task 7 uses all three.

- [ ] **Step 1: Extend the models**

In `TraceModels.ts`, add `spanCount: number;` to `TraceOperationRow` (after `errorCount`) and `totalNanos: number;` to `TraceOverview` (after `maxNanos`). Update `TraceOverview`'s doc comment, which says the totals cover the whole recording — that stays true; no other edit needed there.

- [ ] **Step 2: Add the two GET methods**

In `ProfileTracesClient.ts`, after `getOperations`:

```ts
  /**
   * The traces of one type, chronologically. One call feeds both the timeline and the slowest list
   * of the operation drill-down.
   */
  public getOperationTraces(name: string, limit?: number): Promise<TraceRow[]> {
    return this.get<TraceRow[]>(
      '/operation/traces',
      limit === undefined ? { name } : { name, limit }
    );
  }

  /** Which event types recorded samples inside the traces of one type, with their real counts. */
  public getOperationPanels(name: string): Promise<FlamegraphPanel[]> {
    return this.get<FlamegraphPanel[]>('/operation/panels', { name });
  }
```

- [ ] **Step 3: Add the flamegraph client**

Create `TraceOperationFlamegraphClient.ts`:

```ts
import GlobalVars from '@/services/GlobalVars';
import RemoteFlamegraphClient from '@/services/api/RemoteFlamegraphClient';
import GraphComponents from '@/services/api/model/GraphComponents';

/**
 * Flamegraph client scoped to one trace type. Sends no time range or thread — the backend derives
 * the scope from the traces carrying this root name, so the result contains only the samples those
 * traces cover. The `timeRange`/`search` arguments of the `FlamegraphClient` contract are ignored.
 */
export default class TraceOperationFlamegraphClient extends RemoteFlamegraphClient {
  private readonly name: string;
  private readonly eventType: string;
  private readonly useThreadMode: boolean;
  private readonly useWeight: boolean | null;
  private readonly excludeNonJavaSamples: boolean;
  private readonly excludeIdleSamples: boolean;
  private readonly onlyUnsafeAllocationSamples: boolean;

  constructor(
    profileId: string,
    name: string,
    eventType: string,
    useThreadMode: boolean,
    useWeight: boolean | null,
    excludeNonJavaSamples: boolean,
    excludeIdleSamples: boolean,
    onlyUnsafeAllocationSamples: boolean
  ) {
    super(GlobalVars.internalUrl + '/profiles/' + profileId + '/traces/operation/flamegraph');
    this.name = name;
    this.eventType = eventType;
    this.useThreadMode = useThreadMode;
    this.useWeight = useWeight;
    this.excludeNonJavaSamples = excludeNonJavaSamples;
    this.excludeIdleSamples = excludeIdleSamples;
    this.onlyUnsafeAllocationSamples = onlyUnsafeAllocationSamples;
  }

  // The operation scope fully defines the data — timeRange/search of the contract are ignored.
  protected bothContent(components: GraphComponents): Record<string, unknown> {
    return {
      name: this.name,
      eventType: this.eventType,
      useWeight: this.useWeight,
      useThreadMode: this.useThreadMode,
      excludeNonJavaSamples: this.excludeNonJavaSamples,
      excludeIdleSamples: this.excludeIdleSamples,
      onlyUnsafeAllocationSamples: this.onlyUnsafeAllocationSamples,
      components: components
    };
  }
}
```

Copy the AGPL header from `SpanFlamegraphClient.ts` to the top.

- [ ] **Step 4: Verify the build**

```bash
cd jeffrey-microscope/pages-microscope && npm run build
```

Expected: `✓ built in …`.

---

### Task 6: Extract the timeline bucketing

`SpanTagDetail.vue` computes its buckets inline. The operation detail needs the same computation over traces, so it moves to a tested module both views import.

**Files:**
- Create: `jeffrey-microscope/pages-microscope/src/services/trace/traceTimelineBuckets.ts`
- Create: `jeffrey-microscope/pages-microscope/src/services/trace/traceTimelineBuckets.test.ts`
- Modify: `jeffrey-microscope/pages-microscope/src/components/span/SpanTagDetail.vue`

**Interfaces:**
- Consumes: nothing.
- Produces: `timelineBuckets<T>(items: T[], startMillis: (item: T) => number, durationNanos: (item: T) => number, bucketCount: number): TimelineBucket[]` where `TimelineBucket = { mid: number; maxDuration: number; count: number }`. Task 7 calls it with `TraceRow`.

- [ ] **Step 1: Write the failing test**

Create `traceTimelineBuckets.test.ts` (copy the AGPL header from `TraceWaterfallLayout.test.ts`):

```ts
import { describe, expect, it } from 'vitest';
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';

interface Item {
  start: number;
  duration: number;
}

const start = (item: Item) => item.start;
const duration = (item: Item) => item.duration;

describe('timelineBuckets', () => {
  it('returns nothing for no items, rather than empty buckets', () => {
    expect(timelineBuckets<Item>([], start, duration, 4)).toEqual([]);
  });

  it('places a single item in the first bucket and keeps its duration', () => {
    const buckets = timelineBuckets<Item>([{ start: 1000, duration: 50 }], start, duration, 4);

    expect(buckets).toHaveLength(4);
    expect(buckets[0].count).toBe(1);
    expect(buckets[0].maxDuration).toBe(50);
    expect(buckets[3].count).toBe(0);
  });

  it('keeps the slowest duration and counts every item in a bucket', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: 0, duration: 10 },
        { start: 1, duration: 90 },
        { start: 2, duration: 20 }
      ],
      start,
      duration,
      1
    );

    expect(buckets[0].count).toBe(3);
    expect(buckets[0].maxDuration).toBe(90);
  });

  it('puts the last item in the last bucket rather than off the end', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: 0, duration: 1 },
        { start: 100, duration: 2 }
      ],
      start,
      duration,
      4
    );

    expect(buckets[0].count).toBe(1);
    expect(buckets[buckets.length - 1].count).toBe(1);
  });

  it('spreads bucket midpoints across the observed range', () => {
    const buckets = timelineBuckets<Item>(
      [
        { start: 0, duration: 1 },
        { start: 100, duration: 1 }
      ],
      start,
      duration,
      4
    );

    const mids = buckets.map(bucket => bucket.mid);
    expect(mids).toEqual([...mids].sort((a, b) => a - b));
    expect(mids[0]).toBeGreaterThanOrEqual(0);
  });
});
```

- [ ] **Step 2: Run it and watch it fail**

```bash
cd jeffrey-microscope/pages-microscope && npx vitest run src/services/trace/traceTimelineBuckets.test.ts
```

Expected: FAIL — cannot resolve `@/services/trace/traceTimelineBuckets`.

- [ ] **Step 3: Write the module**

Create `traceTimelineBuckets.ts` (AGPL header first):

```ts
/** One column of a metrics timeline: when it sits, how slow the worst item in it was, how many landed. */
export interface TimelineBucket {
  mid: number;
  maxDuration: number;
  count: number;
}

/**
 * Buckets timestamped items into a fixed number of equal columns spanning the observed range.
 *
 * Shared by the async-profiler tag detail and the trace operation detail: both plot "how slow was
 * the worst one, and how many were there" over time, and differ only in what they are counting.
 *
 * @param items        the items to bucket; an empty list produces no buckets at all
 * @param startMillis  reads an item's start as UTC epoch millis
 * @param durationNanos reads an item's duration in nanoseconds
 * @param bucketCount  how many columns to produce
 */
export function timelineBuckets<T>(
  items: T[],
  startMillis: (item: T) => number,
  durationNanos: (item: T) => number,
  bucketCount: number
): TimelineBucket[] {
  if (items.length === 0) {
    return [];
  }

  let min = Infinity;
  let max = -Infinity;
  for (const item of items) {
    const start = startMillis(item);
    if (start < min) {
      min = start;
    }
    if (start > max) {
      max = start;
    }
  }

  const span = Math.max(1, max - min);
  const width = Math.max(1, Math.ceil(span / bucketCount));

  const buckets: TimelineBucket[] = [];
  for (let i = 0; i < bucketCount; i++) {
    buckets.push({ mid: min + i * width + width / 2, maxDuration: 0, count: 0 });
  }

  for (const item of items) {
    const index = Math.min(bucketCount - 1, Math.floor((startMillis(item) - min) / width));
    const bucket = buckets[index];
    bucket.count++;
    const duration = durationNanos(item);
    if (duration > bucket.maxDuration) {
      bucket.maxDuration = duration;
    }
  }
  return buckets;
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd jeffrey-microscope/pages-microscope && npx vitest run src/services/trace/traceTimelineBuckets.test.ts
```

Expected: 5 passed.

- [ ] **Step 5: Point `SpanTagDetail` at the shared module**

In `SpanTagDetail.vue`, delete the local `Bucket` interface and the whole `buckets` computed, and replace them with:

```ts
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';

const buckets = computed(() =>
  timelineBuckets(
    spans.value,
    span => span.startEpochMillis,
    span => span.durationNanos,
    TIMELINE_BUCKETS
  )
);
```

Keep `TIMELINE_BUCKETS = 40` and both `primaryData`/`secondaryData` computeds exactly as they are — they already map `b.mid`, `b.maxDuration` and `b.count`.

- [ ] **Step 6: Verify the build and the whole frontend suite**

```bash
cd jeffrey-microscope/pages-microscope && npm run build && npm run test
```

Expected: build succeeds, all Vitest suites pass.

---

### Task 7: The operation detail view

Mirrors `SpanTagDetail.vue` and `SpanTagFlamegraphs.vue`. Presentational only — Task 8 mounts it.

**Files:**
- Create: `jeffrey-microscope/pages-microscope/src/components/trace/TraceOperationFlamegraphs.vue`
- Create: `jeffrey-microscope/pages-microscope/src/components/trace/TraceOperationDetail.vue`

**Interfaces:**
- Consumes: Task 5's `getOperationTraces`, `getOperationPanels`, `TraceOperationFlamegraphClient`; Task 6's `timelineBuckets`; the existing `TraceSlowestList` (props `traces`, `total?`, `note?`; emits `rowClick: [trace: TraceRow]` — verified), `TraceSpansModal` (props `profileId`, `traceId`, `rootName`, `v-model:show` — verified), `TabBar`, `TimeSeriesChart`, `FlamegraphCardGrid`, `useFlamegraphPanels`.
- Produces: `<TraceOperationDetail :profile-id="…" :name="…" />`. No emits — the waterfall modal is hosted here, so nothing bubbles to the page.

- [ ] **Step 1: Create the flamegraph tab**

Create `TraceOperationFlamegraphs.vue`. It is `SpanTagFlamegraphs.vue` with the tag swapped for an operation name — read that file and copy its template verbatim, changing only the modal title line and the `modal-id`:

```vue
<template>
  <div>
    <LoadingState v-if="!loaded" message="Loading flamegraph events..." />

    <EmptyState
      v-else-if="!hasEvents"
      icon="bi-fire"
      title="No Flamegraph Data"
      description="No execution, wall-clock or allocation samples were taken while traces of this operation were running."
    />

    <FlamegraphCardGrid
      v-else
      :graph-mode="GraphType.PRIMARY"
      :panels="panels"
      :hide-method="true"
      :hide-native="true"
      :hide-blocking="true"
      emit-view
      @view="openFlamegraph"
    />

    <GenericModal
      modal-id="traceOperationFlamegraphModal"
      :show="showDialog"
      size="fullscreen"
      :show-footer="false"
      @update:show="showDialog = $event"
    >
      <template #header>
        <h5 class="modal-title"><i class="bi bi-fire me-2"></i>{{ activeTitle }} — {{ name }}</h5>
        <button type="button" class="btn-close" @click="showDialog = false" aria-label="Close" />
      </template>
      <div id="scrollable-wrapper" style="padding: 0.75rem" v-if="showDialog">
        <TimeSeriesChart
          :graph-updater="graphUpdater"
          :primary-axis-type="
            TimeseriesEventAxeFormatter.resolveAxisFormatter(activeUseWeight, activeEventType)
          "
          :visible-minutes="60"
          :zoom-enabled="true"
          time-unit="seconds"
        />
        <FlamegraphComponent
          :with-timeseries="true"
          :use-weight="activeUseWeight"
          :use-guardian="null"
          scrollableWrapperClass="scrollable-wrapper"
          :flamegraph-tooltip="flamegraphTooltip"
          :graph-updater="graphUpdater"
          @loaded="onFlamegraphLoaded"
        />
      </div>
    </GenericModal>
  </div>
</template>
```

The script block is the same as `SpanTagFlamegraphs.vue`'s with three substitutions — the prop, the panels source and the client:

```ts
const MODAL_INIT_DELAY_MS = 200;

const props = defineProps<{
  profileId: string;
  name: string;
}>();

// Operation-scoped panels so the cards show the real per-operation counts (matching the
// flamegraph), not the profile-wide totals.
const { loaded, panels } = useFlamegraphPanels(GraphType.PRIMARY, () =>
  new ProfileTracesClient(props.profileId).getOperationPanels(props.name)
);

const hasEvents = computed(() => panels.value.some(panel => panel.event.primary.samples > 0));

const showDialog = ref(false);
const activeTitle = ref('');
const activeEventType = ref('');
const activeUseWeight = ref(false);
let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

function openFlamegraph(payload: FlamegraphCardViewPayload) {
  activeTitle.value = payload.eventType;
  activeEventType.value = payload.eventType;
  activeUseWeight.value = payload.useWeight;

  // The backend scopes the graph to this operation's traces (their thread + window), so no time
  // range or thread filter is sent.
  const client = new TraceOperationFlamegraphClient(
    props.profileId,
    props.name,
    payload.eventType,
    payload.useThreadMode,
    payload.useWeight,
    payload.excludeNonJavaSamples,
    payload.excludeIdleSamples,
    payload.onlyUnsafeAllocationSamples
  );

  graphUpdater = new FullGraphUpdater(client, false);
  flamegraphTooltip = FlamegraphTooltipFactory.create(payload.eventType, payload.useWeight, false);

  showDialog.value = true;

  // Delay so the modal (flamegraph + timeseries) is rendered and callbacks registered.
  setTimeout(() => {
    graphUpdater.initialize();
  }, MODAL_INIT_DELAY_MS);
}

function onFlamegraphLoaded() {
  scrollToTop();
}

function scrollToTop() {
  const wrapper = document.getElementById('scrollable-wrapper');
  if (wrapper) {
    wrapper.scrollTop = 0;
  }
}
```

Imports are `SpanTagFlamegraphs.vue`'s with `SpanFlamegraphClient` → `TraceOperationFlamegraphClient` and `ProfileAsyncProfilerClient` → `ProfileTracesClient`. AGPL header at the top.

- [ ] **Step 2: Create the detail view**

Create `TraceOperationDetail.vue`:

```vue
<template>
  <div class="dashboard-container">
    <LoadingState v-if="loading" message="Loading operation details..." />

    <template v-else>
      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <div v-show="activeTab === 'flames'">
        <TraceOperationFlamegraphs :profile-id="profileId" :name="name" />
      </div>

      <div v-show="activeTab === 'timeline'">
        <TimeSeriesChart
          :primary-data="primaryData"
          primary-title="Trace Duration"
          :secondary-data="secondaryData"
          secondary-title="Traces"
          :visible-minutes="60"
          :independentSecondaryAxis="true"
          :primary-axis-type="AxisFormatType.DURATION_IN_NANOS"
          :secondary-axis-type="AxisFormatType.NUMBER"
        />
      </div>

      <div v-show="activeTab === 'slowest'">
        <TraceSlowestList
          :traces="slowest"
          :total="traces.length"
          :note="capNote"
          @row-click="openTrace"
        />

        <TraceSpansModal
          v-model:show="spansShow"
          :profile-id="profileId"
          :trace-id="selectedTrace?.traceId ?? ''"
          :root-name="selectedTrace?.rootName ?? ''"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';

import LoadingState from '@shared/components/LoadingState.vue';
import TabBar from '@shared/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import TraceSlowestList from '@/components/trace/TraceSlowestList.vue';
import TraceSpansModal from '@/components/trace/TraceSpansModal.vue';
import TraceOperationFlamegraphs from '@/components/trace/TraceOperationFlamegraphs.vue';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import { timelineBuckets } from '@/services/trace/traceTimelineBuckets';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import type { TraceRow } from '@/services/api/model/trace/TraceModels';

const TIMELINE_BUCKETS = 40;
/** Matches the backend's default; a type with more traces than this is summarised, not listed. */
const TRACE_LIMIT = 1000;

const props = defineProps<{
  profileId: string;
  name: string;
}>();

const loading = ref(true);
const traces = ref<TraceRow[]>([]);
const activeTab = ref('flames');

// The waterfall is opened here rather than by navigating to Slowest Traces: that page resolves a
// trace from its own capped list, which need not contain this operation's traces.
const spansShow = ref(false);
const selectedTrace = ref<TraceRow | null>(null);

function openTrace(trace: TraceRow): void {
  selectedTrace.value = trace;
  spansShow.value = true;
}

const tabs: TabBarItem[] = [
  { id: 'flames', label: 'Flamegraphs', icon: 'fire' },
  { id: 'timeline', label: 'Metrics Timeline', icon: 'graph-up' },
  { id: 'slowest', label: 'Slowest Traces', icon: 'hourglass-split' }
];

const buckets = computed(() =>
  timelineBuckets(
    traces.value,
    trace => trace.startEpochMillis,
    trace => trace.durationNanos,
    TIMELINE_BUCKETS
  )
);

const primaryData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.maxDuration]));
const secondaryData = computed<number[][]>(() => buckets.value.map(b => [b.mid, b.count]));

/** The list ranks by duration; the fetch is chronological so the timeline stays unbiased. */
const slowest = computed<TraceRow[]>(() =>
  [...traces.value].sort((a, b) => b.durationNanos - a.durationNanos)
);

// Silence about a cap reads as "this is all of them", which it would not be.
const capNote = computed<string | undefined>(() => {
  if (traces.value.length < TRACE_LIMIT) {
    return undefined;
  }
  return `First ${TRACE_LIMIT} traces of this operation`;
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    traces.value = await new ProfileTracesClient(props.profileId).getOperationTraces(
      props.name,
      TRACE_LIMIT
    );
  } catch (e: unknown) {
    console.error('Failed to load traces for operation:', e);
    traces.value = [];
  } finally {
    loading.value = false;
  }
}

watch(() => props.name, load);

onMounted(load);
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}
</style>
```

`TraceSlowestList`'s props (`traces`, `total?`, `note?`) and `TraceSpansModal`'s (`profileId`, `traceId`, `rootName`, `v-model:show`) are as used above — both were read while writing this plan. `accent` and `tone` are internal to `TraceSlowestList`, not props; do not pass them.

- [ ] **Step 3: Verify the build**

```bash
cd jeffrey-microscope/pages-microscope && npm run build
```

Expected: `✓ built in …`. Unused-import or unknown-prop mistakes surface here.

---

### Task 8: Mount the detail and remove the dead end

Wires `?operation=` into the page, moves the header stats onto the uncapped overview, and deletes the filtered-trace path the drill-down replaces.

**Files:**
- Modify: `jeffrey-microscope/pages-microscope/src/views/profiles/detail/technologies/ProfileTraceOperations.vue`
- Modify: `jeffrey-microscope/pages-microscope/src/components/trace/TraceOperationStats.vue`
- Modify: `jeffrey-microscope/pages-microscope/src/services/trace/traceOperationMetrics.ts`
- Modify: `jeffrey-microscope/pages-microscope/src/services/trace/traceOperationMetrics.test.ts`
- Modify: `jeffrey-microscope/pages-microscope/src/views/profiles/detail/technologies/ProfileTraces.vue`

**Interfaces:**
- Consumes: Task 7's `TraceOperationDetail`, Task 5's models, Task 2's `overview.totalNanos` / `overview.distinctOperations`.
- Produces: the finished feature. Nothing downstream.

- [ ] **Step 1: Narrow the totals helper**

In `traceOperationMetrics.ts`, drop the three fields the overview now supplies and rewrite the doc comment, which currently argues *for* reducing from the rows:

```ts
/**
 * The per-operation extremes shown above the operation list.
 *
 * Reduced from the rows the list renders, because they describe *an operation* — the profile-wide
 * overview cannot express "the slowest single operation". The profile totals beside them come from
 * the overview instead, which is uncapped.
 */
export interface TraceOperationTotals {
  worstP95Nanos: number;
  slowestNanos: number;
}

export function operationTotals(operations: TraceOperationRow[]): TraceOperationTotals {
  return operations.reduce<TraceOperationTotals>(
    (totals, operation) => ({
      worstP95Nanos: Math.max(totals.worstP95Nanos, operation.p95Nanos),
      slowestNanos: Math.max(totals.slowestNanos, operation.maxNanos)
    }),
    { worstP95Nanos: 0, slowestNanos: 0 }
  );
}
```

In `traceOperationMetrics.test.ts`, delete the assertions on `operations`, `calls`, `errors` and `totalNanos` (including the empty-input `toEqual` object, which must shrink to `{ worstP95Nanos: 0, slowestNanos: 0 }`). Keep every case that exercises the two remaining fields.

- [ ] **Step 2: Take the counts from the overview**

In `TraceOperationStats.vue`, add an `overview` prop and read the four counting values from it:

```ts
const props = defineProps<{
  operations: TraceOperationRow[];
  overview: TraceOverview;
}>();

const totals = computed(() => operationTotals(props.operations));

const metrics = computed(() => [
  {
    icon: 'bar-chart-steps',
    title: 'Operations',
    value: FormattingService.formatNumber(props.overview.distinctOperations),
    variant: 'info' as const,
    breakdown: [
      { label: 'Traces', value: FormattingService.formatNumber(props.overview.totalTraces) },
      { label: 'Errors', value: FormattingService.formatNumber(props.overview.errorTraces) }
    ]
  },
  {
    icon: 'clock-fill',
    title: 'Slowest Operation',
    value: FormattingService.formatDuration2Units(totals.value.slowestNanos),
    variant: 'highlight' as const,
    breakdown: [
      { label: 'Total', value: FormattingService.formatDuration2Units(props.overview.totalNanos) },
      {
        label: 'Worst P95',
        value: FormattingService.formatDuration2Units(totals.value.worstP95Nanos)
      }
    ]
  }
]);
```

Add the `TraceOverview` type import. The "Calls" label becomes "Traces" — a row is a trace type now, and its breakdown counts traces.

- [ ] **Step 3: Mount the detail behind `?operation=`**

In `ProfileTraceOperations.vue`, replace the template's content branch and the `openTraces` function:

```vue
    <div v-else class="dashboard-container">
      <template v-if="selectedOperation === ''">
        <TraceOperationStats v-if="overview" :operations="operations" :overview="overview" />
        <TraceOperationList :operations="operations" @operation-click="openOperation" />
      </template>

      <template v-else>
        <DetailBreadcrumb root-label="Trace Operations" icon="bi-bar-chart-steps" @back="clearSelection">
          {{ selectedOperation }}
        </DetailBreadcrumb>

        <EmptyState
          v-if="!isKnownOperation"
          title="Unknown Operation"
          message="No trace in this profile is rooted at that operation."
          icon="bi-bar-chart-steps"
        />

        <TraceOperationDetail v-else :profile-id="profileId" :name="selectedOperation" />
      </template>
    </div>
```

and in the script:

```ts
const overview = ref<TraceOverview | null>(null);

/** The selection lives in the URL so the detail is linkable and Back steps out of it, not off it. */
const selectedOperation = computed(() => (route.query.operation as string) ?? '');

const isKnownOperation = computed(() =>
  operations.value.some(operation => operation.name === selectedOperation.value)
);

function openOperation(name: string): void {
  router.push({ query: { ...route.query, operation: name } });
}

function clearSelection(): void {
  const query = { ...route.query };
  delete query.operation;
  router.push({ query });
}
```

`loadData` fetches both in parallel:

```ts
    const client = new ProfileTracesClient(profileId.value);
    const [operationRows, overviewData] = await Promise.all([
      client.getOperations(),
      client.getOverview()
    ]);
    operations.value = operationRows;
    overview.value = overviewData;
```

Add imports for `DetailBreadcrumb`, `TraceOperationDetail` and the `TraceOverview` type. `EmptyState` is already imported by this file.

`isKnownOperation` guards a hand-edited or stale URL. It is safe to evaluate against `operations` because both are loaded before the detail renders — `loading` gates the whole block.

- [ ] **Step 4: Delete the filtered-trace path**

In `ProfileTraces.vue`, remove `operationFilter` (line ~108), the filter chip in the template (lines ~36-41), the `Not a root operation` `EmptyState` (lines ~48-49), the `filter` in the `filtered` computed (line ~114) — `filtered` then collapses into `traces` — the `operation`-aware `listTotal`/`listNote` (lines ~119-123) and the `delete query.operation` handler (line ~150).

Leave the `?trace=` handling alone: `selectedTrace`, `spansShow`, `openTrace` (line ~141), the `router.replace({ query: { ...route.query, trace: … } })` at line ~145 and the watcher at line ~167 are how this page opens its own waterfall and are unrelated to the operation filter.

- [ ] **Step 5: Verify the build and the suite**

```bash
cd jeffrey-microscope/pages-microscope && npm run build && npm run test
```

Expected: build succeeds, all Vitest suites pass.

- [ ] **Step 6: Verify against the running app**

```bash
JAVA_HOME=/home/pbouda/.sdkman/candidates/java/25.0.1-amzn \
  /home/pbouda/.sdkman/candidates/maven/current/bin/mvn -o \
  -pl build/build-microscope -am install -DskipTests
java -jar build/build-microscope/target/microscope.jar
```

Open a profile with traces → Technologies → Traces → Trace Operations. Confirm on the reference profile (`019ffc8d-63a7-7b93-85d1-1b3b44f8888a`): the list shows ~36 rows, not ~105; `dominator`, `create_indexes` and `chunk.parse` are gone; the header reads 36 operations / 246 traces; clicking `POST /api/internal/recordings` opens the three tabs; the URL carries `?operation=…` and Back returns to the list.

---

### Task 9: Documentation

**Files:**
- Modify: `jeffrey-pages/src/views/docs/profiles/` — the traces page (locate with `grep -rl "Operations" jeffrey-pages/src/views/docs/profiles/`)

- [ ] **Step 1: Find the page**

```bash
grep -rln "Operations\|Traces" jeffrey-pages/src/views/docs/profiles/
```

- [ ] **Step 2: Rewrite the Operations section**

Describe a trace operation as a trace type identified by its root span's name; state that nested spans are explored through the trace's span tree rather than listed; document the three tabs (Flamegraphs over every trace of the type, Metrics Timeline, Slowest Traces) and that the flamegraph covers exactly the windows those traces ran in. Match the surrounding pages' component and prose style — read a neighbouring doc page first.

- [ ] **Step 3: Verify the docs site builds**

```bash
cd jeffrey-pages && npm run build
```

Expected: build succeeds.

---

## Self-Review

**Spec coverage**

| Spec section | Task |
|---|---|
| §1 Regroup the operations query | 1 |
| §2 Fix the overview and header stats | 2 (backend), 8 (frontend) |
| §3 `tracesOfOperation` | 3 |
| §3 `operationIntervals` | 4 |
| §4 Controller endpoints | 3 (traces), 4 (panels, flamegraph) |
| §5 Frontend detail, tabs, deletions, naming | 5, 6, 7, 8 (naming already applied) |
| §6 Error and empty states | 7 (no samples), 8 (unknown operation, cap note) |
| §7 Testing | 1, 2, 3 (repository), 4 (manager), 6 (Vitest) |
| §8 Documentation | 9 |

**Deviation from the spec:** §1 says `GROUP BY root_name, root_kind`; Task 1 uses `GROUP BY root_name` with `ANY_VALUE(root_kind)`, so a name that somehow carried two kinds yields one row rather than two indistinguishable ones. Same output on well-formed data.

**Type consistency:** `TraceOperationRecord`/`TraceOperationRow` carry `spanCount` in the same position (Task 1) and `TraceOperationRow` (TS) matches (Task 5). `TraceOverviewRecord`/`TraceOverview` (Java) and `TraceOverview` (TS) all take `totalNanos` before `distinctOperations` (Tasks 2, 5). `timelineBuckets` is called with the same four-argument signature in Tasks 6 and 7. `TraceOperationDetail` takes `profileId`/`name` and emits nothing (Task 7), matching how Task 8 mounts it.

**Every component prop used in Tasks 7 and 8 was read from source while writing this plan** — `TraceSlowestList` (`traces`, `total?`, `note?`, `@rowClick`), `TraceSpansModal` (`profileId`, `traceId`, `rootName`, `v-model:show`), `TabBar` (`v-model` + `tabs: TabBarItem[]`), `TimeSeriesChart` (the `primaryData`/`secondaryData` form used by `SpanTagDetail`). No step asks the implementer to guess an interface.
