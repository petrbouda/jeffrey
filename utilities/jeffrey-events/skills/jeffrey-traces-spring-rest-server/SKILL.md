---
name: jeffrey-traces-spring-rest-server
description: Instrument the server side of a Spring MVC (@RestController) application with Jeffrey JFR events so every inbound request becomes the root span of a trace. Covers the jeffrey-events-spring-boot-starter (one dependency, no code), the explicit @Import route for plain Spring, registering the filter on any servlet container, and the hand-written filter for older releases. Requires the jeffrey-traces-core skill for the data model, emit rules, recording setup, and verification; outbound HTTP calls are covered by jeffrey-traces-http-client.
---

# Jeffrey Traces — Spring REST Server Instrumentation

Read **`jeffrey-traces-core`** first — it defines the data model, the emit rules, the dependency,
recording configuration, and verification. This skill covers inbound request handling. Outbound
HTTP calls are the companion skill **`jeffrey-traces-http-client`**.

Your `@RestController` classes need **zero changes**. One `HttpServerExchangeEvent` per request,
opened as the root span of that request's trace — the same instrumentation Jeffrey's own
application runs on.

Rules recap (from the core skill) that this embodies:

- The exchange event **is** the root span: `Tracer.inSpanOf` stamps it when the span opens, so
  everything traced further down nests underneath it and no separate span event is emitted.
- The span name must be low-cardinality: the **matched URI template** (`/api/users/{id}`), never
  the raw path.
- `statusCode` drives span status automatically: `describeSpan()` marks `>= 400` as `ERROR` —
  never set `name`/`status` yourself.

---

## 1. Spring Boot: add the starter, write nothing

```xml
<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events-spring-boot-starter</artifactId>
    <version><!-- latest release --></version>
</dependency>
```

That is the whole integration. The auto-configuration registers the filter first in the chain,
names spans by the matched Spring MVC handler pattern, and completes asynchronous requests from an
`AsyncListener`. It backs off entirely if you define your own filter, naming strategy or settings.

Tune it with `jeffrey.tracing.*`:

| Property | Default | Meaning |
|---|---|---|
| `jeffrey.tracing.enabled` | `true` | Turn the instrumentation off without removing the dependency |
| `jeffrey.tracing.url-patterns` | `/*` | Which requests the filter sees |
| `jeffrey.tracing.order` | `HIGHEST_PRECEDENCE` | Filter order; keep it first so security, routing and data access all happen inside the span |
| `jeffrey.tracing.jdbc-enabled` | `true` | Wrap every `DataSource` bean so statements are recorded |
| `jeffrey.tracing.hikari-enabled` | `true` | Give HikariCP pools a Jeffrey metrics tracker |
| `jeffrey.tracing.capture-query-params` | `false` | Record query-string parameters on the event |
| `jeffrey.tracing.capture-path-params` | `false` | Record the route's template variables on the event |

**Both capture flags are off by default, deliberately.** A recording is a file that gets uploaded,
shared and kept, and query strings routinely carry access tokens, e-mail addresses and search
terms. Turn them on for an application whose parameters you know are safe to keep — as Jeffrey does
for itself.

## 2. Plain Spring, or Boot without auto-configuration: `@Import`

`jeffrey-events-spring` carries the same beans with **no Spring Boot dependency and no
auto-configuration**. It ships no `AutoConfiguration.imports` entry, so having it on the classpath
registers nothing at all until you ask:

```java
@Configuration
@Import(JeffreyTracingConfiguration.class)
class ObservabilityConfiguration {

    /** Optional: the default records nothing beyond the request's shape. */
    @Bean
    HttpExchangeSettings jeffreyHttpExchangeSettings() {
        return new HttpExchangeSettings(true, true);
    }
}
```

That gives you the `HttpExchangeFilter` as a bean. Plain Spring MVC has no `FilterRegistrationBean`,
so register it the way your stack does — `web.xml`, or
`AbstractAnnotationConfigDispatcherServletInitializer#getServletFilters` — **first in the chain**.

Using both the starter and this `@Import` is safe: the auto-configuration is guarded with
`@ConditionalOnMissingBean` and yields one filter, not two.

## 3. Any servlet container, no Spring at all

`jeffrey-events-servlet` depends on `jakarta.servlet` and nothing else:

```java
HttpExchangeFilter filter = new HttpExchangeFilter(
        HttpRequestNaming.servletMapping(),          // or your own routing-aware naming
        HttpExchangeSettings.defaults());
```

The one thing a container cannot answer is what a request should be *called*, so the filter asks a
`HttpRequestNaming`. The built-in strategy names requests by their servlet mapping pattern
(`/api/*`), which is already low-cardinality because a mapping is declared rather than derived from
the request. Supply your own to use a router's matched template — that is exactly what
`jeffrey-events-spring` does with Spring MVC's best-matching handler pattern.

## 4. Older releases: the hand-written filter

Before the integration modules existed, this was copy-paste. If you are pinned to such a release,
the filter is:

```java
public class JeffreyJfrHttpEventFilter extends OncePerRequestFilter {

    private static final String UNMATCHED_URI = "<unmatched>";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpServerExchangeEvent event = new HttpServerExchangeEvent();
        if (!event.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        event.begin();
        try {
            // The exchange event IS the root span: inSpanOf stamps it and binds the context.
            try {
                Tracer.inSpanOf(event, () -> {
                    filterChain.doFilter(request, response);
                    return null;
                });
            } catch (IOException | ServletException | RuntimeException e) {
                // Tracer's CallableOp infers one thrown type, which widens to Exception for a body
                // throwing both IOException and ServletException; narrow it back.
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.uri = resolveTemplateUri(request);     // matched pattern, never the raw path
                event.method = request.getMethod();
                event.statusCode = response.getStatus();
                event.commitSpan();                          // inSpanOf already stamped the ids
            }
        }
    }

    private static String resolveTemplateUri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isEmpty()) {
            String contextPath = request.getContextPath();
            return (contextPath == null || contextPath.isEmpty()) ? s : contextPath + s;
        }
        return UNMATCHED_URI;
    }
}
```

Register it with `FilterRegistrationBean` at `Ordered.HIGHEST_PRECEDENCE` for `/*`. Note what this
version does *not* handle: an asynchronous request is measured only until the container thread
returns, so it appears to take microseconds. The starter's filter completes such requests from an
`AsyncListener` instead.

## 5. Why the details matter for the database

- `uri` must be the **template** (`/api/users/{id}`) — Jeffrey's HTTP dashboard aggregates per
  endpoint on it, and the span name (`GET /api/users/{id}`) is derived from it. A request that
  matched no handler is named `<unmatched>` rather than by its raw path, which would otherwise
  produce one "operation" per static asset and per mistyped URL.
- `statusCode` drives span status: `>= 400` becomes `ERROR` automatically.

## 6. Server-side pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| One "endpoint" per user/entity in the HTTP dashboard | raw URI recorded instead of the template | use the starter (Spring MVC naming), or supply a routing-aware `HttpRequestNaming` |
| SQL spans not nested under requests | filter registered after work-dispatching filters, or missing entirely | keep `jeffrey.tracing.order` first; check `url-patterns` covers the endpoint |
| Request span missing, children promoted to roots | the root event was re-stamped by hand | never call `Tracer.stamp` on an `inSpanOf` event; commit with `commitSpan()` |
| 5xx/4xx not red in Traces | `statusCode` not set before commit | the starter handles this; by hand, set it in the `finally` |
| Async requests measured as ~0 ms | event completed when the container thread returned | use the starter's filter, which completes from an `AsyncListener` |
| Two request events per call | a hand-registered filter alongside the starter | the starter backs off on `@ConditionalOnMissingBean`; remove the manual registration |
