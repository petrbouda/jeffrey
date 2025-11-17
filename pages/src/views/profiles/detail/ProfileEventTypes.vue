<template>
    <PageHeader 
      title="Event Types"
      description="Overview of profile event types"
      icon="bi-list-check"
    >

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
          Failed to load event types.
        </div>
      </div>
    </div>

    <!-- Content state -->
    <div v-else class="row">
      <div class="col-12">
        <div class="event-types-container">
          <!-- Event summary -->
          <div class="event-summary mb-3">
            <div class="card">
              <div class="card-body">
                <div class="row">
                  <div class="col-md-6">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ totalEventCount }}</h5>
                      <p class="summary-label">Total Events</p>
                    </div>
                  </div>
                  <div class="col-md-6">
                    <div class="summary-item">
                      <h5 class="summary-value">{{ totalEventTypes }}</h5>
                      <p class="summary-label">Event Types</p>
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
                  placeholder="Search events..."
                  v-model="searchText"
                  @input="filterEvents"
              >
              <button 
                v-if="searchText"
                class="btn btn-outline-secondary clear-btn" 
                type="button"
                @click="searchText = ''; filterEvents()">
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </div>

          <!-- Event tree table -->
          <div class="card mb-4">
            <div class="card-body p-0">
              <table class="table table-hover mb-0 event-tree-table">
                <thead>
                <tr>
                  <th>Event Type</th>
                  <th class="text-center">
                    <div class="d-flex justify-content-end align-items-center">
                      <span class="me-3">Actions</span>
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

                        <!-- Count badge -->
                        <Badge v-if="node.data.count !== undefined"
                               :value="formatNumber(node.data.count)"
                               :variant="node.data.count > 0 ? 'primary' : 'secondary'"
                               size="xs"
                               class="ms-2" />

                        <!-- Event type badge -->
                        <Badge v-if="node.leaf && node.data.source === 'JDK'" value="JDK" variant="info" size="xs" class="ms-2" />
                        <Badge v-else-if="node.leaf && node.data.source === 'Async-Profiler'" value="Async-Profiler" variant="purple" size="xs" class="ms-2" />
                        <Badge v-else-if="node.leaf && node.data.code" value="Custom" variant="secondary" size="xs" class="ms-2" />

                        <!-- Stack trace indicator -->
                        <i v-if="node.leaf && node.data.withStackTrace" class="bi bi-layers ms-2 text-success"
                           title="Has stack traces"></i>
                      </div>
                    </td>
                    <td class="text-center">
                      <div class="d-flex justify-content-end gap-2">
                        <button
                            v-if="node.leaf && node.data.withStackTrace && node.data.count && node.data.count > 0"
                            class="btn btn-sm btn-danger action-btn"
                            @click="viewFlamegraph(node)"
                            title="View event flamegraph"
                        >
                          <i class="bi bi-fire"></i> Flame
                        </button>
                        <button
                            v-if="node.leaf && !node.data.withStackTrace && node.data.count && node.data.count > 0"
                            class="btn btn-sm btn-warning action-btn"
                            @click="viewTimeSeries(node)"
                            title="View event time series"
                        >
                          <i class="bi bi-graph-up"></i> TimeSeries
                        </button>
                        <button
                            v-if="node.leaf && node.data.count && node.data.count > 0"
                            class="btn btn-sm btn-primary action-btn"
                            @click="viewEventDetails(node)"
                            title="View event details"
                        >
                          <i class="bi bi-eye"></i> View
                        </button>
                        <a
                            v-if="node.leaf && node.data.code && node.data.code.startsWith('jdk.')"
                            :href="`https://sap.github.io/SapMachine/jfrevents/24.html#${node.data.code.replace('jdk.', '').toLowerCase()}`"
                            target="_blank"
                            class="btn btn-sm btn-secondary action-btn"
                            title="View JFR event documentation"
                        >
                          <i class="bi bi-box-arrow-up-right"></i> Docs
                        </a>
                      </div>
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

  <!-- Flamegraph Modal -->
  <div class="modal fade" id="flamegraphModal" tabindex="-1" aria-labelledby="flamegraphModalLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg" style="width: 95vw; max-width: 95%;">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title" id="flamegraphModalLabel">{{ selectedEventCode }}</h5>
          <button type="button" class="btn-close" @click="closeModal" aria-label="Close"></button>
        </div>
        <div id="scrollable-wrapper" class="modal-body p-3" v-if="showFlamegraphDialog">
          <TimeseriesComponent
              :graph-type="GraphType.PRIMARY"
              :event-type="selectedEventCode"
              :use-weight="false"
              :with-search="null"
              :search-enabled="true"
              :zoom-enabled="true"
              :graph-updater="graphUpdater" />
          <FlamegraphComponent
              :with-timeseries="true"
              :with-search="null"
              :use-weight="false"
              :use-guardian="null"
              :time-range="null"
              scrollableWrapperClass="scrollable-wrapper"
              :flamegraph-tooltip="flamegraphTooltip"
              :graph-updater="graphUpdater" />
        </div>
      </div>
    </div>
  </div>
  </PageHeader>
</template>

<script setup lang="ts">
import {nextTick, onMounted, onUnmounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import EventViewerClient from '@/services/viewer/EventViewerClient';
import EventType from '@/services/viewer/model/EventType';
import * as bootstrap from 'bootstrap';
import PrimaryFlamegraphClient from '@/services/flamegraphs/client/PrimaryFlamegraphClient';
import FlamegraphTooltipFactory from '@/services/flamegraphs/tooltips/FlamegraphTooltipFactory';
import FlamegraphTooltip from '@/services/flamegraphs/tooltips/FlamegraphTooltip';
import GraphUpdater from '@/services/flamegraphs/updater/GraphUpdater';
import Badge from '@/components/Badge.vue';
import FullGraphUpdater from '@/services/flamegraphs/updater/FullGraphUpdater';
import GraphType from '@/services/flamegraphs/GraphType';
import FlamegraphComponent from '@/components/FlamegraphComponent.vue';
import TimeseriesComponent from '@/components/TimeseriesComponent.vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import type { PropType } from 'vue';

// Props definition
const props = defineProps({
  profile: {
    type: Object,
    required: true
  },
  secondaryProfile: {
    type: Object,
    default: null
  },
  disabledFeatures: {
    type: Array as PropType<string[]>,
    default: () => []
  }
});

const route = useRoute();
const router = useRouter();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// State variables
const loading = ref(true);
const error = ref(false);
const allEvents = ref<EventType[]>([]);
const filteredEvents = ref<EventType[]>([]);
const expandedNodes = ref<Set<string>>(new Set());
const searchText = ref('');
const totalEventCount = ref(0);
const totalEventTypes = ref(0);

// Flamegraph modal state
const showFlamegraphDialog = ref(false);
const selectedEventCode = ref<string>('');
let modalInstance: bootstrap.Modal | null = null;
let flamegraphTooltip: FlamegraphTooltip;
let graphUpdater: GraphUpdater;

// Create an instance of the client with the workspace, project and profile IDs
let eventViewerClient: EventViewerClient;

onMounted(async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    eventViewerClient = new EventViewerClient(workspaceId.value, projectId.value, profileId);

    // Load event types from the client
    const events = await eventViewerClient.eventTypesTree();
    allEvents.value = events;

    // All categories start collapsed by default

    // Calculate totals
    countTotalEvents();

    // Apply initial filtering
    filterEvents();

    loading.value = false;

    // Initialize the flamegraph modal
    nextTick(() => {
      const modalEl = document.getElementById('flamegraphModal');
      if (modalEl) {
        // We'll manually create and dispose of the modal
        // for better control over the behavior
        modalEl.addEventListener('hidden.bs.modal', () => {
          showFlamegraphDialog.value = false;
        });

        // Add event listener to close button
        const closeButton = modalEl.querySelector('.btn-close');
        if (closeButton) {
          closeButton.addEventListener('click', closeModal);
        }
      }
    });
  } catch (err) {
    console.error('Failed to load event types:', err);
    error.value = true;
    loading.value = false;
  }
});

// Clean up modal when component is unmounted
onUnmounted(() => {
  if (modalInstance) {
    modalInstance.dispose();
    modalInstance = null;
  }

  // Remove global event listeners
  document.removeEventListener('hidden.bs.modal', () => {
  });
});

// Function to close the modal
const closeModal = () => {
  if (modalInstance) {
    modalInstance.hide();
  }
  showFlamegraphDialog.value = false;
};

// Watch for changes to showFlamegraphDialog to control modal visibility
watch(showFlamegraphDialog, (isVisible) => {
  if (isVisible) {
    if (!modalInstance) {
      const modalEl = document.getElementById('flamegraphModal');
      if (modalEl) {
        modalInstance = new bootstrap.Modal(modalEl);
      }
    }

    // Initialize flamegraph components
    if (selectedEventCode.value) {
      const flamegraphClient = new PrimaryFlamegraphClient(
          workspaceId.value!,
          projectId.value!,
          profileId,
          selectedEventCode.value,
          false, // useThreadInfo
          false, // useSecondary
          false, // useDifferential
          false, // useGuardian
          false, // useIncomplete
          null
      );

      graphUpdater = new FullGraphUpdater(flamegraphClient, false);
      flamegraphTooltip = FlamegraphTooltipFactory.create(selectedEventCode.value, false, false);

      // Delayed initialization to ensure modal is fully rendered
      setTimeout(() => {
        graphUpdater.initialize();
      }, 200);
    }

    if (modalInstance) {
      modalInstance.show();
    }
  } else {
    if (modalInstance) {
      modalInstance.hide();
    }
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

// Expand all parent nodes
const expandAll = () => {
  allEvents.value.forEach(node => {
    expandAllNodes(node);
  });
  filterEvents();
};

// Recursive function to expand all nodes
const expandAllNodes = (node: EventType) => {
  if (!node.leaf) {
    expandedNodes.value.add(node.key);
    if (node.children) {
      node.children.forEach(child => {
        expandAllNodes(child);
      });
    }
  }
};

// Collapse all parent nodes
const collapseAll = () => {
  expandedNodes.value.clear();
  filterEvents();
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
  if (node.data.code && node.data.name) {
    // Create an EventTypeDescription object to pass to the ProfileEvents view
    const eventTypeParam = {
      code: node.data.code,
      name: node.data.name,
      count: node.data.count || 0
    };

    // Store the event type in localStorage for the Events page to pick up
    localStorage.setItem('selectedEventType', JSON.stringify(eventTypeParam));
    
    // Navigate to the events page
    router.push(`/workspaces/${workspaceId.value}/projects/${projectId.value}/profiles/${profileId}/events`);
  }
};

// View event flamegraph
const viewFlamegraph = (node: EventType) => {
  if (node.data.code) {
    // Set the event code
    selectedEventCode.value = node.data.code;

    // Show the flamegraph modal
    showFlamegraphDialog.value = true;
  }
};

// View event time series
const viewTimeSeries = (node: EventType) => {
  if (node.data.code) {
    // Set the event code
    selectedEventCode.value = node.data.code;

    // Show the flamegraph modal
    showFlamegraphDialog.value = true;
  }
};

// Filter and flatten the event tree based on search and filters
const filterEvents = () => {
  const search = searchText.value.toLowerCase().trim();

  // Recursive function to filter and flatten the tree
  const processNode = (node: EventType, isVisible: boolean, path: string[] = []): EventType[] => {
    const currentPath = [...path, node.data.name];

    // Only match leaf nodes by name and code
    const nodeMatches = node.leaf && (
        node.data.name.toLowerCase().includes(search) ||
        (node.data.code && node.data.code.toLowerCase().includes(search))
    );

    // No additional filtering applied
    const matchesFilter = true;

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
  width: 70%;
}

.event-tree-table th:nth-child(2) {
  width: 30%;
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
  white-space: nowrap;
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

.modal-title {
  font-weight: 600;
}

.modal-title {
  margin: 0;
  font-size: 1.1rem;
  color: #3f51b5;
  font-weight: 600;
  display: flex;
  align-items: center;
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
