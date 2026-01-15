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
  { id: 'memory-analysis', text: 'Memory Analysis', level: 2 },
  { id: 'allocation-profiling', text: 'Allocation Profiling', level: 2 },
  { id: 'gc-analysis', text: 'GC Analysis', level: 2 }
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
        <span class="breadcrumb-item">Guides</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Memory Analysis</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-memory"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Memory Analysis</h1>
          <p class="docs-section-badge">Guides</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Understand memory usage patterns and identify allocation hotspots in your Java applications.</p>

        <h2 id="memory-analysis">Memory Analysis</h2>
        <p>Jeffrey provides tools for analyzing:</p>
        <ul>
          <li>Heap memory usage over time</li>
          <li>Object allocation patterns</li>
          <li>Native memory consumption</li>
        </ul>

        <h2 id="allocation-profiling">Allocation Profiling</h2>
        <p>Use allocation flamegraphs to find:</p>
        <ul>
          <li>Methods that allocate the most objects</li>
          <li>High-frequency allocation sites</li>
          <li>Temporary object creation patterns</li>
        </ul>

        <h2 id="gc-analysis">GC Analysis</h2>
        <p>Analyze garbage collection behavior:</p>
        <ul>
          <li>GC pause frequency and duration</li>
          <li>Memory reclamation patterns</li>
          <li>Generation sizing effectiveness</li>
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
