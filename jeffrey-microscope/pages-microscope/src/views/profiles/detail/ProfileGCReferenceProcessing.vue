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

    <EmptyState
      v-if="!hasData"
      icon="bi-link-45deg"
      title="No reference-processing events"
      description="This recording contains no jdk.GCReferenceStatistics events."
    />

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
          <div id="gc-reference-stacked-chart"></div>
        </div>
      </div>

      <!-- By Type -->
      <div v-show="activeTab === 'by-type'">
        <ChartDescription
          shows="Total and average-per-GC references processed, by reference type"
          use-case="Compare which reference kinds dominate processing work across the whole recording"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>Reference Type</th>
                <th class="text-end">Total Processed</th>
                <th class="text-end">Avg / GC</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="stat in data!.byType" :key="stat.type">
                <td>
                  <span class="type-swatch" :style="{ background: colorForType(stat.type) }"></span>
                  {{ stat.type }}
                </td>
                <td class="text-end">{{ FormattingService.formatNumber(stat.total) }}</td>
                <td class="text-end">{{ FormattingService.formatNumber(stat.avgPerGc) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Per-GC -->
      <div v-show="activeTab === 'per-gc'">
        <ChartDescription
          shows="Per-collection reference totals, ranked by total references processed"
          use-case="Pinpoint the individual GC cycles that did the heaviest reference processing"
        />
        <div class="table-responsive">
          <table class="table table-sm table-hover mb-0">
            <thead>
              <tr>
                <th>GC ID</th>
                <th v-for="type in typeColumns" :key="type" class="text-end">{{ type }}</th>
                <th class="text-end">Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="gc in data!.perGc" :key="gc.gcId">
                <td>{{ gc.gcId }}</td>
                <td v-for="type in typeColumns" :key="type" class="text-end">
                  {{ FormattingService.formatNumber(gc.countsByType[type] ?? 0) }}
                </td>
                <td class="text-end fw-semibold">{{ FormattingService.formatNumber(gc.total) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- About -->
      <div v-show="activeTab === 'about'">
        <AboutPanel>
          <AboutSection icon="bi-link-45deg" title="What Reference Processing Tells You">
            <p>
              Each GC cycle processes the JDK's reference objects:
              <strong>Soft</strong> (memory-sensitive caches), <strong>Weak</strong> (canonicalizing
              maps), <strong>Final</strong> (objects with a <code>finalize()</code> method), and
              <strong>Phantom</strong> (post-mortem cleanup, including <code>Cleaner</code>).
              <code>jdk.GCReferenceStatistics</code> reports, per collection, how many references of
              each type were processed.
            </p>
            <AboutCallout variant="info" title="Counts only on JDK 26" icon="bi-info-circle-fill">
              The JDK 26 event carries only the processed <em>count</em> per type and the GC id — there
              is no per-phase processing time — so every view here is count-based.
            </AboutCallout>
          </AboutSection>

          <AboutSection icon="bi-graph-up" title="Reading the Views">
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
        </AboutPanel>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import ApexCharts from 'apexcharts';

import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import TabBar from '@/components/TabBar.vue';
import ChartDescription from '@/components/ChartDescription.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import EmptyState from '@/components/EmptyState.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import FormattingService from '@/services/FormattingService';
import type { ReferenceProcessingData } from '@/services/api/model/GCReferenceModels';

const route = useRoute();

const loading = ref(true);
const error = ref<string | null>(null);
const data = ref<ReferenceProcessingData>();

let chart: ApexCharts | null = null;

const tabs = [
  { id: 'timeline', label: 'Timeline', icon: 'activity' },
  { id: 'by-type', label: 'By Type', icon: 'list-ol' },
  { id: 'per-gc', label: 'Per-GC', icon: 'bar-chart-steps' },
  { id: 'about', label: 'About', icon: 'info-circle' }
];
const activeTab = ref(tabs[0].id);

// Stable colors per reference type (memory-pressure red for Soft, etc.); unknown types fall back.
const TYPE_COLORS: Record<string, string> = {
  'Soft reference': '#EA4335',
  'Weak reference': '#FBBC04',
  'Final reference': '#9334E6',
  'Phantom reference': '#4285F4',
  'Cleaner reference': '#34A853',
  'Other reference': '#9AA0A6'
};
const FALLBACK_COLOR = '#9AA0A6';
const colorForType = (type: string): string => TYPE_COLORS[type] ?? FALLBACK_COLOR;

const hasData = computed(() => (data.value?.header.totalReferences ?? 0) > 0);
const typeColumns = computed(() => (data.value?.byType ?? []).map(stat => stat.type));

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

const renderChart = async () => {
  if (!hasData.value) {
    return;
  }
  await nextTick();
  const element = document.getElementById('gc-reference-stacked-chart');
  if (!element) {
    return;
  }

  const series = (data.value?.timeline.series ?? []).map(serie => ({
    name: serie.name,
    data: serie.data
  }));
  const colors = (data.value?.byType ?? []).map(stat => colorForType(stat.type));

  const options = {
    chart: {
      type: 'area' as const,
      height: 380,
      stacked: true,
      fontFamily: 'inherit',
      toolbar: { show: false }
    },
    series,
    colors,
    dataLabels: { enabled: false },
    stroke: { curve: 'smooth' as const, width: 2 },
    fill: { type: 'solid', opacity: 0.45 },
    xaxis: {
      type: 'numeric' as const,
      title: { text: 'Time', style: { fontSize: '12px' } },
      labels: {
        style: { fontSize: '10px' },
        formatter: (value: string | number) => FormattingService.formatDuration2Units(Number(value) * 1e9)
      }
    },
    yaxis: {
      title: { text: 'References Processed', style: { fontSize: '12px' } },
      labels: {
        style: { fontSize: '10px' },
        formatter: (value: number) => FormattingService.formatNumber(value)
      }
    },
    legend: { position: 'bottom' as const },
    tooltip: {
      y: { formatter: (value: number) => FormattingService.formatNumber(value) }
    },
    grid: { borderColor: '#e7e7e7', strokeDashArray: 3 }
  } as ApexCharts.ApexOptions;

  if (chart) {
    chart.destroy();
  }
  chart = new ApexCharts(element, options);
  chart.render();
};

const loadData = async () => {
  try {
    loading.value = true;
    error.value = null;
    const client = new ProfileGCClient(route.params.profileId as string);
    data.value = await client.getReferenceProcessing();
    if (activeTab.value === 'timeline') {
      renderChart();
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading reference-processing analysis:', err);
  } finally {
    loading.value = false;
  }
};

// The chart lives inside a v-show tab, so (re)render when the Timeline tab becomes visible.
watch(activeTab, tab => {
  if (tab === 'timeline') {
    renderChart();
  }
});

onMounted(loadData);
onUnmounted(() => {
  if (chart) {
    chart.destroy();
    chart = null;
  }
});
</script>

<style scoped>
.chart-container {
  background: var(--color-white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1rem;
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
