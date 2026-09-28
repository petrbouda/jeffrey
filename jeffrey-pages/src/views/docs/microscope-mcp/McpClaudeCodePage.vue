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
  { id: 'before-you-start', text: 'Before You Start', level: 2 },
  { id: 'install-it', text: 'Install It', level: 2 },
  { id: 'pointing-it-elsewhere', text: 'Pointing It Elsewhere', level: 2 },
  { id: 'a-token', text: 'When Jeffrey Asks for a Token', level: 2 },
  { id: 'what-the-plugin-adds', text: 'What the Plugin Adds', level: 2 },
  { id: 'permissions', text: 'Permissions', level: 2 },
  { id: 'long-answers', text: 'Long Answers', level: 2 },
  { id: 'the-startup-check', text: 'The Startup Check', level: 2 },
  { id: 'check-it-is-connected', text: 'Check It Is Connected', level: 2 },
  { id: 'updating-and-removing', text: 'Updating and Removing', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const v2Runtime = `export MCP_SDK_GENERATION=v2
claude`;

const installMarketplace = `/plugin marketplace add petrbouda/jeffrey
/plugin install microscope@jeffrey`;

const installLocal = `git clone https://github.com/petrbouda/jeffrey
claude --plugin-dir ./jeffrey/jeffrey-claude-plugin`;

const customUrl = `Jeffrey MCP endpoint: http://localhost:9000/api/mcp`;

const tokenSetting = `Jeffrey MCP token: the-token-jeffrey-is-configured-with`;

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

      <h2 id="before-you-start">Before You Start</h2>
      <p>Jeffrey speaks <strong>MCP <code>2026-07-28</code> only</strong>. A client that still opens with <code>initialize</code> is refused with <code>-32602</code> (HTTP 400), an error whose message and <code>data.supported</code> name that version. In Claude Code, only the <strong>v2 MCP runtime</strong> speaks <code>2026-07-28</code>; the v1 runtime cannot connect to Jeffrey at all.</p>

      <p>Claude Code picks the runtime when it starts. On v2.1.274 or later, v2 is the default for sessions on Amazon Bedrock, Claude Platform on AWS, Google Cloud&rsquo;s Agent Platform or Microsoft Foundry, sessions signed in through a Claude apps gateway, and sessions with telemetry or feature-flag fetching turned off. Everywhere else &mdash; or whenever you are not sure &mdash; start Claude Code with the v2 runtime chosen explicitly:</p>
      <DocsCodeBlock :code="v2Runtime" language="bash" />

      <p>Leave <code>MCP_PROTOCOL_NEGOTIATION</code> unset: set to <code>legacy</code>, it keeps even the v2 runtime on the old handshake. <a href="https://code.claude.com/docs/en/mcp" target="_blank" rel="noopener">Claude Code&rsquo;s MCP documentation</a> is the authority on which runtime a session gets.</p>

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

      <h2 id="a-token">When Jeffrey Asks for a Token</h2>
      <p>A Jeffrey with <router-link to="/docs/microscope-mcp/enabling#bearer-token"><code>jeffrey.microscope.mcp.token</code></router-link> set answers <code>401</code> to every request without it. The plugin&rsquo;s second setting holds the token, and the plugin sends it as <code>Authorization: Bearer &hellip;</code> on every request:</p>
      <DocsCodeBlock :code="tokenSetting" language="text" />
      <p>It is a sensitive setting, so Claude Code keeps it in the system&rsquo;s secure storage rather than in <code>settings.json</code>. Leave it empty for a Jeffrey without a token: the header then goes out empty and Jeffrey ignores it. The startup check sends the same value. Under Claude Code the <code>JEFFREY_MCP_TOKEN</code> environment variable plays no part: the plugin&rsquo;s server never reads it, so exporting it changes nothing &mdash; the setting is the only place the token goes.</p>

      <h2 id="what-the-plugin-adds">What the Plugin Adds</h2>
      <p>The endpoint, already configured &mdash; including the per-machine setting above, so the same install works on a laptop and against a tunnelled staging Jeffrey &mdash; the <a href="#the-startup-check">startup check</a> below, the <router-link to="/docs/microscope-mcp/clients#the-skills">ten skills</router-link>, invoked directly as <code>/microscope:analyze-jfr</code>, and the <router-link to="/docs/microscope-mcp/agent">three subagents</router-link> as <code>microscope:profile-analyst</code>, <code>microscope:heap-triage</code> and <code>microscope:profile-lead</code>, which arrive with the plugin and need no install step. Claude Code is the only client that can carry them: its subagent definitions restrict tools &mdash; a deny-list for <code>profile-analyst</code>, allow-lists for <code>heap-triage</code> and <code>profile-lead</code> &mdash; so all three are held to reading a profile rather than told to; the only writers left to them &mdash; <code>heap_prepare</code> and <code>heap_oql</code> for heap-triage, <code>jvm_autoAnalysis</code> and <code>heap_oql</code> for profile-analyst, <code>jvm_autoAnalysis</code> for the lead &mdash; only fill a cache.</p>

      <h2 id="permissions">Permissions</h2>
      <p>Claude Code asks before each tool the first time. Only <router-link to="/docs/microscope-mcp/clients#what-writes">eleven tools write</router-link>, so approving the read-only families once is usually what you want &mdash; from the prompt, or up front with <code>/permissions</code>:</p>
      <DocsCodeBlock :code="permissionRule" language="bash" />

      <p>The name reads <code>mcp__plugin_&lt;plugin&gt;_&lt;server&gt;__&lt;tool&gt;</code>: the <code>microscope</code> plugin, the <code>jeffrey</code> server inside it. In a non-interactive run there is no prompt to answer, so the rule has to be passed explicitly or the run stalls:</p>
      <DocsCodeBlock :code="headlessRun" language="bash" />

      <h2 id="long-answers">Long Answers</h2>
      <p>Claude Code warns when a tool result passes 10,000 tokens and, past 25,000 by default, writes the result to a file and hands the model its path, which costs a second read for the answer it asked for. The seven Jeffrey tools whose answers run long &mdash; the flamegraph, compare and trace exports, <code>jfr_executeQuery</code> and <code>heap_getDominatorTreeRoots</code> &mdash; <router-link to="/docs/microscope-mcp/tools#result-size">declare a larger limit for themselves</router-link>, so they stay inline with nothing to set; to raise the default for every MCP tool instead, start Claude Code with <code>MAX_MCP_OUTPUT_TOKENS</code> set, for example <code>MAX_MCP_OUTPUT_TOKENS=50000 claude</code>. <code>flamegraph_export</code> with <code>detail: "SUMMARY"</code> is the cheaper answer when the whole tree is not yet needed.</p>

      <h2 id="the-startup-check">The Startup Check</h2>
      <p>The plugin installs one hook, on <code>SessionStart</code>. It asks Jeffrey whether it is serving, and says nothing when it is.</p>

      <p>It exists because the commonest way a session goes wrong is the dullest: Jeffrey is not running, or is running somewhere else. Without the check the model discovers that by calling a tool and reading a connection error &mdash; usually several turns in, often after telling you what it is about to do. With it, the session opens knowing, and tells you rather than retrying.</p>

      <p>It is deliberately quiet when all is well and says nothing on success. Otherwise it tells four cases apart by the HTTP status: nothing answering at the endpoint; <code>401</code>, a missing or mismatched <a href="#a-token">token</a> &mdash; set the plugin&rsquo;s <em>Jeffrey MCP token</em> setting (<code>/plugin</code> &rarr; microscope), which is also what Jeffrey&rsquo;s own <code>401</code> names for Claude Code; <code>403</code>, a refused host or origin, where it quotes Jeffrey&rsquo;s own reason, which names the property to change (<code>host.docker.internal</code> missing from <code>jeffrey.microscope.mcp.allowed-hosts</code> is the usual one); and anything else, which means something answers but is not a Jeffrey MCP server. Each is a failure the model would otherwise discover by calling a tool halfway through a plan.</p>

      <DocsCallout type="info" title="Claude Code only">
        Hooks are not part of the <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> format, which defines exactly two component types: skills and MCP servers. A Codex install gets the skills and the server, and finds out that Jeffrey is down the way it always did.
      </DocsCallout>

      <h2 id="check-it-is-connected">Check It Is Connected</h2>
      <p>Run <code>/mcp</code> in Claude Code. The <code>jeffrey</code> server should be listed as connected; if it is not, <router-link to="/docs/microscope-mcp/clients#when-it-is-not-connected">the usual causes</router-link> apply, and the address the plugin is pointed at is the one to check first here.</p>

      <p>One cause is Claude Code&rsquo;s own: a session on the v1 MCP runtime. The startup check stays silent then &mdash; it asks Jeffrey whether it is serving, and it is &mdash; while <code>/mcp</code> shows the server failing and Jeffrey&rsquo;s log records a <code>-32602</code> refusal naming <code>2026-07-28</code>. Restart with <code>MCP_SDK_GENERATION=v2</code>, as in <a href="#before-you-start">Before You Start</a>.</p>

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
