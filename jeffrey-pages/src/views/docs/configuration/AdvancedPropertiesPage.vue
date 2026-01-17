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
  { id: 'cors', text: 'CORS Configuration', level: 2 },
  { id: 'logging', text: 'Logging Configuration', level: 2 },
  { id: 'job-scheduler', text: 'Job Scheduler', level: 2 },
  { id: 'profile', text: 'Profile Configuration', level: 2 },
  { id: 'storage', text: 'Project/Recording Storage', level: 2 },
  { id: 'profiler', text: 'Profiler Agent Settings', level: 2 },
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
    <nav class="docs-breadcrumb">
      <router-link to="/docs" class="breadcrumb-item">
        <i class="bi bi-book me-1"></i>Docs
      </router-link>
      <span class="breadcrumb-separator">/</span>
      <router-link to="/docs/configuration/overview" class="breadcrumb-item">Configuration</router-link>
      <span class="breadcrumb-separator">/</span>
      <span class="breadcrumb-item active">Advanced Properties</span>
    </nav>

    <header class="docs-header">
      <div class="header-icon">
        <i class="bi bi-sliders"></i>
      </div>
      <div class="header-content">
        <h1 class="docs-title">Advanced Properties</h1>
        <p class="docs-subtitle">Fine-tuning options for power users</p>
      </div>
    </header>

    <div class="docs-content">
      <h2 id="overview">Overview</h2>
      <p>
        The <code>advanced.properties</code> file contains tuning and advanced configuration options.
        These settings are primarily used for performance optimization, job scheduling, and specialized deployments.
      </p>

      <DocsCallout type="info">
        <strong>Reference File:</strong> This file serves as documentation of available options.
        All properties have sensible code defaults, so you only need to configure what you want to change.
      </DocsCallout>

      <h2 id="cors">CORS Configuration</h2>
      <p>Cross-Origin Resource Sharing settings for API access.</p>

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
            <td><code>jeffrey.cors.mode</code></td>
            <td><code>PROD</code></td>
            <td>
              CORS mode. <code>DEV</code>: all endpoints available for all origins.
              <code>PROD</code>: only /api/** endpoints available for all origins.
            </td>
          </tr>
        </tbody>
      </table>

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
            <td><code>logging.level.pbouda.jeffrey.platform</code></td>
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

      <h2 id="job-scheduler">Job Scheduler</h2>
      <p>Background job scheduling configuration.</p>

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
            <td><code>jeffrey.job.scheduler.enabled</code></td>
            <td><code>true</code></td>
            <td>Enable background job scheduler</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.default.period</code></td>
            <td><code>1m</code></td>
            <td>Default job execution period (ns, us, ms, s, m, h, d)</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.repository-session-cleaner.period</code></td>
            <td><code>1m</code></td>
            <td>Session cleanup job period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.repository-recording-cleaner.period</code></td>
            <td><code>1m</code></td>
            <td>Recording cleanup job period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.recording-generator.period</code></td>
            <td><code>1m</code></td>
            <td>Recording generator job period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.project-recording-storage-synchronizer.period</code></td>
            <td><code>5m</code></td>
            <td>Storage synchronization job period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.repository-compression.period</code></td>
            <td><code>1m</code></td>
            <td>Repository compression job period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.projects-synchronizer.period</code></td>
            <td><code>1m</code></td>
            <td>Projects synchronizer job period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.profiler-settings-synchronizer.period</code></td>
            <td><code>10s</code></td>
            <td>Profiler settings synchronization period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.workspace-events-replicator.period</code></td>
            <td><code>10s</code></td>
            <td>Workspace events replication period</td>
          </tr>
          <tr>
            <td><code>jeffrey.job.orphaned-project-recording-storage-cleaner.period</code></td>
            <td><code>5m</code></td>
            <td>Orphaned storage cleanup period</td>
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

      <h2 id="storage">Project/Recording Storage</h2>
      <p>File system storage locations for recordings and repository data.</p>

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
            <td><code>jeffrey.project.recording-storage.path</code></td>
            <td><code>${jeffrey.home.dir}/recordings</code></td>
            <td>Directory for storing JFR recordings</td>
          </tr>
          <tr>
            <td><code>jeffrey.project.repository-storage.detection.finished-period</code></td>
            <td><code>30m</code></td>
            <td>Time after which a repository session is considered finished</td>
          </tr>
        </tbody>
      </table>

      <h2 id="profiler">Profiler Agent Settings</h2>
      <p>Global settings for the Jeffrey profiler agent.</p>

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
            <td><code>jeffrey.profiler.global-settings.create-if-not-exists</code></td>
            <td><code>true</code></td>
            <td>Automatically create global profiler settings</td>
          </tr>
          <tr>
            <td><code>jeffrey.profiler.global-settings.command</code></td>
            <td><em>(see below)</em></td>
            <td>Default profiler agent command with placeholders</td>
          </tr>
        </tbody>
      </table>

      <DocsCallout type="tip">
        <strong>Default Profiler Command:</strong>
        <code>-agentpath:&lt;&lt;JEFFREY_PROFILER_PATH&gt;&gt;=start,alloc,lock,event=ctimer,loop=15m,chunksize=5m,file=&lt;&lt;JEFFREY_CURRENT_SESSION&gt;&gt;/profile-%t.jfr</code>
      </DocsCallout>

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
            <td><code>jdbc:duckdb:${jeffrey.home.dir}/jeffrey-data.db</code></td>
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
      <p>Settings for running Jeffrey in containers.</p>

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
            <td><code>spring.ai.anthropic.chat.options.model</code></td>
            <td><code>claude-opus-4-5-20251101</code></td>
            <td>Anthropic Claude model to use</td>
          </tr>
          <tr>
            <td><code>spring.ai.anthropic.chat.options.max-tokens</code></td>
            <td><code>2048</code></td>
            <td>Maximum tokens in AI response</td>
          </tr>
        </tbody>
      </table>
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
