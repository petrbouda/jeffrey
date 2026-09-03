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
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'server', text: 'Server', level: 2 },
  { id: 'uploads', text: 'File Uploads', level: 2 },
  { id: 'core-directories', text: 'Core Directories', level: 2 },
  { id: 'update-check', text: 'Update Check', level: 2 },
  { id: 'mcp-server', text: 'MCP Server', level: 2 },
  { id: 'ai-assistant', text: 'AI Assistant', level: 2 },
  { id: 'advisor', text: 'Advisor', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Application Properties"
      icon="bi bi-file-earmark-text"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        These are the most frequently changed properties when running Jeffrey Microscope.
        All Microscope-specific keys live under the <code>jeffrey.microscope.</code> namespace.
        Standard Spring Boot keys (e.g. <code>server.*</code>, <code>spring.*</code>, <code>logging.*</code>) are also supported.
      </p>

      <DocsCallout type="info">
        <strong>All optional:</strong> every property has a sensible default in code or in
        <code>application.properties</code> bundled with <code>microscope.jar</code>.
        Override only what you need via your own <code>application.properties</code>,
        environment variables, or command-line arguments.
      </DocsCallout>

      <h2 id="server">Server</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>server.port</code></td>
            <td><code>8585</code></td>
            <td>HTTP port for the Microscope web UI. Standard Spring Boot property. Microscope sets 8585 rather than Spring Boot's own 8080, which is usually taken by the application being profiled.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="uploads">File Uploads</h2>
      <p>
        JFR recordings and heap dumps can easily reach multiple gigabytes, so Microscope ships with
        upload size limits disabled. Override these only if you want to clamp incoming uploads.
      </p>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>spring.servlet.multipart.max-file-size</code></td>
            <td><code>-1</code></td>
            <td>Max single-file size. <code>-1</code> means unlimited.</td>
          </tr>
          <tr>
            <td><code>spring.servlet.multipart.max-request-size</code></td>
            <td><code>-1</code></td>
            <td>Max total request size. <code>-1</code> means unlimited.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="core-directories">Core Directories</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.home.dir</code></td>
            <td><code>${user.home}/.jeffrey-microscope</code></td>
            <td>
              Base directory for all Microscope data (DuckDB files, recordings, profiles).
              Equivalent env var: <code>JEFFREY_MICROSCOPE_HOME_DIR</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.temp.dir</code></td>
            <td><code>${jeffrey.microscope.home.dir}/temp</code></td>
            <td>Working directory for temporary files (uploads, parsing scratch space).</td>
          </tr>
        </tbody>
      </table>

      <h2 id="update-check">Update Check</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.update-check.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Periodically checks GitHub releases for new Microscope versions.
              Set to <code>false</code> in air-gapped environments.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="mcp-server">MCP Server</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.mcp.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Serves the MCP endpoint at <code>/api/internal/mcp</code>, which an external Claude Code
              session reads profiles through. Set to <code>false</code> to make it answer
              <code>404</code>. Read at startup, so a change takes a restart. See
              <router-link to="/docs/microscope-mcp/enabling">Enabling the Server</router-link>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.mcp.ingest.enabled</code></td>
            <td><code>true</code></td>
            <td>
              Advertises the <code>recordings_</code> tools on that endpoint, which import a recording
              file from this machine and build a profile from it &mdash; the one family that is not
              read-only. Set to <code>false</code> to keep the read-only server and nothing else. Read
              at startup.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="ai-assistant">AI Assistant</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.ai.provider</code></td>
            <td><code>none</code></td>
            <td>
              AI provider. One of <code>claude</code>, <code>chatgpt</code>, <code>ollama</code>, <code>claude-code</code>, or <code>none</code> (disabled).
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ai.model</code></td>
            <td><code>claude-opus-5</code></td>
            <td>Model identifier matching the chosen provider.</td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.ai.max-tokens</code></td>
            <td><code>128000</code></td>
            <td>Maximum tokens in an AI response.</td>
          </tr>
        </tbody>
      </table>

      <h2 id="advisor">Advisor</h2>
      <table>
        <thead>
          <tr>
            <th>Property</th>
            <th>Default</th>
            <th>Description</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><code>jeffrey.microscope.advisor.max-concurrent-runs</code></td>
            <td><code>2</code></td>
            <td>
              How many event types the Advisor analyzes at the same time. <code>0</code> means no
              ceiling. Installation-wide &mdash; two profiles analyzed at once share it. Also
              editable as <strong>Settings &rarr; Advisor &rarr; Parallel analyses</strong>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.microscope.advisor.prune-threshold-pct</code></td>
            <td><code>1.0</code></td>
            <td>
              Frames below this share of the call tree are pruned from the prompt the Advisor sends,
              trading detail for prompt size. Must be greater than <code>0</code> and less than
              <code>100</code>.
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>API key:</strong> store the provider's API key as a
        <router-link to="/docs/microscope/configuration/secrets">secret</router-link>
        rather than placing it in <code>application.properties</code>.
      </DocsCallout>

      <DocsCallout type="info">
        <strong>Editable at runtime:</strong> every category in <strong>Settings</strong> in the
        Microscope UI is hot-reloaded — the AI properties above, the Advisor properties, the log level
        (<code>logging.level.cafe.jeffrey</code>) and the flamegraph thresholds. A change saved there is
        stored in the Microscope database and applied immediately — switching AI provider, pasting an API
        key or raising the log level does not need a restart. Values stored that way take precedence over
        <code>application.properties</code>, system properties and environment variables.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
