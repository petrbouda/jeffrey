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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'which-skill', text: 'Which Skill Answers Your Question', level: 2 },
  { id: 'why-skills-at-all', text: 'Why Skills at All', level: 2 },
  { id: 'analyze-jfr', text: 'analyze-jfr', level: 2 },
  { id: 'analyze-heap', text: 'analyze-heap', level: 2 },
  { id: 'analyze-hub', text: 'analyze-hub', level: 2 },
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

const askExamples = `# each of these loads a skill on its own - no slash command needed
"why is the checkout endpoint slow?"                     -> analyze-jfr
"analyze target/run.jfr and tell me where the time goes" -> analyze-jfr
"what is holding memory in this heap dump?"              -> analyze-heap
"analyse what production recorded in the last hour"      -> analyze-hub
"did my change make it slower?"                          -> compare-jfr
"what should I change in this repo to fix it?"           -> advise-jfr
"how many events of each type are in the recording?"     -> jfr-sql`;

const invoke = `# Claude Code
/microscope:analyze-jfr
/microscope:analyze-heap
/microscope:analyze-hub
/microscope:compare-jfr
/microscope:advise-jfr
/microscope:jfr-sql
/microscope:heap-sql

# Codex
$analyze-jfr
$analyze-heap
$analyze-hub
$compare-jfr
$advise-jfr
$jfr-sql
$heap-sql`;

const advisePrompt = `advise on the most recent Jeffrey profile - what should I change in this repo?
/microscope:advise-jfr 019f885e-8e69-7d65-8ac7-32a70b92cb94 alloc`;

const skillFrontmatter = `---
name: analyze-jfr
description: Analyses a JVM profile held by a running Jeffrey Microscope - CPU, ...
---`;

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
      <p>The plugin ships seven skills and <router-link to="/docs/microscope-mcp/agent">one analyst agent</router-link>. The client loads a skill on its own when a question calls for it; you can also invoke any of them directly. Registering the MCP server by hand gives you the tools but not these.</p>

      <p>The six are <a href="https://agentskills.io/specification" target="_blank" rel="noopener">Agent Skills</a> &mdash; one directory each, a <code>SKILL.md</code> with two frontmatter fields, and a body:</p>
      <DocsCodeBlock :code="skillFrontmatter" language="yaml" />

      <p>That format is shared, so <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> and <router-link to="/docs/microscope-mcp/codex">Codex</router-link> load the same files out of the same directory rather than each getting a copy. Everything on this page applies to both; only the way you invoke one by hand differs.</p>

      <h2 id="which-skill">Which Skill Answers Your Question</h2>
      <p>Every row is a question you would actually type. You do not pick from this table &mdash; the agent does, from the same descriptions &mdash; but it is the fastest way to see what the seven cover between them, and what each one needs before it can start.</p>
      <table class="skill-chooser">
        <thead>
          <tr>
            <th>You are asking</th>
            <th>Skill</th>
            <th>Needs</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>&ldquo;Why is this slow?&rdquo; &middot; &ldquo;Where does the time go?&rdquo; &middot; &ldquo;What is allocating?&rdquo;</td>
            <td><a href="#analyze-jfr"><code>analyze-jfr</code></a></td>
            <td>One JFR profile</td>
          </tr>
          <tr>
            <td>&ldquo;What is holding memory?&rdquo; &middot; &ldquo;Why did this OOM?&rdquo; &middot; &ldquo;What is leaking?&rdquo;</td>
            <td><a href="#analyze-heap"><code>analyze-heap</code></a></td>
            <td>A heap dump</td>
          </tr>
          <tr>
            <td>&ldquo;What did production record in the last hour?&rdquo; &middot; &ldquo;Why was staging slow this morning?&rdquo;</td>
            <td><a href="#analyze-hub"><code>analyze-hub</code></a></td>
            <td>A connected Jeffrey Hub</td>
          </tr>
          <tr>
            <td>&ldquo;Did my change make it slower?&rdquo; &middot; &ldquo;What got faster?&rdquo;</td>
            <td><a href="#compare-jfr"><code>compare-jfr</code></a></td>
            <td>Two profiles, before and after</td>
          </tr>
          <tr>
            <td>&ldquo;What should I change in this repo to fix it?&rdquo;</td>
            <td><a href="#advise-jfr"><code>advise-jfr</code></a></td>
            <td>A profile, and the checkout it was built from</td>
          </tr>
          <tr>
            <td>&ldquo;How many events of each type are there?&rdquo; &mdash; anything no purpose-built tool covers</td>
            <td><a href="#jfr-sql"><code>jfr-sql</code></a></td>
            <td>One JFR profile</td>
          </tr>
          <tr>
            <td>The heap tools do not answer it, and it needs raw SQL</td>
            <td><a href="#heap-sql"><code>heap-sql</code></a></td>
            <td>A heap dump, indexed</td>
          </tr>
        </tbody>
      </table>

      <h2 id="why-skills-at-all">Why Skills at All</h2>
      <p>Most of what a model needs in order to <em>read</em> Jeffrey&rsquo;s output already travels with the output. Every flamegraph and trace export opens with a preamble that defines what <code>self</code> means against <code>total</code>, what the frame tags mean, what was pruned, and how to analyse that particular event type. Nothing needs to repeat that, and a skill that did would go stale the moment the preamble changed.</p>

      <p>Three things do <em>not</em> travel with any tool output, and those are what the skills carry:</p>
      <ul>
        <li><strong>Where to start.</strong> Nothing in a tool list says that <code>profiles_list</code> comes first, or that <code>profiles_features</code> saves three dead ends.</li>
        <li><strong>What to do about a hotspot.</strong> An export ends where the profile ends, at a call path and a percentage. Getting from there to an edit in the repository &mdash; and knowing when not to trust the mapping &mdash; is a workflow, not a reading instruction.</li>
        <li><strong>The two database schemas.</strong> Jeffrey&rsquo;s in-app assistant is given the JFR and heap-dump schemas in its system prompt. An external client never sees that prompt, so without a skill it would be guessing at column names.</li>
      </ul>

      <h2 id="analyze-jfr">analyze-jfr</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;Why is the checkout endpoint slow, and where does the time actually go?&rdquo;</p>
          <span class="skill-role">Orientation</span>
        </div>

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li><code>profiles_list</code>, then <code>profiles_features</code>, then the family that matches the question. The middle call is the cheap one: it rules out a whole family before it is tried, and a tool list nowhere says to make it.</li>
            <li>Start instead from <code>recordings_analyzeFile</code> when the user named a file Jeffrey has never seen.</li>
            <li>Every scoped tool takes a <code>profileId</code>, and it is required. A <code>404</code> means the server was switched off, not that something is broken.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li>Which flamegraph answers which question &mdash; CPU against allocation against lock contention against wall-clock &mdash; and the order to work a latency question in traces.</li>
            <li>That the cause of GC is allocation. A pause budget showing that GC matters therefore ends in the allocation flamegraph, the only thing that names the code producing the garbage.</li>
            <li>That &ldquo;GC looks fine and we still have pauses&rdquo; is <code>jvm_safepoints</code>, not a deeper GC query &mdash; and that an empty compilation list means nothing compiled <em>slowly</em>, not that nothing compiled.</li>
            <li>That no flag is proposed before <code>jvm_flags</code> says where its value came from: a default, the command line, or the JVM&rsquo;s own ergonomics. That is what separates a flag somebody set from one the machine chose.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>Routes into</h4>
          <ul>
            <li><router-link to="/docs/microscope-mcp/tools#jvm"><code>jvm_</code></router-link> for the machine underneath, which <code>flamegraph_list</code> never offers and a tool list does not explain the order of: <code>jvm_sections</code> first, then the dashboard that matches.</li>
            <li>The <router-link to="/docs/microscope-mcp/tools#technologies">technology dashboards</router-link> for the edges of the application. &ldquo;This endpoint is slow&rdquo; starts at <code>http_overview</code> and <code>jdbc_overview</code> &mdash; two calls, before a single frame is read. Requests that are slow while every statement is fast are waiting for a connection, which is <code>jdbc_pools</code> and nothing else.</li>
            <li><router-link to="/docs/microscope-mcp/tools#waiting"><code>io_</code> and <code>blocking_</code></router-link> for time spent waiting rather than running. That time produces no samples at all: a blocked thread is not on-CPU, so a flamegraph reports the application as idle.</li>
            <li><router-link to="/docs/microscope-mcp/tools#timeline"><code>timeline_</code></router-link> for <em>when</em>. A flamegraph of a whole recording averages a spike away, so the skill has the model find the window first and export it second.</li>
            <li>Whatever each answer&rsquo;s own <code>nextSteps</code> list names. Every tool result says what it cannot tell you and which tool can, so the routing survives the many turns between reading a tool description and needing it. Those lines route and never diagnose &mdash; following one is not the same as accepting a verdict.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Trap</span>
          <p>An export contains call paths and numbers, not source locations. Every file and line number must be read from the repository; inferring one from a profile is how a confident answer ends up pointing at code that does not exist.</p>
        </aside>
      </div>

      <h2 id="analyze-heap">analyze-heap</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;The heap keeps growing &mdash; what is holding it, and is this a leak?&rdquo;</p>
          <span class="skill-role">A heap dump end to end</span>
        </div>

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li><code>heap_</code> is the largest family and the only one whose tools have to be run in an order. Half its reports say &ldquo;this analysis may need to be run first&rdquo; without saying which one.</li>
            <li><code>heap_getDominatorTreeRoots</code> once, early. <code>dominator</code> and <code>retained_size</code> are built lazily, so before it runs every retained figure is <em>missing</em> rather than zero &mdash; and skipping it is the usual reason a heap session stalls on empty results.</li>
            <li>How to enter from a <code>.hprof</code> file Jeffrey has never seen, and what the two guard messages mean when a profile turns out to have no heap dump, or an index that is still being built.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li><strong>Shallow is not retained.</strong> Shallow size is the object itself; retained size is what dies with it. Only the second answers &ldquo;who is holding this memory&rdquo; &mdash; a histogram ranked by shallow size tells you what there is a lot of, not who is responsible for it.</li>
            <li><strong>Six reports are pre-computed in the UI.</strong> Leak Suspects, Biggest Objects, Class Loader Analysis, Top Consumers, String Analysis and Collection Analysis are computed when someone opens them in Jeffrey and only <em>read</em> over MCP; until then their tools answer &ldquo;has not been run yet&rdquo;. The skill names which tools those are and has the model hand you the <code>profiles_link</code> URL and the report to run, instead of retrying.</li>
            <li>That the same question has an on-demand route which does not wait on the UI: the dominator tree into <code>heap_getPathToGCRoot</code>.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>Routes into</h4>
          <ul>
            <li>The histogram and top consumers for what is using the heap; leak suspects into a GC-root path for what is leaking.</li>
            <li><code>heap_getClassLoaderLeakChains</code> for the redeploy case that leaves a class loader behind.</li>
            <li>String and collection analysis for waste, and instance browsing when the question is about one particular class.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Trap</span>
          <p>One dump cannot separate a leak from a large working set, so the skill makes the model say which reading it is offering. Object ids are never carried between dumps, and a finding is cited as class name, retained bytes and GC-root path together &mdash; any one of the three alone is unfalsifiable.</p>
        </aside>
      </div>

      <h2 id="analyze-hub">analyze-hub</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;Analyse what production recorded in the last hour.&rdquo;</p>
          <span class="skill-role">Retrieval</span>
        </div>

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li><code>hubs_sessions</code> with a window &mdash; one call, across every connected hub &mdash; then <code>hubs_download</code> on the row&rsquo;s <code>session_ref</code>, then <code>recordings_analyzeRecording</code> for the <code>profileId</code>.</li>
            <li>From there it hands off: <code>analyze-jfr</code> for a recording, <code>analyze-heap</code> for a dump. There is no hub-specific analysis, because a downloaded session is an ordinary profile.</li>
            <li>No <code>hubs_</code> tool advertised means hub access is off or no hub is connected &mdash; a fact to report, not a path to guess at.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li>That the hub, workspace and project hierarchy is a set of filters and columns, never a sequence of questions. Asking &ldquo;which hub?&rdquo;, then &ldquo;which workspace?&rdquo;, then &ldquo;which project?&rdquo; costs three turns and asks for ids nobody knows by heart.</li>
            <li>When to involve you at all: not when one session obviously matches, and once &mdash; quoting project, duration and size &mdash; when several do or the download is large.</li>
            <li>To read the <code>local</code> column before downloading anything. A session already here needs no transfer, and one already analysed needs no work at all.</li>
            <li>That a session still recording is a normal thing to download, not an error to wait out: the transfer takes the chunks that have been rolled so far.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Trap</span>
          <p>An empty result is not the same as no recordings. A hub that did not answer is reported under the table, and the managers underneath report a hub that is down and a hub that is empty identically &mdash; so the answer to &ldquo;nothing came back&rdquo; is to read the footer, or call <code>hubs_list</code>, before telling anyone their recordings are missing.</p>
        </aside>
      </div>

      <h2 id="compare-jfr">compare-jfr</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;Did my change make it slower?&rdquo;</p>
          <span class="skill-role">Before against after</span>
        </div>

        <p>The question a session in your own checkout actually has &mdash; the agent holds the code diff, Jeffrey holds the behaviour diff &mdash; and the one no single-profile tool can answer. Most of the skill is spent on the failure mode that makes this analysis worse than useless: any two recordings can be subtracted, and the result always looks like a finding.</p>

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li><code>compare_list</code> before anything else, and <strong>&ldquo;these two runs are not comparable&rdquo; reported as a result</strong> rather than worked around. It is a far better answer than a confident regression that was really a recording twice as long.</li>
            <li>Three cases to stop on: different recording lengths, an event type only one side recorded (a profiler-configuration difference, not a change in the application), and nothing in common at all.</li>
            <li>Then <code>compare_movements</code> for the ranking, and <code>compare_flamegraph</code> to follow one movement down its call paths.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li><strong>The direction.</strong> The after run is the <code>profileId</code>; the before run is the <code>baselineProfileId</code>. Backwards, every regression reads as an improvement.</li>
            <li><strong>Ranked first, tree second.</strong> <code>compare_movements</code> attributes by self weight, so a change is charged to the method that moved rather than to every caller above it.</li>
            <li><strong>The limits, stated rather than assumed.</strong> Share and delta answer different questions and must be quoted as the one they are; one pair of recordings cannot separate a 5% move from run-to-run variance; and one event type&rsquo;s distribution is not a wall-clock benchmark, so a shifted CPU profile is never evidence that the application got faster end to end.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>Routes into</h4>
          <ul>
            <li><a href="#advise-jfr"><code>advise-jfr</code></a>, which is where it ends: the profile says where, never why, so the located movements are mapped onto the actual diff with the real source read first.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Two traps</span>
          <p>Pruning in <code>compare_flamegraph</code> is by <em>movement</em>, so an absent frame means &ldquo;did not move&rdquo; &mdash; the opposite of what absence means in a single-profile export. And a renamed or extracted method appears twice, once as new and once as gone, at near-identical size, reading as two dramatic findings; the skill has the model check the source diff &mdash; which it has and the profile does not &mdash; before reporting either half.</p>
        </aside>
      </div>

      <h2 id="advise-jfr">advise-jfr</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;What should I change in this repo to fix it?&rdquo;</p>
          <span class="skill-role">From a profile to a code change</span>
        </div>

        <p>The successor of the in-app Profile Advisor: the same job, done by the agent that is already in your checkout and can build, test and re-profile, instead of by a model given a read-only view of one folder.</p>
        <DocsCodeBlock :code="advisePrompt" language="bash" />

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li>An optional argument &mdash; a profile id or a recording file, then one of <code>cpu</code>, <code>wall</code>, <code>alloc</code>, <code>lock</code>, <code>latency</code>, <code>waiting</code> or <code>memory</code> &mdash; narrows the analysis to one area.</li>
            <li><code>profiles_get</code> first, for the commit the profiled build came from, compared against <code>HEAD</code> before a single frame is mapped. The skill says so out loud when they differ or the commit is unknown: a profile of another commit describes code that may no longer exist.</li>
            <li><code>profiles_features</code> next, so only what the profile actually carries gets analysed. Working every family unconditionally costs a dozen calls and buries the two findings that matter.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li><strong>Two phases with a stop between them</strong> &mdash; <em>recommend</em>, then <em>change</em> &mdash; tracked as a checklist so the gate is visible. Nothing is edited until the recommendation has been read and a finding accepted.</li>
            <li><strong>The output shape:</strong> a summary, then code findings, one section per file and method, each with the cause, the measured share and the proposed change in prose.</li>
            <li><strong>Configuration findings kept apart from code.</strong> A pool that ran out of connections, a flag left at an ergonomic default, a container quota the scheduler enforced: real findings, often the largest single win, and none of them a code change. Presenting one as an edit would misrepresent both the fix and the risk, so they are listed separately with the setting, its current value and the evidence &mdash; and verified differently, since a pool size takes effect on the next run of the application rather than the next test.</li>
            <li><strong>The verification loop:</strong> the smallest edit that implements the finding, the project&rsquo;s own build and tests, then &mdash; where the recording can be reproduced &mdash; a re-run analysed with <code>recordings_analyzeFile</code> and exported with identical parameters, so the delta is real. A saving that was not measured is capped at the frame&rsquo;s own share, since a change cannot save more time than the frame used.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>Routes into</h4>
          <ul>
            <li>The four flamegraph groups &mdash; CPU, wall-clock, allocation and blocking &mdash; each with the event type that answers it, the fallback when a recording carries an older one, and the weighting (bytes or nanoseconds) that makes the ranking meaningful.</li>
            <li>Beyond them: traces for latency and for one slow population, <code>blocking_</code> and <code>io_</code> for the waiting a flamegraph structurally cannot show, <code>memory_</code> for allocation by type, and the database and HTTP dashboards. A group with no samples is reported together with the profiler flag that would capture it next time.</li>
            <li>Each source was picked for <strong>how it reaches a line of code</strong>, because evidence that cannot reach source is not something the skill can act on: a span flamegraph gives frames for one span, a monitor class names the lock to find in the checkout, an I/O target names the dependency and the change is at the calling code, an allocated type goes back to the allocation export to find its site.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Trap</span>
          <p>Never name a file, method or line that was not read. Every finding is tied to a frame and its share from the export, a few high-impact findings are preferred over many speculative ones, and a hotspot that cannot be located is reported as such rather than guessed at.</p>
        </aside>
      </div>

      <p>What <code>advise-jfr</code> deliberately does not carry is how to read a CPU, allocation or blocking graph. Every export already opens with an analysis section written for its event type, so the skill says to follow that document rather than restating it.</p>

      <h2 id="jfr-sql">jfr-sql</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;How many events of each type are in the recording?&rdquo;</p>
          <span class="skill-role">The profile database</span>
        </div>

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li>Loaded when a question needs <code>jfr_executeQuery</code> or <code>jfr_queryEvents</code> because no purpose-built tool covers it.</li>
            <li><code>jfr_describeTable('events')</code> is one call, and it ends the guessing. The duration column is <code>duration</code> &mdash; not <code>duration_ns</code>, not <code>duration_ms</code>.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li>The schema &mdash; <code>events</code>, <code>event_types</code>, <code>threads</code>, <code>stacktraces</code>, <code>frames</code> &mdash; and the handful of idioms that separate a working query from a wrong one: durations are nanoseconds; event-specific data lives in a JSON <code>fields</code> column and must be cast before a numeric comparison; stacks are frame-hash arrays to <code>UNNEST</code> and join; a JEP 371 hidden class is found with <code>hidden_class_id IS NOT NULL</code>, not a <code>LIKE</code>.</li>
            <li>The two <code>event_types</code> columns <code>jfr_listEventTypes</code> does not return: <code>columns</code>, the declared field list of an event type and so the end of guessing at key names, and <code>settings</code>, which settles whether an event was switched off or simply never crossed its threshold.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>Routes into</h4>
          <ul>
            <li>The escape hatch behind the <router-link to="/docs/microscope-mcp/tools#jvm"><code>jvm_</code></router-link> dashboards, for the follow-up a dashboard does not shape: collections ranked by <code>sumOfPauses</code>, the <code>jdk.GCHeapSummary</code> pivot that turns two rows per <code>gcId</code> into reclaimed bytes and a live-set trend, and deoptimisations grouped by method <em>and</em> reason.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Trap, and it has no error message</span>
          <DocsCodeBlock :code="eventsView" language="sql" />
        </aside>
      </div>

      <h2 id="heap-sql">heap-sql</h2>
      <div class="skill-card">
        <div class="skill-head">
          <p class="skill-question">&ldquo;The heap tools do not answer this &mdash; can I query the index directly?&rdquo;</p>
          <span class="skill-role">The heap-dump index</span>
        </div>

        <section class="skill-block">
          <h4>Entry sequence</h4>
          <ul>
            <li>It opens by pointing back at <a href="#analyze-heap"><code>analyze-heap</code></a> and saying to try the purpose-built tools first. Several of them are pre-computed reports, and reproducing one in SQL is slower and easier to get wrong.</li>
            <li>Loaded when <code>heap_executeQuery</code> is genuinely needed. This skill is the escape hatch for what the tools do not cover, not the way in.</li>
          </ul>
        </section>

        <section class="skill-block">
          <h4>What it decides for you</h4>
          <ul>
            <li>The index schema &mdash; <code>class</code>, <code>instance</code>, <code>outbound_ref</code>, <code>gc_root</code>, <code>dominator</code>, <code>retained_size</code>, <code>string</code>, <code>dump_metadata</code> &mdash; with the details that are not guessable from the names.</li>
          </ul>
        </section>

        <aside class="skill-trap">
          <span class="skill-trap-label">Trap</span>
          <p><code>dominator</code> and <code>retained_size</code> are built lazily and stay empty until something asks for them. <code>class.name</code> is already dot-notation, so no conversion is needed. <code>record_kind</code> is a small integer enum. And the <code>string</code> table is the HPROF UTF-8 <em>name</em> pool &mdash; class and field names &mdash; not the contents of Java <code>String</code> instances.</p>
        </aside>
      </div>

      <h2 id="the-analyst">The Analyst They Delegate To</h2>
      <p>Three of the skills do not read the big documents themselves. A single <code>flamegraph_export</code> can run to 120,000 characters, and a question worth asking usually takes several &mdash; four of them in <code>advise-jfr</code>, one per group. Pulled into the session, they leave little room for the thing that has to happen next: reading the actual source behind the frames.</p>

      <p>So there is an analyst agent, and the skills hand it the reading &mdash; <code>microscope:profile-analyst</code> from the Claude Code plugin, or the custom agent a Codex user copies in. It runs the sequence, follows the profile where it leads &mdash; deeper into a subtree, a lower threshold on one path, the GC-root path of the class the histogram named &mdash; and returns the findings alone. What it read stays in its context.</p>

      <p>What it is not allowed to do is as much of the design as what it does: no file access and no <code>recordings_</code>, so it cannot map a frame to a line, edit anything, or build a profile. Mapping onto the checkout, the recommendation, and every question put to you stay in the session, where you can answer them. <router-link to="/docs/microscope-mcp/agent">The analyst reference</router-link> has the full contract &mdash; what it is given, the report shape it returns, and when to read an export yourself instead.</p>

      <h2 id="invoking-one-directly">Invoking One Directly</h2>
      <p>You do not normally have to. The agent loads the skill whose description matches the question, so plain English is enough:</p>
      <DocsCodeBlock :code="askExamples" language="bash" />

      <p>Each skill can also be named directly &mdash; a slash command namespaced by the plugin in Claude Code, a <code>$</code> name in Codex:</p>
      <DocsCodeBlock :code="invoke" language="bash" />

      <p>Useful when you want the schema in front of you before asking a question, or when the agent has gone off in a direction the skill would have corrected.</p>

      <h2 id="what-they-deliberately-omit">What They Deliberately Omit</h2>
      <p>The skills stay short on purpose. They do not restate frame tags, the bullet grammar of an export, pruning semantics, or what <code>self</code> means &mdash; every export says all of that itself, in the version that matches the code that produced it. A skill repeating it would be a second source of truth, and the one that drifts.</p>

      <p>If you are writing your own tooling against the server rather than using the plugin, the same division applies: read the preamble each export gives you, and treat the two schema skills as the reference for raw SQL.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* ============================
   Chooser Table
   ============================ */
.skill-chooser td:nth-child(2) {
  white-space: nowrap;
}

/* ============================
   Skill Card
   ============================ */
.skill-card {
  padding: 1.5rem;
  margin-bottom: 2rem;
  border: 1px solid #e9ecef;
  border-radius: 8px;
  background: #ffffff;
}

.skill-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding-bottom: 1rem;
  margin-bottom: 1.25rem;
  border-bottom: 1px solid #e9ecef;
}

.skill-question {
  margin: 0;
  font-size: 1.0625rem;
  font-weight: 600;
  line-height: 1.5;
  color: #343a40;
}

.skill-role {
  flex-shrink: 0;
  padding: 0.25rem 0.625rem;
  border-radius: 999px;
  background: #eef0ff;
  color: #5e64ff;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}

/* ============================
   Labelled Blocks
   ============================ */
.skill-block {
  margin-bottom: 1.25rem;
}

.skill-block:last-child {
  margin-bottom: 0;
}

.skill-block h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #6c757d;
}

.skill-block ul {
  margin-bottom: 0;
  padding-left: 1.25rem;
}

.skill-block li {
  margin-bottom: 0.5rem;
}

.skill-block li:last-child {
  margin-bottom: 0;
}

.skill-card > p {
  margin-bottom: 1.25rem;
}

/* ============================
   Trap Callout
   ============================ */
.skill-trap {
  margin-top: 1.25rem;
  padding: 1rem 1.25rem;
  border-left: 3px solid #f0a63a;
  border-radius: 0 6px 6px 0;
  background: #fdf7ed;
}

.skill-trap-label {
  display: block;
  margin-bottom: 0.375rem;
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #a4670c;
}

.skill-trap p {
  margin-bottom: 0;
  color: #495057;
}

.skill-trap :deep(.docs-code-block) {
  margin-bottom: 0;
}

/* ============================
   Responsive
   ============================ */
@media (max-width: 640px) {
  .skill-card {
    padding: 1.125rem;
  }

  .skill-head {
    flex-direction: column;
    gap: 0.5rem;
  }
}
</style>
