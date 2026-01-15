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
  { id: 'thread-analysis', text: 'Thread Analysis', level: 2 },
  { id: 'thread-states', text: 'Thread States', level: 2 },
  { id: 'contention-analysis', text: 'Contention Analysis', level: 2 }
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
        <span class="breadcrumb-item active">Thread Analysis</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-diagram-3"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Thread Analysis</h1>
          <p class="docs-section-badge">Guides</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Analyze thread behavior and identify synchronization bottlenecks in your applications.</p>

        <h2 id="thread-analysis">Thread Analysis</h2>
        <p>Jeffrey provides insights into:</p>
        <ul>
          <li>Thread lifecycle and activity</li>
          <li>Thread pool utilization</li>
          <li>Thread state distribution</li>
        </ul>

        <h2 id="thread-states">Thread States</h2>
        <p>Understand thread states:</p>
        <ul>
          <li><strong>Running</strong> - Actively executing</li>
          <li><strong>Blocked</strong> - Waiting for a monitor</li>
          <li><strong>Waiting</strong> - Waiting indefinitely</li>
          <li><strong>Timed Waiting</strong> - Waiting with timeout</li>
        </ul>

        <h2 id="contention-analysis">Contention Analysis</h2>
        <p>Identify lock contention issues:</p>
        <ul>
          <li>High contention monitors</li>
          <li>Lock wait times</li>
          <li>Synchronization bottlenecks</li>
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
