# Traces & Spans — deep analysis (2026-08-15)

A correctness and design review of the Distributed Traces feature (`jeffrey.TraceSpan` /
`jeffrey.TraceScope`): the derivation SQL, the `trace_spans`/`traces` schema, `TraceManagerImpl`'s
tree assembly and interval arithmetic, `TracesController`, and the frontend surface
(`ProfileTraces.vue`, `ProfileTraceOperations.vue`, `components/trace/*`,
`ProfileTracesClient.ts`). Every finding below was verified against the tree; the fixes were
applied on this branch and are covered by new tests.

Method Tracing (`jdk.MethodTrace`) and Async-Profiler Spans (`profiler.Span`) are separate
features and were out of scope, except where a shared utility forced care
(`ThreadWindowEvents` is shared with the async-profiler drill-down and its behavior there is
unchanged).

---

## Fixed on this branch

### 1. Operation flamegraphs absorbed unrelated work across idle gaps

`OPERATION_INTERVALS` reduced each `(trace, thread)` to `MIN(from_us)/MAX(to_us)`. A thread that
worked on a trace early, did something unrelated, and came back later handed the operation-scoped
flamegraph one window spanning the whole idle gap — the graph silently included samples from work
that was never part of the operation. The per-span path (`TraceManagerImpl.selfIntervals`) already
preserved gaps; the operation path did not.

**Fix:** a gaps-and-islands pass in SQL (`JdbcTraceRepository.OPERATION_INTERVALS`): overlapping
and touching windows merge, disjoint stints stay separate rows. The reduction still happens in SQL
— the unbounded fetch this query replaced (see `TRACES_LEFTOVERS.md`, closed items) stays closed.
Test: `OperationIntervals.disjointActivityKeepsTheGap` with the new
`insert-disjoint-thread-activity.sql` fixture.

### 2. `trace_spans` had no uniqueness on `(trace_id, span_id)`

`TraceManagerImpl.assemble` documents that a span id identifies exactly one span and that "the
derivation is what guarantees it" — but the derivation didn't. Duplicate rows (re-imported chunk,
id-reusing instrumentation) made `traces.span_count` (a raw `COUNT(*)`) disagree with the waterfall,
which draws only the first row per id.

**Fix:** `DERIVE_TRACE_SPANS` now dedupes with
`QUALIFY ROW_NUMBER() OVER (PARTITION BY trace_id, span_id ORDER BY start_timestamp, duration) = 1`
(earliest occurrence wins — the row the waterfall drew anyway), and `trace_spans` gained
`PRIMARY KEY (trace_id, span_id)` so a future dedupe regression fails loudly instead of skewing
counts silently. `derive()`'s DELETE-then-INSERT idempotency is unaffected (re-verified by
`derivationIsIdempotent`). Test: `Derivation.dedupesRepeatedSpanIds` with
`insert-duplicate-span-events.sql`. The manager's defensive first-row-wins handling stays, as a
render-whatever-the-database-holds guard.

### 3. Dead index `trace_spans_thread_hash_idx`

No query filters or joins *into* `trace_spans` by `thread_hash` — every use is a projection or the
driving side of a probe into `threads` (whose `thread_hash` is the primary key). The index cost
derive-time insert work and file size for zero reads. **Fix:** dropped from `V001__init.sql`.
`trace_spans_trace_id_idx` is kept alongside the new composite PK because every trace read filters
on `trace_id` alone, and a composite ART key is not a reliable prefix-lookup substitute.

### 4. Nondeterministic ordering at LIMIT boundaries

Four queries ordered by a tie-prone key with no tie-break: `SLOWEST_TRACES` (`duration DESC`),
`TRACES_OF_OPERATION` (`start_timestamp`, microsecond-accurate so ties happen),
`OPERATIONS` and `SPAN_BREAKDOWN_OF_OPERATION` (`total_ns DESC`). A tied row at the limit boundary
could come and go between identical requests. **Fix:** deterministic tie-breaks appended —
`trace_id` for the trace lists, the full group key for the aggregates. Test:
`Reads.tiedDurationsOrderDeterministically` with `insert-tied-duration-traces.sql`.

### 5. Silent 5000-row truncation in the span events drill-down

`ThreadWindowEvents.ROW_LIMIT = 5000` capped `eventsInSpan` with no signal anywhere — a busy span's
drill-down presented the first 5000 events as if they were the whole window. **Fix:** the
repository fetches `ROW_LIMIT + 1` and returns a `ThreadWindowEventsPage(events, truncated)`;
`TraceManager.eventsInSpan` returns the new `TraceSpanEvents` record; `TracesController` serializes
it; `ProfileTracesClient.getSpanEvents` types it; `TraceSpansModal.vue` shows a one-line notice
("Showing the first N events…") when the flag is set. The async-profiler drill-down shares
`ThreadWindowEvents` and is behaviorally unchanged (3-arg `params` overload kept). Tests:
`Reads.eventsInSpanExcludeSpans` asserts the untruncated flag;
`TraceManagerImplTest.EventsInSpan.propagatesTruncation` asserts propagation.

### 6. Frontend consistency fixes

- `TraceOperationDetail.vue` — `ErrorState` now wires `@retry="load"` like its sibling views.
  (Note: the shared `ErrorState.vue` currently declares no `retry` emit at all — see report-only
  items.)
- Route names `profile-traces` / `profile-trace-operations` renamed to
  `profile-technologies-traces` / `profile-technologies-traces-operations`, matching every other
  `profile-technologies-*` sibling. Grep confirmed nothing navigates by these names.
- `MODAL_INIT_DELAY_MS = 200` existed as four identical private constants
  (`TraceSpansModal`, `TraceOperationFlamegraphs`, `SpanEventsModal`, `SpanTagFlamegraphs`);
  hoisted to `GraphUpdater.MODAL_INIT_DELAY_MS`, the class whose callback registration the delay
  exists for.
- `ProfileTraces.vue` declared `TRACE_FETCH_LIMIT = 100` "mirroring" the backend default but called
  `getTraces()` without it — the header note would lie if the backend default ever changed. The
  limit is now passed explicitly, making the frontend constant authoritative.

### 7. Precision honesty (comments only)

`traces.duration` is nanosecond-*valued* but only microsecond-*accurate* at its endpoints:
`epoch_ns(start_timestamp)` scales a microsecond-resolution `TIMESTAMPTZ`. The `DERIVE_TRACES`
comment now says so. No code change — the arithmetic was already correct.

---

## Verified sound (no change needed)

- **Derivation design.** Structural span discovery (`spanId` column in `event_types.columns`)
  means third-party instrumentation participates with zero code changes, and the
  `scopedSpanId`-vs-`spanId` naming firewall keeps scopes out of the waterfall. IDs round-trip
  JSON via `json_extract_string(...)::BIGINT` with boundary tests at `Long.MIN/MAX` and negative
  span ids.
- **Tree assembly** (`TraceManagerImpl.assemble`): orphans promoted to roots, self-parents and
  parent cycles survive, depth-first draw order deterministic — all covered by
  `TraceManagerImplTest`.
- **Self-time arithmetic**: same-thread-only child subtraction, overlap merge, clipping to the
  parent window, half-open millisecond cuts so a sample is never counted twice — covered by
  `SelfTime` and `SpanIntervals` tests.
- **Root election, `has_platform_span` three-state thread logic, error counting, event-field
  stripping** — all match their stated invariants and are tested against real DuckDB.
- **Controller layer**: `boundedLimit` caps caller limits at 10,000; hex ids parse via
  `Long.parseUnsignedLong` with 404 on malformed input; framework types stay at the boundary.
- **Frontend architecture**: shared-component reuse, design tokens throughout, extracted and
  vitest-covered pure logic (`TraceWaterfallLayout`, `traceOperationStats`, `timeUnits`, …),
  three-state view pattern, `GenericModal` usage — the trace surface is the most
  convention-compliant area of the app.

---

## Report-only findings (deliberate, or out of scope)

Recorded in `TRACES_LEFTOVERS.md` §4 so they are decisions, not surprises:

1. **Per-request `spansOf(traceId)` re-reads** — `trace()`, `spanIntervals()`, `eventsInSpan()`
   each fetch the trace's spans. They back separate HTTP requests against a small indexed table;
   caching across requests is a real design cost for an unmeasured win.
2. **Scope events joined via JSON extraction** — `OPERATION_INTERVALS` re-parses `fields` for
   `jeffrey.TraceScope` rows. The event-type filter keeps the set small and only gRPC-server
   traffic emits scopes today; a third derived table isn't yet worth its schema.
3. **`ErrorState` has no retry emit** — every `@retry` listener in the app is currently inert; the
   fix is an `@shared` component change outside this feature's scope.
4. **Interval cardinality** — the islands merge returns one row per contiguous busy stretch; a
   pathological operation could inflate the flamegraph filter's `UNNEST` lists. Not worth a cap
   until measured.
5. **Verification gap (pre-existing, still open)** — per `TRACES_LEFTOVERS.md` §3, no screen of
   this feature has been rendered in a browser against a real profile. This needs a running
   instance with a trace-carrying recording; nothing in this analysis substitutes for it.

Out-of-scope observations handed over for a future pass: the Method Tracing surface carries most
of the codebase's convention debt (missing three-state views in its flamegraph page, hardcoded
colors/shadows/fonts, hand-rolled search instead of the `DataTable` toolbar, a
`method|class` vs `BY_METHOD|BY_CLASS` type mismatch in `ProfileMethodTracingClient`, silent
`CumulationMode` fallback, zero frontend tests), and `AsyncProfilerSpansController.slowestSpans`
accepts an unbounded caller `limit` where `TracesController.boundedLimit` exists precisely for
that.
