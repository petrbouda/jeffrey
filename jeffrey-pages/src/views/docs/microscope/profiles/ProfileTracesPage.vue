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
  { id: 'operations', text: 'Trace Operations', level: 2 },
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

const virtualThreadExample = `POST /api/internal/recordings/…   tomcat-handler-53  virtual   4173ms    0 samples
  profile.initialize              tomcat-handler-53  virtual   4102ms    0
    recording.parse               tomcat-handler-53  virtual   2797ms    0
      chunk.parse                 bulk-parallel      platform  2730ms  153
      chunk.parse                 bulk-parallel      platform  2571ms  140
    profile.data-init             tomcat-handler-53  virtual    933ms    0
      guardian.results            parallel           platform   932ms   63`;

const fanOutExample = `// ScopedValue does not propagate through a plain executor, so the parent
// context is captured before the fork and re-established inside the task.
SpanContext parent = Tracer.current().orElse(null);

executor.submit(() -> Tracer.continueIn(parent, "chunk.parse", SpanKind.INTERNAL, () -> {
    parseChunk(file);
    return null;
}));`;

const reenterExample = `// A gRPC call arrives in pieces, on threads the interceptor does not control,
// so the span is opened without binding and resumed around every callback.
SpanContext span = Tracer.openSpanOf(event);

return new SimpleForwardingServerCallListener<>(listener) {
    @Override
    public void onHalfClose() {          // where a unary handler actually runs
        Tracer.reenter(span, () -> {
            super.onHalfClose();
            return null;
        });
    }
};`;

const stampExample = `JdbcQueryEvent event = new JdbcQueryEvent("listSpans", "profile");
if (event.isEnabled()) {
    // Gives the statement a span of its own, under the span in progress.
    Tracer.stamp(event);
    event.begin();
    // ... run the statement ...
    event.end();
    event.sql = sql;
    event.rows = rows;
    // Derives the span shape from the event's own fields, then commits.
    event.commitSpan();
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

      <p>Tracing lives in the <router-link to="/docs/events/overview">Jeffrey Events</router-link> library, which is zero-dependency and published to Maven Central. This section covers the common patterns; the <router-link to="/docs/events/tracer">Tracer API reference</router-link> documents every method with the span tree it produces.</p>

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

      <h3>Resuming a span the work arrives back into</h3>

      <p><code>continueIn</code> is right when the receiving thread is doing a <em>separate</em> piece of work. A callback-driven protocol is not that: a gRPC call is one operation delivered in fragments, long after the interceptor that started it returned. <code>Tracer.openSpanOf</code> opens the span and hands back its context without binding anything, and <code>Tracer.reenter</code> resumes that same span — not a child of it — around each callback:</p>

      <DocsCodeBlock :code="reenterExample" language="java" />

      <p>Each re-entry emits a <code>jeffrey.TraceScope</code> event recording which thread the span ran on and for how long. That is what the drill-down and the span-scoped flamegraph read: a re-entered span can be committed on a thread it barely ran on, and the scopes are the only record of where the work actually happened. Spans that are never re-entered emit none — <code>call</code>, <code>inSpan</code> and <code>inSpanOf</code> are thread-confined already, so their span is its own single scope and existing instrumentation pays nothing.</p>

      <h2 id="auto-instrumented">Every Instrumented Event Is a Span</h2>

      <p>The HTTP, gRPC and JDBC events in <code>jeffrey-events</code> extend <code>AbstractTracedEvent</code>, which carries the whole span shape — <code>traceId</code>, <code>spanId</code>, <code>parentSpanId</code>, plus the <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code> and <code>attributes</code> a <code>jeffrey.TraceSpan</code> carries. An event that has them <em>is</em> a span; there is nothing for Jeffrey to work out from the event type.</p>

      <p>Two calls populate them. <code>Tracer.stamp</code> gives the event a span of its own nested inside the span in progress — the form for a leaf, like a statement. <code>Tracer.inSpanOf</code> makes the event <em>be</em> the span it opens, so anything traced underneath nests inside it — the form for an entry point, like an inbound request. Committing through <code>commitSpan()</code> lets the event derive its own name and status first:</p>

      <DocsCodeBlock :code="stampExample" language="java" />

      <p>Each event type answers for its own span shape once, in its own class: an HTTP exchange names itself by method and matched URI template and fails from 400 upwards, a gRPC call names itself by service and method and fails on anything but <code>OK</code>, a statement takes its label and fails on what it threw.</p>

      <DocsCallout type="tip">
        The derivation names no event type at all. It treats an event as a span when the recording's own metadata says it declares a <code>spanId</code> field, so instrumentation written outside Jeffrey takes part in traces with no change to Jeffrey — extend <code>AbstractTracedEvent</code> and stamp.
      </DocsCallout>

      <DocsCallout type="tip">
        The identity costs three zero-defaulted longs on an event you were already emitting — a varint-encoded zero is close to free, so events from an untraced code path are barely larger than before. An older recording whose events carry no ids simply produces no traces; the section stays hidden rather than showing empty roots.
      </DocsCallout>

      <h2 id="trace-list">Trace List</h2>

      <p>The Traces page opens with two tiles — how many traces and spans the profile holds, how many failed, and the P95 / P99 / slowest trace duration — over a list of trace roots ranked by duration, the "which runs were slow" view. Each row carries the root operation name, a duration bar scaled to the slowest trace in the profile, the kind, the failure count when there is one, the span count, the start time and the trace id. The gutter beside a failed trace turns red, so a bad run is visible before anything is read. The list shows the fifty slowest; <a href="#operations">Trace Operations</a> is where you narrow to one endpoint or job.</p>

      <h2 id="waterfall">Waterfall</h2>

      <p>Opening a trace renders its span tree as a waterfall filling a full-screen view: indented operation names on the left, proportional duration bars on the right, positioned against the trace's own window. The trace id is kept in the URL as <code>?trace=</code> while the view is open, so a trace can still be linked to and returned to. Sub-pixel spans are clamped to a minimum width so a 200&nbsp;µs span inside a 2&nbsp;s trace stays clickable.</p>

      <p>Each bar is split into <strong>self</strong> and <strong>child</strong> segments — the part of the span's duration not covered by any child, and the part that is. A parent whose bar is almost entirely child time is a pass-through; one that is mostly self time is where the work actually happened, and is the bar worth opening.</p>

      <h2 id="span-drill-down">Span Drill-Down</h2>

      <p>Selecting a span expands its detail inline, underneath the row, rather than in a drawer beside it — the waterfall stays visible, so the span keeps the context of the tree it sits in. The detail shows:</p>

      <ul>
        <li><strong>Attributes</strong> — the span's identity, timing, thread, status and error type, plus any JSON attributes the instrumentation attached.</li>
        <li><strong>Events in span</strong> — opens the JVM events that occurred on that thread inside the span's window: CPU samples, allocations, monitor blocking, GC. Other spans are excluded, so this is JVM activity rather than a restatement of the tree you are already looking at. It takes over the view, because it is a timeline: one lane per event type over the span's window, a mini-map you can drag to zoom into part of it, and a per-type breakdown that both filters the lane and offers the flamegraph for that type. The same component serves the async-profiler span drill-down, so the two read identically.</li>
        <li><strong>Flamegraph</strong> — the flamegraph of what ran inside the span, in either <strong>Inclusive</strong> or <strong>Self</strong> mode.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Inclusive vs Self.</strong> Inclusive is the span's whole window. Self subtracts the windows of the children that ran <em>on the same thread</em> and merges what is left, so the flamegraph shows only the work this span did on its own — the answer to "the parent is slow but its children are not, so what is it doing?". A child forked onto another thread is not subtracted: the parent's thread was busy with its own work the whole time it ran. Both scopes come from the same interval primitive, so neither costs more than the other.
      </DocsCallout>

      <DocsCallout type="info">
        <strong>How a sample is attributed to a span.</strong> Nothing stamps a span id onto a sample. A span is a <code>(thread, window)</code> pair, and the flamegraph is every sample whose thread matches and whose timestamp falls inside — the same mechanism the async-profiler Spans feature uses, on the same clock, because <code>jfrsync</code> writes both the JVM's events and the profiler's samples into one recording. Two consequences worth knowing: the window's edges are millisecond-floored, and a span shorter than the sampling interval may enclose no sample at all.
      </DocsCallout>

      <h3 id="virtual-threads">Spans on virtual threads have no flamegraph</h3>

      <p>The third consequence deserves its own heading, because it decides whether the Flamegraph tab appears at all. Here is one real trace — a recording upload, then the parse it triggers — with the thread each span ran on and the samples that matched it:</p>

      <DocsCodeBlock :code="virtualThreadExample" language="text" />

      <p>The request's own spans record 4.1&nbsp;s of real work and match <strong>zero</strong> samples. Its forked children match immediately, from the same recording, in the same window. Nothing is missing: the difference is attribution. Async-profiler's CPU engine — <code>event=ctimer</code>, Jeffrey's default — names the <strong>carrier</strong> thread a virtual thread is mounted on, never the virtual thread itself, so a span on one can never match. Measured on that recording: with <code>event=ctimer</code>, 0 of 2,646 samples land on a virtual thread; drop it and the JVM's own sampler puts 487 of 992 on <code>tomcat-handler-1</code>.</p>

      <p>So a flamegraph appears exactly where the work left the request thread. On a span or an operation that stayed on a virtual thread Jeffrey keeps the tab and explains the emptiness there, with this same worked example — an unexplained empty graph reads as a bug, and this is not one. Two ways to get flamegraphs for request-thread work: fork the work you care about onto a pool thread with <router-link to="#instrumenting">continueIn</router-link>, which is what <code>chunk.parse</code> above does, or drop <code>event=</code> from the <router-link to="/docs/microscope/profiler-settings">agent settings</router-link> and let the JVM sample CPU itself — at the cost of async-profiler's better engine. Allocation and wall-clock samples are attributed to the carrier either way.</p>

      <h2 id="operations">Trace Operations</h2>

      <p>The trace list answers "which run was slow". Trace Operations answers "which <em>kind</em> of run is slow, across every time it ran": one card per <strong>trace type</strong>, grouped from the <code>traces</code> table. A trace type is keyed by all three of the root span's name, its kind and the event type that opened it — not by the name alone: an inbound <code>GET /orders</code> and an outbound call to the same path are named identically by the same convention, and they are not the same operation. Each card leads with the call count, then Spans / Total / P50 / P95 / Max badges; the name row carries the event type and the kind, and an error-count badge sits on the right when the type has failures. Sort by total, P95, max, call count or errors.</p>

      <DocsCallout type="info">
        <strong>Where did the nested spans go?</strong> This list used to be every span name in the profile — including names, like <code>chunk.parse</code> or <code>dominator</code>, that only ever occur nested inside another span and are never a trace root. Grouping by root name instead of span name dropped one reference profile's list from 105 rows to 36. A nested span is not lost: open the trace it belongs to and find it in the <a href="#waterfall">waterfall</a>, alongside every other span in that trace's tree.
      </DocsCallout>

      <p>The two tiles above the list are not scoped alike. <strong>Operations</strong> — the operation count, plus total traces and errors underneath — reads a SQL aggregate over the whole profile, uncapped. <strong>Slowest Operation</strong> is mixed: its <em>Total</em> sub-value is the same profile-wide aggregate, but the headline duration and the <em>Worst P95</em> beside it are the highest max and P95 found among the operation rows the page actually fetched (capped at 100) — there is no profile-wide aggregate that can answer "what is the slowest single operation", only one that sums or maxes across all spans at once. On a profile with more than 100 trace types, that headline can undercount.</p>

      <p>Clicking a card opens a drill-down for that operation. The selection is kept in the URL — <code>?operation=</code> with <code>&amp;kind=</code> and <code>&amp;eventType=</code> alongside it, since all three identify the type — so it can be linked to directly. It has four tabs:</p>

      <ul>
        <li><strong>Summary</strong> — the tab it opens on. Call count, latency percentiles, total time and the share of all trace time it accounts for, read from the same profile-wide aggregate the card above shows, so the two never disagree. Underneath: a latency histogram, the platform/virtual thread split, where the operation's time goes span by span, and its slowest runs. The histogram, the thread split and the concurrency figure are drawn from the fetched traces, which are capped — the card says so when they are.</li>
        <li><strong>Flamegraphs</strong> — every execution, wall-clock and allocation sample taken while a trace of this type was running, covering exactly the windows those traces ran in and no more. The scope is every span of every trace of the type, nested ones included. When all of them ran on virtual threads the tab stays and explains why it is empty, rather than vanishing — see <a href="#virtual-threads">Spans on virtual threads</a>.</li>
        <li><strong>Metrics Timeline</strong> — the slowest trace of this type and the trace count, bucketed over time.</li>
        <li><strong>Slowest Traces</strong> — the same unfiltered ranked list as the <a href="#trace-list">Trace List</a> above, scoped to this operation's traces; opening a row shows that trace's waterfall. Like that list, it displays the 50 longest at a time. What the larger, 1,000-trace fetch behind it buys is not more rows on screen: it feeds the "Showing 50 of&nbsp;…" count, scales the duration bars against the true slowest of the type rather than just the 50 shown, and is the pool the cap note is counting against once a type reaches it.</li>
      </ul>

      <p>A stale or hand-edited operation in the URL is not rejected — the drill-down opens and its own panels come back empty. The list above is capped, so a link into an operation past the cap is a valid link, and refusing it would have broken more than it caught.</p>

      <h2 id="volume-control">Controlling Span Volume</h2>

      <p>A busy application can emit far more spans than it emits async-profiler spans, and every one of them lands in the JFR chunk. <code>jeffrey.TraceSpan</code> sets neither lever by default — every span is recorded, however short — because how much span volume is acceptable is a property of the application, not of the event. Both levers are configurable from a JFR settings file without touching the application:</p>

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
            <td>Drops spans shorter than the given duration — <code>threshold=1ms</code> is a reasonable starting point when the volume needs cutting. It costs more than it appears to: dropping a parent leaves its children as orphans, which the tree assembly promotes to roots, and dropping a child moves its samples into the parent's <strong>self</strong> time, since a window that was never recorded cannot be subtracted.</td>
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
        <li><strong>A span's own event names the thread that ended it.</strong> JFR attributes a duration event to the thread that <em>commits</em> it, so a span closed somewhere other than where it opened is filed against the closing thread. Re-entered spans emit a <code>jeffrey.TraceScope</code> event per activation — bounded by one lambda, so it cannot straddle a thread — and the drill-down reads those, which is what keeps the thread-plus-window correlation honest. The span row itself still shows the closing thread.</li>
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
