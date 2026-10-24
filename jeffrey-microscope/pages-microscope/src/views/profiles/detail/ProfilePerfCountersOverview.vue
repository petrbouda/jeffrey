<!--
  - Jeffrey
  - Copyright (C) 2026 Petr Bouda
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     https://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
  -->

<template>
  <PageHeader
    title="Perf Counters Overview"
    description="HotSpot's own counters, read from the JVM's hsperfdata snapshot"
    icon="bi-speedometer2"
  >
    <LoadingState v-if="loading" message="Loading the perf counters overview..." />

    <ErrorState v-else-if="error" :message="error" />

    <EmptyState
      v-else-if="!overview"
      icon="bi-speedometer"
      title="No perf counters"
      description="This profile holds no perf-counters snapshot."
    />

    <div v-else>
      <StatsTable :metrics="headlineMetrics" class="mb-4" />

      <SectionHeaderBar text="JVM" />
      <div class="info-rows mb-4">
        <InfoRow label="VM">{{ text(overview.jvm.vmName) }}</InfoRow>
        <InfoRow label="VM version">{{ text(overview.jvm.vmVersion) }}</InfoRow>
        <InfoRow label="Vendor">{{ text(overview.jvm.vmVendor) }}</InfoRow>
        <InfoRow label="Java version">{{ text(overview.jvm.javaVersion) }}</InfoRow>
        <InfoRow label="GC policy">{{ text(overview.jvm.gcPolicy) }}</InfoRow>
        <InfoRow label="Started">
          {{ timestamp(overview.runtime.startedAtEpochMs) }}
        </InfoRow>
        <InfoRow label="Command" mono>
          <span class="info-row-text" :title="overview.jvm.command ?? ''">
            {{ text(overview.jvm.command) }}
          </span>
        </InfoRow>
        <InfoRow label="VM arguments" mono>
          <span class="info-row-text" :title="overview.jvm.vmArgs ?? ''">
            {{ text(overview.jvm.vmArgs) }}
          </span>
        </InfoRow>
      </div>

      <SectionHeaderBar text="Garbage Collectors" />
      <DataTable v-if="overview.gc.collectors.length > 0" class="mb-4">
        <thead>
          <tr>
            <th>Collector</th>
            <th class="text-end">Invocations</th>
            <th class="text-end">Total time</th>
            <th class="text-end">Average</th>
            <th class="text-end">Share of uptime</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(collector, index) in overview.gc.collectors" :key="index">
            <td>{{ text(collector.name) }}</td>
            <td class="text-end">{{ count(collector.invocations) }}</td>
            <td class="text-end">{{ duration(collector.timeNanos) }}</td>
            <td class="text-end">{{ averagePause(collector) }}</td>
            <td class="text-end">{{ shareOfUptime(collector.timeNanos) }}</td>
          </tr>
        </tbody>
      </DataTable>
      <EmptyState
        v-else
        icon="bi-recycle"
        title="No collectors"
        description="The JVM wrote no collector counters."
        class="mb-4"
      />

      <SectionHeaderBar text="Heap Generations" />
      <DataTable v-if="overview.memory.generations.length > 0">
        <thead>
          <tr>
            <th>Generation</th>
            <th class="text-end">Used</th>
            <th class="text-end">Capacity</th>
            <th class="text-end">Max capacity</th>
            <th class="text-end">Used of capacity</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(generation, index) in overview.memory.generations" :key="index">
            <td>{{ text(generation.name) }}</td>
            <td class="text-end">{{ bytes(generation.usedBytes) }}</td>
            <td class="text-end">{{ bytes(generation.capacityBytes) }}</td>
            <td class="text-end">{{ bytes(generation.maxCapacityBytes) }}</td>
            <td class="text-end">{{ ratio(generation.usedBytes, generation.capacityBytes) }}</td>
          </tr>
        </tbody>
      </DataTable>
      <EmptyState
        v-else
        icon="bi-memory"
        title="No generations"
        description="The JVM wrote no generation counters."
      />
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import DataTable from '@shared/components/table/DataTable.vue';
import SectionHeaderBar from '@shared/components/SectionHeaderBar.vue';
import InfoRow from '@shared/components/drawer/InfoRow.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import EmptyState from '@shared/components/EmptyState.vue';
import FormattingService from '@shared/services/FormattingService';
import ProfilePerformanceCountersClient from '@/services/api/ProfilePerformanceCountersClient';
import type {
  PerfCountersCollector,
  PerfCountersOverview
} from '@/services/api/model/PerfCountersOverview';
import '@shared/styles/shared-components.css';

const NO_VALUE = '—';
/** Above a tenth of the run spent collecting, the GC tile is drawn as a warning. */
const GC_SHARE_WARNING = 0.1;

const route = useRoute();
const loading = ref(true);
const error = ref<string | null>(null);
const overview = ref<PerfCountersOverview | null>(null);

const text = (value: string | null): string => value ?? NO_VALUE;

const count = (value: number | null): string =>
  value == null ? NO_VALUE : FormattingService.formatCount(value);

const timestamp = (epochMs: number | null): string =>
  epochMs == null ? NO_VALUE : FormattingService.formatTimestampUTC(epochMs);

const bytes = (value: number | null): string =>
  value == null ? NO_VALUE : FormattingService.formatBytes(value);

const duration = (nanos: number | null): string =>
  nanos == null ? NO_VALUE : FormattingService.formatDuration2Units(nanos);

const ratio = (part: number | null, whole: number | null): string =>
  part == null || whole == null || whole <= 0
    ? NO_VALUE
    : FormattingService.formatPercentage(part / whole);

const shareOfUptime = (nanos: number | null): string =>
  ratio(nanos, overview.value?.runtime.uptimeNanos ?? null);

const averagePause = (collector: PerfCountersCollector): string =>
  collector.timeNanos == null || !collector.invocations
    ? NO_VALUE
    : FormattingService.formatDuration2Units(collector.timeNanos / collector.invocations);

/** Young and old together: what the heap holds, out of what it has reserved so far. */
const heapTotals = computed(() => {
  const generations = overview.value?.memory.generations ?? [];
  const sum = (pick: (used: number | null, capacity: number | null) => number | null) => {
    const known = generations
      .map(generation => pick(generation.usedBytes, generation.capacityBytes))
      .filter((value): value is number => value != null);
    return known.length > 0 ? known.reduce((a, b) => a + b, 0) : null;
  };
  return { used: sum(used => used), capacity: sum((_, capacity) => capacity) };
});

const headlineMetrics = computed(() => {
  const o = overview.value;
  if (!o) {
    return [];
  }
  const uptimeLabel =
    o.runtime.uptimeBasis === 'SAFEPOINTS' ? 'Uptime (to the last safepoint)' : 'Uptime';
  return [
    {
      icon: 'clock-history',
      title: uptimeLabel,
      value: duration(o.runtime.uptimeNanos),
      variant: 'highlight' as const,
      breakdown: [
        { label: 'Application time', value: duration(o.runtime.applicationTimeNanos) },
        {
          label: 'VM init',
          value:
            o.runtime.vmInitMillis == null
              ? NO_VALUE
              : FormattingService.formatDurationMillisCompact(o.runtime.vmInitMillis)
        }
      ]
    },
    {
      icon: 'hdd-stack',
      title: 'Heap Used',
      value: bytes(heapTotals.value.used),
      variant: 'info' as const,
      breakdown: [
        { label: 'Capacity', value: bytes(heapTotals.value.capacity) },
        {
          label: 'Used of capacity',
          value: ratio(heapTotals.value.used, heapTotals.value.capacity)
        }
      ]
    },
    {
      icon: 'recycle',
      title: 'GC Time',
      value: duration(o.gc.timeNanos),
      variant:
        (o.gc.shareOfUptime ?? 0) > GC_SHARE_WARNING ? ('warning' as const) : ('info' as const),
      breakdown: [
        {
          label: 'Share of uptime',
          value:
            o.gc.shareOfUptime == null
              ? NO_VALUE
              : FormattingService.formatPercentage(o.gc.shareOfUptime)
        },
        { label: 'Collections', value: count(o.gc.invocations) },
        { label: 'Last cause', value: text(o.gc.lastCause) }
      ]
    },
    {
      icon: 'pause-circle',
      title: 'Safepoints',
      value: count(o.safepoints.count),
      variant: 'info' as const,
      breakdown: [
        { label: 'Total time', value: duration(o.safepoints.timeNanos) },
        { label: 'Time to reach', value: duration(o.safepoints.syncTimeNanos) }
      ]
    },
    {
      icon: 'box',
      title: 'Metaspace',
      value: bytes(o.memory.metaspace.usedBytes),
      variant: 'info' as const,
      breakdown: [
        { label: 'Capacity', value: bytes(o.memory.metaspace.capacityBytes) },
        { label: 'Class space used', value: bytes(o.memory.compressedClassSpace.usedBytes) }
      ]
    },
    {
      icon: 'layers',
      title: 'Classes Loaded',
      value: count(o.classes.loaded),
      variant: 'info' as const,
      breakdown: [
        { label: 'Unloaded', value: count(o.classes.unloaded) },
        { label: 'From CDS', value: count(o.classes.sharedLoaded) },
        { label: 'Load time', value: duration(o.classes.loadTimeNanos) }
      ]
    },
    {
      icon: 'lightning-charge',
      title: 'JIT Compilations',
      value: count(o.jit.compiles),
      variant: 'info' as const,
      breakdown: [
        { label: 'Compile time', value: duration(o.jit.timeNanos) },
        { label: 'Code size', value: bytes(o.jit.codeBytes) },
        { label: 'Bailouts', value: count(o.jit.bailouts) }
      ]
    },
    {
      icon: 'cpu',
      title: 'Live Threads',
      value: count(o.threads.live),
      variant: 'info' as const,
      breakdown: [
        { label: 'Peak', value: count(o.threads.peak) },
        { label: 'Daemon', value: count(o.threads.daemon) },
        { label: 'Started', value: count(o.threads.started) }
      ]
    }
  ];
});

onMounted(async () => {
  try {
    const profileId = route.params.profileId as string;
    overview.value = await new ProfilePerformanceCountersClient(profileId).getOverview();
  } catch (e) {
    console.error('Failed to load the perf counters overview:', e);
    error.value = 'Could not load the perf counters overview.';
  } finally {
    loading.value = false;
  }
});
</script>
