# Traces & Spans — leftovers

State as of `4a14211` on `claude/traces-spans-jeffrey-hcnlbd` (PR #156). Everything below was
verified against the tree, not recalled.

Two items remain, both blocked on a `jeffrey-events` release. Everything else that was on this
list has been implemented — see [Closed](#closed) at the bottom.

---

## 1. `Tracer` cannot re-enter an existing span — blocks proper async instrumentation

**The one real API gap**, and the reason gRPC nesting is partial.

`Tracer` offers `inSpan` (mints a child) and `continueIn` (mints a child *and* emits an event).
Neither can re-establish an *existing* `SpanContext` without creating a new span.

This bites wherever work is not a single lambda:

- **gRPC** (`JfrGrpcServerInterceptor`) — a call runs from listener callbacks after `interceptCall`
  returns. The exchange is stamped and appears in the trace list, but work during the call is not
  nested under it. Documented on the class and on the docs page.
- Any future callback- or reactor-style instrumentation will hit the same wall.

**Fix:** add something like `Tracer.reenter(SpanContext, body)` to `jeffrey-events` — bind without
emitting. Small addition, but it needs a library release (0.13.0) and a `<jeffrey-events.version>`
bump, so it cannot be done from the monorepo alone.

## 2. Cross-thread spans are misattributed

JFR commits a duration event on the **ending** thread. A span that starts on one thread and ends on
another is recorded against the wrong one, which also breaks the `thread_hash` join the drill-down
uses. OpenTelemetry's own `jfr-events` contrib documents the same flaw.

v1 documents this in the `Tracer` javadoc and on the docs page rather than solving it. Solving it
means adding an explicit start-thread field to the event and reworking the join — again a library
change, and not a simple field addition, because the join key is derived during parsing rather than
supplied by the application.

In practice `ScopedValue` + the `continueIn` pattern keeps spans thread-confined, so this is a
latent sharp edge rather than an active bug.

---

## 3. Verification gap

**Nothing has been rendered in a browser against a real profile.** Build, type-check and the
frontend test suite pass, and the derivation is exercised end to end against a real JFR recording,
but the waterfall's split self/child bar treatment has only been seen in a mockup. It may read
differently at real span counts.

This needs a running instance and a human, not a code change.

## 4. Deliberate omissions worth revisiting later

Not bugs — decisions with a stated trade-off, recorded so they are not rediscovered as surprises.

| Omission | Why | Revisit when |
|---|---|---|
| **No zoom/pan in the waterfall** | DOM/CSS substrate chosen for tens-to-hundreds of spans | Real traces exceed a few thousand spans |
| **Critical path not computed** | Deferred from increment 5 | Traces get deep enough that the longest chain isn't obvious |
| **64-bit trace ids** | Jeffrey mints every id in a single recording | Cross-process assembly or ingesting an external `traceparent` becomes a goal — this is a one-way door, widening is a format change |
| **`@Threshold("1 ms")` default on `TraceSpanEvent`** | Keeps trivial spans out of the recording | Span volume proves fine, or proves too high — JDK 25 `@Throttle("N/s")` is the other lever |
| **Pipeline runs are their own traces** | `PipelineRunRegistry` forks to a virtual thread; a background job's lifetime is unrelated to the request | Users find the disconnection confusing in the trace list |
| **`SpanKind` has no `PRODUCER`/`CONSUMER`** | Their purpose is cross-process pairing, impossible single-JVM; no messaging instrumentation exists | Messaging instrumentation arrives — additive, so safe to defer |
| **`HttpClientExchangeEvent` / `GrpcClientExchangeEvent` never emitted here** | The derivation handles them and they are stamped-capable, so a third-party app gets outbound spans; Jeffrey itself makes no instrumented outbound calls | Jeffrey starts making them |

---

## Closed

Recorded because they were raised and are now done:

- ~~Span-scoped flamegraph tab had no UI callers~~ — done in `8f4c135`; third drawer tab, panel
  cards with real counts, Inclusive/Self toggle above them, fullscreen modal, no timeseries.
- ~~Operations view had no caller~~ — done in `8f4c135`; p50/p95/max on a shared rail, route
  declared before `:traceId`.
- ~~No documentation~~ — done in `4a14211`; a Traces reference under Profiles plus the event
  contract on the Jeffrey Events page.
- ~~`Guardian.process()` unspecified~~ — done in `46dd101`; one span per event type, because the
  frame tree and traversal are shared by every guard for that type.
- ~~Derivation never exercised through real profile initialization~~ — done in `6fd4c36`; an
  `InOrder` chain pins it after `eventWriter.onComplete()` and before `profileDataInitializer`.
- ~~Drill-down listed the spans themselves~~ — fixed in `c73f282`; the query excludes every traced
  event type, with two tests pinning it.
- ~~`jeffrey.TraceSpan` unregistered in `EventTypeName`/`Type`~~ — fixed in `c73f282`; the
  derivation now builds its type list from the shared constants.
- ~~gRPC exchanges carried no trace identity~~ — fixed in `226d30c` (nesting caveat is item 1).
- ~~`TraceSpanEvent.attributes` and `SpanStatus.OK` were dead~~ — fixed in `226d30c`; both are used
  by the pipeline root span and MCP tool dispatch.
- ~~JSON→BIGINT precision for 64-bit ids~~ — verified against DuckDB 1.5.5 at `Long.MIN_VALUE`,
  `Long.MAX_VALUE` and 2^53+1.
