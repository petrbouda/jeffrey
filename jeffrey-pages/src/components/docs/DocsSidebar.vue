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
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { docsNavigation } from '@/composables/useDocsNavigation';

const emit = defineEmits<{
  (e: 'open-search'): void;
}>();

const route = useRoute();

// Extract category and page from path since routes aren't parameterized
const currentCategory = computed(() => {
  const parts = route.path.split('/');
  return parts[2] || '';
});
const currentPage = computed(() => {
  const parts = route.path.split('/');
  return parts[3] || '';
});

// Track expanded sections
const expandedSections = ref<Set<string>>(new Set());

// Auto-expand current category on load and when navigating
watch(currentCategory, (newCategory) => {
  if (newCategory) {
    expandedSections.value.add(newCategory);
  }
}, { immediate: true });

const isActiveLink = (category: string, page: string): boolean => {
  return currentCategory.value === category && currentPage.value === page;
};

const isActiveSection = (sectionPath: string): boolean => {
  return currentCategory.value === sectionPath;
};

const isExpanded = (sectionPath: string): boolean => {
  return expandedSections.value.has(sectionPath);
};

const toggleSection = (sectionPath: string): void => {
  if (expandedSections.value.has(sectionPath)) {
    expandedSections.value.delete(sectionPath);
  } else {
    expandedSections.value.add(sectionPath);
  }
};
</script>

<template>
  <div class="sidebar-content">
    <!-- Search trigger -->
    <div class="sidebar-search" @click="emit('open-search')">
      <i class="bi bi-search"></i>
      <span>Search docs...</span>
      <kbd>/</kbd>
    </div>

    <!-- Navigation with collapsible sections -->
    <nav class="sidebar-nav">
      <div
        v-for="section in docsNavigation"
        :key="section.path"
        class="nav-section"
        :class="{
          'active-section': isActiveSection(section.path),
          'expanded': isExpanded(section.path)
        }"
      >
        <div
          class="nav-section-header"
          @click="toggleSection(section.path)"
        >
          <i class="bi section-icon" :class="section.icon"></i>
          <span class="section-title">{{ section.title }}</span>
          <i class="bi bi-chevron-down chevron-icon"></i>
        </div>

        <div class="nav-section-items">
          <router-link
            v-for="page in section.children"
            :key="page.path"
            :to="`/docs/${section.path}/${page.path}`"
            class="nav-item"
            :class="{ 'active': isActiveLink(section.path, page.path) }"
          >
            <span class="nav-item-indicator"></span>
            {{ page.title }}
          </router-link>
        </div>
      </div>
    </nav>
  </div>
</template>

<style scoped>
/* ============================
   CSS Custom Properties
   ============================ */
.sidebar-content {
  --color-primary: #5e64ff;
  --color-primary-light: rgba(94, 100, 255, 0.08);
  --color-primary-lighter: rgba(94, 100, 255, 0.04);
  --color-text: #1a1f36;
  --color-text-secondary: #5e6e82;
  --color-text-muted: #8492a6;
  --color-hover-bg: #f7f8fc;
  --sidebar-border: #e8ecf1;
  --transition-fast: 150ms ease;
}

/* ============================
   Sidebar Content Container
   ============================ */
.sidebar-content {
  padding: 1.25rem;
  padding-top: 2rem;
}

/* ============================
   Search Trigger
   ============================ */
.sidebar-search {
  display: flex;
  align-items: center;
  gap: 0.625rem;
  padding: 0.625rem 0.875rem;
  background: linear-gradient(135deg, #f8f9fc 0%, #fff 100%);
  border: 1px solid var(--sidebar-border);
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 1.5rem;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
}

.sidebar-search:hover {
  border-color: var(--color-primary);
  box-shadow: 0 2px 8px rgba(94, 100, 255, 0.12);
  transform: translateY(-1px);
}

.sidebar-search i {
  color: var(--color-text-muted);
  font-size: 0.85rem;
}

.sidebar-search span {
  flex: 1;
  color: var(--color-text-muted);
  font-size: 0.8rem;
  font-weight: 500;
}

.sidebar-search kbd {
  background: #edf0f5;
  border: 1px solid #dde2e9;
  border-radius: 4px;
  padding: 0.125rem 0.375rem;
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  font-family: inherit;
}

/* ============================
   Navigation Structure
   ============================ */
.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.nav-section {
  /* Each section is a group */
}

/* ============================
   Section Header (Clickable for collapse)
   ============================ */
.nav-section-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  font-weight: 600;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color var(--transition-fast);
}

.nav-section-header:hover {
  background-color: var(--color-hover-bg);
}

.nav-section-header .section-icon {
  font-size: 0.8rem;
  color: var(--color-text-muted);
}

.nav-section-header .section-title {
  flex: 1;
}

.nav-section-header .chevron-icon {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  transition: transform 0.2s ease;
}

/* Rotate chevron when expanded */
.nav-section.expanded .nav-section-header .chevron-icon {
  transform: rotate(180deg);
}

/* Active section styling */
.nav-section.active-section .nav-section-header {
  color: var(--color-primary);
}

.nav-section.active-section .nav-section-header .section-icon,
.nav-section.active-section .nav-section-header .chevron-icon {
  color: var(--color-primary);
}

/* ============================
   Section Items (Collapsible)
   ============================ */
.nav-section-items {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.25s ease, margin-top 0.25s ease;
  margin-top: 0;
}

.nav-section.expanded .nav-section-items {
  max-height: 500px;
  margin-top: 0.25rem;
}

/* ============================
   Navigation Items (Links)
   ============================ */
.nav-item {
  display: flex;
  align-items: center;
  padding: 0.5rem 0.75rem;
  padding-left: 1.75rem;
  color: var(--color-text-secondary);
  text-decoration: none;
  font-size: 0.875rem;
  font-weight: 450;
  border-radius: 6px;
  position: relative;
  transition:
    color var(--transition-fast),
    background-color var(--transition-fast);
}

.nav-item .nav-item-indicator {
  position: absolute;
  left: 0.5rem;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: var(--color-primary);
  border-radius: 2px;
  transition: height var(--transition-fast);
}

.nav-item:hover {
  color: var(--color-primary);
  background: var(--color-primary-lighter);
}

.nav-item:hover .nav-item-indicator {
  height: 50%;
}

/* Active state */
.nav-item.active {
  color: var(--color-primary);
  background: var(--color-primary-light);
  font-weight: 500;
}

.nav-item.active .nav-item-indicator {
  height: 60%;
}
</style>
