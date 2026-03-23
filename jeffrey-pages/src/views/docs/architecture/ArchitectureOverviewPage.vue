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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
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
      <DocsPageHeader
        title="Architecture Overview"
        icon="bi bi-diagram-3"
      />

      <div class="docs-content">
        <p>Jeffrey consists of two applications: <strong>Jeffrey Local</strong> (an analysis tool for visualizing and exploring JFR profiles) and <strong>Jeffrey Server</strong> (a recording collection service that manages workspaces, sessions, and live recordings). Jeffrey Local can also operate standalone in <strong>Sandbox mode</strong> without any server, allowing you to analyze JFR files directly without setting up the server component.</p>

        <h2 id="technology-stack">Technology Stack</h2>

        <div class="docs-grid docs-grid-2">
          <div class="docs-card tech-card backend">
            <div class="docs-card-header">
              <i class="bi bi-server"></i>
              <h4>Backend</h4>
            </div>
            <div class="docs-card-body">
              <ul>
                <li><strong>Java 25</strong> - Modern Java with latest features</li>
                <li><strong>Spring Boot</strong> - Application framework</li>
                <li><strong>Jersey</strong> - JAX-RS REST API implementation</li>
                <li><strong>Maven</strong> - Build tool and dependency management</li>
              </ul>
            </div>
          </div>
          <div class="docs-card tech-card frontend">
            <div class="docs-card-header">
              <i class="bi bi-window"></i>
              <h4>Frontend</h4>
            </div>
            <div class="docs-card-body">
              <ul>
                <li><strong>Vue 3</strong> - Reactive framework with Composition API</li>
                <li><strong>TypeScript</strong> - Type-safe JavaScript</li>
                <li><strong>Vite</strong> - Build tool and dev server</li>
                <li><strong>ApexCharts</strong> - Data visualization</li>
              </ul>
            </div>
          </div>
          <div class="docs-card tech-card database">
            <div class="docs-card-header">
              <i class="bi bi-database"></i>
              <h4>Database</h4>
            </div>
            <div class="docs-card-body">
              <ul>
                <li><strong>DuckDB</strong> - In-process analytical database</li>
                <li><strong>No external DB</strong> - Zero database servers to deploy</li>
                <li><strong>File-based</strong> - Data stored in local files</li>
                <li><strong>SQL interface</strong> - Standard SQL queries</li>
              </ul>
            </div>
          </div>
          <div class="docs-card tech-card deployment">
            <div class="docs-card-header">
              <i class="bi bi-box-seam"></i>
              <h4>Deployment</h4>
            </div>
            <div class="docs-card-body">
              <ul>
                <li><strong>Two JARs</strong> - <code>jeffrey.jar</code> (Local) and <code>jeffrey-server.jar</code> (Server)</li>
                <li><strong>Docker</strong> - Separate containers for each application</li>
                <li><strong>Kubernetes</strong> - Cloud-native ready</li>
                <li><strong>Filesystem</strong> - Recording storage on disk</li>
              </ul>
            </div>
          </div>
          <div class="docs-card tech-card communication">
            <div class="docs-card-header">
              <i class="bi bi-arrows-angle-expand"></i>
              <h4>Communication</h4>
            </div>
            <div class="docs-card-body">
              <ul>
                <li><strong>gRPC</strong> - Communication between Local and Server</li>
                <li><strong>TLS</strong> - Secure channel support</li>
                <li><strong>Protocol Buffers</strong> - Efficient binary serialization</li>
                <li><strong>Bidirectional</strong> - Streaming and unary calls</li>
              </ul>
            </div>
          </div>
        </div>

        <h2 id="high-level-architecture">High-Level Architecture</h2>
        <p>Jeffrey runs as two separate applications. Jeffrey Server collects and stores recordings, while Jeffrey Local provides the analysis UI and connects to the server via gRPC:</p>

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
          <div class="diagram-row apps-row">
            <div class="diagram-box container">
              <div class="container-title">Jeffrey Local</div>
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
                <div class="component grpc-client">
                  <i class="bi bi-arrows-angle-expand"></i>
                  <span>gRPC Client</span>
                </div>
              </div>
            </div>
            <div class="diagram-connector">
              <i class="bi bi-arrow-left-right"></i>
              <span>gRPC</span>
            </div>
            <div class="diagram-box container">
              <div class="container-title server">Jeffrey Server</div>
              <div class="container-content">
                <div class="component api">
                  <i class="bi bi-gear"></i>
                  <span>REST API</span>
                  <small>/api/*</small>
                </div>
                <div class="component grpc-server">
                  <i class="bi bi-hdd-rack"></i>
                  <span>gRPC Server</span>
                  <small>port 9090</small>
                </div>
              </div>
            </div>
          </div>
          <div class="diagram-row apps-row">
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
            <div class="diagram-connector">
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
        </div>

        <h2 id="domain-architecture">Domain Architecture</h2>
        <p>The backend is organized into two separate applications with clear responsibilities:</p>

        <div class="domain-diagram">
          <div class="domain-row">
            <div class="domain-box platform">
              <div class="domain-header">
                <i class="bi bi-layers"></i>
                <h4>Jeffrey Server</h4>
              </div>
              <div class="domain-body">
                <p>Manages workspace lifecycle, recording collection, and session monitoring:</p>
                <div class="domain-flow">
                  <span class="flow-item">Workspaces</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Projects</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Instances</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Sessions</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Repository</span>
                </div>
                <p class="domain-note">+ Profiler Settings, Messages & Alerts, gRPC Services</p>
              </div>
            </div>

            <div class="domain-connector">
              <i class="bi bi-arrow-left-right"></i>
              <span>gRPC communication</span>
            </div>

            <div class="domain-box profile">
              <div class="domain-header">
                <i class="bi bi-cpu"></i>
                <h4>Jeffrey Local</h4>
              </div>
              <div class="domain-body">
                <p>Profile analysis tool with remote connectivity:</p>
                <div class="domain-flow">
                  <span class="flow-item">Workspaces</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Projects</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Recordings</span>
                  <i class="bi bi-arrow-right"></i>
                  <span class="flow-item">Profiles</span>
                </div>
                <p class="domain-note">+ Remote Workspaces, AI Assistant, Flamegraph, Timeseries, Guardian</p>
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
              <td><code>jeffrey-server/core-server</code></td>
              <td>Server application: gRPC services, workspace/project management</td>
            </tr>
            <tr>
              <td><code>jeffrey-local/core-local</code></td>
              <td>Local application: REST resources, remote workspace connectivity</td>
            </tr>
            <tr>
              <td><code>jeffrey-local/profiles/</code></td>
              <td>All profile analysis modules (flamegraph, timeseries, guardian, etc.)</td>
            </tr>
            <tr>
              <td><code>shared/server-api</code></td>
              <td>gRPC protocol buffer definitions</td>
            </tr>
            <tr>
              <td><code>shared/common</code></td>
              <td>Shared utilities across modules</td>
            </tr>
            <tr>
              <td><code>jeffrey-local/pages-local</code></td>
              <td>Vue.js frontend for Jeffrey Local</td>
            </tr>
            <tr>
              <td><code>jeffrey-server/pages-server</code></td>
              <td>Vue.js frontend for Jeffrey Server</td>
            </tr>
          </tbody>
        </table>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Tech Cards */
.tech-card .docs-card-body ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.85rem;
  color: #5e6e82;
}

.tech-card .docs-card-body li {
  margin-bottom: 0.25rem;
}

.tech-card.backend .docs-card-header i { color: #ef4444; }
.tech-card.frontend .docs-card-header i { color: #10b981; }
.tech-card.database .docs-card-header i { color: #f59e0b; }
.tech-card.deployment .docs-card-header i { color: #5e64ff; }
.tech-card.communication .docs-card-header i { color: #8b5cf6; }

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

.diagram-row.apps-row {
  flex-direction: row;
  gap: 1.5rem;
  align-items: stretch;
  width: 100%;
}

.diagram-row.storage-row {
  flex-direction: row;
  gap: 1rem;
  flex: 1;
  justify-content: center;
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
  flex: 1;
  padding: 0;
  min-width: 220px;
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

.container-title.server {
  background: #8b5cf6;
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

.diagram-connector {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.25rem;
}

.diagram-connector i {
  font-size: 1.5rem;
  color: #8b5cf6;
}

.diagram-connector span {
  font-size: 0.7rem;
  color: #6c757d;
  font-weight: 500;
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

.domain-row {
  display: flex;
  flex-direction: row;
  gap: 1rem;
  align-items: stretch;
}

.domain-box {
  flex: 1;
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
  justify-content: center;
  gap: 0.25rem;
  padding: 0.5rem 0;
  color: #6c757d;
}

.domain-connector i {
  font-size: 1.5rem;
  color: #8b5cf6;
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

  .diagram-row.apps-row {
    flex-direction: column;
  }

  .domain-row {
    flex-direction: column;
  }
}
</style>
