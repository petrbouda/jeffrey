<!--
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

<template>
  <LoadingState v-if="loading" message="Loading native-library load activity..." />

  <ErrorState v-else-if="error" message="Failed to load native-library load activity" />

  <div v-else>
    <PageHeader
      title="Native Library Loads"
      description="Native dynamic-library load/unload timing and failures from jdk.NativeLibraryLoad / jdk.NativeLibraryUnload"
      icon="bi-box-arrow-in-down"
    />

    <EmptyState
      v-if="!hasData"
      icon="bi-box-arrow-in-down"
      title="No native-library load events"
      description="This recording contains no jdk.NativeLibraryLoad or jdk.NativeLibraryUnload events (requires JDK 24+)."
    />

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Operations -->
      <div v-show="activeTab === 'operations'">
        <ChartDescription
          shows="Each native-library load/unload, slowest first, with duration and success status"
          use-case="Slow loads inflate startup; a failed load (missing or incompatible native dependency) is a real bug"
        />
        <DataTable>
          <template #toolbar>
            <TableToolbar v-model="operationsView.query" search-placeholder="Filter libraries...">
              <span class="toolbar-info">Library operations</span>
              <template #filters>
                <label class="failures-toggle">
                  <input v-model="failuresOnly" type="checkbox" />
                  Failures only
                </label>
                <Badge key-label="Shown" :value="operationsView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Operation</th>
              <th>Library</th>
              <th class="text-end">Time</th>
              <th class="text-end">Load Time</th>
              <th>Status</th>
              <th>Error</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(op, i) in operationsView.visible" :key="i">
              <td>
                <Badge
                  :value="op.operation === 'LOAD' ? 'Load' : 'Unload'"
                  :variant="op.operation === 'LOAD' ? 'info' : 'secondary'"
                  size="s"
                />
              </td>
              <td :title="op.name">
                <div class="path-display">
                  <code class="path-name">{{ fileName(op.name) }}</code>
                  <span v-if="dirName(op.name)" class="path-dir">{{ dirName(op.name) }}</span>
                </div>
              </td>
              <td class="text-end">
                {{ FormattingService.formatDuration2Units(op.timeOffsetMillis * 1_000_000) }}
              </td>
              <td class="text-end">{{ FormattingService.formatDuration2Units(op.durationNanos) }}</td>
              <td>
                <Badge
                  :value="op.success ? 'OK' : 'Failed'"
                  :variant="op.success ? 'success' : 'danger'"
                  size="s"
                />
              </td>
              <td class="error-cell" :title="op.errorMessage ?? ''">{{ op.errorMessage ?? '—' }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="operationsView.visible.length"
              :match-count="operationsView.matchCount"
              :total="operationsView.total"
              :expanded="operationsView.expanded"
              :page-size="operationsView.pageSize"
              @toggle="operationsView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Timeline -->
      <div v-show="activeTab === 'timeline'">
        <ChartDescription
          shows="Library loads and unloads per second"
          use-case="Loads cluster at startup; later spikes reveal lazy/dynamic JNI loading or class-loader churn"
        />
        <TimeSeriesChart
          :primary-data="loadsSeries"
          :secondary-data="unloadsSeries"
          primary-title="Loads"
          secondary-title="Unloads"
          :primary-axis-type="AxisFormatType.NUMBER"
          :secondary-axis-type="AxisFormatType.NUMBER"
          :visible-minutes="60"
          primary-color="#34A853"
          secondary-color="#9AA0A6"
        />
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <AboutPanel>
          <AboutSection icon="bi-box-arrow-in-down" title="What Native Library Loads Tell You">
            <p>
              The JVM loads native dynamic libraries (<code>.so</code> / <code>.dll</code> /
              <code>.dylib</code>) for JNI code, the JDK's own native pieces, and agents.
              <code>jdk.NativeLibraryLoad</code> and <code>jdk.NativeLibraryUnload</code> (JDK 24+)
              record each operation with its <strong>duration</strong> and a <strong>success</strong>
              flag, unlike the static <code>jdk.NativeLibrary</code> inventory on the Native Memory page.
            </p>
            <AboutCallout variant="warning" title="Failed loads are real bugs" icon="bi-exclamation-triangle-fill">
              A failed load (a missing or ABI-incompatible native dependency) often surfaces only as a
              later <code>UnsatisfiedLinkError</code>. The error message here is the earliest signal.
            </AboutCallout>
          </AboutSection>

          <AboutSection icon="bi-graph-up" title="Reading the Views">
            <FeatureGrid>
              <FeatureCard icon="bi-list-ol" variant="primary" title="Operations">
                Every load/unload, slowest first, with duration, status and error — find slow loads and
                failures at a glance.
              </FeatureCard>
              <FeatureCard icon="bi-activity" variant="success" title="Timeline">
                Loads/unloads per second. A burst long after startup points at lazy JNI loading or
                class-loader churn.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import type { TabBarItem } from '@/components/TabBar.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import DataTable from '@/components/table/DataTable.vue';
import TableToolbar from '@/components/table/TableToolbar.vue';
import TableShowMore from '@/components/table/TableShowMore.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import Badge from '@/components/Badge.vue';
import EmptyState from '@/components/EmptyState.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import FormattingService from '@/services/FormattingService';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import ProfileNativeMemoryClient from '@/services/api/ProfileNativeMemoryClient';
import type {
  LibraryOperation,
  NativeLibraryActivityData
} from '@/services/api/model/NativeLibraryActivityModels';
import { useTableView } from '@/composables/useTableView';

const route = useRoute();

const loading = ref(true);
const error = ref(false);
const data = ref<NativeLibraryActivityData>();
const failuresOnly = ref(false);
const activeTab = ref('operations');

const allOperations = computed<LibraryOperation[]>(() => data.value?.operations ?? []);
const filteredOperations = computed<LibraryOperation[]>(() =>
  failuresOnly.value ? allOperations.value.filter((op) => !op.success) : allOperations.value
);
const operationsView = useTableView<LibraryOperation>(filteredOperations, {
  searchableText: (op) => op.name
});

// Split a library path into its file name and parent directory for a two-line cell.
const fileName = (path: string): string => {
  const trimmed = path.replace(/\/+$/, '');
  const slash = trimmed.lastIndexOf('/');
  return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
};

const dirName = (path: string): string => {
  const trimmed = path.replace(/\/+$/, '');
  const slash = trimmed.lastIndexOf('/');
  return slash > 0 ? trimmed.substring(0, slash) : '';
};

const hasData = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return false;
  }
  return h.totalLoads > 0 || h.totalUnloads > 0;
});

const loadsSeries = computed<number[][]>(() => data.value?.timeline.series?.[0]?.data ?? []);
const unloadsSeries = computed<number[][]>(() => data.value?.timeline.series?.[1]?.data ?? []);

const tabs = computed<TabBarItem[]>(() => [
  { id: 'operations', label: 'Operations', icon: 'list-ol' },
  { id: 'timeline', label: 'Timeline', icon: 'activity' },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'box-arrow-in-down',
      title: 'Library Loads',
      value: FormattingService.formatNumber(h.totalLoads),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Failed', value: FormattingService.formatNumber(h.failedLoads) }]
    },
    {
      icon: 'hourglass-split',
      title: 'Slowest Load',
      value: FormattingService.formatDuration2Units(h.slowestLoadNanos),
      variant: 'warning' as const,
      breakdown: [{ label: 'Library', value: h.slowestLibrary ?? '—' }]
    },
    {
      icon: 'stopwatch',
      title: 'Total Load Time',
      value: FormattingService.formatDuration2Units(h.totalLoadNanos),
      variant: 'info' as const
    },
    {
      icon: 'box-arrow-up',
      title: 'Unloads',
      value: FormattingService.formatNumber(h.totalUnloads),
      variant: 'success' as const
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = false;
    const client = new ProfileNativeMemoryClient(route.params.profileId as string);
    data.value = await client.getNativeLibraryActivity();
  } catch (err) {
    error.value = true;
    console.error('Error loading native-library activity:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.path-display {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.path-name {
  font-weight: 600;
}

.path-dir {
  font-size: 0.75rem;
  color: var(--color-text-muted);
}

.error-cell {
  max-width: 28rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-danger);
}

.failures-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.8rem;
  color: var(--color-text-muted);
  cursor: pointer;
}
</style>
