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
  { id: 'what-is-repository', text: 'What is Repository?', level: 2 },
  { id: 'merge-and-copy', text: 'Merge and Copy', level: 2 },
  { id: 'live-vs-remote', text: 'Live vs Remote Repository', level: 2 },
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
        <span class="breadcrumb-item active">Repository</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-folder"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Repository</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>The Repository provides access to <strong>recording sessions</strong> from live applications. It's the bridge between your running applications and Jeffrey's analysis capabilities.</p>

        <DocsCallout type="warning">
          <strong>Workspace Availability:</strong> Repository is only available in <strong>Live</strong> and <strong>Remote</strong> workspaces. Sandbox workspaces use direct file uploads instead.
        </DocsCallout>

        <h2 id="what-is-repository">What is Repository?</h2>
        <p>The Repository is a view of recording sessions collected from running Java applications. Unlike the Recordings section (which shows uploaded files), Repository shows:</p>
        <ul>
          <li>Active and completed recording sessions</li>
          <li>Recording chunks as they're being created</li>
          <li>Associated artifacts (logs, heap dumps, perf-counters)</li>
        </ul>

        <p>Think of Repository as a "staging area" where recordings accumulate before you choose which ones to analyze.</p>

        <router-link to="/docs/concepts/recording-sessions/overview" class="session-link-card">
          <div class="session-link-icon">
            <i class="bi bi-collection-play"></i>
          </div>
          <div class="session-link-content">
            <h4>Recording Sessions</h4>
            <p>Learn about session contents (JFR files, heap dumps, logs), lifecycle states, and how sessions are created by Async-Profiler.</p>
          </div>
          <i class="bi bi-arrow-right session-link-arrow"></i>
        </router-link>

        <h2 id="merge-and-copy">Merge and Copy</h2>
        <p>When you find an interesting session in Repository, use <strong>"Merge and Copy"</strong> to:</p>

        <ol>
          <li><strong>Select specific files</strong> - Choose which JFR chunks to include</li>
          <li><strong>Merge recording chunks</strong> - Combine selected files into a single recording</li>
          <li><strong>Download artifacts</strong> - Copy all associated files (logs, heap dumps, etc.)</li>
          <li><strong>Create local recording</strong> - The merged recording appears in your Recordings section</li>
        </ol>

        <DocsCallout type="info">
          <strong>Selective file merging:</strong> Since JFR files are created in chunks (e.g., every 15 minutes), you can select specific files to analyze particular time periods:
          <ul style="margin: 0.5rem 0 0 0; padding-left: 1.25rem;">
            <li><strong>Startup analysis</strong> - Select first few files to analyze application startup</li>
            <li><strong>Peak hours</strong> - Select files from high-traffic periods</li>
            <li><strong>Comparison</strong> - Create separate recordings from different time periods to compare profiles</li>
          </ul>
        </DocsCallout>

        <h3>After Merge and Copy</h3>
        <p>Once you've merged a session:</p>
        <ul>
          <li>The recording appears in your project's Recordings section</li>
          <li>You can create a profile for analysis</li>
          <li>All artifacts are available locally</li>
          <li>Analysis runs on your local machine (not the server)</li>
        </ul>

        <h2 id="live-vs-remote">Live vs Remote Repository</h2>
        <table>
          <thead>
            <tr>
              <th>Aspect</th>
              <th>Live Workspace</th>
              <th>Remote Workspace</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Data source</td>
              <td>Local filesystem</td>
              <td>Remote Jeffrey server</td>
            </tr>
            <tr>
              <td>Session detection</td>
              <td>Automatic (file watcher)</td>
              <td>Synced from remote</td>
            </tr>
            <tr>
              <td>Merge and Copy</td>
              <td>Local file copy</td>
              <td>Network download</td>
            </tr>
            <tr>
              <td>Use case</td>
              <td>Direct server access</td>
              <td>Remote analysis from local machine</td>
            </tr>
          </tbody>
        </table>

        <h2 id="workspace-availability">Workspace Availability</h2>
        <p>Repository availability depends on workspace type:</p>

        <table>
          <thead>
            <tr>
              <th>Workspace</th>
              <th>Repository</th>
              <th>Reason</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><strong>Sandbox</strong></td>
              <td><i class="bi bi-x-lg text-muted"></i> Not available</td>
              <td>Designed for manual file uploads</td>
            </tr>
            <tr>
              <td><strong>Live</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Shows local recording sessions</td>
            </tr>
            <tr>
              <td><strong>Remote</strong></td>
              <td><i class="bi bi-check-lg text-success"></i> Available</td>
              <td>Mirrors remote Live workspace</td>
            </tr>
          </tbody>
        </table>

        <DocsCallout type="tip">
          <strong>Recommended workflow:</strong> Use a Live workspace on your server to collect recordings, then connect from a Remote workspace on your local machine to download and analyze them. This keeps the server lightweight while you use your local machine's resources for analysis.
        </DocsCallout>
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

/* Session Link Card */
.session-link-card {
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

.session-link-card:hover {
  background: linear-gradient(135deg, rgba(94,100,255,0.08) 0%, rgba(124,58,237,0.08) 100%);
  border-color: #5e64ff;
  transform: translateX(4px);
  text-decoration: none !important;
}

.session-link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.session-link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.session-link-content {
  flex: 1;
}

.session-link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
}

.session-link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}

.session-link-arrow {
  font-size: 1.25rem;
  color: #5e64ff;
  transition: transform 0.2s ease;
}

.session-link-card:hover .session-link-arrow {
  transform: translateX(4px);
}
</style>
