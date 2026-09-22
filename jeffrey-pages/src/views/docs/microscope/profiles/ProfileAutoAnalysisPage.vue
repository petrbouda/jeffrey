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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-it-runs', text: 'What It Runs', level: 2 },
  { id: 'when-it-runs', text: 'When It Runs', level: 2 },
  { id: 'results-overview', text: 'Results Overview', level: 2 },
  { id: 'rules-table', text: 'Rules Table', level: 2 },
  { id: 'reading-a-finding', text: 'Reading a Finding', level: 2 },
  { id: 'elsewhere', text: 'The Same Findings Elsewhere', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Auto Analysis"
      icon="bi bi-robot"
    />

    <div class="docs-content">
      <p>Auto Analysis runs the JDK Mission Control rule set against a recording and lists what the rules flagged: long GC pauses, exception storms, contended monitors, a heap that is too small for its live set, a JIT queue that never drained. It is the first place to look at an unfamiliar recording, because it reads the whole file for you and names the subsystem worth opening next. Reach it from the sidebar under <strong>Insights &rarr; Auto Analysis</strong>.</p>

      <h2 id="what-it-runs">What It Runs</h2>
      <p>The rules are JMC&rsquo;s own &mdash; the same evaluation the JDK Mission Control desktop application performs on its Automated Analysis page &mdash; executed by Jeffrey over the recording file. Each rule inspects one concern (a garbage-collection pattern, a lock, a thread-pool shape, a JVM flag) and answers with a severity, a numeric score, and three pieces of text: a one-line summary, an explanation of why the pattern matters, and a solution to try.</p>

      <DocsCallout type="info">
        <strong>What a rule can see:</strong> only the events the recording captured. A rule about exception throws is silent when <code>jdk.JavaExceptionThrow</code> was disabled in the JFR configuration; a rule about allocations needs allocation samples. A rule that could not evaluate is not a rule that passed &mdash; it simply does not appear.
      </DocsCallout>

      <h2 id="when-it-runs">When It Runs</h2>
      <p>For a recording imported through Jeffrey, the rule set starts <strong>during the import</strong>, in parallel with the parse: the JMC toolkit reads the recording file directly and needs nothing the parse writes, so the profile answers with its findings the moment it is ready. The results are cached with the profile and the page draws them once; nothing polls.</p>
      <p>When the cache is empty &mdash; a profile created before this behaviour existed, or a run that failed &mdash; the page opens on a <strong>Run Analysis</strong> button and shows a running state while the rules evaluate. The run reads every JFR file of the recording &mdash; all the chunks of a downloaded session, the same set the import read &mdash; so its findings describe the whole run. A run that flagged nothing caches an empty result and reads as <em>nothing flagged</em>, which is a different thing from a run that never happened.</p>
      <p>The rules need the recording's JFR files on disk. A profile made from a heap dump, a pprof or an OTLP file has none, and a recording with even one chunk missing from Microscope's recording storage is treated the same way: the rule set does not run on import and cannot be run later, so the page has no findings to show. Re-import the recording to get them back.</p>

      <h2 id="results-overview">Results Overview</h2>
      <p>A donut of the rules by severity, with a legend. Three severities appear: <strong>Warning</strong> (something to act on), <strong>Information</strong> (worth knowing, not necessarily wrong), and <strong>OK</strong> (the rule evaluated and found nothing). The share of warnings is the fastest read of a recording&rsquo;s health, and it is the same verdict the <router-link to="/docs/microscope/profiles#summary-dashboard">Summary dashboard</router-link> shows as a chip.</p>

      <h2 id="rules-table">Rules Table</h2>
      <p>Every evaluated rule, sorted with warnings first. The columns are the rule&rsquo;s name, its <strong>Subsystem</strong> (JMC&rsquo;s topic &mdash; Garbage Collection, Exceptions, Lock Instances, JVM Information and so on), and a <strong>Severity</strong> bar carrying the rule&rsquo;s score from 0 to 100. The score is the rule&rsquo;s own confidence that the pattern is significant, which is why two warnings can differ: a 95 is a pattern the rule is sure about, a 30 is a threshold barely crossed.</p>
      <p>The toolbar searches by rule name and filters by severity and by subsystem, so a GC investigation can hide everything that is not garbage collection. Clicking a row expands it.</p>

      <h2 id="reading-a-finding">Reading a Finding</h2>
      <p>An expanded row shows the three texts the rule wrote:</p>
      <ul>
        <li><strong>Summary</strong> &mdash; the finding in one sentence, with the figures that triggered it.</li>
        <li><strong>Explanation</strong> &mdash; why the pattern costs something, and what the rule compared to decide.</li>
        <li><strong>Solution</strong> &mdash; the change JMC suggests: a flag, a configuration, a code pattern to avoid.</li>
      </ul>

      <DocsCallout type="tip">
        <strong>Treat a finding as a pointer, not a verdict.</strong> The rules are heuristics over thresholds, tuned for a typical server workload. A warning names the subsystem to open &mdash; the <router-link to="/docs/microscope/profiles/garbage-collection">GC pages</router-link>, <router-link to="/docs/microscope/profiles/exceptions">Exceptions</router-link>, <router-link to="/docs/microscope/profiles/blocking-operations">Blocking Operations</router-link> &mdash; where the timeline and the stack traces say whether it matters for this application.
      </DocsCallout>

      <h2 id="elsewhere">The Same Findings Elsewhere</h2>
      <p>The findings are not confined to this page. The Summary dashboard shows the verdict as a health chip that links here. The <router-link to="/docs/intellij-plugin">IntelliJ plugin</router-link>&rsquo;s recording panel lists one line per finding under the profile&rsquo;s figures. And over the <router-link to="/docs/microscope-mcp">MCP server</router-link>, <code>profiles_summary</code> carries them as <code>topFindings</code> and <code>jvm_autoAnalysis</code> returns the full list &mdash; with a <code>compute</code> flag to run the rule set on a profile whose cache is empty &mdash; each as a finding record whose <code>category</code> is the subsystem shown here and whose <code>nextTool</code> names the family to open next.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
