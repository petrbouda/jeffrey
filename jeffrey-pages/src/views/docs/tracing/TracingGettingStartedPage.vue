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
  { id: 'dependency', text: '1. Add the Dependency', level: 2 },
  { id: 'spring-boot', text: 'Spring Boot: One Dependency, No Code', level: 2 },
  { id: 'sixty-seconds', text: '2. Sixty Seconds of Tracing', level: 2 },
  { id: 'record', text: '3. Record', level: 2 },
  { id: 'verify', text: '4. Verify with jfr print', level: 2 },
  { id: 'upload', text: '5. Open It in Jeffrey', level: 2 },
  { id: 'next', text: 'Next Steps', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const mavenDependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events</artifactId>
    <version><!-- latest release on Maven Central --></version>
</dependency>`;

const starterDependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-tracing-spring-boot-starter</artifactId>
    <version><!-- latest release on Maven Central --></version>
</dependency>`;

const sixtySeconds = `// 1. The request event IS the root span (in a servlet filter, first in the chain).
//    The spring-boot-starter registers exactly this filter for you.
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

// 3. A statement (or outbound call) is a leaf — TracedEvents.emit is the whole
//    lifecycle: guard, begin, end on success, failed(e) on a throw (the span
//    shows red), commitSpan() stamping it under the span in progress
JdbcQueryEvent query = new JdbcQueryEvent("UserMapper.selectById", "UserMapper");
List<User> users = TracedEvents.emit(query,
        () -> doQuery(),
        (e, result) -> {
            e.sql = sql;
            e.rows = result != null ? result.size() : 0;
        });`;

const recordCommands = `# Plain JFR at startup
java -XX:StartFlightRecording=filename=app.jfr,settings=profile -jar app.jar

# On demand, against a running JVM
jcmd <pid> JFR.start name=jeffrey settings=profile
jcmd <pid> JFR.dump  name=jeffrey filename=app.jfr

# async-profiler: CPU samples + all JFR (and Jeffrey) events in one file
asprof -d 60 -e cpu --jfrsync default -f app.jfr <pid>`;

const verifyCommand = `jfr print --events "jeffrey.*" app.jfr | less`;

const verifyOutput = `jeffrey.HttpServerExchange {
  startTime = 12:41:53.518
  duration = 128 ms
  traceId = 6872570733206835563
  spanId = 4444722480460712002
  parentSpanId = 0                      // 0 => this span is the trace root
  name = "GET /api/users/{id}"
  kind = "SERVER"
  status = "UNSET"
  method = "GET"
  uri = "/api/users/{id}"
  statusCode = 200
  ...
}

jeffrey.JdbcQuery {
  traceId = 6872570733206835563         // same trace as the request above
  spanId = 9032751172020347118
  parentSpanId = 4444722480460712002    // chained up to the root
  name = "UserMapper.selectById"
  kind = "CLIENT"
  sql = "select * from users where id = ?"
  rows = 1
  ...
}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Getting Started"
      icon="bi bi-rocket-takeoff"
    />

    <div class="docs-content">
      <p>From zero to a first trace rendered in Jeffrey. The path is: add one dependency, emit a few spans (or let the framework glue emit them for you), run any JFR recording, and open the file in Jeffrey Microscope.</p>

      <h2 id="dependency">1. Add the Dependency</h2>

      <DocsCodeBlock :code="mavenDependency" language="xml" filename="pom.xml" />

      <ul>
        <li><strong>Java 25 or newer</strong> is required for the <code>Tracer</code> API — it is built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, both finalized in Java&nbsp;25.</li>
        <li>The library has <strong>zero dependencies</strong> (only <code>jdk.jfr</code>) and is safe to leave in production code: with no recording running, every emit path checks <code>event.isEnabled()</code> and runs the body directly.</li>
        <li>No registration step: JFR auto-registers each event type the first time an instance is created.</li>
      </ul>

      <h2 id="spring-boot">Spring Boot: One Dependency, No Code</h2>

      <p>On Spring Boot you can skip hand-written instrumentation entirely:</p>

      <DocsCodeBlock :code="starterDependency" language="xml" filename="pom.xml" />

      <p>Every inbound request becomes the root span of a trace, named by the matched handler pattern. Every <code>DataSource</code> bean is wrapped, so the statements your ORM issues nest underneath the request without anyone writing JDBC instrumentation, and a HikariCP pool gets its acquire/borrow/create timings plus a periodic gauge. Tune it with <code>jeffrey.tracing.*</code> — the property table is on the <router-link to="/docs/tracing/http-events">HTTP Events</router-link> page.</p>

      <p>gRPC and MyBatis are one line each — see <router-link to="/docs/tracing/grpc-events">gRPC Events</router-link> and <router-link to="/docs/tracing/jdbc-events">JDBC Events</router-link>. And if you would rather annotate methods than write lambdas, the <router-link to="/docs/tracing/traced-annotation">Jeffrey Agent weaves <code>@Traced</code> methods</router-link> into spans.</p>

      <h2 id="sixty-seconds">2. Sixty Seconds of Tracing</h2>

      <p>The whole model in one listing: an inbound request becomes the root of a trace, hand-written spans describe the application logic inside it, and every statement or outbound call nests underneath — all through a <code>ScopedValue</code>, so nothing is threaded through your signatures:</p>

      <DocsCodeBlock :code="sixtySeconds" language="java" />

      <h2 id="record">3. Record</h2>

      <p>The events are recorded by whatever JFR recording is running — they are enabled by default in any recording, with no settings-file changes:</p>

      <DocsCodeBlock :code="recordCommands" language="bash" />

      <DocsCallout type="tip">
        The async-profiler form with <code>--jfrsync</code> is the one that unlocks the full experience: CPU samples and Jeffrey spans land in <strong>one file on one clock</strong>, which is what makes per-span flamegraphs possible.
      </DocsCallout>

      <h2 id="verify">4. Verify with jfr print</h2>

      <DocsCodeBlock :code="verifyCommand" language="bash" />

      <p>For one request you exercised, check:</p>

      <ol>
        <li>The root event (e.g. <code>jeffrey.HttpServerExchange</code>) exists with non-zero <code>traceId</code>/<code>spanId</code> and <code>parentSpanId = 0</code>.</li>
        <li>Every leaf event issued while serving it carries the <strong>same <code>traceId</code></strong> and a <code>parentSpanId</code> chaining up to the root.</li>
        <li><code>jeffrey.TraceSpan</code> events show your operation names; <code>status = UNSET</code> on success, <code>ERROR</code> + <code>errorType</code> where you exercised a failure.</li>
        <li>No high-cardinality names — no raw URIs, no ids, no literal-bearing SQL as a name.</li>
      </ol>

      <DocsCodeBlock :code="verifyOutput" language="text" />

      <DocsCallout type="warning">
        An event with all-zero ids means a <code>commit()</code> slipped in where <code>commitSpan()</code> belonged, or work crossed an executor without <code>fork</code>/<code>continueIn</code>. The event still appears in the dashboards — it is just not part of any trace. This is the single most common instrumentation mistake.
      </DocsCallout>

      <h2 id="upload">5. Open It in Jeffrey</h2>

      <p>Upload <code>app.jfr</code> to Jeffrey Microscope (create a project → upload recording → initialize profile). Jeffrey auto-detects the event types and activates the matching sections: the HTTP and Database dashboards, and — as soon as any event with trace identity is found — the <strong>Traces</strong> section, with <router-link to="/docs/tracing/analysis">Traces by Operation, attribute search and the trace waterfall</router-link>.</p>

      <!-- TODO screenshot: /images/docs/tracing/getting-started-operations.png — Traces by Operation list after the first upload -->

      <h2 id="next">Next Steps</h2>

      <ul>
        <li><router-link to="/docs/tracing/concepts">Core Concepts</router-link> — the data model and the five rules that make traces assemble correctly.</li>
        <li><router-link to="/docs/tracing/tracer-api">Tracer API</router-link> — every method with a use-case, an example and its output.</li>
        <li><router-link to="/docs/tracing/configuration">Configuration &amp; Testing</router-link> — volume control, recording thresholds, and asserting on spans in your own tests.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
