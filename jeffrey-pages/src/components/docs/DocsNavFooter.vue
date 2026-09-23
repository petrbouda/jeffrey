<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
-->

<script setup lang="ts">
import { useDocsNavigation } from '@/composables/useDocsNavigation';

const { adjacentPages } = useDocsNavigation();
</script>

<template>
  <nav class="docs-nav-footer">
    <router-link
      v-if="adjacentPages.prev?.to"
      :to="adjacentPages.prev.to"
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
      v-if="adjacentPages.next?.to"
      :to="adjacentPages.next.to"
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
