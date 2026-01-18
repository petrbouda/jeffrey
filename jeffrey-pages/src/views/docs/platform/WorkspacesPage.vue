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
  { id: 'workspace-types', text: 'Workspace Types', level: 2 },
  { id: 'sandbox-workspace', text: 'Sandbox Workspace', level: 2 },
  { id: 'live-workspace', text: 'Live Workspace', level: 2 },
  { id: 'remote-workspace', text: 'Remote Workspace', level: 2 },
  { id: 'recommended-workflows', text: 'Recommended Workflows', level: 2 },
  { id: 'comparison', text: 'Comparison', level: 2 }
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
        <p>Workspaces are the top-level organizational unit in Jeffrey, helping you group related profiling projects together. Jeffrey provides three types of workspaces, each designed for different use cases.</p>

        <h2 id="what-are-workspaces">What are Workspaces?</h2>
        <p>A workspace is a container for one or more projects. You might create separate workspaces for:</p>
        <ul>
          <li>Different applications or services</li>
          <li>Different teams within your organization</li>
          <li>Different environments (development, staging, production)</li>
        </ul>

        <h2 id="workspace-types">Workspace Types</h2>
        <p>Jeffrey offers three workspace types, each optimized for specific scenarios:</p>

        <div class="workspace-types-grid">
          <div class="workspace-type-card sandbox">
            <div class="type-icon"><i class="bi bi-house"></i></div>
            <h4>Sandbox</h4>
            <p>Local experimentation and investigation</p>
          </div>
          <div class="workspace-type-card live">
            <div class="type-icon"><i class="bi bi-folder"></i></div>
            <h4>Live</h4>
            <p>Server-side recording collection</p>
          </div>
          <div class="workspace-type-card remote">
            <div class="type-icon"><i class="bi bi-display"></i></div>
            <h4>Remote</h4>
            <p>Access recordings from remote servers</p>
          </div>
        </div>

        <h2 id="sandbox-workspace">Sandbox Workspace</h2>
        <p>The Sandbox workspace is designed for <strong>local experimentation and investigation</strong>. It's the simplest way to start analyzing JFR recordings on your machine.</p>

        <h3>Typical Workflow</h3>
        <ol>
          <li>Have a JFR file locally on your machine</li>
          <li>Upload it to your local Jeffrey instance</li>
          <li>Create a profile and start investigation</li>
        </ol>

        <h3>Use Cases</h3>
        <ul>
          <li><strong>Quick local analysis</strong> - Upload and analyze JFR recordings from your machine</li>
          <li><strong>Local storage for interesting recordings</strong> - Keep recordings categorized in projects for later access</li>
          <li><strong>Experimentation</strong> - Test and explore without affecting other workspaces</li>
          <li><strong>Beginners getting started</strong> - Simple one-click setup to start using Jeffrey</li>
        </ul>

        <h3>How to Create</h3>
        <p>Click the <strong>"Sandbox Workspace"</strong> button on the home page. That's it - your workspace is ready to use.</p>

        <h2 id="live-workspace">Live Workspace</h2>
        <p>The Live workspace is designed for <strong>server-side collection of recordings</strong>. It automatically detects and stores recording sessions from your applications.</p>

        <DocsCallout type="warning">
          <strong>Resource Consideration:</strong> Profile initialization and visualization consume significant resources. Running analysis directly on servers (especially containerized environments) is <strong>not recommended</strong>. Instead, access Live workspaces remotely from your local machine.
        </DocsCallout>

        <h3>Primary Use Case</h3>
        <ul>
          <li>Running on a <strong>server</strong> to automatically collect and store recordings</li>
          <li>Recordings are automatically provided to Jeffrey and accessible in LiveProject</li>
          <li>Intended to be accessed <strong>remotely</strong> from local machines using Remote workspaces</li>
        </ul>

        <h3>Features</h3>
        <ul>
          <li>Automatic session and recording detection</li>
          <li>Profiler settings synchronization</li>
          <li>Workspace event tracking</li>
          <li>Can be accessed remotely by other Jeffrey instances</li>
        </ul>

        <h3>How to Create</h3>
        <p>Click <strong>"Add Live"</strong> and provide a name and optional directory location. Storage defaults to <code>~/.jeffrey/workspaces/</code>.</p>

        <h2 id="remote-workspace">Remote Workspace</h2>
        <p>The Remote workspace allows you to <strong>mirror a Live workspace</strong> from a remote Jeffrey server to your local machine. This is the <strong>recommended approach</strong> for analyzing recordings from servers.</p>

        <DocsCallout type="info">
          <strong>Recommended for Live Workspaces:</strong> Instead of running heavy analysis directly on your server, use a Remote workspace to connect from your local machine. This keeps your server lightweight (only collecting recordings) while all CPU-intensive profile processing and visualization happens on your local device with better resources.
        </DocsCallout>

        <h3>How It Works</h3>
        <ol>
          <li>Connect to a remote Jeffrey server via URL</li>
          <li>Mirror a Live workspace to your local Jeffrey</li>
          <li>Browse the same projects and recording sessions locally (in Repository page)</li>
          <li>When you find an interesting recording session:
            <ul>
              <li>Trigger "Merge and Copy" to download</li>
              <li>Jeffrey downloads recording files + artifacts (logs, heap dumps, perf-counters, ...)</li>
              <li>Recording appears in your local Recordings page</li>
            </ul>
          </li>
          <li>Create and analyze profiles <strong>locally</strong> using your machine's resources</li>
        </ol>

        <h3>Key Benefits</h3>
        <ul>
          <li><strong>Resource offloading</strong> - Heavy analysis runs on your local machine, not the server</li>
          <li><strong>Selective download</strong> - Only download recordings you want to investigate</li>
          <li><strong>Full artifacts</strong> - Downloads recordings, logs, heap dumps, perf-counters, and others</li>
          <li><strong>Remote configuration</strong> - Configure Profiler Settings on project level</li>
        </ul>

        <h3>How to Create</h3>
        <p>Click <strong>"Add Remote"</strong>, enter the remote Jeffrey server URL, then select which workspaces to mirror.</p>

        <h2 id="recommended-workflows">Recommended Workflows</h2>

        <h3>Quick Local Analysis (Sandbox)</h3>
        <p><strong>Best for:</strong> Individual developers analyzing local JFR files</p>
        <ol>
          <li>Start Jeffrey locally</li>
          <li>Create a Sandbox Workspace</li>
          <li>Upload your JFR file</li>
          <li>Create Profile and analyze</li>
        </ol>

        <h3>Server + Local Analysis (Live + Remote)</h3>
        <p><strong>Best for:</strong> Teams, CI/CD pipelines, resource-constrained servers</p>

        <div class="workflow-diagram">
          <div class="workflow-box server">
            <h4><i class="bi bi-server me-2"></i>Server</h4>
            <p>Jeffrey + Live Workspace</p>
            <ul>
              <li>Collects recordings</li>
              <li>Stores sessions</li>
              <li>Minimal resources</li>
            </ul>
          </div>
          <div class="workflow-arrow">
            <i class="bi bi-arrow-left-right"></i>
            <span>HTTP</span>
          </div>
          <div class="workflow-box local">
            <h4><i class="bi bi-laptop me-2"></i>Local Machine</h4>
            <p>Jeffrey + Remote Workspace</p>
            <ul>
              <li>Mirror Live workspace</li>
              <li>Browse Repository</li>
              <li>Download interesting recordings</li>
              <li>Analyze locally</li>
            </ul>
          </div>
        </div>

        <DocsCallout type="tip">
          <strong>Why this workflow?</strong> The server only stores recordings (low resource usage), while heavy analysis like flamegraph generation runs on your local machine. You download only the recordings you need to investigate, including all artifacts.
        </DocsCallout>

        <h2 id="comparison">Comparison</h2>
        <table>
          <thead>
            <tr>
              <th>Feature</th>
              <th>Sandbox</th>
              <th>Live</th>
              <th>Remote</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Best For</td>
              <td>Local experimentation</td>
              <td>Server-side collection</td>
              <td>Analyzing remote recordings</td>
            </tr>
            <tr>
              <td>Storage</td>
              <td>Local database<br><small class="text-muted">only for materialized recordings</small></td>
              <td>Database on Server</td>
              <td>Local database<br><small class="text-muted">only for materialized recordings</small></td>
            </tr>
            <tr>
              <td>Create Projects</td>
              <td>Yes</td>
              <td>Yes</td>
              <td>No (read-only)</td>
            </tr>
            <tr>
              <td>Network Required</td>
              <td>No</td>
              <td>No</td>
              <td>Yes</td>
            </tr>
            <tr>
              <td>Resource Usage<br><small class="text-muted">profile initialization & visualization</small></td>
              <td>Local</td>
              <td>Server (not recommended)</td>
              <td>Local (recommended)</td>
            </tr>
          </tbody>
        </table>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';

/* Workspace Types Grid */
.workspace-types-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
  margin: 1.5rem 0;
}

.workspace-type-card {
  padding: 1.25rem;
  border-radius: 8px;
  text-align: center;
  border: 1px solid #e2e8f0;
}

.workspace-type-card .type-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 0.75rem;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.5rem;
  color: #fff;
}

.workspace-type-card h4 {
  margin: 0 0 0.5rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
}

.workspace-type-card p {
  margin: 0;
  font-size: 0.85rem;
  color: #6c757d;
}

/* Card color themes */
.workspace-type-card.sandbox {
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.08) 0%, rgba(245, 158, 11, 0.03) 100%);
  border-color: rgba(245, 158, 11, 0.3);
}

.workspace-type-card.sandbox .type-icon {
  background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
}

.workspace-type-card.live {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.08) 0%, rgba(94, 100, 255, 0.03) 100%);
  border-color: rgba(94, 100, 255, 0.3);
}

.workspace-type-card.live .type-icon {
  background: linear-gradient(135deg, #5e64ff 0%, #4338ca 100%);
}

.workspace-type-card.remote {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.08) 0%, rgba(16, 185, 129, 0.03) 100%);
  border-color: rgba(16, 185, 129, 0.3);
}

.workspace-type-card.remote .type-icon {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
}

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
  .workspace-types-grid {
    grid-template-columns: 1fr;
  }

  .workflow-diagram {
    flex-direction: column;
  }

  .workflow-arrow {
    transform: rotate(90deg);
    padding: 0.5rem 0;
  }
}
</style>
