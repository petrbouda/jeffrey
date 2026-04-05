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
  { id: 'server-configuration', text: 'Server Configuration', level: 2 },
  { id: 'core-directories', text: 'Core Directories', level: 2 },
  { id: 'grpc-configuration', text: 'gRPC Configuration', level: 2 },
  { id: 'cors-configuration', text: 'CORS Configuration', level: 2 }
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
        The <code>application.properties</code> file contains essential settings for Jeffrey Server
        (<code>jeffrey-server.jar</code>) — the recording collection server. These are the most frequently changed properties when deploying.
      </p>

      <DocsCallout type="info">
        <strong>Optional File:</strong> All properties have sensible code defaults. You only need
        to create this file if you want to override the defaults.
      </DocsCallout>

      <h2 id="server-configuration">Server Configuration</h2>
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

      <h2 id="grpc-configuration">gRPC Configuration</h2>
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

      <h2 id="cors-configuration">CORS Configuration</h2>
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
