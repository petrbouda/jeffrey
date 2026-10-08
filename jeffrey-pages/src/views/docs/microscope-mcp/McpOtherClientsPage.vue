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
  { id: 'agent-plugins-clients', text: 'Agent Plugins Clients', level: 2 },
  { id: 'cursor', text: 'Cursor', level: 3 },
  { id: 'vs-code-and-github-copilot', text: 'VS Code and GitHub Copilot', level: 3 },
  { id: 'kiro', text: 'Kiro', level: 3 },
  { id: 'any-mcp-client', text: 'Any MCP Client', level: 2 },
  { id: 'what-you-give-up', text: 'What You Give Up', level: 2 },
  { id: 'prompts-skills-and-resources', text: 'Prompts, Skills and Resources', level: 2 },
  { id: 'the-wire-protocol', text: 'The Wire Protocol', level: 2 },
  { id: 'tasks', text: 'Tasks', level: 3 },
  { id: 'input-requests', text: 'Input Requests', level: 3 },
  { id: 'what-an-older-client-sees', text: 'What an Older Client Sees', level: 3 },
  { id: 'instructions-and-completions', text: 'Instructions and Completions', level: 2 },
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
      "url": "http://localhost:8585/api/mcp"
    }
  }
}`;

const manualAdd = `# Claude Code on its v2 MCP runtime (MCP_SDK_GENERATION=v2 where that is not the default)
claude mcp add --transport http jeffrey http://localhost:8585/api/mcp

# Codex v0.147.0 or later, with the global feature flag [features] mcp_2026_07_28 = true
# (or codex --enable mcp_2026_07_28; applies to every HTTP server, under development in Codex)
codex mcp add jeffrey --url http://localhost:8585/api/mcp`;

const mcpJson = `{
  "mcpServers": {
    "jeffrey": {
      "type": "http",
      "url": "http://localhost:8585/api/mcp"
    }
  }
}`;

// Every request carries the revision it speaks and the client's capabilities; indented for "params".
const META = `"_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {}
      }`;

const discover = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: server/discover' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "server/discover",
    "params": {
      ${META}
    }
  }'`;

const discoverResult = `{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "supportedVersions": ["2026-07-28"],
    "capabilities": {
      "tools": { "listChanged": false },
      "prompts": { "listChanged": false },
      "resources": { "subscribe": false, "listChanged": false },
      "completions": {},
      "extensions": {
        "io.modelcontextprotocol/tasks": {},
        "io.modelcontextprotocol/skills": {}
      }
    },
    "instructions": "Jeffrey Microscope analyses JVM recordings ... Start here. Call profiles_list ...",
    "resultType": "complete",
    "ttlMs": 3600000,
    "cacheScope": "public",
    "_meta": {
      "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "<application-build-version>" }
    }
  }
}`;

const toolsCall = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: tools/call' \\
  -H 'Mcp-Name: flamegraph_export' \\
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
      },
      ${META}
    }
  }'`;

const promptsCall = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: prompts/get' \\
  -H 'Mcp-Name: analyze-jfr' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "prompts/get",
    "params": {
      "name": "analyze-jfr",
      ${META}
    }
  }'`;

const skillsCall = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: skills/list' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 8,
    "method": "skills/list",
    "params": {
      ${META}
    }
  }'`;

const skillsResult = `{
  "jsonrpc": "2.0",
  "id": 8,
  "result": {
    "skills": [
      {
        "uri": "skill://report/SKILL.md",
        "frontmatter": { "name": "report", "description": "The shape and the evidence rules ..." },
        "resources": [
          { "uri": "skill://report/SKILL.md", "digest": "sha256:...", "size": 13646 },
          { "uri": "skill://report/references/tool-prefixes.md", "digest": "sha256:...", "size": 715 }
        ]
      }
    ],
    "resultType": "complete",
    "ttlMs": 3600000,
    "cacheScope": "public",
    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "<application-build-version>" } }
  }
}`;

const resourcesCall = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: resources/read' \\
  -H 'Mcp-Name: jeffrey://profiles' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 5,
    "method": "resources/read",
    "params": {
      "uri": "jeffrey://profiles",
      ${META}
    }
  }'`;

// The same envelope from a client that declared the tasks extension; indented for "params".
const TASKS_META = `"_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {
          "extensions": { "io.modelcontextprotocol/tasks": {} }
        }
      }`;

const taskCall = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: tools/call' \\
  -H 'Mcp-Name: recordings_analyzeFile' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 6,
    "method": "tools/call",
    "params": {
      "name": "recordings_analyzeFile",
      "arguments": { "path": "/home/dev/project/target/app.jfr" },
      ${TASKS_META}
    }
  }'`;

const taskCreated = `{
  "jsonrpc": "2.0",
  "id": 6,
  "result": {
    "taskId": "5f0c1d7e-...",
    "status": "working",
    "statusMessage": "analyzing",
    "createdAt": "2026-09-26T09:14:03.120Z",
    "lastUpdatedAt": "2026-09-26T09:14:03.120Z",
    "ttlMs": 3600000,
    "pollIntervalMs": 5000,
    "resultType": "task",
    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "..." } }
  }
}`;

const taskGet = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: tasks/get' \\
  -H 'Mcp-Name: 5f0c1d7e-...' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 7,
    "method": "tasks/get",
    "params": {
      "taskId": "5f0c1d7e-...",
      ${TASKS_META}
    }
  }'`;

const taskCompleted = `{
  "jsonrpc": "2.0",
  "id": 7,
  "result": {
    "taskId": "5f0c1d7e-...",
    "status": "completed",
    "createdAt": "2026-09-26T09:14:03.120Z",
    "lastUpdatedAt": "2026-09-26T09:15:41.806Z",
    "ttlMs": 3600000,
    "pollIntervalMs": 5000,
    "result": {
      "content": [ { "type": "text", "text": "{\\"status\\":\\"READY\\",\\"recordingId\\":\\"0195f0a1-...\\",\\"profileId\\":\\"0195f0a2-...\\", ...}" } ],
      "structuredContent": {
        "status": "READY",
        "recordingId": "0195f0a1-...",
        "profileId": "0195f0a2-...",
        "name": "app.jfr",
        "reused": false,
        ...
        "uiLink": "http://localhost:8585/profiles/0195f0a2-..."
      },
      "isError": false,
      "resultType": "complete",
      "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "..." } }
    },
    "resultType": "complete",
    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "..." } }
  }
}`;

// The same envelope from a client that declared form elicitation; indented for "params".
const ELICIT_META = `"_meta": {
        "io.modelcontextprotocol/protocolVersion": "2026-07-28",
        "io.modelcontextprotocol/clientCapabilities": {
          "elicitation": { "form": {} }
        }
      }`;

const deleteCall = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: tools/call' \\
  -H 'Mcp-Name: recordings_delete' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 10,
    "method": "tools/call",
    "params": {
      "name": "recordings_delete",
      "arguments": { "recordingId": "0195f0a1-..." },
      ${ELICIT_META}
    }
  }'`;

const deleteAsked = `{
  "jsonrpc": "2.0",
  "id": 10,
  "result": {
    "inputRequests": {
      "confirmDeletion": {
        "method": "elicitation/create",
        "params": {
          "mode": "form",
          "message": "Delete recording app.jfr (0195f0a1-...) and the profile 0195f0a2-... built from it, with everything analysed out of it? This cannot be undone here. A copy of the recording on a Jeffrey Hub is untouched, and hubs_download can pull it again.",
          "requestedSchema": {
            "type": "object",
            "properties": {
              "confirm": {
                "type": "boolean",
                "default": false,
                "title": "Delete this recording",
                "description": "Check to delete the recording and everything analysed out of it"
              }
            },
            "required": ["confirm"]
          }
        }
      }
    },
    "resultType": "input_required",
    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "..." } }
  }
}`;

const deleteRetry = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -H 'MCP-Protocol-Version: 2026-07-28' \\
  -H 'Mcp-Method: tools/call' \\
  -H 'Mcp-Name: recordings_delete' \\
  -d '{
    "jsonrpc": "2.0",
    "id": 11,
    "method": "tools/call",
    "params": {
      "name": "recordings_delete",
      "arguments": { "recordingId": "0195f0a1-..." },
      "inputResponses": {
        "confirmDeletion": { "action": "accept", "content": { "confirm": true } }
      },
      ${ELICIT_META}
    }
  }'`;

const deleteDone = `{
  "jsonrpc": "2.0",
  "id": 11,
  "result": {
    "content": [
      { "type": "text", "text": "{\\"status\\":\\"DELETED\\",\\"recordingId\\":\\"0195f0a1-...\\",\\"name\\":\\"app.jfr\\",\\"profileId\\":\\"0195f0a2-...\\",\\"reason\\":null,\\"uiLink\\":\\"http://localhost:8585/recordings\\"}" }
    ],
    "structuredContent": {
      "status": "DELETED",
      "recordingId": "0195f0a1-...",
      "name": "app.jfr",
      "profileId": "0195f0a2-...",
      "reason": null,
      "uiLink": "http://localhost:8585/recordings"
    },
    "isError": false,
    "resultType": "complete",
    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "..." } }
  }
}`;

const toolError = `{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      { "type": "text", "text": "Error: Profile 0195f0a2-... has no heap dump. ..." }
    ],
    "isError": true,
    "resultType": "complete",
    "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "jeffrey", "version": "..." } }
  }
}`;

const olderClient = `curl -s -X POST http://localhost:8585/api/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{}}}'`;

const olderClientAnswer = `HTTP/1.1 400
{
  "jsonrpc": "2.0",
  "id": 1,
  "error": {
    "code": -32602,
    "message": "This server speaks MCP 2026-07-28 only; send per-request _meta (see server/discover)",
    "data": { "supported": ["2026-07-28"], "requested": null }
  }
}`;

const olderClientLog =
  'An MCP client sent a request this server cannot speak: code=-32602' +
  ' message=This server speaks MCP 2026-07-28 only; send per-request _meta (see server/discover)';

const protocolError = `HTTP/1.1 404
{
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
      <p>The plugin &mdash; in <router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link>, <router-link to="/docs/microscope-mcp/codex">Codex</router-link> or <router-link to="/docs/microscope-mcp/gemini">Gemini CLI</router-link> &mdash; is a convenience over an ordinary MCP server. Anything that speaks MCP <code>2026-07-28</code> over Streamable HTTP can connect instead.</p>

      <DocsCallout type="warning" title="MCP 2026-07-28 only">
        Jeffrey works with any client that speaks MCP <code>2026-07-28</code> over Streamable HTTP. A client that opens with <code>initialize</code> is refused with <code>-32602</code> naming <code>2026-07-28</code> &mdash; see <a href="#what-an-older-client-sees">What an Older Client Sees</a>. Everything below about Cursor, VS Code and Kiro applies once the client supports MCP <code>2026-07-28</code>; check your client&rsquo;s release notes for that, since this page cannot track it.
      </DocsCallout>

      <h2 id="agent-plugins-clients">Agent Plugins Clients</h2>
      <p>The plugin carries an <a href="https://agent-plugins.org/" target="_blank" rel="noopener">Agent Plugins</a> manifest, the vendor-neutral format <strong>Cursor</strong>, <strong>GitHub Copilot</strong>, <strong>VS Code</strong> and <strong>Kiro</strong> read alongside Codex. Where a client installs a plugin from a directory, <code>jeffrey-claude-plugin/</code> in a clone is that directory:</p>
      <DocsCodeBlock :code="clonePlugin" language="bash" />

      <p>What is standardised is the manifest, the ten skills and the <code>streamable-http</code> server entry. Everything past that &mdash; how a plugin is browsed and installed, how skills are invoked, how tools are approved &mdash; is the client's own, and moves faster than this page can. The <router-link to="/docs/microscope-mcp/codex">Codex</router-link> page is the closest map, since it documents the same portable half in detail.</p>

      <p>None of them can carry the three agents, for the reason that page gives: Agent Plugins defines skills and MCP servers, and nothing else. And in all of them the endpoint is fixed at <code>localhost:8585</code>, because the format forbids placeholder expansion in a server URL &mdash; a Jeffrey anywhere else is registered by hand, as below.</p>

      <DocsCallout type="info" title="Gemini CLI is not one of them">
        It cannot connect to this Jeffrey until it supports MCP <code>2026-07-28</code>. It reads its own extension format rather than this manifest, and takes more from the package than these clients can &mdash; the skills, the session-start check, and an endpoint you can point elsewhere. It has a <router-link to="/docs/microscope-mcp/gemini">page of its own</router-link>. Its <code>mcpServers</code> entry also spells the endpoint its own way: <code>httpUrl</code>, or <code>url</code> with <code>&quot;type&quot;: &quot;http&quot;</code> &mdash; which is what its own <code>gemini mcp add --transport http</code> writes.
      </DocsCallout>

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
        Build each of them around the address you actually reach Jeffrey on &mdash; behind a container, a proxy or a non-default port, <code>localhost:8585</code> is not it.
      </DocsCallout>

      <h2 id="what-you-give-up">What You Give Up</h2>
      <p>The same hundred and eleven tools, named <code>mcp__jeffrey__*</code> rather than the <code>mcp__plugin_microscope_jeffrey__*</code> Claude Code gives a plugin's server &mdash; a hand-registered server is not namespaced by a plugin. Adjust any approval rule accordingly: <code>/permissions</code> in Claude Code, the <code>[mcp_servers.jeffrey]</code> block in Codex.</p>

      <p>What does not come along as <em>plugin</em> skills is the guidance: the entry sequence and the two database schemas. But it is not lost. The server offers the same files over the protocol twice: as <strong>skills</strong>, through the MCP skills extension, so a client that speaks <code>2026-07-28</code> with that extension gets all ten from the server and loads them as it would a plugin&rsquo;s; and as <strong>prompts</strong>, so a client that only speaks <code>prompts/list</code> can still load any of them &mdash; see below. What is genuinely missing is the <router-link to="/docs/microscope-mcp/agent">agents</router-link>, which no MCP server can provide, and, over prompts, the automatic loading: somebody has to ask for the prompt.</p>

      <h2 id="prompts-skills-and-resources">Prompts, Skills and Resources</h2>
      <p>Three capabilities beyond the tools, and they exist for exactly this page&rsquo;s readers.</p>

      <p>Every call on this page is a complete MCP <code>2026-07-28</code> request: the version and the client&rsquo;s capabilities in <code>params._meta</code>, repeated in the <code>MCP-Protocol-Version</code> and <code>Mcp-Method</code> headers, plus <code>Mcp-Name</code> for the methods that name something. <a href="#the-wire-protocol">The Wire Protocol</a> explains each.</p>

      <p><strong>Prompts</strong> are the plugin&rsquo;s skills, served over the protocol. <code>prompts/list</code> names them &mdash; <code>analyze-jfr</code>, <code>analyze-heap</code>, <code>analyze-hub</code>, <code>compare-jfr</code>, <code>advise-jfr</code>, <code>profile-run</code>, <code>regression-check</code>, <code>jfr-sql</code>, <code>heap-sql</code>, <code>report</code> &mdash; and <code>prompts/get</code> returns one as a message to insert. They are the same files the plugin ships, copied onto the server&rsquo;s classpath when it is built, so they cannot drift from what a Claude Code or Codex user gets.</p>

      <DocsCodeBlock :code="promptsCall" language="bash" />

      <p><strong>Skills</strong> are the same ten files as Agent Skills, over the <code>io.modelcontextprotocol/skills</code> extension, which <code>server/discover</code> lists under <code>capabilities.extensions</code>. <code>skills/list</code> returns every skill (the answer below is cut to one of the ten) with its front matter exactly as written and its complete manifest: each file&rsquo;s <code>skill://</code> URI, a <code>sha256</code> digest over its bytes, and its size. <code>skills/get</code> returns one, named by the URI of its <code>SKILL.md</code>, and each file is read with the ordinary <code>resources/read</code>. A client loads them the way it loads a plugin&rsquo;s skills, from the descriptions, without anybody asking. Every skill is self-contained &mdash; nothing in one points into another &mdash; so a client that reads only what a manifest lists reads everything the skill uses. An unknown skill or file is <code>-32602</code>.</p>

      <DocsCodeBlock :code="skillsCall" language="bash" />
      <DocsCodeBlock :code="skillsResult" language="json" />

      <p><strong>Resources</strong> are the parts of a profile a client can attach rather than call for. <code>jeffrey://profiles</code> is the catalogue; <code>resources/templates/list</code> offers <code>jeffrey://profile/&#123;profileId&#125;/summary</code>, <code>jeffrey://profile/&#123;profileId&#125;/flamegraph/&#123;eventType&#125;</code>, <code>&hellip;/evidence</code>, <code>&hellip;/schema</code> and <code>&hellip;/findings</code>, each while the tool or family that reads the same data is advertised. The distinction is worth the two extra methods: a tool result scrolls away, where a resource a client has attached stays in view and can be referred back to. Reading the summary, evidence or flamegraph template runs the tool that would have answered the same question, so the two never disagree; <code>&hellip;/schema</code> and <code>&hellip;/findings</code> are documents no single tool returns, described below.</p>

      <p><code>jeffrey://diagnostics</code> reports profile readiness, bounded Hub connectivity checks and aggregate tool metrics without connection addresses or tool arguments. The <code>jeffrey://profile/&#123;profileId&#125;/evidence</code> template exposes the same bounded evidence snapshot as <code>profiles_evidence</code>. <code>&hellip;/schema</code> is the profile database as one JSON document &mdash; every table and view, the <code>events</code> view included, with its columns, the note on the JSON <code>fields</code> column and each event type with its count &mdash; served with the <code>jfr_</code> family. <code>&hellip;/findings</code> merges every finding the profile already holds &mdash; the cached Auto Analysis, the container verdict, the capability gaps &mdash; with a <code>status</code> of <code>COMPUTED</code>, <code>NOT_COMPUTED</code> (and a <code>followUp</code> offering <code>jvm_autoAnalysis</code> with <code>compute</code>) or <code>CANNOT_COMPUTE</code>; it is read from the cache, so reading it never starts the analysis. Each read reflects the current state; attach or save a response when you need a fixed snapshot.</p>

      <DocsCodeBlock :code="resourcesCall" language="bash" />
      <p><code>jeffrey://profiles</code> returns the first catalogue page and provides a continuation URI when more profiles match. The <code>jeffrey://profiles{?cursor,limit}</code> template continues it. <code>jeffrey://server</code> reports the build version, effective tool families and count, and supported protocol capabilities. It contains no raw configuration, local paths or Hub addresses.</p>
      <p>Every tool but <code>ide_source</code> declares an <code>outputSchema</code> &mdash; 110 of the 111 &mdash; and <code>tools/call</code> always returns <code>structuredContent</code> valid against it, beside the text: the same record as JSON, or Markdown ending in a footer (<code>Open in Microscope: &hellip;</code> and a <code>Next:</code> list, each call ending in its weight) for the documents written to be read. There is no text-only variant for older clients, because there are no older clients. Each tool&rsquo;s <code>_meta</code> also carries <code>jeffrey/cost</code> and, where it applies, <code>jeffrey/requires</code>; the <router-link to="/docs/microscope-mcp/tools#answers">Tool Reference</router-link> explains both and the conventions every answer follows &mdash; epoch-millisecond time, upper-case enums, <code>status</code> instead of an error for a question with no data, cursor paging, <code>followUp</code> and <code>uiLink</code>. Every next call an answer offers carries a <router-link to="/docs/microscope-mcp/tools#weight"><code>weight</code></router-link> &mdash; <code>LIGHT</code>, <code>MEDIUM</code> or <code>HEAVY</code>, what its answer puts into the conversation &mdash; derived from those hints rather than declared as a key of its own.</p>
      <p id="trace-context">A request may carry the caller&rsquo;s W3C trace context as <code>traceparent</code> and <code>tracestate</code> in <code>params._meta</code>; Jeffrey records both verbatim as attributes of the span it keeps for that tool call, beside its own ids, and drops a malformed one without an error.</p>

      <h2 id="the-wire-protocol">The Wire Protocol</h2>
      <p>Whatever the client, the endpoint is plain <strong>JSON-RPC 2.0 over HTTP POST</strong>, and it speaks exactly one MCP revision: <strong><code>2026-07-28</code></strong>, the stateless one. There is no handshake and no session. Every request says for itself which revision it speaks and what the client can do, in <code>params._meta</code>:</p>
      <ul>
        <li><code>io.modelcontextprotocol/protocolVersion</code> &mdash; <code>&quot;2026-07-28&quot;</code>, required</li>
        <li><code>io.modelcontextprotocol/clientCapabilities</code> &mdash; an object, required; <code>{}</code> when the client declares nothing</li>
        <li><code>io.modelcontextprotocol/clientInfo</code> &mdash; the client&rsquo;s name and version, optional</li>
      </ul>

      <p>And three headers repeat what the body says, so a proxy or a server can route and refuse a request without parsing it:</p>
      <ul>
        <li><code>MCP-Protocol-Version: 2026-07-28</code> &mdash; on every request, equal to the version in <code>_meta</code></li>
        <li><code>Mcp-Method</code> &mdash; on every request, equal to <code>method</code></li>
        <li><code>Mcp-Name</code> &mdash; on <code>tools/call</code> and <code>prompts/get</code> equal to <code>params.name</code>, on <code>resources/read</code> equal to <code>params.uri</code>, on <code>tasks/get</code>, <code>tasks/update</code> and <code>tasks/cancel</code> equal to <code>params.taskId</code>. A value that is not plain ASCII is sent as <code>=?base64?&hellip;?=</code></li>
      </ul>

      <p>Every result says what kind it is in <code>resultType</code> (<code>complete</code>; <code>task</code> when a <code>tools/call</code> hands back a <a href="#tasks">task</a>; <code>input_required</code> when it <a href="#input-requests">asks the user</a> something first) and which server answered in <code>_meta[&quot;io.modelcontextprotocol/serverInfo&quot;]</code>. The results a client may cache &mdash; <code>server/discover</code>, the list methods, <code>skills/get</code> and <code>resources/read</code> &mdash; carry <code>ttlMs</code> and <code>cacheScope</code>: an hour and <code>public</code> for the discover, list and skill answers and for reading a skill&rsquo;s file, which change only with a new Jeffrey build, and <code>0</code> and <code>private</code> for reading a <code>jeffrey://</code> resource, which reflects the catalogue as it is now.</p>

      <p>A <code>GET</code> or <code>DELETE</code> on the endpoint answers <code>405</code>: there is no server-to-client stream to open and no session to end.</p>

      <table>
        <thead>
          <tr>
            <th>Method</th>
            <th>Purpose</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>server/discover</code></td>
            <td>What the server speaks and offers: <code>supportedVersions</code>, <code>capabilities</code> (including <code>extensions</code>), the <code>instructions</code>, and <code>serverInfo</code>. Never assembles the toolset, so it answers even when a tool family cannot be built &mdash; which makes it the probe to use for &ldquo;is Jeffrey up&rdquo;</td>
          </tr>
          <tr>
            <td><code>tools/list</code></td>
            <td>Every tool with its description, its JSON-Schema input (including <code>required</code> and <code>enum</code>), its <code>outputSchema</code> where it has one, and its <code>annotations</code></td>
          </tr>
          <tr>
            <td><code>tools/call</code></td>
            <td>Runs one tool; the result is text content, plus <code>structuredContent</code> for the tools that declare an <code>outputSchema</code></td>
          </tr>
          <tr>
            <td><code>prompts/list</code>, <code>prompts/get</code></td>
            <td>The plugin&rsquo;s skills as prompts &mdash; see <a href="#prompts-skills-and-resources">above</a></td>
          </tr>
          <tr>
            <td><code>skills/list</code>, <code>skills/get</code></td>
            <td>The same skills over the skills extension, each with its front matter and manifest; their files are read with <code>resources/read</code> &mdash; see <a href="#prompts-skills-and-resources">above</a></td>
          </tr>
          <tr>
            <td><code>resources/list</code>, <code>resources/templates/list</code>, <code>resources/read</code></td>
            <td>The catalogue, the per-profile templates, and the content behind a <code>jeffrey://</code> URI</td>
          </tr>
          <tr>
            <td><code>tasks/get</code>, <code>tasks/update</code>, <code>tasks/cancel</code></td>
            <td>Follow and stop a task a <code>tools/call</code> handed back &mdash; only for a client that declared the tasks extension; see <a href="#tasks">Tasks</a></td>
          </tr>
          <tr>
            <td><code>completion/complete</code></td>
            <td>Completes <code>profileId</code> and <code>baselineProfileId</code> for a prompt argument or a per-profile template, from the live catalogue &mdash; see <a href="#completions">below</a></td>
          </tr>
          <tr>
            <td><code>notifications/*</code></td>
            <td>Accepted and acknowledged with <code>202</code> and no body, per JSON-RPC. They still carry <code>_meta</code> and the headers</td>
          </tr>
        </tbody>
      </table>

      <p>Nothing else is served. <code>initialize</code>, <code>ping</code> and <code>logging/setLevel</code> do not exist in <code>2026-07-28</code>, and JSON-RPC batching does not either: a JSON array is refused whole with <code>-32600</code>.</p>

      <h3 id="tasks">Tasks</h3>
      <p>Jeffrey serves the MCP tasks extension, <code>io.modelcontextprotocol/tasks</code>, and <code>server/discover</code> lists it under <code>capabilities.extensions</code> whenever a family that starts long work is advertised. A client that declares it too &mdash; in the <code>clientCapabilities</code> of each request &mdash; is not held for forty-five seconds by a long call. The seven tools that can take a while (<code>recordings_analyzeFile</code>, <code>recordings_analyzeRecording</code>, <code>hubs_download</code>, <code>hubs_fetchFile</code>, <code>heap_prepare</code>, <code>heap_oql</code> with <code>includeRetainedSize</code> and <code>jvm_autoAnalysis</code> with <code>compute</code>) wait about <strong>five seconds</strong> for it: work that finishes answers directly, and work that does not comes back as a task. <code>heap_prepare</code> does not wait at all: such a client gets its task at once. A client that did not declare the extension gets what it always got &mdash; the forty-five-second wait and an <code>operationId</code> for <code>operations_status</code>. Only <code>tools/call</code> ever answers with a task; Jeffrey decides when, so <code>tools/list</code> does not change.</p>
      <DocsCodeBlock :code="taskCall" language="bash" />
      <DocsCodeBlock :code="taskCreated" language="json" />

      <p><code>tasks/get</code> reports where the task stands &mdash; <code>working</code>, <code>completed</code>, <code>failed</code> or <code>cancelled</code> &mdash; and, once it has finished, carries the full <code>tools/call</code> result in <code>result</code>. <code>tasks/cancel</code> asks the work to stop and answers straight away; <code>tasks/get</code> says <code>cancelled</code> once it has. <code>tasks/update</code> only confirms the task exists: no tool asks for input in the middle of its work. Each carries the <code>taskId</code> in <code>params</code> and in <code>Mcp-Name</code>.</p>
      <DocsCodeBlock :code="taskGet" language="bash" />
      <DocsCodeBlock :code="taskCompleted" language="json" />

      <ul>
        <li><strong>The <code>taskId</code> is the <code>operationId</code>.</strong> Both name the same attempt in one in-memory store, so <code>operations_status</code> reads a task as well, and a task is reachable only while the family that started it is advertised. An unknown, expired or withheld id is <code>-32602</code>, and a restart forgets every task.</li>
        <li><strong>Two calls that join one operation share one <code>taskId</code></strong> &mdash; a second import of the same file while the first is copying it, a second analysis of the same recording, a second download of the same session. A <code>tasks/cancel</code> from either caller cancels the work both are following.</li>
        <li><strong><code>ttlMs</code> is retention, not a cache hint and not a deadline.</strong> It says the task stays readable for an hour once it has finished. A task still <code>working</code> outlives its advertised <code>ttlMs</code> for as long as the work runs. <code>pollIntervalMs</code> asks for a poll every five seconds.</li>
        <li><strong>The result is what a waiting caller would have read</strong> &mdash; with one exception. An analysis that failed is a task <code>completed</code> with <code>isError: true</code> in its <code>result</code>, where a client that waited on <code>recordings_analyzeRecording</code> is answered with a status document that reports the failure without being an error. A tool that could not produce an answer at all makes the task <code>failed</code>.</li>
      </ul>

      <h3 id="input-requests">Input Requests</h3>
      <p>Two tools can ask the user something before they act, as the <code>2026-07-28</code> input-request flow (an <code>input_required</code> result carrying a form <code>elicitation/create</code>) allows. They ask <strong>only a client that declared form elicitation</strong> &mdash; <code>&quot;elicitation&quot;: {&quot;form&quot;: {}}</code> in its <code>clientCapabilities</code>, or an empty <code>&quot;elicitation&quot;: {}</code>, the older shape that meant form; <code>url</code> alone is not form. Every other client behaves exactly as before, and answers it sends anyway are ignored.</p>
      <ul>
        <li><code>recordings_delete</code> asks the user to <router-link to="/docs/microscope-mcp/tools#delete-confirmation">confirm the deletion</router-link>. A host that asks before a tool with <code>destructiveHint</code> still does; this question comes from Jeffrey and names what goes.</li>
        <li><code>hubs_download</code> asks <router-link to="/docs/microscope-mcp/tools#window-question">which part of a large session</router-link> to bring &mdash; one longer than an hour or bigger than 1&nbsp;GB by default &mdash; when the call names the whole session. The question comes before anything crosses the network, and before any <a href="#tasks">task</a>: the transfer that follows the answer takes the usual five-second path to a task.</li>
      </ul>
      <p>The question is a result, not a request from the server: the call answers with <code>resultType: &quot;input_required&quot;</code> and an <code>inputRequests</code> map, one entry per question under a key the tool chooses, each an <code>elicitation/create</code> in <code>form</code> mode with a <code>message</code> and a flat <code>requestedSchema</code>. Like every result it carries <code>serverInfo</code>, and like no cacheable one it carries no <code>ttlMs</code> or <code>cacheScope</code> &mdash; the answer belongs to the user.</p>
      <DocsCodeBlock :code="deleteCall" language="bash" />
      <DocsCodeBlock :code="deleteAsked" language="json" />

      <p>The client shows the form, then sends the <strong>same call again</strong> &mdash; a new <code>id</code>, the same <code>name</code> and <code>arguments</code> &mdash; with <code>inputResponses</code> under the same keys: <code>action</code> is <code>accept</code>, <code>decline</code> or <code>cancel</code>, and <code>content</code> holds the form&rsquo;s fields on an accept. Jeffrey sends no <code>requestState</code>: the arguments already name everything, and the retry is judged afresh, so a recording that became undeletable in the meantime is still refused.</p>
      <DocsCodeBlock :code="deleteRetry" language="bash" />
      <DocsCodeBlock :code="deleteDone" language="json" />

      <ul>
        <li><strong>No is an answer, not an error.</strong> A decline or a dismissal &mdash; or the box left unchecked &mdash; completes the call without <code>isError</code>: <code>{&quot;status&quot;: &quot;NOT_CONFIRMED&quot;, &hellip;}</code> from <code>recordings_delete</code>, <code>{&quot;status&quot;: &quot;NOT_DOWNLOADED&quot;, &hellip;}</code> from <code>hubs_download</code>, with nothing deleted or transferred.</li>
        <li><strong>A malformed answer asks again.</strong> Content missing a field, a value of the wrong type or out of range, an end before a start &mdash; each is answered with a new <code>input_required</code> result whose message states the problem first. It never ends in an error.</li>
        <li><strong>A task never waits on input.</strong> Questions are asked only before work starts; <code>tasks/update</code> ignores <code>inputResponses</code>.</li>
      </ul>

      <h3 id="what-an-older-client-sees">What an Older Client Sees</h3>
      <p>A client built for the handshake revisions (<code>2024-11-05</code> to <code>2025-11-25</code>) opens with <code>initialize</code> and sends no <code>_meta</code>. Jeffrey no longer speaks those revisions, and says so rather than failing vaguely. A request without <code>_meta</code>, or without the version in it, is malformed under <code>2026-07-28</code>, so the answer is <code>400</code> with <code>-32602</code> &mdash; but its message and <code>data.supported</code> name <code>2026-07-28</code>, since a handshake-era client has nowhere else to learn what to speak. A client that does send <code>_meta</code>, with the header and <code>_meta</code> agreeing on a version Jeffrey does not speak, gets <code>-32022</code> instead, with that version echoed in <code>data.requested</code>.</p>
      <DocsCodeBlock :code="olderClient" language="bash" />
      <DocsCodeBlock :code="olderClientAnswer" language="json" />

      <p>How the client shows that is up to the client &mdash; usually as a server that failed to start. Jeffrey&rsquo;s log records each refusal at <code>INFO</code>, which is the quickest way to tell &ldquo;wrong revision&rdquo; from &ldquo;not running&rdquo;:</p>
      <DocsCodeBlock :code="olderClientLog" language="text" />

      <p>The fix is on the client side: a version that supports MCP <code>2026-07-28</code>, or the setting that turns it on &mdash; the <router-link to="/docs/microscope-mcp/claude-code#before-you-start">Claude Code</router-link> and <router-link to="/docs/microscope-mcp/codex#before-you-start">Codex</router-link> pages say which.</p>

      <DocsCallout type="info" title="Every tool says whether it writes">
        Each spec in <code>tools/list</code> carries MCP <code>annotations</code>: <code>readOnlyHint</code>, <code>destructiveHint</code>, <code>idempotentHint</code> and <code>openWorldHint</code>. Almost everything Jeffrey exposes only reads a profile, and declares it; the <router-link to="/docs/microscope-mcp/clients#what-writes">eleven that write</router-link> declare that too, each for itself rather than for its family, so <code>recordings_list</code>, <code>recordings_status</code> and <code>heap_status</code> read as read-only although they sit beside writers. <code>destructiveHint</code> is true on <code>recordings_delete</code> alone &mdash; nothing else deletes a profile, a recording or a dump &mdash; and <code>openWorldHint</code> marks the <code>hubs_</code> and <code>ide_</code> families, and the <code>operations_</code> pair, which can poll or cancel a remote Hub transfer. A client that gates approval on those hints does not need a hand-written deny-list.
      </DocsCallout>

      <h2 id="instructions-and-completions">Instructions and Completions</h2>
      <p>Two things the server hands a client that has no plugin behind it.</p>

      <p><strong><code>server/discover</code> returns an <code>instructions</code> field.</strong> A hundred-odd tools in nineteen families is a lot to meet with nothing but a tool list, so discovery carries the short version: start at <code>profiles_list</code>, then <code>profiles_summary</code> and read <code>topFindings</code> and <code>capabilityGaps</code> before choosing a family, and for an open question put its <router-link to="/docs/microscope-mcp/tools#investigation-areas"><code>investigationAreas</code></router-link> to the user as the menu &mdash; each area the profile can answer, its weight and what suggests it &mdash; so the user picks what is worth running; that each next call in <code>followUp.nextTools</code> carries its weight; every tool outside <code>profiles_list</code> and the <code>recordings_</code>, <code>hubs_</code> and <code>operations_</code> families needs a <code>profileId</code>; what each family is for; the call order that matters inside each advertised family &mdash; <code>flamegraph_list</code> before <code>flamegraph_export</code>, <code>compare_list</code> before the other <code>compare_</code> tools, <code>jvm_sections</code> before the other <code>jvm_</code> tools, <code>ide_resolve</code> before a finding names a file, and the like; that the eleven writers are named and the long ones return an <code>operationId</code> to poll, or a task after about five seconds to a client that declared the tasks extension; and that output is capped and always says when it cut. Most clients put it in front of the model automatically. The longer guidance stays where it was &mdash; one prompt per workflow.</p>

      <p id="completions"><strong><code>completion/complete</code> completes <code>profileId</code> and <code>baselineProfileId</code>.</strong> A profile id is a UUIDv7, and there is no way to produce one except by reading it out of the catalogue first, which is exactly what this method exists for; <code>baselineProfileId</code> is the same kind of value, filled from the same catalogue, for the one prompt and handful of tools that compare two profiles. It answers for both reference types &mdash; a <code>ref/prompt</code>, for whichever of the two arguments the prompt declares, and a <code>ref/resource</code> naming one of the per-profile templates &mdash; matching on what has been typed so far, case-insensitively, and capping the response at the hundred values the protocol allows while reporting the true <code>total</code>. No other argument is completed: an event type is <code>jdk.ExecutionSample</code>, a name a model already knows. The capability is declared only when the <code>profiles</code> family is advertised, so a narrowed server does not offer a picker it cannot fill.</p>

      <p><strong>Tool results can carry resource links.</strong> Where a tool has an exact resource counterpart &mdash; <code>profiles_summary</code>, <code>profiles_evidence</code>, and an unnarrowed <code>flamegraph_export</code> &mdash; the result carries a <code>resource_link</code> block after its text, so a client can attach the answer instead of letting it scroll away. <code>profiles_summary</code> and <code>profiles_evidence</code> also link the profile&rsquo;s <code>&hellip;/findings</code>, and <code>jfr_listTables</code> and <code>jfr_describeTable</code> its <code>&hellip;/schema</code>, the whole document their answer is part of. The text block is always there; a link is an extra, never a replacement. A filtered flamegraph gets no link to the template, because the template takes an event type and nothing else and would return a different call tree under the same name.</p>

      <DocsCallout type="info" title="POST-only, and stateless on purpose">
        The endpoint answers <code>POST</code> and nothing else. <code>GET</code> and <code>DELETE</code> return <code>405</code>: there is no server-to-client SSE stream, no <code>Mcp-Session-Id</code>, and therefore no server-initiated notifications &mdash; no <code>notifications/progress</code>, and no <code>listChanged</code> or <code>resources/updated</code>, each of which <code>server/discover</code> declares as absent rather than leaving a client to discover. Long-running work is polled instead: a long call hands back an <code>operationId</code> that <code>operations_status</code> reports on or, to a client that declared the tasks extension, a <a href="#tasks">task</a> that <code>tasks/get</code> reports on. That survives a dropped connection, which a progress stream does not, and it keeps the server a plain request-response service that any HTTP client can drive.
      </DocsCallout>

      <h2 id="a-session-by-hand">A Session by Hand</h2>
      <p>Everything below works with <code>curl</code>, which makes it a good way to check that the server is up before blaming a client.</p>

      <p><strong>Discover</strong> &mdash; the same request the plugin&rsquo;s startup check sends:</p>
      <DocsCodeBlock :code="discover" language="bash" />
      <DocsCodeBlock :code="discoverResult" language="json" />

      <p>Then <code>tools/list</code> with the same envelope &mdash; <code>Mcp-Method: tools/list</code> and <code>&quot;method&quot;: &quot;tools/list&quot;</code> &mdash; returns all hundred and eleven specs. To run one, name the tool in <code>Mcp-Name</code> as well:</p>
      <DocsCodeBlock :code="toolsCall" language="bash" />

      <p>The result arrives as MCP text content &mdash; for the export tools, the same Markdown document the plugin would hand to Claude, preamble included.</p>

      <h2 id="errors">Errors</h2>
      <p>There are two distinct failure shapes, and a client has to read both.</p>

      <p><strong>A tool that ran and failed</strong> is still a <em>successful</em> JSON-RPC call, answered with HTTP <code>200</code>: the result carries <code>isError: true</code> and the message as text content. A profile with no heap dump, a query that matched nothing, a hub that stopped answering &mdash; anything the model is meant to read and try differently &mdash; lands here.</p>
      <DocsCodeBlock :code="toolError" language="json" />

      <p>This is what MCP specifies, and it is deliberate &mdash; the message is written for a model to act on. A profile with no heap dump, for instance, names the families to use instead.</p>

      <p>A mistake in the arguments is this too. A missing required argument, an argument the tool does not take, or a value of the wrong type comes back as a tool result with <code>isError: true</code> and a message naming the fix, so the model can correct the call and try again. The schema&rsquo;s bounds are advice rather than a gate: an out-of-range <code>limit</code> or <code>top</code> is clamped silently &mdash; a non-positive one takes the default, one above the maximum takes the maximum &mdash; and only a value no answer can be built from, such as a <code>thresholdPct</code> outside 0&ndash;100 or a <code>bucketMs</code> below its floor, is refused with <code>isError: true</code>. Only a call that could never reach a tool is a protocol error: an unknown tool name, or <code>arguments</code> that are not a JSON object &mdash; so a client can still tell &ldquo;that tool does not exist&rdquo; from &ldquo;the analysis found nothing&rdquo;.</p>

      <p><strong>A protocol-level failure</strong> is a real JSON-RPC error object, and the HTTP status says which kind:</p>
      <DocsCodeBlock :code="protocolError" language="json" />

      <table>
        <thead>
          <tr>
            <th>Code</th>
            <th>HTTP</th>
            <th>Meaning</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>-32602</code></td>
            <td><code>400</code></td>
            <td>Invalid <code>_meta</code>: no <code>_meta</code> or no <code>protocolVersion</code> in it &mdash; every <code>initialize</code> from a handshake-era client &mdash; with a message and <code>data.supported</code> naming <code>2026-07-28</code> (see <a href="#what-an-older-client-sees">above</a>); also <code>params</code> that are not an object, or <code>clientCapabilities</code> missing</td>
          </tr>
          <tr>
            <td><code>-32022</code></td>
            <td><code>400</code></td>
            <td>Unsupported protocol version: <code>MCP-Protocol-Version</code> and <code>_meta</code> agree on a version other than <code>2026-07-28</code>. <code>data.supported</code> is <code>[&quot;2026-07-28&quot;]</code>; <code>data.requested</code> echoes what was asked for</td>
          </tr>
          <tr>
            <td><code>-32020</code></td>
            <td><code>400</code></td>
            <td>Header mismatch: <code>MCP-Protocol-Version</code>, <code>Mcp-Method</code> or <code>Mcp-Name</code> is missing, disagrees with the body, or is a malformed <code>=?base64?&hellip;?=</code> value. The message names the header and both values. The version header is compared with <code>_meta</code> before the version itself is judged, so a header that does not repeat <code>_meta</code> is <code>-32020</code> even when one of the two names an unsupported version</td>
          </tr>
          <tr>
            <td><code>-32021</code></td>
            <td><code>400</code></td>
            <td>Missing client capability: the method belongs to an extension the client did not declare in <code>clientCapabilities</code>; <code>data.requiredCapabilities</code> says which. That is <code>tasks/get</code>, <code>tasks/update</code> and <code>tasks/cancel</code> from a client that did not declare <code>io.modelcontextprotocol/tasks</code></td>
          </tr>
          <tr>
            <td><code>-32601</code></td>
            <td><code>404</code></td>
            <td>Unknown method &mdash; including <code>initialize</code>, <code>ping</code> and <code>logging/setLevel</code> sent with a valid <code>_meta</code>, <code>tasks/list</code> and <code>tasks/result</code>, which the tasks extension no longer has, and a <code>notifications/*</code> method sent with an <code>id</code></td>
          </tr>
          <tr>
            <td><code>-32600</code></td>
            <td><code>400</code></td>
            <td>Not a JSON-RPC request: not an object, a JSON array (batching does not exist), no <code>method</code>, or an <code>id</code> that is neither a string nor an integer</td>
          </tr>
          <tr>
            <td><code>-32700</code></td>
            <td><code>400</code></td>
            <td>The body is not JSON</td>
          </tr>
          <tr>
            <td><code>-32602</code></td>
            <td><code>200</code></td>
            <td>Invalid params, rejected before any tool was chosen: an unknown tool name, <code>arguments</code> that are not an object, a pagination <code>cursor</code> on a list method (this server never issues one), a malformed completion request, and a <code>resources/read</code> whose subject is not there &mdash; a <code>jeffrey://</code> URI this server does not serve, or a profile that does not exist &mdash; and a <code>taskId</code> that is unknown, expired, or belongs to a family this installation does not advertise. A missing, unknown or mistyped argument is a tool result with <code>isError: true</code> instead, as is a refused <code>thresholdPct</code> or <code>bucketMs</code>; an out-of-range <code>limit</code> or <code>top</code> is clamped, not refused</td>
          </tr>
          <tr>
            <td><code>-32603</code></td>
            <td><code>200</code></td>
            <td>An internal failure outside the tool call</td>
          </tr>
        </tbody>
      </table>

      <p>An HTTP <code>404</code> <em>without</em> a JSON-RPC body is a different thing again: it means this installation switched the server off, not that the method was wrong. See <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
