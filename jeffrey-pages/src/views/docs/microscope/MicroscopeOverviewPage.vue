<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsFeatureCard from '@/components/docs/DocsFeatureCard.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'key-features', text: 'Key Features', level: 2 },
  { id: 'when-to-use-jeffrey-microscope', text: 'When to Use Jeffrey Microscope', level: 2 },
  { id: 'getting-started', text: 'Getting Started', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey Microscope"
      icon="bi bi-pc-display"
    />

    <div class="docs-content">
      <p>Jeffrey Microscope is the standalone application for analyzing JFR recordings and heap dumps. It runs on your local machine and provides the full analysis UI.</p>

      <h2 id="key-features">Key Features</h2>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-lightning"
          title="Recordings"
          description="Upload and analyze JFR recordings instantly without project setup."
        />
        <DocsFeatureCard
          icon="bi bi-speedometer2"
          title="Profile Analysis"
          description="Flamegraphs, timeseries, thread analysis, GC analysis, and more."
        />
        <DocsFeatureCard
          icon="bi bi-memory"
          title="Heap Dump Analysis"
          description="Analyze heap dumps with class histograms, dominator trees, leak suspects, and OQL queries."
        />
        <DocsFeatureCard
          icon="bi bi-robot"
          title="AI-Powered Insights"
          description="Chat-based analysis with Claude or ChatGPT for both JFR and heap dump data."
        />
        <DocsFeatureCard
          icon="bi bi-cloud-arrow-down"
          title="Remote Workspaces"
          description="Connect to Jeffrey Server to download and analyze recordings from production."
        />
        <DocsFeatureCard
          icon="bi bi-grid-1x2"
          title="Technology Dashboards"
          description="HTTP, JDBC, and gRPC analysis dashboards when using Jeffrey Events."
        />
      </div>

      <h2 id="when-to-use-jeffrey-microscope">When to Use Jeffrey Microscope</h2>
      <ul>
        <li>Analyzing JFR recordings from any source (local files, downloaded from production)</li>
        <li>Quick ad-hoc profiling investigations</li>
        <li>Detailed profile analysis with advanced visualizations</li>
        <li>Connecting to Jeffrey Server for remote recording collection</li>
      </ul>

      <h2 id="getting-started">Getting Started</h2>

      <div class="docs-grid docs-grid-2">
        <DocsLinkCard
          title="Recordings"
          description="Start analyzing recordings without any setup."
          to="/docs/microscope/recordings"
          icon="bi bi-lightning"
        />
        <DocsLinkCard
          title="Profiles"
          description="Learn about all analysis features."
          to="/docs/microscope/profiles"
          icon="bi bi-speedometer2"
        />
        <DocsLinkCard
          title="Deployment"
          description="Run Jeffrey Microscope on your machine."
          to="/docs/microscope/deployment/jar-execution"
          icon="bi bi-box-seam"
        />
        <DocsLinkCard
          title="Configuration"
          description="Customize Jeffrey Microscope settings."
          to="/docs/microscope/configuration/application-properties"
          icon="bi bi-gear"
        />
      </div>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
