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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import Fuse from 'fuse.js';
import { getAllDocs } from '@/composables/useDocsNavigation';
import type { SearchableDoc } from '@/types/docs';

interface Props {
  isOpen?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  isOpen: false
});

const emit = defineEmits<{
  (e: 'close'): void;
}>();

const router = useRouter();
const searchQuery = ref('');
const selectedIndex = ref(0);
const searchInput = ref<HTMLInputElement | null>(null);

// Initialize Fuse.js for fuzzy search
const allDocs = getAllDocs();
const fuse = new Fuse(allDocs, {
  keys: ['title', 'section'],
  threshold: 0.3,
  includeMatches: true
});

const results = computed(() => {
  if (!searchQuery.value.trim()) {
    return allDocs.slice(0, 5); // Show first 5 when empty
  }
  return fuse.search(searchQuery.value).slice(0, 10).map(r => r.item);
});

const navigateToResult = (result: SearchableDoc): void => {
  router.push(result.path);
  emit('close');
  searchQuery.value = '';
};

const handleKeydown = (e: KeyboardEvent): void => {
  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault();
      selectedIndex.value = Math.min(selectedIndex.value + 1, results.value.length - 1);
      break;
    case 'ArrowUp':
      e.preventDefault();
      selectedIndex.value = Math.max(selectedIndex.value - 1, 0);
      break;
    case 'Enter':
      e.preventDefault();
      if (results.value[selectedIndex.value]) {
        navigateToResult(results.value[selectedIndex.value]);
      }
      break;
    case 'Escape':
      emit('close');
      break;
  }
};

watch(() => props.isOpen, (isOpen) => {
  if (isOpen) {
    searchQuery.value = '';
    selectedIndex.value = 0;
    setTimeout(() => {
      searchInput.value?.focus();
    }, 100);
  }
});

watch(searchQuery, () => {
  selectedIndex.value = 0;
});

// Global keyboard shortcut
const handleGlobalKeydown = (e: KeyboardEvent): void => {
  if (e.key === '/' && !['INPUT', 'TEXTAREA'].includes((document.activeElement as HTMLElement)?.tagName || '')) {
    e.preventDefault();
    emit('close'); // Toggle - if already open this will close
  }
};

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown);
});

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown);
});
</script>

<template>
  <Teleport to="body">
    <div v-if="isOpen" class="search-overlay" @click.self="emit('close')">
      <div class="search-modal">
        <div class="search-header">
          <i class="bi bi-search"></i>
          <input
            ref="searchInput"
            v-model="searchQuery"
            type="text"
            placeholder="Search documentation..."
            class="search-input"
            @keydown="handleKeydown"
          />
          <kbd>ESC</kbd>
        </div>

        <div class="search-results">
          <div v-if="results.length === 0" class="no-results">
            <i class="bi bi-search"></i>
            <p>No results found for "{{ searchQuery }}"</p>
          </div>

          <div
            v-for="(result, index) in results"
            :key="result.path"
            class="search-result"
            :class="{ 'selected': index === selectedIndex }"
            @click="navigateToResult(result)"
            @mouseenter="selectedIndex = index"
          >
            <div class="result-icon">
              <i class="bi bi-file-text"></i>
            </div>
            <div class="result-content">
              <div class="result-title">{{ result.title }}</div>
              <div class="result-section">{{ result.section }}</div>
            </div>
            <i class="bi bi-arrow-return-left result-enter"></i>
          </div>
        </div>

        <div class="search-footer">
          <div class="search-hint">
            <kbd><i class="bi bi-arrow-up"></i></kbd>
            <kbd><i class="bi bi-arrow-down"></i></kbd>
            <span>to navigate</span>
          </div>
          <div class="search-hint">
            <kbd><i class="bi bi-arrow-return-left"></i></kbd>
            <span>to select</span>
          </div>
          <div class="search-hint">
            <kbd>ESC</kbd>
            <span>to close</span>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.search-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  z-index: 2000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 10vh;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.search-modal {
  width: 600px;
  max-width: 90vw;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  animation: slideIn 0.2s ease;
}

@keyframes slideIn {
  from {
    opacity: 0;
    transform: scale(0.95) translateY(-10px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

.search-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #e9ecef;
}

.search-header i {
  color: #6c757d;
  font-size: 1.1rem;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 1.1rem;
  color: #343a40;
}

.search-input::placeholder {
  color: #adb5bd;
}

.search-header kbd {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
  color: #6c757d;
}

.search-results {
  max-height: 400px;
  overflow-y: auto;
  padding: 0.5rem;
}

.no-results {
  text-align: center;
  padding: 2rem;
  color: #6c757d;
}

.no-results i {
  font-size: 2rem;
  margin-bottom: 0.5rem;
  display: block;
}

.search-result {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 1rem;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.search-result:hover,
.search-result.selected {
  background: linear-gradient(135deg, rgba(0,123,255,0.08) 0%, rgba(0,123,255,0.04) 100%);
}

.result-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: #fff;
  font-size: 1rem;
}

.result-content {
  flex: 1;
}

.result-title {
  font-weight: 600;
  color: #343a40;
  font-size: 0.95rem;
}

.result-section {
  font-size: 0.8rem;
  color: #6c757d;
  margin-top: 0.125rem;
}

.result-enter {
  color: #adb5bd;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.search-result.selected .result-enter,
.search-result:hover .result-enter {
  opacity: 1;
}

.search-footer {
  display: flex;
  justify-content: center;
  gap: 1.5rem;
  padding: 0.75rem;
  background: #f8f9fa;
  border-top: 1px solid #e9ecef;
}

.search-hint {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  font-size: 0.75rem;
  color: #6c757d;
}

.search-hint kbd {
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 3px;
  padding: 0.125rem 0.25rem;
  font-size: 0.7rem;
}

/* Custom scrollbar */
.search-results::-webkit-scrollbar {
  width: 6px;
}

.search-results::-webkit-scrollbar-track {
  background: transparent;
}

.search-results::-webkit-scrollbar-thumb {
  background: #dee2e6;
  border-radius: 3px;
}
</style>
