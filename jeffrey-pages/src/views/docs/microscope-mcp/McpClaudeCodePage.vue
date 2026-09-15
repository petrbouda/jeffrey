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
  { id: 'permissions', text: 'Permissions', level: 2 },
  { id: 'the-startup-check', text: 'The Startup Check', level: 2 },
  { id: 'check-it-is-connected', text: 'Check It Is Connected', level: 2 },
  { id: 'updating-and-removing', text: 'Updating and Removing', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const installMarketplace = `/plugin marketplace add petrbouda/jeffrey
/plugin install microscope@jeffrey`;

const installLocal = `git clone https://github.com/petrbouda/jeffrey
claude --plugin-dir ./jeffrey/jeffrey-claude-plugin`;

const customUrl = `Jeffrey MCP endpoint: http://localhost:9000/api/mcp`;

const pinnedInstall = `/plugin marketplace add petrbouda/jeffrey@v1.2.0`;

const permissionRule = `mcp__plugin_microscope_jeffrey__*`;

const headlessRun = `claude -p "list the Jeffrey profiles" \\
  --allowedTools "mcp__plugin_microscope_jeffrey__*"`;

const update = `/plugin marketplace update jeffrey
/plugin update microscope@jeffrey`;

const removal = `/plugin uninstall microscope@jeffrey`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Claude Code"
      icon="bi bi-plug"
    />

    <div class="docs-content">
      <p>Claude Code reads the <code>.claude-plugin/</code> manifest of the <strong>Microscope plugin</strong>, so connecting is one install rather than a hand-written command per machine and per repository. What the plugin brings &mdash; the skills, the agents, which tools write, how a long call behaves &mdash; is the same for every agent and is on <router-link to="/docs/microscope-mcp/clients">Every Client</router-link>. This page is what Claude Code does differently: a <strong>configurable endpoint</strong>, the <strong>subagents</strong> with a real deny-list, and a <strong>startup check</strong> &mdash; none of which the portable format carries.</p>

      <h2 id="install-it">Install It</h2>
      <p>From inside Claude Code:</p>
      <DocsCodeBlock :code="installMarketplace" language="bash" />

      <p>The first line registers the Jeffrey repository as a plugin marketplace; the second installs the <code>microscope</code> plugin from it. Read the pair as &ldquo;the Microscope plugin, from the Jeffrey marketplace&rdquo;.</p>

      <p>Or, if you would rather work from a clone &mdash; useful when developing against a modified Jeffrey:</p>
      <DocsCodeBlock :code="installLocal" language="bash" />

      <p>The marketplace is the repository itself, read straight off its default branch, so <code>/plugin marketplace update</code> is all it takes to pick up a newer plugin. To hold a machine on one release instead, add the marketplace at a tag:</p>
      <DocsCodeBlock :code="pinnedInstall" language="bash" />

      <h2 id="pointing-it-elsewhere">Pointing It Elsewhere</h2>
      <p>The plugin ships pointed at <code>http://localhost:8585/api/mcp</code>. For any other address &mdash; a different port, a container, an SSH tunnel &mdash; change the endpoint in the plugin's own configuration. Claude Code offers the field when you enable the plugin, and <code>/plugin</code> reopens it afterwards:</p>
      <DocsCodeBlock :code="customUrl" language="text" />

      <p>The value is stored per machine, in <code>~/.claude/settings.json</code>. One plugin therefore serves every installation: a non-default port is a setting, not an edit to the manifest, and a laptop can point at a tunnelled staging Jeffrey while the machine beside it stays on localhost.</p>

      <DocsCallout type="info" title="This part is Claude Code only">
        The portable Agent Plugins format forbids placeholder expansion in a server URL, so the plugin's Codex half is fixed at <code>localhost:8585</code> and a different address is a hand-written <code>config.toml</code> block. See <router-link to="/docs/microscope-mcp/codex">Codex</router-link>.
      </DocsCallout>

      <h2 id="what-the-plugin-adds">What the Plugin Adds</h2>
      <p>The endpoint, already configured &mdash; including the per-machine setting above, so the same install works on a laptop and against a tunnelled staging Jeffrey &mdash; the <a href="#the-startup-check">startup check</a> below, the <router-link to="/docs/microscope-mcp/clients#the-skills">ten skills</router-link>, invoked directly as <code>/microscope:analyze-jfr</code>, and the <router-link to="/docs/microscope-mcp/agent">three subagents</router-link> as <code>microscope:profile-analyst</code>, <code>microscope:heap-triage</code> and <code>microscope:profile-lead</code>, which arrive with the plugin and need no install step. Claude Code is the only client that can carry them: its subagent definitions take a tool deny-list, so all three are held to reading a profile rather than told to.</p>

      <h2 id="permissions">Permissions</h2>
      <p>Claude Code asks before each tool the first time. Only <router-link to="/docs/microscope-mcp/clients#what-writes">nine tools write</router-link>, so approving the read-only families once is usually what you want &mdash; from the prompt, or up front with <code>/permissions</code>:</p>
      <DocsCodeBlock :code="permissionRule" language="bash" />

      <p>The name reads <code>mcp__plugin_&lt;plugin&gt;_&lt;server&gt;__&lt;tool&gt;</code>: the <code>microscope</code> plugin, the <code>jeffrey</code> server inside it. In a non-interactive run there is no prompt to answer, so the rule has to be passed explicitly or the run stalls:</p>
      <DocsCodeBlock :code="headlessRun" language="bash" />

      <h2 id="the-startup-check">The Startup Check</h2>
      <p>The plugin installs one hook, on <code>SessionStart</code>. It asks Jeffrey whether it is serving, and says nothing when it is.</p>

      <p>It exists because the commonest way a session goes wrong is the dullest: Jeffrey is not running, or is running somewhere else. Without the check the model discovers that by calling a tool and reading a connection error &mdash; usually several turns in, often after telling you what it is about to do. With it, the session opens knowing, and tells you rather than retrying.</p>

      <p>It reports only reachability, and it is deliberately quiet when all is well: it says nothing on success, says so when nothing answers at the endpoint, and distinguishes the case where something answers but is not a Jeffrey MCP server. That is the failure the model would otherwise discover by calling a tool and getting a connection error halfway through a plan.</p>

      <DocsCallout type="info" title="Claude Code only">
        Hooks are not part of the <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> format, which defines exactly two component types: skills and MCP servers. A Codex install gets the skills and the server, and finds out that Jeffrey is down the way it always did.
      </DocsCallout>

      <h2 id="check-it-is-connected">Check It Is Connected</h2>
      <p>Run <code>/mcp</code> in Claude Code. The <code>jeffrey</code> server should be listed as connected; if it is not, <router-link to="/docs/microscope-mcp/clients#when-it-is-not-connected">the usual causes</router-link> apply, and the address the plugin is pointed at is the one to check first here.</p>

      <h2 id="updating-and-removing">Updating and Removing</h2>
      <p>Refresh the marketplace, then update the plugin from it &mdash; a restart of Claude Code applies the new version:</p>
      <DocsCodeBlock :code="update" language="bash" />

      <p>To remove it:</p>
      <DocsCodeBlock :code="removal" language="bash" />
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
