<template>
  <!-- Loading State -->
  <div v-if="loading" class="loading-overlay">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden">Loading Container configuration...</span>
    </div>
    <p class="mt-2">Loading Container configuration...</p>
  </div>

  <div v-else-if="error" class="error-state">
    <div class="alert alert-danger d-flex align-items-center">
      <i class="bi bi-exclamation-triangle-fill me-2"></i>
      Failed to load Container configuration
    </div>
  </div>

  <div v-else>
    <!-- Header Section -->
    <DashboardHeader
        title="Container Configuration"
        description="JFR-based analysis of container configuration and settings"
        icon="server"
    >
      <template #actions>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-primary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </template>
    </DashboardHeader>

    <!-- Configuration Overview Cards -->
    <div class="configuration-grid mb-4" v-if="configData?.configuration">
      <!-- Host Information -->
      <DashboardCard
          title="Host Information"
          :valueA="formatBytes(configData?.configuration.hostTotalMemory || 0)"
          :valueB="configData?.configuration.effectiveCpuCount?.toString() || 'N/A'"
          labelA="Total Memory"
          labelB="Effective CPUs"
          variant="highlight"
      />

      <!-- Memory Configuration -->
      <DashboardCard
          title="Memory Limits"
          :valueA="getMemoryRequest(configData?.configuration)"
          :valueB="formatMemoryLimit(configData?.configuration.memoryLimit)"
          labelA="Request"
          labelB="Limit"
          variant="success"
      />

      <!-- CPU Configuration -->
      <DashboardCard
          title="CPU Resources"
          :valueA="formatCpuShares(configData?.configuration.cpuShares)"
          :valueB="formatDuration(configData?.configuration.cpuQuota)"
          labelA="Request"
          labelB="Limit"
          variant="info"
      />
    </div>

    <!-- Detailed Configuration Sections -->
    <div class="config-sections-grid" v-if="configData?.configuration">
      <!-- Host Details -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-server me-2"></i>
          Host Information
        </h5>
        <div class="config-items">
          <div class="config-item">
            <span class="config-label">Container Type:</span>
            <span class="config-value">{{ configData?.configuration.containerType || 'Unknown' }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">Effective CPU Count:</span>
            <span class="config-value">{{ configData?.configuration.effectiveCpuCount || 'N/A' }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">Host Total Memory:</span>
            <span class="config-value">{{ formatBytes(configData?.configuration.hostTotalMemory || 0) }}</span>
          </div>
        </div>
      </div>

      <!-- Memory Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-memory me-2"></i>
          Memory Configuration
        </h5>
        <div class="config-items">
          <div class="config-item">
            <span class="config-label">Memory Limit:</span>
            <span class="config-value">{{ formatMemoryLimit(configData?.configuration.memoryLimit) }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">Memory Soft Limit:</span>
            <span class="config-value">{{ formatMemoryLimit(configData?.configuration.memorySoftLimit) }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">Swap Memory Limit:</span>
            <span class="config-value">{{ formatMemoryLimit(configData?.configuration.swapMemoryLimit) }}</span>
          </div>
        </div>
      </div>

      <!-- CPU Configuration -->
      <div class="config-section">
        <h5 class="section-title">
          <i class="bi bi-cpu me-2"></i>
          CPU Configuration
        </h5>
        <div class="config-items">
          <div class="config-item">
            <span class="config-label">CPU Shares:</span>
            <span class="config-value">{{ formatCpuShares(configData?.configuration.cpuShares) }}</span>
          </div>
          <div class="config-item">
            <span class="config-label">CPU Quota:</span>
            <span class="config-value" v-if="configData?.configuration.cpuQuota">{{
                formatDuration(configData?.configuration.cpuQuota)
              }}</span>
            <span class="config-value" v-else>-</span>
          </div>
          <div class="config-item">
            <span class="config-label">CPU Slice Period:</span>
            <span class="config-value" v-if="configData?.configuration.cpuSlicePeriod">{{
                formatDuration(configData?.configuration.cpuSlicePeriod)
              }}</span>
            <span class="config-value" v-else>-</span>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="alert alert-info">
      <i class="bi bi-info-circle me-2"></i>
      No container configuration data available in this profile.
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import DashboardHeader from '@/components/DashboardHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import ProfileContainerClient from '@/services/profile/container/ProfileContainerClient';
import ContainerConfigurationData from '@/services/profile/container/ContainerConfigurationData';
import FormattingService from '@/services/FormattingService';

const route = useRoute();
const projectId = route.params.projectId as string;
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref(false);
const configData = ref<ContainerConfigurationData | null>(null);

const containerClient = new ProfileContainerClient(projectId, profileId);

const loadData = async () => {
  try {
    loading.value = true;
    error.value = false;
    
    configData.value = await containerClient.getConfiguration();
  } catch (err) {
    console.error('Error loading container configuration:', err);
    error.value = true;
  } finally {
    loading.value = false;
  }
};

const refreshData = async () => {
  await loadData();
};

const formatBytes = (bytes: number): string => {
  return FormattingService.formatBytes(bytes);
};

const formatDuration = (nanoseconds: number | undefined): string => {
  if (!nanoseconds) return '-';
  return FormattingService.formatDuration(nanoseconds);
};

const formatMemoryLimit = (bytes: number | undefined): string => {
  if (!bytes) return '-';
  if (bytes === -1) return 'Unlimited';
  return FormattingService.formatBytes(bytes);
};

const formatCpuShares = (shares: number | undefined): string => {
  if (!shares) return '-';
  if (shares === -1) return 'Not set';
  return shares.toString();
};

const getMemoryRequest = (config: any): string => {
  if (!config) return '-';
  
  // If Memory Soft Limit exists and is not 0, use it as Request
  if (config.memorySoftLimit && config.memorySoftLimit !== 0 && config.memorySoftLimit !== -1) {
    return formatMemoryLimit(config.memorySoftLimit);
  }
  
  // Otherwise, use Memory Limit as both Request and Limit
  return formatMemoryLimit(config.memoryLimit);
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.loading-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.error-state {
  margin: 2rem 0;
}

/* Configuration Grid */
.configuration-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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
  border-bottom: 1px solid #eaecef;
}

.config-item:last-child {
  border-bottom: none;
}

.config-label {
  font-weight: 500;
  color: #6c757d;
  font-size: 0.875rem;
}

.config-value {
  font-weight: 600;
  color: #212529;
  font-size: 0.875rem;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .configuration-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .config-sections-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 576px) {
  .configuration-grid {
    grid-template-columns: 1fr;
  }
}
</style>
