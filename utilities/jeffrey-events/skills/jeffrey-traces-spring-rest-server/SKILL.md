---
name: jeffrey-traces-spring-rest-server
description: Instrument the server side of a Spring MVC (@RestController) application with Jeffrey JFR events — a servlet filter that emits HttpServerExchangeEvent per inbound request and opens it as the root span of the request's trace via Tracer.inSpanOf. Use when wiring Jeffrey Traces into Spring Boot / Spring MVC request handling. Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification; outbound HTTP calls are covered by jeffrey-traces-http-client.
---

# Jeffrey Traces — Spring REST Server Instrumentation

Read **`jeffrey-traces-core`** first — it defines the data model, the emit
rules, the dependency, recording configuration, and verification. This skill
applies those rules to inbound request handling in Spring MVC. Outbound HTTP
calls are the companion skill **`jeffrey-traces-http-client`**.

Your `@RestController` classes need **zero changes**. The correct integration
point is a servlet filter: one `HttpServerExchangeEvent` per request, opened
as the root span of the request's trace — the same pattern Jeffrey's own
application uses.

Rules recap (from the core skill) that this code embodies:

- The exchange event **is** the root span: `Tracer.inSpanOf` stamps it when the
  span opens — the `finally` block only fills HTTP fields and calls
  `commitSpan()`, never `stampAndCommit()` (re-stamping would mint a fresh span
  id and orphan every child).
- The span name must be low-cardinality: the **matched URI template**
  (`/api/users/{id}`), never the raw path.
- `statusCode` drives span status automatically: `describeSpan()` marks
  `>= 400` as `ERROR` — never set `name`/`status` yourself.

---

## 1. The root-span filter

Register it **first in the filter chain** so everything — security, MVC,
data access — happens inside the span.

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

## 2. Registration (Spring Boot)

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

## 3. Why the details matter for the database

- `uri` must be the **template** (`/api/users/{id}`) — Jeffrey's HTTP dashboard
  aggregates per endpoint on it, and the span name (`GET /api/users/{id}`) is
  derived from it in `describeSpan()`.
- `statusCode` drives span status: `>= 400` becomes `ERROR` automatically.
- Optionally fill `queryParams`/`pathParams` as JSON object strings (Spring
  exposes path variables via `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`)
  — they become searchable detail on the exchange, but scrub anything
  sensitive: the recording and the profile database contain them verbatim.

## 4. Server-side pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| One "endpoint" per user/entity in the HTTP dashboard | raw URI recorded instead of the template | `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`; a fixed `<unmatched>` label for handler-less requests |
| SQL spans not nested under requests | filter registered after work-dispatching filters, or missing entirely | `Ordered.HIGHEST_PRECEDENCE`, URL pattern `/*` |
| Request span missing, children promoted to roots | root committed with `stampAndCommit()` (re-stamped) | `commitSpan()` for the `inSpanOf` event |
| 5xx/4xx not red in Traces | `statusCode` not set before commit | set it in the `finally`, before `commitSpan()` |
| Async servlet requests measured wrong | interval ends when the container thread returns, not when the response completes | complete the event from an `AsyncListener`, stamping eagerly with `Tracer.stamp` + `commitSpan()` (deferred-commit pattern in the core skill) |
