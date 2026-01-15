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
  { id: 'what-are-profiler-settings', text: 'What are Profiler Settings?', level: 2 },
  { id: 'settings-hierarchy', text: 'Settings Hierarchy', level: 2 },
  { id: 'configuration-options', text: 'Configuration Options', level: 2 },
  { id: 'synchronization', text: 'Settings Synchronization', level: 2 },
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
        <span class="breadcrumb-item">Concepts</span>
        <span class="breadcrumb-separator">/</span>
        <router-link to="/docs/concepts/projects" class="breadcrumb-item">Projects</router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Profiler Settings</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-cpu"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Profiler Settings</h1>
          <p class="docs-section-badge">Projects</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Profiler Settings configure the <strong>Jeffrey profiler agent</strong> that runs inside your Java applications, controlling what events are recorded and how.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Profiler Settings are only available in <strong>Live</strong> and <strong>Remote</strong> workspaces. They configure the agent that collects recordings server-side.
        </DocsCallout>

        <h2 id="what-are-profiler-settings">What are Profiler Settings?</h2>
        <p>When you run the Jeffrey profiler agent in your Java application, it needs configuration to know:</p>
        <ul>
          <li>Which JFR events to capture</li>
          <li>How frequently to sample</li>
          <li>What thresholds to use for event recording</li>
          <li>How deep to capture stack traces</li>
        </ul>

        <p>Profiler Settings in Jeffrey allow you to configure these options centrally and have them automatically synchronized to running agents.</p>

        <h2 id="settings-hierarchy">Settings Hierarchy</h2>
        <p>Profiler settings follow a hierarchical inheritance model with three levels:</p>

        <div class="hierarchy-diagram">
          <div class="hierarchy-level global">
            <span class="level-label">Global Settings</span>
            <span class="level-desc">Default for all workspaces and projects</span>
          </div>
          <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
          <div class="hierarchy-level workspace">
            <span class="level-label">Workspace Settings</span>
            <span class="level-desc">Override defaults for a specific workspace</span>
          </div>
          <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
          <div class="hierarchy-level project">
            <span class="level-label">Project Settings</span>
            <span class="level-desc">Override for a specific project (highest priority)</span>
          </div>
        </div>

        <h3>How Inheritance Works</h3>
        <ul>
          <li><strong>Global</strong> provides baseline defaults for everything</li>
          <li><strong>Workspace</strong> can override specific settings for all projects in that workspace</li>
          <li><strong>Project</strong> can override any setting for that specific project</li>
        </ul>

        <DocsCallout type="info">
          <strong>Project-level takes priority:</strong> If you set a value at the project level, it overrides workspace and global settings. This lets you fine-tune profiling for specific applications.
        </DocsCallout>

        <h2 id="configuration-options">Configuration Options</h2>
        <p>Profiler settings include various configuration categories:</p>

        <h3>Event Types</h3>
        <p>Control which JFR events are captured:</p>
        <ul>
          <li><strong>CPU Sampling</strong> - Thread stack sampling for CPU analysis</li>
          <li><strong>Allocation Sampling</strong> - Object allocation tracking</li>
          <li><strong>Lock Contention</strong> - Monitor and lock events</li>
          <li><strong>GC Events</strong> - Garbage collection details</li>
          <li><strong>I/O Events</strong> - File and network I/O</li>
        </ul>

        <h3>Sampling Configuration</h3>
        <table>
          <thead>
            <tr>
              <th>Setting</th>
              <th>Description</th>
              <th>Impact</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Sample interval</td>
              <td>How often to sample threads</td>
              <td>Lower = more detail, higher overhead</td>
            </tr>
            <tr>
              <td>Stack depth</td>
              <td>Maximum stack trace depth</td>
              <td>Deeper = better context, more data</td>
            </tr>
            <tr>
              <td>Allocation threshold</td>
              <td>Minimum allocation size to record</td>
              <td>Lower = more events, more data</td>
            </tr>
          </tbody>
        </table>

        <h3>Thresholds</h3>
        <p>Minimum durations for recording certain events:</p>
        <ul>
          <li><strong>Method tracing threshold</strong> - Minimum method execution time</li>
          <li><strong>Lock threshold</strong> - Minimum lock wait time</li>
          <li><strong>I/O threshold</strong> - Minimum I/O operation time</li>
        </ul>

        <h2 id="synchronization">Settings Synchronization</h2>
        <p>When you update profiler settings in Jeffrey, the changes are synchronized to running agents:</p>

        <ol>
          <li>You update settings in the Jeffrey UI</li>
          <li>A scheduled sync job detects the change</li>
          <li>Settings are pushed to connected profiler agents</li>
          <li>Agents apply the new configuration</li>
          <li>New recordings use the updated settings</li>
        </ol>

        <DocsCallout type="tip">
          <strong>Not immediate:</strong> Settings synchronization happens on a schedule, so there may be a short delay before agents pick up changes.
        </DocsCallout>

        <h2 id="workspace-availability">Workspace Availability</h2>
        <p>Profiler Settings availability depends on workspace type:</p>

        <table>
          <thead>
            <tr>
              <th>Workspace</th>
              <th>Profiler Settings</th>
              <th>Reason</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Sandbox</strong></td>
              <td><i class="bi bi-x-lg text-muted"></i> Not available</td>
              <td>No agent connection - manual uploads only</td>
            </tr>
            <tr>
              <td><strong>Live</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Configures local profiler agents</td>
            </tr>
            <tr>
              <td><strong>Remote</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Configures agents on remote server</td>
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

/* Hierarchy Diagram */
.hierarchy-diagram {
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

.hierarchy-level {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.75rem 2rem;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
  min-width: 280px;
}

.hierarchy-level .level-label {
  font-weight: 600;
  font-size: 0.9rem;
  color: #343a40;
}

.hierarchy-level .level-desc {
  font-size: 0.75rem;
  color: #6c757d;
  margin-top: 0.25rem;
  text-align: center;
}

.hierarchy-level.global {
  border-color: rgba(107, 114, 128, 0.3);
}

.hierarchy-level.global .level-label {
  color: #6b7280;
}

.hierarchy-level.workspace {
  border-color: rgba(94, 100, 255, 0.3);
}

.hierarchy-level.workspace .level-label {
  color: #5e64ff;
}

.hierarchy-level.project {
  border-color: rgba(16, 185, 129, 0.3);
}

.hierarchy-level.project .level-label {
  color: #10b981;
}

.hierarchy-arrow {
  color: #94a3b8;
  font-size: 1.25rem;
}
</style>
