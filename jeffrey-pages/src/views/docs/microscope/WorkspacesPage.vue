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
  { id: 'what-are-workspaces', text: 'What are Workspaces?', level: 2 },
  { id: 'how-it-works', text: 'How It Works', level: 2 },
  { id: 'key-benefits', text: 'Key Benefits', level: 2 },
  { id: 'how-to-create', text: 'How to Create', level: 2 },
  { id: 'streaming', text: 'Streaming Control', level: 2 },
  { id: 'recommended-workflow', text: 'Recommended Workflow', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Workspaces"
        icon="bi bi-folder2-open"
      />

      <div class="docs-content">
        <p>Workspaces are the top-level organizational unit in Jeffrey. They <strong>mirror workspaces from a Jeffrey Server</strong> instance, allowing you to browse projects, download recordings, and analyze profiles locally.</p>

        <h2 id="what-are-workspaces">What are Workspaces?</h2>
        <p>A workspace in Jeffrey Microscope is a remote connection to a workspace managed by Jeffrey Server. It provides access to:</p>
        <ul>
          <li>Projects and their recording sessions collected on the server</li>
          <li>Repository browsing for selecting recordings to download</li>
          <li>Profiler settings configuration for remote projects</li>
          <li>Instance and session monitoring</li>
        </ul>

        <DocsCallout type="info">
          <strong>Local analysis, remote data:</strong> All CPU-intensive profile analysis (flamegraphs, timeseries, thread analysis) runs on your local machine. The server only collects and stores recordings — keeping it lightweight.
        </DocsCallout>

        <h2 id="how-it-works">How It Works</h2>
        <ol>
          <li>Connect to a Jeffrey Server instance via gRPC</li>
          <li>Select a workspace to mirror locally</li>
          <li>Browse projects and recording sessions in the Repository</li>
          <li>When you find an interesting recording session:
            <ul>
              <li>Trigger "Merge and Copy" to download</li>
              <li>Jeffrey downloads recording files + artifacts (logs, heap dumps, perf-counters, ...)</li>
              <li>Recording appears in your local Recordings page</li>
            </ul>
          </li>
          <li>Create and analyze profiles <strong>locally</strong> using your machine's resources</li>
        </ol>

        <h2 id="key-benefits">Key Benefits</h2>
        <ul>
          <li><strong>Resource offloading</strong> — Heavy analysis runs on your local machine, not the server</li>
          <li><strong>Selective download</strong> — Only download recordings you want to investigate</li>
          <li><strong>Full artifacts</strong> — Downloads recordings, logs, heap dumps, perf-counters, and others</li>
          <li><strong>Remote configuration</strong> — Configure Profiler Settings on project level</li>
          <li><strong>Cost savings</strong> — Expensive profile analysis runs locally, not in cloud infrastructure</li>
        </ul>

        <h2 id="how-to-create">How to Create</h2>
        <p>Click <strong>"Add Remote"</strong>, enter the Jeffrey Server address (gRPC endpoint), then select which workspaces to mirror.</p>

        <DocsCallout type="tip">
          <strong>Recordings alternative:</strong> If you just want to analyze a local JFR file without connecting to a server, use <router-link to="/docs/microscope/recordings">Recordings</router-link> instead.
        </DocsCallout>

        <h2 id="streaming">Streaming Control</h2>
        <p>Each workspace has a <strong>JFR streaming</strong> setting that controls whether Jeffrey Server continuously streams JFR data from connected application instances. You can configure this per workspace using the settings popover in the workspace context bar.</p>

        <p>Click the <strong>gear icon</strong> in the workspace header to open the settings popover, then use the segmented toggle to choose a streaming mode:</p>

        <table>
          <thead>
            <tr>
              <th>Mode</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>On</strong></td>
              <td>Streaming is explicitly enabled for this workspace. JFR data is continuously streamed from connected instances.</td>
            </tr>
            <tr>
              <td><strong>Off</strong></td>
              <td>Streaming is explicitly disabled. No JFR data is streamed, regardless of the global setting.</td>
            </tr>
            <tr>
              <td><strong>Inherited</strong></td>
              <td>Uses the global streaming setting configured on the server. This is the default for new workspaces.</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Cost control:</strong> Disabling streaming for workspaces you're not actively investigating reduces resource consumption on both the server and connected application instances.
        </DocsCallout>

        <h2 id="recommended-workflow">Recommended Workflow</h2>
        <p><strong>Best for:</strong> Teams, CI/CD pipelines, production profiling</p>

        <div class="workflow-diagram">
          <div class="workflow-box server">
            <h4><i class="bi bi-server me-2"></i>Server</h4>
            <p>Jeffrey Server</p>
            <ul>
              <li>Collects recordings</li>
              <li>Stores sessions</li>
              <li>Minimal resources</li>
            </ul>
          </div>
          <div class="workflow-arrow">
            <i class="bi bi-arrow-left-right"></i>
            <span>gRPC</span>
          </div>
          <div class="workflow-box microscope">
            <h4><i class="bi bi-laptop me-2"></i>Local Machine</h4>
            <p>Jeffrey Microscope + Remote Workspace</p>
            <ul>
              <li>Mirror workspace</li>
              <li>Browse Repository</li>
              <li>Download interesting recordings</li>
              <li>Analyze locally</li>
            </ul>
          </div>
        </div>

        <DocsCallout type="tip">
          <strong>Why this workflow?</strong> The server only stores recordings (low resource usage), while heavy analysis like flamegraph generation runs on your local machine. You download only the recordings you need to investigate, including all artifacts.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Workflow Diagram */
.workflow-diagram {
  display: flex;
  align-items: stretch;
  gap: 1rem;
  margin: 1.5rem 0;
  padding: 1.5rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.workflow-box {
  flex: 1;
  padding: 1rem;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e2e8f0;
}

.workflow-box h4 {
  margin: 0 0 0.5rem 0;
  font-size: 0.9rem;
  font-weight: 600;
  display: flex;
  align-items: center;
}

.workflow-box p {
  margin: 0 0 0.75rem 0;
  font-size: 0.8rem;
  color: #6c757d;
}

.workflow-box ul {
  margin: 0;
  padding-left: 1.25rem;
  font-size: 0.8rem;
}

.workflow-box ul li {
  margin-bottom: 0.25rem;
  color: #495057;
}

.workflow-box.server {
  border-color: rgba(94, 100, 255, 0.3);
}

.workflow-box.server h4 {
  color: #5e64ff;
}

.workflow-box.local {
  border-color: rgba(16, 185, 129, 0.3);
}

.workflow-box.local h4 {
  color: #10b981;
}

.workflow-arrow {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 0.5rem;
  color: #6c757d;
}

.workflow-arrow i {
  font-size: 1.5rem;
}

.workflow-arrow span {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-top: 0.25rem;
}

/* Responsive */
@media (max-width: 768px) {
  .workflow-diagram {
    flex-direction: column;
  }

  .workflow-arrow {
    transform: rotate(90deg);
    padding: 0.5rem 0;
  }
}
</style>
