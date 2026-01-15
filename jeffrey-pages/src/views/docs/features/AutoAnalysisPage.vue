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
import { useDocsNavigation } from '@/composables/useDocsNavigation';
import { useDocHeadings } from '@/composables/useDocHeadings';

const { adjacentPages } = useDocsNavigation();
const { setHeadings } = useDocHeadings();

const headings = [
  { id: 'auto-analysis-overview', text: 'Auto Analysis Overview', level: 2 },
  { id: 'analysis-rules', text: 'Analysis Rules', level: 2 },
  { id: 'severity-levels', text: 'Severity Levels', level: 2 }
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
        <span class="breadcrumb-item">Features</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Auto Analysis</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-robot"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Auto Analysis</h1>
          <p class="docs-section-badge">Features</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Auto Analysis applies rule-based checks to your profiles and provides color-coded insights.</p>

        <h2 id="auto-analysis-overview">Auto Analysis Overview</h2>
        <p>The auto analysis feature runs a set of predefined rules against your profile data to identify:</p>
        <ul>
          <li>Configuration issues</li>
          <li>Performance concerns</li>
          <li>Best practice violations</li>
        </ul>

        <h2 id="analysis-rules">Analysis Rules</h2>
        <p>Rules cover various aspects:</p>
        <ul>
          <li><strong>Memory</strong> - Heap sizing, GC configuration</li>
          <li><strong>Threading</strong> - Thread pool sizing, contention</li>
          <li><strong>JVM</strong> - Flag configurations, compilation</li>
        </ul>

        <h2 id="severity-levels">Severity Levels</h2>
        <p>Issues are categorized by severity:</p>
        <ul>
          <li><strong>Red</strong> - Critical issues requiring attention</li>
          <li><strong>Yellow</strong> - Warnings worth investigating</li>
          <li><strong>Green</strong> - Everything looks good</li>
        </ul>
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
