<template>
  <LoadingState v-if="loading" message="Loading GC configuration..." />

  <ErrorState v-else-if="error" message="Failed to load GC configuration" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
        title="Garbage Collection Configuration"
        description="JFR-based analysis of garbage collection configuration and settings"
        icon="bi-gear"
    />


    <!-- Configuration Overview Cards -->
    <div class="mb-4">
      <StatsTable :metrics="metricsData" />
    </div>

    <!-- Detailed Configuration Sections -->
    <div class="config-sections-grid">
      <ConfigurationSection title="Collector Configuration" icon="bi-recycle">
        <div class="config-item">
          <span class="config-label">Detected Type:</span>
          <span class="config-value">{{ configData?.detectedType || 'Unknown' }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Young Collector:</span>
          <span class="config-value">{{ configData?.collector.youngCollector || 'N/A' }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Old Collector:</span>
          <span class="config-value">{{ configData?.collector.oldCollector || 'N/A' }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Explicit GC Concurrent:</span>
          <span class="config-value">
            <i class="bi" :class="configData?.collector.explicitGCConcurrent ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
            {{ configData?.collector.explicitGCConcurrent ? 'Enabled' : 'Disabled' }}
          </span>
        </div>
        <div class="config-item">
          <span class="config-label">Explicit GC Disabled:</span>
          <span class="config-value">
            <i class="bi" :class="configData?.collector.explicitGCDisabled ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
            {{ configData?.collector.explicitGCDisabled ? 'Yes' : 'No' }}
          </span>
        </div>
        <div class="config-item">
          <span class="config-label">Pause Target:</span>
          <span class="config-value" v-if="configData?.collector.pauseTarget">
            {{ FormattingService.formatDuration2Units(configData?.collector.pauseTarget) }}
          </span>
          <span class="config-value" v-else>-</span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="Heap Configuration" icon="bi-memory">
        <div class="config-item">
          <span class="config-label">Min Size:</span>
          <span class="config-value">{{ FormattingService.formatBytes(configData?.heap.minSize || 0) }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Max Size:</span>
          <span class="config-value">{{ FormattingService.formatBytes(configData?.heap.maxSize || 0) }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Initial Size:</span>
          <span class="config-value">{{ FormattingService.formatBytes(configData?.heap.initialSize || 0) }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Compressed OOPs:</span>
          <span class="config-value">
            <i class="bi" :class="configData?.heap.usesCompressedOops ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
            {{ configData?.heap.usesCompressedOops ? 'Enabled' : 'Disabled' }}
          </span>
        </div>
        <div class="config-item" v-if="configData?.heap.compressedOopsMode">
          <span class="config-label">OOPs Mode:</span>
          <span class="config-value">{{ configData.heap.compressedOopsMode }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Object Alignment:</span>
          <span class="config-value">{{ configData?.heap.objectAlignment || 0 }} bytes</span>
        </div>
        <div class="config-item">
          <span class="config-label">Heap Address Bits:</span>
          <span class="config-value">{{ configData?.heap.heapAddressBits || 0 }} bits</span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="Thread Configuration" icon="bi-cpu">
        <div class="config-item">
          <span class="config-label">Parallel GC Threads:</span>
          <span class="config-value">{{ configData?.threads.parallelGCThreads || 0 }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Concurrent GC Threads:</span>
          <span class="config-value">{{ configData?.threads.concurrentGCThreads || 0 }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Dynamic GC Threads:</span>
          <span class="config-value">
            <i class="bi" :class="configData?.threads.usesDynamicGCThreads ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
            {{ configData?.threads.usesDynamicGCThreads ? 'Enabled' : 'Disabled' }}
          </span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="TLAB Configuration" icon="bi-memory">
        <div class="config-item">
          <span class="config-label">TLABs:</span>
          <span class="config-value">
            <i class="bi" :class="configData?.tlab?.usesTLABs ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
            {{ configData?.tlab?.usesTLABs ? 'Enabled' : 'Disabled' }}
          </span>
        </div>
        <div class="config-item" v-if="configData?.tlab?.usesTLABs">
          <span class="config-label">Min TLAB Size:</span>
          <span class="config-value">{{ FormattingService.formatBytes(configData.tlab.minTLABSize) }}</span>
        </div>
        <div class="config-item" v-if="configData?.tlab?.usesTLABs">
          <span class="config-label">TLAB Refill Waste:</span>
          <span class="config-value">{{ configData.tlab.tlabRefillWasteLimit }}</span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="Survivor Configuration" icon="bi-arrow-repeat">
        <div class="config-item">
          <span class="config-label">Max Tenuring Threshold:</span>
          <span class="config-value">{{ configData?.survivor?.maxTenuringThreshold || 0 }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Initial Tenuring Threshold:</span>
          <span class="config-value">{{ configData?.survivor?.initialTenuringThreshold || 0 }}</span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="Young Generation Configuration" icon="bi-layers">
        <div class="config-item">
          <span class="config-label">Max New Size:</span>
          <span class="config-value">{{ FormattingService.formatBytes(configData?.youngGeneration.maxSize || 0) }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Min New Size:</span>
          <span class="config-value">{{ FormattingService.formatBytes(configData?.youngGeneration.minSize || 0) }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">New Ratio:</span>
          <span class="config-value">{{ configData?.youngGeneration.newRatio || 0 }}</span>
        </div>
      </ConfigurationSection>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import {useNavigation} from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import StatsTable from '@/components/StatsTable.vue';
import ConfigurationSection from '@/components/ConfigurationSection.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ProfileGCClient from '@/services/api/ProfileGCClient';
import GCConfigurationData from '@/services/api/model/GCConfigurationData';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const {workspaceId, projectId} = useNavigation();
const loading = ref(true);
const error = ref<string | null>(null);
const configData = ref<GCConfigurationData>();

let client: ProfileGCClient;

// Computed metrics for StatsTable
const metricsData = computed(() => {
  if (!configData.value) return [];

  return [
    {
      icon: 'recycle',
      title: 'Collector Type',
      value: configData.value.detectedType || 'Unknown',
      variant: 'highlight' as const,
      breakdown: [
        {
          label: 'Young',
          value: configData.value.collector.youngCollector || 'N/A',
          color: '#4285F4'
        },
        {
          label: 'Old',
          value: configData.value.collector.oldCollector || 'N/A',
          color: '#4285F4'
        }
      ]
    },
    {
      icon: 'memory',
      title: 'Heap Memory',
      value: FormattingService.formatBytes(configData.value.heap.maxSize),
      variant: 'success' as const,
      breakdown: [
        {
          label: 'Min Size',
          value: FormattingService.formatBytes(configData.value.heap.minSize),
          color: '#28a745'
        },
        {
          label: 'Initial Size',
          value: FormattingService.formatBytes(configData.value.heap.initialSize),
          color: '#28a745'
        }
      ]
    },
    {
      icon: 'cpu',
      title: 'GC Threads',
      value: (configData.value.threads.parallelGCThreads + configData.value.threads.concurrentGCThreads).toString(),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Parallel',
          value: configData.value.threads.parallelGCThreads,
          color: '#34A853'
        },
        {
          label: 'Concurrent',
          value: configData.value.threads.concurrentGCThreads,
          color: '#34A853'
        }
      ]
    },
    {
      icon: 'layers',
      title: 'Young Generation',
      value: FormattingService.formatBytes(configData.value.youngGeneration.maxSize),
      variant: 'warning' as const,
      breakdown: [
        {
          label: 'Min Size',
          value: FormattingService.formatBytes(configData.value.youngGeneration.minSize),
          color: '#FBBC05'
        },
        {
          label: 'Max Size',
          value: FormattingService.formatBytes(configData.value.youngGeneration.maxSize),
          color: '#FBBC05'
        }
      ]
    }
  ];
});

const loadConfigurationData = async () => {
  try {
    loading.value = true;
    error.value = null;

    if (!client) {
      client = new ProfileGCClient(route.params.profileId as string);
    }

    configData.value = await client.getConfiguration();
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Unknown error occurred';
    console.error('Error loading GC configuration:', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadConfigurationData();
});
</script>

<style scoped>
.config-sections-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
  margin-bottom: 2rem;
}

@media (min-width: 1400px) {
  .config-sections-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 900px) {
  .config-sections-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .config-sections-grid {
    gap: 1rem;
  }
}
</style>
