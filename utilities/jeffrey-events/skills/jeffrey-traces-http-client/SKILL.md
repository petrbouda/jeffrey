---
name: jeffrey-traces-http-client
description: Instrument outbound HTTP calls with Jeffrey JFR events — a ClientHttpRequestInterceptor (RestTemplate, or any HTTP client with an interception point) emitting HttpClientExchangeEvent as a leaf span nested under the request being served. Use when the application calls other services over HTTP and those calls should appear in Jeffrey's HTTP Client dashboard and Traces. Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification; inbound requests are covered by jeffrey-traces-spring-rest-server.
---

# Jeffrey Traces — Outbound HTTP Client Instrumentation

Read **`jeffrey-traces-core`** first — it defines the data model, the emit
rules, the dependency, recording configuration, and verification. This skill
applies those rules to outbound HTTP calls. Inbound request handling is the
companion skill **`jeffrey-traces-spring-rest-server`**.

The client exchange is a **leaf span**: the downstream work happens in another
process this recording cannot see, so the event is committed with
`stampAndCommit()` — nesting it under the span in progress (usually the server
exchange of the request being served) — never with `Tracer.inSpanOf`.

Rules recap (from the core skill) that this code embodies:

- Leaf events commit with `stampAndCommit()` in their own `finally`; a bare
  `commit()` silently drops the call from every trace.
- `uri` must be low-cardinality: the URI template you expanded, or host + path
  with variable segments collapsed — never a URL containing an entity id.
- `statusCode` drives span status automatically: `describeSpan()` marks
  `>= 400` as `ERROR` — never set `name`/`status` yourself.

---

## 1. RestTemplate interceptor

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

## 2. Registration

```java
restTemplate.getInterceptors().add(new JeffreyJfrRestTemplateInterceptor());
```

Or, in Spring Boot, via a `RestTemplateCustomizer`/`RestTemplateBuilder` bean
so every built `RestTemplate` carries it:

```java
@Bean
public RestTemplateCustomizer jeffreyJfrRestTemplateCustomizer() {
    return restTemplate ->
            restTemplate.getInterceptors().add(new JeffreyJfrRestTemplateInterceptor());
}
```

## 3. Async clients (WebClient, async HttpClient)

A blocking interceptor shape does not fit a client whose response arrives via
callbacks on threads you don't control. Use the callback pattern from the core
skill instead (0.13.0+): `Tracer.openSpanOf(event)` when the call starts (on
the thread whose span it belongs to), `Tracer.reenter(ctx, ...)` around each
callback, and `event.commitSpan()` at completion. `openSpanOf` stamps the ids
eagerly, so a completion running after the enclosing binding is gone still
carries the right identity.

## 4. Client-side pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Calls in the HTTP Client dashboard but not in Traces | committed with `commit()` | `stampAndCommit()` in the `finally` |
| Client calls are roots of their own one-span traces | call ran outside a bound span (no server filter, `@Async`, scheduled job) | register the root-span filter (`jeffrey-traces-spring-rest-server`); wrap background work with `Tracer.fork`/`continueIn` |
| One "endpoint" per entity id | raw expanded URL recorded as `uri` | record the template / normalized path |
| Connection failures look green | exception path leaves `statusCode = 0` and status `UNSET` | acceptable (UNSET ≠ OK), or set `statusCode` from the exception mapping if you need red spans for transport errors |
| Async call measured as ~0 ms | event ended when the request was *sent*, not when the response arrived | complete the event from the response callback (deferred-commit pattern, §3) |
