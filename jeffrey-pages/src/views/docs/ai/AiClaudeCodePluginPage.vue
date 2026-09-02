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
  { id: 'enable-the-server', text: 'Enable the Server', level: 2 },
  { id: 'install-the-plugin', text: 'Install the Plugin', level: 2 },
  { id: 'pointing-it-elsewhere', text: 'Pointing It Elsewhere', level: 2 },
  { id: 'what-you-get', text: 'What You Get', level: 2 },
  { id: 'permissions', text: 'Permissions', level: 2 },
  { id: 'example-prompts', text: 'Example Prompts', level: 2 },
  { id: 'connecting-without-the-plugin', text: 'Connecting Without the Plugin', level: 2 },
  { id: 'security', text: 'Security', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const installMarketplace = `/plugin marketplace add petrbouda/jeffrey
/plugin install jeffrey@jeffrey`;

const installLocal = `git clone https://github.com/petrbouda/jeffrey
claude --plugin-dir ./jeffrey/jeffrey-claude-plugin`;

const customUrl = `export JEFFREY_MCP_URL="http://localhost:9000/api/internal/mcp"`;

const permissionRule = `mcp__plugin_jeffrey_microscope__*`;

const promptProfiles = `list the Jeffrey profiles, then show me where the CPU time goes in the most recent one`;

const promptTrace = `the GET /api/orders operation is slow - find a slow example and tell me
what the JVM was doing inside its slowest span`;

const promptHeap = `which classes retain the most memory in the heap dump, and what is keeping
the biggest one alive?`;

const manualAdd = `claude mcp add --transport http jeffrey http://localhost:8080/api/internal/mcp`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Claude Code Plugin"
      icon="bi bi-plug"
    />

    <div class="docs-content">
      <p>Jeffrey serves an <strong>MCP server</strong> that an interactive Claude Code session can connect to from your own repository. Instead of copying an export out of the browser and pasting it into a chat, the profile and your source code end up in front of the same reader: Claude lists your analysed recordings, queries their DuckDB tables, and pulls flamegraph, trace and heap-dump exports on its own.</p>

      <p>The <strong>Jeffrey plugin</strong> packages that server together with a few skills, so connecting is one install rather than a hand-written command per machine and per repository.</p>

      <DocsCallout type="info" title="Everything is read-only">
        The plugin can read every profile in the installation it connects to. It cannot modify, rename or delete anything &mdash; data cleanup and frame renaming stay in the Jeffrey UI.
      </DocsCallout>

      <h2 id="enable-the-server">Enable the Server</h2>
      <p>The MCP server is <strong>off by default</strong>, because it hands an external client every profile you have analysed. Turn it on in <strong>Settings &rarr; Claude Code (MCP)</strong>. The tab also shows the exact endpoint URL for your installation, which is what you need if Jeffrey is not on the default port.</p>

      <h2 id="install-the-plugin">Install the Plugin</h2>
      <p>From inside Claude Code:</p>
      <DocsCodeBlock :code="installMarketplace" language="bash" />

      <p>Or, if you would rather work from a clone &mdash; useful when developing against a modified Jeffrey:</p>
      <DocsCodeBlock :code="installLocal" language="bash" />

      <h2 id="pointing-it-elsewhere">Pointing It Elsewhere</h2>
      <p>The plugin defaults to <code>http://localhost:8080/api/internal/mcp</code>. For any other address &mdash; a different port, a container, an SSH tunnel &mdash; set the endpoint before starting Claude Code:</p>
      <DocsCodeBlock :code="customUrl" language="bash" />

      <h2 id="what-you-get">What You Get</h2>
      <p>Roughly forty tools, in five families:</p>
      <table>
        <thead>
          <tr>
            <th>Family</th>
            <th>What it reads</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>profiles_</code></td>
            <td>The catalogue: which recordings are analysed, what each one can answer, a deep link into the UI</td>
          </tr>
          <tr>
            <td><code>flamegraph_</code></td>
            <td>Which graphs a profile supports, and the call tree as Markdown</td>
          </tr>
          <tr>
            <td><code>traces_</code></td>
            <td>Trace operations, exemplars, span trees and span-scoped flamegraphs</td>
          </tr>
          <tr>
            <td><code>jfr_</code></td>
            <td>The profile&rsquo;s DuckDB tables &mdash; schema and read-only SQL</td>
          </tr>
          <tr>
            <td><code>heap_</code></td>
            <td>Heap summary, class histogram, dominator tree, leak suspects, GC-root paths, read-only SQL</td>
          </tr>
        </tbody>
      </table>

      <p>Plus three skills, which Claude uses on its own or you can invoke directly:</p>
      <ul>
        <li><code>/jeffrey:analyze-profile</code> &mdash; where to start and which family answers which question</li>
        <li><code>/jeffrey:jfr-sql</code> &mdash; the JFR schema and the DuckDB idioms that go with it</li>
        <li><code>/jeffrey:heap-sql</code> &mdash; the heap-dump index schema</li>
      </ul>

      <p>The exports carry their own reading instructions &mdash; what <code>self</code> means against <code>total</code>, what the frame tags mean, what was pruned &mdash; so the skills stay short and cover only what the tool output does not already explain.</p>

      <h2 id="permissions">Permissions</h2>
      <p>Claude Code asks before each tool the first time. Since every Jeffrey tool is read-only, approving the whole family once is usually what you want &mdash; from the prompt, or up front with <code>/permissions</code>:</p>
      <DocsCodeBlock :code="permissionRule" language="bash" />

      <h2 id="example-prompts">Example Prompts</h2>

      <h3>Find the hot paths</h3>
      <DocsCodeBlock :code="promptProfiles" language="bash" />

      <h3>Explain a slow request</h3>
      <DocsCodeBlock :code="promptTrace" language="bash" />

      <h3>Chase a memory problem</h3>
      <DocsCodeBlock :code="promptHeap" language="bash" />

      <h2 id="connecting-without-the-plugin">Connecting Without the Plugin</h2>
      <p>The plugin is a convenience over a plain MCP server. If you would rather register it yourself &mdash; or you want it in one project only &mdash; the Settings tab gives you both the command and the equivalent <code>.mcp.json</code> entry:</p>
      <DocsCodeBlock :code="manualAdd" language="bash" />
      <p>You get the same tools; they are named <code>mcp__jeffrey__*</code> rather than <code>mcp__plugin_jeffrey_microscope__*</code>, and you do not get the skills.</p>

      <h2 id="security">Security</h2>
      <DocsCallout type="warning" title="No authentication yet">
        The MCP endpoint carries the same trust assumption as the rest of Jeffrey&rsquo;s API: anyone who can reach the address can read every profile in that installation. Keep Jeffrey bound to localhost, or put it behind an SSH tunnel or an authenticating reverse proxy, before opening it on a shared network.
      </DocsCallout>

      <p>The server is off until you enable it, and answers <code>404</code> while disabled &mdash; a disabled server looks like no server at all rather than one refusing to talk.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
