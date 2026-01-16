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

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'technology-stack', text: 'Technology Stack', level: 2 },
  { id: 'high-level-architecture', text: 'High-Level Architecture', level: 2 },
  { id: 'domain-architecture', text: 'Domain Architecture', level: 2 }
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
        <span class="breadcrumb-item">Architecture</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Overview</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-diagram-3"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Architecture Overview</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Jeffrey is a <strong>Java application</strong> composed into a single JAR file that contains the backend and also serves the frontend. This design offers an easy way to run the application locally, on a server, or using a single container in the cloud.</p>

        <h2 id="technology-stack">Technology Stack</h2>

        <div class="tech-stack-grid">
          <div class="tech-card backend">
            <div class="card-header">
              <i class="bi bi-server"></i>
              <h4>Backend</h4>
            </div>
            <div class="card-body">
              <ul>
                <li><strong>Java 25</strong> - Modern Java with latest features</li>
                <li><strong>Spring Boot</strong> - Application framework</li>
                <li><strong>Jersey</strong> - JAX-RS REST API implementation</li>
                <li><strong>Maven</strong> - Build tool and dependency management</li>
              </ul>
            </div>
          </div>
          <div class="tech-card frontend">
            <div class="card-header">
              <i class="bi bi-window"></i>
              <h4>Frontend</h4>
            </div>
            <div class="card-body">
              <ul>
                <li><strong>Vue 3</strong> - Reactive framework with Composition API</li>
                <li><strong>TypeScript</strong> - Type-safe JavaScript</li>
                <li><strong>Vite</strong> - Build tool and dev server</li>
                <li><strong>ApexCharts</strong> - Data visualization</li>
              </ul>
            </div>
          </div>
          <div class="tech-card database">
            <div class="card-header">
              <i class="bi bi-database"></i>
              <h4>Database</h4>
            </div>
            <div class="card-body">
              <ul>
                <li><strong>DuckDB</strong> - In-process analytical database</li>
                <li><strong>No external DB</strong> - Zero database servers to deploy</li>
                <li><strong>File-based</strong> - Data stored in local files</li>
                <li><strong>SQL interface</strong> - Standard SQL queries</li>
              </ul>
            </div>
          </div>
          <div class="tech-card deployment">
            <div class="card-header">
              <i class="bi bi-box-seam"></i>
              <h4>Deployment</h4>
            </div>
            <div class="card-body">
              <ul>
                <li><strong>Docker</strong> - Single container deployment</li>
                <li><strong>Kubernetes</strong> - Cloud-native ready</li>
                <li><strong>Filesystem</strong> - Recording storage on disk</li>
                <li><strong>No dependencies</strong> - Self-contained application</li>
              </ul>
            </div>
          </div>
        </div>

        <h2 id="high-level-architecture">High-Level Architecture</h2>
        <p>Jeffrey runs as a single process serving both the REST API and static frontend files:</p>

        <div class="architecture-diagram">
          <div class="diagram-row">
            <div class="diagram-box browser">
              <i class="bi bi-browser-chrome"></i>
              <span>Browser</span>
            </div>
            <div class="diagram-arrow">
              <i class="bi bi-arrow-down"></i>
            </div>
          </div>
          <div class="diagram-row">
            <div class="diagram-box container">
              <div class="container-title">Jeffrey Container</div>
              <div class="container-content">
                <div class="component api">
                  <i class="bi bi-gear"></i>
                  <span>REST API</span>
                  <small>/api/*</small>
                </div>
                <div class="component static">
                  <i class="bi bi-file-code"></i>
                  <span>Static Files</span>
                  <small>Vue SPA</small>
                </div>
              </div>
            </div>
          </div>
          <div class="diagram-row">
            <div class="diagram-arrow">
              <i class="bi bi-arrow-down"></i>
            </div>
          </div>
          <div class="diagram-row storage-row">
            <div class="diagram-box storage">
              <i class="bi bi-database"></i>
              <span>DuckDB</span>
            </div>
            <div class="diagram-box storage">
              <i class="bi bi-folder"></i>
              <span>Filesystem</span>
            </div>
          </div>
        </div>

        <h2 id="domain-architecture">Domain Architecture</h2>
        <p>The backend is organized into two main domains with clear responsibilities:</p>

        <div class="domain-diagram">
          <div class="domain-box platform">
            <div class="domain-header">
              <i class="bi bi-layers"></i>
              <h4>Platform Management</h4>
            </div>
            <div class="domain-body">
              <p>Manages workspace lifecycle and organization:</p>
              <div class="domain-flow">
                <span class="flow-item">Workspaces</span>
                <i class="bi bi-arrow-right"></i>
                <span class="flow-item">Projects</span>
                <i class="bi bi-arrow-right"></i>
                <span class="flow-item">Recordings</span>
                <i class="bi bi-arrow-right"></i>
                <span class="flow-item">Profiles</span>
              </div>
              <p class="domain-note">+ Sessions, Scheduling, Repository</p>
            </div>
          </div>

          <div class="domain-connector">
            <i class="bi bi-arrow-down"></i>
            <span>triggers profile creation</span>
          </div>

          <div class="domain-box profile">
            <div class="domain-header">
              <i class="bi bi-cpu"></i>
              <h4>Profile Domain</h4>
            </div>
            <div class="domain-body">
              <div class="profile-modules">
                <div class="module parser">
                  <h5>Profile Parser</h5>
                  <p>Parses JFR files and stores events to DuckDB. Returns ProfileInfo.</p>
                </div>
                <div class="module-arrow">
                  <i class="bi bi-arrow-right"></i>
                </div>
                <div class="module management">
                  <h5>Profile Management</h5>
                  <p>Analysis features: Flamegraph, Timeseries, Guardian, GC, Threads, etc.</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <h3>Module Structure</h3>
        <table>
          <thead>
            <tr>
              <th>Module</th>
              <th>Purpose</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>platform/platform-management</code></td>
              <td>Workspace, Project, Recording APIs and REST resources</td>
            </tr>
            <tr>
              <td><code>platform/jfr-repository-parser</code></td>
              <td>JFR repository parsing for streaming recordings</td>
            </tr>
            <tr>
              <td><code>profiles/profile-management</code></td>
              <td>Profile analysis features and REST resources</td>
            </tr>
            <tr>
              <td><code>profiles/recording-parser</code></td>
              <td>JFR parsing, event extraction, DuckDB storage</td>
            </tr>
            <tr>
              <td><code>profiles/flamegraph</code></td>
              <td>Flame graph generation</td>
            </tr>
            <tr>
              <td><code>profiles/timeseries</code></td>
              <td>Time series analysis and charts</td>
            </tr>
            <tr>
              <td><code>profiles/profile-guardian</code></td>
              <td>Automated stacktrace analysis (Guardian)</td>
            </tr>
            <tr>
              <td><code>profiles/heap-dump</code></td>
              <td>Heap dump analysis</td>
            </tr>
            <tr>
              <td><code>shared/common</code></td>
              <td>Shared utilities across modules</td>
            </tr>
            <tr>
              <td><code>pages/</code></td>
              <td>Vue.js frontend application</td>
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

/* Tech Stack Grid */
.tech-stack-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.tech-card {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.tech-card .card-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.75rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.tech-card .card-header i {
  font-size: 1rem;
}

.tech-card.backend .card-header i { color: #ef4444; }
.tech-card.frontend .card-header i { color: #10b981; }
.tech-card.database .card-header i { color: #f59e0b; }
.tech-card.deployment .card-header i { color: #5e64ff; }

.tech-card .card-header h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.tech-card .card-body {
  padding: 0.75rem;
  background: #fff;
}

.tech-card .card-body ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.85rem;
  color: #5e6e82;
}

.tech-card .card-body li {
  margin-bottom: 0.25rem;
}

/* Architecture Diagram */
.architecture-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.diagram-row {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.diagram-row.storage-row {
  flex-direction: row;
  gap: 1rem;
}

.diagram-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.75rem 1.5rem;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
}

.diagram-box.browser {
  border-color: #10b981;
}

.diagram-box.browser i {
  color: #10b981;
  font-size: 1.25rem;
}

.diagram-box.container {
  padding: 0;
  min-width: 280px;
}

.container-title {
  padding: 0.5rem 1rem;
  background: #5e64ff;
  color: #fff;
  font-weight: 600;
  font-size: 0.85rem;
  text-align: center;
  border-radius: 7px 7px 0 0;
}

.container-content {
  display: flex;
  gap: 1rem;
  padding: 1rem;
}

.component {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 1rem;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  font-size: 0.8rem;
}

.component i {
  font-size: 1rem;
  color: #5e64ff;
}

.component small {
  font-size: 0.7rem;
  color: #6c757d;
}

.diagram-box.storage {
  min-width: 100px;
}

.diagram-box.storage i {
  font-size: 1.25rem;
  color: #f59e0b;
}

.diagram-arrow {
  color: #6c757d;
  font-size: 1rem;
}

/* Domain Diagram */
.domain-diagram {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 1.5rem 0;
}

.domain-box {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.domain-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.domain-header i {
  font-size: 1rem;
}

.domain-box.platform .domain-header i { color: #5e64ff; }
.domain-box.profile .domain-header i { color: #10b981; }

.domain-header h4 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.domain-body {
  padding: 1rem;
  background: #fff;
}

.domain-body p {
  margin: 0 0 0.75rem 0;
  font-size: 0.85rem;
  color: #5e6e82;
}

.domain-flow {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.flow-item {
  padding: 0.25rem 0.75rem;
  background: #f0f4ff;
  border-radius: 4px;
  font-size: 0.8rem;
  color: #5e64ff;
  font-weight: 500;
}

.domain-flow i {
  color: #6c757d;
  font-size: 0.75rem;
}

.domain-note {
  margin-top: 0.75rem !important;
  font-style: italic;
  color: #6c757d !important;
}

.domain-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 0;
  color: #6c757d;
}

.domain-connector i {
  font-size: 1rem;
}

.domain-connector span {
  font-size: 0.75rem;
  font-style: italic;
}

.profile-modules {
  display: flex;
  align-items: stretch;
  gap: 0.75rem;
}

.module {
  flex: 1;
  padding: 0.75rem;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.module h5 {
  margin: 0 0 0.5rem 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: #343a40;
}

.module p {
  margin: 0 !important;
  font-size: 0.8rem;
}

.module-arrow {
  display: flex;
  align-items: center;
  color: #6c757d;
}

@media (max-width: 768px) {
  .tech-stack-grid {
    grid-template-columns: 1fr;
  }

  .profile-modules {
    flex-direction: column;
  }

  .module-arrow {
    transform: rotate(90deg);
    justify-content: center;
  }

  .container-content {
    flex-direction: column;
  }

  .diagram-row.storage-row {
    flex-direction: column;
  }
}
</style>
