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
  { id: 'profiles-in-projects', text: 'Profiles in Projects', level: 2 },
  { id: 'where-profiles-come-from', text: 'Where Profiles Come From', level: 2 },
  { id: 'profile-creation-process', text: 'Profile Creation Process', level: 2 },
  { id: 'profile-initialization', text: 'Profile Initialization', level: 2 }
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
        <span class="breadcrumb-item active">Profiles</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-file-earmark-text"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Profiles</h1>
          <p class="docs-section-badge">Projects</p>
        </div>
      </header>

      <div class="docs-content">
        <p>The Profiles section within a project shows all <strong>analyzed recordings</strong> ready for investigation. This page explains how profiles are created and managed within the context of a project.</p>

        <DocsCallout type="info">
          For comprehensive information about what profiles contain and the analysis features they provide, see <router-link to="/docs/concepts/profiles">Profiles</router-link>.
        </DocsCallout>

        <h2 id="profiles-in-projects">Profiles in Projects</h2>
        <p>Each project maintains its own list of profiles. Profiles are always associated with a specific project and cannot be shared between projects. This separation ensures:</p>
        <ul>
          <li>Clear organization of analysis work</li>
          <li>Profiles stay close to their source recordings</li>
          <li>Project-level comparisons are straightforward</li>
        </ul>

        <DocsCallout type="tip">
          <strong>Available in all workspaces:</strong> Profiles functionality is identical across Sandbox, Live, and Remote workspaces. There are no differences - you can create, analyze, compare, and manage profiles the same way regardless of workspace type.
        </DocsCallout>

        <h2 id="where-profiles-come-from">Where Profiles Come From</h2>
        <p>Profiles are created from recordings within the same project. The source recordings can come from different places depending on your workspace type:</p>

        <div class="workspace-panels">
          <div class="workspace-panel sandbox">
            <div class="panel-header">
              <i class="bi bi-house"></i>
              <h4>Sandbox Workspace</h4>
            </div>
            <div class="panel-content">
              <p>Recordings come from <strong>manual uploads</strong>:</p>
              <ol>
                <li>Upload a JFR file to Recordings</li>
                <li>Create a profile from that recording</li>
              </ol>
            </div>
          </div>
          <div class="workspace-panel live-remote">
            <div class="panel-header">
              <i class="bi bi-folder"></i>
              <h4>Live / Remote Workspace</h4>
            </div>
            <div class="panel-content">
              <p>Recordings can come from <strong>multiple sources</strong>:</p>
              <ul>
                <li><strong>Manual uploads</strong> - Same as Sandbox</li>
                <li><strong>Repository</strong> - Recording sessions from live applications</li>
              </ul>
              <p class="sub-steps">From Repository: Browse sessions → Merge and Copy → Recording appears → Create profile</p>
            </div>
          </div>
        </div>

        <h2 id="profile-creation-process">Profile Creation Process</h2>
        <p>To create a profile from a recording:</p>

        <div class="process-steps">
          <div class="process-step">
            <div class="step-number">1</div>
            <div class="step-content">
              <strong>Navigate to Recordings</strong>
              <p>Go to your project's Recordings section and find the JFR file you want to analyze.</p>
            </div>
          </div>
          <div class="process-step">
            <div class="step-number">2</div>
            <div class="step-content">
              <strong>Click "Create Profile"</strong>
              <p>Select the recording and click the Create Profile button to start initialization.</p>
            </div>
          </div>
          <div class="process-step">
            <div class="step-number">3</div>
            <div class="step-content">
              <strong>Wait for Initialization</strong>
              <p>Jeffrey parses the JFR file, extracts events, and builds the profile database.</p>
            </div>
          </div>
          <div class="process-step">
            <div class="step-number">4</div>
            <div class="step-content">
              <strong>Profile Ready</strong>
              <p>The profile appears in the Profiles section, ready for analysis.</p>
            </div>
          </div>
        </div>

        <h2 id="profile-initialization">Profile Initialization</h2>
        <p>Profile initialization is the process of converting a raw JFR recording into an analyzable profile. Understanding this process helps set expectations:</p>

        <h3>What Happens During Initialization</h3>
        <ol>
          <li><strong>JFR Parsing</strong> - Jeffrey reads and decodes all events from the JFR file</li>
          <li><strong>Event Storage</strong> - Events are stored in a DuckDB database with proper indexing</li>
          <li><strong>Cache Generation</strong> - Pre-computed data structures are built for quicker access:
            <ul>
              <li>Thread statistics and timeline data</li>
              <li>Guardian analysis results</li>
            </ul>
          </li>
          <li><strong>Metadata Extraction</strong> - JVM info, event types, and time ranges are recorded</li>
        </ol>

        <h3>Initialization Time</h3>
        <p>Initialization time depends on several factors:</p>

        <table>
          <thead>
            <tr>
              <th>Factor</th>
              <th>Impact</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Recording size</td>
              <td>Larger files take longer to parse</td>
            </tr>
            <tr>
              <td>Event count</td>
              <td>More events = more processing</td>
            </tr>
            <tr>
              <td>Event types</td>
              <td>Stack-based events (CPU, allocation) require more processing</td>
            </tr>
            <tr>
              <td>System resources</td>
              <td>CPU and memory affect speed</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="warning">
          <strong>Resource Usage:</strong> Profile initialization is CPU and memory intensive. This is why using Remote workspaces to analyze Live workspace recordings is recommended - initialization runs on your local machine instead of the server.
        </DocsCallout>

        <h3>Profile Status</h3>
        <p>During and after initialization, profiles have a status:</p>
        <ul>
          <li><strong>Initializing</strong> - Profile is being created (progress shown)</li>
          <li><strong>Ready</strong> - Profile is complete and available for analysis</li>
          <li><strong>Failed</strong> - Initialization encountered an error</li>
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

/* Workspace Panels */
.workspace-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin: 1.5rem 0;
}

.workspace-panel {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
}

.workspace-panel .panel-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  color: #fff;
}

.workspace-panel .panel-header i {
  font-size: 1.1rem;
}

.workspace-panel .panel-header h4 {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
}

.workspace-panel .panel-content {
  padding: 1rem;
  background: #fff;
}

.workspace-panel .panel-content p {
  margin: 0 0 0.75rem 0;
  font-size: 0.9rem;
  color: #495057;
}

.workspace-panel .panel-content ol,
.workspace-panel .panel-content ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.85rem;
}

.workspace-panel .panel-content li {
  margin-bottom: 0.25rem;
  color: #495057;
}

.workspace-panel .panel-content .sub-steps {
  margin-top: 0.75rem;
  margin-bottom: 0;
  padding: 0.5rem 0.75rem;
  background: #f8fafc;
  border-radius: 4px;
  font-size: 0.8rem;
  color: #6c757d;
}

/* Sandbox panel theme */
.workspace-panel.sandbox .panel-header {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.workspace-panel.sandbox {
  border-color: rgba(245, 158, 11, 0.3);
}

/* Live/Remote panel theme */
.workspace-panel.live-remote .panel-header {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
}

.workspace-panel.live-remote {
  border-color: rgba(94, 100, 255, 0.3);
}

@media (max-width: 768px) {
  .workspace-panels {
    grid-template-columns: 1fr;
  }
}

/* Process Steps */
.process-steps {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.5rem 0;
}

.process-step {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
}

.step-number {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.9rem;
}

.step-content {
  flex: 1;
  padding-top: 0.25rem;
}

.step-content strong {
  display: block;
  color: #343a40;
  margin-bottom: 0.25rem;
}

.step-content p {
  margin: 0;
  font-size: 0.9rem;
  color: #5e6e82;
}

/* Nested list spacing */
ol > li > ul {
  margin-top: 0.5rem;
}
</style>
