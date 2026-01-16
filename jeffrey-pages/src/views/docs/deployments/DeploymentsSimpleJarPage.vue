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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'prerequisites', text: 'Prerequisites', level: 2 },
  { id: 'download', text: 'Download', level: 2 },
  { id: 'running', text: 'Running Jeffrey', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const basicRun = `java -jar jeffrey.jar`;

const customPort = `java -jar jeffrey.jar --server.port=9090`;

const customHome = `java -jar jeffrey.jar \\
  --jeffrey.home.dir=/path/to/jeffrey-data`;

const fullConfig = `java -jar jeffrey.jar \\
  --server.port=9090 \\
  --jeffrey.home.dir=/data/jeffrey`;
</script>

<template>
  <article class="docs-article">
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Deployments</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Simple JAR Execution</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-file-earmark-zip"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Simple JAR Execution</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>The simplest way to run Jeffrey is directly as a JAR file. This is ideal for local development, quick testing, or environments where containers aren't available.</p>

        <h2 id="prerequisites">Prerequisites</h2>
        <ul>
          <li><strong>Java 25+</strong> - Jeffrey requires Java 25 or later</li>
          <li><strong>Disk space</strong> - At least 10GB for Jeffrey data and recordings</li>
          <li><strong>Memory</strong> - Minimum 2GB heap recommended</li>
        </ul>

        <h2 id="download">Download</h2>
        <p>Download the latest Jeffrey JAR from the <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank">GitHub releases page</a>.</p>

        <h2 id="running">Running Jeffrey</h2>
        <p>Start Jeffrey with default settings:</p>
        <DocsCodeBlock
          language="bash"
          :code="basicRun"
        />

        <p>Jeffrey will start on port <code>8585</code> and create a <code>jeffrey-data</code> directory in the current working directory.</p>

        <DocsCallout type="tip">
          <strong>Access Jeffrey:</strong> Open <a href="http://localhost:8585" target="_blank">http://localhost:8585</a> in your browser after starting.
        </DocsCallout>

        <h3>Custom Port</h3>
        <p>Run on a different port:</p>
        <DocsCodeBlock
          language="bash"
          :code="customPort"
        />

        <h3>Custom Data Directory</h3>
        <p>Specify a custom directory for Jeffrey data:</p>
        <DocsCodeBlock
          language="bash"
          :code="customHome"
        />

        <h2 id="configuration">Configuration</h2>
        <p>Common configuration options can be passed as command-line arguments:</p>
        <DocsCodeBlock
          language="bash"
          :code="fullConfig"
        />

        <table>
          <thead>
            <tr>
              <th>Option</th>
              <th>Default</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>--server.port</code></td>
              <td>8585</td>
              <td>HTTP server port</td>
            </tr>
            <tr>
              <td><code>--jeffrey.home.dir</code></td>
              <td>./jeffrey-data</td>
              <td>Base directory for all Jeffrey data</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="info">
          <strong>Configuration file:</strong> For more complex configurations, create an <code>application.properties</code> file and use <code>--spring.config.location=file:./application.properties</code>.
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
