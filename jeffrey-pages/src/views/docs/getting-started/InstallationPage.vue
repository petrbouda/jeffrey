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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
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
      <nav class="docs-breadcrumb">
        <router-link to="/docs" class="breadcrumb-item">
          <i class="bi bi-book me-1"></i>Docs
        </router-link>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">Getting Started</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Installation</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-download"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Installation</h1>
        </div>
      </header>

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
</style>
