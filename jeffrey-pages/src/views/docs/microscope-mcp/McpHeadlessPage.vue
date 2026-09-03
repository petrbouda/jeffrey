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
  { id: 'what-it-is', text: 'What It Is', level: 2 },
  { id: 'running-the-jar', text: 'Running the Jar', level: 2 },
  { id: 'running-the-image', text: 'Running the Image', level: 2 },
  { id: 'one-home-directory-one-process', text: 'One Home Directory, One Process', level: 2 },
  { id: 'what-is-not-in-it', text: 'What Is Not in It', level: 2 },
  { id: 'security', text: 'Security', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const runJar = `java -jar microscope-mcp.jar
# Microscope MCP started: url=http://localhost:8080/api/internal/mcp
# Connect Claude Code with: claude mcp add --transport http jeffrey http://localhost:8080/api/internal/mcp`;

const runJarOtherHome = `java -Djeffrey.microscope.home.dir=/data/jeffrey-microscope -jar microscope-mcp.jar`;

const runImage = `docker run --rm -p 127.0.0.1:8080:8080 \\
  -v ~/.jeffrey-microscope:/root/.jeffrey-microscope \\
  petrbouda/microscope-mcp:latest`;

const probe = `curl -s -X POST http://localhost:8080/api/internal/mcp \\
  -H 'Content-Type: application/json' \\
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"profiles_list","arguments":{}}}'`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Headless Server (microscope-mcp)"
      icon="bi bi-hdd-network"
    />

    <div class="docs-content">
      <p><code>microscope-mcp.jar</code> is a second Microscope build with one job: serve the MCP endpoint to a Claude Code session. No web UI, no in-app AI providers, no hub client &mdash; and no switch to flip, because there is nothing else it could be doing.</p>

      <h2 id="what-it-is">What It Is</h2>
      <p>The same analysis engine as the full Microscope, opening the same home directory (<code>~/.jeffrey-microscope</code>) and reading the same settings, packaged without the parts an MCP client never touches. It advertises exactly the tool families the full server does &mdash; see the <router-link to="/docs/microscope-mcp/tools">Tool Reference</router-link> &mdash; and every one of them is read-only.</p>

      <p>It analyses nothing itself. Profiles are created in the full Microscope; the headless server answers questions about the ones that already exist. That makes it the right shape for a machine where a browser is a nuisance: a build server that received a copy of a home directory, a container next to a Claude Code session, a CI job that asks the same questions after every run.</p>

      <DocsCallout type="info" title="Roughly 40% smaller">
        The size of the full jar is dominated by DuckDB and, second, by the OpenAI and Anthropic SDKs the in-app assistant uses. The headless build drops the SDKs, the gRPC hub client and the UI; DuckDB stays, because every tool reads through it.
      </DocsCallout>

      <h2 id="running-the-jar">Running the Jar</h2>
      <p>Download <code>microscope-mcp.jar</code> from the same GitHub release as <code>microscope.jar</code> and start it with Java 25:</p>
      <DocsCodeBlock :code="runJar" language="bash" />

      <p>The two log lines are the whole onboarding: the endpoint, and the command that registers it with Claude Code. The <router-link to="/docs/microscope-mcp/plugin">Claude Code Plugin</router-link> works unchanged, since its default endpoint is the same port and path.</p>

      <p>To serve a home directory other than the default, pass the same property the full Microscope takes:</p>
      <DocsCodeBlock :code="runJarOtherHome" language="bash" />

      <h2 id="running-the-image">Running the Image</h2>
      <p>The image <code>petrbouda/microscope-mcp</code> is published alongside <code>petrbouda/microscope</code>. Mount the home directory and publish the port on loopback only:</p>
      <DocsCodeBlock :code="runImage" language="bash" />

      <p>Then check that the server sees your profiles:</p>
      <DocsCodeBlock :code="probe" language="bash" />

      <h2 id="one-home-directory-one-process">One Home Directory, One Process</h2>
      <DocsCallout type="warning" title="Stop the full Microscope first">
        DuckDB locks its database files exclusively. The headless server and the full Microscope can share a home directory, but not at the same time: start one only after the other has stopped. If the second one fails at startup with <code>Could not set lock on file</code>, the first is still running.
      </DocsCallout>

      <p>The usual rhythm is therefore: analyse recordings in the full Microscope, stop it, start <code>microscope-mcp</code> and hand the endpoint to Claude Code. On a machine that only ever runs the headless server, copy or mount a home directory produced elsewhere.</p>

      <h2 id="what-is-not-in-it">What Is Not in It</h2>
      <ul>
        <li><strong>The web UI</strong> &mdash; every path other than <code>/api/internal/mcp</code> answers <code>404</code>.</li>
        <li><strong>The in-app assistant</strong> &mdash; the direction in which Jeffrey calls out to a model provider. The Claude Code session brings its own model; this server needs none.</li>
        <li><strong>Hub workspaces</strong> &mdash; the gRPC client for remote workspaces. Profiles are resolved straight from the home directory&rsquo;s core database, which holds every profile the full Microscope analysed, whichever workspace it came from.</li>
        <li><strong>Ingestion</strong> &mdash; no upload, no analysis, no settings endpoints. A recording becomes a profile in the full Microscope.</li>
      </ul>

      <p>A heap dump is answerable only once its index has been built, which the full Microscope does the first time the dump is opened. A profile whose heap dump has not been indexed yet says so in the tool result.</p>

      <h2 id="security">Security</h2>
      <p>Everything in <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link> applies, with one difference: there is no off state. The endpoint answers from the moment the process is up, so the decision the toggle used to represent moves to the network: bind to loopback, tunnel, or put an authenticating proxy in front. The <code>docker run</code> line above publishes the port on <code>127.0.0.1</code> for that reason.</p>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
