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
import { ref, onMounted, onUnmounted, watch } from 'vue';
import type { DocHeading } from '@/types/docs';

interface Props {
  headings?: DocHeading[];
}

const props = withDefaults(defineProps<Props>(), {
  headings: () => []
});

const activeId = ref('');
const HEADER_OFFSET = 100; // Header height + padding for detection

// Use scroll listener instead of IntersectionObserver for reliable active state
let ticking = false;

const updateActiveHeading = (): void => {
  if (props.headings.length === 0) return;

  let currentActive = '';

  // Find the last heading that has scrolled past the header threshold
  // This gives us the heading that's currently "in view" at the top
  for (const heading of props.headings) {
    const element = document.getElementById(heading.id);
    if (element) {
      const rect = element.getBoundingClientRect();
      // Heading is "active" when its top is at or above the detection line
      if (rect.top <= HEADER_OFFSET) {
        currentActive = heading.id;
      }
    }
  }

  // If no heading has scrolled past the threshold, default to first heading
  if (!currentActive) {
    activeId.value = props.headings[0].id;
  } else {
    activeId.value = currentActive;
  }
};

const handleScroll = (): void => {
  if (!ticking) {
    window.requestAnimationFrame(() => {
      updateActiveHeading();
      ticking = false;
    });
    ticking = true;
  }
};

const scrollToHeading = (id: string): void => {
  const element = document.getElementById(id);
  if (element) {
    const elementPosition = element.getBoundingClientRect().top + window.scrollY;
    window.scrollTo({
      top: elementPosition - HEADER_OFFSET + 10, // Slight adjustment so heading is clearly visible
      behavior: 'smooth'
    });
    // Set active immediately for better UX
    activeId.value = id;
  }
};

watch(() => props.headings, () => {
  setTimeout(updateActiveHeading, 100);
}, { deep: true });

onMounted(() => {
  window.addEventListener('scroll', handleScroll, { passive: true });
  setTimeout(updateActiveHeading, 100);
});

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll);
});
</script>

<template>
  <div class="toc-content" v-if="headings.length > 0">
    <div class="toc-header">On this page</div>
    <nav class="toc-nav">
      <a
        v-for="heading in headings"
        :key="heading.id"
        :href="`#${heading.id}`"
        class="toc-item"
        :class="[
          `level-${heading.level}`,
          { 'active': activeId === heading.id }
        ]"
        @click.prevent="scrollToHeading(heading.id)"
      >
        {{ heading.text }}
      </a>
    </nav>
  </div>
</template>

<style scoped>
/* ============================
   TOC Content Container
   ============================ */
.toc-content {
  padding: 1.5rem 1rem;
  height: 100%;
  --color-primary: #5e64ff;
  --color-text-secondary: #5e6e82;
  --color-text-muted: #6c757d;
}

/* ============================
   TOC Header
   ============================ */
.toc-header {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--color-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 1rem;
  padding-left: 0.75rem;
}

/* ============================
   TOC Navigation
   ============================ */
.toc-nav {
  display: flex;
  flex-direction: column;
}

/* ============================
   TOC Items
   ============================ */
.toc-item {
  display: block;
  padding: 0.375rem 0.75rem;
  font-size: 0.8rem;
  color: var(--color-text-secondary);
  text-decoration: none;
  border-left: 2px solid transparent;
  transition: all 0.15s ease;
  line-height: 1.4;
}

.toc-item:hover {
  color: var(--color-primary);
}

.toc-item.active {
  color: var(--color-primary);
  border-left-color: var(--color-primary);
  font-weight: 500;
}

/* ============================
   Nested Levels
   ============================ */
.toc-item.level-3 {
  padding-left: 1.5rem;
  font-size: 0.75rem;
}

.toc-item.level-4 {
  padding-left: 2.25rem;
  font-size: 0.7rem;
}
</style>
