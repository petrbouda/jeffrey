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
    title="GC Pause Event Details"
    icon="bi-pause-circle"
    size="xl"
    @update:show="$emit('update:show', $event)">
    
    <div v-if="event" class="pause-details">
      <!-- Event Info Header -->
      <div class="pause-info-header mb-4">
        <div class="d-flex align-items-center gap-3 mb-2">
          <Badge 
            :value="`GC ID: ${event.gcId}`"
            variant="primary"
            size="m"
          />
          <Badge 
            v-if="event.collectorName"
            :value="event.collectorName"
            :variant="getGenerationTypeBadgeVariant(event.generationType)"
            size="m"
          />
          <Badge
            v-if="event.type"
            :value="event.type"
            variant="secondary"
            size="m"
          />
        </div>
        <div class="pause-metrics">
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
            <i class="bi bi-stopwatch"></i>
            <span class="metric-label">Longest Pause:</span>
            <span class="metric-value">{{ FormattingService.formatDuration2Units(event.longestPause) }}</span>
          </div>
          <div class="metric-item">
            <i class="bi bi-calendar-event"></i>
            <span class="metric-label">Timestamp:</span>
            <span class="metric-value">{{ FormattingService.formatTimestamp(event.timestamp) }}</span>
          </div>
        </div>
      </div>

      <!-- GC Cause and Type Information -->
      <div class="gc-info-section mb-4">
        <div class="section-header">
          <h6><i class="bi bi-info-circle me-2"></i>Garbage Collection Information</h6>
        </div>
        <div class="gc-info-grid">
          <div class="info-item">
            <span class="info-label">Cause:</span>
            <span class="info-value">{{ event.cause }}</span>
          </div>
          <div class="info-item">
            <span class="info-label">Collector Name:</span>
            <span class="info-value">
              {{ event.collectorName }}
            </span>
          </div>
          <div class="info-item" v-if="event.type">
            <span class="info-label">GC Type:</span>
            <span class="info-value">{{ event.type }}</span>
          </div>
        </div>
      </div>

      <!-- Memory Information -->
      <div class="memory-section mb-4">
        <div class="section-header">
          <h6><i class="bi bi-memory me-2"></i>Memory Information</h6>
        </div>
        <div class="memory-grid">
          <div class="memory-item">
            <div class="memory-label">Before GC</div>
            <div class="memory-value">{{ FormattingService.formatBytes(event.beforeGC) }}</div>
            <div class="memory-bar">
              <div class="progress">
                <div class="progress-bar bg-warning" 
                     :style="{ width: getMemoryPercentage(event.beforeGC, event.heapSize) + '%' }">
                </div>
              </div>
            </div>
          </div>
          <div class="memory-item">
            <div class="memory-label">After GC</div>
            <div class="memory-value">{{ FormattingService.formatBytes(event.afterGC) }}</div>
            <div class="memory-bar">
              <div class="progress">
                <div class="progress-bar bg-success" 
                     :style="{ width: getMemoryPercentage(event.afterGC, event.heapSize) + '%' }">
                </div>
              </div>
            </div>
          </div>
          <div class="memory-item">
            <div class="memory-label">Memory Freed</div>
            <div class="memory-value text-success fw-bold">{{ FormattingService.formatBytes(event.freed) }}</div>
            <div class="memory-bar">
              <div class="progress">
                <div class="progress-bar bg-info" 
                     :style="{ width: getMemoryPercentage(event.freed, event.heapSize) + '%' }">
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Efficiency Metrics -->
      <div class="efficiency-section">
        <div class="section-header">
          <h6><i class="bi bi-speedometer me-2"></i>Efficiency Metrics</h6>
        </div>
        <div class="efficiency-grid">
          <div class="efficiency-item">
            <div class="efficiency-label">Memory Change</div>
            <div class="efficiency-value">
              <div class="d-flex align-items-center">
                <div class="progress flex-grow-1 me-3" style="height: 20px;">
                  <div class="progress-bar" 
                      :class="getDifferenceBarClass(event.beforeGC, event.afterGC)"
                      :style="{ width: getDifferencePercentage(event.beforeGC, event.afterGC) + '%' }">
                    <span class="progress-text">{{ getDifferencePercentage(event.beforeGC, event.afterGC).toFixed(1) }}%</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="efficiency-item">
            <div class="efficiency-label">Heap Size</div>
            <div class="efficiency-value">{{ FormattingService.formatBytes(event.heapSize) }}</div>
          </div>
        </div>
      </div>
    </div>
  </GenericModal>
</template>

<script setup lang="ts">
import GenericModal from '@/components/GenericModal.vue';
import Badge from '@/components/Badge.vue';
import GCEvent from '@/services/api/model/GCEvent';
import GCGenerationType from '@/services/api/model/GCGenerationType';
import FormattingService from '@/services/FormattingService';

interface Props {
  event: GCEvent | null;
  modalId: string;
  show: boolean;
}

defineProps<Props>();
defineEmits(['update:show']);

const getGenerationTypeBadgeVariant = (generationType: GCGenerationType) => {
  switch (generationType) {
    case GCGenerationType.YOUNG:
      return 'blue' as const;
    case GCGenerationType.OLD:
      return 'orange' as const;
    default:
      return 'grey' as const;
  }
};

const getDifferenceBarClass = (beforeGC: number, afterGC: number) => {
  const difference = afterGC - beforeGC;

  if (difference < 0) {
    return 'bg-success'; // Memory decreased (good) - green
  } else {
    return 'bg-danger'; // Memory increased (bad) - red
  }
};

const getDifferencePercentage = (beforeGC: number, afterGC: number) => {
  if (beforeGC === 0) return 0;
  const difference = Math.abs(afterGC - beforeGC);
  return Math.min((difference / beforeGC) * 100, 100);
};

const getMemoryPercentage = (used: number, total: number) => {
  if (total === 0) return 0;
  return Math.min((used / total) * 100, 100);
};
</script>

<style scoped>
.pause-details {
  font-size: 0.9rem;
}

.pause-info-header {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}


.pause-metrics {
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

.gc-info-section,
.memory-section,
.efficiency-section {
  background: #f8f9fa;
  padding: 1rem;
  border-radius: 6px;
  border: 1px solid #e9ecef;
}

.gc-info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.info-label {
  font-size: 0.8rem;
  color: #6c757d;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  font-weight: 600;
  color: #495057;
}

.memory-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 1.5rem;
}

.memory-item {
  text-align: center;
}

.memory-label {
  font-size: 0.8rem;
  color: #6c757d;
  font-weight: 500;
  margin-bottom: 0.5rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.memory-value {
  font-size: 1rem;
  font-weight: 600;
  color: #495057;
  margin-bottom: 0.5rem;
}

.memory-bar .progress {
  height: 8px;
  border-radius: 4px;
}

.efficiency-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 1.5rem;
  align-items: center;
}

.efficiency-item {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.efficiency-label {
  font-size: 0.8rem;
  color: #6c757d;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.efficiency-value {
  font-weight: 600;
  color: #495057;
}

.progress-text {
  font-size: 0.75rem;
  font-weight: 600;
  color: white;
  text-shadow: 0 1px 2px rgba(0,0,0,0.3);
}

@media (max-width: 768px) {
  .pause-metrics {
    flex-direction: column;
    gap: 0.75rem;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }

  .gc-info-grid {
    grid-template-columns: 1fr;
  }

  .memory-grid {
    grid-template-columns: 1fr;
    gap: 1rem;
  }

  .efficiency-grid {
    grid-template-columns: 1fr;
    gap: 1rem;
  }
}
</style>
