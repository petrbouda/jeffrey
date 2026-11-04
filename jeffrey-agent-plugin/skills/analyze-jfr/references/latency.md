# Latency: requests, calls and the populations that are slow

"This endpoint is slow" — what the traces, the technology dashboards and the trace attributes each
answer, and in which order.

## Latency: work the traces first

"This endpoint is slow" is a traces question before it is a flamegraph question. When the profile has
no traces, `http_overview` and `jdbc_overview` answer the same question in aggregate — see the
technology families below. With traces, they answer it request by request:

1. `traces_overview` — totals, and how many **notifications** were raised inside traces.
   Notifications are `jeffrey.Notification` events an instrumented application emits about
   itself (a pool exhausted, a fallback taken). When any are `CRITICAL` or `HIGH`, call
   `traces_notifications` *before* exporting a trace: such a notification usually names the cause
   the span tree only shows the cost of, and its exemplar trace ids are the ones worth exporting.
2. `traces_operations` — sorted by `TOTAL_TIME` by default, which is where the wall-clock went.
   An operation is the triple `(name, kind, eventType)`, not a name alone: an inbound
   `GET /orders` and an outbound call to the same path are different operations.
3. `traces_operationExport` for the population, then `traces_slowestTraces` → `traces_traceExport`
   for one exemplar, then `traces_spanFlamegraphExport` for the frames inside a single slow span.

## The edges of the application: `http_`, `jdbc_`, `grpc_`, `methodtracing_`

Where `jvm_` answers for the machine, these answer for what the application did at its boundaries.
Each `_overview` is the whole dashboard in one call — the header totals, the entities ranked, the
status breakdown and the slowest individual operations — so reach for a drill-down only to narrow to
one endpoint, service or statement group.

| Tool | The question it answers |
|---|---|
| `http_overview` | Requests, response-time percentiles, success rate, 4xx/5xx, endpoints by traffic, slowest requests |
| `http_endpoint` | The same for one URI, taken from the endpoints list |
| `jdbc_overview` | Statement count and percentiles, the operation mix, statement groups by cost, slowest statements with their SQL |
| `jdbc_statementGroup` | The same, narrowed to one group |
| `jdbc_pools` | Configured min/max against peak and average use, threads that waited, acquisition timeouts |
| `grpc_overview` / `grpc_service` | gRPC latency overall and per service, broken down by method |
| `grpc_traffic` | Message sizes rather than timings — where an oversized payload shows up |
| `methodtracing_overview` / `_slowest` / `_timing` | Instrumented method timings: by cost, the worst invocations, the JVM's own aggregates |

Three things worth knowing before reading them:

- **"This endpoint is slow" starts here, not in a flamegraph.** `http_overview` names the endpoint
  and `jdbc_overview` says whether the database is the reason, in two calls. Go to `traces_` when
  the question is *which request* and what it did span by span, and to `flamegraph_export` once the
  question is which frames burned the time.
- **Slow requests whose statements are all fast are usually waiting for a connection.** That is
  `jdbc_pools`, and nothing in the statement view can show it — a pool with acquisition timeouts
  makes every query look fine while the request waits in front of them.
- **Both directions, and they are different questions.** `direction: SERVER` is what the application
  was asked to do; `CLIENT` is what it asked of somebody else, where a slow figure belongs to a
  dependency and the only local fixes are to call less often or stop waiting. They are gated
  separately, so "no client-side data" means the recording captured no outbound calls, not that the
  dashboard is broken. The per-second chart series are left out on purpose — the percentiles carry
  the same information, and the shape over time is what `timeline_` and the `uiLink` are for.

An event type the recording never captured is reported as `status: NOT_RECORDED` with a `reason`
rather than as a zeroed dashboard: "no HTTP server data" is a finding about the profiler's
configuration, not a healthy service.

## Which population, not which operation: trace attributes

`traces_operations` groups by the shape of the request. That cannot answer the question most latency
investigations actually end at — one population is slow and the rest is fine. The same endpoint,
fast for almost everyone and terrible for one tenant, has a healthy average and a p99 nobody can
locate.

1. `traces_attributeKeys` — what the spans recorded. A key is the triple `(source, owner, key)`, and
   the other two tools take all three; an `EVENT_FIELD` always has an owner, an `ATTRIBUTE` usually
   does not.
2. `traces_attributeValues` — one key split into its values, each with its own p50, p95, max and
   error count. A value whose p95 stands apart from the rest names the population. Trace counts do
   not sum to the profile's total: a trace whose spans recorded two values counts under both.
3. `traces_attributeSearch` — the individual traces carrying one value, whose ids go to
   `traces_traceExport`. More than one page: `hasMore` and `nextCursor`, passed back as `cursor`.

An attribute says *which* population was slow and never *why*. The span tree of one of its traces
says why.
