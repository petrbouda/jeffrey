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
  { id: 'what-the-extension-adds', text: 'What the Extension Adds', level: 2 },
  { id: 'the-agents', text: 'The Agents', level: 2 },
  { id: 'tool-names', text: 'Tool Names Are Spelled Differently', level: 2 },
  { id: 'approvals', text: 'Approvals', level: 2 },
  { id: 'timeouts', text: 'Timeouts on Long Calls', level: 2 },
  { id: 'the-tool-list', text: 'The Size of the Tool List', level: 2 },
  { id: 'check-it-is-connected', text: 'Check It Is Connected', level: 2 },
  { id: 'updating-and-removing', text: 'Updating and Removing', level: 2 },
  { id: 'without-the-extension', text: 'Without the Extension', level: 2 },
  { id: 'what-differs', text: 'What Differs from Claude Code', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const installLocal = `git clone https://github.com/petrbouda/jeffrey
gemini extensions install ./jeffrey/jeffrey-claude-plugin`;

const manifest = `{
  "name": "microscope",
  "version": "1.0.0",
  "settings": [
    {
      "name": "Jeffrey MCP endpoint",
      "description": "URL of the Microscope MCP endpoint.",
      "envVar": "JEFFREY_MCP_ENDPOINT"
    }
  ],
  "mcpServers": {
    "jeffrey": {
      "httpUrl": "\${JEFFREY_MCP_ENDPOINT:-http://localhost:8585/api/mcp}",
      "timeout": 900000
    }
  }
}`;

const customUrl = `{
  "mcpServers": {
    "jeffrey": {
      "httpUrl": "http://localhost:9000/api/mcp"
    }
  }
}`;

const trustServer = `{
  "mcpServers": {
    "jeffrey": {
      "httpUrl": "http://localhost:8585/api/mcp",
      "trust": true
    }
  }
}`;

const excludeWriters = `{
  "mcpServers": {
    "jeffrey": {
      "httpUrl": "http://localhost:8585/api/mcp",
      "excludeTools": [
        "recordings_analyzeFile", "recordings_analyzeRecording",
        "hubs_download", "ide_link", "ide_open"
      ]
    }
  }
}`;

const familiesProperty = `# On the Jeffrey side, in application.properties
jeffrey.microscope.mcp.families=profiles,flamegraph,jvm,heap`;

const agentTools = `tools:
  - mcp_jeffrey_*`;

const agentInstall = `mkdir -p ~/.gemini/agents
cp jeffrey/jeffrey-claude-plugin/gemini/agents/profile-analyst.md ~/.gemini/agents/
cp jeffrey/jeffrey-claude-plugin/gemini/agents/heap-triage.md ~/.gemini/agents/`;

const endpointEnv = `export JEFFREY_MCP_ENDPOINT=http://localhost:9000/api/mcp`;

const manualAdd = `gemini mcp add jeffrey http://localhost:8585/api/mcp \\
  --transport http --scope user --timeout 900000`;

const reconfigure = `gemini extensions config microscope`;

const removal = `gemini extensions uninstall microscope`;

const update = `gemini extensions update microscope`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Gemini CLI"
      icon="bi bi-terminal"
    />

    <div class="docs-content">
      <p>The <strong>Microscope plugin</strong> installs into Gemini CLI as an <strong>extension</strong>. One directory in the Jeffrey repository now carries three manifests &mdash; the one <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> reads, the <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> one <router-link to="/docs/microscope-mcp/codex">Codex</router-link> and its neighbours read, and <code>gemini-extension.json</code> &mdash; over a single set of skills and a single MCP server.</p>

      <p>What Gemini takes from the package is the server, the skills and the session-start check that says whether Jeffrey is actually running. The agents are copied by hand, for the reason <a href="#the-agents">below</a>.</p>

      <DocsCallout type="info" title="Jeffrey has to be running">
        The extension installs and loads whether or not Jeffrey is serving, and then every tool call fails. The server is on by default, so a running Jeffrey is usually all it takes. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
      </DocsCallout>

      <h2 id="install-it">Install It</h2>
      <p>Gemini installs an extension from a directory holding a <code>gemini-extension.json</code>, which is <code>jeffrey-claude-plugin/</code> in a clone:</p>
      <DocsCodeBlock :code="installLocal" language="bash" />

      <p>Start a new session afterwards &mdash; an extension's servers and skills are loaded when the session begins, not mid-conversation. <code>/extensions</code> lists what loaded.</p>

      <p>The GitHub-URL form of that command does not work here: Gemini looks for the manifest at the root of whatever it clones, and in this repository it lives one directory down.</p>

      <p>Gemini asks for the endpoint while installing &mdash; keep the default unless your Jeffrey is elsewhere. <code>--skip-settings</code> skips the question. The manifest behind it:</p>
      <DocsCodeBlock :code="manifest" language="json" />

      <p><code>httpUrl</code> is one of <strong>three spellings Gemini accepts</strong> for the same Streamable HTTP server, and the shorthand for &ldquo;this is HTTP, do not guess&rdquo;. <code>url</code> with <code>&quot;type&quot;: &quot;http&quot;</code> is the same thing &mdash; it is what <code>gemini mcp add --transport http</code> writes &mdash; and a bare <code>url</code> with no <code>type</code> also works, because Gemini tries HTTP first and only falls back to SSE when that fails. Only <code>&quot;type&quot;: &quot;sse&quot;</code> is wrong here, since this server offers no SSE stream. <code>/mcp</code> prints the transport it settled on.</p>

      <h2 id="pointing-it-elsewhere">Pointing It Elsewhere</h2>
      <p><strong>Gemini has a setting, as Claude Code does</strong> &mdash; unlike Codex, where the endpoint really is fixed. The extension declares one, and the manifest reads it with a default, so <code>localhost:8585</code> stands until you give it something else. Answer the question at install time, or export the variable the setting is named after:</p>
      <DocsCodeBlock :code="endpointEnv" language="bash" />

      <p>That is the same variable the session-start check reads, so the probe and the tools cannot end up pointed at different machines. To change the answer you gave at install time:</p>
      <DocsCodeBlock :code="reconfigure" language="bash" />

      <p>To skip the extension's server altogether, register your own in <code>~/.gemini/settings.json</code> for every project, or a checkout's <code>.gemini/settings.json</code> for one:</p>
      <DocsCodeBlock :code="customUrl" language="json" />

      <p>Keep the name <code>jeffrey</code>: the skills name tools by the part after the prefix, and the prefix is built from the server's name. Then remove the extension, or accept that the same tools are registered twice.</p>

      <h2 id="what-the-extension-adds">What the Extension Adds</h2>
      <p>Registering the server by hand gives you every tool. The extension adds the endpoint already configured, a check that Jeffrey is serving when a session starts, and <strong>ten skills</strong>, which Gemini loads on its own when a question calls for one:</p>
      <ul>
        <li><code>analyze-jfr</code> &mdash; where to start and which family answers which question</li>
        <li><code>analyze-heap</code> &mdash; a heap dump end to end: what is holding the memory, what is leaking, and the order the heap tools have to be run in</li>
        <li><code>analyze-hub</code> &mdash; the recordings that never reached this machine: finds the session across the connected Jeffrey Hubs, pulls it in, and hands off</li>
        <li><code>compare-jfr</code> &mdash; before against after: which methods moved, and whether the two recordings were comparable in the first place</li>
        <li><code>profile-run</code> &mdash; a workload that has not been recorded yet: what to run it under, for how long, and where the file has to land</li>
        <li><code>regression-check</code> &mdash; the same before-and-after question starting from two revisions rather than two profiles</li>
        <li><code>advise-jfr</code> &mdash; from a profile to a code change: hot frames mapped to your checkout, a recommendation, then the edit</li>
        <li><code>jfr-sql</code> and <code>heap-sql</code> &mdash; the two database schemas and the DuckDB idioms that go with them</li>
        <li><code>report</code> &mdash; the evidence discipline the other nine write to</li>
      </ul>

      <p><code>/skills</code> lists what a session actually loaded, and <code>gemini extensions list</code> prints them with the server and the endpoint setting from outside one. They are the same files Claude Code and Codex load &mdash; all three read the <a href="https://agentskills.io/specification" target="_blank" rel="noopener">Agent Skills</a> format, so the directory is shared rather than duplicated. The <router-link to="/docs/microscope-mcp/skills">Skills</router-link> page covers what each one carries and why it exists.</p>

      <h2 id="the-agents">The Agents</h2>
      <p>A single <code>flamegraph_export</code> can run to 120,000 characters, and answering a question properly often takes several. The <router-link to="/docs/microscope-mcp/agent">agents</router-link> run a sequence and return only the findings, leaving everything they read in their own context.</p>

      <p>Gemini gets <strong>two of the three</strong>, and they are files to copy:</p>
      <DocsCodeBlock :code="agentInstall" language="bash" />

      <p><code>~/.gemini/agents/</code> makes them available in every repository; <code>.gemini/agents/</code> inside a checkout scopes them to that one, and <code>/agents</code> lists what loaded. The skills delegate to an agent of that name when the client has one and read the exports themselves when it does not, so skipping this costs context rather than correctness.</p>

      <p><strong>The extension cannot carry them</strong>, even though Gemini does read an installed extension's <code>agents/</code> directory. It validates that frontmatter against a strict schema and rejects any key it does not define, and the plugin's own agents carry Claude Code's <code>disallowedTools</code>, <code>skills</code> and <code>color</code>; their tool patterns are not names Gemini accepts either. The copies above are the same agents written in the dialect it does accept.</p>

      <DocsCallout type="warning" title="Those three files are noisy">
        Gemini prints a validation error for each of them &mdash; <em>Unrecognized key(s)</em>, then a line per tool pattern &mdash; and it prints them on ordinary commands, not only in debug mode. Nothing else follows from it: the extension loads, the server connects, the skills work. It is the cost of one directory that has to satisfy Claude Code, whose plugins read <code>agents/</code> and nothing else.
      </DocsCallout>

      <p>There is <strong>no <code>profile-lead</code> for Gemini at all</strong>, and that is a property of the client rather than an omission: <strong>a Gemini subagent may not dispatch another subagent</strong>, and dispatching the other two is the whole of what the lead does. Ask an open-ended question in the main conversation instead; <code>analyze-jfr</code> carries the same triage order, and the two specialists work beneath it.</p>

      <p>One more difference worth knowing. The Claude Code subagents are <em>denied</em> the writing tools by their own definitions, so they cannot import a recording even if they tried. Gemini subagents take an allow-list with no deny-list, so what keeps <code>profile-analyst</code> off <code>recordings_</code>, <code>hubs_download</code> and the two <code>ide_</code> tools there is the <em>No writing</em> rule in its own instructions &mdash; the same footing it has in Codex. If that distinction matters to you, keep those tools away from the whole session instead:</p>
      <DocsCodeBlock :code="excludeWriters" language="json" />

      <h2 id="tool-names">Tool Names Are Spelled Differently</h2>
      <p>Gemini gives every MCP tool a fully qualified name of the form <code>mcp_{serverName}_{toolName}</code> &mdash; so <code>flamegraph_export</code> arrives as <code>mcp_jeffrey_flamegraph_export</code>, with single underscores where Claude Code and Codex use double ones. It matters in exactly two places: an allow-list you write by hand, and a subagent's <code>tools</code>:</p>
      <DocsCodeBlock :code="agentTools" language="yaml" />

      <p><strong>A wildcard there covers a server, not a family.</strong> <code>mcp_jeffrey_*</code> is valid and means every Jeffrey tool; <code>mcp_jeffrey_heap_*</code> is <em>not</em> a valid tool name, and one invalid entry makes the whole agent fail to load. Narrowing to a family means naming its tools one by one. The part after the prefix is the same everywhere and is exact and camelCase: <code>jfr_listTables</code>, never <code>jfr_list_tables</code>.</p>

      <h2 id="approvals">Approvals</h2>
      <p>Gemini asks before each tool the first time, and its answers &mdash; <em>Proceed once</em>, <em>Always allow this tool</em>, <em>Always allow this server</em> &mdash; build the allow-list as you go. Every Jeffrey tool reads except the six named on the <router-link to="/docs/microscope-mcp/tools">tool reference</router-link>, so allowing the server once is usually what you want. To decide up front instead:</p>
      <DocsCodeBlock :code="trustServer" language="json" />

      <p><code>trust</code> covers every tool on the server, <code>hubs_download</code> and the <code>ide_</code> pair included, which is why <code>excludeTools</code> above is the sharper instrument when you want a strictly read-only Jeffrey on one machine.</p>

      <h2 id="timeouts">Timeouts on Long Calls</h2>
      <p>Most of Jeffrey's tools answer in well under a second, but three do real work: importing a recording parses every event in it, and pulling a session off a hub moves however many gigabytes it holds. The extension asks for <strong>fifteen minutes</strong> (<code>timeout</code> is milliseconds) rather than leaving them on Gemini's ten.</p>

      <p>Those three are built for the wait either way. <code>recordings_analyzeFile</code> and <code>recordings_analyzeRecording</code> wait about forty-five seconds and then hand back a status of <code>running</code>, and <code>recordings_status</code> reports when the profile is ready. <strong>What matters is not retrying the analyze call:</strong> a second one imports the file again and builds a second profile of it.</p>

      <h2 id="the-tool-list">The Size of the Tool List</h2>
      <p>Jeffrey advertises a hundred-odd tools across eighteen families, and Gemini declares every enabled tool to the model on each turn. On its own that is comfortably inside the API's ceiling on function declarations; stacked with several other MCP servers it stops being comfortable, and the symptom is a request rejected for declaring too many functions rather than anything that looks like Jeffrey.</p>

      <p>There are two ways to narrow it, and they compose. On the Jeffrey side, advertise fewer families:</p>
      <DocsCodeBlock :code="familiesProperty" language="properties" />

      <p>On the Gemini side, <code>includeTools</code> or <code>excludeTools</code> on the server entry narrows what this client sees without touching what Jeffrey serves to anything else. Leave both alone unless you have a reason &mdash; the skills route between families freely, and one that is not advertised is one their advice sends the model to in vain.</p>

      <h2 id="check-it-is-connected">Check It Is Connected</h2>
      <p>Run <code>/mcp</code> inside a session, or <code>gemini mcp list</code> outside one. The <code>jeffrey</code> server should be listed with its tools. If it is listed but <strong>Disabled</strong>, the directory is untrusted &mdash; Gemini disables every MCP server in an untrusted folder, user-level ones included, and says so above the list; trust the folder and start again. If it is not listed at all, in order of likelihood: the session predates the install and needs restarting, this installation set <code>jeffrey.microscope.mcp.enabled=false</code>, Jeffrey is not on <code>localhost:8585</code>, or Jeffrey is not running.</p>

      <p><code>curl</code> against the endpoint settles which side is at fault; <router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> has the exact request.</p>

      <h2 id="updating-and-removing">Updating and Removing</h2>
      <p>Gemini copies an extension when it installs one, so a newer clone is not a newer extension until you say so:</p>
      <DocsCodeBlock :code="update" language="bash" />

      <p>To remove it:</p>
      <DocsCodeBlock :code="removal" language="bash" />

      <p>Uninstalling takes the skills and the session-start check with it. It does not change anything inside Jeffrey &mdash; the MCP server keeps serving &mdash; and it touches neither a server you registered yourself in <code>settings.json</code> nor an agent you copied into <code>~/.gemini/agents/</code>.</p>

      <h2 id="without-the-extension">Without the Extension</h2>
      <p>The endpoint is an ordinary MCP server, and one command registers it with no extension involved:</p>
      <DocsCodeBlock :code="manualAdd" language="bash" />

      <p><code>--scope user</code> writes <code>~/.gemini/settings.json</code> and serves every project; the default, <code>project</code>, writes the checkout's <code>.gemini/settings.json</code> instead. Or write the <code>mcpServers</code> block from <a href="#pointing-it-elsewhere">above</a> by hand. Either way, use the address you actually reach Jeffrey on &mdash; behind a container, a proxy or a non-default port, <code>localhost:8585</code> is not it.</p>

      <p>What you give up is the skills and the session-start check: the entry sequence, the two database schemas, and being told when Jeffrey is not answering. The tools still work; the model just starts colder, and is more likely to guess a column name than to call <code>jfr_describeTable</code> first. The server also offers the skills as MCP <strong>prompts</strong>, so they are not lost &mdash; somebody has to ask for one rather than the client loading it. The agents were never part of the extension anyway.</p>

      <h2 id="what-differs">What Differs from Claude Code</h2>
      <p>The tools and the skills are identical. Everything below is a property of the clients, not of Jeffrey.</p>

      <table>
        <thead>
          <tr>
            <th></th>
            <th>Claude Code</th>
            <th>Gemini CLI</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Manifest</td>
            <td><code>.claude-plugin/plugin.json</code></td>
            <td><code>gemini-extension.json</code></td>
          </tr>
          <tr>
            <td>Install</td>
            <td><code>/plugin install microscope@jeffrey</code></td>
            <td><code>gemini extensions install &lt;path&gt;</code></td>
          </tr>
          <tr>
            <td>Endpoint</td>
            <td>A per-machine setting</td>
            <td>A setting too, asked at install or read from the environment</td>
          </tr>
          <tr>
            <td>Tool prefix</td>
            <td><code>mcp__plugin_microscope_jeffrey__</code></td>
            <td><code>mcp_jeffrey_</code></td>
          </tr>
          <tr>
            <td>Agents</td>
            <td>All three, from the plugin</td>
            <td>Two, copied by hand: no subagent may dispatch another</td>
          </tr>
          <tr>
            <td>Read-only agents</td>
            <td>Enforced by a deny-list</td>
            <td>Instructed, or enforced with <code>excludeTools</code></td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip" title="From the IDE">
        The <router-link to="/docs/intellij-plugin">IntelliJ plugin</router-link>'s recording panel hands a profile straight to Gemini, alongside Claude Code and Codex &mdash; one button opens a terminal with the profile already identified and the matching skill triggered.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>
