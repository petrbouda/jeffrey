<!--
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
 -->

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import LoadingState from '@/components/LoadingState.vue';
import EmptyState from '@/components/EmptyState.vue';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import type { ImportantMessage, Severity } from '@/services/api/model/ImportantMessage';
import ProjectMessagesClient from '@/services/api/ProjectMessagesClient';
import ToastService from '@/services/ToastService';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId } = useNavigation();

const isLoading = ref(true);
const alerts = ref<ImportantMessage[]>([]);

// Time range options in minutes
type TimeRangeOption = { label: string; minutes: number };
const timeRangeOptions: TimeRangeOption[] = [
  { label: 'Last 1 hour', minutes: 60 },
  { label: 'Last 6 hours', minutes: 360 },
  { label: 'Last 24 hours', minutes: 1440 },
  { label: 'Last 7 days', minutes: 10080 },
  { label: 'All time', minutes: 0 }
];
const selectedTimeRange = ref<number>(1440); // Default: Last 24 hours

// Filter state
const selectedSeverity = ref<Severity | ''>('');
const selectedSession = ref('');
const searchQuery = ref('');

// Display limit
const displayLimit = 50;

// Get unique sessions from alerts
const sessions = computed(() => {
  const sessionSet = new Map<string, string>();
  alerts.value.forEach(a => sessionSet.set(a.sessionId, a.sessionId));
  return Array.from(sessionSet.entries()).map(([id]) => ({ id, name: id }));
});

async function loadAlerts() {
  if (!workspaceId.value || !projectId.value) return;

  isLoading.value = true;
  try {
    const client = new ProjectMessagesClient(workspaceId.value, projectId.value);
    // For "All time", don't pass time parameters
    if (selectedTimeRange.value === 0) {
      alerts.value = await client.getAlerts();
    } else {
      const now = Date.now();
      const startMillis = now - selectedTimeRange.value * 60 * 1000;
      alerts.value = await client.getAlerts(startMillis, now);
    }
  } catch (error: any) {
    console.error('Failed to load alerts:', error);
    const message = error.response?.data?.message || 'Failed to load alerts';
    ToastService.error('Alerts', message);
    alerts.value = [];
  } finally {
    isLoading.value = false;
  }
}

// Reload when time range changes
watch(selectedTimeRange, () => {
  loadAlerts();
});

onMounted(() => {
  loadAlerts();
});

// Computed: alerts filtered by time range (already filtered by API, but keep for client-side filtering)
const timeFilteredAlerts = computed(() => {
  return alerts.value;
});

// Computed: timeseries data for chart (grouped by severity)
const alertsTimeseriesData = computed(() => {
  const filteredAlerts = timeFilteredAlerts.value;
  if (filteredAlerts.length === 0) return [];

  // Create time buckets (10 buckets across the time range)
  const now = Date.now();
  const rangeMinutes = selectedTimeRange.value || 10080; // Default to 7 days if "All time"
  const bucketDurationMs = (rangeMinutes * 60 * 1000) / 20;
  const startTime = now - rangeMinutes * 60 * 1000;

  // Initialize buckets with counts
  const buckets: Map<number, number> = new Map();
  for (let i = 0; i < 20; i++) {
    const bucketTime = startTime + i * bucketDurationMs;
    buckets.set(bucketTime, 0);
  }

  // Count alerts per bucket
  filteredAlerts.forEach(alert => {
    const alertTime = new Date(alert.createdAt).getTime();
    // Find the bucket this alert belongs to
    const bucketIndex = Math.floor((alertTime - startTime) / bucketDurationMs);
    if (bucketIndex >= 0 && bucketIndex < 20) {
      const bucketTime = startTime + bucketIndex * bucketDurationMs;
      buckets.set(bucketTime, (buckets.get(bucketTime) || 0) + 1);
    }
  });

  // Convert to array format for TimeSeriesChart [timestamp, count]
  return Array.from(buckets.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([time, count]) => [time, count]);
});

// Computed: filtered alerts (applies severity/session/search on top of time filter)
const filteredAlerts = computed(() => {
  return timeFilteredAlerts.value.filter(alert => {
    // Filter by severity
    if (selectedSeverity.value && alert.severity !== selectedSeverity.value) {
      return false;
    }

    // Filter by session
    if (selectedSession.value && alert.sessionId !== selectedSession.value) {
      return false;
    }

    // Filter by search query
    if (searchQuery.value) {
      const query = searchQuery.value.toLowerCase();
      return (
        alert.message.toLowerCase().includes(query) ||
        alert.type.toLowerCase().includes(query)
      );
    }

    return true;
  });
});

// Computed: limited alerts for display
const displayedAlerts = computed(() => {
  return filteredAlerts.value.slice(0, displayLimit);
});

// Computed: TOTAL alert counts by severity (all alerts, for StatsTable)
const totalCriticalCount = computed(() => alerts.value.filter(a => a.severity === 'CRITICAL').length);
const totalHighCount = computed(() => alerts.value.filter(a => a.severity === 'HIGH').length);
const totalMediumCount = computed(() => alerts.value.filter(a => a.severity === 'MEDIUM').length);
const totalLowCount = computed(() => alerts.value.filter(a => a.severity === 'LOW').length);

// Computed: time-filtered alert counts by severity (for severity bar)
const criticalCount = computed(() => timeFilteredAlerts.value.filter(a => a.severity === 'CRITICAL').length);
const highCount = computed(() => timeFilteredAlerts.value.filter(a => a.severity === 'HIGH').length);
const mediumCount = computed(() => timeFilteredAlerts.value.filter(a => a.severity === 'MEDIUM').length);
const lowCount = computed(() => timeFilteredAlerts.value.filter(a => a.severity === 'LOW').length);

// Computed: StatsTable metrics (showing TOTAL counts, grouped)
const alertMetrics = computed(() => [
  {
    icon: 'exclamation-circle',
    title: 'Critical & High',
    value: (totalCriticalCount.value + totalHighCount.value).toString(),
    variant: 'danger' as const,
    breakdown: [
      { label: 'Critical', value: totalCriticalCount.value.toString() },
      { label: 'High', value: totalHighCount.value.toString() }
    ]
  },
  {
    icon: 'info-circle',
    title: 'Medium & Low',
    value: (totalMediumCount.value + totalLowCount.value).toString(),
    variant: 'info' as const,
    breakdown: [
      { label: 'Medium', value: totalMediumCount.value.toString() },
      { label: 'Low', value: totalLowCount.value.toString() }
    ]
  }
]);

// Get badge class for severity
const getSeverityBadgeClass = (severity: Severity): string => {
  switch (severity) {
    case 'CRITICAL':
      return 'badge-critical';
    case 'HIGH':
      return 'badge-high';
    case 'MEDIUM':
      return 'badge-medium';
    case 'LOW':
      return 'badge-low';
    default:
      return '';
  }
};

// Format createdAt to relative time
const formatTimeAgo = (createdAt: string): string => {
  const ts = new Date(createdAt).getTime();
  return FormattingService.formatRelativeTime(ts);
};

// Generate a unique key for table rows
const getAlertKey = (alert: ImportantMessage, index: number): string => {
  return `${alert.sessionId}-${alert.type}-${alert.createdAt}-${index}`;
};

</script>

<template>
  <PageHeader
    title="Alerts"
    description="Monitor and manage project alerts from active sessions."
    icon="bi-bell"
  >
    <!-- Loading State -->
    <LoadingState v-if="isLoading" message="Loading alerts..." />

    <template v-else>
      <!-- Summary Statistics -->
      <div class="mb-4">
        <StatsTable :metrics="alertMetrics" />
      </div>

      <!-- Severity Summary Bar -->
      <div class="severity-bar mb-3">
        <div class="severity-card time">
          <i class="bi bi-clock"></i>
          <select v-model="selectedTimeRange" class="time-select-inline">
            <option v-for="option in timeRangeOptions" :key="option.minutes" :value="option.minutes">{{ option.label }}</option>
          </select>
        </div>
        <div class="severity-card critical">
          <span class="card-count">{{ criticalCount }}</span>
          <span class="card-label">Critical</span>
        </div>
        <div class="severity-card high">
          <span class="card-count">{{ highCount }}</span>
          <span class="card-label">High</span>
        </div>
        <div class="severity-card medium">
          <span class="card-count">{{ mediumCount }}</span>
          <span class="card-label">Medium</span>
        </div>
        <div class="severity-card low">
          <span class="card-count">{{ lowCount }}</span>
          <span class="card-label">Low</span>
        </div>
      </div>

      <!-- Alerts Over Time Chart -->
      <div class="mb-3">
        <TimeSeriesChart
          v-if="alertsTimeseriesData.length > 0"
          :primary-data="alertsTimeseriesData"
          :visible-minutes="999999"
          :primary-axis-type="AxisFormatType.COUNT"
          primary-title="Alert Count"
          time-unit="milliseconds"
        />
        <EmptyState
          v-else
          icon="bi-graph-up"
          title="No Alert Data"
          description="No alerts in the selected time range."
        />
      </div>

      <!-- Alerts List Card -->
      <div class="card mb-4">
        <div class="card-header d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center gap-2">
            <select
              v-model="selectedSeverity"
              class="form-select form-select-sm filter-select"
            >
              <option value="">All Severities</option>
              <option value="CRITICAL">Critical</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
            <select
              v-model="selectedSession"
              class="form-select form-select-sm filter-select"
            >
              <option value="">All Sessions</option>
              <option v-for="session in sessions" :key="session.id" :value="session.id">
                {{ session.name }}
              </option>
            </select>
            <input
              v-model="searchQuery"
              type="text"
              class="form-control form-control-sm search-input"
              placeholder="Search alerts..."
            />
          </div>
          <div class="d-flex align-items-center">
            <span class="badge bg-info">
              <i class="bi bi-info-circle me-1"></i>Showing {{ Math.min(displayLimit, filteredAlerts.length) }} of {{ filteredAlerts.length }} alerts
            </span>
          </div>
        </div>
        <div class="card-body p-0">

          <!-- Alerts Table -->
          <div v-if="filteredAlerts.length > 0" class="table-responsive">
            <table class="table table-hover mb-0 alerts-table">
              <thead>
                <tr>
                  <th class="col-severity"></th>
                  <th class="col-title">Title</th>
                  <th class="col-category">Category</th>
                  <th class="col-source">Source</th>
                  <th class="col-session">Session</th>
                  <th class="col-time">Time</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(alert, index) in displayedAlerts" :key="getAlertKey(alert, index)" class="alert-row">
                  <td class="severity-cell" :class="getSeverityBadgeClass(alert.severity)" :title="alert.severity"></td>
                  <td class="alert-title">
                    <div class="title-text">{{ alert.title }}</div>
                    <div class="message-text">{{ alert.message }}</div>
                  </td>
                  <td class="alert-category">
                    <span class="category-badge">{{ alert.category }}</span>
                  </td>
                  <td class="alert-source">{{ alert.source }}</td>
                  <td class="alert-session">{{ alert.sessionId }}</td>
                  <td class="alert-time">{{ formatTimeAgo(alert.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Empty State -->
          <EmptyState
            v-else
            icon="bi-bell-slash"
            title="No Alerts"
            description="No alerts match your current filters."
          />
        </div>
      </div>
    </template>
  </PageHeader>
</template>

<style scoped>
/* Severity Summary Bar */
.severity-bar {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 0.5rem;
  gap: 0.25rem;
}

.severity-card {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  transition: background 0.15s ease;
}

.severity-card:hover {
  background: rgba(255, 255, 255, 0.8);
}

.severity-card.time {
  color: #475569;
  margin-right: auto;
}

.severity-card.critical .card-count { color: #dc2626; }
.severity-card.high .card-count { color: #ea580c; }
.severity-card.medium .card-count { color: #ca8a04; }
.severity-card.low .card-count { color: #0891b2; }

.card-count {
  font-weight: 700;
  font-size: 0.95rem;
}

.card-label {
  font-size: 0.7rem;
  font-weight: 500;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.025em;
}

/* Inline Time Select */
.time-select-inline {
  border: none;
  background: transparent;
  font-size: 0.8rem;
  font-weight: 600;
  color: #334155;
  cursor: pointer;
  padding: 0;
  outline: none;
}

.time-select-inline:focus {
  outline: none;
}

/* Filters */
.filter-select {
  width: auto;
  min-width: 150px;
}

.search-input {
  min-width: 200px;
  max-width: 300px;
}

/* Alerts Table */
.alerts-table {
  font-size: 0.875rem;
}

.alerts-table th {
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
  color: #6b7280;
  background: #f9fafb;
  border-bottom: 2px solid #e5e7eb;
  padding: 0.5rem 0.5rem;
}

.alerts-table td {
  padding: 0.4rem 0.5rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.alerts-table tbody tr:hover {
  background-color: rgba(94, 100, 255, 0.04);
}

/* Column widths */
.col-severity {
  width: 6px;
  padding: 0 !important;
}

.col-title {
  min-width: 250px;
  padding-left: 0.75rem !important;
}

.col-category {
  width: 110px;
}

.col-source {
  width: 140px;
}

.col-session {
  width: 150px;
}

.col-time {
  width: 100px;
}

/* Alert cells */
.alert-title {
  padding-left: 0.75rem !important;
}

.title-text {
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 0.25rem;
}

.message-text {
  font-size: 0.8rem;
  color: #6b7280;
  line-height: 1.4;
}

.alert-category {
  vertical-align: middle;
}

.category-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  background-color: #f3f4f6;
  color: #4b5563;
  border-radius: 4px;
}

.alert-source {
  font-weight: 500;
  color: #4b5563;
  font-size: 0.8rem;
}

.alert-session {
  color: #6b7280;
  font-size: 0.8rem;
}

.alert-time {
  color: #9ca3af;
  font-size: 0.8rem;
  white-space: nowrap;
}

/* Severity Cell */
.severity-cell {
  width: 6px;
  padding: 0 !important;
  border-bottom: none !important;
}

.severity-cell.badge-critical {
  background-color: #dc3545;
}

.severity-cell.badge-high {
  background-color: #fd7e14;
}

.severity-cell.badge-medium {
  background-color: #eab308;
}

.severity-cell.badge-low {
  background-color: #0891b2;
}

/* Responsive */
@media (max-width: 1200px) {
  .col-source,
  .col-session {
    display: none;
  }
}

@media (max-width: 768px) {
  .filters-row {
    flex-direction: column;
  }

  .filter-select,
  .search-input {
    width: 100%;
    max-width: none;
  }

  .alerts-table {
    font-size: 0.8rem;
  }

  .col-category {
    display: none;
  }

  .message-text {
    display: none;
  }
}
</style>
