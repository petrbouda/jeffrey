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
| **Old recordings misread HTTP/gRPC/JDBC status** | `status` → `statusCode` and the removal of `isSuccess` are breaking schema changes; fallbacks were considered and declined in favour of one shape | A user reports it on a recording worth keeping — the fallback is three one-line reads |
| **P99 shown only when the trace sample is complete** | There is no server-side p99 column, and a p99 over the first 1,000 traces beside a p95 over all of them is two different questions in one row | A p99 aggregate is added to the `OPERATIONS` query |
| **`TraceSpanInlineDetail` hand-rolls its key/value tables** | `@shared` `DrawerSection`/`InfoRow` fit the identity block but not the two data-driven tables, which need a label-side tooltip and a sub-key | Those shared components grow a richer label slot |

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
- ~~The trace detail recomputed its own header~~ — it reads the stored `traces` row, so a trace's
  duration is the same number in the list and in the detail.
- ~~`has_platform_span` computed per query, two ways~~ — one stored column, one convention: a span
  whose thread did not resolve is not counted as platform.
- ~~`spansOfOperation` unbounded~~ — replaced by an interval aggregate that returns one row per
  `(trace, thread)`.
- ~~`derive()` not idempotent~~ — both tables are cleared first, and a test runs it twice.
- ~~Every virtual thread collapsed into one~~ — `EventThreadCleaner` no longer groups on a null
  `os_id`, which is what `trace_spans.thread_hash` depends on.
