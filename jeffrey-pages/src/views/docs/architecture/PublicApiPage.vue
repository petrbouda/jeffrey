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
  { id: 'purpose', text: 'Purpose', level: 2 },
  { id: 'endpoints', text: 'Endpoints', level: 2 },
  { id: 'use-case', text: 'Use Case', level: 2 }
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
        <span class="breadcrumb-item active">Public API</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-globe"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Public API</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>The Public API provides REST endpoints for <strong>Remote Jeffrey</strong> instances to connect to <strong>Local/Live Jeffrey</strong>. All endpoints are served under <code>/api/public</code>.</p>

        <DocsCallout type="info">
          <strong>Remote Connectivity:</strong> This API is primarily used by Remote workspaces running on developer machines to fetch recordings from Live workspaces running in cloud environments like Kubernetes.
        </DocsCallout>

        <h2 id="purpose">Purpose</h2>
        <p>The Public API serves a specific purpose: enabling Remote workspaces to download recordings for local analysis.</p>

        <div class="purpose-cards">
          <div class="purpose-card">
            <div class="card-icon"><i class="bi bi-eye"></i></div>
            <div class="card-content">
              <h4>Read-Only Focus</h4>
              <p>Minimal write operations - primarily for fetching data from remote instances</p>
            </div>
          </div>
          <div class="purpose-card">
            <div class="card-icon"><i class="bi bi-cloud-download"></i></div>
            <div class="card-content">
              <h4>Recording Downloads</h4>
              <p>Stream JFR recordings and artifacts from Live workspace repository</p>
            </div>
          </div>
          <div class="purpose-card">
            <div class="card-icon"><i class="bi bi-sliders"></i></div>
            <div class="card-content">
              <h4>Settings Sync</h4>
              <p>Fetch profiler settings to maintain consistency between instances</p>
            </div>
          </div>
        </div>

        <h2 id="endpoints">Endpoints</h2>
        <p>All endpoints are served under <code>/api/public</code>.</p>

        <div class="endpoint-groups">
          <!-- Workspaces -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-house"></i>
              <h4>Workspaces</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/workspaces</code>
                </div>
                <p>List all available workspaces (Live workspaces only)</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/workspaces/{id}</code>
                </div>
                <p>Get workspace details</p>
              </div>
            </div>
          </div>

          <!-- Projects -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-folder"></i>
              <h4>Projects</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/workspaces/{id}/projects</code>
                </div>
                <p>List all projects in workspace</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/workspaces/{id}/projects/{projectId}</code>
                </div>
                <p>Get project details</p>
              </div>
            </div>
          </div>

          <!-- Profiler Settings -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-sliders"></i>
              <h4>Profiler Settings</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/.../projects/{id}/profiler/settings</code>
                </div>
                <p>Fetch effective profiler settings</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method post">POST</span>
                  <code>/.../projects/{id}/profiler/settings</code>
                </div>
                <p>Update profiler settings</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method delete">DELETE</span>
                  <code>/.../projects/{id}/profiler/settings</code>
                </div>
                <p>Delete project-level settings override</p>
              </div>
            </div>
          </div>

          <!-- Repository -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-collection"></i>
              <h4>Repository</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/.../repository/statistics</code>
                </div>
                <p>Get repository statistics (session count, size)</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/.../repository/sessions</code>
                </div>
                <p>List all recording sessions</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/.../repository/sessions/{sessionId}</code>
                </div>
                <p>Get single session details</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method post">POST</span>
                  <code>/.../sessions/{sessionId}/recordings</code>
                </div>
                <p>Stream selected JFR recordings for download</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method post">POST</span>
                  <code>/.../sessions/{sessionId}/artifact</code>
                </div>
                <p>Stream single artifact (heap dump, log)</p>
              </div>
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method delete">DELETE</span>
                  <code>/.../repository/sessions/{sessionId}</code>
                </div>
                <p>Delete session from repository</p>
              </div>
            </div>
          </div>

          <!-- Messages -->
          <div class="endpoint-group">
            <div class="group-header">
              <i class="bi bi-chat-left-text"></i>
              <h4>Messages</h4>
            </div>
            <div class="group-body">
              <div class="endpoint-item">
                <div class="endpoint-line">
                  <span class="method get">GET</span>
                  <code>/.../projects/{id}/messages</code>
                </div>
                <p>Get project messages and alerts</p>
              </div>
            </div>
          </div>
        </div>

        <h2 id="use-case">Use Case</h2>
        <p>The typical workflow for Remote workspace connectivity:</p>

        <div class="workflow-diagram">
          <div class="workflow-row">
            <div class="workflow-box local">
              <div class="box-header">
                <i class="bi bi-pc-display"></i>
                <span>Developer Machine</span>
              </div>
              <div class="box-content">
                <strong>Remote Workspace</strong>
                <p>Connects to Live Jeffrey via Public API</p>
              </div>
            </div>
            <div class="workflow-arrow">
              <span>Public API</span>
              <i class="bi bi-arrow-right"></i>
            </div>
            <div class="workflow-box cloud">
              <div class="box-header">
                <i class="bi bi-cloud"></i>
                <span>Kubernetes</span>
              </div>
              <div class="box-content">
                <strong>Live Workspace</strong>
                <p>Collects recordings from running apps</p>
              </div>
            </div>
          </div>
        </div>

        <h3>Benefits</h3>
        <ul>
          <li><strong>Cost Savings</strong> - Expensive profile analysis runs on local machine, not in cloud</li>
          <li><strong>Data Access</strong> - Recordings collected in production are available for local analysis</li>
          <li><strong>Security</strong> - Only read operations exposed, no profile creation or modification</li>
        </ul>
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

/* Purpose Cards */
.purpose-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.purpose-card {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.card-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon i {
  font-size: 1.1rem;
  color: #5e64ff;
}

.card-content {
  flex: 1;
}

.card-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.card-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

/* Endpoint Groups */
.endpoint-groups {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.endpoint-group {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.group-header i {
  font-size: 1rem;
  color: #5e64ff;
}

.group-header h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.group-body {
  background: #fff;
}

.endpoint-item {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
}

.endpoint-item:last-child {
  border-bottom: none;
}

.endpoint-line {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.25rem;
}

.endpoint-line code {
  font-size: 0.8rem;
  color: #374151;
  background: #f1f5f9;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
}

.endpoint-item p {
  margin: 0;
  font-size: 0.8rem;
  color: #6b7280;
  padding-left: 3.5rem;
}

.method {
  display: inline-block;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  min-width: 52px;
  text-align: center;
}

.method.get { background: #d1fae5; color: #065f46; }
.method.post { background: #dbeafe; color: #1e40af; }
.method.put { background: #fef3c7; color: #92400e; }
.method.delete { background: #fee2e2; color: #991b1b; }

/* Workflow Diagram */
.workflow-diagram {
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.workflow-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
}

.workflow-box {
  min-width: 200px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.box-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.workflow-box.local .box-header i { color: #10b981; }
.workflow-box.cloud .box-header i { color: #5e64ff; }

.box-header span {
  font-size: 0.8rem;
  color: #5e6e82;
}

.box-content {
  padding: 1rem;
}

.box-content strong {
  display: block;
  font-size: 0.9rem;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.box-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

.workflow-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  color: #6c757d;
}

.workflow-arrow span {
  font-size: 0.7rem;
}

.workflow-arrow i {
  font-size: 1.25rem;
}

@media (max-width: 768px) {
  .purpose-cards {
    grid-template-columns: 1fr;
  }

  .workflow-row {
    flex-direction: column;
  }

  .workflow-arrow {
    transform: rotate(90deg);
  }
}
</style>
