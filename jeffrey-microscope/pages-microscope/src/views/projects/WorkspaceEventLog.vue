<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
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

<template>
  <div class="workspace-event-log">
    <div class="event-toolbar">
      <select class="event-type-select" v-model="selectedEventType">
        <option value="">All event types</option>
        <option value="PROJECT_CREATED">Project Created</option>
        <option value="PROJECT_DELETED">Project Deleted</option>
        <option value="PROJECT_INSTANCE_CREATED">Instance Created</option>
        <option value="PROJECT_INSTANCE_SESSION_CREATED">Session Created</option>
        <option value="PROJECT_INSTANCE_SESSION_DELETED">Session Deleted</option>
        <option value="PROJECT_INSTANCE_SESSION_FINISHED">Session Finished</option>
      </select>
      <span class="event-toolbar-meta">
        Showing {{ filteredEvents.length }} of {{ events.length }}<span v-if="totalCount > events.length"> · {{ totalCount }} total</span>
      </span>
    </div>

    <LoadingState v-if="loading" message="Loading workspace events…" />

    <div v-else-if="errorMessage" class="event-error">
      <i class="event-error-icon bi bi-exclamation-triangle-fill"></i>
      <h5>Failed to load events</h5>
      <p>{{ errorMessage }}</p>
      <button class="btn btn-primary" @click="refresh">
        <i class="bi bi-arrow-clockwise me-2"></i>Retry
      </button>
    </div>

    <div v-else-if="filteredEvents.length > 0" class="event-list">
      <div v-if="totalCount > events.length" class="event-cap-notice">
        <i class="bi bi-info-circle"></i>
        Only the latest {{ events.length }} events are shown.
        <span class="cap-detail">{{ totalCount }} total · use the type filter to drill down.</span>
      </div>
      <div
        v-for="event in filteredEvents"
        :key="event.eventId"
        class="event-row"
        @click="openEventDetails(event)"
      >
        <div class="event-row-head">
          <Badge
            :value="EventContentParser.getEventDisplayName(event.eventType)"
            :variant="getEventBadgeVariant(event.eventType)"
            size="s"
            :uppercase="false"
          />
          <span class="event-ids">
            {{ event.originEventId }} • {{ event.projectId }}
          </span>
          <span class="event-time">
            <span class="event-time-full">{{
              FormattingService.formatTimestamp(event.originCreatedAt)
            }}</span>
            <span class="event-time-relative">{{
              FormattingService.formatRelativeTime(event.originCreatedAt)
            }}</span>
          </span>
        </div>
        <div class="event-row-meta">
          <i class="bi bi-person-fill"></i>{{ event.createdBy }}
        </div>
        <div v-if="contentPairsFor(event)" class="event-row-pairs">
          <span
            v-for="(value, key) in contentPairsFor(event)"
            :key="key"
            class="content-pair"
          >
            <span class="content-key">{{ key }}:</span>
            <span class="content-value">{{ value }}</span>
          </span>
          <template v-if="attributePairsFor(event)">
            <span
              v-for="(value, key) in attributePairsFor(event)"
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

    <EmptyState
      v-else
      icon="bi-journal-text"
      title="No events"
      :description="
        events.length === 0
          ? 'This workspace has not produced any events yet.'
          : 'No events match the current filter or search.'
      "
    />

    <GenericModal
      modal-id="workspaceEventDetailsModal"
      :show="showEventDetailsModal"
      @update:show="showEventDetailsModal = $event"
      :title="
        selectedEvent
          ? EventContentParser.getEventDisplayName(selectedEvent.eventType)
          : 'Event Details'
      "
      :icon="
        selectedEvent ? EventContentParser.getEventIcon(selectedEvent.eventType) : 'bi-journal-text'
      "
      size="lg"
    >
      <div v-if="selectedEvent" class="event-modal-body">
        <div class="info-grid">
          <div class="info-pair">
            <span class="label">Event</span>
            <span class="value">{{ selectedEvent.originEventId }}</span>
          </div>
          <div class="info-pair">
            <span class="label">Project</span>
            <span class="value">{{ selectedEvent.projectId }}</span>
          </div>
          <div class="info-pair">
            <span class="label">Workspace</span>
            <span class="value">{{ selectedEvent.workspaceRefId }}</span>
          </div>
          <div class="info-pair">
            <span class="label">Created By</span>
            <span class="value">{{ selectedEvent.createdBy }}</span>
          </div>
          <div class="info-pair">
            <span class="label">Created</span>
            <span class="value">{{
              FormattingService.formatTimestamp(selectedEvent.originCreatedAt)
            }}</span>
          </div>
        </div>

        <div class="content-section">
          <div class="content-header">Content</div>
          <pre class="content-json">{{ formatEventContent(selectedEvent) }}</pre>
        </div>
      </div>

      <template #footer>
        <button type="button" class="btn btn-secondary" @click="showEventDetailsModal = false">
          Close
        </button>
      </template>
    </GenericModal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import WorkspaceEvent from '@/services/api/model/WorkspaceEvent';
import WorkspaceEventType from '@/services/api/model/WorkspaceEventType';
import WorkspaceClient from '@/services/api/WorkspaceClient';
import { EventContentParser } from '@/services/EventContentParser';
import FormattingService from '@/services/FormattingService';
import Badge from '@/components/Badge.vue';
import GenericModal from '@/components/GenericModal.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';

const DEFAULT_LIMIT = 100;

const props = withDefaults(
  defineProps<{
    serverId: string;
    workspaceId: string;
    searchQuery?: string;
    limit?: number;
  }>(),
  { limit: DEFAULT_LIMIT },
);

const emit = defineEmits<{
  (e: 'update:count', value: number): void;
}>();

const events = ref<WorkspaceEvent[]>([]);
const totalCount = ref(0);
const loading = ref(false);
const errorMessage = ref('');
const selectedEventType = ref<string>('');
const selectedEvent = ref<WorkspaceEvent | null>(null);
const showEventDetailsModal = ref(false);

const filteredEvents = computed(() => {
  let list = [...events.value];

  if (selectedEventType.value) {
    list = list.filter(e => e.eventType === selectedEventType.value);
  }

  const query = (props.searchQuery ?? '').trim().toLowerCase();
  if (query) {
    list = list.filter(
      e =>
        EventContentParser.getEventDescription(e).toLowerCase().includes(query) ||
        e.originEventId.toLowerCase().includes(query) ||
        e.projectId.toLowerCase().includes(query),
    );
  }

  list.sort((a, b) => b.originCreatedAt - a.originCreatedAt);
  return list;
});

const refresh = async () => {
  if (!props.serverId || !props.workspaceId) {
    events.value = [];
    totalCount.value = 0;
    emit('update:count', 0);
    return;
  }

  loading.value = true;
  errorMessage.value = '';

  try {
    const client = new WorkspaceClient(props.serverId);
    const response = await client.getEvents(props.workspaceId, props.limit);
    const fetched = [...response.events].sort((a, b) => b.originCreatedAt - a.originCreatedAt);
    events.value = fetched;
    totalCount.value = response.totalCount;
    emit('update:count', response.totalCount);
  } catch (error: unknown) {
    errorMessage.value =
      error instanceof Error ? error.message : 'Could not load workspace events';
    events.value = [];
    totalCount.value = 0;
    emit('update:count', 0);
  } finally {
    loading.value = false;
  }
};

watch(
  () => [props.serverId, props.workspaceId] as const,
  () => {
    selectedEventType.value = '';
    refresh();
  },
  { immediate: true },
);

const openEventDetails = (event: WorkspaceEvent) => {
  selectedEvent.value = event;
  showEventDetailsModal.value = true;
};

const formatEventContent = (event: WorkspaceEvent) => {
  const content = EventContentParser.parseContent(event);
  return JSON.stringify(content, null, 2);
};

const contentPairsFor = (event: WorkspaceEvent): Record<string, string> | null => {
  const content = EventContentParser.parseContent(event);
  const pairs: Record<string, string> = {};

  if (event.eventType === WorkspaceEventType.PROJECT_CREATED) {
    if (content.projectName?.trim()) pairs['projectName'] = content.projectName;
    if (content.repositoryType?.trim()) pairs['repositoryType'] = content.repositoryType;
  } else if (event.eventType === WorkspaceEventType.PROJECT_INSTANCE_CREATED) {
    if (content.relativeInstancePath?.trim())
      pairs['relativeInstancePath'] = content.relativeInstancePath;
  } else if (event.eventType === WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED) {
    if (content.relativePath?.trim()) pairs['relativePath'] = content.relativePath;
    if (content.workspacesPath?.trim()) pairs['workspacesPath'] = content.workspacesPath;
  } else if (event.eventType === WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED) {
    // empty content
  } else {
    Object.entries(content).forEach(([key, value]) => {
      const stringValue =
        typeof value === 'object' && value !== null ? JSON.stringify(value) : String(value);
      if (stringValue && stringValue.trim()) pairs[key] = stringValue;
    });
  }

  return Object.keys(pairs).length > 0 ? pairs : null;
};

const attributePairsFor = (event: WorkspaceEvent): Record<string, string> | null => {
  if (event.eventType !== WorkspaceEventType.PROJECT_CREATED) return null;
  const content = EventContentParser.parseContent(event);
  if (!content.attributes || typeof content.attributes !== 'object') return null;
  const pairs: Record<string, string> = {};
  Object.entries(content.attributes).forEach(([key, value]) => {
    pairs[key] = String(value);
  });
  return Object.keys(pairs).length > 0 ? pairs : null;
};

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
    default:
      return 'secondary';
  }
};

defineExpose({ refresh });
</script>

<style scoped>
.workspace-event-log {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: var(--spacing-5);
}

.event-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 8px 12px;
}

.event-type-select {
  background: var(--color-card, #ffffff);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 5px 28px 5px 10px;
  font-size: 12px;
  color: var(--color-text-dark);
  cursor: pointer;
}
.event-type-select:focus {
  outline: none;
  border-color: var(--color-primary-border);
  box-shadow: 0 0 0 3px var(--color-primary-lighter);
}

.event-toolbar-meta {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-muted);
}

.event-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.event-cap-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--color-info-bg);
  border: 1px solid var(--color-info-border);
  border-radius: var(--radius-base);
  font-size: 12px;
  color: var(--color-info-text);
}
.event-cap-notice i {
  font-size: 14px;
}
.event-cap-notice .cap-detail {
  margin-left: auto;
  color: var(--color-text-muted);
  font-size: 11.5px;
}

.event-row {
  background: var(--color-card, #ffffff);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color 0.12s ease, box-shadow 0.12s ease;
}
.event-row:hover {
  border-color: var(--color-primary-border-light);
  box-shadow: var(--shadow-sm);
}

.event-row-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.event-ids {
  flex: 1;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-time {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.event-time-full {
  font-size: 0.7rem;
  color: var(--color-text-muted);
}
.event-time-relative {
  font-size: 0.7rem;
  color: var(--color-text);
  font-weight: 500;
}

.event-row-meta {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  margin-top: 6px;
}
.event-row-meta i {
  margin-right: 4px;
}

.event-row-pairs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 8px;
}

.content-pair {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  white-space: nowrap;
}
.content-key {
  margin-right: 4px;
  font-weight: 500;
}
.content-value {
  color: var(--color-text);
  font-weight: 500;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  vertical-align: bottom;
}
.attribute-pair .content-key {
  font-style: italic;
}
.attribute-pair .content-value {
  color: var(--color-text-muted);
}

.event-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  text-align: center;
  color: var(--color-text);
}
.event-error-icon {
  font-size: 28px;
  color: var(--color-danger);
}

.event-modal-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 14px;
}
.info-pair {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.label {
  font-size: 0.7rem;
  color: var(--color-text-muted);
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.value {
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.8rem;
  color: var(--color-text);
  background: var(--color-card, #ffffff);
  padding: 6px 10px;
  border-radius: var(--radius-base);
  border: 1px solid var(--color-border);
  word-break: break-all;
}

.content-header {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: 8px;
}
.content-json {
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-base);
  padding: 12px;
  font-size: 0.75rem;
  color: var(--color-text);
  max-height: 250px;
  overflow-y: auto;
  margin: 0;
  font-family: SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  line-height: 1.4;
}
</style>
