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

<template>
  <div>
    <TracesDisabledFeatureAlert v-if="featureDisabled" />

    <LoadingState v-else-if="loading" message="Loading traces..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="traces.length === 0"
      title="No Traces"
      message="No trace-carrying events were recorded in this profile."
      icon="bi-diagram-3"
    />

    <div v-else class="dashboard-container">
      <DataTable>
        <template #toolbar>
          <TableToolbar v-model="query" search-placeholder="Filter operations...">
            <span class="toolbar-info">Traces</span>
            <template #filters>
              <Badge key-label="Total" :value="filtered.length" variant="secondary" size="s" borderless />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>Root operation</th>
            <th>Kind</th>
            <th class="numeric">Duration</th>
            <th class="distribution-column">Relative</th>
            <th class="numeric">Spans</th>
            <th>Status</th>
            <th class="numeric">Start</th>
            <th>Trace id</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="trace in visible" :key="trace.traceId" class="clickable" @click="open(trace)">
            <td class="operation">{{ trace.rootName }}</td>
            <td><Badge :variant="kindVariant(trace.rootKind)" size="xs" :value="trace.rootKind" /></td>
            <td class="numeric">{{ FormattingService.formatDuration2Units(trace.durationNanos) }}</td>
            <td class="distribution-column">
              <!-- Relative to the slowest trace, so the spread reads without comparing digits. -->
              <div
                class="relative-bar"
                :class="{ 'has-error': trace.errorCount > 0 }"
                :style="{ width: relativeWidth(trace) }"
              ></div>
            </td>
            <td class="numeric">{{ trace.spanCount }}</td>
            <td>
              <Badge
                v-if="trace.errorCount > 0"
                variant="danger"
                size="xs"
                :value="`${trace.errorCount} error`"
              />
              <Badge v-else variant="success" size="xs" value="ok" />
            </td>
            <td class="numeric">
              {{ FormattingService.formatDuration2Units(trace.startMillisFromBeginning * 1_000_000) }}
            </td>
            <td class="trace-id">{{ trace.traceId }}</td>
          </tr>
        </tbody>
        <template #footer>
          <TableShowMore
            v-if="filtered.length > visibleCount"
            :shown="visible.length"
            :total="filtered.length"
            @show-more="visibleCount += PAGE_SIZE"
          />
        </template>
      </DataTable>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import Badge from '@shared/components/Badge.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import FormattingService from '@shared/services/FormattingService';
import TracesDisabledFeatureAlert from '@/components/alerts/TracesDisabledFeatureAlert.vue';
import ProfileTracesClient from '@/services/api/ProfileTracesClient';
import type { SpanKind, TraceRow } from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();
const router = useRouter();

const PAGE_SIZE = 50;

const traces = ref<TraceRow[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const query = ref('');
const visibleCount = ref(PAGE_SIZE);

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

const filtered = computed(() => {
  const needle = query.value.trim().toLowerCase();
  if (needle === '') {
    return traces.value;
  }
  return traces.value.filter((trace) => trace.rootName.toLowerCase().includes(needle));
});

const visible = computed(() => filtered.value.slice(0, visibleCount.value));

// The list arrives slowest-first, so the first row is the maximum.
const slowestNanos = computed(() => traces.value[0]?.durationNanos ?? 0);

function relativeWidth(trace: TraceRow): string {
  if (slowestNanos.value <= 0) {
    return '100%';
  }
  return Math.max((trace.durationNanos / slowestNanos.value) * 100, 1) + '%';
}

function kindVariant(kind: SpanKind): string {
  if (kind === 'SERVER') {
    return 'primary';
  }
  return kind === 'CLIENT' ? 'info' : 'secondary';
}

function open(trace: TraceRow): void {
  router.push({
    name: 'profile-trace-detail',
    params: { profileId: profileId.value, traceId: trace.traceId }
  });
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    traces.value = await new ProfileTracesClient(profileId.value).getTraces();
  } catch {
    error.value = 'Failed to load the traces for this profile.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (!featureDisabled.value) {
    loadData();
  } else {
    loading.value = false;
  }
});
</script>

<style scoped>
.dashboard-container {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.toolbar-info {
  font-weight: 500;
  color: var(--color-heading);
}

.clickable {
  cursor: pointer;
}

.operation {
  font-weight: 500;
  color: var(--color-heading);
}

.numeric {
  text-align: right;
  font-family: var(--font-family-monospace);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.trace-id {
  font-family: var(--font-family-monospace);
  font-size: var(--font-size-sm);
  color: var(--color-text-light);
}

.distribution-column {
  width: 9rem;
}

.relative-bar {
  height: 0.4rem;
  border-radius: var(--radius-xs);
  background: var(--flamegraph-color-blue);
  min-width: 2px;
}

.relative-bar.has-error {
  background: var(--flamegraph-color-red);
}
</style>
