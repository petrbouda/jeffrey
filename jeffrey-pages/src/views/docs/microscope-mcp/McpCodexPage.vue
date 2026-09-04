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
  { id: 'install-it', text: 'Install It', level: 2 },
  { id: 'pointing-it-elsewhere', text: 'Pointing It Elsewhere', level: 2 },
  { id: 'what-the-plugin-adds', text: 'What the Plugin Adds', level: 2 },
  { id: 'the-analyst-agent', text: 'The Analyst Agent', level: 2 },
  { id: 'approvals', text: 'Approvals', level: 2 },
  { id: 'check-it-is-connected', text: 'Check It Is Connected', level: 2 },
  { id: 'updating-and-removing', text: 'Updating and Removing', level: 2 },
  { id: 'without-the-plugin', text: 'Without the Plugin', level: 2 },
  { id: 'what-differs-from-claude-code', text: 'What Differs from Claude Code', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const installMarketplace = `codex plugin marketplace add petrbouda/jeffrey`;

const installLocal = `git clone https://github.com/petrbouda/jeffrey
codex plugin marketplace add ./jeffrey`;

const pinnedInstall = `codex plugin marketplace add petrbouda/jeffrey --ref v1.2.0`;

const customUrl = `[mcp_servers.jeffrey]
url = "http://localhost:9000/api/internal/mcp"`;

const disablePluginServer = `[plugins."microscope@jeffrey"]
mcp_servers.jeffrey.enabled = false`;

const agentInstall = `mkdir -p ~/.codex/agents
cp jeffrey/jeffrey-claude-plugin/codex/agents/profile-analyst.toml ~/.codex/agents/`;

const approvalRule = `[mcp_servers.jeffrey]
default_tools_approval_mode = "auto"`;

const ingestDenyRule = `[mcp_servers.jeffrey]
disabled_tools = ["recordings_analyzeFile", "recordings_analyzeRecording"]`;

const manualAdd = `codex mcp add jeffrey --url http://localhost:8585/api/internal/mcp`;

const update = `codex plugin marketplace upgrade`;

const removal = `codex plugin marketplace remove jeffrey`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Codex"
      icon="bi bi-terminal"
    />

    <div class="docs-content">
      <p>The same <strong>Microscope plugin</strong> installs into Codex. One directory in the Jeffrey repository carries two manifests &mdash; the one <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> reads, and an <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> manifest that Codex, Cursor, Copilot, VS Code and Kiro read &mdash; over a single set of skills and a single MCP server. Nothing about the endpoint is Claude-specific: it is plain JSON-RPC over Streamable HTTP.</p>

      <DocsCallout type="info" title="Jeffrey has to be running">
        The plugin installs and loads whether or not Jeffrey is serving, and then every tool call fails. The server is on by default, so a running Jeffrey is usually all it takes &mdash; <strong>Settings &rarr; Coding Agents (MCP)</strong> reports whether the endpoint is serving. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
      </DocsCallout>

      <h2 id="install-it">Install It</h2>
      <p>Register the Jeffrey repository as a plugin marketplace:</p>
      <DocsCodeBlock :code="installMarketplace" language="bash" />

      <p>Then, inside Codex, open <code>/plugins</code>, find <strong>microscope</strong> and install it. Start a new thread afterwards &mdash; a plugin's skills and MCP servers are loaded when the session begins, not mid-conversation.</p>

      <p>Or, if you would rather work from a clone &mdash; useful when developing against a modified Jeffrey:</p>
      <DocsCodeBlock :code="installLocal" language="bash" />

      <p>The marketplace is the repository itself, read off its default branch, so an upgrade picks up a newer plugin. To hold a machine on one release instead, add the marketplace at a tag:</p>
      <DocsCodeBlock :code="pinnedInstall" language="bash" />

      <DocsCallout type="tip" title="Codex's plugin commands are young">
        The subcommands moved more than once while the format settled. <code>codex plugin --help</code> is the authority for the version you have; if a command below is not there, the <a href="#without-the-plugin">manual registration</a> at the bottom of this page works on every version.
      </DocsCallout>

      <h2 id="pointing-it-elsewhere">Pointing It Elsewhere</h2>
      <p>The plugin ships pointed at <code>http://localhost:8585/api/internal/mcp</code>, and in Codex <strong>that address is fixed</strong>. The Agent Plugins specification forbids placeholder expansion in a server URL, deliberately &mdash; a URL that can be rewritten per install is a URL an installed plugin can be redirected through &mdash; so there is no per-machine endpoint setting of the kind Claude Code offers.</p>

      <p>For any other address &mdash; a different port, a container, an SSH tunnel &mdash; register the server yourself in <code>~/.codex/config.toml</code>:</p>
      <DocsCodeBlock :code="customUrl" language="toml" />

      <p>And turn off the one the plugin brought, so the tools are not registered twice:</p>
      <DocsCodeBlock :code="disablePluginServer" language="toml" />

      <p>The skills keep working either way &mdash; they name tools by the part after the prefix, and the server is still called <code>jeffrey</code>. Only the registration moves.</p>

      <h2 id="what-the-plugin-adds">What the Plugin Adds</h2>
      <p>Registering the server by hand gives you the eighty-five tools. The plugin adds the endpoint already configured, and <strong>six skills</strong>, which Codex picks up on its own when a question calls for them and which you can also invoke directly with <code>$</code>:</p>
      <ul>
        <li><code>$analyze-jfr</code> &mdash; where to start and which family answers which question</li>
        <li><code>$analyze-heap</code> &mdash; a heap dump end to end: what is holding the memory, what is leaking, and the order the twenty heap tools have to be run in</li>
        <li><code>$compare-jfr</code> &mdash; before against after: whether a change made it slower, which methods moved, and whether the two recordings were comparable in the first place</li>
        <li><code>$advise-jfr</code> &mdash; from a profile to a code change: hot frames mapped to your checkout, a recommendation, then the edit and a re-profile on request</li>
        <li><code>$jfr-sql</code> &mdash; the JFR schema and the DuckDB idioms that go with it</li>
        <li><code>$heap-sql</code> &mdash; the heap-dump index schema</li>
      </ul>

      <p>They are the same files Claude Code loads &mdash; both clients read the <a href="https://agentskills.io/specification" target="_blank" rel="noopener">Agent Skills</a> format, so the skill directory is shared rather than duplicated. The <router-link to="/docs/microscope-mcp/skills">Skills</router-link> page covers what each one carries and why it exists. <code>/skills</code> lists what the session actually loaded.</p>

      <h2 id="the-analyst-agent">The Analyst Agent</h2>
      <p>A single <code>flamegraph_export</code> can run to 120,000 characters, and answering a question properly often takes several. The <router-link to="/docs/microscope-mcp/agent">analyst agent</router-link> runs a sequence and returns only the findings &mdash; the hot frames with their <code>total</code> and <code>self</code> shares, or the retaining classes with their retained bytes and GC-root paths &mdash; leaving everything it read in its own context.</p>

      <p><strong>A Codex plugin cannot carry it.</strong> Agent Plugins defines exactly two component types, skills and MCP servers; agents are not among them. So the plugin ships the analyst as a file to copy:</p>
      <DocsCodeBlock :code="agentInstall" language="bash" />

      <p><code>~/.codex/agents/</code> makes it available in every repository; <code>.codex/agents/</code> inside a checkout scopes it to that one. The skills look for an agent by that name and delegate to it when one exists, and read the exports themselves when none does &mdash; so this step is optional, and skipping it costs context rather than correctness.</p>

      <p>One difference worth knowing: the Claude Code subagent is denied the <code>recordings_</code> tools by its own definition, so it cannot create a profile even if it tried. Codex has no per-agent tool deny-list, so the Codex version is sandboxed read-only against your files and told not to write &mdash; an instruction rather than a wall. If that distinction matters to you, deny the family at the server instead:</p>
      <DocsCodeBlock :code="ingestDenyRule" language="toml" />

      <h2 id="approvals">Approvals</h2>
      <p>Codex asks before each tool the first time. Every tool here reads except the <code>recordings_</code> family, which imports a recording file and builds a profile from it, so approving the server once is usually what you want:</p>
      <DocsCodeBlock :code="approvalRule" language="toml" />

      <p>Tool names arrive prefixed with the server they came from &mdash; <code>mcp__jeffrey__flamegraph_export</code> and so on. <code>enabled_tools</code> and <code>disabled_tools</code> on the same block narrow what the model sees at all, which is the sharper instrument when you want a strictly read-only Jeffrey for one machine regardless of what the server advertises.</p>

      <h2 id="check-it-is-connected">Check It Is Connected</h2>
      <p>Run <code>codex mcp list</code>, or <code>/mcp</code> inside a session. The <code>jeffrey</code> server should be listed with its tools. If it is not, in order of likelihood: the session predates the install and needs restarting, the MCP server is off in Jeffrey's Settings, Jeffrey is not on <code>localhost:8585</code>, or Jeffrey is not running.</p>

      <p>A server that shows as connected but whose tools never appear is worth reporting upstream rather than debugging in Jeffrey &mdash; Codex's Streamable HTTP client has had that failure mode. <code>curl</code> against the endpoint settles which side is at fault; <router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> has the exact request.</p>

      <h2 id="updating-and-removing">Updating and Removing</h2>
      <p>Refresh the marketplace, which pulls the repository again and offers the newer plugin:</p>
      <DocsCodeBlock :code="update" language="bash" />

      <p>A new thread applies it. To remove the plugin, uninstall it from <code>/plugins</code>, or drop the marketplace entirely:</p>
      <DocsCodeBlock :code="removal" language="bash" />

      <p>To keep it installed but silent for a while, set <code>enabled = false</code> under its <code>[plugins."microscope@jeffrey"]</code> block in <code>~/.codex/config.toml</code>. Individual skills can be switched off the same way, with a <code>[[skills.config]]</code> entry naming the skill's path.</p>

      <p>Uninstalling takes the skills with it. It does not change anything inside Jeffrey &mdash; the MCP server keeps serving &mdash; and it does not remove an analyst agent you copied into <code>~/.codex/agents/</code>.</p>

      <h2 id="without-the-plugin">Without the Plugin</h2>
      <p>The endpoint is an ordinary MCP server, so one command connects it with no marketplace involved:</p>
      <DocsCodeBlock :code="manualAdd" language="bash" />

      <p>Or write the <code>[mcp_servers.jeffrey]</code> block from <a href="#pointing-it-elsewhere">above</a> straight into <code>~/.codex/config.toml</code>. <strong>Settings &rarr; Coding Agents (MCP)</strong> in Jeffrey offers both, filled in with the URL your browser actually reached Jeffrey on &mdash; correct behind a container, a proxy or a non-default port, where <code>localhost:8585</code> is not.</p>

      <p>What you give up is the skills: the entry sequence and the two database schemas. The tools still work; the model just starts colder, and is more likely to guess a column name than to call <code>jfr_describeTable</code> first.</p>

      <h2 id="what-differs-from-claude-code">What Differs from Claude Code</h2>
      <p>The tools and the skills are identical. Everything below is a property of the plugin formats, not of Jeffrey.</p>

      <table>
        <thead>
          <tr>
            <th></th>
            <th>Claude Code</th>
            <th>Codex</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Manifest</td>
            <td><code>.claude-plugin/plugin.json</code></td>
            <td>Agent Plugins <code>plugin.json</code> + <code>mcp.json</code></td>
          </tr>
          <tr>
            <td>Install</td>
            <td><code>/plugin install microscope@jeffrey</code></td>
            <td><code>codex plugin marketplace add</code>, then <code>/plugins</code></td>
          </tr>
          <tr>
            <td>Skills</td>
            <td>Six, invoked <code>/microscope:analyze-jfr</code></td>
            <td>The same six, invoked <code>$analyze-jfr</code></td>
          </tr>
          <tr>
            <td>Endpoint</td>
            <td>A per-machine setting</td>
            <td>Fixed at <code>localhost:8585</code>; anything else is a <code>config.toml</code> block</td>
          </tr>
          <tr>
            <td>Analyst agent</td>
            <td>Shipped, tool-restricted</td>
            <td>Copied by hand, restricted by instruction</td>
          </tr>
          <tr>
            <td>Tool prefix</td>
            <td><code>mcp__plugin_microscope_jeffrey__</code></td>
            <td><code>mcp__jeffrey__</code></td>
          </tr>
        </tbody>
      </table>

      <p>The two that bite are the endpoint and the agent. Neither has a workaround inside the plugin: they are the price of a format that six vendors agreed on, and both are one file away from being solved by hand.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
