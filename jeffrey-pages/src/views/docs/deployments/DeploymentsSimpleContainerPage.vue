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
import DocsPageHeader from '@/components/docs/DocsPageHeader.vue';
import DocsCodeBlock from '@/components/docs/DocsCodeBlock.vue';
import DocsCallout from '@/components/docs/DocsCallout.vue';
import DocsNavFooter from '@/components/docs/DocsNavFooter.vue';
import { useDocHeadings } from '@/composables/useDocHeadings';
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'quick-start', text: 'Quick Start', level: 2 },
  { id: 'persistent-storage', text: 'Persistent Storage', level: 2 },
  { id: 'docker-compose', text: 'Docker Compose', level: 2 },
  { id: 'environment-variables', text: 'Environment Variables', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});

const quickStart = `docker run -d \\
  --name jeffrey \\
  -p 8080:8080 \\
  petrbouda/jeffrey:latest`;

const withVolume = `docker run -d \\
  --name jeffrey \\
  -p 8080:8080 \\
  -v jeffrey-data:/data/jeffrey \\
  petrbouda/jeffrey:latest`;

const withBindMount = `docker run -d \\
  --name jeffrey \\
  -p 8080:8080 \\
  -v /path/on/host:/data/jeffrey \\
  petrbouda/jeffrey:latest`;

const dockerCompose = `services:
  jeffrey:
    image: petrbouda/jeffrey:latest
    container_name: jeffrey
    ports:
      - "8080:8080"
    volumes:
      - jeffrey-data:/data/jeffrey
    environment:
      - JAVA_OPTS=-Xmx2g -Xms2g
    restart: unless-stopped

volumes:
  jeffrey-data:`;

const withEnvVars = `docker run -d \\
  --name jeffrey \\
  -p 8080:8080 \\
  -v jeffrey-data:/data/jeffrey \\
  -e JAVA_OPTS="-Xmx2g -Xms2g" \\
  petrbouda/jeffrey:latest`;
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Simple as a Container"
        icon="bi bi-box-seam"
      />

      <div class="docs-content">
        <p>Run Jeffrey as a Docker container for easy deployment with isolated dependencies. This is the recommended approach for most local and development setups.</p>

        <h2 id="quick-start">Quick Start</h2>
        <p>Start Jeffrey with a single command:</p>
        <DocsCodeBlock
          language="bash"
          :code="quickStart"
        />

        <p>Jeffrey is now running at <a href="http://localhost:8080" target="_blank">http://localhost:8080</a>.</p>

        <DocsCallout type="warning">
          <strong>Data persistence:</strong> Without a volume, all data is lost when the container stops. See the next section for persistent storage.
        </DocsCallout>

        <h2 id="persistent-storage">Persistent Storage</h2>
        <p>Use a Docker volume to persist data across container restarts:</p>
        <DocsCodeBlock
          language="bash"
          :code="withVolume"
        />

        <h3>Bind Mount</h3>
        <p>Alternatively, mount a host directory for easier access to the data:</p>
        <DocsCodeBlock
          language="bash"
          :code="withBindMount"
        />

        <DocsCallout type="tip">
          <strong>Bind mounts</strong> are useful when you want to access Jeffrey's data directly from the host, for example to backup recordings or inspect the database.
        </DocsCallout>

        <h2 id="docker-compose">Docker Compose</h2>
        <p>For a more manageable setup, use Docker Compose:</p>
        <DocsCodeBlock
          language="yaml"
          :code="dockerCompose"
        />

        <p>Start with:</p>
        <DocsCodeBlock
          language="bash"
          code="docker compose up -d"
        />

        <h2 id="environment-variables">Environment Variables</h2>
        <p>Configure Jeffrey using environment variables:</p>
        <DocsCodeBlock
          language="bash"
          :code="withEnvVars"
        />

        <table>
          <thead>
            <tr>
              <th>Variable</th>
              <th>Description</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td><code>JAVA_OPTS</code></td>
              <td>JVM options (heap size, GC settings, etc.)</td>
            </tr>
            <tr>
              <td><code>SERVER_PORT</code></td>
              <td>HTTP server port (default: 8080)</td>
            </tr>
            <tr>
              <td><code>JEFFREY_HOME_DIR</code></td>
              <td>Jeffrey data directory (default: /data/jeffrey)</td>
            </tr>
          </tbody>
        </table>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
