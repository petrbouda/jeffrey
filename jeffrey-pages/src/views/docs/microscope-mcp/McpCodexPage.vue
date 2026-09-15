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

const timeoutConfig = `[mcp_servers.jeffrey]
url = "http://localhost:8585/api/mcp"
# Codex gives a tool call 60 seconds by default
tool_timeout_sec = 120`;

const headings = [
  { id: 'install-it', text: 'Install It', level: 2 },
  { id: 'pointing-it-elsewhere', text: 'Pointing It Elsewhere', level: 2 },
  { id: 'what-the-plugin-adds', text: 'What the Plugin Adds', level: 2 },
  { id: 'the-analyst-agent', text: 'The Agents', level: 2 },
  { id: 'approvals', text: 'Approvals', level: 2 },
  { id: 'timeouts', text: 'Timeouts on Long Calls', level: 2 },
  { id: 'the-tool-list', text: 'The Size of the Tool List', level: 2 },
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
url = "http://localhost:9000/api/mcp"`;

const disablePluginServer = `[plugins."microscope@jeffrey"]
mcp_servers.jeffrey.enabled = false`;

const agentInstall = `mkdir -p ~/.codex/agents
cp jeffrey/jeffrey-claude-plugin/codex/agents/profile-analyst.toml ~/.codex/agents/
cp jeffrey/jeffrey-claude-plugin/codex/agents/heap-triage.toml ~/.codex/agents/
cp jeffrey/jeffrey-claude-plugin/codex/agents/profile-lead.toml ~/.codex/agents/`;

const approvalRule = `[mcp_servers.jeffrey]
default_tools_approval_mode = "auto"`;

const ingestDenyRule = `[mcp_servers.jeffrey]
disabled_tools = [
  "recordings_analyzeFile", "recordings_analyzeRecording", "recordings_delete",
  "recordings_list", "hubs_list", "hubs_sessions", "hubs_download",
  "operations_cancel",
]`;

const manualAdd = `codex mcp add jeffrey --url http://localhost:8585/api/mcp`;

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
      <p>The same <strong>Microscope plugin</strong> installs into Codex through its <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> manifest, which Cursor, Copilot, VS Code and Kiro read too. What the plugin brings &mdash; the skills, the agents, which tools write, how a long call behaves &mdash; is on <router-link to="/docs/microscope-mcp/clients">Every Client</router-link>. This page is what Codex does differently: a <strong>fixed endpoint</strong>, agents that are <strong>files to copy</strong>, a <strong>sixty-second</strong> tool timeout and a tool list that is <strong>loaded every turn</strong>.</p>

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
      <p>The plugin ships pointed at <code>http://localhost:8585/api/mcp</code>, and in Codex <strong>that address is fixed</strong>. The Agent Plugins specification forbids placeholder expansion in a server URL, deliberately &mdash; a URL that can be rewritten per install is a URL an installed plugin can be redirected through &mdash; so there is no per-machine endpoint setting of the kind Claude Code offers.</p>

      <p>For any other address &mdash; a different port, a container, an SSH tunnel &mdash; register the server yourself in <code>~/.codex/config.toml</code>:</p>
      <DocsCodeBlock :code="customUrl" language="toml" />

      <p>And turn off the one the plugin brought, so the tools are not registered twice:</p>
      <DocsCodeBlock :code="disablePluginServer" language="toml" />

      <p>The skills keep working either way &mdash; they name tools by the part after the prefix, and the server is still called <code>jeffrey</code>. Only the registration moves.</p>

      <h2 id="what-the-plugin-adds">What the Plugin Adds</h2>
      <p>The endpoint already configured and the <router-link to="/docs/microscope-mcp/clients#the-skills">ten skills</router-link>, which Codex picks up on its own and which you can also invoke directly with <code>$</code>, as <code>$analyze-jfr</code>; <code>/skills</code> lists what the session actually loaded. No startup check: hooks are not part of the Agent Plugins format, so a Codex session finds out that Jeffrey is down the way it always did.</p>

      <h2 id="the-analyst-agent">The Agents</h2>
      <p><strong>A Codex plugin cannot carry the <router-link to="/docs/microscope-mcp/clients#the-agents">three agents</router-link>.</strong> Agent Plugins defines exactly two component types, skills and MCP servers; agents are not among them. So the plugin ships both as files to copy:</p>
      <DocsCodeBlock :code="agentInstall" language="bash" />

      <p><code>~/.codex/agents/</code> makes them available in every repository; <code>.codex/agents/</code> inside a checkout scopes them to that one. The skills look for an agent by name and delegate to it when one exists, and read the exports themselves when none does &mdash; so this step is optional, and skipping it costs context rather than correctness.</p>

      <p>One difference worth knowing: the Claude Code subagents are denied the writing tools one by one in their own definitions &mdash; the <code>recordings_</code> and <code>hubs_</code> families, <code>operations_cancel</code> and the two <code>ide_</code> tools that act on the editor, everything that writes including <code>heap_prepare</code> for the analyst, which only <code>heap-triage</code> keeps &mdash; so they cannot create a profile even if they tried. Codex has no per-agent tool deny-list, so the Codex versions are sandboxed read-only against your files and told not to write &mdash; an instruction rather than a wall. If that distinction matters to you, deny the family at the server instead:</p>
      <DocsCodeBlock :code="ingestDenyRule" language="toml" />

      <h2 id="approvals">Approvals</h2>
      <p>Codex asks before each tool the first time. Only <router-link to="/docs/microscope-mcp/clients#what-writes">nine tools write</router-link>, so approving the server once is usually what you want:</p>
      <DocsCodeBlock :code="approvalRule" language="toml" />

      <p>Tool names arrive prefixed with the server they came from &mdash; <code>mcp__jeffrey__flamegraph_export</code> and so on. <code>enabled_tools</code> and <code>disabled_tools</code> on the same block narrow what the model sees at all, which is the sharper instrument when you want a strictly read-only Jeffrey for one machine regardless of what the server advertises.</p>

      <h2 id="timeouts">Timeouts on Long Calls</h2>
      <p>Codex abandons a tool call after <strong>sixty seconds</strong> by default. The <router-link to="/docs/microscope-mcp/clients#long-calls">long tools</router-link> are built for that &mdash; they hand back <code>running</code> and an <code>operationId</code> well inside it &mdash; but if you would rather wait than poll, raise the client&rsquo;s own timeout:</p>
      <DocsCodeBlock :code="timeoutConfig" language="toml" />

      <h2 id="the-tool-list">The Size of the Tool List</h2>
      <p>This is the one place Codex and Claude Code differ in cost rather than capability. Claude Code fetches a tool&rsquo;s schema when it needs it; Codex loads every schema into the model&rsquo;s context on every turn. That is usually fine and occasionally not; when it matters, <router-link to="/docs/microscope-mcp/clients#the-tool-list">narrow the families Jeffrey advertises</router-link>, or use <code>enabled_tools</code> on the server block above to narrow what this client sees.</p>

      <h2 id="check-it-is-connected">Check It Is Connected</h2>
      <p>Run <code>codex mcp list</code>, or <code>/mcp</code> inside a session. The <code>jeffrey</code> server should be listed with its tools; if it is not, <router-link to="/docs/microscope-mcp/clients#when-it-is-not-connected">the usual causes</router-link> apply. A server that shows as connected but whose tools never appear is worth reporting upstream rather than debugging in Jeffrey &mdash; Codex's Streamable HTTP client has had that failure mode.</p>

      <h2 id="updating-and-removing">Updating and Removing</h2>
      <p>Refresh the marketplace, which pulls the repository again and offers the newer plugin:</p>
      <DocsCodeBlock :code="update" language="bash" />

      <p>A new thread applies it. To remove the plugin, uninstall it from <code>/plugins</code>, or drop the marketplace entirely:</p>
      <DocsCodeBlock :code="removal" language="bash" />

      <p>To keep it installed but silent for a while, set <code>enabled = false</code> under its <code>[plugins."microscope@jeffrey"]</code> block in <code>~/.codex/config.toml</code>. Individual skills can be switched off the same way, with a <code>[[skills.config]]</code> entry naming the skill's path.</p>


      <h2 id="without-the-plugin">Without the Plugin</h2>
      <p>The endpoint is an ordinary MCP server, so one command connects it with no marketplace involved:</p>
      <DocsCodeBlock :code="manualAdd" language="bash" />

      <p>Or write the <code>[mcp_servers.jeffrey]</code> block from <a href="#pointing-it-elsewhere">above</a> straight into <code>~/.codex/config.toml</code>. Use the address you actually reach Jeffrey on &mdash; behind a container, a proxy or a non-default port, <code>localhost:8585</code> is not it.</p>

      <p>What you give up is the skills: the entry sequence and the two database schemas. The tools still work; the model just starts colder, and is more likely to guess a column name than to call <code>jfr_describeTable</code> first.</p>

      <h2 id="what-differs-from-claude-code">What Differs from Claude Code</h2>
      <p>The tools and the skills are identical. Everything below is a property of the plugin formats, not of Jeffrey.</p>

      <div class="docs-compare-wrap">
        <table class="docs-compare">
          <thead>
            <tr>
              <th scope="col">Difference</th>
              <th scope="col" class="is-baseline">
                <span class="docs-compare-client is-baseline">Claude Code</span>
              </th>
              <th scope="col" class="is-primary">
                <span class="docs-compare-client is-primary">Codex</span>
              </th>
            </tr>
          </thead>
          <tbody>
          <tr>
            <th scope="row">Manifest</th>
            <td class="is-baseline" data-client="Claude Code"><span class="docs-compare-value">.claude-plugin/plugin.json</span></td>
            <td class="is-primary" data-client="Codex">Agent Plugins <span class="docs-compare-value">plugin.json</span> + <span class="docs-compare-value">mcp.json</span></td>
          </tr>
          <tr>
            <th scope="row">Install</th>
            <td class="is-baseline" data-client="Claude Code"><span class="docs-compare-value">/plugin install microscope@jeffrey</span></td>
            <td class="is-primary" data-client="Codex"><span class="docs-compare-value">codex plugin marketplace add</span>, then <span class="docs-compare-value">/plugins</span></td>
          </tr>
          <tr>
            <th scope="row">Skills</th>
            <td class="is-baseline" data-client="Claude Code">Ten, invoked <span class="docs-compare-value">/microscope:analyze-jfr</span></td>
            <td class="is-primary" data-client="Codex">The same ten, invoked <span class="docs-compare-value">$analyze-jfr</span></td>
          </tr>
          <tr>
            <th scope="row">Endpoint</th>
            <td class="is-baseline" data-client="Claude Code">A per-machine setting</td>
            <td class="is-primary" data-client="Codex">Fixed at <span class="docs-compare-value">localhost:8585</span>; anything else is a <span class="docs-compare-value">config.toml</span> block</td>
          </tr>
          <tr>
            <th scope="row">Agents</th>
            <td class="is-baseline" data-client="Claude Code">All three, shipped and tool-restricted</td>
            <td class="is-primary" data-client="Codex">The same three, copied by hand and restricted by instruction</td>
          </tr>
          <tr>
            <th scope="row">Tool prefix</th>
            <td class="is-baseline" data-client="Claude Code"><span class="docs-compare-value">mcp__plugin_microscope_jeffrey__</span></td>
            <td class="is-primary" data-client="Codex"><span class="docs-compare-value">mcp__jeffrey__</span></td>
          </tr>
          </tbody>
        </table>
      </div>

      <p>The two that bite are the endpoint and the agent. Neither has a workaround inside the plugin: they are the price of a format that six vendors agreed on, and both are one file away from being solved by hand.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
