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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';
import DocsCallout from '@/components/docs/DocsCallout.vue';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'overview', text: 'Overview', level: 2 },
  { id: 'server', text: 'Server Configuration', level: 2 },
  { id: 'directories', text: 'Core Directories', level: 2 },
  { id: 'ai-assistant', text: 'AI Assistant', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <nav class="docs-breadcrumb">
      <router-link to="/docs" class="breadcrumb-item">
        <i class="bi bi-book me-1"></i>Docs
      </router-link>
      <span class="breadcrumb-separator">/</span>
      <router-link to="/docs/configuration/overview" class="breadcrumb-item">Configuration</router-link>
      <span class="breadcrumb-separator">/</span>
      <span class="breadcrumb-item active">Application Properties</span>
    </nav>

    <header class="docs-header">
      <div class="header-icon">
        <i class="bi bi-file-earmark-text"></i>
      </div>
      <div class="header-content">
        <h1 class="docs-title">Application Properties</h1>
        <p class="docs-subtitle">Essential configuration for Jeffrey</p>
      </div>
    </header>

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

      <h2 id="server">Server Configuration</h2>
      <p>Settings for the HTTP server that hosts the Jeffrey web interface.</p>

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

      <h2 id="directories">Core Directories</h2>
      <p>Base directories where Jeffrey stores its data and temporary files.</p>

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
            <td><code>jeffrey.home.dir</code></td>
            <td><code>${user.home}/.jeffrey</code></td>
            <td>
              Base directory for all Jeffrey data including database, workspaces, and profiles.
              Can also be set via environment variable <code>JEFFREY_HOME_DIR</code>.
            </td>
          </tr>
          <tr>
            <td><code>jeffrey.temp.dir</code></td>
            <td><code>${jeffrey.home.dir}/temp</code></td>
            <td>Directory for temporary files during profile processing</td>
          </tr>
        </tbody>
      </table>

      <h2 id="ai-assistant">AI Assistant</h2>
      <p>Configuration for the AI-powered OQL assistant feature.</p>

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
            <td><code>jeffrey.ai.enabled</code></td>
            <td><code>true</code></td>
            <td>Enable or disable the AI assistant feature</td>
          </tr>
          <tr>
            <td><code>jeffrey.ai.provider</code></td>
            <td><code>anthropic</code></td>
            <td>
              AI provider to use. Options: <code>anthropic</code> (Claude), <code>openai</code> (GPT), <code>none</code>
            </td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>API Keys Required:</strong> To use the AI assistant, you need to configure the
        appropriate API key in <router-link to="/docs/configuration/secrets">secrets.properties</router-link>.
      </DocsCallout>
    </div>

    <nav class="docs-nav-footer">
      <router-link
        v-if="adjacentPages.prev"
        :to="`/docs/${adjacentPages.prev.category}/${adjacentPages.prev.path}`"
        class="nav-link prev"
      >
        <i class="bi bi-arrow-left"></i>
        <div class="nav-text">
          <span class="nav-label">Previous</span>
          <span class="nav-title">{{ adjacentPages.prev.title }}</span>
        </div>
      </router-link>
      <div v-else class="nav-spacer"></div>
      <router-link
        v-if="adjacentPages.next"
        :to="`/docs/${adjacentPages.next.category}/${adjacentPages.next.path}`"
        class="nav-link next"
      >
        <div class="nav-text">
          <span class="nav-label">Next</span>
          <span class="nav-title">{{ adjacentPages.next.title }}</span>
        </div>
        <i class="bi bi-arrow-right"></i>
      </router-link>
    </nav>
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
