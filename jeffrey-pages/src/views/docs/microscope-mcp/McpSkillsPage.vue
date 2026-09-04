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
  { id: 'compare-jfr', text: 'compare-jfr', level: 2 },
  { id: 'advise-jfr', text: 'advise-jfr', level: 2 },
  { id: 'jfr-sql', text: 'jfr-sql', level: 2 },
  { id: 'heap-sql', text: 'heap-sql', level: 2 },
  { id: 'the-analyst', text: 'The Analyst They Delegate To', level: 2 },
  { id: 'invoking-one-directly', text: 'Invoking One Directly', level: 2 },
  { id: 'what-they-deliberately-omit', text: 'What They Deliberately Omit', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const invoke = `/microscope:analyze-jfr
/microscope:analyze-heap
/microscope:compare-jfr
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
      <p>The <router-link to="/docs/microscope-mcp/plugin">plugin</router-link> ships six skills and <router-link to="/docs/microscope-mcp/agent">one subagent</router-link>. Claude loads one on its own when a question calls for it; you can also invoke any of them directly. Registering the MCP server by hand gives you the tools but not these.</p>

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

      <p>It carries the entry sequence &mdash; <code>profiles_list</code>, then <code>profiles_features</code>, then the family that matches the question &mdash; a map of the fifteen families to the questions each answers, when to start instead from <code>recordings_analyzeFile</code> because the user named a file Jeffrey has never seen, the rule that every scoped tool takes a <code>profileId</code>, which flamegraph to pick for CPU versus allocation versus lock contention versus wall-clock, the order to work a latency question in traces, and what a failure means (a <code>404</code> means the server was switched off, not a bug).</p>

      <p>It also routes the machine-level questions to the <router-link to="/docs/microscope-mcp/tools#jvm"><code>jvm_</code></router-link> family, which <code>flamegraph_list</code> never offers and a tool list alone does not explain the order of: <code>jvm_sections</code> first, then the dashboard that matches. What the skill adds on top of the tools is what to do with the answer. The cause of GC is allocation, so a pause budget that shows GC matters ends in the allocation flamegraph &mdash; the only thing that names the code producing the garbage. &ldquo;GC looks fine and we still have pauses&rdquo; is <code>jvm_safepoints</code>, not a deeper GC query. An empty compilation list means nothing compiled <em>slowly</em>, not that nothing compiled. And no flag should be proposed before <code>jvm_flags</code> says where each value came from &mdash; a default, the command line, or the JVM&rsquo;s own ergonomics, which is what separates a flag somebody set from one the machine chose.</p>

      <p>Three more families the skill routes into, each answering something a flamegraph structurally cannot. The <router-link to="/docs/microscope-mcp/tools#technologies">technology dashboards</router-link> answer for the edges of the application: &ldquo;this endpoint is slow&rdquo; starts at <code>http_overview</code> and <code>jdbc_overview</code>, two calls, before any frame is read &mdash; and requests that are slow while every statement is fast are waiting for a connection, which is <code>jdbc_pools</code> and nothing else. <router-link to="/docs/microscope-mcp/tools#waiting"><code>io_</code> and <code>blocking_</code></router-link> hold the time spent waiting rather than running, which produces no samples at all: a blocked thread is not on-CPU, so a flamegraph reports the application as idle. And <router-link to="/docs/microscope-mcp/tools#timeline"><code>timeline_</code></router-link> answers <em>when</em>: a flamegraph of a whole recording averages a spike away, so the skill has the model find the window first and export it second.</p>

      <p>The skill leans on something the tools now do themselves. Every answer comes back with a <code>nextSteps</code> list saying what that result cannot tell you and which tool can, so the routing survives the many turns between reading a tool description and needing it. The skill tells the model to follow those lines &mdash; they route and never diagnose, so following one is never the same as accepting a verdict.</p>

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

      <h2 id="compare-jfr">compare-jfr</h2>
      <p><em>Before against after.</em> Loaded when the question is &ldquo;did my change make it slower&rdquo;, &ldquo;what got faster&rdquo;, &ldquo;compare these two runs&rdquo;, or when a baseline, a before/after or a performance regression is mentioned. It is the question a session in your own checkout actually has &mdash; the agent holds the code diff, and Jeffrey holds the behaviour diff &mdash; and the one no single-profile tool can answer.</p>

      <p>Most of the skill is spent on the failure mode that makes this analysis worse than useless. Any two recordings can be subtracted, and the result always looks like a finding; whether it <em>is</em> one depends on facts the deltas do not show, and nothing in a JFR file proves them. So it carries:</p>
      <ul>
        <li><strong>Comparability first, as a real result.</strong> <code>compare_list</code> before anything else, and &ldquo;these two runs are not comparable&rdquo; reported as a finding rather than worked around &mdash; a far better answer than a confident regression that was really a recording twice as long. It names the three cases to stop on: different recording lengths, an event type only one side recorded (a profiler-configuration difference, not a change in the application), and nothing in common at all.</li>
        <li><strong>The direction.</strong> The after run is the <code>profileId</code> and the before run is the <code>baselineProfileId</code>. Backwards, every regression reads as an improvement.</li>
        <li><strong>Ranked first, tree second.</strong> <code>compare_movements</code> attributes by self weight, so a change is charged to the method that moved rather than to every caller above it; <code>compare_flamegraph</code> follows one movement down its call paths. Pruning there is by movement, so absence means &ldquo;did not move&rdquo; &mdash; the opposite of what it means in a single-profile export.</li>
        <li><strong>What a rename looks like.</strong> A renamed or extracted method appears once as new and once as gone, of near-identical size, and reads as two dramatic findings. The skill has the model check the source diff &mdash; which it has and the profile does not &mdash; before reporting either half.</li>
        <li><strong>The limits, stated.</strong> Share and delta answer different questions and must be quoted as the one they are; one pair of recordings cannot separate a 5% move from run-to-run variance; and one event type&rsquo;s distribution is not a wall-clock benchmark, so a shifted CPU profile is never evidence that the application got faster end to end.</li>
      </ul>

      <p>It ends where <code>advise-jfr</code> begins: the profile says where, never why, so the located movements are mapped onto the actual diff with the real source read first.</p>

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

      <p>It keeps the GC and JIT queries as the escape hatch behind the <code>jvm_</code> dashboards &mdash; collections ranked by <code>sumOfPauses</code>, the <code>jdk.GCHeapSummary</code> pivot that turns two rows per <code>gcId</code> into reclaimed bytes and a live-set trend, deoptimisations grouped by method <em>and</em> reason &mdash; for the follow-up a dashboard does not shape. And it documents the two <code>event_types</code> columns <code>jfr_listEventTypes</code> does not return: <code>columns</code>, which is the declared field list of an event type and so the end of guessing at key names, and <code>settings</code>, which settles whether an event was switched off or simply never crossed its threshold.</p>

      <p>And the trap that has no error message:</p>
      <DocsCodeBlock :code="eventsView" language="sql" />

      <DocsCallout type="info" title="It also says: do not guess column names">
        <code>jfr_describeTable('events')</code> is one call. The duration column is <code>duration</code> &mdash; not <code>duration_ns</code>, not <code>duration_ms</code>.
      </DocsCallout>

      <h2 id="heap-sql">heap-sql</h2>
      <p><em>The heap-dump index.</em> Loaded when <code>heap_executeQuery</code> is needed because the purpose-built heap tools do not answer the question.</p>

      <p>It carries the index schema &mdash; <code>class</code>, <code>instance</code>, <code>outbound_ref</code>, <code>gc_root</code>, <code>dominator</code>, <code>retained_size</code>, <code>string</code>, <code>dump_metadata</code> &mdash; with the details that are not guessable: <code>dominator</code> and <code>retained_size</code> are built lazily and are empty until something asks for them; <code>class.name</code> is already dot-notation; <code>record_kind</code> is a small integer enum; and the <code>string</code> table is the HPROF UTF-8 <em>name</em> pool, not the contents of Java <code>String</code> instances.</p>

      <p>It opens by pointing back at <code>analyze-heap</code> and saying to try the purpose-built tools first &mdash; several are pre-computed reports, and reproducing one in SQL is slower and easier to get wrong. This skill is the escape hatch for what they do not cover, not the way in.</p>

      <h2 id="the-analyst">The Analyst They Delegate To</h2>
      <p>Three of the skills do not read the big documents themselves. A single <code>flamegraph_export</code> can run to 120,000 characters, and a question worth asking usually takes several &mdash; four of them in <code>advise-jfr</code>, one per group. Pulled into the session, they leave little room for the thing that has to happen next: reading the actual source behind the frames.</p>

      <p>So the plugin ships a subagent, <code>microscope:profile-analyst</code>, and the skills hand it the reading. It runs the sequence, follows the profile where it leads &mdash; deeper into a subtree, a lower threshold on one path, the GC-root path of the class the histogram named &mdash; and returns the findings alone. What it read stays in its context.</p>

      <p>What it is not allowed to do is as much of the design as what it does: no file access and no <code>recordings_</code>, so it cannot map a frame to a line, edit anything, or build a profile. Mapping onto the checkout, the recommendation, and every question put to you stay in the session, where you can answer them. <router-link to="/docs/microscope-mcp/agent">The subagent reference</router-link> has the full contract &mdash; what it is given, the report shape it returns, and when to read an export yourself instead.</p>

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
