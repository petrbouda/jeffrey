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
import DocsCallout from '@/components/docs/DocsCallout.vue';
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-are-recording-sessions', text: 'What are Recording Sessions?', level: 2 },
  { id: 'session-contents', text: 'Session Contents', level: 2 },
  { id: 'workspace-availability', text: 'Workspace Availability', level: 2 }
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
        <span class="breadcrumb-item">Platform</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Recording Sessions</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Overview</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-collection-play"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Recording Sessions</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Recording Sessions represent <strong>profiling periods</strong> from running Java applications, containing JFR recordings and associated artifacts collected during application execution.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Recording Sessions are only available in <strong>Live</strong> and <strong>Remote</strong> workspaces through the <router-link to="/docs/concepts/projects/repository">Repository</router-link> feature.
        </DocsCallout>

        <h2 id="what-are-recording-sessions">What are Recording Sessions?</h2>
        <p>A recording session is created when you start profiling a Java application using Async-Profiler configured through <router-link to="/docs/cli/overview">Jeffrey CLI</router-link>. Each session represents a continuous profiling period and collects:</p>
        <ul>
          <li><strong>JFR recording chunks</strong> - Profiling data split into time-based files</li>
          <li><strong>Artifacts</strong> - Additional diagnostic files (heap dumps, logs, perf counters)</li>
          <li><strong>Metadata</strong> - Session timing, status, and file counts</li>
        </ul>

        <DocsCallout type="info">
          <strong>Chunked recordings:</strong> Async-Profiler creates JFR files in chunks (e.g., every 15 minutes) to prevent data loss and enable rolling analysis. This means a single session typically contains multiple JFR files that can be selectively merged for analysis.
        </DocsCallout>

        <h2 id="session-contents">Session Contents</h2>
        <p>Each recording session can contain multiple file types:</p>

        <div class="session-contents-grid">
          <div class="session-content-card jfr">
            <div class="card-header">
              <i class="bi bi-file-earmark-binary"></i>
              <h4>JFR Files</h4>
            </div>
            <div class="card-body">
              <p>Core profiling data with CPU samples, allocations, locks, and JVM events. Multiple chunk files from continuous profiling.</p>
            </div>
          </div>
          <div class="session-content-card heap">
            <div class="card-header">
              <i class="bi bi-memory"></i>
              <h4>Heap Dump</h4>
            </div>
            <div class="card-body">
              <p>Memory snapshot captured on OutOfMemoryError or JVM crash. Compressed with gzip.</p>
            </div>
          </div>
          <div class="session-content-card logs">
            <div class="card-header">
              <i class="bi bi-file-text"></i>
              <h4>JVM Logs</h4>
            </div>
            <div class="card-body">
              <p>Structured JVM diagnostic logs including GC events and JIT compilation activity.</p>
            </div>
          </div>
          <div class="session-content-card perf">
            <div class="card-header">
              <i class="bi bi-speedometer2"></i>
              <h4>Perf Counters</h4>
            </div>
            <div class="card-body">
              <p>JVM performance data with low-level metrics about JVM internals.</p>
            </div>
          </div>
        </div>

        <h3>Session Metadata</h3>
        <p>For each session, Jeffrey tracks:</p>
        <ul>
          <li><strong>Session ID</strong> - Unique identifier for the session</li>
          <li><strong>Start time</strong> - When profiling began</li>
          <li><strong>Duration</strong> - How long the session has been running</li>
          <li><strong>Status</strong> - Active (still recording) or Finished</li>
          <li><strong>File counts</strong> - Number of JFR chunks and artifacts</li>
        </ul>

        <h2 id="workspace-availability">Workspace Availability</h2>
        <p>Recording Sessions are visible through the Repository feature:</p>

        <table>
          <thead>
            <tr>
              <th>Workspace</th>
              <th>Recording Sessions</th>
              <th>Source</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Sandbox</strong></td>
              <td><i class="bi bi-x-lg text-muted"></i> Not available</td>
              <td>Manual file uploads only</td>
            </tr>
            <tr>
              <td><strong>Live</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Local filesystem (auto-detected)</td>
            </tr>
            <tr>
              <td><strong>Remote</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Synced from remote Live workspace</td>
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

/* Session Contents Grid */
.session-contents-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.session-content-card {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.session-content-card .card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  background: #f8fafc;
  color: #495057;
  border-bottom: 1px solid #e2e8f0;
}

.session-content-card .card-header i {
  font-size: 1rem;
}

.session-content-card .card-header h4 {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
}

.session-content-card .card-body {
  padding: 0.75rem;
  background: #fff;
}

.session-content-card .card-body p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.4;
}

.session-content-card.jfr .card-header i {
  color: #5e64ff;
}

.session-content-card.heap .card-header i {
  color: #ef4444;
}

.session-content-card.logs .card-header i {
  color: #10b981;
}

.session-content-card.perf .card-header i {
  color: #f59e0b;
}

@media (max-width: 768px) {
  .session-contents-grid {
    grid-template-columns: 1fr;
  }
}
</style>
