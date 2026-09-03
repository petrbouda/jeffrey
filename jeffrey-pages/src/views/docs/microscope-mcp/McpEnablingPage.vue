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
  { id: 'turn-it-on', text: 'Turn It On', level: 2 },
  { id: 'the-endpoint-url', text: 'The Endpoint URL', level: 2 },
  { id: 'while-it-is-off', text: 'While It Is Off', level: 2 },
  { id: 'what-a-session-holds-open', text: 'What a Session Holds Open', level: 2 },
  { id: 'security', text: 'Security', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const defaultEndpoint = `http://localhost:8080/api/internal/mcp`;

const tunnel = `ssh -N -L 8080:localhost:8080 you@the-host-running-jeffrey`;

const disabledProbe = `curl -s -o /dev/null -w '%{http_code}\\n' \\
  -X POST http://localhost:8080/api/internal/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
# 404 while disabled, 200 once enabled`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Enabling the Server"
      icon="bi bi-toggle-on"
    />

    <div class="docs-content">
      <p>The MCP server is <strong>off by default</strong>. That is deliberate: turning it on hands any client that can reach the address every profile in the installation, and there is no authentication in front of it yet. It should be a decision, not a default.</p>

      <h2 id="turn-it-on">Turn It On</h2>
      <p>In the Jeffrey UI, open <strong>Settings &rarr; Claude Code (MCP)</strong> and enable it. The setting is hot-reloaded and checked per request, so it takes effect immediately &mdash; no restart, and turning it back off closes the door on the next call rather than at the next boot.</p>

      <p>The switch is a stored Jeffrey setting, not an application property: it lives in the settings table of the home directory&rsquo;s core database and outranks the command line and the environment, so passing <code>jeffrey.microscope.mcp.enabled=true</code> at startup has no effect. Flip it in the UI, or through <code>PUT /api/internal/settings</code>.</p>

      <p>For a machine without a browser &mdash; a container, a CI box, a build server &mdash; there is a second artifact whose only job is this endpoint and which needs no switch at all: see <router-link to="/docs/microscope-mcp/headless">Headless Server (microscope-mcp)</router-link>.</p>

      <h2 id="the-endpoint-url">The Endpoint URL</h2>
      <p>One endpoint serves the whole installation:</p>
      <DocsCodeBlock :code="defaultEndpoint" language="bash" />

      <p>The profile is a tool argument rather than part of the URL, so a client registers this address once and can then move between profiles &mdash; and between the JFR, flamegraph, trace and heap-dump families &mdash; inside a single session.</p>

      <DocsCallout type="tip" title="Do not type the URL from memory">
        The Settings tab shows the endpoint <em>as your browser just reached it</em>, derived from the request rather than hardcoded. Behind a container, a reverse proxy or a non-default port, <code>localhost:8080</code> is wrong &mdash; and wrong in a way you would only discover after pasting the command. Copy the one the tab shows.
      </DocsCallout>

      <h2 id="while-it-is-off">While It Is Off</h2>
      <p>A disabled server answers <code>404</code>. It does not answer a JSON-RPC error explaining that it is disabled, because a disabled server should look like no server at all rather than like one refusing to talk &mdash; an unauthenticated probe learns nothing about whether this Jeffrey has profiles worth asking for.</p>

      <p>The practical consequence: if a client reports that the server is unreachable or every tool call fails, check the toggle first.</p>
      <DocsCodeBlock :code="disabledProbe" language="bash" />

      <h2 id="what-a-session-holds-open">What a Session Holds Open</h2>
      <p>Each profile is its own DuckDB database, and Jeffrey's connection pools evict idle databases after a few minutes. That is right for the UI, where a reader moves on, and wrong for an interactive session that may spend twenty minutes on one profile with long pauses for reading.</p>

      <p>So the first tool call for a profile takes a <strong>lease</strong> on that profile's database and holds it. The lease is released after <strong>30 minutes</strong> without a call for that profile; the next call simply takes a new one. Nothing needs closing by hand, and no session breaks halfway through because you stopped to read the code.</p>

      <h2 id="security">Security</h2>
      <DocsCallout type="warning" title="No authentication yet">
        The MCP endpoint carries the same trust assumption as the rest of Jeffrey&rsquo;s API: anyone who can reach the address can read every profile in that installation &mdash; the recordings, their stack traces, their SQL statements, and the contents of any heap dump you have indexed.
      </DocsCallout>

      <p>Before enabling it, decide what can reach the address:</p>
      <ul>
        <li><strong>Bound to loopback.</strong> The default, and enough for a Jeffrey and a Claude Code session on the same machine.</li>
        <li><strong>Through an SSH tunnel.</strong> For a Jeffrey on a remote host or in a container, forward the port rather than publishing it:
          <DocsCodeBlock :code="tunnel" language="bash" />
          The client then points at <code>localhost</code> and the endpoint is never exposed.
        </li>
        <li><strong>Behind an authenticating reverse proxy.</strong> For a shared installation. Note that MCP clients send an ordinary <code>Authorization</code> header, so a proxy that expects one works today &mdash; but Jeffrey itself does not check it.</li>
      </ul>

      <p>Two things limit the blast radius even so. Every advertised tool is read-only &mdash; the one JFR tool that writes is deliberately not exposed, so an external client can read Jeffrey's data but not rewrite it. And the server has no shell and no filesystem access: it answers questions about profiles and nothing else.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
