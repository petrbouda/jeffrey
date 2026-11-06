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
  { id: 'the-agents', text: 'The Agents', level: 2 },
  { id: 'installing', text: 'Installing', level: 2 },
  { id: 'what-they-never-do', text: 'What They Never Do', level: 2 },
  { id: 'delegating', text: 'Delegating', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const delegate = `read the CPU flamegraph of profile 019f885e-8e69-7d65-8ac7-32a70b92cb94
and tell me the top frames with their shares`;

const parallel = `# the groups you picked, one message - they run at once, and none of
# the documents lands in this conversation
CPU:        jdk.ExecutionSample
wall-clock: profiler.WallClockSample
allocation: jdk.ObjectAllocationSample, useWeight
lock:       jdk.JavaMonitorEnter, useWeight`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="The Analysis Agents"
      icon="bi bi-person-badge"
    />

    <div class="docs-content">
      <p>The <code>microscope</code> plugin has two agents: <code>jfr-analyst</code> for JFR recordings and <code>heap-analyst</code> for heap dumps. A skill hands an agent a <code>profileId</code> and one question; the agent works the profile end to end and returns only the findings, each with the Microscope link of the page that shows it. Registering the MCP server by hand gives you the <router-link to="/docs/microscope-mcp/tools">tools</router-link> but not the agents.</p>

      <h2 id="the-agents">The Agents</h2>
      <table>
        <thead>
          <tr>
            <th>Agent</th>
            <th>Reads</th>
            <th>Handed work by</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><router-link to="/docs/microscope-mcp/agent/jfr-analyst"><code>jfr-analyst</code></router-link></td>
            <td>One JFR recording: flamegraphs, traces, the JVM&rsquo;s own views, comparisons of two runs</td>
            <td><code>analyze-jfr</code>, <code>compare-jfr</code>, <code>optimize-code</code></td>
          </tr>
          <tr>
            <td><router-link to="/docs/microscope-mcp/agent/heap-analyst"><code>heap-analyst</code></router-link></td>
            <td>One heap dump: what holds the memory, what leaks, where the waste is</td>
            <td><code>analyze-heap</code></td>
          </tr>
        </tbody>
      </table>
      <p>Both return findings only, about forty lines at most, and stop when handed no <code>profileId</code> or the other kind of profile.</p>

      <p>An agent has no file tools, so an answer its client saved to a file instead of showing it &mdash; Claude Code does that past its output limit &mdash; is one it cannot open. It asks once for the same thing smaller (a flamegraph&rsquo;s <code>detail: SUMMARY</code>, a lower <code>limit</code>), never for a filter that changes the figures, and otherwise returns the file under <strong>Saved, not read</strong> with the call behind it; the skill that delegated reads it in your conversation.</p>

      <h2 id="installing">Installing</h2>
      <ul>
        <li><strong><router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link></strong> &mdash; installed with the plugin, as <code>microscope:jfr-analyst</code> and <code>microscope:heap-analyst</code>.</li>
        <li><strong><router-link to="/docs/microscope-mcp/codex">Codex</router-link></strong> &mdash; copy <code>codex/agents/jfr-analyst.toml</code> and <code>codex/agents/heap-analyst.toml</code> to <code>~/.codex/agents/</code>, or to <code>.codex/agents/</code> for one repository. A Codex plugin cannot carry agents.</li>
        <li><strong><router-link to="/docs/microscope-mcp/gemini">Gemini CLI</router-link></strong> &mdash; copy <code>gemini/agents/jfr-analyst.md</code> and <code>gemini/agents/heap-analyst.md</code> to <code>~/.gemini/agents/</code>, or to <code>.gemini/agents/</code> for one repository.</li>
      </ul>
      <p>Without the agents, the skills read the exports in your conversation instead.</p>

      <h2 id="what-they-never-do">What They Never Do</h2>
      <ul>
        <li><strong>Read your source.</strong> They name frames and classes, never a file or a line; mapping them onto the checkout stays in your conversation.</li>
        <li><strong>Recommend changes.</strong> They report what the profile shows.</li>
        <li><strong>Import or delete.</strong> They cannot import a recording, pull one from the connector, delete one, act on your editor or cancel work your session started.</li>
        <li><strong>Spawn another agent.</strong> They do the reading themselves.</li>
      </ul>

      <DocsCallout type="warning" title="Enforced in Claude Code, instructed elsewhere">
        In Claude Code each agent&rsquo;s own definition withholds those tools, under both tool prefixes &mdash; and <code>jfr-analyst</code> is denied every <code>heap_</code> tool. Codex and Gemini have no per-agent deny-list, so there the limits are instructions; to enforce them, withhold the tools for the whole session (<code>disabled_tools</code> on the <router-link to="/docs/microscope-mcp/codex">Codex</router-link> page, <code>excludeTools</code> on the <router-link to="/docs/microscope-mcp/gemini">Gemini CLI</router-link> one). The writers each agent keeps, which only fill a cache, are on its own page.
      </DocsCallout>

      <h2 id="delegating">Delegating</h2>
      <p>The skills delegate for you whenever more than one export is in play. To do it yourself, give the agent the id and the question:</p>
      <DocsCodeBlock :code="delegate" language="bash" />
      <p>Independent questions go out in one message and run at once &mdash; <code>optimize-code</code> sends one per flamegraph group you pick:</p>
      <DocsCodeBlock :code="parallel" language="text" />
      <p>For a single export you will discuss turn by turn, read it in your own conversation: an agent keeps no document after it reports.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
