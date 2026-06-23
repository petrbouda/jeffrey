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

    <TabBar v-model="activeTab" :tabs="tabs" class="mb-3" />

    <div v-show="activeTab === 'overview'">
    <!-- Configuration Overview -->
    <div class="mb-4" v-if="configData?.configuration">
      <StatsTable :metrics="overviewMetrics" />
    </div>

    <!-- Detailed Configuration Sections -->
    <div class="config-sections-grid" v-if="configData?.configuration">
      <ConfigurationSection title="Host Information" icon="bi-server">
        <div class="config-item">
          <span class="config-label">Container Type</span>
          <span class="config-value">{{
            configData?.configuration.containerType || 'Unknown'
          }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Effective CPU Count</span>
          <span class="config-value">{{
            configData?.configuration.effectiveCpuCount || 'N/A'
          }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Host Total Memory</span>
          <span class="config-value">{{
            FormattingService.formatBytes(configData?.configuration.hostTotalMemory || 0)
          }}</span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="Memory Configuration" icon="bi-memory">
        <div class="config-item">
          <span class="config-label">Memory Limit</span>
          <span class="config-value">{{
            formatMemoryLimit(configData?.configuration.memoryLimit)
          }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Memory Soft Limit</span>
          <span class="config-value">{{
            formatMemoryLimit(configData?.configuration.memorySoftLimit)
          }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">Swap Memory Limit</span>
          <span class="config-value">{{
            formatMemoryLimit(configData?.configuration.swapMemoryLimit)
          }}</span>
        </div>
      </ConfigurationSection>

      <ConfigurationSection title="CPU Configuration" icon="bi-cpu">
        <div class="config-item">
          <span class="config-label">CPU Shares</span>
          <span class="config-value">{{
            formatCpuShares(configData?.configuration.cpuShares)
          }}</span>
        </div>
        <div class="config-item">
          <span class="config-label">CPU Quota</span>
          <span class="config-value" v-if="configData?.configuration.cpuQuota">
            {{ formatDuration(configData?.configuration.cpuQuota) }}
          </span>
          <span class="config-value" v-else>-</span>
        </div>
        <div class="config-item">
          <span class="config-label">CPU Slice Period</span>
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

    <!-- How It Works Tab -->
    <div v-show="activeTab === 'about'">
      <AboutPanel
        icon="bi-question-circle"
        title="Understanding Container Configuration"
        subtitle="What the JVM sees when it runs inside a cgroup — and why it matters"
      >
        <AboutCallout variant="intro">
          <p>
            Inside a container the JVM must size itself to the <strong>cgroup limits</strong>, not the
            host's hardware. A container-aware JVM reads these limits to pick the heap size, GC thread
            count and available processors. When the limits are wrong (or invisible to an old JVM), you
            get surprise OOM kills and CPU throttling. This page shows exactly what the JVM detected.
          </p>
        </AboutCallout>

        <AboutSection icon="bi-cpu" title="The Limits That Shape the JVM">
          <FeatureGrid>
            <FeatureCard icon="bi-cpu-fill" variant="primary" title="CPU Quota &amp; Period">
              cgroup v2 <code>cpu.max</code> (quota/period) caps how much CPU time you get per period.
              The JVM derives <em>effective CPU count</em> from it, which sizes GC/JIT thread pools and
              <code>availableProcessors()</code>.
            </FeatureCard>
            <FeatureCard icon="bi-sliders" variant="info" title="CPU Shares">
              A <em>relative</em> weight (not a hard cap) used by the scheduler when CPUs are contended.
              High shares help under contention but don't guarantee throughput.
            </FeatureCard>
            <FeatureCard icon="bi-memory" variant="warning" title="Memory Limit">
              The hard ceiling. Exceed it and the kernel OOM-kills the process — heap + off-heap +
              metaspace + stacks must all fit. This is why the NMT and Native Memory pages matter in
              containers.
            </FeatureCard>
            <FeatureCard icon="bi-thermometer-half" variant="danger" title="CPU Throttling">
              When you use your full quota within a period, the kernel <em>pauses</em> your threads
              until the next period — latency spikes that look like GC or lock contention but aren't.
            </FeatureCard>
          </FeatureGrid>
        </AboutSection>

        <AboutCallout variant="tip" title="Throttling masquerades as latency" icon="bi-lightbulb-fill">
          Unexplained periodic latency in a container with a low CPU quota is often throttling, not
          your code. Correlate with the System &amp; Host CPU view and your container's CPU limit.
        </AboutCallout>

        <AboutSection icon="bi-broadcast" title="How JFR Emits This">
          <ul>
            <li>
              <code>jdk.ContainerConfiguration</code> — the static cgroup limits shown on this page
              (container type, effective CPU count, memory/swap limits, CPU quota/period/shares).
            </li>
            <li>
              <code>jdk.ContainerCPUThrottling</code> — throttled periods and total throttled time, the
              direct signal of hitting the CPU quota.
            </li>
            <li>
              <code>jdk.ContainerCPUUsage</code> / <code>jdk.ContainerMemoryUsage</code> /
              <code>jdk.ContainerIOUsage</code> — periodic actual usage against the limits.
            </li>
          </ul>
          <p>These events are emitted only when the JVM detects it is running inside a container.</p>
        </AboutSection>
      </AboutPanel>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import PageHeader from '@shared/components/layout/PageHeader.vue';
import StatsTable from '@shared/components/table/StatsTable.vue';
import TabBar from '@shared/components/TabBar.vue';
import type { TabBarItem } from '@shared/components/TabBar.vue';
import AboutPanel from '@/components/about/AboutPanel.vue';
import AboutCallout from '@/components/about/AboutCallout.vue';
import AboutSection from '@/components/about/AboutSection.vue';
import FeatureGrid from '@/components/about/FeatureGrid.vue';
import FeatureCard from '@/components/about/FeatureCard.vue';
import ConfigurationSection from '@/components/ConfigurationSection.vue';
import LoadingState from '@shared/components/LoadingState.vue';
import ErrorState from '@shared/components/ErrorState.vue';
import ProfileContainerClient from '@/services/api/ProfileContainerClient';
import ContainerConfigurationData from '@/services/api/model/ContainerConfigurationData';
import FormattingService from '@shared/services/FormattingService';
import FeatureType from '@/services/api/model/FeatureType';
import ContainerNotAvailableAlert from '@/components/alerts/ContainerNotAvailableAlert.vue';

interface Props {
  disabledFeatures?: FeatureType[];
}

const props = withDefaults(defineProps<Props>(), {
  disabledFeatures: () => []
});

const route = useRoute();

const profileId = route.params.profileId as string;

const loading = ref(true);
const error = ref(false);
const configData = ref<ContainerConfigurationData | null>(null);

const activeTab = ref('overview');
const tabs = computed<TabBarItem[]>(() => [
  { id: 'overview', label: 'Configuration', icon: 'server' },
  { id: 'about', label: 'How It Works', icon: 'book' }
]);

const isContainerDashboardDisabled = computed(() => {
  return props.disabledFeatures.includes(FeatureType.CONTAINER_DASHBOARD);
});

const overviewMetrics = computed(() => {
  const config = configData.value?.configuration;
  if (!config) return [];

  return [
    {
      icon: 'memory',
      title: 'Memory Limits',
      value: getMemoryRequest(config),
      variant: 'success' as const,
      breakdown: [
        {
          label: 'Request',
          value: getMemoryRequest(config)
        },
        {
          label: 'Limit',
          value: formatMemoryLimit(config.memoryLimit)
        }
      ]
    },
    {
      icon: 'cpu',
      title: 'CPU Resources',
      value: formatCpuShares(config.cpuShares),
      variant: 'info' as const,
      breakdown: [
        {
          label: 'Request',
          value: formatCpuShares(config.cpuShares)
        },
        {
          label: 'Limit',
          value: formatDuration(config.cpuQuota)
        }
      ]
    }
  ];
});

let containerClient: ProfileContainerClient;

const loadData = async () => {
  try {
    loading.value = true;
    error.value = false;

    if (!containerClient) {
      containerClient = new ProfileContainerClient(profileId);
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
  .config-sections-grid {
    grid-template-columns: 1fr;
  }
}
</style>
