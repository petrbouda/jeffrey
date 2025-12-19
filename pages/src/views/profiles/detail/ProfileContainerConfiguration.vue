<template>
  <ContainerNotAvailableAlert v-if="isContainerDashboardDisabled" />

  <LoadingState v-else-if="loading" message="Loading Container configuration..." />

  <ErrorState v-else-if="error" message="Failed to load Container configuration" />

  <div v-else>
    <!-- Header Section -->
    <PageHeader
        title="Container Configuration"
        description="JFR-based analysis of container configuration and settings"
        icon="bi-server"
    >
      <template #actions>
        <div class="d-flex gap-2">
          <button class="btn btn-sm btn-outline-primary" @click="refreshData">
            <i class="bi bi-arrow-clockwise"></i>
          </button>
        </div>
      </template>
    </PageHeader>

    <!-- Configuration Overview Cards -->
    <div class="configuration-grid mb-4" v-if="configData?.configuration">
      <DashboardCard
          title="Host Information"
          :valueA="formatBytes(configData?.configuration.hostTotalMemory || 0)"
          :valueB="configData?.configuration.effectiveCpuCount?.toString() || 'N/A'"
          labelA="Total Memory"
          labelB="Effective CPUs"
          variant="highlight"
      />

      <DashboardCard
          title="Memory Limits"
          :valueA="getMemoryRequest(configData?.configuration)"
          :valueB="formatMemoryLimit(configData?.configuration.memoryLimit)"
          labelA="Request"
          labelB="Limit"
          variant="success"
      />

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
      <ConfigurationSection title="Host Information" icon="bi-server">
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
      </ConfigurationSection>

      <ConfigurationSection title="Memory Configuration" icon="bi-memory">
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
      </ConfigurationSection>

      <ConfigurationSection title="CPU Configuration" icon="bi-cpu">
        <div class="config-item">
          <span class="config-label">CPU Shares:</span>
          <span class="config-value">{{ formatCpuShares(configData?.configuration.cpuShares) }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">CPU Quota:</span>
          <span class="config-value" v-if="configData?.configuration.cpuQuota">
            {{ formatDuration(configData?.configuration.cpuQuota) }}
          </span>
          <span class="config-value" v-else>-</span>
        </div>
        <div class="config-item">
          <span class="config-label">CPU Slice Period:</span>
          <span class="config-value" v-if="configData?.configuration.cpuSlicePeriod">
            {{ formatDuration(configData?.configuration.cpuSlicePeriod) }}
          </span>
          <span class="config-value" v-else>-</span>
        </div>
      </ConfigurationSection>
    </div>

    <div v-else class="alert alert-info">
      <i class="bi bi-info-circle me-2"></i>
      No container configuration data available in this profile.
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, withDefaults} from 'vue';
import {useRoute} from 'vue-router';
import {useNavigation} from '@/composables/useNavigation';
import PageHeader from '@/components/layout/PageHeader.vue';
import DashboardCard from '@/components/DashboardCard.vue';
import ConfigurationSection from '@/components/ConfigurationSection.vue';
import LoadingState from '@/components/LoadingState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ProfileContainerClient from '@/services/profile/container/ProfileContainerClient';
import ContainerConfigurationData from '@/services/profile/container/ContainerConfigurationData';
import FormattingService from '@/services/FormattingService';
import FeatureType from '@/services/profile/features/FeatureType';
import ContainerNotAvailableAlert from '@/components/alerts/ContainerNotAvailableAlert.vue';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();
const {workspaceId, projectId} = useNavigation();
const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref(false);
const configData = ref<ContainerConfigurationData | null>(null);

const isContainerDashboardDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.CONTAINER_DASHBOARD);
});

let containerClient: ProfileContainerClient;

const loadData = async () => {
  try {
    if (!workspaceId.value || !projectId.value) return;

    loading.value = true;
    error.value = false;

    if (!containerClient) {
      containerClient = new ProfileContainerClient(workspaceId.value, projectId.value, profileId);
    }

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
  if (config.memorySoftLimit && config.memorySoftLimit !== 0 && config.memorySoftLimit !== -1) {
    return formatMemoryLimit(config.memorySoftLimit);
  }
  return formatMemoryLimit(config.memoryLimit);
};

onMounted(() => {
  if (!isContainerDashboardDisabled.value) {
    loadData();
  }
});
</script>

<style scoped>
.configuration-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

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
