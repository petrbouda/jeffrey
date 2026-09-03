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

const customUrl = `Jeffrey MCP endpoint: http://localhost:9000/api/internal/mcp`;

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
      title="Claude Code Plugin"
      icon="bi bi-plug"
    />

    <div class="docs-content">
      <p>The <strong>Microscope plugin</strong> packages the MCP server together with a few skills, so connecting is one install rather than a hand-written command per machine and per repository. It is a convenience over the plain server &mdash; everything it does can be done by hand, as <router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> describes.</p>

      <DocsCallout type="info" title="Jeffrey has to be running">
        The plugin installs and loads whether or not Jeffrey is serving, and then every tool call fails. The server is on by default, so a running Jeffrey is usually all it takes &mdash; <strong>Settings &rarr; Claude Code (MCP)</strong> reports whether the endpoint is serving. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
      </DocsCallout>

      <h2 id="install-it">Install It</h2>
      <p>From inside Claude Code:</p>
      <DocsCodeBlock :code="installMarketplace" language="bash" />

      <p>The first line registers the Jeffrey repository as a plugin marketplace; the second installs the <code>microscope</code> plugin from it. Read the pair as &ldquo;the Microscope plugin, from the Jeffrey marketplace&rdquo;.</p>

      <p>Or, if you would rather work from a clone &mdash; useful when developing against a modified Jeffrey:</p>
      <DocsCodeBlock :code="installLocal" language="bash" />

      <p>The marketplace is the repository itself, read straight off its default branch, so <code>/plugin marketplace update</code> is all it takes to pick up a newer plugin. To hold a machine on one release instead, add the marketplace at a tag:</p>
      <DocsCodeBlock :code="pinnedInstall" language="bash" />

      <h2 id="pointing-it-elsewhere">Pointing It Elsewhere</h2>
      <p>The plugin ships pointed at <code>http://localhost:8585/api/internal/mcp</code>. For any other address &mdash; a different port, a container, an SSH tunnel &mdash; change the endpoint in the plugin's own configuration. Claude Code offers the field when you enable the plugin, and <code>/plugin</code> reopens it afterwards:</p>
      <DocsCodeBlock :code="customUrl" language="text" />

      <p>The value is stored per machine, in <code>~/.claude/settings.json</code>. One plugin therefore serves every installation: a non-default port is a setting, not an edit to the manifest, and a laptop can point at a tunnelled staging Jeffrey while the machine beside it stays on localhost.</p>

      <h2 id="what-the-plugin-adds">What the Plugin Adds</h2>
      <p>Registering the server by hand gives you the forty-three tools. The plugin adds three things on top.</p>

      <p><strong>The endpoint, already configured</strong> &mdash; including the per-machine setting above, so the same install works on a laptop and against a tunnelled staging Jeffrey.</p>

      <p><strong>Five skills</strong>, which Claude picks up on its own when a question calls for them, and which you can also invoke directly:</p>
      <ul>
        <li><code>/microscope:analyze-jfr</code> &mdash; where to start and which family answers which question</li>
        <li><code>/microscope:analyze-heap</code> &mdash; a heap dump end to end: what is holding the memory, what is leaking, and the order the twenty heap tools have to be run in</li>
        <li><code>/microscope:advise-jfr</code> &mdash; from a profile to a code change: hot frames mapped to your checkout, a recommendation, then the edit and a re-profile on request</li>
        <li><code>/microscope:jfr-sql</code> &mdash; the JFR schema and the DuckDB idioms that go with it</li>
        <li><code>/microscope:heap-sql</code> &mdash; the heap-dump index schema</li>
      </ul>

      <p>The <router-link to="/docs/microscope-mcp/skills">Skills</router-link> page covers what each one carries and why it exists.</p>

      <p><strong>One subagent</strong>, <code>microscope:profile-analyst</code>. A single <code>flamegraph_export</code> can run to 120,000 characters &mdash; the cap a tool result is truncated at &mdash; and answering a question properly often takes several of them. The analyst runs a sequence and returns only the findings: the hot frames with their <code>total</code> and <code>self</code> shares, or the retaining classes with their retained bytes and GC-root paths. Everything it read stays in its own context window rather than crowding out the conversation you are having.</p>

      <p>The skills hand it the reading and keep what actually needs your session: mapping frames onto the checkout, the recommendation, and every question put to you. <code>advise-jfr</code> uses it hardest, sending CPU, wall-clock, allocation and blocking out as four parallel delegations instead of pulling four documents into one context. Its tools are the read-only MCP families and nothing else &mdash; no file access, no <code>recordings_</code> &mdash; so it can neither touch your repository nor create a profile.</p>

      <h2 id="permissions">Permissions</h2>
      <p>Claude Code asks before each tool the first time. Every tool here reads except the <code>recordings_</code> family, which imports a recording file and builds a profile from it, so approving the whole family once is usually what you want &mdash; from the prompt, or up front with <code>/permissions</code>:</p>
      <DocsCodeBlock :code="permissionRule" language="bash" />

      <p>The name reads <code>mcp__plugin_&lt;plugin&gt;_&lt;server&gt;__&lt;tool&gt;</code>: the <code>microscope</code> plugin, the <code>jeffrey</code> server inside it. In a non-interactive run there is no prompt to answer, so the rule has to be passed explicitly or the run stalls:</p>
      <DocsCodeBlock :code="headlessRun" language="bash" />

      <h2 id="check-it-is-connected">Check It Is Connected</h2>
      <p>Run <code>/mcp</code> in Claude Code. The <code>jeffrey</code> server should be listed as connected. If it is not, in order of likelihood: the MCP server is still off in Settings, Jeffrey is not on the address the plugin is pointed at, or Jeffrey is not running.</p>

      <h2 id="updating-and-removing">Updating and Removing</h2>
      <p>Refresh the marketplace, then update the plugin from it &mdash; a restart of Claude Code applies the new version:</p>
      <DocsCodeBlock :code="update" language="bash" />

      <p>To remove it:</p>
      <DocsCodeBlock :code="removal" language="bash" />

      <p>Uninstalling takes the skills with it. It does not change anything inside Jeffrey &mdash; the MCP server keeps serving.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
