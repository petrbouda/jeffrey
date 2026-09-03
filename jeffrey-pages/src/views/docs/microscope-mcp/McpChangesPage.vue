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
  { id: 'the-division-of-labour', text: 'The Division of Labour', level: 2 },
  { id: 'the-repository-check', text: 'The Repository Check', level: 2 },
  { id: 'the-graph-to-take', text: 'The Graph to Take', level: 2 },
  { id: 'frames-are-not-file-paths', text: 'Frames Are Not File Paths', level: 2 },
  { id: 'proving-it-worked', text: 'Proving It Worked', level: 2 },
  { id: 'why-jeffrey-does-not-do-this-itself', text: 'Why Jeffrey Does Not Do This Itself', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const session = `You: the checkout service got slower after the last release, have a look

Claude: [profiles_buildInfo]   profile "checkout-run", commit 9f21c0e
        [git rev-parse HEAD]   9f21c0e — same commit, going on
        [flamegraph_panels]    jdk.ExecutionSample 41,200 · jdk.ObjectAllocationSample 8,900
        [flamegraph_export]    jdk.ExecutionSample
        [Grep, Read]           PriceResolver.resolve — 34% of on-CPU time
        [Edit]                 hoists the currency lookup out of the per-item loop
        [Bash]                 ./mvnw -pl checkout test`;

const packages = `SELECT class_name, count(*) FROM frames
WHERE class_name IS NOT NULL GROUP BY class_name ORDER BY 2 DESC LIMIT 40`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="From Profile to Change"
      icon="bi bi-wrench-adjustable"
    />

    <div class="docs-content">
      <p>Reading a profile and acting on one are different jobs. The tools are the same &mdash; there is no separate &ldquo;recommendations&rdquo; family &mdash; but the discipline is not, and one call is only useful when you intend to change something. This page is that workflow; the <router-link to="/docs/microscope-mcp/skills">optimize-from-profile skill</router-link> carries it into a session for you.</p>

      <h2 id="the-division-of-labour">The Division of Labour</h2>
      <p>Jeffrey supplies measurement, and nothing else. It knows where the time went, what the recording carries, and what build produced it. It does not know your code, your conventions, your build, or whether a change is a good idea.</p>

      <p>Everything on that second list is what a session in your repository is already good at &mdash; so the server does not try. There is no tool that returns advice, no prompt shipped inside a tool result, and nothing that tells you how to write Java. The instructions live in a skill, which is a Markdown file you can read, edit and disagree with; the tools return facts.</p>

      <DocsCallout type="info" title="Nothing here writes to your working copy">
        Every tool the server exposes reads. Jeffrey never edits your source: the changes are made by the client with its own tools, under the permissions you granted it, and land as an ordinary diff you review.
      </DocsCallout>

      <h2 id="the-repository-check">The Repository Check</h2>
      <p>Acting on a profile assumes one thing that reading it does not: that the checkout in front of you is the code that ran. Nothing else in the server can settle that, which is what <code>profiles_buildInfo</code> is for.</p>

      <p>It returns the commit resolved from the recording&rsquo;s tags &mdash; <code>git.commit</code>, <code>git.commit.id</code>, <code>git_commit</code>, <code>vcs.revision</code> or <code>org.opencontainers.image.revision</code>, whichever is present &mdash; every tag the recording carries, and the <code>jdk.JVMInformation</code> command line: the main class or <code>-jar</code> name, the classpath, the flags. Compare the commit with <code>git rev-parse HEAD</code>, and the command line with what this repository builds.</p>

      <table>
        <thead>
          <tr>
            <th>What you find</th>
            <th>What it means</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Commit matches, or is an ancestor or descendant of HEAD</td>
            <td>Go on. A working copy a few commits ahead of the recording is normal and still useful</td>
          </tr>
          <tr>
            <td>Commit disagrees with HEAD</td>
            <td>Stop and say both. The profile may describe code that is already gone, and an edit made against it is confident and wrong</td>
          </tr>
          <tr>
            <td>No commit, but the main class or jar plainly belongs here</td>
            <td>Go on, and say the identification was by command line rather than by commit</td>
          </tr>
          <tr>
            <td>Nothing identifies the build</td>
            <td>Stop. Ask which repository this profile belongs to</td>
          </tr>
        </tbody>
      </table>

      <p>Recordings made by hand often carry no commit tag, and <code>profiles_buildInfo</code> says so rather than implying a match. Tagging recordings with the commit they were built from is what turns this check from a heuristic into a fact. Without one, the frames themselves are a second signal &mdash; through <code>jfr_executeQuery</code>:</p>
      <DocsCodeBlock :code="packages" language="sql" />

      <p>The packages that come back should be packages this repository contains.</p>

      <h2 id="the-graph-to-take">The Graph to Take</h2>
      <p><code>flamegraph_panels</code> says what the recording carries; <code>flamegraph_export</code> returns the tree. Two choices matter more than the rest:</p>

      <table>
        <thead>
          <tr>
            <th>The question</th>
            <th>Event type</th>
            <th>Also pass</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Where is on-CPU time going</td>
            <td><code>jdk.ExecutionSample</code></td>
            <td>&mdash;</td>
          </tr>
          <tr>
            <td>Where is wall-clock time going, waiting included</td>
            <td><code>jdk.WallClockSample</code></td>
            <td>&mdash;</td>
          </tr>
          <tr>
            <td>What is allocating</td>
            <td><code>jdk.ObjectAllocationSample</code></td>
            <td><code>useWeight: true</code> &mdash; rank by bytes, not by call count</td>
          </tr>
          <tr>
            <td>What is waiting on locks</td>
            <td><code>jdk.JavaMonitorEnter</code></td>
            <td><code>useWeight: true</code> &mdash; weight is nanoseconds blocked</td>
          </tr>
        </tbody>
      </table>

      <p>Take the whole recording: no thread scoping, no search, no time window unless the user asked about one interval. Each of those filters turns the question into a narrower one. <code>thresholdPct</code> decides how much survives pruning &mdash; lower it to chase one path deeper, and remember that a frame which is absent was below the threshold for its parent. Absence is not zero.</p>

      <h2 id="frames-are-not-file-paths">Frames Are Not File Paths</h2>
      <p>The tree gives you method signatures. Turning one into a file is where this goes wrong quietly, and the rules are worth knowing whether or not you use the skill:</p>

      <ul>
        <li><strong><code>[INL]</code></strong> &mdash; the frame was inlined into its caller at runtime. The source still has both methods, but there is no separate function whose cost you can remove.</li>
        <li><strong><code>$$Lambda</code></strong> &mdash; belongs to the method that declares the lambda. Find that method, not a file named after the synthetic class.</li>
        <li><strong><code>[SYNTHETIC]</code></strong> &mdash; markers Jeffrey inserted: thread names, allocated-object placeholders, collapsed subtrees. They are not call frames and are not in the source at all.</li>
        <li><strong><code>self</code> far below <code>total</code></strong> &mdash; an orchestration frame. The cost is in what it calls, and the fix is usually &ldquo;call it less often&rdquo;, one level up.</li>
      </ul>

      <p>And the rule that decides what may be edited at all: <strong>change only code this repository contains</strong>. When the hot frame is in the JDK, a library or a driver, the change is at your call site &mdash; call it less, call it differently, batch it, cache it, or change the dependency&rsquo;s version or configuration.</p>

      <DocsCodeBlock :code="session" language="text" />

      <h2 id="proving-it-worked">Proving It Worked</h2>
      <p>An edit that follows from a profile is a claim until the profile changes. Record the workload again, import the new recording with <code>recordings_analyzeFile</code>, and compare the same event type&rsquo;s totals and hot path against the original. A change that does not move the number is worth reverting.</p>

      <p>Build and test first, though. A patch that does not compile is not a recommendation, and this is the one thing an in-repo session can check that nothing reading a profile from outside can.</p>

      <h2 id="why-jeffrey-does-not-do-this-itself">Why Jeffrey Does Not Do This Itself</h2>
      <p>Earlier versions shipped an <strong>Advisor</strong>: Jeffrey called a model itself, gave it read-only access to a source folder you configured, and stored the recommendation and a unified diff against the profile. It has been removed, because every part of it was weaker than doing the same work where the code is.</p>

      <p>Reading four files through four tools is worse than reading the repository. A diff written by a model that cannot compile it is worse than an edit that was built and tested. And a recommendation stored in Jeffrey is a second place to look for something your session already showed you. What is left is the part only Jeffrey can supply &mdash; the measurement, and the identity of the build it came from.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
