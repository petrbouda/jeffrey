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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsSpanTree from '@/components/docs/DocsSpanTree.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'events', text: 'The Two Events', level: 2 },
  { id: 'fields', text: 'Fields and Derived Span Shape', level: 2 },
  { id: 'servlet', text: 'Any Servlet Container', level: 2 },
  { id: 'naming', text: 'Naming: What a Container Cannot Answer', level: 2 },
  { id: 'manual-server', text: 'Writing the Filter Yourself', level: 2 },
  { id: 'client', text: 'Outbound Calls: the Client Event', level: 2 },
  { id: 'async-clients', text: 'Async Clients', level: 2 },
  { id: 'spring-support', text: 'Using Spring Boot?', level: 2 },
  { id: 'pitfalls', text: 'Pitfalls', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const servletExample = `// jeffrey-tracing-servlet depends on jakarta.servlet and nothing else
HttpExchangeFilter filter = new HttpExchangeFilter(
        HttpRequestNaming.servletMapping(),          // or your own routing-aware naming
        HttpExchangeSettings.defaults());
// register it FIRST in the chain, for /*`;

const manualFilter = `public class JeffreyJfrHttpEventFilter implements Filter {

    // The one thing the container cannot answer — see "Naming" above.
    private final HttpRequestNaming naming;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        HttpServerExchangeEvent event = new HttpServerExchangeEvent();
        if (!event.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        event.begin();
        try {
            // The exchange event IS the root span: inSpanOf stamps it and binds the context.
            try {
                Tracer.inSpanOf(event, () -> {
                    chain.doFilter(request, response);
                    return null;
                });
            } catch (IOException | ServletException | RuntimeException e) {
                // Tracer infers one thrown type, which widens to Exception for a body
                // throwing both IOException and ServletException. Narrow it back to
                // what a filter may declare.
                throw e;
            } catch (Exception e) {
                throw new ServletException(e);
            }
        } finally {
            event.end();
            if (event.shouldCommit()) {
                event.uri = naming.uri(httpRequest);        // the template, never the raw path
                event.method = httpRequest.getMethod();
                event.statusCode = httpResponse.getStatus();
                event.commitSpan();                          // inSpanOf already stamped the ids
            }
        }
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

const manualClient = `// The JDK's own client — no dependency beyond java.net.http. Any other client
// wraps the same way: the shape is the event, not the library.
public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) throws IOException {

    // TracedEvents.emit is the whole leaf lifecycle: guard, begin, end on
    // success, failed(e) on the exception path (a transport failure that
    // never produced a status code shows red), commitSpan() stamping the
    // event under the span in progress — usually the server exchange of
    // the request being served. The IOException propagates through typed.
    HttpClientExchangeEvent event = new HttpClientExchangeEvent();
    return TracedEvents.emit(event,
            () -> client.send(request, handler),
            (e, response) -> {
                e.method = request.method();
                // Low-cardinality: host + path with variable segments
                // collapsed, ideally the URI template you expanded.
                e.uri = request.uri().getHost() + normalizePath(request.uri().getPath());
                e.remoteHost = request.uri().getHost();
                e.remotePort = request.uri().getPort();
                // response is null when the call threw before answering.
                e.statusCode = response != null ? response.statusCode() : 0;
            });
}`;

const clientSpans = [
  { depth: 0, name: 'GET /api/orders/{id}', kind: 'SERVER' as const, start: 0, duration: 128,
    event: 'HttpServerExchangeEvent', note: 'the inbound request' },
  { depth: 1, name: 'order.load', kind: 'INTERNAL' as const, start: 6, duration: 41,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'payments.example.com/api/charges', kind: 'CLIENT' as const,
    start: 52, duration: 62, event: 'HttpClientExchangeEvent', note: 'leaf' }
];
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

      <h2 id="servlet">Any Servlet Container</h2>

      <DocsCodeBlock :code="servletExample" language="java" />

      <p>That is the whole integration on any servlet stack. The module depends on <code>jakarta.servlet</code> and nothing else, so it fits Tomcat, Jetty, Undertow or an embedded container the same way. Register the filter <strong>first in the chain</strong>, so security, routing and data access all happen inside the request's span.</p>

      <p>Asynchronous requests are handled for you: when the handler starts async processing the filter completes the event from an <code>AsyncListener</code> instead of when the container thread returns, so the recorded interval covers the whole exchange. The ids were stamped when the span opened, so the deferred commit still lands in the right trace.</p>

      <h2 id="naming">Naming: What a Container Cannot Answer</h2>

      <p>The one thing a container cannot answer is what a request should be <em>called</em>, so the filter asks a <code>HttpRequestNaming</code>. The span name is derived from the recorded <code>uri</code> and every distinct name enters the JFR constant pool, so the answer has to be the routing framework's matched <strong>template</strong> — knowledge only that framework has. That is why this is an interface rather than a lookup: the filter asks for a name, and whoever knows the routing supplies one.</p>

      <p>The built-in strategy, <code>HttpRequestNaming.servletMapping()</code>, names requests by the pattern their servlet was mapped with (<code>/api/*</code>) — the best a container can do alone, and already low-cardinality because a mapping is declared rather than derived from the request. Supply your own to use a router's matched template. A request that matched nothing is named <code>&lt;unmatched&gt;</code>: still recorded, simply named together, because one operation per mistyped URL is worth nothing to anyone.</p>

      <h2 id="manual-server">Writing the Filter Yourself</h2>

      <p>For stacks the modules don't cover — or to see precisely what they do — this is the whole filter:</p>

      <DocsCodeBlock :code="manualFilter" language="java" />

      <p>Note what this simple version does <em>not</em> handle: an asynchronous request is measured only until the container thread returns, so it appears to take microseconds. <code>HttpExchangeFilter</code> completes such requests from an <code>AsyncListener</code> instead, and guards against being applied twice when the filter is mapped more than once.</p>

      <h2 id="client">Outbound Calls: the Client Event</h2>

      <p>There is no client module to add: applications build clients in too many ways for one to guess, so an outbound call is instrumented where the call is made. The shape is the same everywhere — record host and path with the query string dropped, since that is where ids and tokens live:</p>

      <DocsCodeBlock :code="manualClient" language="java" />

      <DocsSpanTree
        trace="8c1d33f0…"
        :spans="clientSpans"
        caption="The downstream work happens in another process this recording cannot see, so the client exchange is a leaf."
      />

      <h2 id="async-clients">Async Clients</h2>

      <p>A blocking interceptor shape does not fit a client whose response arrives via callbacks on threads you don't control (WebClient, async HttpClient). Use the callback pattern (<router-link to="/docs/tracing/tracer-api/open-span-of">openSpanOf</router-link> + <router-link to="/docs/tracing/tracer-api/reenter">reenter</router-link>): <code>Tracer.openSpanOf(event)</code> when the call starts (on the thread whose span it belongs to), <code>Tracer.reenter(ctx, ...)</code> around each callback, and <code>event.commitSpan()</code> at completion. <code>openSpanOf</code> stamps the ids eagerly, so a completion running after the enclosing binding is gone still carries the right identity.</p>

      <h2 id="spring-support">Using Spring Boot?</h2>

      <p>One dependency registers the filter for you, names requests by the matched Spring MVC handler pattern, and binds the capture flags to <code>jeffrey.tracing.*</code> — plus a <code>RestTemplate</code> interceptor for the outbound half.</p>

      <DocsLinkCard
        to="/docs/tracing/spring-support"
        icon="bi bi-flower1"
        title="Spring Support"
        description="The starter, the jeffrey.tracing.* property table, Spring MVC request naming and the RestTemplate interceptor."
      />

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
            <td>Supply a routing-aware <code>HttpRequestNaming</code> instead of the servlet-mapping default</td>
          </tr>
          <tr>
            <td>SQL spans not nested under requests</td>
            <td>Filter registered after work-dispatching filters, or missing entirely</td>
            <td>Register the filter first in the chain, mapped at <code>/*</code></td>
          </tr>
          <tr>
            <td>Request span missing, children promoted to roots</td>
            <td>The root event was re-stamped by hand</td>
            <td>Never call <code>Tracer.stamp</code> on an <code>inSpanOf</code> event; commit with <code>commitSpan()</code></td>
          </tr>
          <tr>
            <td>5xx/4xx not red in Traces</td>
            <td><code>statusCode</code> not set before commit</td>
            <td><code>HttpExchangeFilter</code> sets it; by hand, set it in the <code>finally</code></td>
          </tr>
          <tr>
            <td>Async requests measured as ~0 ms</td>
            <td>Event completed when the container thread returned</td>
            <td>Use <code>HttpExchangeFilter</code>, which completes from an <code>AsyncListener</code></td>
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
