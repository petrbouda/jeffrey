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
  { id: 'how-it-works', text: 'How it Works', level: 2 }
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

        <DocsCallout type="info">
          <strong>Remote Workspaces:</strong> Repository is available in Remote workspaces connected to a jeffrey-server instance. It provides access to recording sessions collected on the server.
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
          to="/docs/server/recording-sessions/overview"
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

        <DocsCallout type="info">
          <strong>Collector-Only Mode:</strong> On the server side (jeffrey-server), collector-only mode is enabled by default. Use a <strong>Remote</strong> workspace to access Merge and Copy and Download features.
        </DocsCallout>

        <h3>After Merge and Copy</h3>
        <p>Once you've merged a session:</p>
        <ul>
          <li>The recording appears in your project's Recordings section</li>
          <li>You can create a profile for analysis</li>
          <li>All artifacts are available locally</li>
          <li>Analysis runs on your local machine (not the server)</li>
        </ul>

        <h2 id="how-it-works">How it Works</h2>
        <p>The Repository in a Remote workspace connects to the jeffrey-server to access recording sessions. Sessions are detected automatically on the server side and synced to your local machine through the Remote workspace connection. When you use Merge and Copy, the recording data is downloaded over the network to your local Recordings section.</p>

        <DocsCallout type="tip">
          <strong>Recommended workflow:</strong> Deploy jeffrey-server to collect recordings from your applications, then connect from a Remote workspace on your local machine to download and analyze them. This keeps the server lightweight while you use your local machine's resources for analysis.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
