<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
-->

<script setup lang="ts">
import { onMounted } from 'vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'events', text: 'The Two Events', level: 2 },
  { id: 'fields', text: 'Fields and Derived Span Shape', level: 2 },
  { id: 'starter', text: 'Spring Boot: The Starter', level: 2 },
  { id: 'spring', text: 'Plain Spring: @Import', level: 2 },
  { id: 'servlet', text: 'Any Servlet Container', level: 2 },
  { id: 'manual-server', text: 'Writing the Filter Yourself', level: 2 },
  { id: 'client', text: 'Outbound Calls: the Client Event', level: 2 },
  { id: 'async-clients', text: 'Async Clients', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const starterDependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-tracing-spring-boot-starter</artifactId>
    <version><!-- latest release --></version>
</dependency>`;

const springImport = `@Configuration
@Import(JeffreyTracingConfiguration.class)
class ObservabilityConfiguration {

    /** Optional: the default records nothing beyond the request's shape. */
    @Bean
    HttpExchangeSettings jeffreyHttpExchangeSettings() {
        return new HttpExchangeSettings(true, true);   // capture query + path params
    }
}`;

const servletExample = `// jeffrey-tracing-servlet depends on jakarta.servlet and nothing else
HttpExchangeFilter filter = new HttpExchangeFilter(
        HttpRequestNaming.servletMapping(),          // or your own routing-aware naming
        HttpExchangeSettings.defaults());
// register it FIRST in the chain, for /*`;

const manualFilter = `public class JeffreyJfrHttpEventFilter extends OncePerRequestFilter {

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
}`;

const serverOutput = `jeffrey.HttpServerExchange {
  duration = 128 ms
  traceId = 6872570733206835563
  spanId = 4444722480460712002
  parentSpanId = 0                     // the trace root
  name = "GET /api/users/{id}"         // derived in describeSpan(): "{method} {uri}"
  kind = "SERVER"
  status = "UNSET"                     // would be ERROR from statusCode >= 400
  method = "GET"
  uri = "/api/users/{id}"              // the TEMPLATE — never the raw path
  statusCode = 200
  remoteHost = "10.0.4.17"
  remotePort = 55712
  requestLength = 0
  responseLength = 1834
}`;

const clientInterceptor = `// jeffrey-tracing-spring ships JfrClientHttpRequestInterceptor as a bean;
// attach it where you build the client:
RestTemplate restTemplate = new RestTemplate();
restTemplate.getInterceptors().add(interceptor);   // the bean from the configuration`;

const manualClient = `public class JeffreyJfrRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {

        // TracedEvents.emit is the whole leaf lifecycle: guard, begin, end on
        // success, failed(e) on the exception path (a transport failure that
        // never produced a status code shows red), commitSpan() stamping the
        // event under the span in progress — usually the server exchange of
        // the request being served. The IOException propagates through typed.
        HttpClientExchangeEvent event = new HttpClientExchangeEvent();
        return TracedEvents.emit(event,
                () -> execution.execute(request, body),
                (e, response) -> {
                    e.method = request.getMethod().name();
                    // Low-cardinality: host + path with variable segments
                    // collapsed, ideally the URI template you expanded.
                    e.uri = request.getURI().getHost() + normalizePath(request.getURI().getPath());
                    e.remoteHost = request.getURI().getHost();
                    e.remotePort = request.getURI().getPort();
                    e.requestLength = body.length;
                    // response is null when the call threw before answering.
                    e.statusCode = response != null ? response.getStatusCode().value() : 0;
                });
    }
}`;

const clientTree = `trace 8c1d33f0…
└─ GET /api/orders/{id}          HttpServerExchangeEvent   SERVER  (the inbound request)
   ├─ order.load                 jeffrey.TraceSpan         INTERNAL
   └─ payments.example.com/api/charges   HttpClientExchangeEvent  CLIENT  leaf
        └— the downstream work happens in another process this recording cannot see`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="HTTP Events"
      icon="bi bi-globe2"
    />

    <div class="docs-content">
      <p>Two event types cover HTTP: <code>jeffrey.HttpServerExchange</code> — one per inbound request, opened as the <strong>root span</strong> of that request's trace — and <code>jeffrey.HttpClientExchange</code> — one per outbound call, committed as a <strong>leaf</strong> under whatever span made it. Your controllers need zero changes; the instrumentation lives in a filter and a client interceptor.</p>

      <h2 id="events">The Two Events</h2>

      <table>
        <thead>
          <tr>
            <th>Event</th>
            <th>Kind</th>
            <th>Role</th>
            <th>Opened with</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.HttpServerExchange</code></td>
            <td><code>SERVER</code></td>
            <td>Root span of the inbound request; everything traced while serving it nests underneath</td>
            <td><code>Tracer.inSpanOf</code> in a filter registered <strong>first</strong> in the chain</td>
          </tr>
          <tr>
            <td><code>jeffrey.HttpClientExchange</code></td>
            <td><code>CLIENT</code></td>
            <td>Leaf: the downstream work happens in another process this recording cannot see</td>
            <td><code>TracedEvents.emit</code> / <code>commitSpan()</code> in a client interceptor</td>
          </tr>
        </tbody>
      </table>

      <h2 id="fields">Fields and Derived Span Shape</h2>

      <p>Both extend <code>AbstractHttpExchangeEvent</code> (which extends <code>AbstractTracedEvent</code>) and carry: <code>method</code>, <code>uri</code>, <code>statusCode</code>, <code>remoteHost</code>, <code>remotePort</code>, <code>mediaType</code>, <code>queryParams</code> (JSON), <code>pathParams</code> (JSON), <code>requestLength</code> and <code>responseLength</code>.</p>

      <p>The span shape is derived for you in <code>describeSpan()</code>, invoked by <code>commitSpan()</code>: the name is <code>"{method} {uri}"</code> (that template is also declared on the class with <code>@Span</code>, so it travels in the recording's metadata), and the status turns <code>ERROR</code> from <code>statusCode&nbsp;≥&nbsp;400</code>. <strong>Never set <code>name</code> or <code>status</code> yourself</strong> — a transport failure that produced no status code is recorded with <code>event.failed(throwable)</code>, and the derived verdict never paints over it.</p>

      <DocsCodeBlock :code="serverOutput" language="text" />

      <DocsCallout type="warning">
        <strong><code>uri</code> must be the matched template</strong> — <code>/api/users/{id}</code>, never the raw path. The HTTP dashboard aggregates per endpoint on it and the span name derives from it; a raw path produces one "operation" per entity id, per static asset and per mistyped URL. A request that matched no handler is named <code>&lt;unmatched&gt;</code>.
      </DocsCallout>

      <h2 id="starter">Spring Boot: The Starter</h2>

      <DocsCodeBlock :code="starterDependency" language="xml" />

      <p>That is the whole integration. The auto-configuration registers the filter first in the chain, names spans by the matched Spring MVC handler pattern, and completes asynchronous requests from an <code>AsyncListener</code>. It backs off entirely if you define your own filter, naming strategy or settings. Tune it with <code>jeffrey.tracing.*</code>:</p>

      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.tracing.enabled</code></td>
            <td><code>true</code></td>
            <td>Turn the instrumentation off without removing the dependency</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.url-patterns</code></td>
            <td><code>/*</code></td>
            <td>Which requests the filter sees</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.order</code></td>
            <td><code>HIGHEST_PRECEDENCE</code></td>
            <td>Filter order; keep it first so security, routing and data access all happen inside the span</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.jdbc-enabled</code></td>
            <td><code>true</code></td>
            <td>Wrap every <code>DataSource</code> bean so statements are recorded — see <router-link to="/docs/tracing/jdbc-events">JDBC Events</router-link></td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.hikari-enabled</code></td>
            <td><code>true</code></td>
            <td>Give HikariCP pools a Jeffrey metrics tracker</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.mybatis-enabled</code></td>
            <td><code>true</code></td>
            <td>Name statements by their mapper method for applications with a <code>SqlSessionFactory</code>; stands the <code>DataSource</code> wrapper down so nothing records twice</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.mybatis-capture-parameters</code></td>
            <td><code>true</code></td>
            <td>Record the values a MyBatis statement was bound with</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.mybatis-max-parameter-length</code></td>
            <td><code>256</code></td>
            <td>Truncate longer parameter values</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.capture-query-params</code></td>
            <td><code>false</code></td>
            <td>Record query-string parameters on the event</td>
          </tr>
          <tr>
            <td><code>jeffrey.tracing.capture-path-params</code></td>
            <td><code>false</code></td>
            <td>Record the route's template variables on the event</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info">
        <strong>Both HTTP capture flags are off by default, deliberately.</strong> A recording is a file that gets uploaded, shared and kept, and query strings routinely carry access tokens, e-mail addresses and search terms. Turn them on only for an application whose parameters you know are safe to keep.
      </DocsCallout>

      <h2 id="spring">Plain Spring: @Import</h2>

      <p><code>jeffrey-tracing-spring</code> carries the same beans with no Spring Boot dependency and no auto-configuration — nothing happens until you ask:</p>

      <DocsCodeBlock :code="springImport" language="java" />

      <p>That gives you the <code>HttpExchangeFilter</code> as a bean; register it the way your stack does (<code>web.xml</code>, or <code>AbstractAnnotationConfigDispatcherServletInitializer#getServletFilters</code>) — <strong>first in the chain</strong>. Using both the starter and this <code>@Import</code> is safe: the auto-configuration is guarded with <code>@ConditionalOnMissingBean</code> and yields one filter, not two.</p>

      <h2 id="servlet">Any Servlet Container</h2>

      <DocsCodeBlock :code="servletExample" language="java" />

      <p>The one thing a container cannot answer is what a request should be <em>called</em>, so the filter asks a <code>HttpRequestNaming</code>. The built-in strategy names requests by their servlet mapping pattern (<code>/api/*</code>) — already low-cardinality because a mapping is declared, not derived from the request. Supply your own to use a router's matched template; that is exactly what the Spring module does with Spring MVC's best-matching handler pattern.</p>

      <h2 id="manual-server">Writing the Filter Yourself</h2>

      <p>For stacks the modules don't cover — or to see precisely what they do — this is the whole filter:</p>

      <DocsCodeBlock :code="manualFilter" language="java" />

      <p>Note what this simple version does <em>not</em> handle: an asynchronous request is measured only until the container thread returns, so it appears to take microseconds. The starter's filter completes such requests from an <code>AsyncListener</code> instead.</p>

      <h2 id="client">Outbound Calls: the Client Event</h2>

      <DocsCodeBlock :code="clientInterceptor" language="java" />

      <p>It is only a bean, not attached automatically — applications build clients in too many ways for a starter to guess. By default it records host and path with the query string dropped (that is where ids and tokens live), and accepts a <code>Function&lt;URI, String&gt;</code> to collapse variable path segments into a template. Written by hand, the same interceptor is:</p>

      <DocsCodeBlock :code="manualClient" language="java" />

      <DocsCodeBlock :code="clientTree" language="text" />

      <h2 id="async-clients">Async Clients</h2>

      <p>A blocking interceptor shape does not fit a client whose response arrives via callbacks on threads you don't control (WebClient, async HttpClient). Use the callback pattern (<router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> + <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link>): <code>Tracer.openSpanOf(event)</code> when the call starts (on the thread whose span it belongs to), <code>Tracer.reenter(ctx, ...)</code> around each callback, and <code>event.commitSpan()</code> at completion. <code>openSpanOf</code> stamps the ids eagerly, so a completion running after the enclosing binding is gone still carries the right identity.</p>

      <h2 id="pitfalls">Pitfalls</h2>

      <table>
        <thead>
          <tr>
            <th>Symptom</th>
            <th>Cause</th>
            <th>Fix</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>One "endpoint" per user/entity in the HTTP dashboard</td>
            <td>Raw URI recorded instead of the template</td>
            <td>Use the starter (Spring MVC naming), or supply a routing-aware <code>HttpRequestNaming</code></td>
          </tr>
          <tr>
            <td>SQL spans not nested under requests</td>
            <td>Filter registered after work-dispatching filters, or missing entirely</td>
            <td>Keep <code>jeffrey.tracing.order</code> first; check <code>url-patterns</code> covers the endpoint</td>
          </tr>
          <tr>
            <td>Request span missing, children promoted to roots</td>
            <td>The root event was re-stamped by hand</td>
            <td>Never call <code>Tracer.stamp</code> on an <code>inSpanOf</code> event; commit with <code>commitSpan()</code></td>
          </tr>
          <tr>
            <td>5xx/4xx not red in Traces</td>
            <td><code>statusCode</code> not set before commit</td>
            <td>The starter handles this; by hand, set it in the <code>finally</code></td>
          </tr>
          <tr>
            <td>Async requests measured as ~0 ms</td>
            <td>Event completed when the container thread returned</td>
            <td>Use the starter's filter, which completes from an <code>AsyncListener</code></td>
          </tr>
          <tr>
            <td>Calls in the HTTP Client dashboard but not in Traces</td>
            <td>Committed with <code>commit()</code></td>
            <td><code>TracedEvents.emit</code>, or <code>commitSpan()</code> in the <code>finally</code></td>
          </tr>
          <tr>
            <td>Client calls are roots of their own one-span traces</td>
            <td>Call ran outside a bound span (no server filter, <code>@Async</code>, scheduled job)</td>
            <td>Register the root filter; wrap background work with <code>Tracer.fork</code>/<code>continueIn</code></td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
