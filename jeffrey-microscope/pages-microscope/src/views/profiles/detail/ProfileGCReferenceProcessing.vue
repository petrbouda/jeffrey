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
  <LoadingState v-if="loading" message="Loading reference-processing analysis..." />

  <ErrorState v-else-if="error" message="Failed to load reference-processing analysis" />

  <div v-else>
    <PageHeader
      title="Reference Processing"
      description="Soft / Weak / Final / Phantom reference processing per GC from jdk.GCReferenceStatistics"
      icon="bi-link-45deg"
    />

    <DisabledEventsNotice
      v-if="!hasData"
      title="No reference-processing events in this recording"
      icon="bi-link-45deg"
    >
      <p>
        These views are built from <code>jdk.GCReferenceStatistics</code>, which a garbage collector
        emits each time it processes the JDK's reference objects (Soft, Weak, Final, Phantom). This
        event is <strong>enabled by default</strong> — in both the bundled <code>default</code> and
        <code>profile</code> configs — because it is tied to GC being recorded at the normal level.
      </p>
      <p>
        So an empty page here is <strong>not a configuration gap</strong>. It means no qualifying GC
        reference-processing happened during the recording: the run was very short, no GC cycles
        occurred, or the collector/phase that ran did not report reference statistics. Capture a
        longer run, or one with real GC activity, and these views will populate on their own.
        Recording with <code>settings=profile</code> ensures the full GC detail is present.
      </p>
    </DisabledEventsNotice>

    <div v-else>
      <div class="mb-4">
        <StatsTable :metrics="metrics" />
      </div>

      <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

      <!-- Timeline -->
      <div v-show="activeTab === 'timeline'">
        <ChartDescription
          shows="References processed per second, stacked by reference type"
          use-case="A Soft-reference spike signals heap pressure clearing caches; sustained Final/Phantom volume signals finalizer/cleaner backlog that lengthens reference-processing phases"
        />
        <div class="chart-container">
          <TimeSeriesChart
            :seriesData="timelineSeries"
            :stacked="true"
            :primaryAxisType="AxisFormatType.NUMBER"
            :visibleMinutes="60"
          />
        </div>
      </div>

      <!-- By Type -->
      <div v-show="activeTab === 'by-type'">
        <ChartDescription
          shows="Total and average-per-GC references processed, by reference type"
          use-case="Compare which reference kinds dominate processing work across the whole recording"
        />
        <DataTable>
          <template #toolbar>
            <TableToolbar v-model="byTypeView.query" search-placeholder="Filter reference types...">
              <span class="toolbar-info">By Type</span>
              <template #filters>
                <Badge key-label="Total" :value="byTypeView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>Reference Type</th>
              <th class="text-end">Total Processed</th>
              <th class="text-end">Avg / GC</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="stat in byTypeView.visible" :key="stat.type">
              <td>
                <span class="type-swatch" :style="{ background: colorForType(stat.type) }"></span>
                {{ stat.type }}
              </td>
              <td class="text-end">{{ FormattingService.formatNumber(stat.total) }}</td>
              <td class="text-end">{{ FormattingService.formatNumber(stat.avgPerGc) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="byTypeView.visible.length"
              :match-count="byTypeView.matchCount"
              :total="byTypeView.total"
              :expanded="byTypeView.expanded"
              :page-size="byTypeView.pageSize"
              @toggle="byTypeView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- Per-GC -->
      <div v-show="activeTab === 'per-gc'">
        <ChartDescription
          shows="Per-collection reference totals, ranked by total references processed"
          use-case="Pinpoint the individual GC cycles that did the heaviest reference processing"
        />
        <DataTable>
          <template #toolbar>
            <TableToolbar :show-search="false">
              <span class="toolbar-info">Per-GC</span>
              <template #filters>
                <Badge key-label="Total" :value="perGcView.matchCount" variant="secondary" size="s" borderless />
              </template>
            </TableToolbar>
          </template>
          <thead>
            <tr>
              <th>GC ID</th>
              <th v-for="type in typeColumns" :key="type" class="text-end">{{ type }}</th>
              <th class="text-end">Total</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="gc in perGcView.visible" :key="gc.gcId">
              <td>{{ gc.gcId }}</td>
              <td v-for="type in typeColumns" :key="type" class="text-end">
                {{ FormattingService.formatNumber(gc.countsByType[type] ?? 0) }}
              </td>
              <td class="text-end fw-semibold">{{ FormattingService.formatNumber(gc.total) }}</td>
            </tr>
          </tbody>
          <template #footer>
            <TableShowMore
              :shown="perGcView.visible.length"
              :match-count="perGcView.matchCount"
              :total="perGcView.total"
              :expanded="perGcView.expanded"
              :page-size="perGcView.pageSize"
              @toggle="perGcView.toggle"
            />
          </template>
        </DataTable>
      </div>

      <!-- How It Works -->
      <div v-show="activeTab === 'about'">
        <AboutPanel
          icon="bi-question-circle"
          title="Understanding Reference Processing"
          subtitle="How each GC handles Soft / Weak / Final / Phantom references"
        >
          <AboutCallout variant="intro">
            <p>
              Each GC cycle processes the JDK's reference objects:
              <strong>Soft</strong> (memory-sensitive caches), <strong>Weak</strong> (canonicalizing
              maps), <strong>Final</strong> (objects with a <code>finalize()</code> method), and
              <strong>Phantom</strong> (post-mortem cleanup, including <code>Cleaner</code>).
              <code>jdk.GCReferenceStatistics</code> reports, per collection, how many references of
              each type were processed.
            </p>
          </AboutCallout>

          <AboutSection icon="bi-graph-up" title="What the Views Show">
            <FeatureGrid>
              <FeatureCard icon="bi-activity" variant="danger" title="Timeline">
                References processed per second, stacked by type. Soft-reference bursts line up with
                heap-pressure episodes; rising Final/Phantom volume points at finalizer/cleaner load.
              </FeatureCard>
              <FeatureCard icon="bi-list-ol" variant="primary" title="By Type">
                Totals and per-GC averages — which reference kinds dominate the processing work.
              </FeatureCard>
              <FeatureCard icon="bi-bar-chart-steps" variant="warning" title="Per-GC">
                The individual collections that processed the most references, with a per-type
                breakdown.
              </FeatureCard>
            </FeatureGrid>
          </AboutSection>

          <AboutCallout variant="note" title="Counts only on JDK 26" icon="bi-info-circle-fill">
            The JDK 26 event carries only the processed <em>count</em> per type and the GC id — there
            is no per-phase processing time — so every view here is count-based.
          </AboutCallout>

          <AboutSection icon="bi-broadcast" title="How JFR Emits This">
            <ul>
              <li>
                <code>jdk.GCReferenceStatistics</code> — emitted per GC cycle: the count of
                Soft/Weak/Final/Phantom references processed, along with the GC id. On JDK 26 it
                carries counts only (no per-phase time).
              </li>
            </ul>
            <p>
              It is <strong>enabled</strong> in both the bundled <code>default</code> and
              <code>profile</code> configs — tied to GC being recorded at the normal level — so an
              empty page means no qualifying GC ran, not a missing setting.
            </p>
          </AboutSection>
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import TabBar from '@shared/components/TabBar.vue';
import ChartDescription from '@shared/components/ChartDescription.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import TableToolbar from '@shared/components/table/TableToolbar.vue';
import TableShowMore from '@shared/components/table/TableShowMore.vue';
import Badge from '@shared/components/Badge.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import DisabledEventsNotice from '@/components/alerts/DisabledEventsNotice.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import TimeSeriesChart from '@/components/TimeSeriesChart.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@shared/services/FormattingService';
import ChartColors from '@shared/services/ChartColors';
import AxisFormatType from '@/services/timeseries/AxisFormatType';
import { useTableView } from '@/composables/useTableView';
import type { ReferenceProcessingData } from '@/services/api/model/GCReferenceModels';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<ReferenceProcessingData>();

const tabs = [
  { id: 'timeline', label: 'Timeline', icon: 'activity' },
  { id: 'by-type', label: 'By Type', icon: 'list-ol' },
  { id: 'per-gc', label: 'Per-GC', icon: 'bar-chart-steps' },
  { id: 'about', label: 'How It Works', icon: 'book' }
];
const activeTab = ref(tabs[0].id);

// Stable design-token color per reference type (memory-pressure red for Soft, etc.); types the
// JDK adds later fall back to the categorical series palette by position.
const TYPE_COLOR_TOKENS: Record<string, string> = {
  'Soft reference': 'color-danger',
  'Weak reference': 'color-warning',
  'Final reference': 'color-violet',
  'Phantom reference': 'color-accent-blue',
  'Cleaner reference': 'color-success',
  'Other reference': 'color-text-muted'
};

// Resolved once per payload so the timeline series and the By Type swatches always agree.
const colorByType = computed<Record<string, string>>(() => {
  const colors: Record<string, string> = {};
  (data.value?.byType ?? []).forEach((stat, index) => {
    const token = TYPE_COLOR_TOKENS[stat.type];
    colors[stat.type] = token ? ChartColors.chartColor(token) : ChartColors.seriesColor(index);
  });
  return colors;
});

const colorForType = (type: string): string =>
  colorByType.value[type] ?? ChartColors.chartColor('color-text-muted');

const hasData = computed(() => (data.value?.header.totalReferences ?? 0) > 0);
const typeColumns = computed(() => (data.value?.byType ?? []).map(stat => stat.type));

// The backend emits one series per reference type, ordered exactly like `byType`.
const timelineSeries = computed(() =>
  (data.value?.timeline.series ?? []).map(serie => ({
    name: serie.name,
    data: serie.data,
    color: colorForType(serie.name)
  }))
);

const byTypeView = useTableView(() => data.value?.byType ?? [], {
  searchableText: stat => stat.type
});
const perGcView = useTableView(() => data.value?.perGc ?? []);

const metrics = computed(() => {
  const h = data.value?.header;
  if (!h) {
    return [];
  }
  return [
    {
      icon: 'link-45deg',
      title: 'References Processed',
      value: FormattingService.formatNumber(h.totalReferences),
      variant: 'highlight' as const,
      breakdown: [{ label: 'Dominant', value: h.dominantType ?? '—' }]
    },
    {
      icon: 'list-ol',
      title: 'Reference Types',
      value: FormattingService.formatNumber(h.distinctTypes),
      variant: 'info' as const
    },
    {
      icon: 'recycle',
      title: 'GC Cycles',
      value: FormattingService.formatNumber(h.gcCount),
      variant: 'success' as const
    }
  ];
});

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileGCClient(route.params.profileId as string);
    data.value = await client.getReferenceProcessing();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading reference-processing analysis:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(loadData);
</script>

<style scoped>
.chart-container {
  width: 100%;
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1rem;
}

.toolbar-info {
  font-weight: 600;
  font-size: 0.9rem;
  color: var(--color-text);
}

.type-swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: var(--radius-sm);
  margin-right: 0.4rem;
  vertical-align: middle;
}
</style>
