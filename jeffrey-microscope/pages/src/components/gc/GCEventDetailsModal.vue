<!--
  - Jeffrey
  - Copyright (C) 2025 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <GenericModal
    :modal-id="modalId"
    :show="show"
    title="Concurrent GC Event Details"
    icon="bi-layers"
    size="xl"
    @update:show="$emit('update:show', $event)"
  >
    <div v-if="event" class="event-details">
      <!-- Event Info Header -->
      <div class="event-info-header mb-4">
        <div class="d-flex align-items-center gap-3 mb-2">
          <Badge :value="`GC ID: ${event.gcId}`" variant="secondary" size="m" />
          <Badge
            :value="event.collectorName"
            :variant="getGenerationTypeBadgeVariant(event.generationType)"
            size="m"
          />
        </div>
        <div class="event-metrics">
          <div class="metric-item">
            <i class="bi bi-clock"></i>
            <span class="metric-label">Duration:</span>
            <span class="metric-value text-danger fw-bold">{{
              FormattingService.formatDuration2Units(event.duration)
            }}</span>
          </div>
          <div class="metric-item">
            <i class="bi bi-pause-circle"></i>
            <span class="metric-label">Sum of Pauses:</span>
            <span class="metric-value">{{
              FormattingService.formatDuration2Units(event.sumOfPauses)
            }}</span>
          </div>
          <div class="metric-item">
            <i class="bi bi-calendar-event"></i>
            <span class="metric-label">Timestamp:</span>
            <span class="metric-value">{{
              FormattingService.formatTimestamp(event.timestamp)
            }}</span>
          </div>
        </div>
      </div>

      <!-- Concurrent Phases Section -->
      <div v-if="event.phases && event.phases.length > 0" class="phases-section">
        <div class="section-header">
          <h6><i class="bi bi-layers me-2"></i>Concurrent Phases ({{ event.phases.length }})</h6>
        </div>
        <div class="phases-table-container">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Phase Name</th>
                <th>Duration</th>
                <th>Timestamp</th>
                <th>Timestamp from Start</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(phase, index) in event.phases" :key="index">
                <td>
                  <Badge :value="phase.name" variant="info" size="m" />
                </td>
                <td>{{ FormattingService.formatDuration2Units(phase.duration) }}</td>
                <td>{{ FormattingService.formatTimestamp(phase.timestamp) }}</td>
                <td>{{ FormattingService.formatDuration2Units(phase.timestampFromStart) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- No Phases Message -->
      <div v-else class="phases-section">
        <div class="section-header">
          <h6><i class="bi bi-layers me-2"></i>Concurrent Phases</h6>
        </div>
        <div class="no-phases">
          <i class="bi bi-info-circle me-2"></i>
          No concurrent phases recorded for this event
        </div>
      </div>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import GenericModal from '@shared/components/GenericModal.vue';
import Badge from '@shared/components/Badge.vue';
import ConcurrentEvent from '@/services/api/model/ConcurrentEvent';
import FormattingService from '@shared/services/FormattingService';
import { getGenerationTypeBadgeVariant } from '@/services/api/model/GarbageCollectionUtils';

interface Props {
  event: ConcurrentEvent | null;
  modalId: string;
  show: boolean;
}

defineProps<Props>();
defineEmits(['update:show']);
</script>

<style scoped>
.event-details {
  font-size: 0.9rem;
}

.event-info-header {
  background: var(--color-light);
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.event-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin-top: 0.75rem;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.metric-item i {
  color: var(--color-text-muted);
  font-size: 0.9rem;
}

.metric-label {
  color: var(--color-text-muted);
  font-weight: 500;
}

.metric-value {
  font-weight: 600;
  color: var(--color-text);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.75rem;
}

.section-header h6 {
  margin: 0;
  font-weight: 600;
  color: var(--color-text);
}

.phases-table-container {
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 6px;
  overflow: hidden;
}

.phases-table-container .table {
  margin-bottom: 0;
}

.phases-table-container .table td {
  font-size: 0.8rem;
  padding: 0.5rem;
  vertical-align: middle;
}

.phases-table-container .table tbody tr:hover {
  background-color: rgba(0, 123, 255, 0.05);
}

.no-phases {
  padding: 1rem;
  text-align: center;
  color: var(--color-text-muted);
  font-style: italic;
  background: var(--color-light);
  border: 1px solid var(--color-border);
  border-radius: 6px;
}

@media (max-width: 768px) {
  .event-metrics {
    flex-direction: column;
    gap: 0.75rem;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .phases-table-container .table td {
    font-size: 0.75rem;
    padding: 0.375rem;
  }
}
</style>
