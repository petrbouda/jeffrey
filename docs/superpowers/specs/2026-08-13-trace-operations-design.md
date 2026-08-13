# Trace Operations — aggregate by trace type, with a three-tab drill-down

Date: 2026-08-13
Status: approved, ready for planning

## Problem

The Traces feature has two pages: **Operations** and **Slowest Traces**. Clicking an operation
leads nowhere useful, and the page's name does not describe what it shows.

Two defects, one root cause.

**The aggregation is at the wrong level.** `JdbcTraceRepository.OPERATIONS` reads
`FROM trace_spans GROUP BY name`, so it aggregates *every* span in the profile, nested ones
included. An "operation" in this project means a *trace type* — the root of a trace, the thing a
trace is an instance of. A trace is a tree of spans; the individual spans inside it are explored
through the waterfall, not through a flat top-level list.

Measured on a real profile (`019ffc8d-63a7-7b93-85d1-1b3b44f8888a`):

| | count |
|---|---|
| spans | 846 |
| traces | 246 |
| distinct span names (what the page lists) | 105 |
| distinct root names (actual trace types) | 36 |
| names that are **never** a trace root | 69 |

So 69 of 105 listed rows are not trace types at all, including three of the four largest by total
time (`dominator` 51.65s, `create_indexes` 35.12s, `chunk.parse` 32.46s — all nested spans).

**There is no drill-down.** `ProfileTraceOperations.vue` routes the click to
`profile-traces?operation=<name>`, and `ProfileTraces.vue` filters that list by `trace.rootName`.
For the 69 nested-only names no trace matches, so the row leads to a "Not a root operation" empty
state. The sibling async-profiler Spans page, by contrast, opens an in-place detail with three tabs
(Flamegraphs / Metrics Timeline / Slowest Spans) via `SpanTagDetail.vue`.

The machinery for that detail already exists on the trace side. `TracesController` builds
`List<SpanInterval>` per span and hands it to `SpanScopedGraphParameters` +
`flamegraphManager().generate(...)` — the same path `AsyncProfilerSpansController` uses for a tag.
Nothing new is needed to scope a flamegraph to a set of windows; only a query that produces the
windows for a whole trace type.

## Goals

1. **Trace Operations** lists trace types, aggregated from `traces` by `root_name`.
2. Clicking one opens an in-place detail with Flamegraphs, Metrics Timeline and Slowest Traces,
   matching the async-profiler Spans page.
3. The "Not a root operation" dead end stops existing by construction.

## Non-goals

- A top-level list of nested span names. Nested spans are reached through the trace's span tree in
  the waterfall. This is a deliberate loss of the current page's nested-span rows.
- Any schema change. `V001__init.sql` is untouched; `traces` and `trace_spans` already carry
  everything needed.
- Cross-profile or comparative analysis.

## Design

### 1. Regroup the operations query

`JdbcTraceRepository.OPERATIONS` becomes `FROM traces GROUP BY root_name, root_kind`. Every metric
then describes traces rather than spans:

| field | before | after |
|---|---|---|
| `name` | `trace_spans.name` | `traces.root_name` |
| `kind` | `ANY_VALUE(trace_spans.kind)` | `traces.root_kind` |
| `count` | spans carrying the name | traces of the type |
| `errorCount` | spans with `status = 'ERROR'` | traces with `error_count > 0` |
| `totalNanos` / `p50Nanos` / `p95Nanos` / `maxNanos` | span duration | trace duration (whole tree) |

`TraceOperationRecord` and `TraceOperationRow` gain one field, `spanCount` — total spans across the
type's traces — so a 1-span trace type is distinguishable from a 44-span one. Their Javadoc, which
currently describes span-name aggregation, is rewritten.

Ordering (`ORDER BY total_ns DESC`) and the `limit` parameter stay as they are.

### 2. Fix the overview and the header stats

`TraceOverviewRecord.distinctOperations` is currently
`(SELECT COUNT(DISTINCT name) FROM trace_spans)`. It becomes `COUNT(DISTINCT root_name)` over
`traces`.

`TraceOperationStats.vue` derives its header numbers by summing the row list returned by
`/operations`, which is capped at 100 — the page currently reports "OPERATIONS 100 / CALLS 834"
where the truth is 105 and 846, presenting a truncation as a total.

The counting tiles switch to the uncapped, SQL-aggregated `/traces/overview`:

| tile | source after |
|---|---|
| Operations | `overview.distinctOperations` |
| Calls | `overview.totalTraces` |
| Errors | `overview.errorTraces` |
| Total | `overview.totalNanos` (new field, `SUM(duration) FROM traces`) |
| Slowest Operation, Worst P95 | still the max across the returned rows |

`TraceOverviewRecord` therefore gains `totalNanos`. The last two stay row-derived: they are extremes
*of an operation*, which the overview cannot express. That leaves them theoretically truncatable by
the row cap, which does not bite at 36 rows and is accepted. `operationTotals` in
`services/trace/traceOperationMetrics.ts` keeps only the extremes; its count and sum fields and
their cases in `traceOperationMetrics.test.ts` are deleted.

### 3. Two new reads

**`TraceRepository.tracesOfOperation(String rootName, int limit)`** → `List<TraceSummaryRecord>`,
filtered by `root_name`, ordered by `start_timestamp` ascending, capped by a named constant. One
call feeds both the timeline and the slowest list; the detail view sorts by duration client-side
for the latter. This mirrors `spanManager().tagSpans(tag)`, which the async-profiler detail already
uses the same way.

**`TraceManager.operationIntervals(String rootName)`** → `List<SpanInterval>`. For each trace of
the type, one interval per distinct `thread_hash`, spanning that thread's earliest span start to
its latest span end within the trace.

Two measured facts keep this simple: no span in the profile extends beyond its trace's window
(verified, 0 rows), so the trace window bounds everything; and only 6 of 246 traces touch more than
one thread, so the result is one interval per trace in the common case. The per-thread grouping is
still required for correctness on async traces — `SpanInterval` is `(threadHash, from, to)` and a
window on the wrong thread would attribute another thread's samples to this operation.

Unlike `spanIntervals(traceId, spanId, selfOnly)`, there is no `selfOnly` variant: a trace type has
no parent to subtract from.

### 4. Controller endpoints

Operation names contain `/` and `{}` (`GET /api/internal/profiles/{profileId}/heap/…`), so the name
cannot be a path segment. It travels as a query parameter, or in the body for the POST — exactly how
`AsyncProfilerSpansController` handles `?tag=`.

```
GET  /api/internal/profiles/{profileId}/traces/operation/traces?name=…&limit=…
GET  /api/internal/profiles/{profileId}/traces/operation/panels?name=…
POST /api/internal/profiles/{profileId}/traces/operation/flamegraph      (name in the body)
```

`panels` and `flamegraph` resolve `operationIntervals(name)` and delegate to the existing
`JfrFlamegraphPanelProvider` / `SpanScopedGraphParameters` path, returning an empty list and a
404 respectively when the interval set is empty — the same contract as the per-span endpoints.

### 5. Frontend

**`ProfileTraceOperations.vue`** reads `?operation=` from the route: absent renders the list,
present renders the detail. The selection lives in the query string rather than component state so
the detail is deep-linkable and the browser back button steps out of it instead of off the page.
`ProfileTraces.vue:108` already reads `route.query.operation`, so this reuses an existing
convention.

**`TraceOperationDetail.vue`** (new, `components/trace/`) mirrors `SpanTagDetail.vue`:
`DetailBreadcrumb` with root label "Trace Operations", then a `TabBar`:

- **Flamegraphs** — `TraceOperationFlamegraphs.vue`, mirroring `SpanTagFlamegraphs.vue`: panels →
  `FlamegraphCardGrid` → fullscreen `GenericModal` with `TimeSeriesChart` + `FlamegraphComponent`.
  Backed by a new `TraceOperationFlamegraphClient` extending `RemoteFlamegraphClient`.
- **Metrics Timeline** — `TimeSeriesChart` plotting max trace duration and trace count per bucket.
  `SpanTagDetail`'s inline bucketing moves to `services/trace/traceTimelineBuckets.ts` so both pages
  share one implementation and it becomes unit-testable.
- **Slowest Traces** — the existing `TraceSlowestList` unchanged; a row click opens that trace's
  waterfall.

**Deletions.** `ProfileTraces.vue` loses `operationFilter`, the filter chip and the
"Not a root operation" empty state; Slowest Traces returns to one unfiltered list.

**Naming.** The sidebar item in `profileNavConfig.ts` becomes **Trace Operations**. The route name
`profile-trace-operations` and the path `/technologies/traces/operations` are unchanged.

**Already done.** `TraceOperationList.vue` had its `PercentileSpread` rail, p50/p95/max legend and
scoped styles removed so its rows match `SpanTagList.vue`. `@shared/components/PercentileSpread.vue`
now has no callers.

### 6. Error and empty states

| condition | behaviour |
|---|---|
| profile has no traces | existing `TracesDisabledFeatureAlert` / `EmptyState`, unchanged |
| `?operation=` names something absent | `EmptyState` in the detail; breadcrumb still returns to the list |
| operation has no samples in range | `panels` returns empty → `EmptyState`, as `SpanTagFlamegraphs` does |
| trace list hit the cap | a count note in the Slowest Traces tab, not silent truncation |

### 7. Testing

**Backend** (`JdbcTraceRepositoryTest`, `@DuckDBTest`):

- Rewrite "operations aggregate latency by name across traces" to root-name semantics: counts are
  traces, durations are trace durations, `errorCount` counts traces with any erroring span.
- New: a nested-only span name does **not** appear in `operations()` — the regression that
  prompted this work.
- New: `tracesOfOperation` returns only traces with that root name, ordered by start, honouring the
  limit.
- New: `operationIntervals` yields one interval per (trace, thread), and a trace whose spans run on
  two threads yields two.

**Frontend** (Vitest): `traceTimelineBuckets` gets a suite following `TraceWaterfallLayout.test.ts`
— empty input, single trace, bucket boundary, and count/max aggregation.

### 8. Documentation

`jeffrey-pages/src/views/docs/profiles/` — the traces page describes Operations as span-name
aggregation and needs updating to trace types plus the new drill-down.

## Consequences

- The list shrinks from 105 rows to 36 on the reference profile and stops showing where time goes
  inside nested instrumentation. That data is still in the database and still visible in the
  waterfall; it is no longer summarised anywhere. If that summary turns out to be wanted, it is a
  separate page aggregating `trace_spans` by name, not a reversal of this design.
- Header totals change (36/246 rather than 100/834) because they become true rather than truncated.
