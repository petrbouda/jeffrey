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

const headings: { id: string; text: string; level: number }[] = [];

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
        <span class="breadcrumb-item">Deployments</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item active">Container with Examples</span>
      </nav>

      <header class="docs-header">
        <div class="header-icon">
          <i class="bi bi-collection"></i>
        </div>
        <div class="header-content">
          <h1 class="docs-title">Container with Examples</h1>
        </div>
      </header>

      <div class="docs-content">
        <p>The <code>jeffrey-examples</code> image comes pre-loaded with sample JFR recordings and profiles. This is perfect for exploring Jeffrey's features without needing your own profiling data.</p>

        <router-link to="/tour-with-examples" class="tour-link-card">
          <div class="tour-link-icon">
            <i class="bi bi-play-circle"></i>
          </div>
          <div class="tour-link-content">
            <h4>Tour with Examples</h4>
            <p>Follow a guided tour through Jeffrey's features using the pre-loaded example profiles.</p>
          </div>
          <i class="bi bi-arrow-right tour-link-arrow"></i>
        </router-link>
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

/* Tour Link Card */
.tour-link-card {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem;
  margin: 1.5rem 0;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-left: 4px solid #5e64ff;
  border-radius: 8px;
  text-decoration: none !important;
  color: inherit;
  transition: all 0.2s ease;
}

.tour-link-card:hover {
  background: linear-gradient(135deg, rgba(94,100,255,0.08) 0%, rgba(124,58,237,0.08) 100%);
  border-color: #5e64ff;
  transform: translateX(4px);
  text-decoration: none !important;
}

.tour-link-icon {
  width: 48px;
  height: 48px;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5e64ff 0%, #7c3aed 100%);
  border-radius: 10px;
}

.tour-link-icon i {
  font-size: 1.25rem;
  color: #fff;
}

.tour-link-content {
  flex: 1;
}

.tour-link-content h4 {
  margin: 0 0 0.25rem 0;
  font-size: 1rem;
  font-weight: 600;
  color: #343a40;
}

.tour-link-content p {
  margin: 0;
  font-size: 0.85rem;
  color: #5e6e82;
  line-height: 1.4;
}

.tour-link-arrow {
  font-size: 1.25rem;
  color: #5e64ff;
  transition: transform 0.2s ease;
}

.tour-link-card:hover .tour-link-arrow {
  transform: translateX(4px);
}
</style>
