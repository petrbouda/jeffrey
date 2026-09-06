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
  { id: 'family-map', text: 'Which Family Answers Your Question', level: 2 },
  { id: 'profiles', text: 'profiles_ — the catalogue', level: 2 },
  { id: 'flamegraph', text: 'flamegraph_ — call trees', level: 2 },
  { id: 'compare', text: 'compare_ — two profiles', level: 2 },
  { id: 'traces', text: 'traces_ — latency', level: 2 },
  { id: 'jvm', text: 'jvm_ — the machine underneath', level: 2 },
  { id: 'technologies', text: 'http_, jdbc_, grpc_, methodtracing_ — the technology dashboards', level: 2 },
  { id: 'waiting', text: 'io_, blocking_ — waiting rather than running', level: 2 },
  { id: 'timeline', text: 'timeline_ — when, not where', level: 2 },
  { id: 'memory', text: 'memory_ — allocation and leaks without a heap dump', level: 2 },
  { id: 'jfr', text: 'jfr_ — the profile database', level: 2 },
  { id: 'heap', text: 'heap_ — heap dumps', level: 2 },
  { id: 'hubs', text: 'hubs_ — recordings that are not on this machine', level: 2 },
  { id: 'ide', text: 'ide_ — where the code actually is', level: 2 },
  { id: 'recordings', text: 'recordings_ — creating profiles', level: 2 },
  { id: 'links', text: 'Links Back to the UI', level: 2 },
  { id: 'what-is-not-here', text: 'What Is Not Here', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const nameShape = `flamegraph_export
    ^         ^
  family    method name, camelCase`;

const exProfiles = `profiles_list  { "search": "checkout" }
profiles_features  { "profileId": "019f885e-..." }
profiles_viewLink  { "profileId": "019f885e-...", "view": "garbage-collection" }`;

const exFlamegraph = `flamegraph_list    { "profileId": "019f885e-..." }
flamegraph_export  { "profileId": "019f885e-...",
                     "eventType": "jdk.ObjectAllocationSample",
                     "useWeight": true, "thresholdPct": 2 }`;

const exTimeline = `# 1. where the mass is
timeline_hotWindows  { "profileId": "019f885e-...",
                       "eventType": "jdk.ObjectAllocationSample", "useWeight": true }
#    -> { "startMs": 31000, "endMs": 32000, "percentOfTotal": 10.5 }

# 2. graph only that window
flamegraph_export    { "profileId": "019f885e-...",
                       "eventType": "jdk.ObjectAllocationSample",
                       "useWeight": true, "startMs": 31000, "endMs": 32000 }

# 3. below one second, for a startup or the inside of a spike
timeline_zoom        { "profileId": "019f885e-...",
                       "eventType": "jdk.ObjectAllocationSample",
                       "startMs": 31000, "endMs": 33000, "bucketMs": 20 }`;

const exTraces = `traces_overview     { "profileId": "019f885e-..." }
traces_operations   { "profileId": "019f885e-...", "sort": "TOTAL_TIME", "limit": 20 }
traces_traceExport  { "profileId": "019f885e-...", "traceId": "2291db38124f4a53" }

# find one trace by correlation id, then open it
traces_attributeSearch { "profileId": "019f885e-...", "key": "correlationId",
                         "source": "ATTRIBUTE", "operator": "EQ",
                         "value": "01a03fb5-d51f-7292-974c-bdfcef9d35de" }`;

const exTechnologies = `http_overview  { "profileId": "019f885e-...", "direction": "SERVER" }
http_endpoint  { "profileId": "019f885e-...", "uri": "/api/orders", "direction": "SERVER" }
jdbc_overview  { "profileId": "019f885e-..." }
jdbc_pools     { "profileId": "019f885e-..." }
grpc_traffic   { "profileId": "019f885e-...", "direction": "CLIENT" }`;

const exWaiting = `blocking_overview  { "profileId": "019f885e-..." }
blocking_monitors  { "profileId": "019f885e-..." }
io_overview        { "profileId": "019f885e-...", "kind": "SOCKET" }
io_slowest         { "profileId": "019f885e-...", "kind": "FILE" }`;

const exJvm = `jvm_sections   { "profileId": "019f885e-..." }
jvm_gc         { "profileId": "019f885e-..." }
jvm_flags      { "profileId": "019f885e-..." }
jvm_threadDumps { "profileId": "019f885e-..." }`;

const exMemory = `memory_allocations    { "profileId": "019f885e-..." }
memory_leakCandidates { "profileId": "019f885e-..." }`;

const exCompare = `compare_list       { "profileId": "<after>", "baselineProfileId": "<before>" }
compare_movements  { "profileId": "<after>", "baselineProfileId": "<before>",
                     "eventType": "jdk.ExecutionSample", "limit": 20 }`;

const exHeap = `heap_getDominatorTreeRoots { "profileId": "01a06c53-...", "limit": 20 }
heap_getPathToGCRoot       { "profileId": "01a06c53-...", "objectId": 27908898928 }
heap_diff                  { "profileId": "<later>", "baselineProfileId": "<earlier>" }`;

const exJfr = `jfr_listTables   { "profileId": "019f885e-..." }
jfr_executeQuery { "profileId": "019f885e-...",
                   "query": "SELECT event_type, COUNT(*) FROM events GROUP BY 1 ORDER BY 2 DESC" }`;

const exRecordings = `recordings_analyzeFile { "path": "/abs/path/target/checkout-run.jfr",
                         "name": "checkout run" }
#  -> { "profileId": "019f885e-...", "link": "http://localhost:8585/profiles/019f885e-..." }`;

const analyzeExample = `Analyze target/checkout-run.jfr and tell me where the time goes.`;

const hubsExample = `Analyse what production recorded in the last hour.`;

const exIde = `ide_resolve { "profileId": "019f885e-...", "className": "com.example.OrderService", "methodName": "process", "line": 214 }
ide_windows { "profileId": "019f885e-...", "className": "com.example.OrderService" }
ide_source  { "profileId": "019f885e-...", "className": "com.zaxxer.hikari.pool.HikariPool" }`;

const exHubs = `hubs_sessions { "hub": "production", "withinLastMinutes": 60 }
#  -> | hub | workspace | project | started | duration | status | files | size | local | session_ref |
#     | production | default | checkout | 2026-03-01T11:41Z | 18m3s | FINISHED | 4 | 240MB | | h1Y2ZnLX... |

hubs_download { "sessionRef": "h1Y2ZnLX..." }
#  -> { "recordingId": "019f885e-...", "recordingFiles": 2, "artifactFiles": 2,
#       "nextStep": "Call recordings_analyzeRecording with recordingId=019f885e-..." }`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tool Reference"
      icon="bi bi-list-columns"
    />

    <div class="docs-content">
      <p>A hundred and four tools in eighteen families. Fifteen of them read a profile; <code>recordings_</code> and <code>hubs_</code> create one &mdash; from a file on this machine, or from a recording still sitting on a Jeffrey Hub &mdash; and <code>ide_</code> reads the code behind it out of the developer&rsquo;s running IntelliJ.</p>

      <h2 id="rules-that-apply-to-all-of-them">Rules That Apply to All of Them</h2>

      <p><strong>Names are <code>family_methodName</code>, camelCase preserved</strong> &mdash; <code>jfr_listTables</code>, not <code>jfr_list_tables</code>.</p>
      <DocsCodeBlock :code="nameShape" language="bash" />

      <p><strong>Every tool except <code>profiles_list</code> and the <code>recordings_</code> and <code>hubs_</code> families takes a <code>profileId</code></strong> (<code>ide_</code> included: the IDE window is linked per profile), and it is required. That is the id from <code>profiles_list</code>; nothing else works without one.</p>

      <p><strong>Output is capped at 120,000 characters, and says so when it cuts.</strong> A silently shortened flamegraph would be read as a complete one, so nothing is trimmed quietly. A Markdown answer &mdash; the exports, the listings &mdash; ends with an explicit <code>TRUNCATED</code> line naming the cap and suggesting a narrower query. A JSON answer is trimmed <em>in the tree</em> instead of at a character count: the largest array is shortened until the document fits, so what comes back is still parseable rather than ending mid-token, and it carries a <code>_truncated</code> object saying how many elements each shortened array kept out of how many it had. The SQL tools cap rows as well, and say when they do. Aggregate in the query rather than pulling rows back to count them.</p>

      <p><strong>The schema says what is required, and what the alternatives are.</strong> <code>tools/list</code> returns a JSON Schema per tool with a real <code>required</code> array &mdash; a missing argument is refused by the client before the call rather than deep inside Jeffrey &mdash; and parameters that are enumerations (<code>direction</code>, <code>kind</code>, <code>status</code>, <code>sortBy</code>, <code>page</code>, <code>report</code>) carry an <code>enum</code> rather than listing their values only in prose. In the tables below, an argument marked <code>name?</code> is one the schema leaves optional.</p>

      <p><strong>Every tool declares what it does to the world.</strong> Each spec carries MCP <code>annotations</code> &mdash; <code>readOnlyHint</code>, <code>destructiveHint</code>, <code>idempotentHint</code>, <code>openWorldHint</code> &mdash; so a client can tell the handful that write from the great majority that only read, without reading a hundred descriptions. Nothing Jeffrey exposes is destructive: no tool deletes a profile, a recording or a dump. <code>openWorldHint</code> marks the <code>hubs_</code> and <code>ide_</code> families, the two that reach outside this server &mdash; a machine other than this installation, and another process on it.</p>

      <p><strong>The Markdown exports carry their own reading instructions.</strong> <code>flamegraph_export</code>, <code>traces_traceExport</code> and <code>traces_operationExport</code> return documents that open by explaining what <code>self</code> means against <code>total</code>, what the frame tags mean, and what was pruned. Read the preamble the document gives you rather than assuming conventions from elsewhere &mdash; Jeffrey&rsquo;s <code>self</code> is a merged-interval computation, not a subtraction.</p>

      <h2 id="family-map">Which Family Answers Your Question</h2>
      <p>Eighteen families is more than anyone reads through. They group into six questions, and the question you arrived with picks the family for you &mdash; the same taxonomy the <router-link to="/docs/microscope-mcp/skills#analyze-jfr"><code>analyze-jfr</code></router-link> skill routes by, so the docs and the skill tell the same story. Every row links to its section below.</p>
      <table class="family-map">
        <thead>
          <tr>
            <th>Family</th>
            <th>Tools</th>
            <th>What it answers</th>
          </tr>
        </thead>
        <tbody>
          <tr class="map-group">
            <th colspan="3">Start here</th>
          </tr>
          <tr>
            <td><a href="#profiles"><code>profiles_</code></a></td>
            <td class="map-count">7</td>
            <td>Which recordings are analysed, what each one can answer, and a deep link into the UI. Every <code>profileId</code> comes from here.</td>
          </tr>
          <tr>
            <td><a href="#recordings"><code>recordings_</code></a></td>
            <td class="map-count">4</td>
            <td>A recording Jeffrey has never seen, as a file on this machine. Creates a profile rather than reading one, and an installation can switch it off on its own.</td>
          </tr>
          <tr>
            <td><a href="#hubs"><code>hubs_</code></a></td>
            <td class="map-count">3</td>
            <td>The recordings that never reached this machine &mdash; what a deployed application sent to a connected Jeffrey Hub. Finds a session and pulls it in; <code>recordings_</code> then turns it into a profile.</td>
          </tr>
          <tr>
            <td><a href="#ide"><code>ide_</code></a></td>
            <td class="map-count">5</td>
            <td>Where a frame lives in the reader&rsquo;s checkout, answered by their running IntelliJ. The step every other family stops one short of, and an installation can switch it off on its own.</td>
          </tr>
          <tr class="map-group">
            <th colspan="3">Where the time went</th>
          </tr>
          <tr>
            <td><a href="#flamegraph"><code>flamegraph_</code></a></td>
            <td class="map-count">2</td>
            <td>Which graphs this profile supports, and the call tree as Markdown &mdash; CPU, allocation, lock contention or wall-clock.</td>
          </tr>
          <tr>
            <td><a href="#timeline"><code>timeline_</code></a></td>
            <td class="map-count">2</td>
            <td><em>When</em> the samples landed: the busiest windows ranked, and sub-second zoom inside one. A graph of a whole recording averages a spike away.</td>
          </tr>
          <tr>
            <td><a href="#compare"><code>compare_</code></a></td>
            <td class="map-count">3</td>
            <td>Two profiles against each other: whether they are comparable at all, what moved, and the differential call tree.</td>
          </tr>
          <tr class="map-group">
            <th colspan="3">Why a request was slow</th>
          </tr>
          <tr>
            <td><a href="#traces"><code>traces_</code></a></td>
            <td class="map-count">11</td>
            <td>Trace operations, exemplars, span trees and span-scoped flamegraphs, plus attribute search to find one trace by correlation id.</td>
          </tr>
          <tr>
            <td><a href="#technologies"><code>http_</code></a></td>
            <td class="map-count">2</td>
            <td>The HTTP dashboard: latency percentiles, endpoints, status codes, slowest requests. Where &ldquo;this endpoint is slow&rdquo; starts.</td>
          </tr>
          <tr>
            <td><a href="#technologies"><code>jdbc_</code></a></td>
            <td class="map-count">3</td>
            <td>Statement timings and statement groups, plus the connection pools in front of them &mdash; the answer when every statement is fast and the request is not.</td>
          </tr>
          <tr>
            <td><a href="#technologies"><code>grpc_</code></a></td>
            <td class="map-count">3</td>
            <td>gRPC latency per service and method, and the message sizes moved.</td>
          </tr>
          <tr>
            <td><a href="#technologies"><code>methodtracing_</code></a></td>
            <td class="map-count">3</td>
            <td>Instrumented method timings (JEP 520): the methods by cost, the slowest invocations, per-method statistics.</td>
          </tr>
          <tr>
            <td><a href="#waiting"><code>io_</code></a></td>
            <td class="map-count">3</td>
            <td>Socket and file I/O: bytes, targets and slowest operations &mdash; waiting that produces no samples, so a flamegraph shows it as idle.</td>
          </tr>
          <tr>
            <td><a href="#waiting"><code>blocking_</code></a></td>
            <td class="map-count">3</td>
            <td>Contended monitors, waits, parks, sleeps and virtual-thread pinning.</td>
          </tr>
          <tr class="map-group">
            <th colspan="3">The machine underneath</th>
          </tr>
          <tr>
            <td><a href="#jvm"><code>jvm_</code></a></td>
            <td class="map-count">17</td>
            <td>Garbage collection and the pages beneath it, safepoints, JIT compilation, threads, native memory, class loading, exceptions, the host and who else is on it, TLS and certificates, the container quota, and what the JVM was actually started with.</td>
          </tr>
          <tr class="map-group">
            <th colspan="3">Memory</th>
          </tr>
          <tr>
            <td><a href="#memory"><code>memory_</code></a></td>
            <td class="map-count">2</td>
            <td>Allocation by type rather than by call site, and JFR-side leak candidates that need no heap dump.</td>
          </tr>
          <tr>
            <td><a href="#heap"><code>heap_</code></a></td>
            <td class="map-count">24</td>
            <td>Heap dumps: histogram, dominator tree, GC-root paths, class-loader leak chains, a diff between two dumps, SQL and OQL, and the pair that builds an index before it can be read.</td>
          </tr>
          <tr class="map-group">
            <th colspan="3">When nothing else fits</th>
          </tr>
          <tr>
            <td><a href="#jfr"><code>jfr_</code></a></td>
            <td class="map-count">7</td>
            <td>Raw SQL over the profile database, the fields of one event type, and anything no dashboard carries &mdash; a distribution over time, a correlation between two event types.</td>
          </tr>
        </tbody>
      </table>

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
            <td><code>profiles_summary</code></td>
            <td><code>profileId</code></td>
            <td>What one profile is, what it can answer, every event type it recorded and the top auto-analysis findings &mdash; <code>profiles_get</code>, <code>features</code> and <code>samplerHealth</code> in one call. The orienting question, in one round trip instead of four</td>
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
          <tr>
            <td><code>profiles_samplerHealth</code></td>
            <td><code>profileId</code></td>
            <td>Captured versus dropped CPU-time samples &mdash; whether the figures every other tool reports can be trusted</td>
          </tr>
          <tr>
            <td><code>profiles_viewLink</code></td>
            <td><code>profileId</code>, <code>view</code>, <code>objectId?</code></td>
            <td>A deep link to one named view &mdash; the GC, thread, JIT, memory and heap-dump pages. An unknown <code>view</code> is refused with the list of valid ones</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip" title="profiles_features is the cheap way to avoid dead ends">
        A JFR recording usually has no heap dump; a heap dump has no flamegraphs; traces exist only if the application ran Jeffrey&rsquo;s tracing instrumentation. One call rules out a whole family before it is tried.
      </DocsCallout>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exProfiles" language="json" />

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

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exFlamegraph" language="json" />

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

      <p><strong>A rename is not a regression.</strong> The diff matches method names level by level, so a renamed, moved or extracted method breaks the match and its work appears once as new and once as gone, often of near-identical size. <code>compare_movements</code> lists such pairs under a candidate-renames heading &mdash; suspicions for a reader holding the source diff to confirm, never a resolution, because weight alone cannot tell a rename from a coincidence. <code>compare_flamegraph</code> does not pair them for you: it marks the two halves <code>[NEW]</code> and <code>[GONE]</code> and says in its preamble to check the diff you have and it does not.</p>

      <p>Pruning in <code>compare_flamegraph</code> is by <strong>movement</strong>, not by size: a subtree in which nothing changed is dropped however large it is, and unmoved ancestors are kept so the frames that did move can still be placed. Absence there means &ldquo;did not move&rdquo;, the opposite of what it means in <code>flamegraph_export</code>.</p>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exCompare" language="json" />

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
            <td><code>traces_attributeKeys</code></td>
            <td><code>profileId</code>, <code>eventType?</code></td>
            <td>The attribute keys the traces carried, each identified by its (source, owner, key) triple</td>
          </tr>
          <tr>
            <td><code>traces_attributeValues</code></td>
            <td><code>profileId</code>, <code>key</code>, <code>source?</code>, <code>owner?</code>, <code>eventType?</code>, <code>sort?</code>, <code>limit?</code></td>
            <td>One key split into its values, each with its own p50, p95, max and error count</td>
          </tr>
          <tr>
            <td><code>traces_attributeSearch</code></td>
            <td><code>profileId</code>, <code>key</code>, <code>operator</code>, <code>value?</code>, <code>source?</code>, <code>owner?</code>, <code>scope?</code>, <code>limit?</code></td>
            <td>The individual traces carrying one value, with their ids</td>
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
        On <code>traces_operationFlamegraphExport</code>, <code>eventType</code> is the event that <em>opened the trace</em> (e.g. <code>jeffrey.HttpServerExchange</code>) while <code>graphEventType</code> is what to <em>graph</em> (e.g. <code>jdk.ExecutionSample</code>). They are never the same value. <code>traces_spanFlamegraphExport</code> has no such split: the span is already identified by <code>traceId</code> and <code>spanId</code>, so its <code>eventType</code> is what to graph. Both are required: there is no event type that is right to graph for every profile &mdash; a recording made with the CPU-time sampler carries no <code>jdk.ExecutionSample</code> at all &mdash; and a default would draw an empty graph for exactly those profiles.
      </DocsCallout>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exTraces" language="json" />

      <h2 id="jvm">jvm_ &mdash; the machine underneath</h2>
      <p>Garbage collection, safepoints, JIT compilation, threads, native memory, class loading, exceptions, the host, TLS, the container and the JVM&rsquo;s own configuration. Each tool renders the manager behind the matching Jeffrey UI page, so the numbers come from the same tested builders the UI draws its charts from.</p>

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
            <td><code>profileId</code>, <code>compute?</code> (false)</td>
            <td>Jeffrey&rsquo;s rule set over the recording &mdash; findings with a severity, an explanation and a suggested fix. Cached; <code>compute</code> runs it when nothing has, which reads the whole recording and is slow</td>
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
          <tr>
            <td><code>jvm_flags</code></td>
            <td><code>profileId</code></td>
            <td>The flag list grouped by <strong>origin</strong> &mdash; a default, the command line, or the JVM&rsquo;s own ergonomics</td>
          </tr>
          <tr>
            <td><code>jvm_gcDetail</code></td>
            <td><code>profileId</code>, <code>page?</code></td>
            <td>The GC pages beneath the overview, one at a time: <code>tenuring</code>, <code>ihop</code>, <code>g1</code>, <code>zgc</code>, <code>stringTables</code>, <code>finalizers</code>, <code>references</code>, <code>phases</code>, <code>plab</code>, <code>configuration</code>. Omit <code>page</code> for the list</td>
          </tr>
          <tr>
            <td><code>jvm_classLoading</code></td>
            <td><code>profileId</code></td>
            <td>Classes loaded and unloaded, the metaspace they hold, the loaders ranked by what they carry, the slowest individual loads, and any redefinitions an agent made</td>
          </tr>
          <tr>
            <td><code>jvm_exceptions</code></td>
            <td><code>profileId</code></td>
            <td>How many throwables, how many were sampled with a stack, how many were Errors, and the types ranked with their commonest messages</td>
          </tr>
          <tr>
            <td><code>jvm_system</code></td>
            <td><code>profileId</code></td>
            <td>Machine CPU against this JVM&rsquo;s own, what the difference leaves for everything else on the box, the peak context-switch rate, and the other processes running there</td>
          </tr>
          <tr>
            <td><code>jvm_security</code></td>
            <td><code>profileId</code></td>
            <td>TLS handshakes and distinct peers, the protocols and ciphers negotiated, certificates expired or weakly signed, and what was deserialized</td>
          </tr>
          <tr>
            <td><code>jvm_threadDumps</code></td>
            <td><code>profileId</code></td>
            <td>The dumps together: deadlocks, monitors threads queued on, threads stuck across consecutive dumps, and the most frequent frames</td>
          </tr>
          <tr>
            <td><code>jvm_threadDump</code></td>
            <td><code>profileId</code>, <code>index</code></td>
            <td>One dump in full, every thread with its state and stack</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="Every result says what it cannot answer">
        Each dashboard comes back wrapped with a <code>nextSteps</code> list &mdash; the same idea as the reading instructions a flamegraph or trace export opens with. <code>jvm_gc</code> says that no event in it names the code that produced the garbage and points at the allocation flamegraph; <code>jvm_container</code> points back at per-thread CPU load; <code>jvm_configuration</code> says to prefer these values over a deployment manifest. Every other analysis family carries the same envelope; the raw-SQL <code>jfr_</code> tools do not, and neither do <code>traces_operations</code> and <code>traces_notifications</code>. They route and never diagnose: no threshold decides whether they appear, and none of them claims the figures beside them are bad.
      </DocsCallout>

      <DocsCallout type="info" title="Call jvm_sections first">
        A recording holds only what the profiler was told to capture. Every section reports whether this profile carries its events, and a section asked for anyway is refused naming the events it needed &mdash; a dashboard rendered from events that were never recorded is a page of zeroes, which reads like a finding rather than like an absence.
      </DocsCallout>

      <DocsCallout type="warning" title="Auto analysis is read from a cache, not computed here">
        Generating it loads the whole recording through the JMC toolkit, which is bounded neither in time nor in memory by anything the server controls &mdash; a poor trade inside a tool whose point is being cheap. The Auto Analysis page in the Jeffrey UI computes and caches it; every call afterwards is a cache read. Until then the tool says so, and the other sections still answer.
      </DocsCallout>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exJvm" language="json" />

      <h2 id="technologies">http_, jdbc_, grpc_, methodtracing_ &mdash; the technology dashboards</h2>
      <p>Where <code>jvm_</code> answers for the machine, these four answer for what the application did at its edges: the calls it served, the queries it ran, the methods it instrumented. Each <code>_overview</code> is the whole dashboard in one call &mdash; header totals, the entities ranked, the status breakdown and the slowest individual operations &mdash; so the drill-down tools exist only to narrow to one endpoint, service or group.</p>
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
            <td><code>http_overview</code></td>
            <td><code>profileId</code>, <code>direction?</code></td>
            <td>Requests, response-time percentiles, success rate, 4xx/5xx counts, endpoints by traffic, status and method breakdowns, slowest requests</td>
          </tr>
          <tr>
            <td><code>http_endpoint</code></td>
            <td><code>profileId</code>, <code>uri</code>, <code>direction?</code></td>
            <td>The same, narrowed to one URI</td>
          </tr>
          <tr>
            <td><code>jdbc_overview</code></td>
            <td><code>profileId</code></td>
            <td>Statement count, execution-time percentiles, the operation mix, statement groups by cost, and the slowest statements with their SQL</td>
          </tr>
          <tr>
            <td><code>jdbc_statementGroup</code></td>
            <td><code>profileId</code>, <code>group</code></td>
            <td>The same, narrowed to one statement group</td>
          </tr>
          <tr>
            <td><code>jdbc_pools</code></td>
            <td><code>profileId</code></td>
            <td>Each connection pool: configured min/max against peak and average use, threads that waited, acquisition timeouts</td>
          </tr>
          <tr>
            <td><code>grpc_overview</code></td>
            <td><code>profileId</code>, <code>direction?</code></td>
            <td>Calls, response-time percentiles, success rate, services by traffic, status codes, slowest calls</td>
          </tr>
          <tr>
            <td><code>grpc_service</code></td>
            <td><code>profileId</code>, <code>service</code>, <code>direction?</code></td>
            <td>One service broken down by method</td>
          </tr>
          <tr>
            <td><code>grpc_traffic</code></td>
            <td><code>profileId</code>, <code>direction?</code></td>
            <td>Message sizes rather than timings: bytes moved, the size distribution, largest calls</td>
          </tr>
          <tr>
            <td><code>methodtracing_overview</code></td>
            <td><code>profileId</code></td>
            <td>Invocations, duration percentiles, and the methods ranked by count and by total time</td>
          </tr>
          <tr>
            <td><code>methodtracing_slowest</code></td>
            <td><code>profileId</code></td>
            <td>The slowest individual invocations, each with its thread</td>
          </tr>
          <tr>
            <td><code>methodtracing_timing</code></td>
            <td><code>profileId</code></td>
            <td>Per-method statistics as the JVM aggregated them: count with min, average and max</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="An empty dashboard and a missing one are different answers">
        These managers answer an event type that was never recorded with a well-formed empty result &mdash; zero statements, a perfect success rate. Every tool here checks first and says so in words instead, because &ldquo;the profiler did not capture this&rdquo; is a finding about the recording, not a clean bill of health for the database.
      </DocsCallout>

      <p><strong>Both directions.</strong> <code>http_</code> and <code>grpc_</code> take a <code>direction</code> of <code>SERVER</code> (the default) or <code>CLIENT</code>, and they are different questions: SERVER is what the application was asked to do, CLIENT what it asked of somebody else, where a slow figure belongs to a dependency and the only local fixes are to call less often or stop waiting. The two are gated separately, so &ldquo;no client-side data&rdquo; means the recording captured no outbound calls.</p>

      <p><strong>No chart series.</strong> The per-second series that draw the dashboard&rsquo;s graphs are left out of every answer &mdash; thousands of points describing a shape the percentiles already summarise. The shape is what the UI link is for. SQL text is truncated for the same reason: it is there to identify a statement, not to be executed.</p>

      <p><strong>Method tracing is JEP 520</strong>, not distributed tracing: instrumented method timings. Request-level spans are the <code>traces_</code> family above. Its two event types are independent, and a recording often carries one without the other, so each tool reports its own half as empty rather than returning zeros.</p>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exTechnologies" language="json" />

      <h2 id="waiting">io_, blocking_ &mdash; waiting rather than running</h2>
      <p>A thread blocked on a socket read or a monitor is not on-CPU, so it produces no samples and a CPU flamegraph reports the application as idle rather than as waiting. These two families are where that time is.</p>
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
            <td><code>io_overview</code></td>
            <td><code>profileId</code>, <code>kind</code> (<code>SOCKET</code> | <code>FILE</code>)</td>
            <td>Bytes read and written, operation count, and the slowest single operation with its target</td>
          </tr>
          <tr>
            <td><code>io_endpoints</code></td>
            <td><code>profileId</code>, <code>kind</code></td>
            <td>The hosts, ports or paths ranked by cost, each with operations, bytes, total and maximum time</td>
          </tr>
          <tr>
            <td><code>io_slowest</code></td>
            <td><code>profileId</code>, <code>kind</code></td>
            <td>The slowest individual operations, each with its target, bytes and the thread that waited</td>
          </tr>
          <tr>
            <td><code>blocking_overview</code></td>
            <td><code>profileId</code></td>
            <td>Contended monitors and time blocked, waits, parks, sleeps, and virtual-thread pinning &mdash; each with whether its event type was recorded at all</td>
          </tr>
          <tr>
            <td><code>blocking_monitors</code></td>
            <td><code>profileId</code></td>
            <td>Contention aggregated per lock class, with the waits alongside</td>
          </tr>
          <tr>
            <td><code>blocking_pinnedThreads</code></td>
            <td><code>profileId</code></td>
            <td>Virtual threads that pinned their carrier, and for how long</td>
          </tr>
        </tbody>
      </table>

      <p>These event types are threshold-gated, so a recording can hold none of them because nothing blocked for long enough as well as because the profiler was never asked. The tools report which of the two it is rather than returning a zero that reads like health.</p>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exWaiting" language="json" />

      <h2 id="timeline">timeline_ &mdash; when, not where</h2>
      <p><code>flamegraph_export</code>, <code>compare_flamegraph</code> and the trace exports all accept <code>startMs</code> and <code>endMs</code>, and nothing else in the surface helps you choose them. A flamegraph of a whole recording flattens a thirty-second spike into a five-minute average, and the spike stops being visible.</p>
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
            <td><code>timeline_hotWindows</code></td>
            <td><code>profileId</code>, <code>eventType</code>, <code>useWeight?</code>, <code>top?</code> (5)</td>
            <td>The recording bucketed, the busiest windows ranked with the bounds to pass on, and a one-line shape</td>
          </tr>
          <tr>
            <td><code>timeline_zoom</code></td>
            <td><code>profileId</code>, <code>eventType</code>, <code>startMs</code>, <code>endMs</code>, <code>bucketMs?</code> (20)</td>
            <td>The same at sub-second resolution inside one window &mdash; the only view that resolves below a second</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The reduction is the product, not the series">
        The managers behind these return chart geometry &mdash; three hundred points for a five-minute recording at one-second resolution, thirty thousand at ten milliseconds. A curve is not something a model can act on, which is why the dashboards drop their series entirely. What comes back instead is the ranked windows, each with the <code>startMs</code> and <code>endMs</code> the next tool takes, and a coarse shape line so a steady load, a ramp, a sawtooth and a single burst are told apart at a glance.
      </DocsCallout>

      <p>The workflow is three calls: <code>timeline_hotWindows</code> to find the window, <code>flamegraph_export</code> with its bounds to see what ran inside it, and <code>timeline_zoom</code> when a second is too coarse &mdash; a startup, or the inside of a spike.</p>

      <p><strong>The three-call loop.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exTimeline" language="json" />

      <h2 id="memory">memory_ &mdash; allocation and leaks without a heap dump</h2>
      <p>Two memory questions a plain JFR recording answers on its own.</p>
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
            <td><code>memory_allocations</code></td>
            <td><code>profileId</code></td>
            <td>Total bytes, the TLAB split, distinct types, and the types ranked by bytes</td>
          </tr>
          <tr>
            <td><code>memory_leakCandidates</code></td>
            <td><code>profileId</code></td>
            <td>Objects the JVM sampled and watched survive collections, with size and age</td>
          </tr>
        </tbody>
      </table>

      <p><strong>The other axis from a flamegraph.</strong> An allocation flamegraph ranks the call <em>sites</em> &mdash; where the allocating code is. This ranks the <em>types</em> allocated, and the two disagree usefully: <code>byte[]</code> and <code>char[]</code> at the top read very differently from a domain class, and one call site allocating many types looks nothing like one type coming from everywhere.</p>

      <p><strong>Leak candidates come from <code>jdk.OldObjectSample</code></strong>, which is off in most recordings. Their absence says nothing about whether the application leaks, and the tool says exactly that rather than reporting zero candidates &mdash; the difference between &ldquo;measured and found nothing&rdquo; and &ldquo;never measured&rdquo; is the whole finding.</p>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exMemory" language="json" />

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
            <td><code>jfr_describeEventType</code></td>
            <td><code>profileId</code>, <code>eventType</code></td>
            <td>The fields inside one event type, with their labels and types, and whether it carries a stack trace. <code>jfr_describeTable</code> can only say that <code>events</code> has a JSON column; this says what is in it</td>
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
        <code>jfr_listTables</code> lists tables only, so <code>events_raw</code> appears there and <code>events</code> does not &mdash; query <code>events</code> anyway. It is a view over <code>events_raw</code> that splices back the one large string field the parser pools out of each row; querying <code>events_raw</code> silently returns truncated JSON in <code>fields</code>, with no error to warn you. The bundled <code>jfr-sql</code> skill carries this and the rest of the schema.
      </DocsCallout>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exJfr" language="json" />

      <h2 id="heap">heap_ &mdash; heap dumps</h2>
      <p><code>heap_diff</code> is the one that needs two profiles: it compares this dump against an earlier one class by class, ranked by growth, and is the only way to separate a leak from a large working set &mdash; a single dump shows a state, and a state cannot tell the two apart. Pass the earlier dump as <code>baselineProfileId</code>; backwards, every growth reads as a shrink. Both dumps have to be indexed first, and the tool says which one is not.</p>
      <p>Twenty-four tools against a parsed heap dump&rsquo;s own DuckDB index, separate from the profile&rsquo;s JFR database. Asking for them on a profile with no heap dump fails immediately with a message saying so, rather than deep inside the engine.</p>

      <p><strong>Preparing a dump.</strong> Retained sizes, the dominator tree and the cached reports do not exist until something builds them. Two tools do:</p>
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
            <td><code>heap_prepare</code></td>
            <td><code>profileId</code>, <code>report?</code></td>
            <td>Starts the index, the dominator tree and the cached reports, and returns straight away with the stage list. Pass a report name &mdash; <code>leaks</code>, <code>biggest</code>, <code>classloaders</code>, <code>consumers</code>, <code>strings</code>, <code>collections</code>, <code>dominator</code>, <code>threads</code>, <code>biggest-collections</code>, <code>duplicates</code> &mdash; to compute one on a dump that is already indexed</td>
          </tr>
          <tr>
            <td><code>heap_status</code></td>
            <td><code>profileId</code></td>
            <td>How far that has got, stage by stage. Poll this rather than retrying the report tool, which cannot tell &ldquo;still building&rdquo; from &ldquo;never asked for&rdquo;</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The one pair here that writes, and what it writes is a cache">
        <code>heap_prepare</code> runs the same pipeline as the <strong>Initialize</strong> button in the UI, on the same registry &mdash; a run started from a session is visible in the browser and the other way round, and a second request joins the one in flight rather than racing it. It returns immediately because a dominator build over a multi-gigabyte heap takes minutes, which is well past what a client waits for a tool call. No dump is altered and nothing is deleted.
      </DocsCallout>

      <p><strong>Reports</strong> &mdash; cached, and faster and safer than reproducing them in SQL. A report nothing has computed answers that it has not been run for this dump yet, and names the <code>heap_prepare</code> report that computes it:</p>
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
            <td><code>heap_diff</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code>, <code>topN?</code></td>
            <td>This dump against an earlier one, class by class, ranked by growth (default 30, maximum 200)</td>
          </tr>
          <tr>
            <td><code>heap_getLeakSuspects</code></td>
            <td><code>profileId</code></td>
            <td>Leak-suspect analysis, once <code>heap_prepare</code> or the UI has built it</td>
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
          <tr>
            <td><code>heap_oql</code></td>
            <td><code>profileId</code>, <code>query</code>, <code>limit?</code> (50, max 100), <code>includeRetainedSize?</code></td>
            <td>Jeffrey&rsquo;s OQL against the object graph: <code>SELECT * FROM INSTANCEOF java.util.Map</code>, <code>SELECT AS RETAINED SET * FROM com.acme.Cache</code>, a filter over an object&rsquo;s own fields. Rows carry an <code>objectId</code> the other tools take</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The dominator tree is built lazily">
        <code>dominator</code> and <code>retained_size</code> are empty until something builds them, so a SQL query joining <code>retained_size</code> on a fresh dump returns nulls rather than zeros. Build them first with <code>heap_prepare</code> and <code>report: "dominator"</code>, then watch <code>heap_status</code>; <code>heap_getDominatorTreeRoots</code> also triggers the build on a dump small enough to finish inside the call. The bundled <code>heap-sql</code> skill covers the whole schema.
      </DocsCallout>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exHeap" language="json" />

      <h2 id="hubs">hubs_ &mdash; recordings that are not on this machine</h2>
      <p>Everything above starts from something Jeffrey already holds, and <code>recordings_</code> below starts from a file on the machine Jeffrey runs on. This family starts from neither: it is the recordings a <em>deployed</em> application sent to a connected <router-link to="/docs/hub">Jeffrey Hub</router-link>, which is where the interesting ones usually are.</p>
      <DocsCodeBlock :code="hubsExample" language="text" />

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
            <td><code>hubs_list</code></td>
            <td>&mdash;</td>
            <td>Every connected hub with its address, whether it was declared in configuration or added through the UI, and whether it answers right now</td>
          </tr>
          <tr>
            <td><code>hubs_sessions</code></td>
            <td><code>hub?</code>, <code>workspace?</code>, <code>project?</code>, <code>withinLastMinutes?</code>, <code>status?</code>, <code>limit?</code></td>
            <td>Recording sessions across <strong>every</strong> hub at once, newest first, each row carrying a <code>session_ref</code> and a <code>local</code> column saying whether it is already here</td>
          </tr>
          <tr>
            <td><code>hubs_download</code></td>
            <td><code>sessionRef</code></td>
            <td>Pulls that session in &mdash; its recording files merged into one, its heap dumps and logs alongside &mdash; and returns a <code>recordingId</code> for <code>recordings_analyzeRecording</code>. A transfer that outlasts the call comes back with a status saying so; call the tool again with the same <code>sessionRef</code> to check</td>
          </tr>
        </tbody>
      </table>

      <p><strong>Flat, not a tree.</strong> A hub holds workspaces holding projects holding sessions, and the web UI lets you walk that. There is deliberately no tool for the walk. One <code>hubs_sessions</code> call fans out across every hub and returns flat rows, because four calls before anything is downloaded is four chances for a model to pair a workspace with the wrong project. The hierarchy survives as the <code>hub</code>, <code>workspace</code> and <code>project</code> filters, all matched loosely against names, and as columns you can read.</p>

      <p><strong><code>withinLastMinutes</code> is an overlap, not a start time.</strong> A JVM that began recording three hours ago and is still running matches a sixty-minute window, because it <em>was</em> recording during it. That is what someone asking for "the last hour" means, and the opposite of what filtering on start time would return.</p>

      <p><strong>Downloading and analysing are two calls on purpose.</strong> <code>hubs_download</code> stops at a recording and hands back its id; <code>recordings_analyzeRecording</code> builds the profile. A single call covering a multi-gigabyte transfer <em>and</em> a full analysis is the shape that trips a client's tool timeout, and a timeout partway through says nothing about whether the work survived.</p>

      <p><strong>The download itself is bounded too.</strong> It waits about forty-five seconds and then answers with a status saying the transfer continues, rather than holding the call open until the bytes land. It needs no separate status tool: it answers from the local store first, so calling <code>hubs_download</code> again with the same <code>sessionRef</code> <em>is</em> the poll, and a second call while the first is still running joins it rather than fetching the session twice.</p>

      <DocsCallout type="tip" title="Read the local column before downloading">
        A row whose <code>local</code> reads <code>profile:&lt;id&gt;</code> is already analysed and that id works immediately; <code>recording:&lt;id&gt;</code> is downloaded but not yet analysed. Jeffrey recognises a session it has seen before from the <code>origin.*</code> tags it wrote at download time, so a repeated <code>hubs_download</code> returns what is already there rather than moving the bytes again &mdash; but reading the column first saves the round trip.
      </DocsCallout>

      <p>A hub that does not answer is reported <em>under the table</em> rather than as a failure, and it is reported even when no rows came back at all. That case is the one worth getting right: "no sessions found" and "production is unreachable" lead to completely different next steps, and the managers underneath this family return an empty list for both, so the family probes each hub explicitly to tell them apart.</p>

      <p>The family is advertised only while both hub access and ingestion are enabled; see <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> for the properties and why the two are linked.</p>

      <p><strong>Example.</strong></p>
      <DocsCodeBlock :code="exHubs" language="json" />

      <h2 id="ide">ide_ &mdash; where the code actually is</h2>
      <p>Every other family ends at a method signature. The exports say so themselves &mdash; they carry call paths and figures, and a source line only where every sample at a frame agreed on one &mdash; which leaves an agent that wants to act on a finding grepping a checkout for a name that may be inherited, overloaded, generated, or a Kotlin facade stored under a different name on disk. This family closes that gap by asking the thing that already knows: an IntelliJ window with the project open, its indexes built, and sources attached for the dependencies too.</p>
      <p>It needs the <router-link to="/docs/intellij-plugin">Jeffrey IntelliJ plugin</router-link> running, at protocol version 2 or newer for <code>ide_resolve</code>.</p>

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
            <td><code>ide_resolve</code></td>
            <td><code>className</code>, <code>methodName?</code>, <code>line?</code></td>
            <td>The absolute file and line, plus whether the position is <code>decompiled</code>, <code>imprecise</code> or <code>stale</code>, and what to do about each. Does <strong>not</strong> move the editor</td>
          </tr>
          <tr>
            <td><code>ide_source</code></td>
            <td><code>className</code></td>
            <td>The source text as the IDE has it &mdash; attached sources for a library when they exist, a decompiled reconstruction when they do not</td>
          </tr>
          <tr>
            <td><code>ide_windows</code></td>
            <td><code>className?</code></td>
            <td>Every open window, its branch and HEAD commit, whether it holds the class, and whether it is on the commit the recording was built from</td>
          </tr>
          <tr>
            <td><code>ide_link</code></td>
            <td><code>projectId</code></td>
            <td>Binds one window to this profile for every later lookup. Only needed when the choice is ambiguous</td>
          </tr>
          <tr>
            <td><code>ide_open</code></td>
            <td><code>className</code>, <code>methodName?</code>, <code>line?</code></td>
            <td>Opens the location and brings the window to the front. The one tool here with a visible side effect</td>
          </tr>
        </tbody>
      </table>

      <p><strong>Resolving is not jumping.</strong> <code>ide_resolve</code> and <code>ide_open</code> are separate tools because they are separate acts, and only one of them is safe to do a hundred times while writing up an analysis. An agent grounding a finding wants the first; only an explicit &ldquo;show me this&rdquo; wants the second. This is why the plugin grew a <code>resolve</code> endpoint of its own rather than reusing <code>navigate</code>.</p>

      <p><strong>A location arrives with its caveats or not at all.</strong> A decompiled file&rsquo;s line numbers are a decompiler&rsquo;s and match nothing anybody wrote; an imprecise hit is the declaration rather than the statement; a stale file has been edited well after the recording was taken. Each comes back with the one instruction that makes it actionable, because the difference between a line a finding can cite and one it cannot is exactly those three facts.</p>

      <p><strong>The window is chosen once, and only when it is unambiguous.</strong> There is no reader at the other end of an MCP call to answer a picker, so the first lookup links the single window that contains the class &mdash; or the single window there is, which is the normal case for a frame in a dependency &mdash; and otherwise refuses with the candidates named. Guessing between two checkouts is how an analysis ends up quoting the wrong repository.</p>

      <DocsCallout type="info" title="It can put a file on somebody's screen">
        This family has its own switch, <code>jeffrey.microscope.mcp.ide.enabled</code>, for the same reason <code>hubs_</code> does, one step closer to home: everything else reads a recording Jeffrey already holds, while this reaches into another process on this machine and <code>ide_open</code> moves a developer&rsquo;s cursor. It is on by default and answers nothing until a window is linked. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
      </DocsCallout>

      <p><strong>Examples.</strong></p>
      <DocsCodeBlock :code="exIde" language="json" />

      <h2 id="recordings">recordings_ &mdash; creating profiles</h2>
      <p>Everything above answers questions about a profile that already exists. This family is how one comes to exist without leaving the terminal: you point the agent at a recording file in your repository and it imports the file and builds the profile, then carries on with the id it got back.</p>
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
            <td><code>recordings_status</code></td>
            <td><code>recordingId</code></td>
            <td>Whether an analysis that outlasted its call has finished, and the profile id once it has. Poll this rather than analysing again, which would build a second profile of the same file</td>
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
        <code>path</code> must be <strong>absolute</strong> and must exist <strong>on the machine Jeffrey runs on</strong>. A relative path is rejected rather than guessed at &mdash; it would resolve against Jeffrey&rsquo;s working directory, not yours. A leading <code>~</code> is the one exception, and it expands against <em>Jeffrey&rsquo;s</em> home directory rather than the caller&rsquo;s, so it is only the same file when both are the same account on the same machine. For a Jeffrey in a container or on another host, mount or copy the file where Jeffrey can see it first.
      </DocsCallout>

      <DocsCallout type="warning" title="A large recording outlasts the call &mdash; poll, do not re-analyse">
        Both analyse tools wait about <strong>forty-five seconds</strong> for the parse. A small recording finishes inside that and its <code>profileId</code> comes straight back, exactly as before. A large one comes back with a status of <code>running</code> while the parse carries on in the background, and <code>recordings_status</code> reports the stage it is on and the <code>profileId</code> once it lands. Poll that rather than calling the analyse tool again: a second <code>recordings_analyzeFile</code> imports the file a second time and leaves you with two profiles of one recording and no way to tell them apart. A second <code>recordings_analyzeRecording</code> for the same recording is safe &mdash; it joins the run already in flight rather than racing it.
      </DocsCallout>

      <p>One more thing worth knowing: each <code>recordings_analyzeFile</code> imports the file again and builds another profile &mdash; call <code>recordings_list</code> or <code>profiles_list</code> first if the same file may already be there.</p>

      <p>The family is advertised only while ingestion is enabled; see <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> for the property and for why a shared installation might turn it off.</p>

      <p><strong>Example.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exRecordings" language="json" />

      <h2 id="links">Links Back to the UI</h2>
      <p>Analysis answers carry a link to the view that shows them &mdash; a <code>uiLink</code> field on the JSON answers, and a trailing <code>Open in Jeffrey: &hellip;</code> line on the Markdown exports. The flamegraph link reproduces the event type and filters the export was built with, the operation link opens on its slowest or flames tab, and a trace link opens that trace&rsquo;s span waterfall.</p>
      <p>The link is for the reader, not for the model. A URL carries nothing that can be analysed further and does not help choose the next tool, which is exactly why it travels attached to an answer rather than behind a tool of its own: a model weighing its own context would reasonably skip a call whose result it cannot use. <code>profiles_viewLink</code> is there for the pages an answer did not come from.</p>
      <p>The host comes from the request the client made, so the address is by definition one that reaches this installation. Two things a link cannot reproduce: the rendered flamegraph view has no query parameter for a time window or a search term, so a filtered export says so in its link line rather than quietly opening a different graph.</p>

      <h2 id="what-is-not-here">What Is Not Here</h2>
      <p><strong>No write tool inside a profile.</strong> Jeffrey&rsquo;s JFR toolset has an <code>executeModification</code> that runs <code>UPDATE</code> and <code>DELETE</code>; it is deliberately not advertised to external clients. Not exposed rather than exposed-and-refusing: a tool that always answers &ldquo;not enabled&rdquo; spends a slot in the model&rsquo;s context and invites a call that cannot succeed. Data cleanup and frame renaming happen in the Jeffrey UI. <code>recordings_</code> is not a counter-example &mdash; it creates profiles, it does not rewrite one.</p>

      <p><strong>No deleting.</strong> The server can add a profile and never removes one, so a session that imported the wrong file leaves it behind for you to delete in the UI.</p>

      <p><strong>No OQL <em>assistant</em>.</strong> The OQL language itself is here &mdash; <code>heap_oql</code> runs it against the object graph. What stays in the UI is the <router-link to="/docs/ai/oql-assistant">assistant</router-link> that writes a query for you from a question in English; over MCP the model writes its own.</p>

      <p><strong>No charts.</strong> The <code>jvm_</code> family carries the numbers behind each UI dashboard, not the timeseries they are drawn from: pause and throttling timelines, the G1 and ZGC deep dives, tenuring and reference processing, the thread timeline and the sub-second view stay in the UI, where a reader can scrub them. <code>profiles_link</code> opens the profile there.</p>

      <p><strong>No shell.</strong> The server answers questions about profiles and, with ingestion on, opens the one recording path it is handed. It runs nothing.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ============================
   Family Map
   ============================ */
.family-map tr.map-group th {
  padding-top: 1rem;
  background: #f1f2ff;
  color: #3b40c9;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.family-map tr.map-group:first-child th {
  padding-top: 0.75rem;
}

.family-map tr.map-group:hover {
  background: #f1f2ff;
}

.family-map .map-count {
  width: 4.5rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: #6c757d;
}
</style>
