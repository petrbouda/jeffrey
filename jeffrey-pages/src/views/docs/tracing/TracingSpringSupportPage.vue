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
  { id: 'split', text: 'Which Module Do You Need?', level: 2 },
  { id: 'starter', text: 'Spring Boot: The Starter', level: 2 },
  { id: 'properties', text: 'The jeffrey.tracing.* Properties', level: 2 },
  { id: 'plain-spring', text: 'Plain Spring: @Import', level: 2 },
  { id: 'http', text: 'HTTP: Naming and Settings', level: 2 },
  { id: 'client', text: 'Outbound Calls: RestTemplate', level: 2 },
  { id: 'jdbc', text: 'DataSource, HikariCP and MyBatis', level: 2 },
  { id: 'grpc', text: 'gRPC: No Starter', level: 2 },
  { id: 'overriding', text: 'Overriding What the Starter Wires', level: 2 },
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

const allImports = `@Configuration
@Import({
    JeffreyTracingConfiguration.class,        // HTTP filter, MVC naming, RestTemplate interceptor
    JeffreyJdbcTracingConfiguration.class,    // wraps every DataSource bean
    JeffreyHikariTracingConfiguration.class,  // connection-pool events from HikariCP
    JeffreyMyBatisTracingConfiguration.class  // statements named by mapper method
})
class ObservabilityConfiguration {
}

// Import the MyBatis one OR the JDBC one, never both — they record the same
// statement twice, once by mapper method and once by parsed SQL.`;

const springMvcNaming = `public class SpringMvcRequestNaming implements HttpRequestNaming {

    @Override
    public String uri(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String matched && !matched.isEmpty()) {
            String contextPath = request.getContextPath();
            return contextPath == null || contextPath.isEmpty() ? matched : contextPath + matched;
        }
        return UNMATCHED_URI;                    // a static asset, a 404
    }

    @Override
    public Map<String, String> pathParams(HttpServletRequest request) {
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return variables instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }
}`;

const clientInterceptor = `// jeffrey-tracing-spring ships JfrClientHttpRequestInterceptor as a bean;
// attach it where you build the client:
RestTemplate restTemplate = new RestTemplate();
restTemplate.getInterceptors().add(interceptor);   // the bean from the configuration

// Or with a normaliser that collapses variable path segments into a template:
new JfrClientHttpRequestInterceptor(uri -> "payments" + normalize(uri.getPath()));`;

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
                    e.statusCode = response != null ? statusOf(response) : 0;
                });
    }

    // getStatusCode() throws IOException, and the filler cannot: swallow it here.
    private static int statusOf(ClientHttpResponse response) {
        try {
            return response.getStatusCode().value();
        } catch (IOException e) {
            return 0;
        }
    }
}`;

const springGrpc = `@Bean
@GlobalServerInterceptor
JfrGrpcServerInterceptor jfrGrpcServerInterceptor() {
    return new JfrGrpcServerInterceptor();
}

// The client interceptor goes on the channel you build, as everywhere else.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Spring Support"
      icon="bi bi-flower1"
    />

    <div class="docs-content">
      <p class="docs-lede">Everything Spring-specific in one place: the Boot starter and its <code>jeffrey.tracing.*</code> properties, the explicit <code>@Import</code> wiring for applications without Boot, and the handful of behaviours that only exist because Spring knows something a servlet container does not. The events themselves — what is recorded and what it looks like — are on <router-link to="/docs/tracing/http-events">HTTP</router-link>, <router-link to="/docs/tracing/grpc-events">gRPC</router-link>, <router-link to="/docs/tracing/jdbc-events">JDBC</router-link> and <router-link to="/docs/tracing/mybatis-events">MyBatis</router-link>; nothing here changes any of that.</p>

      <h2 id="split">Which Module Do You Need?</h2>

      <table>
        <thead>
          <tr>
            <th>Module</th>
            <th>Needs</th>
            <th>What it gives you</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey-tracing-spring-boot-starter</code></td>
            <td>Spring Boot</td>
            <td>One dependency, zero code: auto-configuration, filter registration and order, <code>jeffrey.tracing.*</code> binding</td>
          </tr>
          <tr>
            <td><code>jeffrey-tracing-spring</code></td>
            <td>Spring, any version</td>
            <td>The same beans, registered only when you <code>@Import</code> them — no auto-configuration at all</td>
          </tr>
          <tr>
            <td><code>jeffrey-tracing-servlet</code>, <code>-jdbc</code>, <code>-hikari</code>, <code>-mybatis</code>, <code>-grpc</code></td>
            <td>Nothing beyond the technology itself</td>
            <td>The instrumentation proper; usable from any stack, Spring or not</td>
          </tr>
        </tbody>
      </table>

      <p>The split is load-bearing, not tidiness. <code>jeffrey-tracing-spring</code> ships no <code>AutoConfiguration.imports</code> file, so having it on the classpath — even of a Spring Boot application — registers nothing at all. Keeping Boot's types out of it is what makes it usable from the plain Spring MVC applications it is meant to serve: <code>FilterRegistrationBean</code> is a Spring Boot type, and depending on it would rule those applications out.</p>

      <h2 id="starter">Spring Boot: The Starter</h2>

      <DocsCodeBlock :code="starterDependency" language="xml" />

      <p>That is the whole integration. The auto-configuration registers the filter first in the chain, names spans by the matched Spring MVC handler pattern, and binds <code>jeffrey.tracing.*</code>. It backs off entirely if you define your own filter, naming strategy or settings. Four auto-configurations decide independently what applies:</p>

      <table>
        <thead>
          <tr>
            <th>Auto-configuration</th>
            <th>Applies when</th>
            <th>Effect</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>JeffreyTracingAutoConfiguration</code></td>
            <td>Servlet web application, <code>enabled</code></td>
            <td>Registers <code>HttpExchangeFilter</code> with an order and URL patterns; binds the HTTP settings</td>
          </tr>
          <tr>
            <td><code>JeffreyMyBatisTracingAutoConfiguration</code></td>
            <td>A <code>SqlSessionFactory</code> <strong>bean</strong> exists, <code>mybatis-enabled</code></td>
            <td>Registers the MyBatis interceptor; declared <em>before</em> the JDBC one</td>
          </tr>
          <tr>
            <td><code>JeffreyJdbcTracingAutoConfiguration</code></td>
            <td><code>DataSource</code> on the classpath, <code>jdbc-enabled</code>, and no MyBatis interceptor bean</td>
            <td>Wraps every <code>DataSource</code> bean in a <code>TracingDataSource</code></td>
          </tr>
          <tr>
            <td><code>JeffreyHikariTracingAutoConfiguration</code></td>
            <td><code>HikariDataSource</code> on the classpath, <code>hikari-enabled</code></td>
            <td>Gives each pool a Jeffrey metrics tracker, which is what makes pool events appear</td>
          </tr>
        </tbody>
      </table>

      <p>The MyBatis condition is a <strong>built <code>SqlSessionFactory</code>, not the jar</strong>, and that distinction is deliberate: a transitive dependency is enough to put MyBatis on the classpath, while the consequence of guessing wrong from it is severe — an application with the jar and no mappers would silently stop recording statements. A factory also happens to be what makes the interceptor work at all, since mybatis-spring registers <code>Interceptor</code> beans into the factory it builds.</p>

      <h2 id="properties">The jeffrey.tracing.* Properties</h2>

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

      <h2 id="plain-spring">Plain Spring: @Import</h2>

      <p><code>jeffrey-tracing-spring</code> carries the same beans with no Spring Boot dependency and no auto-configuration — nothing happens until you ask:</p>

      <DocsCodeBlock :code="springImport" language="java" />

      <p>That gives you the <code>HttpExchangeFilter</code> as a bean; register it the way your stack does (<code>web.xml</code>, or <code>AbstractAnnotationConfigDispatcherServletInitializer#getServletFilters</code>) — <strong>first in the chain</strong>. Using both the starter and this <code>@Import</code> is safe: Spring registers the same <code>@Configuration</code> class once however many times it is imported, so you get one filter, not two.</p>

      <p>The data-access halves are separate configurations rather than more beans inside that one, and for a reason worth knowing before you import them: an application with no data source must be able to instrument HTTP without the JDBC types being loaded, and referencing <code>HikariDataSource</code> from a shared configuration would break every application on a different pool.</p>

      <DocsCodeBlock :code="allImports" language="java" />

      <h2 id="http">HTTP: Naming and Settings</h2>

      <p>The one thing a servlet container cannot answer is what a request should be <em>called</em>, and the matched route template is knowledge only the routing framework has. That is the whole reason <code>HttpRequestNaming</code> is an interface, and <code>SpringMvcRequestNaming</code> is the Spring MVC answer to it:</p>

      <DocsCodeBlock :code="springMvcNaming" language="java" />

      <p>A request that matched no handler — a static asset, a 404 — is named <code>&lt;unmatched&gt;</code> rather than by its raw path. Such requests are still recorded; they are simply named together, because the name becomes the identity of a whole trace type and one operation per mistyped URL is worth nothing to anyone.</p>

      <DocsCallout type="info">
        <strong><code>HttpExchangeSettings</code> is deliberately not a bean in <code>JeffreyTracingConfiguration</code>.</strong> That configuration is imported <em>by</em> the starter's auto-configuration, so a default settings bean declared there would already be registered by the time the starter's <code>@ConditionalOnMissingBean</code> was evaluated — and the bean bound from <code>jeffrey.tracing.*</code> would silently lose to it. Declare your own and it wins over both.
      </DocsCallout>

      <h2 id="client">Outbound Calls: RestTemplate</h2>

      <DocsCodeBlock :code="clientInterceptor" language="java" />

      <p>It is only a bean, not attached automatically: Spring Boot 4 removed <code>RestTemplateCustomizer</code>, and a plain Spring application builds its own clients anyway. By default it records host and path with the query string dropped (that is where ids and tokens live), and accepts a <code>Function&lt;URI, String&gt;</code> to collapse variable path segments into a template. Written by hand, the same interceptor is:</p>

      <DocsCodeBlock :code="manualClient" language="java" />

      <p>For a client whose response arrives on threads you don't control — <code>WebClient</code>, an async <code>HttpClient</code> — the blocking interceptor shape does not fit; see <router-link to="/docs/tracing/http-events#async-clients">Async Clients</router-link>.</p>

      <h2 id="jdbc">DataSource, HikariCP and MyBatis</h2>

      <p>On the starter, every <code>DataSource</code> bean is wrapped automatically (<code>jeffrey.tracing.jdbc-enabled=true</code>, the default). Both the wrapping and the Hikari tracker are bean post-processors rather than replacement bean definitions, so the application keeps declaring its data source exactly as it did and everything injected with one transparently gets the traced view. The <strong>bean name becomes the statement group</strong>, which is what separates two pools in Jeffrey's Database dashboard.</p>

      <DocsCallout type="warning">
        <strong>The two post-processors run at opposite ends of initialisation, and must.</strong> <code>HikariMetricsBeanPostProcessor</code> runs <em>before</em> initialisation, while the pool has not started yet; <code>TracingDataSourceBeanPostProcessor</code> replaces the bean with a <code>DataSource</code> wrapper <em>after</em> — by which point it is no longer a <code>HikariDataSource</code> to look at. A pool that already carries a tracker keeps it: an application that configured its own metrics made a deliberate choice.
      </DocsCallout>

      <p>Registering the MyBatis interceptor stands the <code>DataSource</code> wrapper down automatically, so the two cannot record the same statement twice — once under <code>UserMapper.selectById</code> and once under a name parsed out of its SQL. On the plain <code>@Import</code> path there is no <code>jeffrey.tracing.*</code> binding at all — those properties exist only where an auto-configuration reads them — so the remedy there is simply not to import <code>JeffreyJdbcTracingConfiguration</code>. The trade-off is what <code>jeffrey.tracing.mybatis-enabled=false</code> exists for: an application using MyBatis <em>and</em> a plain <code>JdbcTemplate</code> sees only the mapper calls, and gets everything back — under SQL-parsed names — by leaving the wrapper in charge.</p>

      <p>Statement naming and MyBatis settings are both taken from a bean when one exists: declare a <code>StatementNaming</code> to name statements by something better than verb and primary table, or a <code>MyBatisStatementSettings</code> to change parameter capture (<code>jeffrey.tracing.mybatis-capture-parameters</code>, <code>jeffrey.tracing.mybatis-max-parameter-length</code> do the same through properties).</p>

      <h2 id="grpc">gRPC: No Starter</h2>

      <p>On Spring gRPC, the server interceptor is a bean like any other:</p>

      <DocsCodeBlock :code="springGrpc" language="java" />

      <p>There is no starter and no auto-configuration for gRPC: a channel is built by application code, and a server is assembled from interceptors an application chooses — there is no equivalent of a servlet filter chain to hook into.</p>

      <h2 id="overriding">Overriding What the Starter Wires</h2>

      <p>The <strong>starter's</strong> beans are guarded with <code>@ConditionalOnMissingBean</code>, so on Spring Boot declaring your own replaces them:</p>

      <DocsCallout type="warning">
        <strong>That guard does not exist on the plain <code>@Import</code> path.</strong> <code>jeffrey-tracing-spring</code> deliberately carries no Spring Boot types, <code>@ConditionalOnMissingBean</code> among them, so declaring a second <code>HttpRequestNaming</code> or <code>HttpExchangeFilter</code> beside an imported <code>JeffreyTracingConfiguration</code> fails the context with <em>expected single matching bean but found 2</em>. Mark yours <code>@Primary</code>, or import the pieces you want and declare the rest yourself.
      </DocsCallout>

      <table>
        <thead>
          <tr>
            <th>Declare</th>
            <th>Replaces</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>HttpRequestNaming</code></td>
            <td><code>SpringMvcRequestNaming</code> — use your own router's matched template</td>
          </tr>
          <tr>
            <td><code>HttpExchangeSettings</code></td>
            <td>The settings bound from <code>jeffrey.tracing.capture-*</code></td>
          </tr>
          <tr>
            <td><code>HttpExchangeFilter</code></td>
            <td>The filter itself</td>
          </tr>
          <tr>
            <td>A <code>FilterRegistrationBean</code> named <code>jeffreyHttpExchangeFilterRegistration</code></td>
            <td>The registration — order and URL patterns. Matched <strong>by name</strong>, not by type, so any other <code>FilterRegistrationBean</code> leaves it alone</td>
          </tr>
          <tr>
            <td><code>StatementNaming</code></td>
            <td>Naming by verb and primary table</td>
          </tr>
          <tr>
            <td><code>MyBatisStatementSettings</code></td>
            <td>The settings bound from <code>jeffrey.tracing.mybatis-*</code></td>
          </tr>
          <tr>
            <td>Nothing — set <code>jeffrey.tracing.enabled=false</code></td>
            <td>All of it, without removing the dependency</td>
          </tr>
        </tbody>
      </table>

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
            <td>Nothing recorded at all, starter on the classpath</td>
            <td>The HTTP auto-configuration is <code>@ConditionalOnWebApplication(SERVLET)</code></td>
            <td>A WebFlux or non-web application gets no filter; instrument it directly with the <router-link to="/docs/tracing/tracer-api/run">Tracer API</router-link></td>
          </tr>
          <tr>
            <td>One "endpoint" per user/entity in the HTTP dashboard</td>
            <td>Raw URI recorded instead of the template</td>
            <td>Let <code>SpringMvcRequestNaming</code> name requests, or declare a routing-aware <code>HttpRequestNaming</code></td>
          </tr>
          <tr>
            <td>SQL spans not nested under requests</td>
            <td>Filter registered after work-dispatching filters, or not covering the endpoint</td>
            <td>Keep <code>jeffrey.tracing.order</code> first; check <code>url-patterns</code></td>
          </tr>
          <tr>
            <td>Capture flags in <code>application.yaml</code> ignored</td>
            <td>A hand-declared <code>HttpExchangeSettings</code> bean wins over the property-bound one</td>
            <td>Delete the bean, or configure it there instead of in properties</td>
          </tr>
          <tr>
            <td>Every mapper call recorded twice</td>
            <td>The MyBatis interceptor and the <code>DataSource</code> wrapper are both active</td>
            <td>On the starter this cannot happen; importing by hand, drop <code>JeffreyJdbcTracingConfiguration</code></td>
          </tr>
          <tr>
            <td>Statements named from SQL, not by mapper method</td>
            <td>No <code>SqlSessionFactory</code> <em>bean</em> — the jar alone is not the condition</td>
            <td>Build the factory as a bean (mybatis-spring does), or register the interceptor yourself</td>
          </tr>
          <tr>
            <td>No connection-pool events on HikariCP</td>
            <td>The application set its own <code>MetricsTrackerFactory</code>, or <code>hikari-enabled=false</code></td>
            <td>A pool that already has a tracker keeps it — register <code>JfrMetricsTrackerFactory</code> yourself</td>
          </tr>
          <tr>
            <td>Client calls are roots of their own one-span traces</td>
            <td>Call ran outside a bound span (<code>@Async</code>, a scheduled job)</td>
            <td>Wrap background work with <router-link to="/docs/tracing/tracer-api/fork">Tracer.fork</router-link> / <router-link to="/docs/tracing/tracer-api/continue-in">continueIn</router-link></td>
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
