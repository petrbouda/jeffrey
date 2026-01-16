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
  { id: 'guardian-analysis', text: 'Guardian Analysis', level: 2 },
  { id: 'detected-issues', text: 'Detected Issues', level: 2 },
  { id: 'recommendations', text: 'Recommendations', level: 2 }
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
        <span class="breadcrumb-item active">Guardian</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-shield-check"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Guardian Analysis</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>Guardian automatically analyzes your profiles to detect potential issues and suspicious patterns.</p>

        <h2 id="guardian-analysis">Guardian Analysis</h2>
        <p>The Guardian feature traverses your stacktraces looking for:</p>
        <ul>
          <li>Known anti-patterns</li>
          <li>Performance hotspots</li>
          <li>Suspicious behavior</li>
        </ul>

        <h2 id="detected-issues">Detected Issues</h2>
        <p>Guardian can identify:</p>
        <ul>
          <li><strong>JIT Compilation</strong> - Excessive compilation activity</li>
          <li><strong>Logging</strong> - Expensive logging operations</li>
          <li><strong>Regular Expressions</strong> - Costly regex patterns</li>
          <li><strong>Exception Handling</strong> - Frequent exceptions</li>
        </ul>

        <h2 id="recommendations">Recommendations</h2>
        <p>Each detected issue includes actionable recommendations for improvement.</p>
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
