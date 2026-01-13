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
const messages = ref<ImportantMessage[]>([]);

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
const selectedCategory = ref('');
const selectedType = ref('');
const selectedSession = ref('');
const alertsOnly = ref(false);
const searchQuery = ref('');

// Display limit
const displayLimit = 50;

// Get unique sessions from messages
const sessions = computed(() => {
  const sessionSet = new Map<string, string>();
  messages.value.forEach(m => sessionSet.set(m.sessionId, m.sessionId));
  return Array.from(sessionSet.entries()).map(([id]) => ({ id, name: id }));
});

async function loadMessages() {
  if (!workspaceId.value || !projectId.value) return;

  isLoading.value = true;
  try {
    const client = new ProjectMessagesClient(workspaceId.value, projectId.value);
    // For "All time", don't pass time parameters
    if (selectedTimeRange.value === 0) {
      messages.value = await client.getMessages();
    } else {
      const now = Date.now();
      const startMillis = now - selectedTimeRange.value * 60 * 1000;
      messages.value = await client.getMessages(startMillis, now);
    }
  } catch (error: any) {
    console.error('Failed to load messages:', error);
    const message = error.response?.data?.message || 'Failed to load messages';
    ToastService.error('Messages', message);
    messages.value = [];
  } finally {
    isLoading.value = false;
  }
}

// Reload when time range changes
watch(selectedTimeRange, () => {
  loadMessages();
});

onMounted(() => {
  loadMessages();
});

// Get unique categories from messages
const categories = computed(() => {
  const cats = new Set(messages.value.map(m => m.category));
  return Array.from(cats).sort();
});

// Get unique types from messages
const messageTypes = computed(() => {
  const types = new Set(messages.value.map(m => m.type));
  return Array.from(types).sort();
});

// Computed: messages filtered by time range (already filtered by API)
const timeFilteredMessages = computed(() => {
  return messages.value;
});

// Computed: timeseries data for chart (grouped by time)
const messagesTimeseriesData = computed(() => {
  const filteredMessages = timeFilteredMessages.value;
  if (filteredMessages.length === 0) return [];

  // Create time buckets (20 buckets across the time range)
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

  // Count messages per bucket
  filteredMessages.forEach(msg => {
    const msgTime = new Date(msg.createdAt).getTime();
    // Find the bucket this message belongs to
    const bucketIndex = Math.floor((msgTime - startTime) / bucketDurationMs);
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

// Computed: filtered messages (applies all filters on top of time filter)
const filteredMessages = computed(() => {
  return timeFilteredMessages.value.filter(msg => {
    // Filter by alerts only
    if (alertsOnly.value && !msg.isAlert) {
      return false;
    }

    // Filter by severity
    if (selectedSeverity.value && msg.severity !== selectedSeverity.value) {
      return false;
    }

    // Filter by category
    if (selectedCategory.value && msg.category !== selectedCategory.value) {
      return false;
    }

    // Filter by type
    if (selectedType.value && msg.type !== selectedType.value) {
      return false;
    }

    // Filter by session
    if (selectedSession.value && msg.sessionId !== selectedSession.value) {
      return false;
    }

    // Filter by search query
    if (searchQuery.value) {
      const query = searchQuery.value.toLowerCase();
      return (
        msg.message.toLowerCase().includes(query) ||
        msg.title.toLowerCase().includes(query) ||
        msg.type.toLowerCase().includes(query)
      );
    }

    return true;
  });
});

// Computed: limited messages for display
const displayedMessages = computed(() => {
  return filteredMessages.value.slice(0, displayLimit);
});

// Computed: TOTAL message counts (for StatsTable)
const totalMessagesCount = computed(() => messages.value.length);

// Computed: category counts
const categoryCounts = computed(() => {
  const counts: Record<string, number> = {};
  messages.value.forEach(m => {
    counts[m.category] = (counts[m.category] || 0) + 1;
  });
  return counts;
});

// Computed: time-filtered message counts (for filter bar)
const alertCount = computed(() => timeFilteredMessages.value.filter(m => m.isAlert).length);
const nonAlertCount = computed(() => timeFilteredMessages.value.filter(m => !m.isAlert).length);

// Computed: severity counts
const severityCounts = computed(() => ({
  critical: messages.value.filter(m => m.severity === 'CRITICAL').length,
  high: messages.value.filter(m => m.severity === 'HIGH').length,
  medium: messages.value.filter(m => m.severity === 'MEDIUM').length,
  low: messages.value.filter(m => m.severity === 'LOW').length
}));

// Computed: StatsTable metrics (showing TOTAL counts)
const messageMetrics = computed(() => [
  {
    icon: 'chat-square-text',
    title: 'Total Messages',
    value: totalMessagesCount.value.toString(),
    variant: 'highlight' as const,
    breakdown: [
      { label: 'Critical', value: severityCounts.value.critical.toString() },
      { label: 'High', value: severityCounts.value.high.toString() },
      { label: 'Medium', value: severityCounts.value.medium.toString() },
      { label: 'Low', value: severityCounts.value.low.toString() }
    ]
  },
  {
    icon: 'tag',
    title: 'Categories',
    value: Object.keys(categoryCounts.value).length.toString(),
    variant: 'info' as const,
    breakdown: (() => {
      const sorted = Object.entries(categoryCounts.value).sort((a, b) => b[1] - a[1]);
      return sorted.length > 0 ? [{ label: 'Top', value: sorted[0][0] }] : [];
    })()
  }
]);

// Get badge class for severity
const getSeverityClass = (severity: Severity): string => {
  switch (severity) {
    case 'CRITICAL':
      return 'severity-critical';
    case 'HIGH':
      return 'severity-high';
    case 'MEDIUM':
      return 'severity-medium';
    case 'LOW':
      return 'severity-low';
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
const getMessageKey = (msg: ImportantMessage, index: number): string => {
  return `${msg.sessionId}-${msg.type}-${msg.createdAt}-${index}`;
};
</script>

<template>
  <PageHeader
    title="Important Messages"
    description="All system messages and notifications from active sessions."
    icon="bi-chat-square-text"
  >
    <!-- Loading State -->
    <LoadingState v-if="isLoading" message="Loading messages..." />

    <template v-else>
      <!-- Summary Statistics -->
      <div class="mb-4">
        <StatsTable :metrics="messageMetrics" />
      </div>

      <!-- Filter Bar -->
      <div class="filter-bar mb-3">
        <div class="filter-card time">
          <i class="bi bi-clock"></i>
          <select v-model="selectedTimeRange" class="filter-select-inline">
            <option v-for="option in timeRangeOptions" :key="option.minutes" :value="option.minutes">{{ option.label }}</option>
          </select>
        </div>
        <div class="filter-card alerts" :class="{ active: alertsOnly }" @click="alertsOnly = !alertsOnly">
          <i class="bi bi-bell-fill"></i>
          <span class="card-count">{{ alertCount }}</span>
          <span class="card-label">Alerts</span>
        </div>
        <div class="filter-card info">
          <i class="bi bi-info-circle"></i>
          <span class="card-count">{{ nonAlertCount }}</span>
          <span class="card-label">Info</span>
        </div>
      </div>

      <!-- Messages Over Time Chart -->
      <div class="mb-3">
        <TimeSeriesChart
          v-if="messagesTimeseriesData.length > 0"
          :primary-data="messagesTimeseriesData"
          :visible-minutes="999999"
          :primary-axis-type="AxisFormatType.COUNT"
          primary-title="Message Count"
          time-unit="milliseconds"
        />
        <EmptyState
          v-else
          icon="bi-graph-up"
          title="No Message Data"
          description="No messages in the selected time range."
        />
      </div>

      <!-- Messages List Card -->
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
              v-model="selectedType"
              class="form-select form-select-sm filter-select"
            >
              <option value="">All Types</option>
              <option v-for="type in messageTypes" :key="type" :value="type">
                {{ type.replace(/_/g, ' ') }}
              </option>
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
              placeholder="Search messages..."
            />
          </div>
          <div class="d-flex align-items-center">
            <span class="badge bg-info">
              <i class="bi bi-info-circle me-1"></i>Showing {{ Math.min(displayLimit, filteredMessages.length) }} of {{ filteredMessages.length }} messages
            </span>
          </div>
        </div>
        <div class="card-body p-0">

          <!-- Messages Table -->
          <div v-if="filteredMessages.length > 0" class="table-responsive">
            <table class="table table-hover mb-0 messages-table">
              <thead>
                <tr>
                  <th class="col-severity"></th>
                  <th class="col-alert"></th>
                  <th class="col-title">Title</th>
                  <th class="col-category">Category</th>
                  <th class="col-source">Source</th>
                  <th class="col-session">Session</th>
                  <th class="col-time">Time</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(msg, index) in displayedMessages" :key="getMessageKey(msg, index)" class="message-row">
                  <td class="severity-cell" :class="getSeverityClass(msg.severity)" :title="msg.severity"></td>
                  <td class="alert-indicator">
                    <i v-if="msg.isAlert" class="bi bi-bell-fill alert-icon" title="Alert"></i>
                  </td>
                  <td class="message-title">
                    <div class="title-text">{{ msg.title }}</div>
                    <div class="message-text">{{ msg.message }}</div>
                  </td>
                  <td class="message-category">
                    <span class="category-badge">{{ msg.category }}</span>
                  </td>
                  <td class="message-source">{{ msg.source }}</td>
                  <td class="message-session">{{ msg.sessionId }}</td>
                  <td class="message-time">{{ formatTimeAgo(msg.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Empty State -->
          <EmptyState
            v-else
            icon="bi-chat-square-text"
            title="No Messages"
            description="No messages match your current filters."
          />
        </div>
      </div>
    </template>
  </PageHeader>
</template>

<style scoped>
/* Filter Bar */
.filter-bar {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 0.5rem;
  gap: 0.25rem;
}

.filter-card {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  border-radius: 6px;
  transition: background 0.15s ease;
  cursor: pointer;
}

.filter-card:hover {
  background: rgba(255, 255, 255, 0.8);
}

.filter-card.time {
  color: #475569;
  margin-right: auto;
  cursor: default;
}

.filter-card.alerts .card-count { color: #7c3aed; }
.filter-card.info .card-count { color: #0891b2; }

.filter-card.alerts {
  border: 1px solid transparent;
  cursor: pointer;
}

.filter-card.alerts.active {
  background: rgba(124, 58, 237, 0.1);
  border-color: #7c3aed;
}

.filter-card.alerts .bi-bell-fill {
  color: #7c3aed;
}

.filter-card.info {
  cursor: default;
}

.filter-card.info .bi-info-circle {
  color: #0891b2;
}

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

/* Inline Filter Select */
.filter-select-inline {
  border: none;
  background: transparent;
  font-size: 0.8rem;
  font-weight: 600;
  color: #334155;
  cursor: pointer;
  padding: 0;
  outline: none;
}

.filter-select-inline:focus {
  outline: none;
}

/* Filters */
.filter-select {
  width: auto;
  min-width: 140px;
}

.search-input {
  min-width: 200px;
  max-width: 300px;
}

/* Messages Table */
.messages-table {
  font-size: 0.875rem;
}

.messages-table th {
  font-weight: 600;
  font-size: 0.75rem;
  text-transform: uppercase;
  color: #6b7280;
  background: #f9fafb;
  border-bottom: 2px solid #e5e7eb;
  padding: 0.5rem 0.5rem;
}

.messages-table td {
  padding: 0.4rem 0.5rem;
  vertical-align: middle;
  border-bottom: 1px solid #f0f0f0;
}

.messages-table tbody tr:hover {
  background-color: rgba(94, 100, 255, 0.04);
}

/* Column widths */
.col-severity {
  width: 6px;
  padding: 0 !important;
}

.col-alert {
  width: 32px;
  text-align: center;
  padding: 0.5rem 0.25rem !important;
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

/* Severity Cell */
.severity-cell {
  width: 6px;
  padding: 0 !important;
  border-bottom: none !important;
}

.severity-cell.severity-critical {
  background-color: #dc3545;
}

.severity-cell.severity-high {
  background-color: #fd7e14;
}

.severity-cell.severity-medium {
  background-color: #eab308;
}

.severity-cell.severity-low {
  background-color: #0891b2;
}

/* Alert Indicator */
.alert-indicator {
  text-align: center;
}

.alert-icon {
  color: #dc3545;
  font-size: 0.9rem;
}

/* Message cells */
.message-title {
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

.message-category {
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

.message-source {
  font-weight: 500;
  color: #4b5563;
  font-size: 0.8rem;
}

.message-session {
  color: #6b7280;
  font-size: 0.8rem;
}

.message-time {
  color: #9ca3af;
  font-size: 0.8rem;
  white-space: nowrap;
}

/* Responsive */
@media (max-width: 1200px) {
  .col-source,
  .col-session {
    display: none;
  }
}

@media (max-width: 768px) {
  .filter-bar {
    flex-wrap: wrap;
  }

  .filter-select,
  .search-input {
    width: 100%;
    max-width: none;
  }

  .messages-table {
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
