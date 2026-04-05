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
  { id: 'ai-assistant', text: 'AI Assistant', level: 2 }
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
        The <code>application.properties</code> file contains essential settings for Jeffrey Local
        (<code>jeffrey.jar</code>) — the analysis tool. These are the most frequently changed properties when deploying.
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
            <td><code>jeffrey.local.ai.provider</code></td>
            <td><code>none</code></td>
            <td>
              AI provider: <code>claude</code>, <code>openai</code>, or <code>none</code> (disabled)
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
        appropriate API key in <router-link to="/docs/local/configuration/secrets">secrets.properties</router-link>.
      </DocsCallout>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
