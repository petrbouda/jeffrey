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
  { id: 'cpu-profiling', text: 'CPU Profiling', level: 2 },
  { id: 'identifying-hotspots', text: 'Identifying Hotspots', level: 2 },
  { id: 'optimization-tips', text: 'Optimization Tips', level: 2 }
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
        <span class="breadcrumb-item active">CPU Performance</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-cpu"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">CPU Performance</h1>
          <p class="docs-section-badge">Guides</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Learn how to analyze CPU performance and identify bottlenecks in your Java applications.</p>

        <h2 id="cpu-profiling">CPU Profiling</h2>
        <p>CPU profiling helps you understand:</p>
        <ul>
          <li>Where your application spends processing time</li>
          <li>Which methods are called most frequently</li>
          <li>Hot paths through your code</li>
        </ul>

        <h2 id="identifying-hotspots">Identifying Hotspots</h2>
        <p>Use CPU flamegraphs to identify:</p>
        <ul>
          <li><strong>Wide stacks</strong> - Methods that consume significant time</li>
          <li><strong>Tall stacks</strong> - Deep call chains</li>
          <li><strong>Common patterns</strong> - Frequently called methods</li>
        </ul>

        <h2 id="optimization-tips">Optimization Tips</h2>
        <p>Common optimization strategies:</p>
        <ul>
          <li>Optimize the widest flames first</li>
          <li>Look for unnecessary work in hot paths</li>
          <li>Consider caching frequently computed values</li>
          <li>Profile before and after changes</li>
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
