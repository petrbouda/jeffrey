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
  { id: 'principle', text: 'The Principle', level: 2 },
  { id: 'architecture', text: 'Architecture Diagram', level: 2 },
  { id: 'jeffrey-cli', text: 'Jeffrey CLI', level: 2 },
  { id: 'configuration-hierarchy', text: 'Configuration Hierarchy', level: 2 },
  { id: 'sessions', text: 'Recording Sessions', level: 2 },
  { id: 'repository-visualization', text: 'Repository Visualization', level: 2 }
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
        <span class="breadcrumb-item">Live Recording</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Overview</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-broadcast-pin"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Overview</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Live Recording enables continuous profiling by sharing a mounted disk between your Java services and Jeffrey. This approach eliminates manual file transfers and provides real-time visibility into application performance.</p>

        <h2 id="principle">The Principle</h2>
        <p>The core concept behind Live Recording is simple: services and Jeffrey share the same mounted storage.</p>

        <div class="principle-cards">
          <div class="principle-card">
            <div class="principle-icon"><i class="bi bi-hdd-network"></i></div>
            <div class="principle-content">
              <h4>Shared Storage</h4>
              <p>Services and Jeffrey mount the same storage volume (NFS, PVC in Kubernetes, or local disk).</p>
            </div>
          </div>
          <div class="principle-card">
            <div class="principle-icon"><i class="bi bi-arrow-right-circle"></i></div>
            <div class="principle-content">
              <h4>Services Write</h4>
              <p>Java applications emit JFR recordings and artifacts to a specified directory structure.</p>
            </div>
          </div>
          <div class="principle-card">
            <div class="principle-icon"><i class="bi bi-eye"></i></div>
            <div class="principle-content">
              <h4>Jeffrey Reads</h4>
              <p>Jeffrey monitors the directories and automatically discovers new recordings for analysis.</p>
            </div>
          </div>
        </div>

        <DocsCallout type="tip">
          <strong>No manual transfers:</strong> Since both services and Jeffrey access the same storage, there's no need to manually copy, upload, or transfer JFR files. Analysis can begin as soon as recordings are created.
        </DocsCallout>

        <h2 id="architecture">Architecture Diagram</h2>
        <p>The following diagram illustrates the Live Recording architecture:</p>

        <div class="architecture-diagram">
          <pre class="diagram-ascii">
┌───────────────────────────────────────────────────────────────────────────┐
│                           SHARED MOUNTED STORAGE                          │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  /recordings                                                        │  │
│  │    ├── workspace-1/                                                 │  │
│  │    │     └── project-a/                                             │  │
│  │    │           ├── session-001/  (recordings, artifacts, metadata)  │  │
│  │    │           └── session-002/                                     │  │
│  │    └── profiler-settings.json                                       │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
          ▲                                           ▲
          │ writes                                    │ reads
          │                                           │
┌─────────┴─────────┐                        ┌────────┴────────┐
│    SERVICES       │                        │     JEFFREY     │
│  (Java Apps)      │                        │                 │
│                   │                        │  - Watches dirs │
│  - Jeffrey CLI    │                        │  - Parses JFR   │
│  - Async-Profiler │                        │  - Visualizes   │
└───────────────────┘                        └─────────────────┘</pre>
        </div>

        <h2 id="jeffrey-cli">Jeffrey CLI</h2>
        <p>Jeffrey CLI is a lightweight Java library that configures your services for Live Recording. Jeffrey can provide the necessary components on the shared disk for services to use.</p>

        <h3>Provided Components</h3>
        <p>The official Jeffrey container includes these components that can be copied to shared storage:</p>

        <div class="component-grid">
          <div class="component-card">
            <div class="component-icon"><i class="bi bi-file-earmark-binary"></i></div>
            <div class="component-text">
              <h4>Async-Profiler Library</h4>
              <p>Native profiling library (<code>libasyncProfiler.so</code>) for capturing CPU, allocation, and lock events.</p>
            </div>
          </div>
          <div class="component-card">
            <div class="component-icon"><i class="bi bi-file-earmark-zip"></i></div>
            <div class="component-text">
              <h4>Jeffrey CLI JAR</h4>
              <p>Java library that reads profiler configuration and sets up the JVM arguments for profiling.</p>
            </div>
          </div>
        </div>

        <p>To copy these libraries to shared storage, configure the following properties:</p>

        <div class="code-block">
          <code>jeffrey.copy-libs.enabled=true<br/>jeffrey.copy-libs.target=${jeffrey.home.dir}/libs</code>
        </div>

        <p>Jeffrey CLI and Async-Profiler can then be referenced from the target directory by your services.</p>

        <DocsCallout type="info">
          <strong>Alternative setup:</strong> If you're not using the official Jeffrey container, you'll need to provide the Async-Profiler library and Jeffrey CLI JAR through other means (e.g., baking them into your service images or mounting them separately).
        </DocsCallout>

        <h3>Environment Variable Configuration</h3>
        <p>Jeffrey CLI generates the final JVM configuration and can output it to one of two environment variables:</p>

        <div class="env-options">
          <div class="env-option">
            <div class="env-header">
              <code>JEFFREY_PROFILER_CONFIG</code>
              <span class="env-badge manual">Manual</span>
            </div>
            <p>The configuration value needs to be manually placed on the command-line when starting the JVM.</p>
          </div>
          <div class="env-option">
            <div class="env-header">
              <code>JDK_JAVA_OPTIONS</code>
              <span class="env-badge auto">Automatic</span>
            </div>
            <p>Automatically loaded by the JVM at startup - no manual intervention required.</p>
          </div>
        </div>

        <router-link to="/docs/cli/configuration" class="config-link-card">
          <div class="config-link-icon">
            <i class="bi bi-terminal"></i>
          </div>
          <div class="config-link-content">
            <h4>Jeffrey CLI Configuration</h4>
            <p>Learn about HOCON configuration files, environment variable options, and how to set up profiling for your services.</p>
          </div>
          <i class="bi bi-arrow-right config-link-arrow"></i>
        </router-link>

        <h3>Metadata File</h3>
        <p>For each recording session, Jeffrey CLI creates a JSON metadata file containing:</p>
        <ul>
          <li><strong>Session ID</strong> - Unique identifier for the recording session</li>
          <li><strong>Project information</strong> - Which project this session belongs to</li>
          <li><strong>Timestamp</strong> - When the session started</li>
          <li><strong>Configuration</strong> - Profiler settings used for this session</li>
        </ul>

        <h2 id="configuration-hierarchy">Configuration Hierarchy</h2>
        <p>Jeffrey propagates profiler configuration through a folder hierarchy. Jeffrey CLI loads configuration from this structure, allowing different settings at each level.</p>

        <div class="hierarchy-diagram">
          <div class="hierarchy-level global">
            <div class="level-header">
              <i class="bi bi-globe"></i>
              <span>Global Settings</span>
              <span class="level-badge default">Default</span>
            </div>
            <p>Base configuration applied to all workspaces and projects</p>
          </div>
          <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
          <div class="hierarchy-level workspace">
            <div class="level-header">
              <i class="bi bi-folder2"></i>
              <span>Workspace Settings</span>
              <span class="level-badge override">Override</span>
            </div>
            <p>Workspace-specific settings that override global defaults</p>
          </div>
          <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
          <div class="hierarchy-level project">
            <div class="level-header">
              <i class="bi bi-folder"></i>
              <span>Project Settings</span>
              <span class="level-badge override">Override</span>
            </div>
            <p>Project-specific settings with highest priority</p>
          </div>
        </div>

        <DocsCallout type="info">
          <strong>Configuration inheritance:</strong> If no custom configuration exists at a level, settings from the parent level are used. This allows you to set global defaults while customizing specific projects as needed.
        </DocsCallout>

        <h2 id="sessions">Recording Sessions</h2>
        <p>A <strong>session</strong> represents a single application execution. Each time your application starts, a new session folder is created.</p>

        <div class="session-structure">
          <div class="structure-header">
            <i class="bi bi-folder-check"></i>
            <h4>Session Folder Structure</h4>
          </div>
          <div class="structure-content">
            <pre class="structure-tree">session-001/
├── recording-1.jfr      # JFR recording chunks
├── recording-2.jfr
├── recording-3.jfr
├── perf-counters.json   # Performance counters (session end marker)
└── metadata.json        # Session metadata</pre>
          </div>
        </div>

        <p>Key points about sessions:</p>
        <ul>
          <li><strong>Application restart = New session</strong> - Each application restart creates a new session folder</li>
          <li><strong>Multiple JFR chunks</strong> - Long-running applications produce multiple recording files over time</li>
          <li><strong>Session markers</strong> - Artifacts like <code>perf-counters.json</code> indicate when a session has finished</li>
        </ul>

        <DocsCallout type="tip">
          <strong>Session detection:</strong> Jeffrey uses the presence of <code>perf-counters.json</code> to detect when a session has finished. This allows for immediate analysis once profiling stops.
        </DocsCallout>

        <h2 id="repository-visualization">Repository Visualization</h2>
        <p>Jeffrey watches session folders for each project and displays them in the <router-link to="/docs/concepts/projects/repository">Repository</router-link> page.</p>

        <div class="repo-features">
          <div class="repo-feature">
            <div class="feature-icon"><i class="bi bi-collection"></i></div>
            <div class="feature-content">
              <h4>Session Overview</h4>
              <p>View all recording sessions for a project, with status indicators showing active or finished state.</p>
            </div>
          </div>
          <div class="repo-feature">
            <div class="feature-icon"><i class="bi bi-clock-history"></i></div>
            <div class="feature-content">
              <h4>Timeline View</h4>
              <p>See when sessions started, their duration, and how many recordings each session contains.</p>
            </div>
          </div>
          <div class="repo-feature">
            <div class="feature-icon"><i class="bi bi-file-earmark-play"></i></div>
            <div class="feature-content">
              <h4>Recording Actions</h4>
              <p>Merge selected JFR chunks, download recordings, or create profiles for detailed analysis.</p>
            </div>
          </div>
        </div>

        <p>The Repository provides a central place to manage all live recording data, from viewing session status to creating profiles for flame graph analysis.</p>
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

/* Principle Cards */
.principle-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.principle-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.principle-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 12px;
  margin-bottom: 0.75rem;
}

.principle-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.principle-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: #343a40;
}

.principle-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}

/* Architecture Diagram */
.architecture-diagram {
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #1e293b;
  border-radius: 8px;
  overflow-x: auto;
}

.diagram-ascii {
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.75rem;
  line-height: 1.4;
  color: #e2e8f0;
  white-space: pre;
}

/* Component Grid */
.component-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1rem 0 1.5rem 0;
}

.component-card {
  display: flex;
  gap: 1rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.component-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
  border-radius: 10px;
}

.component-icon i {
  font-size: 1.1rem;
  color: #fff;
}

.component-text h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.component-text p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.4;
}

/* Code Block */
.code-block {
  margin: 1rem 0;
  padding: 0.75rem 1rem;
  background: #1e293b;
  border-radius: 6px;
  overflow-x: auto;
}

.code-block code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
  color: #e2e8f0;
}

/* Environment Options */
.env-options {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;
  margin: 1rem 0 1.5rem 0;
}

.env-option {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.env-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.env-header code {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
  font-weight: 600;
  color: #343a40;
}

.env-badge {
  padding: 0.15rem 0.4rem;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  border-radius: 4px;
}

.env-badge.manual {
  background: #fef3c7;
  color: #92400e;
}

.env-badge.auto {
  background: #d1fae5;
  color: #065f46;
}

.env-option p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.4;
}

/* Config Link Card */
.config-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 4px solid #5e64ff;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.config-link-card:hover {
  background: linear-gradient(135deg, rgba(94,100,255,0.08) 0%, rgba(124,58,237,0.08) 100%);
  border-color: #5e64ff;
  transform: translateX(4px);
  text-decoration: none !important;
}

.config-link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.config-link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.config-link-content {
  flex: 1;
}

.config-link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
  text-decoration: none !important;
}

.config-link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
  text-decoration: none !important;
}

.config-link-arrow {
  font-size: 1.25rem;
  color: #5e64ff;
  transition: transform 0.2s ease;
}

.config-link-card:hover .config-link-arrow {
  transform: translateX(4px);
}

/* Hierarchy Diagram */
.hierarchy-diagram {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin: 1.5rem 0;
}

.hierarchy-level {
  width: 100%;
  max-width: 400px;
  padding: 1rem 1.25rem;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.hierarchy-level.global {
  border-left: 3px solid #6b7280;
}

.hierarchy-level.workspace {
  border-left: 3px solid #5e64ff;
}

.hierarchy-level.project {
  border-left: 3px solid #10b981;
}

.level-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.level-header i {
  font-size: 1rem;
  color: #5e6e82;
}

.level-header span:first-of-type {
  font-weight: 600;
  font-size: 0.9rem;
  color: #343a40;
}

.level-badge {
  padding: 0.15rem 0.4rem;
  font-size: 0.65rem;
  font-weight: 600;
  text-transform: uppercase;
  border-radius: 4px;
}

.level-badge.default {
  background: #e5e7eb;
  color: #374151;
}

.level-badge.override {
  background: #dbeafe;
  color: #1d4ed8;
}

.hierarchy-level p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

.hierarchy-arrow {
  color: #9ca3af;
  font-size: 1rem;
}

/* Session Structure */
.session-structure {
  margin: 1.5rem 0;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.structure-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.structure-header i {
  font-size: 1rem;
  color: #10b981;
}

.structure-header h4 {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.structure-content {
  padding: 1rem;
  background: #fff;
}

.structure-tree {
  margin: 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.8rem;
  line-height: 1.6;
  color: #374151;
}

/* Repository Features */
.repo-features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.repo-feature {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 1.25rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.feature-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  border-radius: 10px;
  margin-bottom: 0.75rem;
}

.feature-icon i {
  font-size: 1.1rem;
  color: #fff;
}

.feature-content h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.feature-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
  line-height: 1.4;
}

@media (max-width: 992px) {
  .principle-cards,
  .repo-features {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .component-grid,
  .env-options {
    grid-template-columns: 1fr;
  }

  .hierarchy-level {
    max-width: none;
  }
}
</style>
