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
  { id: 'waterfall', text: 'Waterfall', level: 2 },
  { id: 'blocking-ops', text: 'Blocking Operations as Spans', level: 2 },
  { id: 'jvm-context', text: 'Why a Trace Was Slow', level: 2 },
  { id: 'span-drill-down', text: 'Span Drill-Down', level: 2 },
  { id: 'operations', text: 'Traces by Operation', level: 2 },
  { id: 'attributes', text: 'Traces by Attributes', level: 2 },
  { id: 'ai-export', text: 'AI Export', level: 2 },
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

const fanOutExample = `// ScopedValue does not propagate through a plain executor. fork captures the
// enclosing span here, on the submitting thread, and the task re-establishes
// it wherever the pool eventually runs it. The kind defaults to INTERNAL.
executor.submit(Tracer.fork("chunk.parse", () -> parseChunk(file)));

// Supplier form for value-returning tasks:
CompletableFuture.supplyAsync(
    Tracer.fork("chunk.parse", () -> parseAndCollect(file)),
    executor);`;

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
    event.begin();
    // ... run the statement ...
    event.end();
    event.sql = sql;
    event.rows = rows;
    // Gives the statement a span of its own under the span in progress,
    // derives the span shape from the event's own fields, then commits.
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

      <p>and a <strong>status</strong> — <code>UNSET</code>, <code>OK</code> or <code>ERROR</code>. A span that ends by throwing records <code>ERROR</code> and the exception's type; the operation cards and the search results count those, so a failed run is visible without opening it.</p>

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

      <p><code>ScopedValue</code> propagates to child threads only through structured concurrency; work submitted to a plain executor does not inherit the current span. Wrap the task with <code>Tracer.fork</code>, which captures the current span when the task is wrapped and opens a <em>separate</em> child span on the receiving thread rather than carrying one across the boundary. (The underlying primitive, <code>continueIn</code>, remains available for when the captured context has to travel further than the wrapping site.)</p>

      <DocsCodeBlock :code="fanOutExample" language="java" />

      <h3>Resuming a span the work arrives back into</h3>

      <p><code>continueIn</code> is right when the receiving thread is doing a <em>separate</em> piece of work. A callback-driven protocol is not that: a gRPC call is one operation delivered in fragments, long after the interceptor that started it returned. <code>Tracer.openSpanOf</code> opens the span and hands back its context without binding anything, and <code>Tracer.reenter</code> resumes that same span — not a child of it — around each callback:</p>

      <DocsCodeBlock :code="reenterExample" language="java" />

      <p>Each re-entry emits a <code>jeffrey.TraceScope</code> event recording which thread the span ran on and for how long. That is what the drill-down and the span-scoped flamegraph read: a re-entered span can be committed on a thread it barely ran on, and the scopes are the only record of where the work actually happened. Spans that are never re-entered emit none — <code>call</code> and <code>inSpanOf</code> are thread-confined already, so their span is its own single scope and existing instrumentation pays nothing.</p>

      <p class="docs-read-more">
        <router-link to="/docs/microscope/profiles/traces/api">Full Tracer API reference &rarr;</router-link>
      </p>

      <h2 id="auto-instrumented">Every Instrumented Event Is a Span</h2>

      <p>The HTTP, gRPC and JDBC events in <code>jeffrey-events</code> extend <code>AbstractTracedEvent</code>, which carries the whole span shape — <code>traceId</code>, <code>spanId</code>, <code>parentSpanId</code>, plus the <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code> and <code>attributes</code> a <code>jeffrey.TraceSpan</code> carries. An event that has them <em>is</em> a span; there is nothing for Jeffrey to work out from the event type.</p>

      <p>Both roles commit through <code>commitSpan()</code>, which stamps an event that does not yet carry trace identity and leaves one that does untouched. A leaf, like a statement, is stamped by the commit itself with a span of its own nested inside the span in progress — the stamp cannot be forgotten. An entry point, like an inbound request, runs through <code>Tracer.inSpanOf</code> first, which makes the event <em>be</em> the span it opens, so anything traced underneath nests inside it. Both let the event derive its own name and status first:</p>

      <DocsCodeBlock :code="stampExample" language="java" />

      <p>Each event type answers for its own span shape once, in its own class: an HTTP exchange names itself by method and matched URI template and fails from 400 upwards, a gRPC call names itself by service and method and fails on anything but <code>OK</code>, a statement takes its label and fails on what it threw.</p>

      <p>Jeffrey applies the same conventions again when it derives the trace, rather than reading the recorded name back. They are the naming rules OpenTelemetry states for the same operations, and applying them at the point the operation is assembled is what makes one endpoint one operation: a recorded name is only ever this rule evaluated by whichever version of the library produced the recording, so an endpoint that only some recordings carry the answer for would be split across two rows of Traces by Operation.</p>

      <p>Where the naming conventions come from is layered: what the recording itself declares per event type — the <code>@Span</code> template carried in its metadata — comes first, then the built-in conventions for Jeffrey's own types on recordings that predate the annotation, then the name the event recorded for itself, and only then the event type as a last resort. The <em>verdict</em> is not layered the same way, because it is not derivable: an exchange that threw and still answered 200 knows something its code does not, so a span's status is the writer's statement — recorded through <code>commitSpan()</code> or <code>failed()</code> — and Jeffrey's built-in rules judge only its own exchange types' codes, on recordings of any vintage.</p>

      <DocsCallout type="tip">
        Discovery names no event type at all. Jeffrey treats an event as a span when the recording's own metadata says it declares a <code>spanId</code> field, so instrumentation written outside Jeffrey takes part in traces with no change to Jeffrey — extend <code>AbstractTracedEvent</code> and stamp. Naming works the same way: annotate the class with <code>@Span</code> and the template travels inside every recording, applied by Jeffrey without knowing the type. Failure detection is the one thing that must be recorded: commit through <code>commitSpan()</code>.
      </DocsCallout>

      <DocsCallout type="tip">
        The identity costs three zero-defaulted longs on an event you were already emitting — a varint-encoded zero is close to free, so events from an untraced code path are barely larger than before. An older recording whose events carry no ids simply produces no traces; the section stays hidden rather than showing empty roots.
      </DocsCallout>

      <h2 id="waterfall">Waterfall</h2>

      <p>Opening a trace renders its span tree as a waterfall filling a full-screen view: indented operation names on the left, proportional duration bars on the right, positioned against the trace's own window. The trace id is kept in the URL as <code>?trace=</code> while the view is open, so a trace can still be linked to and returned to. Sub-pixel spans are clamped to a minimum width so a 200&nbsp;µs span inside a 2&nbsp;s trace stays clickable.</p>

      <p>Each bar is split into <strong>self</strong> and <strong>child</strong> segments — the part of the span's duration not covered by any child, and the part that is. A parent whose bar is almost entirely child time is a pass-through; one that is mostly self time is where the work actually happened, and is the bar worth opening. Self time is computed once, at derivation, by merging each span's same-thread child windows in SQL — so the waterfall, the operation breakdown and the exports all read the same number rather than three that could drift.</p>

      <h2 id="blocking-ops">Blocking Operations as Spans</h2>

      <p>The JVM already records where a thread stood still — <code>jdk.SocketRead</code> and <code>jdk.SocketWrite</code>, <code>jdk.FileRead</code>, <code>jdk.FileWrite</code> and <code>jdk.FileForce</code>, lock contention (<code>jdk.JavaMonitorEnter</code>), <code>Object.wait</code>, parking, sleeping, ZGC allocation stalls and pinned virtual threads. When Jeffrey derives a profile's traces, it <strong>promotes</strong> each of these events into a leaf span of its own, hanging under the innermost span that was open on the same thread when the wait began. A JDBC span that spent 39&nbsp;ms of its 43 on the database socket shows exactly that: the socket read is a child bar with a real position and duration, and the parent's solid self segments shrink by the same stretch.</p>

      <p>Promoted spans are ordinary spans everywhere it matters — they count in the trace's span total, appear in the operation's span breakdown ("Socket read" ranks against your own operations), and subtract from their parent's self time — but they are marked apart: drawn solid in their wait category's colour rather than a span-kind pastel, and their detail names the event they came from. The event's own payload (host and port, file path, monitor class, parked class) is right there in the inline detail. Two toolbar toggles govern them along the reader's question: <strong>Blocking ops</strong> for the lock, park, sleep and stall rows, and <strong>I/O</strong> for the file and socket rows — so hiding the lock noise never hides the socket read that explains the trace. Switching both off reads the recorded span structure alone; a toggle whose family recorded nothing stays visible but disabled. The legend below the waterfall decodes the colours; it does not filter, so the two toolbar toggles are the whole answer to what is drawn.</p>

      <DocsCallout type="tip">
        Nothing is instrumented for this and nothing new is recorded — the promotion is pure analysis over events every recording already contains, so it applies retroactively to existing profiles. What bounds it is the recording itself: these JDK events carry thresholds (typically 10–20&nbsp;ms by default), so only waits long enough to be recorded are long enough to become bars. An event that began outside every span stays an ordinary event.
      </DocsCallout>

      <h2 id="jvm-context">Why a Trace Was Slow</h2>

      <p>A span's bar says it took 200&nbsp;ms; it does not say whether that was 200&nbsp;ms of computing, of waiting on a lock, or of standing still inside a GC pause — three different problems wearing the same bar. The waterfall overlays the answer, and a panel beneath it sums it up.</p>

      <p><strong>On the bars</strong>: stop-the-world pauses — GC pauses and safepoints — are drawn as vertical lanes across the whole waterfall, because they stopped every span they cross. What an individual span's thread was waiting on is not an overlay at all any more: those waits are the <a href="#blocking-ops">promoted blocking spans</a>, child bars in their category's colour. The one thread-scoped category that stays pure context is deoptimization — an instant, not a stretch of blocked time. The "JVM context" toolbar toggle switches the pause overlay off, and the toggles reset per trace, so one trace's tuning does not silently reshape the next.</p>

      <p><strong>One band per stoppage.</strong> A collection pause is recorded as a <code>GCPhasePause</code> plus the levelled phases that ran inside it, and the phases are a breakdown rather than pauses of their own — every one of them overlaps the band above it, so drawing them redraws the same stopped world up to five times without adding a single stretch of it. The lanes draw only the pauses themselves; totals are unaffected, because a lane's figure and the why-slow panel's both merge overlapping intervals before summing — an instant the JVM was stopped counts once, however many phases describe it.</p>

      <p><strong>Reading an instant</strong>: a band cannot be read as a duration. Below about half a percent of the trace a pause is thinner than a pixel, so every short one is drawn at the same minimum width — the lanes say <em>where</em> the JVM stopped but never <em>for how long</em>. Moving the pointer across the waterfall draws a time cursor and reads out the instant under it: how far into the trace it is, and, when it falls inside a pause, that pause's name and its true duration. Hovering a row marks it with a rule above and below rather than a fill, so the bands stay visible on the row being inspected. The same offset and wall-clock time are in the span detail, which <kbd>Enter</kbd> opens from the keyboard.</p>

      <DocsCallout type="info">
        <strong>Durations are carried in nanoseconds, and unmeasured pauses are not drawn.</strong> A GC phase is routinely shorter than a microsecond — 41&nbsp;ns is the shortest in a recording measured while writing this — so a pause duration is taken from the recording as written rather than rebuilt from a microsecond-resolution end. JFR also writes some phases with no duration at all; those are dropped rather than defaulted to zero, because a pause of unknown length is not a stretch of stopped time, and drawing it at the minimum band width claims one the recording never did.
      </DocsCallout>

      <p><strong>Under the bars</strong>, the <em>"Why was this trace slow?"</em> panel ranks where the trace's wall-clock time went, category by category, with the remainder named as <strong>own work</strong> rather than left as an unexplained gap — a breakdown that only names the waiting invites the reader to assume the rest is mystery. The promoted categories' figures are summed from the promoted spans themselves, so the panel's numbers and the bars in the waterfall are one source of truth by construction. Each category links out to the profile view that explains it: a GC finding leads to the Garbage Collection view, a lock finding to Blocking Operations, an I/O finding to the Socket or File I/O view.</p>

      <DocsCallout type="info">
        <strong>Overlap, not starts-inside.</strong> A pause is attributed to a trace when their windows <em>overlap</em>: a 40&nbsp;ms collection that began 5&nbsp;ms before a span started is exactly the pause that explains it, and a starts-inside match would miss it. GC pauses and safepoints are emitted on VM threads, so this matching is thread-agnostic; the thread-scoped categories are matched against the span's own thread. Because global pauses stop every thread at once, summing a trace's categories can legitimately exceed its duration when spans ran concurrently — the panel measures against the trace's own window instead.
      </DocsCallout>

      <h2 id="span-drill-down">Span Drill-Down</h2>

      <p>Selecting a span expands its detail inline, underneath the row, rather than in a drawer beside it — the waterfall stays visible, so the span keeps the context of the tree it sits in. The detail shows:</p>

      <ul>
        <li><strong>Attributes</strong> — the span's identity, timing, thread, status and error type, plus any JSON attributes the instrumentation attached.</li>
        <li><strong>Events in span</strong> — opens the JVM events that occurred on that thread inside the span's window: CPU samples, allocations, GC. Other spans are excluded — and so are the blocking events the derivation promoted, which are already child bars of the very span being opened — so this is JVM activity rather than a restatement of the tree you are already looking at. It takes over the view, because it is a timeline: one lane per event type over the span's window, a mini-map you can drag to zoom into part of it, and a per-type breakdown that both filters the lane and offers the flamegraph for that type. The same component serves the async-profiler span drill-down, so the two read identically.</li>
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

      <h2 id="operations">Traces by Operation</h2>

      <p>Traces by Operation is where a profile's traces are read from: it answers "which <em>kind</em> of run is slow, across every time it ran", where <a href="#attributes">Traces by Attributes</a> answers "which particular runs match this". One card per <strong>trace type</strong>, grouped from the <code>traces</code> table. An operation's name is derived from what the root did — <code>GET /api/internal/profiles/{profileId}</code>, <code>jeffrey.api.v1.ProjectService/List</code> — rather than read out of the recording, so the same endpoint is one operation whichever version of the library recorded it. A trace type is keyed by all three of that name, the root's kind and the event type that opened it — not by the name alone: an inbound <code>GET /orders</code> and an outbound call to the same path are named identically by the same convention, and they are not the same operation. Each card leads with the call count, then Spans / Total / P50 / P95 / Max badges; the name row carries the event type and the kind, and an error-count badge sits on the right when the type has failures. Sort by total, P95, max, call count or errors.</p>

      <DocsCallout type="info">
        <strong>Where did the nested spans go?</strong> This list used to be every span name in the profile — including names, like <code>chunk.parse</code> or <code>dominator</code>, that only ever occur nested inside another span and are never a trace root. Grouping by root name instead of span name dropped one reference profile's list from 105 rows to 36. A nested span is not lost: open the trace it belongs to and find it in the <a href="#waterfall">waterfall</a>, alongside every other span in that trace's tree.
      </DocsCallout>

      <p>The tiles above the list all read the same profile-wide SQL aggregate, uncapped — the operation count with total traces and errors, and the slowest-trace / P99 durations. They deliberately do not fold the fetched rows, which are capped and re-cut by every filter: a tile that changed value when you sorted the list would be lying about the profile. The list itself is searched, filtered and paged on the server, with the filter in the URL — so "look at the failing operations" is a link rather than a set of instructions.</p>

      <p>Clicking a card opens a drill-down for that operation. The selection is kept in the URL — <code>?operation=</code> with <code>&amp;kind=</code> and <code>&amp;eventType=</code> alongside it, since all three identify the type — so it can be linked to directly. It has four tabs:</p>

      <ul>
        <li><strong>Summary</strong> — the tab it opens on. Call count, latency percentiles, total time and the share of all trace time it accounts for, read from the same profile-wide aggregate the card above shows, so the two never disagree. Underneath: a latency histogram, the platform/virtual thread split, where the operation's time goes span by span, and its slowest runs. The histogram, the thread split and the concurrency figure are drawn from the fetched traces, which are capped — the card says so when they are.</li>
        <li><strong>Flamegraphs</strong> — every execution, wall-clock and allocation sample taken while a trace of this type was running, covering exactly the windows those traces ran in and no more. The scope is every span of every trace of the type, nested ones included. When all of them ran on virtual threads the tab stays and explains why it is empty, rather than vanishing — see <a href="#virtual-threads">Spans on virtual threads</a>.</li>
        <li><strong>Metrics Timeline</strong> — the slowest trace of this type and the trace count, bucketed over time.</li>
        <li><strong>Slowest Traces</strong> — this operation's traces ranked by duration; opening a row shows that trace's <a href="#waterfall">waterfall</a>, in place, with the trace id added to the URL rather than a page of its own. It displays the 50 longest at a time. What the larger, 1,000-trace fetch behind it buys is not more rows on screen: it feeds the "Showing 50 of&nbsp;…" count, scales the duration bars against the true slowest of the type rather than just the 50 shown, and is the pool the cap note is counting against once a type reaches it.</li>
      </ul>

      <p>A stale or hand-edited operation in the URL is not rejected — the drill-down opens and its own panels come back empty. The list above is capped, so a link into an operation past the cap is a valid link, and refusing it would have broken more than it caught.</p>

      <h2 id="attributes">Traces by Attributes</h2>

      <p>Every span carries two key/value payloads, and until this page they were readable only one span at a time, inside the waterfall. The first is the open <strong>attributes</strong> map a developer attaches at the call site — <code>tenant</code>, <code>cache.hit</code>, <code>order.items</code>, whatever was passed. The second is what the event type <strong>declares about itself</strong>: a statement's <code>rows</code> and <code>group</code>, an exchange's <code>uri</code> and <code>statusCode</code>, a custom <code>@Span</code> event's own fields. Alongside them the page exposes the <strong>span shape</strong> — <code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code>, <code>eventType</code> — so one query surface answers "spans that failed" and "spans of this tenant" the same way. All three are flattened once, immediately after the trace tables are derived, into an index that makes filtering and grouping ordinary SQL rather than JSON extraction repeated per span per query.</p>

      <p>The two breakdown pages reach a key in two steps, because a key on its own is not a question. First pick the <strong>event type</strong> whose spans you mean — the list is the event types that actually produced spans, ranked by how many, with each type's trace count, error count and how many of its attributes are narrow enough to break down. Then pick one of the <strong>attributes those spans carried</strong>. Each answered step collapses to a single row stating what it holds, so the picker is about eighty pixels once the breakdown below it is what you are reading; clicking a collapsed row expands that step again in place.</p>

      <p>The two steps are what make the second list honest. An event field belongs to the event type declaring it — <code>rows</code> on a JDBC query and <code>rows</code> on anything else are two keys that happen to share a name — but the attributes a developer attaches belong to no event type at all: one <code>tenant</code> rides on an HTTP span, a Kafka span and a hand-written one alike. Naming the event type first is what lets the page say how many values <code>tenant</code> took <em>on HTTP spans</em>, and the breakdown that opens is scoped to the same spans, so the number the picker showed is the number the table reproduces. A newly instrumented event type needs no change here — it is in the first step the first time its recording is opened, with its declared fields under it, the same property the <a href="#operations">naming convention</a> already has.</p>

      <DocsCallout type="warning">
        <strong>One span, or anywhere in the trace?</strong> The search offers both, because they are different questions with different answers. A trace whose HTTP span carries <code>tenant=acme</code> and whose JDBC span carries <code>rows&nbsp;&gt;&nbsp;10000</code> matches "anywhere in the trace" and does not match "all on one span" — attributes are recorded per span and are never inherited down the tree. A page that quietly picked one of the two would be wrong for whoever wanted the other.
      </DocsCallout>

      <p>The three readings are three pages, listed under <strong>Traces by Attributes</strong> in the sidebar. The conditions, the scope and the selected key all live in the URL, so a filter is a link rather than a set of instructions — and the key travels between the pages, so a key opened in Attribute Values is still the one Latency draws:</p>

      <ul>
        <li><strong>Search Traces</strong> <code>/technologies/traces/attributes/search</code> — conditions built from the catalog and ANDed together, with <code>=</code>, <code>≠</code>, <code>contains</code>, the four numeric comparisons and "is present". Only the operators a key's values can answer are offered; the comparisons read a numeric column filled only where the value is a number, so <code>rows&nbsp;&gt;&nbsp;9</code> never compares <code>"9"</code> against <code>"10000"</code> as text. This is the one page without the key rail — its subject is traces rather than a key, and it is the only one that works with none selected — so the builder's own key list carries what the rail would have said: the source it came from, how many values it has, and whether it is search only. Above the results: how many traces matched and how their percentiles compare with the profile's, and a density strip drawing the matches against every trace as a backdrop. Each result row shows <em>which span</em> matched and what it held, so the list is usually the whole answer rather than the start of one.</li>
        <li><strong>Attribute Values</strong> <code>/technologies/traces/attributes/values</code> — one key of one event type, broken down into every value it took, ranked by total time rather than by call count: a busy value and an expensive value are rarely the same value. Each row carries the traces holding it, their P50, P95 and max, and their error rate. A trace that recorded two values of one key counts towards both, so the rows do not sum to the profile — and traces where no span carried the key at all get a row of their own rather than being silently dropped.</li>
        <li><strong>Latency by Attributes</strong> <code>/technologies/traces/attributes/latency</code> — each value's traces spread over log-spaced duration buckets. Percentiles hide bimodality: a value whose traces are either fast or catastrophic has the same median as one that is uniformly mediocre, and it is the first that is worth looking at. Colour is normalised inside each row, so the shapes are comparable even where one value ran ten times as often.</li>
      </ul>

      <DocsCallout type="info">
        <strong>Cardinality is the guard.</strong> A <code>user.id</code> attribute on sixty thousand spans is legitimate instrumentation, and every breakdown of it — a value list, a heatmap axis — would be eighteen thousand rows of one trace each. Keys past the cap are <em>search only</em>: they are offered in the Search Traces builder, marked, and kept out of the picker's second step entirely — picking one there would open a page that could only report that it will not draw anything. The step says how many it left out rather than showing four of a type's eleven keys and letting that read as the type having four. Where a list is the top of a key rather than the whole of it, it says so.
      </DocsCallout>

      <h2 id="ai-export">AI Export</h2>

      <p>Both the trace waterfall and an operation's drill-down carry an export button that renders what you are looking at as a single Markdown document, built for handing to an AI assistant — paste it into Claude Code, or attach the downloaded file. The document is rendered <strong>server-side</strong>, and its value is less the numbers than the preamble: each bundle opens with a "how to read this" section stating the semantics an assistant would otherwise guess wrong.</p>

      <ul>
        <li><strong>Trace bundle</strong> — the span tree with self and inclusive times, the JVM context (pauses that crossed the trace, what each thread waited on), and the ranked why-slow summary. The preamble spells out that self time is an interval merge rather than a subtraction, and that the critical path assumes children block their parent.</li>
        <li><strong>Operation bundle</strong> — the operation's profile-wide aggregates, its per-span time breakdown in both inclusive and self terms, and its slowest runs. The preamble states the invariant worth checking: inclusive sums <em>past</em> the total (spans nest), self sums <em>to</em> it.</li>
      </ul>

      <p>Anything truncated says so inside the document — a capped list is annotated rather than silently complete. <strong>Copy for AI</strong> uses the browser clipboard, which exists only on HTTPS or localhost origins; on a plain-HTTP deployment the button becomes <em>Export for AI</em> and downloads the file instead, with the copy item explaining why it is off. Filenames lead with the operation name and are timestamped to the second, so two exports of the same thing do not overwrite each other.</p>

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
        <li><strong>Background jobs are their own traces.</strong> A pipeline run forked onto its own thread appears as a separate root rather than a child of whatever request triggered it. That is deliberate — its lifetime is unrelated to the request — but worth remembering when reading a list of operations.</li>
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
