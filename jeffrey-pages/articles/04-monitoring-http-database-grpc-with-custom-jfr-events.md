# Monitoring HTTP, Database, and gRPC in Production with Custom JFR Events

JFR gives you deep visibility into the JVM — CPU usage, garbage collection, thread activity, memory allocation. But it tells you nothing about your application's business: which HTTP endpoints are slow, which SQL queries are expensive, or which gRPC services are failing. That gap is where most performance problems actually live.

The **Jeffrey Events** library bridges this gap. It defines custom JFR event types for HTTP traffic, database queries, connection pools, gRPC calls, and method tracing. These events are captured in the same JFR recording alongside standard JVM events, so a single file gives you both infrastructure metrics and application behavior. Jeffrey detects these events automatically and activates dedicated analysis dashboards — no configuration required.

This article — the fourth in our series on Java profiling with Jeffrey — shows you how to instrument your application, capture a recording, and analyze HTTP, database, and gRPC traffic in Jeffrey.

## Adding Jeffrey Events to Your Application

Jeffrey Events is a lightweight library available on Maven Central. Add the dependency to your Spring Boot application:

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events</artifactId>
    <version>0.10.0</version>
</dependency>
```

The library provides JFR event definitions and instrumentation hooks for common frameworks. Once integrated, your application emits structured events for every HTTP request served, every database query executed, every gRPC call made, and every connection borrowed from the pool.

These events are recorded by JFR with negligible overhead — the same low-impact mechanism the JVM uses for its own events. There are no external agents, no sidecars, no separate telemetry pipelines. The data flows directly into the JFR recording file.

When you profile with Async Profiler using `jfrsync=default`, these custom events are captured alongside CPU samples, allocation events, GC data, and all other JFR events. Upload the resulting `.jfr` file to Jeffrey, and it automatically detects which custom event types are present and activates the corresponding dashboards.

![Technology dashboards overview](images/release-notes/tech-stack/01-overview.png)
*Jeffrey automatically detects custom events and activates the relevant dashboards.*

## HTTP Server Analysis

The HTTP dashboard gives you a complete picture of your application's inbound request traffic. Every request is captured with its method, URI, status code, response time, and payload sizes.

### Overview Metrics

The header shows aggregate statistics at a glance:

- **Total request count** — How many requests were served during the recording
- **Max / P99 / P95 response time** — Latency distribution. The P99 tells you the worst experience for 1% of your users
- **Success rate** — Percentage of 2xx responses
- **4xx / 5xx error counts** — Client errors vs. server errors
- **Bytes transferred** — Total data sent and received

### Timeseries: Response Time and Request Count

The dual-axis timeseries is where patterns emerge. One axis shows response times (nanosecond precision), the other shows request count per time bucket.

![HTTP Server metrics timeline](images/release-notes/tech-stack/02-http-timeseries.png)
*Dual-axis timeseries: response time spikes correlated with request count.*

Look for correlations:
- **Response time spikes during high request volume** — Your application doesn't scale linearly. Investigate thread pool sizing, connection pool limits, or downstream bottlenecks.
- **Response time spikes during low request volume** — A single slow request is dragging the average. Check the slowest requests view.
- **Steady increase in response time** — Resource exhaustion over time (memory leak, connection pool drain, thread starvation).

### Slowest Requests

The slowest requests table shows individual requests ranked by response time, with full details: URI, HTTP method, status code, response time, request/response sizes, host, and timestamp.

![HTTP Server slowest requests](images/release-notes/tech-stack/03-http-slowest.png)
*Top slowest HTTP requests with full request details.*

This is often the fastest path to a root cause. If your P99 is 3 seconds but your P95 is 50 milliseconds, a handful of slow requests are the problem. The slowest requests table shows you exactly which endpoints and when.

### Per-Endpoint Drill-Down

Click any endpoint in the endpoint list to see its isolated metrics: request count, response time percentiles, error rate, and a dedicated timeseries. This lets you compare endpoints — is `/api/users` consistently fast while `/api/reports` is the outlier?

Jeffrey also shows status code and HTTP method distributions, helping you spot patterns like a surge in 429 (rate limited) or an unexpected volume of DELETE requests.

## HTTP Client Analysis

If your application makes outbound HTTP calls — to microservices, external APIs, or third-party services — Jeffrey captures those too. The HTTP client dashboard mirrors the server dashboard: overview metrics, timeseries, slowest calls, and per-endpoint breakdown.

This is critical for distributed systems. Your service might be fast internally, but if it waits 2 seconds for a downstream response, your users see 2-second latency. Client-side HTTP analysis pinpoints exactly which external dependency is the bottleneck.

## Database (JDBC) Analysis

The JDBC dashboard tracks every SQL statement executed through your connection pool: SELECT, INSERT, UPDATE, DELETE, and generic EXECUTE operations.

### Statement Timeseries

![Database metrics timeline](images/release-notes/tech-stack/04-jdbc-timeseries.png)
*JDBC timeseries showing execution time and statement count over time.*

The dual-axis timeseries shows execution time and statement count. Spikes in execution time often correlate with specific query patterns — a report that runs every 5 minutes, a batch job that floods the database, or a query that degrades as the table grows.

### Statement Groups and Distributions

Jeffrey groups SQL statements and shows the distribution of operations by type. This immediately reveals the read/write ratio of your application and whether any operation type dominates.

![Database distribution charts](images/release-notes/tech-stack/05-jdbc-distribution.png)
*SQL operation distribution and statement group breakdown.*

### Slowest Statements

The slowest statements table shows individual SQL executions ranked by duration. Each entry includes the full SQL text, operation type, execution time, rows processed, and whether it was a batch or LOB (Large Object) operation.

Common findings:
- **Full table scans** — SELECT statements that process millions of rows because of a missing index
- **N+1 queries** — Hundreds of fast individual queries that collectively take seconds (look for high statement count + moderate total time on the same query pattern)
- **Lock contention at the database level** — Long execution times on simple statements during concurrent access
- **Unparameterized queries** — The same logical query appearing with different literal values, preventing prepared statement caching

## Connection Pool Analysis

Connection pools are invisible until they become bottlenecks. Jeffrey tracks pool metrics including:

- **Peak connection count** — Maximum simultaneous connections used
- **Active connection count** — Average and peak active connections
- **Pending threads** — Threads waiting for a connection. If this number is consistently above zero, your pool is undersized.
- **Pending time percentage** — How much time the pool spent with queued waiters
- **Timeout count and rate** — Connection acquisition timeouts. Even one timeout means a request failed because no connection was available within the configured wait time.

![Connection pool analysis](images/feature-screenshots/custom_jdbc_pool.png)
*Connection pool dashboard showing utilization metrics and event timeseries.*

The pool timeseries shows connection acquisition patterns over time. A healthy pool has low, stable active connection counts with zero pending threads. A stressed pool shows increasing pending counts, growing acquisition times, and eventually timeouts.

**Practical sizing guidance:** If your peak active connections equal your max pool size and you see pending threads, the pool is too small. If your average active connections are 10% of max pool size, the pool is oversized — those idle connections consume database resources for nothing.

## gRPC Analysis

For applications using gRPC (increasingly common in microservice architectures), Jeffrey provides server and client dashboards that mirror the HTTP analysis pattern:

- **Overview metrics** — Call count, max/P99/P95 response times, success rate, error count, bytes sent/received
- **Per-service breakdown** — Metrics aggregated by gRPC service and method
- **Status code distribution** — gRPC status codes (OK, UNAVAILABLE, DEADLINE_EXCEEDED, etc.)
- **Payload size analysis** — Request and response size distributions, average and maximum payload sizes
- **Slowest calls** — Individual gRPC calls ranked by duration with full details
- **Traffic patterns** — Service-to-service communication volumes

The payload size analysis is unique to gRPC dashboards. Protobuf payloads are binary and compact, but oversized messages can still cause latency. If your P99 response time correlates with large response payloads, you may need pagination or streaming instead of single large responses.

## Method Tracing

Method tracing captures wall-clock execution time for instrumented methods. Unlike CPU flamegraphs (which sample periodically), method tracing records every invocation with its exact duration.

Jeffrey provides:

- **Overview** — Total invocations, total/max/P99/P95/average duration, unique method count
- **Slowest invocations** — Individual calls ranked by duration
- **Cumulated statistics** — Aggregated by method or by class, showing which methods consume the most total time
- **Flamegraph** — Wall-clock flamegraph built from tracing data, showing the full call tree with exact timing

**When to use method tracing vs. CPU flamegraphs:** CPU flamegraphs show you where the CPU is active. Method tracing shows you where wall-clock time goes — including time spent waiting for I/O, locks, or downstream services. If a method takes 500ms but only uses 10ms of CPU, the CPU flamegraph barely registers it, but method tracing captures the full 500ms.

## Connecting the Dots

The real power of Jeffrey Events is correlation. Because all events live in the same JFR recording and share the same timeline, you can connect application behavior to JVM behavior:

- **HTTP response time spike at 14:32** → Check the GC timeseries → Full GC pause at 14:32. The latency spike was caused by garbage collection, not your code.
- **JDBC execution time doubled** → Check the CPU flamegraph for that time range → Lock contention in the connection pool. The database is fast; threads are waiting for connections.
- **gRPC error rate increased** → Check thread statistics → Thread pool exhaustion. Your application ran out of handler threads.

This is the advantage of a unified profiling approach over scattered observability tools. A distributed tracing system shows you that a request was slow. Jeffrey shows you *why* — was it CPU, GC, memory pressure, lock contention, pool exhaustion, or the downstream service?

## What's Next

You now have application-level visibility alongside JVM profiling:

1. **HTTP traffic** — Request volumes, latency percentiles, error rates, slowest endpoints
2. **Database queries** — Execution times, statement analysis, slow query identification
3. **Connection pools** — Utilization, pending threads, timeout detection
4. **gRPC services** — Call metrics, payload sizes, status code distributions
5. **Method tracing** — Wall-clock timing for instrumented code paths

In the next article, we'll explore **AI-powered analysis** — asking questions about your recordings in natural language and getting answers backed by real data queries against the profiling database.

You can find the source code at [github.com/petrbouda/jeffrey](https://github.com/petrbouda/jeffrey), the events library at [github.com/petrbouda/jeffrey-events](https://github.com/petrbouda/jeffrey-events), and the full documentation at [jeffrey-analyst.cafe](https://jeffrey-analyst.cafe). To try it right now:

```bash
docker run -it --network host petrbouda/microscope-examples
```

Open [http://localhost:8080](http://localhost:8080) and explore the pre-loaded examples with custom event data.
