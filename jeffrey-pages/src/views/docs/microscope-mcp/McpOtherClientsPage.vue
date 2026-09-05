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

const promptsCall = `curl -s http://localhost:8585/api/internal/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":1,"method":"prompts/get","params":{"name":"analyze-jfr"}}'`;

const resourcesCall = `curl -s http://localhost:8585/api/internal/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"jeffrey://profiles"}}'`;

const headings = [
  { id: 'agent-plugins-clients', text: 'Agent Plugins Clients', level: 2 },
  { id: 'cursor', text: 'Cursor', level: 3 },
  { id: 'vs-code-and-github-copilot', text: 'VS Code and GitHub Copilot', level: 3 },
  { id: 'kiro', text: 'Kiro', level: 3 },
  { id: 'any-mcp-client', text: 'Any MCP Client', level: 2 },
  { id: 'what-you-give-up', text: 'What You Give Up', level: 2 },
  { id: 'prompts-and-resources', text: 'Prompts and Resources', level: 2 },
  { id: 'the-wire-protocol', text: 'The Wire Protocol', level: 2 },
  { id: 'a-session-by-hand', text: 'A Session by Hand', level: 2 },
  { id: 'errors', text: 'Errors', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const clonePlugin = `git clone https://github.com/petrbouda/jeffrey
# the plugin directory is ./jeffrey/jeffrey-claude-plugin`;

const vscodeMcpJson = `{
  "servers": {
    "jeffrey": {
      "type": "http",
      "url": "http://localhost:8585/api/internal/mcp"
    }
  }
}`;

const manualAdd = `claude mcp add --transport http jeffrey http://localhost:8585/api/internal/mcp

codex mcp add jeffrey --url http://localhost:8585/api/internal/mcp`;

const mcpJson = `{
  "mcpServers": {
    "jeffrey": {
      "type": "http",
      "url": "http://localhost:8585/api/internal/mcp"
    }
  }
}`;

const initialize = `curl -s -X POST http://localhost:8585/api/internal/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": { "protocolVersion": "2025-06-18" }
  }'`;

const initializeResult = `{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "tools": { "listChanged": false },
      "prompts": { "listChanged": false },
      "resources": { "subscribe": false, "listChanged": false }
    },
    "serverInfo": { "name": "jeffrey", "version": "1.0.0" }
  }
}`;

const toolsCall = `curl -s -X POST http://localhost:8585/api/internal/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "flamegraph_export",
      "arguments": {
        "profileId": "0195f0a2-...",
        "eventType": "jdk.ExecutionSample",
        "thresholdPct": 1.0
      }
    }
  }'`;

const toolError = `{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      { "type": "text", "text": "Error: Profile 0195f0a2-... has no heap dump. ..." }
    ],
    "isError": true
  }
}`;

const protocolError = `{
  "jsonrpc": "2.0",
  "id": 9,
  "error": { "code": -32601, "message": "Method not found: tools/nope" }
}`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Other Clients"
      icon="bi bi-terminal-split"
    />

    <div class="docs-content">
      <p>The plugin &mdash; in <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> or <router-link to="/docs/microscope-mcp/codex">Codex</router-link> &mdash; is a convenience over an ordinary MCP server. Anything that speaks MCP over Streamable HTTP can connect instead.</p>

      <h2 id="agent-plugins-clients">Agent Plugins Clients</h2>
      <p>The plugin carries an <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> manifest, the vendor-neutral format <strong>Cursor</strong>, <strong>GitHub Copilot</strong>, <strong>VS Code</strong> and <strong>Kiro</strong> read alongside Codex. Where a client installs a plugin from a directory, <code>jeffrey-claude-plugin/</code> in a clone is that directory:</p>
      <DocsCodeBlock :code="clonePlugin" language="bash" />

      <p>What is standardised is the manifest, the nine skills and the <code>streamable-http</code> server entry. Everything past that &mdash; how a plugin is browsed and installed, how skills are invoked, how tools are approved &mdash; is the client's own, and moves faster than this page can. The <router-link to="/docs/microscope-mcp/codex">Codex</router-link> page is the closest map, since it documents the same portable half in detail.</p>

      <p>None of them can carry the two agents, for the reason that page gives: Agent Plugins defines skills and MCP servers, and nothing else. And in all of them the endpoint is fixed at <code>localhost:8585</code>, because the format forbids placeholder expansion in a server URL &mdash; a Jeffrey anywhere else is registered by hand, as below.</p>

      <h3 id="cursor">Cursor</h3>
      <p>Install the plugin from the cloned directory through Cursor's plugin browser. Without it, add the server to Cursor's MCP configuration &mdash; <code>~/.cursor/mcp.json</code> for every project, <code>.cursor/mcp.json</code> for one &mdash; using the <code>mcpServers</code> entry from <a href="#any-mcp-client">Any MCP Client</a> below. The tools then appear as <code>jeffrey</code> in Cursor's MCP settings, one toggle per tool.</p>

      <h3 id="vs-code-and-github-copilot">VS Code and GitHub Copilot</h3>
      <p>Copilot's agent mode reads MCP servers from <code>.vscode/mcp.json</code> in the workspace, or from your user settings for every workspace. The shape differs slightly from the one Claude Code uses &mdash; the key is <code>servers</code>:</p>
      <DocsCodeBlock :code="vscodeMcpJson" language="json" />

      <p>Check it in and everyone working in that repository gets the same Jeffrey, assuming they run one. The command palette's <em>MCP: List Servers</em> shows whether it connected.</p>

      <h3 id="kiro">Kiro</h3>
      <p>Kiro reads MCP servers from <code>.kiro/settings/mcp.json</code> in the workspace or <code>~/.kiro/settings/mcp.json</code> for every workspace, in the same <code>mcpServers</code> shape as <a href="#any-mcp-client">below</a>. Its autoApprove list is the equivalent of the approval rules the plugin pages describe: naming the read-only tools there stops it asking each time.</p>

      <h2 id="any-mcp-client">Any MCP Client</h2>
      <p>Register the server directly &mdash; useful when you want it in one project only, or when you would rather not add a marketplace:</p>
      <DocsCodeBlock :code="manualAdd" language="bash" />

      <p>Or write it into a project&rsquo;s <code>.mcp.json</code>:</p>
      <DocsCodeBlock :code="mcpJson" language="json" />

      <DocsCallout type="tip" title="Both are offered ready-made">
        <strong>Settings &rarr; Coding Agents (MCP)</strong> shows both CLI commands, the <code>.mcp.json</code> entry and the <code>config.toml</code> block, with the URL your browser actually reached Jeffrey on &mdash; correct behind a container, a proxy or a non-default port, where <code>localhost:8585</code> is not.
      </DocsCallout>

      <h2 id="what-you-give-up">What You Give Up</h2>
      <p>The same ninety-nine tools, named <code>mcp__jeffrey__*</code> rather than the <code>mcp__plugin_microscope_jeffrey__*</code> Claude Code gives a plugin's server &mdash; a hand-registered server is not namespaced by a plugin. Adjust any approval rule accordingly: <code>/permissions</code> in Claude Code, the <code>[mcp_servers.jeffrey]</code> block in Codex.</p>

      <p>What does not come along as <em>skills</em> is the guidance: the entry sequence and the two database schemas. But it is not lost. The server offers the same files over the protocol as <strong>prompts</strong>, so a client that speaks <code>prompts/list</code> can load any of them without a plugin at all &mdash; see below. What is genuinely missing is the <router-link to="/docs/microscope-mcp/agent">agents</router-link>, which no MCP server can provide, and the automatic loading: a plugin client picks a skill up when a question calls for it, where here somebody has to ask for the prompt.</p>

      <h2 id="prompts-and-resources">Prompts and Resources</h2>
      <p>Two capabilities beyond the tools, and they exist for exactly this page&rsquo;s readers.</p>

      <p><strong>Prompts</strong> are the plugin&rsquo;s skills, served over the protocol. <code>prompts/list</code> names them &mdash; <code>analyze-jfr</code>, <code>analyze-heap</code>, <code>analyze-hub</code>, <code>compare-jfr</code>, <code>advise-jfr</code>, <code>profile-run</code>, <code>regression-check</code>, <code>jfr-sql</code>, <code>heap-sql</code> &mdash; and <code>prompts/get</code> returns one as a message to insert. They are the same files the plugin ships, copied onto the server&rsquo;s classpath when it is built, so they cannot drift from what a Claude Code or Codex user gets.</p>

      <DocsCodeBlock :code="promptsCall" language="bash" />

      <p><strong>Resources</strong> are the parts of a profile a client can attach rather than call for. <code>jeffrey://profiles</code> is the catalogue; <code>resources/templates/list</code> offers <code>jeffrey://profile/&#123;profileId&#125;/summary</code> and <code>jeffrey://profile/&#123;profileId&#125;/flamegraph/&#123;eventType&#125;</code>. The distinction is worth the two extra methods: a tool result scrolls away, where a resource a client has attached stays in view and can be referred back to. Reading one runs the tool that would have answered the same question, so the two never disagree.</p>

      <DocsCodeBlock :code="resourcesCall" language="bash" />

      <h2 id="the-wire-protocol">The Wire Protocol</h2>
      <p>Whatever the client, the endpoint is plain <strong>JSON-RPC 2.0 over HTTP POST</strong>. No SSE stream, no session header, no handshake beyond what the protocol requires. A <code>GET</code> on the endpoint answers <code>405</code>, which is what the MCP specification prescribes for a server that does not offer the optional server-to-client stream &mdash; a client that treats that as fatal rather than as the documented refusal is at fault.</p>

      <table>
        <thead>
          <tr>
            <th>Method</th>
            <th>Purpose</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>initialize</code></td>
            <td>Negotiates the protocol version and returns <code>serverInfo</code></td>
          </tr>
          <tr>
            <td><code>tools/list</code></td>
            <td>Every tool with its description, its JSON-Schema input (including <code>required</code> and <code>enum</code>) and its <code>annotations</code></td>
          </tr>
          <tr>
            <td><code>tools/call</code></td>
            <td>Runs one tool; the result is text content</td>
          </tr>
          <tr>
            <td><code>prompts/list</code>, <code>prompts/get</code></td>
            <td>The plugin&rsquo;s skills as prompts &mdash; see <a href="#prompts-and-resources">above</a></td>
          </tr>
          <tr>
            <td><code>resources/list</code>, <code>resources/templates/list</code>, <code>resources/read</code></td>
            <td>The catalogue, the per-profile templates, and the content behind a <code>jeffrey://</code> URI</td>
          </tr>
          <tr>
            <td><code>ping</code></td>
            <td>Liveness</td>
          </tr>
          <tr>
            <td><code>notifications/*</code></td>
            <td>Accepted and acknowledged with no body, per JSON-RPC</td>
          </tr>
        </tbody>
      </table>

      <p>The server speaks <code>2024-11-05</code>, <code>2025-03-26</code> and <code>2025-06-18</code>, and <code>2025-06-18</code> is the default. <code>initialize</code> answers with the version the client asked for when it is one of those three, and with the default when it is not &mdash; echoing back an unrecognised version would promise a revision the server may not speak, so the client is told what it will actually get and decides from there.</p>

      <DocsCallout type="info" title="Every tool says whether it writes">
        Each spec in <code>tools/list</code> carries MCP <code>annotations</code>: <code>readOnlyHint</code>, <code>destructiveHint</code>, <code>idempotentHint</code> and <code>openWorldHint</code>. Almost everything Jeffrey exposes only reads a profile, and declares it. What does not is the <code>recordings_</code> family, which creates a profile, and <code>heap_prepare</code>, which builds a cache. <code>destructiveHint</code> is false throughout &mdash; nothing here deletes a profile, a recording or a dump &mdash; and <code>openWorldHint</code> marks the <code>hubs_</code> family, the only one that reaches a machine other than this installation. A client that gates approval on those hints does not need a hand-written deny-list.
      </DocsCallout>

      <h2 id="a-session-by-hand">A Session by Hand</h2>
      <p>Everything below works with <code>curl</code>, which makes it a good way to check that the server is up before blaming a client.</p>

      <p><strong>Initialize:</strong></p>
      <DocsCodeBlock :code="initialize" language="bash" />
      <DocsCodeBlock :code="initializeResult" language="json" />

      <p>Then <code>tools/list</code> with the same envelope returns all ninety-nine specs. To run one:</p>
      <DocsCodeBlock :code="toolsCall" language="bash" />

      <p>The result arrives as MCP text content &mdash; for the export tools, the same Markdown document the plugin would hand to Claude, preamble included.</p>

      <h2 id="errors">Errors</h2>
      <p>Everything answers HTTP <code>200</code>. There are two distinct failure shapes, and a client has to read both.</p>

      <p><strong>A tool that failed</strong> is still a <em>successful</em> JSON-RPC call: the result carries <code>isError: true</code> and the message as text content. An unknown tool name, a bad argument, and a profile with no heap dump all land here.</p>
      <DocsCodeBlock :code="toolError" language="json" />

      <p>This is what MCP specifies, and it is deliberate &mdash; the message is written for a model to act on. A profile with no heap dump, for instance, names the families to use instead.</p>

      <p><strong>A protocol-level failure</strong> is a real JSON-RPC error object:</p>
      <DocsCodeBlock :code="protocolError" language="json" />

      <ul>
        <li><code>-32601</code> &mdash; unknown method</li>
        <li><code>-32602</code> &mdash; invalid params, rejected before the tool ran</li>
        <li><code>-32603</code> &mdash; an internal failure outside the tool call</li>
      </ul>

      <p>An HTTP <code>404</code> on the endpoint itself is a third thing again: it means this installation switched the server off, not that the request was wrong. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
