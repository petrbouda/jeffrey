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
  { id: 'flamegraph-basics', text: 'Flamegraph Basics', level: 2 },
  { id: 'flamegraph-types', text: 'Flamegraph Types', level: 2 },
  { id: 'differential-flamegraphs', text: 'Differential Flamegraphs', level: 2 },
  { id: 'navigation', text: 'Navigation', level: 2 }
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
        <span class="breadcrumb-item active">Flamegraphs</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-fire"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Flamegraphs</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Flamegraphs are powerful visualizations for understanding where your application spends its time.</p>

        <h2 id="flamegraph-basics">Flamegraph Basics</h2>
        <p>A flamegraph shows stack traces in a hierarchical view:</p>
        <ul>
          <li>Width represents sample count or time</li>
          <li>Height represents stack depth</li>
          <li>Colors help distinguish different code areas</li>
        </ul>

        <h2 id="flamegraph-types">Flamegraph Types</h2>
        <p>Jeffrey supports multiple flamegraph types:</p>
        <ul>
          <li><strong>CPU</strong> - CPU execution samples</li>
          <li><strong>Allocation</strong> - Memory allocation sites</li>
          <li><strong>Lock</strong> - Thread contention points</li>
          <li><strong>Native</strong> - Native memory allocations</li>
        </ul>

        <h2 id="differential-flamegraphs">Differential Flamegraphs</h2>
        <p>Compare two profiles side-by-side to identify:</p>
        <ul>
          <li>Performance regressions</li>
          <li>Optimization improvements</li>
          <li>Changed code paths</li>
        </ul>

        <h2 id="navigation">Navigation</h2>
        <p>Interact with flamegraphs by:</p>
        <ul>
          <li>Clicking to zoom into a specific stack</li>
          <li>Searching for specific methods</li>
          <li>Filtering by thread or frame type</li>
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
