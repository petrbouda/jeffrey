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
  { id: 'all-traces', text: 'All Traces', level: 2 },
  { id: 'operations', text: 'Traces by Operation', level: 2 },
  { id: 'waterfall', text: 'The Trace Detail: Waterfall', level: 2 },
  { id: 'overlays', text: 'Overlays and Rails', level: 2 },
  { id: 'span-detail', text: 'Span Drill-Down', level: 2 },
  { id: 'flamegraphs', text: 'Per-Span Flamegraphs', level: 2 },
  { id: 'virtual-threads', text: 'Spans on Virtual Threads', level: 2 },
  { id: 'attributes', text: 'Traces by Attributes', level: 2 },
  { id: 'ai-export', text: 'AI Export', level: 2 },
  { id: 'limits', text: 'Limits', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const virtualThreadExample = `POST /api/internal/recordings/…   tomcat-handler-53  virtual   4173ms    0 samples
  profile.initialize              tomcat-handler-53  virtual   4102ms    0
    recording.parse               tomcat-handler-53  virtual   2797ms    0
      chunk.parse                 bulk-parallel      platform  2730ms  153
      chunk.parse                 bulk-parallel      platform  2571ms  140
    profile.data-init             tomcat-handler-53  virtual    933ms    0
      guardian.results            parallel           platform   932ms   63`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Analyzing Traces"
      icon="bi bi-bar-chart"
    />

    <div class="docs-content">
      <p>Once a recording with trace identity is opened as a profile, the <strong>Traces</strong> section appears in the profile's sidebar: All Traces, Traces by Operation, attribute search, and — from any trace row — the full-screen Trace Detail with the waterfall. This page is the tour of the analysis side; the instrumentation that produces the data is the rest of this guide.</p>

      <h2 id="overview">Overview</h2>

      <p>A tracing tool normally tells you that a span took 400&nbsp;ms and stops there — the next question, "doing what?", needs a profiler and a second correlated data source. Jeffrey has both in one file. Every span carries its thread and its time window, and the profile database already scopes flamegraphs, timeseries and event summaries to <code>(thread, from, to)</code> windows — so a span selected in the waterfall becomes a flamegraph query with no extra instrumentation and no second agent.</p>

      <p>After parsing, Jeffrey derives typed trace tables once per profile: every event type declaring a <code>spanId</code> field feeds one span table, which is why an HTTP request shows its JDBC statements as native children without either side knowing about the other. Trace and span ids are 64-bit values rendered as 16-character hex in the UI (they exceed the JavaScript safe-integer range, so the API carries them as strings).</p>

      <h2 id="all-traces">All Traces</h2>

      <p>All Traces is the one page that does not begin by choosing something. &quot;Which runs were the slowest&quot; and &quot;what failed&quot; are questions about the recording rather than about one operation or one attribute, and answering them through the operations page meant first knowing which operation to open &mdash; which is the thing being asked.</p>

      <p>The chart above the list buckets every trace over the recording, pairing the slowest trace in each bucket with how many there were, so a burst reads as a burst where it happened. Below it, filter by operation name, restrict to traces that carry an error, set a duration floor, and order by duration, start time, span count or error count. The narrowing and the ordering happen in SQL over the whole profile, not over the page in the browser &mdash; a page ranked client-side would rank the page rather than the recording &mdash; and the list pages in fifty at a time. Every filter is mirrored into the URL, so a narrowed list is a link. Clicking a row opens the same full-screen Trace Detail the other pages open, and its header links onward to that trace's operation.</p>

      <h2 id="operations">Traces by Operation</h2>

      <p>Traces by Operation answers "which <em>kind</em> of run is slow, across every time it ran". One card per <strong>trace type</strong>: an operation's name is derived from what the root did — <code>GET /api/internal/profiles/{profileId}</code>, <code>jeffrey.api.v1.ProjectService/List</code> — rather than read out of the recording, so the same endpoint is one operation whichever version of the library recorded it. A trace type is keyed by name <em>plus</em> the root's kind and event type — an inbound <code>GET /orders</code> and an outbound call to the same path are named identically and are not the same operation.</p>

      <p>Each card leads with the call count, then Spans / Total / P50 / P95 / Max badges, with an error-count badge when the type has failures; sort by total, P95, max, call count or errors. The profile-wide tiles above the list read an uncapped SQL aggregate, so they never change value as you filter the capped list below. Clicking a card opens the operation drill-down — the selection lives in the URL, so it can be linked to directly — with four tabs:</p>

      <ul>
        <li><strong>Summary</strong> — call count, latency percentiles, total time and the share of all trace time it accounts for; a latency histogram, the platform/virtual thread split, where the operation's time goes span by span, and its slowest runs.</li>
        <li><strong>Flamegraphs</strong> — every execution, wall-clock and allocation sample taken while a trace of this type was running, covering exactly the windows those traces ran in and no more.</li>
        <li><strong>Traces Timeline</strong> — the slowest trace and the trace count, bucketed over the whole recording, aggregated in the database over <em>every</em> trace of the type (not the capped fetch), with empty slices drawn as zeroes.</li>
        <li><strong>Slowest Traces</strong> — this operation's traces ranked by duration; opening a row shows that trace's waterfall in place, with the trace id added to the URL.</li>
      </ul>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/operations.webp" alt="Traces by Operation with stat tiles and one card per operation" />
        <figcaption>Traces by Operation &mdash; the profile-wide tiles above, one card per trace type below, sortable by total, P95, max, count or errors.</figcaption>
      </figure>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/operation-summary.webp" alt="The operation drill-down's Summary tab" />
        <figcaption>The drill-down&rsquo;s <strong>Summary</strong> tab &mdash; calls, latency percentiles, the latency distribution, and where the operation&rsquo;s time goes span by span.</figcaption>
      </figure>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/top-spans-slowest.webp" alt="Top spans by time and the slowest traces of one operation" />
        <figcaption>Top spans by time &mdash; promoted JDK events rank right next to your own spans &mdash; and the operation&rsquo;s slowest runs, each one click from its waterfall.</figcaption>
      </figure>

      <DocsCallout type="info">
        <strong>Where did the nested spans go?</strong> The list groups by <em>root</em> name, not by every span name in the profile — names that only ever occur nested (<code>chunk.parse</code>) are not operations. A nested span is not lost: open a trace it belongs to and find it in the waterfall.
      </DocsCallout>

      <h2 id="waterfall">The Trace Detail: Waterfall</h2>

      <p>Opening a trace renders its span tree as a waterfall filling a full-screen view: indented operation names on the left, proportional duration bars on the right, positioned against the trace's own window. The trace id is kept in the URL (<code>?trace=</code>), so a trace can be linked to and returned to. Sub-pixel spans are clamped to a minimum width so a 200&nbsp;µs span inside a 2&nbsp;s trace stays clickable.</p>

      <ul>
        <li><strong>Self vs child time.</strong> Each bar is split into <em>self</em> and <em>child</em> segments — the part of the span's duration not covered by any same-thread child, and the part that is. A parent whose bar is almost entirely child time is a pass-through; one that is mostly self time is where the work actually happened. Self time is computed once, at derivation, so the waterfall, the operation breakdown and the exports all read the same number.</li>
        <li><strong>Colour by instrumentation.</strong> A bar's hue says what recorded the span, not which way the call points: inbound HTTP and outbound HTTP, inbound gRPC and outbound gRPC, and database statements each get their own colour, traced methods share the own-work green, and a promoted JDK wait keeps its context category's colour &mdash; the same one its lane and the per-category summary use, darkened where it wrote rather than read, so socket and file reads and writes stay one category apiece while the direction still separates at a glance. Everything else &mdash; a hand-written <code>Tracer</code> span, a <code>@Traced</code> method, an event from instrumentation Jeffrey holds no convention for &mdash; is drawn grey, because a hue there would claim a meaning that cannot be read off the event. The same palette colours the operation drill-down's <em>Top spans by time</em>, so a row reads the same way on both screens.</li>
        <li><strong>Critical path.</strong> A toolbar toggle dims everything off the critical path — the chain of spans that actually determined the trace's duration, computed by walking backwards from the end crediting the last-finishing child.</li>
        <li><strong>Folding.</strong> Subtrees collapse; a folded row reports what it swallowed (span count, hollow notification/exception counts, a hidden-error dot). Repeated sibling spans collapse into a "run" row with count, total, median, P95, max and a mini-histogram.</li>
        <li><strong>First error.</strong> A jump control lands on the first failed span in the trace.</li>
      </ul>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/jdk-overlays.webp" alt="The full Trace Detail: waterfall with self/child bars, context lanes and the why-slow summary" />
        <figcaption>The full Trace Detail &mdash; solid-self/washed-children bars, the safepoint lane, promoted File read runs, virtual-thread pinning, and the per-category summary underneath.</figcaption>
      </figure>

      <h2 id="overlays">Overlays and Rails</h2>

      <p>The toolbar carries one switch per overlay family, each disabled with a "0 events" note when the recording holds none:</p>

      <table>
        <thead>
          <tr>
            <th>Toggle</th>
            <th>What it draws</th>
            <th>Details</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><strong>JVM context</strong></td>
            <td>GC-pause and safepoint lanes + background stripes</td>
            <td><router-link to="/docs/tracing/gc-safepoints">GC Pauses &amp; Safepoints</router-link></td>
          </tr>
          <tr>
            <td><strong>Blocking ops</strong></td>
            <td>Promoted lock/park/sleep/stall leaf spans</td>
            <td rowspan="2"><router-link to="/docs/tracing/jdk-events">JDK Events in Traces</router-link></td>
          </tr>
          <tr>
            <td><strong>I/O ops</strong></td>
            <td>Promoted socket/file leaf spans</td>
          </tr>
          <tr>
            <td><strong>Notifications</strong></td>
            <td>The diamond rail + per-span pins</td>
            <td rowspan="2"><router-link to="/docs/tracing/notifications-exceptions">Notifications &amp; Exceptions</router-link></td>
          </tr>
          <tr>
            <td><strong>Exceptions</strong></td>
            <td>The cross rail + per-span pins</td>
          </tr>
        </tbody>
      </table>

      <p>Under the waterfall, the <em>"Why was this trace slow?"</em> panel ranks where the trace's wall-clock time went — GC, safepoints, I/O, locks, and the <em>own work</em> remainder — with each category linking to the profile view that explains it. Its numbers and the bars above are one source of truth by construction.</p>

      <h2 id="span-detail">Span Drill-Down</h2>

      <p>Selecting a span expands its detail inline, underneath the row — the waterfall stays visible, so the span keeps the context of the tree it sits in. The detail shows:</p>

      <ul>
        <li><strong>Timing</strong> — a self-vs-children meter, plus the per-span wait chips (what this span's thread waited on, from the promoted categories).</li>
        <li><strong>Identity</strong> — span id, parent, thread, started-at (UTC), and the source event type.</li>
        <li><strong>Attributes</strong> — the JSON attributes the instrumentation attached, as a key/value table.</li>
        <li><strong>Event fields</strong> — the source event's own declared fields: an HTTP exchange's status and URI, a statement's SQL (with a copy button), a custom event's typed fields, a promoted JDK event's payload (host/port, path, monitor class).</li>
        <li><strong>Notifications / Exceptions</strong> — the instants pinned to this span; a throw opens its folded stack trace.</li>
        <li><strong>Events in span</strong> — a timeline of the JVM events that occurred on that thread inside the span's window (CPU samples, allocations, GC…): one lane per event type, a draggable mini-map, and a per-type breakdown that filters the lane and offers a per-type flamegraph. Other spans and the already-promoted blocking events are excluded — this is JVM activity, not a restatement of the tree.</li>
      </ul>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/socketwrite-detail.webp" alt="A span expanded inline with timing meter, identity and event fields" />
        <figcaption>A span expanded inline &mdash; the timing meter, identity, and the source event&rsquo;s own fields (here a promoted <code>jdk.SocketWrite</code> with host, port and bytes written).</figcaption>
      </figure>

      <h2 id="flamegraphs">Per-Span Flamegraphs</h2>

      <p>The span detail's <strong>Flamegraph</strong> action renders what ran inside the span, in either <strong>Inclusive</strong> or <strong>Self</strong> mode. Inclusive is the span's whole window; Self subtracts the windows of same-thread children and merges what is left — the answer to "the parent is slow but its children are not, so what is it doing?". A child forked onto another thread is not subtracted: the parent's thread was busy with its own work the whole time it ran.</p>

      <DocsCallout type="info">
        <strong>How a sample is attributed to a span.</strong> Nothing stamps a span id onto a sample. A span is a <code>(thread, window)</code> pair, and the flamegraph is every sample whose thread matches and whose timestamp falls inside — which works because <code>--jfrsync</code> writes the profiler's samples and the JVM's events into one recording on one clock. Consequences: window edges are millisecond-floored, and a span shorter than the sampling interval may enclose no sample at all.
      </DocsCallout>

      <h2 id="virtual-threads">Spans on Virtual Threads</h2>

      <p>One consequence decides whether the Flamegraph tab has anything to show. A real trace — a recording upload, then the parse it triggers — with the thread each span ran on and the samples that matched it:</p>

      <DocsCodeBlock :code="virtualThreadExample" language="text" />

      <p>The request's own spans record 4.1&nbsp;s of real work and match <strong>zero</strong> samples; its forked children match immediately, from the same recording. Nothing is missing — async-profiler's CPU engine (<code>event=ctimer</code>, Jeffrey's default) names the <strong>carrier</strong> thread a virtual thread is mounted on, never the virtual thread itself, so a span on one can never match. Jeffrey keeps the tab and explains the emptiness in place rather than hiding it. Two ways to get flamegraphs for request-thread work: fork the work you care about onto a pool thread (<code>chunk.parse</code> above), or drop <code>event=</code> from the profiler settings and let the JVM sample CPU itself. Allocation and wall-clock samples are attributed to the carrier either way.</p>

      <h2 id="attributes">Traces by Attributes</h2>

      <p>Every span carries two key/value payloads — the open <code>attributes</code> map the developer attached, and the fields its event type declares about itself (<code>rows</code>, <code>statusCode</code>, a custom event's own fields) — plus the span shape (<code>name</code>, <code>kind</code>, <code>status</code>, <code>errorType</code>, <code>eventType</code>). All three are flattened once into an index, and three pages read it:</p>

      <ul>
        <li><strong>Search Traces</strong> — conditions built from the catalog and ANDed together (<code>=</code>, <code>≠</code>, <code>contains</code>, numeric comparisons, "is present"), with a choice between <em>anywhere in the trace</em> and <em>all on one span</em> — different questions with different answers, since attributes are never inherited down the tree. Results show which span matched and what it held, with match-vs-profile percentiles and a density strip above.</li>
        <li><strong>Attribute Values</strong> — one key of one event type broken down into every value it took, ranked by <em>total time</em> rather than call count (a busy value and an expensive value are rarely the same value), with per-value P50/P95/max and error rates.</li>
        <li><strong>Latency by Attributes</strong> — each value's traces spread over log-spaced duration buckets, colour-normalised per row. Percentiles hide bimodality; the heatmap shows it.</li>
      </ul>

      <p>Keys are picked in two steps — first the event type, then a key those spans carried — which is what lets the page say how many values <code>tenant</code> took <em>on HTTP spans</em>. Keys whose cardinality exceeds the cap are <em>search only</em>: usable as conditions, kept out of the breakdowns. Notifications are searchable the same way, indexed apart from spans on purpose.</p>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/search-timeline.webp" alt="Search Traces with the anywhere-in-trace toggle and the when-the-matches-happened timeline" />
        <figcaption>Search Traces &mdash; conditions built from the catalog, the <em>anywhere in the trace</em> / <em>all on one span</em> choice, match-vs-profile percentiles, and when the matches happened over the recording.</figcaption>
      </figure>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/search-results.webp" alt="Matched traces ranked by duration" />
        <figcaption>The matches ranked &mdash; from a 2-minute <code>heap-dump-init</code> down to sub-millisecond requests, each row one click from its waterfall.</figcaption>
      </figure>

      <figure class="docs-figure">
        <img src="/images/docs/tracing/attribute-values.webp" alt="Attribute Values with the event-type picker open" />
        <figcaption>Attribute Values &mdash; keys are picked in two steps, event type first; JDK events carry attributes too, so <code>jdk.FileWrite</code> ranks beside your own spans.</figcaption>
      </figure>

      <h2 id="ai-export">AI Export</h2>

      <p>Both the trace waterfall and an operation's drill-down carry an export button that renders what you are looking at as a single Markdown document built for handing to an AI assistant — paste it into Claude Code, or attach the downloaded file. The document is rendered server-side, and each bundle opens with a "how to read this" preamble stating the semantics an assistant would otherwise guess wrong:</p>

      <ul>
        <li><strong>Trace bundle</strong> — the span tree with self and inclusive times, the JVM context (pauses that crossed the trace, what each thread waited on), the ranked why-slow summary, the trace's I/O grouped by target, and every throw recorded inside it. The preamble spells out that self time is an interval merge, and that the critical path assumes children block their parent.</li>
        <li><strong>I/O operations</strong> — the promoted <code>jdk.SocketRead</code>, <code>jdk.SocketWrite</code>, <code>jdk.FileRead</code>, <code>jdk.FileWrite</code> and <code>jdk.FileForce</code> spans grouped by file path or <code>host:port</code>, each with its operation count, bytes, and <em>mean bytes per operation</em> — the buffering figure. Many operations with a small mean is the fingerprint of an absent or undersized buffer, and such rows are marked <code>!small-ops</code>. Grouped over every operation, including ones the span tree had to truncate. The preamble carries the caveat that matters: JFR only records operations slower than its I/O threshold, so the counts are lower bounds, not measurements.</li>
        <li><strong>Exceptions</strong> — every captured throw grouped by class and message, with the spans it was thrown in. A throw is not a failure: only one marked <code>!escaped</code> is why its span failed, and a large all-caught group is the exceptions-as-control-flow cost rather than breakage.</li>
        <li><strong>Operation bundle</strong> — the operation's profile-wide aggregates, its per-span time breakdown in inclusive and self terms, and its slowest runs — with the invariant worth checking: inclusive sums <em>past</em> the total (spans nest), self sums <em>to</em> it.</li>
      </ul>

      <p>Anything truncated says so inside the document. <em>Copy for AI</em> needs the browser clipboard (HTTPS or localhost); on plain HTTP the button downloads the file instead.</p>

      <h2 id="limits">Limits</h2>

      <ul>
        <li><strong>A span's own event names the thread that ended it.</strong> JFR attributes a duration event to the thread that <em>commits</em> it. Re-entered spans emit a <code>jeffrey.TraceScope</code> per activation, and the drill-down reads those — which is what keeps the thread-plus-window correlation honest.</li>
        <li><strong>Background jobs are their own traces.</strong> A pipeline run forked onto its own thread appears as a separate root, deliberately — its lifetime is unrelated to the request that triggered it.</li>
        <li><strong>Trace ids are 64-bit</strong>, not the 128-bit W3C shape — ample for a single JVM that mints its own ids, but an application running OpenTelemetry cannot hand Jeffrey its <code>traceparent</code> and expect matching ids.</li>
        <li><strong>The waterfall is sized for tens to hundreds of spans</strong> per trace; traces of several thousand spans would want a different substrate.</li>
        <li><strong>Two kinds of "span" coexist.</strong> Async-Profiler Spans are flat, per-thread, tag-based, and need the patched async-profiler agent; Jeffrey Tracing spans are nested, trace-scoped, pure JFR. They are complementary and kept in separate sections.</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
