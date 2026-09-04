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
  { id: 'rules-that-apply-to-all-of-them', text: 'Rules That Apply to All of Them', level: 2 },
  { id: 'profiles', text: 'profiles_ — the catalogue', level: 2 },
  { id: 'flamegraph', text: 'flamegraph_ — call trees', level: 2 },
  { id: 'compare', text: 'compare_ — two profiles', level: 2 },
  { id: 'traces', text: 'traces_ — latency', level: 2 },
  { id: 'jvm', text: 'jvm_ — the machine underneath', level: 2 },
  { id: 'jfr', text: 'jfr_ — the profile database', level: 2 },
  { id: 'heap', text: 'heap_ — heap dumps', level: 2 },
  { id: 'recordings', text: 'recordings_ — creating profiles', level: 2 },
  { id: 'what-is-not-here', text: 'What Is Not Here', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const nameShape = `flamegraph_export
    ^         ^
  family    method name, camelCase`;

const analyzeExample = `Analyze target/checkout-run.jfr and tell me where the time goes.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tool Reference"
      icon="bi bi-list-columns"
    />

    <div class="docs-content">
      <p>Fifty-five tools in eight families. Seven families read a profile; the eighth, <code>recordings_</code>, is the only one that creates one.</p>

      <h2 id="rules-that-apply-to-all-of-them">Rules That Apply to All of Them</h2>

      <p><strong>Names are <code>family_methodName</code>, camelCase preserved</strong> &mdash; <code>jfr_listTables</code>, not <code>jfr_list_tables</code>.</p>
      <DocsCodeBlock :code="nameShape" language="bash" />

      <p><strong>Every tool except <code>profiles_list</code> and the <code>recordings_</code> family takes a <code>profileId</code></strong>, and it is required. That is the id from <code>profiles_list</code>; nothing else works without one.</p>

      <p><strong>Output is capped.</strong> A result is truncated at roughly 120,000 characters, with an explicit trailer saying so &mdash; a silently shortened flamegraph would be read as a complete one. The SQL tools cap rows as well, and say when they do. Aggregate in the query rather than pulling rows back to count them.</p>

      <p><strong>The Markdown exports carry their own reading instructions.</strong> <code>flamegraph_export</code>, <code>traces_traceExport</code> and <code>traces_operationExport</code> return documents that open by explaining what <code>self</code> means against <code>total</code>, what the frame tags mean, and what was pruned. Read the preamble the document gives you rather than assuming conventions from elsewhere &mdash; Jeffrey&rsquo;s <code>self</code> is a merged-interval computation, not a subtraction.</p>

      <h2 id="profiles">profiles_ &mdash; the catalogue</h2>
      <p>Start here. <code>profiles_list</code> is the only tool that does not need a <code>profileId</code>, because it is where ids come from.</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>profiles_list</code></td>
            <td><code>search?</code>, <code>limit?</code> (100)</td>
            <td>Every profile in the installation, with its id</td>
          </tr>
          <tr>
            <td><code>profiles_get</code></td>
            <td><code>profileId</code></td>
            <td>Identity, the recording window it covers, its size, and the source commit the profiled build came from (<code>recordingCommit</code>, <code>null</code> when the recording carries no commit tag)</td>
          </tr>
          <tr>
            <td><code>profiles_features</code></td>
            <td><code>profileId</code></td>
            <td>Which analysis features this profile has the data for, plus every event type recorded with sample counts</td>
          </tr>
          <tr>
            <td><code>profiles_link</code></td>
            <td><code>profileId</code></td>
            <td>A deep link that opens the profile in the Jeffrey UI</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip" title="profiles_features is the cheap way to avoid dead ends">
        A JFR recording usually has no heap dump; a heap dump has no flamegraphs; traces exist only if the application ran Jeffrey&rsquo;s tracing instrumentation. One call rules out a whole family before it is tried.
      </DocsCallout>

      <h2 id="flamegraph">flamegraph_ &mdash; call trees</h2>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>flamegraph_list</code></td>
            <td><code>profileId</code></td>
            <td><code>available</code> &mdash; the event types this profile can be graphed by, each with its sample and weight totals and the argument defaults that type is normally graphed with &mdash; plus <code>notRecorded</code>, the standard groups the profiler did not capture. Call it first &mdash; asking for a type the profile did not record returns an empty tree, not an error</td>
          </tr>
          <tr>
            <td><code>flamegraph_export</code></td>
            <td><code>profileId</code>, <code>eventType</code>, <code>thresholdPct?</code>, <code>startMs?</code>, <code>endMs?</code>, <code>threadMode?</code>, <code>useWeight?</code>, <code>search?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The call tree as Markdown, with the reading preamble for that event type</td>
          </tr>
        </tbody>
      </table>

      <p>Common starting points: <code>jdk.ExecutionSample</code> for on-CPU time, <code>jdk.ObjectAllocationSample</code> for allocation (with <code>useWeight</code> to rank by bytes rather than call count), <code>jdk.JavaMonitorEnter</code> for lock contention (weight is nanoseconds blocked), <code>profiler.WallClockSample</code> for latency including off-CPU &mdash; async-profiler&rsquo;s event, so unlike its neighbours it carries no <code>jdk.</code> prefix. <code>thresholdPct</code> controls how much survives pruning &mdash; raise it for an overview, lower it to chase one path.</p>

      <h2 id="compare">compare_ &mdash; two profiles</h2>
      <p>The only family scoped to a <strong>pair</strong>. <code>profileId</code> is the run under examination and <code>baselineProfileId</code> is what it is measured against, so a positive delta always means the primary spends more &mdash; a regression. Get the direction wrong and every regression reads as an improvement.</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>compare_list</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code></td>
            <td>Both recordings&rsquo; length, the event types they have in common with each side&rsquo;s totals, the types only one of them recorded, and the notes that decide whether the pair is comparable at all</td>
          </tr>
          <tr>
            <td><code>compare_movements</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code>, <code>eventType</code>, <code>limit?</code>, <code>startMs?</code>, <code>endMs?</code>, <code>useWeight?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The methods that grew and the ones that shrank, ranked by how much work moved with them, as Markdown</td>
          </tr>
          <tr>
            <td><code>compare_flamegraph</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code>, <code>eventType</code>, <code>thresholdPct?</code>, <code>startMs?</code>, <code>endMs?</code>, <code>useWeight?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The differential call tree as Markdown, every frame carrying both sides and the movement between them</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning" title="compare_list is not a warm-up call">
        Any two recordings can be subtracted, and the result always looks like a finding. Whether it <em>is</em> one depends on facts the deltas do not show &mdash; comparable recording length, comparable volume, the same profiler settings &mdash; and nothing inside a JFR file proves them. <code>compare_list</code> is the step that decides whether the rest means anything, and &ldquo;these two runs are not comparable&rdquo; is a real result.
      </DocsCallout>

      <p><strong>Movements are attributed by self weight.</strong> A delta taken on subtree totals charges a change to every caller above it, so one slow leaf reports <code>main</code>, the thread-pool runnable and every framework frame in between as having regressed by the same amount. <code>compare_movements</code> ranks by the work that stopped <em>at</em> each method, which moves only where the work moved; <code>compare_flamegraph</code> is the drill-down once a method has been named.</p>

      <p><strong>The baseline is scaled onto the primary&rsquo;s recording length</strong> before any delta is taken, because a sampling profiler emits samples at a roughly fixed rate and a run that lasted twice as long carries twice as many of them. Both documents print the raw figure, the scaled figure and the factor, so the correction is visible rather than merely applied. It assumes a steady workload measured over time and is wrong for a fixed-size benchmark &mdash; there the share column is the honest one.</p>

      <p><strong>A rename is not a regression.</strong> The diff matches method names level by level, so a renamed, moved or extracted method breaks the match and its work appears once as new and once as gone, often of near-identical size. Both documents list such pairs as candidate renames &mdash; suspicions for a reader holding the source diff to confirm, never a resolution, because weight alone cannot tell a rename from a coincidence.</p>

      <p>Pruning in <code>compare_flamegraph</code> is by <strong>movement</strong>, not by size: a subtree in which nothing changed is dropped however large it is, and unmoved ancestors are kept so the frames that did move can still be placed. Absence there means &ldquo;did not move&rdquo;, the opposite of what it means in <code>flamegraph_export</code>.</p>

      <h2 id="traces">traces_ &mdash; latency</h2>
      <p>Available only for a profile recorded with <router-link to="/docs/tracing">Jeffrey Tracing</router-link>. An operation is identified by the <strong>triple</strong> <code>(name, kind, eventType)</code>, not by name alone: an inbound <code>GET /orders</code> and an outbound call to the same path are different operations.</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>traces_overview</code></td>
            <td><code>profileId</code></td>
            <td>Profile-wide totals: how many traces and spans, how many failed, and how many notifications the application raised inside them (with the <code>CRITICAL</code> and <code>HIGH</code> ones counted apart)</td>
          </tr>
          <tr>
            <td><code>traces_operations</code></td>
            <td><code>profileId</code>, <code>search?</code>, <code>errorsOnly?</code>, <code>sort?</code> (<code>TOTAL_TIME</code>), <code>limit?</code> (50)</td>
            <td>One row per operation with call count, latency percentiles, errors and notification counts; <code>sort</code> also accepts <code>NOTIFICATIONS</code></td>
          </tr>
          <tr>
            <td><code>traces_operationExport</code></td>
            <td><code>profileId</code>, <code>name</code>, <code>kind</code>, <code>eventType</code></td>
            <td>One operation as Markdown: percentiles, where the time goes, and the reading preamble</td>
          </tr>
          <tr>
            <td><code>traces_slowestTraces</code></td>
            <td><code>profileId</code>, <code>name</code>, <code>kind</code>, <code>eventType</code>, <code>limit?</code> (20)</td>
            <td>Individual traces, slowest first, with their ids</td>
          </tr>
          <tr>
            <td><code>traces_traceExport</code></td>
            <td><code>profileId</code>, <code>traceId</code></td>
            <td>One trace as Markdown: the span tree with self time</td>
          </tr>
          <tr>
            <td><code>traces_spanFlamegraphExport</code></td>
            <td><code>profileId</code>, <code>traceId</code>, <code>spanId</code>, <code>eventType</code>, <code>selfOnly?</code>, <code>threadMode?</code>, <code>useWeight?</code></td>
            <td>A flamegraph of the samples taken while one span was open</td>
          </tr>
          <tr>
            <td><code>traces_operationFlamegraphExport</code></td>
            <td><code>profileId</code>, <code>name</code>, <code>kind</code>, <code>eventType</code>, <code>graphEventType</code>, <code>threadMode?</code>, <code>useWeight?</code></td>
            <td>The same, aggregated over every trace of one operation</td>
          </tr>
          <tr>
            <td><code>traces_notifications</code></td>
            <td><code>profileId</code>, <code>severity?</code>, <code>type?</code>, <code>category?</code>, <code>source?</code>, <code>search?</code>, <code>name?</code>, <code>kind?</code>, <code>eventType?</code>, <code>limit?</code> (50)</td>
            <td>What the application reported about itself while traces ran &mdash; every <code>jeffrey.Notification</code> raised inside a trace, grouped by kind, the most severe first, each with its count, how many traces raised it, and exemplar trace ids for <code>traces_traceExport</code>. The operation triple, given whole or not at all, narrows to one operation</td>
          </tr>
        </tbody>
      </table>

      <p>A notification is the application&rsquo;s own account of what went wrong &mdash; a pool exhausted, a fallback taken &mdash; emitted by its own code, so it is a diagnosis where every other tool reports a measurement. The trace and operation exports carry a Notifications section of their own; <code>traces_notifications</code> is the profile-wide reading, and the place to start when <code>traces_overview</code> reports any <code>CRITICAL</code> or <code>HIGH</code> ones.</p>

      <DocsCallout type="info" title="Two event types, two different meanings">
        On the flamegraph exports, <code>eventType</code> is the event that <em>opened the trace</em> (e.g. <code>jeffrey.HttpServerExchange</code>) while <code>graphEventType</code> is what to <em>graph</em> (e.g. <code>jdk.ExecutionSample</code>). They are never the same value.
      </DocsCallout>

      <h2 id="jvm">jvm_ &mdash; the machine underneath</h2>
      <p>Garbage collection, safepoints, JIT compilation, threads, native memory, the container and the JVM&rsquo;s own configuration. Each tool renders the manager behind the matching Jeffrey UI page, so the numbers come from the same tested builders the UI draws its charts from.</p>

      <p>These questions are all answerable with <code>jfr_executeQuery</code>, and that is exactly why the family exists. Answering &ldquo;how much of the run went to GC pauses&rdquo; by hand is six round trips of invented SQL, and several of those queries are ones a reader reliably gets wrong: pause time is <code>sumOfPauses</code> rather than an event&rsquo;s duration, <code>jdk.GCHeapSummary</code> is two rows per collection, <code>jdk.SafepointLatency</code> fires once per thread per safepoint. One call, the same answer the UI would give.</p>

      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jvm_sections</code></td>
            <td><code>profileId</code></td>
            <td>Which sections this profile can answer, each with the event types it is built from</td>
          </tr>
          <tr>
            <td><code>jvm_autoAnalysis</code></td>
            <td><code>profileId</code></td>
            <td>Jeffrey&rsquo;s rule set over the recording &mdash; findings with a severity, an explanation and a suggested fix</td>
          </tr>
          <tr>
            <td><code>jvm_gc</code></td>
            <td><code>profileId</code></td>
            <td>The stop-the-world budget, collections by generation and cause, bytes freed, the longest collections</td>
          </tr>
          <tr>
            <td><code>jvm_safepoints</code></td>
            <td><code>profileId</code></td>
            <td>VM operations, time to safepoint, and the threads that kept the others waiting with the state they were in</td>
          </tr>
          <tr>
            <td><code>jvm_jit</code></td>
            <td><code>profileId</code></td>
            <td>Compiler totals, the slowest compilations, code cache occupancy, deoptimisation by method and reason</td>
          </tr>
          <tr>
            <td><code>jvm_threads</code></td>
            <td><code>profileId</code></td>
            <td>Population and peak, sleeps, parks and monitor blocks, top CPU and allocating threads, virtual-thread pinning</td>
          </tr>
          <tr>
            <td><code>jvm_nativeMemory</code></td>
            <td><code>profileId</code></td>
            <td>Resident set size and its growth, direct buffers, native libraries, NMT categories when NMT was enabled</td>
          </tr>
          <tr>
            <td><code>jvm_container</code></td>
            <td><code>profileId</code></td>
            <td>cgroup limits, and whether the scheduler throttled the process, with the verdict and its counters</td>
          </tr>
          <tr>
            <td><code>jvm_configuration</code></td>
            <td><code>profileId</code>, <code>section?</code></td>
            <td>What the JVM was started with, in the UI&rsquo;s own tabs; without a section, the section names</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="Call jvm_sections first">
        A recording holds only what the profiler was told to capture. Every section reports whether this profile carries its events, and a section asked for anyway is refused naming the events it needed &mdash; a dashboard rendered from events that were never recorded is a page of zeroes, which reads like a finding rather than like an absence.
      </DocsCallout>

      <DocsCallout type="warning" title="Auto analysis is read from a cache, not computed here">
        Generating it loads the whole recording through the JMC toolkit, which is bounded neither in time nor in memory by anything the server controls &mdash; a poor trade inside a tool whose point is being cheap. The Auto Analysis page in the Jeffrey UI computes and caches it; every call afterwards is a cache read. Until then the tool says so, and the other sections still answer.
      </DocsCallout>

      <h2 id="jfr">jfr_ &mdash; the profile database</h2>
      <p>Each profile is one DuckDB database. This family is the escape hatch for questions no purpose-built tool covers &mdash; distributions over time, correlations between event types, the cardinality of a field.</p>

      <p>For garbage collection, safepoints and JIT compilation, reach for <code>jvm_</code> first: those dashboards are computed by the same builders the UI uses, and reproducing one here is slower and easier to get wrong. This family is for the questions they do not shape &mdash; a distribution over time, a correlation between two event types, one field a dashboard does not carry. The <router-link to="/docs/microscope-mcp/skills#jfr-sql"><code>jfr-sql</code></router-link> skill has the schema and the queries.</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jfr_listTables</code></td>
            <td><code>profileId</code></td>
            <td>The queryable tables</td>
          </tr>
          <tr>
            <td><code>jfr_describeTable</code></td>
            <td><code>profileId</code>, <code>tableName</code></td>
            <td>Column names, types and nullability</td>
          </tr>
          <tr>
            <td><code>jfr_listEventTypes</code></td>
            <td><code>profileId</code></td>
            <td>Every event type present, with counts and descriptions</td>
          </tr>
          <tr>
            <td><code>jfr_queryEvents</code></td>
            <td><code>profileId</code>, <code>eventType</code>, <code>limit?</code> (100), <code>whereClause?</code></td>
            <td>Events of one type &mdash; the common case, without writing SQL</td>
          </tr>
          <tr>
            <td><code>jfr_executeQuery</code></td>
            <td><code>profileId</code>, <code>query</code></td>
            <td>An arbitrary read-only query (<code>SELECT</code> / <code>WITH</code> only), one statement per call, capped at 1,000 rows and 30 seconds</td>
          </tr>
          <tr>
            <td><code>jfr_getProfileInfo</code></td>
            <td><code>profileId</code></td>
            <td>Profile, project and workspace ids</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The engine is sandboxed, not just the syntax">
        The engine behind these two is sandboxed rather than merely checked: the profile database is opened with DuckDB&rsquo;s external file access and extension autoloading disabled, so a query cannot reach the host&rsquo;s filesystem through <code>read_text</code>, <code>read_csv</code> or <code>glob</code>, and cannot <code>ATTACH</code> another database. A second statement after a semicolon is refused rather than run. What a query can reach is this profile&rsquo;s own tables, which is what the family is for.
      </DocsCallout>

      <DocsCallout type="warning" title="Query the events view, not events_raw">
        <code>jfr_listTables</code> shows both. <code>events</code> is a view over <code>events_raw</code> that splices back the one large string field the parser pools out of each row; querying <code>events_raw</code> silently returns truncated JSON in <code>fields</code>, with no error to warn you. The bundled <code>jfr-sql</code> skill carries this and the rest of the schema.
      </DocsCallout>

      <h2 id="heap">heap_ &mdash; heap dumps</h2>
      <p>Twenty tools against a parsed heap dump&rsquo;s own DuckDB index, separate from the profile&rsquo;s JFR database. Asking for them on a profile with no heap dump fails immediately with a message saying so, rather than deep inside the engine.</p>

      <p><strong>Reports</strong> &mdash; pre-computed, and faster and safer than reproducing them in SQL:</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>heap_getHeapSummary</code></td>
            <td><code>profileId</code></td>
            <td>Total live bytes and instances, and the shape of the heap</td>
          </tr>
          <tr>
            <td><code>heap_getClassHistogram</code></td>
            <td><code>profileId</code>, <code>topN?</code> (50, max 200), <code>sortBy?</code> (<code>SIZE</code>)</td>
            <td>Top classes by memory or instance count</td>
          </tr>
          <tr>
            <td><code>heap_getBiggestObjects</code></td>
            <td><code>profileId</code>, <code>topN?</code> (20, max 50)</td>
            <td>The largest individual objects by retained size</td>
          </tr>
          <tr>
            <td><code>heap_getLeakSuspects</code></td>
            <td><code>profileId</code></td>
            <td>Leak-suspect analysis</td>
          </tr>
          <tr>
            <td><code>heap_getClassLoaderLeakChains</code></td>
            <td><code>profileId</code></td>
            <td>Suspicious class loaders and what keeps them alive</td>
          </tr>
          <tr>
            <td><code>heap_getTopConsumers</code></td>
            <td><code>profileId</code></td>
            <td>Memory grouped by (package, class loader)</td>
          </tr>
          <tr>
            <td><code>heap_getStringAnalysis</code></td>
            <td><code>profileId</code></td>
            <td>Duplicate and oversized strings</td>
          </tr>
          <tr>
            <td><code>heap_getCollectionAnalysis</code></td>
            <td><code>profileId</code></td>
            <td>Empty, singleton and oversized collections</td>
          </tr>
          <tr>
            <td><code>heap_getThreads</code></td>
            <td><code>profileId</code></td>
            <td>Threads in the dump, with object counts</td>
          </tr>
          <tr>
            <td><code>heap_getGCRootSummary</code></td>
            <td><code>profileId</code></td>
            <td>GC-root kinds and counts</td>
          </tr>
          <tr>
            <td><code>heap_getDumpMetadata</code></td>
            <td><code>profileId</code></td>
            <td>HPROF version, id size, compressed oops, parser warnings</td>
          </tr>
        </tbody>
      </table>

      <p><strong>Navigation</strong> &mdash; following one object through the graph:</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>heap_browseClassInstances</code></td>
            <td><code>profileId</code>, <code>className</code>, <code>limit?</code> (20), <code>offset?</code></td>
            <td>A page of instances of one class</td>
          </tr>
          <tr>
            <td><code>heap_getInstanceDetail</code></td>
            <td><code>profileId</code>, <code>objectId</code></td>
            <td>One object&rsquo;s fields and their values</td>
          </tr>
          <tr>
            <td><code>heap_getDominatorTreeRoots</code></td>
            <td><code>profileId</code>, <code>limit?</code> (20, max 50)</td>
            <td>The objects with the largest retained size</td>
          </tr>
          <tr>
            <td><code>heap_getDominatorTreeChildren</code></td>
            <td><code>profileId</code>, <code>objectId</code>, <code>limit?</code> (20)</td>
            <td>What one object retains</td>
          </tr>
          <tr>
            <td><code>heap_getPathToGCRoot</code></td>
            <td><code>profileId</code>, <code>objectId</code>, <code>maxPaths?</code> (3, max 5)</td>
            <td>The shortest chains from a GC root &mdash; why this object is still alive</td>
          </tr>
          <tr>
            <td><code>heap_getReferrers</code></td>
            <td><code>profileId</code>, <code>objectId</code>, <code>limit?</code> (20)</td>
            <td>Incoming references</td>
          </tr>
        </tbody>
      </table>

      <p><strong>SQL</strong> &mdash; for what the reports do not cover:</p>
      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>heap_listTables</code></td>
            <td><code>profileId</code></td>
            <td>The index schema&rsquo;s tables</td>
          </tr>
          <tr>
            <td><code>heap_describeTable</code></td>
            <td><code>profileId</code>, <code>tableName</code></td>
            <td>Column names, types and nullability</td>
          </tr>
          <tr>
            <td><code>heap_executeQuery</code></td>
            <td><code>profileId</code>, <code>query</code></td>
            <td>A read-only query against the index</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The dominator tree is built lazily">
        <code>dominator</code> and <code>retained_size</code> are empty until something asks for them, so a SQL query joining <code>retained_size</code> on a fresh dump returns nulls. Run <code>heap_getDominatorTreeRoots</code> once first. The bundled <code>heap-sql</code> skill covers the whole schema.
      </DocsCallout>

      <h2 id="recordings">recordings_ &mdash; creating profiles</h2>
      <p>Everything above answers questions about a profile that already exists. This family is how one comes to exist without leaving the terminal: you point Claude at a recording file in your repository and it imports the file and builds the profile, then carries on with the id it got back.</p>
      <DocsCodeBlock :code="analyzeExample" language="text" />

      <table>
        <thead>
          <tr>
            <th>Tool</th>
            <th>Arguments</th>
            <th>Returns</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>recordings_analyzeFile</code></td>
            <td><code>path</code>, <code>name?</code></td>
            <td>Imports the file at <code>path</code> and builds a profile from it &mdash; the <code>profileId</code> every other family takes, plus a UI link</td>
          </tr>
          <tr>
            <td><code>recordings_analyzeRecording</code></td>
            <td><code>recordingId</code></td>
            <td>The same, for a recording already in the Quick Analysis store &mdash; one uploaded through the UI but never analysed</td>
          </tr>
          <tr>
            <td><code>recordings_list</code></td>
            <td>&mdash;</td>
            <td>Every recording in the Quick Analysis store, analysed or not. A row with an empty <code>profile_id</code> is waiting for <code>recordings_analyzeRecording</code></td>
          </tr>
        </tbody>
      </table>

      <p>The file types are the ones Jeffrey analyses anywhere else: <code>.jfr</code>, <code>.jfr.lz4</code>, <code>.hprof</code>, <code>.hprof.gz</code>, <code>.pprof</code> and <code>.otlp</code>. A heap dump lands as a profile the <code>heap_</code> family answers about; the rest land as one the <code>jfr_</code>, <code>flamegraph_</code> and <code>traces_</code> families answer about. <code>profiles_features</code> tells you which you got.</p>

      <DocsCallout type="warning" title="The path is opened by Jeffrey, not by the client">
        <code>path</code> must be <strong>absolute</strong> and must exist <strong>on the machine Jeffrey runs on</strong>. A relative path is rejected rather than guessed at &mdash; it would resolve against Jeffrey&rsquo;s working directory, not yours. For a Jeffrey in a container or on another host, mount or copy the file where Jeffrey can see it first.
      </DocsCallout>

      <p>Two more things worth knowing. The call <strong>returns when the profile is built</strong>, which for a large recording is a wait rather than an acknowledgement. And each <code>recordings_analyzeFile</code> imports the file again and builds another profile &mdash; call <code>recordings_list</code> or <code>profiles_list</code> first if the same file may already be there.</p>

      <p>The family is advertised only while ingestion is enabled; see <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> for the property and for why a shared installation might turn it off.</p>

      <h2 id="what-is-not-here">What Is Not Here</h2>
      <p><strong>No write tool inside a profile.</strong> Jeffrey&rsquo;s JFR toolset has an <code>executeModification</code> that runs <code>UPDATE</code> and <code>DELETE</code>; it is deliberately not advertised to external clients. Not exposed rather than exposed-and-refusing: a tool that always answers &ldquo;not enabled&rdquo; spends a slot in the model&rsquo;s context and invites a call that cannot succeed. Data cleanup and frame renaming happen in the Jeffrey UI. <code>recordings_</code> is not a counter-example &mdash; it creates profiles, it does not rewrite one.</p>

      <p><strong>No deleting.</strong> The server can add a profile and never removes one, so a session that imported the wrong file leaves it behind for you to delete in the UI.</p>

      <p><strong>No OQL.</strong> Jeffrey&rsquo;s <router-link to="/docs/ai/oql-assistant">OQL assistant</router-link> is a UI feature; over MCP, heap questions go through the <code>heap_</code> family and its SQL.</p>

      <p><strong>No charts.</strong> The <code>jvm_</code> family carries the numbers behind each UI dashboard, not the timeseries they are drawn from: pause and throttling timelines, the G1 and ZGC deep dives, tenuring and reference processing, the thread timeline and the sub-second view stay in the UI, where a reader can scrub them. <code>profiles_link</code> opens the profile there.</p>

      <p><strong>No shell.</strong> The server answers questions about profiles and, with ingestion on, opens the one recording path it is handed. It runs nothing.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
