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
  { id: 'one-package', text: 'One Package, Three Manifests', level: 2 },
  { id: 'the-skills', text: 'The Ten Skills', level: 2 },
  { id: 'the-agents', text: 'The Three Agents', level: 2 },
  { id: 'what-writes', text: 'The Nine Tools That Write', level: 2 },
  { id: 'long-calls', text: 'Long Calls', level: 2 },
  { id: 'the-tool-list', text: 'The Size of the Tool List', level: 2 },
  { id: 'when-it-is-not-connected', text: 'When It Is Not Connected', level: 2 },
  { id: 'removing', text: 'Removing the Plugin', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const familiesProperty = `# On the Jeffrey side, in application.properties
jeffrey.microscope.mcp.families=profiles,flamegraph,jvm,heap`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Every Client"
      icon="bi bi-people"
    />

    <div class="docs-content">
      <p>Three coding agents connect to Jeffrey through the same <strong>Microscope plugin</strong>, and most of what there is to know about the connection is the same for all of them: which skills arrive, which tools write, how a long call behaves, what a hundred-odd tools cost. This page holds that once. The <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link>, <router-link to="/docs/microscope-mcp/codex">Codex</router-link> and <router-link to="/docs/microscope-mcp/gemini">Gemini CLI</router-link> pages hold only what differs: how to install, where the endpoint is configured, how approvals are spelled, and what that client can and cannot carry.</p>

      <DocsCallout type="info" title="Jeffrey has to be running">
        A plugin installs and loads whether or not Jeffrey is serving, and then every tool call fails. The server is on by default, so a running Jeffrey is usually all it takes. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
      </DocsCallout>

      <h2 id="one-package">One Package, Three Manifests</h2>
      <p>The plugin is one directory in the Jeffrey repository, <code>jeffrey-claude-plugin/</code>, with a manifest per client: Claude Code reads <code>.claude-plugin/plugin.json</code>, Codex and the other <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> clients read <code>plugin.json</code> beside it, and Gemini CLI reads <code>gemini-extension.json</code>. The skills, the agents and the MCP server underneath are the same files. Registering the server by hand instead gives you every tool and none of the rest; <router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> shows how.</p>

      <table>
        <thead>
          <tr>
            <th>What the plugin adds</th>
            <th>Claude Code</th>
            <th>Codex</th>
            <th>Gemini CLI</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>The endpoint, already configured</td>
            <td>Yes &mdash; a per-machine setting</td>
            <td>Yes &mdash; fixed at <code>localhost:8585</code></td>
            <td>Yes &mdash; a setting asked for at install</td>
          </tr>
          <tr>
            <td>A startup check that Jeffrey is serving</td>
            <td>Yes</td>
            <td>No &mdash; the format has no hooks</td>
            <td>Yes</td>
          </tr>
          <tr>
            <td>The ten skills</td>
            <td>Yes</td>
            <td>Yes</td>
            <td>Yes</td>
          </tr>
          <tr>
            <td>The three agents</td>
            <td>Yes, with a tool deny-list</td>
            <td>Files to copy, all three</td>
            <td>Files to copy, two of three</td>
          </tr>
        </tbody>
      </table>

      <h2 id="the-skills">The Ten Skills</h2>
      <p>Every client gets the same ten skills, which the agent picks up on its own when a question calls for one: <code>analyze-jfr</code>, <code>analyze-heap</code>, <code>analyze-hub</code>, <code>compare-jfr</code>, <code>profile-run</code>, <code>regression-check</code>, <code>advise-jfr</code>, <code>jfr-sql</code>, <code>heap-sql</code>, <code>report</code>. They are one set of files in the <a href="https://agentskills.io/specification" target="_blank" rel="noopener">Agent Skills</a> format, which all three clients read, so the directory is shared rather than duplicated. What each one carries is on the <router-link to="/docs/microscope-mcp/skills">Skills</router-link> page; how each client invokes one directly is on that client's page.</p>

      <p>The same skills are also served by Jeffrey itself as MCP <strong>prompts</strong>, so a client with no plugin at all can still read them &mdash; <router-link to="/docs/microscope-mcp/other-clients#prompts-and-resources">Other Clients</router-link> has the call.</p>

      <h2 id="the-agents">The Three Agents</h2>
      <p><code>profile-analyst</code>, <code>heap-triage</code> and <code>profile-lead</code> read an export end to end and return only the findings, so the raw document never lands in the main conversation. What each one is for is on the <router-link to="/docs/microscope-mcp/agent">Agents</router-link> page. How a client gets them differs: Claude Code carries them in the plugin with a tool deny-list; Codex and Gemini take them as files to copy, held to reading by an instruction rather than a wall, and Gemini has no <code>profile-lead</code> at all because one of its subagents may not dispatch another. The skills delegate to an agent of that name when the client has one and read the exports themselves when it does not, so skipping the copy costs context rather than correctness.</p>

      <h2 id="what-writes">The Nine Tools That Write</h2>
      <p>Every client asks before a tool the first time, and the answer that fits Jeffrey is the same everywhere: approve the read-only families once. Every tool reads except nine, and each of the nine says so in its own MCP annotations rather than inheriting its family's:</p>

      <ul>
        <li><code>recordings_analyzeFile</code> and <code>recordings_analyzeRecording</code>, which build a profile from a recording file on this machine or from one already in the Quick Analysis store;</li>
        <li><code>recordings_delete</code>, the one destructive tool, which removes a recording together with the profile built from it;</li>
        <li><code>hubs_download</code>, which pulls a session &mdash; or an hour of one, or files named from its listing &mdash; off a connected hub and creates a recording here;</li>
        <li><code>hubs_fetchFile</code>, which pulls one of a session&rsquo;s artifacts off that machine;</li>
        <li><code>heap_prepare</code>, which builds a cache;</li>
        <li><code>operations_cancel</code>, which stops background work;</li>
        <li><code>ide_link</code> and <code>ide_open</code>, which act on the editor beside Jeffrey rather than on a profile.</li>
      </ul>

      <p>None of them alters an analysed profile. The reading members of those same families &mdash; <code>recordings_list</code>, <code>recordings_status</code>, <code>heap_status</code>, <code>hubs_files</code>, <code>operations_status</code> &mdash; declare themselves read-only, so a client that gates on the hints does not sweep them up. The <router-link to="/docs/microscope-mcp/tools">Tool Reference</router-link> has the full list; a client that wants a strictly read-only Jeffrey on one machine denies those nine at its own server entry, and each client page shows the spelling.</p>

      <h2 id="long-calls">Long Calls</h2>
      <p>Most of Jeffrey&rsquo;s tools answer in well under a second, but a few do real work: importing a recording parses every event in it, pulling a session off a hub moves however many gigabytes it holds, and preparing a heap dump walks the whole graph. Each client has its own idea of how long a tool call may take, and the client pages say what it is and how to raise it.</p>

      <p>The long tools are built for the wait either way. <code>recordings_analyzeFile</code> and <code>recordings_analyzeRecording</code> wait about forty-five seconds and then hand back a status of <code>running</code> with an <code>operationId</code>; <code>hubs_download</code>, <code>hubs_fetchFile</code> and <code>heap_prepare</code> do the same. <code>operations_status</code> reports where the work has got to and the result once it lands, and <code>operations_cancel</code> asks it to stop. <strong>What matters is not retrying the analyze call:</strong> a second <code>recordings_analyzeFile</code> imports the file again and builds a second profile of it. The skills know this; a hand-driven session should too.</p>

      <h2 id="the-tool-list">The Size of the Tool List</h2>
      <p>Jeffrey advertises a hundred and eleven tools across nineteen families. Whether that costs anything depends on the client: one fetches a tool&rsquo;s schema when it needs it, another declares every enabled tool to the model on each turn, and the client pages say which. When it matters, the Jeffrey side can advertise fewer:</p>
      <DocsCodeBlock :code="familiesProperty" language="properties" />

      <p>Families are named by their tool prefix, and <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> lists them and the named presets. Leave it alone unless you have a reason &mdash; the skills route between families freely, and one that is not advertised is one their advice sends the model to in vain. Keep <code>operations</code> wherever a writer is: it is how the work a writer starts is polled and cancelled.</p>

      <h2 id="when-it-is-not-connected">When It Is Not Connected</h2>
      <p>Each client lists its MCP servers with a command of its own; the <code>jeffrey</code> server should be there with its tools. When it is not, the causes are the same everywhere and worth checking in this order: the session predates the install and needs restarting; this installation set <code>jeffrey.microscope.mcp.enabled=false</code>; Jeffrey is not on the address the client is pointed at; Jeffrey is not running. A server that shows as connected but whose tools never appear is the client&rsquo;s problem rather than Jeffrey&rsquo;s &mdash; <code>curl</code> against the endpoint settles which side is at fault, and <router-link to="/docs/microscope-mcp/other-clients#the-wire-protocol">Other Clients</router-link> has the exact request.</p>

      <h2 id="removing">Removing the Plugin</h2>
      <p>Uninstalling takes the skills with it &mdash; and the startup check, where the client has one. It does not change anything inside Jeffrey: the MCP server keeps serving, and a client that registered the server by hand is still connected. It does not remove an agent you copied into a client&rsquo;s own <code>agents/</code> directory either.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
