<template>
  <div class="container-fluid p-0">
    <!-- Header Section -->
    <div class="mb-4">
      <h2 class="event-types-title">
        <i class="bi bi-list-check me-2"></i>
        Event Types
      </h2>
      <p class="text-muted fs-6">Overview of profile event types</p>
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

    <!-- Error state -->
    <div v-else-if="error" class="row">
      <div class="col-12">
        <div class="alert alert-danger" role="alert">
          Failed to load event types. Please try again later.
        </div>
      </div>
    </div>

    <!-- Content state -->
    <div v-else class="row">
      <div class="col-12">
        <div class="event-types-container">
          <!-- Search/Filter control -->
          <div class="search-container mb-3">
            <div class="input-group d-flex align-items-center">
              <span class="input-group-text d-flex align-items-center">
                <i class="bi bi-search"></i>
              </span>
              <input 
                type="text" 
                class="form-control" 
                placeholder="Search events..." 
                v-model="searchText"
                @input="filterEvents"
              >
              <button class="btn btn-outline-secondary d-flex align-items-center justify-content-center" type="button" @click="searchText = ''; filterEvents()">
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
            <div class="filter-options mt-2 d-flex align-items-center">
              <div class="form-check form-check-inline">
                <input class="form-check-input" type="checkbox" id="showEmpty" v-model="showEmpty" @change="filterEvents">
                <label class="form-check-label" for="showEmpty">Show empty (0 count)</label>
              </div>
              <div class="form-check form-check-inline">
                <input class="form-check-input" type="checkbox" id="onlyWithStack" v-model="onlyWithStack" @change="filterEvents">
                <label class="form-check-label" for="onlyWithStack">Only with stack trace</label>
              </div>
            </div>
          </div>

          <!-- Event summary -->
          <div class="event-summary mb-3">
            <div class="card">
              <div class="card-body">
                <div class="row">
                  <div class="col-md-4">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ filteredEventCount }}</h5>
                      <p class="summary-label">Filtered Events</p>
                    </div>
                  </div>
                  <div class="col-md-4">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ totalEventCount }}</h5>
                      <p class="summary-label">Total Events</p>
                    </div>
                  </div>
                  <div class="col-md-4">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ totalEventTypes }}</h5>
                      <p class="summary-label">Event Types</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Event tree table -->
          <div class="card mb-4">
            <div class="card-body p-0">
              <table class="table table-hover mb-0 event-tree-table">
                <thead>
                  <tr>
                    <th>Event Type</th>
                    <th class="text-center">Count</th>
                    <th class="text-center">Stack Trace</th>
                    <th class="text-center">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="(node, index) in filteredEvents" :key="node.key">
                    <tr :class="{ 'parent-row': !node.leaf, 'leaf-row': node.leaf }">
                      <td>
                        <div class="d-flex align-items-center event-name-cell">
                          <!-- Indentation based on level -->
                          <div class="tree-indent" :style="{ 'width': `${getTreeLevel(node.key) * 20}px` }"></div>
                          
                          <!-- Expand/collapse icon for parent nodes -->
                          <button v-if="!node.leaf" 
                                  class="btn btn-sm expand-btn p-0 me-2"
                                  @click="toggleExpand(node.key)">
                            <i class="bi" :class="isExpanded(node.key) ? 'bi-dash-square' : 'bi-plus-square'"></i>
                          </button>
                          <span v-else class="tree-leaf-icon me-2">
                            <i class="bi bi-circle-fill"></i>
                          </span>
                          
                          <!-- Node name -->
                          <span class="event-name">{{ node.data.name }}</span>
                          
                          <!-- Event code for leaf nodes -->
                          <span v-if="node.data.code" class="event-code ms-2">{{ node.data.code }}</span>
                        </div>
                      </td>
                      <td class="text-center">
                        <span v-if="node.data.count !== undefined" class="badge bg-secondary">
                          {{ formatNumber(node.data.count) }}
                        </span>
                      </td>
                      <td class="text-center">
                        <i v-if="node.data.withStackTrace" class="bi bi-check-lg text-success"></i>
                        <i v-else-if="node.leaf" class="bi bi-x-lg text-danger"></i>
                      </td>
                      <td class="text-center">
                        <button 
                          v-if="node.leaf && node.data.count && node.data.count > 0" 
                          class="btn btn-sm btn-primary action-btn"
                          @click="viewEventDetails(node)"
                          title="View event details"
                        >
                          <i class="bi bi-eye"></i> View
                        </button>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
          </div>

          <!-- No Results message -->
          <div v-if="filteredEvents.length === 0" class="alert alert-info">
            No events match your filter criteria. Try adjusting your search terms or filter options.
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute } from 'vue-router';
import EventViewerService from '@/services/viewer/EventViewerService';
import EventType from '@/services/viewer/model/EventType';

// Props definition
const props = defineProps({
  profile: {
    type: Object,
    required: true
  },
  secondaryProfile: {
    type: Object,
    default: null
  }
});

const route = useRoute();
const projectId = route.params.projectId as string;
const profileId = route.params.profileId as string;

// State variables
const loading = ref(true);
const error = ref(false);
const allEvents = ref<EventType[]>([]);
const filteredEvents = ref<EventType[]>([]);
const expandedNodes = ref<Set<string>>(new Set());
const searchText = ref('');
const showEmpty = ref(true);
const onlyWithStack = ref(false);
const totalEventCount = ref(0);
const totalEventTypes = ref(0);
const filteredEventCount = ref(0);

// Create an instance of the service with the project and profile IDs
const eventViewerService = new EventViewerService(projectId, profileId);

onMounted(async () => {
  try {
    // Load event types from the service
    const events = await eventViewerService.allEventTypes();
    allEvents.value = events;
    
    // All categories start collapsed by default
    
    // Calculate totals
    countTotalEvents();
    
    // Apply initial filtering
    filterEvents();
    
    loading.value = false;
  } catch (err) {
    console.error('Failed to load event types:', err);
    error.value = true;
    loading.value = false;
  }
});

// Calculate total events and event type counts
const countTotalEvents = () => {
  let eventCount = 0;
  let typeCount = 0;
  
  const countEvents = (events: EventType[]) => {
    for (const event of events) {
      if (event.data.count) {
        eventCount += event.data.count;
      }
      
      if (event.leaf && event.data.code) {
        typeCount++;
      }
      
      if (event.children && event.children.length > 0) {
        countEvents(event.children);
      }
    }
  };
  
  countEvents(allEvents.value);
  totalEventCount.value = eventCount;
  totalEventTypes.value = typeCount;
};

// Toggle expand/collapse of tree nodes
const toggleExpand = (key: string) => {
  if (expandedNodes.value.has(key)) {
    expandedNodes.value.delete(key);
  } else {
    expandedNodes.value.add(key);
  }
  filterEvents();
};

// Check if a node is expanded
const isExpanded = (key: string) => {
  return expandedNodes.value.has(key);
};

// Get the tree level from the key
const getTreeLevel = (key: string) => {
  return key.split('-').length - 1;
};

// Format numbers with thousands separators
const formatNumber = (num: number) => {
  return num.toLocaleString();
};

// Helper function to find if node has any matching leaf descendants
const findMatchingLeafDescendant = (node: EventType): boolean => {
  if (!node.children || node.children.length === 0) {
    return false;
  }
  
  for (const child of node.children) {
    if (child.leaf) {
      // Check if this leaf matches the search
      if (child.data.name.toLowerCase().includes(searchText.value.toLowerCase()) || 
          (child.data.code && child.data.code.toLowerCase().includes(searchText.value.toLowerCase()))) {
        return true;
      }
    } else {
      // Recursively check this child's descendants
      if (findMatchingLeafDescendant(child)) {
        return true;
      }
    }
  }
  
  return false;
};

// View event details
const viewEventDetails = (node: EventType) => {
  // In a real implementation, this would navigate to event details or open a modal
  alert(`Viewing details for ${node.data.name} (${node.data.code})`);
  
  // Could also use the service to fetch actual event data:
  // if (node.data.code) {
  //   eventViewerService.events(node.data.code)
  //     .then(data => {
  //       // Show event details
  //     });
  // }
};

// Filter and flatten the event tree based on search and filters
const filterEvents = () => {
  const search = searchText.value.toLowerCase().trim();
  let filteredCount = 0;
  
  // Recursive function to filter and flatten the tree
  const processNode = (node: EventType, isVisible: boolean, path: string[] = []): EventType[] => {
    const currentPath = [...path, node.data.name];
    
    // Only match leaf nodes by name and code
    const nodeMatches = node.leaf && (
      node.data.name.toLowerCase().includes(search) || 
      (node.data.code && node.data.code.toLowerCase().includes(search))
    );
    
    // Check if node matches all filters
    const matchesFilter = (
      // Show empty filter
      (showEmpty.value || (node.data.count !== undefined && node.data.count > 0)) &&
      // Only with stack trace filter (only apply to leaf nodes)
      (!onlyWithStack.value || !node.leaf || (node.data.withStackTrace === true))
    );
    
    // This node is visible if it matches the search/filters or any parent is visible
    const nodeShouldBeVisible = isVisible || (nodeMatches && matchesFilter);
    
    // For parent nodes (categories), process children first
    if (!node.leaf) {
      let result: EventType[] = [];
      let hasMatchingDescendant = false;
      
      // Auto-expand nodes that have matching descendants
      if (search && !isExpanded(node.key)) {
        const hasMatchingLeafDescendant = findMatchingLeafDescendant(node);
        if (hasMatchingLeafDescendant) {
          expandedNodes.value.add(node.key);
        }
      }
      
      // Process children if the parent is expanded
      if (isExpanded(node.key) && node.children) {
        for (const child of node.children) {
          const childResults = processNode(child, nodeShouldBeVisible, currentPath);
          if (childResults.length > 0) {
            hasMatchingDescendant = true;
            result = [...result, ...childResults];
          }
        }
      }
      
      // When searching, include parent/category node when it has matching descendants
      if (search && (hasMatchingDescendant || nodeMatches)) {
        return [node, ...result];
      }
      
      // When not searching, show all nodes according to expanded state
      if (!search) {
        if (result.length > 0) {
          return [node, ...result];
        } else if (matchesFilter) {
          return [node];
        }
      }
      
      return result;
    }
    
    // For leaf nodes
    if (node.leaf) {
      // Include the node if it's visible and matches filters
      if (nodeShouldBeVisible && matchesFilter) {
        if (node.data.count) {
          filteredCount += node.data.count;
        }
        return [node];
      }
      return [];
    }
    
    return result;
  };
  
  // Start with empty result
  let result: EventType[] = [];
  
  // Process each root node
  for (const rootNode of allEvents.value) {
    result = [...result, ...processNode(rootNode, false)];
  }
  
  filteredEvents.value = result;
  filteredEventCount.value = filteredCount;
};
</script>

<style scoped>
.event-types-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

@media (max-width: 768px) {
  .event-types-title {
    font-size: 1.5rem;
  }
}

.event-types-container {
  background-color: #fff;
  border-radius: 0.4rem;
}

/* Table styles */
.event-tree-table {
  width: 100%;
  table-layout: fixed;
}

.event-tree-table th:nth-child(1) {
  width: 60%;
}

.event-tree-table th:nth-child(2) {
  width: 15%;
}

.event-tree-table th:nth-child(3) {
  width: 10%;
}

.event-tree-table th:nth-child(4) {
  width: 15%;
}

.event-name-cell {
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

.expand-btn:hover {
  color: #0d6efd;
}

.event-name {
  font-weight: 500;
}

.event-code {
  color: #6c757d;
  font-size: 0.9em;
  font-family: monospace;
}

.parent-row {
  background-color: #f8f9fa;
}

.parent-row .event-name {
  font-weight: 600;
}

.action-btn {
  padding: 0.25rem 0.5rem;
  font-size: 0.75rem;
}

/* Summary styles */
.event-summary .card {
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

/* Search container */
.search-container {
  padding: 0.5rem;
}

.input-group-text, 
.input-group .btn,
.input-group .form-control {
  height: 38px;
  display: flex;
  align-items: center;
}

.input-group-text i,
.input-group .btn i {
  font-size: 0.875rem;
}

/* Remove blue focus outline */
.form-control:focus {
  box-shadow: none;
  border-color: #ced4da;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .event-tree-table {
    table-layout: auto;
  }
  
  .event-name-cell {
    max-width: 300px;
  }
}
</style>