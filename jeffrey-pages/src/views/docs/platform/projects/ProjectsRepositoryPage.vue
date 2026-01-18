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
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';

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
      <DocsPageHeader
        title="Repository"
        icon="bi bi-folder"
      />

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

        <DocsLinkCard
          to="/docs/concepts/recording-sessions/overview"
          icon="bi bi-collection-play"
          title="Recording Sessions"
          description="Learn about session contents (JFR files, heap dumps, logs), lifecycle states, and how sessions are created by Async-Profiler."
        />

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

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
