<template>
  <div class="container-fluid p-0">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="performance-counters-title">
        <i class="bi bi-speedometer2 me-2"></i>
        Performance Counters
      </h2>
      <p class="text-muted fs-6">Overview of JVM/HotSpot Performance Counters and Metrics</p>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="row">
      <div class="col-12">
        <div class="d-flex justify-content-center my-5">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Content state -->
    <div v-else class="row">
      <div class="col-12">
        <div class="performance-counters-container">
          <!-- Counter summary -->
          <div class="counter-summary mb-3">
            <div class="card">
              <div class="card-body">
                <div class="row">
                  <div class="col-md-6">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ allCounters.length }}</h5>
                      <p class="summary-label">Total Counters</p>
                    </div>
                  </div>
                  <div class="col-md-6">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ categories.length }}</h5>
                      <p class="summary-label">Categories</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Search/Filter control -->
          <div class="mb-3">
            <div class="input-group search-container">
              <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
              <input 
                type="text" 
                class="form-control search-input" 
                placeholder="Filter counters..." 
                v-model="searchQuery"
                @input="filterCounters"
                aria-label="Filter counters"
                autocomplete="off"
              >
              <button 
                v-if="searchQuery" 
                class="btn btn-outline-secondary clear-btn" 
                type="button"
                @click="clearSearch"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>

          <!-- Performance Counters Tree -->
          <div class="card mb-4">
            <div class="card-body p-0">
              <table class="table table-hover mb-0 counter-tree-table">
                <thead>
                <tr>
                  <th>Counter</th>
                  <th>Value</th>
                  <th class="text-center">
                    <div class="d-flex justify-content-end align-items-center">
                      <div class="tree-controls">
                        <button class="btn btn-sm btn-outline-primary btn-xs px-1" @click="collapseAll" title="Collapse All">
                          <i class="bi bi-arrows-collapse"></i>
                        </button>
                        <button class="btn btn-sm btn-outline-primary btn-xs px-1 ms-1" @click="expandAll" title="Expand All">
                          <i class="bi bi-arrows-expand"></i>
                        </button>
                      </div>
                    </div>
                  </th>
                </tr>
                </thead>
                <tbody>
                <!-- Category nodes (folders) -->
                <template v-for="(category, categoryIndex) in organizedCounters" :key="category.name">
                  <tr class="parent-row">
                    <td>
                      <div class="d-flex align-items-center counter-name-cell">
                        <!-- Expand/collapse icon for parent nodes -->
                        <button class="btn btn-sm expand-btn p-0 me-2"
                                @click="toggleExpand(category.name)">
                          <i class="bi" :class="isExpanded(category.name) ? 'bi-dash-square' : 'bi-plus-square'"></i>
                        </button>

                        <!-- Category name -->
                        <span class="counter-category">{{ getCategoryLabel(category.name) }}</span>
                      </div>
                    </td>
                    <td></td>
                    <td></td>
                  </tr>

                  <!-- Counter items (when category is expanded) -->
                  <template v-if="isExpanded(category.name)" v-for="counter in category.counters" :key="`${category.name}-${counter.key}`">
                    <tr class="leaf-row">
                      <td>
                        <div class="d-flex align-items-center counter-name-cell">
                          <!-- Indentation for counter items -->
                          <div class="tree-indent" style="width: 20px;"></div>

                          <!-- Counter leaf indicator -->
                          <span class="tree-leaf-icon me-2">
                            <i class="bi bi-circle-fill"></i>
                          </span>

                          <!-- Counter name -->
                          <span class="counter-key" :title="counter.key">{{ formatCounterKey(counter.key) }}</span>
                          
                          <!-- Question mark icon with description tooltip -->
                          <i v-if="counter.description" 
                             class="bi bi-question-circle-fill ms-2 description-icon" 
                             :title="counter.description"
                             data-bs-toggle="tooltip" 
                             data-bs-placement="top">
                          </i>
                          
                          <!-- JVM and Java badges -->
                          <span v-if="getBadgeForKey(counter.key)" 
                                class="badge ms-3"
                                :class="getBadgeClass(counter.key)">
                            {{ getBadgeForKey(counter.key) }}
                          </span>
                        </div>
                      </td>
                      <td>
                        <span class="counter-value">{{ counter.formattedValue }}</span>
                        <span v-if="counter.formattedValue !== counter.value" class="text-muted ms-2 small">
                          (raw: {{ counter.value }})
                        </span>
                      </td>
                      <td></td>
                    </tr>
                  </template>
                </template>
                </tbody>
              </table>
            </div>
          </div>

          <!-- No Results message -->
          <div v-if="organizedCounters.length === 0" class="alert alert-info">
            <i class="bi bi-info-circle me-2"></i>
            No performance counters found matching your search criteria.
          </div>

          <!-- Counter stats -->
          <div v-if="organizedCounters.length > 0 && allCounters.length !== filteredCounters.length" class="mb-3 text-muted small">
            Showing {{ filteredCounters.length }} of {{ allCounters.length }} counters
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import { onMounted, ref, computed, nextTick } from 'vue';
import * as bootstrap from 'bootstrap';
import ProfilePerformanceCountersClient from '@/services/ProfilePerformanceCountersClient';
import PerformanceCounter from "@/services/model/PerformanceCounter.ts";
import PerformanceCounterEnhanced from "@/services/model/PerformanceCounterEnhanced.ts";

const route = useRoute();
const { workspaceId, projectId } = useNavigation();

// State
const loading = ref(true);
const allCounters = ref<PerformanceCounterEnhanced[]>([]);
const filteredCounters = ref<PerformanceCounterEnhanced[]>([]);
const searchQuery = ref('');
const sortField = ref('key');
const sortDirection = ref<'asc' | 'desc'>('asc');
const expandedCategories = ref<Set<string>>(new Set());

// Computed properties
const categories = computed(() => {
  const uniqueCategories = new Set<string>();
  allCounters.value.forEach(counter => {
    uniqueCategories.add(counter.category);
  });
  
  return Array.from(uniqueCategories).sort();
});

// Organized counters by category
const organizedCounters = computed(() => {
  const result: { name: string; counters: PerformanceCounterEnhanced[] }[] = [];
  
  // Process each category
  for (const category of categories.value) {
    // Filter counters for this category
    const categoryCounters = filteredCounters.value.filter(
      counter => counter.category === category
    );
    
    // Only include categories that have counters matching the current filters
    if (categoryCounters.length > 0) {
      result.push({
        name: category,
        counters: categoryCounters
      });
    }
  }
  
  // Sort categories by name
  return result.sort((a, b) => {
    return getCategoryLabel(a.name).localeCompare(getCategoryLabel(b.name));
  });
});

// Methods
const loadPerformanceCounters = async () => {
  loading.value = true;
  try {
    // Use the ProfilePerformanceCountersClient to fetch real data
    const profileId = route.params.profileId as string;
    const counters: PerformanceCounterEnhanced[] = await ProfilePerformanceCountersClient.get(workspaceId.value!, projectId.value!, profileId);
    
    // Process counters to use the second part of the key for category determination
    allCounters.value = counters
    
    // All categories start collapsed by default
    filterCounters();
  } catch (error) {
    console.error('Failed to load performance counters:', error);
  } finally {
    loading.value = false;
  }
};

const filterCounters = () => {
  let result = [...allCounters.value];
  
  // Filter by search query
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    result = result.filter(counter => 
      counter.key.toLowerCase().includes(query) || 
      getCategoryLabel(counter.category).toLowerCase().includes(query)
    );
    
    // Auto-expand categories that have matching counters
    if (result.length > 0) {
      // Get the categories of counters that match the search
      const matchingCategories = new Set(result.map(counter => counter.category));
      
      // Expand these categories
      matchingCategories.forEach(category => {
        expandedCategories.value.add(category);
      });
    }
  }

  // Apply sorting
  result.sort((a, b) => {
    let valueA = a[sortField.value as keyof PerformanceCounter] as string;
    let valueB = b[sortField.value as keyof PerformanceCounter] as string;
    
    // For sorting values numerically when appropriate
    if (sortField.value === 'value' && !isNaN(Number(valueA)) && !isNaN(Number(valueB))) {
      return sortDirection.value === 'asc' 
        ? Number(valueA) - Number(valueB) 
        : Number(valueB) - Number(valueA);
    }

    // String comparison
    return sortDirection.value === 'asc'
      ? String(valueA).localeCompare(String(valueB))
      : String(valueB).localeCompare(String(valueA));
  });
  
  filteredCounters.value = result;
};

const clearSearch = () => {
  searchQuery.value = '';
  filterCounters();
};

const formatCounterKey = (key: string): string => {
  // Return the entire key without stripping any prefix
  return key;
};

const extractKeySecondPart = (key: string): string | null => {
  const parts = key.split('.');
  if (parts.length > 1) {
    return parts[1];
  }
  return null;
};

const getCategoryLabel = (category: string): string => {
  // Map category codes to readable labels
  const categoryMap: Record<string, string> = {
    'ci': 'JIT Compiler',
    'cls': 'ClassLoader',  // Changed from 'Bytecode' to 'ClassLoader'
    'classloader': 'ClassLoader',
    'property': 'Property',
    'rt': 'Runtime',
    'threads': 'Threads',
    'gc': 'Garbage Collector',
    'os': 'Operating System',
    'zip': 'ZIP',
    'urlClassLoader': 'ClassLoader',
  };
  
  return categoryMap[category] || category;
};

const getCategoryClass = (category: string): string => {
  // Map categories to Bootstrap color classes
  const classMap: Record<string, string> = {
    'Compiler': 'bg-primary',
    'ClassLoader': 'bg-info',
    'Bytecode': 'bg-info',  // Added style for Bytecode category
    'Property': 'bg-secondary',
    'Runtime': 'bg-success',
    'Threads': 'bg-warning',
    'Garbage Collector': 'bg-danger',
    'Operating System': 'bg-dark',
    'ZIP': 'bg-secondary'
  };
  
  // Use the mapped category name, not the original code
  const categoryLabel = getCategoryLabel(category);
  return classMap[categoryLabel] || 'bg-secondary';
};

const extractKeyPrefix = (key: string): string | null => {
  const parts = key.split('.');
  if (parts.length > 0) {
    return parts[0];
  }
  return null;
};

const getBadgeForKey = (key: string): string | null => {
  const prefix = extractKeyPrefix(key);
  if (prefix === 'sun') {
    return 'Unsupported';
  } else if (prefix === 'java') {
    return 'Supported';
  }
  return null;
};

const getBadgeClass = (key: string): string => {
  const prefix = extractKeyPrefix(key);
  if (prefix === 'sun') {
    return 'bg-orange text-white'; // JVM badge style - changed to orange
  } else if (prefix === 'java') {
    return 'bg-dark-green text-white'; // Java badge style - dark green
  }
  return 'bg-secondary text-white';
};

// Use the second part of the key for category determination
const getCategoryFromKey = (key: string): string => {
  const secondPart = extractKeySecondPart(key);
  
  // Map specific categories to a common category to merge them
  if (secondPart === 'urlClassLoader' || secondPart === 'cls' || secondPart === 'classloader') {
    return 'classloader'; // Merge all classloader-related categories
  }
  
  return secondPart || 'unknown';
};

// Toggle expand/collapse of category nodes
const toggleExpand = (category: string) => {
  if (expandedCategories.value.has(category)) {
    expandedCategories.value.delete(category);
  } else {
    expandedCategories.value.add(category);
  }
};

// Check if a category is expanded
const isExpanded = (category: string) => {
  return expandedCategories.value.has(category);
};

// Expand all categories
const expandAll = () => {
  categories.value.forEach(category => {
    expandedCategories.value.add(category);
  });
};

// Collapse all categories
const collapseAll = () => {
  expandedCategories.value.clear();
};

onMounted(() => {
  loadPerformanceCounters();
  
  // Initialize Bootstrap tooltips after the DOM has been updated
  nextTick(() => {
    const tooltips = document.querySelectorAll('[data-bs-toggle="tooltip"]');
    tooltips.forEach(tooltip => {
      new bootstrap.Tooltip(tooltip);
    });
  });
});
</script>

<style scoped>
.performance-counters-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .performance-counters-title {
    font-size: 1.5rem;
  }
}

.performance-counters-container {
  width: 100%;
}

/* Table styles */
.counter-tree-table {
  width: 100%;
  table-layout: fixed;
}

.counter-tree-table th:nth-child(1) {
  width: 50%;
}

.counter-tree-table th:nth-child(2) {
  width: 47%;
}

.counter-tree-table th:nth-child(3) {
  width: 3%;
}

.counter-name-cell {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-indent {
  display: inline-block;
  height: 1px;
}

.tree-leaf-icon {
  display: inline-block;
  width: 20px;
  text-align: center;
  font-size: 6px;
  vertical-align: middle;
  color: #adb5bd;
}

.expand-btn {
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #6c757d;
  background: transparent;
  border: none;
  padding: 0;
}

.tree-controls {
  display: flex;
  align-items: center;
}

.btn-xs {
  font-size: 0.7rem;
  line-height: 1;
  padding: 0.1rem 0.2rem;
}

.expand-btn:hover {
  color: #0d6efd;
}

.counter-category {
  font-weight: 600;
}

.counter-key {
  font-family: monospace;
  font-size: 0.9rem;
}

.counter-value {
  font-weight: 500;
}

.parent-row {
  background-color: #f8f9fa;
}

.badge {
  font-size: 0.8rem;
  font-weight: 500;
  padding: 0.3em 0.6em;
}

/* Summary styles */
.counter-summary .card {
  border: none;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.summary-item {
  text-align: center;
  padding: 0.5rem;
}

.summary-value {
  margin-bottom: 0.25rem;
  font-weight: 600;
  color: #495057;
}

.summary-label {
  margin-bottom: 0;
  font-size: 0.875rem;
  color: #6c757d;
}

/* Search Styles */
.search-container {
  box-shadow: 0 0.125rem 0.25rem rgba(0, 0, 0, 0.075);
  border-radius: 0.25rem;
  overflow: hidden;
}

.search-container .input-group-text {
  background-color: #fff;
  border-right: none;
  padding: 0 0.75rem;
  display: flex;
  align-items: center;
  height: 38px;
}

.search-icon {
  font-size: 0.85rem;
  color: #6c757d;
}

.search-input {
  border-left: none;
  font-size: 0.875rem;
  height: 38px;
  padding: 0.375rem 0.75rem;
  line-height: 1.5;
}

.search-input:focus {
  box-shadow: none;
  border-color: #ced4da;
}

.clear-btn {
  border-color: #ced4da;
  border-left: none;
  background-color: #fff;
  padding: 0 0.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 38px;
}

.clear-btn:hover {
  background-color: #f8f9fa;
}

.clear-btn i {
  font-size: 0.75rem;
}

/* Custom badge colors */
.bg-dark-green {
  background-color: #006400; /* Dark green color */
}

.bg-orange {
  background-color: #FFA500; /* Orange color */
}

/* Description icon style */
.description-icon {
  font-size: 0.75rem;
  color: #6c757d;
  cursor: help;
  transition: color 0.2s ease;
}

.description-icon:hover {
  color: #0d6efd;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .counter-tree-table {
    table-layout: auto;
  }

  .counter-name-cell {
    max-width: 300px;
  }
}
</style>
