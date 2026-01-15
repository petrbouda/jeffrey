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
  { id: 'jfr-events-overview', text: 'JFR Events Overview', level: 2 },
  { id: 'event-categories', text: 'Event Categories', level: 2 },
  { id: 'custom-events', text: 'Custom Events', level: 2 }
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
        <span class="breadcrumb-item">Concepts</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">JFR Events</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-calendar-event"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">JFR Events</h1>
          <p class="docs-section-badge">Concepts</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Java Flight Recorder captures a wide variety of events that provide insight into your application's behavior.</p>

        <h2 id="jfr-events-overview">JFR Events Overview</h2>
        <p>JFR events are data points recorded during application execution. They include:</p>
        <ul>
          <li>CPU samples and execution traces</li>
          <li>Memory allocation events</li>
          <li>Garbage collection events</li>
          <li>Thread activity and synchronization</li>
          <li>I/O operations</li>
        </ul>

        <h2 id="event-categories">Event Categories</h2>
        <p>Jeffrey organizes JFR events into categories:</p>
        <ul>
          <li><strong>JVM</strong> - JIT compilation, class loading, JVM flags</li>
          <li><strong>GC</strong> - Garbage collection pauses and phases</li>
          <li><strong>Thread</strong> - Thread states and activity</li>
          <li><strong>I/O</strong> - File and socket operations</li>
        </ul>

        <h2 id="custom-events">Custom Events</h2>
        <p>Jeffrey also supports custom JFR events from libraries like Jeffrey Events:</p>
        <ul>
          <li>HTTP Server events</li>
          <li>JDBC Statement events</li>
          <li>Connection pool events</li>
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
