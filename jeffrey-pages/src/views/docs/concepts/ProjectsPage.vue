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
  { id: 'what-are-projects', text: 'What are Projects?', level: 2 },
  { id: 'project-structure', text: 'Project Structure', level: 2 },
  { id: 'managing-projects', text: 'Managing Projects', level: 2 }
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
        <span class="breadcrumb-item active">Projects</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-kanban"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Projects</h1>
          <p class="docs-section-badge">Concepts</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Projects are containers within workspaces that hold your recordings and profiles.</p>

        <h2 id="what-are-projects">What are Projects?</h2>
        <p>A project represents a specific application or service you want to profile. Each project can contain:</p>
        <ul>
          <li>Multiple JFR recordings</li>
          <li>Generated profiles from those recordings</li>
          <li>Associated heap dumps</li>
        </ul>

        <h2 id="project-structure">Project Structure</h2>
        <p>Within a project, you'll find:</p>
        <ul>
          <li><strong>Recordings</strong> - Upload and manage your JFR files</li>
          <li><strong>Profiles</strong> - View and analyze generated profiles</li>
        </ul>

        <h2 id="managing-projects">Managing Projects</h2>
        <p>Create projects within a workspace to organize recordings for specific applications or profiling sessions.</p>
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
