<template>
  <div>
    <!-- Workspace Selector -->
    <div class="main-card mb-4">
      <div class="main-card-content">
        <div class="workspace-cards-container">
          <div class="workspace-cards-header">
            <span class="workspace-label">Select Workspace</span>
          </div>
          <div class="workspace-cards-grid">
            <WorkspaceSelectionCard
              v-for="workspace in workspaces"
              :key="workspace.id"
              :name="workspace.name"
              :description="getWorkspaceDescription(workspace)"
              :selected="selectedWorkspace === workspace.id"
              :workspace-type="workspace.type"
              :badge-value="getWorkspaceEventCount(workspace.id)"
              @select="selectWorkspace(workspace.id)"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Events Timeline -->
    <div class="main-card mb-4">
      <div class="main-card-header">
        <i class="bi bi-clock-history main-card-header-icon"></i>
        <h5 class="main-card-header-title">Events Timeline</h5>
      </div>
      <div class="main-card-content">
        <!-- Filters and Search -->
        <div class="d-flex align-items-center mb-4 gap-3">
          <SearchBox
            v-model="searchQuery"
            placeholder="Search events..."
            @update:model-value="filterEvents"
          />
          <div class="filter-dropdown">
            <select class="form-select" v-model="selectedEventType" @change="filterEvents">
              <option value="">All Events</option>
              <option value="PROJECT_CREATED">Project Created</option>
              <option value="PROJECT_DELETED">Project Deleted</option>
              <option value="PROJECT_INSTANCE_CREATED">Instance Created</option>
              <option value="PROJECT_INSTANCE_SESSION_CREATED">Session Created</option>
              <option value="PROJECT_INSTANCE_SESSION_DELETED">Session Deleted</option>
              <option value="PROJECT_INSTANCE_SESSION_FINISHED">Session Finished</option>
              <option value="RECORDING_FILE_CREATED">Recording Created</option>
            </select>
          </div>
        </div>

        <!-- Loading indicator -->
        <LoadingState v-if="loading" message="Loading workspace events..." />

        <!-- Error state -->
        <div v-else-if="errorMessage" class="error-state">
          <i class="bi bi-exclamation-triangle-fill"></i>
          <h5>Failed to load events</h5>
          <p>{{ errorMessage }}</p>
          <button class="btn btn-primary" @click="refreshEvents">
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
                <div class="event-second-line">
                  <span class="event-created-by">
                    <i class="bi bi-person-fill me-1"></i>{{ event.createdBy }}
                  </span>
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
                  <template v-if="getAttributesPairs(event)">
                    <span
                      v-for="(value, key) in getAttributesPairs(event)"
                      :key="`attr-${key}`"
                      class="content-pair attribute-pair"
                    >
                      <span class="content-key">{{ key }}:</span>
                      <span class="content-value">{{ value }}</span>
                    </span>
                  </template>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <EmptyState
          v-else
          icon="bi-journal-text"
          title="No Events Found"
          :description="selectedWorkspace ? 'No events found for the selected workspace.' : 'No events match your current filters.'"
        />
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
                <span class="label">Created By:</span>
                <span class="value">{{ selectedEvent.createdBy }}</span>
              </div>
            </div>

            <div class="info-row">
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
import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';
import WorkspaceEventType from '@/services/api/model/WorkspaceEventType';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import WorkspaceType from '@/services/api/model/WorkspaceType';
import { EventContentParser } from '@/services/EventContentParser';
import ToastService from '@/services/ToastService';
import FormattingService from '@/services/FormattingService';
import WorkspaceSelectionCard from '@/components/settings/WorkspaceSelectionCard.vue';
import Badge from '@/components/Badge.vue';
import BaseModal from '@/components/BaseModal.vue';
import SearchBox from '@/components/SearchBox.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import '@/styles/shared-components.css';

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
    // Filter to show only LIVE workspaces
    workspaces.value = allWorkspaces.filter(workspace => workspace.type === WorkspaceType.LIVE);
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
  } else if (event.eventType === WorkspaceEventType.PROJECT_INSTANCE_CREATED) {
    // For ProjectInstanceCreatedEvent
    if (content.relativeInstancePath && content.relativeInstancePath.trim()) {
      pairs['relativeInstancePath'] = content.relativeInstancePath;
    }
  } else if (event.eventType === WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED) {
    // For SessionCreatedEvent
    if (content.relativePath && content.relativePath.trim()) {
      pairs['relativePath'] = content.relativePath;
    }
    if (content.workspacesPath && content.workspacesPath.trim()) {
      pairs['workspacesPath'] = content.workspacesPath;
    }
  } else if (event.eventType === WorkspaceEventType.RECORDING_FILE_CREATED) {
    if (content.fileName && content.fileName.trim()) {
      pairs['fileName'] = content.fileName;
    }
    if (content.originalSize != null) {
      pairs['originalSize'] = FormattingService.formatBytes(content.originalSize);
    }
    if (content.compressedSize != null) {
      pairs['compressedSize'] = FormattingService.formatBytes(content.compressedSize);
    }
  } else if (event.eventType === WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED) {
    // These events have empty content (Json.EMPTY)
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
    case WorkspaceEventType.PROJECT_INSTANCE_CREATED:
      return 'info';
    case WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED:
      return 'primary';
    case WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED:
      return 'warning';
    case WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED:
      return 'info';
    case WorkspaceEventType.RECORDING_FILE_CREATED:
      return 'green';
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
    case WorkspaceEventType.PROJECT_INSTANCE_CREATED:
      return 'text-info';
    case WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED:
      return 'text-primary';
    case WorkspaceEventType.PROJECT_INSTANCE_SESSION_DELETED:
      return 'text-warning';
    case WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED:
      return 'text-info';
    case WorkspaceEventType.RECORDING_FILE_CREATED:
      return 'text-success';
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
.filter-dropdown {
  min-width: 200px;
}

/* Workspace Cards */
.workspace-label {
  color: var(--color-text, #374151);
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
  color: var(--color-text-muted, #8b95a7);
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
  color: var(--color-text, #374151);
  font-weight: 500;
  white-space: nowrap;
}

.event-time-full {
  font-size: 0.7rem;
  color: var(--color-text-muted, #9ca3af);
  font-weight: 400;
  opacity: 0.8;
  white-space: nowrap;
}

.event-second-line {
  margin-bottom: 0.4rem;
}

.event-created-by {
  font-size: 0.7rem;
  color: var(--color-text-muted, #6b7280);
  font-weight: 500;
}

.event-created-by i {
  font-size: 0.65rem;
  opacity: 0.7;
}

.event-content-pairs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.content-pair {
  display: inline;
  font-size: 0.7rem;
  color: var(--color-text-muted, #6b7280);
  margin-right: 12px;
  white-space: nowrap;
}

.content-key {
  color: var(--color-text-muted, #9ca3af);
  font-weight: 500;
  margin-right: 4px;
}

.content-value {
  color: var(--color-text, #374151);
  font-weight: 500;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attribute-pair {
  .content-key {
    color: var(--color-text-muted, #9ca3af);
    font-style: italic;
  }

  .content-value {
    color: var(--color-text-muted, #6b7280);
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
  color: var(--color-text-muted, #9ca3af);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  font-size: 0.8rem;
  color: var(--color-text, #374151);
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
  color: var(--color-text-muted, #6b7280);
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
  color: var(--color-text, #374151);
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
  color: var(--color-text, #374151);
  font-weight: 500;
  background: linear-gradient(135deg, #f8f9fa, #ffffff);

  &:focus {
    border-color: rgba(94, 100, 255, 0.3);
    box-shadow: 0 0 0 3px rgba(94, 100, 255, 0.05);
  }
}
</style>
