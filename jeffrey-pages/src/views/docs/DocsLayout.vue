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
import { ref, onMounted, onUnmounted, computed } from 'vue';
import DocsSidebar from '@/components/docs/DocsSidebar.vue';
import DocsSearch from '@/components/docs/DocsSearch.vue';
import DocsOnThisPage from '@/components/docs/DocsOnThisPage.vue';
import { provideDocHeadings } from '@/composables/useDocHeadings';

// Provide headings context for child pages
const { headings } = provideDocHeadings();

const searchOpen = ref(false);
const mobileMenuOpen = ref(false);
const sidebarHeight = ref<number | null>(null);

const HEADER_HEIGHT = 70;

const openSearch = (): void => {
  searchOpen.value = true;
};

const closeSearch = (): void => {
  searchOpen.value = false;
};

const toggleMobileMenu = (): void => {
  mobileMenuOpen.value = !mobileMenuOpen.value;
};

const closeMobileMenu = (): void => {
  mobileMenuOpen.value = false;
};

const handleKeydown = (e: KeyboardEvent): void => {
  if (e.key === '/' && !['INPUT', 'TEXTAREA'].includes((document.activeElement as HTMLElement)?.tagName || '')) {
    e.preventDefault();
    searchOpen.value = !searchOpen.value;
  }
  if (e.key === 'Escape' && mobileMenuOpen.value) {
    mobileMenuOpen.value = false;
  }
};

// Adjust sidebar height when footer comes into view
const handleScroll = (): void => {
  const footer = document.querySelector('footer');
  if (!footer) {
    sidebarHeight.value = null;
    return;
  }

  const footerRect = footer.getBoundingClientRect();
  const viewportHeight = window.innerHeight;

  // If footer is visible (its top is within viewport)
  if (footerRect.top < viewportHeight) {
    // Calculate available height (from header to footer top)
    const availableHeight = footerRect.top - HEADER_HEIGHT;
    sidebarHeight.value = Math.max(0, availableHeight);
  } else {
    sidebarHeight.value = null;
  }
};

const sidebarStyle = computed(() => {
  if (sidebarHeight.value !== null) {
    return { height: `${sidebarHeight.value}px` };
  }
  return {};
});

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  window.addEventListener('scroll', handleScroll, { passive: true });
  handleScroll(); // Initial check
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown);
  window.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <div class="docs-layout">
    <!-- Fixed left sidebar -->
    <aside class="docs-sidebar" :class="{ 'mobile-open': mobileMenuOpen }" :style="sidebarStyle">
      <div class="docs-sidebar-inner">
        <DocsSidebar @open-search="openSearch" />
      </div>
    </aside>

    <!-- Scrollable main content area -->
    <main class="docs-main">
      <div class="docs-main-container">
        <div class="docs-content-wrapper">
          <router-view />
        </div>
        <!-- Sticky right TOC -->
        <aside class="docs-toc" v-if="headings.length > 0">
          <DocsOnThisPage :headings="headings" />
        </aside>
      </div>
    </main>

    <!-- Search modal -->
    <DocsSearch :isOpen="searchOpen" @close="closeSearch" />

    <!-- Mobile sidebar overlay -->
    <div
      v-if="mobileMenuOpen"
      class="mobile-overlay"
      @click="closeMobileMenu"
    ></div>

    <!-- Mobile menu toggle -->
    <button class="mobile-menu-btn" @click="toggleMobileMenu">
      <i class="bi" :class="mobileMenuOpen ? 'bi-x' : 'bi-list'"></i>
    </button>
  </div>
</template>

<style scoped>
/* ============================
   Layout Variables
   ============================ */
.docs-layout {
  --sidebar-width: 280px;
  --toc-width: 220px;
  --header-height: 70px;
  --content-max-width: 1500px;
  --sidebar-bg: #ffffff;
  --sidebar-border: #e8ecf1;
  --content-bg: #f8f9fa;
  --color-primary: #5e64ff;
}

/* ============================
   Main Layout Container
   ============================ */
.docs-layout {
  display: flex;
  min-height: 100vh;
  background: var(--content-bg);
}

/* ============================
   Left Sidebar (Fixed positioning for stability)
   ============================ */
.docs-sidebar {
  position: fixed;
  left: 0;
  top: var(--header-height);
  width: var(--sidebar-width);
  height: calc(100vh - var(--header-height));
  background: var(--sidebar-bg);
  border-right: 1px solid var(--sidebar-border);
  z-index: 50;
}

.docs-sidebar-inner {
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}

/* Custom scrollbar for sidebar */
.docs-sidebar-inner::-webkit-scrollbar {
  width: 4px;
}

.docs-sidebar-inner::-webkit-scrollbar-track {
  background: transparent;
}

.docs-sidebar-inner::-webkit-scrollbar-thumb {
  background: #dde2e9;
  border-radius: 4px;
}

.docs-sidebar-inner::-webkit-scrollbar-thumb:hover {
  background: #c5ccd6;
}

/* ============================
   Scrollable Main Content Area
   ============================ */
.docs-main {
  flex: 1;
  min-width: 0;
  padding: 2rem;
  margin-left: var(--sidebar-width);
  background: var(--content-bg);
}

/* Container for content + TOC */
.docs-main-container {
  display: flex;
  gap: 2rem;
  max-width: calc(var(--content-max-width) + var(--toc-width) + 2rem);
}

/* Content wrapper */
.docs-content-wrapper {
  flex: 1;
  min-width: 0;
  max-width: var(--content-max-width);
}

/* ============================
   Sticky Right TOC
   ============================ */
.docs-toc {
  position: sticky;
  top: calc(var(--header-height) + 2rem);
  width: var(--toc-width);
  min-width: var(--toc-width);
  height: fit-content;
  max-height: calc(100vh - var(--header-height) - 4rem);
  background: var(--sidebar-bg);
  border-radius: 8px;
  overflow-y: auto;
}

/* Custom scrollbar for TOC */
.docs-toc::-webkit-scrollbar {
  width: 3px;
}

.docs-toc::-webkit-scrollbar-track {
  background: transparent;
}

.docs-toc::-webkit-scrollbar-thumb {
  background: #dde2e9;
  border-radius: 3px;
}

/* ============================
   Mobile Overlay
   ============================ */
.mobile-overlay {
  display: none;
}

/* ============================
   Mobile Menu Button
   ============================ */
.mobile-menu-btn {
  display: none;
}

/* ============================
   Responsive - Hide TOC on medium screens
   ============================ */
@media (max-width: 1199px) {
  .docs-toc {
    display: none;
  }

  .docs-main-container {
    max-width: var(--content-max-width);
  }
}

/* ============================
   Responsive - Mobile Layout
   ============================ */
@media (max-width: 991px) {
  .docs-layout {
    display: block;
  }

  .docs-sidebar {
    position: fixed;
    left: 0;
    top: 0;
    height: 100vh;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.3s ease, box-shadow 0.3s ease;
    box-shadow: none;
  }

  .docs-sidebar.mobile-open {
    transform: translateX(0);
    box-shadow: 4px 0 24px rgba(0, 0, 0, 0.15);
  }

  .docs-sidebar-inner {
    position: static;
    height: 100%;
  }

  .docs-main {
    padding: 1rem;
    margin-left: 0;
  }

  .mobile-overlay {
    display: block;
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 99;
  }

  .mobile-menu-btn {
    display: flex;
    position: fixed;
    bottom: 1.5rem;
    right: 1.5rem;
    width: 50px;
    height: 50px;
    border-radius: 50%;
    background: linear-gradient(135deg, var(--color-primary) 0%, #7c3aed 100%);
    color: #fff;
    border: none;
    box-shadow: 0 4px 15px rgba(94, 100, 255, 0.4);
    align-items: center;
    justify-content: center;
    font-size: 1.5rem;
    z-index: 101;
    cursor: pointer;
    transition: transform 0.2s ease;
  }

  .mobile-menu-btn:hover {
    transform: scale(1.05);
  }
}
</style>
