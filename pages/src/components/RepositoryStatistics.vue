<script setup lang="ts">
/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
import {computed} from 'vue';
import RepositoryStatistics from "@/services/api/model/RepositoryStatistics.ts";
import RecordingStatus from "@/services/api/model/RecordingStatus.ts";
import Utils from "@/services/Utils";
import Badge from '@/components/Badge.vue';
import FormattingService from "@/services/FormattingService.ts";
import type {Variant} from "@/types/ui.ts";

interface Props {
  statistics: RepositoryStatistics | null;
}

const props = defineProps<Props>();

const getStatusVariant = (status: RecordingStatus): Variant => {
  switch (status) {
    case RecordingStatus.ACTIVE:
      return 'warning';
    case RecordingStatus.FINISHED:
      return 'green';
    case RecordingStatus.UNKNOWN:
    default:
      return 'purple';
  }
};

const lastActivityTime = computed(() => {
  if (!props.statistics?.lastActivityTime) return 'Never';
  return FormattingService.formatRelativeTime(props.statistics.lastActivityTime);
});
</script>

<template>
  <div class="mb-4" v-if="statistics">
    <div class="row g-3">
      <!-- Sessions Overview -->
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-collection text-primary"></i>
            <span class="compact-stat-title">Sessions</span>
          </div>
          <div class="compact-stat-metrics">
            <div class="metric-item">
              <span class="metric-label">Latest Status</span>
              <span class="metric-value">
                  <Badge :value="Utils.capitalize(statistics.sessionStatus.toLowerCase())"
                         :variant="getStatusVariant(statistics.sessionStatus)" size="xxs"/>
                </span>
            </div>
            <div class="metric-item">
              <span class="metric-label">Total Sessions</span>
              <span class="metric-value">{{ statistics.totalSessions }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">Last Activity</span>
              <span class="metric-value">{{ lastActivityTime }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Storage Overview -->
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-hdd text-success"></i>
            <span class="compact-stat-title">Storage</span>
          </div>
          <div class="compact-stat-metrics">
            <div class="metric-item">
              <span class="metric-label">Total Size</span>
              <span class="metric-value">{{ FormattingService.formatBytes(statistics.totalSize) }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">Total Files</span>
              <span class="metric-value">{{ statistics.totalFiles }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">Biggest Session</span>
              <span class="metric-value">{{ FormattingService.formatBytes(statistics.biggestSessionSize) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- File Types -->
      <div class="col-md-4">
        <div class="compact-stat-card">
          <div class="compact-stat-header">
            <i class="bi bi-files text-info"></i>
            <span class="compact-stat-title">File Types</span>
          </div>
          <div class="compact-stat-metrics">
            <div class="metric-item">
              <span class="metric-label">JFR Files</span>
              <span class="metric-value text-primary">{{ statistics.jfrFiles }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">Heap Dumps</span>
              <span class="metric-value text-danger">{{ statistics.heapDumpFiles }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">JVM Logs</span>
              <span class="metric-value text-warning">{{ statistics.logFiles }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">JVM Error Logs</span>
              <span class="metric-value text-danger-emphasis">{{ statistics.errorLogFiles }}</span>
            </div>
            <div class="metric-item">
              <span class="metric-label">Other Files</span>
              <span class="metric-value">{{ statistics.otherFiles }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Compact Repository Statistics Cards Styling */
.compact-stat-card {
  background: linear-gradient(135deg, #f8f9fa, #ffffff);
  border: 1px solid rgba(94, 100, 255, 0.08);
  border-radius: 8px;
  padding: 12px 16px;
  height: 100%;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04),
  0 1px 2px rgba(0, 0, 0, 0.02);
}

.compact-stat-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(0, 0, 0, 0.06),
  0 2px 4px rgba(94, 100, 255, 0.1);
  border-color: rgba(94, 100, 255, 0.15);
}

.compact-stat-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.compact-stat-header i {
  font-size: 1rem;
}

.compact-stat-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.compact-stat-metrics {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}

.metric-label {
  font-size: 0.75rem;
  color: #6b7280;
  font-weight: 500;
}

.metric-value {
  font-size: 0.8rem;
  font-weight: 600;
  color: #374151;
  text-align: right;
}

.text-primary {
  color: #5e64ff !important;
}

/* Responsive adjustments for smaller screens */
@media (max-width: 768px) {
  .compact-stat-card {
    padding: 10px 12px;
  }

  .compact-stat-header {
    gap: 6px;
    margin-bottom: 6px;
  }

  .compact-stat-title {
    font-size: 0.75rem;
  }

  .metric-label {
    font-size: 0.7rem;
  }

  .metric-value {
    font-size: 0.75rem;
  }
}

@media (max-width: 576px) {
  .compact-stat-card {
    padding: 8px 10px;
  }

  .compact-stat-metrics {
    gap: 3px;
  }

  .metric-item {
    padding: 1px 0;
  }
}
</style>
