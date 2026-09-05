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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'why-it-exists', text: 'Why It Exists', level: 2 },
  { id: 'not-the-in-app-assistant', text: 'Not the In-App Assistant', level: 2 },
  { id: 'how-a-request-travels', text: 'How a Request Travels', level: 2 },
  { id: 'what-it-can-read', text: 'What It Can Read', level: 2 },
  { id: 'more-than-tools', text: 'More Than Tools', level: 2 },
  { id: 'where-to-go-next', text: 'Where to Go Next', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Microscope MCP"
      icon="bi bi-plugin"
    />

    <div class="docs-content">
      <p class="docs-lede">Jeffrey Microscope serves an <strong>MCP server</strong> that an outside coding agent &mdash; an interactive Claude Code or Codex session in your own repository &mdash; can connect to. It turns every profile you have analysed into something a model can read directly: the catalogue, the DuckDB tables behind each profile, and flamegraph, trace and heap-dump exports. It can also take a recording file you have <em>not</em> analysed yet and build the profile for you &mdash; or find one that never reached this machine at all, on a connected Jeffrey Hub, and pull it down first.</p>

      <DocsCallout type="info" title="Reading is read-only">
        Every analysis tool hands out data and nothing more &mdash; it cannot modify, rename or delete a profile, so data cleanup and frame renaming stay in the Jeffrey UI. The exceptions are the <code>recordings_</code> and <code>hubs_</code> families, which create profiles rather than changing them &mdash; from a local file, and from a recording still on a connected hub &mdash; and <code>heap_prepare</code>, which builds a heap dump&rsquo;s index and reports. Each can be switched off on its own.
      </DocsCallout>

      <h2 id="why-it-exists">Why It Exists</h2>
      <p>Reading a profile and reading the code that produced it are the same job, and until now they happened in two places. You would export a flamegraph out of the browser, paste it into a chat, and then describe from memory what the code around the hot frame looks like &mdash; or paste that too, and hope you picked the right file.</p>

      <p>With the MCP server the profile comes to the code instead. The agent is already sitting in your repository; it can now pull <code>jdk.ExecutionSample</code> for the profile you just recorded, see that 21% of the samples land in one method, open that method's actual source, and tell you whether the two agree. The interesting questions &mdash; <em>&ldquo;the profile says this loop is hot; is it doing what I think it is?&rdquo;</em> &mdash; only become askable when both halves are in front of the same reader.</p>

      <h2 id="not-the-in-app-assistant">Not the In-App Assistant</h2>
      <p>Jeffrey has two AI integrations and they point in opposite directions.</p>

      <table>
        <thead>
          <tr>
            <th></th>
            <th>AI Analysis</th>
            <th>Microscope MCP</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Where the conversation happens</td>
            <td>Inside the Jeffrey UI</td>
            <td>In your terminal, in your repository</td>
          </tr>
          <tr>
            <td>Who calls whom</td>
            <td>Jeffrey calls out to a model provider</td>
            <td>A client calls in to Jeffrey</td>
          </tr>
          <tr>
            <td>Scope</td>
            <td>One profile, the one you have open</td>
            <td>Every profile in the installation</td>
          </tr>
          <tr>
            <td>Sees your source code</td>
            <td>No</td>
            <td>Yes &mdash; the client is already in your checkout</td>
          </tr>
          <tr>
            <td>Configuration</td>
            <td>A provider and an API key or a local subscription</td>
            <td>Nothing to configure; the client brings its own model</td>
          </tr>
        </tbody>
      </table>

      <p>They are not alternatives; running both is normal. The <router-link to="/docs/ai/overview">AI Analysis</router-link> pages cover the in-app side. What used to be the third thing &mdash; the Profile Advisor, which read one source folder from inside Jeffrey and proposed a patch &mdash; is now the <router-link to="/docs/microscope-mcp/skills#advise-jfr"><code>advise-jfr</code> skill</router-link> on this side: the same job, done by the agent that can also build, test and re-profile.</p>

      <h2 id="how-a-request-travels">How a Request Travels</h2>
      <p>The server speaks <strong>MCP over Streamable HTTP</strong> &mdash; JSON-RPC 2.0 against a single endpoint, <code>POST /api/internal/mcp</code>, on the Jeffrey Microscope you already run. There is no separate process to start and no extra port to open.</p>

      <p>A call arrives naming a tool and a <code>profileId</code>. Jeffrey resolves that id to the profile's own DuckDB database, holds a lease on it for as long as the session stays active, runs the tool, and returns Markdown or a result table. The heavy machinery &mdash; the flamegraph builder, the trace analysis, the heap-dump index &mdash; is the same code the UI renders from, so what the model reads and what you see on screen cannot drift apart.</p>

      <h2 id="what-it-can-read">What It Can Read</h2>
      <p>Ninety-nine tools in seventeen families:</p>
      <table>
        <thead>
          <tr>
            <th>Family</th>
            <th>Tools</th>
            <th>What it reads</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>profiles_</code></td>
            <td>7</td>
            <td>The catalogue: which recordings are analysed, what each one can answer, a deep link into the UI</td>
          </tr>
          <tr>
            <td><code>flamegraph_</code></td>
            <td>2</td>
            <td>Which graphs a profile supports, and the call tree as Markdown</td>
          </tr>
          <tr>
            <td><code>compare_</code></td>
            <td>4</td>
            <td>Two profiles against each other: whether they are comparable, what moved, and the differential call tree</td>
          </tr>
          <tr>
            <td><code>traces_</code></td>
            <td>11</td>
            <td>Trace operations, exemplars, span trees and span-scoped flamegraphs</td>
          </tr>
          <tr>
            <td><code>jvm_</code></td>
            <td>17</td>
            <td>The machine underneath: garbage collection and the pages beneath it, safepoints, JIT compilation, threads, native memory, class loading, exceptions, the host, TLS and certificates, the container, and what the JVM was started with</td>
          </tr>
          <tr>
            <td><code>http_</code></td>
            <td>2</td>
            <td>The HTTP server dashboard: latency percentiles, endpoints, status codes, slowest requests</td>
          </tr>
          <tr>
            <td><code>jdbc_</code></td>
            <td>3</td>
            <td>Statement timings and statement groups, plus the connection pools in front of them</td>
          </tr>
          <tr>
            <td><code>grpc_</code></td>
            <td>3</td>
            <td>gRPC latency per service and method, and the message sizes moved</td>
          </tr>
          <tr>
            <td><code>methodtracing_</code></td>
            <td>3</td>
            <td>Instrumented method timings (JEP 520): the methods by cost, the slowest invocations, per-method statistics</td>
          </tr>
          <tr>
            <td><code>io_</code></td>
            <td>3</td>
            <td>Socket and file I/O: bytes, targets and slowest operations &mdash; the waiting a CPU flamegraph cannot see</td>
          </tr>
          <tr>
            <td><code>blocking_</code></td>
            <td>3</td>
            <td>Contended monitors, waits, parks, sleeps and virtual-thread pinning</td>
          </tr>
          <tr>
            <td><code>timeline_</code></td>
            <td>2</td>
            <td>When the samples landed: the busiest windows ranked, and sub-second zoom inside one</td>
          </tr>
          <tr>
            <td><code>memory_</code></td>
            <td>2</td>
            <td>Allocation by type rather than by call site, and JFR-side leak candidates that need no heap dump</td>
          </tr>
          <tr>
            <td><code>jfr_</code></td>
            <td>7</td>
            <td>The profile&rsquo;s DuckDB tables &mdash; schema, event types and read-only SQL</td>
          </tr>
          <tr>
            <td><code>heap_</code></td>
            <td>24</td>
            <td>Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, a two-dump diff, read-only SQL and OQL, and the pair that builds an index before it can be read</td>
          </tr>
          <tr>
            <td><code>recordings_</code></td>
            <td>4</td>
            <td>One of the two families that write: imports a recording file from the machine Jeffrey runs on and builds a profile from it</td>
          </tr>
          <tr>
            <td><code>hubs_</code></td>
            <td>3</td>
            <td>The recordings still on a connected Jeffrey Hub: lists the sessions across every hub and pulls one in to be analysed</td>
          </tr>
        </tbody>
      </table>

      <p>The <router-link to="/docs/microscope-mcp/tools">Tool Reference</router-link> lists every one of them with its arguments.</p>

      <p>Analysis answers carry a link back to the view that shows them &mdash; the flamegraph with its filters applied, the operation on its slowest tab, the GC dashboard, the endpoint detail. The link is for you, not for Claude: a URL is nothing a model can analyse, which is exactly why it travels attached to an answer rather than behind a tool the model would reasonably never call.</p>

      <DocsCallout type="warning" title="Know what it exposes">
        The endpoint is on by default and unauthenticated unless you give it a token &mdash; anyone who can reach the address can read every profile in that installation, ask it to open a recording file from that machine, and have it pull a recording down from a connected hub. Jeffrey binds every interface by default, so on a shared network &ldquo;anyone who can reach the address&rdquo; is wider than it sounds until you bind it to loopback. It does refuse a request carrying a foreign <code>Origin</code>, which closes the browser path but nothing else. <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> covers how to expose it safely, how to require a bearer token, how to turn ingestion, hub access and compute off on their own, and how to switch the whole thing off.
      </DocsCallout>

      <h2 id="more-than-tools">More Than Tools</h2>
      <p>Tools are what a model calls. The server offers two other things a client can use, and both exist for the same reason: a hundred tools are only usable if something says which to reach for first.</p>

      <p><strong>Prompts</strong> are the plugin&rsquo;s <router-link to="/docs/microscope-mcp/skills">skills</router-link>, served over the protocol. A Claude Code or Codex user gets them from the plugin; every other MCP client &mdash; Cursor, VS Code, Kiro, anything registered by hand &mdash; cannot install a plugin, and would otherwise have the tools with no account of how to use them. <code>prompts/list</code> names them and <code>prompts/get</code> returns one. They are the same files the plugin ships, copied onto the server&rsquo;s classpath when it is built, so the two cannot drift apart.</p>

      <p><strong>Resources</strong> are the parts of a profile a client can attach rather than call for: the catalogue at <code>jeffrey://profiles</code>, and templates for a profile&rsquo;s summary and a flamegraph export. A tool result scrolls away; a resource a client has attached stays in view. Reading one runs the tool that would have answered the same question, so the two never disagree.</p>

      <p><router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> has the calls.</p>

      <h2 id="where-to-go-next">Where to Go Next</h2>
      <ul>
        <li><router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> &mdash; the endpoint URL, the security posture, and how to switch it off</li>
        <li><router-link to="/docs/microscope-mcp/claude-code">Claude Code</router-link> &mdash; installing the plugin, pointing it at this Jeffrey, and the two subagents only that client can carry</li>
        <li><router-link to="/docs/microscope-mcp/codex">Codex</router-link> &mdash; the same plugin through the portable Agent Plugins format, and what changes with it</li>
        <li><router-link to="/docs/microscope-mcp/recipes">Recipes</router-link> &mdash; worked sessions, from &ldquo;where does the time go&rdquo; to a leak hunt</li>
        <li><router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> &mdash; Cursor, Copilot, VS Code and Kiro, connecting without a plugin, and the wire protocol</li>
      </ul>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

.docs-lede {
  font-size: 16px;
  color: #5e6e82;
  margin-bottom: 24px;
}
</style>
