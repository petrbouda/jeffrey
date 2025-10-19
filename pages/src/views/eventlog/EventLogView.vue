<template>
  <div>
    <!-- Workspace Selector -->
    <div class="workspace-selector-card mb-4">
      <div class="workspace-selector-content">
        <div class="workspace-cards-container">
          <div class="workspace-cards-header">
            <span class="workspace-label">Event Logs</span>
          </div>
          <div class="workspace-cards-grid">
            <div 
              v-for="workspace in workspaces"
              :key="workspace.id"
              class="workspace-card"
              :class="{ 'active': selectedWorkspace === workspace.id }"
              @click="selectWorkspace(workspace.id)"
            >
              <div class="workspace-card-content">
                <div class="workspace-card-header">
                  <h6 class="workspace-name">{{ workspace.name }}</h6>
                  <span class="workspace-badge">{{ getWorkspaceEventCount(workspace.id) }}</span>
                </div>
                <div class="workspace-card-description">
                  {{ getWorkspaceDescription(workspace) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Events Timeline -->
    <div class="events-main-card mb-4">
      <div class="events-main-content">
        <!-- Filters and Search -->
        <div class="d-flex align-items-center mb-4 gap-3">
          <div class="search-box">
            <div class="input-group input-group-sm phoenix-search">
              <span class="input-group-text border-0 ps-3 pe-0 search-icon-container">
                <i class="bi bi-search text-primary"></i>
              </span>
              <input
                type="text"
                class="form-control border-0 py-2"
                v-model="searchQuery"
                placeholder="Search events..."
                @input="filterEvents"
              >
            </div>
          </div>
          <div class="filter-dropdown">
            <select class="form-select" v-model="selectedEventType" @change="filterEvents">
              <option value="">All Events</option>
              <option value="PROJECT_CREATED">Project Created</option>
              <option value="PROJECT_DELETED">Project Deleted</option>
              <option value="SESSION_CREATED">Session Created</option>
              <option value="SESSION_DELETED">Session Deleted</option>
            </select>
          </div>
        </div>
        <!-- Loading indicator -->
        <div v-if="loading" class="text-center py-4">
          <div class="spinner-border text-primary" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <p class="mt-2">Loading workspace events...</p>
        </div>

        <!-- Error state -->
        <div v-else-if="errorMessage" class="text-center py-4">
          <i class="bi bi-exclamation-triangle-fill fs-1 text-warning mb-3"></i>
          <h5>Failed to load events</h5>
          <p class="text-muted">{{ errorMessage }}</p>
          <button class="btn btn-primary mt-2" @click="refreshEvents">
            <i class="bi bi-arrow-clockwise me-2"></i>Retry
          </button>
        </div>

        <!-- Events list -->
        <div v-else-if="filteredEvents.length > 0" class="events-list">
          <div 
            v-for="event in filteredEvents" 
            :key="event.eventId" 
            class="event-row"
            @click="showEventDetails(event)"
          >
            <div class="event-main">
              <div class="event-details">
                <div class="event-first-line">
                  <Badge 
                    :value="EventContentParser.getEventDisplayName(event.eventType)"
                    :variant="getEventBadgeVariant(event.eventType)"
                    size="s"
                    :uppercase="false"
                  />
                  <span class="event-ids">
                    {{ event.originEventId }} • {{ event.projectId }} • {{ event.workspaceId }}
                  </span>
                  <div class="event-time-info">
                    <span class="event-time-full">{{ FormattingService.formatTimestamp(event.originCreatedAt) }}</span>
                    <span class="event-time-relative">{{ FormattingService.formatRelativeTime(event.originCreatedAt) }}</span>
                  </div>
                </div>
                <div class="event-content-pairs">
                  <span 
                    v-for="(value, key) in getMainContentPairs(event)" 
                    :key="key"
                    class="content-pair"
                  >
                    <span class="content-key">{{ key }}:</span>
                    <span class="content-value">{{ value }}</span>
                  </span>
                  
                  <!-- Attributes for PROJECT_CREATED events -->
                  <span 
                    v-if="getAttributesPairs(event)"
                    v-for="(value, key) in getAttributesPairs(event)" 
                    :key="`attr-${key}`"
                    class="content-pair attribute-pair"
                  >
                    <span class="content-key">{{ key }}:</span>
                    <span class="content-value">{{ value }}</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-else class="text-center py-5">
          <i class="bi bi-journal-text fs-1 text-muted mb-3"></i>
          <h5>No events found</h5>
          <p v-if="selectedWorkspace" class="text-muted">No events found for the selected workspace</p>
          <p v-else class="text-muted">No events match your current filters</p>
        </div>
      </div>
    </div>

    <!-- Event Details Modal -->
    <BaseModal
      ref="eventDetailsModal"
      modal-id="eventDetailsModal"
      :title="selectedEvent ? EventContentParser.getEventDisplayName(selectedEvent.eventType) : 'Event Details'"
      :icon="selectedEvent ? EventContentParser.getEventIcon(selectedEvent.eventType) : 'bi-journal-text'"
      :icon-color="selectedEvent ? getEventIconColor(selectedEvent.eventType) : 'text-primary'"
      size="lg"
      primary-button-text="Close"
      :enable-enter-key="false"
      @submit="closeEventDetails"
      @cancel="closeEventDetails"
    >
      <template #body>
        <div v-if="selectedEvent" class="event-modal-content">
          <div class="info-section">
            <div class="info-row">
              <div class="info-pair">
                <span class="label">Event:</span>
                <span class="value">{{ selectedEvent.originEventId }}</span>
              </div>
              <div class="info-pair">
                <span class="label">Project:</span>
                <span class="value">{{ selectedEvent.projectId }}</span>
              </div>
            </div>
            
            <div class="info-row">
              <div class="info-pair">
                <span class="label">Workspace:</span>
                <span class="value">{{ selectedEvent.workspaceId }}</span>
              </div>
              <div class="info-pair">
                <span class="label">Created:</span>
                <span class="value">{{ FormattingService.formatTimestamp(selectedEvent.originCreatedAt) }}</span>
              </div>
            </div>
          </div>
          
          <div class="content-section">
            <div class="content-header">Content</div>
            <pre class="content-json">{{ formatEventContent(selectedEvent) }}</pre>
          </div>
        </div>
      </template>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import WorkspaceEvent from '@/services/model/WorkspaceEvent';
import WorkspaceEventType from '@/services/model/WorkspaceEventType';
import WorkspaceClient from '@/services/workspace/WorkspaceClient';
import WorkspaceType from '@/services/workspace/model/WorkspaceType';
import { EventContentParser } from '@/services/EventContentParser';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import Badge from '@/components/Badge.vue';
import BaseModal from '@/components/BaseModal.vue';

// Workspaces data
const workspaces = ref<any[]>([]);

// State
const selectedWorkspace = ref<string>('');
const events = ref<WorkspaceEvent[]>([]);
const filteredEvents = ref<WorkspaceEvent[]>([]);
const searchQuery = ref('');
const selectedEventType = ref<string>('');
const loading = ref(true);
const errorMessage = ref('');
const selectedEvent = ref<WorkspaceEvent | null>(null);

// Modal reference
const eventDetailsModal = ref<InstanceType<typeof BaseModal>>();

// Fetch workspaces function
const refreshWorkspaces = async () => {
  try {
    const allWorkspaces = await WorkspaceClient.list(true);
    // Filter to show only LOCAL workspaces
    workspaces.value = allWorkspaces.filter(workspace => workspace.type === WorkspaceType.LOCAL);
    // Set the first workspace as selected if none is selected
    if (!selectedWorkspace.value && workspaces.value.length > 0) {
      selectedWorkspace.value = workspaces.value[0].id;
    }
  } catch (error) {
    console.error('Failed to load workspaces:', error);
    ToastService.error('Failed to load workspaces', 'Cannot load workspaces from the server.');
  }
};

// Fetch events function
const refreshEvents = async () => {
  if (!selectedWorkspace.value) {
    loading.value = false;
    return;
  }

  loading.value = true;
  errorMessage.value = '';

  try {
    const workspaceId = selectedWorkspace.value;
    events.value = await WorkspaceClient.getEvents(workspaceId);
    
    // Sort events by timestamp (youngest to oldest)
    events.value.sort((a, b) => b.originCreatedAt - a.originCreatedAt);
    
    // Update workspace event counts
    workspaces.value.forEach(workspace => {
      workspace.eventCount = events.value.filter(event => 
        event.workspaceId === workspace.id
      ).length;
    });
    
    filteredEvents.value = [...events.value];
  } catch (error) {
    console.error('Failed to load events:', error);
    errorMessage.value = error instanceof Error ? error.message : 'Could not connect to server';
    ToastService.error('Failed to load events', 'Cannot load events from the server.');
  } finally {
    loading.value = false;
  }
};

// Select workspace
const selectWorkspace = (workspaceId: string) => {
  selectedWorkspace.value = workspaceId;
  refreshEvents();
};

// Get workspace event count
const getWorkspaceEventCount = (workspaceId: string) => {
  const workspace = workspaces.value.find(w => w.id === workspaceId);
  return workspace?.eventCount ?? 0;
};

// Get workspace description
const getWorkspaceDescription = (workspace: any) => {
  return workspace?.description || `Events for ${workspace.name}`;
};

// Filter events
const filterEvents = () => {
  let filtered = [...events.value];

  // Filter by event type
  if (selectedEventType.value) {
    filtered = filtered.filter(event => event.eventType === selectedEventType.value);
  }

  // Filter by search query
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    filtered = filtered.filter(event => 
      EventContentParser.getEventDescription(event).toLowerCase().includes(query) ||
      event.originEventId.toLowerCase().includes(query) ||
      event.projectId.toLowerCase().includes(query)
    );
  }

  // Sort filtered results by timestamp (youngest to oldest)
  filtered.sort((a, b) => b.originCreatedAt - a.originCreatedAt);

  filteredEvents.value = filtered;
};

// Format event content for modal
const formatEventContent = (event: WorkspaceEvent) => {
  const content = EventContentParser.parseContent(event);
  return JSON.stringify(content, null, 2);
};

// Get main content key-value pairs for display (excluding attributes)
const getMainContentPairs = (event: WorkspaceEvent) => {
  const content = EventContentParser.parseContent(event);
  const pairs: Record<string, string> = {};
  
  if (event.eventType === WorkspaceEventType.PROJECT_CREATED) {
    // Show only main fields, not attributes
    if (content.projectName && content.projectName.trim()) {
      pairs['projectName'] = content.projectName;
    }
    if (content.repositoryType && content.repositoryType.trim()) {
      pairs['repositoryType'] = content.repositoryType;
    }
  } else if (event.eventType === WorkspaceEventType.SESSION_CREATED) {
    // For SessionCreatedEvent
    if (content.relativePath && content.relativePath.trim()) {
      pairs['relativePath'] = content.relativePath;
    }
    if (content.workspacesPath && content.workspacesPath.trim()) {
      pairs['workspacesPath'] = content.workspacesPath;
    }
  } else {
    // For other event types (PROJECT_DELETED, SESSION_DELETED), show all top-level properties
    Object.entries(content).forEach(([key, value]) => {
      let stringValue = '';
      if (typeof value === 'object' && value !== null) {
        stringValue = JSON.stringify(value);
      } else {
        stringValue = String(value);
      }
      
      if (stringValue && stringValue.trim()) {
        pairs[key] = stringValue;
      }
    });
  }
  
  return pairs;
};

// Get attributes for PROJECT_CREATED events
const getAttributesPairs = (event: WorkspaceEvent) => {
  if (event.eventType !== WorkspaceEventType.PROJECT_CREATED) {
    return null;
  }
  
  const content = EventContentParser.parseContent(event);
  if (!content.attributes || typeof content.attributes !== 'object') {
    return null;
  }
  
  const pairs: Record<string, string> = {};
  Object.entries(content.attributes).forEach(([key, value]) => {
    pairs[key] = String(value);
  });
  
  return Object.keys(pairs).length > 0 ? pairs : null;
};

// Map event types to badge variants
const getEventBadgeVariant = (eventType: WorkspaceEventType) => {
  switch (eventType) {
    case WorkspaceEventType.PROJECT_CREATED:
      return 'green';
    case WorkspaceEventType.PROJECT_DELETED:
      return 'danger';
    case WorkspaceEventType.SESSION_CREATED:
      return 'primary';
    case WorkspaceEventType.SESSION_DELETED:
      return 'warning';
    default:
      return 'secondary';
  }
};

// Show event details modal
const showEventDetails = (event: WorkspaceEvent) => {
  selectedEvent.value = event;
  eventDetailsModal.value?.showModal();
};

// Close event details modal
const closeEventDetails = () => {
  eventDetailsModal.value?.hideModal();
};

// Get icon color for event type
const getEventIconColor = (eventType: WorkspaceEventType) => {
  switch (eventType) {
    case WorkspaceEventType.PROJECT_CREATED:
      return 'text-success';
    case WorkspaceEventType.PROJECT_DELETED:
      return 'text-danger';
    case WorkspaceEventType.SESSION_CREATED:
      return 'text-primary';
    case WorkspaceEventType.SESSION_DELETED:
      return 'text-warning';
    default:
      return 'text-secondary';
  }
};

// Component lifecycle
onMounted(async () => {
  await refreshWorkspaces();
  await refreshEvents();
});
</script>

<style scoped>
.search-box {
  flex: 1;
  max-width: 600px;
}

.filter-dropdown {
  min-width: 200px;
}

.phoenix-search {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.12);
  overflow: hidden;
  border-radius: 12px;
  height: 48px;
  box-shadow: 
    inset 0 1px 3px rgba(0, 0, 0, 0.05),
    0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);

  &:focus-within {
    border-color: rgba(94, 100, 255, 0.3);
    box-shadow: 
      inset 0 1px 3px rgba(0, 0, 0, 0.05),
      0 4px 12px rgba(94, 100, 255, 0.1),
      0 0 0 3px rgba(94, 100, 255, 0.05);
    transform: translateY(-1px);
  }

  .search-icon-container {
    width: 48px;
    display: flex;
    justify-content: center;
    background: transparent;
    border: none;
    color: #6c757d;
  }

  .form-control {
    height: 46px;
    font-size: 0.9rem;
    background: transparent;
    border: none;
    color: #374151;
    font-weight: 500;

    &::placeholder {
      color: #9ca3af;
      font-weight: 400;
    }

    &:focus {
      box-shadow: none;
      background: transparent;
    }
  }
}

/* Modern Card Styling */
.workspace-selector-card,
.events-main-card {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 16px;
  box-shadow: 
    0 4px 20px rgba(0, 0, 0, 0.04),
    0 1px 3px rgba(0, 0, 0, 0.02);
  backdrop-filter: blur(10px);
}

.workspace-selector-content,
.events-main-content {
  padding: 24px 28px;
}

/* Workspace Cards */
.workspace-label {
  color: #374151;
  font-size: 0.9rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  opacity: 0.7;
}

.workspace-cards-header {
  margin-bottom: 16px;
}

.workspace-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 300px));
  gap: 12px;
}

.workspace-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 
    0 2px 8px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);

  &:hover:not(.active) {
    transform: translateY(-2px);
    box-shadow: 
      0 6px 16px rgba(0, 0, 0, 0.06),
      0 2px 8px rgba(94, 100, 255, 0.1);
    border-color: rgba(94, 100, 255, 0.2);
  }

  &.active {
    background: linear-gradient(135deg, #5e64ff, #4c52ff);
    border-color: #5e64ff;
    transform: translateY(-1px);
    box-shadow: 
      0 6px 20px rgba(94, 100, 255, 0.3),
      0 2px 8px rgba(94, 100, 255, 0.2);

    .workspace-name {
      color: white;
    }

    .workspace-card-description {
      color: rgba(255, 255, 255, 0.8);
    }

    .workspace-badge {
      background: rgba(255, 255, 255, 0.2);
      color: white;
    }
  }

}

.workspace-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.workspace-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: #374151;
  margin: 0;
  letter-spacing: 0.01em;
}

.workspace-card-description {
  font-size: 0.75rem;
  color: #6b7280;
  line-height: 1.4;
  margin: 0;
}

.workspace-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 24px;
  height: 20px;
  padding: 0 6px;
  background: rgba(94, 100, 255, 0.1);
  color: #5e64ff;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
}

/* Events List */
.events-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-row {
  background: linear-gradient(135deg, #ffffff, #fafbff);
  border-radius: 8px;
  border: 1px solid rgba(94, 100, 255, 0.08);
  box-shadow: 
    0 1px 4px rgba(0, 0, 0, 0.04),
    0 1px 2px rgba(0, 0, 0, 0.02);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 
      0 2px 8px rgba(0, 0, 0, 0.08),
      0 1px 4px rgba(94, 100, 255, 0.15);
    border-color: rgba(94, 100, 255, 0.2);
  }
}

.event-main {
  display: flex;
  align-items: center;
  padding: 12px 16px;
}

.event-details {
  flex: 1;
  min-width: 0;
}

.event-first-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 0.5rem;
}

.event-ids {
  font-size: 0.65rem;
  color: #8b95a7;
  font-weight: 400;
  opacity: 0.8;
  letter-spacing: 0.1px;
  flex: 1;
  margin-left: 8px;
}

.event-time-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.event-time-relative {
  font-size: 0.7rem;
  color: #374151;
  font-weight: 500;
  white-space: nowrap;
}

.event-time-full {
  font-size: 0.7rem;
  color: #9ca3af;
  font-weight: 400;
  opacity: 0.8;
  white-space: nowrap;
}

.event-content-pairs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.content-pair {
  display: inline;
  font-size: 0.7rem;
  color: #6b7280;
  margin-right: 12px;
  white-space: nowrap;
}

.content-key {
  color: #9ca3af;
  font-weight: 500;
  margin-right: 4px;
}

.content-value {
  color: #374151;
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attribute-pair {
  .content-key {
    color: #9ca3af;
    font-style: italic;
  }

  .content-value {
    color: #6b7280;
  }
}

/* Modal Styling */
.event-modal-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-section {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.info-row:not(:last-child) {
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(94, 100, 255, 0.06);
}

.info-pair {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label {
  font-size: 0.7rem;
  color: #9ca3af;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8rem;
  color: #374151;
  font-weight: 500;
  word-break: break-all;
  background: rgba(94, 100, 255, 0.06);
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid rgba(94, 100, 255, 0.1);
}

.content-section {
  margin-top: 8px;
}

.content-header {
  color: #6b7280;
  font-size: 0.75rem;
  font-weight: 600;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.content-json {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  padding: 12px;
  font-size: 0.75rem;
  color: #374151;
  max-height: 250px;
  overflow-y: auto;
  margin: 0;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  line-height: 1.4;
}

/* Form Select Styling */
.form-select {
  border: 1px solid rgba(94, 100, 255, 0.12);
  border-radius: 12px;
  height: 48px;
  font-size: 0.9rem;
  color: #374151;
  font-weight: 500;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);

  &:focus {
    border-color: rgba(94, 100, 255, 0.3);
    box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  }
}
</style>
