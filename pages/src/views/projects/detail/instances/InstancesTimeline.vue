<template>
  <PageHeader
    title="Instance Timeline"
    description="Visual timeline showing when instances were active. Track the lifecycle of your application instances over time."
    icon="bi-bar-chart-steps"
  >
    <!-- Time Range Selector -->
    <div class="d-flex gap-3 mb-3 align-items-center">
      <div class="btn-group" role="group">
        <button
          v-for="range in timeRanges"
          :key="range.value"
          type="button"
          class="btn btn-sm"
          :class="selectedRange === range.value ? 'btn-primary' : 'btn-outline-secondary'"
          @click="selectedRange = range.value"
        >
          {{ range.label }}
        </button>
      </div>
    </div>

    <!-- Loading Indicator -->
    <LoadingState v-if="loading" message="Loading timeline..." />

    <!-- Timeline Visualization -->
    <div v-else class="col-12">
      <!-- Timeline Header -->
      <div class="timeline-header mb-3">
        <div class="timeline-scale">
          <span v-for="tick in timelineTicks" :key="tick" class="tick">{{ tick }}</span>
        </div>
      </div>

      <!-- Instance Rows -->
      <div class="timeline-container">
        <div v-for="instance in instances" :key="instance.id" class="timeline-row">
          <div class="instance-label">
            <span class="status-dot" :class="instance.status === 'ONLINE' ? 'online' : 'offline'"></span>
            <span class="hostname">{{ truncateHostname(instance.hostname) }}</span>
          </div>
          <div class="instance-bar-container">
            <div
              class="instance-bar"
              :class="instance.status === 'ONLINE' ? 'active' : 'inactive'"
              :style="getBarStyle(instance)"
            >
              <span class="bar-tooltip">
                {{ instance.hostname }}<br>
                Sessions: {{ instance.sessionCount }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Legend -->
      <div class="timeline-legend mt-4">
        <div class="legend-item">
          <span class="legend-bar active"></span>
          <span>Online</span>
        </div>
        <div class="legend-item">
          <span class="legend-bar inactive"></span>
          <span>Offline</span>
        </div>
      </div>
    </div>
  </PageHeader>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import PageHeader from '@/components/layout/PageHeader.vue';
import LoadingState from '@/components/LoadingState.vue';
import ProjectInstanceClient from '@/services/api/ProjectInstanceClient';
import ProjectInstance from '@/services/api/model/ProjectInstance';
import { useNavigation } from '@/composables/useNavigation';
import '@/styles/shared-components.css';

const { workspaceId, projectId } = useNavigation();

const timeRanges = [
  { label: '1H', value: '1h' },
  { label: '6H', value: '6h' },
  { label: '24H', value: '24h' },
  { label: '7D', value: '7d' },
  { label: '30D', value: '30d' }
];

const loading = ref(true);
const selectedRange = ref('24h');
const instances = ref<ProjectInstance[]>([]);

const timelineTicks = computed(() => {
  switch (selectedRange.value) {
    case '1h':
      return ['Now', '-15m', '-30m', '-45m', '-1h'];
    case '6h':
      return ['Now', '-1h', '-2h', '-3h', '-4h', '-5h', '-6h'];
    case '24h':
      return ['Now', '-6h', '-12h', '-18h', '-24h'];
    case '7d':
      return ['Today', '-1d', '-2d', '-3d', '-4d', '-5d', '-6d', '-7d'];
    case '30d':
      return ['Today', '-1w', '-2w', '-3w', '-4w'];
    default:
      return ['Now', '-6h', '-12h', '-18h', '-24h'];
  }
});

function truncateHostname(hostname: string): string {
  if (hostname.length > 20) {
    return hostname.substring(0, 17) + '...';
  }
  return hostname;
}

function getBarStyle(instance: ProjectInstance): Record<string, string> {
  const now = Date.now();
  const rangeMs = getRangeMs();

  // Calculate how far back (as percentage) the instance started
  // 0% = now (left side), 100% = end of range (right side)
  const startPercent = Math.min((now - instance.startedAt) / rangeMs * 100, 100);

  // Calculate where the bar ends (0 = now for ONLINE, or when it went offline)
  const endPercent = instance.status === 'ONLINE'
    ? 0
    : Math.min((now - instance.lastHeartbeat) / rangeMs * 100, 100);

  // Bar starts at endPercent (closer to "Now") and extends to startPercent
  const left = endPercent;
  const width = Math.max(startPercent - endPercent, 2); // min 2% width for visibility

  return {
    left: `${left}%`,
    width: `${Math.min(width, 100 - left)}%`
  };
}

function getRangeMs(): number {
  switch (selectedRange.value) {
    case '1h': return 3600000;
    case '6h': return 21600000;
    case '24h': return 86400000;
    case '7d': return 604800000;
    case '30d': return 2592000000;
    default: return 86400000;
  }
}

onMounted(async () => {
  const client = new ProjectInstanceClient(workspaceId.value!, projectId.value!);
  instances.value = await client.list();
  loading.value = false;
});
</script>

<style scoped>
.timeline-header {
  padding-left: 160px;
}

.timeline-scale {
  display: flex;
  justify-content: space-between;
  color: #64748b;
  font-size: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #e2e8f0;
}

.timeline-container {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.timeline-row {
  display: flex;
  align-items: center;
  height: 40px;
}

.instance-label {
  width: 150px;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding-right: 1rem;
  flex-shrink: 0;
}

.hostname {
  font-size: 0.8rem;
  color: #374151;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.online {
  background-color: #22c55e;
}

.status-dot.offline {
  background-color: #9ca3af;
}

.instance-bar-container {
  flex: 1;
  height: 24px;
  background-color: #f1f5f9;
  border-radius: 4px;
  position: relative;
  overflow: hidden;
}

.instance-bar {
  position: absolute;
  height: 100%;
  border-radius: 4px;
  min-width: 8px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.instance-bar.active {
  background: linear-gradient(135deg, #22c55e, #16a34a);
}

.instance-bar.inactive {
  background: linear-gradient(135deg, #9ca3af, #6b7280);
}

.instance-bar:hover {
  opacity: 0.8;
}

.bar-tooltip {
  display: none;
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: #1f2937;
  color: white;
  padding: 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  white-space: nowrap;
  z-index: 10;
}

.instance-bar:hover .bar-tooltip {
  display: block;
}

.timeline-legend {
  display: flex;
  gap: 1.5rem;
  padding-left: 160px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: #64748b;
}

.legend-bar {
  width: 20px;
  height: 12px;
  border-radius: 3px;
}

.legend-bar.active {
  background: linear-gradient(135deg, #22c55e, #16a34a);
}

.legend-bar.inactive {
  background: linear-gradient(135deg, #9ca3af, #6b7280);
}

.btn-group .btn {
  font-size: 0.75rem;
  padding: 0.25rem 0.75rem;
}
</style>
