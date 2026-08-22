---
name: jeffrey-traces-grpc
description: Instrument gRPC with Jeffrey JFR events — a ServerInterceptor that makes every inbound call the root span of a trace, and a ClientInterceptor that records every outbound call as a leaf under the span that made it, both emitting GrpcServerExchangeEvent/GrpcClientExchangeEvent into Jeffrey Traces. Covers the jeffrey-events-grpc module, registration on plain gRPC and on Spring gRPC, and the callback problem that makes gRPC different from a servlet filter. Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification.
---

# Jeffrey Traces — gRPC Instrumentation

Read **`jeffrey-traces-core`** first — it defines the data model, the emit rules, the dependency,
recording configuration, and verification. This skill covers gRPC on both sides of the wire.

Your services and stubs need **zero changes**. An inbound call becomes the root span of its trace;
an outbound call becomes a leaf under whatever the caller was doing.

Rules recap (from the core skill) that this embodies:

- The inbound exchange event **is** the root span, so nothing your handler does needs to know it is
  being traced — spans opened inside it nest underneath.
- An outbound call is a **leaf**: it is committed with `commitSpan()`, which nests it under the span
  in progress. The work it triggers happens in another process this recording cannot see.
- Names are low-cardinality by construction: `package.Service/Method`, straight off the
  `MethodDescriptor`. Nothing derived from a message reaches the name.

---

## 1. Add the module, register the interceptors

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events-grpc</artifactId>
    <version><!-- latest release --></version>
</dependency>
```

```java
Server server = ServerBuilder.forPort(port)
        .intercept(new JfrGrpcServerInterceptor())    // every service on this server
        .build();

ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        .intercept(new JfrGrpcClientInterceptor())    // every stub on this channel
        .build();
```

Both are stateless and thread-safe: one instance per server or channel is enough, and registering
the same instance on several is fine.

On **Spring gRPC**, the server side is a bean marked global so it applies to every registered
service — which is how Jeffrey's own hub registers it:

```java
@Bean
@GlobalServerInterceptor
ServerInterceptor jfrGrpcServerInterceptor() {
    return new JfrGrpcServerInterceptor();
}
```

There is no starter for this and no auto-configuration: a channel is built by application code, not
handed out as a bean the starter could recognise, so the one line above is the integration.

## 2. Why gRPC is not a servlet filter

A servlet filter can wrap the whole request in one `try`/`finally` on one thread. A gRPC call cannot
be wrapped that way: `interceptCall` returns immediately, and the call then proceeds through
callbacks (`onMessage`, `onHalfClose`, `onClose`) that arrive on transport threads the application
does not control, possibly long afterwards.

Both interceptors therefore split the span from the thread that holds it:

- The span is **opened without binding** — `Tracer.openSpanOf(event)` — at the point where the trace
  identity is known. On the client that is the calling thread, so the outbound call becomes a child
  of the request being served; on the server there is nothing above it, so it is a root.
- Every callback runs inside `Tracer.reenter(span, …)`, which re-establishes the span for whatever
  the callback does and records a `jeffrey.TraceScope` event saying which thread it ran on. Work
  your handler does in a callback still nests under the call.
- The exchange is committed from `onClose`/`close`, the one callback that always arrives — for a
  success and for a failure alike.

You get this for free by using the module. It is the reason to use it rather than write your own.

## 3. What it records

| Field | Server | Client |
|---|---|---|
| span name | `package.Service/Method` | `package.Service/Method` |
| `kind` | `SERVER` (trace root) | `CLIENT` (leaf) |
| `service` / `method` | from the `MethodDescriptor` | from the `MethodDescriptor` |
| `authority` | — | the channel's target authority |
| `statusCode` | the gRPC status name (`OK`, `INTERNAL`, …) | the same |
| `requestSize` / `responseSize` | serialized protobuf sizes | serialized protobuf sizes |

Status handling escalates only: a call that already recorded a failure is not talked back down to
`OK` by a later status. A `Status` carrying a cause is recorded with `failed(cause)`, so the span
shows red and carries the exception type.

Message sizes are read from `MessageLite#getSerializedSize` when the message is a protobuf, which is
every generated stub. A non-protobuf marshaller records size 0 rather than guessing.

## 4. gRPC-specific pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Server spans present, client spans missing | the client interceptor is not registered — they are two separate registrations | `.intercept(new JfrGrpcClientInterceptor())` on the channel builder |
| Outbound calls appear as roots of their own traces | the call was made outside any bound span (a scheduler thread, an `@Async` method) | wrap the caller with `Tracer.run`, or hand the context over with `Tracer.fork`/`continueIn` |
| Streaming calls measured as ~0 ms | a hand-written interceptor that committed from `interceptCall` instead of `onClose` | use the module; it commits from the closing callback |
| Two events per call | the interceptor registered both globally and per-service | register once — globally on the server, once per channel on the client |
| Handler work not nested under the call | the handler dispatched to an executor without carrying the context | `Tracer.propagating(executor)`, or `Tracer.fork` at the hand-off |
