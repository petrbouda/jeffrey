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
        <MethodTracingOverviewStats v-if="overviewData" :header="overviewData.header" />

        <!-- Slowest Traces Table -->
        <ChartSection title="Slowest Method Invocations" icon="list-ol" :full-width="true">
          <template #header-actions>
            <div class="input-group search-container" style="width: 280px">
              <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
              <input
                type="text"
                class="form-control search-input"
                placeholder="Filter by class, method or thread..."
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
          <div class="table-responsive">
            <table class="table table-sm table-hover mb-0">
              <thead>
                <tr>
                  <th>Method</th>
                  <th class="text-end">Duration</th>
                  <th>Thread</th>
                  <th class="text-end">% of Max</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(trace, index) in filteredTraces" :key="index" class="trace-row">
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
                    <Badge
                      :value="getPercentOfMax(trace.duration).toFixed(1) + '%'"
                      variant="blue"
                      size="xs"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </ChartSection>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';

import Badge from '@/components/Badge.vue';
import MethodTracingOverviewStats from '@/components/method-tracing/MethodTracingOverviewStats.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import ChartSection from '@/components/ChartSection.vue';
import FormattingService from '@/services/FormattingService';
import ProfileMethodTracingClient from '@/services/api/ProfileMethodTracingClient';
import '@/styles/shared-components.css';
import type MethodTracingSlowestData from '@/services/api/model/MethodTracingSlowestData';
import type MethodTracingOverviewData from '@/services/api/model/MethodTracingOverviewData';
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
const slowestData = ref<MethodTracingSlowestData | null>(null);
const overviewData = ref<MethodTracingOverviewData | null>(null);
const searchQuery = ref('');

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

const getDurationClass = (duration: number) => {
  if (duration >= 100_000_000) return 'duration-critical';
  if (duration >= 50_000_000) return 'duration-warning';
  if (duration >= 10_000_000) return 'duration-slow';
  return 'duration-normal';
};

// Load data
async function loadData() {
  loading.value = true;
  error.value = null;

  try {
    const client = new ProfileMethodTracingClient(profileId);
    const [slowest, overview] = await Promise.all([client.getSlowest(), client.getOverview()]);
    slowestData.value = slowest;
    overviewData.value = overview;
  } catch (e: unknown) {
    console.error('Failed to load slowest traces data:', e);
    error.value = 'Failed to load slowest traces data. Please try again.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  // Only load data if the feature is not disabled
  if (!isTracingDisabled.value) {
    loadData();
  }
});
</script>

<style scoped>
.dashboard-container {
  padding: 0;
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

.duration-value {
  font-weight: 600;
  font-size: 0.8rem;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.duration-critical {
  color: var(--color-danger);
}

.duration-warning {
  color: var(--bs-orange);
}

.duration-slow {
  color: var(--color-alert-warning-accent);
}

.duration-normal {
  color: var(--color-success);
}

.thread-name {
  font-size: 0.8rem;
  color: var(--color-text);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  font-weight: 500;
  font-size: 0.7rem;
}

.table th {
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-text-muted);
  border-bottom: 2px solid var(--color-border);
  padding: 0.5rem 0.75rem;
}

.table td {
  vertical-align: middle;
  font-size: 0.8rem;
  padding: 0.5rem 0.75rem;
}
</style>
