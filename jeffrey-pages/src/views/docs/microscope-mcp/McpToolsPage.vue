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
  { id: 'traces', text: 'traces_ — latency', level: 2 },
  { id: 'jfr', text: 'jfr_ — the profile database', level: 2 },
  { id: 'heap', text: 'heap_ — heap dumps', level: 2 },
  { id: 'what-is-not-here', text: 'What Is Not Here', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const nameShape = `flamegraph_export
    ^         ^
  family    method name, camelCase`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tool Reference"
      icon="bi bi-list-columns"
    />

    <div class="docs-content">
      <p>Thirty-nine tools in five families. Every one of them reads; none of them writes.</p>

      <h2 id="rules-that-apply-to-all-of-them">Rules That Apply to All of Them</h2>

      <p><strong>Names are <code>family_methodName</code>, camelCase preserved</strong> &mdash; <code>jfr_listTables</code>, not <code>jfr_list_tables</code>.</p>
      <DocsCodeBlock :code="nameShape" language="bash" />

      <p><strong>Every tool except <code>profiles_list</code> takes a <code>profileId</code></strong>, and it is required. That is the id from <code>profiles_list</code>; nothing else works without one.</p>

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
            <td>Identity, the recording window it covers, and its size</td>
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
            <td><code>flamegraph_panels</code></td>
            <td><code>profileId</code></td>
            <td>Which event types this profile can be graphed by. Call it first &mdash; asking for a type the profile did not record returns an empty tree, not an error</td>
          </tr>
          <tr>
            <td><code>flamegraph_export</code></td>
            <td><code>profileId</code>, <code>eventType</code>, <code>thresholdPct?</code>, <code>startMs?</code>, <code>endMs?</code>, <code>threadMode?</code>, <code>useWeight?</code>, <code>search?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The call tree as Markdown, with the reading preamble for that event type</td>
          </tr>
        </tbody>
      </table>

      <p>Common starting points: <code>jdk.ExecutionSample</code> for on-CPU time, <code>jdk.ObjectAllocationSample</code> for allocation (with <code>useWeight</code> to rank by bytes rather than call count), <code>jdk.JavaMonitorEnter</code> for lock contention (weight is nanoseconds blocked), <code>jdk.WallClockSample</code> for latency including off-CPU. <code>thresholdPct</code> controls how much survives pruning &mdash; raise it for an overview, lower it to chase one path.</p>

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
            <td>Profile-wide totals: how many traces and spans, how many failed</td>
          </tr>
          <tr>
            <td><code>traces_operations</code></td>
            <td><code>profileId</code>, <code>search?</code>, <code>errorsOnly?</code>, <code>sort?</code> (<code>TOTAL_TIME</code>), <code>limit?</code> (50)</td>
            <td>One row per operation with call count, latency percentiles and errors</td>
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
        </tbody>
      </table>

      <DocsCallout type="info" title="Two event types, two different meanings">
        On the flamegraph exports, <code>eventType</code> is the event that <em>opened the trace</em> (e.g. <code>jeffrey.HttpServerExchange</code>) while <code>graphEventType</code> is what to <em>graph</em> (e.g. <code>jdk.ExecutionSample</code>). They are never the same value.
      </DocsCallout>

      <h2 id="jfr">jfr_ &mdash; the profile database</h2>
      <p>Each profile is one DuckDB database. This family is the escape hatch for questions no purpose-built tool covers &mdash; distributions over time, correlations between event types, the cardinality of a field.</p>
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
            <td>An arbitrary read-only query (<code>SELECT</code> / <code>WITH</code> only)</td>
          </tr>
          <tr>
            <td><code>jfr_getProfileInfo</code></td>
            <td><code>profileId</code></td>
            <td>Profile, project and workspace ids</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning" title="Query events, never events_raw">
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

      <h2 id="what-is-not-here">What Is Not Here</h2>
      <p><strong>No write tool.</strong> Jeffrey&rsquo;s JFR toolset has an <code>executeModification</code> that runs <code>UPDATE</code> and <code>DELETE</code>; it is deliberately not advertised to external clients. Not exposed rather than exposed-and-refusing: a tool that always answers &ldquo;not enabled&rdquo; spends a slot in the model&rsquo;s context and invites a call that cannot succeed. Data cleanup and frame renaming happen in the Jeffrey UI.</p>

      <p><strong>No OQL.</strong> Jeffrey&rsquo;s <router-link to="/docs/ai/oql-assistant">OQL assistant</router-link> is a UI feature; over MCP, heap questions go through the <code>heap_</code> family and its SQL.</p>

      <p><strong>No shell, no filesystem.</strong> The server answers questions about profiles and nothing else.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
