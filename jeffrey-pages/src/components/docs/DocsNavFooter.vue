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
import { useDocsNavigation } from '@/composables/useDocsNavigation';

const { adjacentPages } = useDocsNavigation();
</script>

<template>
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
        <span v-if="adjacentPages.prev.section" class="nav-section">{{ adjacentPages.prev.section }}</span>
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
        <span v-if="adjacentPages.next.section" class="nav-section">{{ adjacentPages.next.section }}</span>
      </div>
      <i class="bi bi-arrow-right"></i>
    </router-link>
  </nav>
</template>

<style scoped>
@import '@/views/docs/docs-page.css';
</style>
