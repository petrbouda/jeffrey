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
  { id: 'what-are-profiles', text: 'What are Profiles?', level: 2 },
  { id: 'creating-profiles', text: 'Creating Profiles', level: 2 },
  { id: 'profile-types', text: 'Profile Types', level: 2 }
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
        <span class="breadcrumb-item active">Profiles</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-person-lines-fill"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Profiles</h1>
          <p class="docs-section-badge">Concepts</p>
        </div>
      </header>

      <div class="docs-content">
        <p>Profiles are the parsed and analyzed representations of your JFR recordings.</p>

        <h2 id="what-are-profiles">What are Profiles?</h2>
        <p>A profile is created when you initialize a JFR recording in Jeffrey. It contains:</p>
        <ul>
          <li>Parsed event data stored in an optimized format</li>
          <li>Pre-computed analysis results</li>
          <li>Ready-to-view visualizations</li>
        </ul>

        <h2 id="creating-profiles">Creating Profiles</h2>
        <p>To create a profile from a recording:</p>
        <ol>
          <li>Navigate to the Recordings section</li>
          <li>Select a recording</li>
          <li>Click "Create Profile"</li>
          <li>Wait for initialization to complete</li>
        </ol>

        <h2 id="profile-types">Profile Types</h2>
        <p>Jeffrey supports two profile designations:</p>
        <ul>
          <li><strong>Primary Profile</strong> - The main profile you're analyzing</li>
          <li><strong>Secondary Profile</strong> - Used for differential comparison</li>
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
