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
  { id: 'where-does-the-time-go', text: 'Where Does the Time Go', level: 2 },
  { id: 'explain-a-slow-endpoint', text: 'Explain a Slow Endpoint', level: 2 },
  { id: 'chase-a-memory-problem', text: 'Chase a Memory Problem', level: 2 },
  { id: 'does-the-code-agree-with-the-profile', text: 'Does the Code Agree With the Profile', level: 2 },
  { id: 'compare-two-recordings', text: 'Compare Two Recordings', level: 2 },
  { id: 'a-question-with-no-tool', text: 'A Question With No Tool', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const promptFreshRecording = `analyze target/checkout-run.jfr in Jeffrey and tell me which of my own
methods dominate the CPU profile`;

const promptHotPaths = `list the Jeffrey profiles, then show me where the CPU time goes in the most recent one`;

const promptSlowEndpoint = `the GET /api/orders operation is slow - find a slow example and tell me
what the JVM was doing inside its slowest span`;

const promptMemory = `which classes retain the most memory in the heap dump, and what is keeping
the biggest one alive?`;

const promptCrossCheck = `the profile says OrderMapper.toDto is 18% of CPU. Read the actual
implementation in this repo and tell me whether that is plausible, and what
you would change`;

const promptCompare = `compare the allocation flamegraphs of the before and after profiles and
tell me what actually changed`;

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

      <p>Drives <code>recordings_analyzeFile</code> &rarr; <code>profiles_features</code> &rarr; <code>flamegraph_panels</code> &rarr; <code>flamegraph_export</code>.</p>

      <p>This is the loop closing. You run a benchmark, a JFR file lands in <code>target/</code>, and the next thing you type is a question about it &mdash; no upload, no browser, no clicking Analyze. The first tool call imports the file and builds the profile, and hands back the <code>profileId</code> every later call uses; the profile is a normal one afterwards, visible in the Jeffrey UI and in <code>profiles_list</code>.</p>

      <p>The same works for a heap dump: <code>analyze heap.hprof and tell me what is retaining the most memory</code> lands as a profile the <code>heap_</code> family answers about instead.</p>

      <DocsCallout type="warning" title="Jeffrey opens the path, you do not upload the file">
        The path must be absolute and must exist on the machine Jeffrey runs on. That is the same machine for the usual setup &mdash; a Jeffrey and a terminal on one laptop &mdash; and not the same machine for a Jeffrey in a container or on a remote host, where the file has to be mounted or copied across first. Each call builds another profile, so say <em>&ldquo;check whether it is already analysed&rdquo;</em> if you may be repeating yourself.
      </DocsCallout>

      <h2 id="where-does-the-time-go">Where Does the Time Go</h2>
      <DocsCodeBlock :code="promptHotPaths" language="bash" />

      <p>Drives <code>profiles_list</code> &rarr; <code>profiles_features</code> &rarr; <code>flamegraph_panels</code> &rarr; <code>flamegraph_export</code> with <code>jdk.ExecutionSample</code>.</p>

      <p>The export is a pruned call tree with total and self samples on every frame, and it opens by explaining exactly how those are computed. Expect an answer that names call paths and percentages. Follow up by narrowing: <em>&ldquo;drop the threshold to 0.5% and expand the path through the JDBC driver&rdquo;</em>, or <em>&ldquo;same graph but only the first 30 seconds&rdquo;</em> &mdash; both are arguments to the same tool.</p>

      <p>For allocation rather than CPU, ask for it by weight: <code>jdk.ObjectAllocationSample</code> ranked by <code>useWeight</code> answers &ldquo;which call path allocates the most bytes&rdquo;, where the default sample count would answer &ldquo;which allocates most often&rdquo;.</p>

      <h2 id="explain-a-slow-endpoint">Explain a Slow Endpoint</h2>
      <DocsCodeBlock :code="promptSlowEndpoint" language="bash" />

      <p>Drives <code>traces_overview</code> &rarr; <code>traces_operations</code> &rarr; <code>traces_operationExport</code> &rarr; <code>traces_slowestTraces</code> &rarr; <code>traces_traceExport</code> &rarr; <code>traces_spanFlamegraphExport</code>.</p>

      <p>This is the sequence that only works because both halves are in one place. The operation export gives the population &mdash; percentiles, and which spans the wall-clock went to. A single slow trace gives the span tree for one real request. The span flamegraph then shows the JVM frames sampled <em>while that one span was open</em>, so &ldquo;the query span takes 400ms&rdquo; becomes &ldquo;380ms of it is result-set deserialisation in this method&rdquo;.</p>

      <p>Requires a profile recorded with <router-link to="/docs/tracing">Jeffrey Tracing</router-link>; <code>profiles_features</code> says whether one was.</p>

      <h2 id="chase-a-memory-problem">Chase a Memory Problem</h2>
      <DocsCodeBlock :code="promptMemory" language="bash" />

      <p>Drives <code>heap_getHeapSummary</code> &rarr; <code>heap_getDominatorTreeRoots</code> &rarr; <code>heap_getPathToGCRoot</code>, usually with <code>heap_getLeakSuspects</code> and <code>heap_getInstanceDetail</code> along the way.</p>

      <p>The two-step matters. A class histogram answers &ldquo;what is there&rdquo;; the dominator tree answers &ldquo;what would be freed&rdquo;, which is the one that finds a leak. And <code>heap_getPathToGCRoot</code> is the actual answer to &ldquo;why is this still alive&rdquo; &mdash; a reference chain from a root, usually ending somewhere recognisable like a static cache or a thread-local.</p>

      <h2 id="does-the-code-agree-with-the-profile">Does the Code Agree With the Profile</h2>
      <DocsCodeBlock :code="promptCrossCheck" language="bash" />

      <p>This is the one that has no equivalent in the Jeffrey UI, and the reason the MCP server exists. Claude pulls the flamegraph, then reads the real method in your checkout &mdash; not a guess at what it probably does &mdash; and reconciles the two: an N+1 in a mapper, a regex recompiled per call, a defensive copy in a loop.</p>

      <p>Two things make the answers trustworthy: the numbers come from the profile rather than from intuition, and the code comes from disk rather than from memory of a similar codebase. Ask for the diff once you agree with the diagnosis.</p>

      <h2 id="compare-two-recordings">Compare Two Recordings</h2>
      <DocsCodeBlock :code="promptCompare" language="bash" />

      <p>Nothing special is needed: <code>profiles_list</code> returns both, and every tool takes a <code>profileId</code>, so the same export runs twice in one session. Comparison is where a terminal beats two browser tabs &mdash; the model holds both trees at once and reports the delta rather than making you scan for it.</p>

      <p>Keep the graph parameters identical across the two calls; a threshold difference will read as a change that is not there.</p>

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
