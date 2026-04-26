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
  { id: 'logging', text: 'Logging Configuration', level: 2 },
  { id: 'profile', text: 'Profile Configuration', level: 2 },
  { id: 'database', text: 'Database Persistence', level: 2 },
  { id: 'container', text: 'Container Deployment', level: 2 },
  { id: 'compression', text: 'HTTP Compression', level: 2 },
  { id: 'ai-model', text: 'AI Model Settings', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Advanced Properties"
      icon="bi bi-sliders"
    />

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        The <code>advanced.properties</code> file contains tuning and advanced configuration options
        for Jeffrey Local. These settings are primarily used for performance optimization and specialized deployments.
      </p>

      <DocsCallout type="info">
        <strong>Reference File:</strong> All properties have sensible code defaults, so you only need to configure what you want to change.
      </DocsCallout>

      <h2 id="logging">Logging Configuration</h2>
      <p>Application logging and JFR event monitoring settings.</p>

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
            <td><code>logging.level.cafe.jeffrey.platform</code></td>
            <td><code>DEBUG</code></td>
            <td>Log level for Jeffrey platform classes</td>
          </tr>
          <tr>
            <td><code>jeffrey.logging.http-access.enabled</code></td>
            <td><code>false</code></td>
            <td>Enable HTTP access logging</td>
          </tr>
          <tr>
            <td><code>jeffrey.logging.jfr-events.application.enabled</code></td>
            <td><code>true</code></td>
            <td>Enable internal JFR event logging for HTTP and JDBC latency</td>
          </tr>
          <tr>
            <td><code>jeffrey.logging.jfr-events.application.threshold</code></td>
            <td><code>1s</code></td>
            <td>Only log events taking longer than this threshold</td>
          </tr>
        </tbody>
      </table>

      <h2 id="profile">Profile Configuration</h2>
      <p>Settings for profile initialization and processing.</p>

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
            <td><code>jeffrey.profile.data-initializer.enabled</code></td>
            <td><code>true</code></td>
            <td>Enable automatic profile data initialization</td>
          </tr>
          <tr>
            <td><code>jeffrey.profile.data-initializer.blocking</code></td>
            <td><code>true</code></td>
            <td>Block until initialization completes</td>
          </tr>
          <tr>
            <td><code>jeffrey.profile.data-initializer.concurrent</code></td>
            <td><code>true</code></td>
            <td>Enable concurrent initialization</td>
          </tr>
          <tr>
            <td><code>jeffrey.profile.frame-resolution</code></td>
            <td><code>CACHE</code></td>
            <td>
              Frame resolution mode for flamegraphs.
              <code>CACHE</code>: ~10x faster using in-memory cache.
              <code>DATABASE</code>: SQL-side resolution.
            </td>
          </tr>
        </tbody>
      </table>

      <h2 id="database">Database Persistence</h2>
      <p>DuckDB database connection and pool settings.</p>

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
            <td><code>jeffrey.persistence.database.url</code></td>
            <td><code>jdbc:duckdb:${jeffrey.local.home.dir}/jeffrey-data.db</code></td>
            <td>DuckDB database connection URL</td>
          </tr>
          <tr>
            <td><code>jeffrey.persistence.database.pool-size</code></td>
            <td><code>25</code></td>
            <td>Database connection pool size</td>
          </tr>
          <tr>
            <td><code>jeffrey.persistence.database.batch-size</code></td>
            <td><code>10000</code></td>
            <td>Batch size for bulk database operations</td>
          </tr>
        </tbody>
      </table>

      <h2 id="container">Container Deployment</h2>
      <p>Settings for running Jeffrey Local in containers.</p>

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
            <td><code>jeffrey.copy-libs.enabled</code></td>
            <td><code>false</code></td>
            <td>Enable copying libraries from container image</td>
          </tr>
          <tr>
            <td><code>jeffrey.copy-libs.source</code></td>
            <td><code>/jeffrey-libs</code></td>
            <td>Source path for libraries (inside container)</td>
          </tr>
          <tr>
            <td><code>jeffrey.copy-libs.target</code></td>
            <td><code>${jeffrey.home.dir}/libs</code></td>
            <td>Target path for copied libraries</td>
          </tr>
          <tr>
            <td><code>jeffrey.copy-libs.max-kept-versions</code></td>
            <td><code>10</code></td>
            <td>Maximum number of versioned library directories to keep</td>
          </tr>
        </tbody>
      </table>

      <h2 id="compression">HTTP Compression</h2>
      <p>Response compression settings for the web server.</p>

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
            <td><code>server.compression.enabled</code></td>
            <td><code>false</code></td>
            <td>Enable GZIP compression for responses</td>
          </tr>
          <tr>
            <td><code>server.compression.mime-types</code></td>
            <td><code>application/json,text/html</code></td>
            <td>MIME types to compress</td>
          </tr>
          <tr>
            <td><code>server.compression.min-response-size</code></td>
            <td><code>1024</code></td>
            <td>Minimum response size (bytes) to compress</td>
          </tr>
        </tbody>
      </table>

      <h2 id="ai-model">AI Model Settings</h2>
      <p>Configuration for the AI model used by the assistant.</p>

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
            <td>AI provider: <code>claude</code> for Claude models, <code>openai</code> for GPT/O1/O3 models, <code>none</code> to disable</td>
          </tr>
          <tr>
            <td><code>jeffrey.local.ai.model</code></td>
            <td><code>claude-opus-4-6</code></td>
            <td>AI model name. Claude: <code>claude-opus-4-6</code>, <code>claude-sonnet-4-6</code>, <code>claude-sonnet-4-20250514</code>. OpenAI: <code>gpt-4o</code>, <code>gpt-4o-mini</code>, <code>o3-mini</code></td>
          </tr>
          <tr>
            <td><code>jeffrey.local.ai.max-tokens</code></td>
            <td><code>128000</code></td>
            <td>Maximum tokens in AI response</td>
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
