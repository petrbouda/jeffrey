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
        <p>Jeffrey can be installed using Docker or as a standalone Java application. Choose the method that best suits your environment.</p>

        <h2 id="docker-installation">Docker Installation</h2>
        <p>The easiest way to get started with Jeffrey is using Docker.</p>

        <DocsCodeBlock
          language="bash"
          code="docker run -it --network host petrbouda/jeffrey"
        />

        <p>For a version with pre-loaded examples:</p>

        <DocsCodeBlock
          language="bash"
          code="docker run -it --network host petrbouda/jeffrey-examples"
        />

        <h2 id="java-installation">Java Installation</h2>
        <p>If you prefer to run Jeffrey as a standalone Java application:</p>

        <ol>
          <li>Download the latest <code>jeffrey.jar</code> from <a href="https://github.com/petrbouda/jeffrey/releases" target="_blank">GitHub Releases</a></li>
          <li>Ensure you have Java 25 or higher installed</li>
          <li>Run the application:</li>
        </ol>

        <DocsCodeBlock
          language="bash"
          code="java -jar jeffrey.jar"
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
