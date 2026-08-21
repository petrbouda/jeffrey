---
name: jeffrey-traces-instrumentation
description: Instrument a Spring (RestController) + MyBatis application with Jeffrey JFR events — HttpServerExchangeEvent/HttpClientExchangeEvent, the JdbcQuery/Insert/Update/Delete/Execute statement events, and Tracer/TraceSpanEvent spans — so that JFR recordings uploaded to Jeffrey land correctly in its profile database and activate the HTTP, Database and Traces dashboards. Use when adding jeffrey-events instrumentation, writing a servlet filter or MyBatis interceptor that emits JFR events, or connecting spans across threads.
---

# Instrumenting a Spring + MyBatis Application with Jeffrey Traces

This skill describes how to emit Jeffrey's custom JFR events (library
`cafe.jeffrey-analyst:jeffrey-events`) from a Spring application with
`@RestController` endpoints and MyBatis data access, in a way that Jeffrey
ingests **correctly into its profile database**: every HTTP exchange becomes the
root span of a trace, every SQL statement becomes a leaf span nested under the
request that issued it, and hand-written `TraceSpan` events fill in the
application logic between them.

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
   instead of `stampAndCommit()`/`commitSpan()`).

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
    <artifactId>jeffrey-events</artifactId>
    <version>0.12.0</version> <!-- use the latest release -->
</dependency>
```

- **Java 25 or newer is required for `Tracer` and `TraceSpanEvent`** — the API
  is built on `ScopedValue` (JEP 506) and `jdk.jfr.Contextual`, both finalized
  in Java 25.
- On **Java 17–24** you can use the HTTP, gRPC and JDBC events from an earlier
  `jeffrey-events` release (they still light up the HTTP/Database dashboards),
  but not `Tracer` — no hand-written spans and no cross-event trace nesting.
- `Tracer.openSpanOf` / `Tracer.reenter` / `jeffrey.TraceScope` arrived after
  0.12.0 and need the release that follows it.
- The library has **zero dependencies** (only `jdk.jfr`) and is safe to leave
  on in production: when no recording is running, every emit path checks
  `event.isEnabled()` and runs the body directly with nothing allocated beyond
  an escape-analysable event instance.
- If the application runs on the module path, `jeffrey-events` already
  `opens` its event packages to `jdk.jfr`; nothing to configure. On the
  classpath, nothing to configure either.

**Optional but recommended** — register all event types eagerly at startup so
their metadata is present in every recording even before the first event fires
(e.g. in an `ApplicationListener<ApplicationReadyEvent>` or any init hook):

```java
import cafe.jeffrey.jfr.events.JeffreyEventRegistry;
import jdk.jfr.FlightRecorder;

JeffreyEventRegistry.all().forEach(FlightRecorder::register);
```

---

## 3. The event catalog you will emit

| Event type (JFR name) | Class | Kind | Emitted by |
|---|---|---|---|
| `jeffrey.HttpServerExchange` | `http.HttpServerExchangeEvent` | SERVER | servlet filter — **root span of the trace** |
| `jeffrey.HttpClientExchange` | `http.HttpClientExchangeEvent` | CLIENT | RestTemplate/HTTP-client interceptor — leaf |
| `jeffrey.JdbcQuery` | `jdbc.statement.JdbcQueryEvent` | CLIENT | MyBatis interceptor, SELECT — leaf |
| `jeffrey.JdbcInsert` | `jdbc.statement.JdbcInsertEvent` | CLIENT | MyBatis interceptor, INSERT — leaf |
| `jeffrey.JdbcUpdate` | `jdbc.statement.JdbcUpdateEvent` | CLIENT | MyBatis interceptor, UPDATE — leaf |
| `jeffrey.JdbcDelete` | `jdbc.statement.JdbcDeleteEvent` | CLIENT | MyBatis interceptor, DELETE — leaf |
| `jeffrey.JdbcExecute` | `jdbc.statement.JdbcExecuteEvent` | CLIENT | MyBatis interceptor, DDL/other — leaf |
| `jeffrey.TraceSpan` | `trace.TraceSpanEvent` | any | `Tracer.run`/`call`/`continueIn` — interior spans |
| `jeffrey.TraceScope` | `trace.TraceScopeEvent` | — | `Tracer.reenter` (automatic; never emit by hand) |

Field notes:

- **HTTP events** (`AbstractHttpExchangeEvent`): `remoteHost`, `remotePort`,
  `uri`, `method`, `mediaType`, `statusCode`, `queryParams` (JSON),
  `pathParams` (JSON), `requestLength`, `responseLength`. Span name and status
  are derived for you in `describeSpan()`: name = `"{method} {uri}"`, status =
  `ERROR` when `statusCode >= 400`. **Never set `name`/`status` yourself.**
- **JDBC events** (`JdbcBaseEvent`): constructor `(String name, String group)`
  — `name` is the statement label (in this project: MyBatis statement id),
  `group` groups statements in the Database dashboard (e.g. mapper name).
  Fields: `sql`, `params` (JSON), `rows`. `JdbcInsertEvent` adds
  `isLob`/`isBatch`; `JdbcQueryEvent` adds `samples`. Failures are recorded
  with `event.failed(throwable)` — never by setting `status` directly.
- **`TraceSpanEvent`**: no fields of its own; the span shape is all there is.
  You never construct it — `Tracer` does.

There are also connection-pool events (`jeffrey.JdbcPoolStatistics`,
`jeffrey.PooledJdbcConnectionAcquired/Borrowed/Created`,
`jeffrey.AcquiringPooledJdbcConnectionTimeout`) that feed the pool dashboard.
They are plain events (not spans); emit them from your pool's hook points
(e.g. HikariCP's `MetricsTrackerFactory`) if you want pool analysis.

---

## 4. The five rules that make the data land correctly

1. **One root per request, opened with `Tracer.inSpanOf`.** The HTTP server
   exchange event *is* the root span. `inSpanOf` stamps it with a fresh
   trace/span id and binds that context for the duration of the request, so
   everything traced further down nests under it. Do **not** additionally call
   `Tracer.run` around the request — that would record the same interval twice.

2. **Leaf events use `stampAndCommit()`, never `commit()`.** A JDBC statement
   or HTTP client event committed in its own `finally` must end with
   `event.stampAndCommit()`. It stamps the event as a child of the span in
   progress (or leaves ids at `0` when there is none — still valid, just
   untraced) and then runs `describeSpan()` + `commit()`. A bare `commit()`
   silently drops the event from every trace.

3. **Always follow the guard/begin/end/shouldCommit shape:**

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
       event.failed(e);                // JDBC events; rethrow afterwards
       throw e;
   } finally {
       if (event.shouldCommit()) {     // respects per-recording thresholds
           event.sql = ...;            // fill fields only when committing
           event.stampAndCommit();
       }
   }
   ```

   (On the exception path `end()` is intentionally not called — `commit()`
   closes the interval itself. This mirrors Jeffrey's own `DatabaseClient`.)

4. **Names must be stable and low-cardinality.** Every distinct string enters
   the JFR per-chunk constant pool. Name the *operation*, not the instance:
   the matched URI template (`/api/users/{id}`), the MyBatis statement id
   (`UserMapper.selectById`), `order.checkout` — never a raw URI with ids in
   it, a user id, or SQL with inlined literals as the name. The identity of an
   individual request lives in the trace id, which is already there.

5. **A trace does not cross a plain executor by itself.** `ScopedValue`
   propagates only through structured concurrency. Before handing work to an
   `ExecutorService`, `@Async` method, or `CompletableFuture`, capture the
   context and re-establish it: `Tracer.fork(...)` (capture at call site) or
   `Tracer.current()` + `Tracer.continueIn(parent, ...)` (explicit). Work
   submitted without this still runs and its leaf events still commit — but as
   roots of their own single-span traces.

---

## 5. HTTP server: the root-span filter

One servlet filter emits `HttpServerExchangeEvent` per request and opens the
trace. Register it **first in the filter chain** so everything — security,
MVC, MyBatis — happens inside the span.

> Import note: the code below uses `jakarta.servlet` (Spring Boot 3/4,
> Spring Framework 6/7). On a `javax.servlet` stack only the imports change.

```java
import cafe.jeffrey.jfr.events.http.HttpServerExchangeEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

public class JeffreyJfrHttpEventFilter extends OncePerRequestFilter {

    /** Requests that matched no handler (static assets, 404s) share one label. */
    private static final String UNMATCHED_URI = "<unmatched>";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        HttpServerExchangeEvent event = new HttpServerExchangeEvent();
        if (!event.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        event.begin();
        try {
            // The exchange event IS the root span: inSpanOf stamps it with fresh
            // ids and binds the context, so no separate TraceSpan is emitted for
            // the same interval. Everything below nests under this request.
            try {
                Tracer.inSpanOf(event, () -> {
                    filterChain.doFilter(request, response);
                    return null;
                });
            } catch (IOException | ServletException | RuntimeException e) {
                // Tracer's CallableOp infers a single thrown type, which widens
                // to Exception for a body throwing both IOException and
                // ServletException; narrow it back to what the filter declares.
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.remoteHost = request.getRemoteHost();
                event.remotePort = request.getRemotePort();
                event.uri = resolveTemplateUri(request);
                event.method = request.getMethod();
                event.mediaType = request.getContentType();
                event.statusCode = response.getStatus();
                event.requestLength = parseLong(request.getHeader("Content-Length"));
                event.responseLength = parseLong(response.getHeader("Content-Length"));
                // commitSpan(), NOT stampAndCommit(): inSpanOf already stamped
                // the ids when the span opened. Re-stamping would mint a fresh
                // span id and orphan every child recorded under the original.
                event.commitSpan();
            }
        }
    }

    /**
     * The matched handler pattern keeps the span name low-cardinality: one
     * operation per endpoint, not one per distinct path.
     */
    private static String resolveTemplateUri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isEmpty()) {
            String contextPath = request.getContextPath();
            return (contextPath == null || contextPath.isEmpty()) ? s : contextPath + s;
        }
        return UNMATCHED_URI;
    }

    private static long parseLong(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
```

Registration (Spring Boot):

```java
@Configuration
public class JeffreyInstrumentationConfiguration {

    @Bean
    public FilterRegistrationBean<JeffreyJfrHttpEventFilter> jeffreyJfrHttpEventFilter() {
        FilterRegistrationBean<JeffreyJfrHttpEventFilter> registration =
                new FilterRegistrationBean<>(new JeffreyJfrHttpEventFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
```

Why the details matter for the database:

- `uri` must be the **template** (`/api/users/{id}`) — Jeffrey's HTTP dashboard
  aggregates per endpoint on it, and the span name (`GET /api/users/{id}`) is
  derived from it in `describeSpan()`.
- `statusCode` drives span status: `>= 400` becomes `ERROR` automatically.
- The event is stamped by `inSpanOf` at open time, so the `finally` block only
  fills HTTP fields and calls `commitSpan()`.

---

## 6. HTTP client: outbound calls as leaf spans

For `RestTemplate` (a `ClientHttpRequestInterceptor`); the same shape applies
to any HTTP client with an interception point:

```java
public class JeffreyJfrRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        HttpClientExchangeEvent event = new HttpClientExchangeEvent();
        if (!event.isEnabled()) {
            return execution.execute(request, body);
        }
        event.begin();
        ClientHttpResponse response = null;
        try {
            response = execution.execute(request, body);
            event.end();
            return response;
        } finally {
            if (event.shouldCommit()) {
                event.method = request.getMethod().name();
                // Low-cardinality: host + path with variable segments collapsed,
                // ideally the URI template you expanded. Never a URL containing
                // an entity id — one distinct name per endpoint, not per call.
                event.uri = request.getURI().getHost() + normalizePath(request.getURI().getPath());
                event.remoteHost = request.getURI().getHost();
                event.remotePort = request.getURI().getPort();
                event.requestLength = body.length;
                event.statusCode = response != null ? response.getStatusCode().value() : 0;
                // A leaf: nested under the span in progress (usually the
                // server exchange of the request being served).
                event.stampAndCommit();
            }
        }
    }
}
```

Register it on the `RestTemplate` bean
(`restTemplate.getInterceptors().add(...)`). The client exchange is a **leaf**
— it uses `stampAndCommit()`, not `inSpanOf` — because the downstream work
happens in another process this recording cannot see.

---

## 7. MyBatis: every statement as a leaf span

A MyBatis `Interceptor` (plugin) on the `Executor` covers all mappers,
including cached and batched execution. The statement id gives a perfect
low-cardinality name and `SqlCommandType` picks the event class.

```java
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcBaseEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcDeleteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcExecuteEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent;
import cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
public class JeffreyJfrMyBatisInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        JdbcBaseEvent event = createEvent(statement);
        if (!event.isEnabled()) {
            return invocation.proceed();
        }

        Object parameter = invocation.getArgs()[1];
        event.begin();
        Object result = null;
        try {
            result = invocation.proceed();
            event.end();
            return result;
        } catch (Throwable t) {
            event.failed(t);        // status=ERROR + errorType; the span shows red
            throw t;
        } finally {
            if (event.shouldCommit()) {
                event.sql = statement.getBoundSql(parameter).getSql();
                event.rows = countRows(result);
                // Leaf span: nested under the HTTP request (or Tracer span)
                // in progress on this thread; untraced-but-recorded when none.
                event.stampAndCommit();
            }
        }
    }

    /**
     * name  = "UserMapper.selectById"  — stable, one per mapper method
     * group = "UserMapper"             — Database dashboard grouping
     */
    private static JdbcBaseEvent createEvent(MappedStatement statement) {
        String id = statement.getId();                        // com.example.mapper.UserMapper.selectById
        int methodDot = id.lastIndexOf('.');
        int mapperDot = id.lastIndexOf('.', methodDot - 1);
        String name = id.substring(mapperDot + 1);            // UserMapper.selectById
        String group = id.substring(mapperDot + 1, methodDot); // UserMapper

        return switch (statement.getSqlCommandType()) {
            case SELECT -> new JdbcQueryEvent(name, group);
            case INSERT -> new JdbcInsertEvent(name, group);
            case UPDATE -> new JdbcUpdateEvent(name, group);
            case DELETE -> new JdbcDeleteEvent(name, group);
            default -> new JdbcExecuteEvent(name, group);
        };
    }

    private static long countRows(Object result) {
        if (result instanceof List<?> list) {
            return list.size();       // SELECT: returned rows
        }
        if (result instanceof Integer updated) {
            return updated;           // INSERT/UPDATE/DELETE: affected rows
        }
        return 0;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
```

Registration:

- **mybatis-spring-boot-starter**: declare it as a bean — every `Interceptor`
  bean is added to the `SqlSessionFactory` automatically:

  ```java
  @Bean
  public JeffreyJfrMyBatisInterceptor jeffreyJfrMyBatisInterceptor() {
      return new JeffreyJfrMyBatisInterceptor();
  }
  ```

- **Plain mybatis-spring** (`SqlSessionFactoryBean`):
  `factoryBean.setPlugins(new JeffreyJfrMyBatisInterceptor());`
- **XML config**: `<plugins><plugin interceptor="…JeffreyJfrMyBatisInterceptor"/></plugins>`

Correctness notes for the database:

- `sql` may contain `?` placeholders — that is *good*: identical statements
  aggregate. Do not inline parameter values into `sql`.
- If you also want parameter values, serialize them as a JSON object string
  into `event.params` — and scrub anything sensitive; the recording and the
  profile database will contain it verbatim.
- Batched inserts: set `event.isBatch = true` (and skip `sql`/`params` if the
  batch is large) when you intercept batch execution.
- Lazy loading / nested selects execute wherever the property is touched. If
  that happens on the request thread inside the span, they nest correctly; if
  it happens later or on another thread, the statement records as its own
  root — prefer eager fetching in traced paths or accept the orphan.

---

## 8. Hand-written spans: `Tracer` and `jeffrey.TraceSpan`

The HTTP root and JDBC leaves alone produce two-level traces. `Tracer` fills
in the application logic between them, emitting a `jeffrey.TraceSpan` per
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

`ScopedValue` does not survive a plain executor hand-off. Two tools:

```java
// fork(): capture here, replay there — for work that IS a separate operation
CompletableFuture.runAsync(
        Tracer.fork("report.render", () -> renderChunk(part)),   // capture at call site!
        executor);

// continueIn(): the explicit form when you carry the context yourself
SpanContext parent = Tracer.current().orElse(null);
executor.submit(() -> Tracer.continueIn(parent, "chunk.parse", () -> {
    parseChunk(file);
    return null;
}));
```

`fork` captures the parent when *called*, not when the task runs — always call
it on the thread whose span the work belongs to, then submit the result.
`@Async` methods and scheduled tasks need the same treatment.

### Callback-driven work (newer releases)

For one operation arriving in pieces on threads you don't control (async HTTP
clients, gRPC listeners): `SpanContext ctx = Tracer.openSpanOf(event)` stamps
the event without binding, then wrap **every** callback in
`Tracer.reenter(ctx, () -> ...)`. Each re-entry emits a `jeffrey.TraceScope`
recording which thread the span actually ran on. `reenter` resumes the *same*
span; `continueIn` mints a *child* — pick by whether the receiving thread is
doing a separate piece of work.

### Deferred commits

If an event is committed after the enclosing binding is gone (e.g. from a
stream's `close()`), `stampAndCommit()` in the `finally` would find no span
and record ids of `0`. Stamp eagerly instead: call `Tracer.stamp(event)` at
construction (inside the span) and commit later with `event.commitSpan()` —
`stampAndCommit()` never re-stamps an event that already carries identity.

---

## 9. Recording configuration

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

## 10. Verify before uploading

```bash
jfr print --events "jeffrey.*" app.jfr | less
```

Check, for one request you exercised:

1. A `jeffrey.HttpServerExchange` exists with non-zero `traceId`/`spanId` and
   `parentSpanId = 0` (it is the root), `uri` showing the **template**.
2. Every `jeffrey.JdbcQuery`/`JdbcUpdate`/… issued while serving it carries
   the **same `traceId`** and a `parentSpanId` chaining up to the exchange
   (directly, or via the `jeffrey.TraceSpan`s between them).
3. `jeffrey.TraceSpan` events show your operation names, `status = UNSET` on
   success and `ERROR` + `errorType` where you exercised a failure.
4. No high-cardinality names (raw URIs, ids, literal-bearing SQL as names).

An event with all-zero ids means a `commit()` slipped in where
`stampAndCommit()`/`commitSpan()` belonged, or work crossed an executor
without `fork`/`continueIn`.

Then upload `app.jfr` to Jeffrey (create a project → upload recording →
initialize profile). Jeffrey auto-detects the event types and activates the
HTTP, Database, and Traces & Spans dashboards; every event is now queryable in
the profile database, and the Traces view shows the waterfall with per-span
self time and flamegraphs.

## 11. Pitfall checklist

| Symptom in Jeffrey | Cause | Fix |
|---|---|---|
| Events in HTTP/DB dashboards but Traces empty or flat | committed with `commit()` | use `stampAndCommit()` (leaves) / `commitSpan()` (root) |
| SQL spans are roots of their own one-span traces | executor/`@Async` boundary, or no HTTP filter in front | `Tracer.fork`/`continueIn`; register the filter first in the chain |
| Children of a request orphaned & promoted to roots | root event re-stamped, or `TraceSpan` threshold dropped the parent | never stamp an `inSpanOf` event again; commit root via `commitSpan()`; mind thresholds |
| Huge recordings | high-cardinality span names / URIs / `params` | templates & statement ids as names; ids belong in the trace id |
| Span durations wrong | `begin()`/`end()` not tight around the work | begin right before, end right after; fill fields only in `shouldCommit` block |
| HTTP 500s not red in traces | `statusCode` never set before commit | set `statusCode` in the `finally`, then `commitSpan()` |
| SQL errors invisible | exception swallowed without `event.failed(e)` | call `failed(e)` in the catch, rethrow |
| Nothing recorded at all | no JFR recording running | start one (§9); `isEnabled()` guards made instrumentation a no-op |
| Span missing but its children fine | event committed after binding gone (deferred commit) | eager `Tracer.stamp` at construction + `commitSpan()` later |
