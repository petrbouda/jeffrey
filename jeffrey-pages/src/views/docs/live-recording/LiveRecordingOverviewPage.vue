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
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'principle', text: 'The Principle', level: 2 },
  { id: 'architecture', text: 'Architecture Diagram', level: 2 },
  { id: 'jeffrey-cli', text: 'Jeffrey CLI', level: 2 },
  { id: 'profiler-settings-hierarchy', text: 'Profiler Settings Hierarchy', level: 2 },
  { id: 'sessions', text: 'Recording Sessions', level: 2 },
  { id: 'repository-visualization', text: 'Repository Visualization', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Live Recording Overview"
        icon="bi bi-broadcast-pin"
      />

      <div class="docs-content">
        <p>Live Recording enables continuous profiling by sharing a mounted disk between your Java services and Jeffrey. This approach eliminates manual file transfers and provides real-time visibility into application performance.</p>

        <h2 id="principle">The Principle</h2>
        <p>The core concept behind Live Recording is simple: services and Jeffrey share the same mounted storage.</p>

        <div class="docs-grid docs-grid-3">
          <DocsFeatureCard
            icon="bi bi-hdd-network"
            title="Shared Storage"
            description="Services and Jeffrey mount the same storage volume (NFS, PVC in Kubernetes, or local disk)."
            layout="vertical"
          />
          <DocsFeatureCard
            icon="bi bi-arrow-right-circle"
            title="Services Write"
            description="Java applications emit JFR recordings and artifacts to a specified directory structure."
            layout="vertical"
          />
          <DocsFeatureCard
            icon="bi bi-eye"
            title="Jeffrey Reads"
            description="Jeffrey monitors the directories and automatically discovers new recordings for analysis."
            layout="vertical"
          />
        </div>

        <DocsCallout type="tip">
          <strong>No manual transfers:</strong> Since both services and Jeffrey access the same storage, there's no need to manually copy, upload, or transfer JFR files. Analysis can begin as soon as recordings are created.
        </DocsCallout>

        <h2 id="architecture">Architecture Diagram</h2>
        <p>The following diagram illustrates the Live Recording architecture:</p>

        <div class="docs-code-block">
          <pre>┌───────────────────────────────────────────────────────────────────────────┐
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

        <div class="docs-grid docs-grid-2">
          <DocsFeatureCard
            icon="bi bi-file-earmark-binary"
            title="Async-Profiler Library"
            description="Native profiling library (libasyncProfiler.so) for capturing CPU, allocation, and lock events."
            color="orange"
          />
          <DocsFeatureCard
            icon="bi bi-file-earmark-zip"
            title="Jeffrey CLI JAR"
            description="Java library that reads profiler configuration and sets up the JVM arguments for profiling."
            color="orange"
          />
        </div>

        <p>To copy these libraries to shared storage, configure the following properties:</p>

        <div class="docs-code-inline">
          <code>jeffrey.copy-libs.enabled=true<br/>jeffrey.copy-libs.target=${jeffrey.home.dir}/libs</code>
        </div>

        <p>Jeffrey CLI and Async-Profiler can then be referenced from the target directory by your services.</p>

        <DocsCallout type="info">
          <strong>Alternative setup:</strong> If you're not using the official Jeffrey container, you'll need to provide the Async-Profiler library and Jeffrey CLI JAR through other means (e.g., baking them into your service images or mounting them separately).
        </DocsCallout>

        <h3>Environment Variable Configuration</h3>
        <p>Jeffrey CLI generates the final JVM configuration and can output it to one of two environment variables:</p>

        <div class="docs-grid docs-grid-2">
          <div class="docs-card docs-card-subtle">
            <div class="docs-card-body">
              <div class="env-header">
                <code>JEFFREY_PROFILER_CONFIG</code>
                <span class="docs-badge docs-badge-warning">Manual</span>
              </div>
              <p>The configuration value needs to be manually placed on the command-line when starting the JVM.</p>
            </div>
          </div>
          <div class="docs-card docs-card-subtle">
            <div class="docs-card-body">
              <div class="env-header">
                <code>JDK_JAVA_OPTIONS</code>
                <span class="docs-badge docs-badge-success">Automatic</span>
              </div>
              <p>Automatically loaded by the JVM at startup - no manual intervention required.</p>
            </div>
          </div>
        </div>

        <DocsLinkCard
          to="/docs/cli/configuration"
          icon="bi bi-terminal"
          title="Jeffrey CLI Configuration"
          description="Learn about HOCON configuration files, environment variable options, and how to set up profiling for your services."
        />

        <h3>Metadata File</h3>
        <p>For each recording session, Jeffrey CLI creates a JSON metadata file containing:</p>
        <ul>
          <li><strong>Session ID</strong> - Unique identifier for the recording session</li>
          <li><strong>Project information</strong> - Which project this session belongs to</li>
          <li><strong>Timestamp</strong> - When the session started</li>
          <li><strong>Configuration</strong> - Profiler settings used for this session</li>
        </ul>

        <h2 id="profiler-settings-hierarchy">Profiler Settings Hierarchy</h2>
        <p>Jeffrey propagates profiler settings to the shared storage in a <code>.settings</code> folder at each level. Jeffrey CLI loads these settings when initializing a profiling session.</p>

        <div class="hierarchy-diagram">
          <div class="hierarchy-level global">
            <div class="level-header">
              <i class="bi bi-globe"></i>
              <span>Global Settings</span>
              <span class="docs-badge docs-badge-default">Default</span>
            </div>
            <p>Base configuration applied to all workspaces and projects</p>
            <code class="level-path">&lt;jeffrey-home&gt;/.settings/</code>
          </div>
          <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
          <div class="hierarchy-level workspace">
            <div class="level-header">
              <i class="bi bi-folder2"></i>
              <span>Workspace Settings</span>
              <span class="docs-badge docs-badge-primary">Override</span>
            </div>
            <p>Workspace-specific settings that override global defaults</p>
            <code class="level-path">&lt;jeffrey-home&gt;/workspaces/&lt;workspace-id&gt;/.settings/</code>
          </div>
          <div class="hierarchy-arrow"><i class="bi bi-arrow-down"></i></div>
          <div class="hierarchy-level project">
            <div class="level-header">
              <i class="bi bi-folder"></i>
              <span>Project Settings</span>
              <span class="docs-badge docs-badge-primary">Override</span>
            </div>
            <p>Project-specific settings with highest priority</p>
            <code class="level-path">&lt;jeffrey-home&gt;/workspaces/&lt;workspace-id&gt;/&lt;project-name&gt;/.settings/</code>
          </div>
        </div>

        <DocsCallout type="info">
          <strong>Settings inheritance:</strong> Jeffrey CLI merges settings from all levels, with more specific levels overriding parent settings. This allows you to set global defaults while customizing specific workspaces or projects as needed.
        </DocsCallout>

        <h2 id="sessions">Recording Sessions</h2>
        <p>A <strong>session</strong> represents a single application execution. Each time your application starts, a new session folder is created.</p>

        <div class="docs-structure-block">
          <div class="docs-structure-header">
            <i class="bi bi-folder-check"></i>
            <h4>Session Folder Structure</h4>
          </div>
          <div class="docs-structure-body">
            <pre>session-001/
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

        <div class="docs-grid docs-grid-3">
          <DocsFeatureCard
            icon="bi bi-collection"
            title="Session Overview"
            description="View all recording sessions for a project, with status indicators showing active or finished state."
            color="green"
            layout="vertical"
          />
          <DocsFeatureCard
            icon="bi bi-clock-history"
            title="Timeline View"
            description="See when sessions started, their duration, and how many recordings each session contains."
            color="green"
            layout="vertical"
          />
          <DocsFeatureCard
            icon="bi bi-file-earmark-play"
            title="Recording Actions"
            description="Merge selected JFR chunks, download recordings, or create profiles for detailed analysis."
            color="green"
            layout="vertical"
          />
        </div>

        <p>The Repository provides a central place to manage all live recording data, from viewing session status to creating profiles for flame graph analysis.</p>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Environment Header */
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

/* Hierarchy Diagram */
.hierarchy-diagram {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 1.5rem 0;
}

.hierarchy-level {
  width: 100%;
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

.hierarchy-level p {
  margin: 0 0 0.5rem 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

.level-path {
  display: block;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.7rem;
  color: #6b7280;
  background: #f1f5f9;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
}

.hierarchy-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: #5e64ff;
  font-size: 1.25rem;
}

.hierarchy-arrow::after {
  content: 'inherits from';
  font-size: 0.75rem;
  color: #6b7280;
  font-style: italic;
}
</style>
