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
    <!-- Feature Disabled State -->
    <TracingDisabledFeatureAlert v-if="isTracingDisabled" />

    <div v-else>
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
        <MethodTracingOverviewStats v-if="overviewData" :header="overviewData.header" />

        <!-- Cumulated Data Table -->
        <ChartSection :title="tableTitle" icon="list-ol" :full-width="true">
          <template #header-actions>
            <div class="input-group search-container" style="width: 280px">
              <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
              <input
                type="text"
                class="form-control search-input"
                :placeholder="
                  mode === 'method' ? 'Filter by class or method...' : 'Filter by class...'
                "
                v-model="searchQuery"
              />
              <button
                v-if="searchQuery"
                class="btn btn-outline-secondary clear-btn"
                type="button"
                @click="searchQuery = ''"
              >
                <i class="bi bi-x-lg"></i>
              </button>
            </div>
          </template>
          <DataTable>
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
                    <Badge :value="item.percentOfTotal.toFixed(1) + '%'" variant="blue" size="xs" />
                  </td>
                </tr>
              </tbody>
          </DataTable>
        </ChartSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';

import Badge from '@/components/Badge.vue';
import MethodTracingOverviewStats from '@/components/method-tracing/MethodTracingOverviewStats.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import ChartSection from '@/components/ChartSection.vue';
import DataTable from '@/components/table/DataTable.vue';
import FormattingService from '@/services/FormattingService';
import ProfileMethodTracingClient from '@/services/api/ProfileMethodTracingClient';
import '@/styles/shared-components.css';
import type MethodTracingCumulatedData from '@/services/api/model/MethodTracingCumulatedData';
import type MethodTracingOverviewData from '@/services/api/model/MethodTracingOverviewData';
import type CumulatedStats from '@/services/api/model/CumulatedStats';
import FeatureType from '@/services/api/model/FeatureType';

// Define props
interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

// Route and navigation
const route = useRoute();

const profileId = route.params.profileId as string;

// Check if tracing dashboard is disabled
const isTracingDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.TRACING_DASHBOARD);
});

// State
const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<MethodTracingCumulatedData | null>(null);
const overviewData = ref<MethodTracingOverviewData | null>(null);
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

// Methods
function getItemKey(item: CumulatedStats): string {
  return item.methodName ? `${item.className}#${item.methodName}` : item.className;
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
    const client = new ProfileMethodTracingClient(profileId);
    const [cumulated, overview] = await Promise.all([
      client.getCumulated(mode.value),
      client.getOverview()
    ]);
    data.value = cumulated;
    overviewData.value = overview;
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
  // Only load data if the feature is not disabled
  if (!isTracingDisabled.value) {
    loadData();
  }
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
  background: var(--color-code-bg);
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
  color: var(--color-slate-text);
  cursor: pointer;
  transition: all 0.2s ease;
}

.segmented-control .segment:hover:not(.active) {
  background: rgba(0, 0, 0, 0.04);
  color: var(--color-heading-dark);
}

.segmented-control .segment.active {
  background: var(--color-white);
  color: var(--color-accent-blue);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.06);
}

.segmented-control .segment i {
  font-size: 0.9rem;
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
  color: var(--color-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 3px;
}

.method-cell .class-name {
  display: block;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 0.7rem;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  font-weight: 500;
  font-size: 0.7rem;
}
</style>
