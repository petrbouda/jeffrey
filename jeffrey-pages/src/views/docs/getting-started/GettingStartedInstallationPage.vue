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
  { id: 'macos-installation', text: 'macOS Application (DMG)', level: 2 },
  { id: 'docker-installation', text: 'Docker Installation', level: 2 },
  { id: 'java-installation', text: 'Java Installation', level: 2 },
  { id: 'verifying-installation', text: 'Verifying Installation', level: 2 }
];

onMounted(() => {
  setHeadings(headings);
});
</script>

<template>
  <article class="docs-article">
      <DocsPageHeader
        title="Installation"
        icon="bi bi-download"
      />

      <div class="docs-content">
        <p>Jeffrey can be installed as a native macOS application, using Docker, or as a standalone Java application. Choose the method that best suits your environment.</p>

        <h2 id="macos-installation">macOS Application (DMG)</h2>
        <p>On Apple Silicon Macs the easiest option is the self-contained application bundle &mdash; it ships its own Java runtime, so nothing needs to be pre-installed.</p>

        <ol>
          <li>Download <code>microscope.dmg</code> from <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank">GitHub Releases</a></li>
          <li>Open the DMG and drag <strong>Jeffrey</strong> into your <strong>Applications</strong> folder</li>
          <li>Launch <strong>Jeffrey</strong> &mdash; it starts the local server and opens your browser automatically</li>
        </ol>

        <DocsCallout type="warning">
          The application is currently <strong>not notarized</strong>, so on first launch macOS Gatekeeper will block it. Right-click the app and choose <strong>Open</strong>, then confirm in the dialog. This is only required the first time.
        </DocsCallout>

        <DocsCallout type="info">
          The DMG is built for <strong>Apple Silicon</strong> (M-series) only. On Intel Macs, use the Java installation below with <code>microscope.jar</code>.
        </DocsCallout>

        <h2 id="java-installation">Java Installation</h2>
        <p>If you prefer to run Jeffrey as a standalone Java application:</p>

        <ol>
          <li>Download the latest <code>microscope.jar</code> from <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank">GitHub Releases</a></li>
          <li>Ensure you have Java 25 or higher installed</li>
          <li>Run the application:</li>
        </ol>

        <DocsCodeBlock
            language="bash"
            code="java -jar microscope.jar"
        />

        <h2 id="docker-installation">Docker Installation</h2>
        <p>The easiest way to get started with Jeffrey is using Docker.</p>

        <DocsCodeBlock
          language="bash"
          code="docker run -it --network host petrbouda/microscope"
        />

        <p>For a version with pre-loaded examples:</p>

        <DocsCodeBlock
          language="bash"
          code="docker run -it --network host petrbouda/microscope-examples"
        />

        <h2 id="verifying-installation">Verifying Installation</h2>
        <p>After starting Jeffrey, open your browser and navigate to:</p>

        <DocsCodeBlock
          language="text"
          code="http://localhost:8080"
        />

        <DocsCallout type="tip">
          You should see the Jeffrey welcome page. If you're using the examples image, you'll see pre-loaded sample profiles ready for exploration.
        </DocsCallout>
      </div>

      <DocsNavFooter />
  </article>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
