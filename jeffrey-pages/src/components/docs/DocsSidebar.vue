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
import {
  PRODUCTS,
  getProductForPath,
  navigationForProduct,
  pageHref
} from '@/composables/useDocsNavigation';
import type { DocPage, DocSection } from '@/types/docs';

const emit = defineEmits<{
  (e: 'open-search'): void;
}>();

const route = useRoute();

// Default to the Microscope product when the route does not name one
// (legacy /docs/getting-started/* and /docs/architecture/* fall here).
const currentProduct = computed(() => getProductForPath(route.path) ?? 'microscope');

const productInfo = computed(() => PRODUCTS[currentProduct.value]);

const productNav = computed<DocSection[]>(() => navigationForProduct(currentProduct.value));

// Extract category and page from path since routes aren't parameterized
const currentCategory = computed(() => {
  const parts = route.path.split('/');
  return parts[2] || '';
});
const currentPage = computed(() => {
  const parts = route.path.split('/');
  // Handle nested paths like projects/profiles
  return parts.slice(3).join('/') || '';
});

// Track expanded sections
const expandedSections = ref<Set<string>>(new Set());
// Track expanded page items (pages with children)
const expandedPageItems = ref<Set<string>>(new Set());

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

const isPageItemExpanded = (category: string, page: string): boolean => {
  return expandedPageItems.value.has(`${category}/${page}`);
};

const togglePageItem = (category: string, page: string): void => {
  const key = `${category}/${page}`;
  if (expandedPageItems.value.has(key)) {
    expandedPageItems.value.delete(key);
  } else {
    expandedPageItems.value.add(key);
  }
};

const hasChildren = (page: DocPage): boolean => {
  return !!(page.children && page.children.length > 0);
};

const getPageLink = (category: string, page: DocPage): string => {
  return pageHref(category, page);
};

const isSinglePageSection = (section: DocSection): boolean => {
  return section.children.length === 1;
};

const getSinglePageLink = (section: DocSection): string => {
  return pageHref(section.path, section.children[0]);
};

// Sidebar items mark themselves active when the current URL matches their target.
// Anchor-targeted items (e.g. /docs/server#architecture) require a hash match too,
// so plain "Overview" and anchor "Architecture" don't both light up on the same path.
const isItemActive = (sectionPath: string, page: DocPage): boolean => {
  const href = pageHref(sectionPath, page);
  const [hrefPath, hrefHash] = href.split('#');
  if (route.path !== hrefPath) return false;
  return hrefHash ? route.hash === `#${hrefHash}` : !route.hash;
};

// Section is "active" when one of its descendants is the current route.
// Walks two levels (direct children + their children) — that's the depth the
// existing data allows. Used by both the active-class binding and the
// auto-expand watcher so synthetic-path groups (e.g. "Architecture") and
// nested children (e.g. "Profiles > Guardian") both behave correctly.
const isSectionActive = (section: DocSection): boolean => {
  for (const p of section.children) {
    if (isItemActive(section.path, p)) return true;
    if (p.children?.some(c => isItemActive(section.path, c))) return true;
  }
  return false;
};

// Auto-expand current category and page items on load and when navigating.
// Also tracked: route.fullPath, so that hash-only navigations (e.g. clicking
// Architecture > Diagram which goes to /docs/microscope#architecture) re-trigger
// expansion of the synthetic-path sections that contain the active link.
watch(
  [currentCategory, currentPage, currentProduct, () => route.fullPath],
  ([newCategory, newPage, product]) => {
    // /docs/microscope and /docs/server are product-level URLs whose category
    // is the product id, not a section path — expand the lead section instead.
    const isProductRoute = newCategory === 'microscope' || newCategory === 'server';
    if (newCategory && !isProductRoute) {
      expandedSections.value.add(newCategory);
    }
    if (isProductRoute) {
      const lead = navigationForProduct(product)[0];
      if (lead) expandedSections.value.add(lead.path);
    }
    // Expand any section that contains the current route — covers synthetic-path
    // groups like "Architecture" (children use absolute `to` overrides) and the
    // multi-page JEFFREY MICROSCOPE group (nested-children pages like
    // Profiles > Guardian).
    for (const section of productNav.value) {
      if (isSectionActive(section)) {
        expandedSections.value.add(section.path);
      }
    }
    // Auto-expand page items that have children and are active.
    // Handle nested paths like projects/profiles -> expand projects.
    // The rendered key uses `section.path`, which may be synthetic (e.g.
    // `_microscope-server-integration`) and unrelated to the URL's first segment.
    // Walk all sections to find the one that hosts the matching parent page.
    if (newPage) {
      const parentPage = newPage.split('/')[0];
      for (const section of productNav.value) {
        const hosts = section.children.some(p => p.path === parentPage && !!p.children);
        if (hosts) {
          expandedPageItems.value.add(`${section.path}/${parentPage}`);
        }
      }
    }
  },
  { immediate: true }
);
</script>

<template>
  <div class="sidebar-content" :class="`product-${currentProduct}`">
    <!-- Product header: backlink + current product name -->
    <router-link to="/docs" class="sidebar-back">
      <i class="bi bi-arrow-left"></i>
      <span>All docs</span>
    </router-link>
    <div class="sidebar-product">
      <i class="bi" :class="productInfo.icon"></i>
      <span>{{ productInfo.title }}</span>
    </div>

    <!-- Search trigger -->
    <div class="sidebar-search" @click="emit('open-search')">
      <i class="bi bi-search"></i>
      <span>Search docs...</span>
      <kbd>/</kbd>
    </div>

    <!-- Navigation with collapsible sections -->
    <nav class="sidebar-nav">
      <template v-for="section in productNav" :key="section.path">
        <!-- Single-page section: direct link -->
        <router-link
          v-if="isSinglePageSection(section)"
          :to="getSinglePageLink(section)"
          class="nav-section-direct"
          :class="{ 'active-section': isSectionActive(section) }"
        >
          <i class="bi section-icon" :class="section.icon"></i>
          <span class="section-title">{{ section.title }}</span>
        </router-link>

        <!-- Multi-page section: collapsible -->
        <div
          v-else
          class="nav-section"
          :class="{
            'active-section': isSectionActive(section),
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
            <template v-for="page in section.children" :key="page.to ?? page.path ?? page.title">
              <!-- Page with children (collapsible) -->
              <div v-if="hasChildren(page)" class="nav-item-group" :class="{ 'expanded': isPageItemExpanded(section.path, page.path ?? '') }">
                <div
                  class="nav-item nav-item-parent"
                  :class="{ 'active': page.path !== undefined && (currentPage === page.path || currentPage.startsWith(page.path + '/')) }"
                  @click="togglePageItem(section.path, page.path ?? '')"
                >
                  <span class="nav-item-indicator"></span>
                  {{ page.title }}
                  <i class="bi bi-chevron-down nav-item-chevron"></i>
                </div>
                <div class="nav-item-children">
                  <router-link
                    v-for="child in page.children"
                    :key="child.path"
                    :to="getPageLink(section.path, child)"
                    class="nav-item nav-item-child"
                    :class="{ 'active': isItemActive(section.path, child) }"
                  >
                    <span class="nav-item-indicator"></span>
                    {{ child.title }}
                  </router-link>
                </div>
              </div>
              <!-- Regular page link -->
              <router-link
                v-else
                :to="getPageLink(section.path, page)"
                class="nav-item"
                :class="{ 'active': isItemActive(section.path, page) }"
              >
                <span class="nav-item-indicator"></span>
                {{ page.title }}
              </router-link>
            </template>
          </div>
        </div>
      </template>
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
  padding-top: 1.25rem;
}

/* Per-product accent — Server gets the violet that already brands its hub. */
.sidebar-content.product-server {
  --color-primary: #7c3aed;
  --color-primary-light: rgba(124, 58, 237, 0.10);
  --color-primary-lighter: rgba(124, 58, 237, 0.05);
}

/* ============================
   Product Header
   ============================ */
.sidebar-back {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  text-decoration: none;
  padding: 0.25rem 0.4rem;
  border-radius: 6px;
  transition: color var(--transition-fast), background-color var(--transition-fast);
}

.sidebar-back:hover {
  color: var(--color-primary);
  background-color: var(--color-hover-bg);
}

.sidebar-back i { font-size: 0.7rem; }

.sidebar-product {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  margin: 0.5rem 0 1.25rem;
  padding: 0.55rem 0.7rem;
  background: var(--color-primary-light);
  color: var(--color-primary);
  border-radius: 8px;
  font-weight: 600;
  font-size: 0.95rem;
  letter-spacing: -0.005em;
}

.sidebar-product i { font-size: 1rem; }

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
   Direct Link Section (single-page)
   ============================ */
.nav-section-direct {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.5rem;
  font-weight: 600;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  text-decoration: none;
  border-radius: 6px;
  transition:
    background-color var(--transition-fast),
    color var(--transition-fast);
}

.nav-section-direct:hover {
  background-color: var(--color-hover-bg);
  color: var(--color-primary);
}

.nav-section-direct:hover .section-icon {
  color: var(--color-primary);
}

.nav-section-direct .section-icon {
  font-size: 0.8rem;
  color: var(--color-text-muted);
  transition: color var(--transition-fast);
}

.nav-section-direct.active-section {
  color: var(--color-primary);
  background: var(--color-primary-light);
}

.nav-section-direct.active-section .section-icon {
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

/* ============================
   Nested Page Items (Collapsible)
   ============================ */
.nav-item-group {
  /* Container for parent + children */
}

.nav-item-parent {
  cursor: pointer;
  justify-content: flex-start;
}

.nav-item-parent .nav-item-chevron {
  margin-left: auto;
  font-size: 0.65rem;
  color: var(--color-text-muted);
  transition: transform 0.2s ease;
}

.nav-item-group.expanded .nav-item-parent .nav-item-chevron {
  transform: rotate(180deg);
}

.nav-item-children {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.25s ease, margin-top 0.25s ease;
  margin-top: 0;
}

.nav-item-group.expanded .nav-item-children {
  max-height: 500px;
  margin-top: 0.25rem;
}

.nav-item-child {
  padding-left: 2.5rem !important;
  font-size: 0.8rem !important;
  color: var(--color-text-muted) !important;
}

.nav-item-child:hover {
  color: var(--color-primary) !important;
}

.nav-item-child.active {
  color: var(--color-primary) !important;
  font-weight: 500 !important;
}
</style>
