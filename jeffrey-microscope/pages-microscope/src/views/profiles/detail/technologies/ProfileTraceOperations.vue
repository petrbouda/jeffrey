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

    <LoadingState v-else-if="loading" message="Loading operations..." />

    <ErrorState v-else-if="error" :message="error" @retry="loadData" />

    <EmptyState
      v-else-if="operations.length === 0"
      title="No Operations"
      message="No trace-carrying events were recorded in this profile."
      icon="bi-bar-chart-steps"
    />

    <div v-else class="dashboard-container">
      <DataTable>
        <template #toolbar>
          <TableToolbar v-model="query" search-placeholder="Filter operations...">
            <span class="toolbar-info">
              Operations <span class="muted">(ranked by total time)</span>
            </span>
            <template #filters>
              <Badge
                key-label="Total"
                :value="filtered.length"
                variant="secondary"
                size="s"
                borderless
              />
            </template>
          </TableToolbar>
        </template>
        <thead>
          <tr>
            <th>Operation</th>
            <th>Kind</th>
            <th class="numeric">Count</th>
            <th class="numeric">Total</th>
            <th class="spread-column">p50 · p95 · max</th>
            <th class="numeric">p50</th>
            <th class="numeric">p95</th>
            <th class="numeric">Max</th>
            <th class="numeric">Errors</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="operation in visible" :key="operation.name">
            <td class="operation">{{ operation.name }}</td>
            <td><Badge :variant="kindVariant(operation.kind)" size="xs" :value="operation.kind" /></td>
            <td class="numeric">{{ operation.count }}</td>
            <td class="numeric">{{ format(operation.totalNanos) }}</td>
            <td class="spread-column">
              <!--
                All operations share one rail scaled to the slowest single span in the profile, so a
                wide p50-to-p95 gap -- the signature of an operation that is usually fine and
                occasionally awful -- reads as a shape rather than as two numbers to compare.
              -->
              <div class="spread">
                <span class="rail"></span>
                <span
                  class="range"
                  :style="{ left: pct(operation.p50Nanos), width: width(operation) }"
                ></span>
                <span class="tick tick-p50" :style="{ left: pct(operation.p50Nanos) }"></span>
                <span class="tick tick-p95" :style="{ left: pct(operation.p95Nanos) }"></span>
                <span class="tick tick-max" :style="{ left: pct(operation.maxNanos) }"></span>
              </div>
            </td>
            <td class="numeric">{{ format(operation.p50Nanos) }}</td>
            <td class="numeric">{{ format(operation.p95Nanos) }}</td>
            <td class="numeric">{{ format(operation.maxNanos) }}</td>
            <td class="numeric">
              <Badge
                v-if="operation.errorCount > 0"
                variant="danger"
                size="xs"
                :value="operation.errorCount"
              />
              <span v-else class="muted">—</span>
            </td>
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

      <div class="legend">
        <span><i class="swatch swatch-p50"></i> p50</span>
        <span><i class="swatch swatch-p95"></i> p95</span>
        <span><i class="swatch swatch-max"></i> max</span>
        <span class="muted">A wide p50 → p95 gap means usually fine, occasionally slow</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

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
import type { SpanKind, TraceOperationRow } from '@/services/api/model/trace/TraceModels';
import FeatureType from '@/services/api/model/FeatureType';

const props = defineProps<{ disabledFeatures: FeatureType[] }>();

const route = useRoute();

const PAGE_SIZE = 50;

const operations = ref<TraceOperationRow[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);
const query = ref('');
const visibleCount = ref(PAGE_SIZE);

const profileId = computed(() => route.params.profileId as string);

const featureDisabled = computed(() => props.disabledFeatures.includes(FeatureType.TRACES));

const filtered = computed(() => {
  const needle = query.value.trim().toLowerCase();
  if (needle === '') {
    return operations.value;
  }
  return operations.value.filter((operation) => operation.name.toLowerCase().includes(needle));
});

const visible = computed(() => filtered.value.slice(0, visibleCount.value));

/** The slowest single span anywhere, which is what the shared rail is scaled to. */
const scaleNanos = computed(() =>
  operations.value.reduce((slowest, operation) => Math.max(slowest, operation.maxNanos), 0)
);

function pct(nanos: number): string {
  if (scaleNanos.value <= 0) {
    return '0%';
  }
  return Math.min((nanos / scaleNanos.value) * 100, 100) + '%';
}

/** The p50-to-p95 band, floored so an operation with no spread is still visible. */
function width(operation: TraceOperationRow): string {
  if (scaleNanos.value <= 0) {
    return '0%';
  }
  const span = ((operation.p95Nanos - operation.p50Nanos) / scaleNanos.value) * 100;
  return Math.max(span, 0.5) + '%';
}

function format(nanos: number): string {
  return FormattingService.formatDuration2Units(nanos);
}

function kindVariant(kind: SpanKind): string {
  if (kind === 'SERVER') {
    return 'primary';
  }
  return kind === 'CLIENT' ? 'info' : 'secondary';
}

async function loadData(): Promise<void> {
  loading.value = true;
  error.value = null;
  try {
    operations.value = await new ProfileTracesClient(profileId.value).getOperations();
  } catch {
    error.value = 'Failed to load the trace operations for this profile.';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  if (featureDisabled.value) {
    loading.value = false;
  } else {
    loadData();
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

.muted {
  color: var(--color-text-muted);
  font-weight: 400;
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

.spread-column {
  min-width: 11rem;
}

.spread {
  position: relative;
  height: 0.85rem;
}

.rail {
  position: absolute;
  inset: 0.35rem 0 auto 0;
  height: 0.16rem;
  background: var(--color-border);
  border-radius: var(--radius-xs);
}

.range {
  position: absolute;
  top: 0.28rem;
  height: 0.3rem;
  background: var(--flamegraph-color-blue);
  border-radius: var(--radius-xs);
}

.tick {
  position: absolute;
  top: 0.05rem;
  width: 2px;
  height: 0.75rem;
  border-radius: var(--radius-xs);
}

.tick-p50 {
  background: var(--color-primary);
}

.tick-p95 {
  background: var(--color-warning);
}

.tick-max {
  background: var(--color-danger);
}

.legend {
  display: flex;
  gap: 0.9rem;
  flex-wrap: wrap;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.swatch {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: var(--radius-xs);
  display: inline-block;
}

.swatch-p50 {
  background: var(--color-primary);
}

.swatch-p95 {
  background: var(--color-warning);
}

.swatch-max {
  background: var(--color-danger);
}
</style>
