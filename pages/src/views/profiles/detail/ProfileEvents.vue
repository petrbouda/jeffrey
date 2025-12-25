<template>
    <PageHeader 
      title="Events"
      description="View and analyze profile events"
      icon="bi-collection"
    >

    <!-- Event Type Selector -->
    <div class="mb-4">
      <div v-if="!selectedEventType || showEventTypeList">
        <div class="mb-3">
          <div class="input-group search-container">
            <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
            <input
                id="searchFilter"
                type="text"
                class="form-control search-input"
                v-model="searchTerm"
                placeholder="Filter event types..."
                aria-label="Filter event types"
                autocomplete="off"
            >
            <button 
              v-if="searchTerm" 
              class="btn btn-outline-secondary clear-btn" 
              type="button"
              @click="searchTerm = ''">
              <i class="bi bi-x-lg"></i>
            </button>
          </div>
        </div>

        <div class="event-type-list" :class="{ 'limit-height': selectedEventType && showEventTypeList }">
          <div v-if="loading" class="text-center py-3">
            <div class="spinner-border spinner-border-sm" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <span class="ms-2">Loading event types...</span>
          </div>
          <div v-else-if="filteredEventTypes.length === 0" class="text-center py-3">
            <i class="bi bi-exclamation-circle me-2"></i>
            No event types found matching your filter
          </div>
          <div v-else class="list-group">
            <button
                v-for="eventType in filteredEventTypes"
                :key="eventType.code"
                class="list-group-item list-group-item-action d-flex justify-content-between align-items-center"
                :class="{ 'active': selectedEventType?.code === eventType.code }"
                @click="selectAndHideList(eventType)"
            >
              <div>
                <div class="fw-medium">{{ eventType.name }}</div>
                <div class="small text-muted">{{ eventType.code }}</div>
              </div>
              <span class="badge bg-secondary rounded-pill">{{ eventType.count }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Selecteadd d Event Type Content -->
    <div v-if="selectedEventType" class="card mb-4">
      <div class="card-header d-flex justify-content-between align-items-center">
        <span>
          <strong>{{ selectedEventType.name }}</strong>
          <span class="text-muted ms-2">({{ selectedEventType.code }})</span>
        </span>
        <div class="d-flex align-items-center">
          <span class="badge bg-primary me-2">{{ selectedEventType.count }} samples</span>
          <span v-if="eventData.length > 200" class="badge bg-info me-3">
            <i class="bi bi-info-circle me-1"></i>Showing 200 of {{ eventData.length }} rows
          </span>
          <button v-if="!showEventTypeList" class="btn btn-sm btn-outline-secondary" @click="toggleEventTypeList">
            <i class="bi bi-pencil"></i> Change
          </button>
        </div>
      </div>
      <div class="card-body p-0">
        <div v-if="loadingEventData" class="p-4 text-center">
          <div class="spinner-border spinner-border-sm" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <span class="ms-2">Loading event data...</span>
        </div>
        <div v-else-if="eventColumns.length === 0" class="p-4 text-center">
          <i class="bi bi-exclamation-triangle me-2"></i>
          No event columns found for this event type
        </div>
        <div v-else-if="eventData.length === 0" class="p-4 text-center">
          <i class="bi bi-info-circle me-2"></i>
          No event data found for this event type
        </div>
        <div v-else>
          <div class="table-responsive">
            <table class="table table-hover mb-0 event-tree-table">
              <thead>
                <tr>
                  <th v-for="column in eventColumns" :key="column.field">
                    <div class="d-flex flex-column">
                      <!-- Column header -->
                      <div class="d-flex align-items-center mb-1" 
                           :class="{ 'sortable': isSortableField(column.type) }"
                           @click="isSortableField(column.type) && toggleSort(column.field)">
                        <span>{{ column.header }}</span>
                        <span v-if="isSortableField(column.type)" class="ms-1">
                          <i v-if="sortConfig && sortConfig.field === column.field" 
                             :class="sortConfig.direction === 'asc' ? 'bi bi-sort-up' : 'bi bi-sort-down'"></i>
                          <i v-else class="bi bi-sort text-muted"></i>
                        </span>
                      </div>
                      
                      <!-- Filter input for string columns -->
                      <div v-if="isStringField(column.field, column.type)" class="column-filter">
                        <input type="text" 
                               class="form-control form-control-sm filter-input"
                               :placeholder="'Filter...'" 
                               v-model="columnFilters[column.field]"
                               @click.stop
                               @input="applyFilters">
                      </div>
                    </div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr class="leaf-row" v-for="(event, index) in limitedEventData" :key="index">
                  <td v-for="column in eventColumns" :key="column.field" class="event-cell">
                    <div class="event-name-cell">
                      <span class="event-value">{{ FormattingService.format(event[column.field], column.type || '') }}</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import EventViewerClient from '@/services/api/EventViewerClient';
import EventTypeDescription from '@/services/api/model/EventTypeDescription';
import EventFieldDescription from '@/services/api/model/EventFieldDescription';
import FormattingService from '@/services/FormattingService';
import {useRoute} from "vue-router";
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';

const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// State
const eventTypes = ref<EventTypeDescription[]>([]);
const selectedEventType = ref<EventTypeDescription | null>(null);
const searchTerm = ref('');
const loading = ref(false);
const showEventTypeList = ref(false);
const eventColumns = ref<EventFieldDescription[]>([]);
const eventData = ref<Record<string, string | number>[]>([]);
const loadingEventData = ref(false);
const columnFilters = ref<Record<string, string>>({});
const sortConfig = ref<{field: string, direction: 'asc' | 'desc'} | null>(null);

// Computed properties
const nonZeroEventTypes = computed(() => {
  return eventTypes.value.filter(eventType => eventType.count > 0);
});

const filteredEventTypes = computed(() => {
  // First, filter out event types with zero count
  const baseList = nonZeroEventTypes.value;
  
  if (!searchTerm.value) {
    return baseList;
  }

  const search = searchTerm.value.toLowerCase();
  return baseList.filter(eventType =>
      eventType.name.toLowerCase().includes(search) ||
      eventType.code.toLowerCase().includes(search)
  );
});

// Function to determine if a field is string-related
function isStringField(fieldName: string, fieldType?: string): boolean {
  if (!fieldType) return true;
  return resolveType(fieldType) === "text";
}

// Apply filters and sorting to event data
const filteredEventData = computed(() => {
  let result = [...eventData.value];
  
  // Apply column filters (only for string columns)
  if (Object.keys(columnFilters.value).length > 0) {
    for (const [field, filterValue] of Object.entries(columnFilters.value)) {
      if (filterValue && filterValue.trim() !== '') {
        result = result.filter(event => {
          const value = event[field];
          // Only filter string values or values that can be converted to strings
          if (value !== null && value !== undefined) {
            return String(value).toLowerCase().includes(filterValue.toLowerCase());
          }
          return false;
        });
      }
    }
  }
  
  // Apply sorting if configured
  if (sortConfig.value) {
    const { field, direction } = sortConfig.value;
    result.sort((a, b) => {
      const valueA = a[field];
      const valueB = b[field];
      
      // Handle undefined or null values
      if (valueA === undefined || valueA === null) return direction === 'asc' ? -1 : 1;
      if (valueB === undefined || valueB === null) return direction === 'asc' ? 1 : -1;
      
      // Compare based on type
      if (typeof valueA === 'number' && typeof valueB === 'number') {
        return direction === 'asc' ? valueA - valueB : valueB - valueA;
      } else {
        // Convert to string for comparison
        const strA = String(valueA).toLowerCase();
        const strB = String(valueB).toLowerCase();
        return direction === 'asc' ? strA.localeCompare(strB) : strB.localeCompare(strA);
      }
    });
  }
  
  return result;
});

// Display maximum 200 events for performance
const limitedEventData = computed(() => {
  return filteredEventData.value.slice(0, 200);
});

// Removed custom formatValue function in favor of FormattingService.format

// Methods
// Apply filter for specific column
function applyFilters() {
  // No additional implementation needed - the computed property will handle filtering
}

// Function to check if a field is numeric or time-based and can be sorted
function isSortableField(fieldType?: string): boolean {
  if (!fieldType) return false;
  const type = resolveType(fieldType);
  return type === "numeric";
}

// Toggle sorting for a column
function toggleSort(field: string) {
  if (sortConfig.value && sortConfig.value.field === field) {
    // Toggle direction if same field
    sortConfig.value.direction = sortConfig.value.direction === 'asc' ? 'desc' : 'asc';
  } else {
    // Set new sort config
    sortConfig.value = { field, direction: 'asc' };
  }
}


async function selectEventType(eventType: EventTypeDescription) {
  selectedEventType.value = eventType;
  
  if (eventType && eventType.code) {
    loadingEventData.value = true;
    try {
      if (!workspaceId.value || !projectId.value) return;

      const client = new EventViewerClient(workspaceId.value, projectId.value, profileId);
      
      // Load columns information
      eventColumns.value = await client.eventColumns(eventType.code);

      // Load event data
      eventData.value = await client.events(eventType.code);
      
      // Reset column filters
      columnFilters.value = {};
      
      // Check if startTime column exists and set it as default sort if it does
      const startTimeColumn = eventColumns.value.find(col => 
        col.field === 'startTime' && resolveType(col.type) === 'numeric'
      );
      
      if (startTimeColumn) {
        sortConfig.value = { field: 'startTime', direction: 'desc' };
      } else {
        sortConfig.value = null;
      }
    } catch (error) {
      console.error(`Failed to load event data for ${eventType.code}:`, error);
      eventColumns.value = [];
      eventData.value = [];
      columnFilters.value = {};
      sortConfig.value = null;
    } finally {
      loadingEventData.value = false;
    }
  }
}

function selectAndHideList(eventType: EventTypeDescription) {
  selectEventType(eventType);
  showEventTypeList.value = false;
}

function toggleEventTypeList() {
  showEventTypeList.value = !showEventTypeList.value;
  // Clear search when showing the list again
  if (showEventTypeList.value) {
    searchTerm.value = '';
  }
}

function resolveType(jfrType: string) {
  // jdk.jfr.Percentage
  // jdk.jfr.Timespan
  // jdk.jfr.Timestamp
  // jdk.jfr.Frequency
  // jdk.jfr.BooleanFlag
  // jdk.jfr.MemoryAddress
  // jdk.jfr.DataAmount
  // jdk.jfr.Unsigned -> "byte", "short", "int", "long"
  // jdk.jfr.snippets.Temperature
  // => text, numeric, date

  if (
      jfrType === "jdk.jfr.Unsigned"
      || jfrType === "jdk.jfr.Timestamp"
      || jfrType === "jdk.jfr.DataAmount"
      || jfrType === "jdk.jfr.MemoryAddress"
      || jfrType === "jdk.jfr.Frequency"
      || jfrType === "jdk.jfr.Timespan"
      || jfrType === "jdk.jfr.Percentage") {

    return "numeric"
  } else {
    return "text"
  }
}

// Lifecycle hooks
onMounted(async () => {
  try {
    loading.value = true;
    if (!workspaceId.value || !projectId.value) return;

    const client = new EventViewerClient(workspaceId.value, projectId.value, profileId);
    eventTypes.value = await client.eventTypes();

    // Sort by name as default
    eventTypes.value.sort((a, b) => a.name.localeCompare(b.name));

    // Check if an event type was selected from the event types view
    const savedEventTypeJson = localStorage.getItem('selectedEventType');
    if (savedEventTypeJson) {
      try {
        const savedEventType = JSON.parse(savedEventTypeJson);
        
        // Find the matching event type in our loaded list to ensure it exists
        const matchingEventType = eventTypes.value.find(et => et.code === savedEventType.code);
        
        if (matchingEventType) {
          // Select this event type
          await selectEventType(matchingEventType);
          showEventTypeList.value = false;
          
          // Clear the localStorage entry to prevent it from being used again on refresh
          localStorage.removeItem('selectedEventType');
        } else {
          // Fallback to default - no selection
          selectedEventType.value = null;
          showEventTypeList.value = true;
        }
      } catch (e) {
        console.error('Error parsing saved event type:', e);
        selectedEventType.value = null;
        showEventTypeList.value = true;
      }
    } else {
      // Default behavior - no selection
      selectedEventType.value = null;
      showEventTypeList.value = true;
    }
  } catch (error) {
    console.error('Failed to load event types:', error);
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.events-title {
  font-size: 1.75rem;
  font-weight: 600;
  color: #343a40;
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

.event-type-list {
  overflow-y: auto;
}

.event-type-list.limit-height {
  max-height: 300px;
}

.list-group-item.active {
  background-color: #d1e5ff;
  border-color: #b8d8ff;
  color: #212529;
}

.list-group-item:hover {
  background-color: #f8f9fa;
}

.list-group-item.active:hover {
  background-color: #c2ddff;
  color: #212529;
}

.input-group-text {
  height: 31px;
  padding: 0.25rem 0.5rem;
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

/* Table styles */
.event-tree-table {
  width: 100%;
  table-layout: auto;
  border-collapse: collapse;
}

.event-name-cell {
  overflow: hidden;
  text-overflow: ellipsis;
}


.leaf-row:hover {
  background-color: #f8f9fa;
}

.event-value {
  font-weight: 400;
}

.sortable {
  cursor: pointer;
}

.sortable:hover {
  color: #0d6efd;
}

.event-cell {
  max-width: 250px;
}

th {
  background-color: #f8f9fa;
  font-weight: 600;
  white-space: nowrap;
  padding: 0.75rem;
  user-select: none;
}

.column-filter {
  width: 100%;
  min-width: 80px;
}

.filter-input {
  font-size: 0.75rem;
  padding: 0.25rem 0.5rem;
  height: 1.5rem;
}

.filter-input:focus {
  box-shadow: none;
  border-color: #0d6efd;
}

/* Dropdown styles */
.bi-empty {
  width: 1em;
  display: inline-block;
}
</style>
