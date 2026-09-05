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
  { id: 'analyse-a-recording-you-just-made', text: 'Analyse a Recording You Just Made', level: 2 },
  { id: 'analyse-what-production-recorded', text: 'Analyse What Production Recorded', level: 2 },
  { id: 'where-does-the-time-go', text: 'Where Does the Time Go', level: 2 },
  { id: 'explain-a-slow-endpoint', text: 'Explain a Slow Endpoint', level: 2 },
  { id: 'find-when-it-happened', text: 'Find When It Happened', level: 2 },
  { id: 'the-cpu-is-idle-and-it-is-still-slow', text: 'The CPU Is Idle and It Is Still Slow', level: 2 },
  { id: 'chase-a-memory-problem', text: 'Chase a Memory Problem', level: 2 },
  { id: 'account-for-the-gc-pauses', text: 'Account for the GC Pauses', level: 2 },
  { id: 'ask-what-the-jit-is-doing', text: 'Ask What the JIT Is Doing', level: 2 },
  { id: 'does-the-code-agree-with-the-profile', text: 'Does the Code Agree With the Profile', level: 2 },
  { id: 'from-profile-to-patch', text: 'From Profile to Patch', level: 2 },
  { id: 'compare-two-recordings', text: 'Compare Two Recordings', level: 2 },
  { id: 'a-question-with-no-tool', text: 'A Question With No Tool', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const promptFreshRecording = `analyze target/checkout-run.jfr in Jeffrey and tell me which of my own
methods dominate the CPU profile`;

const promptFromHub = `analyse what production recorded in the last hour and tell me where the time
went`;

const promptHotPaths = `list the Jeffrey profiles, then show me where the CPU time goes in the most recent one`;

const promptSlowEndpoint = `the GET /api/orders operation is slow - find a slow example and tell me
what the JVM was doing inside its slowest span`;

const promptWhen = `allocation in this profile looks bursty - find the window where most of it
happened and graph just that window`;

const promptWaiting = `the CPU looks idle but requests are slow - what is this application
waiting on?`;

const promptMemory = `which classes retain the most memory in the heap dump, and what is keeping
the biggest one alive?`;

const promptGc = `how much of the run went to GC pauses in the most recent profile, what caused
them, and which code is producing the garbage?`;

const promptJit = `is the JIT compiler a problem in this profile - anything deoptimising repeatedly,
or a code cache that filled up?`;

const promptCrossCheck = `the profile says OrderMapper.toDto is 18% of CPU. Read the actual
implementation in this repo and tell me whether that is plausible, and what
you would change`;

const promptAdvise = `advise on the most recent Jeffrey profile - what should I change in this repo?`;

const promptCompare = `compare the allocation profiles of the before and after runs and tell me
what actually changed - and whether the two runs are even comparable`;

const promptSql = `across the whole recording, what is the distribution of jdk.FileRead
durations per file, and which file is worst?`;

const sqlAnswer = `SELECT fields->>'path'          AS path,
       COUNT(*)                  AS reads,
       SUM(duration) / 1000000.0 AS total_ms,
       MAX(duration) / 1000000.0 AS max_ms
FROM events
WHERE event_type = 'jdk.FileRead'
GROUP BY path
ORDER BY total_ms DESC
LIMIT 20`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Recipes"
      icon="bi bi-lightbulb"
    />

    <div class="docs-content">
      <p>Worked sessions. Each one is a prompt you can paste, and the tool sequence it drives &mdash; useful both for getting started and for recognising when Claude has taken a wrong turn.</p>

      <DocsCallout type="tip" title="Name the profile if you know it">
        Every prompt below starts with a catalogue lookup because it does not say which profile it means. If you paste a profile name or id, that step is skipped.
      </DocsCallout>

      <h2 id="analyse-a-recording-you-just-made">Analyse a Recording You Just Made</h2>
      <DocsCodeBlock :code="promptFreshRecording" language="bash" />

      <p>Drives <code>recordings_analyzeFile</code> &rarr; <code>profiles_features</code> &rarr; <code>flamegraph_list</code> &rarr; <code>flamegraph_export</code>.</p>

      <p>This is the loop closing. You run a benchmark, a JFR file lands in <code>target/</code>, and the next thing you type is a question about it &mdash; no upload, no browser, no clicking Analyze. The first tool call imports the file and builds the profile, and hands back the <code>profileId</code> every later call uses; the profile is a normal one afterwards, visible in the Jeffrey UI and in <code>profiles_list</code>.</p>

      <p>The same works for a heap dump: <code>analyze heap.hprof and tell me what is retaining the most memory</code> lands as a profile the <code>heap_</code> family answers about instead.</p>

      <DocsCallout type="warning" title="Jeffrey opens the path, you do not upload the file">
        The path must be absolute and must exist on the machine Jeffrey runs on. That is the same machine for the usual setup &mdash; a Jeffrey and a terminal on one laptop &mdash; and not the same machine for a Jeffrey in a container or on a remote host, where the file has to be mounted or copied across first. Each call builds another profile, so say <em>&ldquo;check whether it is already analysed&rdquo;</em> if you may be repeating yourself.
      </DocsCallout>

      <h2 id="analyse-what-production-recorded">Analyse What Production Recorded</h2>
      <DocsCodeBlock :code="promptFromHub" language="bash" />

      <p>Drives <code>hubs_sessions</code> &rarr; <code>hubs_download</code> &rarr; <code>recordings_analyzeRecording</code> &rarr; the usual analysis.</p>

      <p>The recording you actually want is rarely on your laptop. This is the same loop as the recipe above, starting one step further back: the first call searches every connected <router-link to="/docs/hub">Jeffrey Hub</router-link> at once and comes back with a flat list of sessions, newest first, each carrying a reference the download takes. Nothing asks you to pick a hub, then a workspace, then a project &mdash; that hierarchy is filters and columns here, not a sequence of questions.</p>

      <p>&ldquo;The last hour&rdquo; means <em>recording during</em> the last hour, not <em>started within</em> it, so a JVM that came up this morning and is still running is included. That is almost always what the question meant.</p>

      <p>You will be asked to choose only when the choice is real &mdash; several projects matched, or the session is large enough that pulling it is worth a moment's thought &mdash; and when you are, the projects, durations and sizes come with the question.</p>

      <DocsCallout type="tip" title="The second time costs nothing">
        Sessions already pulled in are marked in the listing, with the profile id when they have been analysed. Ask the same question tomorrow and yesterday's session is not downloaded again.
      </DocsCallout>

      <p>A heap dump in the session arrives with it, so <em>&ldquo;pull the dump from the pod that OOMed and tell me what was holding memory&rdquo;</em> is the same recipe ending in the <code>heap_</code> family instead.</p>

      <h2 id="where-does-the-time-go">Where Does the Time Go</h2>
      <DocsCodeBlock :code="promptHotPaths" language="bash" />

      <p>Drives <code>profiles_list</code> &rarr; <code>profiles_features</code> &rarr; <code>flamegraph_list</code> &rarr; <code>flamegraph_export</code> with <code>jdk.ExecutionSample</code>.</p>

      <p>The export is a pruned call tree with total and self samples on every frame, and it opens by explaining exactly how those are computed. Expect an answer that names call paths and percentages. Follow up by narrowing: <em>&ldquo;drop the threshold to 0.5% and expand the path through the JDBC driver&rdquo;</em>, or <em>&ldquo;same graph but only the first 30 seconds&rdquo;</em> &mdash; both are arguments to the same tool.</p>

      <p>For allocation rather than CPU, ask for it by weight: <code>jdk.ObjectAllocationSample</code> ranked by <code>useWeight</code> answers &ldquo;which call path allocates the most bytes&rdquo;, where the default sample count would answer &ldquo;which allocates most often&rdquo;.</p>

      <h2 id="explain-a-slow-endpoint">Explain a Slow Endpoint</h2>
      <DocsCodeBlock :code="promptSlowEndpoint" language="bash" />

      <p>Drives <code>traces_overview</code> &rarr; <code>traces_operations</code> &rarr; <code>traces_notifications</code> &rarr; <code>traces_operationExport</code> &rarr; <code>traces_slowestTraces</code> &rarr; <code>traces_traceExport</code> &rarr; <code>traces_spanFlamegraphExport</code>.</p>

      <p>The notifications step is the one that reads the application&rsquo;s own account first. When the overview reports any <code>CRITICAL</code> or <code>HIGH</code> notifications, <code>traces_notifications</code> says what kind, how often, and in which traces &mdash; and a &ldquo;connection pool has no idle connections&rdquo; raised inside the slow operation is the answer before any span is timed. The exports that follow carry the same notifications in context, against the span each was raised in.</p>

      <p>This is the sequence that only works because both halves are in one place. The operation export gives the population &mdash; percentiles, and which spans the wall-clock went to. A single slow trace gives the span tree for one real request. The span flamegraph then shows the JVM frames sampled <em>while that one span was open</em>, so &ldquo;the query span takes 400ms&rdquo; becomes &ldquo;380ms of it is result-set deserialisation in this method&rdquo;.</p>

      <p>Requires a profile recorded with <router-link to="/docs/tracing">Jeffrey Tracing</router-link>; <code>profiles_features</code> says whether one was. Without traces, <code>http_overview</code> and <code>jdbc_overview</code> answer the same question in aggregate &mdash; which endpoint carries the traffic, which statements cost the most, and whether the pool in front of them was the real constraint.</p>

      <h2 id="find-when-it-happened">Find When It Happened</h2>
      <DocsCodeBlock :code="promptWhen" language="bash" />

      <p>Drives <code>timeline_hotWindows</code> &rarr; <code>flamegraph_export</code> with the window it returned, and <code>timeline_zoom</code> when a second is too coarse.</p>

      <p>This is the recipe for anything bursty, and for a startup. A flamegraph of a whole recording is an average, and an average hides a spike: on a real Jeffrey recording, a quarter of all allocation landed in five seconds out of nearly eight hours, and exporting only those five seconds moved the top frame from 56% to 99% of the graph. The tool returns the <code>startMs</code> and <code>endMs</code> ready to pass on, plus a one-line shape so a steady load, a ramp and a single burst are told apart before anything is exported.</p>

      <p><code>timeline_zoom</code> is the only view that resolves below a second, which is what a startup needs &mdash; one-second buckets hide everything that happens while the JVM is warming up.</p>

      <h2 id="the-cpu-is-idle-and-it-is-still-slow">The CPU Is Idle and It Is Still Slow</h2>
      <DocsCodeBlock :code="promptWaiting" language="bash" />

      <p>Drives <code>blocking_overview</code> &rarr; <code>blocking_monitors</code> or <code>blocking_pinnedThreads</code>, and <code>io_overview</code> &rarr; <code>io_endpoints</code> &rarr; <code>io_slowest</code>.</p>

      <p>A CPU flamegraph cannot answer this question at all, and the reason is structural: a thread blocked on a monitor or a socket read is not on-CPU, so it is never sampled and the graph reports the application as idle rather than as waiting. <code>blocking_monitors</code> aggregates per lock class, which names the monitor rather than whichever call site happened to reach it, and <code>blocking_pinnedThreads</code> covers the failure mode Loom introduces &mdash; a pinned carrier blocks every virtual thread scheduled on it while looking merely busy.</p>

      <p>For a request that is slow while every statement is fast, the answer is usually <code>jdbc_pools</code>: a connection wait shows up nowhere in the statement timings, and no change to a query will fix it.</p>

      <h2 id="chase-a-memory-problem">Chase a Memory Problem</h2>
      <DocsCodeBlock :code="promptMemory" language="bash" />

      <p>Loads the <router-link to="/docs/microscope-mcp/skills"><code>analyze-heap</code></router-link> skill, which drives <code>heap_getHeapSummary</code> &rarr; <code>heap_getDominatorTreeRoots</code> &rarr; <code>heap_getPathToGCRoot</code>, usually with <code>heap_getLeakSuspects</code> and <code>heap_getInstanceDetail</code> along the way.</p>

      <p>The two-step matters. A class histogram answers &ldquo;what is there&rdquo;; the dominator tree answers &ldquo;what would be freed&rdquo;, which is the one that finds a leak. And <code>heap_getPathToGCRoot</code> is the actual answer to &ldquo;why is this still alive&rdquo; &mdash; a reference chain from a root, usually ending somewhere recognisable like a static cache or a thread-local.</p>

      <p>The order is not optional: <code>dominator</code> and <code>retained_size</code> are built lazily, so every retained figure comes back missing until <code>heap_getDominatorTreeRoots</code> has run once. That is what the skill exists to get right.</p>

      <h2 id="account-for-the-gc-pauses">Account for the GC Pauses</h2>
      <DocsCodeBlock :code="promptGc" language="bash" />

      <p>Drives <code>jvm_sections</code> &rarr; <code>jvm_gc</code>, one call for the whole dashboard: the stop-the-world budget, collections split by generation, the causes, bytes freed, the longest individual collections. The last clause of the prompt is what makes it useful &mdash; the answer ends in <code>flamegraph_export</code> over <code>jdk.ObjectAllocationSample</code>, because no GC event names the code that produced the garbage.</p>

      <DocsCallout type="warning" title="Pauses are sumOfPauses, not the event duration">
        For ZGC, Shenandoah and G1&rsquo;s concurrent cycles a <code>jdk.GarbageCollection</code> event&rsquo;s <code>duration</code> spans phases the application ran straight through, so ranking by it reports pauses that never happened. <code>jvm_gc</code> reads <code>sumOfPauses</code> and <code>longestPause</code> because the builder behind it always has &mdash; which is the argument for the tool over an improvised query.
      </DocsCallout>

      <p>If the budget comes back small and the application still stalls, the pauses are not the collector&rsquo;s: <code>jvm_safepoints</code> has the VM operations, and names the threads that were slow to reach the safepoint with the state they were in.</p>

      <h2 id="ask-what-the-jit-is-doing">Ask What the JIT Is Doing</h2>
      <DocsCodeBlock :code="promptJit" language="bash" />

      <p>One call to <code>jvm_jit</code>: the compiler&rsquo;s totals, the slowest compilations, code cache occupancy per heap, and deoptimisations aggregated by method and by reason.</p>

      <p>The finding worth having is a method that deoptimises over and over &mdash; it ran interpreted for part of the recording, and the reason (<code>unstable_if</code>, <code>class_check</code>) is a pointer into your source, which is exactly the hand-off the terminal is good at and the browser is not. A code cache that ran full is the other one: compilation stops, and the application quietly settles at interpreted speed with nothing in a CPU profile to say why.</p>

      <h2 id="does-the-code-agree-with-the-profile">Does the Code Agree With the Profile</h2>
      <DocsCodeBlock :code="promptCrossCheck" language="bash" />

      <p>This is the one that has no equivalent in the Jeffrey UI, and the reason the MCP server exists. The agent pulls the flamegraph, then reads the real method in your checkout &mdash; not a guess at what it probably does &mdash; and reconciles the two: an N+1 in a mapper, a regex recompiled per call, a defensive copy in a loop.</p>

      <p>Two things make the answers trustworthy: the numbers come from the profile rather than from intuition, and the code comes from disk rather than from memory of a similar codebase. Ask for the diff once you agree with the diagnosis.</p>

      <h2 id="from-profile-to-patch">From Profile to Patch</h2>
      <DocsCodeBlock :code="promptAdvise" language="bash" />

      <p>Drives the <router-link to="/docs/microscope-mcp/skills#advise-jfr"><code>advise-jfr</code></router-link> skill: <code>profiles_get</code> for the recording&rsquo;s commit, <code>flamegraph_list</code>, then <code>flamegraph_export</code> once per group the profile carries &mdash; CPU, wall-clock, allocation, blocking &mdash; followed by reads of the real source behind the heaviest frames.</p>

      <p>The previous recipe reconciles one frame with one method. This one is the whole loop: every group at once, a recommendation per hotspot with the measured share that justifies it, and a stop before anything is edited. Say which findings to apply and Claude makes the smallest edit for each, runs the tests, and &mdash; if you name the command that produced the recording &mdash; re-runs it, analyses the new file with <code>recordings_analyzeFile</code> and reports the delta on the frames it changed.</p>

      <DocsCallout type="info" title="It checks the commit first">
        When the recording was tagged with the commit it was built from, <code>profiles_get</code> reports it and the skill compares it with <code>HEAD</code> before mapping a single frame. A mismatch is stated up front, not discovered after a patch to code that never ran.
      </DocsCallout>

      <h2 id="compare-two-recordings">Compare Two Recordings</h2>
      <DocsCodeBlock :code="promptCompare" language="bash" />

      <p>The <code>compare_</code> family does this as one operation rather than as two exports the model has to hold side by side. <code>compare_list</code> first &mdash; both recordings&rsquo; length, the event types they share, and the ones only one of them captured. Then <code>compare_movements</code>, which ranks the methods that moved. Then <code>compare_flamegraph</code> on whichever one the answer turned out to be.</p>

      <p>Pass the <strong>after</strong> run as <code>profileId</code> and the <strong>before</strong> run as <code>baselineProfileId</code>. A positive delta then means the primary spends more, which is a regression; swap them and every regression reads as an improvement.</p>

      <DocsCallout type="warning" title="The first question is whether they are comparable at all">
        Two recordings can always be subtracted, and the difference always looks like a finding. Recording lengths that differ, a volume that stays far apart after scaling, an event type only one profiler captured &mdash; each produces a confident number that means nothing. <code>compare_list</code> reports all three, and every comparison document repeats them in a comparability section before it shows a single delta. &ldquo;These two runs are not comparable&rdquo; is a real answer, and a better one than the alternative.
      </DocsCallout>

      <p>Two things the output will not let you misread. Movements are attributed by <strong>self</strong> weight, so a change lands on the method that moved rather than on every caller above it &mdash; without that, one slow leaf reports its whole call stack as regressed. And a renamed or extracted method appears once as new and once as gone, of near-identical size, which reads as two dramatic findings; those pairs are listed as <strong>candidate renames</strong> for you to settle against the source diff, which the profile cannot see.</p>

      <p>The <code>compare-jfr</code> skill carries the whole sequence, so the prompt above does not have to name any of it.</p>

      <h2 id="a-question-with-no-tool">A Question With No Tool</h2>
      <DocsCodeBlock :code="promptSql" language="bash" />

      <p>Drives <code>jfr_listEventTypes</code> &rarr; <code>jfr_describeTable</code> &rarr; <code>jfr_executeQuery</code>, with the <code>jfr-sql</code> skill supplying the schema. Something like:</p>
      <DocsCodeBlock :code="sqlAnswer" language="sql" />

      <p>Distributions, correlations between event types, and cardinality questions all land here &mdash; anything the purpose-built tools do not shape. Aggregate in SQL: results are row-capped, so pulling rows back to count them is both slower and likely to truncate.</p>

      <p>The <router-link to="/docs/microscope-mcp/tools">Tool Reference</router-link> lists what each family covers, which is the fastest way to tell whether a question needs SQL at all.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
