<template>
  <!-- Loading State -->
  <div v-if="loading" class="loading-overlay">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading GC configuration...</span>
    </div>
    <p class="mt-2">Loading GC configuration...</p>
  </div>

  <div v-else-if="error" class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      Failed to load GC configuration
    </div>
  </div>

  <div v-else>
    <!-- Header Section -->
    <PageHeader
        title="Garbage Collection Configuration"
        description="JFR-based analysis of garbage collection configuration and settings"
        icon="bi-gear">
      <template #actions>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-primary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </template>
    </PageHeader>

    <!-- Configuration Overview Cards -->
    <div class="configuration-grid mb-4">
      <!-- Collector Configuration -->
      <DashboardCard
          title="Collector Type"
          :value="configData?.detectedType"
          :valueA="configData?.collector.youngCollector"
          :valueB="configData?.collector.oldCollector"
          labelA="Young"
          labelB="Old"
          variant="highlight"
      />

      <!-- Heap Configuration -->
      <DashboardCard
          title="Heap Memory"
          :value="FormattingService.formatBytes(configData!!.heap.maxSize)"
          :valueA="FormattingService.formatBytes(configData!!.heap.minSize)"
          :valueB="FormattingService.formatBytes(configData!!.heap.initialSize)"
          labelA="Min Size"
          labelB="Initial Size"
          variant="success"
      />

      <!-- Thread Configuration -->
      <DashboardCard
          title="GC Threads"
          :valueA="configData?.threads.parallelGCThreads"
          :valueB="configData?.threads.concurrentGCThreads"
          labelA="Parallel"
          labelB="Concurrent"
          variant="info"
      />

      <!-- Young Generation Configuration -->
      <DashboardCard
          title="Young Generation"
          :valueA="FormattingService.formatBytes(configData!!.youngGeneration.minSize)"
          :valueB="FormattingService.formatBytes(configData!!.youngGeneration.maxSize)"
          labelA="Min Size"
          labelB="Max Size"
          variant="warning"
      />

    </div>

    <!-- Detailed Configuration Sections -->
    <div class="config-sections-grid">
      <!-- Collector Details -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-recycle me-2"></i>
          Collector Configuration
        </h5>
        <div class="config-items">
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
              <i class="bi"
                 :class="configData?.collector.explicitGCConcurrent ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
              {{ configData?.collector.explicitGCConcurrent ? 'Enabled' : 'Disabled' }}
            </span>
          </div>
          <div class="config-item">
            <span class="config-label">Explicit GC Disabled:</span>
            <span class="config-value">
              <i class="bi"
                 :class="configData?.collector.explicitGCDisabled ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
              {{ configData?.collector.explicitGCDisabled ? 'Yes' : 'No' }}
            </span>
          </div>
          <div class="config-item">
            <span class="config-label">Pause Target:</span>
            <span class="config-value" v-if="configData?.collector.pauseTarget">{{
                FormattingService.formatDuration2Units(configData?.collector.pauseTarget)
              }}</span>
            <span class="config-value" v-else>-</span>
          </div>
        </div>
      </div>

      <!-- Heap Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-memory me-2"></i>
          Heap Configuration
        </h5>
        <div class="config-items">
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
              <i class="bi"
                 :class="configData?.heap.usesCompressedOops ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
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
        </div>
      </div>

      <!-- Thread Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-cpu me-2"></i>
          Thread Configuration
        </h5>
        <div class="config-items">
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
              <i class="bi"
                 :class="configData?.threads.usesDynamicGCThreads ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
              {{ configData?.threads.usesDynamicGCThreads ? 'Enabled' : 'Disabled' }}
            </span>
          </div>
        </div>
      </div>

      <!-- TLAB Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-memory me-2"></i>
          TLAB Configuration
        </h5>
        <div class="config-items">
          <div class="config-item">
            <span class="config-label">TLABs:</span>
            <span class="config-value">
              <i class="bi"
                 :class="configData?.tlab?.usesTLABs ? 'bi-check-circle text-success' : 'bi-x-circle text-danger'"></i>
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
        </div>
      </div>

      <!-- Survivor Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-arrow-repeat me-2"></i>
          Survivor Configuration
        </h5>
        <div class="config-items">
          <div class="config-item">
            <span class="config-label">Max Tenuring Threshold:</span>
            <span class="config-value">{{ configData?.survivor?.maxTenuringThreshold || 0 }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">Initial Tenuring Threshold:</span>
            <span class="config-value">{{ configData?.survivor?.initialTenuringThreshold || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- Young Generation Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-layers me-2"></i>
          Young Generation Configuration
        </h5>
        <div class="config-items">
          <div class="config-item">
            <span class="config-label">Max New Size:</span>
            <span class="config-value">{{
                FormattingService.formatBytes(configData?.youngGeneration.maxSize || 0)
              }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">Min New Size:</span>
            <span class="config-value">{{
                FormattingService.formatBytes(configData?.youngGeneration.minSize || 0)
              }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">New Ratio:</span>
            <span class="config-value">{{ configData?.youngGeneration.newRatio || 0 }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import {useNavigation} from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import ProfileGCClient from '@/services/profile/gc/ProfileGCClient';
import GCConfigurationData from '@/services/profile/gc/GCConfigurationData';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const {workspaceId, projectId} = useNavigation();
const loading = ref(true);
const error = ref<string | null>(null);
const configData = ref<GCConfigurationData>();

// Client initialization - will be set after workspace/project IDs are available
let client: ProfileGCClient;

const refreshData = () => {
  loadConfigurationData();
};

// Load GC configuration data from API
const loadConfigurationData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = null;

    // Initialize client if needed
    if (!client) {
      client = new ProfileGCClient(workspaceId.value, projectId.value, route.params.profileId as string);
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
.loading-overlay, .error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* Configuration Grid */
.configuration-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1rem;
}

/* Configuration Sections Grid */
.config-sections-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
  margin-bottom: 2rem;
}

/* 3 columns on large screens */
@media (min-width: 1400px) {
  .config-sections-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

/* Configuration Sections */
.config-section {
  background: #fafbfc;
  border-radius: 6px;
  padding: 1.25rem;
  border: 1px solid #e9ecef;
  transition: background-color 0.2s ease;
}

.config-section:hover {
  background: #f8f9fa;
}

.section-title {
  color: #6c757d;
  font-weight: 500;
  font-size: 0.95rem;
  margin-bottom: 1rem;
  display: flex;
  align-items: center;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.config-items {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f8f9fa;
}

.config-item:last-child {
  border-bottom: none;
}

.config-label {
  font-weight: 500;
  color: #6c757d;
  flex: 1;
}

.config-value {
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-weight: 600;
  color: #495057;
  text-align: right;
  flex: 1;
}

/* Responsive Design */
@media (max-width: 1400px) {
  .configuration-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 1200px) {
  .configuration-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 900px) {
  .config-sections-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .configuration-grid {
    grid-template-columns: 1fr;
  }

  .config-sections-grid {
    gap: 1rem;
  }

  .config-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
  }

  .config-value {
    text-align: left;
  }
}
</style>
