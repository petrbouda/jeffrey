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
    <PageHeader title="Cumulated Traces" icon="bi-layers" />

    <!-- Mode Toggle -->
    <div class="controls-bar mb-4">
      <div class="segmented-control">
        <button
          type="button"
          class="segment"
          :class="{ active: mode === 'method' }"
          @click="setMode('method')"
        >
          <i class="bi bi-code-slash"></i>
          By Method
        </button>
        <button
          type="button"
          class="segment"
          :class="{ active: mode === 'class' }"
          @click="setMode('class')"
        >
          <i class="bi bi-box"></i>
          By Class
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <LoadingState v-if="loading" message="Loading cumulated traces..." />

    <!-- Error State -->
    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <!-- Empty State -->
    <EmptyState
      v-else-if="!data || data.items.length === 0"
      title="No Cumulated Data"
      message="No method tracing events were recorded in this profile."
      icon="bi-layers"
    />

    <!-- Data Content -->
    <div v-else>
      <!-- Stats Summary -->
      <div class="mb-4">
        <StatsTable :metrics="metricsData" />
      </div>

      <!-- Cumulated Data Table -->
      <div class="table-card">
        <div class="table-header">
          <h4>
            <i class="bi bi-list-ol me-2"></i>
            {{ tableTitle }}
          </h4>
          <div class="search-box">
            <i class="bi bi-search search-icon"></i>
            <input
              type="text"
              class="search-input"
              :placeholder="mode === 'method' ? 'Filter by class or method...' : 'Filter by class...'"
              v-model="searchQuery"
            />
            <button
              v-if="searchQuery"
              class="clear-btn"
              @click="searchQuery = ''"
            >
              <i class="bi bi-x"></i>
            </button>
          </div>
        </div>
        <div class="table-responsive">
          <table class="table table-hover mb-0">
            <thead>
              <tr>
                <th>{{ mode === 'method' ? 'Method' : 'Class' }}</th>
                <th class="text-end">Invocations</th>
                <th class="text-end">Total Time</th>
                <th class="text-end">Avg Time</th>
                <th class="text-end">Max Time</th>
                <th class="text-end">% of Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredItems" :key="getItemKey(item)">
                <td class="method-cell">
                  <template v-if="mode === 'method'">
                    <span class="method-name">{{ item.methodName || '&lt;init&gt;' }}</span>
                    <span class="class-name">{{ item.className }}</span>
                  </template>
                  <template v-else>
                    <span class="method-name">{{ getSimpleClassName(item.className) }}</span>
                    <span class="class-name">{{ getPackageName(item.className) }}</span>
                  </template>
                </td>
                <td class="text-end">
                  {{ FormattingService.formatNumber(item.invocationCount) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(item.totalDuration) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(item.avgDuration) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(item.maxDuration) }}
                </td>
                <td class="text-end">
                  <span class="badge bg-primary">{{ item.percentOfTotal.toFixed(1) }}%</span>
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
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useNavigation } from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import FormattingService from '@/services/FormattingService';
import ProfileMethodTracingClient from '@/services/profile/custom/methodtracing/ProfileMethodTracingClient';
import type MethodTracingCumulatedData from '@/services/profile/custom/methodtracing/MethodTracingCumulatedData';
import type CumulatedStats from '@/services/profile/custom/methodtracing/CumulatedStats';

// Route and navigation
const route = useRoute();
const { workspaceId, projectId } = useNavigation();
const profileId = route.params.profileId as string;

// State
const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<MethodTracingCumulatedData | null>(null);
const mode = ref<'method' | 'class'>('method');
const searchQuery = ref('');

// Computed
const tableTitle = computed(() =>
  mode.value === 'method' ? 'Cumulated by Method' : 'Cumulated by Class'
);

const filteredItems = computed(() => {
  if (!data.value) return [];
  if (!searchQuery.value.trim()) return data.value.items;

  const query = searchQuery.value.toLowerCase();
  return data.value.items.filter(item => {
    const classMatch = item.className.toLowerCase().includes(query);
    const methodMatch = item.methodName?.toLowerCase().includes(query) ?? false;
    return classMatch || methodMatch;
  });
});

const metricsData = computed(() => {
  if (!data.value) return [];

  // Find max values from items
  const maxSingleDuration = data.value.items.reduce((max, item) => Math.max(max, item.maxDuration), 0);
  const maxTotalDuration = data.value.items.reduce((max, item) => Math.max(max, item.totalDuration), 0);

  return [
    {
      icon: 'play-circle',
      title: 'Total Invocations',
      value: FormattingService.formatNumber(data.value.totalInvocations),
      variant: 'info' as const,
      breakdown: []
    },
    {
      icon: 'stopwatch',
      title: 'Total Duration',
      value: FormattingService.formatDuration2Units(data.value.totalDuration),
      variant: 'highlight' as const,
      breakdown: []
    },
    {
      icon: 'hourglass-split',
      title: 'Max Duration',
      value: FormattingService.formatDuration2Units(maxSingleDuration),
      variant: 'warning' as const,
      breakdown: [
        {
          label: 'Max Total',
          value: FormattingService.formatDuration2Units(maxTotalDuration),
          color: '#EA4335'
        }
      ]
    },
    {
      icon: 'collection',
      title: mode.value === 'method' ? 'Unique Methods' : 'Unique Classes',
      value: data.value.uniqueCount,
      variant: 'success' as const,
      breakdown: []
    }
  ];
});

// Methods
function getItemKey(item: CumulatedStats): string {
  return item.methodName
    ? `${item.className}#${item.methodName}`
    : item.className;
}

function getSimpleClassName(fullClassName: string): string {
  const lastDot = fullClassName.lastIndexOf('.');
  return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
}

function getPackageName(fullClassName: string): string {
  const lastDot = fullClassName.lastIndexOf('.');
  return lastDot >= 0 ? fullClassName.substring(0, lastDot) : '';
}

function setMode(newMode: 'method' | 'class') {
  if (mode.value !== newMode) {
    mode.value = newMode;
    searchQuery.value = '';
  }
}

async function loadData() {
  loading.value = true;
  error.value = null;

  try {
    const client = new ProfileMethodTracingClient(workspaceId.value, projectId.value, profileId);
    data.value = await client.getCumulated(mode.value);
  } catch (e: unknown) {
    console.error('Failed to load cumulated traces:', e);
    error.value = 'Failed to load cumulated traces. Please try again.';
  } finally {
    loading.value = false;
  }
}

// Watch mode changes
watch(mode, () => {
  loadData();
});

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.controls-bar {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.segmented-control {
  display: inline-flex;
  background: #f1f3f4;
  border-radius: 8px;
  padding: 4px;
  gap: 4px;
}

.segmented-control .segment {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 0.85rem;
  font-weight: 500;
  color: #5f6368;
  cursor: pointer;
  transition: all 0.2s ease;
}

.segmented-control .segment:hover:not(.active) {
  background: rgba(0, 0, 0, 0.04);
  color: #202124;
}

.segmented-control .segment.active {
  background: #fff;
  color: #1a73e8;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
}

.segmented-control .segment i {
  font-size: 0.9rem;
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
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1), 0 1px 2px rgba(0, 0, 0, 0.06);
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

.method-cell {
  line-height: 1.4;
  max-width: 450px;
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

.badge {
  font-weight: 500;
  font-size: 0.7rem;
}
</style>
