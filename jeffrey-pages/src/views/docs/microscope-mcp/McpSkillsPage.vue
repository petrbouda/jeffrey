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
  { id: 'why-skills-at-all', text: 'Why Skills at All', level: 2 },
  { id: 'analyze-jfr', text: 'analyze-jfr', level: 2 },
  { id: 'analyze-heap', text: 'analyze-heap', level: 2 },
  { id: 'advise-jfr', text: 'advise-jfr', level: 2 },
  { id: 'jfr-sql', text: 'jfr-sql', level: 2 },
  { id: 'heap-sql', text: 'heap-sql', level: 2 },
  { id: 'invoking-one-directly', text: 'Invoking One Directly', level: 2 },
  { id: 'what-they-deliberately-omit', text: 'What They Deliberately Omit', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const invoke = `/microscope:analyze-jfr
/microscope:analyze-heap
/microscope:advise-jfr
/microscope:jfr-sql
/microscope:heap-sql`;

const advisePrompt = `advise on the most recent Jeffrey profile - what should I change in this repo?
/microscope:advise-jfr 019f885e-8e69-7d65-8ac7-32a70b92cb94 alloc`;

const eventsView = `-- correct
SELECT event_type, COUNT(*) FROM events GROUP BY event_type

-- silently returns truncated JSON in \`fields\`
SELECT event_type, COUNT(*) FROM events_raw GROUP BY event_type`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Skills"
      icon="bi bi-mortarboard"
    />

    <div class="docs-content">
      <p>The <router-link to="/docs/microscope-mcp/plugin">plugin</router-link> ships five skills. Claude loads one on its own when a question calls for it; you can also invoke any of them directly. Registering the MCP server by hand gives you the tools but not these.</p>

      <h2 id="why-skills-at-all">Why Skills at All</h2>
      <p>Most of what a model needs in order to <em>read</em> Jeffrey&rsquo;s output already travels with the output. Every flamegraph and trace export opens with a preamble that defines what <code>self</code> means against <code>total</code>, what the frame tags mean, what was pruned, and how to analyse that particular event type. Nothing needs to repeat that, and a skill that did would go stale the moment the preamble changed.</p>

      <p>Three things do <em>not</em> travel with any tool output, and those are what the skills carry:</p>
      <ul>
        <li><strong>Where to start.</strong> Nothing in a tool list says that <code>profiles_list</code> comes first, or that <code>profiles_features</code> saves three dead ends.</li>
        <li><strong>What to do about a hotspot.</strong> An export ends where the profile ends, at a call path and a percentage. Getting from there to an edit in the repository &mdash; and knowing when not to trust the mapping &mdash; is a workflow, not a reading instruction.</li>
        <li><strong>The two database schemas.</strong> Jeffrey&rsquo;s in-app assistant is given the JFR and heap-dump schemas in its system prompt. An external client never sees that prompt, so without a skill it would be guessing at column names.</li>
      </ul>

      <h2 id="analyze-jfr">analyze-jfr</h2>
      <p><em>Orientation.</em> Loaded whenever the question is &ldquo;why is this slow&rdquo;, &ldquo;where does the time go&rdquo;, &ldquo;what is allocating&rdquo;, &ldquo;what is holding memory&rdquo;, or when a Jeffrey profile, a JFR recording or a heap dump is mentioned.</p>

      <p>It carries the entry sequence &mdash; <code>profiles_list</code>, then <code>profiles_features</code>, then the family that matches the question &mdash; a map of the six families to the questions each answers, when to start instead from <code>recordings_analyzeFile</code> because the user named a file Jeffrey has never seen, the rule that every scoped tool takes a <code>profileId</code>, which flamegraph to pick for CPU versus allocation versus lock contention versus wall-clock, the order to work a latency question in traces, and what a failure means (a <code>404</code> means the server was switched off, not a bug).</p>

      <p>It also tells the model to <strong>ground its claims</strong>: the exports contain call paths and numbers, not source locations, so file and line numbers must be read from the repository rather than inferred from a profile.</p>

      <h2 id="analyze-heap">analyze-heap</h2>
      <p><em>A heap dump end to end.</em> Loaded when the question is &ldquo;what is holding memory&rdquo;, &ldquo;why is the heap growing&rdquo;, &ldquo;why did this OOM&rdquo;, &ldquo;what is leaking&rdquo;, or when retained size, a dominator tree, GC roots or a <code>.hprof</code> file are mentioned.</p>

      <p><code>heap_</code> is the largest family, and the only one whose tools have to be run in an order. Half of its reports say &ldquo;this analysis may need to be run first&rdquo; without saying which one, and nothing in a tool list explains what that means. The skill carries the order and the three rules that decide every heap answer:</p>
      <ul>
        <li><strong>Shallow is not retained.</strong> Shallow size is the object itself; retained size is what dies with it. Only the second answers &ldquo;who is holding this memory&rdquo; &mdash; a histogram ranked by shallow size tells you what there is a lot of, not who is responsible for it.</li>
        <li><strong>The dominator tree is built lazily.</strong> <code>dominator</code> and <code>retained_size</code> stay empty until <code>heap_getDominatorTreeRoots</code> runs, so every retained figure is missing rather than zero. Calling it once, early, is what makes retained sizes appear, and skipping it is the usual reason a heap session stalls on empty results.</li>
        <li><strong>Six reports are pre-computed in the UI.</strong> Leak Suspects, Biggest Objects, Class Loader Analysis, Top Consumers, String Analysis and Collection Analysis are computed when someone opens them in Jeffrey and only <em>read</em> over MCP; until then their tools answer &ldquo;has not been run yet&rdquo;. The skill names which tools those are, tells the model to hand the user the <code>profiles_link</code> URL and the report to run instead of retrying, and gives an on-demand route for the same question &mdash; the dominator tree into <code>heap_getPathToGCRoot</code> &mdash; so the answer does not wait on the UI.</li>
      </ul>

      <p>On top of that it carries the routes &mdash; the histogram and top consumers for what is using the heap, leak suspects into a GC-root path for what is leaking, <code>heap_getClassLoaderLeakChains</code> for the redeploy case that leaves a class loader behind, string and collection analysis for waste, and instance browsing for one particular class &mdash; plus how to enter from a <code>.hprof</code> file Jeffrey has never seen, and what the two guard messages mean when a profile turns out to have no heap dump or an index that is still being built.</p>

      <p>It grounds claims the way <code>analyze-jfr</code> does: cite the class name, the retained bytes and the GC-root path together, never carry an object id between dumps, and say whether one dump is being read as a leak or as a large working set &mdash; a single dump cannot tell those apart.</p>

      <h2 id="advise-jfr">advise-jfr</h2>
      <p><em>From a profile to a code change.</em> Loaded when the question is &ldquo;what should I change&rdquo;, &ldquo;optimise this&rdquo;, or when a hotspot has been found and the next question is what to do about it. It is the successor of the in-app Profile Advisor: the same job, done by the agent that is already in your checkout and can build, test and re-profile, instead of by a model given a read-only view of one folder.</p>
      <DocsCodeBlock :code="advisePrompt" language="bash" />

      <p>It takes an optional argument &mdash; a profile id or a recording file, then one of <code>cpu</code>, <code>wall</code>, <code>alloc</code>, <code>lock</code> to narrow the analysis to one group &mdash; and works in two phases with a stop between them, <strong>recommend</strong>, then <strong>change</strong>, tracked as a checklist so the gate is visible. It carries what neither the exports nor the tool list say:</p>
      <ul>
        <li><strong>The commit check.</strong> <code>profiles_get</code> reports the commit the profiled build came from when the recording was tagged with one. The skill compares it with <code>HEAD</code> before mapping a single frame, and says so when they differ or when the commit is unknown &mdash; a profile of another commit describes code that may no longer exist.</li>
        <li><strong>The four groups.</strong> CPU, wall-clock, allocation and blocking, each with the event type that answers it and the fallback when a recording carries an older one (the TLAB pair for allocation; monitor-wait and park for blocking), weighted by bytes or nanoseconds where that is the meaningful ranking. A group with no samples is reported with the profiler flag that would capture it next time.</li>
        <li><strong>The grounding rules.</strong> Never name a file, method or line that was not read; tie every finding to a frame and its share from the export; prefer a few high-impact findings over many speculative ones; say when a hotspot cannot be located rather than guessing.</li>
        <li><strong>The output shape</strong> of the recommendation &mdash; a summary, one section per file and method with the cause, the measured share and the proposed change in prose &mdash; and the gate: nothing is edited until the recommendation has been read and a finding has been accepted.</li>
        <li><strong>The verification loop.</strong> The smallest edit that implements the finding, the project&rsquo;s own build and tests, and where the recording can be reproduced, a re-run analysed with <code>recordings_analyzeFile</code> and exported with identical parameters so the delta is real. A saving that was not measured is capped at the frame&rsquo;s own share, since a change cannot save more time than the frame used.</li>
      </ul>

      <p>What it deliberately does not carry is how to read a CPU, allocation or blocking graph. Every export already opens with an analysis section written for its event type, so the skill says to follow that document rather than restating it.</p>

      <h2 id="jfr-sql">jfr-sql</h2>
      <p><em>The profile database.</em> Loaded when a question needs <code>jfr_executeQuery</code> or <code>jfr_queryEvents</code> because no purpose-built tool covers it.</p>

      <p>It carries the schema &mdash; <code>events</code>, <code>event_types</code>, <code>threads</code>, <code>stacktraces</code>, <code>frames</code> &mdash; and the handful of idioms that separate a working query from a wrong one: durations are nanoseconds; event-specific data lives in a JSON <code>fields</code> column and must be cast before a numeric comparison; stacks are frame-hash arrays to <code>UNNEST</code> and join; a JEP 371 hidden class is found with <code>hidden_class_id IS NOT NULL</code>, not a <code>LIKE</code>.</p>

      <p>And the trap that has no error message:</p>
      <DocsCodeBlock :code="eventsView" language="sql" />

      <DocsCallout type="info" title="It also says: do not guess column names">
        <code>jfr_describeTable('events')</code> is one call. The duration column is <code>duration</code> &mdash; not <code>duration_ns</code>, not <code>duration_ms</code>.
      </DocsCallout>

      <h2 id="heap-sql">heap-sql</h2>
      <p><em>The heap-dump index.</em> Loaded when <code>heap_executeQuery</code> is needed because the purpose-built heap tools do not answer the question.</p>

      <p>It carries the index schema &mdash; <code>class</code>, <code>instance</code>, <code>outbound_ref</code>, <code>gc_root</code>, <code>dominator</code>, <code>retained_size</code>, <code>string</code>, <code>dump_metadata</code> &mdash; with the details that are not guessable: <code>dominator</code> and <code>retained_size</code> are built lazily and are empty until something asks for them; <code>class.name</code> is already dot-notation; <code>record_kind</code> is a small integer enum; and the <code>string</code> table is the HPROF UTF-8 <em>name</em> pool, not the contents of Java <code>String</code> instances.</p>

      <p>It opens by pointing back at <code>analyze-heap</code> and saying to try the purpose-built tools first &mdash; several are pre-computed reports, and reproducing one in SQL is slower and easier to get wrong. This skill is the escape hatch for what they do not cover, not the way in.</p>

      <h2 id="invoking-one-directly">Invoking One Directly</h2>
      <p>Each skill is also a slash command, namespaced by the plugin:</p>
      <DocsCodeBlock :code="invoke" language="bash" />

      <p>Useful when you want the schema in front of you before asking a question, or when Claude has gone off in a direction the skill would have corrected.</p>

      <h2 id="what-they-deliberately-omit">What They Deliberately Omit</h2>
      <p>The skills stay short on purpose. They do not restate frame tags, the bullet grammar of an export, pruning semantics, or what <code>self</code> means &mdash; every export says all of that itself, in the version that matches the code that produced it. A skill repeating it would be a second source of truth, and the one that drifts.</p>

      <p>If you are writing your own tooling against the server rather than using the plugin, the same division applies: read the preamble each export gives you, and treat the two schema skills as the reference for raw SQL.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
