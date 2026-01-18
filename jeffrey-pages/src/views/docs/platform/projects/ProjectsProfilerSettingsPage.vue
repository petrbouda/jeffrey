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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'what-are-profiler-settings', text: 'What are Profiler Settings?', level: 2 },
  { id: 'settings-builder', text: 'Settings Builder', level: 2 },
  { id: 'settings-hierarchy', text: 'Settings Hierarchy', level: 2 },
  { id: 'workspace-availability', text: 'Workspace Availability', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Profiler Settings"
        icon="bi bi-cpu"
      />

      <div class="docs-content">
        <p>Profiler Settings configure the <strong>Async-Profiler agent</strong> used for <strong>Recording Collection</strong> in Live workspaces, controlling what events are recorded and how profiling is performed.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Profiler Settings are only available in <strong>Live</strong> and <strong>Remote</strong> workspaces. They configure the Async-Profiler agent that collects recordings from running applications.
        </DocsCallout>

        <h2 id="what-are-profiler-settings">What are Profiler Settings?</h2>
        <p>When collecting recordings from Java applications, the profiler agent needs configuration to know:</p>
        <ul>
          <li>Which profiling events to capture (CPU, allocation, locks, etc.)</li>
          <li>How frequently to sample</li>
          <li>What thresholds to use for event recording</li>
          <li>Where to store output files</li>
        </ul>

        <p>Profiler Settings in Jeffrey provide a convenient way to create and manage these configurations. Changes are pushed to application and <strong>applied after restart</strong>.</p>

        <h2 id="settings-builder">Settings Builder</h2>
        <p>Jeffrey uses <a href="https://github.com/async-profiler/async-profiler" target="_blank" rel="noopener">Async-Profiler</a> as its profiling agent and includes a visual <strong>Profiler Settings Builder</strong> to configure it without memorizing command-line arguments. The builder generates the correct profiler parameters based on your selections.</p>

        <DocsCallout type="info">
          <strong>Only Supported Profiler:</strong> Currently, Async-Profiler is the only supported profiling agent in Jeffrey. It provides excellent integration with JFR format and low overhead profiling capabilities.
        </DocsCallout>

        <div class="screenshot-container">
          <img src="/images/docs/profiler-settings-builder.png" alt="Profiler Settings Builder overview" class="doc-screenshot" />
          <p class="screenshot-caption">Profiler Settings Builder with mandatory options, event toggles, and generated parameters</p>
        </div>

        <h3>Mandatory Options</h3>
        <p>Every profiler configuration requires these essential settings:</p>

        <div class="options-list">
          <div class="option-item">
            <div class="option-header">
              <i class="bi bi-folder2-open"></i>
              <strong>Agent Path</strong>
            </div>
            <p>Path to the Async-Profiler native library (libasyncProfiler.so)</p>
          </div>
          <div class="option-item">
            <div class="option-header">
              <i class="bi bi-file-earmark-code"></i>
              <strong>Output File Pattern</strong>
            </div>
            <p>Template for output file names, supporting placeholders for timestamps and process IDs</p>
          </div>
          <div class="option-item">
            <div class="option-header">
              <i class="bi bi-arrow-repeat"></i>
              <strong>Loop Duration</strong>
            </div>
            <p>Duration of each recording cycle before starting a new output file</p>
          </div>
        </div>

        <h3>Event Options</h3>
        <p>Select which profiling events to capture. Each event type can be expanded to configure additional options like sampling intervals, thresholds, and profiling modes.</p>

        <div class="profiler-features">
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-cpu"></i></div>
            <div class="feature-content">
              <h4>CPU Profiling</h4>
              <p>Sample thread stack traces at regular intervals to find CPU-intensive methods and performance bottlenecks</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-memory"></i></div>
            <div class="feature-content">
              <h4>Allocation Profiling</h4>
              <p>Track object allocations above threshold to identify memory pressure sources and allocation hotspots</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-lock"></i></div>
            <div class="feature-content">
              <h4>Lock Profiling</h4>
              <p>Monitor contended locks and synchronization events to debug threading and concurrency issues</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-clock-history"></i></div>
            <div class="feature-content">
              <h4>Wall-Clock Profiling</h4>
              <p>Sample all threads regardless of state to find I/O bottlenecks and blocking operations</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-signpost-split"></i></div>
            <div class="feature-content">
              <h4>Method Tracing</h4>
              <p>Trace specific method invocations for detailed timing analysis of critical code paths</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-hdd-stack"></i></div>
            <div class="feature-content">
              <h4>Native Memory Profiling</h4>
              <p>Track native (off-heap) memory allocations to debug memory usage outside the Java heap</p>
            </div>
          </div>
        </div>

        <div class="screenshot-container">
          <img src="/images/docs/profiler-settings-builder-2.png" alt="Profiler Settings Builder with expanded options" class="doc-screenshot" />
          <p class="screenshot-caption">Expanded CPU and Allocation profiling options with sampling interval and threshold settings</p>
        </div>

        <h3>Advanced Options</h3>
        <p>Fine-tune JFR output format and enable additional features:</p>

        <div class="profiler-features">
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-arrow-down-up"></i></div>
            <div class="feature-content">
              <h4>JFR Synchronization</h4>
              <p>Merge Async-Profiler events with JDK's JFR recording for richer profiling data. Choose from predefined JFC modes (default, profile) or use custom configuration.</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-file-binary"></i></div>
            <div class="feature-content">
              <h4>Chunk Size</h4>
              <p>Maximum size of each JFR chunk file. Smaller chunks enable parallel processing of JFR data. Default is 100 MB.</p>
            </div>
          </div>
          <div class="feature-card">
            <div class="feature-icon"><i class="bi bi-hourglass-split"></i></div>
            <div class="feature-content">
              <h4>Chunk Time</h4>
              <p>Maximum duration of each JFR chunk. A new chunk starts after the specified time. Default is 1 hour.</p>
            </div>
          </div>
        </div>

        <h3>Active Parameters</h3>
        <p>The Settings Builder displays the generated Async-Profiler command arguments in real-time. This helps you understand exactly what configuration will be applied and allows you to copy or modify the parameters if needed.</p>

        <DocsCallout type="tip">
          <strong>Apply After Restart:</strong> Profiler settings changes are written to the application's configuration. The new settings take effect when the application restarts - they are not applied to currently running processes.
        </DocsCallout>

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

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Profiler Features Grid */
.profiler-features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.feature-card {
  display: flex;
  gap: 0.75rem;
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.feature-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 8px;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.feature-icon i {
  font-size: 1.1rem;
  color: #5e64ff;
}

.feature-content {
  flex: 1;
}

.feature-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  color: #343a40;
}

.feature-content p {
  margin: 0;
  font-size: 0.8rem;
  color: #5e6e82;
}

/* Options List */
.options-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 1.5rem 0;
}

.option-item {
  padding: 1rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.option-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.option-header i {
  font-size: 1rem;
  color: #5e64ff;
}

.option-header strong {
  font-size: 0.9rem;
  color: #343a40;
}

.option-item p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  padding-left: 1.5rem;
}

/* Screenshot Styling */
.screenshot-container {
  margin: 1.5rem 0;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.doc-screenshot {
  width: 100%;
  display: block;
}

.screenshot-caption {
  margin: 0;
  padding: 0.75rem 1rem;
  font-size: 0.8rem;
  color: #5e6e82;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  text-align: center;
}

@media (max-width: 992px) {
  .profiler-features {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 576px) {
  .profiler-features {
    grid-template-columns: 1fr;
  }
}

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
