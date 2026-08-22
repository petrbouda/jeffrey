---
name: jeffrey-traces-core
description: Core concepts and rules for instrumenting any JVM application with Jeffrey JFR events and Jeffrey Traces — the trace/span data model (AbstractTracedEvent, TraceSpanEvent, SpanContext), the emit rules that make events land correctly in Jeffrey's profile database, the Tracer API (run/call/inSpanOf/stamp/fork/continueIn/openSpanOf/reenter), JFR recording configuration, and verification. Read this FIRST before any technology-specific Jeffrey instrumentation skill (jeffrey-traces-spring-rest-server, jeffrey-traces-http-client, jeffrey-traces-mybatis, jeffrey-traces-grpc) or when writing custom spans, custom traced events, or connecting spans across threads.
---

# Jeffrey Traces — Core Concepts and Rules

This is the foundation skill for instrumenting an application with Jeffrey's
custom JFR events (library `cafe.jeffrey-analyst:jeffrey-tracing`) so that
recordings uploaded to Jeffrey land **correctly in its profile database**:
requests become roots of traces, SQL statements and outbound calls become leaf
spans nested under them, and hand-written `TraceSpan` events fill in the
application logic between.

Technology-specific wiring lives in companion skills — apply them after this
one:

- **`jeffrey-traces-spring-rest-server`** — Spring MVC `@RestController`
  apps: the servlet filter that emits `HttpServerExchangeEvent` as the trace
  root of every inbound request.
- **`jeffrey-traces-http-client`** — outbound HTTP calls: the client
  interceptor that emits `HttpClientExchangeEvent` as a leaf span
  (RestTemplate; async clients via the callback pattern).
- **`jeffrey-traces-mybatis`** — MyBatis: the `Executor` interceptor that
  emits a `JdbcQuery/Insert/Update/Delete/Execute` event per statement.
- **`jeffrey-traces-grpc`** — gRPC: the server interceptor that roots a trace
  per inbound call, and the client interceptor that records outbound calls as
  leaf spans.

Application logic in between has two forms. `Tracer.run`/`call` wrap work in a
lambda — precise, and visible in the method. `@Traced` on a method declares the
same span and is woven by the Jeffrey agent
(`-javaagent:jeffrey-agent.jar=tracing.enabled=true`), which leaves the method
untouched but needs the agent attached at startup and Java 25. Both emit
`jeffrey.TraceSpan` and nest identically; neither replaces the emit rules below.

There is no separate "send data to Jeffrey" step. The events are ordinary JFR
events written into the JVM's flight recording; you upload the `.jfr` file to
Jeffrey (or let a Jeffrey workspace collect it), and Jeffrey parses every event
into the per-profile DuckDB database. Getting the data "into the database
correctly" is therefore entirely a matter of **emitting the events correctly**
— right emit path, right ids, right names. That is what the rules below encode.

---

## 1. The contract: how events become database rows

Understand this model first; every rule follows from it.

1. **Every event is a row.** Jeffrey parses the recording and stores each event
   (with all its fields) in the profile database. HTTP events feed the HTTP
   dashboard, JDBC events feed the Database dashboard — this works even without
   any tracing, as long as the events are committed.

2. **An event is a span when it carries trace identity.** All HTTP, gRPC, and
   JDBC statement events extend `AbstractTracedEvent`, which declares:

   | Field | Type | Meaning |
   |---|---|---|
   | `traceId` | `long` | Identifies the whole trace; shared by every span in it |
   | `spanId` | `long` | Identifies this span; unique within the trace |
   | `parentSpanId` | `long` | Enclosing span's id; `0` = this span is a root |
   | `name` | `String` | Operation name — **stable, low-cardinality** |
   | `kind` | `String` | `INTERNAL` \| `SERVER` \| `CLIENT` |
   | `status` | `String` | `UNSET` \| `OK` \| `ERROR` |
   | `errorType` | `String` | Exception class name when status is `ERROR` |
   | `attributes` | `String` | Operation-specific detail as a JSON object |

   The value `0` means "absent". An event whose `traceId`/`spanId` are `0` still
   appears in the dashboards, but it is **not part of any trace** — this is the
   single most common instrumentation mistake (committing with `commit()`
   instead of `commitSpan()`).

3. **Jeffrey detects spans from metadata, not from event names.** Any event
   type that declares a `spanId` field takes part in traces — including your
   own custom events, if you extend `AbstractTracedEvent`. No Jeffrey-side
   configuration is needed.

4. **The trace tree is just the three ids.** Jeffrey rebuilds the waterfall
   from `(traceId, spanId, parentSpanId)`. A span whose parent is missing from
   the recording (fell under a threshold, never instrumented) is promoted to a
   root — nothing is lost, but the nesting is.

5. **JFR attributes an event to the thread that commits it**, and its interval
   is `begin()`…`end()`. Jeffrey uses `(thread, time window)` to correlate
   spans with CPU samples, locks, and allocations — which is why spans get
   per-span flamegraphs, and why the emit patterns below always commit on the
   thread that did the work.

---

## 2. Dependency and version requirements

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-tracing</artifactId>
    <version><!-- latest release on Maven Central --></version>
</dependency>
```

- **Java 25 or newer is required for `Tracer` and `TraceSpanEvent`** — the API
  is built on `ScopedValue` (JEP 506) and `jdk.jfr.Contextual`, both finalized
  in Java 25.
- On **Java 17–24** you can use the HTTP, gRPC and JDBC events from an earlier
  `jeffrey-tracing` release (they still light up the HTTP/Database dashboards),
  but not `Tracer` — no hand-written spans and no cross-event trace nesting.
- There is a single commit verb: `commitSpan()` stamps an event that does not
  yet carry identity, `failed(Throwable)` exists on **every** traced event, and
  `TracedEvents.emit` writes the whole leaf emit shape in one call.
  (`stampAndCommit()` is a deprecated alias kept for older call sites.)
- The library has **zero dependencies** (only `jdk.jfr`) and is safe to leave
  on in production: when no recording is running, every emit path checks
  `event.isEnabled()` and runs the body directly with nothing allocated beyond
  an escape-analysable event instance.
- If the application runs on the module path, `jeffrey-tracing` already
  `opens` its event packages to `jdk.jfr`; nothing to configure. On the
  classpath, nothing to configure either.
- No registration step is needed: JFR auto-registers each event type the
  first time an instance of its class is created, so committed events always
  land in the recording with full metadata.

---

## 3. The event catalog

| Event type (JFR name) | Class | Kind | Emitted by |
|---|---|---|---|
| `jeffrey.HttpServerExchange` | `http.HttpServerExchangeEvent` | SERVER | servlet filter — **root span of the trace** (see `jeffrey-traces-spring-rest-server`) |
| `jeffrey.HttpClientExchange` | `http.HttpClientExchangeEvent` | CLIENT | HTTP-client interceptor — leaf (see `jeffrey-traces-http-client`) |
| `jeffrey.JdbcQuery` | `jdbc.statement.JdbcQueryEvent` | CLIENT | DB interceptor, SELECT — leaf (see `jeffrey-traces-mybatis`) |
| `jeffrey.JdbcInsert` | `jdbc.statement.JdbcInsertEvent` | CLIENT | DB interceptor, INSERT — leaf |
| `jeffrey.JdbcUpdate` | `jdbc.statement.JdbcUpdateEvent` | CLIENT | DB interceptor, UPDATE — leaf |
| `jeffrey.JdbcDelete` | `jdbc.statement.JdbcDeleteEvent` | CLIENT | DB interceptor, DELETE — leaf |
| `jeffrey.JdbcExecute` | `jdbc.statement.JdbcExecuteEvent` | CLIENT | DB interceptor, DDL/other — leaf |
| `jeffrey.GrpcServerExchange` | `grpc.GrpcServerExchangeEvent` | SERVER | gRPC server interceptor — **root span of the trace** (see `jeffrey-traces-grpc`) |
| `jeffrey.GrpcClientExchange` | `grpc.GrpcClientExchangeEvent` | CLIENT | gRPC client interceptor — leaf (see `jeffrey-traces-grpc`) |
| `jeffrey.TraceSpan` | `trace.TraceSpanEvent` | any | `Tracer.run`/`call`/`continueIn` — interior spans |
| `jeffrey.TraceScope` | `trace.TraceScopeEvent` | — | `Tracer.reenter` (automatic; never emit by hand) |

Field notes:

- **HTTP events** (`AbstractHttpExchangeEvent`): `remoteHost`, `remotePort`,
  `uri`, `method`, `mediaType`, `statusCode`, `queryParams` (JSON),
  `pathParams` (JSON), `requestLength`, `responseLength`. Span name and status
  are derived for you in `describeSpan()`: name = `"{method} {uri}"`, status =
  `ERROR` when `statusCode >= 400`. **Never set `name`/`status` yourself** — a
  transport failure that produced no status code is recorded with
  `event.failed(throwable)`, which the derived verdict never paints over.
- **JDBC events** (`JdbcBaseEvent`): constructor `(String name, String group)`
  — `name` is the statement label, `group` groups statements in the Database
  dashboard. Fields: `sql`, `params` (JSON), `rows`. `JdbcInsertEvent` adds
  `isLob`/`isBatch`; `JdbcQueryEvent` adds `samples`. Failures are recorded
  with `event.failed(throwable)` — never by setting `status` directly.
- **`TraceSpanEvent`**: no fields of its own; the span shape is all there is.
  You never construct it — `Tracer` does.
- **`attributes`** (any traced event): operation-specific detail as a JSON
  object string — per-request values (an entity id, a retry count) that must
  never go into the span *name*. Build it with `SpanAttributes` rather than
  concatenating JSON by hand — it escapes
  quotes, backslashes and control characters — and only inside the
  `shouldCommit()` block, so an event under threshold pays nothing:

  ```java
  event.attributes = SpanAttributes.create()
          .put("cache", "miss")
          .put("retries", 2)
          .json();
  ```

  The recording and the profile database contain the values verbatim — scrub
  anything sensitive.

There are also connection-pool events (`jeffrey.JdbcPoolStatistics`,
`jeffrey.PooledJdbcConnectionAcquired/Borrowed/Created`,
`jeffrey.AcquiringPooledJdbcConnectionTimeout`) that feed the pool dashboard.
They are plain events (not spans); emit them from your pool's hook points
(e.g. HikariCP's `MetricsTrackerFactory`) if you want pool analysis.

---

## 4. The five rules that make the data land correctly

1. **One root per request, opened with `Tracer.inSpanOf`.** The inbound-request
   event (e.g. an HTTP server exchange) *is* the root span. `inSpanOf` stamps
   it with a fresh trace/span id and binds that context for the duration of the
   request, so everything traced further down nests under it. Do **not**
   additionally call `Tracer.run` around the request — that would record the
   same interval twice.

2. **Every traced event commits through `commitSpan()`, never `commit()`.** A
   JDBC statement or HTTP client event committed in its own `finally` must end
   with `event.commitSpan()`. It stamps an event that does not yet carry trace
   identity as a child of the span in progress (or leaves ids at `0` when there
   is none — still valid, just untraced), keeps an event that already carries
   identity exactly as it is, and then runs `describeSpan()` + `commit()`. A
   bare `commit()` silently drops the event from every trace.

3. **Emit leaves through `TracedEvents.emit`**, which
   is the whole guard/begin/end/failed/shouldCommit/fill/commitSpan lifecycle
   in one call — the emit site states only the event, the work, and the
   fields:

   ```java
   JdbcQueryEvent event = new JdbcQueryEvent("UserMapper.selectById", "UserMapper");
   List<User> users = TracedEvents.emit(event,
           () -> doQuery(),                     // checked exceptions carry through typed
           (e, result) -> {                     // runs only when actually committing;
               e.sql = sql;                     // result is null on the failure path
               e.rows = result != null ? result.size() : 0;
           });
   ```

   An exception escaping the body is recorded with `failed(t)` and rethrown
   unchanged. Where the helper does not fit, write what it expands to:

   ```java
   SomeEvent event = new SomeEvent(...);
   if (!event.isEnabled()) {           // no recording -> zero-cost passthrough
       return doWork();
   }
   event.begin();
   try {
       result = doWork();
       event.end();                    // interval ends when the work ends
   } catch (Exception e) {
       event.failed(e);                // any traced event; rethrow afterwards
       throw e;
   } finally {
       if (event.shouldCommit()) {     // respects per-recording thresholds
           event.sql = ...;            // fill fields only when committing
           event.commitSpan();
       }
   }
   ```

   (On the exception path `end()` is intentionally not called — `commit()`
   closes the interval itself. This mirrors Jeffrey's own `DatabaseClient`,
   which emits every statement through `TracedEvents.emit`.)

4. **Names must be stable and low-cardinality.** Every distinct string enters
   the JFR per-chunk constant pool. Name the *operation*, not the instance:
   the matched URI template (`/api/users/{id}`), the MyBatis statement id
   (`UserMapper.selectById`), `order.checkout` — never a raw URI with ids in
   it, a user id, or SQL with inlined literals as the name. The identity of an
   individual request lives in the trace id, which is already there.

5. **A trace does not cross a plain executor by itself.** `ScopedValue`
   propagates only through structured concurrency. Either wrap the pool once
   with `Tracer.propagating(executor)` so every task
   submitted inside a span runs inside it, or capture per call site:
   `Tracer.fork(...)`/`Tracer.forkCallable(...)` (a named child span) or
   `Tracer.current()` + `Tracer.continueIn(parent, ...)` (explicit). Work
   submitted without any of this still runs and its leaf events still commit —
   but as roots of their own single-span traces.

---

## 5. Hand-written spans: `Tracer` and `jeffrey.TraceSpan`

Root events and leaf events alone produce two-level traces. `Tracer` fills in
the application logic between them, emitting a `jeffrey.TraceSpan` per
operation. The current span travels in a `ScopedValue` — nothing is threaded
through your call signatures.

```java
import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;

// Void body; kind defaults to INTERNAL
Tracer.run("order.validate", () -> validator.validate(order));

// Value-returning; checked exceptions propagate through the type variable
Order order = Tracer.call("order.load", () -> orderService.load(id));

// Explicit kind for an outbound wait
Tracer.run("payment.charge", SpanKind.CLIENT, () -> paymentGateway.charge(order));
```

- An exception escaping the body marks the span `ERROR` with the exception's
  class name and is **rethrown unchanged**. Catch inside the body if a handled
  failure should not mark the operation failed.
- `SpanKind`: `INTERNAL` = the process's own work (default), `SERVER` =
  handling an inbound request, `CLIENT` = waiting on something else (SQL,
  HTTP, queue). Kind is how Jeffrey distinguishes "my time" from "their time".
- Instrument **operations, not methods**: `order.checkout`,
  `inventory.reserve`, `report.render` — a handful of meaningful spans per
  request beats hundreds of one-per-method spans.

### Crossing threads

`ScopedValue` does not survive a plain executor hand-off. Three tools:

```java
// propagating(): wrap the POOL once, and every task submitted inside a span
// runs inside that span — no per-call-site wrapping, no name, no child span.
// Leaf events in the task stamp under the submitting span; each activation
// records a jeffrey.TraceScope naming the thread it ran on.
ExecutorService executor = Tracer.propagating(Executors.newFixedThreadPool(8));
executor.submit(() -> parseChunk(file));

// fork()/forkCallable(): capture here, replay there — for work that IS a
// separate operation deserving its own named span (composes with the above)
CompletableFuture.runAsync(
        Tracer.fork("report.render", () -> renderChunk(part)),   // capture at call site!
        executor);
Future<Report> report = executor.submit(
        Tracer.forkCallable("report.render", () -> render(part)));

// continueIn(): the explicit form when you carry the context yourself
SpanContext parent = Tracer.current().orElse(null);
executor.submit(() -> Tracer.continueIn(parent, "chunk.parse", () -> {
    parseChunk(file);
    return null;
}));
```

Pick `propagating` when a whole pool serves traced requests (releases after
`fork`/`forkCallable` when one task is a distinct operation worth a
span of its own. `fork` captures the parent when *called*, not when the task
runs — always call it on the thread whose span the work belongs to, then
submit the result. `@Async` methods and scheduled tasks need the same
treatment (a `TaskDecorator`/wrapped executor is the `propagating` shape).

### Callback-driven work

For one operation arriving in pieces on threads you don't control (async HTTP
clients, gRPC listeners): `SpanContext ctx = Tracer.openSpanOf(event)` stamps
the event without binding, then wrap **every** callback in
`Tracer.reenter(ctx, () -> ...)`. Each re-entry emits a `jeffrey.TraceScope`
recording which thread the span actually ran on. `reenter` resumes the *same*
span; `continueIn` mints a *child* — pick by whether the receiving thread is
doing a separate piece of work.

### Deferred commits

If an event is committed after the enclosing binding is gone (e.g. from a
stream's `close()`), `commitSpan()` in the `finally` would find no span and
record ids of `0` — or worse, run inside someone else's binding and stamp the
event into the wrong trace. Stamp eagerly instead: call `Tracer.stamp(event)`
at construction (inside the span) and commit later with `event.commitSpan()` —
it never re-stamps an event that already carries identity.

### Custom traced event types

To make your own domain event a span (a batch job step, a cache rebuild),
extend `AbstractTracedEvent`, declare your fields, optionally override
`describeSpan()` to derive `name`/`status` from them, and commit through
`commitSpan()`. Jeffrey picks it up with no configuration —
span participation is detected from the `spanId` field in the recording's
metadata.

---

## 6. Recording configuration

The events are recorded by whatever JFR recording is running; they are enabled
by default in any recording (no settings file changes needed).

```bash
# Plain JFR at startup
-XX:StartFlightRecording=filename=app.jfr,settings=profile

# On demand
jcmd <pid> JFR.start name=jeffrey settings=profile
jcmd <pid> JFR.dump  name=jeffrey filename=app.jfr

# async-profiler: CPU samples + all JFR (and Jeffrey) events in one file
asprof -d 60 -e cpu --jfrsync default -f app.jfr <pid>
```

Volume control, per recording (never bake thresholds into the code — a
dropped span orphans its children and shifts their time into the parent):

```bash
# Drop hand-written spans shorter than 1 ms
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceSpanEvent#threshold=1ms

# Keep re-entry nesting but stop recording where re-entered spans ran
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceScopeEvent#enabled=false
```

---

## 7. Verify before uploading

```bash
jfr print --events "jeffrey.*" app.jfr | less
```

Check, for one request you exercised:

1. The root event (e.g. `jeffrey.HttpServerExchange`) exists with non-zero
   `traceId`/`spanId` and `parentSpanId = 0`.
2. Every leaf event issued while serving it carries the **same `traceId`** and
   a `parentSpanId` chaining up to the root (directly, or via the
   `jeffrey.TraceSpan`s between them).
3. `jeffrey.TraceSpan` events show your operation names, `status = UNSET` on
   success and `ERROR` + `errorType` where you exercised a failure.
4. No high-cardinality names (raw URIs, ids, literal-bearing SQL as names).

An event with all-zero ids means a `commit()` slipped in where
`commitSpan()` belonged, or work crossed an executor without
`fork`/`continueIn`.

Then upload `app.jfr` to Jeffrey (create a project → upload recording →
initialize profile). Jeffrey auto-detects the event types and activates the
HTTP, Database, and Traces & Spans dashboards; every event is now queryable in
the profile database, and the Traces view shows the waterfall with per-span
self time and flamegraphs.

## 8. Pitfall checklist

| Symptom in Jeffrey | Cause | Fix |
|---|---|---|
| Events in HTTP/DB dashboards but Traces empty or flat | committed with `commit()` | use `commitSpan()` |
| Leaf spans are roots of their own one-span traces | executor/`@Async` boundary, or no root filter in front | `Tracer.fork`/`continueIn`; register the root filter first in the chain |
| Children of a request orphaned & promoted to roots | root event re-stamped, or `TraceSpan` threshold dropped the parent | never stamp an `inSpanOf` event again; commit root via `commitSpan()`; mind thresholds |
| Huge recordings | high-cardinality span names / URIs / `params` | templates & statement ids as names; ids belong in the trace id |
| Span durations wrong | `begin()`/`end()` not tight around the work | begin right before, end right after; fill fields only in `shouldCommit` block |
| Errors invisible in traces | exception swallowed without `failed(e)` / `statusCode` never set | call `failed(e)` and rethrow; set `statusCode` before `commitSpan()` |
| Nothing recorded at all | no JFR recording running | start one (§6); `isEnabled()` guards made instrumentation a no-op |
| Span missing but its children fine | event committed after binding gone (deferred commit) | eager `Tracer.stamp` at construction + `commitSpan()` later |
