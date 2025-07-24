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

<template>
  <GenericModal
    :modal-id="modalId"
    :show="show"
    title="Concurrent GC Event Details"
    icon="bi-layers"
    size="xl"
    @update:show="$emit('update:show', $event)">
    
    <div v-if="event" class="event-details">
      <!-- Event Info Header -->
      <div class="event-info-header mb-4">
        <div class="d-flex align-items-center gap-3 mb-2">
          <Badge :value="`GC ID: ${event.gcId}`" variant="secondary" size="m"/>
          <Badge :value="event.collectorName" :variant="getGenerationTypeBadgeVariant(event.generationType)" size="m"/>
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
            <span class="metric-value">{{ FormattingService.formatDuration2Units(event.sumOfPauses) }}</span>
          </div>
          <div class="metric-item">
            <i class="bi bi-calendar-event"></i>
            <span class="metric-label">Timestamp:</span>
            <span class="metric-value">{{ FormattingService.formatTimestamp(event.timestamp) }}</span>
          </div>
        </div>
      </div>

      <!-- Concurrent Phases Section -->
      <div class="phases-section" v-if="event.phases && event.phases.length > 0">
        <div class="section-header">
          <h6><i class="bi bi-layers me-2"></i>Concurrent Phases ({{ event.phases.length }})</h6>
        </div>
        <div class="phases-table-container">
          <table class="table table-sm table-hover">
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
                  <Badge :value="phase.name" variant="info" size="m"/>
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
      <div class="phases-section" v-else>
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
import GenericModal from '@/components/GenericModal.vue';
import Badge from '@/components/Badge.vue';
import ConcurrentEvent from '@/services/profile/gc/ConcurrentEvent';
import FormattingService from '@/services/FormattingService';
import { getGenerationTypeBadgeVariant } from '@/services/profile/gc/GarbageCollectionUtils';

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
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid #e9ecef;
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
  color: #6c757d;
  font-size: 0.9rem;
}

.metric-label {
  color: #6c757d;
  font-weight: 500;
}

.metric-value {
  font-weight: 600;
  color: #495057;
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
  color: #495057;
}

.phases-table-container {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  overflow: hidden;
}

.phases-table-container .table {
  margin-bottom: 0;
}

.phases-table-container .table thead th {
  background-color: #e9ecef;
  font-weight: 600;
  color: #495057;
  font-size: 0.8rem;
  padding: 0.75rem 0.5rem;
  border-bottom: 1px solid #dee2e6;
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
  color: #6c757d;
  font-style: italic;
  background: #f8f9fa;
  border: 1px solid #e9ecef;
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
