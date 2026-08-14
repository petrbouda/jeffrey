# Traces & Spans — leftovers

State as of the `feature/trace-operations` branch. Everything below was verified against the tree,
not recalled.

Two items remain open, both blocked on a `jeffrey-events` release. The rest of this file is the
record of decisions taken with a stated trade-off, so they are not rediscovered later as surprises.

---

## 1. `Tracer` cannot re-enter an existing span — blocks proper async instrumentation

**The one real API gap**, and the reason gRPC nesting is partial.

`Tracer` offers `inSpan` (mints a child), `inSpanOf` (mints a child and makes an event that span)
and `continueIn` (mints a child of a carried context *and* emits an event). None can re-establish an
*existing* `SpanContext` without creating a new span.

This bites wherever work is not a single lambda:

- **gRPC** (`JfrGrpcServerInterceptor`) — a call runs from listener callbacks after `interceptCall`
  returns. The exchange is stamped and appears in the trace list, but work during the call is not
  nested under it. Documented on the class and on the docs page.
- Any future callback- or reactor-style instrumentation will hit the same wall.

**Fix:** add something like `Tracer.reenter(SpanContext, body)` to `jeffrey-events` — bind without
emitting. Small addition, but it needs a library release and a `<jeffrey-events.version>` bump, so
it cannot be done from the monorepo alone.

## 2. Cross-thread spans are misattributed

JFR commits a duration event on the **ending** thread. A span that starts on one thread and ends on
another is recorded against the wrong one, which also breaks the `thread_hash` join the drill-down
uses. OpenTelemetry's own `jfr-events` contrib documents the same flaw.

This is documented in the `Tracer` javadoc and on the docs page rather than solved. Solving it means
adding an explicit start-thread field to the event and reworking the join — again a library change,
and not a simple field addition, because the join key is derived during parsing rather than supplied
by the application.

In practice `ScopedValue` + the `continueIn` pattern keeps spans thread-confined, so this is a
latent sharp edge rather than an active bug.

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
| **Old recordings misread HTTP/gRPC/JDBC status** | `status` → `statusCode` and the removal of `isSuccess` are breaking schema changes; fallbacks were considered and declined in favour of one shape | A user reports it on a recording worth keeping — the fallback is three one-line reads |
| **P99 shown only when the trace sample is complete** | There is no server-side p99 column, and a p99 over the first 1,000 traces beside a p95 over all of them is two different questions in one row | A p99 aggregate is added to the `OPERATIONS` query |
| **`TraceSpanInlineDetail` hand-rolls its key/value tables** | `@shared` `DrawerSection`/`InfoRow` fit the identity block but not the two data-driven tables, which need a label-side tooltip and a sub-key | Those shared components grow a richer label slot |

---

## Closed on this branch

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
- ~~`spansOfOperation` unbounded~~ — replaced by an interval aggregate that returns one row per
  `(trace, thread)`.
- ~~`derive()` not idempotent~~ — both tables are cleared first, and a test runs it twice.
- ~~Every virtual thread collapsed into one~~ — `EventThreadCleaner` no longer groups on a null
  `os_id`, which is what `trace_spans.thread_hash` depends on.
