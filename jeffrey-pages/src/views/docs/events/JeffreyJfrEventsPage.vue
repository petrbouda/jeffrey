<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'event-types', text: 'Event Types', level: 2 },
  { id: 'trace-context', text: 'Trace Context', level: 2 },
  { id: 'integration', text: 'Integration', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Jeffrey JFR Events"
        icon="bi bi-broadcast"
      />

      <div class="docs-content">
        <p><strong>Jeffrey JFR Events</strong> is a library that provides custom JFR event definitions for emitting application-level events that can be captured and analyzed in Jeffrey.</p>

        <DocsCallout type="info">
          <strong>Open Source Library:</strong> Jeffrey Events is available at <a href="https://github.com/petrbouda/jeffrey-events" target="_blank" rel="noopener">github.com/petrbouda/jeffrey-events</a>. Add it to your project to emit custom events that Jeffrey can process.
        </DocsCallout>

        <h2 id="event-types">Event Types</h2>
        <p>While standard JFR captures JVM-level events (GC, threads, CPU sampling), Jeffrey Events allows you to emit <strong>application-level events</strong> that provide insight into your business logic. These events are recorded into the same JFR file alongside standard JVM events, providing a complete picture of both infrastructure and application behavior.</p>

        <div class="event-cards">
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-heart-pulse"></i></div>
            <div class="event-content">
              <h4>Heartbeat</h4>
              <p>Periodic liveness signal emitted by the Jeffrey Agent. Used by the platform to detect when profiled applications stop running. Each event carries a sequence number for ordering.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-globe"></i></div>
            <div class="event-content">
              <h4>HTTP Events</h4>
              <p>Capture HTTP server requests including method, URI, status code, and response time. Useful for analyzing request patterns and identifying slow endpoints.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-hdd-network"></i></div>
            <div class="event-content">
              <h4>gRPC Events</h4>
              <p>Capture gRPC service calls including method name, status code, response time, and payload sizes. Available for both server-side and client-side calls, enabling analysis of service-to-service communication.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-database"></i></div>
            <div class="event-content">
              <h4>Database Events</h4>
              <p>Track SQL query execution with query text, execution time, and row counts. Identify slow queries and database bottlenecks.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-layers"></i></div>
            <div class="event-content">
              <h4>Connection Pool Events</h4>
              <p>Monitor connection pool activity including connection acquisition time, pool utilization, and timeouts. Detect pool exhaustion issues.</p>
            </div>
          </div>
          <div class="event-card">
            <div class="event-icon"><i class="bi bi-bezier2"></i></div>
            <div class="event-content">
              <h4>Trace Spans</h4>
              <p>Hand-written spans opened through the <code>Tracer</code> API — a name, a kind, a status and optional JSON attributes, nested automatically through <code>ScopedValue</code>. Emitted as <code>jeffrey.TraceSpan</code> and assembled by Jeffrey into a trace tree. The event declares no fields of its own: they all live on the traced base below, which is what makes an instrumented event a span too.</p>
            </div>
          </div>
        </div>

        <h2 id="trace-context">The Span Shape</h2>
        <p>The HTTP, gRPC and database events above extend <code>AbstractTracedEvent</code>, which carries the whole shape of a span: three identity fields — <code>traceId</code>, <code>spanId</code> and <code>parentSpanId</code>, 64-bit longs where <code>0</code> means absent — and the <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code> and <code>attributes</code> that describe it. The library never populates the ids itself; the instrumentation does, either with <code>Tracer.stamp(event)</code>, which gives the event a span of its own under the one in progress, or with <code>Tracer.inSpanOf(...)</code>, which makes the event <em>be</em> the span it opens.</p>

        <p>An event fills in the rest for itself when it is committed through <code>commitSpan()</code>: an exchange is named by its method and matched URI template and fails from HTTP 400 upwards, a gRPC call is named by service and method and fails on anything but <code>OK</code>. These are the naming rules OpenTelemetry states for the same operations, and the recording carries the answer so that <code>jfr print</code>, JMC and any other reader see a described span. Jeffrey itself applies the same rules again when it derives a trace, from the exchange's own <code>method</code>, <code>uri</code>, <code>service</code> and status fields — so an operation is named the same way whichever version of this library recorded it, and instrumentation that stamps the ids and commits plainly is named too. An instrumented event lands in the same tree as <code>jeffrey.TraceSpan</code>, which is what makes a request's SQL statements appear as its children with neither side knowing about the other. The trace and span ids are also annotated <code>@Contextual</code>, so <code>jfr print</code> and JMC show them alongside the surrounding events even outside Jeffrey.</p>

        <p>Jeffrey recognises a span by that shape rather than by a list of event types: any event declaring a <code>spanId</code> field takes part in traces, including instrumentation written outside Jeffrey.</p>

        <p>An event type can also declare its <em>conventions</em> the same way — inside the recording. <code>@SpanName("{method} {uri}")</code> on the event class states the naming template, and <code>@SpanOutcome(from = "statusCode", semantics = SpanOutcome.HTTP_CODE)</code> states which field carries the operation's outcome and how to judge it (<code>HTTP_CODE</code>: 400 and up fails; <code>GRPC_CODE</code>: anything but <code>OK</code> fails; <code>BOOLEAN</code>: <code>false</code> fails). Both are JFR metadata annotations, so they are persisted into every recording the event is written to, and any reader — Jeffrey's derivation included — can name and judge the span without knowing the event type, even when it was committed with plain <code>commit()</code> and never described itself. The semantics names are wire format: frozen once recordings carry them, extended only by addition.</p>

        <DocsCallout type="warning">
          <strong>Tracing requires Java 25.</strong> The <code>Tracer</code> API is built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, both finalized in Java&nbsp;25. The event definitions themselves remain usable on older releases from an earlier version of the library.
        </DocsCallout>

        <DocsCallout type="info">
          <strong>The field names changed once, and Jeffrey reads either spelling.</strong> Giving every traced event a <code>status</code> of its own — <code>UNSET</code>, <code>OK</code> or <code>ERROR</code> — collided with the field HTTP and gRPC exchanges already used for their response code, so that one was renamed to <code>statusCode</code>, and <code>JdbcBaseEvent.isSuccess</code> was replaced by the span status. A recording produced by an earlier version of the library still carries the old names. Because Jeffrey derives an operation from the event's own fields, both shapes read correctly: the outcome is taken from <code>statusCode</code> where it exists and from <code>status</code> where it does not, which cannot be confused with a span status — <code>ERROR</code> and <code>UNSET</code> are not codes any exchange reports. What an older recording still loses is the identity of its database statements: before <code>Tracer.stamp</code> minted an id per event, every statement inside one span carried that span's id, so such statements collapse together in the waterfall. Re-record with the current library for accurate statement identity.
        </DocsCallout>

        <p class="docs-read-more">
          <router-link to="/docs/events/tracer">Read the Tracer API reference &rarr;</router-link><br>
          <router-link to="/docs/microscope/profiles/traces">Read the Traces &amp; Spans reference &rarr;</router-link>
        </p>

        <h2 id="integration">Integration</h2>
        <p>Add Jeffrey Events to your project and instrument your code to emit events:</p>

        <h3>Maven Dependency</h3>
        <div class="code-block">
          <pre><code>&lt;dependency&gt;
    &lt;groupId&gt;cafe.jeffrey-analyst&lt;/groupId&gt;
    &lt;artifactId&gt;jeffrey-events&lt;/artifactId&gt;
    &lt;version&gt;0.12.0&lt;/version&gt;
&lt;/dependency&gt;</code></pre>
        </div>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Event Cards */
.event-cards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.event-card {
  display: flex;
  gap: 1rem;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.event-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  border-radius: 10px;
  background: linear-gradient(135deg, #5e64ff, #4c52ff);
  display: flex;
  align-items: center;
  justify-content: center;
}

.event-icon i {
  font-size: 1.25rem;
  color: white;
}

.event-content {
  flex: 1;
}

.event-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
}

.event-content p {
  margin: 0;
  font-size: 0.875rem;
  color: #5e6e82;
  line-height: 1.5;
}

/* Code Block */
.code-block {
  margin: 1rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.code-block pre {
  margin: 0;
  padding: 1rem;
  background: #1e293b;
  overflow-x: auto;
}

.code-block code {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.85rem;
  color: #e2e8f0;
  line-height: 1.5;
}

/* Feature List */
.feature-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  background: #f0fdf4;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.feature-item i {
  color: #10b981;
  font-size: 1rem;
}

.feature-item div {
  font-size: 0.875rem;
  color: #374151;
  line-height: 1.5;
}

.feature-item strong {
  color: #1f2937;
}
</style>
