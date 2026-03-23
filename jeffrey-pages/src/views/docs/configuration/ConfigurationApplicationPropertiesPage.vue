<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
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
  { id: 'jeffrey-local', text: 'Jeffrey Local Properties', level: 2 },
  { id: 'jeffrey-server', text: 'Jeffrey Server Properties', level: 2 }
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
        The <code>application.properties</code> file contains essential settings that are commonly
        customized. These are the most frequently changed properties when deploying Jeffrey.
      </p>

      <DocsCallout type="info">
        <strong>Optional File:</strong> All properties have sensible code defaults. You only need
        to create this file if you want to override the defaults.
      </DocsCallout>

      <h2 id="jeffrey-local">Jeffrey Local Properties</h2>
      <p>Settings for the Jeffrey Local application (jeffrey.jar) — the analysis tool.</p>

      <h3>Server Configuration</h3>
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
            <td><code>8080</code></td>
            <td>HTTP server port for the web interface</td>
          </tr>
        </tbody>
      </table>

      <h3>Core Directories</h3>
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
            <td><code>jeffrey.local.home.dir</code></td>
            <td><code>${user.home}/.jeffrey-local</code></td>
            <td>
              Base directory for all Jeffrey Local data. Env var: <code>JEFFREY_LOCAL_HOME_DIR</code>
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.local.temp.dir</code></td>
            <td><code>${jeffrey.local.home.dir}/temp</code></td>
            <td>Directory for temporary files during processing</td>
          </tr>
        </tbody>
      </table>

      <h3>AI Assistant</h3>
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
            <td><code>jeffrey.local.ai.provider</code></td>
            <td><code>claude</code></td>
            <td>
              AI provider: <code>claude</code>, <code>chatgpt</code>, or <code>none</code> (disabled)
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.local.ai.model</code></td>
            <td><code>claude-opus-4-6</code></td>
            <td>AI model name</td>
          </tr>
          <tr>
            <td><code>jeffrey.local.ai.max-tokens</code></td>
            <td><code>128000</code></td>
            <td>Maximum tokens in AI response</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>API Keys Required:</strong> To use the AI assistant, you need to configure the
        appropriate API key in <router-link to="/docs/configuration/secrets">secrets.properties</router-link>.
      </DocsCallout>

      <h2 id="jeffrey-server">Jeffrey Server Properties</h2>
      <p>Settings for the Jeffrey Server application (jeffrey-server.jar) — the recording collection server.</p>

      <h3>Server Configuration</h3>
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
            <td><code>8080</code></td>
            <td>HTTP server port for the web interface</td>
          </tr>
        </tbody>
      </table>

      <h3>Core Directories</h3>
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
            <td><code>jeffrey.server.home.dir</code></td>
            <td><code>${user.home}/.jeffrey-server</code></td>
            <td>
              Base directory for all Jeffrey Server data. Env var: <code>JEFFREY_SERVER_HOME_DIR</code>
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.server.temp.dir</code></td>
            <td><code>${jeffrey.server.home.dir}/temp</code></td>
            <td>Directory for temporary files</td>
          </tr>
        </tbody>
      </table>

      <h3>gRPC Configuration</h3>
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
            <td><code>jeffrey.server.grpc.port</code></td>
            <td><code>9090</code></td>
            <td>gRPC server port for Jeffrey Local connections</td>
          </tr>
        </tbody>
      </table>

      <h3>CORS Configuration</h3>
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
            <td><code>jeffrey.server.cors.mode</code></td>
            <td><code>DEV</code></td>
            <td>CORS mode: <code>DEV</code> allows all cross-origin requests, <code>PROD</code> restricts to specific origins</td>
          </tr>
        </tbody>
      </table>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
