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
      class="docs-nav-link docs-nav-link--prev"
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
      class="docs-nav-link docs-nav-link--next"
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
.docs-nav-footer {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 3rem;
  padding-top: 2rem;
  border-top: 1px solid #e9ecef;
}

.docs-nav-link {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.75rem;
  max-width: 45%;
  padding: 1rem 1.25rem;
  overflow: hidden;
  font-size: 1.1rem;
  font-weight: 500;
  color: inherit;
  text-decoration: none;
  background: #f8f9fa;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.docs-nav-link:hover {
  background: linear-gradient(135deg, rgba(94, 100, 255, 0.1) 0%, rgba(124, 58, 237, 0.1) 100%);
}

.docs-nav-link:focus-visible {
  outline: 2px solid #5e64ff;
  outline-offset: 2px;
}

.docs-nav-link--prev:hover {
  transform: translateX(-4px);
}

.docs-nav-link--next {
  justify-content: flex-end;
  text-align: right;
}

.docs-nav-link--next:hover {
  transform: translateX(4px);
}

.docs-nav-link i {
  font-size: 1.25rem;
  color: #5e64ff;
}

.nav-text {
  display: flex;
  flex-direction: column;
}

.nav-label {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6c757d;
}

.nav-title {
  font-weight: 600;
  color: #343a40;
}

.nav-section {
  font-size: 0.75rem;
  color: #6c757d;
  margin-top: 0.15rem;
}

.nav-spacer {
  flex: 1;
}

@media (max-width: 768px) {
  .docs-nav-footer {
    flex-direction: column;
  }

  .docs-nav-link {
    max-width: 100%;
    font-size: 1rem;
  }
}
</style>
