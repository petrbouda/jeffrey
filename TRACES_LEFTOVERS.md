# Traces & Spans — leftovers

State as of the `feature/trace-operations` branch. Everything below was verified against the tree,
not recalled.

One item remains open. The rest of this file is the record of decisions taken with a stated
trade-off, so they are not rediscovered later as surprises.

---

## 3. Verification gap

**Nothing has been rendered in a browser against a real profile.** The Maven build, the frontend
build and both test suites pass, and the derivation is exercised end to end against real JFR
fixtures in DuckDB — but no screen of this feature has been looked at by a human against a real
recording. This needs a running instance, not a code change.

## 4. Deliberate omissions worth revisiting later

Not bugs — decisions with a stated trade-off.

| Omission | Why | Revisit when |
|---|---|---|
| **No zoom/pan in the waterfall** | DOM/CSS substrate chosen for tens-to-hundreds of spans | Real traces exceed a few thousand spans |
| **Critical path not computed** | Deferred | Traces get deep enough that the longest chain isn't obvious |
| **64-bit trace ids** | Jeffrey mints every id in a single recording | Cross-process assembly or ingesting an external `traceparent` becomes a goal — this is a one-way door, widening is a format change |
| **No threshold or throttle on `TraceSpanEvent`** | How much span volume is acceptable is a property of the application, not of the event; both levers are settable from a JFR settings file without touching code | Span volume proves too high in practice |
| **Pipeline runs are their own traces** | `PipelineRunRegistry` forks to a virtual thread; a background job's lifetime is unrelated to the request | Users find the disconnection confusing in the trace list |
| **`SpanKind` has no `PRODUCER`/`CONSUMER`** | Their purpose is cross-process pairing, impossible single-JVM; no messaging instrumentation exists | Messaging instrumentation arrives — additive, so safe to defer |
| **`HttpClientExchangeEvent` / `GrpcClientExchangeEvent` never emitted here** | The derivation handles them and they are stamped-capable, so a third-party app gets outbound spans; Jeffrey itself makes no instrumented outbound calls | Jeffrey starts making them |
| **An old recording's exchange loses its response code from the span detail** | The span shape is stripped out of `event_fields` as plumbing, and in the older shape the response code *is* the `status` key that gets stripped; the outcome it implies survives as the span status | Someone needs to read the code, not just the outcome, off a legacy recording |
| **An old recording's statements share their parent's span id** | Before `Tracer.stamp` minted an id in the JVM, a stamped event carried the enclosing span's; the derivation used to invent one, and re-adding that is a different concern from the span shape | Waterfalls of legacy recordings prove unreadable |
| **P99 shown only when the trace sample is complete** | There is no server-side p99 column, and a p99 over the first 1,000 traces beside a p95 over all of them is two different questions in one row | A p99 aggregate is added to the `OPERATIONS` query |
| **`TraceSpanInlineDetail` hand-rolls its key/value tables** | `@shared` `DrawerSection`/`InfoRow` fit the identity block but not the two data-driven tables, which need a label-side tooltip and a sub-key | Those shared components grow a richer label slot |
| **Span drill-downs re-read `spansOf(traceId)` per request** | `trace()`, `spanIntervals()` and `eventsInSpan()` each fetch the trace's spans; they back separate HTTP requests against a small indexed table, and caching across requests is a design cost with no measured win | A profiled slow drill-down names this read as the cost |
| **Scope events are joined via JSON extraction per query** | `OPERATION_INTERVALS` re-parses `fields` for `jeffrey.TraceScope` rows; the event-type filter keeps that set small, and only re-entered spans (gRPC server traffic today) emit scopes at all. A third derived table was judged more schema than the join is worth | Scope-emitting instrumentation broadens beyond gRPC |
| **`@retry` listeners on `ErrorState` are inert** | The shared `ErrorState.vue` declares no `retry` emit and renders no retry button, so every `@retry` in the app is wiring to nothing. Trace views wire it anyway for the day the shared component grows the button — an `@shared` change, out of this feature's scope | `ErrorState` gains a retry button |
| **Operation intervals are unbounded per busy stretch** | The gaps-and-islands merge returns one row per contiguous busy stretch per `(trace, thread)` rather than one per pair; a pathological operation of thousands of alternating disjoint spans would inflate the `UNNEST` list parameters behind the span-scoped flamegraph filter | A hot operation's flamegraph request measurably slows on the interval lists |

---

## Closed on this branch

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
- ~~A recording older than the span shape listed event types instead of operations~~ — an exchange
  recorded before `AbstractTracedEvent` carried `name`/`kind`/`status` has none to read, so every
  request in it fell back to its event type and collapsed into one INTERNAL operation called
  `jeffrey.HttpServerExchange`. `LegacySpanShape` restores the derivation those events were written
  for, consulted only when the event did not describe itself: `kind` is the discriminator, since it
  is absent in every older shape and never absent in the current one. It matters most for `status`,
  which exists in both shapes meaning two different things — an HTTP response code then, a span
  status now. That is the fallback declined earlier "in favour of one shape"; the one shape is still
  what the derivation reads first, and this only answers for recordings that already exist. No new
  event type can ever need an entry here.
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
