<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
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
  { id: 'answers', text: 'How an Answer Reads', level: 2 },
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
  { id: 'window-question', text: 'Which Part of a Large Session', level: 3 },
  { id: 'ide', text: 'ide_ — where the code actually is', level: 2 },
  { id: 'recordings', text: 'recordings_ — creating profiles', level: 2 },
  { id: 'delete-confirmation', text: 'Confirming a Deletion', level: 3 },
  { id: 'operations', text: 'operations_ — the work the writers start', level: 2 },
  { id: 'tasks', text: 'Tasks — the same work, the standard way', level: 3 },
  { id: 'links', text: 'Links Back to the UI', level: 2 },
  { id: 'what-is-not-here', text: 'What Is Not Here', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const nameShape = `flamegraph_export
    ^         ^
  family    method name, camelCase`;

const metaExample = `{ "name": "heap_getLeakSuspects",
  "_meta": { "jeffrey/cost": "CHEAP",
             "jeffrey/requires": ["HEAP_DUMP_INDEXED", "HEAP_REPORTS"] },
  ... }`;

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
#    -> hottestWindows[0] = { "startEpochMs": 1766190052403, "endEpochMs": 1766190053403,
#                             "value": 9, "percentOfTotal": 3.4 }
#       followUp.nextTools[0] is the export below, arguments filled in

# 2. graph only that window
flamegraph_export    { "profileId": "019f885e-...",
                       "eventType": "jdk.ObjectAllocationSample", "useWeight": true,
                       "startEpochMs": 1766190052403, "endEpochMs": 1766190053403 }

# 3. below one second, for a startup or the inside of a spike
timeline_zoom        { "profileId": "019f885e-...",
                       "eventType": "jdk.ObjectAllocationSample",
                       "startEpochMs": 1766190052403, "endEpochMs": 1766190054403,
                       "bucketMs": 20 }`;

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
heap_getPathToGCRoot       { "profileId": "01a06c53-...", "objectId": "27908898928" }
heap_diff                  { "profileId": "<later>", "baselineProfileId": "<earlier>" }`;

const exJfr = `jfr_listTables   { "profileId": "019f885e-..." }
jfr_executeQuery { "profileId": "019f885e-...",
                   "query": "SELECT event_type, COUNT(*) FROM events GROUP BY 1 ORDER BY 2 DESC" }`;

const exRecordings = `recordings_analyzeFile { "path": "/abs/path/target/checkout-run.jfr",
                         "name": "checkout run" }
#  -> { "status": "READY", "recordingId": "01a0e285-...", "profileId": "019f885e-...",
#       "reused": false, ..., "uiLink": "http://localhost:8585/profiles/019f885e-..." }`;

const analyzeExample = `Analyze target/checkout-run.jfr and tell me where the time goes.`;

const hubsExample = `Analyse what production recorded between 14:00 and 15:00 today.`;

const exIde = `ide_resolve { "profileId": "019f885e-...", "className": "com.example.OrderService", "methodName": "process", "line": 214 }
ide_windows { "profileId": "019f885e-...", "className": "com.example.OrderService" }
ide_source  { "profileId": "019f885e-...", "className": "com.zaxxer.hikari.pool.HikariPool" }`;

const exHubs = `hubs_sessions { "hub": "production", "withinLastMinutes": 60 }
#  -> sessions[0] = { "hub": "production", "project": "checkout", "status": "FINISHED",
#                      "startedAtEpochMs": 1772365260000, "durationMs": 1083000,
#                      "files": 4, "sizeBytes": 251658240, "recordingId": null,
#                      "sessionRef": "h1Y2ZnLX..." }

hubs_download { "sessionRef": "h1Y2ZnLX...",
                "startEpochMs": 1772365260000, "endEpochMs": 1772368860000 }
#  -> { "status": "DOWNLOADED", "recordingId": "019f885e-...", "recordingFiles": 2,
#       "artifactFiles": 2, "coveredStartEpochMs": ..., "coveredEndEpochMs": ...,
#       "followUp": { "nextTools": [ { "tool": "recordings_analyzeRecording",
#                                      "arguments": { "recordingId": "019f885e-..." }, ... } ] } }`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Tool Reference"
      icon="bi bi-list-columns"
    />

    <div class="docs-content">
      <p>Tools are grouped by the questions they answer. Most families read a profile; <code>recordings_</code> and <code>hubs_</code> create one &mdash; from a file on this machine, or from a recording still sitting on a Jeffrey Hub &mdash; and <code>ide_</code> reads the code behind it out of the developer&rsquo;s running IntelliJ.</p>

      <h2 id="rules-that-apply-to-all-of-them">Rules That Apply to All of Them</h2>

      <p><strong>Names are <code>family_methodName</code>, camelCase preserved</strong> &mdash; <code>jfr_listTables</code>, not <code>jfr_list_tables</code>.</p>
      <DocsCodeBlock :code="nameShape" language="bash" />

      <p><strong>Every tool except <code>profiles_list</code> and the <code>recordings_</code>, <code>hubs_</code> and <code>operations_</code> families takes a <code>profileId</code></strong> (<code>ide_</code> included: the IDE window is linked per profile), and it is required. That is the id from <code>profiles_list</code>; nothing else works without one.</p>

      <p><strong>Output is capped at 120,000 characters, and says so when it cuts.</strong> A silently shortened flamegraph would be read as a complete one, so nothing is trimmed quietly. A structured answer bounds its own lists before it is written and declares the cut in its record &mdash; <code>truncated</code>, an <code>omitted&hellip;</code> count, <code>hasMore</code> with a <code>nextCursor</code>, or the SQL tools&rsquo; <code>truncation</code> and <code>capped</code> &mdash; so what comes back is always valid against its schema. A Markdown export &mdash; the flamegraphs, the trace documents &mdash; is cut before its <a href="#answers">footer</a>, reports <code>truncated: true</code>, and ends its body with an explicit <code>TRUNCATED</code> line naming the cap and suggesting a narrower query. Aggregate in the query rather than pulling rows back to count them.</p>

      <p id="result-size"><strong>The tools whose answers run long say so up front.</strong> Claude Code warns at 10,000 tokens of tool result and, past 25,000 by default, writes the result to a file and hands the model a path instead. Seven tools can land in that band on an ordinary profile &mdash; <code>flamegraph_export</code>, <code>compare_flamegraph</code>, <code>traces_traceExport</code>, <code>traces_operationExport</code>, <code>traces_spanFlamegraphExport</code>, <code>jfr_executeQuery</code> and <code>heap_getDominatorTreeRoots</code> &mdash; and each carries <code>"_meta": {"anthropic/maxResultSizeChars": 120000}</code> in <code>tools/list</code>, the same figure as the cap above, so Claude Code keeps their answers inline. Other clients ignore the key. The defaults are sized to stay well under it: the flamegraph export prunes at 2% unless asked otherwise, <code>jvm_threadDump</code> answers fifty threads, and <code>heap_getDominatorTreeRoots</code> fifty roots.</p>

      <p id="descriptions"><strong>A description says what a tool does, not when to call it.</strong> Each opens with a verb &mdash; <em>Returns</em>, <em>Ranks</em>, <em>Exports</em>, <em>Downloads</em> &mdash; and says what the tool does, what it returns and, where two tools sit close together, which one answers the neighbouring question instead. No tool description runs past 1,500 characters, well inside the 2,048 at which Claude Code cuts one off without saying so, and no parameter description past 400; <code>McpToolsetAssemblerTest</code> pins both. The order tools are called in is not in a description: it is in the server&rsquo;s <code>instructions</code>, family by family and only for the families advertised &mdash; <code>flamegraph_list</code> before <code>flamegraph_export</code>, <code>compare_list</code> before the other <code>compare_</code> tools, <code>jvm_sections</code> before the other <code>jvm_</code> tools, <code>heap_status</code> polled after <code>heap_prepare</code>, <code>ide_resolve</code> before a finding names a file &mdash; and in the skills.</p>

      <p><strong>The schema says what is required, and what the alternatives are.</strong> <code>tools/list</code> returns a JSON Schema per tool with a real <code>required</code> array &mdash; a missing argument is refused by the client before the call rather than deep inside Jeffrey &mdash; and parameters that are enumerations (<code>detail</code>, <code>direction</code>, <code>kind</code>, <code>state</code>, <code>status</code>, <code>source</code>, <code>operator</code>, <code>scope</code>, <code>sort</code>, <code>sortBy</code>, <code>order</code>, <code>page</code>, <code>report</code>) carry an <code>enum</code> rather than listing their values only in prose. A value outside the list is refused by name, with the alternatives spelled out, before the tool runs &mdash; as is a required argument the call left out, rather than being bound to an empty value the tool then reads as an answer. In the tables below, an argument marked <code>name?</code> is one the schema leaves optional.</p>

      <p><strong>Every tool declares what it does to the world.</strong> Each spec carries MCP <code>annotations</code> &mdash; <code>readOnlyHint</code>, <code>destructiveHint</code>, <code>idempotentHint</code>, <code>openWorldHint</code> &mdash; so a client can tell the handful that write from the great majority that only read, without reading a hundred descriptions. The tools that change state are: <code>recordings_analyzeFile</code> and <code>recordings_analyzeRecording</code>, which create a profile, <code>heap_prepare</code>, which builds a cache, <code>heap_oql</code> with <code>includeRetainedSize</code> and <code>jvm_autoAnalysis</code> with <code>compute</code>, which fill the dominator tree and the Auto Analysis cache and so run as operations the way the writers do, <code>hubs_download</code>, which moves a recording off another machine and creates one here, <code>hubs_fetchFile</code>, which moves one of a session&rsquo;s artifacts the same way, <code>recordings_delete</code>, which removes a recording and the profile built from it, <code>operations_cancel</code>, which requests cancellation of background work, and <code>ide_link</code> and <code>ide_open</code>, which act on the editor beside Jeffrey rather than on a profile. Each says so for itself rather than inheriting its family&rsquo;s hint, which is why <code>recordings_list</code>, <code>recordings_status</code> and <code>heap_status</code> read as read-only although they sit in families that write. One tool is destructive, and says so with <code>destructiveHint</code>: <code>recordings_delete</code>. Nothing else deletes a profile, a recording or a dump. <code>openWorldHint</code> marks the <code>hubs_</code> and <code>ide_</code> families and the <code>operations_</code> pair, which can poll or cancel work on a Hub. These tools can reach outside this server &mdash; a machine other than this installation, and another process on it.</p>

      <p id="findings"><strong>The two tools that judge share one finding shape.</strong> Almost everything here reports figures and routes; two tools go further and say something is wrong &mdash; <code>jvm_autoAnalysis</code>, the JMC rule set, and the throttling verdict in <code>jvm_container</code> &mdash; and both emit the same record rather than a shape of their own: <code>id</code> (<code>category:subject</code>, stable across tools, so the same condition reported twice collapses into one), <code>severity</code> (<code>CRITICAL</code>, <code>WARNING</code>, <code>INFO</code>, or <code>OK</code> for a check that ran and passed), <code>category</code>, <code>title</code>, <code>detail</code>, <code>source</code> (the tool that produced it), <code>evidence</code> (the figures it rests on), <code>action</code> (the source&rsquo;s suggestion, not a diagnosis) and <code>nextTool</code> &mdash; a ready call, <code>{"tool", "arguments", "why"}</code>, that carries the figures in full, or <code>null</code> when that tool is not advertised. <code>profiles_summary</code> leads with the ones that flagged something. A rule that had no events to run on is not a finding of any severity: it goes under <code>notEvaluated</code>, and the summary&rsquo;s <code>capabilityGaps</code> say what it would have needed.</p>

      <p><strong>The Markdown exports carry their own reading instructions.</strong> <code>flamegraph_export</code>, <code>traces_traceExport</code> and <code>traces_operationExport</code> return documents that open by explaining what <code>self</code> means against <code>total</code>, what the frame tags mean, and what was pruned. Read the preamble the document gives you rather than assuming conventions from elsewhere &mdash; Jeffrey&rsquo;s <code>self</code> is a merged-interval computation, not a subtraction.</p>

      <h2 id="answers">How an Answer Reads</h2>
      <p>Every tool but one answers with a typed record: <code>tools/list</code> gives 110 of the 111 an <code>outputSchema</code>, and each call returns the record in <code>structuredContent</code>. The text beside it is the same record as JSON &mdash; or, for the documents written to be read (the flamegraph, comparison and trace exports, the listings), Markdown that ends in a footer rendered from the same record. <code>ide_source</code> is the exception: its answer is a class&rsquo;s source text. The conventions below hold for every tool, so an agent that has read one answer can read them all.</p>
      <ul>
        <li><strong>Closed schemas.</strong> Each <code>outputSchema</code> is generated from the record the tool returns: every field required, <code>additionalProperties: false</code>, no <code>$ref</code>. A field that can be absent is typed <code>["&lt;type&gt;", "null"]</code> and comes back as an explicit <code>null</code>, never left out &mdash; and a count the tool cannot know is <code>null</code>, not a zero that reads as a measurement. The one open shape is <code>{"type": "object"}</code>, used only where a payload differs by kind: a next call&rsquo;s <code>arguments</code>, a finding&rsquo;s <code>evidence</code>, an operation&rsquo;s <code>result</code>.</li>
        <li><strong>Time is UTC epoch milliseconds.</strong> Every instant and every window bound is named <code>&hellip;EpochMs</code>, in the arguments as in the answers &mdash; <code>startEpochMs</code> and <code>endEpochMs</code> on <code>flamegraph_export</code>, <code>compare_movements</code>, <code>compare_flamegraph</code>, <code>timeline_zoom</code> and <code>hubs_download</code>, and on every window an answer hands out. A bound outside the recording is refused naming the recording&rsquo;s own span. Durations carry their unit in the name: <code>durationMs</code>, <code>durationNanos</code>. A comparison window is given on the primary&rsquo;s clock and applied at the same offset into the baseline; the answer reports where it landed on both.</li>
        <li><strong>Enumerations are upper case.</strong> A status, a kind, a report name or a page is its constant name &mdash; <code>NOT_RECORDED</code>, <code>SUMMARY</code>, <code>LEAKS</code>, <code>TENURING</code> &mdash; in the answers and in the arguments alike, so any value an answer gives can be passed straight back. Arguments are matched case-insensitively.</li>
        <li><strong>A heap object id is a decimal string</strong> (<code>"27908898928"</code>): HPROF ids are 64-bit addresses, beyond what a JSON number carries exactly.</li>
        <li><strong>&ldquo;Nothing here&rdquo; is a status, not an error.</strong> A question with no data &mdash; an event type never recorded, a report not computed yet, a profile with no traces &mdash; comes back as the same record with a <code>status</code> (<code>NOT_RECORDED</code>, <code>NOT_RUN_YET</code>, <code>NOT_COMPUTED</code>, <code>NO_TRACES</code>, &hellip;), a <code>reason</code> in words, and empty lists. An id that is well-formed but unknown &mdash; an object, an operation, a trace, an event type the profile never recorded &mdash; is a tool error naming it, to be corrected rather than read as an answer.</li>
        <li><strong>Paging is a cursor.</strong> A list that can continue returns <code>hasMore</code> and <code>nextCursor</code>; pass <code>nextCursor</code> back as <code>cursor</code> with the same arguments. The cursor is opaque and bound to the filters it came from. A capped top-N instead keeps its <code>limit</code> and reports what it left out (<code>omitted&hellip;</code>), keeping the rows that matter &mdash; the latest for a time series or a list of dumps, the worst for a ranking.</li>
        <li><strong><code>followUp</code> says where to go next.</strong> <code>followUp.nextTools</code> are calls ready to send &mdash; <code>{"tool", "arguments", "why"}</code>, with this answer&rsquo;s ids, windows and cursors already filled in &mdash; and <code>followUp.guidance</code> is advice that is not a call. A call to a family this installation does not advertise is left out, a call naming an event type names one the profile recorded, and a slow or rebuilding call (a retry, <code>compute</code>) is offered only to recover from a failure or a missing prerequisite, never to redo something that succeeded.</li>
        <li><strong><code>uiLink</code> is for the user.</strong> Every answer whose subject has a page in Microscope carries one &mdash; the same profile and view, with the same event type, filters, window, search and baseline where the page takes them. Hand it to the person you are reporting to; it carries nothing to analyse further. A row that names something with a page of its own &mdash; a trace, an operation, a notification&rsquo;s exemplar &mdash; carries its own <code>uiLink</code> too, so a trace named from a list opens without an export first. A <code>uiLinkNote</code> says what a link still cannot reproduce. See <a href="#links">Links Back to the UI</a>.</li>
        <li><strong>A Markdown answer keeps a footer.</strong> Because a client may pass only the text to the model, a Markdown answer ends with <code>Open in Microscope: &lt;uiLink&gt;</code> and a <code>Next:</code> list of the same calls as <code>tool {arguments}</code>. The body is capped first, so the footer always survives.</li>
      </ul>

      <p id="hints"><strong>Every tool says what a call costs.</strong> A tool&rsquo;s <code>_meta</code> in <code>tools/list</code> carries <code>jeffrey/cost</code> &mdash; <code>CHEAP</code> (an indexed lookup, a cached report, one bounded call to a hub or the IDE), <code>MODERATE</code> (a scan of one event type, a flamegraph at the default threshold), <code>EXPENSIVE</code> (caller-written SQL, a walk of the object graph, the same work over two profiles), or <code>SLOW</code> for the seven that can hand back an operation or a task &mdash; and, where it applies, <code>jeffrey/requires</code>: what must already be in place, such as <code>HEAP_DUMP_INDEXED</code>, <code>HEAP_REPORTS</code>, <code>AUTO_ANALYSIS</code>, <code>TRACES</code>, <code>HUB</code> or <code>IDE_LINKED</code>. The seven tools whose answers run long also carry <code>anthropic/maxResultSizeChars</code> (<a href="#result-size">above</a>). For example:</p>
      <DocsCodeBlock :code="metaExample" language="json" />

      <p id="trace-context"><strong>Your trace context is recorded.</strong> A client that sends a W3C <code>traceparent</code> (and <code>tracestate</code>) in a request&rsquo;s <code>params._meta</code> finds them verbatim as attributes of the span Jeffrey records for that tool call, beside Jeffrey&rsquo;s own ids &mdash; enough to find the call behind a step of your own trace. A malformed value is dropped, never an error.</p>

      <p><strong>Two documents are resources rather than tools.</strong> <code>jeffrey://profile/{profileId}/schema</code> is the whole profile database &mdash; every table and view, the <code>events</code> view included, with its columns, the note on the JSON <code>fields</code> column, and the event types with their counts; <code>jfr_listTables</code> and <code>jfr_describeTable</code> name it as <code>schemaResource</code>. <code>jeffrey://profile/{profileId}/findings</code> is every finding the profile already holds &mdash; the cached Auto Analysis, the container verdict and the capability gaps &mdash; with a <code>status</code> of <code>COMPUTED</code>, <code>NOT_COMPUTED</code> (offering <code>jvm_autoAnalysis</code> with <code>compute</code>) or <code>CANNOT_COMPUTE</code>. Reading a resource never starts work.</p>

      <p><strong>Nothing is exported to a file.</strong> Data comes back in the answer, bounded and saying what it left out; the whole thing, drawn, is the <code>uiLink</code>. No tool writes a result to disk for an agent to fetch.</p>

      <h2 id="family-map">Which Family Answers Your Question</h2>
      <p>Nineteen families is more than anyone reads through. They group into six questions, and the question you arrived with picks the family for you &mdash; the same taxonomy the <router-link to="/docs/microscope-mcp/skills#analyze-jfr"><code>analyze-jfr</code></router-link> skill routes by, so the docs and the skill tell the same story. Every row links to its section below.</p>
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
            <td class="map-count">8</td>
            <td>Which recordings are analysed, what each one can answer, and a deep link into the UI. Every <code>profileId</code> comes from here.</td>
          </tr>
          <tr>
            <td><a href="#recordings"><code>recordings_</code></a></td>
            <td class="map-count">5</td>
            <td>A recording Jeffrey has never seen, as a file on this machine. Creates a profile rather than reading one; an installation withholds it by leaving <code>recordings</code> out of <code>families</code>.</td>
          </tr>
          <tr>
            <td><a href="#hubs"><code>hubs_</code></a></td>
            <td class="map-count">5</td>
            <td>The recordings that never reached this machine &mdash; what a deployed application sent to a connected Jeffrey Hub. Finds a session and pulls it in; <code>recordings_</code> then turns it into a profile. Also lists the files a session holds beside its recording and fetches one of them on its own.</td>
          </tr>
          <tr>
            <td><a href="#operations"><code>operations_</code></a></td>
            <td class="map-count">2</td>
            <td>Status and cancellation for background imports, analysis, Hub downloads and heap preparation.</td>
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
            <td class="map-count">4</td>
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

      <p id="input-schemas"><strong>Input schemas say what they accept.</strong> Every tool&rsquo;s <code>inputSchema</code> is closed with <code>"additionalProperties": false</code>, and the server enforces it: an argument the tool does not take &mdash; almost always a misspelling &mdash; comes back as a tool error naming the ones it does, <code>Unknown argument 'limt'; this tool accepts: profileId, limit, cursor</code>, rather than being dropped while the call succeeds with the default. A numeric argument declares its <code>default</code>, <code>minimum</code> and <code>maximum</code>; the numbers in parentheses in the tables below are those values. The bounds describe one clamp convention every tool follows rather than a gate: an omitted, zero or negative <code>limit</code> (or <code>top</code>, <code>topN</code>, <code>maxPaths</code>) takes the default, and one above the maximum takes the maximum. An argument with a fixed set of values carries it as an <code>enum</code> of upper-case constant names &mdash; the notification severities, the <code>jvm_configuration</code> sections, the <code>heap_prepare</code> reports &mdash; matched case-insensitively, and a blank string counts as leaving it out; <code>profiles_viewLink</code>&rsquo;s <code>view</code> is the one enum spelled as the page&rsquo;s path, which its answer echoes back. A list is a JSON array: <code>hubs_download</code>&rsquo;s <code>fileIds</code> is <code>["c2", "log"]</code>, and a comma-separated string is still read for a client that learned it that way.</p>

      <p><strong>Evidence snapshots.</strong> <code>profiles_evidence(profileId, limit?)</code>, also available at <code>jeffrey://profile/{profileId}/evidence</code>, exports the current profile and recording identity, filters, units, denominators, existing findings, sampling evidence and capability gaps. The snapshot is versioned and explicitly reports omitted rows. Save the response to preserve that evidence: reading the URI again reflects the current profile state.</p>

      <p><strong>Comparison quality.</strong> <code>compare_quality(profileId, baselineProfileId)</code> reports duration, event overlap, available sampling settings and loss telemetry before interpreting a difference. It returns evidence, not a single comparability verdict. Persisted settings are a merged snapshot, so matching settings do not prove they stayed constant. Observed HTTP/gRPC events are not automatically a complete request count; per-operation normalization remains unavailable without a defensible denominator. That still permits comparing observed hotspot shares or qualified movements per recording duration. State the denominator and limitations; those movements alone do not prove that each request became faster or slower.</p>

      <p><strong>Runtime diagnostics.</strong> Read <code>jeffrey://diagnostics</code> for build and family information, profile readiness counts, bounded Hub reachability and aggregate tool latency/output-size measurements. The resource does not retain tool arguments or result contents and respects the Hub access switch.</p>

      <h2 id="profiles">profiles_ &mdash; the catalogue</h2>
      <p>Start here. <code>profiles_list</code> discovers the profile IDs used by analysis tools.</p>
      <p>Pass <code>nextCursor</code> back as <code>cursor</code> while <code>hasMore</code> is true. Keep the same search; the page size may change. Profiles are ordered by their stable IDs. These are live pages: profiles added before your cursor appear on a fresh traversal. <code>recordingId</code> links a profile to <code>recordings_status</code>. Long display names are shortened explicitly; profile IDs remain intact.</p>
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
            <td><code>search?</code>, <code>limit?</code> (100, max 1000), <code>cursor?</code></td>
            <td>A page of profiles, including profile and recording IDs, readiness, total matches and continuation metadata</td>
          </tr>
          <tr>
            <td><code>profiles_summary</code></td>
            <td><code>profileId</code></td>
            <td>What one profile is, what it can answer, every event type it recorded, the Auto Analysis state (<code>COMPUTED</code>, <code>NOT_COMPUTED</code> or <code>CANNOT_COMPUTE</code>), the rules that flagged something (as <a href="#findings">findings</a>) and <code>capabilityGaps</code> &mdash; in words, every question this recording cannot answer, which tools that leaves empty, and what would close each gap next time. It carries the id, name, event source and recording window, and everything <code>profiles_features</code> reports; the sampler&rsquo;s loss figures stay with <code>profiles_samplerHealth</code>, and the source commit and size on disk with <code>profiles_get</code>. Read the gaps before believing any negative result</td>
          </tr>
          <tr>
            <td><code>profiles_get</code></td>
            <td><code>profileId</code></td>
            <td>Identity, the recording window it covers, its size, and the source commit the profiled build came from (<code>recordingCommit</code>, <code>null</code> when the recording carries no commit tag). A timestamp the recording did not carry is <code>null</code>, as is the duration when either end is unknown</td>
          </tr>
          <tr>
            <td><code>profiles_features</code></td>
            <td><code>profileId</code></td>
            <td>Which analysis features this profile has the data for, every event type recorded with sample counts, and the same <code>capabilityGaps</code> list the summary carries</td>
          </tr>
          <tr>
            <td><code>profiles_link</code></td>
            <td><code>profileId</code></td>
            <td>The <code>uiLink</code> that opens the profile in the Microscope UI</td>
          </tr>
          <tr>
            <td><code>profiles_samplerHealth</code></td>
            <td><code>profileId</code></td>
            <td>Captured versus dropped CPU-time samples &mdash; whether the figures every other tool reports can be trusted</td>
          </tr>
          <tr>
            <td><code>profiles_viewLink</code></td>
            <td><code>profileId</code>, <code>view</code> (enum), <code>objectId?</code></td>
            <td>The <code>uiLink</code> of one named view &mdash; the GC, thread, JIT, memory and heap-dump pages. The schema enumerates the views, every one a route the UI serves; an unknown <code>view</code> is refused with the list of valid ones. <code>objectId</code> (a decimal string) preselects the object on <code>heap-dump/gc-root-path</code></td>
          </tr>
          <tr>
            <td><code>profiles_evidence</code></td>
            <td><code>profileId</code>, <code>limit?</code> (100, max 500)</td>
            <td>A versioned evidence snapshot of the profile as it stands &mdash; recording identity and build, whole-recording units and denominators, the sampling settings it was recorded with, the findings as evaluated and the capability gaps, with omission counts for every bounded collection. Returned inline as one JSON document; save it to keep the observation. Also served as the resource <code>jeffrey://profile/{profileId}/evidence</code></td>
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
            <td><code>profileId</code>, <code>eventType</code>, <code>detail?</code> (<code>SUMMARY</code>, <code>STANDARD</code>, <code>FULL</code>; <code>STANDARD</code>), <code>thresholdPct?</code> (0-100, configured default 2.0), <code>startEpochMs?</code>, <code>endEpochMs?</code>, <code>threadMode?</code>, <code>useWeight?</code>, <code>search?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The call tree as Markdown, with the reading preamble for that event type. <code>detail</code> picks how much: <code>SUMMARY</code> is no tree at all but the top 25 frames by self (summed across every path they were called from) and the top 10 stacks written leaf first &mdash; a few kilobytes, the cheapest first look; <code>STANDARD</code> is the tree at the configured threshold; <code>FULL</code> is the tree at 0.5%, several times longer &mdash; on a large profile long enough to pass the 120,000-character cap and come back truncated, where <code>STANDARD</code> or a coarser <code>thresholdPct</code> fits. An explicit <code>thresholdPct</code> overrides the threshold either tree level implies. <code>search</code> marks every frame whose name matches it &mdash; as a regular expression, the way the UI&rsquo;s flamegraph search reads it, or as a literal substring &mdash; with <code>&laquo;match&raquo;</code>, adds <code>search_pattern</code> and <code>search_matches</code> (samples under the matches and their share) to the header, and keeps every frame on a path to a match even below <code>thresholdPct</code>; it does not filter samples. The record beside the Markdown names the event type, window, threshold and filters it was built with, <code>markdownChars</code> and <code>truncated</code>, and a <code>uiLink</code> to the same graph</td>
          </tr>
        </tbody>
      </table>

      <p>Common starting points: <code>jdk.ExecutionSample</code> for on-CPU time, <code>jdk.ObjectAllocationSample</code> for allocation (with <code>useWeight</code> to rank by bytes rather than call count), <code>jdk.JavaMonitorEnter</code> for lock contention (weight is nanoseconds blocked), <code>profiler.WallClockSample</code> for latency including off-CPU &mdash; async-profiler&rsquo;s event, so unlike its neighbours it carries no <code>jdk.</code> prefix. <code>thresholdPct</code> controls how much survives pruning &mdash; raise it for an overview, lower it to chase one path. Start with <code>detail: "SUMMARY"</code> when you do not yet know which path matters, and ask for the tree once you do.</p>

      <p><strong>A frame may carry a source line</strong>, as <code>Class.method:214</code>, and it is printed only when every sample at that frame reported the same one. Nodes in the tree are keyed by method name, so one node stands for every sample of that method at that point &mdash; a method called from three places, or a hot loop sampled across its own body, all merge there. Printing whichever line arrived first would hand a reader one call site out of several with nothing saying so, and a plausible wrong line is worse than none for somebody whose next act is to open the file. So a frame without a line was sampled at more than one, or carries no line information at all, as native and inlined frames do: <strong>its absence is not the absence of a location</strong>. There is never a file name &mdash; for that, ask the reader&rsquo;s IDE through <a href="#ide"><code>ide_resolve</code></a>.</p>

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
            <td>Both recordings&rsquo; span and length (<code>durationMs</code>, <code>null</code> when a side carries no span), the event types they have in common with each side&rsquo;s totals, the types only one of them recorded, and the notes that decide whether the pair is comparable at all; status <code>EMPTY</code> when they share nothing to compare</td>
          </tr>
          <tr>
            <td><code>compare_movements</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code>, <code>eventType</code>, <code>limit?</code> (15, max 100), <code>startEpochMs?</code>, <code>endEpochMs?</code>, <code>useWeight?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The methods that grew and the ones that shrank, ranked by how much work moved with them, as Markdown</td>
          </tr>
          <tr>
            <td><code>compare_flamegraph</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code>, <code>eventType</code>, <code>thresholdPct?</code> (0-100), <code>startEpochMs?</code>, <code>endEpochMs?</code>, <code>useWeight?</code>, <code>excludeIdle?</code>, <code>excludeNonJava?</code></td>
            <td>The differential call tree as Markdown, every frame carrying both sides and the movement between them</td>
          </tr>
          <tr>
            <td><code>compare_quality</code></td>
            <td><code>profileId</code>, <code>baselineProfileId</code></td>
            <td>Whether the pair&rsquo;s evidence supports a verdict at all: both identities and durations, event overlap, differences in the stored sampling settings, the CPU-time samples each side lost, and whether per-workload normalisation is available. Call it after <code>compare_list</code> and before quoting any delta</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="warning" title="compare_list is not a warm-up call">
        Any two recordings can be subtracted, and the result always looks like a finding. Whether it <em>is</em> one depends on facts the deltas do not show &mdash; comparable recording length, comparable volume, the same profiler settings &mdash; and nothing inside a JFR file proves them. <code>compare_list</code> is the step that decides whether the rest means anything, and &ldquo;these two runs are not comparable&rdquo; is a real result.
      </DocsCallout>

      <p><strong>A window is on the primary&rsquo;s clock.</strong> <code>startEpochMs</code>/<code>endEpochMs</code> are instants in the primary recording; the same offsets from the start are applied to the baseline, since one instant cannot fall in two recordings made at different times. The answer&rsquo;s <code>window</code> reports where it landed on each side, and a window reaching past the shorter recording is refused naming both spans.</p>

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
            <td><code>profileId</code>, <code>search?</code>, <code>errorsOnly?</code>, <code>sort?</code> (<code>TOTAL_TIME</code>), <code>limit?</code> (50, max 1000), <code>cursor?</code></td>
            <td>One row per operation with call count, latency percentiles, errors and notification counts, and a <code>uiLink</code> of its own that opens that operation; <code>sort</code> also accepts <code>NOTIFICATIONS</code>. <code>totalMatching</code>, <code>hasMore</code> and <code>nextCursor</code> continue the list; status <code>NO_TRACES</code> for a profile without traces, <code>NO_MATCH</code> when the filter matched nothing</td>
          </tr>
          <tr>
            <td><code>traces_operationExport</code></td>
            <td><code>profileId</code>, <code>name</code>, <code>kind</code>, <code>eventType</code></td>
            <td>One operation as Markdown: percentiles, where the time goes, and the reading preamble. Declares its result size to the host (<a href="#result-size">above</a>)</td>
          </tr>
          <tr>
            <td><code>traces_slowestTraces</code></td>
            <td><code>profileId</code>, <code>name</code>, <code>kind</code>, <code>eventType</code>, <code>limit?</code> (20, max 1000)</td>
            <td>Individual traces, slowest first &mdash; ranked over every trace of the operation, not over its first ones &mdash; with their ids, <code>startEpochMs</code>, <code>durationNanos</code> and each trace&rsquo;s own <code>uiLink</code> to its span waterfall. An operation the profile does not have is an error naming it</td>
          </tr>
          <tr>
            <td><code>traces_traceExport</code></td>
            <td><code>profileId</code>, <code>traceId</code></td>
            <td>One trace as Markdown: the span tree with self time. Declares its result size to the host</td>
          </tr>
          <tr>
            <td><code>traces_spanFlamegraphExport</code></td>
            <td><code>profileId</code>, <code>traceId</code>, <code>spanId</code>, <code>eventType</code>, <code>selfOnly?</code>, <code>threadMode?</code>, <code>useWeight?</code></td>
            <td>A flamegraph of the samples taken while one span was open, pruned at the configured 2%; status <code>NO_SELF_TIME</code> when the span has no time of its own to graph, and an unknown trace or span is an error. Its link opens the trace&rsquo;s waterfall, where the span&rsquo;s graph is one click away (<code>uiLinkNote</code>). Declares its result size to the host</td>
          </tr>
          <tr>
            <td><code>traces_attributeKeys</code></td>
            <td><code>profileId</code>, <code>eventType?</code></td>
            <td>The attribute keys the traces carried, each identified by its (source, owner, key) triple</td>
          </tr>
          <tr>
            <td><code>traces_attributeValues</code></td>
            <td><code>profileId</code>, <code>key</code>, <code>source?</code>, <code>owner?</code>, <code>eventType?</code>, <code>sort?</code>, <code>limit?</code> (25, max 200)</td>
            <td>One key split into its values, each with its own p50, p95, max and error count</td>
          </tr>
          <tr>
            <td><code>traces_attributeSearch</code></td>
            <td><code>profileId</code>, <code>key</code>, <code>operator?</code> (<code>EQ</code>), <code>value?</code>, <code>source?</code>, <code>owner?</code>, <code>scope?</code>, <code>limit?</code> (25, max 200), <code>cursor?</code></td>
            <td>The individual traces carrying one value, with their ids and each trace&rsquo;s own <code>uiLink</code>, <code>stats</code> over every match, and <code>hasMore</code>/<code>nextCursor</code>; status <code>NO_MATCH</code> when nothing carried it. Its <code>uiLink</code> opens the attribute search on the same condition and scope</td>
          </tr>
          <tr>
            <td><code>traces_operationFlamegraphExport</code></td>
            <td><code>profileId</code>, <code>name</code>, <code>kind</code>, <code>eventType</code>, <code>graphEventType</code>, <code>threadMode?</code>, <code>useWeight?</code></td>
            <td>The same, aggregated over every trace of one operation; its link opens the operation&rsquo;s Flamegraphs tab (<code>uiLinkNote</code>)</td>
          </tr>
          <tr>
            <td><code>traces_notifications</code></td>
            <td><code>profileId</code>, <code>severity?</code> (<code>CRITICAL</code>, <code>HIGH</code>, <code>MEDIUM</code>, <code>LOW</code>), <code>type?</code>, <code>category?</code>, <code>source?</code>, <code>search?</code>, <code>name?</code>, <code>kind?</code>, <code>eventType?</code>, <code>limit?</code> (50, max 1000)</td>
            <td>What the application reported about itself while traces ran &mdash; every <code>jeffrey.Notification</code> raised inside a trace, grouped by kind, the most severe first, each with its count, how many traces raised it, <code>firstEpochMs</code>/<code>lastEpochMs</code>, and <code>exemplarTraces</code> &mdash; each a trace id for <code>traces_traceExport</code> with a <code>uiLink</code> to its waterfall &mdash; with <code>omittedGroups</code> past the limit; status <code>NO_TRACES</code>, <code>NO_NOTIFICATIONS</code> or <code>NO_MATCH</code> otherwise. The operation triple, given whole or not at all, narrows to one operation</td>
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
            <td>Jeffrey&rsquo;s rule set over the recording, as <a href="#findings">findings</a>: every rule that reached a verdict, the passes included, plus <code>findingCounts</code> by severity and <code>notEvaluated</code> &mdash; the rules that had no events to run on, which did not pass. Cached, with a <code>status</code> of <code>COMPUTED</code>, <code>NOT_COMPUTED</code> or <code>CANNOT_COMPUTE</code> (the recording is gone, or the profile is not a JFR recording). <code>compute</code> runs it when nothing has, which reads the whole recording and is slow &mdash; the call waits up to forty-five seconds, then answers <code>NOT_COMPUTED</code> with an <code>operationId</code> and the <code>operation</code> for <code>operations_status</code>, whose result carries the findings &mdash; or, for a client that declared the MCP tasks extension, a <a href="#tasks">task</a> after about five seconds. Not read-only for that reason, and safe to repeat: a second call joins the run in flight</td>
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
            <td>cgroup limits (CPU quota and period in nanoseconds), and whether the scheduler throttled the process, with the verdict and its counters; with a verdict the link opens the CPU-throttling page</td>
          </tr>
          <tr>
            <td><code>jvm_configuration</code></td>
            <td><code>profileId</code>, <code>section?</code> (enum)</td>
            <td>What the JVM was started with, in the UI&rsquo;s own tabs; without a section, the section names. The schema enumerates the eleven the JDK records as constant names, from <code>JVM_INFORMATION</code> to <code>VIRTUALIZATION_INFORMATION</code></td>
          </tr>
          <tr>
            <td><code>jvm_flags</code></td>
            <td><code>profileId</code></td>
            <td>The flag list grouped by <strong>origin</strong> &mdash; a default, the command line, or the JVM&rsquo;s own ergonomics</td>
          </tr>
          <tr>
            <td><code>jvm_gcDetail</code></td>
            <td><code>profileId</code>, <code>page?</code></td>
            <td>The GC pages beneath the overview, one at a time: <code>TENURING</code>, <code>IHOP</code>, <code>G1</code>, <code>ZGC</code>, <code>STRING_TABLES</code>, <code>FINALIZERS</code>, <code>REFERENCES</code>, <code>PHASES</code>, <code>PLAB</code>, <code>CONFIGURATION</code>. Omit <code>page</code> for the list. The chart series are left out; a table longer than 25 rows keeps the most recent or the worst, says which, and counts the rest in <code>omittedRows</code></td>
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
            <td>The dumps together: recurring deadlocks (first dump, occurrences, <code>lastSeenEpochMs</code>), monitors threads queued on, threads stuck across consecutive dumps (longest-stuck first), and the most frequent frames. A cut keeps the latest dumps and says how many rows it left out in <code>omittedRows</code>; status <code>NO_THREAD_DUMPS</code> when the recording has none</td>
          </tr>
          <tr>
            <td><code>jvm_threadDump</code></td>
            <td><code>profileId</code>, <code>index</code>, <code>state?</code> (<code>RUNNABLE</code>, <code>BLOCKED</code>, <code>WAITING</code>, <code>TIMED_WAITING</code>, <code>NEW</code>, <code>TERMINATED</code>, <code>UNKNOWN</code>), <code>limit?</code> (50, max 500)</td>
            <td>One dump: its threads with their state and stack, and its deadlocks. <code>state</code> narrows to one thread state; <code>capturedAtEpochMs</code> dates it, <code>totalThreads</code> and <code>matchingThreads</code> count the dump, and <code>omittedThreads</code> says how many matching threads the limit left out; status <code>NO_SUCH_DUMP</code> for an index past the last dump</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="Every result says what it cannot answer">
        Each dashboard comes back with a <code>followUp</code> &mdash; the same idea as the reading instructions a flamegraph or trace export opens with. <code>jvm_gc</code> says that no event in it names the code that produced the garbage and offers the allocation flamegraph of the type the profile recorded; <code>jvm_container</code> points back at per-thread CPU load; <code>jvm_configuration</code> says to prefer these values over a deployment manifest. Every tool carries it: <code>nextTools</code> for the calls, with their arguments filled in, and <code>guidance</code> for the rest. They route and never diagnose: no threshold decides whether they appear, and none of them claims the figures beside them are bad. A call that routes to a family this installation does not advertise &mdash; trimmed by <code>families</code>, a <code>preset</code> or the hub and IDE switches &mdash; is left out, and so is that family&rsquo;s paragraph in the instructions sent at <code>server/discover</code>.
      </DocsCallout>

      <DocsCallout type="info" title="Call jvm_sections first">
        A recording holds only what the profiler was told to capture. Every section reports whether this profile carries its events, and a section asked for anyway answers with status <code>NOT_RECORDED</code> and a <code>reason</code> naming the events it needed &mdash; a dashboard rendered from events that were never recorded is a page of zeroes, which reads like a finding rather than like an absence.
      </DocsCallout>

      <DocsCallout type="info" title="Auto analysis is read from a cache, not computed here">
        Generating it loads the whole recording through the JMC toolkit a second time, which is bounded neither in time nor in memory by anything the server controls &mdash; a poor trade inside a tool whose point is being cheap. So Jeffrey computes it when the recording is imported, as part of the same warm-up that builds the thread bands, and caches it: by the time a client asks, the cache is normally already warm. A profile that missed that &mdash; imported before Jeffrey warmed it &mdash; answers <code>NOT_COMPUTED</code>, with the <code>compute: true</code> call in <code>followUp.nextTools</code>, and that call runs it on the spot; one whose recording file has since gone answers <code>CANNOT_COMPUTE</code>. A call arriving while a run is already in flight joins that run rather than starting a second one. Either way the other sections still answer.
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
        These managers answer an event type that was never recorded with a well-formed empty result &mdash; zero statements, a perfect success rate. Every tool here checks first and answers status <code>NOT_RECORDED</code> with a <code>reason</code> instead, because &ldquo;the profiler did not capture this&rdquo; is a finding about the recording, not a clean bill of health for the database.
      </DocsCallout>

      <p><strong>Both directions.</strong> <code>http_</code> and <code>grpc_</code> take a <code>direction</code> of <code>SERVER</code> (the default) or <code>CLIENT</code>, and they are different questions: SERVER is what the application was asked to do, CLIENT what it asked of somebody else, where a slow figure belongs to a dependency and the only local fixes are to call less often or stop waiting. The two are gated separately, so &ldquo;no client-side data&rdquo; means the recording captured no outbound calls.</p>

      <p><strong>No chart series.</strong> The per-second series that draw the dashboard&rsquo;s graphs are left out of every answer &mdash; thousands of points describing a shape the percentiles already summarise. The shape is what the UI link is for. SQL text is truncated for the same reason: it is there to identify a statement, not to be executed. Durations are in nanoseconds (<code>&hellip;Nanos</code>), instants in epoch milliseconds (<code>atEpochMs</code>), and a success rate is a number from 0 to 1. A ranked list keeps its worst rows and counts the rest in its <code>omitted&hellip;</code> field; <code>methodtracing_timing</code> keeps the 100 costliest methods.</p>

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

      <p>These event types are threshold-gated, so a recording can hold none of them because nothing blocked for long enough as well as because the profiler was never asked. The tools answer status <code>NOT_RECORDED</code> with a <code>reason</code> saying which of the two it is, rather than returning a zero that reads like health.</p>

      <p><strong>Examples.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exWaiting" language="json" />

      <h2 id="timeline">timeline_ &mdash; when, not where</h2>
      <p><code>flamegraph_export</code>, <code>compare_movements</code> and <code>compare_flamegraph</code> all accept <code>startEpochMs</code> and <code>endEpochMs</code>, and nothing else in the surface helps you choose them. A flamegraph of a whole recording flattens a thirty-second spike into a five-minute average, and the spike stops being visible.</p>
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
            <td><code>profileId</code>, <code>eventType</code>, <code>useWeight?</code>, <code>top?</code> (5, max 25)</td>
            <td>The recording bucketed, the busiest windows ranked with the <code>startEpochMs</code>/<code>endEpochMs</code> to pass on (the export of the busiest one is already in <code>followUp.nextTools</code>), and a one-line shape; status <code>NOT_RECORDED</code> or <code>NO_TIMESERIES</code> when there is nothing to bucket</td>
          </tr>
          <tr>
            <td><code>timeline_zoom</code></td>
            <td><code>profileId</code>, <code>eventType</code>, <code>startEpochMs</code>, <code>endEpochMs</code>, <code>bucketMs?</code> (20, min 1)</td>
            <td>The same at sub-second resolution inside one window &mdash; the only view that resolves below a second. Its link opens the sub-second view of the same event type</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The reduction is the product, not the series">
        The managers behind these return chart geometry &mdash; three hundred points for a five-minute recording at one-second resolution, thirty thousand at ten milliseconds. A curve is not something a model can act on, which is why the dashboards drop their series entirely. What comes back instead is the ranked windows, each with the <code>startEpochMs</code> and <code>endEpochMs</code> the next tool takes, and a coarse shape line so a steady load, a ramp, a sawtooth and a single burst are told apart at a glance.
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

      <p><strong>Leak candidates come from <code>jdk.OldObjectSample</code></strong>, which is off in most recordings. Their absence says nothing about whether the application leaks, and the tool answers status <code>NOT_RECORDED</code> saying exactly that rather than reporting zero candidates &mdash; the difference between &ldquo;measured and found nothing&rdquo; and &ldquo;never measured&rdquo; is the whole finding.</p>

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
            <td>The queryable tables and views (<code>view: true</code> marks <code>events</code>), and <code>schemaResource</code>, the URI of the whole schema document</td>
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
            <td><code>profileId</code>, <code>eventType</code>, <code>limit?</code> (100, max 1000), <code>whereClause?</code></td>
            <td>Events of one type, newest first &mdash; the common case, without writing SQL. An event type the profile never recorded is an error; a known one answers status <code>OK</code>, <code>NO_EVENTS</code> or <code>NO_MATCH</code>, with typed <code>columns</code> and <code>rows</code> and a <code>truncation</code> of <code>COMPLETE</code> or <code>ROW_LIMIT</code></td>
          </tr>
          <tr>
            <td><code>jfr_executeQuery</code></td>
            <td><code>profileId</code>, <code>query</code></td>
            <td>An arbitrary read-only query (<code>SELECT</code> / <code>WITH</code> only), one statement per call, capped at 1,000 rows and 30 seconds. Rows come back as JSON &mdash; <code>columns</code>, <code>rows</code> (every cell a string, SQL <code>NULL</code> as <code>null</code>), <code>returned</code>, <code>rowCap</code> and <code>truncation</code> (<code>COMPLETE</code>, <code>ROW_LIMIT</code> or <code>OUTPUT_SIZE_LIMIT</code>)</td>
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
        <code>jfr_listTables</code> lists both, with <code>events</code> marked as a view &mdash; query <code>events</code>. It is a view over <code>events_raw</code> that splices back the one large string field the parser pools out of each row; querying <code>events_raw</code> silently returns truncated JSON in <code>fields</code>, with no error to warn you. The bundled <code>jfr-sql</code> skill carries this and the rest of the schema.
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
            <td><code>profileId</code>, <code>report?</code>, <code>retry?</code></td>
            <td>Starts the index, the dominator tree and the cached reports, and returns straight away with the stage list and an <code>operationId</code> for <code>operations_status</code> &mdash; or a <a href="#tasks">task</a>, at once, for a client that declared the tasks extension. A build that failed or was cancelled is reported as it stands until <code>retry</code> is true. Pass a report name &mdash; <code>LEAKS</code>, <code>BIGGEST</code>, <code>CLASSLOADERS</code>, <code>CONSUMERS</code>, <code>STRINGS</code>, <code>COLLECTIONS</code>, <code>DOMINATOR</code>, <code>THREADS</code>, <code>BIGGEST_COLLECTIONS</code>, <code>DUPLICATES</code> &mdash; to compute one on a dump that is already indexed. A finished build is never rebuilt by a next call: the answer&rsquo;s <code>followUp</code> offers <code>retry</code> only after a failure</td>
          </tr>
          <tr>
            <td><code>heap_status</code></td>
            <td><code>profileId</code></td>
            <td>How far that has got: a <code>state</code> of <code>IDLE</code>, <code>RUNNING</code>, <code>COMPLETED</code> or <code>FAILED</code>, and every stage with the report it computes, its status and <code>durationMs</code>. Poll this rather than retrying the report tool, which cannot tell &ldquo;still building&rdquo; from &ldquo;never asked for&rdquo;</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The pair that builds the index, and what it writes is a cache">
        <code>heap_prepare</code> runs the same pipeline as the <strong>Initialize</strong> button in the UI, on the same registry &mdash; a run started from a session is visible in the browser and the other way round, and a second request joins the one in flight rather than racing it. It returns immediately because a dominator build over a multi-gigabyte heap takes minutes, which is well past what a client waits for a tool call. Completed work is reused when it covers the requested reports. Asking for another report, or for all reports after preparing only one, starts new work. Failed or cancelled attempts require <code>retry: true</code>. No dump is altered and nothing is deleted.
      </DocsCallout>

      <p><strong>Reports</strong> &mdash; cached, and faster and safer than reproducing them in SQL. A report nothing has computed answers status <code>NOT_RUN_YET</code>, with the <code>heap_prepare</code> call that computes it in <code>followUp.nextTools</code>. Each keeps the largest rows and counts the rest in an <code>omitted&hellip;</code> field:</p>
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
            <td><code>profileId</code>, <code>baselineProfileId</code>, <code>topN?</code> (30, max 200)</td>
            <td>This dump against an earlier one, class by class, ranked by growth (default 30, maximum 200), with <code>primaryDump</code> and <code>baselineDump</code> describing the two. A side that is not a heap dump or not indexed is a status &mdash; <code>NO_HEAP_DUMP</code>, <code>NOT_INDEXED</code>, <code>BASELINE_NO_HEAP_DUMP</code>, <code>BASELINE_NOT_INDEXED</code> &mdash; naming what to do</td>
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
            <td>Memory grouped by (package, class loader), ranked by shallow size</td>
          </tr>
          <tr>
            <td><code>heap_getStringAnalysis</code></td>
            <td><code>profileId</code></td>
            <td>Duplicate and oversized strings; <code>contentTruncated</code> marks a value the engine shortened</td>
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
            <td>HPROF version, id size, compressed oops, parser warnings and <code>parsedAtEpochMs</code>, under <code>metadata</code>; status <code>NO_METADATA</code> when the index holds none</td>
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
            <td><code>profileId</code>, <code>className</code>, <code>limit?</code> (20, max 50), <code>cursor?</code></td>
            <td>A page of instances of one class, each with its <code>objectId</code>, and <code>hasMore</code>/<code>nextCursor</code></td>
          </tr>
          <tr>
            <td><code>heap_getInstanceDetail</code></td>
            <td><code>profileId</code>, <code>objectId</code></td>
            <td>One object&rsquo;s fields and their values. An id the dump does not hold is an error naming it, here and on every tool that takes an <code>objectId</code></td>
          </tr>
          <tr>
            <td><code>heap_getDominatorTreeRoots</code></td>
            <td><code>profileId</code>, <code>limit?</code> (50, max 50)</td>
            <td>The objects with the largest retained size. Declares its result size to the host</td>
          </tr>
          <tr>
            <td><code>heap_getDominatorTreeChildren</code></td>
            <td><code>profileId</code>, <code>objectId</code>, <code>limit?</code> (20, max 50), <code>cursor?</code></td>
            <td>What one object retains, with <code>hasMore</code>/<code>nextCursor</code> for the next page</td>
          </tr>
          <tr>
            <td><code>heap_getPathToGCRoot</code></td>
            <td><code>profileId</code>, <code>objectId</code>, <code>maxPaths?</code> (3, max 5)</td>
            <td>The shortest chains from a GC root &mdash; why this object is still alive. Status <code>IS_GC_ROOT</code> (with <code>targetRootKind</code>) when the object is itself a root, <code>NO_PATH</code> when no chain was found within the search depth</td>
          </tr>
          <tr>
            <td><code>heap_getReferrers</code></td>
            <td><code>profileId</code>, <code>objectId</code>, <code>limit?</code> (20, max 50), <code>cursor?</code></td>
            <td>Incoming references, with <code>totalReferrers</code> and <code>hasMore</code>/<code>nextCursor</code></td>
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
            <td>A read-only query against the index, always capped at 100 rows whatever <code>LIMIT</code> it carries. Rows come back as JSON with SQL <code>NULL</code> as <code>null</code>, and <code>capped</code> says whether the query matched more rows than <code>rowCap</code></td>
          </tr>
          <tr>
            <td><code>heap_oql</code></td>
            <td><code>profileId</code>, <code>query</code>, <code>limit?</code> (50, max 100), <code>cursor?</code>, <code>includeRetainedSize?</code></td>
            <td>Jeffrey&rsquo;s OQL against the object graph: <code>SELECT * FROM INSTANCEOF java.util.Map</code>, <code>SELECT AS RETAINED SET * FROM com.acme.Cache</code>, a filter over an object&rsquo;s own fields. Rows carry an <code>objectId</code> (a decimal string) the other tools take and a value cut at 1,000 characters, and <code>hasMore</code>/<code>nextCursor</code> continue the list. With <code>includeRetainedSize</code> the dominator tree is built first, so the call waits up to forty-five seconds and then answers status <code>RUNNING</code> with an <code>operationId</code> whose result carries the rows (a client that declared the tasks extension gets a <a href="#tasks">task</a> after about five) &mdash; which is why the tool is not marked read-only. Like the rest of the family it refuses a profile with no indexed heap dump</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="info" title="The dominator tree is built lazily">
        <code>dominator</code> and <code>retained_size</code> are empty until something builds them, so a SQL query joining <code>retained_size</code> on a fresh dump returns nulls rather than zeros. Build them first with <code>heap_prepare</code> and <code>report: "DOMINATOR"</code>, then watch <code>heap_status</code>; <code>heap_getDominatorTreeRoots</code> also triggers the build on a dump small enough to finish inside the call. The bundled <code>heap-sql</code> skill covers the whole schema.
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
            <td><code>limit?</code> (100, max 500), <code>cursor?</code></td>
            <td>Every connected hub with its address, whether it was declared in configuration or added through the UI, and whether it answers right now</td>
          </tr>
          <tr>
            <td><code>hubs_sessions</code></td>
            <td><code>hub?</code>, <code>workspace?</code>, <code>project?</code>, <code>withinLastMinutes?</code> (min 1), <code>status?</code>, <code>limit?</code> (50, max 500), <code>cursor?</code></td>
            <td>Recording sessions across <strong>every</strong> hub at once, newest first, each row carrying its <code>sessionRef</code>, <code>startedAtEpochMs</code>, <code>durationMs</code> (null while it still records), <code>sizeBytes</code>, and the <code>recordingId</code>/<code>profileId</code> of a local copy when there is one; the Markdown table beside it keeps the readable duration and size and a <code>local</code> column</td>
          </tr>
          <tr>
            <td><code>hubs_download</code></td>
            <td><code>sessionRef</code>, <code>retry?</code>, <code>startEpochMs?</code>, <code>endEpochMs?</code>, <code>fileIds?</code> (array)</td>
            <td>Pulls that session in and returns a <code>recordingId</code> for <code>recordings_analyzeRecording</code>. Alone, the whole session &mdash; every recording file it holds, fetched separately and kept separate, with its heap dumps and logs alongside. With <code>startEpochMs</code>/<code>endEpochMs</code> (either alone), only the chunks covering that window, every chunk whose span touches it, so the recording always covers the window with some slack at either end; with <code>fileIds</code> (an array of <code>fileId</code> values from <code>hubs_files</code>), the named files &mdash; whose chunks must be next to each other, because the recording reports one span across the files it holds and a skipped chunk leaves no hole to see; artifacts beside the run are free to name. A part of a session reports <code>coveredStartEpochMs</code>/<code>coveredEndEpochMs</code>, the span its chunks cover, and is a recording of its own, never answered from a whole-session copy already here. Only JFR chunks are recordings on a hub &mdash; a pprof or OTLP file lying in a session is neither downloadable here as a recording nor fetchable with <code>hubs_fetchFile</code> as an artifact. The <code>status</code> is <code>DOWNLOADED</code>, <code>RUNNING</code>, <code>NOT_DOWNLOADED</code>, <code>FAILED</code> or <code>CANCELLED</code>. A transfer that outlasts the call comes back <code>RUNNING</code> with an <code>operationId</code>; <code>operations_status</code> is where to follow it, and a client that declared the tasks extension gets a <a href="#tasks">task</a> after about five seconds instead. A client that declared form elicitation is first <a href="#window-question">asked which part</a> of a large session to bring</td>
          </tr>
          <tr>
            <td><code>hubs_files</code></td>
            <td><code>sessionRef</code>, <code>limit?</code> (100, max 1000), <code>cursor?</code></td>
            <td>Every file the session holds &mdash; the JFR chunks and, beside them, the application logs, the <code>gc.jvm-log</code>, the crash file, the perf-counters file, a heap dump &mdash; with its <code>fileId</code>, type, category, status, size and <code>createdAtEpochMs</code>, a <code>localPath</code>: the absolute path of an artifact already on this machine (fetched, or brought along by <code>hubs_download</code>) &mdash; the session&rsquo;s own <code>recordingId</code>/<code>profileId</code> say whether its chunks are already here &mdash; and a <code>fetch</code> value saying how the row is reached: <code>FETCH</code> takes its <code>fileId</code> to <code>hubs_fetchFile</code>, <code>DOWNLOAD</code> is a recording chunk for <code>hubs_download</code>, and <code>NEVER</code> is a type Jeffrey does not classify, which a hub will not serve one at a time. Every artifact is fetchable, including one of a session that is still recording: only the newest recording chunk is held open, and a log is worth grepping while it is being written. When the session is analysed, <code>profileId</code> and <code>profilingStartedAtEpochMs</code> name the profile and its zero point, the instant an uptime in a GC log counts from. <code>hasMore</code>/<code>nextCursor</code> continue the list</td>
          </tr>
          <tr>
            <td><code>hubs_fetchFile</code></td>
            <td><code>sessionRef</code>, <code>fileId</code></td>
            <td>Pulls one artifact down without the recording and returns (status <code>FETCHED</code>) the <strong>absolute <code>path</code></strong> it now has on the machine Jeffrey runs on &mdash; beside the profile (<code>profiles/&lt;id&gt;/artifacts/</code>) when the session is analysed, under <code>artifacts/&lt;hub&gt;/&lt;project&gt;/&lt;session&gt;/</code> otherwise. A file already at its path comes back as it is &mdash; including one fetched before the session was analysed, which is moved beside the profile rather than transferred again. A transfer that outlasts the call answers <code>RUNNING</code> with an <code>operationId</code>, or is a <a href="#tasks">task</a> after about five seconds for a client that declared the tasks extension; one that failed or was cancelled is started again by calling the tool again, with no retry flag &mdash; <code>operations_status</code> offers that call. Recording chunks are refused in favour of <code>hubs_download</code></td>
          </tr>
        </tbody>
      </table>

      <p><strong>A JVM writes more than it records.</strong> A session directory provisioned by Jeffrey holds the application&rsquo;s own log if the application was pointed at it, the unified-logging file (<code>gc.jvm-log</code>, rotated as <code>.0</code>, <code>.1</code>&hellip;), the perf-counters file, and &mdash; when the JVM died &mdash; <code>hs-jvm-err.log</code> (or the JVM&rsquo;s default <code>hs_err_pid*.log</code>), often with nothing else beside it because the first chunk never rolled. Those two tools are for that case: find out what the session holds, fetch the one file that matters, and read it. Jeffrey deliberately does not parse a log for you. The path it returns is on the machine Jeffrey runs on, which is the machine the agent runs on (the endpoint accepts loopback hosts only), and an agent&rsquo;s own <code>grep</code>, <code>sed</code> and file reader are better at a text file than anything a tool result could carry.</p>

      <p><strong>Ask for the interval, not the session.</strong> A session on a hub is a JVM&rsquo;s whole recording life &mdash; hours or days of chunks rolled every few minutes &mdash; and the question is almost never about all of it. The <code>started</code> and <code>duration</code> columns of <code>hubs_sessions</code> say what span there is; <code>hubs_download</code> with <code>startEpochMs</code>/<code>endEpochMs</code> brings the chunks covering the hour that matters and nothing else. A chunk&rsquo;s start is the timestamp in its own name, so chunk <em>n</em> covers everything up to the start of chunk <em>n+1</em>; every chunk whose span touches the window is brought, which is why the recording begins at or before the window and ends at or after it rather than leaving a gap. The answer&rsquo;s <code>coveredStartEpochMs</code>/<code>coveredEndEpochMs</code> are that covered span, and they are what the profile&rsquo;s figures are about. A narrow window is also the cheap way to look before pulling a wider one &mdash; does the session throw at all, does it record allocation samples &mdash; and <code>recordings_delete</code> is how the look is cleaned up afterwards. Figures measured in a window hold for that window; a rate must not be extrapolated across the session it was cut from.</p>

      <h3 id="window-question">Which Part of a Large Session</h3>
      <p>A client that declared <strong>form elicitation</strong> (<code>&quot;elicitation&quot;: {&quot;form&quot;: {}}</code> in its <code>clientCapabilities</code>) is asked before a large session crosses the network: <code>hubs_download</code> answers with an <router-link to="/docs/microscope-mcp/other-clients#input-requests">input request</router-link> instead of starting the transfer, and the client shows the user a form. Only then, and only when all of this holds:</p>
      <ul>
        <li>the call names the whole session &mdash; no <code>startEpochMs</code>, <code>endEpochMs</code> or <code>fileIds</code>;</li>
        <li>no attempt for the whole session is running or retained, and the whole session is not already here &mdash; those answer as they always did;</li>
        <li>the session runs longer than <code>jeffrey.microscope.mcp.hubs.ask-window-over-duration</code> (one hour by default), measured to its finish or, while it is still recording, to now &mdash; or holds more than <code>jeffrey.microscope.mcp.hubs.ask-window-over-size</code> (<code>1GB</code> by default). A session with no recorded start is judged by its size alone.</li>
      </ul>
      <p>Every other call downloads exactly as before, with no question: a smaller session comes down whole, and a client that did not declare form elicitation is never asked &mdash; answers such a client sends anyway are ignored. The two thresholds are on the <router-link to="/docs/microscope-mcp/enabling#turning-hub-access-off">Enabling the Server</router-link> page; <code>PT0S</code> or <code>0B</code> means always ask.</p>
      <p><strong>The question.</strong> The message names the session and its id, the hub and project, the span it covers (or &ldquo;to now&rdquo; while it is still recording), its length and its size. The form has one required choice and three fields that go with it, all with defaults:</p>
      <ul>
        <li><strong>Part of the session to download</strong> &mdash; <em>The last hour (HH:mm&ndash;HH:mm UTC)</em>, the default; <em>The last N minutes</em>; <em>The whole session</em>, with its size; <em>A custom window</em>;</li>
        <li><strong>Minutes</strong> &mdash; for the last N minutes: a whole number from 1 to the session&rsquo;s length in minutes, 15 by default (or the whole length, when shorter);</li>
        <li><strong>Start (UTC)</strong> and <strong>End (UTC)</strong> &mdash; for a custom window: date-times defaulting to the session&rsquo;s start and to its finish, or now while it is still recording.</li>
      </ul>
      <p>The last hour and the last N minutes are measured back from the session&rsquo;s end &mdash; when it finished, or now while it is still recording &mdash; and are downloaded as a window, exactly as if the call had passed those <code>startEpochMs</code>/<code>endEpochMs</code>: a recording of its own, tagged as a part. On a finished session a stretch that reaches back to its start is all of it, so it is downloaded as the whole session instead. A custom window with a bound left out takes the form&rsquo;s default for it.</p>
      <p><strong>The answers.</strong></p>
      <ul>
        <li><strong>A window or the whole session</strong> &mdash; the transfer starts from the session as the question read it, and from there it is the usual path: a client that declared the <a href="#tasks">tasks extension</a> waits about five seconds and then gets a task, any other waits up to forty-five and gets an <code>operationId</code>. The question always comes first; a task is only ever created for the transfer that follows the answer.</li>
        <li><strong>Decline or dismiss</strong> &mdash; nothing is transferred, and the call completes without an error: status <code>NOT_DOWNLOADED</code> with a <code>reason</code>, and <code>followUp.guidance</code> saying how to download without being asked (<code>startEpochMs</code> and <code>endEpochMs</code>, or <code>fileIds</code> from <code>hubs_files</code>) or to ask the user which part they need.</li>
        <li><strong>An answer that does not make a window</strong> &mdash; no choice, an unknown one, missing or non-whole or out-of-range minutes, a start or end that is not a date-time, an end before the start, a window outside the session &mdash; is asked again, with the problem stated at the start of the message. It never ends in an error.</li>
        <li><strong>A window no finished chunk covers</strong> &mdash; a gap in the session, or the part still being written &mdash; is asked again too, starting &ldquo;No finished chunk covers the window&rdquo;.</li>
      </ul>
      <p><strong>A session still recording.</strong> Its newest chunk is still being written and is never downloaded, so its last finished chunk ends where that open chunk begins &mdash; the same rule as everywhere else, chunk <em>n</em> covers up to the start of chunk <em>n+1</em>. A window lying only inside the chunk still being written therefore selects nothing: chosen in the form, it is asked again; passed as explicit <code>startEpochMs</code>/<code>endEpochMs</code>, it is refused with &ldquo;No finished chunk of session &hellip; covers the window&rdquo;, as before.</p>

      <p><strong>Flat, not a tree.</strong> A hub holds workspaces holding projects holding sessions, and the web UI lets you walk that. There is deliberately no tool for the walk. One <code>hubs_sessions</code> call fans out across every hub and returns flat rows, because four calls before anything is downloaded is four chances for a model to pair a workspace with the wrong project. The hierarchy survives as the <code>hub</code>, <code>workspace</code> and <code>project</code> filters, all matched loosely against names, and as columns you can read.</p>

      <p><strong>Read both page and scan completeness.</strong> <code>returned</code> counts rows on this page; <code>hasMore</code> and <code>nextCursor</code> continue the observed catalogue. <code>complete=false</code> means remote scopes failed and <code>failures</code> explains which ones, each with a <code>kind</code> &mdash; <code>UNREACHABLE</code>, <code>DEADLINE_EXCEEDED</code>, <code>CAPACITY_EXHAUSTED</code> or <code>OTHER</code> &mdash; beside its <code>reason</code>. In that case <code>total</code> is unknown (<code>null</code>), while <code>observedTotal</code> counts rows actually found. An exhausted partial scan can have <code>hasMore=false</code> without proving there are no other sessions.</p>
      <p>Hub pages use recording time and the full session reference as a stable ordering. Keep the same filters when reusing a cursor; a relative-time filter retains its original cutoff. Each page reads the live Hubs again, so retention or newly recorded sessions can change the observed catalogue. Start a fresh traversal after an unavailable scope recovers. Output limits can reduce the page below the requested limit; continuation follows the last row actually returned.</p>

      <p><strong><code>withinLastMinutes</code> is an overlap, not a start time.</strong> A JVM that began recording three hours ago and is still running matches a sixty-minute window, because it <em>was</em> recording during it. That is what someone asking for "the last hour" means, and the opposite of what filtering on start time would return.</p>

      <p><strong>Downloading and analysing are two calls on purpose.</strong> <code>hubs_download</code> stops at a recording and hands back its id; <code>recordings_analyzeRecording</code> builds the profile. A single call covering a multi-gigabyte transfer <em>and</em> a full analysis is the shape that trips a client's tool timeout, and a timeout partway through says nothing about whether the work survived.</p>

      <p><strong>Response and transfer deadlines.</strong> A download call waits up to forty-five seconds, including remote session lookup. For a client that declared the tasks extension only the wait for the transfer is shortened, to about five seconds before it gets a <a href="#tasks">task</a>; the session lookup keeps its full budget. A longer transfer continues in the background under its own one-hour deadline, and the answer carries an <code>operationId</code>: poll <code>operations_status</code> with it, which reports the attempt&rsquo;s progress, its result and its retry instructions, and <code>operations_cancel</code> it if it is no longer wanted. Calling <code>hubs_download</code> again with the same arguments is harmless &mdash; concurrent calls share a transfer when hub, workspace, project, session and the part asked for all match &mdash; but it is the poll that says what is happening. The <code>RUNNING</code> answer&rsquo;s <code>followUp</code> carries the call that finds this transfer again: the same <code>sessionRef</code> for a whole session, and for a window the user chose when <a href="#window-question">asked</a>, its exact <code>startEpochMs</code> and <code>endEpochMs</code>. Calling with those joins the same transfer without asking again; calling with the bare <code>sessionRef</code> would put the question again, since it names the whole session. These deadlines are configurable on the <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> page.</p>

      <p><strong>Failed transfers and retries.</strong> Failed transfer outcomes are retained in memory for one hour after completion. During that window, subsequent polls report the failure; set <code>retry=true</code> to start another attempt. After the outcome expires or Microscope restarts, calling <code>hubs_download</code> can start a new transfer even with <code>retry</code> omitted or set to <code>false</code>. An existing local copy is still returned without downloading it again.</p>

      <p>A local download is a snapshot. If the remote session is still recording, its local copy does not include files recorded after that download. Repeating <code>hubs_download</code> returns the existing copy.</p>

      <DocsCallout type="tip" title="Read the local column before downloading">
        A row with a <code>profileId</code> has an enabled profile ready for analysis; one with only a <code>recordingId</code> is downloaded but has no ready profile, and its <code>followUp</code> offers <code>recordings_analyzeRecording</code>. A download is never offered as a call &mdash; which part to bring is the user&rsquo;s choice. Use <code>recordings_status</code> to check an import that is running or failed. Jeffrey recognises a session it has seen before from the <code>origin.*</code> tags it wrote at download time, so a repeated <code>hubs_download</code> returns what is already there rather than moving the bytes again &mdash; but reading the column first saves the round trip.
      </DocsCallout>

      <p>Discovery shares a twenty-second deadline across remote calls and cancels outstanding RPCs when it expires. Completed project results remain available even if another hub or workspace stalls. Incomplete scopes and their reasons appear under the table, including when no rows returned. Unavailable hubs, expired deadlines and missing sessions produce distinct explanations.</p>

      <p>The family is advertised only while <code>jeffrey.microscope.mcp.hubs.enabled</code> is on, and only while <code>hubs</code> is among the families the <code>families</code> list or the active <code>preset</code> selects; see <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>. Keep <code>operations</code> selected alongside it &mdash; that is how a download is polled and cancelled.</p>

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
            <td><code>profileId</code>, <code>className</code>, <code>methodName?</code>, <code>line?</code></td>
            <td>The absolute file and line, plus whether the position is <code>decompiled</code>, <code>imprecise</code> or <code>stale</code>, and what to do about each. Does <strong>not</strong> move the editor</td>
          </tr>
          <tr>
            <td><code>ide_source</code></td>
            <td><code>profileId</code>, <code>className</code></td>
            <td>The source text as the IDE has it &mdash; attached sources for a library when they exist, a decompiled reconstruction when they do not</td>
          </tr>
          <tr>
            <td><code>ide_windows</code></td>
            <td><code>profileId</code>, <code>className?</code></td>
            <td>Every open window, its branch and HEAD commit, whether it holds the class, and whether it is on the commit the recording was built from</td>
          </tr>
          <tr>
            <td><code>ide_link</code></td>
            <td><code>profileId</code>, <code>projectId</code></td>
            <td>Binds one window to this profile for every later lookup. Only needed when the choice is ambiguous</td>
          </tr>
          <tr>
            <td><code>ide_open</code></td>
            <td><code>profileId</code>, <code>className</code>, <code>methodName?</code>, <code>line?</code></td>
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
            <td><code>path</code>, <code>name?</code>, <code>force?</code></td>
            <td>Imports the file at <code>path</code> and builds a profile from it &mdash; the <code>profileId</code> every other family takes, plus a UI link. A file whose name and size match a recording already in the store is not imported again: its existing profile comes back with <code>reused: true</code>, or its stored recording is analysed when it has no profile yet; <code>force=true</code> imports it again regardless. The <code>status</code> is <code>READY</code>, <code>RUNNING</code>, <code>NOT_STARTED</code>, <code>FAILED</code> or <code>INTERRUPTED</code>. A large file comes back <code>RUNNING</code> with an <code>operationId</code> instead (a <a href="#tasks">task</a> after about five seconds, for a client that declared the tasks extension), and no <code>recordingId</code>, because the copy itself may not have finished; <code>operations_status</code> follows it from there. A failed import is a tool error that names its <code>operationId</code></td>
          </tr>
          <tr>
            <td><code>recordings_analyzeRecording</code></td>
            <td><code>recordingId</code>, <code>retry?</code></td>
            <td>The same, for a recording already in the Quick Analysis store &mdash; one uploaded through the UI or pulled in by <code>hubs_download</code> but never analysed. Carries an <code>operationId</code> either way; an attempt that failed is reported as it stands until <code>retry</code> is true</td>
          </tr>
          <tr>
            <td><code>recordings_status</code></td>
            <td><code>recordingId</code></td>
            <td>Whether an analysis that outlasted its call has finished, and the profile id once it has, for a <code>recordingId</code> the caller already holds &mdash; from <code>recordings_list</code> or <code>recordings_analyzeRecording</code>. A running <code>recordings_analyzeFile</code> has none to give yet; its <code>operationId</code> and <code>operations_status</code> are the handle there</td>
          </tr>
          <tr>
            <td><code>recordings_list</code></td>
            <td><code>limit?</code> (100, max 1000), <code>cursor?</code></td>
            <td>The recordings in the Quick Analysis store, newest first, analysed or not &mdash; 100 per page unless <code>limit</code> says otherwise, with <code>hasMore</code>/<code>nextCursor</code> when there are more. A row with a <code>null</code> <code>profileId</code> is waiting for <code>recordings_analyzeRecording</code></td>
          </tr>
          <tr>
            <td><code>recordings_delete</code></td>
            <td><code>recordingId</code></td>
            <td>Removes the recording from the Quick Analysis store together with the profile built from it and its files. The one destructive tool, and the cleanup after a window of a hub session has answered its question &mdash; the hub&rsquo;s copy is untouched, and <code>hubs_download</code> can pull it again. The profile id stops working the moment this returns. Answers status <code>DELETED</code>; a client that declared form elicitation is <a href="#delete-confirmation">asked to confirm</a> first</td>
          </tr>
        </tbody>
      </table>

      <p>The file types are the ones Jeffrey analyses anywhere else: <code>.jfr</code>, <code>.jfr.lz4</code>, <code>.hprof</code>, <code>.hprof.gz</code>, <code>.pprof</code> and <code>.otlp</code>. A heap dump lands as a profile the <code>heap_</code> family answers about; the rest land as one the <code>jfr_</code>, <code>flamegraph_</code> and <code>traces_</code> families answer about. <code>profiles_features</code> tells you which you got.</p>

      <DocsCallout type="warning" title="The path is opened by Jeffrey, not by the client">
        <code>path</code> must be <strong>absolute</strong> and must exist <strong>on the machine Jeffrey runs on</strong>. A relative path is rejected rather than guessed at &mdash; it would resolve against Jeffrey&rsquo;s working directory, not yours. A leading <code>~</code> is the one exception, and it expands against <em>Jeffrey&rsquo;s</em> home directory rather than the caller&rsquo;s, so it is only the same file when both are the same account on the same machine. For a Jeffrey in a container or on another host, mount or copy the file where Jeffrey can see it first.
      </DocsCallout>

      <DocsCallout type="warning" title="A large recording outlasts the call &mdash; poll, do not re-analyse">
        Both analyse tools wait about <strong>forty-five seconds</strong> for the parse. A small recording finishes inside that and its <code>profileId</code> comes straight back, exactly as before. A large one comes back with a status of <code>RUNNING</code> and an <code>operationId</code> while the copy and the parse carry on in the background &mdash; and, from <code>recordings_analyzeFile</code>, no <code>recordingId</code>, because the file may still be being copied in. <code>operations_status(operationId)</code> reports the stage it is on and the <code>profileId</code> once it lands; <code>recordings_status</code> answers the same question for a <code>recordingId</code> you already hold. Poll one of those rather than calling the analyse tool again. A client that declared the MCP tasks extension is not held for the forty-five seconds: after about five it gets a <a href="#tasks">task</a> for the same work, followed with <code>tasks/get</code>. Once the first call&rsquo;s copy has landed, a second <code>recordings_analyzeFile</code> of a file with the same name and size does not import it again &mdash; it returns the existing profile, or joins the analysis of the recording the first call stored. While the copy is still in flight the second call joins that import instead &mdash; the same <code>operationId</code> and the same outcome, one copy and one profile, which keeps the first caller&rsquo;s profile <code>name</code> &mdash; and <code>force=true</code> always starts an import of its own. An import that failed or was cancelled is not held against the file: the next call imports it again. A second <code>recordings_analyzeRecording</code> for the same recording is safe &mdash; it joins the run already in flight rather than racing it.
      </DocsCallout>

      <p><code>recordings_status</code> reports retained failures and their error details for repeated polls. Completed import job outcomes are retained in memory for one hour and are lost on restart. Once failure details are no longer available, a disabled profile with no active initialization is reported as <code>INTERRUPTED</code>; an attempt that never created a profile is reported as <code>NOT_STARTED</code>. Call <code>recordings_analyzeRecording</code> again with <code>retry=true</code> to retry a failed or interrupted attempt; without it the retained outcome is reported rather than a new attempt started. <code>operations_cancel</code> asks a running one to stop. Work waiting for a pipeline slot is included in <code>RUNNING</code>. Imports themselves run at most <code>jeffrey.microscope.mcp.recordings.max-concurrent-imports</code> at a time (two by default); a call beyond that is reported as <code>QUEUED</code> in its operation until a slot frees, and cancelling it there means it never starts. A requested profile name is applied before the background attempt completes, including when the original call has already returned.</p>

      <p>One more thing worth knowing: <code>recordings_analyzeFile</code> recognises a file already in the store by its file name and size alone &mdash; the directory it sits in and its modification time are not compared. A recording the next run rewrites to a different size is a new file and is imported. Two cases are taken for the stored file although they are not: a rewrite that happens to come out at exactly the same size, and a different file with the same name and size. Pass <code>force=true</code> to import the file anyway, which is also how to build a second profile of an unchanged file on purpose.</p>

      <p>The family is always built, and is advertised whenever <code>recordings</code> is among the families the <code>families</code> list or the active <code>preset</code> selects &mdash; every preset keeps it. Drop it from <code>families</code> to refuse imports on a shared installation; see <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>. Keep <code>operations</code> selected alongside it, which is how an import is polled and cancelled.</p>

      <p><strong>Example.</strong> Arguments are shown as JSON; the tool name omits the server prefix.</p>
      <DocsCodeBlock :code="exRecordings" language="json" />

      <h3 id="delete-confirmation">Confirming a Deletion</h3>
      <p>A client that declared <strong>form elicitation</strong> is asked before <code>recordings_delete</code> removes anything: the call answers with an <router-link to="/docs/microscope-mcp/other-clients#input-requests">input request</router-link>, and the client shows the user the question. The message names the recording, its id and the profile built from it (or says it has none yet), and says that the deletion cannot be undone here while a copy on a Jeffrey Hub is untouched. The form is one required box, <strong>Delete this recording</strong>, unchecked to begin with.</p>
      <ul>
        <li><strong>Checked</strong> &mdash; the recording and its profile are deleted, and the answer is the usual one.</li>
        <li><strong>Left unchecked, declined or dismissed</strong> &mdash; nothing is deleted, and the call completes without an error: status <code>NOT_CONFIRMED</code> with the <code>reason</code> &ldquo;The user did not confirm; nothing was deleted.&rdquo;</li>
        <li><strong>An answer without a yes or no in the box</strong> &mdash; asked again.</li>
      </ul>
      <p>The recording is checked before the question and again on the retry that carries the answer, so the user is never asked about a deletion that would be refused &mdash; a blank or unknown id, or a recording whose profile is still being built, is refused without asking &mdash; and a parse that started since the question still blocks it. A client that did not declare form elicitation is not asked, and the recording is deleted as before; answers such a client sends anyway are ignored. <code>destructiveHint</code> is unchanged either way, so a host that asks before destructive tools still does.</p>

      <h2 id="operations">operations_ &mdash; the work the writers start</h2>
      <p>Seven of the tools above start something that can outlast the call &mdash; <code>recordings_analyzeFile</code> and <code>recordings_analyzeRecording</code>, <code>hubs_download</code>, <code>hubs_fetchFile</code>, <code>heap_prepare</code>, and <code>heap_oql</code> with <code>includeRetainedSize</code> and <code>jvm_autoAnalysis</code> with <code>compute</code>. Each returns an <code>operationId</code> naming that exact attempt, and this family is how the attempt is followed afterwards without touching the tool that started it. Neither tool here takes a <code>profileId</code>.</p>
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
            <td><code>operations_status</code></td>
            <td><code>operationId</code></td>
            <td>The status, progress, result and retry of that attempt &mdash; <code>kind</code> (<code>RECORDING_IMPORT</code>, <code>HUB_DOWNLOAD</code>, <code>HEAP_PREPARE</code>, &hellip;), <code>status</code> (<code>QUEUED</code>, <code>RUNNING</code>, <code>CANCEL_REQUESTED</code>, <code>COMPLETED</code>, <code>FAILED</code> or <code>CANCELLED</code>), <code>startedAtEpochMs</code>/<code>finishedAtEpochMs</code>, the measured stages under <code>progress</code> and, once an import lands, the <code>profileId</code>. A failed or cancelled attempt that can be retried carries the retry call in <code>followUp.nextTools</code>. It reads the operations of <code>recordings_analyzeFile</code>, <code>recordings_analyzeRecording</code>, <code>hubs_download</code>, <code>hubs_fetchFile</code>, <code>heap_prepare</code>, <code>heap_oql</code> with <code>includeRetainedSize</code> and <code>jvm_autoAnalysis</code> with <code>compute</code>. Polling never starts work</td>
          </tr>
          <tr>
            <td><code>operations_cancel</code></td>
            <td><code>operationId</code></td>
            <td>A request that the attempt stop. Best effort: <code>CANCEL_REQUESTED</code> stays non-terminal while the worker or its cleanup is active, nothing already written &mdash; a file, a profile, a cached report &mdash; is rolled back, and an old id never cancels a newer retry</td>
          </tr>
        </tbody>
      </table>
      <p>Cancellation is cooperative: the operation keeps its slot until the worker and its cleanup finish, and repeating the request is safe. Terminal attempts stay readable for one hour in this process; a restart or expiry makes the id unavailable. Progress reports the stages that were measured, and where a byte total or a percentage was never known it stays unknown rather than being estimated. A submission the server refused &mdash; a full pipeline, say &mdash; reports an error without leaving queued work behind, and can be tried again once it can accept work.</p>
      <p>The family is advertised by every preset, because a client that can start work and not follow it is worse off than one that can do neither; an explicit <code>jeffrey.microscope.mcp.families</code> list that keeps a writer family &mdash; <code>recordings</code>, <code>heap</code>, <code>hubs</code>, <code>ide</code> or <code>jvm</code> &mdash; without <code>operations</code> is refused at startup.</p>

      <h3 id="tasks">Tasks &mdash; the same work, the standard way</h3>
      <p>A client that declares the MCP tasks extension &mdash; <code>io.modelcontextprotocol/tasks</code> in the <code>clientCapabilities</code> of its request &mdash; is not held for forty-five seconds. The seven tools above wait about <strong>five seconds</strong> for it. Work that finishes inside that answers directly, exactly as before; work that does not comes back as a standard <strong>task</strong> (<code>resultType: &quot;task&quot;</code> with a <code>taskId</code> and a <code>status</code> of <code>working</code>) instead of an <code>operationId</code> in the text. <code>heap_prepare</code> does not wait at all: such a client gets its task at once while the preparation runs, any other client its <code>operationId</code>; a retained failed or cancelled run answers directly with its retry guidance, since there is nothing left to follow. The client follows the task with <code>tasks/get</code> and stops it with <code>tasks/cancel</code> &mdash; <router-link to="/docs/microscope-mcp/other-clients#tasks">Other Clients</router-link> shows the requests. <code>server/discover</code> declares the extension whenever a family that starts such work is advertised.</p>
      <p>A task is not a second mechanism. <strong>The <code>taskId</code> is the <code>operationId</code></strong>: both name the same attempt, so <code>operations_status</code> reads a task too, and a task is reachable on the same terms &mdash; only while the family that started it is advertised. A client that did not declare the extension keeps the forty-five-second wait and the <code>operationId</code>, which is why this family stays.</p>
      <ul>
        <li><strong>Joined calls share one task.</strong> A second call that joins work already in flight &mdash; the same file while its copy runs, the same recording, the same hub transfer, a compute run already going &mdash; is handed the same <code>taskId</code> as the first. Cancelling it cancels the work both callers are following.</li>
        <li><strong><code>ttlMs</code> is retention, not a deadline.</strong> A task advertises a <code>ttlMs</code> of one hour: how long it stays readable once it has finished. A task still <code>working</code> outlives it for as long as the work runs, and the hour counts from the finish. <code>pollIntervalMs</code> asks the client to come back every five seconds.</li>
        <li><strong>The finished result is what a waiting caller would have read.</strong> <code>tasks/get</code> on a completed task carries the full <code>tools/call</code> result &mdash; the same text the call returns when the work finishes inside its wait. One exception: an analysis that failed is a task <code>completed</code> with <code>isError: true</code>, where a caller that waited on <code>recordings_analyzeRecording</code> gets a status document reporting the failure without being an error.</li>
        <li><strong>In memory only</strong>, like the operations: a restart forgets them, and an unknown or expired <code>taskId</code> is refused with <code>-32602</code>.</li>
      </ul>

      <h2 id="links">Links Back to the UI</h2>
      <p>Every answer whose subject has a page in Microscope carries a link to it &mdash; the <code>uiLink</code> field of the record, repeated as the <code>Open in Microscope: &hellip;</code> line of a Markdown answer&rsquo;s footer. Rows that name a thing with its own page carry their own link as well: every trace in <code>traces_slowestTraces</code> and <code>traces_attributeSearch</code>, every operation in <code>traces_operations</code>, every exemplar in <code>traces_notifications</code>. Only the tools with no page leave it out: <code>operations_status</code> and <code>operations_cancel</code>, the SQL catalogue tools of <code>jfr_</code> and <code>heap_</code>, and the five <code>ide_</code> tools. Links are built from the routes the UI serves, never typed by hand, and a test holds every one to the router&rsquo;s own manifest.</p>
      <p>The flamegraph link reproduces the graph exactly: the event type and filters the export was built with, its window as <code>startEpochMs</code>/<code>endEpochMs</code> (the whole recording when none was asked), and its <code>search</code>. A comparison opens the differential graph with <code>graphMode=DIFFERENTIAL&amp;baseline=&lt;id&gt;</code> and the same window; <code>compare_list</code> and <code>compare_quality</code> open the pair&rsquo;s differential grid. An events answer opens the events page on its event type, a timeline zoom the sub-second view, an attribute search the same condition and scope, the operation link its slowest or Flamegraphs tab, and a trace link that trace&rsquo;s span waterfall.</p>
      <p>The link is for the reader, not for the model. A URL carries nothing that can be analysed further and does not help choose the next tool, which is exactly why it travels attached to an answer rather than behind a tool of its own: a model weighing its own context would reasonably skip a call whose result it cannot use. <code>profiles_viewLink</code> is there for the pages an answer did not come from.</p>
      <p>The host comes from the request the client made, so the address is by definition one that reaches this installation. Where a link still cannot reproduce the answer exactly, <code>uiLinkNote</code> says what differs. Every case that carries one: <code>traces_spanFlamegraphExport</code> opens the trace&rsquo;s span waterfall, where the span&rsquo;s graph opens in a dialog; <code>traces_operationFlamegraphExport</code> opens the operation&rsquo;s Flamegraphs tab, which draws its own panels; <code>traces_notifications</code> opens the traced operations, since no page lists notifications; <code>compare_movements</code> opens the differential graph its ranking was read from; <code>compare_flamegraph</code> (and <code>compare_movements</code>) note a baseline read past the primary&rsquo;s window, which the page cuts there; <code>heap_oql</code> opens the OQL console without the query; and the heap-object answers &mdash; <code>heap_browseClassInstances</code> (the class&rsquo;s histogram row), <code>heap_getInstanceDetail</code> and <code>heap_getReferrers</code> (the object&rsquo;s GC-root path), <code>heap_getDominatorTreeChildren</code> (the tree&rsquo;s roots, to expand down to the object).</p>

      <h2 id="what-is-not-here">What Is Not Here</h2>
      <p><strong>No write tool inside a profile.</strong> Every <code>jfr_</code> and <code>heap_</code> tool reads; nothing runs an <code>UPDATE</code> or <code>DELETE</code> against a profile&rsquo;s database, so an agent can read what a recording says and never change what it says. <code>recordings_</code> is not a counter-example &mdash; it creates profiles, it does not rewrite one.</p>

      <p><strong>No deleting beyond <code>recordings_delete</code>.</strong> That one tool removes a recording in the Quick Analysis store and the profile built from it &mdash; after the user confirms, on a client that can ask. Nothing else deletes a profile, a recording or a heap dump.</p>

      <p><strong>No OQL <em>assistant</em>.</strong> The OQL language itself is here &mdash; <code>heap_oql</code> runs it against the object graph, and answers with a link to the UI&rsquo;s OQL console, which opens without the query &mdash; paste it there, as the answer&rsquo;s <code>uiLinkNote</code> says. There is no tool that drafts a query from a question in English: over MCP the model writes its own, with the <router-link to="/docs/microscope-mcp/skills#heap-sql"><code>heap-sql</code> skill</router-link> as its reference.</p>

      <p><strong>No charts.</strong> The <code>jvm_</code> family carries the numbers behind each UI dashboard, not the timeseries they are drawn from: pause and throttling timelines, the G1 and ZGC deep dives, tenuring and reference processing, the thread timeline and the sub-second view stay in the UI, where a reader can scrub them. <code>profiles_link</code> opens the profile there.</p>

      <p><strong>No exports.</strong> No tool writes a result to a file or a resource for an agent to fetch later. The data an agent needs is in the answer, bounded and declaring what it left out; the full picture is the <code>uiLink</code>, for the person reading.</p>

      <p><strong>No shell.</strong> The server answers questions about profiles and, when the <code>recordings</code> family is advertised, opens the one recording path it is handed. It runs nothing.</p>
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
