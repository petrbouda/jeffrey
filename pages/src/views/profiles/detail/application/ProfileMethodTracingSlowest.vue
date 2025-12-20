<!--
  ~ Jeffrey
  ~ Copyright (C) 2025 Petr Bouda
  ~
  ~ This program is free software: you can redistribute it and/or modify
  ~ it under the terms of the GNU Affero General Public License as published by
  ~ the Free Software Foundation, either version 3 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU Affero General Public License for more details.
  ~
  ~ You should have received a copy of the GNU Affero General Public License
  ~ along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
  <div>
    <PageHeader title="Slowest Method Traces" icon="bi-hourglass-split" />

    <!-- Loading State -->
    <LoadingState v-if="loading" message="Loading slowest traces..." />

    <!-- Error State -->
    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <!-- Empty State -->
    <EmptyState
      v-else-if="!slowestData || slowestData.slowestTraces.length === 0"
      title="No Slow Traces"
      message="No slow method traces were recorded in this profile."
      icon="bi-hourglass-split"
    />

    <div v-else class="dashboard-container">
      <!-- Stats Summary -->
      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <!-- Slowest Traces Table -->
      <div class="table-card">
        <div class="table-header">
          <h4>
            <i class="bi bi-list-ol me-2"></i>
            Slowest Method Invocations
          </h4>
          <div class="search-box">
            <i class="bi bi-search search-icon"></i>
            <input
              type="text"
              class="search-input"
              placeholder="Filter by class, method or thread..."
              v-model="searchQuery"
            />
            <button v-if="searchQuery" class="clear-btn" @click="searchQuery = ''">
              <i class="bi bi-x"></i>
            </button>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead>
              <tr>
                <th class="rank-col">#</th>
                <th>Method</th>
                <th class="text-end">Duration</th>
                <th>Thread</th>
                <th class="text-end">% of Max</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(trace, index) in filteredTraces" :key="index" class="trace-row">
                <td class="rank-col">
                  <span class="rank-badge" :class="getRankClass(trace.originalIndex)">{{
                    trace.originalIndex + 1
                  }}</span>
                </td>
                <td class="method-cell">
                  <span class="method-name">{{ trace.methodName || '&lt;init&gt;' }}</span>
                  <span class="class-name">{{ trace.className }}</span>
                </td>
                <td class="text-end">
                  <span class="duration-value" :class="getDurationClass(trace.duration)">
                    {{ FormattingService.formatDuration2Units(trace.duration) }}
                  </span>
                </td>
                <td class="thread-name">{{ trace.threadName }}</td>
                <td class="text-end">
                  <div class="percent-bar-container">
                    <div
                      class="percent-bar"
                      :style="{ width: getPercentOfMax(trace.duration) + '%' }"
                    ></div>
                    <span class="percent-value"
                      >{{ getPercentOfMax(trace.duration).toFixed(0) }}%</span
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import FormattingService from '@/services/FormattingService';
import ProfileMethodTracingClient from '@/services/profile/custom/methodtracing/ProfileMethodTracingClient';
import type MethodTracingSlowestData from '@/services/profile/custom/methodtracing/MethodTracingSlowestData';

// Route and navigation
const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// State
const loading = ref(true);
const error = ref<string | null>(null);
const slowestData = ref<MethodTracingSlowestData | null>(null);
const searchQuery = ref('');

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!slowestData.value) return [];
  const header = slowestData.value.header;
  const traces = slowestData.value.slowestTraces;
  const slowest = traces[0];

  // Compute avg duration from traces
  const totalDuration = traces.reduce((sum, t) => sum + t.duration, 0);
  const avgDuration = traces.length > 0 ? totalDuration / traces.length : 0;

  return [
    {
      icon: 'hourglass-split',
      title: 'Slowest Trace',
      value: slowest ? FormattingService.formatDuration2Units(slowest.duration) : '-',
      variant: 'danger' as const,
      breakdown: slowest
        ? [
            {
              label: 'Method',
              value: getShortMethodName(slowest.className, slowest.methodName),
              color: '#EA4335'
            }
          ]
        : []
    },
    {
      icon: 'clock-fill',
      title: 'P99 Duration',
      value: FormattingService.formatDuration2Units(header.p99Duration),
      variant: 'warning' as const,
      breakdown: [
        {
          label: 'P95',
          value: FormattingService.formatDuration2Units(header.p95Duration),
          color: '#FBBC05'
        }
      ]
    },
    {
      icon: 'exclamation-triangle',
      title: 'Unique Methods',
      value: header.uniqueMethodCount,
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Shown Traces',
          value: traces.length,
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'stopwatch',
      title: 'Avg Duration',
      value: FormattingService.formatDuration2Units(avgDuration),
      variant: 'highlight' as const
    }
  ];
});

const maxDuration = computed(() => {
  if (!slowestData.value || slowestData.value.slowestTraces.length === 0) return 1;
  return slowestData.value.slowestTraces[0].duration || 1;
});

const filteredTraces = computed(() => {
  if (!slowestData.value) return [];

  const tracesWithIndex = slowestData.value.slowestTraces.map((trace, index) => ({
    ...trace,
    originalIndex: index
  }));

  if (!searchQuery.value.trim()) return tracesWithIndex;

  const query = searchQuery.value.toLowerCase();
  return tracesWithIndex.filter(trace => {
    const classMatch = trace.className.toLowerCase().includes(query);
    const methodMatch = trace.methodName?.toLowerCase().includes(query) ?? false;
    const threadMatch = trace.threadName?.toLowerCase().includes(query) ?? false;
    return classMatch || methodMatch || threadMatch;
  });
});

const getPercentOfMax = (duration: number) => (duration / maxDuration.value) * 100;

const getRankClass = (index: number) => {
  if (index === 0) return 'rank-1';
  if (index === 1) return 'rank-2';
  if (index === 2) return 'rank-3';
  return 'rank-default';
};

const getDurationClass = (duration: number) => {
  if (duration >= 100_000_000) return 'duration-critical';
  if (duration >= 50_000_000) return 'duration-warning';
  if (duration >= 10_000_000) return 'duration-slow';
  return 'duration-normal';
};

function getShortMethodName(className: string, methodName: string): string {
  return methodName || className.split('.').pop() || className;
}

// Load data
async function loadData() {
  loading.value = true;
  error.value = null;

  try {
    const client = new ProfileMethodTracingClient(workspaceId.value, projectId.value, profileId);
    slowestData.value = await client.getSlowest();
  } catch (e: unknown) {
    console.error('Failed to load slowest traces data:', e);
    error.value = 'Failed to load slowest traces data. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
}

.table-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e9ecef;
  gap: 1rem;
}

.table-header h4 {
  margin: 0;
  color: #2c3e50;
  font-size: 1rem;
  font-weight: 600;
  white-space: nowrap;
}

.search-box {
  display: flex;
  align-items: center;
  background: #f1f3f4;
  border-radius: 8px;
  padding: 0 12px;
  height: 36px;
  width: 280px;
  transition: all 0.2s ease;
}

.search-box:focus-within {
  background: #fff;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.06);
}

.search-icon {
  color: #5f6368;
  font-size: 0.9rem;
  margin-right: 8px;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 0.85rem;
  color: #202124;
  outline: none;
}

.search-input::placeholder {
  color: #5f6368;
}

.clear-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: rgba(0, 0, 0, 0.06);
  border-radius: 50%;
  color: #5f6368;
  cursor: pointer;
  transition: all 0.15s ease;
}

.clear-btn:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #202124;
}

.rank-col {
  width: 40px;
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 0.65rem;
  font-weight: 700;
}

.rank-1 {
  background: linear-gradient(135deg, #ffd700, #ffb800);
  color: #000;
}

.rank-2 {
  background: linear-gradient(135deg, #c0c0c0, #a8a8a8);
  color: #000;
}

.rank-3 {
  background: linear-gradient(135deg, #cd7f32, #b87333);
  color: #fff;
}

.rank-default {
  background: #f0f0f0;
  color: #666;
}

.method-cell {
  line-height: 1.4;
  max-width: 400px;
  padding-top: 0.4rem;
  padding-bottom: 0.4rem;
}

.method-cell .method-name {
  display: block;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.8rem;
  font-weight: 600;
  color: #212529;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 3px;
}

.method-cell .class-name {
  display: block;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.7rem;
  color: #6c757d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.duration-value {
  font-weight: 600;
  font-size: 0.8rem;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.duration-critical {
  color: #dc3545;
}

.duration-warning {
  color: #fd7e14;
}

.duration-slow {
  color: #ffc107;
}

.duration-normal {
  color: #28a745;
}

.thread-name {
  font-size: 0.85rem;
  color: #495057;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.percent-bar-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 100px;
}

.percent-bar {
  height: 8px;
  background: linear-gradient(90deg, #4285f4, #5e64ff);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.percent-value {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6c757d;
  min-width: 35px;
}

.table th {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  color: #6c757d;
  border-bottom: 2px solid #dee2e6;
  padding: 0.5rem 0.75rem;
}

.table td {
  vertical-align: middle;
  font-size: 0.8rem;
  padding: 0.5rem 0.75rem;
}
</style>
