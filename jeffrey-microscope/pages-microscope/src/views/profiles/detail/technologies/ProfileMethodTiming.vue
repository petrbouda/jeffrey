<!--
  ~ Jeffrey
  ~ Copyright (C) 2026 Petr Bouda
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

<!--
  What jdk.MethodTiming counted, as opposed to what jdk.MethodTrace sampled.

  The distinction is the whole reason this view exists beside the others. Method *tracing* writes an
  event per invocation, so it costs in proportion to how often a method is called and is normally
  pointed at a handful of them. Method *timing* instruments the method to keep running counters and
  reports them periodically, so it can watch a method called millions of times for a fixed price --
  and in exchange keeps no stack, no thread and no individual call.
-->
<template>
  <div>
    <TracingDisabledFeatureAlert v-if="isTracingDisabled" />

    <div v-else>
      <LoadingState v-if="loading" message="Loading method timing..." />

      <ErrorState v-else-if="error" :message="error" @retry="loadData" />

      <EmptyState
        v-else-if="!data || data.methods.length === 0"
        title="No Method Timing Data"
        description="No jdk.MethodTiming events were recorded. The event is enabled in both JFR configurations but reports nothing until it is given a filter, e.g. jdk.MethodTiming#filter=com.example.Service::handle"
        icon="bi-stopwatch"
      />

      <div v-else>
        <ChartSection title="Timed Methods" icon="stopwatch" :full-width="true">
          <template #header-actions>
            <div class="input-group search-container" style="width: 280px">
              <span class="input-group-text"><i class="bi bi-search search-icon"></i></span>
              <input
                v-model="searchQuery"
                type="text"
                class="form-control search-input"
                placeholder="Filter by class or method..."
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
                <th>Method</th>
                <th class="text-end">Invocations</th>
                <th class="text-end">Min</th>
                <th class="text-end">Avg</th>
                <th class="text-end">Max</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="method in filteredMethods" :key="keyOf(method)">
                <td class="method-cell">
                  <span class="method-name">{{ method.methodName }}</span>
                  <span class="class-name">{{ method.className }}</span>
                </td>
                <td class="text-end">
                  {{ FormattingService.formatNumber(method.invocations) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(method.minNanos) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(method.avgNanos) }}
                </td>
                <td class="text-end">
                  {{ FormattingService.formatDuration2Units(method.maxNanos) }}
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
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import TracingDisabledFeatureAlert from '@/components/alerts/TracingDisabledFeatureAlert.vue';
import ChartSection from '@/components/ChartSection.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfileMethodTracingClient from '@/services/api/ProfileMethodTracingClient';
import '@shared/styles/shared-components.css';
import type MethodTimingData from '@/services/api/model/MethodTimingData';
import type { MethodTimingStat } from '@/services/api/model/MethodTimingData';
import FeatureType from '@/services/api/model/FeatureType';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const profileId = route.params.profileId as string;

const isTracingDisabled = computed(() =>
  props.disabledFeatures.includes(FeatureType.METHOD_TRACING_DASHBOARD)
);

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<MethodTimingData | null>(null);
const searchQuery = ref('');

const filteredMethods = computed(() => {
  if (!data.value) {
    return [];
  }
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) {
    return data.value.methods;
  }
  return data.value.methods.filter(
    method =>
      method.className.toLowerCase().includes(query) ||
      method.methodName.toLowerCase().includes(query)
  );
});

function keyOf(method: MethodTimingStat): string {
  return `${method.className}#${method.methodName}`;
}

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    data.value = await new ProfileMethodTracingClient(profileId).getTiming();
  } catch (e) {
    console.error('Failed to load method timing data:', e);
    error.value = 'Failed to load method timing data';
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.method-cell {
  display: flex;
  flex-direction: column;
}

.method-name {
  font-weight: 600;
  color: var(--color-text);
}

.class-name {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
</style>
