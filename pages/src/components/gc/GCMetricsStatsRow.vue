<template>
  <div v-if="!loading && !error && metricsData.length > 0" class="mb-4">
    <StatsTable :metrics="metricsData" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import StatsTable from '@/components/StatsTable.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import GCOverviewData from '@/services/api/model/GCOverviewData';
import FormattingService from '@/services/FormattingService';

const props = defineProps<{
  profileId: string;
}>();

const loading = ref(true);
const error = ref<string | null>(null);
const gcOverviewData = ref<GCOverviewData | null>(null);

// GC Summary data (computed from real data)
const gcSummary = computed(() => {
  if (!gcOverviewData.value) return null;

  const header = gcOverviewData.value.header;
  return {
    totalCollections: FormattingService.formatNumber(header.totalCollections),
    youngCollections: FormattingService.formatNumber(header.youngCollections),
    oldCollections: FormattingService.formatNumber(header.oldCollections),
    fullCollections: FormattingService.formatNumber(header.fullCollections),
    maxPauseTime: FormattingService.formatDuration2Units(header.maxPauseTime),
    p99PauseTime: FormattingService.formatDuration2Units(header.p99PauseTime),
    p95PauseTime: FormattingService.formatDuration2Units(header.p95PauseTime),
    gcThroughput: FormattingService.formatPercentage(header.gcThroughput / 100),
    gcOverhead: FormattingService.formatPercentage(header.gcOverhead / 100),
    collectionFrequency: `${header.collectionFrequency.toFixed(2)} GC/s`,
    manualGCTime: FormattingService.formatDuration2Units(header.manualGCCalls.totalTime),
    systemGCCalls: FormattingService.formatNumber(header.manualGCCalls.systemGCCalls),
    diagnosticCommandCalls: FormattingService.formatNumber(header.manualGCCalls.diagnosticCommandCalls)
  };
});

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!gcOverviewData.value || !gcSummary.value) return [];

  return [
    {
      icon: 'recycle',
      title: 'GC Collections',
      value: gcSummary.value.totalCollections,
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Young',
          value: gcSummary.value.youngCollections,
          color: '#4285F4'
        },
        {
          label: 'Old',
          value: gcSummary.value.oldCollections,
          color: '#4285F4'
        },
        {
          label: 'Full',
          value: gcSummary.value.fullCollections,
          color: '#EA4335'
        }
      ]
    },
    {
      icon: 'hourglass-split',
      title: 'GC Pauses',
      value: gcSummary.value.maxPauseTime,
      variant: 'warning' as const,
      breakdown: [
        {
          label: '99th',
          value: gcSummary.value.p99PauseTime,
          color: '#FBBC05'
        },
        {
          label: '95th',
          value: gcSummary.value.p95PauseTime,
          color: '#FBBC05'
        }
      ]
    },
    {
      icon: 'speedometer2',
      title: 'GC Overhead',
      value: gcSummary.value.gcOverhead,
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Throughput',
          value: gcSummary.value.gcThroughput,
          color: '#34A853'
        },
        {
          label: 'Frequency',
          value: gcSummary.value.collectionFrequency,
          color: '#34A853'
        }
      ]
    },
    {
      icon: 'hand-index-thumb',
      title: 'Manual GC Calls',
      value: gcSummary.value.manualGCTime,
      variant: 'warning' as const,
      breakdown: [
        {
          label: 'System GC',
          value: gcSummary.value.systemGCCalls,
          color: '#FBBC05'
        },
        {
          label: 'Diagnostic Cmd',
          value: gcSummary.value.diagnosticCommandCalls,
          color: '#FBBC05'
        }
      ]
    }
  ];
});

// Load GC overview data from API
const loadGCOverviewData = async () => {
  try {
    loading.value = true;
    error.value = null;

    const client = new ProfileGCClient(props.profileId);
    gcOverviewData.value = await client.getOverview();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading GC overview data:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadGCOverviewData();
});
</script>
