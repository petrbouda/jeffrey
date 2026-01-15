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
  { id: 'timeseries-overview', text: 'Timeseries Overview', level: 2 },
  { id: 'available-metrics', text: 'Available Metrics', level: 2 },
  { id: 'time-selection', text: 'Time Selection', level: 2 }
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
        <span class="breadcrumb-item active">Timeseries</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-graph-up"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Timeseries</h1>
          <p class="docs-section-badge">Features</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Timeseries charts show how metrics change over the duration of your recording.</p>

        <h2 id="timeseries-overview">Timeseries Overview</h2>
        <p>Timeseries visualizations help you:</p>
        <ul>
          <li>Identify trends in resource usage</li>
          <li>Correlate events with performance changes</li>
          <li>Find specific time periods of interest</li>
        </ul>

        <h2 id="available-metrics">Available Metrics</h2>
        <p>Jeffrey provides timeseries for:</p>
        <ul>
          <li><strong>Heap Memory</strong> - Memory usage over time</li>
          <li><strong>GC Activity</strong> - Garbage collection pauses</li>
          <li><strong>CPU Usage</strong> - Processor utilization</li>
          <li><strong>Thread Activity</strong> - Thread counts and states</li>
        </ul>

        <h2 id="time-selection">Time Selection</h2>
        <p>Use timeseries charts to select time ranges for detailed analysis in other views like flamegraphs.</p>
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
