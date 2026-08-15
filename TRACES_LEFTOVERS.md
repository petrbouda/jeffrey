# Traces & Spans — leftovers

State as of the `claude/tracespan-feature-analysis-65d6c4` branch (with the unified timeline on
`claude/unified-jvm-timeline`, PR #167). Everything below was verified against the tree, not
recalled.

One item remains open. The rest of this file is the record of decisions taken with a stated
trade-off, so they are not rediscovered later as surprises.

---

## Open

### Verification gap

**Nothing has been rendered in a browser against a real profile.** The Maven build, the frontend
build and every test suite pass, and the derivation is exercised end to end against real JFR
fixtures in DuckDB — but no screen of this feature has been looked at by a human against a real
recording. The unified timeline is where this bites hardest: scroll feel, the crosshair, minimap
scrubbing, the wait underlay's legibility at 35% opacity and the density rendering are all
interaction judgements no test can make. This needs a running instance, not a code change.


## Deliberate omissions worth revisiting later

Not bugs — decisions with a stated trade-off.

| Omission | Why | Revisit when |
|---|---|---|
| **No zoom/pan in the waterfall** | DOM/CSS substrate chosen for tens-to-hundreds of spans. Subtree folding, the critical-path filter and error navigation cover the density problem at those sizes, and the unified timeline is now where zoom/pan lives — the waterfall links to it with "Show on timeline" | Real traces exceed a few thousand spans |
| **Timeline density columns are not hoverable** | A bucket is a count, not a span — there is nothing real to open, and a tooltip repeating the count adds little over the opacity. The cap notice names the remedy (zoom in) instead | The count-per-bucket proves to be something readers want to read off exactly |
| **Timeline waits on span-less threads are dropped** | A track exists because work ran on it; a thread that only waited has no track to underlay. The whole-JVM idle picture is the threads timeline's job, and duplicating it here would double the row count for threads this view has nothing to say about | Readers ask where a thread "went" during a window and the answer is a wait on a track this view refused to draw |
| **Timeline states have their own row cap and flag** | `statesTruncated` is separate from `truncated` because the spans being complete says nothing about the waits; both caps are stated in the UI and inside the AI export rather than silently absorbed | A real window regularly exceeds 4,000 wait events and the lower-bound totals start to mislead |
| **The critical path assumes children block their parent** | True by construction on the parent's own thread, and the intended reading of a parent that hands work off and waits. A parent that forked a cross-thread child and never waited sees that child credited time it did not cost — the span tree alone cannot tell the two apart. The per-span context now says what a thread was *waiting* on, which narrows this but does not close it: a parent with no wait recorded was running, not joining | A thread's on-CPU state is derivable per span window, which is what settles it exactly |
| **Context is attributed to the span an event starts in** | An event is charged to the innermost span open on its thread when it began. A wait that straddles a child's start is charged wholly to whichever span it started in rather than split between them — splitting would need the same interval arithmetic self time uses, per category | A straddling wait is seen to matter in practice |
| **Pause overlap is bounded by a look-back** | `MAX_PAUSE_LOOKBACK_MILLIS` floors how far back the query will look for a pause still running at the window's start. Overlap is not sargable on the lower bound, so without a floor every context read scans the events table from the start of the recording | A pause longer than a minute needs to be drawn, which is a JVM in more trouble than this view diagnoses |
| **64-bit trace ids** | Jeffrey mints every id in a single recording | Cross-process assembly or ingesting an external `traceparent` becomes a goal — this is a one-way door, widening is a format change |
| **No threshold or throttle on `TraceSpanEvent`** | How much span volume is acceptable is a property of the application, not of the event; both levers are settable from a JFR settings file without touching code | Span volume proves too high in practice |
| **Pipeline runs are their own traces** | `PipelineRunRegistry` forks to a virtual thread; a background job's lifetime is unrelated to the request | Users find the disconnection confusing in the trace list |
| **`SpanKind` has no `PRODUCER`/`CONSUMER`** | Their purpose is cross-process pairing, impossible single-JVM; no messaging instrumentation exists | Messaging instrumentation arrives — additive, so safe to defer |
| **`HttpClientExchangeEvent` / `GrpcClientExchangeEvent` never emitted here** | The derivation handles them and they are stamped-capable, so a third-party app gets outbound spans; Jeffrey itself makes no instrumented outbound calls | Jeffrey starts making them |
| **Old recordings misread HTTP/gRPC/JDBC status** | `status` → `statusCode` and the removal of `isSuccess` are breaking schema changes; fallbacks were considered and declined in favour of one shape | A user reports it on a recording worth keeping — the fallback is three one-line reads |
| **`TraceSpanInlineDetail` hand-rolls its key/value tables** | `@shared` `DrawerSection`/`InfoRow` fit the identity block but not the two data-driven tables, which need a label-side tooltip and a sub-key | Those shared components grow a richer label slot |
| **Span drill-downs re-read `spansOf(traceId)` per request** | `trace()`, `spanIntervals()` and `eventsInSpan()` each fetch the trace's spans; they back separate HTTP requests against a small indexed table, and caching across requests is a design cost with no measured win | A profiled slow drill-down names this read as the cost |
| **Scope events are joined via JSON extraction per query** | `OPERATION_INTERVALS` re-parses `fields` for `jeffrey.TraceScope` rows; the event-type filter keeps that set small, and only re-entered spans (gRPC server traffic today) emit scopes at all. A third derived table was judged more schema than the join is worth | Scope-emitting instrumentation broadens beyond gRPC |
| **Operation intervals are unbounded per busy stretch** | The gaps-and-islands merge returns one row per contiguous busy stretch per `(trace, thread)` rather than one per pair; a pathological operation of thousands of alternating disjoint spans would inflate the `UNNEST` list parameters behind the span-scoped flamegraph filter | A hot operation's flamegraph request measurably slows on the interval lists |

---

## Closed on this branch

- ~~`vue-tsc` could not gate the build~~ — the app-wide backlog (219 errors when the tool last
  genuinely ran) is fixed to zero, `npm run build` is now `vue-tsc --noEmit && vite build`
  (`build:only` skips the gate deliberately), and the Maven build installs dev dependencies
  explicitly so a production-flavoured environment cannot silently prune the checker — the exact
  failure mode that once made the check vacuous. The burn-down surfaced two real runtime bugs:
  `hubId` was read by the live/replay stream templates but never declared (the session picker
  could never render), and a skipped constructor argument in the secondary-profile modal stored a
  profile's duration as its `modified` flag and its size as its duration.
- ~~`@retry` listeners on `ErrorState` were inert~~ — the shared `ErrorState.vue` now declares the
  `retry` emit and renders a "Try again" button whenever a listener is actually wired
  (`useAttrs().onRetry`), so every error state in the trace views became recoverable at once and a
  call site with no listener shows no dead button.
- ~~Nothing said what the JVM was doing *around* a trace~~ — the unified timeline (PR #167) draws
  every thread on one axis: spans per thread, pinned pause tracks, a per-thread wait underlay in
  the same `TraceContextCategory` colours as the waterfall's stripes, density columns instead of a
  biased sample when a window exceeds the span cap, keyboard and deep-link (`?from&to`, `?trace=`)
  support, and its own AI export whose preamble states the cross-section rules.
- ~~The trace and operation views were islands~~ — why-slow findings link to the views that explain
  them (GC → Garbage Collection, locks → Blocking Operations, I/O → the I/O views), the waterfall
  links to its operation and the operation back to a filtered trace list (`TraceListQuery` gained
  the operation triple), and the slowest HTTP/JDBC/gRPC/span rows all link "Show on timeline".
- ~~Self time is computed per read, and the operation breakdown cannot have it~~ — `trace_spans`
  carries a derived `self_duration`, filled once at derivation by the same gaps-and-islands merge
  `OPERATION_INTERVALS` uses. The Java computation is gone, so there is one definition of "self"
  rather than two that could drift, and `SPAN_BREAKDOWN_OF_OPERATION` can now aggregate it — the
  "not expressible as a group-by" note was true of a group-by and not of the merge. The Top-spans
  card toggles between inclusive and self, and the two rankings disagree, which is the point: a span
  that only wraps three slow queries tops one list and barely registers on the other.
- ~~Nothing says *why* a span was slow~~ — `GET /traces/{traceId}/context` returns the stop-the-world
  pauses that crossed the trace, what each span's thread waited on, and a ranked summary whose
  remainder is named as the code's own work. GC pauses and safepoints are emitted on VM threads, so
  they needed a genuinely new kind of query: thread-agnostic, and matching on *overlap* rather than
  on starting inside the window — a 40 ms collection that began 5 ms before a span is exactly the one
  that explains it, and the existing starts-inside predicate missed it entirely.
- ~~Event durations were drawn as instants~~ — the span drill-down's markers are as wide as the event
  lasted, so a 90 ms lock wait no longer looks like the same 3 px tick as a 2 µs allocation.

- ~~Critical path not computed~~ — `TraceManagerImpl` walks it backwards from the end of the trace,
  attributing each stretch to whatever was holding it open, and every span carries its own share as
  `criticalPathNanos`. The waterfall marks the rows on it, the inline detail says how much of the
  trace each one decided, and a filter narrows to them. Children are read in the order they
  *finished*, not the order they started: a child that began earlier can finish later, and reading by
  start credits the shorter sibling while skipping the one that actually held the window open.
- ~~No search, filter or sorting anywhere in the trace views~~ — the trace and operation lists narrow,
  sort and page on the server. Client-side was considered and declined: both lists are capped, so
  filtering the fetched page only ever searches the slowest few hundred and a search for anything
  outside them comes back empty while the row sits in the table. Both endpoints return the count the
  filter matched, which is what makes "load more" possible and what lets a capped list say it is one.
- ~~P99 shown only when the trace sample is complete~~ — `OPERATIONS` aggregates it, so an operation's
  p50, p95 and p99 all describe the same population and the summary no longer prints "—" for it.
- ~~No time axis on the trace list~~ — `GET /traces/timeline` buckets the traces over the recording,
  aggregated in SQL rather than from the fetched rows, which would have drawn the density of *slow*
  traces and labelled it the density of traces.
- ~~A waterfall reached through an operation was not linkable~~ — `TraceOperationDetail` writes
  `?trace=` the way the trace list already did. Async-profiler tag selection moved into `?tag=` too.
- ~~No way through a large waterfall but scrolling~~ — subtrees fold, and arrow keys walk the rows.

- ~~`Tracer` cannot re-enter an existing span~~ — `Tracer.openSpanOf` opens a span without binding
  and `Tracer.reenter` resumes that same span (not a child) around any block, so a callback-driven
  protocol nests its work. `JfrGrpcServerInterceptor` wraps every listener callback and forwarded
  call method; for a unary call `onHalfClose` is the one that carries the handler. This was never
  actually blocked on a release: the reactor builds `utilities/jeffrey-events` from source
  (`<jeffrey-events.version>1.0-SNAPSHOT</jeffrey-events.version>`), so a release is only needed to
  hand the API to third-party consumers.
- ~~Cross-thread spans are misattributed~~ — solved the way OpenTelemetry's `jfr-events` contrib
  solves it, with a second event type rather than a start-thread field. Each `reenter` emits a
  `jeffrey.TraceScope` (`traceId` + `scopedSpanId`), bounded by one lambda and therefore
  thread-confined by construction, and `operationIntervals` unions those windows with the spans'
  own. A start-thread field was considered and declined: it fixes the label while leaving the
  drill-down window pointing at a thread the work never ran on. The field is deliberately
  `scopedSpanId`, not `spanId`, so the structural span discovery cannot mistake a scope for a span.
  Spans that are never re-entered emit no scopes and are unaffected.
- ~~Operations aggregated by span name~~ — they are grouped from `traces` by the whole trace type
  (root name + kind + event type), so an inbound and an outbound call of the same name stay apart.
- ~~Stamped events shared their enclosing span's id~~ — `Tracer.stamp` now mints a child per event,
  in the JVM, so the derivation is a flat projection with nothing to work out.
- ~~Span shape interpreted in SQL~~ — each event describes itself via `describeSpan()`/`commitSpan()`;
  the derivation names no event type at all, and discovers span types from JFR metadata.
- ~~The trace detail recomputed its own header~~ — it reads the stored `traces` row, so a trace's
  duration is the same number in the list and in the detail.
- ~~`has_platform_span` computed per query, two ways~~ — one stored column, one convention: a span
  whose thread did not resolve is not counted as platform.
- ~~`spansOfOperation` unbounded~~ — replaced by an interval aggregate, later refined: windows are
  merged per `(trace, thread)` with idle gaps preserved (gaps-and-islands), because a plain MIN/MAX
  per pair handed the operation flamegraph one window spanning a thread's idle gap and absorbed
  whatever unrelated work ran in it.
- ~~`derive()` not idempotent~~ — both tables are cleared first, and a test runs it twice.
- ~~Every virtual thread collapsed into one~~ — `EventThreadCleaner` no longer groups on a null
  `os_id`, which is what `trace_spans.thread_hash` depends on.
