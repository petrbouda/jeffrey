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
  { id: 'why-a-separate-agent', text: 'Why a Separate Agent', level: 2 },
  { id: 'installing-it', text: 'Installing It', level: 2 },
  { id: 'what-it-is-given', text: 'What It Is Given', level: 2 },
  { id: 'what-it-returns', text: 'What It Returns', level: 2 },
  { id: 'what-it-never-does', text: 'What It Never Does', level: 2 },
  { id: 'delegating-to-it', text: 'Delegating to It', level: 2 },
  { id: 'when-not-to', text: 'When Not To', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const delegate = `read the CPU flamegraph of profile 019f885e-8e69-7d65-8ac7-32a70b92cb94
and tell me the top frames with their shares`;

const parallel = `# four questions, one message - they run at once, and none of the
# four documents lands in this conversation
CPU:        jdk.ExecutionSample
wall-clock: profiler.WallClockSample
allocation: jdk.ObjectAllocationSample, useWeight
lock:       jdk.JavaMonitorEnter, useWeight`;

const report = `## jdk.ObjectAllocationSample - 5.2 GiB over 2724 samples

1. \`com.example.OrderMapper.toDto\` - total 34.1% (1.8 GiB), self 31.2%
   Reached from OrderService.list through a stream collector; allocating a
   new DTO plus an ArrayList per row.
2. ...

Notes: threshold 1%, weighted by bytes. Frames below 1% rolled into parents.`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="The profile-analyst Agent"
      icon="bi bi-person-badge"
    />

    <div class="docs-content">
      <p><code>profile-analyst</code> reads a Jeffrey export end to end and returns only the findings. Registering the MCP server by hand gives you the <router-link to="/docs/microscope-mcp/tools">tools</router-link> but not this.</p>

      <h2 id="why-a-separate-agent">Why a Separate Agent</h2>
      <p>A single <code>flamegraph_export</code> can run to 120,000 characters, and a question worth asking usually takes several &mdash; four in <code>advise-jfr</code>, one per group. Pulled into your session, they crowd out the thing that has to happen next: reading the actual source behind the frames, and holding the conversation about what to change.</p>

      <p>So the reading happens somewhere else. The analyst runs the sequence, follows the profile where it leads &mdash; deeper into a heavy subtree, a lower <code>thresholdPct</code> on one path, the GC-root path of the class the histogram named &mdash; and returns a report. Everything it read stays in its context, not yours. Extra reads cost you nothing, which is why it is told to keep going until it can name the causes rather than stopping at the first export.</p>

      <h2 id="installing-it">Installing It</h2>
      <p>In <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> it arrives with the plugin as <code>microscope:profile-analyst</code>, and there is nothing to do.</p>

      <p>In <router-link to="/docs/microscope-mcp/codex">Codex</router-link> it is a file to copy. The Agent Plugins format defines exactly two component types &mdash; skills and MCP servers &mdash; so no plugin can hand a Codex install an agent, however the plugin was written. The analyst ships as <code>codex/agents/profile-analyst.toml</code>; copy it to <code>~/.codex/agents/</code> for every repository, or <code>.codex/agents/</code> for one.</p>

      <p>The skills delegate to an agent of that name when the client has one and read the exports themselves when it does not, so skipping this costs context rather than correctness.</p>

      <h2 id="what-it-is-given">What It Is Given</h2>
      <p>A <code>profileId</code> and one question. For a comparison, a second id as the <strong>baseline</strong>: the <code>profileId</code> is the run under examination and the baseline is what it is measured against, and it never swaps them to make a result read better.</p>

      <p>The <router-link to="/docs/microscope-mcp/skills"><code>analyze-jfr</code>, <code>analyze-heap</code> and <code>compare-jfr</code></router-link> skills come with it &mdash; preloaded in Claude Code, read on demand in Codex &mdash; so it carries the entry sequence, which flamegraph answers which question, the order to work a latency question in traces, the heap rules (shallow versus retained, the lazily built dominator tree, which reports only the Jeffrey UI can compute), and &mdash; for a comparison &mdash; that <code>compare_list</code> runs first and &ldquo;these two runs are not comparable&rdquo; is a finding to report rather than an obstacle to work around.</p>

      <DocsCallout type="info" title="It will not pick a profile for you">
        If the request names no <code>profileId</code>, it says so and stops. The caller knows which profile the conversation is about and the analyst does not &mdash; guessing would produce a confident report about the wrong run.
      </DocsCallout>

      <h2 id="what-it-returns">What It Returns</h2>
      <p>Findings only, in the units the export used, so a reader who never saw the document can check every claim against it. Roughly forty lines:</p>
      <DocsCodeBlock :code="report" language="markdown" />

      <p>For a heap dump the same shape, with the class name, retained bytes and the GC-root path together &mdash; those three are what make a heap claim checkable.</p>

      <p>Two rules decide whether the report is usable. <strong>Every figure comes from a tool result:</strong> it never estimates, rounds a number it did not see, or carries a total between event types. And <strong>it says what is missing</strong> &mdash; a group the profiler never recorded, a report only the UI can compute, an empty result. A gap reported is useful; a gap papered over sends you down a path with no data under it.</p>

      <h2 id="what-it-never-does">What It Never Does</h2>
      <p>What it is not allowed to do is as much of the design as what it does.</p>
      <ul>
        <li><strong>No source.</strong> It has no file tools and cannot read your repository. It names the frame, never a file or a line &mdash; mapping frames onto the checkout is yours, and a guess made there would arrive looking measured.</li>
        <li><strong>No recommendations.</strong> It reports what the profile shows. Whether to change anything, and what, stays in your session where you can be asked.</li>
        <li><strong>No writing.</strong> It cannot import a recording or build a profile. If the profile it was given does not exist or is not ready, it reports that and stops.</li>
        <li><strong>No nesting.</strong> The skills it carries tell <em>their</em> reader to delegate export reading to the analyst; that instruction is written for your session, not for it. It does the reading itself and never spawns another agent.</li>
      </ul>

      <DocsCallout type="warning" title="Enforced in Claude Code, instructed in Codex">
        The Claude Code subagent is denied file tools and the <code>recordings_</code> family in its own definition, so the first two rules hold whatever the model decides. Codex has no per-agent tool deny-list: its copy is sandboxed read-only against your files, and the rest is instruction. To make it a wall there, deny <code>recordings_</code> at the server with <code>disabled_tools</code> &mdash; the <router-link to="/docs/microscope-mcp/codex">Codex</router-link> page has the block.
      </DocsCallout>

      <h2 id="delegating-to-it">Delegating to It</h2>
      <p>Usually you do not: the skills delegate on your behalf when more than one export is in play. To do it yourself, give it the id and the one question.</p>
      <DocsCodeBlock :code="delegate" language="bash" />

      <p>Independent questions go out in a single message so they run at once. This is how <code>advise-jfr</code> uses it hardest &mdash; four groups, four parallel delegations, four documents that never enter your context:</p>
      <DocsCodeBlock :code="parallel" language="text" />

      <h2 id="when-not-to">When Not To</h2>
      <p>Read the export in your own session when there is exactly one and its result will be discussed turn by turn. The analyst returns a report and then the document is gone: it cannot answer a follow-up about a passage the conversation never saw, and asking it again re-reads from scratch.</p>

      <p>The division that makes the whole thing work: the analyst holds the documents, and your session holds everything that needs you &mdash; mapping frames onto the checkout, the recommendation, and every question put to you.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
