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
      <p class="docs-lede">Jeffrey Microscope serves an <strong>MCP server</strong> that an outside client &mdash; an interactive Claude Code session in your own repository &mdash; can connect to. It turns every profile you have analysed into something a model can read directly: the catalogue, the DuckDB tables behind each profile, and flamegraph, trace and heap-dump exports. It can also take a recording file you have <em>not</em> analysed yet and build the profile for you.</p>

      <DocsCallout type="info" title="Reading is read-only">
        Every analysis tool hands out data and nothing more &mdash; it cannot modify, rename or delete a profile, so data cleanup and frame renaming stay in the Jeffrey UI. The single exception is the <code>recordings_</code> family, which creates profiles rather than changing them, and which an installation can switch off on its own.
      </DocsCallout>

      <h2 id="why-it-exists">Why It Exists</h2>
      <p>Reading a profile and reading the code that produced it are the same job, and until now they happened in two places. You would export a flamegraph out of the browser, paste it into a chat, and then describe from memory what the code around the hot frame looks like &mdash; or paste that too, and hope you picked the right file.</p>

      <p>With the MCP server the profile comes to the code instead. Claude Code is already sitting in your repository; it can now pull <code>jdk.ExecutionSample</code> for the profile you just recorded, see that 21% of the samples land in one method, open that method's actual source, and tell you whether the two agree. The interesting questions &mdash; <em>&ldquo;the profile says this loop is hot; is it doing what I think it is?&rdquo;</em> &mdash; only become askable when both halves are in front of the same reader.</p>

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
      <p>Forty-six tools in seven families:</p>
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
            <td>4</td>
            <td>The catalogue: which recordings are analysed, what each one can answer, a deep link into the UI</td>
          </tr>
          <tr>
            <td><code>flamegraph_</code></td>
            <td>2</td>
            <td>Which graphs a profile supports, and the call tree as Markdown</td>
          </tr>
          <tr>
            <td><code>compare_</code></td>
            <td>3</td>
            <td>Two profiles against each other: whether they are comparable, what moved, and the differential call tree</td>
          </tr>
          <tr>
            <td><code>traces_</code></td>
            <td>8</td>
            <td>Trace operations, exemplars, span trees and span-scoped flamegraphs</td>
          </tr>
          <tr>
            <td><code>jfr_</code></td>
            <td>6</td>
            <td>The profile&rsquo;s DuckDB tables &mdash; schema, event types and read-only SQL</td>
          </tr>
          <tr>
            <td><code>heap_</code></td>
            <td>20</td>
            <td>Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, read-only SQL</td>
          </tr>
          <tr>
            <td><code>recordings_</code></td>
            <td>3</td>
            <td>The one family that writes: imports a recording file from the machine Jeffrey runs on and builds a profile from it</td>
          </tr>
        </tbody>
      </table>

      <p>The <router-link to="/docs/microscope-mcp/tools">Tool Reference</router-link> lists every one of them with its arguments.</p>

      <DocsCallout type="warning" title="Know what it exposes">
        The endpoint is on by default and has no authentication yet &mdash; anyone who can reach the address can read every profile in that installation, and ask it to open a recording file from that machine. Jeffrey binds every interface by default, so on a shared network &ldquo;anyone who can reach the address&rdquo; is wider than it sounds until you bind it to loopback. <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> covers how to expose it safely, how to turn ingestion off on its own, and how to switch the whole thing off.
      </DocsCallout>

      <h2 id="where-to-go-next">Where to Go Next</h2>
      <ul>
        <li><router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> &mdash; the endpoint URL, the security posture, and how to switch it off</li>
        <li><router-link to="/docs/microscope-mcp/plugin">Claude Code Plugin</router-link> &mdash; one install instead of a hand-written command per machine</li>
        <li><router-link to="/docs/microscope-mcp/recipes">Recipes</router-link> &mdash; worked sessions, from &ldquo;where does the time go&rdquo; to a leak hunt</li>
        <li><router-link to="/docs/microscope-mcp/other-clients">Other Clients</router-link> &mdash; connecting without the plugin, and the wire protocol</li>
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
