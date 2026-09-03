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
  { id: 'claude-code-without-the-plugin', text: 'Claude Code, Without the Plugin', level: 2 },
  { id: 'what-you-give-up', text: 'What You Give Up', level: 2 },
  { id: 'the-wire-protocol', text: 'The Wire Protocol', level: 2 },
  { id: 'a-session-by-hand', text: 'A Session by Hand', level: 2 },
  { id: 'errors', text: 'Errors', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const manualAdd = `claude mcp add --transport http jeffrey http://localhost:8080/api/internal/mcp`;

const mcpJson = `{
  "mcpServers": {
    "jeffrey": {
      "type": "http",
      "url": "http://localhost:8080/api/internal/mcp"
    }
  }
}`;

const initialize = `curl -s -X POST http://localhost:8080/api/internal/mcp \\
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
    "capabilities": { "tools": { "listChanged": false } },
    "serverInfo": { "name": "jeffrey", "version": "1.0.0" }
  }
}`;

const toolsCall = `curl -s -X POST http://localhost:8080/api/internal/mcp \\
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
      <p>The <router-link to="/docs/microscope-mcp/plugin">plugin</router-link> is a convenience over an ordinary MCP server. Anything that speaks MCP over Streamable HTTP can connect instead.</p>

      <h2 id="claude-code-without-the-plugin">Claude Code, Without the Plugin</h2>
      <p>Register the server directly &mdash; useful when you want it in one project only, or when you would rather not add a marketplace:</p>
      <DocsCodeBlock :code="manualAdd" language="bash" />

      <p>Or write it into a project&rsquo;s <code>.mcp.json</code>:</p>
      <DocsCodeBlock :code="mcpJson" language="json" />

      <DocsCallout type="tip" title="Both are offered ready-made">
        <strong>Settings &rarr; Claude Code (MCP)</strong> shows the command and the <code>.mcp.json</code> entry with the URL your browser actually reached Jeffrey on &mdash; correct behind a container, a proxy or a non-default port, where <code>localhost:8080</code> is not.
      </DocsCallout>

      <h2 id="what-you-give-up">What You Give Up</h2>
      <p>The same thirty-nine tools, named <code>mcp__jeffrey__*</code> rather than <code>mcp__plugin_microscope_jeffrey__*</code> &mdash; a hand-registered server is not namespaced by a plugin. Adjust any <code>/permissions</code> rule accordingly.</p>

      <p>What does not come along is the <router-link to="/docs/microscope-mcp/skills">skills</router-link>: the entry sequence and the two database schemas. The tools still work; the model just starts colder, and is more likely to guess a column name than to call <code>jfr_describeTable</code> first.</p>

      <h2 id="the-wire-protocol">The Wire Protocol</h2>
      <p>For a client that is not Claude Code, the endpoint is plain <strong>JSON-RPC 2.0 over HTTP POST</strong>. No SSE stream, no session header, no handshake beyond what the protocol requires.</p>

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
            <td>Every tool with its description and JSON-Schema input</td>
          </tr>
          <tr>
            <td><code>tools/call</code></td>
            <td>Runs one tool; the result is text content</td>
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

      <p>The default protocol version is <code>2025-06-18</code>; a version the client asks for is echoed back.</p>

      <h2 id="a-session-by-hand">A Session by Hand</h2>
      <p>Everything below works with <code>curl</code>, which makes it a good way to check that the server is up before blaming a client.</p>

      <p><strong>Initialize:</strong></p>
      <DocsCodeBlock :code="initialize" language="bash" />
      <DocsCodeBlock :code="initializeResult" language="json" />

      <p>Then <code>tools/list</code> with the same envelope returns all thirty-nine specs. To run one:</p>
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
