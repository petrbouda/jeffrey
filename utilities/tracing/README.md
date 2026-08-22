# jeffrey-tracing

Custom JFR event types and a tracing API for instrumenting JVM applications, visualized by
[Jeffrey](https://github.com/petrbouda/jeffrey).

The events are ordinary JFR events written into the JVM's flight recording — there is no separate
"send data" step, no collector, no agent. Record with whatever starts a JFR recording, upload the
`.jfr` file to Jeffrey, and every event lands in its profile database: HTTP exchanges feed the HTTP
dashboards, JDBC statements the Database dashboard, and events carrying trace identity assemble
into full request traces with per-span flamegraphs.

- **Zero dependencies** — only `jdk.jfr`.
- **Zero cost when off** — every emit path checks `event.isEnabled()` first; with no recording
  running the instrumented code runs directly. Safe to leave in production code.
- **Java 25+** for the `Tracer` API (built on `ScopedValue` and `jdk.jfr.Contextual`); the plain
  event types work on earlier releases via older versions of this library.

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-tracing</artifactId>
    <version><!-- latest from Maven Central --></version>
</dependency>
```

## Spring Boot: one dependency, no code

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-tracing-spring-boot-starter</artifactId>
    <version><!-- latest release --></version>
</dependency>
```

Every inbound request becomes the root span of a trace, named by the matched handler pattern. Every
`DataSource` bean is wrapped, so the statements your ORM issues nest underneath the request without
anyone writing JDBC instrumentation, and a HikariCP pool gets its acquire/borrow/create timings and a
periodic gauge. Tune it with `jeffrey.tracing.*`; see the
[Spring server skill](skills/jeffrey-traces-spring-rest-server/SKILL.md).

Not on Boot? `jeffrey-tracing-spring` has the same beans with no Boot dependency and no
auto-configuration — `@Import(JeffreyTracingConfiguration.class)` and nothing happens until you ask.
No Spring at all? `jeffrey-tracing-servlet` needs only `jakarta.servlet`.

gRPC and MyBatis are one line each, wherever you build the server, the channel or the
`Configuration` — there is nothing to auto-configure because gRPC has no bean the starter could
recognise:

```java
Server server = ServerBuilder.forPort(port)
        .intercept(new JfrGrpcServerInterceptor())      // inbound call = trace root
        .build();

ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .intercept(new JfrGrpcClientInterceptor())      // outbound call = leaf under the caller
        .build();

configuration.addInterceptor(new JeffreyMyBatisInterceptor());   // UserMapper.selectById, with its parameters
```

Use `jeffrey-tracing-mybatis` **or** the `DataSource` wrapper, not both — the MyBatis one names
statements by mapper method rather than by parsing their SQL, and two of them record every statement
twice.

## Or annotate the method and write nothing

```java
@Traced(name = "order.checkout", args = {"tier=gold"}, includeMethodArgs = {"orderId"})
public Receipt checkout(String orderId, Card card) { ... }
```

```bash
java -javaagent:jeffrey-agent.jar=tracing.enabled=true -jar app.jar
```

The [Jeffrey agent](https://www.jeffrey-analyst.cafe) weaves the same span the explicit form builds,
so the method is not written around its own tracing. It nests under whatever span is in progress,
fails with the exception's type, and records only the arguments you name. Needs Java 25; without the
agent the annotation is inert and the method runs as written.

## Sixty seconds of tracing

An inbound request becomes the root of a trace, hand-written spans describe the application logic
inside it, and every statement or outbound call nests underneath — all through a `ScopedValue`, so
nothing is threaded through your signatures:

```java
// 1. The request event IS the root span (in a servlet filter, first in the chain)
HttpServerExchangeEvent event = new HttpServerExchangeEvent();
event.begin();
try {
    Tracer.inSpanOf(event, () -> {
        chain.doFilter(request, response);
        return null;
    });
} finally {
    event.end();
    if (event.shouldCommit()) {
        event.method = request.getMethod();
        event.uri = matchedTemplate(request);      // "/api/users/{id}", never the raw path
        event.statusCode = response.getStatus();
        event.commitSpan();
    }
}

// 2. Application logic becomes named spans (jeffrey.TraceSpan events)
Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
    Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
});

// 3. A statement (or outbound HTTP call) is a leaf — TracedEvents.emit is the
//    whole lifecycle: guard, begin, end on success, failed(e) on a throw (the
//    span shows red), commitSpan() stamping it under the span in progress
JdbcQueryEvent query = new JdbcQueryEvent("UserMapper.selectById", "UserMapper");
List<User> users = TracedEvents.emit(query,
        () -> doQuery(),
        (e, result) -> {
            e.sql = sql;
            e.rows = result != null ? result.size() : 0;
        });
```

Record and verify:

```bash
java -XX:StartFlightRecording=filename=app.jfr,settings=profile -jar app.jar
jfr print --events "jeffrey.*" app.jfr
```

Then upload `app.jfr` to Jeffrey — it auto-detects the event types and activates the matching
dashboards.

## Modules

| Artifact | What it is |
|---|---|
| `jeffrey-tracing` | the events and the `Tracer` API; zero dependencies |
| `jeffrey-tracing-test` | assertions over the spans in a recording, for your own tests; zero dependencies |
| `jeffrey-tracing-servlet` | the root-span filter; needs only `jakarta.servlet` |
| `jeffrey-tracing-spring` | Spring `@Configuration` you `@Import` explicitly; no Spring Boot dependency |
| `jeffrey-tracing-jdbc` | a `DataSource` wrapper recording every statement — JdbcTemplate, Hibernate, jOOQ and MyBatis alike |
| `jeffrey-tracing-hikari` | HikariCP metrics tracker: connection acquire/borrow/create/timeout, plus the periodic pool gauge |
| `jeffrey-tracing-mybatis` | MyBatis `Executor` interceptor naming every statement by its mapper method, and recording the values it ran with |
| `jeffrey-tracing-grpc` | gRPC server and client interceptors: an inbound call becomes a trace root, an outbound one a leaf |
| `jeffrey-tracing-spring-boot-starter` | auto-configuration: add it and write nothing |

## Event catalog

| JFR name | Class | Role in a trace |
|---|---|---|
| `jeffrey.HttpServerExchange` | `http.HttpServerExchangeEvent` | root span of an inbound request |
| `jeffrey.HttpClientExchange` | `http.HttpClientExchangeEvent` | leaf: outbound HTTP call |
| `jeffrey.GrpcServerExchange` | `grpc.GrpcServerExchangeEvent` | root span of an inbound call |
| `jeffrey.GrpcClientExchange` | `grpc.GrpcClientExchangeEvent` | leaf: outbound gRPC call |
| `jeffrey.JdbcQuery` / `JdbcInsert` / `JdbcUpdate` / `JdbcDelete` / `JdbcExecute` | `jdbc.statement.*` | leaf: one statement per event, split by verb |
| `jeffrey.JdbcStream` | `jdbc.statement.JdbcStreamEvent` | leaf: query consumed as a stream (deferred commit) |
| `jeffrey.TraceSpan` | `trace.TraceSpanEvent` | interior span, emitted by `Tracer.run`/`call`/`continueIn` and by `@Traced` |
| `jeffrey.TraceScope` | `trace.TraceScopeEvent` | where a re-entered span ran; emitted by `Tracer.reenter` only |
| `jeffrey.JdbcPoolStatistics` + `PooledJdbcConnection*` | `jdbc.pool.*` | not spans: pool gauges and durations |
| `jeffrey.Message` / `jeffrey.Alert` | `message.*` | not spans: operational notes and alerts |

Each package's `package-info` documents its emit patterns in detail; the `trace.Tracer` javadoc
covers the tracing model itself.

## The rules that make traces assemble

1. **One root per request, opened with `Tracer.inSpanOf`.** The inbound-request event *is* the
   root span; no separate span event is emitted for the same interval.
2. **Everything commits through `commitSpan()`.** It stamps an event that does not yet carry trace
   identity as a child of the span in progress, keeps one that does exactly as it is, and leaves
   the ids at zero when no span is bound (recorded, just untraced). A bare `commit()` skips both
   the stamp and the event's self-description — it is the deliberate opt-out, not the default.
   For a leaf committed in its own `finally`, `TracedEvents.emit` writes the whole lifecycle in
   one call (each package's docs show what it expands to).
3. **Failures are stated with `failed(throwable)`** — on any traced event — then rethrown. Events
   that derive a verdict from their own fields (HTTP status ≥ 400, gRPC status ≠ OK) only ever
   escalate; they never paint over a recorded failure.
4. **Names must be stable and low-cardinality**: the URI template, the mapper method id,
   `order.checkout`. Every distinct string enters the JFR per-chunk constant pool; the identity of
   an individual request lives in the trace id, which is already there.
5. **A trace does not cross a plain executor by itself.** `ScopedValue` propagates only through
   structured concurrency — wrap tasks with `Tracer.fork(...)`, or carry `Tracer.current()` and
   re-establish it with `Tracer.continueIn(parent, ...)`. For one operation arriving in callback
   pieces on foreign threads, open with `Tracer.openSpanOf(event)` and wrap each callback in
   `Tracer.reenter(ctx, ...)`.

## Volume control

Never bake thresholds into instrumentation — a dropped span orphans its children. Set them per
recording instead:

```bash
# Drop hand-written spans shorter than 1 ms
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceSpanEvent#threshold=1ms

# Keep re-entry nesting but stop recording where re-entered spans ran
-XX:StartFlightRecording=...,cafe.jeffrey.jfr.events.trace.TraceScopeEvent#enabled=false
```

## Instrumentation guides

The [`skills/`](skills) directory contains complete, framework-specific guides (also written to be
consumed by AI coding agents):

- [`jeffrey-traces-core`](skills/jeffrey-traces-core/SKILL.md) — the data model, emit rules,
  recording setup, verification. Read first.
- [`jeffrey-traces-spring-rest-server`](skills/jeffrey-traces-spring-rest-server/SKILL.md) — the
  servlet filter that roots every request's trace.
- [`jeffrey-traces-http-client`](skills/jeffrey-traces-http-client/SKILL.md) — the client
  interceptor for outbound calls.
- [`jeffrey-traces-mybatis`](skills/jeffrey-traces-mybatis/SKILL.md) — the MyBatis interceptor
  emitting one statement event per mapper call.
- [`jeffrey-traces-grpc`](skills/jeffrey-traces-grpc/SKILL.md) — the gRPC server and client
  interceptors.

Full documentation: [jeffrey-analyst.cafe](https://www.jeffrey-analyst.cafe).

## License

Apache License 2.0 — see [LICENSE](LICENSE).
