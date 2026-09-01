# Traces — feature analysis (2026-09-01)

A complete review of the Distributed Traces feature in Jeffrey Microscope: the `Tracer` API and
agent instrumentation, the derivation pipeline (`JdbcTraceRepository` / `JdbcTraceAttributeRepository`),
the `traces`/`trace_spans`/`trace_*` schema, `TraceManagerImpl`, `TracesController` /
`TraceAttributesController`, the four trace views, `components/trace/*`, and the docs site
(`jeffrey-pages/src/views/docs/tracing/`).

Every finding carries file:line references and was verified against the current tree. The
fixes for §1.1–§1.8, §1.10, §2.2, §3.1, §3.2 and §4.1 were subsequently applied on this branch —
they are marked **[FIXED]** below; everything else remains report-only. It picks up where
`TRACES_ANALYSIS.md` (2026-08-15) left off: the findings fixed there are not repeated, and where
that document has gone stale it is called out (§2.1).

Scope note: "Traces" here means distributed traces — `jeffrey.TraceSpan` / `jeffrey.TraceScope`,
any event type declaring a `spanId` column, promoted `jdk.*` blocking events and `jdk.MethodTrace`
wrappers. Async-Profiler Spans (`profiler.Span`) and the standalone Method Tracing view are
separate features and are mentioned only where a bug lives in shared or adjacent code.

---

## What is healthy

Worth stating first, because the core holds up well:

- The derivation SQL is unusually well documented in-source; the 15-CTE `DERIVE_TRACE_SPANS`
  (anchoring, minting, adoption, gaps-and-islands self-time) reads as designed, not accreted.
- Null handling is sound by construction: `SpanConventions` projections always terminate in a
  literal fallback (`'INTERNAL'`, `'UNSET'`, `event_type`), `escaped` guards `error_type IS NOT
  NULL` before comparing, and `EVENTS_IN_SPAN` uses `NOT EXISTS` rather than `NOT IN`.
- Time handling is TZ-safe throughout (`EPOCH_US`/`make_timestamptz`, all UTC-anchored); no
  `Instant.now()` / `System.currentTimeMillis()` anywhere in trace code; frontend has zero
  `new Date()` in trace code and routes everything through `FormattingService` and
  `services/trace/timeUnits.ts`.
- The `Tracer` API is immutable + `ScopedValue`-based, safe for concurrent use, and zero-cost when
  the event is disabled. `TracedRuntime` / `TracedRuntimeBinding` cache via `ClassValue` so entries
  die with their class loader.
- The pure frontend logic layer (`services/trace/*`, 10 modules) has 9 matching spec files.
- The three-state view pattern, shared components, `Badge`, `GenericModal`, and the DataTable
  family are used correctly almost everywhere.
- There are zero `TODO`/`FIXME`/`XXX`/`HACK` markers in the entire trace surface.

The problems cluster at the edges: stranded backend surface, a handful of concrete frontend bugs,
semantic surprises in attribute search, and consistency gaps between sibling code paths.

---

## 1. Confirmed bugs

### 1.1 Broken router-link to the operation page (user-visible) — [FIXED]

`jeffrey-microscope/pages-microscope/src/components/trace/TraceSpansModal.vue:401` builds the
"All *{rootName}* traces" link with:

```ts
name: 'profile-technologies-traces-operations',
```

No such route exists; the registered name is `profile-traces-operations`
(`src/router/profileChildRoutes.ts:694`). Vue Router 4 throws on an unresolvable named location
during `RouterLink` resolution, so the "one trace → all traces of its operation" edge is dead
everywhere the modal is opened with `with-operation-link` (i.e. from Search Traces). Almost
certainly a leftover from the rename when Traces was promoted out of `technologies/`.

### 1.2 `SpanEventsModal` renders fetch errors as "no events" — [FIXED]

`src/components/span/SpanEventsModal.vue:218-230` (async-profiler span feature, adjacent surface):
the `catch` logs to console and sets `events.value = []`. There is no `error` ref and no
`ErrorState` in the template, so a failed request renders the `EmptyState` "No events — No JFR
events were recorded on this thread during the span window" — asserting a fact about the recording
that was never established. Direct violation of the three-state rule, and inconsistent with
`TraceSpansModal.loadEvents()` (lines 428-449), which does exactly this correctly with
`eventsError`. Affects `SpanTagDetail.vue:43` and `ProfileAsyncProfilerSlowestSpans.vue:39`.

### 1.3 Four dead-end `ErrorState`s where a retry is already available — [FIXED]

`ErrorState.vue` renders its retry button only when a `@retry` listener is attached. These omit it:

- `components/trace/TraceOperationFlamegraphs.vue:24`
- `components/trace/TraceSpanFlamegraphs.vue:59`
- `components/span/SpanTagFlamegraphs.vue:24`
- `components/trace/TraceOperationSummary.vue:108`

The first three destructure `useFlamegraphPanels(...)` and ignore the `reload` it already returns;
`TraceOperationSummary` has a local `load()`. `TRACES_ANALYSIS.md` records fixing precisely this
class of bug in `TraceOperationDetail.vue` — these four were missed.

### 1.4 Out-of-order response races — [FIXED]

- `ProfileTraceAttributeSearch.vue:228-257` has no generation counter, unlike its siblings
  `ProfileTraceAttributeValues.vue:86` and `ProfileTraceAttributeLatency.vue:74` (both explicitly
  documented as guarding against out-of-order responses) and `TraceOperationDetail.vue:279`.
  Changing conditions twice in quick succession can land the slower earlier response on top of the
  newer one.
- `ProfileTraceOperations.loadPage()` (line 273): a `loadMore` (offset > 0) in flight when a
  filter change fires `loadPage(0)` will *append* stale-filter rows onto the fresh list
  (`operations.value = [...operations.value, ...page.operations]`).

### 1.5 Stale results shown with no loading affordance during a re-search — [FIXED]

`ProfileTraceAttributeSearch.vue:51` gates the spinner on `searchLoading && search === null`. Once
a first result exists, every subsequent condition change leaves the previous matches, stats and
timeline fully rendered with no indication they belong to the old query
(`:loading-more="searchLoading"` only drives the `LoadMoreFooter`). A slow query silently displays
the wrong answer.

### 1.6 `derive()` is not transactional — [FIXED]

`JdbcTraceRepository.derive()`
(`profiles/profile-sql-persistence/.../jdbc/JdbcTraceRepository.java:1743-1819`) issues 6 DELETEs
and 5 INSERTs as independent statements, even though `DatabaseClient` exposes `inTransaction`
(`shared/persistence/.../DatabaseClient.java:124-129`). A failure between `DERIVE_TRACE_SPANS`
and `DERIVE_TRACES` leaves `trace_spans` populated and `traces` empty — `hasTraces()` returns
false, `TracesFeatureChecker` disables the section, and the orphan spans stay in the profile file.
Same for `JdbcTraceAttributeRepository.derive()` (:727-748). Re-running is idempotent (the
DELETE-first design), so the fix is cheap: wrap each `derive()` in `inTransaction`.

### 1.7 Exception-window arithmetic disagrees with every other window — [FIXED]

- `DERIVE_TRACE_SPANS` / `SPAN_CONTEXT` / `OPERATION_INTERVALS` use **integer** division and an
  **inclusive** upper bound: `c.start_us <= EPOCH_US(s.start_timestamp) + s.duration // 1000`
  (`JdbcTraceRepository.java:492`, `:1721`, `:1226`).
- `DERIVE_TRACE_EXCEPTIONS` uses **float** division and an **exclusive** bound:
  `x.start_us < EPOCH_US(s.start_timestamp) + (s.duration / 1000)` (`:1046`). In DuckDB, `/` on
  integers yields DOUBLE, so a 1500 ns span spans 1.5 µs here but 1 µs everywhere else.

A span's window can differ by up to 1 µs between exception attribution and every other path, and
the boundary instant is included in one and excluded in the other. Exceptions near span edges can
attach to a different span than the notifications/context bands beside them.

### 1.8 `NOT_EQ` is existential, not universal — [FIXED by adding `NONE_EQ`]

`TraceAttributeOperator.NOT_EQ` renders `value_text <> :x`, wrapped as
`value_id IN (SELECT … WHERE value_text <> :x)` (`TraceAttributeQueries.java:144-150`), and the
per-condition branch predicate is `COUNT(*) FILTER (WHERE <predicate>) > 0` (`:191-193`). So
`status != ERROR` means *"this trace has **some** span whose status isn't ERROR"* — true of
essentially every failing trace. The intuitive reading ("no span has status ERROR") is not what
executes. See §5.2 for the suggested resolution.

### 1.9 `CONTAINS` does not escape LIKE metacharacters

`TraceAttributeOperator.java:31`:
`CONTAINS("lower(value_text) LIKE '%%' || lower(:%s) || '%%'")`. A user-supplied `%` or `_` acts
as a wildcard. Not SQL injection (the value is bound), but a silently wrong result for values
containing those characters (URLs with encoded sequences, SQL statements as attribute values).

### 1.10 `threadHash` serialized in two different bases — [FIXED]

- `TraceSpanRow.threadHash` = `Long.toString(span.threadHash())` → **decimal**
  (`TraceManagerImpl.java:821`)
- `TraceNotificationRow.threadHash` and `TraceExceptionRow.threadHash` = `toHex(...)` → **hex**
  (`:848`, `:886`)

All three are `threadHash: string` in `TraceModels.ts` (:82, :173, :201). Nothing cross-compares
them today (verified by grep), so it is latent — but any future "which notifications fired on this
span's thread" check will silently match nothing.

### 1.11 Hand-rolled unit conversion bypasses `timeUnits.ts`

`TraceWaterfall.vue:2027` and `TraceSpanInlineDetail.vue:175` hand-roll `Math.floor(x / 1000)`
(micros→millis) while `services/trace/timeUnits.ts` exports `MICROS_PER_MILLI` and
`floorToMillis()` — and that module's own header explains it exists because the factors "had been
redeclared in every file that needed it, which is how a start came to be floored in one place and
rounded in another". `TraceSpanInlineDetail` already imports `NANOS_PER_MICRO` from it.

### 1.12 Silent truncation without a signal in the context queries

`ThreadWindowEvents.ROW_LIMIT = 5000` is surfaced as `TraceSpanEvents.truncated` for the span
drill-down — but **not** for `pausesInWindow` (`JdbcTraceRepository.java:2147`) or
`throttledWindowsIn` (`:2174`), which bind the same cap with no truncation flag. A trace crossing
5000+ GC/safepoint events silently loses pause/throttle bands. Related, documented-but-silent
caps: `MAX_PAUSE_LOOKBACK_MILLIS = 60_000` (`:1540`) and `MAX_THROTTLE_SAMPLE_GAP_MILLIS =
120_000` (`:1600`).

### 1.13 Hash-collision failure modes

- `trace_span_payloads.payload_id`, `trace_notification_messages.message_id` and
  `trace_attribute_values.value_id` are `mod(hash(text), 2^63-1)` used as **primary keys**. A
  collision between two distinct texts fails the INSERT and therefore **the entire profile
  initialization**. The schema comment (`V001__init.sql:598-599`) says this is intentional ("fails
  the primary key loudly") — but the blast radius is the whole profile, not one row.
- Minted span ids (`1 + mod(hash(...), 2^63-2)`, `JdbcTraceRepository.java:505-508`) are guarded
  against colliding with *recorded* ids (`NOT EXISTS`, `:576-578`), but two minted ids colliding
  is resolved by `QUALIFY … = 1` (`:579-580`) — **one synthesized span is silently dropped**, and
  any child parented to the dropped id becomes an orphan (recovered as a root by `assemble`, so
  the waterfall shows a detached subtree with no diagnostic).

---

## 2. Dead / stranded surface

### 2.1 The profile-wide trace list has no UI (and `TRACES_ANALYSIS.md` is stale on it) — [MOCKUP DELIVERED, DECISION PENDING]

`GET /api/internal/profiles/{id}/traces` (`TracesController.java:125-151` — a full `TracesPage`
with `search`, `errorsOnly`, `minDurationNanos`, the operation triple, sort, desc, limit, offset)
and `GET /traces/timeline` (`:171-178`, whose javadoc says "for the density strip above the list")
have **no caller anywhere** — frontend, IntelliJ plugin, MCP or AI export. `ProfileTracesClient.ts`
has no `getTraces()` and no `/timeline` call; `TraceModels.ts` has no `TracesPage` /
`TraceSortField`. The page that consumed them (`ProfileTraces.vue`, referenced throughout
`TRACES_ANALYSIS.md`) no longer exists — `views/profiles/detail/traces/` contains only the
operations and attributes views. Consequently the whole `TraceListQuery` → `TRACE_LIST` /
`COUNT_TRACES` / `TIMELINE` path is dead in production, and `TraceListQuery.slowest(int)`
(`TraceListQuery.java:68`) has no production caller at all.

Either ship the page (§5.1) or delete the endpoints deliberately — today it is tested, maintained,
unserved surface.

### 2.2 Operation-level AI export is implemented but unreachable — [FIXED]

`TraceAiExportClient.generateOperation()` exists, `GET /operation/ai-export` exists
(`TracesController.java:277`), and the docs assert it: *"Both the trace waterfall and an
operation's drill-down carry an export button"*
(`jeffrey-pages/src/views/docs/tracing/TracingAnalysisPage.vue:224`). But `AiExportButton` appears
only in `TraceSpansModal.vue:47` and `FlamegraphComponent.vue:354` — the operation drill-down has
no export button. The docs are wrong and a finished feature is stranded.

### 2.3 Dead query parameters in the client contract

- `AttributeSearchQuery.sort` / `.desc` (`ProfileTracesClient.ts:271-278`) are never populated —
  search results have no user-controllable ordering.
- `TraceOperationListQuery.desc` is never sent.
- `TraceOperationSortField` includes `P50` and `NAME`, which the UI's `TraceOperationSortKey`
  (`TraceOperationList.vue:137-140`) excludes — operations cannot be sorted by name or median.

---

## 3. UX and feature gaps

### 3.1 Slowest Traces tab fetches 1000, shows 50 — [FIXED]

`TraceOperationDetail.vue:74-83` passes up to 1000 traces into `TraceCardList` without
`max-displayed`, so the component default of 50 (`TraceCardList.vue:174`) applies, and there is no
`LoadMoreFooter` on that tab. `TraceAttributeResults.vue:72` deliberately passes
`:max-displayed="matches.length"` to defeat the same cap, which shows the omission is
unintentional. Rows 51-1000 are fetched and thrown away.

### 3.2 Attribute Values grid: sort direction and paging — [SORT DIRECTION FIXED; offset paging still open]

`ProfileTraceAttributeValues.vue:38` hardcodes `:descending="true"`; `applySort` sets only the
field; `getAttributeValues()` has no `desc` parameter. "Which value is *fastest*" and A→Z on
`VALUE` are unaskable, and clicking an active column re-issues the identical request.
`TraceAttributeValueQuery` has a limit but no offset — the grid cannot page past truncation.

### 3.3 Unbounded attribute metadata queries

`KEYS` (`JdbcTraceAttributeRepository.java:510-514`), `KEYS_OF_EVENT_TYPE` (`:483-500`) and
`ATTRIBUTE_EVENT_TYPES` (`:438-471`) carry no `LIMIT`, and `/attributes/keys`,
`/attributes/event-types`, `/attributes/values`, `/attributes/latency` return whole result sets.
Bounded only by distinct-key cardinality — which, with `message` deliberately indexed (`:88-91`),
is not obviously small for chatty recordings.

### 3.4 Attribute search expressiveness

- Conditions are AND-only — no OR, no grouping (`TraceAttributeSearchBar.vue`).
- A condition cannot be edited in place — only removed and rebuilt.
- No duration-range condition (see §5.3 — needed for heatmap drill-through too).
- `v-for … :key="index"` over the mutable conditions list (`:45`).

### 3.5 The two-step attribute picker has no filter

`TraceAttributeStepPicker.vue:55-73`, `:116-134` render every event type and every key as an
unfiltered, unpaginated button list in a popover — a scroll hunt on profiles with many event
types, and inconsistent with `@shared/components/form/` which offers `SearchInput` and
`SearchableSelect`.

### 3.6 Attribute timeline lacks the bucket grid its neighbor has

`JdbcTraceRepository.TIMELINE_TEMPLATE` generates a `grid` CTE and LEFT JOINs so empty buckets
come back as zeros, with a long comment on why (`:1356-1361`), plus a `from_ms IS NOT NULL` guard
(`:1384`). `JdbcTraceAttributeRepository.TIMELINE` (`:590-615`) has neither — buckets containing
no trace are simply absent, and there is no null-bounds guard. The two strips are drawn
side-by-side in the UI. Also, `COUNT(*) FILTER (WHERE t.trace_id IN (SELECT trace_id FROM
matches))` (`:610`) re-evaluates CTE membership per row rather than joining.

### 3.7 Debounce swallows button clicks

`ProfileTraceOperations.vue:293-297` shares one 250 ms-debounced watcher across
`[search, errorsOnly, sortKey]`, so "Errors only" and sort clicks have a quarter-second dead zone.
The comment acknowledges it as a simplification; splitting immediate (clicks) from debounced
(typing) is cheap.

### 3.8 Pagination ceiling behaves silently

`boundedOffset` clamps offset to `MAX_LIMIT = 10_000` (`TracesController.java:505-507`,
`TraceAttributesController.java:273-275`). With the 50-row attribute-search page only the first
200 pages are reachable, and beyond the clamp the API silently returns the same page rather than
erroring. `GET /operation/traces` (`:216-227`) returns a bare list with no total and no offset.

### 3.9 Accessibility

- `TraceWaterfall.vue:694`: span rows are `<button tabindex="-1">` — removed from tab order.
- `TraceWaterfall.vue:615-621`: `<span role="button">` with no `tabindex`/key handler.
- `TraceAttributeValues.vue`: clickable `<tr @click>` without `role`/`tabindex`/key handler —
  whereas `TraceCardList.vue:61-68` does this correctly (`role="button"`, `tabindex="0"`,
  Enter/Space).

### 3.10 Repeated `spansOf(traceId)` per request

`TraceManagerImpl.trace()` (`:161`), `context()` (`:212`), `spanIntervals()` (`:369`) and
`spanOf()`/`eventsInSpan()` (`:483`) each re-fetch the whole span list — a single trace-detail
screen issues at least 4 such reads. `context()` fetches `spansOf` purely to compute a min/max
window that `traces.start_timestamp`/`duration` almost provides. Similarly,
`TraceManagerImpl.operation()` (`:418-433`) runs a full grouped scan (up to
`OPERATION_LOOKUP_LIMIT = 10_000` rows) to find one `(name, kind, eventType)` row, on the
AI-export path.

### 3.11 Adjacent: async-profiler spans feature has unbounded reads

Out of scope but adjacent and still open (also noted in `TRACES_ANALYSIS.md`):
`JdbcSpanRepository.LIST_SPANS` (`:47-62`) has no `LIMIT`; `SpanManagerImpl` calls `listSpans()`
five times (`:55, :79, :110, :122, :133`) and filters in Java;
`AsyncProfilerSpansController.slowestSpans` passes the caller's `limit` through unbounded
(`:73-79`); `tagSpans` has no limit parameter at all.

---

## 4. Convention and test debt

### 4.1 Design-token violations — [FIXED]

Fix note: the waterfall's inset hover rules and glyph rings (`box-shadow: inset 0 1px 0 …`,
`0 0 0 2px …`) are drawn separators over token colors, not elevation shadows — they stay as
written, the same judgment as a `border-radius: 0` reset. The hairline 1px glyph radii derive
from the token scale as `calc(var(--radius-xs) / 2)`, since no 1px token exists and
`--radius-xs` (2px) visibly rounds a 6px glyph.

- `TraceAttributeResults.vue:43-44`: hardcoded `primary-color="#5e64ff"` /
  `secondary-color="#b6c1d2"` — literally the values of `--color-primary` and
  `--color-text-light`. Worse: `TraceOperationDetail.vue:50-60` renders the *same*
  `TimeSeriesChart` with no color props, falling back to `ChartColors.chartColor('primary')` =
  `#2e93fa` — two "when did traces happen" charts, two palettes.
- `TraceStackTrace.vue:290`: `color: #475569` — not any token value.
- `TraceWaterfall.vue:2582, 2602, 2635, 2656, 2719, 3089, 3139`: literal `border-radius` values;
  `:2509`: literal multi-line `box-shadow` — instead of `var(--radius-*)` / `var(--shadow-*)`.
- Minor: `TraceAttributeValues.vue:56` inline `style="width: 160px"` on a `<th>` whose sortable
  siblings use the `SortableTableHeader` `width` prop; `TraceWaterfall.vue:623` inline
  `style="height: 3px"` ×4.

### 4.2 `TraceWaterfall.vue` is 3,270 lines

893 template / 1,122 script / 1,236 scoped CSS in one component — against the project's own
"focused single-responsibility collaborators" rule. The lane rendering, the two instant rails
(notifications, exceptions), the docked detail strips, and the run-collapsing of repeated leaf
siblings are four separable components. The hand-rolled `sd-table` key/value tables in
`TraceWaterfall` / `TraceSpanInlineDetail` / `TraceSpansModal` are defensible (2-column detail
tables, not data grids) but are exactly where ~1,200 lines of bespoke scoped CSS accumulated.

### 4.3 Test coverage is inverted at the boundaries

- `JdbcTraceRepositoryTest` is 2,921 lines and `TraceManagerImplTest` 692 — the core is well
  covered.
- `TracesControllerTest` is 142 lines against a 520-line controller with 17 endpoints.
- There is **no `TraceAttributesControllerTest`** — the hand-rolled `~`-separated condition parser
  (`TraceAttributesController.java:239-256`), the only bespoke wire format in the trace surface,
  is untested at the controller level.
- There are **zero frontend component/view tests** for traces (all 9 spec files cover
  `services/trace/*`) — which is how the broken route name (§1.1) survived. A single mount test of
  `TraceSpansModal` with router would have caught it.

---

## 5. Enhancement ideas

1. **Ship the profile-wide trace list.** The backend (§2.1) is implemented, tested and unserved: a
   page with the density-strip timeline on top and a sortable/searchable list (slowest, errors
   only, min-duration) closes the "show me the 50 slowest traces in this profile" gap. If the
   product decision is that traces are only reachable through operations and attribute search,
   delete the endpoints instead — either way the current state is the worst of both.

2. **A universal negative operator.** Keep `NOT_EQ` existential if desired, but add `NONE_EQ`
   ("no span in the trace has this value") — that is the query users actually mean by
   `status != ERROR` (§1.8). SQL-wise it is the same branch with `COUNT(*) FILTER (…) = 0`.

3. **Latency heatmap → search drill-through.** Clicking a cell in Latency by Attributes should
   open Search Traces pre-filtered to that attribute value + that duration bucket. Requires a
   duration-range condition in the search model — which also fills the search-expressiveness gap
   (§3.4) on its own.

4. **W3C `traceparent` / OTLP interop.** Trace/span ids are private random 64-bit longs; nothing
   propagates across process boundaries in a standard format. Accepting and emitting `traceparent`
   in the HTTP/gRPC instrumentation (`jeffrey-tracing-servlet`, `-spring`, `-grpc`) would let
   Jeffrey spans join traces from OTel-instrumented services, and an OTLP export of the derived
   `trace_spans` would let Jaeger/Tempo-class tools consume Jeffrey traces. The `otlp-parser`
   module already exists on the import side.

5. **Cross-profile trace stitching.** Once ids propagate (idea 4), the hub can correlate the same
   trace id across two profiles (client JVM + server JVM) and render one waterfall spanning both —
   a genuinely differentiating feature for a profiler-first tool, since each half carries full JFR
   context (GC pauses, blocking, flamegraphs) that APM traces lack.

6. **Trace and operation comparison.** Diff two traces of one operation span-by-span (aligned by
   span name/structure), or one operation across two profiles — modeled on the differential
   flamegraphs Jeffrey already has. "Why is P99 worse in this build" is the question this answers.

7. **"Why slow" against a baseline.** Extend `TraceWhySlowPanel` to compare the selected trace's
   per-span-name self-times against the operation's P50 for the same span names, highlighting the
   divergent span instead of only decomposing the single trace.

8. **Waterfall virtualization.** The docs honestly cap the waterfall at "tens to hundreds of
   spans" — virtual scrolling of the row list would lift that to large fan-out traces. This pairs
   naturally with splitting `TraceWaterfall.vue` (§4.2), since a virtualized row list wants to be
   its own component anyway.

9. **Transactional, and eventually incremental, derivation.** Short term: wrap both `derive()`
   bodies in `DatabaseClient.inTransaction` (§1.6). Longer term: derive per-chunk/appended-window
   instead of DELETE-all + re-INSERT, which is the prerequisite for traces over streamed/live
   recordings from the hub.

10. **AI deep-link.** The trace and operation markdown exports are thorough; feed the operation
    export of the slowest N operations into the Profile Advisor automatically so recommendations
    can cite specific traces, rather than requiring the user to carry the markdown over by hand.

---

## Suggested fix priority (when fixes are requested)

1. ~~§1.1 route name (one line, user-visible break) · §1.2 error-as-empty-state · §1.3 four
   `@retry` wires~~ — **done on this branch**.
2. ~~§1.4/§1.5 race guards + loading affordance in attribute search; guard the `loadPage`
   append~~ — **done on this branch**.
3. ~~§1.6 transactional `derive()`~~ — **done on this branch**.
4. ~~§2.2 operation AI-export button~~ — **done on this branch**; §2.1 trace list: interactive
   mockup delivered, build/delete decision pending.
5. ~~§1.7 unify window arithmetic · §1.8 add `NONE_EQ` · §1.10 unify `threadHash` base~~ —
   **done on this branch**.
6. ~~§3.1 slowest-traces pagination · §3.2 sort direction end-to-end · §4.1 token cleanup~~ —
   **done on this branch**.
7. §4.3 controller + component tests (a `TraceAttributesControllerTest` for the `~` parser and a
   mount test of `TraceSpansModal` first — each would have caught a bug in this report).
