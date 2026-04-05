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
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsLinkCard from '@/components/docs/DocsLinkCard.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'jar-execution', text: 'JAR Execution', level: 2 },
  { id: 'docker-container', text: 'Docker Container', level: 2 },
  { id: 'kubernetes', text: 'Kubernetes', level: 2 },
  { id: 'configuration', text: 'Configuration', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const jarRun = `java -jar jeffrey-server.jar`;

const dockerRun = `docker run -p 8081:8081 petrbouda/jeffrey-server`;

const dockerVolume = `docker run -p 8081:8081 \\
  -v /path/to/data:/data/jeffrey \\
  petrbouda/jeffrey-server`;
</script>

<template>
  <article class="docs-article">
    <DocsPageHeader
      title="Deployment"
      icon="bi bi-cloud-upload"
    />

    <div class="docs-content">
      <p>Jeffrey Server can be deployed as a JAR file or Docker container in your server or cloud environment. For Kubernetes deployments with continuous recording, see the Continuous Recording section.</p>

      <h2 id="jar-execution">JAR Execution</h2>
      <p>Download the latest <code>jeffrey-server.jar</code> from the <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank">GitHub releases page</a>.</p>

      <DocsCallout type="info">
        <strong>Prerequisite:</strong> Jeffrey Server requires <strong>Java 25 or later</strong> to run.
      </DocsCallout>

      <p>Start the server with default settings:</p>
      <DocsCodeBlock
        language="bash"
        :code="jarRun"
      />

      <p>Jeffrey Server starts on HTTP port <code>8081</code> and gRPC port <code>9090</code> by default, using <code>~/.jeffrey-server</code> as its home directory.</p>

      <DocsCallout type="tip">
        <strong>Access Jeffrey Server:</strong> Open <a href="http://localhost:8081" target="_blank">http://localhost:8081</a> in your browser after starting.
      </DocsCallout>

      <h2 id="docker-container">Docker Container</h2>
      <p>Run Jeffrey Server as a Docker container:</p>
      <DocsCodeBlock
        language="bash"
        :code="dockerRun"
      />

      <p>To persist data across container restarts, mount a volume for the Jeffrey data directory:</p>
      <DocsCodeBlock
        language="bash"
        :code="dockerVolume"
      />

      <h2 id="kubernetes">Kubernetes</h2>
      <p>For production Kubernetes deployments, Jeffrey Server is typically deployed as part of a continuous recording setup. This allows automatic collection and analysis of profiling data from your applications.</p>

      <DocsLinkCard
        to="/docs/server/continuous-recording/overview"
        icon="bi bi-broadcast-pin"
        title="Continuous Recording"
        description="Set up Jeffrey Server in Kubernetes with automatic recording collection from profiled applications."
      />

      <h2 id="configuration">Configuration</h2>
      <p>Server configuration is optional &mdash; sensible defaults are provided out of the box. You can customize behavior through command-line arguments or an external <code>application.properties</code> file when needed.</p>

      <DocsLinkCard
        to="/docs/server/configuration/application-properties"
        icon="bi bi-gear"
        title="Application Properties"
        description="Explore all available configuration options for Jeffrey Server."
        variant="secondary"
      />
    </div>

    <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
