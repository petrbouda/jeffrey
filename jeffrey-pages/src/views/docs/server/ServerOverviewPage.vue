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
  { id: 'architecture', text: 'Architecture', level: 2 },
  { id: 'getting-started', text: 'Getting Started', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Jeffrey Server"
      icon="bi bi-cloud"
    />

    <div class="docs-content">
      <p>Jeffrey Server is the recording collection server designed for cloud and production environments. It runs alongside your applications (typically in Kubernetes) and continuously collects JFR recordings, heap dumps, and other artifacts.</p>

      <h2 id="key-features">Key Features</h2>

      <div class="docs-grid docs-grid-2">
        <DocsFeatureCard
          icon="bi bi-broadcast-pin"
          title="Continuous Recording"
          description="Continuously collect JFR recordings from running applications."
        />
        <DocsFeatureCard
          icon="bi bi-collection"
          title="Recording Sessions"
          description="Organize recordings into sessions with automatic artifact discovery."
        />
        <DocsFeatureCard
          icon="bi bi-diagram-3"
          title="Application Instances"
          description="Track application instances, their lifecycle, and recording timelines."
        />
        <DocsFeatureCard
          icon="bi bi-hdd-network"
          title="gRPC Communication"
          description="Jeffrey Microscope connects to Server via gRPC for downloading and managing recordings."
        />
        <DocsFeatureCard
          icon="bi bi-clock-history"
          title="Scheduler"
          description="Schedule recording jobs and automated collection tasks."
        />
        <DocsFeatureCard
          icon="bi bi-bell"
          title="Alerts & Messages"
          description="Real-time notifications about recording events and instance status."
        />
      </div>

      <h2 id="architecture">Architecture</h2>
      <p>Jeffrey Server and Jeffrey Microscope work together as a split architecture:</p>
      <ul>
        <li><strong>Server</strong> collects recordings in production (Kubernetes, cloud)</li>
        <li><strong>Local</strong> provides full analysis on your developer machine</li>
        <li>Communication happens via <strong>gRPC</strong> — Local connects to Server as a "remote workspace"</li>
      </ul>
      <p>This split keeps expensive profile processing off your cloud infrastructure and on local developer machines, reducing operational costs.</p>

      <h2 id="getting-started">Getting Started</h2>

      <div class="docs-grid docs-grid-2">
        <DocsLinkCard
          title="Continuous Recording"
          description="Set up continuous JFR recording in production."
          to="/docs/server/continuous-recording/overview"
          icon="bi bi-broadcast-pin"
        />
        <DocsLinkCard
          title="Recording Sessions"
          description="Understand how recordings are organized."
          to="/docs/server/recording-sessions/overview"
          icon="bi bi-collection"
        />
        <DocsLinkCard
          title="gRPC API"
          description="Learn about the Server communication protocol."
          to="/docs/server/grpc-api"
          icon="bi bi-hdd-network"
        />
        <DocsLinkCard
          title="Deployment"
          description="Deploy Jeffrey Server."
          to="/docs/server/deployment"
          icon="bi bi-cloud-upload"
        />
      </div>
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
