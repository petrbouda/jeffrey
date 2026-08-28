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
import DocsTracingPipeline from '@/components/docs/DocsTracingPipeline.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-it-is', text: 'What Jeffrey Tracing Is', level: 2 },
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'pieces', text: 'The Pieces', level: 2 },
  { id: 'event-catalog', text: 'Event Catalog', level: 2 },
  { id: 'not-distributed', text: 'What It Is Not', level: 2 },
  { id: 'requirements', text: 'Requirements', level: 2 },
  { id: 'where-next', text: 'Where to Go Next', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const taste = `Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
    Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
});`;

const tasteSpans = [
  { depth: 0, name: 'order.checkout', kind: 'SERVER' as const, start: 0, duration: 84.2,
    event: 'jeffrey.TraceSpan', note: 'root' },
  { depth: 1, name: 'inventory.reserve', kind: 'CLIENT' as const, start: 2.1, duration: 31.7,
    event: 'jeffrey.TraceSpan' },
  { depth: 1, name: 'payment.charge', kind: 'CLIENT' as const, start: 35.4, duration: 46.5,
    event: 'jeffrey.TraceSpan' }
];

</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey Tracing"
      icon="bi bi-bezier2"
    />

    <div class="docs-content">
      <p class="docs-lede">Jeffrey Tracing turns one unit of work — an HTTP request, a gRPC call, a batch job — into a tree of timed spans recorded as <strong>ordinary JFR events</strong>, and correlates that tree with everything else the JVM was doing at the same time: CPU samples, SQL statements, socket reads, lock waits, GC pauses and safepoints.</p>

      <h2 id="what-it-is">What Jeffrey Tracing Is</h2>

      <p>A tracing tool normally tells you that a span took 400&nbsp;ms and stops there. The next question — <em>doing what?</em> — needs a profiler and a second, correlated data source. Jeffrey has both in one file: spans are JFR events written into the same flight recording that carries the profiler's samples, so a span selected in the waterfall becomes a flamegraph query, a socket read becomes a child bar, and a GC pause becomes a lane drawn across the whole trace.</p>

      <p>There is no collector, no exporter, no separate "send data" step. You instrument with the zero-dependency <code>cafe.jeffrey-analyst:jeffrey-events</code> library (or attach the Jeffrey Agent and annotate methods with <code>@Traced</code>), record with whatever starts a JFR recording, and open the <code>.jfr</code> file in Jeffrey Microscope. Everything else — trace assembly, JDK-event correlation, visualization — happens at analysis time.</p>

      <DocsCodeBlock :code="taste" language="java" />
      <DocsSpanTree trace="5f3a90c2…" :spans="tasteSpans" />

      <h2 id="how-it-works">How It Works</h2>

      <DocsTracingPipeline />

      <p>Three properties make the model work:</p>

      <ul>
        <li><strong>A span is an event that carries trace identity.</strong> Every traced event extends <code>AbstractTracedEvent</code>, which declares <code>traceId</code>, <code>spanId</code> and <code>parentSpanId</code> (64-bit longs, <code>0</code> = absent) plus <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code> and <code>attributes</code>. The trace tree is rebuilt from the three ids alone.</li>
        <li><strong>Span discovery is structural.</strong> Jeffrey treats an event type as a span when the recording's own metadata says it declares a <code>spanId</code> field — no event-type list, no configuration. Your own custom event types take part in traces the moment they extend <code>AbstractTracedEvent</code>.</li>
        <li><strong>Context travels in a <code>ScopedValue</code>.</strong> The span in progress is published on the thread, so a nested span, a JDBC statement or a notification discovers its parent by itself — nothing is threaded through method signatures, and the binding cannot leak because it is bounded by a lambda.</li>
      </ul>

      <p>After a recording is parsed into a profile, Jeffrey derives the trace tables once: spans are assembled into trees, JDK blocking events (socket and file I/O, lock waits, parking) are <em>promoted</em> into synthesized leaf spans under the span that was waiting, exceptions are attributed to the span that threw them, and GC pauses and safepoints are matched against every trace window they overlap.</p>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/jdk-overlays.webp" alt="A complete trace in the Microscope waterfall with JVM context, blocking and I/O overlays" />
        <figcaption>One trace, one <code>.jfr</code> file: recorded spans, promoted JDK file I/O, virtual-thread pinning, the safepoint lane, and the per-category time summary.</figcaption>
      </figure>

      <h2 id="pieces">The Pieces</h2>

      <table>
        <thead>
          <tr>
            <th>Piece</th>
            <th>Artifact</th>
            <th>What it does</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>Events library</strong></td>
            <td><code>cafe.jeffrey-analyst:jeffrey-events</code></td>
            <td>The whole developer-facing API: <router-link to="/docs/tracing/instrumentation">Tracer</router-link>, <code>@Traced</code>, <code>AbstractTracedEvent</code>, and every <code>jeffrey.*</code> event type. Zero dependencies (only <code>jdk.jfr</code>); safe to leave in production — every emit path checks <code>isEnabled()</code> first.</td>
          </tr>
          <tr>
            <td><strong>Framework glue</strong></td>
            <td><code>jeffrey-tracing-*</code></td>
            <td>Drop-in instrumentation for Servlet, Spring, Spring Boot (a starter: one dependency, no code), JDBC <code>DataSource</code>, HikariCP, MyBatis and gRPC. See the <router-link to="/docs/tracing/http-events">HTTP</router-link>, <router-link to="/docs/tracing/grpc-events">gRPC</router-link>, <router-link to="/docs/tracing/jdbc-events">JDBC</router-link> and <router-link to="/docs/tracing/mybatis-events">MyBatis</router-link> pages, and <router-link to="/docs/tracing/spring-support">Spring Support</router-link>.</td>
          </tr>
          <tr>
            <td><strong>Jeffrey Agent</strong></td>
            <td><code>jeffrey-agent.jar</code></td>
            <td>A <code>-javaagent</code> that weaves methods annotated with <code>@Traced</code> into spans without touching their code. Optional — the library works fully without it. See <router-link to="/docs/tracing/traced-annotation">@Traced &amp; the Agent</router-link>.</td>
          </tr>
          <tr>
            <td><strong>Jeffrey Microscope</strong></td>
            <td>—</td>
            <td>The analysis side: derives traces from any recording, renders the waterfall with JVM context, and answers questions across traces — by operation, by attribute, by latency. See <router-link to="/docs/tracing/analysis">Analyzing Traces</router-link>.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="event-catalog">Event Catalog</h2>

      <table>
        <thead>
          <tr>
            <th>JFR name</th>
            <th>Role in a trace</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.HttpServerExchange</code> / <code>jeffrey.HttpClientExchange</code></td>
            <td>Root span of an inbound request / leaf for an outbound HTTP call</td>
          </tr>
          <tr>
            <td><code>jeffrey.GrpcServerExchange</code> / <code>jeffrey.GrpcClientExchange</code></td>
            <td>Root span of an inbound call / leaf for an outbound gRPC call</td>
          </tr>
          <tr>
            <td><code>jeffrey.JdbcQuery</code> / <code>JdbcInsert</code> / <code>JdbcUpdate</code> / <code>JdbcDelete</code> / <code>JdbcExecute</code> / <code>JdbcStream</code></td>
            <td>Leaf: one statement per event, split by verb</td>
          </tr>
          <tr>
            <td><code>jeffrey.TraceSpan</code></td>
            <td>Interior span — emitted by <code>Tracer.run</code>/<code>call</code>/<code>continueIn</code> and by <code>@Traced</code></td>
          </tr>
          <tr>
            <td><code>jeffrey.TraceScope</code></td>
            <td>Where a re-entered span ran; emitted by <code>Tracer.reenter</code> only — not a span itself</td>
          </tr>
          <tr>
            <td><code>jeffrey.Notification</code></td>
            <td>Not a span: an <em>instant</em> that records the span it fired in — a note the application writes into its own recording</td>
          </tr>
          <tr>
            <td>Your own — anything extending <code>AbstractTracedEvent</code></td>
            <td>A full span, discovered structurally with zero Jeffrey-side configuration — see <router-link to="/docs/tracing/custom-events">Custom Traced Events</router-link></td>
          </tr>
        </tbody>
      </table>

      <p>On top of the instrumented events, the analysis correlates the JDK's own recordings into every trace: <code>jdk.SocketRead</code>/<code>Write</code>, <code>jdk.FileRead</code>/<code>Write</code>/<code>Force</code>, lock waits, parking, sleeping, allocation stalls and virtual-thread pinning become <router-link to="/docs/tracing/jdk-events">synthesized leaf spans</router-link>; <code>jdk.JavaExceptionThrow</code> becomes the <router-link to="/docs/tracing/notifications-exceptions">exception rail</router-link>; GC pauses and safepoints become <router-link to="/docs/tracing/gc-safepoints">stop-the-world lanes</router-link>. None of that needs instrumentation — it applies retroactively to any recording.</p>

      <h2 id="not-distributed">What It Is Not</h2>

      <DocsCallout type="info">
        <strong>Not a distributed tracer.</strong> Traces are scoped to a <strong>single JVM</strong> — one recording, one set of traces. Jeffrey mints every trace and span id itself (64-bit, not the 128-bit W3C shape); it does not read or propagate a <code>traceparent</code> header and will not stitch a request across service boundaries. The goal is profiler-grade breakdown of one process, not a replacement for Jaeger or Tempo.
      </DocsCallout>

      <DocsCallout type="info">
        <strong>Not Async-Profiler Spans.</strong> Jeffrey has a second, separate span feature: <strong>Async-Profiler Spans</strong> are flat, per-thread, tag-based and need the patched async-profiler agent. Jeffrey Tracing spans are nested, trace-scoped and pure JFR. They are complementary and live in separate sections of the UI.
      </DocsCallout>

      <h2 id="requirements">Requirements</h2>

      <ul>
        <li><strong>Java 25 or newer</strong> for the <code>Tracer</code> API and <code>@Traced</code> — both are built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, finalized in Java&nbsp;25.</li>
        <li>On <strong>Java 17–24</strong>, an earlier <code>jeffrey-events</code> release still provides the HTTP, gRPC and JDBC events (they light up the HTTP and Database dashboards), but no hand-written spans and no cross-event nesting.</li>
        <li><strong>Any JFR recording</strong> records the events — plain <code>-XX:StartFlightRecording</code>, <code>jcmd</code>, or async-profiler with <code>--jfrsync</code>. Nothing to enable; the events are on by default in any recording.</li>
      </ul>

      <h2 id="where-next">Where to Go Next</h2>

      <div class="docs-grid docs-grid-2">
        <DocsLinkCard
          to="/docs/tracing/getting-started"
          icon="bi bi-rocket-takeoff"
          title="Getting Started"
          description="From zero to a first trace in Jeffrey in a few minutes."
        />
        <DocsLinkCard
          to="/docs/tracing/concepts"
          icon="bi bi-box"
          title="Core Concepts"
          description="Traces, spans, instants, ids — and the five rules that make traces assemble."
        />
        <DocsLinkCard
          to="/docs/tracing/instrumentation"
          icon="bi bi-code-square"
          title="Tracer API Reference"
          description="Every method on its own page, with use-cases, examples and the tree it produces."
        />
        <DocsLinkCard
          to="/docs/tracing/analysis"
          icon="bi bi-bar-chart"
          title="Analyzing Traces"
          description="The waterfall, Traces by Operation, attribute search and the AI export."
        />
      </div>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.docs-lede {
  font-size: 16px;
  color: #5e6e82;
}
</style>
