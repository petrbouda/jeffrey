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
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'what-is-a-trace', text: 'What a Trace Is Here', level: 2 },
  { id: 'instrumenting', text: 'Instrumenting an Application', level: 2 },
  { id: 'auto-instrumented', text: 'Events That Already Carry Trace Identity', level: 2 },
  { id: 'trace-list', text: 'Trace List', level: 2 },
  { id: 'waterfall', text: 'Waterfall', level: 2 },
  { id: 'span-drill-down', text: 'Span Drill-Down', level: 2 },
  { id: 'operations', text: 'Operations', level: 2 },
  { id: 'volume-control', text: 'Controlling Span Volume', level: 2 },
  { id: 'limits', text: 'Limits', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const mavenDependency = `<dependency>
    <groupId>cafe.jeffrey-analyst</groupId>
    <artifactId>jeffrey-events</artifactId>
    <version>0.12.0</version>
</dependency>`;

const tracerExample = `import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.Tracer;

// A root span. Everything opened inside it becomes its child, with no
// context threaded through the call chain.
Tracer.run("order.checkout", SpanKind.SERVER, () -> {
    Tracer.run("inventory.reserve", SpanKind.CLIENT, this::reserve);
    Tracer.run("payment.charge", SpanKind.CLIENT, this::charge);
});

// Value-returning form; the body may throw a checked exception.
Order order = Tracer.call("order.load", SpanKind.INTERNAL, () -> repository.load(id));`;

const fanOutExample = `// ScopedValue does not propagate through a plain executor, so the parent
// context is captured before the fork and re-established inside the task.
SpanContext parent = Tracer.current().orElse(null);

executor.submit(() -> Tracer.continueIn(parent, "chunk.parse", SpanKind.INTERNAL, () -> {
    parseChunk(file);
    return null;
}));`;

const stampExample = `HttpServerExchangeEvent event = new HttpServerExchangeEvent();
if (event.isEnabled()) {
    // Copies traceId / spanId / parentSpanId from the span in progress.
    Tracer.stamp(event);
    event.method = request.getMethod();
    event.uri = request.getRequestURI();
    event.begin();
    // ... handle the request ...
    event.commit();
}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Traces &amp; Spans"
      icon="bi bi-bezier2"
    />

    <div class="docs-content">
      <p>Traces break a single unit of work — an HTTP request, a background job, an AI tool call — into the operations it is actually made of, and show how long each one took. Jeffrey reads them from the <strong>same JFR recording</strong> that carries the CPU samples, allocations and lock events, which is what makes the drill-down below possible: you can open the flamegraph of what the JVM did <em>inside</em> one span.</p>

      <h2 id="overview">Overview</h2>

      <p>A tracing tool normally tells you that a span took 400&nbsp;ms and stops there — the next question, "doing what?", needs a profiler and a second correlated data source. Jeffrey has both in one file. Every span carries its thread and its time window, and the profile database already scopes flamegraphs, timeseries and event summaries to a list of <code>(thread, from, to)</code> windows, so a span selected in the waterfall becomes a flamegraph query with no extra instrumentation and no second agent.</p>

      <DocsCallout type="info">
        <strong>Not a distributed tracer.</strong> Traces are scoped to a <strong>single JVM</strong> — one recording, one set of traces. Jeffrey mints every trace and span id itself; it does not read or propagate a W3C <code>traceparent</code>, and it will not stitch a request across service boundaries. The goal is profiler-grade breakdown of one process, not a replacement for Jaeger or Tempo.
      </DocsCallout>

      <h2 id="what-is-a-trace">What a Trace Is Here</h2>

      <p>A <strong>span</strong> is one timed operation: a name, a start, a duration, a thread, and a parent. A <strong>trace</strong> is the tree of spans reachable from one root. Both ids are 64-bit values rendered as 16-character hex in the UI and carried over the API as strings, because they exceed the JavaScript safe-integer range.</p>

      <p>Spans reach the profile as ordinary JFR events, which is why no parser configuration is involved. After a recording is parsed, Jeffrey derives two typed tables once — <code>trace_spans</code> and <code>traces</code> — from every traced event type in the recording. Because all of them feed one table, <strong>an HTTP request shows its JDBC statements as native children</strong> without either side knowing about the other.</p>

      <p>Every span carries a <strong>kind</strong>, which is what lets the waterfall answer "was this our own work, or were we waiting on something else":</p>

      <table>
        <thead>
          <tr>
            <th>Kind</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>SERVER</code></td>
            <td>Work performed in response to an inbound call. Typically the trace root.</td>
          </tr>
          <tr>
            <td><code>CLIENT</code></td>
            <td>Waiting on something outside the process — a database, another service. 110&nbsp;ms of a 120&nbsp;ms request spent in <code>CLIENT</code> spans answers most latency questions on its own.</td>
          </tr>
          <tr>
            <td><code>INTERNAL</code></td>
            <td>In-process work. The default.</td>
          </tr>
        </tbody>
      </table>

      <p>and a <strong>status</strong> — <code>UNSET</code>, <code>OK</code> or <code>ERROR</code>. A span that ends by throwing records <code>ERROR</code> and the exception's type; the trace list counts those so a failed run is visible without opening it.</p>

      <h2 id="instrumenting">Instrumenting an Application</h2>

      <p>Tracing lives in the <router-link to="/docs/events/overview">Jeffrey Events</router-link> library, which is zero-dependency and published to Maven Central.</p>

      <DocsCodeBlock :code="mavenDependency" language="xml" />

      <DocsCallout type="warning">
        <strong>Java 25 or newer.</strong> The tracing API is built on <code>ScopedValue</code> (JEP&nbsp;506) and <code>jdk.jfr.Contextual</code>, both finalized in Java&nbsp;25, so <code>jeffrey-events</code> 0.12.0 targets 25. Applications on Java 17–24 can stay on an earlier release for the HTTP, gRPC and JDBC events, but cannot use <code>Tracer</code>.
      </DocsCallout>

      <h3>Opening spans</h3>

      <p>The span in progress is published through a <code>ScopedValue</code>, so nesting needs nothing threaded through the call chain — a nested <code>run</code> or <code>call</code> discovers its parent by itself. The binding is bounded by the lambda, so it cannot outlive the span and never needs clearing.</p>

      <DocsCodeBlock :code="tracerExample" language="java" />

      <p>When the <code>jeffrey.TraceSpan</code> event type is disabled — no recording running, or a configuration that leaves it off — the body runs directly with no binding established and no event committed. Instrumentation is safe to leave in production code.</p>

      <h3>Crossing a thread boundary</h3>

      <p><code>ScopedValue</code> propagates to child threads only through structured concurrency; work submitted to a plain executor does not inherit the current span. Capture the context before the fork and re-establish it with <code>continueIn</code>, which opens a <em>separate</em> child span on the receiving thread rather than carrying one across the boundary:</p>

      <DocsCodeBlock :code="fanOutExample" language="java" />

      <h2 id="auto-instrumented">Events That Already Carry Trace Identity</h2>

      <p>The HTTP, gRPC and JDBC events in <code>jeffrey-events</code> extend a shared base that carries <code>traceId</code>, <code>spanId</code> and <code>parentSpanId</code>. They become spans as soon as those fields are populated — call <code>Tracer.stamp</code> before <code>begin()</code>, or wrap the emission in <code>Tracer.inSpanOf</code>, which stamps and nests in one step:</p>

      <DocsCodeBlock :code="stampExample" language="java" />

      <p>The derivation treats these event types as spans alongside <code>jeffrey.TraceSpan</code>:</p>

      <ul>
        <li><code>jeffrey.HttpServerExchange</code>, <code>jeffrey.HttpClientExchange</code></li>
        <li><code>jeffrey.GrpcServerExchange</code>, <code>jeffrey.GrpcClientExchange</code></li>
        <li><code>jeffrey.JdbcQuery</code>, <code>jeffrey.JdbcInsert</code>, <code>jeffrey.JdbcUpdate</code>, <code>jeffrey.JdbcDelete</code>, <code>jeffrey.JdbcExecute</code>, <code>jeffrey.JdbcStream</code></li>
      </ul>

      <DocsCallout type="tip">
        Stamping the ids costs three zero-defaulted longs on an event you were already emitting — a varint-encoded zero is close to free, so events from an untraced code path are no larger than before. An older recording whose HTTP events carry no ids simply produces no traces; the section stays hidden rather than showing empty roots.
      </DocsCallout>

      <h2 id="trace-list">Trace List</h2>

      <p>The Traces page lists every trace root, sorted by duration by default — the "which runs were slow" view. Each row shows the root operation name and kind, when it started, how long it took, how many spans it contains and how many of them failed. Filter by operation name to narrow to one endpoint or job.</p>

      <h2 id="waterfall">Waterfall</h2>

      <p>Opening a trace gives it its own URL and renders the span tree as a waterfall: indented operation names on the left, proportional duration bars on the right, positioned against the trace's own window. Sub-pixel spans are clamped to a minimum width so a 200&nbsp;µs span inside a 2&nbsp;s trace stays clickable.</p>

      <p>Each bar is split into <strong>self</strong> and <strong>child</strong> segments — the part of the span's duration not covered by any child, and the part that is. A parent whose bar is almost entirely child time is a pass-through; one that is mostly self time is where the work actually happened, and is the bar worth opening.</p>

      <h2 id="span-drill-down">Span Drill-Down</h2>

      <p>Selecting a span opens a drawer with three tabs:</p>

      <ul>
        <li><strong>Attributes</strong> — the span's identity, timing, thread, status and error type, plus any JSON attributes the instrumentation attached.</li>
        <li><strong>Events in span</strong> — the JVM events that occurred on that thread inside the span's window: CPU samples, allocations, monitor blocking, GC. Other spans are excluded, so this is JVM activity rather than a restatement of the tree you are already looking at.</li>
        <li><strong>Flamegraph</strong> — the flamegraph of what ran inside the span, in either <strong>Inclusive</strong> or <strong>Self</strong> mode.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Inclusive vs Self.</strong> Inclusive is the span's whole window. Self subtracts every child's window from it and merges what is left, so the flamegraph shows only the work this span did on its own — the answer to "the parent is slow but its children are not, so what is it doing?". Both come from the same interval primitive, so neither costs more than the other.
      </DocsCallout>

      <h2 id="operations">Operations</h2>

      <p>The trace list answers "which run was slow". The Operations view answers "which operation is slow <em>in general</em>": one row per operation name, with call count, error count, total time, and p50 / p95 / max drawn on a shared rail. A wide p50-to-p95 gap reads as a shape rather than a pair of numbers, which is what distinguishes an operation that is uniformly slow from one that is usually fast and occasionally terrible.</p>

      <h2 id="volume-control">Controlling Span Volume</h2>

      <p>A busy application can emit far more spans than it emits async-profiler spans, and every one of them lands in the JFR chunk. Two levers on the event type keep that in hand, both configurable from a JFR settings file without touching the application:</p>

      <table>
        <thead>
          <tr>
            <th>Setting</th>
            <th>Effect</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>threshold</code></td>
            <td>Drops spans shorter than the given duration. <code>jeffrey.TraceSpan</code> ships with a conservative <strong>1&nbsp;ms</strong> default, so trivial spans cost nothing. Note that dropping a parent leaves its children as orphans, which the tree assembly promotes to roots.</td>
          </tr>
          <tr>
            <td><code>throttle</code></td>
            <td>Caps the emission rate (<code>N/s</code>), sampling rather than truncating. Use it when spans are individually meaningful but too numerous.</td>
          </tr>
        </tbody>
      </table>

      <p>Span names enter the JFR per-chunk string pool, so they must be <strong>stable and low-cardinality</strong>. Name the operation, not the instance of it: <code>order.checkout</code>, never <code>order.checkout.a3f9c1</code>.</p>

      <h2 id="limits">Limits</h2>

      <ul>
        <li><strong>A span must begin and end on the same thread.</strong> JFR attributes a duration event to the thread that <em>commits</em> it, so a span handed off mid-flight is recorded against the wrong thread — which also breaks the thread-plus-window correlation the drill-down relies on. <code>ScopedValue</code> plus the <code>continueIn</code> pattern keeps spans thread-confined in practice, so this is a sharp edge rather than an everyday problem.</li>
        <li><strong>Work that outlives the lambda is not nested under it.</strong> Callback-driven instrumentation — a gRPC call whose real work runs from listener callbacks after the interceptor returns — records the exchange itself but cannot nest the work beneath it, because the span's binding closes when the lambda does.</li>
        <li><strong>Background jobs are their own traces.</strong> A pipeline run forked onto its own thread appears as a separate root rather than a child of whatever request triggered it. That is deliberate — its lifetime is unrelated to the request — but worth remembering when reading the trace list.</li>
        <li><strong>Trace ids are 64-bit</strong>, not the 128-bit W3C shape. Ample for a single JVM that mints all of its own ids, and a deliberate trade: it means an application already running OpenTelemetry cannot hand Jeffrey its real <code>traceparent</code> and expect the ids to match what Jaeger or Datadog display.</li>
        <li><strong>The waterfall is sized for tens to hundreds of spans</strong> per trace, and has no zoom or pan. Deep traces render fine; traces of several thousand spans would want a different substrate.</li>
      </ul>

      <DocsCallout type="info">
        <strong>Two kinds of "span" coexist.</strong> <strong>Async-Profiler Spans</strong> are flat, per-thread, tag-based, and need the patched async-profiler agent. <strong>Traces &amp; Spans</strong> are nested, trace-scoped, and pure JFR. They are complementary — the former still scopes flamegraphs when tracing is off — and Jeffrey keeps them in separate sections rather than merging them.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
