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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'start-jeffrey', text: 'Start Jeffrey', level: 2 },
  { id: 'create-workspace', text: 'Create a Sandbox Workspace', level: 2 },
  { id: 'upload-recording', text: 'Upload a Recording', level: 2 },
  { id: 'analyze-profile', text: 'Analyze Your Profile', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Quick Start"
        icon="bi bi-lightning"
      />

      <div class="docs-content">
        <p>Get up and running with Jeffrey in just a few minutes. This guide walks you through analyzing your first JFR recording.</p>

        <DocsCallout type="tip">
          <strong>Don't have a JFR recording yet?</strong> Try our <router-link to="/tour-with-examples">Tour with Examples</router-link> page which comes with pre-loaded recordings so you can explore Jeffrey's features right away.
        </DocsCallout>

        <h2 id="start-jeffrey">Start Jeffrey</h2>
        <p>Launch Jeffrey using Docker:</p>

        <DocsCodeBlock
          language="bash"
          code="docker run -it --network host petrbouda/jeffrey"
        />

        <p>Then open <a href="http://localhost:8080" target="_blank">http://localhost:8080</a> in your browser.</p>

        <h2 id="create-workspace">Create a Sandbox Workspace</h2>
        <p>The Sandbox Workspace is designed for local experimentation and investigation. To create one:</p>
        <ol>
          <li>Click "Sandbox Workspace" on the home page</li>
          <li>This creates a workspace for your local projects and recordings</li>
          <li>Create a new project within the sandbox workspace</li>
        </ol>

        <h2 id="upload-recording">Upload a Recording</h2>
        <p>Within your project, upload your JFR recording:</p>
        <ol>
          <li>Navigate to "Recordings" section</li>
          <li>Upload your <code>.jfr</code> file</li>
          <li>Click "Create Profile" to initialize analysis</li>
        </ol>

        <DocsCallout type="info">
          Profile initialization may take a moment depending on the size of your recording.
        </DocsCallout>

        <h2 id="analyze-profile">Analyze Your Profile</h2>
        <p>Once your profile is ready, you can:</p>
        <ul>
          <li>View CPU flamegraphs to identify hot spots</li>
          <li>Analyze memory allocations</li>
          <li>Examine thread activity</li>
          <li>Review JVM events and metrics</li>
        </ul>

        <DocsCallout type="tip">
          Use the sidebar navigation to explore different analysis modes and features.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
